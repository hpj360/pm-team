"""向量化回测引擎（M5）。

vectorbt 在 Python 3.14 不可用（numba/plotly 兼容性，plan Critic reservation #5
预判），按 plan 降级路径手写 pandas 向量化回测——3 个样例策略覆盖足够。

Point-in-time 强制（Principle 6）: 引擎内部对信号 shift(1)，策略侧不允许绕过。
"""

from __future__ import annotations

from dataclasses import dataclass, field

import pandas as pd


@dataclass
class BacktestResult:
    total_return: float
    annualized_return: float
    max_drawdown: float
    sharpe: float
    n_trades: int
    equity: pd.Series = field(repr=False)

    def as_dict(self) -> dict:
        return {
            "total_return": round(self.total_return, 4),
            "annualized_return": round(self.annualized_return, 4),
            "max_drawdown": round(self.max_drawdown, 4),
            "sharpe": round(self.sharpe, 3),
            "n_trades": self.n_trades,
        }


def run_backtest(bars: pd.DataFrame, entries: pd.Series, exits: pd.Series,
                 init_cash: float = 100_000.0, fee: float = 0.0005,
                 slippage: float = 0.0) -> BacktestResult:
    """全仓进出的多空信号回测。

    语义:
        - entries/exits: 布尔 Series（index 与 bars.ts 对齐），True=当日收盘产生信号
        - 成交价: 下一根 bar 开盘价 ×(1+slippage)（信号 shift(1) 强制，无未来函数）
        - 全仓进出，fee 按成交额双边收取
    """
    if bars.empty or len(bars) < 2:
        raise ValueError("bars 至少 2 根")
    df = bars.reset_index(drop=True)
    close = df["close"]
    open_ = df["open"]

    entries = entries.reset_index(drop=True).fillna(False).astype(bool)
    exits = exits.reset_index(drop=True).fillna(False).astype(bool)

    # ---- point-in-time 防线（不可绕过）: 信号次bar开盘生效 ----
    entry_sig = entries.shift(1).fillna(False).astype(bool)   # 第 i 根 bar 的买入在 i 开盘执行
    exit_sig = exits.shift(1).fillna(False).astype(bool)

    # 持仓状态: entry 开仓，exit 平仓（同 bar 同时触发时 exit 优先）
    state, holding, trades = [], False, 0
    for i in range(len(df)):
        if exit_sig.iloc[i]:
            if holding:
                trades += 1
            holding = False
        elif entry_sig.iloc[i]:
            if not holding:
                holding = True
        state.append(holding)
    position = pd.Series(state, dtype=bool)

    # ---- 资金曲线（向量化） ----
    exec_price = open_ * (1 + slippage)
    # 换仓 bar: 持仓变化时按开盘价换仓并扣手续费
    pos_change = position != position.shift(1).fillna(False)
    cash = init_cash
    equity_values = []
    units = 0.0
    for i in range(len(df)):
        if pos_change.iloc[i]:
            if position.iloc[i]:  # 开仓: 全仓买入
                cash -= cash * fee
                units = cash / exec_price.iloc[i]
                cash = 0.0
            else:  # 平仓
                cash = units * exec_price.iloc[i]
                cash -= cash * fee
                units = 0.0
        equity_values.append(cash + units * close.iloc[i])
    equity = pd.Series(equity_values, index=df["ts"])

    returns = equity.pct_change().dropna()
    n = len(df)
    total_return = equity.iloc[-1] / init_cash - 1
    annualized = (1 + total_return) ** (252 / max(n, 1)) - 1
    max_dd = ((equity / equity.cummax()) - 1).min()
    sharpe = (returns.mean() / returns.std() * (252 ** 0.5)) if returns.std() > 0 else 0.0

    return BacktestResult(float(total_return), float(annualized), float(max_dd),
                          float(sharpe), trades, equity)


def buy_and_hold(bars: pd.DataFrame, init_cash: float = 100_000.0,
                 fee: float = 0.0005) -> BacktestResult:
    """买入持有基线（P1）: 首根开盘全仓买入持有到末根，供策略对比。

    与 run_backtest 同语义（次 bar 开盘成交、双边 fee 只在买入侧收一次）。
    """
    if bars.empty or len(bars) < 2:
        raise ValueError("bars 至少 2 根")
    df = bars.reset_index(drop=True)
    cash = init_cash * (1 - fee)
    units = cash / float(df["open"].iloc[0])
    equity = units * df["close"]
    equity.index = df["ts"]

    returns = equity.pct_change().dropna()
    n = len(df)
    total_return = equity.iloc[-1] / init_cash - 1
    annualized = (1 + total_return) ** (252 / max(n, 1)) - 1
    max_dd = ((equity / equity.cummax()) - 1).min()
    sharpe = (returns.mean() / returns.std() * (252 ** 0.5)) if returns.std() > 0 else 0.0
    return BacktestResult(float(total_return), float(annualized), float(max_dd),
                          float(sharpe), 1, equity)


def run_dca(bars: pd.DataFrame, amount_per_period: float = 1000.0,
            period: str = "M", fee: float = 0.0) -> dict:
    """定投回测（基金场景）: 份额累计法——权益 = 累计份额 × 最新净值。

    与 run_backtest 语义不同（现金流持续投入），单独实现（plan reservation #6）。
    注意: to_period 频率别名用 'M'（'ME' 是 date-offset 别名，Period 不接受）。
    """
    df = bars.reset_index(drop=True)
    ts = pd.to_datetime(df["ts"])
    if ts.dt.tz is not None:
        ts = ts.dt.tz_localize(None)  # to_period 会丢 tz，显式去除消除告警
    df["period"] = ts.dt.to_period(period)
    first_bars = df.groupby("period").first()  # 每期首根 bar 开盘价买入
    shares = 0.0
    invested = 0.0
    for _, row in first_bars.iterrows():
        price = row["open"]
        buy = amount_per_period
        shares += buy * (1 - fee) / price
        invested += buy
    last_price = df["close"].iloc[-1]
    final_value = shares * last_price
    return {
        "invested": round(invested, 2),
        "final_value": round(final_value, 2),
        "total_return": round(final_value / invested - 1, 4) if invested else None,
        "shares": round(shares, 6),
        "n_periods": len(first_bars),
    }
