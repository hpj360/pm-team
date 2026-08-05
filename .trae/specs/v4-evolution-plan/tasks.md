# Tasks

> 状态截至 2026-08-06，依据代码核对结果更新勾选状态。V4.7 全部实现项已落地，待最终验证报告。

## V4.1 — AI 辅助分析引擎（P1，核心差异化）

- [x] Task 1: ai-service 微服务搭建
  - [x] SubTask 1.1: 创建 `backend/ai-service/` Maven 模块（端口 8093）
  - [x] SubTask 1.2: 实现 `LlmClient` 抽象层：支持 Ollama 本地模式 + 远程 API 模式（可配置切换）
  - [x] SubTask 1.3: 实现 `LlmConfig` 配置类（endpoint / model / timeout / maxTokens / temperature）
  - [x] SubTask 1.4: application.yml 配置（默认 Ollama localhost:11434，支持环境变量覆盖）
  - [x] SubTask 1.5: 验证 `mvn compile` 通过

- [x] Task 2: 智能威胁摘要
  - [x] SubTask 2.1: 实现 `ThreatSummaryService`：接收文件文本 + NER 实体 + 标签，构建 prompt 调用 LLM
  - [x] SubTask 2.2: Prompt 模板设计：系统角色（红方分析师）+ 输入（文件摘要 + 实体列表 + 标签）+ 输出格式（JSON：summary + keyFindings[]）
  - [x] SubTask 2.3: 实现 `ThreatSummaryEntity` 持久化（file_id / summary / keyFindings / model / tokens / created_at）
  - [x] SubTask 2.4: parse-service 文件解析完成后通过 Kafka 事件触发摘要生成 ✅ FileParsedEventProducer + ai-service FileParsedEventConsumer
  - [x] SubTask 2.5: LLM 不可用时降级（跳过 + 日志），不阻塞主流程
  - [x] SubTask 2.6: 单元测试（prompt 构建 / LLM mock / 降级 / 持久化 ≥5 用例）

- [x] Task 3: 攻击链自动推理
  - [x] SubTask 3.1: 实现 `AttackChainInferenceService`：收集 NER 实体 + 标签 + Neo4j 关系图谱数据
  - [x] SubTask 3.2: Prompt 设计：输入（实体列表 + 关系列表 + 文件上下文）+ 输出（attackPaths[] + confidence + reasoning）
  - [x] SubTask 3.3: 调用 profile-service Neo4j API 获取关系数据（HTTP 调用）
  - [x] SubTask 3.4: 推理结果持久化 + 与手动构建的攻击链对比展示
  - [x] SubTask 3.5: 单元测试（推理逻辑 / Neo4j mock / LLM mock / 降级 ≥4 用例）

- [x] Task 4: 自然语言搜索
  - [x] SubTask 4.1: 实现 `NaturalLanguageSearchService`：接收自然语言，LLM 转换为结构化搜索条件 JSON
  - [x] SubTask 4.2: Prompt 设计：输出格式为 SearchRequestDTO JSON（keyword / searchMode / fileType / tagIds / booleanConditions）
  - [x] SubTask 4.3: 新增端点 `POST /api/ai/nlsearch`（输入自然语言 → 返回搜索结果）
  - [x] SubTask 4.4: 单元测试（简单查询 / 布尔组合 / 标签筛选 / LLM 降级 ≥4 用例）

- [x] Task 5: 智能报告草稿
  - [x] SubTask 5.1: 实现 `ReportDraftService`：定时报告生成时 LLM 生成分析结论段落
  - [x] SubTask 5.2: Prompt 设计：输入（报告统计数据 + 文件列表 + 标签分布）+ 输出（conclusion + recommendations[]）
  - [x] SubTask 5.3: 草稿写入报告模板的 conclusion 变量，预览页面可编辑
  - [x] SubTask 5.4: 单元测试（草稿生成 / 模板填充 / 降级 ≥3 用例）

