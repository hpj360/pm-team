"""Streamlit 入口: streamlit run app/Home.py"""

import pandas as pd
import streamlit as st

from quant.data.quality import detect_gaps, freshness
from quant.data.store import Store

st.set_page_config(page_title="Quant", page_icon="📈", layout="wide")
st.title("量化分析 · 组合总览与数据质量")

try:
    store = Store.open_readonly()
except Exception:  # noqa: BLE001 duckdb 锁/权限等任何不可用原因都降级为友好提示
    st.error("数据库不可用（可能正被写入进程占用），请稍后重试")
    st.stop()

instruments = store.conn.execute("SELECT id, market, symbol FROM instruments").fetchall()
if not instruments:
    st.info("暂无数据，请先运行 quant fetch")
    st.stop()

# ---- 持仓组合（positions.csv） ----
from quant.portfolio.io import load_positions
from quant.portfolio.viewer import build_view

positions = load_positions()
if positions:
    fx_rate = st.session_state.get("usdt_cny", 1.0)
    if any(p.market == "crypto" for p in positions):
        fx_rate = st.number_input("USDT/CNY 汇率", value=7.0, min_value=0.01, step=0.01)
        st.session_state["usdt_cny"] = fx_rate
    view = build_view(store, positions, usdt_cny=fx_rate)
    rows = [{
        "标的": p.position.symbol, "市场": p.position.market, "数量": p.position.quantity,
        "现价": p.last_price, "市值(CNY)": round(p.value_cny, 2),
        "盈亏(CNY)": round(p.pnl_cny, 2), "数据时点": p.data_as_of, "状态": p.data_status,
    } for p in view.positions]
    st.subheader(f"组合市值: {view.total_cny:,.2f} CNY")
    st.dataframe(pd.DataFrame(rows), use_container_width=True)
else:
    st.info("无持仓：编辑 quant/data/positions.csv 后刷新")

# ---- 数据质量面板 ----
st.subheader("数据质量")
dq_rows = []
for iid, market, sym in instruments:
    df = store.get_navs(iid) if market == "fund" else store.get_bars(iid)
    last = store.last_nav_ts(iid) if market == "fund" else store.last_bar_ts(iid)
    status, desc = freshness(market, last)
    gaps = 0
    if not df.empty and market != "crypto":
        gaps = len(detect_gaps(market, df, df["ts"].min().date(), df["ts"].max().date()))
    dq_rows.append({"标的": sym, "市场": market, "最后数据": str(last),
                    "新鲜度": status, "缺口数": gaps, "说明": desc})
st.dataframe(pd.DataFrame(dq_rows), use_container_width=True)

events = store.get_dq_events(limit=20)
if not events.empty:
    with st.expander("近期数据质量事件"):
        st.dataframe(events, use_container_width=True)
store.close()
