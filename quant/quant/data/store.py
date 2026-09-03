"""存储层：DuckDB 单文件 + Parquet 冷备。

原则（见 plan Principle 5「单写多读」）:
    - 写入只经 fetch / 信号引擎 / 模拟盘成交路径
    - 读取（研析/回测/UI）一律 open_readonly()
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Optional

import duckdb
import pandas as pd

# 数据目录: <repo>/quant/data/（可被环境变量覆盖）
DATA_DIR = Path(os.environ.get("QUANT_DATA_DIR", Path(__file__).resolve().parents[2] / "data"))
DEFAULT_DB_PATH = Path(os.environ.get("QUANT_DB", DATA_DIR / "quant.duckdb"))

SCHEMA = """
CREATE TABLE IF NOT EXISTS instruments(
    id VARCHAR PRIMARY KEY, market VARCHAR, symbol VARCHAR, currency VARCHAR);
CREATE TABLE IF NOT EXISTS bars(
    instrument_id VARCHAR, ts TIMESTAMP, open DOUBLE, high DOUBLE, low DOUBLE,
    close DOUBLE, volume DOUBLE, adj_type VARCHAR,
    PRIMARY KEY (instrument_id, ts));
CREATE TABLE IF NOT EXISTS navs(
    instrument_id VARCHAR, ts DATE, nav DOUBLE, acc_nav DOUBLE,
    PRIMARY KEY (instrument_id, ts));
CREATE TABLE IF NOT EXISTS fx_rates(
    ts DATE, pair VARCHAR, rate DOUBLE, source VARCHAR,
    PRIMARY KEY (ts, pair));
CREATE TABLE IF NOT EXISTS signals(
    id BIGINT PRIMARY KEY, ts TIMESTAMP, symbol VARCHAR, market VARCHAR,
    metric VARCHAR, op VARCHAR, threshold DOUBLE, value DOUBLE,
    direction VARCHAR, rule_id VARCHAR, created_at TIMESTAMP);
CREATE TABLE IF NOT EXISTS dq_events(
    ts TIMESTAMP, kind VARCHAR, symbol VARCHAR, detail VARCHAR);
CREATE TABLE IF NOT EXISTS orders(
    id BIGINT PRIMARY KEY, ts TIMESTAMP, signal_id BIGINT, instrument_id VARCHAR,
    market VARCHAR, side VARCHAR, price DOUBLE, qty DOUBLE, amount DOUBLE,
    fee DOUBLE, slippage DOUBLE, account VARCHAR, status VARCHAR, note VARCHAR,
    created_at TIMESTAMP);
CREATE TABLE IF NOT EXISTS accounts(
    ts TIMESTAMP, account VARCHAR, cash DOUBLE, positions_json VARCHAR,
    created_at TIMESTAMP);
