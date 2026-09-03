"""quality: OHLC 合法性（AC-14 部分）+ 新鲜度（AC-13）+ 缺口检测（AC-12 前置）。"""

from datetime import date, datetime, timedelta

import pandas as pd

from conftest import make_bars

from quant.data.quality import detect_gaps, freshness, validate_ohlc, validate_navs


def test_validate_ohlc_isolates_bad_rows():
    df = make_bars(5)
    df.loc[2, "high"] = df.loc[2, "low"] - 1  # 注入非法行: high < low
    clean, bad = validate_ohlc(df)
    assert len(clean) == 4 and len(bad) == 1  # 非法行被隔离（AC-14）


def test_validate_ohlc_negative_volume():
    df = make_bars(3)
    df.loc[0, "volume"] = -1
    clean, bad = validate_ohlc(df)
    assert len(bad) == 1


def test_validate_navs():
    df = pd.DataFrame({"ts": pd.date_range("2026-01-01", periods=3),
                       "nav": [1.0, -0.5, 1.1], "acc_nav": None})
    clean, bad = validate_navs(df)
    assert len(clean) == 2 and len(bad) == 1


def test_freshness_crypto_stale():
    now = pd.Timestamp("2026-09-03 12:00")
    status, _ = freshness("crypto", now - pd.Timedelta(hours=3), now=now)
    assert status == "STALE"  # 超 2h 阈值（AC-13）


def test_freshness_crypto_fresh():
    now = pd.Timestamp("2026-09-03 12:00")
    status, _ = freshness("crypto", now - pd.Timedelta(hours=1), now=now)
    assert status == "FRESH"


def test_freshness_cn_trading_days():
    now = datetime(2026, 9, 3)  # 周四
    # 最后数据 8/29（周五），此后 8/31、9/1、9/2、9/3 共 4 个交易日 > 1
    status, _ = freshness("cn", datetime(2026, 8, 29), now=now,
                          is_trading_day=lambda d: d.weekday() < 5)
    assert status == "STALE"


def test_freshness_fund_natural_days():
    now = datetime(2026, 9, 3)
    status, _ = freshness("fund", datetime(2026, 9, 2), now=now)
    assert status == "FRESH"
    status2, _ = freshness("fund", datetime(2026, 8, 30), now=now)
    assert status2 == "STALE"


def test_detect_gaps():
    df = make_bars(5, start="2026-08-31")  # 8/31-9/4（含周末 9/5-6? freq=D 5 天到 9/4）
    df = df[df["ts"].dt.date != date(2026, 9, 2)]  # 删 1 个交易日
    df = df[df["ts"].dt.date != date(2026, 9, 3)]
    df = df[df["ts"].dt.date != date(2026, 9, 4)]
    gaps = detect_gaps("cn", df, date(2026, 8, 31), date(2026, 9, 4),
                       is_trading_day=lambda d: d.weekday() < 5)
    assert gaps == [date(2026, 9, 2), date(2026, 9, 3), date(2026, 9, 4)]  # 精确 3 缺口


def test_no_data():
    status, _ = freshness("cn", None)
    assert status == "NO_DATA"
