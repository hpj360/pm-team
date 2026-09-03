---
name: quant-analysis
version: 1.0.0
description: 个人量化分析工具：A股/场外基金/加密币的行情获取、指标研析、信号告警、组合管理、策略回测与模拟盘/币实盘执行
provides: quant-fetch,quant-analyze,quant-signals,quant-backtest,quant-portfolio,quant-dq,quant-trade
---

# Quant Analysis

个人量化分析 CLI（`quant/` 目录，Python 包），覆盖 A股 / 场外基金 / 加密币。

## 前置

```bash
pip install -e quant[full]   # akshare + ccxt 数据源
```

## 能力与命令

### 1. 行情/净值获取（quant-fetch）
```bash
quant fetch 600519 --range 1y            # A股（akshare，前复权 qfq）
quant fetch 000001 --market fund         # 场外基金净值
quant fetch BTC-USDT --market crypto     # 加密币日线（ccxt binance）
quant fetch 600519 --backfill            # 检测并回补数据缺口
```

### 2. 指标研析（quant-analyze）
```bash
quant analyze 600519   # MA/RSI/MACD/BOLL + 数据时点戳 + STALE 标注
```

### 3. 信号与操作建议单（quant-signals）
```bash
quant signals --dry-run   # 求值 data/signals.yaml 规则（不落库）
quant signals --push      # 触发后经飞书/企微/pushplus 推送建议单
```
建议单内容：代码 / 方向 / 目标仓位比例 / 信号依据 / 数据时点戳 / STALE 状态。
规则声明式配置在 `quant/data/signals.yaml`（symbol/metric/op/threshold/cooldown_h）。

### 4. 策略回测（quant-backtest）
```bash
quant backtest 600519 --strategy sma_cross   # 或 momentum / dca
```
输出年化 / 最大回撤 / 夏普 / 交易次数。信号次 bar 开盘成交，引擎强制无未来函数。

### 5. 组合管理（quant-portfolio）
```bash
quant portfolio   # 读 quant/data/positions.csv，统一 CNY 计价
```

### 6. 数据质量（quant-dq）
```bash
quant dq           # 新鲜度 + 完整性缺口 + 覆盖率 + 近期事件
quant dq --cross   # 双源交叉验证最新价（腾讯行情/天天基金/OKX）
```

### 7. 交易执行（quant-trade，M7/M8）
```bash
quant trade paper                       # 信号 -> 模拟盘成交（次bar开盘+滑点，账户快照落库）
quant trade paper --amount 5000         # 指定买入金额
quant portfolio --paper                 # 查看模拟盘持仓（统一 CNY 计价）
quant trade live-crypto BTC-USDT --side buy --amount-usdt 80 --price 50000
                                        # 币实盘：默认 dry-run 不触网，必过风控
quant trade live-crypto BTC-USDT ... --mode testnet   # 需 TESTNET_API_KEY/SECRET
quant trade live-crypto BTC-USDT ... --mode live      # 需 I_CONFIRM_LIVE_TRADING=1
```
风控规则（`quant/data/risk.yaml`）: 单笔 ≤100 USDT / 日亏熔断 50 / 日频次 10 / 价格偏离 >5% 拒单。
拒绝单写 orders(status=rejected) + dq_events 审计，绝不发往交易所。

## 数据质量三防线

1. 合法性：OHLC 校验，非法行隔离进 dq_events
2. 时效性：分市场新鲜度阈值（cn >1 交易日 / fund >1 自然日 / crypto >2h → STALE）
3. 准确性：缺口检测+回补、双源交叉验证（偏差 >0.5% 告警）

## 注意事项

- 所有输出携带数据时点戳；STALE 数据显著标注，结论需人工核对
- 免费数据源（akshare/天天基金/binance）可能限流或改版，失败自动降级本地缓存
- 操作建议单仅为个人研究辅助，非投资建议

## 实现细节

- 存储：DuckDB（单写多读）+ Parquet 冷备
- Web UI：`streamlit run quant/app/Home.py`
- 完整方案：`.claude/artifacts/plans/quant-analysis-tool.md`
