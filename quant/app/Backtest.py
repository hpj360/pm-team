"""回测页。"""

import pandas as pd
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
    bh = bt_engine.buy_and_hold(clean, init_cash=init_cash)
    c1, c2, c3, c4 = st.columns(4)
    c1.metric("总收益", f"{r.total_return:.2%}",
              f"{r.total_return - bh.total_return:+.2%} vs 基线")
    c2.metric("年化", f"{r.annualized_return:.2%}")
    c3.metric("最大回撤", f"{r.max_drawdown:.2%}")
    c4.metric("夏普", f"{r.sharpe:.2f}")
    st.subheader("资金曲线（vs 买入持有基线）")
    st.line_chart(pd.DataFrame({"策略": r.equity, "买入持有": bh.equity}))
    st.caption(f"交易次数 {r.n_trades} · 信号次 bar 开盘成交（无未来函数）")

    # Walk-forward 参数稳健性（P2 防过拟合）
    with st.expander("Walk-forward 参数稳健性（train 选参 / test 样本外）"):
        try:
            from quant.backtest.walkforward import walk_forward

            wf = walk_forward(clean)
            m1, m2, m3 = st.columns(3)
            m1.metric("样本外总收益", f"{wf.oos_total_return:.2%}")
            m2.metric("OOS 中位折收益", f"{wf.oos_median_fold_return:.2%}")
            m3.metric("过拟合比 (IS/OOS)", f"{wf.overfit_ratio:.1f}x"
                      if wf.overfit_ratio != float("inf") else "∞")
            if wf.overfit_ratio > 3 or wf.overfit_ratio == float("inf"):
                st.warning("过拟合比 > 3: 参数在样本外不稳定，谨慎采信回测结果")
            st.dataframe(pd.DataFrame(wf.folds), width='stretch')
            st.caption("各折 train 段最优参数频次: " +
                       ", ".join(f"{k}×{v}" for k, v in wf.best_params_freq.items()))
        except ValueError as exc:
            st.info(str(exc))
