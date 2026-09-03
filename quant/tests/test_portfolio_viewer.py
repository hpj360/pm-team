"""组合视图：统一 CNY 计价 + 时点戳 + STALE（AC-4 / AC-13）。"""

from datetime import datetime

import pytest

from conftest import make_bars, make_navs

from quant.portfolio.io import Position
from quant.portfolio.viewer import build_view, render


def _seed(store):
    # 600519 收盘 1500；基金净值 2.0；BTC 60000
    store.upsert_bars("cn:600519.SH", make_bars(5, base=1500.0))
    store.upsert_navs("fund:000001.OF", make_navs([2.0, 2.0]))
    store.upsert_bars("crypto:BTC-USDT", make_bars(5, base=60000.0))


def test_total_cny_with_fx_conversion(store):
    """AC-4: 三市场组合统一 CNY 计价，与手算一致。"""
    _seed(store)
    positions = [
        Position("cn", "600519.SH", 100, 1400.0),       # 100*1500 = 150000 CNY
        Position("fund", "000001.OF", 10000, 1.8),       # 10000*2.0 = 20000 CNY
        Position("crypto", "BTC-USDT", 0.5, 50000.0),    # 0.5*60000*7.0 = 210000 CNY
    ]
    view = build_view(store, positions, usdt_cny=7.0, fx_desc="测试 7.0")
    assert view.total_cny == 150000 + 20000 + 210000  # 手算基准

    # 逐持仓盈亏
    cn, fund, btc = view.positions
    assert cn.pnl_cny == 100 * (1500 - 1400)              # +10000
    assert fund.pnl_cny == pytest.approx(10000 * (2.0 - 1.8))  # +2000
    assert btc.pnl_cny == pytest.approx(0.5 * (60000 - 50000) * 7.0)  # +35000


def test_missing_data_marked_not_fatal(store):
    _seed(store)
    positions = [Position("cn", "999999.SZ", 100, 10.0)]  # 无数据标的
    view = build_view(store, positions)
    assert view.positions[0].data_status == "NO_DATA"
    assert view.total_cny == 0.0
    assert "请先 quant fetch" in render(view)


def test_render_contains_timestamps(store):
    _seed(store)
    positions = [Position("cn", "600519.SH", 100, 1400.0)]
    text = render(build_view(store, positions))
    assert "合计市值" in text and "[数据 " in text  # 时点戳强制携带
