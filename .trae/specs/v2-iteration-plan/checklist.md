# V2 迭代验收检查清单

## V2.1 — security-BERT DJL 模型集成
- [x] parse-service pom.xml 包含 ai.djl:api + ai.djl:onnxruntime + ai.djl:tokenizers 依赖
- [x] NerServiceImpl.loadDjlModel() 使用 DJL Criteria 加载模型，不再抛出 IOException 占位
- [x] NerServiceImpl.extractByModel() 调用 Predictor 推理并解析 BIO 标签为 NerEntityVO
- [x] 模型加载失败时自动降级到正则兜底，不影响 bean 初始化
- [x] 模型推理异常时降级到正则，标记 modelReady=false
- [x] 新增定时重试恢复机制（模型不可用后定期重试）
- [x] GET /api/parse/ner/model-status 端点返回模型状态（READY/FALLBACK/FAILED）
- [x] Prometheus 指标端点暴露 ner_inference_latency / ner_fallback_count
- [x] 单元测试覆盖模型加载/推理/降级/重试 每路径至少 3 用例
- [x] mvn compile + mvn test 通过

## V2.2 — MinIO composeObject + Neo4j
- [x] FileServiceImpl.completeMultipartUpload() 使用 MinIO composeObject API
- [x] composeObject 失败时回退到应用层合并
- [x] 单元测试覆盖 composeObject 路径和降级路径
- [x] profile-service pom.xml 包含 spring-boot-starter-data-neo4j
- [x] Neo4j 节点实体定义完整（Target/File/Ioc/Vuln/AttackChain）
- [x] Neo4j 关系实体定义完整（CONTAINS/RELATES_TO/EXPLOITS/TARGETS）
- [x] Cypher 多跳查询（1-3 跳）返回正确节点和边
- [x] GET /api/profile/relations/{targetId}?depth=3 端点可用
- [x] 前端 RelationGraph 页面支持 Mock/Neo4j 数据源切换
- [x] 集成测试验证 Cypher 查询正确性
- [x] mvn compile + mvn test 通过

## V2.3 — 前端组件级单元测试
- [x] vitest + @testing-library/react + jsdom 依赖已安装
- [x] vitest.config.ts 配置正确（复用 vite alias + jsdom 环境）
- [x] package.json 包含 test:unit 和 test:unit:coverage 脚本
- [x] src/test/setup.ts 配置 jest-dom 匹配器
- [x] utils 单元测试覆盖（index.tsx / accessibility.ts / echarts.ts）
- [x] stores 单元测试覆盖（auth / file / theme）
- [x] 核心页面单元测试（Login/Dashboard/FileUpload/FileSearch/FileList 每页≥3用例）
- [x] 红方页面单元测试（TargetProfile/ThreatIntel/AttackChain/RelationGraph 每页≥2用例）
- [x] 后台页面单元测试（UserManage/ReportCenter/NotificationCenter 每页≥2用例）
- [x] npm run test:unit 全部通过
- [x] 测试覆盖率 ≥80%

## V2.4 — 前端构建性能优化
- [x] vite.config.ts manualChunks 细化为 antd-core/antd-pro/echarts-core/echarts-charts
- [x] router/index.tsx 全部 49 页面使用 React.lazy + Suspense
- [x] 重型组件（ECharts 图表）使用 React.lazy 懒加载
- [x] npm run build 构建通过
- [x] npx tsc --noEmit 零错误
- [x] 首屏 JS < 500KB（gzip）
- [x] 构建产物分析报告生成

## V2.5 — 定时报告 + 邮件推送
- [x] report_schedule 表已创建（DDL 迁移脚本）
- [x] ReportScheduleEntity + ReportScheduleMapper 实现
- [x] ReportSchedulerService @Scheduled 定时扫描触发报告生成
- [x] EmailService SMTP 邮件发送（HTML 附件）
- [x] POST /api/report/schedules 创建定时报告配置
- [x] GET /api/report/schedules 列表查询
- [x] PUT /api/report/schedules/{id}/toggle 启停
- [x] 前端 ReportCenter "定时报告" Tab 实现（配置/列表/启停/历史）
- [x] 单元测试覆盖调度触发/邮件发送/配置CRUD
- [x] mvn compile + mvn test 通过

## V2.6 — 文档更新
- [x] API 文档新增 NER 模型状态端点 + 关系图谱端点 + 定时报告端点
- [x] 运维手册新增 Neo4j 部署/运维章节
- [x] 用户手册新增定时报告操作指南
- [x] V2 迭代验证报告生成（质量分 ≥95）

## 综合验证
- [x] 所有后端微服务 mvn compile + mvn test 通过
- [x] 前端 npx tsc --noEmit 零错误
- [x] 前端 npm run build 通过
- [x] 前端 npm run test:unit 通过且覆盖率 ≥80%
- [x] 前端 npm run test:e2e 通过（56 用例无回归）
- [x] V2 迭代验证报告质量分 ≥95
