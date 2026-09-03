# Quant Analysis Tool（个人量化分析工具）Implementation Plan

> Status: APPROVED（v1.2：信号/建议单前置 + 数据质量体系 + 多轮评审定稿）
> Source: .claude/artifacts/designs/quant-analysis-tool.md（v1.2, ALIGNED）
> Mode: default + 多轮评审（用户明示要求"多轮评审最终优化方案"）
> Iterations: 5 / 5（v1 两轮 + v1.1 复审一轮 + v1.2 两轮）
> Author: hpj360
> Last updated: 2026-09-03

## Requirements summary

为个人投资者构建覆盖 A股 / 场外基金 / 加密币的量化分析工具（免费数据源）：统一数据层 +
**数据质量体系（完整性/时效性/准确性）** + **信号引擎与操作建议单推送（最高交付优先级，
飞书+微信双通道）** + 跨市场组合视图 + 向量化回测 + Streamlit UI + skill 化；交易执行
分阶段后置：模拟盘 → 币实盘（ccxt + 风控熔断）→ A股自动下单（可选、门槛制、首期建议单人工执行）。

## Acceptance criteria

继承 spec v1.2 全部 14 条 AC，分组复述：

- **数据地基与质量**：AC-1/2（三市场 fetch 幂等）、AC-7（单源故障缓存可用）、
  AC-12（缺口检测→回补归零）、AC-13（STALE 标注+数据时点戳）、AC-14（非法 OHLC 隔离 + 交叉验证告警）
- **信号与推送（前置核心）**：AC-3（指标基准一致）、AC-6（双通道推送可达）、
  AC-11（建议单内容完整 + cooldown 防打扰）
- **组合与回测**：AC-4（统一 CNY 视图）、AC-5（无未来函数）、AC-8（测试全绿+快照）
- **交易（后置）**：AC-9（模拟盘三方一致）、AC-10（testnet 闭环+风控拦截）

## RALPLAN-DR

### Principles

1. **数据层是生存关键**：免费源接口漂移是本项目最大死因，适配器薄封装 + 契约测试先行
2. **数据质量是一等公民而非事后补丁**（v1.2 升格）：完整性/时效性/准确性的度量与展示
   内建于 M0-M3，所有下游输出强制携带质量元数据（时点戳/复权口径/新鲜度）
3. **最小可行**：每个里程碑独立可用、独立可验证，不提前建设 UI/抽象
4. **统一 Instrument 与 Signal 模型**：三市场归一到 `Instrument`；告警与交易信号统一为
   `Signal` 实体（AlertRule 是 Signal 的一种来源），cooldown/去重只在引擎层做一次
5. **单写多读**：DuckDB 单文件，写入只经 fetch/paper 成交路径，读取全部只读连接
6. **回测与信号 point-in-time 强制**：信号只能用 bar 收盘前数据，框架层面 shift，不靠自觉
7. **实盘风控不可绕过**：risk.py 熔断独立于策略代码，任何下单必经风控检查

### Decision drivers

1. **个人维护成本**（决定性）：单人维护 → 源漂移"早暴露、易修复"
2. **数据可信度**（v1.2 升格，用户明示）：免费源无 SLA → 质量度量体系让"数据坏了"
   变成可观测事件而非沉默的错误结论
3. **交付优先级**（v1.2 用户裁决）：信号+建议单推送最先产生日常价值 → 前置到 M2
4. **资金安全**（后置但不可妥协）：模拟盘先行、testnet 先行、风控硬限制

### Viable options

**Option A: 自研轻量数据层 + 内建质量体系 + 前置信号链（quant/ 单包）** ← favored
- 实现思路：akshare(A股+基金) + ccxt(币) 薄适配器归一 schema；quality.py 质量体系
  （缺口/新鲜度/合法性/交叉验证）；signals/ 引擎 + advice.py 建议单前置交付；
  vectorbt 回测后置；交易分层最后
- Pros: 质量体系与数据层同构内建（而非外挂）；信号链最早跑通产生日常价值；完全可控
- Cons: 免费源维护负担全在自己；质量体系的交叉验证依赖第二源可用性

**Option B: 开源框架二次开发（zvt / RQAlpha / vnpy）**
- **Rejected**（同 v1.1 理由）：三市场统一 + 免费源 + 建议单推送混搭是框架拼图短板；
  且各框架数据质量校验普遍薄弱（默认信任源数据），与 v1.2 核心诉求相反