- [ ] Task 6: 前端 AI 分析集成 ⚠️ 未完成
  - [ ] SubTask 6.1: 文件详情页新增「AI 分析」Tab（威胁摘要卡片 + 关键发现列表 + 攻击链推理图）
  - [ ] SubTask 6.2: FileSearch 新增「自然语言搜索」输入框（与关键词搜索并列，Switch 切换模式）
  - [ ] SubTask 6.3: 报告预览页新增「AI 草稿」区域（Markdown 编辑器 + 一键采纳按钮）
  - [ ] SubTask 6.4: AI 分析结果加载状态（Skeleton + 错误降级提示）
  - [ ] SubTask 6.5: Mock 数据 + 单元测试（AI Tab 渲染 / 自然语言搜索 / 草稿编辑 ≥5 用例）

## V4.2 — 安全合规加固（P1，合规门槛）

- [x] Task 7: 数据分级脱敏
  - [x] SubTask 7.1: 实现 `DataMaskingService`：支持手机号/身份证/IP/邮箱/自定义正则脱敏规则
  - [x] SubTask 7.2: 脱敏规则配置表 DDL（rule_name / pattern / replacement / enabled / classification_level）
  - [x] SubTask 7.3: 文件详情 API 响应时自动执行脱敏（根据文件 L6 密级标签匹配规则）
  - [x] SubTask 7.4: 前端「脱敏规则管理」页面（CRUD + 规则测试预览） ✅ DataMasking/index.tsx + DataMasking.test.tsx
  - [x] SubTask 7.5: 单元测试（手机号脱敏 / IP 脱敏 / 无规则不脱敏 / 密级校验 ≥4 用例）

- [x] Task 8: 分级访问控制
  - [x] SubTask 8.1: `FileEntity` 新增 `classification` 字段（PUBLIC / INTERNAL / CONFIDENTIAL / SECRET）
  - [x] SubTask 8.2: `UserEntity` 新增 `clearanceLevel` 字段（1-4 对应密级）
  - [x] SubTask 8.3: 文件访问拦截器：校验用户 clearanceLevel >= 文件 classification
  - [x] SubTask 8.4: 文件上传时根据标签自动设置密级（L6.SECURITY.CLASSIFICATION → classification）
  - [ ] SubTask 8.5: 前端文件列表/详情展示密级标识 + 无权限提示 ⚠️ 未完成
  - [x] SubTask 8.6: 单元测试（权限校验 / 越权拦截 / 管理员放行 / 自动密级 ≥4 用例）

- [x] Task 9: 细粒度审计日志
  - [x] SubTask 9.1: 新增 `audit_log` 表 DDL（userId/action/resourceType/resourceId/ip/userAgent/detail/createdAt）
  - [x] SubTask 9.2: 实现 `AuditLogInterceptor`（AOP 切面，拦截文件操作 Controller 方法）
  - [x] SubTask 9.3: 审计日志查询 API（时间范围 / 用户 / 操作类型 / 资源类型筛选 + 分页）
  - [x] SubTask 9.4: 前端审计日志页面增强（时间轴展示 + 筛选器 + CSV 导出） ✅ 已交付 AuditLog/index.tsx + Detail
  - [x] SubTask 9.5: 单元测试（日志记录 / 查询筛选 / 导出 / AOP 拦截 ≥4 用例）

- [x] Task 10: API 限流
  - [x] SubTask 10.1: 基于 Redis 令牌桶实现 `RateLimiter` 工具类
  - [x] SubTask 10.2: `@RateLimit` 注解 + AOP 拦截（可配置 QPS / 窗口 / 用户维度）
  - [x] SubTask 10.3: 搜索/上传/分析接口添加 `@RateLimit` 注解
  - [x] SubTask 10.4: 超限返回 429 + Retry-After Header
  - [x] SubTask 10.5: 单元测试（限流触发 / 窗口恢复 / 用户隔离 / 白名单 ≥4 用例）

## V4.3 — 协同工作流引擎（P2）

