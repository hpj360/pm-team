# Tasks

## V2.1 — security-BERT DJL 模型集成（P1）

- [x] Task 1: 添加 DJL 依赖并实现模型加载
  - [x] SubTask 1.1: parse-service pom.xml 添加 `ai.djl:api`、`ai.djl:onnxruntime`、`ai.djl:tokenizers` 依赖
  - [x] SubTask 1.2: 实现 `NerServiceImpl.loadDjlModel()`：使用 DJL Criteria 加载本地 ONNX 模型 + tokenizer
  - [x] SubTask 1.3: 实现 `NerServiceImpl.extractByModel()`：调用 Predictor 推理，解析 BIO 标签为 `NerEntityVO`
  - [x] SubTask 1.4: 新增模型状态定时重试恢复机制（每 5 分钟检查模型可用性）
  - [x] SubTask 1.5: 新增 `GET /api/parse/ner/model-status` 健康检查端点
  - [x] SubTask 1.6: 新增 Prometheus 指标（推理延迟、吞吐量、降级次数）
  - [x] SubTask 1.7: 编写单元测试（模型加载/推理/降级/重试 每路径至少 3 用例）
  - [x] SubTask 1.8: 验证 `mvn compile` + `mvn test` 通过

## V2.2 — MinIO composeObject + Neo4j 关系图谱（P2）

- [x] Task 2: MinIO composeObject 分片合并重构
  - [x] SubTask 2.1: 重构 `FileServiceImpl.completeMultipartUpload()`：使用 MinIO `composeObject` API
  - [x] SubTask 2.2: 添加 composeObject 失败回退到应用层合并的降级逻辑
  - [x] SubTask 2.3: 更新单元测试验证 composeObject 路径
  - [x] SubTask 2.4: 验证 `mvn compile` + `mvn test` 通过

- [x] Task 3: Neo4j 关系图谱后端
  - [x] SubTask 3.1: profile-service pom.xml 添加 `spring-boot-starter-data-neo4j` 依赖
  - [x] SubTask 3.2: application.yml 添加 Neo4j 连接配置
  - [x] SubTask 3.3: 新增 Neo4j 节点实体（TargetNode / FileNode / IocNode / VulnNode / AttackChainNode）
  - [x] SubTask 3.4: 新增 Neo4j 关系实体（CONTAINS / RELATES_TO / EXPLOITS / TARGETS）
  - [x] SubTask 3.5: 新增 `Neo4jRelationRepository`：Cypher 多跳查询（1-3 跳）
  - [x] SubTask 3.6: 新增 `GET /api/profile/relations/{targetId}?depth=3` 端点
  - [x] SubTask 3.7: 前端 RelationGraph 页面增加 API 数据源切换（Mock / Neo4j）
  - [x] SubTask 3.8: 编写集成测试验证 Cypher 查询正确性
  - [x] SubTask 3.9: 验证 `mvn compile` + `mvn test` 通过

## V2.3 — 前端组件级单元测试（P2）

- [x] Task 4: 搭建 Vitest 测试环境
  - [x] SubTask 4.1: 安装 `vitest`、`@testing-library/react`、`@testing-library/jest-dom`、`@testing-library/user-event`、`jsdom`
  - [x] SubTask 4.2: 创建 `vitest.config.ts`（复用 vite alias，设置 jsdom 环境）
  - [x] SubTask 4.3: 添加 `package.json` scripts：`test:unit`、`test:unit:coverage`
  - [x] SubTask 4.4: 创建 `src/test/setup.ts`（jest-dom 匹配器 + Mock 全局）

- [x] Task 5: 编写核心组件单元测试
  - [x] SubTask 5.1: utils 测试（`utils/index.tsx`、`utils/accessibility.ts`、`utils/echarts.ts`）
  - [x] SubTask 5.2: stores 测试（auth store、file store、theme store）
  - [x] SubTask 5.3: 核心页面测试（Login、Dashboard、FileUpload、FileSearch、FileList — 每页至少 3 用例）
  - [x] SubTask 5.4: 红方页面测试（TargetProfile、ThreatIntel、AttackChain、RelationGraph — 每页至少 2 用例）
  - [x] SubTask 5.5: 后台页面测试（UserManage、ReportCenter、NotificationCenter — 每页至少 2 用例）
  - [x] SubTask 5.6: 验证 `npm run test:unit` 通过且覆盖率 ≥80%

## V2.4 — 前端构建性能优化（P3）

- [x] Task 6: Vite manualChunks 细化 + 路由懒加载
  - [x] SubTask 6.1: vite.config.ts 细化 manualChunks（antd-core/antd-pro/echarts-core/echarts-charts）
  - [x] SubTask 6.2: `router/index.tsx` 全部 49 页面改为 `React.lazy` + `Suspense` 懒加载
  - [x] SubTask 6.3: 重型组件懒加载（ECharts 图表组件用 `React.lazy`）
  - [x] SubTask 6.4: 构建产物分析，验证首屏 JS < 500KB（gzip）
  - [x] SubTask 6.5: 验证 `npm run build` 通过 + `npx tsc --noEmit` 零错误

## V2.5 — 定时报告 + 邮件推送（P2）

- [x] Task 7: report-service 定时调度器 + 邮件推送
  - [x] SubTask 7.1: 新增 `report_schedule` 表（id/report_type/cron/recipients/template/status/created_at）
  - [x] SubTask 7.2: 新增 `ReportScheduleEntity` + `ReportScheduleMapper`
  - [x] SubTask 7.3: 新增 `ReportSchedulerService`：`@Scheduled` 定时扫描 + 触发报告生成
  - [x] SubTask 7.4: 新增 `EmailService`：SMTP 邮件发送（HTML 附件）
  - [x] SubTask 7.5: 新增 REST 端点：`POST /api/report/schedules`（创建）、`GET /api/report/schedules`（列表）、`PUT /api/report/schedules/{id}/toggle`（启停）
  - [x] SubTask 7.6: 前端 ReportCenter 新增"定时报告"Tab：配置表单 + 列表 + 启停 + 历史记录
  - [x] SubTask 7.7: 编写单元测试（调度触发/邮件发送/配置CRUD）
  - [x] SubTask 7.8: 验证 `mvn compile` + `mvn test` 通过

## V2.6 — 文档更新

- [x] Task 8: 更新项目文档
  - [x] SubTask 8.1: API 文档新增 NER 模型状态端点 + 关系图谱端点 + 定时报告端点
  - [x] SubTask 8.2: 运维手册新增 Neo4j 部署/运维章节
  - [x] SubTask 8.3: 用户手册新增定时报告操作指南
  - [x] SubTask 8.4: 生成 V2 迭代验证报告（质量分 ≥95）

# Task Dependencies

- Task 2 和 Task 3 可并行（MinIO 和 Neo4j 互不依赖）
- Task 4 和 Task 6 可并行（测试环境和构建优化互不依赖）
- Task 5 依赖 Task 4（需要先搭建测试环境）
- Task 7 独立于 Task 1-6，可并行
- Task 8 依赖 Task 1-7 全部完成
