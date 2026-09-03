"""M8 风控熔断测试（AC-10: 超限单拦截）。"""

from __future__ import annotations

import pytest

from quant.trading.risk import DailyState, RiskChecker


@pytest.fixture
def checker():
    return RiskChecker(config={
        "max_order_usdt": 100.0, "max_daily_loss_usdt": 50.0,
        "max_daily_orders": 10, "price_deviation_pct": 0.05,
    })


def test_normal_order_approved(checker):
    d = checker.check("buy", 80.0, price=100.0, ref_price=99.5)
    assert d.approved


def test_order_amount_cap(checker):
    d = checker.check("buy", 150.0, price=100.0)
    assert not d.approved and "超上限" in d.reason


def test_daily_frequency_cap(checker):
    d = checker.check("buy", 50.0, price=100.0, state=DailyState(orders_today=10))
    assert not d.approved and "频次上限" in d.reason


def test_daily_loss_circuit_breaker(checker):
    d = checker.check("buy", 50.0, price=100.0,
                      state=DailyState(realized_pnl_usdt=-55.0))
    assert not d.approved and "日亏熔断" in d.reason
    # 恰好 -50（等于上限）也熔断（宁严勿松）
    d2 = checker.check("buy", 50.0, price=100.0,
                       state=DailyState(realized_pnl_usdt=-50.0))
    assert not d2.approved


def test_price_deviation_rejected(checker):
    d = checker.check("buy", 50.0, price=106.0, ref_price=100.0)  # +6%
    assert not d.approved and "价格偏离" in d.reason
    # 5% 以内放行
    d2 = checker.check("buy", 50.0, price=104.9, ref_price=100.0)
    assert d2.approved


def test_config_load_from_yaml(tmp_path):
    from quant.trading.risk import load_config

    cfg_file = tmp_path / "risk.yaml"
    cfg_file.write_text("max_order_usdt: 200\nmax_daily_orders: 3\n")
    cfg = load_config(cfg_file)
    assert cfg["max_order_usdt"] == 200 and cfg["max_daily_orders"] == 3
    # 未覆盖项回落默认
    assert cfg["price_deviation_pct"] == 0.05
