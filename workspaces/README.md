# 工作空间目录

此目录存放各Agent的工作产出物，每个Agent有独立的工作空间。

## 目录结构

| Agent | 目录 | 主要产出 |
|-------|------|----------|
| director | director/ | 任务记录、流程日志、评审记录、汇总文档 |
| requirement-analyst | requirement-analyst/ | PRD文档、用户故事、验收标准 |
| architect | architect/ | 技术架构方案、技术选型文档 |
| product-designer | product-designer/ | 原型图、交互文档 |
| ui-designer | ui-designer/ | 设计稿、切图资源、设计规范 |
| database-designer | database-designer/ | 数据库设计方案、ER图、数据模型 |
| backend-developer | backend-developer/ | 后端代码、API文档 |
| frontend-developer | frontend-developer/ | 前端代码、页面实现 |
| security-engineer | security-engineer/ | 安全方案、审计报告 |
| code-reviewer | code-reviewer/ | 审查报告、改进建议 |
| tester | tester/ | 测试用例、测试报告 |
| operations | operations/ | 部署配置、运维脚本 |
| tech-writer | tech-writer/ | 技术文档、API文档 |

## 命名规范

- 任务产出物: `{task-id}-{artifact-type}-{version}.{ext}`
- 临时文件: `temp-{timestamp}-{name}.{ext}`
- 归档文件: `archive/{date}/{filename}`

## 版本管理

每个工作空间保留最近3个版本，旧版本自动归档到 `archive/` 子目录。
