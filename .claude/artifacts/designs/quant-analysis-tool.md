# Quant Analysis Tool（个人量化分析工具）Spec

> Status: DRAFT（用户跳过访谈，关键假设显式列出，待确认后升 ALIGNED）
> Author: hpj360
> Last updated: 2026-09-03

## Background

用户（个人投资者，技术背景强，已有 Hermes/Workbench agent 生态与 pm-team 仓库）需要一个
**个人使用**的量化分析工具，覆盖三类异构资产：**股票、基金、虚拟币**。本仓库刚从 Hermes
同步了 `stock-analysis` skill v6.2（Yahoo Finance，美股+加密币，含组合/自选/告警/热点扫描），
可作为部分能力复用起点。

## 需求深度分析

### 表层需求 → 深层需求

| 表层说法 | 深层真实需求 | 依据 |
|---|---|---|
| "量化分析" | 数据聚合 + 指标计算 + 策略验证，而非自动交易 | 个人自用、无合规需求表述、无风控/资金安全诉求出现 |
| "股票、基金、虚拟币" | 跨市场**统一数据模型**是第一难点：A股 T+1、基金净值 T+1、币 7×24；计价货币混合 CNY/USD | 三类资产数据频率、源、语义完全异构 |
| "个人使用" | 单用户、本地部署可接受、成本敏感（免费数据源优先）、无高并发 | 未提及多用户/SaaS |
| "工具" | 既要日常轻量使用（盯盘/研析/告警），也要低频深度使用（回测/归因） | 使用频率双峰 → 交付形态需 CLI + Web 双入口 |
| 隐含需求 | 与已有 agent 生态协同：能被 Hermes Workbench 调度、能作为 skill 被 agent 调用 | 用户刚完成 Hermes 能力融合，生态意图明显 |

### 核心需求（按优先级）

- **R1 数据层**：三市场行情/净值/K线获取、缓存与本地存储（最高优先——一切的地基）
- **R2 研析**：技术指标（MA/RSI/MACD/BOLL）、基本面、基金持仓/费率/经理、币种资金费率
- **R3 组合管理**：跨市场持仓统一视图、成本、盈亏、汇率折算
- **R4 策略回测**：向量化回测 + 三资产策略样例 + 绩效报告
- **R5 监控告警**：价格/指标/净值阈值提醒，推送至 IM
- **R6 交付形态**：CLI 优先，Web UI（Streamlit）次之，agent skill 化接口收尾

## In scope

- A 股 / 美股 / 中国场外基金与 ETF / 主流加密币（交易所公开行情）的数据获取、存储、查询
- 技术指标计算与单标的研析报告（CLI 输出）
- 跨市场持仓录入与组合盈亏视图
- 向量化回测引擎（vectorbt 集成）与 3 个示例策略
- 阈值告警 + 定时任务 + IM 推送（Telegram / 飞书 webhook 二选一）
- Streamlit Web 界面（组合视图 + K线 + 回测结果展示）
- 封装为 skill 供 Hermes Workbench / agent 调用

## Out of scope（v1 明确不做）

- **实盘/模拟盘自动交易**（下单、撤单、仓位管理）——高风险，且用户未表达该诉求；架构上预留 DataFeed→Signal 接口即可
- 期货、期权、杠杆合约行情
- Tick 级/L2 深度数据（免费源不可得，个人研析用日线/小时线足够）
- 多用户、鉴权、SaaS 化
- 高频策略（Tick 级回测、低延迟执行）
- 移动 App

## Assumptions（用户未确认，显式列出）

1. **核心目标是"研究决策辅助 + 策略回测"，不是自动交易**。若实际想要实盘交易，架构需重新评审（交易通道、风控熔断、API Key 托管）。
2. 市场范围：A股（akshare/tushare）+ 美股（yfinance）+ 中国场外基金/ETF（akshare/天天基金）+ 主流币现货（ccxt/币安公开行情）。
3. 项目落位：本仓库新顶层目录 `quant/`（Python monorepo 包），成熟后可拆独立仓库。
4. 数据频率：日线为主，币加小时线；无实时推送需求（轮询分钟级延迟可接受）。
5. 免费数据源优先，接受限流（需本地缓存 + 多源冗余设计）。
6. 计价：本位币 CNY，USD 资产按即期汇率折算展示。
7. 运行环境：个人电脑/NAS 上的 Docker 或 venv，单实例。

## Solution（sketch）

```text
quant/
├── pyproject.toml
├── quant/                    # Python 包
│   ├── data/                 # R1: 数据接入层
│   │   ├── sources/          #   akshare_cn / yfinance_us / akshare_fund / ccxt_crypto
│   │   ├── store.py          #   DuckDB + Parquet 读写，统一 OHLCV/NAV schema
│   │   └── universe.py       #   标的主数据（代码规范化：600519.SH / AAPL / 000001.OF / BTC-USDT）
│   ├── research/             # R2: 指标与研析
│   ├── portfolio/            # R3: 持仓与组合
│   ├── backtest/             # R4: vectorbt 封装 + 策略样例
│   ├── alerts/               # R5: 阈值规则 + 调度 + 推送
│   └── cli.py                # R6: typer CLI 入口
├── app/                      # Streamlit Web UI
├── skills/quant-analysis/    # agent skill 封装（对接 Hermes Workbench）
└── tests/
```

