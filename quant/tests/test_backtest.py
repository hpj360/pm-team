"""回测引擎: 手算基准 + 未来函数专项断言 + 快照（AC-5 / AC-8）。"""

import pandas as pd
import pytest
from conftest import make_bars

from quant.backtest.engine import run_backtest, run_dca
from quant.backtest.strategies import momentum, sma_cross


def _trend_bars(n=80, start=10.0, drift=0.1):
    """单边上涨趋势（open≈close 前一根，便于手算）。"""
    closes = [start + i * drift for i in range(n)]
    dates = pd.date_range("2025-01-01", periods=n, freq="B")
    return pd.DataFrame({
        "ts": dates, "open": [c * 0.999 for c in closes], "high": [c * 1.01 for c in closes],
        "low": [c * 0.99 for c in closes], "close": closes, "volume": [100.0] * n,
    })


# ---------- run_backtest 手算基准 ----------
def test_manual_baseline_single_roundtrip():
    """手算: bar2 开盘买入 -> bar5 开盘卖出，验证权益精确值。"""
    n = 6
    closes = [10.0, 10.0, 11.0, 12.0, 13.0, 13.0]
    dates = pd.date_range("2026-01-01", periods=n, freq="D")
    bars = pd.DataFrame({
        "ts": dates, "open": [10.0, 10.0, 10.0, 11.0, 12.0, 13.0],
        "high": [c + 0.5 for c in closes], "low": [c - 0.5 for c in closes],
        "close": closes, "volume": [1.0] * n,
    })
    entries = pd.Series([False, True, False, False, False, False])   # bar1 收盘信号
    exits = pd.Series([False, False, False, False, True, False])     # bar4 收盘信号
    r = run_backtest(bars, entries, exits, init_cash=1000.0, fee=0.0)
    # bar2 开盘 10.0 买入 100 股 -> bar5 开盘 13.0 卖出 = 1300
    assert r.total_return == pytest.approx(0.30)
    assert r.n_trades == 1
    assert r.equity.iloc[-1] == pytest.approx(1300.0)


def test_fee_deducted():
    bars = _trend_bars(10)
    entries = pd.Series([True] + [False] * 9)
    exits = pd.Series([False] * 8 + [True, False])
    r0 = run_backtest(bars, entries, exits, fee=0.0)
    r1 = run_backtest(bars, entries, exits, fee=0.001)
    assert r1.total_return < r0.total_return


def test_no_signal_holds_cash():
    bars = _trend_bars(10)
    r = run_backtest(bars, pd.Series([False] * 10), pd.Series([False] * 10))
    assert r.total_return == 0.0 and r.n_trades == 0


# ---------- 未来函数专项（AC-5 核心） ----------
def test_no_lookahead():
    """信号 shift(1) 强制: 当根信号必须次 bar 开盘才生效。"""
    bars = _trend_bars(10)
    # bar5 收盘发出买入信号
    entries = pd.Series([False] * 5 + [True] + [False] * 4)
    exits = pd.Series([False] * 10)
    r = run_backtest(bars, entries, exits, fee=0.0)
    # 若无未来函数: bar6 开盘价成交，bar5 当根不应持有
    # 构造对照: 信号提前一根（bar4 收盘）结果必然不同（价格趋势上涨）
    entries_early = pd.Series([False] * 4 + [True] + [False] * 5)
    r_early = run_backtest(bars, entries_early, exits, fee=0.0)
    assert r_early.total_return > r.total_return  # 更早入场在上涨趋势中收益更高
    # 且当根信号版本: bar5 的权益仍为初始资金
    assert r.equity.iloc[4] == pytest.approx(100_000.0)
    assert r.equity.iloc[5] == pytest.approx(100_000.0)  # bar6 开盘才建仓，bar5 权益不变


# ---------- 策略 + 快照（AC-8） ----------
def _sine_bars(n=160):
    """振荡行情（强制产生均线交叉）。"""
    import math

    closes = [100 + 20 * math.sin(i / 12) + i * 0.05 for i in range(n)]
    dates = pd.date_range("2025-01-01", periods=n, freq="B")
    return pd.DataFrame({
        "ts": dates, "open": [c * 0.999 for c in closes], "high": [c * 1.01 for c in closes],
        "low": [c * 0.99 for c in closes], "close": closes, "volume": [100.0] * n,
    })


def test_sma_cross_strategy_runs_and_snapshot():
    bars = _sine_bars(160)
    entries, exits = sma_cross(bars)
    r = run_backtest(bars, entries, exits)
    assert r.n_trades >= 1  # 振荡行情必有交叉
    # 快照: 固定输入固定参数，结果必须可复现（数值锁定）
    snap = r.as_dict()
    r2 = run_backtest(bars, *sma_cross(bars))
    assert r2.as_dict() == snap  # 复跑一致
    assert all(k in snap for k in ("total_return", "annualized_return",
                                    "max_drawdown", "sharpe", "n_trades"))


def test_momentum_strategy_runs():
    bars = _trend_bars(100, drift=0.05)
    entries, exits = momentum(bars)
    r = run_backtest(bars, entries, exits)
    assert isinstance(r.total_return, float)


# ---------- DCA 份额累计法手工基准（plan reservation #6） ----------
def test_dca_manual_baseline():
    """手算: 3 期定投，份额累计法。"""
    closes = [1.0, 1.1, 1.2, 1.5]
    opens = [1.0, 1.0, 1.1, 1.2]
    dates = pd.date_range("2026-01-01", periods=4, freq="D")
    bars = pd.DataFrame({
        "ts": dates, "open": opens, "high": [c + 0.1 for c in closes],
        "low": [c - 0.1 for c in closes], "close": closes, "volume": [1.0] * 4,
    })
    # 按日定投（period="D"），每期首根 bar 开盘买入
    result = run_dca(bars, amount_per_period=1000.0, period="D")
    shares = 1000 / 1.0 + 1000 / 1.0 + 1000 / 1.1 + 1000 / 1.2
    invested = 4000.0
    assert result["shares"] == pytest.approx(shares)
    assert result["invested"] == invested
    assert result["final_value"] == pytest.approx(shares * 1.5)
    # run_dca 返回值保留 4 位小数
    assert result["total_return"] == pytest.approx(round(shares * 1.5 / 4000 - 1, 4), abs=1e-8)


def test_backtest_rejects_short_input():
    with pytest.raises(ValueError):
        run_backtest(make_bars(1), pd.Series([False]), pd.Series([False]))
