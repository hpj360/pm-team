# Tasks

## V3.1 — 标签体系落地（P1，核心功能）

- [x] Task 1: 标签字典数据模型 + DDL 迁移
  - [x] SubTask 1.1: 新增 `tag_dict_v2` 表 DDL（编码/中文名/层级/分类/值类型/适用对象/识别规则/是否多选/父标签/启用/口径定义，13 字段）
  - [x] SubTask 1.2: 新增 `file_tags` 表 DDL（file_id/tag_id/source[AUTO/MANUAL]/created_at）
  - [x] SubTask 1.3: 新增 `TagDictEntity` + `FileTagEntity` 实体类（backend/common）
  - [x] SubTask 1.4: 新增 `TagDictMapper` + `FileTagMapper` MyBatis Mapper
  - [x] SubTask 1.5: 初始化标签字典种子数据（L1-L6 六层核心标签，76 个标签）

- [x] Task 2: 标签 CRUD REST 端点
  - [x] SubTask 2.1: 新增 `TagController`：标签字典 CRUD（GET 列表/树形/详情，POST 创建，PUT 更新，DELETE 删除，PATCH 启用/禁用）
  - [x] SubTask 2.2: 新增文件打标端点：`POST /api/tags/files/{fileId}`（打标）、`DELETE /api/tags/files/{fileId}/{tagId}`（取消打标）
  - [x] SubTask 2.3: 新增按标签检索文件端点：`GET /api/tags/{tagId}/files`（分页）
  - [x] SubTask 2.4: 编写单元测试（CRUD / 打标 / 取消打标 / 按标签检索，19 用例）
  - [x] SubTask 2.5: 验证 `mvn compile` + `mvn test` 通过

- [x] Task 3: 自动标签识别引擎
  - [x] SubTask 3.1: 新增 `TagRecognitionEngine` 服务：接收文件文本 + 元信息，执行四类规则
  - [x] SubTask 3.2: 实现 REGEX 规则引擎：正则匹配文件类型/CVE编号/IP/域名/邮箱，产出对应标签
  - [x] SubTask 3.3: 实现 DICT 规则引擎：字典匹配 APT 组织/文件类型，产出对应标签
  - [x] SubTask 3.4: 实现 ML 规则引擎：对接 NerService 的 NER 实体结果，映射为 L3 实体标签
  - [x] SubTask 3.5: 实现 ASSOC 规则引擎：关联推导（文件含目标IP→场景标签）
  - [x] SubTask 3.6: 在 parse-service 文件解析完成后调用 `TagRecognitionEngine`，写入 `file_tags`
  - [x] SubTask 3.7: 编写单元测试（四类规则 15 用例 + 组合识别 + 空文本边界）
  - [x] SubTask 3.8: 验证 `mvn compile` + `mvn test` 通过

- [x] Task 4: 前端标签管理页面
  - [x] SubTask 4.1: 新增 `src/pages/admin/TagManage/index.tsx`：标签字典 CRUD + 层级树展示
  - [x] SubTask 4.2: 树形展示 L1-L6 标签，支持展开/折叠/搜索/筛选层级
  - [x] SubTask 4.3: 标签创建/编辑 Modal（编码/中文名/层级/分类/值类型/识别规则/启用）
  - [x] SubTask 4.4: 标签启用/禁用开关 + 删除（Popconfirm 确认）
  - [x] SubTask 4.5: 新增路由 + 侧边栏菜单项（后台管理 > 标签管理）
  - [x] SubTask 4.6: 编写单元测试（树渲染/创建/编辑/禁用/删除 7 用例）

- [x] Task 5: 前端标签集成（FileSearch / FileList / FileDetail）
  - [x] SubTask 5.1: FileSearch 页面左侧 facet 面板新增「标签」聚合分组（展示 Top 标签 + 点击筛选）
  - [x] SubTask 5.2: FileSearch 搜索请求新增 `tagIds` 参数，支持多标签 AND/OR 组合
  - [x] SubTask 5.3: FileList 表格新增「标签」列（Tag 组件展示，最多 3 个 + 更多展开）
  - [x] SubTask 5.4: 文件详情页新增「标签」区域：展示已有标签 + 手动打标 Input + 取消打标
  - [x] SubTask 5.5: Mock 数据新增标签 facet / 文件标签关联
  - [x] SubTask 5.6: 编写单元测试（标签 facet 渲染/筛选/列表列/详情打标 6 用例）

## V3.2 — 搜索体验闭环（P2）

- [x] Task 6: 搜索模板后端
  - [x] SubTask 6.1: 新增 `search_template` 表 DDL（PostgreSQL 语法）
  - [x] SubTask 6.2: 新增 `SearchTemplateEntity` + `SearchTemplateMapper`
  - [x] SubTask 6.3: 新增 REST 端点：`POST /api/search/templates`（保存）、`GET /api/search/templates`（列表）、`DELETE /api/search/templates/{id}`（删除）
  - [x] SubTask 6.4: 编写单元测试（保存/列表/删除/参数校验 8 用例）
  - [x] SubTask 6.5: 验证 `mvn compile` + `mvn test` 通过

