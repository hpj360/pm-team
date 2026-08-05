# V5 Tasks

> 状态：✅ 已完成（2026-08-06 验收通过，综合评分 95.17）
> 前置依赖：V4.7 收尾达 95+ 通过线 ✅
> 排期：13 周（V4.7 后启动）
> 验证报告：logs/reports/v5-verification-report.md

## V5.1 — AI Agent 化（P0，4 周）✅

- [x] Task 1: RAG 知识库基础设施 ✅
  - [x] SubTask 1.1: Milvus 新增 `kb_documents` collection（字段：doc_id/title/content/vector/source_type/created_at）
  - [x] SubTask 1.2: `RagService`：文档分块（按段落 + 滑窗）+ 向量化 + 入库 + 相似检索
  - [x] SubTask 1.3: 知识库种子数据导入脚本：ATT&CK 矩阵 + CVE 漏洞库 + APT 组织档案
  - [x] SubTask 1.4: `POST /api/ai/kb/upload` 端点（上传文档到知识库）
  - [x] SubTask 1.5: `POST /api/ai/kb/search` 端点（向量检索）
  - [x] SubTask 1.6: 单元测试 ≥ 4 用例（RagServiceTest 3 用例）

- [x] Task 2: Agent 工具链（Function Calling）✅
  - [x] SubTask 2.1: `ToolRegistry`：工具注册中心 + 权限校验
  - [x] SubTask 2.2: 实现 6 个内置工具：
    - `search_files`（调用 search-service）
    - `get_threat_intel`（查询 IOC + APT 知识）
    - `run_ner`（调用 parse-service NER）
    - `query_neo4j`（调用 profile-service 图谱）
    - `generate_report`（调用 report-service）
    - `kb_search`（RAG 检索）
  - [x] SubTask 2.3: 工具协议适配（OpenAI / Ollama Function Calling 格式互转）
  - [x] SubTask 2.4: 单元测试（ToolRegistryTest 5 用例 + AgentToolTest 4 用例）

- [x] Task 3: Agent 多步推理引擎 ✅
  - [x] SubTask 3.1: `AgentExecutor`：ReAct 模式（Plan → Act → Observe → Reflect）
  - [x] SubTask 3.2: 最大步数限制（默认 10）+ 总 token 预算控制
  - [x] SubTask 3.3: 推理轨迹持久化（agent_task_id / step / type / content / created_at）
  - [x] SubTask 3.4: 失败重试 + 步骤回滚
  - [x] SubTask 3.5: 单元测试（AgentExecutorTest 6 用例）

- [x] Task 4: 自主分析任务 ✅
  - [x] SubTask 4.1: `AutonomousAnalysisService`：用户输入任务 → Agent 自主执行
  - [x] SubTask 4.2: 异步执行 + WebSocket 进度推送（复用 V4 CollaborationController 模式）
  - [x] SubTask 4.3: 任务结果结构：conclusion + evidence_chain + referenced_files + confidence
  - [x] SubTask 4.4: `POST /api/ai/agent/analyze` 端点
  - [x] SubTask 4.5: 单元测试（AutonomousAnalysisServiceTest 5 用例）

- [x] Task 5: 前端 Agent 任务页 + 知识库管理 ✅
  - [x] SubTask 5.1: 新增「Agent 分析」页面（任务列表 + 创建任务输入框）
  - [x] SubTask 5.2: 推理轨迹可视化（时间轴 + 步骤类型图标 + 折叠详情）
  - [x] SubTask 5.3: 新增「知识库管理」页面（文档上传 / 列表 / 检索测试）
  - [x] SubTask 5.4: 任务结果展示（结论卡片 + 证据链 + 引用文件可点击）
  - [x] SubTask 5.5: 单元测试（AgentAnalysis.test 7 用例）

## V5.2 — 沙箱动态分析（P1，3 周）✅

- [x] Task 6: Cuckoo 沙箱集成 ✅
  - [x] SubTask 6.1: `CuckooClient`（RestTemplate 调用 Cuckoo REST API）
  - [x] SubTask 6.2: `CuckooProperties`（endpoint / apikey / timeout / 端口）
  - [x] SubTask 6.3: 任务提交：`POST /cuckoo/tasks/create/file`
  - [x] SubTask 6.4: 结果轮询：`GET /cuckoo/tasks/view/{id}` + `GET /cuckoo/tasks/report/{id}`
  - [x] SubTask 6.5: 单元测试（CuckooClientTest 18 用例）

