# V2 迭代增强计划 Spec

## Why

V1（W2-W14）已完成全量功能开发并通过验收（综合质量 96.31 分），但存在 5 项已知问题：
- **P1**：security-BERT NER 仅用正则兜底，DJL 模型推理未接入（`NerServiceImpl.loadDjlModel()` / `extractByModel()` 均为 TODO 占位）
- **P2**：MinIO 分片合并使用应用层 `completeMultipartUpload` 手动拼接，大文件性能差，未用 `composeObject` 服务端合并
- **P2**：前端仅有 5 个 E2E 测试文件（56 用例），无组件级单元测试，覆盖率不足
- **P3**：Vite 已有 manualChunks（react/antd/charts/query），但 ECharts 和 antd chunk 仍偏大
- **P3**：report-service 无定时调度器，不支持周期性报告自动生成与邮件推送

本迭代聚焦于将上述"可用但不够好"的功能提升至"生产可用"水平。

## What Changes

### V2.1 — security-BERT DJL 模型集成（P1）
- 实现 `NerServiceImpl.loadDjlModel()`：使用 DJL Criteria 加载本地 security-BERT ONNX/PyTorch 模型
- 实现 `NerServiceImpl.extractByModel()`：调用 DJL Predictor 进行 token 分类推理，解析 BIO 标签为 `NerEntityVO`
- 新增模型健康检查端点 `GET /api/parse/ner/model-status`
- 新增模型推理指标（延迟、吞吐、准确率）上报到 Prometheus
- 保留正则兜底作为降级路径，模型不可用时自动切换

### V2.2 — MinIO composeObject 优化 + Neo4j 关系图谱（P2）
- 重构 `FileServiceImpl.completeMultipartUpload()`：使用 MinIO `composeObject` API 替代应用层分片拼接
- 新增 Neo4j 依赖到 profile-service，实现关系图谱的图数据库后端
- 实现 Cypher 查询：目标-文件-IOC-漏洞-攻击链 多跳关系遍历
- 前端 RelationGraph 页面增加 Neo4j 数据源切换（保留 Mock 降级）

### V2.3 — 前端组件级单元测试（P2）
- 搭建 Vitest + React Testing Library 测试环境
- 为 49 个页面中的核心组件编写单元测试（目标覆盖率 ≥80%）
- 为 utils / stores / hooks 编写单元测试
- 接入 CI：`npm run test:unit` 必须通过

### V2.4 — 前端构建性能优化（P3）
- 细化 manualChunks：将 antd 拆为 antd-core / antd-pro，将 echarts 按图表类型拆分
- 添加路由级 React.lazy 懒加载（49 页面按模块分包）
- 添加重型组件懒加载（ECharts 图表、代码编辑器等）
- 构建产物分析：确保首屏 JS < 500KB（gzip）

### V2.5 — 定时报告 + 邮件推送（P2）
- report-service 新增 `@Scheduled` 调度器，支持 Cron 表达式配置
- 新增定时报告配置表（report_schedule）：报告类型、频率、收件人、模板
- 实现定时报告生成 + SMTP 邮件推送
- 前端 ReportCenter 新增"定时报告"Tab：配置/启停/历史记录

## Impact

- **Affected code**:
  - `backend/parse-service/` — NerServiceImpl 重构、新增模型健康检查
  - `backend/upload-service/` — FileServiceImpl 分片合并重构
  - `backend/profile-service/` — 新增 Neo4j 集成层
  - `backend/report-service/` — 新增调度器、定时报告配置、邮件推送
  - `frontend/` — Vitest 配置、49 页面单元测试、vite.config 优化、路由懒加载、ReportCenter 定时报告
- **Affected docs**: API 文档（新增 2 个端点）、运维手册（新增 Neo4j 运维）、用户手册（定时报告操作）
- **New dependencies**: DJL (ai.djl:api + ai.djl:onnxruntime)、Neo4j (spring-boot-starter-data-neo4j)、Vitest + @testing-library/react

## ADDED Requirements

### Requirement: security-BERT 模型推理
系统 SHALL 使用 DJL 框架加载 security-BERT NER 模型，对文件文本进行 token 级实体识别，输出 BIO 标签解析后的实体列表。

#### Scenario: 模型加载成功
- **WHEN** parse-service 启动且模型路径有效
- **THEN** 模型状态为 READY，推理请求走模型路径

#### Scenario: 模型加载失败降级
- **WHEN** 模型文件缺失或 native 库不可用
- **THEN** 自动降级到正则兜底，日志记录降级原因，不影响主流程

#### Scenario: 模型推理异常降级
- **WHEN** 单次推理抛出异常
- **THEN** 该次请求降级到正则兜底，模型状态标记为不可用，后续请求走正则

### Requirement: MinIO composeObject 分片合并
系统 SHALL 使用 MinIO `composeObject` API 在服务端合并分片，替代应用层手动拼接。

#### Scenario: 大文件分片合并
- **WHEN** 分片上传完成后触发合并
- **THEN** 调用 composeObject 在 MinIO 服务端合并，应用层不下载分片内容

### Requirement: Neo4j 关系图谱后端
系统 SHALL 使用 Neo4j 图数据库存储和查询目标-文件-IOC-漏洞-攻击链的多跳关系。

#### Scenario: 多跳关系查询
- **WHEN** 用户查询目标的 3 跳关联实体
- **THEN** 通过 Cypher 查询返回关联节点和边，P99 < 100ms

### Requirement: 前端组件级单元测试
系统 SHALL 为前端核心组件提供 Vitest 单元测试，覆盖率 ≥80%。

#### Scenario: CI 集成
- **WHEN** CI 流水线执行 `npm run test:unit`
- **THEN** 所有测试通过且覆盖率报告生成

### Requirement: 定时报告调度
系统 SHALL 支持 Cron 表达式配置的定时报告自动生成与邮件推送。

#### Scenario: 定时触发
- **WHEN** 到达 Cron 配置时间
- **THEN** 自动生成报告并通过 SMTP 发送给配置的收件人

## MODIFIED Requirements

### Requirement: Vite 构建优化
V1 已有 manualChunks（react/antd/charts/query），V2 细化为 antd-core/antd-pro/echarts-core/echarts-charts，并添加路由级懒加载。

### Requirement: NER 服务降级策略
V1 降级策略为"模型不可用→正则兜底"，V2 新增"模型推理异常→正则兜底→模型状态标记不可用→定时重试恢复"。

## REMOVED Requirements

无移除项。所有 V1 功能保持兼容，V2 为增强而非替换。
