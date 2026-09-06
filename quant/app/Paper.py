"""模拟盘页: 权益曲线 + 绩效指标 + 成交流水（streamlit run app/Paper.py）。"""

import pandas as pd
import streamlit as st

from quant.data.store import Store
from quant.report import build_paper_report

st.title("模拟盘 · 绩效与流水")

try:
    store = Store.open_readonly()
except Exception:  # noqa: BLE001 duckdb 锁/权限等任何不可用原因都降级为友好提示
    st.error("数据库不可用（可能正被写入进程占用），请稍后重试")
    st.stop()

try:
    rep = build_paper_report(store)
except ValueError as exc:
    st.info(f"{exc}")
    store.close()
    st.stop()

c1, c2, c3, c4 = st.columns(4)
c1.metric("运行天数", f"{rep.days_running} 天")
c2.metric("当前权益", f"{rep.equity_now:,.0f} CNY")
c3.metric("累计收益率", f"{rep.total_return:+.2%}")
c4.metric("最大回撤", f"{rep.max_drawdown:.2%}")

st.subheader("权益曲线")
st.line_chart(rep.equity_curve)
st.caption("各成交时点账户快照估值（现金 + 持仓 × 该时点最新收盘/净值）")

c5, c6, c7 = st.columns(3)
c5.metric("成交笔数", rep.orders_filled)
c6.metric("拒单", rep.orders_rejected)
c7.metric("跳过", rep.skips)

if rep.positions:
    st.subheader("当前持仓")
    st.dataframe(pd.DataFrame(rep.positions), width='stretch')

orders = store.get_orders(account="paper", limit=200)
if orders is not None and not orders.empty:
    with st.expander("成交流水（最近 200 笔）"):
        st.dataframe(orders, width='stretch')

events = store.get_dq_events(limit=100)
if not events.empty and (events["kind"].isin(["paper_skip"])).any():
    with st.expander("跳过记录"):
        st.dataframe(events[events["kind"] == "paper_skip"], width='stretch')

store.close()
