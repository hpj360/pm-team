"""M3 完整性：缺口检测 -> 回补 -> 归零（AC-12）。"""

from datetime import date

from conftest import make_bars

from quant.data.sources.base import BaseSource


class RangeSource(BaseSource):
    """远端可返回任意区间的假源（按日历生成，测试用）。"""

    market = "cn"
    kind = "bars"

    def _fetch_remote(self, raw_symbol, start, end):
        df = make_bars((end - start).days + 1, start=str(start))
        return df[df["ts"].dt.date <= end]


def _seed_with_holes(store):
    """落 10 根 bar 并删掉中间 3 个交易日（8/3 周一、8/4、8/5）。"""
    df = make_bars(10, start="2026-08-01")  # 8/1(六)~8/10(一)，日频
    # 用工作日视角制造缺口：保留 ts，手动删 3 行
    store.upsert_bars("cn:600519.SH", df, adj_type="qfq")
    for d in ("2026-08-03", "2026-08-04", "2026-08-05"):
        store.conn.execute("DELETE FROM bars WHERE ts = ?", [d])
    return df


def test_gap_detection_reports_exactly_three(store):
    df = _seed_with_holes(store)
    from quant.data.quality import detect_gaps

    bars = store.get_bars("cn:600519.SH")
    gaps = detect_gaps("cn", bars, bars["ts"].min().date(), bars["ts"].max().date(),
                       is_trading_day=lambda d: d.weekday() < 5)
    assert gaps == [date(2026, 8, 3), date(2026, 8, 4), date(2026, 8, 5)]  # 精确 3 缺口


def test_backfill_closes_gaps_to_zero(store):
    _seed_with_holes(store)
    src = RangeSource()
    added = src.backfill(store, "cn:600519.SH", "600519", date(2026, 8, 10),
                         is_trading_day=lambda d: d.weekday() < 5)
    assert added == 3  # 补回 3 根
    from quant.data.quality import detect_gaps

    bars = store.get_bars("cn:600519.SH")
    gaps = detect_gaps("cn", bars, bars["ts"].min().date(), bars["ts"].max().date(),
                       is_trading_day=lambda d: d.weekday() < 5)
    assert gaps == []  # 缺口归零（AC-12）


def test_backfill_no_gaps_is_noop(store):
    store.upsert_bars("cn:600519.SH", make_bars(5, start="2026-08-03"), adj_type="qfq")
    src = RangeSource()
    assert src.backfill(store, "cn:600519.SH", "600519", date(2026, 8, 7)) == 0
    assert store.conn.execute("SELECT count(*) FROM bars").fetchone()[0] == 5


def test_backfill_remote_failure_recorded_not_raised(store):
    _seed_with_holes(store)

    class BrokenSource(RangeSource):
        def _fetch_remote(self, raw_symbol, start, end):
            raise ConnectionError("源不可用")

    added = BrokenSource().backfill(store, "cn:600519.SH", "600519", date(2026, 8, 10),
                                    is_trading_day=lambda d: d.weekday() < 5)
    assert added == 0
    events = store.get_dq_events()
    assert events.iloc[0]["kind"] == "backfill_failure"


def test_crypto_never_gaps():
    from quant.data.quality import detect_gaps

    assert detect_gaps("crypto", make_bars(3), date(2026, 8, 1), date(2026, 8, 3)) == []
