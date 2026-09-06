"""dq --push: 摘要内容契约 + 推送接线（plan step 16）。"""

from __future__ import annotations

from conftest import make_bars

from quant import cli


def _seed_stale_with_gaps(store):
    """10 根 bar 删中间 3 个交易日 → 3 缺口；末根停在 2026-08-10（必然 STALE）。"""
    df = make_bars(10, start="2026-08-01")
    store.upsert_instrument("cn:600519.SH", "cn", "600519.SH", "CNY")
    store.upsert_bars("cn:600519.SH", df, adj_type="qfq")
    for d in ("2026-08-03", "2026-08-04", "2026-08-05"):
        store.conn.execute("DELETE FROM bars WHERE ts = ?", [d])


def test_dq_summary_lists_stale_gaps_and_deviation():
    out = {
        "freshness": [
            {"instrument": "cn:600519.SH", "status": "STALE", "gaps": 3},
            {"instrument": "crypto:BTC-USDT", "status": "FRESH"},
        ],
        "cross_check": [
            {"instrument": "cn:600519.SH", "deviated": True, "deviation": 0.0123},
            {"instrument": "crypto:BTC-USDT", "deviated": False},
        ],
        "recent_dq_events": [{"kind": "source_failure"}],
    }
    s = cli._dq_summary(out)
    assert "STALE 标的 1 个: cn:600519.SH" in s
    assert "存在缺口标的 1 个: cn:600519.SH(3)" in s
    assert "交叉验证偏差 1 个: cn:600519.SH 0.0123" in s
    assert "近期 dq 事件 1 条" in s
    assert "全部正常" not in s


def test_dq_summary_all_clean():
    out = {"freshness": [{"instrument": "cn:600519.SH", "status": "FRESH"}],
           "recent_dq_events": []}
    assert "全部正常" in cli._dq_summary(out)


def test_dq_push_sends_summary(store, monkeypatch):
    _seed_stale_with_gaps(store)
    monkeypatch.setattr(cli, "_open_store", lambda read_only=False: store)
    sent: list[tuple[str, str, str]] = []

    class FakeNotifier:
        def __init__(self, *args, **kwargs):
            pass

        def send(self, title, content, level="regular"):
            sent.append((title, content, level))
            return {}

    monkeypatch.setattr(cli, "Notifier", FakeNotifier)
    cli.dq_report(symbol=None, cross=False, push=True)
    assert len(sent) == 1
    title, content, level = sent[0]
    assert title == "数据质量周报" and level == "regular"
    assert "STALE" in content and "600519" in content
