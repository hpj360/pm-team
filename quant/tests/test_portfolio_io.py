"""positions.csv 读写。"""

from quant.portfolio.io import Position, load_positions, save_positions


def test_roundtrip(tmp_path):
    path = tmp_path / "positions.csv"
    positions = [
        Position("cn", "600519.SH", 100, 1500.0, "2026-01-05"),
        Position("crypto", "BTC-USDT", 0.5, 60000.0, "2026-02-01"),
    ]
    save_positions(positions, path)
    loaded = load_positions(path)
    assert loaded == positions


def test_missing_file_returns_empty(tmp_path):
    assert load_positions(tmp_path / "nope.csv") == []


def test_bare_symbol_normalized(tmp_path):
    """裸代码 600519 / 000001 应规范化为库内主键形式（cn:600519.SH / fund:000001.OF）。"""
    path = tmp_path / "positions.csv"
    path.write_text("market,symbol,quantity,avg_cost,opened_at\n"
                    "cn,600519,100,1500.0,2026-01-05\n"
                    "fund,000001,200,1.0,2026-01-05\n"
                    "crypto,BTC-USDT,0.5,60000.0,2026-02-01\n")
    loaded = load_positions(path)
    assert [(p.market, p.symbol) for p in loaded] == [
        ("cn", "600519.SH"), ("fund", "000001.OF"), ("crypto", "BTC-USDT")]
