# V4 平台演进验收检查清单

## V4.1 — AI 辅助分析引擎

### ai-service 微服务
- [ ] `backend/ai-service/` 模块已创建（端口 8093）
- [ ] `LlmClient` 抽象层支持 Ollama + 远程 API 两种模式
- [ ] `LlmConfig` 配置类（endpoint / model / timeout / maxTokens / temperature）
- [ ] application.yml 配置完成
- [ ] mvn compile 通过

### 智能威胁摘要
- [ ] `ThreatSummaryService` 已实现
- [ ] Prompt 模板设计完成（系统角色 + 输入格式 + 输出 JSON）
- [ ] `ThreatSummaryEntity` 持久化
- [ ] parse-service Kafka 事件触发摘要生成
- [ ] LLM 不可用时降级
- [ ] 单元测试 ≥5 用例

### 攻击链自动推理
- [ ] `AttackChainInferenceService` 已实现
- [ ] Prompt 设计完成（实体 + 关系 + 上下文 → attackPaths）
- [ ] 调用 profile-service Neo4j API 获取关系数据
- [ ] 推理结果持久化
- [ ] 单元测试 ≥4 用例

### 自然语言搜索
- [ ] `NaturalLanguageSearchService` 已实现
- [ ] Prompt 输出 SearchRequestDTO JSON
- [ ] `POST /api/ai/nlsearch` 端点
- [ ] 单元测试 ≥4 用例

### 智能报告草稿
- [ ] `ReportDraftService` 已实现
- [ ] 草稿写入报告模板 conclusion 变量
- [ ] 单元测试 ≥3 用例

### 前端 AI 分析集成
- [ ] 文件详情页「AI 分析」Tab
- [ ] FileSearch「自然语言搜索」输入框
- [ ] 报告预览页「AI 草稿」区域
- [ ] 加载状态 + 错误降级提示
- [ ] 单元测试 ≥5 用例

## V4.2 — 安全合规加固

### 数据分级脱敏
- [ ] `DataMaskingService` 支持 5 种脱敏规则
- [ ] 脱敏规则配置表 DDL
- [ ] 文件详情 API 自动脱敏
- [ ] 前端脱敏规则管理页面
- [ ] 单元测试 ≥4 用例

### 分级访问控制
- [ ] `FileEntity.classification` 字段
- [ ] `UserEntity.clearanceLevel` 字段
- [ ] 文件访问拦截器（RBAC + 密级双校验）
- [ ] 文件上传自动密级设置
- [ ] 前端密级标识 + 无权限提示
- [ ] 单元测试 ≥4 用例

### 细粒度审计日志
- [ ] `audit_log` 表 DDL
- [ ] `AuditLogInterceptor` AOP 切面
- [ ] 审计日志查询 API（筛选 + 分页）
- [ ] 前端审计日志页面增强（时间轴 + 筛选 + 导出）
- [ ] 单元测试 ≥4 用例

### API 限流
- [ ] Redis 令牌桶 `RateLimiter`
- [ ] `@RateLimit` 注解 + AOP
- [ ] 搜索/上传/分析接口限流
- [ ] 429 + Retry-After 返回
- [ ] 单元测试 ≥4 用例

## V4.3 — 协同工作流引擎

### workflow-service 微服务
- [ ] `backend/workflow-service/` 模块（端口 8094）
- [ ] 工作流定义表 + 审批实例表 DDL
- [ ] `WorkflowEngine`（线性 / 会签 / 或签）
- [ ] Kafka 通知集成
- [ ] 单元测试 ≥6 用例

### 文件评审流程
- [ ] 评审工作流模板
- [ ] analyze-service 触发评审
- [ ] 评审意见记录表
- [ ] 前端评审区域 + 审批时间轴
- [ ] 单元测试 ≥4 用例

### 实时协同标注
- [ ] WebSocket + STOMP 配置
- [ ] 标注实时同步推送
- [ ] 在线状态指示器
- [ ] 前端 WebSocket 集成
- [ ] 单元测试 ≥3 用例

### 前端工作流设计器
- [ ] React Flow 拖拽式节点编排
- [ ] 5 种节点类型
- [ ] 保存 + 启用/禁用 + 列表管理
- [ ] 单元测试 ≥3 用例

## V4.4 — 威胁情报互通

### STIX 2.1 导出
- [ ] STIX2 JSON 序列化
- [ ] IOC → STIX Indicator 转换
- [ ] APT → STIX ThreatActor 转换
- [ ] TTP → STIX AttackPattern 转换
- [ ] `GET /api/intel/export/stix` 端点
- [ ] 单元测试 ≥3 用例

### TAXII 2.1 Server
- [ ] Discovery 端点 `/taxii/`
- [ ] Collections 端点
- [ ] STIX Objects 端点
- [ ] 认证（API Key / Basic Auth）
- [ ] 单元测试 ≥3 用例

### MISP 集成
- [ ] `MispClient` REST API 调用
- [ ] IOC → MISP Event 同步（推送 + 拉取）
- [ ] MISP Webhook 接收端点
- [ ] 前端 MISP 连接配置
- [ ] 单元测试 ≥3 用例

### 前端情报导出 + 源管理
- [ ] IocCenter 情报导出功能（STIX/MISP 格式选择）
- [ ] 情报源管理页面
- [ ] 单元测试 ≥2 用例

## V4.5 — 前端体验提升

### 暗色模式
- [ ] CSS Variables 主题变量
- [ ] antd ConfigProvider darkAlgorithm
- [ ] 主题切换开关
- [ ] localStorage 持久化
- [ ] 全页面适配验证

### i18n 国际化
- [ ] react-i18next 引入
- [ ] zh-CN.json + en-US.json
- [ ] 语言切换器
- [ ] localStorage 持久化

### 命令面板
- [ ] Cmd/Ctrl+K 全局快捷键
- [ ] 文件/页面/操作搜索
- [ ] 键盘导航
- [ ] 单元测试 ≥3 用例

### 移动端响应式
- [ ] 关键页面响应式断点
- [ ] 侧边栏抽屉模式
- [ ] 表格卡片模式切换

## V4.6 — 综合验证

- [ ] 所有后端微服务 mvn compile + mvn test 通过
- [ ] 前端 npx tsc --noEmit 零错误
- [ ] 前端 npm run build 通过
- [ ] 前端 npm run test:unit 通过且覆盖率 ≥80%
- [ ] 前端 npm run test:e2e 通过（无回归）
- [ ] V4 迭代验证报告质量分 ≥95
- [ ] API 文档版本号 + 变更记录已更新
- [ ] 运维手册更新（ai-service / workflow-service / Redis / Ollama）
