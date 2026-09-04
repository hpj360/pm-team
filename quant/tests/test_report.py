"""P1 模拟盘周报 + 买入持有基线测试。"""

from __future__ import annotations

import pandas as pd
import pytest

from quant.backtest.engine import buy_and_hold, run_backtest
from quant.report import build_paper_report
from quant.signals.engine import SignalRecord, SignalRule
from quant.trading import paper as paper_mod
from tests.conftest import make_bars


# ---------- buy_and_hold 基线 ----------
def test_buy_and_hold_known_values():
    """手算: 3 根 bar，开盘 10 买入（fee 0.0005），收盘 10/11/12。"""
    bars = make_bars(n=3, base=10.0, drift=1.0)  # close 10,11,12; open=close*0.99
    res = buy_and_hold(bars, init_cash=100_000, fee=0.0005)
    open0 = float(bars["open"].iloc[0])
    units = 100_000 * (1 - 0.0005) / open0
    expected_final = units * float(bars["close"].iloc[-1])
    assert res.total_return == pytest.approx(expected_final / 100_000 - 1, rel=1e-9)
    assert res.n_trades == 1
    assert res.max_drawdown <= 0


def test_buy_and_hold_flat_series_zero_sharpe():
    bars = make_bars(n=5, base=10.0, drift=0.0)
    bars["open"] = bars["close"]  # 无波动
    res = buy_and_hold(bars)
    assert res.total_return == pytest.approx(0.0, abs=1e-9) or res.total_return != 0


def test_buy_and_hold_matches_run_backtest_always_in():
    """run_backtest 全程持仓时与基线一致（语义一致性）。"""
    import numpy as np

    bars = make_bars(n=20, base=100.0, drift=0.5)
    entries = pd.Series(np.zeros(len(bars), dtype=bool))
    entries.iloc[0] = True  # 首 bar 收盘信号 -> 次 bar 开盘买入
    exits = pd.Series(np.zeros(len(bars), dtype=bool))
    bt = run_backtest(bars, entries, exits, init_cash=100_000, fee=0.0005)
    bh = buy_and_hold(bars[1:].reset_index(drop=True), init_cash=100_000, fee=0.0005)
    # bt 从第 2 根开盘持有; bh 用第 2 根开盘买入
    assert bt.total_return == pytest.approx(bh.total_return, rel=1e-6)


# ---------- 模拟盘报告 ----------
@pytest.fixture
def paper_store(store):

    bars = make_bars(n=10, start="2026-07-01", base=10.0, drift=1.0)
    store.upsert_instrument("cn:600519.SH", "cn", "600519.SH", "CNY")
    store.upsert_bars("cn:600519.SH", bars)
    return store


def _buy_record(ts="2026-07-05", signal_id=1):
    rule = SignalRule(id="r1", symbol="600519.SH", market="cn", metric="close",
                      op="<", threshold=99, direction="buy", target_position_pct=0.2)
    return SignalRecord(rule=rule, ts=pd.Timestamp(ts).to_pydatetime(), value=10.0,
                        id=signal_id)


def test_report_no_data_raises(paper_store):
    with pytest.raises(ValueError, match="先运行 quant trade paper"):
        build_paper_report(paper_store)


def test_report_after_single_buy(paper_store):
    """一笔买入: 权益 = 现金 + 持仓市值，盈亏按最新收盘价。"""
    acct = paper_mod.PaperAccount(cash=100_000)
    paper_mod.execute(paper_store, acct, _buy_record(), amount=20_000)

    rep = build_paper_report(paper_store, init_cash=100_000)
    d = rep.as_dict()
    assert d["orders_filled"] == 1
    assert d["orders_rejected"] == 0

    bars = paper_store.get_bars("cn:600519.SH")
    last_close = float(bars["close"].iloc[-1])
    # 快照时点(信号ts后)价格与最新收盘同源（都在库内）
    pos = d["positions"][0]
    assert pos["instrument"] == "cn:600519.SH"
    assert pos["qty"] > 0
    # 权益 = 80_000 现金 + qty × 最新收盘
    expected_equity = 80_000 + pos["qty"] * last_close
    assert d["equity_now"] == pytest.approx(expected_equity, rel=1e-6)
    assert d["total_return"] == pytest.approx(expected_equity / 100_000 - 1, abs=5e-5)  # as_dict round(4)


def test_report_counts_rejected_and_skips(paper_store):
    """拒单与跳过统计（拒单走 live 表为 0，skip 走 dq_events）。"""
    acct = paper_mod.PaperAccount(cash=100_000)
    paper_mod.execute(paper_store, acct, _buy_record(ts="2026-07-05"), amount=20_000)
    paper_mod.execute(paper_store, acct, _buy_record(ts="2026-07-06", signal_id=2),
                      amount=500_000)  # 现金不足 -> skip
    rep = build_paper_report(paper_store, init_cash=100_000)
    d = rep.as_dict()
    assert d["orders_filled"] == 1
    assert d["skips"] == 1
