"""K线与指标页。"""

import pandas as pd
import streamlit as st

from quant.data.quality import validate_ohlc
from quant.data.store import Store
from quant.data.universe import normalize
from quant.research.indicators import compute_all

st.title("K线与指标")

symbol = st.text_input("标的代码", value="600519", help="600519 / 000001 / BTC-USDT")
if not symbol:
    st.stop()
try:
    inst = normalize(symbol)
except ValueError as exc:
    st.error(str(exc))
    st.stop()

store = Store.open_readonly()
iid = inst.id
if inst.market == "fund":
    df = store.get_navs(iid)
    store.close()
    if df.empty:
        st.warning("无本地数据，请先 quant fetch")
        st.stop()
    st.line_chart(df.set_index("ts")["nav"])
else:
    df = store.get_bars(iid)
    store.close()
    if df.empty:
        st.warning("无本地数据，请先 quant fetch")
        st.stop()
    clean, _ = validate_ohlc(df)
    close = clean.set_index("ts")["close"]
    ind = compute_all(close)

    st.subheader(f"{inst.symbol} 价格与均线")
    chart_df = clean.set_index("ts")[["close"]].copy()
    for name in ("ma20", "ma60"):
        if ind[name].notna().any():
            chart_df[name.upper()] = ind[name].values
    st.line_chart(chart_df)

    c1, c2, c3 = st.columns(3)
    c1.metric("RSI14", f"{ind['rsi14'].iloc[-1]:.1f}" if ind['rsi14'].notna().iloc[-1] else "N/A")
    c2.metric("MACD 柱", f"{ind['macd']['hist'].iloc[-1]:.3f}")
    pb = ind["boll"]["pct_b"].iloc[-1]
    c3.metric("BOLL %b", f"{pb:.2f}" if pd.notna(pb) else "N/A")

    st.subheader("布林带")
    boll = ind["boll"]
    st.line_chart(pd.DataFrame({"close": close, "upper": boll["upper"],
                                "mid": boll["mid"], "lower": boll["lower"]}).dropna())
