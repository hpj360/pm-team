# Quant Analysis Tool（个人量化分析工具）Implementation Plan

> Status: APPROVED（v1.1：应用用户 4 项裁决后复审通过）
> Source: .claude/artifacts/designs/quant-analysis-tool.md（v1.1, ALIGNED）
> Mode: default（Planner → Architect → Critic 完整循环）+ v1.1 裁决复审轮
> Iterations: 3 / 3（v1 两轮 + v1.1 复审一轮）
> Author: hpj360
> Last updated: 2026-09-03

## Requirements summary

为个人投资者构建覆盖 A股 / 场外基金 / 加密币（美股已裁撤）的量化分析工具：统一数据层、
指标研析、跨市场组合视图、向量化回测、阈值告警（飞书+微信双通道），CLI 优先 +
Streamlit Web 界面，封装为 skill 接入 Hermes agent 生态；交易能力分阶段引入：
M6 模拟盘 → M7 币实盘（ccxt）→ M8 A股爬虫实盘（可选、风险自担）。
数据坚持免费渠道（akshare/天天基金/ccxt 公开行情）。

## Acceptance criteria

继承 spec v1.1 全部 10 条 AC（AC-1 ~ AC-10），核心复述：

- AC-1/2: 三市场 fetch 落库幂等（cn/fund/crypto，无美股）
- AC-3: analyze 指标与 pandas 基准一致（< 1e-8）
- AC-4: 组合统一 CNY 计价视图
- AC-5: 回测无未来函数（shifted 数据断言）
- AC-6: 告警触发 + 飞书 webhook 与微信通道（企微机器人/pushplus）双通道推送
- AC-7: 单数据源故障不影响缓存查询
- AC-8: pytest 全绿 + 回测快照测试
- AC-9: 模拟盘信号→虚拟成交→组合明细三方一致
- AC-10: 币实盘 testnet 下单闭环 + 风控限额拦截超限单

## RALPLAN-DR

### Principles

1. **数据层是生存关键**：免费源（akshare/ccxt）接口漂移是本项目最大死因，适配器薄封装 + 契约测试先行
2. **最小可行**：每个里程碑独立可用、独立可验证，不提前建设 UI/抽象
3. **统一 Instrument 模型**：三市场归一到 `Instrument(market, symbol, currency)`，下游全部面向统一 schema
4. **单写多读**：DuckDB 单文件，写入只经 fetch/paper 成交路径，读取（analyze/backtest/Streamlit）全部只读连接
5. **回测与交易 point-in-time 强制**：信号生成只能用 bar 收盘前数据，框架层面 shift，不靠自觉
6. **实盘风控不可绕过**：risk.py 熔断规则独立于策略代码，任何下单必经风控检查

### Decision drivers

1. **个人维护成本**（决定性）：单人维护 → 架构必须让源漂移"早暴露、易修复"
2. **资金安全**（v1.1 新增，因实盘决策）：模拟盘先行、testnet 先行、风控硬限制、最小权限 API Key
3. **落地速度**：个人工具需要快速见效维持投入动力 → CLI 优先，里程碑短

### Viable options

**Option A: 自研轻量数据层 + vectorbt 回测 + 分层交易执行（quant/ 单包）** ← favored
- 实现思路：akshare(A股+基金) + ccxt(币) 两个薄适配器归一到统一 OHLCV/NAV schema，
  DuckDB+Parquet 存储，typer CLI，vectorbt 回测，Streamlit UI；交易层 Signal→Paper→Live 分阶段
- 改动文件：全新 `quant/` 目录（约 25 个源文件，见 Implementation steps）
- Pros: 三市场统一模型完全可控；交易分层可对照验证；可被 agent 生态调用；零外部框架学习成本；免费
- Cons: 数据源接口维护负担全在自己（用契约测试缓解）；A股实盘爬虫方案先天脆弱（延后+可选）

**Option B: 基于开源量化框架二次开发（zvt / RQAlpha / vnpy）**
- Pros: 数据/回测/交易（vnpy）基建现成，社区分摊源维护
- Cons: 三市场覆盖不齐（基金是二等公民）；vnpy 交易生态偏期货/CTP，币/A股爬虫支持弱；
  框架绑定后定制受限；zvt 社区活跃度低
- **Rejected**：三市场统一 + 币实盘 + A股爬虫混搭恰是各框架拼图短板，胶水成本 ≥ 自研薄层

**Option C: 纯组合现有 stock-analysis skill，不自建系统**
- **Rejected（invalidation rationale）**: 无回测、无统一组合模型、数据不沉淀；Yahoo 对 A股/
  场外基金覆盖差；无交易能力