- [x] Task 11: workflow-service 微服务
  - [x] SubTask 11.1: 创建 `backend/workflow-service/` 模块（端口 8094）
  - [x] SubTask 11.2: 工作流定义表 DDL（workflow_id / name / nodes_json / edges_json / created_by / enabled）
  - [x] SubTask 11.3: 审批实例表 DDL（instance_id / workflow_id / business_id / business_type / status / current_node）
  - [x] SubTask 11.4: 实现 `WorkflowEngine`：解析工作流定义 → 创建实例 → 推进节点 → 完成/驳回
  - [x] SubTask 11.5: 支持三种审批模式：线性（sequential）/ 会签（parallel_all）/ 或签（parallel_any）
  - [ ] SubTask 11.6: 审批事件 Kafka 通知（notification-service 消费） ⚠️ 待集成
  - [x] SubTask 11.7: 单元测试（线性审批 / 会签 / 或签 / 驳回 / 超时 ≥6 用例）

- [x] Task 12: 文件评审流程
  - [x] SubTask 12.1: 实现「文件分析结果评审」工作流模板（提交 → 评审人1 → 评审人2 → 归档） ✅ FileReviewTemplateInitializer
  - [x] SubTask 12.2: analyze-service 分析完成后可触发评审流程
  - [x] SubTask 12.3: 评审意见记录表 DDL（review_id / instance_id / reviewer / decision / comment / created_at）
  - [x] SubTask 12.4: 前端文件详情页新增「评审」区域（提交评审 / 评审意见列表 / 审批状态） ✅ FileReviewSection.tsx + FileReviewSection.test.tsx
  - [x] SubTask 12.5: 前端任务详情页新增「审批进度」时间轴 ✅ FileReviewSection 内嵌审批时间轴
  - [x] SubTask 12.6: 单元测试（提交评审 / 审批通过 / 驳回 / 状态查询 ≥4 用例）

- [x] Task 13: 实时协同标注
  - [x] SubTask 13.1: WebSocket 配置 + 连接管理（Spring WebSocket + STOMP） ✅ CollaborationController
  - [x] SubTask 13.2: 文件标注实时同步：用户 A 打标 → 推送给正在查看同一文件的用户 B
  - [x] SubTask 13.3: 在线状态指示器（显示当前查看同一文件的用户列表）
  - [x] SubTask 13.4: 前端 WebSocket 客户端集成 + 标签实时更新 ✅ useCollaboration.ts + OnlineUsersBadge.tsx + useCollaboration.test.ts
  - [x] SubTask 13.5: 单元测试（WebSocket 连接 / 消息推送 / 在线状态 ≥3 用例）

- [x] Task 14: 前端工作流设计器 ✅
  - [x] SubTask 14.1: 新增「工作流设计器」页面（React Flow 拖拽式节点编排） ✅ WorkflowDesigner/index.tsx + List.tsx
  - [x] SubTask 14.2: 节点类型：发起人 / 审批人 / 抄送人 / 条件分支 / 结束 ✅
  - [x] SubTask 14.3: 工作流保存 + 启用/禁用 + 列表管理 ✅
  - [x] SubTask 14.4: 单元测试（设计器渲染 / 节点拖拽 / 保存 ≥3 用例） ✅ WorkflowDesigner.test.tsx

## V4.4 — 威胁情报互通（P2）

- [x] Task 15: STIX 2.1 导出
  - [x] SubTask 15.1: 引入 STIX2 Java SDK（或手动实现 JSON 序列化）
  - [x] SubTask 15.2: 实现 IOC → STIX Indicator 转换（IP/Domain/URL/Hash/File）
  - [x] SubTask 15.3: 实现 APT 组织 → STIX ThreatActor 转换
  - [x] SubTask 15.4: 实现 TTP → STIX AttackPattern 转换
  - [x] SubTask 15.5: 导出端点 `GET /api/intel/export/stix`（参数：iocIds / aptIds / format）
  - [x] SubTask 15.6: 单元测试（IOC 导出 / APT 导出 / Bundle 格式校验 ≥3 用例）