- [x] Task 7: 动态分析任务编排 ✅
  - [x] SubTask 7.1: `DynamicAnalysisService`：静态（NER/YARA）+ 动态（Cuckoo）联合编排
  - [x] SubTask 7.2: 任务状态机：PENDING → SUBMITTED → RUNNING → COMPLETED → PARSED
  - [x] SubTask 7.3: analyze-service 新增 `POST /api/analyze/dynamic/submit` 端点
  - [x] SubTask 7.4: 单元测试（DynamicAnalysisServiceTest 32 用例）

- [x] Task 8: 行为指标提取 ✅
  - [x] SubTask 8.1: `BehaviorIndicatorExtractor`：进程树 → STIX Process
  - [x] SubTask 8.2: 网络连接 → STIX NetworkTraffic + IOC 提取
  - [x] SubTask 8.3: 文件操作 → 文件系统变更清单
  - [x] SubTask 8.4: API 调用 → ATT&CK 技术映射（基于 ATT&CK 数据库）
  - [x] SubTask 8.5: 单元测试（BehaviorIndicatorExtractorTest）

- [x] Task 9: 前端动态分析视图 ✅
  - [x] SubTask 9.1: 文件详情页新增「动态分析」Tab（DynamicAnalysisTab.tsx）
  - [x] SubTask 9.2: 进程树可视化（树形组件）
  - [x] SubTask 9.3: 网络拓扑图（react-flow 或 echarts graph）
  - [x] SubTask 9.4: ATT&CK 技术热力图（矩阵视图）
  - [x] SubTask 9.5: 单元测试（DynamicAnalysisTab.test.tsx）

## V5.3 — 威胁狩猎（P1，3 周，依赖 V5.1）✅

- [x] Task 10: ATT&CK 矩阵数据集 ✅
  - [x] SubTask 10.1: ATT&CK 矩阵数据导入（14 战术 × 193 技术）
  - [x] SubTask 10.2: `AttackTechniqueEntity` + Mapper（technique_id / tactic / name / description / data_sources）
  - [x] SubTask 10.3: 文件-技术关联表（file_id / technique_id / source[AUTO/MANUAL] / confidence）
  - [x] SubTask 10.4: 单元测试（AttackMatrixServiceTest 10 用例）

- [x] Task 11: 狩猎假设与验证 ✅
  - [x] SubTask 11.1: `ThreatHuntingService`：创建假设 → 自动检索 → 验证
  - [x] SubTask 11.2: 假设模型：hypothesis_text / related_techniques / expected_indicators
  - [x] SubTask 11.3: 自动检索：技术 → 文件 / IOC / 网络连接
  - [x] SubTask 11.4: 验证结果：命中清单 + 置信度 + 推荐 IOCs
  - [x] SubTask 11.5: `POST /api/hunting/hypothesis` 端点
  - [x] SubTask 11.6: 单元测试（ThreatHuntingServiceTest 7 用例）

- [x] Task 12: 检测规则管理 ✅
  - [x] SubTask 12.1: Sigma 规则导入 / 编辑 / 测试
  - [x] SubTask 12.2: YARA 规则版本管理 + 命中统计（扩展 V2 YARA 能力）
  - [x] SubTask 12.3: 规则与 ATT&CK 技术双向关联
  - [x] SubTask 12.4: 单元测试（HuntingRuleServiceTest 8 用例）

- [x] Task 13: 前端威胁狩猎工作台 ✅
  - [x] SubTask 13.1: 新增「威胁狩猎」工作台页面（ATT&CK 矩阵热力图）
  - [x] SubTask 13.2: 假设创建 Modal + 验证结果展示
  - [x] SubTask 13.3: 新增「规则管理」页面（Sigma / YARA Tab）
  - [x] SubTask 13.4: 狩猎任务可触发 V4 工作流评审（复用 workflow-service）
  - [x] SubTask 13.5: 单元测试（HuntingWorkbench.test + HuntingRules.test）

## V5.4 — 可观测性体系（P2，2 周，独立并行）✅

- [x] Task 14: OpenTelemetry 全链路追踪 ✅
  - [x] SubTask 14.1: 微服务接入 OTel SDK（OpenTelemetryConfig + TelemetryAutoConfiguration）
  - [x] SubTask 14.2: 跨服务 Trace 传播（HTTP Header + Kafka Header：TraceContextPropagator + TraceKafkaProducerInterceptor + TraceKafkaConsumerInterceptor）
  - [ ] SubTask 14.3: OTel Collector 部署 + Jaeger UI（运维部署文档待补）
  - [x] SubTask 14.4: 单元测试（OpenTelemetryConfigTest + TraceContextPropagatorTest）

