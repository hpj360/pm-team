"""模拟盘：Signal -> 虚拟成交（M7，AC-9）。

成交规则（plan step 24）:
    - 买入/卖出按「信号时点之后的第一根 bar 开盘价」成交（point-in-time，
      与回测引擎次 bar 开盘语义一致）；若信号依据的是最新 bar（尚无次 bar），
      降级用最新收盘价并在 note 标注。
    - 滑点: cn 0.1% / crypto 0.05% / fund 0（按净值申赎）。
    - 买入金额默认 = 规则 target_position_pct × 初始资金；卖出清仓该标的。
    - 账户快照落 accounts 表，订单落 orders 表——信号/成交/账户三方可对账。
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import datetime

import pandas as pd

from ..data.store import Store
from ..signals.engine import SignalRecord

SLIPPAGE = {"cn": 0.001, "fund": 0.0, "crypto": 0.0005}
ACCOUNT = "paper"


@dataclass
class PaperAccount:
    cash: float
    positions: dict[str, dict] = field(default_factory=dict)  # {iid: {qty, avg_cost}}

    @classmethod
    def load(cls, store: Store, init_cash: float = 100_000.0) -> PaperAccount:
        row = store.latest_account(ACCOUNT)
        if row is None:
            return cls(cash=init_cash)
        return cls(cash=float(row[1]), positions=json.loads(row[2]))

    def save(self, store: Store, ts=None) -> None:
        store.save_account(ACCOUNT, self.cash, self.positions, ts=ts)


def _fill_price(store: Store, market: str, iid: str, signal_ts: datetime) -> tuple[float, str]:
    """返回 (成交价, 备注)。优先信号后第一根 bar 开盘价，无次 bar 用最新收盘价。"""
    if market == "fund":
        navs = store.get_navs(iid)
        after = navs[navs["ts"] > pd.Timestamp(signal_ts)]
        if not after.empty:
            return float(after["nav"].iloc[0]), "次净值申赎"
        return float(navs["nav"].iloc[-1]), "无次净值，按最新净值"
    bars = store.get_bars(iid)
    after = bars[bars["ts"] > pd.Timestamp(signal_ts)]
    if not after.empty:
        return float(after["open"].iloc[0]), "次bar开盘"
    return float(bars["close"].iloc[-1]), "无次bar，按最新收盘"


def _realized_vol(store: Store, market: str, iid: str, window: int = 20) -> float | None:
    """近 window 根 bar 的年化实现波动率（无数据返回 None）。"""
    df = store.get_navs(iid) if market == "fund" else store.get_bars(iid)
    if df is None or len(df) < window + 1:
        return None
    price = df["nav"] if market == "fund" else df["close"]
    ret = price.pct_change().dropna().iloc[-window:]
    return float(ret.std() * (252 ** 0.5))


def default_amount(store: Store, account: PaperAccount, record: SignalRecord,
                   ref_equity: float = 100_000.0) -> float:
    """买入金额: fixed → target_position_pct × ref_equity；
    vol_target → clip(target_vol/实现波动, 0, 1) × ref_equity（波动率目标仓位）。"""
    rule = record.rule
    if rule.sizing != "vol_target":
        return round(rule.target_position_pct * ref_equity, 2)
    iid = f"{rule.market}:{rule.symbol}"
    vol = _realized_vol(store, rule.market, iid)
    if vol is None or vol <= 0:
        return round(rule.target_position_pct * ref_equity, 2)  # 数据不足回退 fixed
    weight = min(max(rule.target_vol / vol, 0.0), 1.0)
    return round(weight * ref_equity, 2)


def execute(store: Store, account: PaperAccount, record: SignalRecord,
            amount: float | None = None) -> dict:
    """按信号执行虚拟成交，写 orders + 账户快照，返回成交结果 dict。"""
    rule = record.rule
    iid = f"{rule.market}:{rule.symbol}"
    price, note = _fill_price(store, rule.market, iid, record.ts)
    slip = SLIPPAGE[rule.market]
    pos = account.positions.get(iid, {"qty": 0.0, "avg_cost": 0.0})

    if rule.direction == "buy":
        # 默认金额: fixed → target_position_pct × 100k；vol_target → 波动率目标权重 × 100k
        amount = amount if amount is not None else default_amount(store, account, record)
        fill = price * (1 + slip)
        qty = amount / fill
        if amount > account.cash:
            result = {"status": "skipped", "reason": "现金不足", "instrument": iid}
            store.add_dq_event("paper_skip", rule.symbol, f"买入现金不足: 需 {amount} 有 {account.cash}")
            return result
        account.cash -= amount
        new_qty = pos["qty"] + qty
        pos["avg_cost"] = ((pos["avg_cost"] * pos["qty"]) + amount) / new_qty if new_qty else 0.0
        pos["qty"] = new_qty
        side, order_amount = "buy", amount
    elif rule.direction == "sell":
        if pos["qty"] <= 0:
            result = {"status": "skipped", "reason": "无持仓", "instrument": iid}
            store.add_dq_event("paper_skip", rule.symbol, "卖出信号但无持仓")
            return result
        fill = price * (1 - slip)
        qty = pos["qty"]
        order_amount = qty * fill
        account.cash += order_amount
        pos = {"qty": 0.0, "avg_cost": 0.0}
        side = "sell"
    else:  # info 信号不交易
        return {"status": "ignored", "reason": "info 信号不触发交易", "instrument": iid}

    account.positions[iid] = pos
    order_id = store.add_order(
        ts=record.ts, signal_id=record.id, instrument_id=iid, market=rule.market,
        side=side, price=fill, qty=qty, amount=order_amount, fee=0.0,
        slippage=slip, account=ACCOUNT, note=note,
    )
    account.save(store)
    return {
        "status": "filled", "order_id": order_id, "signal_id": record.id,
        "instrument": iid, "side": side, "price": round(fill, 6),
        "qty": qty, "amount": round(order_amount, 2), "note": note,
        "cash_after": round(account.cash, 2),
    }
