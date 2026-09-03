"""组合视图：跨市场持仓统一 CNY 计价（M4，AC-4）。

- 市值取本地最新收盘/净值（USDT 资产经汇率折算，汇率可注入便于测试）
- 输出强制携带数据时点戳与 STALE 状态（plan v1.2 数据质量要求）
"""

from __future__ import annotations

from dataclasses import dataclass, field

from ..data.quality import freshness
from ..data.store import Store
from .io import Position


@dataclass
class PositionView:
    position: Position
    last_price: float | None
    value_local: float  # 市值（本币: CNY 或 USDT 折算前）
    value_cny: float
    pnl_cny: float
    data_as_of: str
    data_status: str
    note: str = ""


@dataclass
class PortfolioView:
    positions: list[PositionView] = field(default_factory=list)
    total_cny: float = 0.0
    fx_desc: str = ""


def build_view(store: Store, positions: list[Position],
               usdt_cny: float = 1.0, fx_desc: str = "") -> PortfolioView:
    """构造组合视图。usdt_cny 注入便于测试；生产路径由 CLI 从 fx.py 取。"""
    view = PortfolioView(fx_desc=fx_desc)
    for pos in positions:
        iid = f"{pos.market}:{pos.symbol}"
        if pos.market == "fund":
            df, last = store.get_navs(iid), store.last_nav_ts(iid)
            price = float(df["nav"].iloc[-1]) if not df.empty else None
        else:
            df, last = store.get_bars(iid), store.last_bar_ts(iid)
            price = float(df["close"].iloc[-1]) if not df.empty else None
        status, _ = freshness(pos.market, last)

        if price is None:
            view.positions.append(PositionView(pos, None, 0.0, 0.0, 0.0,
                                               "无数据", "NO_DATA",
                                               note="请先 quant fetch"))
            continue
        value_local = pos.quantity * price
        rate = usdt_cny if pos.market == "crypto" else 1.0
        value_cny = value_local * rate
        cost_cny = pos.quantity * pos.avg_cost * rate
        view.positions.append(PositionView(
            pos, price, value_local, value_cny, value_cny - cost_cny,
            str(last), status,
        ))
        view.total_cny += value_cny
    return view


def render(view: PortfolioView) -> str:
    lines = []
    for p in view.positions:
        pos = p.position
        base = (f"[{pos.market}] {pos.symbol}  数量 {pos.quantity:g}")
        if p.last_price is None:
            lines.append(f"{base}  ⚠️ {p.note}")
            continue
        cost_part = f"成本 {pos.avg_cost:g}" if pos.avg_cost else ""
        lines.append(
            f"{base}  现价 {p.last_price:.4g}  市值 {p.value_cny:,.2f} CNY  "
            f"盈亏 {p.pnl_cny:+,.2f} CNY  {cost_part}  "
            f"[数据 {p.data_as_of} {p.data_status}]"
        )
    lines.append(f"合计市值: {view.total_cny:,.2f} CNY")
    if view.fx_desc:
        lines.append(f"USDT/CNY: {view.fx_desc}")
    return "\n".join(lines)