**Option C: 数据质量外包给商业数据源（tushare 等）**
- **Rejected**：用户裁决 #4 明确只用免费渠道；且付费也不消除质量度量需求（只是降低
  断裂频率）——质量体系无论如何都要自建，免费源约束只影响阈值松紧

**v1.2 新增裁决——信号引擎形态**：

**Option S-1: 规则/阈值信号引擎（声明式 yaml 规则 + 少量内置策略函数）** ← favored
- Pros: 规则即配置、可版本化、cooldown/去重天然集中在引擎层；建议单生成零额外机制
- Cons: 复杂策略表达力有限
- 裁决：M2 用 S-1 交付日常价值；复杂策略等回测引擎（M5）就位后经 BacktestRun 验证
  再升级为策略函数注册制——两阶段演进，不提前抽象

**Option S-2: 直接上完整策略框架（事件驱动 + 组合优化）**
- **Rejected（现阶段）**：过拟合个人工具规模；信号优先级是"每天有用的推送"而非全自动决策

### Implementation steps（基于 Option A + S-1）

> 全部为新建文件；`quant/` 为本仓库新顶层目录。

**M0 数据地基（含质量地基）**
1. 脚手架 — `quant/pyproject.toml`（deps: akshare, ccxt, pandas, duckdb, typer, pyyaml, requests, streamlit, vectorbt; dev: pytest, ruff）、`quant/quant/__init__.py`、`quant/quant/cli.py`（typer app 骨架）
2. Instrument 模型 — `quant/quant/data/universe.py`：`Instrument` dataclass；`normalize("600519")→("cn","600519.SH","CNY")`、`("000001")→("fund","000001.OF","CNY")`、`("BTC-USDT")→("crypto","BTC-USDT","USDT")`
3. 存储层 — `quant/quant/data/store.py`：DuckDB 建表 `instruments/bars/navs/fx_rates/signals/orders/accounts/dq_events`；`upsert_bars()` 幂等；`open_readonly()` 只读连接；`snapshot_parquet()` 冷备；bars 表带 `adj_type` 列（复权口径元数据，cn 固定 `qfq`，写入时声明）
4. 交易日历 — `quant/quant/data/calendar.py`：akshare `tool_trade_date_hist_sina` 拉取年度交易日历本地缓存（每年初刷新）；`is_trading_day()/expected_bars()`——完整性检测的基准（cn/fund 用日历，crypto 7×24 免日历）
5. 数据源适配器 — `quant/quant/data/sources/base.py`（`fetch(symbol, start, end) -> DataFrame` + 缓存优先 + 失败降级缓存并写 dq_event）；`cn_stock.py`(akshare `stock_zh_a_hist`, adjust="qfq")、`fund.py`(akshare `fund_open_fund_info_em`)、`crypto.py`(ccxt binance klines)
6. fetch CLI + 汇率 — `quant/cli.py` `fetch` 子命令（增量拉取：只请求缺失区间）；`quant/quant/data/fx.py`（USDT/CNY 双源 + 日频缓存 + 时点标注）
7. 质量地基 — `quant/quant/data/quality.py` v0：`validate_ohlc()`（low≤open/close≤high、vol≥0、非空，非法行隔离进 `dq_events` 并从查询结果排除）；`freshness()`（分市场阈值：cn >1 交易日 / fund >1 自然日 / crypto >2h → STALE）；契约测试 — `quant/tests/test_sources_contract.py`（`@pytest.mark.network` 默认跳过）

**M1 指标（信号的地基）**
8. 指标库 — `quant/quant/research/indicators.py`：纯 pandas 实现 ma/rsi/macd/boll（纯函数，供信号引擎与回测共用）；单测 — `quant/tests/test_indicators.py`（手造 20 根 K线，基准比对 < 1e-8）

