"""quant CLI 入口（typer）。

命令: fetch / analyze / signals / dq
"""

from __future__ import annotations

import json
import logging
from datetime import date, timedelta
from pathlib import Path
from typing import Optional

import pandas as pd
import typer

from .data.quality import (cross_check, detect_gaps, fetch_spot_cn, fetch_spot_crypto,
                           fetch_spot_fund, freshness, validate_ohlc)
from .data.sources import get_source
from .data.store import DATA_DIR, Store
from .data.universe import normalize
from .research.indicators import compute_all
from .signals.advice import build_advice, push_advices, render
from .signals.engine import SignalEngine, load_rules
from .alerts.notify import Notifier

app = typer.Typer(add_completion=False, help="个人量化分析工具：A股/场外基金/加密币")
logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

RANGE_DAYS = {"30d": 30, "90d": 90, "6m": 183, "1y": 365, "3y": 1095, "5y": 1825}


def _parse_range(range_str: str) -> date:
    days = RANGE_DAYS.get(range_str)
    if days is None:
        raise typer.BadParameter(f"range 可选 {sorted(RANGE_DAYS)}")
    return date.today() - timedelta(days=days)


def _open_store(read_only: bool = False) -> Store:
    return Store(read_only=read_only)


# ---------- fetch ----------
@app.command()
def fetch(
    symbol: str = typer.Argument(..., help="标的代码，如 600519 / 000001 / BTC-USDT"),
    market: Optional[str] = typer.Option(None, help="cn / fund / crypto（推断歧义时必填）"),
    range_str: str = typer.Option("1y", "--range", help="回看区间: " + "/".join(RANGE_DAYS)),
    backfill: bool = typer.Option(False, "--backfill", help="检测并回补区间内缺口（M3）"),
):
    """拉取行情/净值并落库（增量 + 幂等）。"""
    inst = normalize(symbol, market)
    start = _parse_range(range_str)
    store = _open_store()
    try:
        store.upsert_instrument(inst.id, inst.market, inst.symbol, inst.currency)
        source = get_source(inst.market)
        df = source.fetch(store, inst.id, inst.raw, start, date.today())
        result = {
            "instrument": inst.id, "rows": len(df),
            "last": str(df["ts"].iloc[-1]) if len(df) else None,
        }
        if backfill:
            from .data.calendar import load_calendar

            try:
                cal = load_calendar()
                itd = lambda d: d in set(cal)  # noqa: E731
            except Exception:
                itd = None
            result["backfilled"] = source.backfill(store, inst.id, inst.raw,
                                                   date.today(), is_trading_day=itd)
        typer.echo(json.dumps(result, ensure_ascii=False))
    finally:
        store.close()


# ---------- analyze ----------
@app.command()
def analyze(
    symbol: str,
    market: Optional[str] = None,
):
    """单标的研析：指标概览 + 数据时点戳（陈旧数据显著标注）。"""
    inst = normalize(symbol, market)
    store = _open_store()
    try:
        if inst.market == "fund":
            navs = store.get_navs(inst.id)
            if navs.empty:
                typer.echo("无本地数据，请先 quant fetch")
                raise typer.Exit(1)
            nav = navs.set_index("ts")["nav"]
            drawdown = (nav / nav.cummax() - 1).iloc[-1]
            status, desc = freshness("fund", navs["ts"].iloc[-1])
            typer.echo(f"{inst.symbol} 最新净值 {nav.iloc[-1]:.4f}（{navs['ts'].iloc[-1]}）")
            typer.echo(f"当前回撤 {drawdown:.2%}")
            typer.echo(f"数据状态: {status} | {desc}")
            return
        bars = store.get_bars(inst.id)
        if bars.empty:
            typer.echo("无本地数据，请先 quant fetch")
            raise typer.Exit(1)
        clean, _bad = validate_ohlc(bars)
        close = clean.set_index("ts")["close"]
        ind = compute_all(close)
        status, desc = freshness(inst.market, clean["ts"].iloc[-1])
        last = close.iloc[-1]
        typer.echo(f"{inst.symbol} 收盘 {last:.2f}（{clean['ts'].iloc[-1]}）")
        ma20 = ind["ma20"].iloc[-1]
        ma60 = ind["ma60"].iloc[-1]
        typer.echo(f"MA20 {ma20:.2f}" if pd.notna(ma20) else "MA20 数据不足")
        typer.echo(f"MA60 {ma60:.2f}" if pd.notna(ma60) else "MA60 数据不足")
        rsi14 = ind["rsi14"].iloc[-1]
        hist = ind["macd"]["hist"].iloc[-1]
        typer.echo(f"RSI14 {rsi14:.1f} | MACD柱 {hist:.3f}"
                   if pd.notna(rsi14) else "RSI14 数据不足")
        if pd.notna(ind["boll"]["pct_b"].iloc[-1]):
            typer.echo(f"BOLL %b {ind['boll']['pct_b'].iloc[-1]:.2f}")
        typer.echo(f"数据状态: {status} | {desc}")
        if status != "FRESH":
            typer.echo("⚠️ STALE: 数据陈旧，结论仅供参考，请先 quant fetch 更新")
    finally:
        store.close()


