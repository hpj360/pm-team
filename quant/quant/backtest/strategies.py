"""策略样例（M5）: sma_cross / momentum / dca。

策略只产出信号（entries/exits 或 dca 参数），成交与 point-in-time 由引擎统一处理。
"""

from __future__ import annotations

import pandas as pd

from ..research.indicators import ma, rsi


def sma_cross(bars: pd.DataFrame, fast: int = 20, slow: int = 60) -> tuple[pd.Series, pd.Series]:
    """均线交叉: 金叉买入，死叉卖出。"""
    close = bars["close"].reset_index(drop=True)
    f, s = ma(close, fast), ma(close, slow)
    entries = (f > s) & (f.shift(1) <= s.shift(1))
    exits = (f < s) & (f.shift(1) >= s.shift(1))
    return entries, exits


def momentum(bars: pd.DataFrame, lookback: int = 20, rsi_low: float = 30,
             rsi_high: float = 70) -> tuple[pd.Series, pd.Series]:
    """动量+RSI: 动量向上且 RSI 低位买入；RSI 超买卖出。"""
    close = bars["close"].reset_index(drop=True)
    mom = close.pct_change(lookback)
    r = rsi(close, 14)
    entries = (mom > 0) & (mom.shift(1) <= 0) & (r < rsi_low)
    exits = r > rsi_high
    return entries, exits


# dca 策略参数封装（引擎 run_dca 直接消费）
DCA_DEFAULTS = {"amount_per_period": 1000.0, "period": "M"}