**M2 信号与操作建议推送（核心前置交付）**
9. 推送层 — `quant/quant/alerts/notify.py`：通道适配器 `feishu_webhook`（签名校验）+ `wecom_webhook`（企微机器人）+ `pushplus`（微信直达兜底）；`quant/data/notify.yaml` 多通道并发、单通道失败不互斥、通道分级（紧急双通道/常规单通道）；测试 — `quant/tests/test_notify.py`（mock webhook，断言三通道 payload 结构与重试）
10. 信号引擎 — `quant/quant/signals/engine.py`：`SignalRule`（yaml 声明：symbol/metric/op/threshold/cooldown_h）→ `Signal`（落 DuckDB `signals` 表）；规则求值只允许用已收盘 bar（point-in-time 强制）；cooldown 窗口（默认 24h）内同标的同向信号去重——**告警与交易信号统一走此引擎，去重只在这一层**；测试 — `quant/tests/test_signals.py`（触发/去重/point-in-time 三组断言）
11. 操作建议单 — `quant/quant/signals/advice.py`：A股/基金 Signal → 建议单（代码/名称/方向/目标仓位比例/信号依据[规则+指标值]/数据时点戳/STALE 状态）；有 `positions.csv` 则附当前持仓参考，无则只给比例建议（绝对数量留给 M4 组合就位后）；经 notify.py 推飞书+微信；测试 — `quant/tests/test_advice.py`（内容完整性 + STALE 标注 + cooldown 内不重复推送）
12. 信号规则样例 — `quant/data/signals.yaml`：3 条示例规则（600519 跌破 MA60 / BTC-USDT RSI<30 / 000001.OF 净值回撤 5%）
13. 调度 — `quant/scripts/crontab.example`：fetch(交易日 17:00 + crypto 每小时) / signals check(fetch 后)

**M3 数据质量硬化（v1.2 新增）**
14. 完整性 — `quant/quant/data/quality.py` 补全：`detect_gaps()`（交易日历 expected_bars vs 实际，输出缺口区间列表）；`quant fetch --backfill` 按缺口区间补数；`dq report` CLI（JSON：缺口数/覆盖率%/新鲜度/最近交叉验证偏差）；测试 — `quant/tests/test_quality_gaps.py`（删 3 根 bar → 检出 3 缺口 → backfill → 归零）
15. 交叉验证 — `quant/quant/data/quality.py` `cross_check()`：cn 最新收盘 vs 腾讯实时行情（akshare `stock_zh_a_spot_em`）；fund 最新净值 vs 天天基金快照；crypto 最新收盘 vs 第二交易所（okx 公开 API）；偏差 > 0.5% 写 `dq_events` 并可选推送告警；测试 — `quant/tests/test_quality_cross.py`（mock 双源，构造偏差场景断言事件产生）
16. 质量例行 — `quant/scripts/crontab.example` 增加 `dq report`（每周日）；报告经 notify 推送摘要（缺口/STALE/偏差异常清单）

**M4 组合管理**
17. 持仓模型 — `quant/quant/portfolio/models.py` + `io.py`（positions.csv：market,symbol,quantity,avg_cost,opened_at）
18. 组合视图 — `quant/quant/portfolio/viewer.py` + `portfolio show`：逐持仓市值/盈亏 + 合计 CNY 计价（USDT 经 fx.py 折算）；输出强制带数据时点戳；建议单从此可附绝对参考数量

**M5 回测**
19. 回测引擎 — `quant/quant/backtest/engine.py`：vectorbt 封装，强制 `signals = indicators.shift(1)`；回测输入数据先过 `validate_ohlc`（脏数据不进回测）；输出年化/最大回撤/夏普/交易明细
20. 策略样例 — `strategies/sma_cross.py`、`momentum.py`、`dca.py`（定投，份额累计法手工基准）
21. 快照测试 — `quant/tests/test_backtest_snapshot.py`（固定区间+参数快照；未来函数专项断言）

**M6 Web UI 与 skill 化**
22. Streamlit — `quant/app/Home.py`（组合总览+质量面板：缺口/新鲜度）、`Portfolio.py`、`Backtest.py`，全部 `open_readonly()`
23. skill 封装 — `quant/skills/quant-analysis/SKILL.md` + 注册 `skills/registry.json`（provides: quant-fetch, quant-analyze, quant-signals, quant-backtest, quant-portfolio, quant-dq）

**M7 模拟盘**
24. 信号→虚拟成交 — `quant/quant/trading/paper.py`：`Account(type=paper)`；`execute(signal)` 按下一根 bar 开盘价+滑点（cn 0.1%/crypto 0.05%）成交；`trade paper` / `portfolio show --paper`；测试 — `quant/tests/test_paper.py`（信号/成交/账户三方一致 + 手算基准）

