"""交易日历：完整性检测的基准。

数据源: akshare tool_trade_date_hist_sina（每年初刷新，本地 JSON 缓存）。
akshare 为延迟导入——测试与无网络环境不依赖真实安装。
"""

from __future__ import annotations

import json
from datetime import date, timedelta

from .store import DATA_DIR

CALENDAR_CACHE = DATA_DIR / "trade_calendar.json"


def _fetch_remote_calendar() -> list[str]:
    """从 akshare 拉取交易日历（延迟导入，网络操作）。"""
    import akshare as ak

    df = ak.tool_trade_date_hist_sina()
    return [str(d) for d in df["trade_date"].astype(str)]


def load_calendar(refresh: bool = False) -> list[date]:
    """加载交易日历，缓存缺失或跨年时刷新。"""
    cache: list[date] = []
    if CALENDAR_CACHE.exists() and not refresh:
        cache = [date.fromisoformat(s) for s in json.loads(CALENDAR_CACHE.read_text())]
    this_year = date.today().year
    if not cache or cache[-1].year < this_year:
        try:
            remote = _fetch_remote_calendar()
            cache = sorted({date.fromisoformat(s) for s in remote})
            CALENDAR_CACHE.parent.mkdir(parents=True, exist_ok=True)
            CALENDAR_CACHE.write_text(json.dumps([d.isoformat() for d in cache]))
        except Exception:
            # 日历源失效：用缓存兜底（跨年部分由调用方工作日近似）
            if not cache:
                raise
    return cache


def is_trading_day(d: date, calendar: list[date] | None = None) -> bool:
    """d 是否交易日。无日历时退化为「周一至周五」近似（置信度较低）。"""
    if calendar is None:
        try:
            calendar = load_calendar()
        except Exception:  # noqa: BLE001 无日历时退化为工作日近似（置信度较低）
            return d.weekday() < 5
    return d in set(calendar)


def expected_trading_days(start: date, end: date, calendar: list[date] | None = None) -> list[date]:
    """[start, end] 区间应有的交易日列表。"""
    if calendar is None:
        try:
            calendar = load_calendar()
        except Exception:  # noqa: BLE001 无日历时退化为工作日近似（置信度较低）
            # 近似：工作日（含法定节假日误差，detect_gaps 会标注置信度）
            days, cur = [], start
            while cur <= end:
                if cur.weekday() < 5:
                    days.append(cur)
                cur += timedelta(days=1)
            return days
    cal = set(calendar)
    return [d for d in calendar if start <= d <= end] if cal else []
