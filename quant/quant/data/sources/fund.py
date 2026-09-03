"""场外基金净值源：akshare fund_open_fund_info_em（天天基金）。akshare 延迟导入。"""

from __future__ import annotations

from datetime import date

import pandas as pd

from .base import BaseSource


class FundSource(BaseSource):
    market = "fund"
    kind = "navs"

    def _fetch_remote(self, raw_symbol: str, start: date, end: date) -> pd.DataFrame:
        import akshare as ak  # noqa: PLC0415 延迟导入

        df = ak.fund_open_fund_info_em(symbol=raw_symbol, indicator="单位净值走势")
        if df is None or df.empty:
            return pd.DataFrame(columns=["ts", "nav", "acc_nav"])
        df = df.rename(columns={"净值日期": "ts", "单位净值": "nav"})
        df = df[["ts", "nav"]].copy()
        df["ts"] = pd.to_datetime(df["ts"])
        df["acc_nav"] = None
        df = df[(df["ts"].dt.date >= start) & (df["ts"].dt.date <= end)]
        return df
