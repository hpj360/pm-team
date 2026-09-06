"""三市场真实数据源契约测试（plan step 7/15）。

@ pytest.mark.network —— 依赖真实网络与 akshare/ccxt 安装，默认跳过：
    pytest -m network            # 全部契约
    pytest -m network cn         # 只跑 A股
契约内容（源改版时第一时间在此暴露，Principle 1）:
    - schema: 列名/类型/顺序与 BAR_COLS/NAV_COLS 一致
    - 合法性: 过 validate_ohlc/validate_navs 后零非法行
    - 时效性: 最新 bar 在近 N 天内
    - 交叉验证第二源（腾讯行情/天天基金/okx）与主源偏差 < 1%（观察期容差，Critic 保留意见 #1）
"""

from __future__ import annotations

from datetime import date, timedelta

import pytest

from quant.data.quality import (
    cross_check,
    fetch_spot_cn,
    fetch_spot_crypto,
    fetch_spot_fund,
    validate_navs,
    validate_ohlc,
)
from quant.data.sources import get_source

pytestmark = pytest.mark.network

TODAY = date.today()


@pytest.fixture(scope="module")
def cn_bars():
    src = get_source("cn")
    return src._fetch_remote("600519", TODAY - timedelta(days=30), TODAY)


@pytest.fixture(scope="module")
def fund_navs():
    src = get_source("fund")
    return src._fetch_remote("000001", TODAY - timedelta(days=30), TODAY)


@pytest.fixture(scope="module")
def crypto_bars():
    src = get_source("crypto")
    return src._fetch_remote("BTC-USDT", TODAY - timedelta(days=30), TODAY)


# ---------- A股主源（akshare stock_zh_a_hist, qfq） ----------
def test_cn_schema_and_validity(cn_bars):
    assert not cn_bars.empty, "akshare 返回空：接口可能改版"
    assert list(cn_bars.columns) == ["ts", "open", "high", "low", "close", "volume"]
    clean, bad = validate_ohlc(cn_bars)
    assert len(bad) == 0 and len(clean) > 0


def test_cn_freshness(cn_bars):
    last = cn_bars["ts"].max().date()
    assert (TODAY - last).days <= 10, f"最新 bar 过旧: {last}"


# ---------- 基金主源（akshare fund_open_fund_info_em） ----------
def test_fund_schema_and_validity(fund_navs):
    assert not fund_navs.empty, "akshare 返回空：接口可能改版"
    assert list(fund_navs.columns) == ["ts", "nav", "acc_nav"]
    clean, bad = validate_navs(fund_navs)
    assert len(bad) == 0 and len(clean) > 0


def test_fund_freshness(fund_navs):
    last = fund_navs["ts"].max().date()
    # 场外基金 T+1 披露，留 5 天余量（含周末）
    assert (TODAY - last).days <= 7, f"最新净值过旧: {last}"


# ---------- 加密币主源（ccxt 交易所链） ----------
def test_crypto_schema_and_validity(crypto_bars):
    assert not crypto_bars.empty, "ccxt 全链返回空：交易所均不可达或接口改版"
    assert list(crypto_bars.columns) == ["ts", "open", "high", "low", "close", "volume"]
    clean, bad = validate_ohlc(crypto_bars)
    assert len(bad) == 0 and len(clean) > 0


def test_crypto_freshness(crypto_bars):
    last = crypto_bars["ts"].max()
    assert (TODAY - last.date()).days <= 3, f"最新 bar 过旧: {last}"


# ---------- 交叉验证第二源（plan step 15，与主源同等纳入契约） ----------
def _assert_cross(local: float, remote, tolerance: float, src_name: str) -> None:
    """第二源不可达（None）→ skip；可达 → 断言偏差（cross_check 统一容差语义）。"""
    result = cross_check(local, remote)
    if result["skipped"]:
        pytest.skip(f"{src_name} 第二源不可达（返回 None），无法交叉验证")
    assert result["deviation"] < tolerance, (
        f"{src_name} 双源偏差 {result['deviation']:.4f} 超 {tolerance:.0%}: "
        f"本地 {local} vs 远端 {remote}")


def test_cross_check_cn(cn_bars):
    local = float(cn_bars["close"].iloc[-1])
    _assert_cross(local, fetch_spot_cn("600519"), 0.01, "腾讯行情")


def test_cross_check_fund(fund_navs):
    local = float(fund_navs["nav"].iloc[-1])
    # 基金净值按日披露，第二源可能领先/滞后一天，容差放宽
    _assert_cross(local, fetch_spot_fund("000001"), 0.02, "天天基金快照")


def test_cross_check_crypto(crypto_bars):
    local = float(crypto_bars["close"].iloc[-1])
    _assert_cross(local, fetch_spot_crypto("BTC-USDT"), 0.01, "okx")
