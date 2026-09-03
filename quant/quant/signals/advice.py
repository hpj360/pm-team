"""操作建议单：Signal -> 人读建议（A股/基金人工确认执行，M2）。

内容契约（spec AC-11）: 代码/名称/方向/目标仓位比例/信号依据/数据时点戳/STALE 状态。
有 positions.csv 时附当前持仓参考；绝对参考数量留待 M4 组合就位后。
"""

from __future__ import annotations

import pandas as pd

from ..alerts.notify import Notifier
from ..data.quality import freshness
from ..data.store import DATA_DIR, Store
from .engine import SignalRecord

POSITIONS_PATH = DATA_DIR / "positions.csv"

DIRECTION_CN = {"buy": "买入", "sell": "卖出/减仓", "info": "关注"}


def _load_positions() -> pd.DataFrame:
    if POSITIONS_PATH.exists():
        return pd.read_csv(POSITIONS_PATH)
    return pd.DataFrame()


def build_advice(store: Store, record: SignalRecord) -> dict:
    """构造结构化建议单 dict。"""
    rule = record.rule
    iid = f"{rule.market}:{rule.symbol}"
    last_ts = (store.last_nav_ts(iid) if rule.market == "fund" else store.last_bar_ts(iid))
    status, freshness_desc = freshness(rule.market, last_ts, now=pd.Timestamp.now())

    advice = {
        "code": rule.symbol,
        "market": rule.market,
        "direction": rule.direction,
        "direction_cn": DIRECTION_CN.get(rule.direction, rule.direction),
        "target_position_pct": rule.target_position_pct,
        "reason": f"{rule.metric} {rule.op} {rule.threshold:g}，当前值 {record.value:.4f}"
                  + (f"（规则: {rule.name}）" if rule.name else ""),
        "data_as_of": str(last_ts) if last_ts is not None else "无数据",
        "data_status": status,  # FRESH / STALE / NO_DATA
        "freshness": freshness_desc,
    }
    positions = _load_positions()
    if not positions.empty and rule.symbol in set(positions.get("symbol", [])):
        row = positions[positions["symbol"] == rule.symbol].iloc[0]
        advice["current_holding"] = {
            "quantity": float(row.get("quantity", 0)),
            "avg_cost": float(row.get("avg_cost", 0)) or None,
        }
    return advice


def render(advice: dict) -> str:
    """渲染为 IM 推送文本。"""
    market_cn = {"cn": "A股", "fund": "场外基金", "crypto": "加密币"}[advice["market"]]
    lines = [
        f"[{market_cn}] {advice['code']}",
        f"建议方向: {advice['direction_cn']}",
        f"目标仓位: {advice['target_position_pct']:.0%}",
        f"信号依据: {advice['reason']}",
        f"数据时点: {advice['data_as_of']}",
    ]
    if advice["data_status"] != "FRESH":
        lines.append(f"⚠️ 数据状态: {advice['data_status']} —— {advice['freshness']}，请先核对数据再操作")
    if "current_holding" in advice:
        h = advice["current_holding"]
        cost = f"，成本 {h['avg_cost']:g}" if h.get("avg_cost") else ""
        lines.append(f"当前持仓: {h['quantity']:g}{cost}")
    lines.append("（个人工具建议，非投资建议，人工确认后执行）")
    return "\n".join(lines)


def push_advices(store: Store, notifier: Notifier, records: list[SignalRecord]) -> list[dict]:
    """批量构造建议单并推送。返回建议单列表（供 CLI 输出）。"""
    advices = []
    for record in records:
        advice = build_advice(store, record)
        advices.append(advice)
        level = "urgent" if record.rule.direction in ("buy", "sell") else "regular"
        notifier.send("量化信号 · 操作建议", render(advice), level=level)
    return advices