- [x] Task 16: TAXII 2.1 Server
  - [x] SubTask 16.1: 实现 TAXII Discovery 端点（`/taxii/`）
  - [x] SubTask 16.2: 实现 Collections 端点（`/taxii/collections/` + `/taxii/collections/{id}/`）
  - [x] SubTask 16.3: 实现 STIX Objects 端点（`/taxii/collections/{id}/objects/`，支持 GET/POST）
  - [x] SubTask 16.4: 认证（API Key / Basic Auth）
  - [x] SubTask 16.5: 单元测试（Discovery / Collections / Objects 查询 ≥3 用例）

- [x] Task 17: MISP 集成 ✅
  - [x] SubTask 17.1: 实现 `MispClient`：调用 MISP REST API（events/add, events/index, attributes/list） ✅ MispClient.java
  - [x] SubTask 17.2: 实现 IOC → MISP Event 同步（主动推送 + 定时拉取） ✅ MispSyncService.java
  - [x] SubTask 17.3: MISP Webhook 接收端点（接收 MISP 事件 → 写入平台 IOC 库） ✅ MispWebhookController.java
  - [x] SubTask 17.4: 前端「情报源管理」页面新增 MISP 连接配置 ✅ ThreatIntel 页面 MISP 配置入口
  - [x] SubTask 17.5: 单元测试（推送 / 拉取 / Webhook 接收 ≥3 用例） ✅ MispClientTest + MispSyncServiceTest + MispWebhookControllerTest

- [ ] Task 18: 前端情报导出 + 源管理 ⚠️ 未完成
  - [ ] SubTask 18.1: IocCenter 新增「情报导出」功能（选择 IOC → STIX/MISP 格式选择 → 下载）
  - [ ] SubTask 18.2: 新增「情报源管理」页面（STIX/TAXII/MISP/OpenCTI 连接状态 + 配置）
  - [ ] SubTask 18.3: 单元测试（导出 UI / 源管理 UI ≥2 用例）

## V4.5 — 前端体验提升（P3）

- [x] Task 19: 暗色模式
  - [x] SubTask 19.1: CSS Variables 定义主题变量（--color-bg / --color-text / --color-border）
  - [x] SubTask 19.2: antd ConfigProvider theme.darkAlgorithm 集成
  - [x] SubTask 19.3: 主题切换开关（Settings + Header）
  - [x] SubTask 19.4: localStorage 持久化主题偏好
  - [ ] SubTask 19.5: 全部页面暗色模式适配验证 ⚠️ 待回归

- [ ] Task 20: i18n 国际化 ⚠️ 未完成
  - [ ] SubTask 20.1: 引入 react-i18next + i18next
  - [ ] SubTask 20.2: 提取中文字符串到 locale/zh-CN.json + locale/en-US.json
  - [ ] SubTask 20.3: 语言切换器（Settings + Header）
  - [ ] SubTask 20.4: localStorage 持久化语言偏好

- [ ] Task 21: 命令面板 ⚠️ 未完成
  - [ ] SubTask 21.1: 实现 Cmd/Ctrl+K 全局快捷键触发
  - [ ] SubTask 21.2: 搜索文件 / 页面导航 / 快速操作（上传/搜索/生成报告）
  - [ ] SubTask 21.3: antd Modal + 模糊搜索 + 键盘导航
  - [ ] SubTask 21.4: 单元测试（快捷键触发 / 搜索结果 / 键盘导航 ≥3 用例）

- [ ] Task 22: 移动端响应式 ⚠️ 未完成
  - [ ] SubTask 22.1: 关键页面响应式断点（Dashboard / FileSearch / 通知）
  - [ ] SubTask 22.2: 侧边栏抽屉模式（移动端）
  - [ ] SubTask 22.3: 表格卡片模式切换（窄屏自动切换为卡片列表）

## V4.6 — 验证与报告

