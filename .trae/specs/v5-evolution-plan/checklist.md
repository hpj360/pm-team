# V5 平台演进验收检查清单

> 状态：规划稿，2026-07-31 锁定方案 A
> 验收门槛：综合质量评分 ≥ 95

## V5.1 — AI Agent 化

### RAG 知识库基础设施
- [ ] Milvus `kb_documents` collection 已创建
- [ ] `RagService` 实现分块/向量化/入库/检索
- [ ] ATT&CK + CVE + APT 知识库种子数据已导入
- [ ] `POST /api/ai/kb/upload` 端点
- [ ] `POST /api/ai/kb/search` 端点
- [ ] 单元测试 ≥ 4 用例

### Agent 工具链
- [ ] `ToolRegistry` 注册中心 + 权限校验
- [ ] 6 个内置工具实现（search_files / get_threat_intel / run_ner / query_neo4j / generate_report / kb_search）
- [ ] OpenAI / Ollama Function Calling 协议适配
- [ ] 单元测试 ≥ 4 用例

### Agent 多步推理引擎
- [ ] `AgentExecutor` ReAct 模式实现
- [ ] 最大步数 + token 预算控制
- [ ] 推理轨迹持久化
- [ ] 失败重试 + 步骤回滚
- [ ] 单元测试 ≥ 4 用例

### 自主分析任务
- [ ] `AutonomousAnalysisService` 异步执行
- [ ] WebSocket 进度推送
- [ ] 任务结果结构（conclusion + evidence_chain + referenced_files + confidence）
- [ ] `POST /api/ai/agent/analyze` 端点
- [ ] 单元测试 ≥ 3 用例

### 前端 Agent 任务页 + 知识库管理
- [ ] 「Agent 分析」页面（任务列表 + 创建 Modal）
- [ ] 推理轨迹可视化（时间轴 + 步骤图标）
- [ ] 「知识库管理」页面（上传 / 列表 / 检索测试）
- [ ] 任务结果展示（结论 + 证据链 + 引用文件）
- [ ] 单元测试 ≥ 4 用例

## V5.2 — 沙箱动态分析

### Cuckoo 沙箱集成
- [ ] `CuckooClient` + `CuckooConfig`
- [ ] 任务提交端点
- [ ] 结果轮询机制
- [ ] 单元测试 ≥ 3 用例（mock Cuckoo）

### 动态分析任务编排
- [ ] `DynamicAnalysisService` 静态+动态联合编排
- [ ] 状态机：PENDING → SUBMITTED → RUNNING → COMPLETED → PARSED
- [ ] `POST /api/analyze/dynamic/submit` 端点
- [ ] 单元测试 ≥ 3 用例

### 行为指标提取
- [ ] 进程树 → STIX Process
- [ ] 网络连接 → STIX NetworkTraffic + IOC
- [ ] 文件操作 → 文件系统变更清单
- [ ] API 调用 → ATT&CK 技术映射
- [ ] 单元测试 ≥ 4 用例

### 前端动态分析视图
- [ ] 文件详情页「动态分析」Tab
- [ ] 进程树可视化（树形组件）
- [ ] 网络拓扑图
- [ ] ATT&CK 技术热力图
- [ ] 单元测试 ≥ 3 用例

## V5.3 — 威胁狩猎

### ATT&CK 矩阵数据集
- [ ] 14 战术 × 193 技术数据导入
- [ ] `AttackTechniqueEntity` + Mapper
- [ ] 文件-技术关联表
- [ ] 单元测试 ≥ 2 用例

### 狩猎假设与验证
- [ ] `ThreatHuntingService` 创建/检索/验证
- [ ] 假设模型（hypothesis_text / related_techniques / expected_indicators）
- [ ] 验证结果（命中清单 + 置信度 + 推荐 IOCs）
- [ ] `POST /api/hunting/hypothesis` 端点
- [ ] 单元测试 ≥ 3 用例

### 检测规则管理
- [ ] Sigma 规则导入/编辑/测试
- [ ] YARA 规则版本管理 + 命中统计
- [ ] 规则与 ATT&CK 技术双向关联
- [ ] 单元测试 ≥ 3 用例

### 前端威胁狩猎工作台
- [ ] 「威胁狩猎」工作台（ATT&CK 矩阵热力图）
- [ ] 假设创建 Modal + 验证结果展示
- [ ] 「规则管理」页面（Sigma / YARA Tab）
- [ ] 狩猎任务触发 V4 工作流评审
- [ ] 单元测试 ≥ 3 用例

## V5.4 — 可观测性体系

### OpenTelemetry 全链路追踪
- [ ] 12 微服务接入 OTel SDK
- [ ] 跨服务 Trace 传播（HTTP + Kafka Header）
- [ ] OTel Collector + Jaeger UI 部署
- [ ] 集成测试 1 用例（跨 3 服务链路）

### Prometheus 指标体系
- [ ] micrometer-registry-prometheus 接入 12 微服务
- [ ] 业务指标（解析成功率 / AI 延迟 / 审批时长 / 狩猎命中率）
- [ ] 系统指标（JVM / DB / Kafka lag / Redis 命中）
- [ ] Grafana Dashboard 模板 4 套
- [ ] 单元测试 ≥ 2 用例

### 告警体系
- [ ] AlertManager + 飞书 Webhook
- [ ] 告警规则（错误率 / AI 降级 / 限流 / Kafka 积压）
- [ ] 告警分级（P0 电话 / P1 加急 / P2 普通）
- [ ] 单元测试 ≥ 2 用例

### 统一日志体系
- [ ] 统一 JSON 日志格式
- [ ] Loki + Promtail 部署
- [ ] 运维手册：Loki 日志查询指南

## V5.5 — 综合验证

- [ ] 所有后端微服务 mvn compile + mvn test 通过
- [ ] 前端 npx tsc --noEmit 零错误
- [ ] 前端 npm run build 通过
- [ ] 前端 npm run test:unit 通过且覆盖率 ≥ 80%
- [ ] 前端 npm run test:e2e 通过（无回归）
- [ ] V5 迭代验证报告质量分 ≥ 95
- [ ] API 文档版本号 + 变更记录已更新
- [ ] 运维手册更新（Cuckoo / Jaeger / Prometheus / Grafana / Loki）

## V5 验收门槛汇总

| 维度 | 目标 | 验收方式 |
|---|---|---|
| 综合质量评分 | ≥ 95 | 加权评分表 |
| 后端测试用例 | 新增 ≥ 35 | mvn test 报告 |
| 前端测试用例 | 新增 ≥ 20 | vitest 报告 |
| Agent 工具数 | ≥ 6 | 工具注册中心 |
| Agent 推理轨迹可视化 | ✅ | 前端验证 |
| Cuckoo 集成 | ✅ | 动态分析任务 |
| ATT&CK 矩阵 | 14 × 193 | 数据库校验 |
| OTel 全链路 | 12 微服务 | Jaeger UI |
| Grafana Dashboard | 4 套 | UI 验证 |
| 告警规则 | ≥ 4 条 | 触发测试 |
| 端点数 | 160 → ~180 | API 文档 |
