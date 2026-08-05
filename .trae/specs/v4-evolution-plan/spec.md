# V4 平台演进计划 Spec

## Why

V1-V3 三轮迭代已完成核心功能开发（140 端点 / 245 测试 / 综合评分 96.30），平台具备文件汇聚→解析→检索→分析→画像→报告的完整闭环。产品验收审查发现以下演进方向：

1. **AI 辅助分析能力缺失**：当前 NER + YARA 仅做实体提取和规则匹配，缺乏 LLM 驱动的威胁摘要、攻击链自动推理、智能报告草稿生成。红方分析师需手动阅读全文、拼接线索，效率瓶颈明显
2. **安全合规执行力度不足**：L6 合规标签已定义但无强制执行——敏感数据未自动脱敏、操作审计粒度粗、分级访问控制未落地，不满足等保 2.0 / 数据安全法要求
3. **协同工作流碎片化**：任务管理 + 飞书通知各自独立，缺乏审批链、工作流编排、实时协同标注，多人协作场景效率低
4. **威胁情报互操作性差**：IOC 数据锁在平台内部，无法以 STIX/TAXII 标准格式导出或与 MISP/OpenCTI 互通，情报共享靠手工
5. **前端体验待提升**：无暗色模式、无 i18n、无键盘快捷键、移动端不可用，长时间分析作业体验不佳

V4 聚焦于 **AI 赋能** + **安全合规加固** + **协同工作流** + **情报互通** 四个方向，将平台从"文件管理工具"升级为"AI 驱动的红方协作分析平台"。

## What Changes

### V4.1 — AI 辅助分析引擎（P1，核心差异化）
- 后端：新增 `ai-service` 微服务，封装 LLM 调用（支持本地 Ollama / 远程 API 两种模式）
- 后端：实现「智能威胁摘要」——文件解析后 LLM 自动生成 3-5 句威胁摘要 + 关键发现列表
- 后端：实现「攻击链自动推理」——基于 NER 实体 + 标签 + 关系图谱，LLM 推理可能攻击路径
- 后端：实现「智能报告草稿」——定时报告触发时 LLM 生成分析结论段落草稿
- 后端：实现「自然语言搜索」——用户输入自然语言描述，LLM 转换为结构化搜索条件
- 前端：文件详情页新增「AI 分析」Tab（威胁摘要 + 关键发现 + 建议行动）
- 前端：FileSearch 新增「自然语言搜索」输入框（与关键词搜索并列）
- 前端：报告预览页新增「AI 草稿」区域（可编辑 + 一键采纳）

### V4.2 — 安全合规加固（P1，合规门槛）
- 后端：实现「数据分级脱敏」——L6 标签标记的敏感文件，详情页自动脱敏（手机号/身份证/IP 可配置）
- 后端：实现「分级访问控制」——文件按密级（公开/内部/秘密/机密）限制访问，RBAC + 密级双校验
- 后端：实现「细粒度审计日志」——所有文件操作（查看/下载/打标/删除）记录 userId+action+resource+ip+timestamp
- 后端：实现「API 限流」——基于 Redis 令牌桶，按用户 + 端点维度限流（可配置 QPS）
- 前端：新增「审计日志」查询页面增强（时间轴展示 + 操作类型筛选 + 用户筛选 + 导出）
- 前端：文件详情页敏感文件显示「密级标识」水印
- 前端：系统配置新增「脱敏规则」管理页面

### V4.3 — 协同工作流引擎（P2）
- 后端：新增 `workflow-service` 微服务，实现审批链编排（线性/会签/或签）
- 后端：文件分析结果新增「评审」流程——提交评审 → 评审人审批 → 通过/驳回 → 归档
- 后端：实现「实时协同标注」——WebSocket 推送，多用户同时标注同一文件时实时同步
- 后端：任务管理新增「工作流绑定」——任务状态变更触发审批链
- 前端：新增「工作流设计器」页面（拖拽式审批节点编排）
- 前端：文件详情页新增「评审」区域（提交评审 + 评审意见 + 审批状态）
- 前端：任务详情页新增「审批进度」时间轴

### V4.4 — 威胁情报互通（P2）
- 后端：实现 STIX 2.1 标准导出——IOC/APT/TTP 数据导出为 STIX JSON Bundle
- 后端：实现 TAXII 2.1 Server——提供 `/taxii/collections` / `/taxii/stix` 端点供外部订阅
- 后端：实现 MISP 集成——MISP Webhook 接收 + 主动同步 IOC 事件
- 后端：实现 OpenCTI 集成——通过 GraphQL API 双向同步威胁实体
- 前端：IocCenter 新增「情报导出」功能（选择 IOC → 导出 STIX/MISP 格式）
- 前端：新增「情报源管理」页面（配置 STIX/TAXII/MISP/OpenCTI 连接）

### V4.5 — 前端体验提升（P3）
- 前端：暗色模式（CSS Variables + antd ConfigProvider theme.darkAlgorithm）
- 前端：i18n 国际化（react-i18next，中文/英文双语）
- 前端：命令面板（Cmd/Ctrl+K 快速搜索文件/页面/操作）
- 前端：移动端响应式适配（关键页面：Dashboard / FileSearch / 通知）

## Impact

