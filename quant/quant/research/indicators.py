"""技术指标库：纯 pandas 纯函数，供信号引擎与回测共用（M1）。

Point-in-time 约束（plan Principle 6）: 输入只应传「已收盘 bar」的 DataFrame，
调用方（signals/backtest）负责保证，指标函数不做未来窥探。
"""

from __future__ import annotations

import pandas as pd


def ma(close: pd.Series, n: int = 20) -> pd.Series:
    """简单移动平均。"""
    return close.rolling(n, min_periods=n).mean()


def rsi(close: pd.Series, n: int = 14) -> pd.Series:
    """RSI（Wilder 平滑）。前 n 行为 NaN。"""
    delta = close.diff()
    gain = delta.clip(lower=0.0)
    loss = -delta.clip(upper=0.0)
    avg_gain = gain.ewm(alpha=1 / n, adjust=False, min_periods=n).mean()
    avg_loss = loss.ewm(alpha=1 / n, adjust=False, min_periods=n).mean()
    rs = avg_gain / avg_loss
    return 100 - 100 / (1 + rs)


def macd(close: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9) -> pd.DataFrame:
    """MACD，返回 DataFrame: dif / dea / hist。"""
    dif = close.ewm(span=fast, adjust=False).mean() - close.ewm(span=slow, adjust=False).mean()
    dea = dif.ewm(span=signal, adjust=False).mean()
    hist = (dif - dea) * 2  # 国内惯例 MACD 柱 = 2*(DIF-DEA)
    return pd.DataFrame({"dif": dif, "dea": dea, "hist": hist})


def boll(close: pd.Series, n: int = 20, k: float = 2.0) -> pd.DataFrame:
    """布林带（总体标准差 ddof=0），返回 mid/upper/lower/%b。"""
    mid = close.rolling(n, min_periods=n).mean()
    std = close.rolling(n, min_periods=n).std(ddof=0)
    upper, lower = mid + k * std, mid - k * std
    pct_b = (close - lower) / (upper - lower)
    return pd.DataFrame({"mid": mid, "upper": upper, "lower": lower, "pct_b": pct_b})


def compute_all(close: pd.Series) -> dict[str, pd.Series | pd.DataFrame]:
    """一次算齐常用指标，供研析报告与信号引擎使用。"""
    return {
        "ma20": ma(close, 20),
        "ma60": ma(close, 60),
        "rsi14": rsi(close, 14),
        "macd": macd(close),
        "boll": boll(close),
    }
