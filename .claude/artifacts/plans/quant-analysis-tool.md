# Quant Analysis Tool（个人量化分析工具）Implementation Plan

> Status: APPROVED
> Source: .claude/artifacts/designs/quant-analysis-tool.md
> Mode: default（Planner → Architect → Critic 完整循环）
> Iterations: 2 / 3
> Author: hpj360
> Last updated: 2026-09-03

## Requirements summary

为个人投资者构建覆盖 A股 / 美股 / 场外基金 / 加密币的量化分析工具：统一数据层、
指标研析、跨市场组合视图、向量化回测、阈值告警，CLI 优先 + Streamlit Web 界面，
最终封装为 skill 接入 Hermes agent 生态。v1 明确不做实盘/模拟盘自动交易。

## Acceptance criteria

继承 spec 全部 8 条 AC（AC-1 ~ AC-8），见 spec 文件。核心复述：

- AC-1/2: 三市场 fetch 落库幂等
- AC-3: analyze 指标与 pandas 基准一致（< 1e-8）
- AC-4: 组合统一 CNY 计价视图
- AC-5: 回测无未来函数（shifted 数据断言）
- AC-6: 告警触发 + webhook 推送
- AC-7: 单数据源故障不影响缓存查询
- AC-8: pytest 全绿 + 回测快照测试

## RALPLAN-DR

### Principles

1. **数据层是生存关键**：免费源（akshare/yfinance/ccxt）接口漂移是本项目最大死因，适配器薄封装 + 契约测试先行
2. **最小可行**：每个里程碑独立可用、独立可验证，不提前建设 UI/抽象
3. **统一 Instrument 模型**：三市场归一到 `Instrument(market, symbol, currency)`，下游全部面向统一 schema
4. **单写多读**：DuckDB 单文件，写入只经 fetch 路径，读取（analyze/backtest/Streamlit）全部只读连接
5. **回测 point-in-time 强制**：信号生成只能用 bar 收盘前数据，框架层面 shift，不靠自觉

### Decision drivers

1. **个人维护成本**（决定性）：单人维护，没有团队分摊数据源跟进负担 → 架构必须让源漂移"早暴露、易修复"
2. **落地速度**：个人工具需要快速见效维持投入动力 → CLI 优先，里程碑短
3. **数据可信度**：回测结论的价值 = 数据质量 → 本地数据资产沉淀（Parquet 冷备）优先于实时性

### Viable options

**Option A: 自研轻量数据层 + vectorbt 回测（quant/ 单包）** ← favored
- 实现思路：akshare(A股+基金) + yfinance(美股) + ccxt(币) 三个薄适配器归一到统一
  OHLCV/NAV schema，DuckDB+Parquet 存储，typer CLI，vectorbt 回测，Streamlit UI
- 改动文件：全新 `quant/` 目录（约 20 个源文件，见 Implementation steps）
- Pros: 三市场统一模型完全可控；可被 agent 生态调用；零外部框架学习成本；免费
- Cons: 数据源接口维护负担全在自己（用契约测试缓解）

**Option B: 基于开源量化框架二次开发（zvt / RQAlpha / vnpy）**
- 实现思路：zvt 有统一 entity 模型且覆盖股票/基金/部分币，RQAlpha 强在 A股回测，vnpy 偏交易
- Pros: 数据/回测基建现成，社区分摊源维护
- Cons: 三市场覆盖不齐（基金/币是二等公民）；框架绑定后定制受限；zvt 社区活跃度低、
  文档少，出问题仍需自己读源码；学习成本不低于自研薄层
- **Rejected**：三市场统一是本项目核心需求，恰是所有框架的短板；"社区分摊"在低活跃
  框架上是伪命题。但吸收其 entity 模型设计思想到 Option A 的 schema

**Option C: 纯组合现有 stock-analysis skill，不自建系统**
- 实现思路：用刚同步的 stock-analysis v6.2（Yahoo Finance）+ cron 定期 agent 跑批
- **Rejected（invalidation rationale）**: 无回测能力（R4 不满足）；无统一组合模型（JSON
  存储，无法跨市场归因）；数据无法沉淀为本地资产；Yahoo 对 A股/场外基金覆盖差