- **新增微服务**: `ai-service`（端口 8093）、`workflow-service`（端口 8094）
- **Affected code**:
  - `backend/ai-service/` — 全新微服务
  - `backend/workflow-service/` — 全新微服务
  - `backend/common/` — 新增审计/脱敏/限流/STIX 工具类
  - `backend/parse-service/` — 解析后触发 AI 摘要
  - `backend/search-service/` — 自然语言搜索 + STIX 导出
  - `backend/profile-service/` — 攻击链推理 + OpenCTI 同步
  - `backend/auth-service/` — 分级访问控制
  - `backend/notification-service/` — 工作流通知
  - `frontend/` — AI 分析 Tab + 审计增强 + 工作流设计器 + 情报导出 + 暗色模式 + i18n
- **New dependencies**: Ollama Java Client（可选）、STIX2 Java SDK、Redis（限流）
- **Affected docs**: API 文档、用户手册、运维手册（新增 AI/合规/工作流/情报互通章节）
- **Affected specs**: 无已有 spec 受影响，V4 为全新增量

## ADDED Requirements

### Requirement: AI 智能威胁摘要
系统 SHALL 在文件解析完成后，自动调用 LLM 生成威胁摘要和关键发现。

#### Scenario: 自动生成摘要
- **WHEN** 文件解析 + NER 识别完成
- **THEN** LLM 基于文件文本 + NER 实体 + 标签生成 3-5 句威胁摘要 + 关键发现列表

#### Scenario: LLM 不可用降级
- **WHEN** LLM 服务不可用
- **THEN** 跳过摘要生成，记录日志，不影响文件解析主流程

### Requirement: 自然语言搜索
系统 SHALL 支持用户输入自然语言描述，LLM 自动转换为结构化搜索条件。

#### Scenario: 自然语言转搜索
- **WHEN** 用户输入"查找所有包含 APT28 相关 IP 的 PDF 文件"
- **THEN** LLM 解析为 {keyword: "APT28", fileType: "pdf", tags: ["IP"]} 并执行搜索

### Requirement: 数据分级脱敏
系统 SHALL 对标记为敏感的文件内容执行自动脱敏。

#### Scenario: IP 脱敏
- **WHEN** 文件标记为 L6.SECURITY.CLASSIFICATION.SECRET 且配置了 IP 脱敏规则
- **THEN** 文件详情页展示时 IP 地址被替换为 `xxx.xxx.x.1`

#### Scenario: 可配置脱敏规则
- **WHEN** 管理员在脱敏规则页面配置手机号脱敏
- **THEN** 所有标记敏感的文件中手机号被替换为 `138****8888`

### Requirement: 分级访问控制
系统 SHALL 对文件按密级实施访问控制，用户权限 + 密级双校验。

#### Scenario: 密级权限校验
- **WHEN** 普通用户尝试查看"机密"级别文件
- **THEN** 返回 403 禁止访问，记录审计日志

#### Scenario: 管理员访问
- **WHEN** 管理员查看任意密级文件
- **THEN** 正常展示，记录审计日志

### Requirement: API 限流
系统 SHALL 对 API 请求按用户 + 端点维度实施限流。

#### Scenario: 超出限流
- **WHEN** 用户 1 秒内调用搜索接口超过配置的 10 次
- **THEN** 返回 429 Too Many Requests，提示稍后重试

### Requirement: 审批工作流
系统 SHALL 支持线性/会签/或签三种审批模式的工作流编排。

#### Scenario: 线性审批
- **WHEN** 分析师提交文件分析结果评审
- **THEN** 按配置的审批链依次通知审批人，全部通过后归档

#### Scenario: 驳回返工
- **WHEN** 审批人驳回评审
- **THEN** 结果退回提交人，记录驳回原因，任务状态变为"待修改"

### Requirement: STIX 2.1 导出
系统 SHALL 支持将 IOC/APT/TTP 数据导出为 STIX 2.1 标准 JSON Bundle。

#### Scenario: IOC 导出
- **WHEN** 用户在 IocCenter 选择 IOC 并点击"导出 STIX"
- **THEN** 生成符合 STIX 2.1 规范的 JSON Bundle 文件下载

### Requirement: TAXII 2.1 Server
系统 SHALL 提供 TAXII 2.1 兼容端点，供外部系统订阅威胁情报。

#### Scenario: 外部订阅
- **WHEN** 外部系统通过 TAXII 客户端请求 `/taxii/collections`
- **THEN** 返回可用的情报集合列表

## MODIFIED Requirements

### Requirement: 文件详情页（增强）
V3 展示文件元信息 + NER 实体 + 标签。V4 新增 AI 分析 Tab（威胁摘要 + 关键发现 + 攻击链推理）+ 评审区域 + 密级水印。

### Requirement: 文件搜索（增强）
V3 支持布尔/二次/标签/模板/历史。V4 新增自然语言搜索模式。

### Requirement: 审计日志（增强）
V2 有基本操作日志。V4 新增细粒度文件操作审计 + 时间轴展示 + 导出功能。

### Requirement: 任务管理（增强）
V2 支持任务 CRUD + 状态机。V4 新增工作流绑定 + 审批进度时间轴。

## REMOVED Requirements

无移除项。