- 但保留为美股场景兜底（用户偶发美股需求时直接用 skill，不进本工具包）

**v1.1 复审新增裁决——A股实盘通道（因 OQ-1 确认需要交易）**：

**Option A8-1: easytrader 类爬虫方案（浏览器/客户端自动化对接券商）** ← M8 暂定
- Pros: 免费、无资金门槛、覆盖主流券商
- Cons: 券商接口改版即断；模拟键鼠脆弱；有触发券商风控/账号限制的先例
- 裁决：**延后 + 门槛**——模拟盘稳定 ≥ 4 周才启动，且首期只做"信号→操作建议单推送"
  （飞书/微信推送人工确认），不直接自动下单

**Option A8-2: QMT/miniQMT 券商通道** — 需开通量化权限（各券商资金门槛不一，部分较低），
  官方 API 稳定。**Follow-up**：若用户某券商已有低门槛 QMT 权限，M8 优先切此方案（免费但需权限）

### Implementation steps（基于 Option A）

> 全部为新建文件；`quant/` 为本仓库新顶层目录。

**M0 数据地基**
1. 脚手架 — `quant/pyproject.toml`（deps: akshare, ccxt, pandas, duckdb, typer, vectorbt, streamlit, requests; dev: pytest, ruff）、`quant/quant/__init__.py`、`quant/quant/cli.py`（typer app 骨架）
2. Instrument 模型与标的规范化 — `quant/quant/data/universe.py`：`Instrument` dataclass；`normalize("600519")→("cn","600519.SH","CNY")`、`("000001")→("fund","000001.OF","CNY")`、`("BTC-USDT")→("crypto","BTC-USDT","USDT")`（美股分支已裁撤）
3. 存储层 — `quant/quant/data/store.py`：DuckDB 建表 `instruments/bars/navs/fx_rates/orders/accounts`；`upsert_bars()` 幂等（按 instrument+ts 主键去重）；`open_readonly()` 只读连接（供 Streamlit/回测）；`snapshot_parquet()` 冷备导出
4. 数据源适配器 — `quant/quant/data/sources/base.py`（接口 `fetch(symbol, start, end) -> DataFrame[ts,o,h,l,c,v]` + 缓存优先 + 失败降级到缓存并告警日志）；`cn_stock.py`(akshare `stock_zh_a_hist`)、`fund.py`(akshare `fund_open_fund_info_em` 净值)、`crypto.py`(ccxt binance klines)
5. fetch CLI — `quant/cli.py`：`quant fetch --market --symbol --range`；汇率源 — `quant/quant/data/fx.py`（USDT/CNY：binance USDT-CNY 或欧易公开价 + akshare 人民币中间价双源，日频缓存，7 天陈旧容忍并标注时点）
6. 契约测试 — `quant/tests/test_sources_contract.py`：每源固定区间小样本断言（600519 近 10 根日线非空且列齐；000001.OF 净值单调日期；BTC-USDT 24h 波动 < 50% 合理性检查），标记 `@pytest.mark.network` 默认跳过，本地 `pytest -m network` 手动跑

**M1 指标研析**
7. 指标库 — `quant/quant/research/indicators.py`：纯 pandas 实现 ma/rsi/macd/boll，输入只认 bar 收盘后的 DataFrame（point-in-time 原则落地的第一处）
8. 研析报告 — `quant/quant/research/report.py` + `quant/cli.py` `analyze` 子命令：文本报告（趋势/超买超卖/波动分位）；币种可调用现有 `skills/stock-analysis/scripts/analyze_stock.py` 补充 8 维评分
9. 指标单测 — `quant/tests/test_indicators.py`：手造 20 根 K线，指标值与 pandas/manual 基准比对 < 1e-8

**M2 组合管理**
10. 持仓模型 — `quant/quant/portfolio/models.py`（Position/Portfolio dataclass）+ `io.py`（`quant/data/positions.csv` 人肉可编辑，含 market,symbol,quantity,avg_cost,opened_at）
11. 组合视图 — `quant/quant/portfolio/viewer.py` + `quant/cli.py` `portfolio show`：逐持仓市值/盈亏（本币），合计行 CNY 计价（USDT 资产经 fx.py 折算）

**M3 回测**
12. 回测引擎 — `quant/quant/backtest/engine.py`：vectorbt 封装，强制 `signals = indicators.shift(1)`（未来函数防线写死在引擎，不在策略）；输出年化/最大回撤/夏普/交易明细
13. 策略样例 — `quant/quant/backtest/strategies/sma_cross.py`、`momentum.py`、`dca.py`（定投，基金场景）+ `quant/cli.py` `backtest` 子命令
14. 快照测试 — `quant/tests/test_backtest_snapshot.py`：固定历史区间 + 固定参数，指标结果快照比对；未来函数专项断言（shift 前后结果必须不同）

