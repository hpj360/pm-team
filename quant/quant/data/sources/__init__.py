"""数据源注册。"""

from __future__ import annotations

from .base import BaseSource
from .cn_stock import CnStockSource
from .crypto import CryptoSource
from .fund import FundSource

SOURCES: dict[str, type[BaseSource]] = {
    "cn": CnStockSource,
    "fund": FundSource,
    "crypto": CryptoSource,
}


def get_source(market: str) -> BaseSource:
    try:
        return SOURCES[market]()
    except KeyError:
        raise ValueError(f"未知市场: {market}（可选 {sorted(SOURCES)}）") from None
