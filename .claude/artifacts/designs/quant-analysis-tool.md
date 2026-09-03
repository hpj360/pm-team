# Quant Analysis Tool（个人量化分析工具）Spec

> Status: ALIGNED（2026-09-03 用户确认全部 4 项开放问题，假设已转为决策）
> Author: hpj360
> Last updated: 2026-09-03（v1.1：纳入实盘决策 / 砍美股 / 飞书+微信 / 免费源）

## Background

用户（个人投资者，技术背景强，已有 Hermes/Workbench agent 生态与 pm-team 仓库）需要一个
**个人使用**的量化分析工具，覆盖三类异构资产：**A股、场外基金、虚拟币**（美股已确认排除）。
本仓库已从 Hermes 同步 `stock-analysis` skill v6.2（Yahoo Finance，美股+加密币研析），
作为美股场景的兜底能力（不在本工具包内重复建设）。

## 需求深度分析

### 表层需求 → 深层需求

| 表层说法 | 深层真实需求 | 依据 |
|---|---|---|
| "量化分析" | 数据聚合 + 指标计算 + 策略验证 + **信号驱动的交易执行**（用户确认需要交易） | OQ-1 确认"需要"，但分阶段推进：模拟盘先行 |
| "股票、基金、虚拟币" | 跨市场**统一数据模型**是第一难点：A股 T+1、基金净值 T+1、币 7×24；计价混合 CNY/USDT | 三类资产数据频率、源、语义完全异构 |
| "个人使用" | 单用户、本地部署可接受、成本敏感（**确认只用免费渠道/爬虫**） | OQ-4 确认不付费买 tushare |
| "工具" | 既要日常轻量使用（盯盘/研析/告警），也要低频深度使用（回测/归因/交易） | 使用频率双峰 → CLI + Web 双入口 |
| 隐含需求 | 与已有 agent 生态协同：能被 Hermes Workbench 调度、能作为 skill 被 agent 调用 | 用户刚完成 Hermes 能力融合，生态意图明显 |

### 核心需求（按优先级）

- **R1 数据层**：A股/基金/币行情与净值获取、缓存与本地存储（最高优先——一切的地基）
- **R2 研析**：技术指标（MA/RSI/MACD/BOLL）、基金持仓/费率/经理、币种资金费率
- **R3 组合管理**：跨市场持仓统一视图、成本、盈亏、汇率折算
- **R4 策略回测**：向量化回测 + 三资产策略样例 + 绩效报告
- **R5 监控告警**：价格/指标/净值阈值提醒，推送至**飞书 + 微信**（已确认双通道）
- **R6 交付形态**：CLI 优先，Web UI（Streamlit）次之，agent skill 化接口收尾
- **R7 交易执行（已确认需要，分阶段）**：M6 模拟盘（虚拟账户+信号引擎）→ M7 币实盘
  （ccxt 官方 API + 风控熔断）→ M8 A股实盘（爬虫方案，高风险可选，视模拟盘稳定性决定）

## In scope

- A 股 / 中国场外基金与 ETF / 主流加密币（交易所公开行情）的数据获取、存储、查询
- 技术指标计算与单标的研析报告（CLI 输出）
- 跨市场持仓录入与组合盈亏视图
- 向量化回测引擎（vectorbt 集成）与 3 个示例策略
- 阈值告警 + 定时任务 + 推送：飞书 webhook + 微信（企业微信群机器人 webhook / pushplus，均免费）
- Streamlit Web 界面（组合视图 + K线 + 回测结果展示）
- 封装为 skill 供 Hermes Workbench / agent 调用
- 模拟盘：虚拟账户、信号引擎、模拟成交与绩效跟踪
- 币种实盘下单：ccxt 私有 API（testnet 先行 → 实盘），含风控熔断（单笔/日亏上限、异常停机）

## Out of scope

- **A股实盘自动交易（M8 之前）**：爬虫下单方案（easytrader 类）脆弱且有账号限制风险，
  仅在模拟盘稳定运行 ≥ 4 周后作为可选项启动，风险自担
- **美股**（已确认排除）：本工具包不集成 yfinance；美股研析直接用已有 stock-analysis skill
- 期货、期权、杠杆合约行情与交易
- Tick 级/L2 深度数据（免费源不可得，个人研析用日线/小时线足够）
- 多用户、鉴权、SaaS 化
- 高频策略（Tick 级回测、低延迟执行）
- 移动 App
- 场外基金自动申赎（无免费 API，工具产出操作建议单，人工执行）

## Decisions（原 Open questions，2026-09-03 用户裁决）

| # | 问题 | 裁决 | 架构影响 |
|---|---|---|---|
| 1 | 实盘交易是否需要？ | **需要** | 路线图新增 M6 模拟盘 / M7 币实盘 / M8 A股爬虫实盘（可选）；数据层预留 Signal→Order 链路 |
| 2 | 美股是否必需？ | **不需要** | 砍掉 yfinance 数据源；美股研析由 stock-analysis skill 兜底 |
| 3 | 通知渠道？ | **飞书 + 微信** | notify.py 实现 feishu_webhook / wecom_webhook / pushplus 三通道，可配置多通道并发 |
| 4 | 数据源付费？ | **只用免费渠道/爬虫** | akshare（本身即爬虫聚合）+ 天天基金 + ccxt 公开行情；多源冗余 + 本地缓存缓解免费源风险 |

## Assumptions（仍有效的显式假设）

