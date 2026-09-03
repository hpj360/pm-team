"""回测页。"""

import streamlit as st

from quant.backtest import engine as bt_engine
from quant.backtest import strategies as bt_strategies
from quant.data.quality import validate_ohlc
from quant.data.store import Store
from quant.data.universe import normalize

st.title("策略回测")

symbol = st.text_input("标的代码", value="600519")
strategy = st.selectbox("策略", ["sma_cross", "momentum", "dca"])
init_cash = st.number_input("初始资金", value=100_000.0, min_value=1000.0, step=10_000.0)

if not symbol or not st.button("运行回测"):
    st.stop()

try:
    inst = normalize(symbol)
except ValueError as exc:
    st.error(str(exc))
    st.stop()

store = Store.open_readonly()
if inst.market == "fund":
    bars = store.get_navs(inst.id).rename(columns={"nav": "close"})
    bars["open"] = bars["close"]
    bars["high"] = bars["close"]
    bars["low"] = bars["close"]
    bars["volume"] = 0.0
else:
    bars = store.get_bars(inst.id)
store.close()

if bars.empty:
    st.warning("无本地数据，请先 quant fetch")
    st.stop()
clean, _ = validate_ohlc(bars)

if strategy == "dca":
    result = bt_engine.run_dca(clean, **bt_strategies.DCA_DEFAULTS)
    st.json({"instrument": inst.id, "strategy": strategy, **result})
else:
    entries, exits = getattr(bt_strategies, strategy)(clean)
    r = bt_engine.run_backtest(clean, entries, exits, init_cash=init_cash)
    c1, c2, c3, c4 = st.columns(4)
    c1.metric("总收益", f"{r.total_return:.2%}")
    c2.metric("年化", f"{r.annualized_return:.2%}")
    c3.metric("最大回撤", f"{r.max_drawdown:.2%}")
    c4.metric("夏普", f"{r.sharpe:.2f}")
    st.subheader("资金曲线")
    st.line_chart(r.equity)
    st.caption(f"交易次数 {r.n_trades} · 信号次 bar 开盘成交（无未来函数）")
