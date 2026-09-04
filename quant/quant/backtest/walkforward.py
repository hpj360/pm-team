"""Walk-forward 滚动验证（P2）——防参数过拟合。

流程（每折）:
    [---- train ----][-- test --]
                    ↑ 用 train 选最优参数 → 在紧邻的 test（样本外）评估
滚动推进，拼接所有 test 段得样本外（OOS）绩效。

过拟合判据: train 中位收益 与 OOS 中位收益 的比值——比值越大，参数越不可信。
sma_cross 参数网格: fast ∈ {5,10,20}, slow ∈ {30,60,120}（fast < slow 才有效）。
"""

from __future__ import annotations

from dataclasses import dataclass, field

import pandas as pd

from . import strategies as bt_strategies
from .engine import run_backtest

SMA_GRIDS: list[dict] = [
    {"fast": f, "slow": s}
    for f in (5, 10, 20) for s in (30, 60, 120) if f < s
]


@dataclass
class WalkForwardResult:
    n_folds: int
    oos_total_return: float
    oos_median_fold_return: float
    is_median_fold_return: float
    overfit_ratio: float  # IS/OOS 中位收益比，>3 视为严重过拟合
    best_params_freq: dict = field(default_factory=dict)
    folds: list[dict] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "n_folds": self.n_folds,
            "oos_total_return": round(self.oos_total_return, 4),
            "oos_median_fold_return": round(self.oos_median_fold_return, 4),
            "is_median_fold_return": round(self.is_median_fold_return, 4),
            "overfit_ratio": round(self.overfit_ratio, 2),
            "best_params_freq": self.best_params_freq,
        }


def _evaluate(bars: pd.DataFrame, params: dict) -> float:
    entries, exits = bt_strategies.sma_cross(bars, **params)
    return run_backtest(bars, entries, exits).total_return


def walk_forward(bars: pd.DataFrame, train_n: int = 120, test_n: int = 20,
                 grids: list[dict] | None = None) -> WalkForwardResult:
    """滚动 train/test 验证 sma_cross 参数（每折在 train 选参、test 样本外评估）。"""
    grids = grids or SMA_GRIDS
    df = bars.reset_index(drop=True)
    if len(df) < train_n + test_n:
        raise ValueError(f"数据不足: 需 ≥{train_n + test_n} 根，实有 {len(df)}")

    folds: list[dict] = []
    best_params_freq: dict[str, int] = {}
    start = 0
    while start + train_n + test_n <= len(df):
        train = df.iloc[start:start + train_n]
        test = df.iloc[start + train_n:start + train_n + test_n]

        # train 段选参（每折独立重选，模拟真实滚动使用）
        scores = [(p, _evaluate(train, p)) for p in grids]
        best_params, best_is = max(scores, key=lambda x: x[1])
        oos = _evaluate(test, best_params)

        key = f"fast={best_params['fast']},slow={best_params['slow']}"
        best_params_freq[key] = best_params_freq.get(key, 0) + 1
        folds.append({"start": int(start), "params": best_params,
                      "is_return": round(best_is, 4), "oos_return": round(oos, 4)})
        start += test_n  # 滚动步长 = test 段长

    is_median = float(pd.Series([f["is_return"] for f in folds]).median())
    oos_median = float(pd.Series([f["oos_return"] for f in folds]).median())
    # OOS 总收益: 各折收益复利拼接
    oos_total = 1.0
    for f in folds:
        oos_total *= 1 + f["oos_return"]
    oos_total -= 1
    overfit = (is_median / oos_median) if oos_median > 0 else float("inf")

    return WalkForwardResult(
        n_folds=len(folds), oos_total_return=float(oos_total),
        oos_median_fold_return=oos_median, is_median_fold_return=is_median,
        overfit_ratio=overfit, best_params_freq=best_params_freq, folds=folds,
    )
