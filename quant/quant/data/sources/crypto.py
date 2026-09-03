"""加密币数据源：ccxt binance 公开行情（无需 API Key）。ccxt 延迟导入。"""

from __future__ import annotations

from datetime import date

import pandas as pd

from .base import BaseSource


class CryptoSource(BaseSource):
    market = "crypto"
    kind = "bars"

    def __init__(self, exchange_id: str = "binance"):
        self.exchange_id = exchange_id

    def _fetch_remote(self, raw_symbol: str, start: date, end: date) -> pd.DataFrame:
        import ccxt  # noqa: PLC0415 延迟导入

        ex = getattr(ccxt, self.exchange_id)({"enableRateLimit": True})
        since = int(pd.Timestamp(start).timestamp() * 1000)
        limit = (end - start).days + 1
        ohlcv = ex.fetch_ohlcv(raw_symbol, timeframe="1d", since=since, limit=min(limit, 1000))
        if not ohlcv:
            return pd.DataFrame(columns=["ts", "open", "high", "low", "close", "volume"])
        df = pd.DataFrame(ohlcv, columns=["ts", "open", "high", "low", "close", "volume"])
        df["ts"] = pd.to_datetime(df["ts"], unit="ms")
        df = df[df["ts"].dt.date <= end]
        return df
