"""汇率：USDT/CNY 折算。双源（OKX 公开价 -> frankfurter USD/CNY 近似）+ 日频缓存 + 时点标注。"""

from __future__ import annotations

import logging
from datetime import timedelta

import pandas as pd
import requests

from .store import Store

logger = logging.getLogger("quant.fx")

OKX_TICKER_URL = "https://www.okx.com/api/v5/market/ticker"
FRANKFURTER_URL = "https://api.frankfurter.app/latest"
STALE_TOLERANCE_DAYS = 7  # 汇率 7 天陈旧容忍（plan risk 表）


def _fetch_okx_usdt_cny() -> float | None:
    resp = requests.get(OKX_TICKER_URL, params={"instId": "USDT-CNY"}, timeout=10)
    resp.raise_for_status()
    data = resp.json()
    return float(data["data"][0]["last"])


def _fetch_usd_cny_approx() -> tuple[float, bool]:
    """frankfurter USD/CNY，近似认为 USDT ≈ USD（返回 approx 标记）。"""
    resp = requests.get(FRANKFURTER_URL, params={"from": "USD", "to": "CNY"}, timeout=10)
    resp.raise_for_status()
    return float(resp.json()["rates"]["CNY"]), True


def get_usdt_cny(store: Store) -> tuple[float, str]:
    """返回 (汇率, 时点描述)。缓存优先，超 7 天才尝试刷新。"""
    cached = store.last_fx("USDT/CNY")
    if cached:
        ts, rate, source = cached
        if pd.Timestamp.now() - pd.Timestamp(ts) <= timedelta(days=STALE_TOLERANCE_DAYS):
            return float(rate), f"缓存 {ts} ({source})"
    for fetcher, source_name in ((_fetch_okx_usdt_cny, "okx"), (_fetch_usd_cny_approx, "frankfurter~")):
        try:
            result = fetcher()
            rate = result[0] if isinstance(result, tuple) else result
            store.upsert_fx("USDT/CNY", rate, source_name)
            approx = "（USD 近似）" if "~" in source_name else ""
            return float(rate), f"{pd.Timestamp.now().normalize().date()} ({source_name}){approx}"
        except Exception as exc:  # noqa: BLE001 汇率源失败继续降级下一源/陈旧缓存
            logger.warning("fx 源 %s 失败: %s", source_name, exc)
    if cached:
        ts, rate, source = cached
        return float(rate), f"陈旧缓存 {ts} ({source})，超过容忍期请检查网络"
    raise RuntimeError("USDT/CNY 汇率获取失败且无缓存")