- [x] Task 7: 搜索模板 + 搜索历史前端
  - [x] SubTask 7.1: FileSearch 页面新增「保存搜索」按钮 → Modal 输入模板名称 → 调用保存 API
  - [x] SubTask 7.2: FileSearch 页面顶部新增搜索模板下拉选择 → 选中后填充条件并搜索
  - [x] SubTask 7.3: FileSearch 页面新增搜索历史区域（最近 20 条，localStorage，点击恢复条件）
  - [x] SubTask 7.4: Mock 数据新增搜索模板支持
  - [x] SubTask 7.5: 编写单元测试（保存模板/应用模板/历史记录/恢复搜索 6 用例）

- [x] Task 8: 文档更新（搜索增强 + 标签）
  - [x] SubTask 8.1: API 文档新增布尔组合检索 + 二次检索 + 标签检索 + 搜索模板端点说明
  - [x] SubTask 8.2: 用户手册新增布尔检索 / 二次检索 / 标签筛选 / 搜索模板 / 搜索历史操作指南
  - [x] SubTask 8.3: API 文档新增标签管理 + 文件打标 + 自动识别端点说明

## V3.3 — V2 遗留 P3 清零（P3）

- [x] Task 9: 定时报告增强（节假日 + Slack/钉钉）
  - [x] SubTask 9.1: 新增 `HolidayCalendarService`：中国法定节假日 + 调休日历（2026-2027）
  - [x] SubTask 9.2: `ReportSchedulerService` 触发前检查节假日，命中则跳过并记录日志
  - [x] SubTask 9.3: 新增 `SlackWebhookService` + `DingTalkWebhookService`：Webhook 推送
  - [x] SubTask 9.4: 定时报告配置新增 `webhookType` 字段（EMAIL / SLACK / DINGTALK / ALL）
  - [x] SubTask 9.5: 编写单元测试（节假日跳过 / Slack推送 / 钉钉推送 / 多通道 28 用例）
  - [x] SubTask 9.6: 验证 `mvn compile` + `mvn test` 通过

- [x] Task 10: Neo4j GDS 图算法（可选模块）
  - [x] SubTask 10.1: profile-service 新增 GDS 依赖配置（可选，检测 GDS 插件是否安装）
  - [x] SubTask 10.2: 新增 `GraphAlgorithmService`：PageRank / Community Detection Cypher 调用
  - [x] SubTask 10.3: 新增端点 `GET /api/profile/graph/algorithms`（列出可用算法）
  - [x] SubTask 10.4: 新增端点 `POST /api/profile/graph/algorithms/{algo}`（执行算法）
  - [x] SubTask 10.5: GDS 不可用时降级返回提示信息
  - [x] SubTask 10.6: 编写单元测试（算法调用 / 降级 10 用例）

- [x] Task 11: 前端构建优化 + Monitor 单测
  - [x] SubTask 11.1: vite.config.ts 将 antd-pro 拆分为 ProTable / ProForm / ProDescriptions 独立 chunk
  - [x] SubTask 11.2: Monitor 页面补齐单元测试（13 用例）
  - [x] SubTask 11.3: 验证 `npx tsc --noEmit` + `npm run build` + `npm run test:unit` 通过

- [x] Task 12: Postman 集合同步
  - [x] SubTask 12.1: 导出 V2 + V3 全部端点为 Postman Collection JSON（29 端点）
  - [x] SubTask 12.2: 放置到 `docs/postman/` 目录

## V3.4 — 验证与报告

- [x] Task 13: V3 迭代验证
  - [x] SubTask 13.1: 后端全量 `mvn compile` + `mvn test` 通过
  - [x] SubTask 13.2: 前端 `npx tsc --noEmit` + `npm run build` + `npm run test:unit` 通过
  - [x] SubTask 13.3: 生成 V3 迭代验证报告（质量分 ≥95）
  - [x] SubTask 13.4: 更新 API 文档版本号 + 变更记录

# Task Dependencies

- Task 1（标签数据模型）是 Task 2/3/4/5 的前置依赖
- Task 2（标签 CRUD）和 Task 3（识别引擎）可并行（都依赖 Task 1）
- Task 4（标签管理页面）依赖 Task 2（需要 API 端点）
- Task 5（标签集成）依赖 Task 2 + Task 3
- Task 6（搜索模板后端）独立于 Task 1-5，可并行
- Task 7（搜索模板前端）依赖 Task 6
- Task 8（文档更新）依赖 Task 2 + Task 6 完成
- Task 9/10/11 互相独立，可并行
- Task 12 依赖全部功能 Task 完成
- Task 13 依赖全部 Task 完成
