"""signals: 触发 / cooldown 去重 / point-in-time（AC-11 核心）。"""

from datetime import datetime

from conftest import make_bars, make_navs

from quant.signals.engine import SignalEngine, SignalRule


def test_rule_trigger_and_persist(store):
    store.upsert_bars("cn:600519.SH", make_bars(30, base=10.0))
    rule = SignalRule(id="r1", symbol="600519.SH", market="cn", metric="close",
                      op="<", threshold=11.0, direction="buy")
    fired = SignalEngine(store, [rule], now=datetime.now()).check()
    assert len(fired) == 1
    assert fired[0].value == 10.0 + 29 * 0.0  # base=10, drift=0
    assert fired[0].value == 10.0
    # 信号落库
    assert len(store.get_signals()) == 1


def test_not_triggered_when_condition_false(store):
    store.upsert_bars("cn:600519.SH", make_bars(30, base=10.0))
    rule = SignalRule(id="r2", symbol="600519.SH", market="cn", metric="close",
                      op=">", threshold=100.0, direction="buy")
    assert SignalEngine(store, [rule], now=datetime.now()).check() == []


def test_cooldown_suppresses_duplicate(store):
    """同标的同向信号在 cooldown 窗口内不重复（AC-11）。"""
    store.upsert_bars("cn:600519.SH", make_bars(30, base=10.0))
    rule = SignalRule(id="r3", symbol="600519.SH", market="cn", metric="close",
                      op="<", threshold=11.0, direction="buy", cooldown_h=24)
    engine = SignalEngine(store, [rule], now=datetime.now())
    assert len(engine.check()) == 1
    assert engine.check() == []  # 24h 内第二次触发被抑制
    # 冷却期过后允许再次触发
    engine2 = SignalEngine(store, [rule], now=datetime.fromtimestamp(
        datetime.now().timestamp() + 25 * 3600))
    assert len(engine2.check()) == 1


def test_cooldown_independent_by_direction(store):
    store.upsert_bars("cn:600519.SH", make_bars(30, base=10.0))
    buy = SignalRule(id="rb", symbol="600519.SH", market="cn", metric="close",
                     op="<", threshold=11.0, direction="buy", cooldown_h=24)
    sell = SignalRule(id="rs", symbol="600519.SH", market="cn", metric="close",
                      op="<", threshold=11.0, direction="sell", cooldown_h=24)
    engine = SignalEngine(store, [buy, sell], now=datetime.now())
    assert len(engine.check()) == 2  # 不同方向互不抑制


def test_cross_below_requires_two_bars(store):
    closes = [12.0] * 29 + [10.0]
    store.upsert_bars("cn:600519.SH", make_bars(30).assign(close=closes))
    rule = SignalRule(id="rc", symbol="600519.SH", market="cn", metric="close",
                      op="cross_below", threshold=11.0, direction="sell")
    fired = SignalEngine(store, [rule], now=datetime.now()).check()
    assert len(fired) == 1 and fired[0].ts == store.get_bars("cn:600519.SH")["ts"].iloc[-1].to_pydatetime()
    # point-in-time: 信号 ts 等于最后一根已收盘 bar 的时点，而非 check 时刻


def test_point_in_time_uses_closed_bars_only(store):
    """指标求值只基于已落库（已收盘）bar——最后一根 bar 即证据。"""
    bars = make_bars(30, base=10.0, drift=0.1)
    store.upsert_bars("crypto:BTC-USDT", bars)
    rule = SignalRule(id="rp", symbol="BTC-USDT", market="crypto", metric="close",
                      op=">", threshold=0.0, direction="info")
    fired = SignalEngine(store, [rule], now=datetime.now()).check()
    assert fired[0].value == bars["close"].iloc[-1]  # 用最后一根收盘价，无未来数据


def test_fund_nav_drawdown_rule(store):
    navs = make_navs([1.0, 1.02, 1.05, 0.98])  # 回撤 6.7%
    store.upsert_navs("fund:000001.OF", navs)
    rule = SignalRule(id="rf", symbol="000001.OF", market="fund", metric="nav_drawdown",
                      op="<", threshold=-0.05, direction="buy")
    fired = SignalEngine(store, [rule], now=datetime.now()).check()
    assert len(fired) == 1
    assert fired[0].value < -0.05


def test_no_data_records_dq_event(store):
    rule = SignalRule(id="rnd", symbol="600519.SH", market="cn", metric="close",
                      op="<", threshold=11.0, direction="buy")
    SignalEngine(store, [rule], now=datetime.now()).check()
    events = store.get_dq_events()
    assert len(events) == 1 and events.iloc[0]["kind"] == "signal_no_data"