- 但其美股/币研析能力作为 M1 的补充数据源复用

### Implementation steps（基于 Option A）

> 全部为新建文件；`quant/` 为本仓库新顶层目录。

**M0 数据地基**
1. 脚手架 — `quant/pyproject.toml`（deps: akshare, yfinance, ccxt, pandas, duckdb, typer, vectorbt, streamlit; dev: pytest, ruff）、`quant/quant/__init__.py`、`quant/quant/cli.py`（typer app 骨架）
2. Instrument 模型与标的规范化 — `quant/quant/data/universe.py`：`Instrument` dataclass；`normalize("600519")→("cn","600519.SH","CNY")`、`("AAPL")→("us","AAPL","USD")`、`("000001")→("fund","000001.OF","CNY")`、`("BTC-USDT")→("crypto","BTC-USDT","USDT")`
3. 存储层 — `quant/quant/data/store.py`：DuckDB 建表 `instruments/bars/navs/fx_rates`；`upsert_bars()` 幂等（按 instrument+ts 主键去重）；`open_readonly()` 只读连接（供 Streamlit/回测）；`snapshot_parquet()` 冷备导出
4. 数据源适配器 — `quant/quant/data/sources/base.py`（接口 `fetch(symbol, start, end) -> DataFrame[ts,o,h,l,c,v]` + 缓存优先 + 失败降级到缓存并告警日志）；`cn_stock.py`(akshare `stock_zh_a_hist`)、`us_stock.py`(yfinance)、`fund.py`(akshare `fund_open_fund_info_em` 净值)、`crypto.py`(ccxt binance klines)
5. fetch CLI — `quant/cli.py`：`quant fetch --market --symbol --range`；汇率源 — `quant/quant/data/fx.py`（akshare `currency_boc_safe` 或 frankfurter.app，日频缓存）
6. 契约测试 — `quant/tests/test_sources_contract.py`：每源固定区间小样本断言（600519 近 10 根日线非空且列齐；000001.OF 净值单调日期；BTC-USDT 24h 波动 < 50% 合理性检查），标记 `@pytest.mark.network` 默认跳过，本地 `pytest -m network` 手动跑

**M1 指标研析**
7. 指标库 — `quant/quant/research/indicators.py`：纯 pandas 实现 ma/rsi/macd/boll，输入只认 bar 收盘后的 DataFrame（point-in-time 原则落地的第一处）
8. 研析报告 — `quant/quant/research/report.py` + `quant/cli.py` `analyze` 子命令：文本报告（趋势/超买超卖/波动分位），美股与币可调用现有 `skills/stock-analysis/scripts/analyze_stock.py` 补充 8 维评分
9. 指标单测 — `quant/tests/test_indicators.py`：手造 20 根 K线，指标值与 pandas/manual 基准比对 < 1e-8

**M2 组合管理**
10. 持仓模型 — `quant/quant/portfolio/models.py`（Position/Portfolio dataclass）+ `io.py`（`quant/data/positions.csv` 人肉可编辑，含 market,symbol,quantity,avg_cost,opened_at）
11. 组合视图 — `quant/quant/portfolio/viewer.py` + `quant/cli.py` `portfolio show`：逐持仓市值/盈亏（本币），合计行 CNY 计价（USD/USDT 资产经 fx.py 折算）

**M3 回测**
12. 回测引擎 — `quant/quant/backtest/engine.py`：vectorbt 封装，强制 `signals = indicators.shift(1)`（未来函数防线写死在引擎，不在策略）；输出年化/最大回撤/夏普/交易明细
13. 策略样例 — `quant/quant/backtest/strategies/sma_cross.py`、`momentum.py`、`dca.py`（定投，基金场景）+ `quant/cli.py` `backtest` 子命令
14. 快照测试 — `quant/tests/test_backtest_snapshot.py`：固定历史区间 + 固定参数，指标结果快照比对；未来函数专项断言（shift 前后结果必须不同）

