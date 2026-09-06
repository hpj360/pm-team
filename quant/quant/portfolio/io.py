"""持仓模型与 positions.csv 读写（人肉可编辑）。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pandas as pd

from ..data.store import DATA_DIR

POSITIONS_PATH = DATA_DIR / "positions.csv"
COLUMNS = ["market", "symbol", "quantity", "avg_cost", "opened_at"]


@dataclass
class Position:
    market: str
    symbol: str
    quantity: float
    avg_cost: float
    opened_at: str = ""


def _normalize_symbol(market: str, symbol: str) -> tuple[str, str]:
    """持仓 symbol/market 规范化（用户可写 600519 裸代码，与信号规则一致）。"""
    from ..data.universe import normalize

    try:
        inst = normalize(symbol, market)
        return inst.market, inst.symbol
    except ValueError:
        return market.strip().lower(), symbol.strip().upper()


def load_positions(path: Path | None = None) -> list[Position]:
    path = Path(path) if path else POSITIONS_PATH
    if not path.exists():
        return []
    df = pd.read_csv(path)
    out = []
    for _, r in df.iterrows():
        market, symbol = _normalize_symbol(str(r["market"]), str(r["symbol"]))
        out.append(Position(market, symbol, float(r["quantity"]),
                            float(r["avg_cost"]), str(r.get("opened_at", ""))))
    return out


def save_positions(positions: list[Position], path: Path | None = None) -> None:
    path = Path(path) if path else POSITIONS_PATH
    df = pd.DataFrame([p.__dict__ for p in positions], columns=COLUMNS)
    path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(path, index=False)