**M8 币实盘（testnet 先行）**
25. 风控熔断 — `quant/quant/trading/risk.py`：单笔金额上限/日亏上限/日频次上限/价格偏离 >5% 拒单；`quant/data/risk.yaml` 规则、审计日志、策略无权限绕过
26. 币实盘执行 — `quant/quant/trading/live_crypto.py`：ccxt private API（`--dry-run` → `--testnet` → 实盘需 `I_CONFIRM_LIVE_TRADING=1`）；API Key 只读起步 + IP 白名单；测试 — `quant/tests/test_live_crypto.py`（`@pytest.mark.network+testnet`：闭环 + 超限单拦截）

**M9 A股执行辅助（门槛制）**
27. 建议单已具备（M2）；自动下单延后：模拟盘稳定 ≥ 4 周后单独评审 easytrader vs QMT

### Workspace setup

- 实施前运行 `git status --short` 和 `git branch --show-current`
- 当前 working tree 已有未提交改动（Hermes 融合 53 条），**不要混入**：建议先提交融合改动，
  或 `git worktree add -b codex/quant-tool ../pm-team-quant-tool` 隔离实施
- 新目录 `quant/` 纯增量，冲突风险低

### Open questions (留给后续)

- M9 通道选型（easytrader vs QMT）模拟盘稳定后单独评审
- 币实盘交易所默认 binance，M8 开工前可改（ccxt 均支持）
- 交叉验证第二源（腾讯行情/okx）自身改版的跟进纳入契约测试范围

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| akshare 接口改版导致数据层断裂 | 薄适配器 + 契约测试（M0 就位）+ Parquet 冷备 |
| **数据沉默腐坏（最危险：看起来对其实错，如复权口径变化）**（v1.2 升格为首要风险） | 三层防线：schema 合法性校验（M0）→ 交叉验证双源偏差检测（M3）→ 输出强制携带时点戳/复权口径元数据，让错误可归因 |
| 免费源限流/封 IP | 缓存优先 + 增量拉取 + 源独立重试 |
| 交易日历源失效 | 年度日历本地缓存 + 跨年缺口用工作日近似兜底并标注置信度 |
| DuckDB 文件锁并发冲突 | 单写多读原则 |
| 回测过拟合/未来函数 | 引擎强制 shift(1) + 快照测试专项断言 + 回测输入过质量校验 |
| **信号风暴打扰用户（推送疲劳导致重要信号被忽略）** | cooldown 窗口 + 通道分级（紧急/常规）+ signals.yaml 单文件可审计 |
| 汇率源不可用 | 双源 + 日频缓存 + 7 天陈旧容忍 + 时点标注 |
| 实盘 API Key 泄露 / 风控缺失 / 券商风控 | 同 v1.1：环境变量+只读起步+testnet 先行；risk.py 不可绕过；M9 建议单人工确认 |
| vectorbt 安装失败 | M0 先验证安装；降级路径手写向量化回测 |
| pushplus 免费额度（日 200 条）耗尽 | 通道分级 + cooldown 天然限频；超额自动降级单通道 |

## Verification steps

- AC-1/2: `quant fetch --market cn --symbol 600519 --range 1y` 连跑两次，`SELECT count(*) FROM bars` 不变
- AC-3: `pytest quant/tests/test_indicators.py -v` 全绿
- AC-4: 三市场 3 持仓 positions.csv，`portfolio show` 总市值与手算一致
- AC-5: `pytest quant/tests/test_backtest_snapshot.py::test_no_lookahead` 绿
- AC-6: 必触发 alerts + `--dry-run` 出 JSON；飞书测试 webhook 与企微测试机器人各收一条
- AC-7: 断网 `quant analyze 600519`（有缓存）正常出报告 + source-degraded 日志
- AC-8: `pytest quant/ -m "not network"` 全绿
- AC-11: `pytest quant/tests/test_advice.py quant/tests/test_signals.py -v` 全绿（含 cooldown 去重断言）
- AC-12: `pytest quant/tests/test_quality_gaps.py -v` 全绿（删 3 根→检出 3→回补→归零）
- AC-13: 造陈旧数据跑 `analyze`，断言输出含 STALE 标注与时点戳
- AC-14: `pytest quant/tests/test_quality_cross.py -v` 全绿（非法 OHLC 隔离 + 偏差事件）
- AC-9/10: `pytest quant/tests/test_paper.py` 与 `test_live_crypto.py -m "network and testnet"` 全绿