"""

BAR_COLS = ["ts", "open", "high", "low", "close", "volume"]
NAV_COLS = ["ts", "nav", "acc_nav"]


class Store:
    def __init__(self, path: Optional[Path] = None, read_only: bool = False):
        self.path = Path(path) if path else DEFAULT_DB_PATH
        if not read_only:
            self.path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = duckdb.connect(str(self.path), read_only=read_only)
        if not read_only:
            for stmt in SCHEMA.strip().split(";\n"):
                if stmt.strip():
                    self.conn.execute(stmt)

    # ---------- 基础 ----------
    def close(self) -> None:
        self.conn.close()

    @staticmethod
    def open_readonly(path: Optional[Path] = None) -> "Store":
        return Store(path, read_only=True)

    # ---------- 标的 ----------
    def upsert_instrument(self, instrument_id: str, market: str, symbol: str, currency: str) -> None:
        self.conn.execute(
            "INSERT OR REPLACE INTO instruments VALUES (?, ?, ?, ?)",
            [instrument_id, market, symbol, currency],
        )

    # ---------- 行情（幂等 upsert，主键 instrument_id+ts 去重） ----------
    def upsert_bars(self, instrument_id: str, df: pd.DataFrame, adj_type: str = "none") -> int:
        if df.empty:
            return 0
        rows = df[BAR_COLS].copy()
        self.conn.register("rows_view", rows)
        self.conn.execute(
            "INSERT OR REPLACE INTO bars "
            "SELECT ?, ts, open, high, low, close, volume, ? FROM rows_view",
            [instrument_id, adj_type],
        )
        self.conn.unregister("rows_view")
        return len(rows)

    def get_bars(self, instrument_id: str, start=None, end=None) -> pd.DataFrame:
        sql = "SELECT ts, open, high, low, close, volume, adj_type FROM bars WHERE instrument_id = ?"
        params: list = [instrument_id]
        if start is not None:
            sql += " AND ts >= ?"
            params.append(start)
        if end is not None:
            sql += " AND ts <= ?"
            params.append(end)
        sql += " ORDER BY ts"
        return self.conn.execute(sql, params).fetchdf()

    def last_bar_ts(self, instrument_id: str):
        row = self.conn.execute(
            "SELECT max(ts) FROM bars WHERE instrument_id = ?", [instrument_id]
        ).fetchone()
        return row[0]

    # ---------- 基金净值 ----------
    def upsert_navs(self, instrument_id: str, df: pd.DataFrame) -> int:
        if df.empty:
            return 0
        rows = df[NAV_COLS].copy()
        self.conn.register("rows_view", rows)
        self.conn.execute(
            "INSERT OR REPLACE INTO navs SELECT ?, ts, nav, acc_nav FROM rows_view",
            [instrument_id],
        )
        self.conn.unregister("rows_view")
        return len(rows)

    def get_navs(self, instrument_id: str, start=None, end=None) -> pd.DataFrame:
        sql = "SELECT ts, nav, acc_nav FROM navs WHERE instrument_id = ?"
        params: list = [instrument_id]
        if start is not None:
            sql += " AND ts >= ?"
            params.append(start)
        if end is not None:
            sql += " AND ts <= ?"
            params.append(end)
        sql += " ORDER BY ts"
        return self.conn.execute(sql, params).fetchdf()

    def last_nav_ts(self, instrument_id: str):
        row = self.conn.execute(
            "SELECT max(ts) FROM navs WHERE instrument_id = ?", [instrument_id]
        ).fetchone()
        return row[0]

    # ---------- 汇率 ----------
    def upsert_fx(self, pair: str, rate: float, source: str, ts=None) -> None:
        ts = ts or pd.Timestamp.now().normalize()
        self.conn.execute(
            "INSERT OR REPLACE INTO fx_rates VALUES (?, ?, ?, ?)",
            [pd.Timestamp(ts).date(), pair, float(rate), source],
        )

    def last_fx(self, pair: str):
        row = self.conn.execute(
            "SELECT ts, rate, source FROM fx_rates WHERE pair = ? ORDER BY ts DESC LIMIT 1",
            [pair],
        ).fetchone()
        return row  # (ts, rate, source) or None

    # ---------- 信号 ----------
    def add_signal(self, ts, symbol: str, market: str, metric: str, op: str,
                   threshold: float, value: float, direction: str, rule_id: str) -> int:
        row = self.conn.execute("SELECT coalesce(max(id), 0) + 1 FROM signals").fetchone()
        new_id = int(row[0])
        self.conn.execute(
            "INSERT INTO signals VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [new_id, pd.Timestamp(ts), symbol, market, metric, op,
             float(threshold), float(value), direction, rule_id, pd.Timestamp.now()],
        )
        return new_id

    def recent_signals(self, symbol: str, direction: str, since) -> pd.DataFrame:
        return self.conn.execute(
            "SELECT * FROM signals WHERE symbol = ? AND direction = ? AND created_at >= ? "
            "ORDER BY created_at DESC",
            [symbol, direction, pd.Timestamp(since)],
        ).fetchdf()

    def get_signals(self, limit: int = 50) -> pd.DataFrame:
        return self.conn.execute(
            "SELECT * FROM signals ORDER BY created_at DESC, id DESC LIMIT ?", [limit]
        ).fetchdf()

    # ---------- 数据质量事件 ----------
    def add_dq_event(self, kind: str, symbol: str, detail: str) -> None:
        self.conn.execute(
            "INSERT INTO dq_events VALUES (?, ?, ?, ?)",
            [pd.Timestamp.now(), kind, symbol, detail],
        )

    def get_dq_events(self, limit: int = 100) -> pd.DataFrame:
        return self.conn.execute(
            "SELECT * FROM dq_events ORDER BY ts DESC LIMIT ?", [limit]
        ).fetchdf()

    # ---------- 订单与账户（M7 模拟盘 / M8 实盘共用） ----------
    def add_order(self, ts, signal_id, instrument_id: str, market: str, side: str,
                  price: float, qty: float, amount: float, fee: float = 0.0,
                  slippage: float = 0.0, account: str = "paper", status: str = "filled",
                  note: str = "") -> int:
        row = self.conn.execute("SELECT coalesce(max(id), 0) + 1 FROM orders").fetchone()
        new_id = int(row[0])
        ts = ts if ts is not None else pd.Timestamp.now()
        self.conn.execute(
            "INSERT INTO orders VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [new_id, pd.Timestamp(ts), signal_id, instrument_id, market, side,
             float(price), float(qty), float(amount), float(fee), float(slippage),
             account, status, note, pd.Timestamp.now()],
        )
        return new_id

    def get_orders(self, account: str | None = None, limit: int = 100) -> pd.DataFrame:
        if account:
            return self.conn.execute(
                "SELECT * FROM orders WHERE account = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                [account, limit],
            ).fetchdf()
        return self.conn.execute(
            "SELECT * FROM orders ORDER BY created_at DESC, id DESC LIMIT ?", [limit]
        ).fetchdf()

    def save_account(self, account: str, cash: float, positions: dict,
                     ts=None) -> None:
        import json

        ts = ts or pd.Timestamp.now()
        self.conn.execute(
            "INSERT INTO accounts VALUES (?, ?, ?, ?, ?)",
            [pd.Timestamp(ts), account, float(cash),
             json.dumps(positions, ensure_ascii=False), pd.Timestamp.now()],
        )

    def latest_account(self, account: str = "paper"):
        row = self.conn.execute(
            "SELECT ts, cash, positions_json FROM accounts WHERE account = ? "
            "ORDER BY ts DESC LIMIT 1", [account],
        ).fetchone()
        return row  # (ts, cash, positions_json) or None

    # ---------- 冷备 ----------
    def snapshot_parquet(self, out_dir: Path, tables: tuple[str, ...] = ("bars", "navs", "fx_rates")) -> list[Path]:
        """全量导出 Parquet 冷备——免费源改版后本地数据资产不丢。"""
        out_dir = Path(out_dir)
        out_dir.mkdir(parents=True, exist_ok=True)
        written = []
        for table in tables:
            df = self.conn.execute(f"SELECT * FROM {table}").fetchdf()
            if df.empty:
                continue
            path = out_dir / f"{table}.parquet"
            df.to_parquet(path, index=False)
            written.append(path)
        return written