**M4 告警与调度**
15. 告警规则 — `quant/quant/alerts/rules.py`（`quant/data/alerts.yaml`: symbol/metric/op/threshold）+ `checker.py` + `quant/cli.py` `alerts check`
16. 推送（双通道，已确认）— `quant/quant/alerts/notify.py`：通道适配器 `feishu_webhook`（飞书自定义机器人，签名校验）+ `wecom_webhook`（企业微信群机器人，消息可达企微群并经微信插件转达微信）+ `pushplus`（微信公众号模板消息）；`quant/data/notify.yaml` 配置多通道并发发送，任一通道失败不影响其他通道
17. 调度 — `quant/scripts/crontab.example`：fetch(交易日 17:00) / alerts check(币 7×24 每小时)

**M5 Web UI 与 skill 化**
18. Streamlit — `quant/app/Home.py`（组合总览）、`Portfolio.py`（持仓明细+K线 plotly）、`Backtest.py`（回测结果可视化），全部经 `store.open_readonly()`
19. skill 封装 — `quant/skills/quant-analysis/SKILL.md` + 注册进 `skills/registry.json`（provides: quant-fetch, quant-analyze, quant-backtest, quant-portfolio, quant-alerts），对接 Hermes Workbench 跨源调度

**M6 模拟盘（v1.1 新增）**
20. 信号引擎 — `quant/quant/trading/signals.py`：AlertRule/策略 → `Signal` 记录（落 DuckDB `signals` 表），复用 M1 指标，point-in-time 约束同回测
21. 虚拟账户 — `quant/quant/trading/paper.py`：`Account(type=paper)` + 现金/持仓/成交明细；`execute(signal)` 按下一根 bar 开盘价 + 可配置滑点（默认 cn 0.1%、crypto 0.05%）模拟成交；`quant/cli.py` `trade paper` / `portfolio show --paper`
22. 模拟盘测试 — `quant/tests/test_paper.py`：信号→成交→账户状态三方一致性；滑点/手续费计入后收益率与手算基准一致

**M7 币实盘（v1.1 新增，testnet 先行）**
23. 风控熔断 — `quant/quant/trading/risk.py`：下单前强制检查（单笔金额上限、日亏损上限、日下单次数上限、价格偏离最新价 > 5% 拒单）；规则来自 `quant/data/risk.yaml`，检查失败写审计日志并拒单——策略代码无权限绕过
24. 币实盘执行 — `quant/quant/trading/live_crypto.py`：ccxt private API（`quant trade live --dry-run` 仅打印；`--testnet` 走 binance testnet；实盘需 `I_CONFIRM_LIVE_TRADING=1` 环境变量双重确认）；API Key 只读权限起步，升交易权限 + IP 白名单后才可实盘
25. 实盘验证 — `quant/tests/test_live_crypto.py`（`@pytest.mark.network + @pytest.mark.testnet`）：testnet 下单→成交→查询闭环 + 超限单被 risk.py 拦截的断言

**M8 A股实盘（可选，门槛制）**
26. 操作建议单（首期形态）— `quant/quant/trading/advice.py`：A股信号 → 格式化操作建议单（代码/方向/数量/理由）经 notify.py 推飞书+微信，人工确认执行——**不做自动下单**
27. 自动下单（延后决策）— 若模拟盘稳定 ≥ 4 周且用户坚持自动化：评估 easytrader（爬虫，脆弱）或 QMT（需券商权限）；单独出 plan 评审后再实施

### Workspace setup

- 实施前运行 `git status --short` 和 `git branch --show-current`
- 当前 working tree 已有未提交改动（Hermes 能力融合），**不要混入**：建议 quant 工具在
  `git worktree add -b codex/quant-tool ../pm-team-quant-tool` 中实施，或先提交融合改动再开工
- 新目录 `quant/` 为纯增量，与现有改动无文件交集，冲突风险低

### Open questions (留给后续)