**M4 告警与调度**
15. 告警规则 — `quant/quant/alerts/rules.py`（`quant/data/alerts.yaml`: symbol/metric/op/threshold）+ `checker.py` + `quant/cli.py` `alerts check`
16. 推送 — `quant/quant/alerts/notify.py`：飞书 webhook（首选，用户已有飞书生态）+ Telegram 二选一实现
17. 调度 — `quant/scripts/crontab.example`：fetch(交易日 17:00) / alerts check(币 7×24 每小时)

**M5 Web UI 与 skill 化**
18. Streamlit — `quant/app/Home.py`（组合总览）、`Portfolio.py`（持仓明细+K线 plotly）、`Backtest.py`（回测结果可视化），全部经 `store.open_readonly()`
19. skill 封装 — `quant/skills/quant-analysis/SKILL.md` + 注册进 `skills/registry.json`（provides: quant-fetch, quant-analyze, quant-backtest, quant-portfolio），对接 Hermes Workbench 跨源调度

### Workspace setup

- 实施前运行 `git status --short` 和 `git branch --show-current`
- 当前 working tree 已有未提交改动（Hermes 能力融合），**不要混入**：建议 quant 工具在
  `git worktree add -b codex/quant-tool ../pm-team-quant-tool` 中实施，或先提交融合改动再开工
- 新目录 `quant/` 为纯增量，与现有改动无文件交集，冲突风险低

### Open questions (留给后续)

- spec Open questions 1-4（实盘边界 / 美股必要性 / 通知渠道 / tushare 付费）——实施中遇到按 spec 假设走，用户可随时纠正

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| akshare 接口改版导致数据层断裂（历史高频） | 薄适配器（只依赖少数宽接口）+ 契约测试（M0 就位）+ Parquet 冷备（已采数据永不丢） |
| DuckDB 文件锁：调度进程与 Streamlit 并发冲突 | 单写多读原则：写入只经 fetch CLI；读取一律 `open_readonly()` |
| 免费源限流/封 IP | 缓存命中优先；增量拉取（只取缺失区间）；源间无依赖可独立重试 |
| 回测过拟合/未来函数 | 引擎层强制 shift(1)；快照测试含未来函数专项断言 |
| 汇率源不可用 | fx 日频缓存 + 7 天陈旧容忍 + 明确标注折算汇率时点 |
| 币种 7×24 与 A股日历不一致导致调度复杂 | 调度按任务独立 cron，不做统一交易日历抽象（YAGNI） |

## Verification steps

- AC-1/2: `quant fetch --market cn --symbol 600519 --range 1y` 后连跑两次，`duckdb` 查 `SELECT count(*) FROM bars` 不变
- AC-3: `pytest quant/tests/test_indicators.py -v` 全绿（基准比对）
- AC-4: 造含三市场 3 持仓的 positions.csv，`quant portfolio show` 输出总市值 = Σ(持仓市值折 CNY)，手算比对
- AC-5: `pytest quant/tests/test_backtest_snapshot.py::test_no_lookahead` 绿（shift 前后结果差异断言）
- AC-6: 配 threshold 已必触发的 alerts.yaml，`quant alerts check --dry-run` 输出触发 JSON；接测试 webhook 验证到达
- AC-7: 断网跑 `quant analyze 600519`（已有缓存），正常出报告且日志含 source-degraded 告警
- AC-8: `pytest quant/ -m "not network"` 全绿；`pytest -m network` 本地手动全绿

## Roadmap（交付路线）

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0（周1-2） | 数据地基：Instrument/store/4 源/fetch/契约测试 | AC-1,2,7 |
| M1（周3-4） | 指标研析：indicators/analyze/复用 stock-analysis | AC-3 |
| M2（周5-6） | 组合管理：positions/统一视图/汇率折算 | AC-4 |
| M3（周7-9） | 回测：vectorbt 引擎/3 策略/快照测试 | AC-5 |
| M4（周10-11） | 告警调度：规则/检查/推送/cron | AC-6 |
| M5（周12-13） | Streamlit UI + skill 化接入 Workbench | AC-8 收口 |
| 后置 | 模拟盘（paper trading）→ 仅在 spec OQ-1 确认后启动 | — |

