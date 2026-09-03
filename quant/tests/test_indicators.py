"""indicators: 与手工基准比对，误差 < 1e-8（AC-3）。"""

import math

import pandas as pd

from quant.research.indicators import boll, compute_all, ma, macd, rsi

CLOSES = [10.0, 10.5, 10.2, 10.8, 11.0, 10.6, 10.9, 11.2, 11.5, 11.3,
          11.8, 12.0, 11.6, 11.9, 12.2, 12.5, 12.1, 12.4, 12.6, 12.3,
          12.8, 13.0, 12.7, 13.1, 13.4]
S = pd.Series(CLOSES)


def _close(a, b):
    assert abs(a - b) < 1e-8, f"{a} != {b}"


def test_ma_manual_baseline():
    result = ma(S, 20)
    assert result.isna().iloc[:19].all() and not pd.isna(result.iloc[19])
    expected = sum(CLOSES[:20]) / 20
    _close(result.iloc[19], expected)
    expected2 = sum(CLOSES[1:21]) / 20
    _close(result.iloc[20], expected2)


def _ewm_manual(values, alpha):
    """adjust=False 的 ewm 手工递推。"""
    out, prev = [], None
    for x in values:
        prev = x if prev is None else (1 - alpha) * prev + alpha * x
        out.append(prev)
    return out


def test_rsi_manual_wilder_baseline():
    n = 14
    result = rsi(S, n)
    assert pd.isna(result.iloc[n - 1]) and not pd.isna(result.iloc[n])
    deltas = [CLOSES[i] - CLOSES[i - 1] for i in range(1, len(CLOSES))]
    gains = _ewm_manual([max(d, 0.0) for d in deltas], 1 / n)
    losses = _ewm_manual([max(-d, 0.0) for d in deltas], 1 / n)
    for i in (n, len(CLOSES) - 1):  # 第 n 个 diff 对应 close 下标 i
        g, l = gains[i - 1], losses[i - 1]
        expected = 100 - 100 / (1 + g / l) if l else 100.0
        _close(result.iloc[i], expected)


def test_macd_manual_ewm_baseline():
    result = macd(S)
    fast = _ewm_manual(CLOSES, 2 / 13)
    slow = _ewm_manual(CLOSES, 2 / 27)
    dif = [f - s for f, s in zip(fast, slow)]
    dea = _ewm_manual(dif, 2 / 10)
    for i in (5, 24):
        _close(result["dif"].iloc[i], dif[i])
        _close(result["dea"].iloc[i], dea[i])
        _close(result["hist"].iloc[i], (dif[i] - dea[i]) * 2)


def test_boll_manual_baseline():
    result = boll(S, 20, 2.0)
    window = CLOSES[:20]
    mean = sum(window) / 20
    var = sum((x - mean) ** 2 for x in window) / 20  # 总体方差 ddof=0
    std = math.sqrt(var)
    _close(result["mid"].iloc[19], mean)
    _close(result["upper"].iloc[19], mean + 2 * std)
    _close(result["lower"].iloc[19], mean - 2 * std)
    _close(result["pct_b"].iloc[19], (CLOSES[19] - (mean - 2 * std)) / (4 * std))


def test_compute_all_shapes():
    all_ind = compute_all(S)
    assert set(all_ind) == {"ma20", "ma60", "rsi14", "macd", "boll"}
    assert all_ind["ma60"].isna().all()  # 25 根不足 60