## Roadmap（v1.2 交付路线）

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0（周1-2） | 数据地基+质量地基：Instrument/store/日历/3 源/fetch/OHLC 校验/新鲜度 | AC-1,2,7,14(部分) |
| M1（周3） | 指标库（纯函数） | AC-3 |
| **M2（周4-6）** | **信号引擎+双通道推送+操作建议单（核心前置交付）** | **AC-6,11** |
| M3（周7-8） | 数据质量硬化：缺口检测/回补/交叉验证/例行报告 | AC-12,13,14 |
| M4（周9-10） | 组合管理：positions/统一视图/时点戳 | AC-4 |
| M5（周11-13） | 回测：vectorbt 引擎/3 策略/快照 | AC-5,8 |
| M6（周14-15） | Streamlit UI（含质量面板）+ skill 化 | AC-8 收口 |
| M7（周16-18） | 模拟盘 | AC-9 |
| M8（周19-21） | 币实盘（testnet→最小仓位） | AC-10 |
| M9（门槛制） | A股自动下单（建议单 M2 已具备） | 单独评审 |

> 门槛不变：模拟盘稳定 ≥ 4 周才开实盘；实盘首月单笔 ≤ 100 USDT。

## ADR

- **Decision**: 自研轻量 Python 数据层（quant/ 单包，DuckDB+Parquet，akshare/ccxt 薄适配器）+ **内建三层数据质量体系**（合法性校验/缺口检测+回补/双源交叉验证，全输出强制时点戳与复权口径元数据）+ **规则式信号引擎与操作建议单前置到 M2 交付**（飞书+微信双通道，cooldown 防打扰）+ vectorbt 回测后置 + 交易分层最后（模拟盘→testnet→最小仓位实盘；A股首期建议单人工执行）。
- **Drivers**: 数据可信度（v1.2 用户明示）≈ 个人维护成本 > 交付优先级（信号前置）> 资金安全（后置里程碑的硬约束）
- **Alternatives considered**: 开源框架 rejected（质量校验薄弱+拼图短板）；商业数据源 rejected（违背免费裁决，且不消除质量度量需求）；S-2 完整策略框架 rejected（信号场景过重，两阶段演进保留升级路径）
- **Why chosen**: 免费源的质量风险只能靠自建度量体系对冲（付费也只降频率不消需求）；规则式信号引擎让"每天有用的推送"最快上线；质量元数据贯穿全链路使错误结论可归因可回溯。
- **Consequences**: 正——质量可观测、信号链最早产生价值、交易行为可验证、错误可归因；负——质量体系本身是新增维护面（第二源也要跟改版）、规则引擎表达力有限（留两阶段升级）、单人维护节奏决定项目寿命
- **Follow-ups**: M9 通道选型单独评审；信号引擎→策略函数注册制的升级触发条件（回测引擎就位+用户需要非规则逻辑）；M6 后评估拆独立仓库

## Review trail（多轮评审全程）

**第一轮（v1，2026-09-03 上午）**
- Planner v1 → Architect v1（steelman 免费源维护负担；3 tension）→ Critic v1 REVISE（契约测试 flaky/只读模式/汇率源）→ Planner v2 → Critic v2 APPROVED

**第二轮（v1.1 裁决复审）**
- 用户裁决 4 项 OQ → Planner v1.1（交易层+裁美股+双通道）→ Architect（A股爬虫风险降级建议单）→ Critic APPROVED

**第三轮（v1.2 第一遍，2026-09-03 下午）**
- Planner v3: 按裁决 #5/#6 重排里程碑（信号+建议单前置 M2，新增 M3 质量硬化），新增 quality.py/calendar.py/dq_events 设计
- Architect v3 challenge:
  - Steelman: "质量体系对个人工具是过度工程——商业终端也没做交叉验证"。裁决：部分成立 →
    砍掉过重项（不做 tick 级校验、不做多历史区间全量比对），保留三件高杠杆小事
    （合法性校验/缺口检测/最新价交叉验证），全部纳入既有 cron 例行，不新增运维动作
  - Tension: 信号前置 vs 信号需要质量背书（M2 在 M3 前）→ 裁决：M2 信号自带 M0 的
    合法性+新鲜度地基（STALE 标注），完整交叉验证 M3 补上——建议单内容已含时点戳与
    STALE 状态，用户可自行判断可信度，风险可接受
  - Tension: cooldown 统一 vs 告警与交易信号语义差异 → 统一 Signal 实体，engine 层单点去重
