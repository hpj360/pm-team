"""universe: 标的规范化。"""

import pytest

from quant.data.universe import normalize


def test_cn_auto_detect():
    inst = normalize("600519")
    assert (inst.market, inst.symbol, inst.currency) == ("cn", "600519.SH", "CNY")


def test_cn_explicit_for_ambiguous():
    # 000001 默认推断为基金；显式 market=cn 归 A股
    assert normalize("000001").market == "fund"
    assert normalize("000001", "cn").symbol == "000001.SZ"


def test_fund():
    inst = normalize("000001", "fund")
    assert (inst.market, inst.symbol) == ("fund", "000001.OF")


def test_crypto():
    inst = normalize("BTC-USDT")
    assert (inst.market, inst.symbol, inst.currency) == ("crypto", "BTC-USDT", "USDT")


def test_invalid():
    with pytest.raises(ValueError):
        normalize("AAPL")  # 无 - 的裸代码无法识别（美股已裁撤）
    with pytest.raises(ValueError):
        normalize("60051")  # 位数不对
    with pytest.raises(ValueError):
        normalize("600519", "us")  # 未知市场
