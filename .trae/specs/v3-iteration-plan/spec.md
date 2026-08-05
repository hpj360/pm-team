# V3 迭代增强计划 Spec

## Why

V2 迭代已完成全部验收（综合质量 95.60 分），5 项 V1 遗留问题全部修复。但仍有以下待落地内容：

1. **标签体系已设计未实现**：`tag-system-design` spec 已完成六层架构设计与评审（质量分≥90），但代码层面尚未落地——标签字典表未建、自动识别规则未编码、前端标签管理 UI 未开发
2. **V2 遗留 6 项 P3 问题**：节假日日历、Slack/钉钉推送、Neo4j GDS 图算法、Postman 集合、antd-pro chunk 拆分、Monitor 单测补齐
3. **文件搜索增强未文档化**：布尔组合检索（AND/OR/NOT）与二次检索已在代码层面实现，但 API 文档/用户手册未同步更新
4. **搜索体验待提升**：用户无法保存常用搜索条件、无搜索历史，高频检索场景效率低

本迭代聚焦于**标签体系落地**（将设计文档转化为可用功能）+ **搜索体验闭环**（文档同步 + 搜索模板）+ **V2 遗留清零**。

## What Changes

### V3.1 — 标签体系落地（P1，核心功能）
- 后端：新增 `tag_dict_v2` 标签字典表 + `file_tags` 文件标签关联表 + DDL 迁移
- 后端：实现标签 CRUD REST 端点（标签字典管理、文件打标/取消打标、按标签检索文件）
- 后端：实现四类自动识别规则引擎（REGEX 正则 / DICT 字典 / ML 模型 / ASSOC 关联推导）
- 后端：在 parse-service 文件解析完成后自动触发标签识别，产出标签写入 `file_tags`
- 前端：新增「标签管理」页面（标签字典 CRUD + 层级树展示 + 启用/禁用）
- 前端：FileSearch / FileList 页面增加标签筛选 facet + 标签列展示
- 前端：文件详情页增加标签展示与手动打标操作

### V3.2 — 搜索体验闭环（P2）
- 文档：API 文档新增布尔组合检索 + 二次检索 + 标签检索端点说明
- 文档：用户手册新增布尔检索 / 二次检索 / 标签筛选操作指南
- 后端：新增「搜索模板」表 + REST 端点（保存/列表/删除/应用搜索条件）
- 前端：FileSearch 页面增加「保存搜索」按钮 + 搜索模板下拉选择
- 前端：FileSearch 页面增加搜索历史记录（最近 20 条，localStorage 存储）

### V3.3 — V2 遗留 P3 清零（P3）
- 定时报告节假日日历支持（中国法定节假日 + 调休，跳过执行）
- 定时报告 Slack / 钉钉 Webhook 推送通道（与邮件并列）
- Neo4j GDS 图算法接入（PageRank / Community Detection，可选模块）
- Postman 集合同步更新（V2 新增 9 端点 + V3 新增端点）
- antd-pro chunk 进一步拆分（ProTable / ProForm / ProDescriptions 独立分包）
- Monitor 页面单元测试补齐（覆盖率 ≥ 80%）

## Impact

- **Affected code**:
  - `backend/common/` — 新增 TagDictEntity / FileTagEntity / TagDTO
  - `backend/parse-service/` — 文件解析后触发标签识别
  - `backend/search-service/` — 标签筛选 + 搜索模板端点
  - `backend/report-service/` — 节假日日历 + Slack/钉钉推送
  - `backend/profile-service/` — Neo4j GDS 图算法（可选）
  - `frontend/` — 标签管理页面 + FileSearch/FileList/FileDetail 标签集成 + 搜索模板 + Monitor 单测
- **Affected docs**: API 文档（新增标签 + 搜索模板端点）、用户手册（标签 + 搜索增强）、Postman 集合
- **New dependencies**: 无新增外部依赖（标签引擎基于现有正则/字典/NER能力）
- **Affected specs**: `tag-system-design`（设计→实现）、`v2-iteration-plan`（遗留清零）

## ADDED Requirements

### Requirement: 标签字典管理
系统 SHALL 提供六层标签字典的 CRUD 管理能力，支持层级树展示、启用/禁用、批量导入。

