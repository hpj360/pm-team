# V3 迭代验收检查清单

## V3.1 — 标签体系落地

### 数据模型
- [x] `tag_dict_v2` 表 DDL 已创建（13 字段 + 4 索引 + uk_tag_code 唯一键）
- [x] `file_tags` 表 DDL 已创建（file_id/tag_id/source/created_at）
- [x] `TagDictEntity` + `FileTagEntity` 实体类已实现
- [x] `TagDictMapper` + `FileTagMapper` MyBatis Mapper 已实现
- [x] 标签字典种子数据已初始化（L1-L6 六层 76 个标签）

### 标签 CRUD
- [x] `GET /api/tags` 标签列表查询（支持层级/分类/启用状态筛选）
- [x] `GET /api/tags/tree` 标签层级树查询
- [x] `GET /api/tags/{id}` 标签详情
- [x] `POST /api/tags` 创建标签（编码全局唯一校验）
- [x] `PUT /api/tags/{id}` 更新标签
- [x] `PATCH /api/tags/{id}/toggle` 启用/禁用标签
- [x] `DELETE /api/tags/{id}` 删除标签
- [x] `POST /api/tags/files/{fileId}` 文件打标（支持批量 tagIds）
- [x] `DELETE /api/tags/files/{fileId}/{tagId}` 取消打标
- [x] `GET /api/tags/files/{fileId}` 查询文件标签
- [x] `GET /api/tags/{tagId}/files` 按标签检索文件
- [x] 单元测试覆盖（19 用例）
- [x] mvn compile + mvn test 通过

### 自动标签识别引擎
- [x] `TagRecognitionEngine` 服务已实现
- [x] REGEX 规则引擎：正则匹配 CVE/IP/域名/邮箱/文件类型
- [x] DICT 规则引擎：字典匹配 APT 组织
- [x] ML 规则引擎：对接 NerService NER 实体映射为 L3 标签
- [x] ASSOC 规则引擎：关联推导（IP→场景标签、pcap→网络地形等）
- [x] parse-service 文件解析完成后异步触发标签识别
- [x] 单元测试覆盖（15 用例）
- [x] mvn compile + mvn test 通过

### 前端标签管理页面
- [x] `src/pages/admin/TagManage/index.tsx` 页面已创建
- [x] 层级筛选 Radio.Group（L1-L6 + 全部）
- [x] 标签创建/编辑 Modal
- [x] 标签启用/禁用 Switch
- [x] 标签删除（Popconfirm 确认）
- [x] 路由 + 侧边栏菜单项已添加
- [x] 单元测试覆盖（7 用例）

### 前端标签集成
- [x] FileSearch 左侧 facet 新增「标签」聚合分组
- [x] FileSearch 搜索请求支持 `tagIds` 多标签 AND 组合
- [x] FileList 表格新增「标签」列（最多 3 个 + Tooltip 展开）
- [x] 文件详情页新增「标签」区域（展示 + 手动打标 + 取消打标）
- [x] Mock 数据新增标签 facet / 文件标签关联
- [x] 单元测试覆盖（6 用例）

## V3.2 — 搜索体验闭环

### 搜索模板后端
- [x] `search_template` 表 DDL 已创建（PostgreSQL）
- [x] `SearchTemplateEntity` + `SearchTemplateMapper` 已实现
- [x] `POST /api/search/templates` 保存搜索模板
- [x] `GET /api/search/templates` 模板列表
- [x] `DELETE /api/search/templates/{id}` 删除模板
- [x] 单元测试覆盖（8 用例）
- [x] mvn compile + mvn test 通过

### 搜索模板 + 搜索历史前端
- [x] FileSearch 页面「保存搜索」按钮 + Modal
- [x] FileSearch 页面搜索模板下拉选择
- [x] FileSearch 页面搜索历史区域（最近 20 条，localStorage）
- [x] Mock 数据新增搜索模板支持
- [x] 单元测试覆盖（6 用例）

### 文档更新
- [x] API 文档新增布尔组合检索 + 二次检索说明
- [x] API 文档新增标签管理 + 搜索模板端点（接口总数 140）
- [x] 用户手册新增标签管理 / 搜索增强 / 定时报告增强章节

## V3.3 — V2 遗留 P3 清零

### 定时报告增强
- [x] `HolidayCalendarService` 中国法定节假日 + 调休日历（2026-2027）
- [x] `ReportSchedulerService` 节假日跳过执行 + 日志记录
- [x] `SlackWebhookService` Slack Webhook 推送已实现
- [x] `DingTalkWebhookService` 钉钉 Webhook 推送已实现
- [x] 定时报告配置新增 `webhookType` 字段
- [x] 单元测试覆盖（28 用例）
- [x] mvn compile + mvn test 通过

### Neo4j GDS 图算法
- [x] GDS 依赖配置已添加（默认禁用，环境变量开启）
- [x] `GraphAlgorithmService` PageRank / Community / ShortestPath / Centrality
- [x] `GET /api/profile/graph/algorithms` 列出可用算法
- [x] `POST /api/profile/graph/algorithms/{algo}` 执行算法
- [x] GDS 不可用时降级返回提示
- [x] 单元测试覆盖（10 用例）

### 前端构建优化 + Monitor 单测
- [x] vite.config.ts antd-pro 拆分为 4 个独立 chunk
- [x] Monitor 页面单元测试（13 用例）
- [x] npx tsc --noEmit + npm run build + npm run test:unit 通过

### Postman 集合
- [x] V2 + V3 Postman Collection JSON 已导出（29 端点）
- [x] 放置到 `docs/postman/` 目录

## V3.4 — 综合验证

- [x] 所有后端微服务 mvn compile + mvn test 通过
- [x] 前端 npx tsc --noEmit 零错误
- [x] 前端 npm run build 通过
- [x] 前端 npm run test:unit 通过且覆盖率 ≥80%
- [x] V3 迭代验证报告质量分 ≥95
- [x] API 文档版本号 + 变更记录已更新