## ADR

- **Decision**: 自研轻量 Python 数据层（quant/ 单包，DuckDB+Parquet，akshare/yfinance/ccxt 三薄适配器归一 Instrument 模型）+ vectorbt 向量化回测 + typer CLI + Streamlit UI，v1 不含任何交易通道。
- **Drivers**: 个人维护成本（源漂移早暴露易修复）> 落地速度 > 数据可信度基建
- **Alternatives considered**: Option B（开源框架二次开发）rejected——三市场统一恰是框架短板；Option C（纯 skill 组合）rejected——无回测、数据不沉淀；两者思想被吸收（zvt 的 entity 模型 → schema 设计；stock-analysis skill → M1 美股/币补充源）
- **Why chosen**: 三市场统一模型是核心需求且只有自研能保证；薄适配器 + 契约测试把最大风险（免费源漂移）控制在可维护范围；全部组件零运维、零费用。
- **Consequences**: 正——完全可控、agent 生态可集成、数据资产本地沉淀；负——akshare 等源改版需自己跟（契约测试缓解）、无 SLA 数据质量需接受、单人维护节奏决定项目寿命
- **Follow-ups**: spec OQ-1（实盘边界）确认后规划 paper-trading 层；OQ-4（tushare 付费）可作为数据可靠性升级项；M5 后评估是否拆独立仓库

## Review trail

- Planner draft v1: Option A 自研薄数据层，19 步实施，M0-M5 路线
- Architect challenge v1: steelman"个人维护者低估免费源长期维护负担"→ 采纳为契约测试前置 M0；3 条 tension（速度vs健壮性 / DuckDB单文件vs并发 / 向量化vs事件驱动）均有取舍裁决
- Critic verdict v1: REVISE — 契约测试依赖外网会 flaky 未处理；DuckDB 并发读模式未写明；汇率源未指定
- Planner draft v2: 契约测试加 `@pytest.mark.network` 默认跳过；store 增加 `open_readonly()`；fx.py 指定源与缓存策略；"单写多读"升格为 Principle 4
- Critic verdict v2: APPROVED（改进已合入主体），reservations 见下
- Final iterations: 2 / 3

## Critic verdict（v2，最终）

| 维度 | 状态 | 备注 |
|---|---|---|
| Principle-option consistency | ✓ | Option A 与 5 条 Principles 一致 |
| Alternative exploration | ✓ | B/C 为真候选，invalidation rationale 具体 |
| Risk mitigation clarity | ✓ | 6 条 risk 均有落地 mitigation（非"以后再说"） |
| AC testability | ✓ | 8 条 AC 二值可验证 |
| Verification concreteness | ✓ | 每条 AC 有具体命令/测试名 |
| File/line coverage | ✓ | 19 步全部 cite 新建文件路径（greenfield 无既有行号） |

### Verdict: APPROVED

### Reservations（保留意见，即使 APPROVED）

1. **契约测试覆盖不了"数据正确但悄悄变差"**（如源改变复权口径）：快照断言只能抓 schema 断裂，抓不住语义漂移。缓解建议：M1 起每月人工抽查 1 只标的与行情软件比对，写进 `quant/docs/data-quality.md`（运维习惯，非代码）。
2. **vectorbt 依赖较重**（numba/llvmlite），Apple Silicon/Windows 偶发安装问题：若 M3 安装受阻，降级路径是手写向量化回测（pandas 即可覆盖 3 个样例策略），建议在 M0 脚手架阶段先验证 `pip install vectorbt` 可行。
3. **`dca.py`（定投策略）在 vectorbt 中的现金流建模**与真实定投（份额累计）有语义差距，快照测试须用份额累计法手工基准，不能只信框架输出。