#### Scenario: 创建标签
- **WHEN** 管理员在标签管理页面创建新标签
- **THEN** 标签写入 `tag_dict_v2` 表，编码遵循 `层级.分类.名称.值` 规范，全局唯一

#### Scenario: 标签层级树展示
- **WHEN** 用户打开标签管理页面
- **THEN** 以树形结构展示 L1-L6 六层标签，支持展开/折叠/搜索

#### Scenario: 禁用标签
- **WHEN** 管理员禁用某个标签
- **THEN** 该标签不再出现在自动识别和手动打标选项中，已打标文件的标签保留但标记为"已禁用"

### Requirement: 自动标签识别引擎
系统 SHALL 在文件解析完成后自动触发标签识别，支持 REGEX / DICT / ML / ASSOC 四类规则。

#### Scenario: 正则规则识别
- **WHEN** 文件解析完成，正则规则匹配到 CVE 编号
- **THEN** 自动为文件打上 `L3.ENTITY.VULN.CVE` 标签

#### Scenario: 字典规则识别
- **WHEN** 文件内容包含 APT 组织字典中的关键词
- **THEN** 自动为文件打上 `L5.INTEL.APT.组织名` 标签

#### Scenario: 模型规则识别
- **WHEN** NER 模型识别到 IP 实体
- **THEN** 自动为文件打上 `L3.ENTITY.IP` 标签（区分公网/私网）

#### Scenario: 关联推导规则识别
- **WHEN** 文件包含目标 IP 且目标画像存在
- **THEN** 自动为文件打上 `L4.SCENE.TARGET_PROFILE` 场景标签

### Requirement: 标签筛选检索
系统 SHALL 支持按标签筛选文件，支持多标签组合（AND/OR 逻辑）。

#### Scenario: 单标签筛选
- **WHEN** 用户在 FileSearch 页面点击标签 facet
- **THEN** 搜索结果过滤为包含该标签的文件

#### Scenario: 多标签组合筛选
- **WHEN** 用户选择多个标签并选择 AND 逻辑
- **THEN** 搜索结果为同时包含所有选中标签的文件

### Requirement: 搜索模板
系统 SHALL 支持保存常用搜索条件为模板，一键应用。

#### Scenario: 保存搜索模板
- **WHEN** 用户在 FileSearch 页面配置好搜索条件后点击「保存搜索」
- **THEN** 搜索条件（关键词/模式/布尔条件/标签）保存为模板，可在后续一键应用

#### Scenario: 应用搜索模板
- **WHEN** 用户从搜索模板下拉中选择一个模板
- **THEN** 搜索条件自动填充并执行搜索

### Requirement: 搜索历史
系统 SHALL 记录用户最近 20 条搜索历史，支持快速重新执行。

#### Scenario: 搜索历史记录
- **WHEN** 用户执行一次搜索
- **THEN** 搜索条件保存到 localStorage 历史列表（最多 20 条，新搜索置顶）

#### Scenario: 从历史重新搜索
- **WHEN** 用户点击历史记录中的某条
- **THEN** 搜索条件恢复并重新执行搜索

### Requirement: 节假日日历
系统 SHALL 支持中国法定节假日 + 调休日历，定时报告在节假日跳过执行。

#### Scenario: 节假日跳过
- **WHEN** 定时报告触发时间命中节假日
- **THEN** 跳过本次执行，记录跳过日志，不影响下次执行

### Requirement: Slack / 钉钉推送
系统 SHALL 支持通过 Slack Webhook 和钉钉 Webhook 推送定时报告通知。

#### Scenario: Slack 推送
- **WHEN** 定时报告生成完成且配置了 Slack Webhook
- **THEN** 向 Slack 频道推送报告摘要 + 下载链接

## MODIFIED Requirements

### Requirement: 文件搜索（增强）
V2 已支持布尔组合检索（AND/OR/NOT）和二次检索。V3 新增标签筛选 facet + 搜索模板 + 搜索历史。

### Requirement: 定时报告（增强）
V2 支持 Cron 表达式 + 邮件推送。V3 新增节假日日历跳过 + Slack/钉钉 Webhook 推送。

### Requirement: 文件详情页（增强）
V1/V2 文件详情页展示文件元信息 + NER 实体。V3 新增标签展示区 + 手动打标/取消打标操作。

## REMOVED Requirements

无移除项。所有 V1/V2 功能保持兼容，V3 为增强而非替换。