关键设计决策（详见 plan）：
- **统一标的模型**：`Instrument(market, symbol, currency)` 为一等公民，三市场数据源都归一到它
- **存储选 DuckDB + Parquet** 而非数据库服务：分析型负载、零运维、pandas/polars 原生互操作
- **回测选 vectorbt**（向量化）：个人规模速度快、代码量小；事件驱动复杂策略留到确有需要再引入
- **数据源适配器模式 + 本地缓存**：免费源限流是最大工程风险，缓存命中优先、多源冗余降级

## Edge cases & risks

| Category | Notes |
|---|---|
| Boundary conditions | 基金净值 T+1 更新（当日查看为空）；币 7×24 与 A股交易日历冲突；停牌/退市标的；新币无历史数据 |
| Failure modes | akshare/yfinance 免费源限流、接口改版（历史高危区）；汇率源不可用；DuckDB 文件锁（多进程并发写） |
| Risks | 回测未来函数/幸存者偏差（指标计算须只用 bar 收盘后数据）；免费数据质量无 SLA；策略过拟合 |
| Mitigation | 数据源适配器隔离改版影响 + 多源冗余；回测框架强制 point-in-time 语义；缓存层保证已采数据不丢 |

## Acceptance criteria

- AC-1 `quant fetch --market cn --symbol 600519 --range 1y` 将日线数据落库，重复执行幂等（不产生重复行）
- AC-2 `quant fetch --market crypto --symbol BTC-USDT` 与 `--market fund --symbol 000001` 同样落库成功
- AC-3 `quant analyze 600519` 输出含 MA20/60、RSI14、MACD、BOLL(20,2) 的研析报告，指标值与 pandas 计算基准一致（误差 < 1e-8）
- AC-4 `quant portfolio show` 对含三市场资产的组合输出统一 CNY 计价的总市值与个股盈亏
- AC-5 `quant backtest --strategy sma_cross --market cn --symbol 600519` 输出年化收益/最大回撤/夏普，回测期间无未来函数（用 shifted 数据断言）
- AC-6 `quant alerts check` 对已触发规则输出 JSON，并能通过 webhook 推送到配置的 IM
- AC-7 三市场数据源任一单源失败时，已有缓存数据查询不受影响
- AC-8 单元测试覆盖 data/store 与 research 指标计算，`pytest` 全绿；回测结果有快照测试

## Open questions

1. **实盘交易是否在未来 6 个月内需要？**（影响是否预留 broker/exchange 交易适配层）——需用户确认
2. 美股是否为必需市场？（stock-analysis skill 已覆盖美股+币研析，若非必需可砍掉 yfinance 源降低维护面）
3. 通知渠道偏好：Telegram 还是飞书 webhook？（pm-team 已有 feishu-service，个人工具直接复用会太重）
4. 基金数据源：akshare 免费接口稳定性一般，是否愿意为 tushare token（~200元/年）付费以提高 A股/基金数据可靠性？

## Core entities (ontology)

| Entity | Type | Key fields | Relationship |
|---|---|---|---|
| Instrument | 主数据 | market(cn/us/fund/crypto), symbol, currency | 1:N Quote/NAV |
| Bar | 行情 | instrument_id, ts, o/h/l/c/v | 属于 Instrument |
| Nav | 基金净值 | instrument_id, ts, nav, acc_nav | 属于 Instrument(fund) |
| Position | 持仓 | instrument_id, quantity, avg_cost | 属于 Portfolio |
| Portfolio | 组合 | name, base_currency | 1:N Position |
| Signal | 信号 | instrument_id, ts, rule_id, direction | 由 Rule 产生 |
| AlertRule | 告警规则 | instrument_id, metric, op, threshold | 1:N Signal |
| BacktestRun | 回测 | strategy_id, params, metrics_json | 引用 Instrument 历史数据 |

## Interview metadata

- Mode: default（用户 Wave 1 跳过访谈，EARLY_EXIT_BY_USER）
- Waves: 1
- Final ambiguity: 64% → 假设显式化后约 35%（剩余集中在 Open questions 1-4）
- Status: EARLY_EXIT_BY_USER

### Clarity breakdown

| Dimension | Score | Weight | Weighted |
|---|---|---|---|
| Goal | 0.4 | 0.40 | 0.16 |
| Scope | 0.3 | 0.25 | 0.075 |
| AC | 0.3 | 0.25 | 0.075 |
| Context | 0.5 | 0.10 | 0.05 |