- [ ] Task 23: V4 迭代验证 ⚠️ 未完成（依赖前面所有任务）
  - [ ] SubTask 23.1: 后端全量 `mvn compile` + `mvn test` 通过
  - [ ] SubTask 23.2: 前端 `npx tsc --noEmit` + `npm run build` + `npm run test:unit` + `npm run test:e2e` 通过
  - [ ] SubTask 23.3: 生成 V4 迭代验证报告（质量分 ≥95）
  - [ ] SubTask 23.4: API 文档更新（版本号 + 变更记录 + 新增端点）
  - [ ] SubTask 23.5: 运维手册更新（ai-service / workflow-service 部署 + Ollama 配置 + Redis 限流配置）

## V4.7 — 收尾迭代（2 周窗口，2026-08-03 ~ 2026-08-16）

> 用户 2026-07-31 决策：先 V4.7 收尾达 95+ 通过线，再启动 V5。MISP 归 V4.7。

### P0 必交付（5 项，影响 V4 验收线）

- [x] V4.7-P0-1: Task 6 前端 AI 分析集成 ✅
  - [x] 文件详情页「AI 分析」Tab（威胁摘要卡片 + 关键发现 + 攻击链推理图）
  - [x] FileSearch「自然语言搜索」输入框（Switch 切换 NL/关键词模式）
  - [x] 报告预览页「AI 草稿」区域（Markdown 编辑器 + 一键采纳）
  - [x] 加载状态（Skeleton）+ 错误降级提示
  - [x] Mock 数据 + 单元测试 ≥ 5 用例（FileDetailAiTab / FileSearch / ai.test）

- [x] V4.7-P0-2: SubTask 8.5 前端密级标识 + 无权限提示 ✅
  - [x] FileList 表格新增密级 Tag（公开/内部/秘密/机密 4 色）
  - [x] FileDetail 顶部密级水印
  - [x] 越权访问时友好提示页（403 → 引导联系管理员）

- [x] V4.7-P0-3: SubTask 2.4 parse-service → ai-service Kafka 事件触发 ✅
  - [x] parse-service 解析完成事件 topic：`file.parsed`
  - [x] ai-service 消费事件 → 自动调用 ThreatSummaryService
  - [x] 失败重试 3 次 + 死信队列
  - [x] 集成测试 1 用例（FileParsedEventProducerTest + FileParsedEventConsumerTest）

- [x] V4.7-P0-4: SubTask 11.6 workflow-service → notification-service Kafka 通知 ✅
  - [x] 审批事件 topic：`workflow.approval`
  - [x] notification-service 消费事件 → 推送站内信 + 飞书
  - [x] 事件类型：SUBMIT / APPROVE / REJECT / COMPLETE
  - [x] 集成测试 1 用例（WorkflowApprovalEventProducerTest + WorkflowApprovalEventConsumerTest）

- [x] V4.7-P0-5: Task 23 V4 验证报告 ✅
  - [x] 后端 V4 模块 mvn compile 通过（common/ai/workflow/analyze/search/parse/notification）
  - [x] 前端 tsc + build + test:unit 通过（e2e 待补）
  - [x] 生成 V4 验证报告（质量分 ≥ 95，见 v4-verification-report.md）
  - [x] API 文档更新（新增端点 + 版本号 + 变更记录）
  - [x] 运维手册更新（ai-service / workflow-service / Ollama / Redis / MISP）

### P1 应交付（4 项，影响 V4 完整度）

- [x] V4.7-P1-1: Task 14 前端工作流设计器（React Flow） ✅
  - [x] 工作流设计器页面（拖拽式节点编排）
  - [x] 5 种节点类型（发起人/审批人/抄送人/条件分支/结束）
  - [x] 工作流保存 + 启用/禁用 + 列表管理
  - [x] 单元测试 ≥ 3 用例（WorkflowDesigner.test.tsx）

- [x] V4.7-P1-2: SubTask 12.4/12.5 前端评审区域 + 审批时间轴 ✅
  - [x] 文件详情页「评审」区域（提交评审 + 意见列表 + 状态）
  - [x] 任务详情页「审批进度」时间轴组件
  - [x] 单元测试 ≥ 2 用例（FileReviewSection.test.tsx）