# ---------- signals ----------
@app.command("signals")
def signals_cmd(
    push: bool = typer.Option(False, "--push", help="触发后经飞书/微信推送建议单"),
    dry_run: bool = typer.Option(False, "--dry-run", help="只打印不落库不推送"),
):
    """求值信号规则，产出操作建议单（cooldown 窗口内同标的同向不重复）。"""
    rules = load_rules()
    if not rules:
        typer.echo(f"无规则，请配置 {DATA_DIR / 'signals.yaml'}")
        raise typer.Exit(1)
    store = _open_store()
    try:
        engine = SignalEngine(store, rules)
        if dry_run:
            # dry-run: 只求值展示，不落库（临时用只读视角——直接跑 check 但回滚信号）
            before = set(store.get_signals(limit=1000).get("id", []))
        fired = engine.check()
        if dry_run:
            store.conn.execute(
                f"DELETE FROM signals WHERE id NOT IN ({','.join(map(str, before)) or '0'})"
            )
        if not fired:
            typer.echo("无新信号")
            return
        if push and not dry_run:
            notifier = Notifier()
            advices = push_advices(store, notifier, fired)
        else:
            advices = [build_advice(store, r) for r in fired]
            for a, r in zip(advices, fired):
                typer.echo(render(a))
        typer.echo(json.dumps(advices, ensure_ascii=False, default=str))
    finally:
        store.close()


# ---------- dq ----------
@app.command("dq")
def dq_report(
    symbol: Optional[str] = typer.Option(None, help="只查单个标的（如 600519）"),
    cross: bool = typer.Option(False, "--cross", help="最新价双源交叉验证（需网络）"),
):
    """数据质量报告：新鲜度 + 完整性缺口 + 近期 dq 事件 (+ 可选交叉验证)。"""
    store = _open_store(read_only=False)
    try:
        from .data.calendar import load_calendar

        try:
            cal = load_calendar()
            itd = lambda d: d in set(cal)  # noqa: E731
        except Exception:
            itd = None

        instruments = store.conn.execute("SELECT id, market, symbol FROM instruments").fetchall()
        report = []
        for iid, market, sym in instruments:
            if symbol and sym != normalize(symbol).symbol:
                continue
            if market == "fund":
                df, last = store.get_navs(iid), store.last_nav_ts(iid)
            else:
                df, last = store.get_bars(iid), store.last_bar_ts(iid)
            status, desc = freshness(market, last, is_trading_day=itd)
            item = {"instrument": iid, "last_data": str(last), "status": status,
                    "freshness": desc}
            if not df.empty and market != "crypto":
                gaps = detect_gaps(market, df, df["ts"].min().date(),
                                   df["ts"].max().date(), is_trading_day=itd)
                item["gaps"] = len(gaps)
                item["gap_dates"] = [str(g) for g in gaps[:10]]
                # 覆盖率: 实际行数 / 应有交易日数
                expected = len(detect_gaps(market, df, df["ts"].min().date(),
                                           df["ts"].max().date(), is_trading_day=itd)) + len(df)
                item["coverage_pct"] = round(100 * len(df) / expected, 2) if expected else None
            report.append(item)

        cross_results = []
        if cross:
            fetchers = {"cn": fetch_spot_cn, "fund": fetch_spot_fund, "crypto": fetch_spot_crypto}
            for iid, market, sym in instruments:
                if symbol and sym != normalize(symbol).symbol:
                    continue
                if market == "fund":
                    local = store.get_navs(iid)
                    local_price = float(local["nav"].iloc[-1]) if not local.empty else None
                    raw = sym.split(".")[0]
                else:
                    bars = store.get_bars(iid)
                    local_price = float(bars["close"].iloc[-1]) if not bars.empty else None
                    raw = sym
                try:
                    remote_price = fetchers[market](raw)
                except Exception as exc:
                    cross_results.append({"instrument": iid, "error": f"{type(exc).__name__}: {exc}"})
                    continue
                result = cross_check(local_price, remote_price)
                result.update({"instrument": iid, "local": local_price, "remote": remote_price})
                if result["deviated"]:
                    store.add_dq_event("cross_check_deviation", sym,
                                       f"本地 {local_price} vs 第二源 {remote_price}，"
                                       f"偏差 {result['deviation']:.4f} 超容差")
                cross_results.append(result)

        events = store.get_dq_events(limit=20)
        out = {"freshness": report, "recent_dq_events":
               events.to_dict(orient="records") if not events.empty else []}
        if cross:
            out["cross_check"] = cross_results
        typer.echo(json.dumps(out, ensure_ascii=False, default=str))
    finally:
        store.close()


