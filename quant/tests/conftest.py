"""pytest 公共夹具。"""

from __future__ import annotations

import pandas as pd
import pytest

from quant.data.store import Store


@pytest.fixture
def store(tmp_path):
    s = Store(tmp_path / "test.duckdb")
    yield s
    s.close()


def make_bars(n=30, start="2026-07-01", base=10.0, drift=0.0) -> pd.DataFrame:
    """生成 n 根合成日线 bar（close = base + i*drift）。"""
    dates = pd.date_range(start, periods=n, freq="D")
    close = [base + i * drift for i in range(n)]
    return pd.DataFrame({
        "ts": dates,
        "open": [c * 0.99 for c in close],
        "high": [c * 1.02 for c in close],
        "low": [c * 0.98 for c in close],
        "close": close,
        "volume": [1000.0 + i for i in range(n)],
    })


def make_navs(values, start="2026-07-01") -> pd.DataFrame:
    dates = pd.date_range(start, periods=len(values), freq="D")
    return pd.DataFrame({"ts": dates, "nav": values, "acc_nav": None})
