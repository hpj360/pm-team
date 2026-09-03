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
