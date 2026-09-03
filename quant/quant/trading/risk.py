"""风控熔断（M8，AC-10）——独立于策略代码，任何实盘下单必经此处。

规则（risk.yaml，plan step 25）:
    - 单笔金额上限（默认 100 USDT，实盘首月最小仓位）
    - 日亏上限（默认 50 USDT：当日已实现亏损达到即熔断）
    - 日频次上限（默认 10 笔）
    - 价格偏离 > 5% 拒单（成交价 vs 参考价，防插针/错价单）

设计: RiskChecker 是纯函数式检查器（不碰网络不碰库），调用方传入当日状态；
拒绝决策由调用方写审计日志（dq_events kind=risk_rejected / orders status=rejected）。
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from ..data.store import DATA_DIR

RISK_YAML = DATA_DIR / "risk.yaml"

DEFAULTS = {
    "max_order_usdt": 100.0,
    "max_daily_loss_usdt": 50.0,
    "max_daily_orders": 10,
    "price_deviation_pct": 0.05,
}


@dataclass
class RiskDecision:
    approved: bool
    reason: str = ""


@dataclass
class DailyState:
    """当日账户状态（由调用方从 orders/accounts 汇总）。"""

    orders_today: int = 0
    realized_pnl_usdt: float = 0.0  # 当日已实现盈亏（负数=亏损）


def load_config(path: Path | str | None = None) -> dict:
    import yaml

    path = Path(path) if path else RISK_YAML
    cfg = dict(DEFAULTS)
    if path.exists():
        raw = yaml.safe_load(path.read_text()) or {}
        cfg.update({k: raw[k] for k in DEFAULTS if k in raw})
    return cfg


class RiskChecker:
    """下单前置检查。check() 全过才允许进实盘执行器。"""

    def __init__(self, config: dict | None = None):
        self.cfg = config or load_config()

    def check(self, side: str, amount_usdt: float, price: float,
              ref_price: float | None = None, state: DailyState | None = None) -> RiskDecision:
        state = state or DailyState()
        if amount_usdt > self.cfg["max_order_usdt"]:
            return RiskDecision(False, f"单笔 {amount_usdt:.2f} USDT 超上限 "
                                       f"{self.cfg['max_order_usdt']:.0f}")
        if state.orders_today >= self.cfg["max_daily_orders"]:
            return RiskDecision(False, f"当日已 {state.orders_today} 笔，达频次上限 "
                                       f"{self.cfg['max_daily_orders']}")
        if state.realized_pnl_usdt <= -self.cfg["max_daily_loss_usdt"]:
            return RiskDecision(False, f"当日已实现亏损 {state.realized_pnl_usdt:.2f} USDT，"
                                       f"触发日亏熔断 {self.cfg['max_daily_loss_usdt']:.0f}")
        if ref_price and ref_price > 0:
            dev = abs(price - ref_price) / ref_price
            if dev > self.cfg["price_deviation_pct"]:
                return RiskDecision(False, f"价格偏离 {dev:.2%} 超过 "
                                           f"{self.cfg['price_deviation_pct']:.0%}，疑似错价")
        return RiskDecision(True, "ok")
