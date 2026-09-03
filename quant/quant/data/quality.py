"""数据质量体系（M0 地基 + M3 硬化）。

三层防线（plan v1.2）:
    1. 合法性校验  validate_ohlc / validate_navs —— 非法行隔离进 dq_events
    2. 时效性      freshness —— 分市场新鲜度阈值，STALE 判定
    3. 完整性      detect_gaps —— 交易日历比对缺口（M3）
"""

from __future__ import annotations

from datetime import date, datetime, timedelta

import pandas as pd

# 分市场新鲜度阈值（plan v1.2：cn >1 交易日 / fund >1 自然日 / crypto >2h 判 STALE）
FRESHNESS_RULES = {
    "cn": {"unit": "trading_day", "max": 1},
    "fund": {"unit": "natural_day", "max": 1},
    "crypto": {"unit": "hour", "max": 2},
}


# ---------- 1. 合法性校验 ----------
def validate_ohlc(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """校验 OHLCV 合法性，返回 (合法行, 非法行)。

    规则: low <= open/close <= high、low <= high、volume >= 0、价格 > 0、无空值。
    """
    if df.empty:
        return df, df
    required = {"ts", "open", "high", "low", "close", "volume"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"bar 数据缺列: {missing}")
    ok = (
        df["low"].notna() & df["open"].notna() & df["high"].notna() & df["close"].notna()
        & (df["low"] > 0) & (df["open"] > 0) & (df["high"] > 0) & (df["close"] > 0)
        & (df["low"] <= df[["open", "close", "high"]].min(axis=1))
        & (df["high"] >= df[["open", "close", "low"]].max(axis=1))
        & (df["low"] <= df["high"])
        & (df["volume"] >= 0)
    )
    return df[ok].copy(), df[~ok].copy()


def validate_navs(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """校验基金净值: nav > 0、acc_nav > 0（若存在）、无空值。"""
    if df.empty:
        return df, df
    ok = df["nav"].notna() & (df["nav"] > 0)
    if "acc_nav" in df.columns:
        ok &= df["acc_nav"].fillna(0) >= 0
    return df[ok].copy(), df[~ok].copy()


# ---------- 2. 时效性 ----------
def freshness(market: str, last_ts, now=None, is_trading_day=None) -> tuple[str, str]:
    """返回 (状态, 描述)。状态: FRESH / STALE / NO_DATA。"""
    if last_ts is None:
        return "NO_DATA", "无本地数据"
    last = pd.Timestamp(last_ts)
    now = pd.Timestamp(now) if now else pd.Timestamp.now()
    rule = FRESHNESS_RULES.get(market)
    if rule is None:
        return "UNKNOWN", f"未知市场 {market}"

    if rule["unit"] == "hour":
        age_h = (now - last).total_seconds() / 3600
        if age_h > rule["max"]:
            return "STALE", f"数据已 {age_h:.1f} 小时未更新（阈值 {rule['max']}h）"
        return "FRESH", f"数据时点 {last}"

    if rule["unit"] == "natural_day":
        age_d = (now.normalize() - last.normalize()).days
        if age_d > rule["max"]:
            return "STALE", f"数据已 {age_d} 天未更新（阈值 {rule['max']} 天）"
        return "FRESH", f"数据时点 {last}"

    # trading_day: 统计 last 之后的交易日数（无日历时工作日近似）
    _itd = is_trading_day or (lambda d: d.weekday() < 5)
    cur, trading_days = last.date() + timedelta(days=1), 0
    while cur <= (now.date() if isinstance(now, datetime) else now):
        if _itd(cur):
            trading_days += 1
        cur += timedelta(days=1)
    if trading_days > rule["max"]:
        return "STALE", f"数据落后 {trading_days} 个交易日（阈值 {rule['max']}）"
    return "FRESH", f"数据时点 {last}"


# ---------- 3. 完整性（M3） ----------
def detect_gaps(market: str, df: pd.DataFrame, start: date, end: date,
                is_trading_day=None) -> list[date]:
    """比对应有交易日 vs 实际数据，返回缺失日期列表。

    仅适用于 cn/fund 日频；crypto 7×24 免日历。
    """
    if market == "crypto":
        return []
    _itd = is_trading_day or (lambda d: d.weekday() < 5)
    have = {pd.Timestamp(ts).date() for ts in df["ts"]}
    missing = []
    cur = start
    while cur <= end:
        if _itd(cur) and cur not in have:
            missing.append(cur)
        cur += timedelta(days=1)
    return missing
