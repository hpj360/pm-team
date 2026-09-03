"""M8 币实盘执行器测试。

- 离线部分: dry-run 不触网 + 风控拦截审计（默认运行）
- testnet 闭环: @pytest.mark.network+testnet（需 TESTNET_API_KEY/SECRET，默认跳过）
"""

from __future__ import annotations

import pytest

from quant.data.store import Store
from quant.trading.live_crypto import LiveCryptoExecutor
from quant.trading.risk import RiskChecker

RISK_CFG = {"max_order_usdt": 100.0, "max_daily_loss_usdt": 50.0,
            "max_daily_orders": 10, "price_deviation_pct": 0.05}


def test_dry_run_no_network(store: Store):
    """dry-run: 只构建订单 + 过风控，不触网。"""
    ex = LiveCryptoExecutor(store, mode="dry-run", risk=RiskChecker(RISK_CFG))
    res = ex.place_order("BTC-USDT", "buy", 80.0, price=50_000.0, ref_price=50_100.0)
    assert res["status"] == "dry-run"
    assert res["risk"] == "passed"
    assert res["order"]["symbol"] == "BTC-USDT"
    # dry-run 不写 filled 订单
    assert store.get_orders().empty


def test_rejected_order_audited(store: Store):
    """风控拒绝: 订单以 rejected 落库 + dq_events 审计，绝不触网。"""
    ex = LiveCryptoExecutor(store, mode="dry-run", risk=RiskChecker(RISK_CFG))
    res = ex.place_order("BTC-USDT", "buy", 150.0, price=50_000.0)  # 超单笔上限
    assert res["status"] == "rejected"
    orders = store.get_orders()
    assert len(orders) == 1
    assert orders.iloc[0]["status"] == "rejected"
    assert "超上限" in orders.iloc[0]["note"]
    events = store.get_dq_events()
    assert (events["kind"] == "risk_rejected").any()


def test_live_mode_requires_confirmation(store: Store, monkeypatch):
    """实盘模式: 无 I_CONFIRM_LIVE_TRADING=1 直接拒绝。"""
    monkeypatch.delenv("I_CONFIRM_LIVE_TRADING", raising=False)
    ex = LiveCryptoExecutor(store, mode="live", risk=RiskChecker(RISK_CFG))
    with pytest.raises(RuntimeError, match="I_CONFIRM_LIVE_TRADING"):
        ex.place_order("BTC-USDT", "buy", 80.0, price=50_000.0)


def test_testnet_requires_keys(store: Store, monkeypatch):
    monkeypatch.delenv("TESTNET_API_KEY", raising=False)
    monkeypatch.delenv("TESTNET_SECRET", raising=False)
    ex = LiveCryptoExecutor(store, mode="testnet", risk=RiskChecker(RISK_CFG))
    with pytest.raises(RuntimeError, match="TESTNET"):
        ex.place_order("BTC-USDT", "buy", 80.0, price=50_000.0)


def test_daily_state_counts_today_orders(store: Store):
    """风控状态机: 当日订单数从 orders 表汇总。"""
    import pandas as pd

    for i in range(3):
        store.add_order(ts=pd.Timestamp.now(), signal_id=0,
                        instrument_id="crypto:BTC-USDT", market="crypto",
                        side="buy", price=50_000.0, qty=0.001, amount=50.0,
                        account="testnet", status="filled")
    ex = LiveCryptoExecutor(store, mode="dry-run", risk=RiskChecker(RISK_CFG))
    state = ex._daily_state("testnet")
    assert state.orders_today == 3


@pytest.mark.network
@pytest.mark.testnet
def test_testnet_roundtrip(store: Store):
    """testnet 闭环: 需 TESTNET_API_KEY/SECRET（CI 与本地默认跳过）。"""
    import os

    if not (os.environ.get("TESTNET_API_KEY") and os.environ.get("TESTNET_SECRET")):
        pytest.skip("未配置 TESTNET_API_KEY/SECRET")
    ex = LiveCryptoExecutor(store, mode="testnet", risk=RiskChecker(RISK_CFG))
    res = ex.place_order("BTC-USDT", "buy", 50.0, price=50_000.0, ref_price=50_000.0)
    assert res["status"] == "filled"
    orders = store.get_orders(account="testnet")
    assert (orders["status"] == "filled").any()
    # 超限单在 testnet 也被拦截
    res2 = ex.place_order("BTC-USDT", "buy", 500.0, price=50_000.0)
    assert res2["status"] == "rejected"
