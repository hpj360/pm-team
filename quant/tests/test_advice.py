"""advice: 建议单内容契约（AC-11）+ STALE 标注（AC-13）+ 持仓参考。"""

from datetime import datetime

import pandas as pd
from conftest import make_bars

from quant.signals import advice as advice_mod
from quant.signals.advice import build_advice, push_advices, render
from quant.signals.engine import SignalEngine, SignalRule


def _rule(**kw):
    defaults = {"id": "r1", "symbol": "600519.SH", "market": "cn", "metric": "close",
                "op": "<", "threshold": 11.0, "direction": "buy", "target_position_pct": 0.2}
    defaults.update(kw)
    return SignalRule(**defaults)


def test_advice_content_contract(store, monkeypatch, tmp_path):
    monkeypatch.setattr(advice_mod, "POSITIONS_PATH", tmp_path / "none.csv")
    store.upsert_bars("cn:600519.SH", make_bars(30, base=10.0))
    engine = SignalEngine(store, [_rule()], now=datetime.now())
    fired = engine.check()
    advice = build_advice(store, fired[0])
    # spec AC-11 内容契约
    for key in ("code", "direction", "target_position_pct", "reason",
                "data_as_of", "data_status"):
        assert key in advice, f"缺少字段 {key}"
    assert advice["direction_cn"] == "买入"
    assert advice["target_position_pct"] == 0.2
    assert "close < 11" in advice["reason"] and "10" in advice["reason"]


def test_advice_stale_annotation(store, monkeypatch, tmp_path):
    """数据陈旧时建议单显著标注（AC-13）。"""
    monkeypatch.setattr(advice_mod, "POSITIONS_PATH", tmp_path / "none.csv")
    # crypto 最后一根 bar 在 3 小时前（超 2h 阈值）-> STALE
    bars = make_bars(30, base=10.0)
    bars["ts"] = pd.Timestamp.now() - pd.to_timedelta(range(32, 2, -1), unit="h")
    store.upsert_bars("crypto:BTC-USDT", bars)
    engine = SignalEngine(store, [_rule(symbol="BTC-USDT", market="crypto")], now=datetime.now())
    fired = engine.check()
    advice = build_advice(store, fired[0])
    assert advice["data_status"] == "STALE"
    text = render(advice)
    assert "STALE" in text and "⚠️" in text  # 显著标注


def test_advice_with_positions_reference(store, monkeypatch, tmp_path):
    pos = tmp_path / "positions.csv"
    pos.write_text("market,symbol,quantity,avg_cost,opened_at\n"
                   "cn,600519.SH,100,9.5,2026-01-01\n")
    monkeypatch.setattr(advice_mod, "POSITIONS_PATH", pos)
    store.upsert_bars("cn:600519.SH", make_bars(30, base=10.0))
    engine = SignalEngine(store, [_rule()], now=datetime.now())
    fired = engine.check()
    advice = build_advice(store, fired[0])
    assert advice["current_holding"] == {"quantity": 100.0, "avg_cost": 9.5}
    assert "当前持仓: 100，成本 9.5" in render(advice)


def test_push_advices_routes_by_level(store, monkeypatch, tmp_path):
    """buy/sell 走 urgent，info 走 regular；cooldown 内不重复推送。"""
    monkeypatch.setattr(advice_mod, "POSITIONS_PATH", tmp_path / "none.csv")
    store.upsert_bars("cn:600519.SH", make_bars(30, base=10.0))
    sent: list[tuple[str, str]] = []

    class FakeNotifier:
        def send(self, title, content, level="regular"):
            sent.append((level, content))
            return {"fake": True}

    rule = _rule()
    engine = SignalEngine(store, [rule], now=datetime.now())
    fired = engine.check()
    advices = push_advices(store, FakeNotifier(), fired)
    assert len(advices) == 1 and sent[0][0] == "urgent"  # buy -> urgent 双通道

    info_rule = _rule(id="ri", direction="info")
    store2_fired = SignalEngine(store, [info_rule], now=datetime.now()).check()
    push_advices(store, FakeNotifier(), store2_fired)
    assert sent[-1][0] == "regular"  # info -> regular

    # cooldown 内同向信号不再推送（AC-11）
    refired = SignalEngine(store, [rule], now=datetime.now()).check()
    assert refired == []