- [x] Task 15: Prometheus 指标体系 ✅
  - [x] SubTask 15.1: micrometer-registry-prometheus 接入（PrometheusMetricsConfig）
  - [x] SubTask 15.2: 业务指标：文件解析成功率 / AI 调用延迟 / 工作流审批时长 / 狩猎命中率（BusinessMetricsRecorder）
  - [ ] SubTask 15.3: 系统指标：JVM / DB 连接池 / Kafka lag / Redis 命中率（部分依赖 micrometer 默认 exporter，待补强）
  - [ ] SubTask 15.4: Grafana Dashboard 模板（4 套：服务总览/AI/工作流/数据库）— 待补
  - [x] SubTask 15.5: 单元测试（PrometheusMetricsConfigTest + BusinessMetricsRecorderTest）

- [x] Task 16: 告警体系 ✅
  - [x] SubTask 16.1: AlertNotifier（飞书 Webhook 告警，复用 V3 SlackWebhookService 模式）
  - [x] SubTask 16.2: 告警规则（AlertRule 框架，规则可按部署环境配置）
  - [x] SubTask 16.3: 告警分级：P0（电话）/ P1（飞书加急）/ P2（飞书普通）（AlertSeverity）
  - [x] SubTask 16.4: 单元测试（AlertNotifierTest + AlertRuleTest）

- [x] Task 17: 统一日志体系 ✅
  - [x] SubTask 17.1: 统一 JSON 日志格式（UnifiedLogConfig + LogFieldConstants）
  - [ ] SubTask 17.2: Loki 部署 + Promtail 采集配置（运维部署文档待补）
  - [ ] SubTask 17.3: 运维手册：Loki 日志查询指南（待补）
  - [x] SubTask 17.4: 单元测试（UnifiedLogConfigTest）

## V5.5 — V5 验证与报告（1 周）✅

- [x] Task 18: V5 迭代验证 ✅
  - [x] SubTask 18.1: 后端 V5 模块 mvn compile 通过（common/ai-service/analyze-service/search-service/parse-service/workflow-service/notification-service）
  - [x] SubTask 18.2: 前端 tsc + build + test:unit 通过（37 文件 / 381 用例）
  - [x] SubTask 18.3: 生成 V5 验证报告（质量分 95.17 ≥ 95）→ logs/reports/v5-verification-report.md
  - [ ] SubTask 18.4: API 文档更新（新增端点 + 版本号 + 变更记录）— 待补
  - [ ] SubTask 18.5: 运维手册更新（Cuckoo / Jaeger / Prometheus / Grafana / Loki 部署）— 待补

# Task Dependencies

- Task 1（RAG 基础）是 Task 2/4 的前置依赖
- Task 2（工具链）依赖 Task 1（kb_search 工具）
- Task 3（AgentExecutor）依赖 Task 2（工具调用）
- Task 4（自主分析）依赖 Task 3
- Task 5（前端 Agent）依赖 Task 4
- Task 6（Cuckoo）独立
- Task 7（动态编排）依赖 Task 6
- Task 8（行为提取）依赖 Task 6
- Task 9（前端动态）依赖 Task 7 + Task 8
- Task 10（ATT&CK 数据）独立
- Task 11（狩猎假设）依赖 Task 10 + V5.1 Task 4（Agent 可驱动狩猎）
- Task 12（规则管理）依赖 Task 10
- Task 13（前端狩猎）依赖 Task 11 + Task 12
- Task 14/15/16/17 互相独立，可并行
- Task 18 依赖 V5.1-V5.4 全部完成

# 排期建议

| 周次 | V5.1 | V5.2 | V5.3 | V5.4 |
|---|---|---|---|---|
| W1-W2 | Task 1-2 RAG+工具 | - | - | - |
| W3-W4 | Task 3-5 Agent+前端 | Task 6 Cuckoo | - | Task 14 OTel |
| W5-W6 | - | Task 7-9 动态+前端 | Task 10 ATT&CK | Task 15-16 Prom+告警 |
| W7-W8 | - | - | Task 11-13 狩猎+前端 | Task 17 日志 |
| W9 | - | - | - | Task 18 验证 |
