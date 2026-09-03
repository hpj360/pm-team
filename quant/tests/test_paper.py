"""M7 模拟盘测试: 信号/成交/账户三方一致 + 手算基准（AC-9）。"""

from __future__ import annotations

import json

import pandas as pd
import pytest

from quant.data.store import Store
from quant.signals.engine import SignalRecord, SignalRule
from quant.trading import paper as paper_mod


@pytest.fixture
def cn_store(store: Store):
    """10 根日 bar，收盘价 10..19，信号取第 5 根时点（次 bar 开盘 = 15*0.99）。"""
    from tests.conftest import make_bars

    bars = make_bars(n=10, start="2026-07-01", base=10.0, drift=1.0)
    store.upsert_instrument("cn:600519.SH", "cn", "600519.SH", "CNY")
    store.upsert_bars("cn:600519.SH", bars)
    return store


def _record(direction="buy", ts="2026-07-06", signal_id=1):
    rule = SignalRule(id="r1", symbol="600519.SH", market="cn", metric="close",
                      op="<", threshold=99, direction=direction, target_position_pct=0.2)
    return SignalRecord(rule=rule, ts=pd.Timestamp(ts).to_pydatetime(), value=15.0,
                        id=signal_id)


def test_buy_next_bar_open_with_slippage(cn_store):
    """买入: 信号后第一根 bar 开盘价 × (1+0.1%)，三方一致。"""
    acct = paper_mod.PaperAccount(cash=100_000)
    res = paper_mod.execute(cn_store, acct, _record(), amount=10_000)

    assert res["status"] == "filled"
    # 手算: 2026-07-07 bar open = close(16)*0.99 = 15.84? no—make_bars open=close*0.99 of same row
    # 第 7 根(07-07) close=16, open=16*0.99=15.84; fill=15.84*1.001
    bars = cn_store.get_bars("cn:600519.SH")
    next_open = float(bars[bars["ts"] > pd.Timestamp("2026-07-06")]["open"].iloc[0])
    expected_fill = next_open * 1.001
    assert res["price"] == pytest.approx(expected_fill, rel=1e-9)
    assert res["amount"] == 10_000
    assert res["qty"] == pytest.approx(10_000 / expected_fill, rel=1e-9)

    # 账户一致
    assert acct.cash == pytest.approx(90_000)
    pos = acct.positions["cn:600519.SH"]
    assert pos["qty"] == pytest.approx(10_000 / expected_fill, rel=1e-9)

    # 订单落库一致
    orders = cn_store.get_orders(account="paper")
    assert len(orders) == 1
    o = orders.iloc[0]
    assert o["side"] == "buy" and o["status"] == "filled"
    assert o["price"] == pytest.approx(expected_fill)
    assert o["signal_id"] == 1

    # 快照可回读（三方对账闭环）
    row = cn_store.latest_account("paper")
    snap = json.loads(row[2])
    assert snap["cn:600519.SH"]["qty"] == pytest.approx(pos["qty"], rel=1e-9)
    assert row[1] == pytest.approx(90_000)


def test_buy_default_amount_from_target_pct(cn_store):
    """未指定金额: target_position_pct(0.2) × 100_000 = 20_000。"""
    acct = paper_mod.PaperAccount(cash=100_000)
    res = paper_mod.execute(cn_store, acct, _record())
    assert res["amount"] == 20_000
    assert acct.cash == pytest.approx(80_000)


def test_buy_insufficient_cash_skipped(cn_store):
    acct = paper_mod.PaperAccount(cash=5_000)
    res = paper_mod.execute(cn_store, acct, _record(), amount=10_000)
    assert res["status"] == "skipped" and res["reason"] == "现金不足"
    assert acct.cash == 5_000
    assert cn_store.get_orders(account="paper").empty
    # dq 事件留痕
    events = cn_store.get_dq_events()
    assert (events["kind"] == "paper_skip").any()


def test_sell_clears_position(cn_store):
    """卖出: 全仓成交价 × (1-0.1%)，持仓清零。"""
    acct = paper_mod.PaperAccount(cash=100_000)
    paper_mod.execute(cn_store, acct, _record(direction="buy"), amount=10_000)
    res = paper_mod.execute(cn_store, acct, _record(direction="sell", signal_id=2))

    assert res["status"] == "filled" and res["side"] == "sell"
    bars = cn_store.get_bars("cn:600519.SH")
    next_open = float(bars[bars["ts"] > pd.Timestamp("2026-07-06")]["open"].iloc[0])
    qty = 10_000 / (next_open * 1.001)
    expected_proceeds = qty * next_open * 0.999
    assert res["amount"] == pytest.approx(expected_proceeds, abs=0.01)  # 返回值 round(2)
    assert acct.positions["cn:600519.SH"]["qty"] == 0
    assert acct.cash == pytest.approx(100_000 - 10_000 + expected_proceeds, rel=1e-9)
    assert len(cn_store.get_orders(account="paper")) == 2


def test_sell_without_position_skipped(cn_store):
    acct = paper_mod.PaperAccount(cn_store)
    res = paper_mod.execute(cn_store, acct, _record(direction="sell"))
    assert res["status"] == "skipped" and res["reason"] == "无持仓"


def test_info_signal_not_traded(cn_store):
    acct = paper_mod.PaperAccount(cn_store)
    res = paper_mod.execute(cn_store, acct, _record(direction="info"))
    assert res["status"] == "ignored"
    assert cn_store.get_orders(account="paper").empty


def test_no_next_bar_falls_back_to_last_close(cn_store):
    """信号依据最新 bar（无次 bar）: 用最新收盘价并标注。"""
    acct = paper_mod.PaperAccount(cash=100_000)
    res = paper_mod.execute(cn_store, acct, _record(ts="2026-07-10"), amount=10_000)
    assert res["status"] == "filled"
    assert res["note"] == "无次bar，按最新收盘"
    last_close = float(cn_store.get_bars("cn:600519.SH")["close"].iloc[-1])
    assert res["price"] == pytest.approx(last_close * 1.001, rel=1e-9)


def test_fund_uses_nav_without_slippage(store: Store):
    from tests.conftest import make_navs

    navs = make_navs([1.0, 1.1, 1.2, 1.3])
    store.upsert_instrument("fund:000001.OF", "fund", "000001.OF", "CNY")
    store.upsert_navs("fund:000001.OF", navs)
    rule = SignalRule(id="r2", symbol="000001.OF", market="fund", metric="nav",
                      op="<", threshold=99, direction="buy", target_position_pct=0.2)
    record = SignalRecord(rule=rule, ts=pd.Timestamp("2026-07-02").to_pydatetime(),
                          value=1.1, id=1)
    acct = paper_mod.PaperAccount(cash=10_000)
    res = paper_mod.execute(store, acct, record, amount=5_000)
    assert res["status"] == "filled"
    assert res["price"] == pytest.approx(1.2)  # 次净值 1.2，fund 零滑点
    assert res["qty"] == pytest.approx(5_000 / 1.2)
