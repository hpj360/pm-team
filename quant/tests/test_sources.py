"""数据源适配器：增量拉取 + 降级（AC-7）——mock 远端。"""

from datetime import date

from conftest import make_bars

from quant.data.sources.base import BaseSource


class FakeBarsSource(BaseSource):
    market = "cn"
    kind = "bars"
    remote_calls = 0

    def _fetch_remote(self, raw_symbol, start, end):
        self.remote_calls += 1
        df = make_bars(10, start=str(start))
        return df[df["ts"].dt.date <= end]


def test_incremental_fetch_and_idempotent(store):
    src = FakeBarsSource()
    start, end = date(2026, 7, 1), date(2026, 7, 10)
    df1 = src.fetch(store, "cn:600519.SH", "600519", start, end)
    assert len(df1) == 10 and src.remote_calls == 1
    # 区间已覆盖：不再请求远端（幂等，AC-1）
    df2 = src.fetch(store, "cn:600519.SH", "600519", start, end)
    assert len(df2) == 10 and src.remote_calls == 1
    assert store.conn.execute("SELECT count(*) FROM bars").fetchone()[0] == 10


def test_source_failure_degrades_to_cache(store):
    """源失败降级到缓存，已有数据仍可查（AC-7）。"""
    src = FakeBarsSource()
    start, end = date(2026, 7, 1), date(2026, 7, 10)
    src.fetch(store, "cn:600519.SH", "600519", start, end)

    class BrokenSource(FakeBarsSource):
        def _fetch_remote(self, raw_symbol, start, end):
            self.remote_calls += 1
            raise ConnectionError("akshare 接口不可用")

    broken = BrokenSource()
    df = broken.fetch(store, "cn:600519.SH", "600519", date(2026, 7, 1), date(2026, 8, 1))
    assert len(df) == 10  # 缓存数据不受影响
    events = store.get_dq_events()
    assert events.iloc[0]["kind"] == "source_failure"  # 降级事件已记录


def test_invalid_rows_quarantined(store):
    class DirtySource(FakeBarsSource):
        def _fetch_remote(self, raw_symbol, start, end):
            self.remote_calls += 1
            df = make_bars(5, start=str(start))
            df.loc[2, "high"] = 0.1  # 非法 OHLC
            return df

    src = DirtySource()
    df = src.fetch(store, "cn:600519.SH", "600519", date(2026, 7, 1), date(2026, 7, 5))
    assert len(df) == 4  # 非法行被隔离，不入库
    events = store.get_dq_events()
    assert events.iloc[0]["kind"] == "invalid_ohlc"
