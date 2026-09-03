"""币实盘执行器（M8，AC-10）——testnet 先行，风控不可绕过。

安全分层（plan step 26）:
    - 默认 dry-run: 只构建订单 + 过风控，不触网。
    - --testnet: ccxt set_sandbox_mode(True)，需 TESTNET_API_KEY/SECRET。
    - 实盘: 需环境变量 I_CONFIRM_LIVE_TRADING=1 且 LIVE_API_KEY/SECRET，
      否则直接拒绝。API Key 建议只开启交易权限 + IP 白名单。

风控（Principle 7）: place_order 必先过 RiskChecker，拒绝单写 orders
（status=rejected）+ dq_events 审计，绝不发往交易所。
"""

from __future__ import annotations

import os

from ..data.store import Store
from .risk import DailyState, RiskChecker

ACCOUNT_LIVE = "live"
ACCOUNT_TESTNET = "testnet"


class LiveCryptoExecutor:
    def __init__(self, store: Store, mode: str = "dry-run",
                 risk: RiskChecker | None = None, exchange_id: str = "binance"):
        if mode not in ("dry-run", "testnet", "live"):
            raise ValueError("mode 可选 dry-run / testnet / live")
        self.store = store
        self.mode = mode
        self.risk = risk or RiskChecker()
        self.exchange_id = exchange_id
        self._exchange = None

    # ---------- 交易所连接（懒加载，dry-run 不触网） ----------
    def _connect(self):
        if self._exchange is not None:
            return self._exchange
        # 先校验准入（无 Key/未确认直接拒绝，不 import ccxt）
        if self.mode == "testnet":
            key, secret = os.environ.get("TESTNET_API_KEY"), os.environ.get("TESTNET_SECRET")
            if not (key and secret):
                raise RuntimeError("testnet 需要 TESTNET_API_KEY / TESTNET_SECRET 环境变量")
        elif self.mode == "live":
            if os.environ.get("I_CONFIRM_LIVE_TRADING") != "1":
                raise RuntimeError("实盘需显式确认: I_CONFIRM_LIVE_TRADING=1")
            key, secret = os.environ.get("LIVE_API_KEY"), os.environ.get("LIVE_SECRET")
            if not (key and secret):
                raise RuntimeError("实盘需要 LIVE_API_KEY / LIVE_SECRET 环境变量")
        else:
            raise RuntimeError("dry-run 模式不连接交易所")

        import ccxt

        ex = getattr(ccxt, self.exchange_id)({"apiKey": key, "secret": secret,
                                              "enableRateLimit": True})
        if self.mode == "testnet":
            ex.set_sandbox_mode(True)
        self._exchange = ex
        return ex

    def _daily_state(self, account: str) -> DailyState:
        """从 orders 表汇总当日状态（频次 + 已实现盈亏）。"""
        today = self.store.conn.execute("SELECT current_date").fetchone()[0]
        rows = self.store.conn.execute(
            "SELECT side, amount, status FROM orders "
            "WHERE account = ? AND CAST(created_at AS DATE) = ?", [account, today],
        ).fetchall()
        pnl = 0.0
        for side, amount, status in rows:
            if status != "filled":
                continue
            pnl += amount if side == "sell" else -amount
        return DailyState(orders_today=len(rows), realized_pnl_usdt=pnl)

    # ---------- 下单 ----------
    def place_order(self, symbol: str, side: str, amount_usdt: float,
                    price: float, ref_price: float | None = None,
                    signal_id: int = 0) -> dict:
        """市价单。风控拒绝 -> 审计落库不发交易所；通过 -> 按模式执行。"""
        side = side.lower()
        if side not in ("buy", "sell"):
            raise ValueError("side 可选 buy / sell")
        account = {"dry-run": "dry-run", "testnet": ACCOUNT_TESTNET,
                   "live": ACCOUNT_LIVE}[self.mode]
        state = self._daily_state(ACCOUNT_TESTNET if self.mode != "live" else ACCOUNT_LIVE)

        decision = self.risk.check(side, amount_usdt, price, ref_price, state)
        if not decision.approved:
            self.store.add_order(ts=None, signal_id=signal_id,
                                 instrument_id=f"crypto:{symbol}", market="crypto",
                                 side=side, price=price, qty=0.0, amount=amount_usdt,
                                 account=account, status="rejected", note=decision.reason)
            self.store.add_dq_event("risk_rejected", symbol, decision.reason)
            return {"status": "rejected", "reason": decision.reason}

        order = {"symbol": symbol, "side": side, "type": "market",
                 "amount_usdt": amount_usdt, "price": price, "mode": self.mode}
        if self.mode == "dry-run":
            return {"status": "dry-run", "order": order,
                    "risk": "passed", "note": "未触网，仅风控演练"}

        ex = self._connect()
        qty = amount_usdt / price
        resp = ex.create_order(symbol, "market", side, qty)
        self.store.add_order(ts=None, signal_id=signal_id,
                             instrument_id=f"crypto:{symbol}", market="crypto",
                             side=side, price=price, qty=qty, amount=amount_usdt,
                             account=account, status="filled",
                             note=f"exchange_order_id={resp.get('id')}")
        return {"status": "filled", "exchange_id": resp.get("id"), "qty": qty,
                "order": order}
