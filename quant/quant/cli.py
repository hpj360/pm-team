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

from .data.quality import freshness, validate_ohlc
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
):
    """拉取行情/净值并落库（增量 + 幂等）。"""
    inst = normalize(symbol, market)
    start = _parse_range(range_str)
    store = _open_store()
    try:
        store.upsert_instrument(inst.id, inst.market, inst.symbol, inst.currency)
        source = get_source(inst.market)
        df = source.fetch(store, inst.id, inst.raw, start, date.today())
        typer.echo(json.dumps({
            "instrument": inst.id, "rows": len(df),
            "last": str(df["ts"].iloc[-1]) if len(df) else None,
        }, ensure_ascii=False))
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
    as_json: bool = typer.Option(False, "--json"),
):
    """数据质量报告：新鲜度 + 近期 dq 事件（缺口检测 M3 补全）。"""
    store = _open_store(read_only=False)
    try:
        instruments = store.conn.execute("SELECT id, market, symbol FROM instruments").fetchall()
        report = []
        for iid, market, sym in instruments:
            if symbol and sym != normalize(symbol).symbol:
                continue
            last = store.last_nav_ts(iid) if market == "fund" else store.last_bar_ts(iid)
            status, desc = freshness(market, last)
            report.append({"instrument": iid, "last_data": str(last), "status": status,
                           "freshness": desc})
        events = store.get_dq_events(limit=20)
        out = {"freshness": report,
               "recent_dq_events": events.to_dict(orient="records") if not events.empty else []}
        typer.echo(json.dumps(out, ensure_ascii=False, default=str))
    finally:
        store.close()


if __name__ == "__main__":
    app()