- Critic v3: **REVISE** — ① 交易日历来源未指定（cn 完整性检测无基准）② 交叉验证第二源
  未指定（"第二源"是空话）③ 建议单"参考数量"依赖 M4 持仓，M2 交付时无解 ④ 信号风暴
  （每小时 crypto cron + 无 cooldown = 推送疲劳）无 mitigation

**第四轮（v1.2 第二遍，定稿）**
- Planner v4 修复：
  - ① → step 4 `calendar.py`：akshare `tool_trade_date_hist_sina` 年度日历本地缓存，
    跨年缺口工作日近似兜底+置信度标注
  - ② → step 15 交叉验证源逐一指定：cn=腾讯行情（`stock_zh_a_spot_em`）、fund=天天基金快照、
    crypto=okx 公开 API；纳入契约测试范围
  - ③ → 建议单 v1 给"目标仓位比例"而非绝对数量；positions.csv 存在则附当前持仓参考；
    绝对数量 M4 后补
  - ④ → cooldown 默认 24h 写进 SignalRule schema + 通道分级（紧急/常规）+ pushplus 额度
    耗尽自动降级单通道（新增 risk 行）
- Architect v4: 确认修复成立；新增提醒——交叉验证的两个源自身也会改版，必须与主源
  同等纳入契约测试（已并入 step 15 与 Open questions）
- Critic v4: **APPROVED**（六维全过：原则一致/备选真实/风险均有 mitigation/AC 二值可验/
  验证具体/27+ 步全 cite 文件），reservations 见下
- Final iterations: 5 / 5（含 v1 两轮 + v1.1 一轮 + v1.2 两轮，用户要求的多轮评审达成）

## Critic verdict（v1.2 第四轮，最终）

| 维度 | 状态 | 备注 |
|---|---|---|
| Principle-option consistency | ✓ | 质量体系 Principle 2 与 Option A 内建式设计一致；信号前置与 driver 3 一致 |
| Alternative exploration | ✓ | B/C/S-2 均真候选且 rejected 理由具体；S-1 保留两阶段升级路径 |
| Risk mitigation clarity | ✓ | 12 条 risk 均落地 mitigation；"数据沉默腐坏"升格首要风险有三层防线 |
| AC testability | ✓ | 14 条 AC 二值可验证，新增 4 条均绑定具体测试/命令 |
| Verification concreteness | ✓ | 每条 AC 有 pytest 节点或 CLI 命令 |
| File/line coverage | ✓ | 27 步全部 cite 新建文件路径 |

### Verdict: APPROVED

### Reservations（保留意见，即使 APPROVED）

1. **交叉验证的 0.5% 容差是经验值**：A股尾盘竞价波动或数据源时点差（盘中 vs 收盘）可能造成
   误报。建议 M3 上线首月把容差放宽到 1% 观察误报率，再收紧；dq_events 保留原始偏差值供调参。
2. **crypto 新鲜度阈值 2h 与每小时 fetch cron 之间只有 1 小时余量**：一次 fetch 失败 + 下一轮
   前就会 STALE。这符合"宁误报不漏报"，但用户要有心理预期；如觉吵可把 crypto 阈值放宽到 4h。
3. **建议单的"目标仓位比例"缺乏用户风险偏好的输入**：v1 用规则里写死的保守比例（如单标的
   ≤20%），个性化仓位模型（基于波动率的目标风险仓位）留到 M4/M5 有组合与回测数据后。
4. **契约测试覆盖不了语义漂移**（同 v1 遗留）：复权口径变化的唯一防线是 bars.adj_type 元数据
   + 交叉验证，建议保持"每月人工抽查 1 只标的与行情软件比对"的习惯，写入
   `quant/docs/data-quality.md`。
5. **vectorbt 依赖较重**（同 v1 遗留）：M0 脚手架阶段先验证 `pip install vectorbt`；降级路径
   手写向量化回测。
6. **`dca.py` 定投策略现金流建模**（同 v1 遗留）：快照测试须用份额累计法手工基准。