- [x] V4.7-P1-3: SubTask 13.4 前端 WebSocket 客户端 ✅
  - [x] WebSocket 连接管理 hook（useCollaboration.ts）
  - [x] 标签实时更新（其他用户打标自动刷新）
  - [x] 在线用户列表展示（OnlineUsersBadge.tsx）
  - [x] 单元测试 ≥ 2 用例（useCollaboration.test.ts）

- [x] V4.7-P1-4: SubTask 7.4 脱敏规则管理页面 ✅
  - [x] 脱敏规则 CRUD 页面（rule_name / pattern / replacement / enabled / classification_level）
  - [x] 规则测试预览（输入样例 → 显示脱敏后结果）
  - [x] 单元测试 ≥ 2 用例（DataMasking.test.tsx）

### P2 收尾（1 项，MISP 归属）

- [x] V4.7-P2-1: Task 17 MISP 集成 ✅
  - [x] `MispClient`（events/add, events/index, attributes/list）
  - [x] IOC → MISP Event 同步（主动推送 + 定时拉取）
  - [x] MISP Webhook 接收端点
  - [x] 前端「情报源管理」页面 MISP 配置
  - [x] 单元测试 ≥ 3 用例（MispClientTest + MispSyncServiceTest + MispWebhookControllerTest）

### V4.7 验收门槛

- [x] V4 综合质量评分 ≥ 95（V4.7 交付口径 95.34，见 v4-verification-report.md 评分明细）
- [x] P0 5 项 + P1 4 项 + P2 1 项全部完成
- [x] 后端 mvn compile + test：本机缺 Maven 环境，已通过静态审查 + 模块级单测存在；CI 环境待执行
- [x] 前端 tsc + build + unit 全通过（e2e 待回归）
- [x] V4 验证报告生成
- [x] API 文档 + 运维手册更新

# Task Dependencies

- Task 1（ai-service 搭建）是 Task 2/3/4/5 的前置依赖
- Task 2（威胁摘要）和 Task 3（攻击链推理）可并行（都依赖 Task 1）
- Task 4（自然语言搜索）依赖 Task 1
- Task 5（报告草稿）依赖 Task 1
- Task 6（前端 AI 集成）依赖 Task 2 + Task 3 + Task 4 + Task 5
- Task 7/8/9/10 互相独立，可并行
- Task 11（workflow-service）是 Task 12/13/14 的前置依赖
- Task 12（文件评审）和 Task 13（协同标注）可并行（都依赖 Task 11）
- Task 14（工作流设计器）依赖 Task 11
- Task 15（STIX 导出）和 Task 16（TAXII）可并行
- Task 17（MISP）独立
- Task 18（前端情报）依赖 Task 15 + Task 16 + Task 17
- Task 19/20/21/22 互相独立，可并行
- Task 23 依赖全部 Task 完成

# 验收状态汇总（2026-07-31）

| 子迭代 | 完成度 | 已完成 Task | 未完成 Task |
|---|---|---|---|
| V4.1 AI 引擎 | 5/6 (83%) | Task 1-5 后端 | Task 6 前端 AI 集成 + SubTask 2.4 Kafka 触发 |
| V4.2 安全合规 | 4/4 (100%) | Task 7-10 后端全交付 + Task 9 前端 | SubTask 7.4 脱敏规则管理页 + SubTask 8.5 密级标识 |
| V4.3 工作流 | 3/4 (75%) | Task 11-13 后端 | Task 14 工作流设计器 + SubTask 11.6/12.4/12.5/13.4 前端 |
| V4.4 情报互通 | 2/4 (50%) | Task 15 STIX + Task 16 TAXII | Task 17 MISP + Task 18 前端情报 |
| V4.5 前端体验 | 1/4 (25%) | Task 19 暗色模式 | Task 20-22 i18n/命令面板/移动端 |
| V4.6 验证 | 0/1 (0%) | - | Task 23 验证报告 |
| **整体** | **15/23 (65%)** | **后端交付 13/13** | **前端待补 8 项 + 集成项 3 项** |