- M8 通道选型（easytrader vs QMT）在模拟盘稳定后单独评审，本期不做决定
- 币实盘交易所默认 binance，若用户主用其他所（OKX 等）在 M7 开工前告知即可，ccxt 均支持

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| akshare 接口改版导致数据层断裂（历史高频） | 薄适配器（只依赖少数宽接口）+ 契约测试（M0 就位）+ Parquet 冷备（已采数据永不丢） |
| DuckDB 文件锁：调度进程与 Streamlit 并发冲突 | 单写多读原则：写入只经 fetch/paper 成交 CLI；读取一律 `open_readonly()` |
| 免费源限流/封 IP | 缓存命中优先；增量拉取（只取缺失区间）；源间无依赖可独立重试 |
| 回测过拟合/未来函数 | 引擎层强制 shift(1)；快照测试含未来函数专项断言 |
| 汇率源不可用 | fx 双源 + 日频缓存 + 7 天陈旧容忍 + 明确标注折算汇率时点 |
| 币种 7×24 与 A股日历不一致导致调度复杂 | 调度按任务独立 cron，不做统一交易日历抽象（YAGNI） |
| **实盘 API Key 泄露** | 仅存本地环境变量；只读权限起步；交易权限 + IP 白名单后才实盘；testnet 先行 |
| **风控缺失导致异常损失** | risk.py 独立于策略、下单必经、规则 yaml 化可审计；单笔/日亏/频次三维硬限制 |
| **A股爬虫下单触发券商风控/账号限制** | M8 首期只推操作建议单人工确认；自动下单延后且单独评审 |
| **vectorbt 安装失败**（numba/llvmlite 平台问题） | M0 脚手架阶段先验证 `pip install vectorbt`；降级路径：手写向量化回测（3 个样例策略 pandas 可覆盖） |

## Verification steps

- AC-1/2: `quant fetch --market cn --symbol 600519 --range 1y` 后连跑两次，`duckdb` 查 `SELECT count(*) FROM bars` 不变
- AC-3: `pytest quant/tests/test_indicators.py -v` 全绿（基准比对）
- AC-4: 造含三市场 3 持仓的 positions.csv，`quant portfolio show` 输出总市值 = Σ(持仓市值折 CNY)，手算比对
- AC-5: `pytest quant/tests/test_backtest_snapshot.py::test_no_lookahead` 绿（shift 前后结果差异断言）
- AC-6: 配 threshold 已必触发的 alerts.yaml，`quant alerts check --dry-run` 输出触发 JSON；向飞书测试 webhook 与企微测试机器人各发一条，双通道均收到
- AC-7: 断网跑 `quant analyze 600519`（已有缓存），正常出报告且日志含 source-degraded 告警
- AC-8: `pytest quant/ -m "not network"` 全绿；`pytest -m network` 本地手动全绿
- AC-9: `pytest quant/tests/test_paper.py -v` 全绿（信号/成交/账户三方一致 + 手算基准）
- AC-10: `pytest quant/tests/test_live_crypto.py -m "network and testnet"` 绿（testnet 闭环 + 超限单拒单断言）

## Roadmap（交付路线）

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0（周1-2） | 数据地基：Instrument/store/3 源/fetch/契约测试 | AC-1,2,7 |
| M1（周3-4） | 指标研析：indicators/analyze/复用 stock-analysis（币） | AC-3 |
| M2（周5-6） | 组合管理：positions/统一视图/汇率折算 | AC-4 |
| M3（周7-9） | 回测：vectorbt 引擎/3 策略/快照测试 | AC-5 |
| M4（周10-11） | 告警调度：规则/检查/飞书+微信双通道/cron | AC-6 |
| M5（周12-13） | Streamlit UI + skill 化接入 Workbench | AC-8 收口 |
| M6（周14-16） | 模拟盘：信号引擎/虚拟账户/绩效跟踪 | AC-9 |
| M7（周17-19） | 币实盘：risk.py 风控/ccxt testnet→实盘（最小仓位） | AC-10 |
| M8（门槛制） | A股操作建议单 →（可选）自动下单单独评审 | 建议单触达双通道 |

> M6/M7 之间设硬门槛：模拟盘稳定运行 ≥ 4 周且绩效与回测偏差可解释，才开实盘；
> 实盘首月限定最小仓位（如单笔 ≤ 100 USDT）。

## ADR