# ---------- portfolio ----------
@app.command("portfolio")
def portfolio_show(
    positions_path: Optional[str] = typer.Option(None, help="positions.csv 路径（默认 data/）"),
):
    """组合视图：跨市场持仓统一 CNY 计价（含数据时点戳）。"""
    from .data.fx import get_usdt_cny
    from .portfolio.io import load_positions
    from .portfolio.viewer import build_view, render

    positions = load_positions(Path(positions_path) if positions_path else None)
    if not positions:
        typer.echo("无持仓，请编辑 data/positions.csv（market,symbol,quantity,avg_cost,opened_at）")
        raise typer.Exit(1)
    store = _open_store()
    try:
        usdt_cny, fx_desc = 1.0, ""
        if any(p.market == "crypto" for p in positions):
            usdt_cny, fx_desc = get_usdt_cny(store)
        view = build_view(store, positions, usdt_cny, fx_desc)
        typer.echo(render(view))
    finally:
        store.close()


# ---------- backtest ----------
@app.command()
def backtest(
    symbol: str,
    strategy: str = typer.Option("sma_cross", help="sma_cross / momentum / dca"),
    market: Optional[str] = None,
    range_str: str = typer.Option("1y", "--range"),
):
    """向量化回测（信号次 bar 开盘成交，引擎强制无未来函数）。"""
    from .backtest import engine as bt_engine
    from .backtest import strategies as bt_strategies

    inst = normalize(symbol, market)
    store = _open_store()
    try:
        if inst.market == "fund":
            bars = store.get_navs(inst.id)
            if bars.empty:
                typer.echo("无本地数据，请先 quant fetch")
                raise typer.Exit(1)
            bars = bars.rename(columns={"nav": "close"})
            # 净值序列无开高低量，用净值近似，补齐字段让 validate_ohlc 通过
            bars["open"] = bars["close"]
            bars["high"] = bars["close"]
            bars["low"] = bars["close"]
            bars["volume"] = 0.0
        else:
            bars = store.get_bars(inst.id)
            if bars.empty:
                typer.echo("无本地数据，请先 quant fetch")
                raise typer.Exit(1)
        # 回测输入先过质量校验（脏数据不进回测）
        clean, _ = validate_ohlc(bars)
        if strategy == "dca":
            result = bt_engine.run_dca(clean, **bt_strategies.DCA_DEFAULTS)
        else:
            if strategy not in ("sma_cross", "momentum"):
                raise typer.BadParameter("strategy 可选 sma_cross / momentum / dca")
            entries, exits = getattr(bt_strategies, strategy)(clean)
            result = bt_engine.run_backtest(clean, entries, exits).as_dict()
        typer.echo(json.dumps({"instrument": inst.id, "strategy": strategy,
                               **result}, ensure_ascii=False, default=str))
    finally:
        store.close()


if __name__ == "__main__":
    app()
