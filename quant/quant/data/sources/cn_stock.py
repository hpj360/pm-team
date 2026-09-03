"""A股数据源：akshare stock_zh_a_hist（前复权 qfq）。akshare 延迟导入。"""

from __future__ import annotations

from datetime import date

import pandas as pd

from .base import BaseSource

_COL_MAP = {"日期": "ts", "开盘": "open", "收盘": "close", "最高": "high",
            "最低": "low", "成交量": "volume"}


class CnStockSource(BaseSource):
    market = "cn"
    kind = "bars"

    def _fetch_remote(self, raw_symbol: str, start: date, end: date) -> pd.DataFrame:
        import akshare as ak  # noqa: PLC0415 延迟导入

        df = ak.stock_zh_a_hist(
            symbol=raw_symbol, period="daily",
            start_date=start.strftime("%Y%m%d"), end_date=end.strftime("%Y%m%d"),
            adjust="qfq",
        )
        if df is None or df.empty:
            return pd.DataFrame(columns=["ts", "open", "high", "low", "close", "volume"])
        df = df.rename(columns=_COL_MAP)[list(_COL_MAP.values())]
        df["ts"] = pd.to_datetime(df["ts"])
        return df