1. 项目落位：本仓库新顶层目录 `quant/`（Python monorepo 包），成熟后可拆独立仓库。
2. 数据频率：日线为主，币加小时线；无实时推送需求（轮询分钟级延迟可接受）。
3. 免费数据源限流可接受（本地缓存 + 增量拉取 + 多源冗余设计）。
4. 计价：本位币 CNY，USDT 计价资产按 USDT/CNY 汇率折算展示。
5. 运行环境：个人电脑/NAS 上的 Docker 或 venv，单实例。
6. 币实盘 API Key 仅存本地环境变量；权限从只读起步，升交易权限前先过 testnet。

## Solution（sketch）

```text
quant/
├── pyproject.toml
├── quant/                    # Python 包
│   ├── data/                 # R1: 数据接入层
│   │   ├── sources/          #   akshare_cn / akshare_fund / ccxt_crypto（美股源已裁撤）
│   │   ├── store.py          #   DuckDB + Parquet 读写，统一 OHLCV/NAV schema
│   │   └── universe.py       #   标的主数据（600519.SH / 000001.OF / BTC-USDT）
│   ├── research/             # R2: 指标与研析
│   ├── portfolio/            # R3: 持仓与组合
│   ├── backtest/             # R4: vectorbt 封装 + 策略样例
│   ├── alerts/               # R5: 阈值规则 + 调度 + 飞书/微信推送
│   ├── trading/              # R7: 交易执行（M6+）
│   │   ├── paper.py          #   模拟盘：虚拟账户 + 模拟成交
│   │   ├── live_crypto.py    #   币实盘：ccxt private API（testnet 先行）
│   │   └── risk.py           #   风控熔断：单笔上限/日亏上限/异常停机
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
- **交易分层推进**：Signal（回测/研析产出）→ PaperAccount（模拟成交）→ LiveCrypto（真实下单），
  同一套 Signal 语义贯穿，实盘与模拟盘行为可对照验证

## Edge cases & risks

| Category | Notes |
|---|---|
| Boundary conditions | 基金净值 T+1 更新（当日查看为空）；币 7×24 与 A股交易日历冲突；停牌/退市标的；新币无历史数据 |
| Failure modes | akshare 免费接口限流、改版（历史高危区）；汇率源不可用；DuckDB 文件锁（多进程并发写）；交易所 API 限频/停机维护 |
| Risks | 回测未来函数/幸存者偏差；免费数据质量无 SLA；策略过拟合；**实盘 API Key 泄露；A股爬虫下单触发券商风控/账号限制；风控缺失导致异常损失** |
| Mitigation | 数据源适配器隔离改版影响 + 多源冗余；回测强制 point-in-time；API Key 仅存环境变量、只读权限起步、testnet 先行、下单 IP 白名单；风控熔断硬编码于 risk.py 不可被策略绕过；A股实盘延后至模拟盘稳定 ≥ 4 周 |

## Acceptance criteria

- AC-1 `quant fetch --market cn --symbol 600519 --range 1y` 将日线数据落库，重复执行幂等（不产生重复行）
- AC-2 `quant fetch --market crypto --symbol BTC-USDT` 与 `--market fund --symbol 000001` 同样落库成功
- AC-3 `quant analyze 600519` 输出含 MA20/60、RSI14、MACD、BOLL(20,2) 的研析报告，指标值与 pandas 计算基准一致（误差 < 1e-8）
- AC-4 `quant portfolio show` 对含三市场资产的组合输出统一 CNY 计价的总市值与个股盈亏
- AC-5 `quant backtest --strategy sma_cross --market cn --symbol 600519` 输出年化收益/最大回撤/夏普，回测期间无未来函数（用 shifted 数据断言）
- AC-6 `quant alerts check` 对已触发规则输出 JSON，并能通过**飞书 webhook 与微信通道（企微机器人/pushplus）各推送一条**，两通道均可达
- AC-7 三市场数据源任一单源失败时，已有缓存数据查询不受影响
- AC-8 单元测试覆盖 data/store 与 research 指标计算，`pytest` 全绿；回测结果有快照测试
- AC-9 `quant trade paper`：信号触发后虚拟账户按次笔成交记录（含滑点假设），`portfolio show --paper` 与虚拟账户明细一致
- AC-10 币实盘在 exchange testnet 下完成一次市价单闭环（下单→成交→查询→风控限额拦截第二笔超限单）

## Core entities (ontology)

| Entity | Type | Key fields | Relationship |
|---|---|---|---|
| Instrument | 主数据 | market(cn/fund/crypto), symbol, currency | 1:N Quote/NAV |
| Bar | 行情 | instrument_id, ts, o/h/l/c/v | 属于 Instrument |
| Nav | 基金净值 | instrument_id, ts, nav, acc_nav | 属于 Instrument(fund) |
| Position | 持仓 | instrument_id, quantity, avg_cost | 属于 Portfolio |
| Portfolio | 组合 | name, base_currency | 1:N Position |
| Signal | 信号 | instrument_id, ts, rule_id, direction | 由 Rule/Strategy 产生 |
| AlertRule | 告警规则 | instrument_id, metric, op, threshold | 1:N Signal |
| BacktestRun | 回测 | strategy_id, params, metrics_json | 引用 Instrument 历史数据 |
| Account | 交易账户 | type(paper/live), exchange, balance | 1:N Order |
| Order | 委托/成交 | account_id, instrument_id, side, qty, price, status | 由 Signal 驱动，属于 Account |

## Interview metadata

- Mode: default（Wave 1 用户跳过访谈 EARLY_EXIT_BY_USER；2026-09-03 补充裁决轮确认全部 4 项 OQ）
- Waves: 1 + 1 裁决轮
- Final ambiguity: ~10%（剩余不确定性仅为实施细节，无方向性分歧）
- Status: PASSED（补充裁决轮）
