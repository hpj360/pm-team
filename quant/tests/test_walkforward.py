"""P2 walk-forward + 波动率目标仓位测试。"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from quant.backtest.walkforward import walk_forward
from quant.signals.engine import SignalRecord, SignalRule
from quant.trading import paper as paper_mod
from tests.conftest import make_bars


def _sin_bars(n: int = 300, base: float = 100.0) -> pd.DataFrame:
    """正弦振荡: 均线交叉频繁，walk-forward 有信号可选。"""
    ts = pd.date_range("2025-01-01", periods=n, freq="D")
    close = base + 10 * np.sin(np.arange(n) * 2 * np.pi / 60)
    bars = pd.DataFrame({"ts": ts, "close": close})
    bars["open"] = close * 0.999
    bars["high"] = close * 1.005
    bars["low"] = close * 0.995
    bars["volume"] = 0.0
    return bars


# ---------- walk-forward ----------
def test_walkforward_shape_and_fields():
    res = walk_forward(_sin_bars(300), train_n=120, test_n=20)
    d = res.as_dict()
    assert d["n_folds"] == (300 - 120) // 20  # 滚动步长 20
    assert set(d) >= {"n_folds", "oos_total_return", "oos_median_fold_return",
                      "is_median_fold_return", "overfit_ratio", "best_params_freq"}
    assert len(res.folds) == d["n_folds"]
    assert sum(res.best_params_freq.values()) == d["n_folds"]


def test_walkforward_insufficient_data_raises():
    with pytest.raises(ValueError, match="数据不足"):
        walk_forward(_sin_bars(100), train_n=120, test_n=20)


def test_walkforward_oos_compound():
    """OOS 总收益 = Π(1+折收益)-1（复利拼接，手算对照）。"""
    res = walk_forward(_sin_bars(200), train_n=120, test_n=20)
    manual = 1.0
    for f in res.folds:
        manual *= 1 + f["oos_return"]
    assert res.oos_total_return == pytest.approx(manual - 1, abs=1e-6)


# ---------- 波动率目标仓位 ----------
@pytest.fixture
def vol_store(store):
    bars = make_bars(n=30, start="2026-07-01", base=100.0, drift=1.0)
    store.upsert_instrument("cn:600519.SH", "cn", "600519.SH", "CNY")
    store.upsert_bars("cn:600519.SH", bars)
    return store


def _record(sizing="fixed", target_vol=0.15, pct=0.2):
    rule = SignalRule(id="r1", symbol="600519.SH", market="cn", metric="close",
                      op="<", threshold=999, direction="buy",
                      target_position_pct=pct, sizing=sizing, target_vol=target_vol)
    return SignalRecord(rule=rule, ts=pd.Timestamp("2026-07-15").to_pydatetime(), value=1.0)


def test_default_amount_fixed(vol_store):
    amt = paper_mod.default_amount(vol_store, paper_mod.PaperAccount(cash=100_000),
                                   _record(pct=0.3))
    assert amt == 30_000.0  # 0.3 × 100k


def test_default_amount_vol_target(vol_store):
    """vol_target: 权重 = clip(target_vol/实现波动) 截到 1.0 上限。"""
    bars = vol_store.get_bars("cn:600519.SH")
    ret = bars["close"].pct_change().dropna().iloc[-20:]
    vol = float(ret.std() * (252 ** 0.5))
    # 目标波动 0.5 >> 实现波动 -> 权重截顶 1.0 -> 全额
    amt = paper_mod.default_amount(vol_store, paper_mod.PaperAccount(cash=100_000),
                                   _record(sizing="vol_target", target_vol=0.5))
    assert amt == 100_000.0
    # 目标波动 = 0.8×0.5×实现波动 -> 权重 = 0.4
    amt2 = paper_mod.default_amount(vol_store, paper_mod.PaperAccount(cash=100_000),
                                    _record(sizing="vol_target", target_vol=0.5 * vol * 0.8))
    assert amt2 == pytest.approx(0.4 * 100_000, rel=1e-6)


def test_vol_target_insufficient_data_falls_back(vol_store):
    """数据不足（< window+1）回退 fixed。"""
    record = _record(sizing="vol_target", target_vol=0.15, pct=0.1)
    # 直接构造 window 大于数据的调用: 用 default_amount 的 window 默认 20，
    # 上文 store 有 30 根 -> 覆盖不足场景需删数据，这里用新空 store 验证回退
    from quant.data.store import Store

    empty_store = Store(":memory:")
    empty_store.upsert_instrument("cn:600519.SH", "cn", "600519.SH", "CNY")
    amt = paper_mod.default_amount(empty_store, paper_mod.PaperAccount(cash=100_000),
                                   record)
    assert amt == 10_000.0  # 回退 target_position_pct × 100k
    empty_store.close()


def test_vol_target_rule_roundtrip_yaml():
    """sizing/target_vol 可从 yaml 加载（load_rules 透传新字段）。"""
    import yaml

    from quant.signals.engine import load_rules

    cfg_file = __import__("tempfile").mkdtemp() + "/s.yaml"
    with open(cfg_file, "w") as f:
        yaml.safe_dump({"rules": [{
            "id": "v1", "symbol": "600519", "market": "cn", "metric": "rsi14",
            "op": "<", "threshold": 30, "direction": "buy",
            "sizing": "vol_target", "target_vol": 0.2,
        }]}, f)
    rules = load_rules(cfg_file)
    assert rules[0].sizing == "vol_target"
    assert rules[0].target_vol == 0.2
