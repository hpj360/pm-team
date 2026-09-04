"""加密币数据源：ccxt 多交易所公开行情（无需 API Key），逐个降级。

默认链: binance -> okx -> gate。单一交易所可能因地域/网络封锁不可达
（如大陆网络封锁 binance/okx），故支持:
    - 环境变量 QUANT_CRYPTO_EXCHANGE 指定单所（如 gate / bybit）
    - 失败自动尝试链中下一所，全部失败才降级本地缓存（base.py 统一处理）
ccxt 延迟导入。
"""

from __future__ import annotations

import os
from datetime import date

import pandas as pd

from .base import BaseSource

DEFAULT_CHAIN = ["binance", "okx", "gate"]
EMPTY = pd.DataFrame(columns=["ts", "open", "high", "low", "close", "volume"])


class CryptoSource(BaseSource):
    market = "crypto"
    kind = "bars"

    def __init__(self, exchange_id: str | None = None):
        # 显式指定单所；否则走默认降级链
        self.chain = [exchange_id] if exchange_id else list(DEFAULT_CHAIN)
        self.exchange_id = self.chain[0]

    def _fetch_one(self, ccxt, exchange_id: str, raw_symbol: str,
                   start: date, end: date) -> pd.DataFrame:
        ex = getattr(ccxt, exchange_id)({"enableRateLimit": True})
        since = int(pd.Timestamp(start).timestamp() * 1000)
        limit = (end - start).days + 1
        ohlcv = ex.fetch_ohlcv(raw_symbol, timeframe="1d", since=since,
                               limit=min(limit, 1000))
        if not ohlcv:
            return EMPTY
        df = pd.DataFrame(ohlcv, columns=["ts", "open", "high", "low", "close", "volume"])
        df["ts"] = pd.to_datetime(df["ts"], unit="ms")
        return df[df["ts"].dt.date <= end]

    def _fetch_remote(self, raw_symbol: str, start: date, end: date) -> pd.DataFrame:
        import ccxt  # 延迟导入

        chain = [os.environ.get("QUANT_CRYPTO_EXCHANGE")] if os.environ.get(
            "QUANT_CRYPTO_EXCHANGE") else self.chain
        last_exc: Exception | None = None
        for exchange_id in chain:
            try:
                df = self._fetch_one(ccxt, exchange_id, raw_symbol, start, end)
                self.exchange_id = exchange_id  # 记录实际成功的所
                return df
            except Exception as exc:  # noqa: BLE001 单所失败继续降级（网络/地域封锁等多因）
                last_exc = exc
                continue
        if last_exc is not None:
            raise RuntimeError(
                f"全部交易所尝试失败（{', '.join(chain)}）: {last_exc}。"
                f"多为地域/网络封锁，可设 QUANT_CRYPTO_EXCHANGE=<id> 指定其他交易所"
                f"（如 gate / bybit / htx），或配置代理。"
            ) from last_exc
        return EMPTY
