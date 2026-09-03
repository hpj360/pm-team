"""信号引擎：规则 -> Signal（M2 核心前置交付）。

设计（plan v1.2）:
    - 告警与交易信号统一为 Signal 实体，cooldown 去重只在这一层做（Principle 4）
    - 规则求值只允许用已收盘 bar（point-in-time，Principle 6）
    - SignalRule 声明式 yaml，cooldown_h 默认 24h 防信号风暴
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path

import pandas as pd

from ..data.store import DATA_DIR, Store
from ..research.indicators import compute_all

DEFAULT_RULES_PATH = DATA_DIR / "signals.yaml"

OPS = {"<", ">", "<=", ">=", "cross_below", "cross_above"}


@dataclass
class SignalRule:
    id: str
    symbol: str  # 规范化代码
    market: str  # cn / fund / crypto
    metric: str  # close / ma20 / ma60 / rsi14 / macd_hist / boll_pct_b / nav / nav_drawdown
    op: str
    threshold: float
    direction: str = "info"  # buy / sell / info
    cooldown_h: int = 24
    target_position_pct: float = 0.2  # 建议单目标仓位比例（保守默认 20%）
    name: str = ""

    def __post_init__(self) -> None:
        if self.op not in OPS:
            raise ValueError(f"非法操作符 {self.op!r}，可选 {sorted(OPS)}")


@dataclass
class SignalRecord:
    rule: SignalRule
    ts: datetime  # 触发所依据的 bar 时点
    value: float
    id: int = 0


def load_rules(path: Path | str | None = None) -> list[SignalRule]:
    import yaml

    path = Path(path) if path else DEFAULT_RULES_PATH
    if not path.exists():
        return []
    raw = yaml.safe_load(path.read_text()) or {}
    rules = []
    for item in raw.get("rules", []):
        item.setdefault("id", f"{item['symbol']}-{item['metric']}-{item['op']}")
        rules.append(SignalRule(**item))
    return rules


class SignalEngine:
    def __init__(self, store: Store, rules: list[SignalRule], now: datetime | None = None):
        self.store = store
        self.rules = rules
        self.now = now or datetime.now()

    # ---------- 指标序列（point-in-time：只读已落库的已收盘 bar） ----------
    def _metric_series(self, rule: SignalRule) -> pd.Series:
        iid = f"{rule.market}:{rule.symbol}"
        if rule.market == "fund":
            navs = self.store.get_navs(iid)
            if navs.empty:
                return pd.Series(dtype=float)
            nav = navs.set_index("ts")["nav"]
            if rule.metric == "nav":
                return nav
            if rule.metric == "nav_drawdown":
                return nav / nav.cummax() - 1
            raise ValueError(f"基金规则不支持指标 {rule.metric}")
        bars = self.store.get_bars(iid)
        if bars.empty:
            return pd.Series(dtype=float)
        close = bars.set_index("ts")["close"]
        if rule.metric == "close":
            return close
        ind = compute_all(close)
        if rule.metric in ("ma20", "ma60", "rsi14"):
            return ind[rule.metric]
        if rule.metric == "macd_hist":
            return ind["macd"]["hist"]
        if rule.metric == "boll_pct_b":
            return ind["boll"]["pct_b"]
        raise ValueError(f"未知指标 {rule.metric}")

    # ---------- 求值 ----------
    @staticmethod
    def _evaluate(op: str, value: float, prev: float | None, threshold: float) -> bool:
        if op == "<":
            return value < threshold
        if op == ">":
            return value > threshold
        if op == "<=":
            return value <= threshold
        if op == ">=":
            return value >= threshold
        if prev is None:
            return False
        if op == "cross_below":
            return prev >= threshold and value < threshold
        if op == "cross_above":
            return prev <= threshold and value > threshold
        return False

    def check(self) -> list[SignalRecord]:
        """求值全部规则，返回本次新产生的信号（cooldown 窗口内不重复）。"""
        fired: list[SignalRecord] = []
        for rule in self.rules:
            series = self._metric_series(rule)
            if series.empty or pd.isna(series.iloc[-1]):
                self.store.add_dq_event("signal_no_data", rule.symbol,
                                        f"规则 {rule.id} 无可用数据")
                continue
            value = float(series.iloc[-1])
            prev = float(series.iloc[-2]) if len(series) > 1 and not pd.isna(series.iloc[-2]) else None
            if not self._evaluate(rule.op, value, prev, rule.threshold):
                continue
            # cooldown: 同标的同向信号窗口内不重复（AC-11）
            since = self.now - timedelta(hours=rule.cooldown_h)
            dup = self.store.recent_signals(rule.symbol, rule.direction, since)
            if not dup.empty:
                continue
            ts = series.index[-1].to_pydatetime()
            sig_id = self.store.add_signal(
                ts=ts, symbol=rule.symbol, market=rule.market, metric=rule.metric,
                op=rule.op, threshold=rule.threshold, value=value,
                direction=rule.direction, rule_id=rule.id,
            )
            fired.append(SignalRecord(rule=rule, ts=ts, value=value, id=sig_id))
        return fired
