"""模拟盘绩效报告（P1）——M9 实盘门槛的验收材料。

数据来源（全部本地库，无网络依赖）:
    - orders 表: 成交流水（filled / rejected）
    - accounts 表: 成交后账户快照序列 -> 权益曲线
    - dq_events: paper_skip 次数（信号触发但未成交）

权益计算: 各快照时点 cash + Σ 持仓数量 × 该时点最新收盘/净值。
期初权益取 --init-cash（与 trade paper 默认一致），首点拼接进权益曲线。
"""

from __future__ import annotations

import json
from dataclasses import dataclass

import pandas as pd

from ..data.store import Store


@dataclass
class PaperReport:
    days_running: int
    initial_cash: float
    equity_now: float
    total_return: float
    max_drawdown: float
    orders_filled: int
    orders_rejected: int
    skips: int
    positions: list[dict]
    equity_curve: pd.Series

    def as_dict(self) -> dict:
        return {
            "days_running": self.days_running,
            "initial_cash": round(self.initial_cash, 2),
            "equity_now": round(self.equity_now, 2),
            "total_return": round(self.total_return, 4),
            "max_drawdown": round(self.max_drawdown, 4),
            "orders_filled": self.orders_filled,
            "orders_rejected": self.orders_rejected,
            "skips": self.skips,
            "positions": self.positions,
        }

    def render(self) -> str:
        lines = [
            "===== 模拟盘报告 =====",
            f"运行天数: {self.days_running}",
            f"期初权益: {self.initial_cash:,.2f} CNY",
            f"当前权益: {self.equity_now:,.2f} CNY",
            f"累计收益率: {self.total_return:+.2%}   最大回撤: {self.max_drawdown:.2%}",
            f"成交 {self.orders_filled} 笔（拒单 {self.orders_rejected}，跳过 {self.skips}）",
        ]
        if self.positions:
            lines.append("当前持仓:")
            for p in self.positions:
                pnl = f"{p['unrealized_pnl']:+,.2f}" if p["unrealized_pnl"] is not None else "-"
                lines.append(
                    f"  {p['instrument']}  数量 {p['qty']:,.2f}  成本 {p['avg_cost']:.4f}  "
                    f"现价 {p['last_price']:.4f}  市值 {p['market_value']:,.2f} CNY  盈亏 {pnl} CNY"
                )
        else:
            lines.append("当前无持仓")
        return "\n".join(lines)


def _last_price_at(store: Store, iid: str, ts) -> float | None:
    """标的在 ts 时点的最新已收盘价（bars 收盘价 / navs 净值）。"""
    market = iid.split(":", 1)[0]
    if market == "fund":
        navs = store.get_navs(iid)
        if navs.empty:
            return None
        navs = navs[navs["ts"] <= pd.Timestamp(ts)]
        return float(navs["nav"].iloc[-1]) if not navs.empty else None
    bars = store.get_bars(iid)
    if bars.empty:
        return None
    bars = bars[bars["ts"] <= pd.Timestamp(ts)]
    return float(bars["close"].iloc[-1]) if not bars.empty else None


def build_paper_report(store: Store, init_cash: float = 100_000.0) -> PaperReport:
    orders = store.get_orders(account="paper", limit=10_000)
    snaps = store.conn.execute(
        "SELECT ts, cash, positions_json FROM accounts WHERE account = 'paper' ORDER BY ts"
    ).fetchall()

    # 权益曲线: 期初点 + 各快照点
    points: list[tuple[pd.Timestamp, float]] = []
    if orders is not None and not orders.empty:
        first_ts = pd.Timestamp(orders["ts"].min())
        points.append((first_ts, init_cash))
    elif snaps:
        points.append((pd.Timestamp(snaps[0][0]), init_cash))
    for ts, cash, positions_json in snaps:
        ts = pd.Timestamp(ts)
        equity = float(cash)
        for iid, p in json.loads(positions_json).items():
            if p.get("qty"):
                price = _last_price_at(store, iid, ts)
                if price is not None:
                    equity += p["qty"] * price
        points.append((ts, equity))
    if not points:
        raise ValueError("模拟盘无数据，请先运行 quant trade paper")

    equity_curve = pd.Series({t: v for t, v in points}).sort_index()
    equity_now = float(equity_curve.iloc[-1])
    total_return = equity_now / init_cash - 1
    max_dd = float((equity_curve / equity_curve.cummax() - 1).min())
    days_running = max((pd.Timestamp.now() - equity_curve.index[0]).days, 0)

    # 成交统计
    filled = rejected = 0
    if orders is not None and not orders.empty:
        filled = int((orders["status"] == "filled").sum())
        rejected = int((orders["status"] == "rejected").sum())
    events = store.get_dq_events(limit=10_000)
    skips = int((events["kind"] == "paper_skip").sum()) if not events.empty else 0

    # 当前持仓明细
    positions = []
    if snaps:
        for iid, p in json.loads(snaps[-1][2]).items():
            if not p.get("qty"):
                continue
            price = _last_price_at(store, iid, pd.Timestamp.now())
            mv = p["qty"] * price if price is not None else None
            cost = p.get("avg_cost") or 0.0
            pnl = (price - cost) * p["qty"] if (price is not None and cost) else None
            positions.append({
                "instrument": iid, "qty": p["qty"], "avg_cost": cost,
                "last_price": price, "market_value": mv, "unrealized_pnl": pnl,
            })

    return PaperReport(days_running, init_cash, equity_now, total_return, max_dd,
                       filled, rejected, skips, positions, equity_curve)
