"""标的主数据：三市场 Instrument 模型与代码规范化。

市场:
    cn     - A股（沪/深）
    fund   - 中国场外基金
    crypto - 加密币（交易所现货）

规范化规则（market 未显式给出时）:
    - 6 位数字，60/68/30/43 开头        -> cn（沪市 60/68，深市 30/43 归深）
    - 其余 6 位数字（0/1/5 等）          -> fund
      注意: A股深市 00/002 开头代码与基金代码空间重叠，
      这类标的请显式传 market="cn"。
    - 含 "-" 或 "/" 的交易对（如 BTC-USDT）-> crypto
"""

from __future__ import annotations

from dataclasses import dataclass

CN_PREFIXES = {"60": "SH", "68": "SH", "00": "SZ", "30": "SZ", "43": "SZ", "92": "SZ"}


@dataclass(frozen=True)
class Instrument:
    market: str  # cn / fund / crypto
    symbol: str  # 规范化代码: 600519.SH / 000001.OF / BTC-USDT
    currency: str  # CNY / USDT
    raw: str = ""  # 用户原始输入

    @property
    def id(self) -> str:
        return f"{self.market}:{self.symbol}"


def normalize(code: str, market: str | None = None) -> Instrument:
    """把用户输入的标的代码规范化为 Instrument。

    显式 market 优先；未提供时按上文规则推断。
    """
    code = code.strip().upper()
    if market:
        market = market.strip().lower()
        if market not in ("cn", "fund", "crypto"):
            raise ValueError(f"未知市场: {market}（可选 cn/fund/crypto）")
    elif ("-" in code or "/" in code) and any(c.isalpha() for c in code):
        market = "crypto"
    elif code.isdigit() and len(code) == 6:
        market = "cn" if code[:2] in ("60", "68", "30", "43") else "fund"
    else:
        raise ValueError(f"无法识别标的代码: {code!r}，请显式指定 --market")

    if market == "cn":
        if not (code.isdigit() and len(code) == 6):
            raise ValueError(f"A股代码应为 6 位数字: {code!r}")
        suffix = CN_PREFIXES.get(code[:2], "SZ")
        return Instrument("cn", f"{code}.{suffix}", "CNY", raw=code)
    if market == "fund":
        if not (code.isdigit() and len(code) == 6):
            raise ValueError(f"场外基金代码应为 6 位数字: {code!r}")
        return Instrument("fund", f"{code}.OF", "CNY", raw=code)
    # crypto: 须为含 - 或 / 的交易对（如 BTC-USDT）
    if "-" not in code and "/" not in code:
        raise ValueError(f"加密币代码须为交易对形式（如 BTC-USDT）: {code!r}")
    if not all(c.isalnum() or c in "-/" for c in code):
        raise ValueError(f"非法交易对代码: {code!r}")
    return Instrument("crypto", code, "USDT", raw=code)
