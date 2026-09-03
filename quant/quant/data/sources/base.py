"""数据源适配器基类：缓存优先 + 增量拉取 + 失败降级。

免费源限流/改版是本项目最大工程风险（plan Principle 1）:
    - 适配器薄封装，只依赖源少数宽接口
    - 已有缓存数据在源失败时仍可查询（AC-7），降级写 dq_event
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from datetime import date, timedelta

import pandas as pd

from ..quality import validate_ohlc, validate_navs
from ..store import Store

logger = logging.getLogger("quant.sources")


class BaseSource(ABC):
    market: str
    kind: str = "bars"  # bars / navs

    @abstractmethod
    def _fetch_remote(self, raw_symbol: str, start: date, end: date) -> pd.DataFrame:
        """从远端拉取。bars 返回列 ts/open/high/low/close/volume；navs 返回 ts/nav/acc_nav。"""

    def fetch(self, store: Store, instrument_id: str, raw_symbol: str,
              start: date, end: date) -> pd.DataFrame:
        """增量拉取并落库，返回库内全量数据（降级时返回已有缓存）。"""
        last = self._last_ts(store, instrument_id)
        fetch_from = start
        if last is not None:
            fetch_from = max(start, (last + timedelta(days=1)).date())
        if fetch_from > end:
            return self._read_all(store, instrument_id, start, end)

        try:
            df = self._fetch_remote(raw_symbol, fetch_from, end)
        except Exception as exc:  # 源失败：降级到缓存（AC-7）
            logger.warning("source-degraded: %s 拉取 %s 失败: %s", self.market, raw_symbol, exc)
            store.add_dq_event("source_failure", raw_symbol, f"{type(exc).__name__}: {exc}")
            return self._read_all(store, instrument_id, start, end)

        if df is not None and not df.empty:
            if self.kind == "bars":
                clean, bad = validate_ohlc(df)
                if len(bad):
                    store.add_dq_event("invalid_ohlc", raw_symbol,
                                       f"{len(bad)} 行非法 OHLC 被隔离: {bad['ts'].tolist()[:5]}")
                store.upsert_bars(instrument_id, clean, self._adj_type())
            else:
                clean, bad = validate_navs(df)
                if len(bad):
                    store.add_dq_event("invalid_nav", raw_symbol, f"{len(bad)} 行非法净值被隔离")
                store.upsert_navs(instrument_id, clean)
        return self._read_all(store, instrument_id, start, end)

    def _adj_type(self) -> str:
        return "qfq" if self.market == "cn" else "none"

    def _last_ts(self, store: Store, instrument_id: str):
        if self.kind == "bars":
            return store.last_bar_ts(instrument_id)
        return store.last_nav_ts(instrument_id)

    def _read_all(self, store: Store, instrument_id: str, start, end) -> pd.DataFrame:
        if self.kind == "bars":
            return store.get_bars(instrument_id, start, end)
        return store.get_navs(instrument_id, start, end)
