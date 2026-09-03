"""M3 准确性：双源交叉验证（AC-14）。"""

from quant.data.quality import CROSS_CHECK_TOLERANCE, cross_check


def test_within_tolerance():
    r = cross_check(100.0, 100.3)  # 0.3% < 0.5%
    assert not r["deviated"] and abs(r["deviation"] - 0.003) < 1e-9


def test_deviated_beyond_tolerance():
    r = cross_check(100.0, 101.0)  # 1% > 0.5%
    assert r["deviated"] and r["deviation"] == 0.01


def test_skipped_when_missing_price():
    assert cross_check(None, 100.0)["skipped"]
    assert cross_check(100.0, None)["skipped"]
    assert cross_check(0.0, 100.0)["skipped"]


def test_deviation_writes_dq_event_via_cli_logic(store):
    """复刻 dq --cross 的写事件逻辑：偏差超限必须落 dq_event。"""
    local, remote = 100.0, 101.0
    r = cross_check(local, remote)
    if r["deviated"]:
        store.add_dq_event("cross_check_deviation", "600519.SH",
                           f"本地 {local} vs 第二源 {remote}，偏差 {r['deviation']:.4f} 超容差")
    events = store.get_dq_events()
    assert len(events) == 1
    assert events.iloc[0]["kind"] == "cross_check_deviation"
    assert "0.0100" in events.iloc[0]["detail"]


def test_tolerance_constant_is_documented_value():
    assert CROSS_CHECK_TOLERANCE == 0.005