- **Decision**: 自研轻量 Python 数据层（quant/ 单包，DuckDB+Parquet，akshare/ccxt 两薄适配器归一 Instrument 模型）+ vectorbt 向量化回测 + typer CLI + Streamlit UI；交易分层推进（Signal→Paper→Live），币实盘走 ccxt 官方 API + 强制风控，A股实盘首期只做人工确认的操作建议单；通知走飞书+微信双通道；全部数据源免费。
- **Drivers**: 个人维护成本 > 资金安全（实盘决策引入）> 落地速度
- **Alternatives considered**: Option B（开源框架）rejected——三市场统一+混搭交易通道是框架拼图短板；Option C（纯 skill 组合）rejected——无回测/交易/数据沉淀，保留为美股兜底；A8-1（easytrader 自动下单）deferred——脆弱且有账号风险，降级为建议单+人工确认；A8-2（QMT）follow-up——需券商权限，模拟盘稳定后评估
- **Why chosen**: 三市场统一模型与分层交易只有自研能保证行为一致可对照；免费源风险靠薄适配器+契约测试+冷备控制在可维护范围；资金安全靠"模拟盘→testnet→最小仓位实盘"渐进 + 不可绕过的风控层。
- **Consequences**: 正——完全可控、agent 生态可集成、数据资产本地沉淀、交易行为可验证；负——akshare 改版需自己跟、无 SLA 数据质量、A股自动化受限（建议单+人工）、单人维护节奏决定项目寿命
- **Follow-ups**: M8 通道选型单独评审（easytrader vs QMT）；模拟盘稳定后评估实盘仓位上限放宽；M5 后评估拆独立仓库

## Review trail

- Planner draft v1: Option A 自研薄数据层，19 步实施，M0-M5 路线
- Architect challenge v1: steelman"个人维护者低估免费源长期维护负担"→ 采纳为契约测试前置 M0；3 条 tension（速度vs健壮性 / DuckDB单文件vs并发 / 向量化vs事件驱动）均有取舍裁决
- Critic verdict v1: REVISE — 契约测试依赖外网会 flaky 未处理；DuckDB 并发读模式未写明；汇率源未指定
- Planner draft v2: 契约测试加 `@pytest.mark.network` 默认跳过；store 增加 `open_readonly()`；fx.py 指定源与缓存策略；"单写多读"升格为 Principle 4
- Critic verdict v2: APPROVED（改进已合入主体），reservations 见下
- **v1.1 裁决复审轮（2026-09-03）**: 用户确认 4 项 OQ（需要交易/砍美股/飞书+微信/免费源）。
  Planner 更新：裁撤 yfinance 源与美股分支；新增 M6/M7/M8 交易层与 Principle 6（风控不可绕过）、
  Decision driver"资金安全"；notify.py 三通道。Architect 挑战：A股自动下单爬虫风险 → 降级为
  建议单+人工确认、M8 门槛制（模拟盘 ≥ 4 周）。Critic 复审：AC-9/10 可验证、交易风险均有
  mitigation、步骤均 cite 文件 → APPROVED
- Final iterations: 3 / 3

## Critic verdict（v1.1 复审，最终）

| 维度 | 状态 | 备注 |
|---|---|---|
| Principle-option consistency | ✓ | 交易分层与 Principle 5/6 一致 |
| Alternative exploration | ✓ | B/C 真候选；A股通道 A8-1/A8-2 独立评审并给 defer 理由 |
| Risk mitigation clarity | ✓ | 10 条 risk 均有落地 mitigation（含新增 4 条交易类风险） |
| AC testability | ✓ | 10 条 AC 二值可验证（新增 AC-9/10 均绑定具体测试） |
| Verification concreteness | ✓ | 每条 AC 有具体命令/测试名 |
| File/line coverage | ✓ | 27 步全部 cite 新建文件路径（greenfield 无既有行号） |

### Verdict: APPROVED

### Reservations（保留意见，即使 APPROVED）

1. **契约测试覆盖不了"数据正确但悄悄变差"**（如源改变复权口径）：快照断言只能抓 schema 断裂，抓不住语义漂移。缓解建议：M1 起每月人工抽查 1 只标的与行情软件比对，写进 `quant/docs/data-quality.md`（运维习惯，非代码）。
2. **vectorbt 依赖较重**（numba/llvmlite），Apple Silicon/Windows 偶发安装问题：若 M3 安装受阻，降级路径是手写向量化回测（pandas 即可覆盖 3 个样例策略），建议在 M0 脚手架阶段先验证 `pip install vectorbt` 可行。
3. **`dca.py`（定投策略）在 vectorbt 中的现金流建模**与真实定投（份额累计）有语义差距，快照测试须用份额累计法手工基准，不能只信框架输出。
4. **（v1.1 新增）企微 webhook 消息到达个人微信依赖用户把企微群消息转发设置配好**：pushplus 通道作为微信直达兜底，但 pushplus 免费额度有限（日 200 条），高频告警场景需在 notify.yaml 里做通道分级（紧急走双通道，常规走单通道）。
5. **（v1.1 新增）模拟盘用"下一根 bar 开盘价+滑点"近似成交，与实盘盘口有偏差**：M7 开实盘后应回填真实成交价与模拟价偏差统计（`paper.py` 增加 `reconcile` 子命令），偏差 > 2 倍滑点时告警——本 plan 未把该统计列为 AC，属 M7 期间自然演进。
