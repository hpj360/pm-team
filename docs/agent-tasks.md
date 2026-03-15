# Agent任务分配 - 网络安全红方文件汇聚平台

## 项目信息

- **项目名称**: 网络安全红方文件汇聚平台
- **项目总监**: Director
- **创建日期**: 2026-03-15

---

## Agent任务分配总览

| Agent | 角色 | 阶段 | 任务数 | 预计周期 | 状态 |
|-------|------|------|--------|---------|------|
| requirement-analyst | 需求分析师 | 阶段1 | 12 | 3天 | 待开始 |
| architect | 技术架构师 | 阶段2 | 16 | 5天 | 待开始 |
| database-designer | 数据库设计师 | 阶段3 | 13 | 3天 | 待开始 |
| product-designer | 产品设计师 | 阶段4 | 12 | 5天 | 待开始 |
| ui-designer | UI设计师 | 阶段5 | 14 | 5天 | 待开始 |
| backend-developer | 后端工程师 | 阶段6 | 24 | 15天 | 待开始 |
| frontend-developer | 前端工程师 | 阶段7 | 20 | 12天 | 待开始 |
| code-reviewer | 代码审查员 | 阶段9 | 6 | 2天 | 待开始 |
| security-engineer | 安全工程师 | 阶段10 | 9 | 3天 | 待开始 |
| tester | 测试工程师 | 阶段11 | 14 | 5天 | 待开始 |
| operations | 运维工程师 | 阶段12,14 | 17 | 3天 | 待开始 |
| tech-writer | 技术文档工程师 | 阶段15 | 7 | 3天 | 待开始 |

---

## 1. requirement-analyst (需求分析师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段1: 需求分析 |
| 预计周期 | 3天 |
| 工作空间 | `workspaces/requirement-analyst/` |
| 输出目录 | `docs/` |

### 核心任务

1. 分析网络安全红方业务场景
2. 收集文件上传功能需求
3. 收集文件解析功能需求
4. 收集混合检索功能需求
5. 收集文件分析功能需求
6. 收集目标画像功能需求
7. 定义非功能性需求
8. 编写PRD文档

### 输出物

- `docs/prd.md` - 产品需求文档
- `docs/feature-list.md` - 功能清单
- `docs/use-case-diagram.png` - 用例图

### 依赖

- 无前置依赖

### 验收标准

- 需求描述清晰完整
- 功能点覆盖全面
- 无需求冲突或遗漏

---

## 2. architect (技术架构师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段2: 架构设计 |
| 预计周期 | 5天 |
| 工作空间 | `workspaces/architect/` |
| 输出目录 | `docs/` |

### 核心任务

1. 分析系统整体架构需求
2. 设计微服务架构
3. 设计服务划分方案
4. 设计存储架构
5. 设计数据流转架构
6. 设计各模块架构
7. 技术选型评估
8. 编写架构设计文档

### 输出物

- `docs/architecture.md` - 技术架构方案
- `docs/diagrams/service-architecture.png` - 服务架构图
- `docs/diagrams/storage-architecture.png` - 存储架构图
- `docs/tech-stack.md` - 技术选型说明

### 依赖

- 阶段1完成
- 需求评审通过

### 验收标准

- 架构设计满足需求
- 技术选型合理
- 架构可扩展

---

## 3. database-designer (数据库设计师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段3: 数据库设计 |
| 预计周期 | 3天 |
| 工作空间 | `workspaces/database-designer/` |
| 输出目录 | `docs/`, `scripts/` |

### 核心任务

1. 分析数据模型需求
2. 设计文件元数据表
3. 设计用户权限表
4. 设计解析结果表
5. 设计分析结果表
6. 设计目标画像表
7. 设计ES索引结构
8. 设计Milvus Collection
9. 设计Neo4j图模型
10. 设计分片和索引策略

### 输出物

- `docs/database-design.md` - 数据库设计方案
- `docs/diagrams/er-diagram.png` - ER图
- `scripts/ddl/` - DDL脚本
- `docs/index-design.md` - 索引设计文档

### 依赖

- 阶段2完成

### 验收标准

- 表结构设计合理
- 索引设计优化
- 满足性能要求

---

## 4. product-designer (产品设计师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段4: 产品设计 |
| 预计周期 | 5天 |
| 工作空间 | `workspaces/product-designer/` |
| 输出目录 | `docs/prototype/` |

### 核心任务

1. 分析用户角色和使用场景
2. 设计文件上传页面交互
3. 设计文件列表页面交互
4. 设计文件检索页面交互
5. 设计文件详情页面交互
6. 设计文件分析页面交互
7. 设计目标画像页面交互
8. 设计后台管理页面交互
9. 绘制原型图

### 输出物

- `docs/prototype/` - 原型图
- `docs/interaction-design.md` - 交互文档
- `docs/diagrams/page-flow.png` - 页面流程图

### 依赖

- 阶段3完成

### 验收标准

- 交互流程合理
- 功能覆盖完整
- 用户体验良好

---

## 5. ui-designer (UI设计师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段5: UI设计 |
| 预计周期 | 5天 |
| 工作空间 | `workspaces/ui-designer/` |
| 输出目录 | `docs/design/` |

### 核心任务

1. 定义设计规范
2. 设计色彩体系
3. 设计字体体系
4. 设计图标库
5. 设计组件库
6. 设计各页面高保真设计稿
7. 输出切图资源
8. 编写标注文档

### 输出物

- `docs/design/` - 高保真设计稿
- `docs/design/assets/` - 切图资源
- `docs/design-spec.md` - 设计规范文档
- `docs/design/components/` - 组件库

### 依赖

- 阶段4完成

### 验收标准

- 视觉效果美观
- 设计规范统一
- 切图资源完整

---

## 6. backend-developer (后端工程师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段6: 后端开发 |
| 预计周期 | 15天 |
| 工作空间 | `workspaces/backend-developer/` |
| 输出目录 | `backend/` |

### 核心任务

1. 搭建项目框架
2. 配置各中间件连接
3. 开发文件上传服务
4. 开发文件解析服务
5. 开发混合检索服务
6. 开发文件分析服务
7. 开发目标画像服务
8. 开发用户权限服务
9. 编写单元测试
10. 编写API文档

### 输出物

- `backend/` - 后端代码
- `docs/api/` - API文档
- `backend/src/test/` - 单元测试

### 依赖

- 设计评审通过

### 验收标准

- 功能实现完整
- 代码质量合格
- 单元测试通过

---

## 7. frontend-developer (前端工程师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段7: 前端开发 |
| 预计周期 | 12天 |
| 工作空间 | `workspaces/frontend-developer/` |
| 输出目录 | `frontend/` |

### 核心任务

1. 搭建项目框架
2. 配置路由和状态管理
3. 封装API请求
4. 开发公共组件
5. 开发各页面功能
6. 前端性能优化
7. 响应式适配

### 输出物

- `frontend/` - 前端代码
- `frontend/src/components/` - 组件库
- `frontend/src/mocks/` - Mock数据

### 依赖

- 设计评审通过

### 验收标准

- 页面功能完整
- 交互体验良好
- 性能达标

---

## 8. code-reviewer (代码审查员)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段9: 代码审查 |
| 预计周期 | 2天 |
| 工作空间 | `workspaces/code-reviewer/` |
| 输出目录 | `logs/reports/` |

### 核心任务

1. 后端代码质量审查
2. 前端代码质量审查
3. 代码规范检查
4. 性能问题排查
5. 安全问题排查
6. 编写审查报告

### 输出物

- `logs/reports/code-review-report.md` - 审查报告
- `logs/issues/code-review-issues.md` - 问题清单

### 依赖

- 阶段8完成

### 验收标准

- 无严重代码问题
- 代码规范符合要求

---

## 9. security-engineer (安全工程师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段10: 安全审计 |
| 预计周期 | 3天 |
| 工作空间 | `workspaces/security-engineer/` |
| 输出目录 | `logs/reports/` |

### 核心任务

1. 代码安全审计
2. SQL注入检测
3. XSS漏洞检测
4. 文件上传漏洞检测
5. 权限漏洞检测
6. 敏感信息泄露检测
7. 依赖漏洞扫描
8. 渗透测试
9. 编写安全审计报告

### 输出物

- `logs/reports/security-audit-report.md` - 安全审计报告
- `logs/issues/security-issues.md` - 漏洞清单
- `docs/security-hardening.md` - 加固方案

### 依赖

- 阶段9完成

### 验收标准

- 无高危漏洞
- 符合安全合规要求

---

## 10. tester (测试工程师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段11: 测试验证 |
| 预计周期 | 5天 |
| 工作空间 | `workspaces/tester/` |
| 输出目录 | `docs/`, `logs/reports/` |

### 核心任务

1. 编写测试计划
2. 编写测试用例
3. 执行功能测试
4. 执行性能测试
5. 执行并发测试
6. Bug修复验证
7. 回归测试
8. 编写测试报告

### 输出物

- `docs/test-plan.md` - 测试计划
- `docs/test-cases/` - 测试用例
- `logs/reports/test-report.md` - 测试报告
- `logs/issues/bugs.md` - Bug清单

### 依赖

- 阶段10完成

### 验收标准

- 功能测试通过
- 性能测试达标
- 无阻塞性Bug

---

## 11. operations (运维工程师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段12,14: 部署 |
| 预计周期 | 3天 |
| 工作空间 | `workspaces/operations/` |
| 输出目录 | `docs/`, `logs/` |

### 核心任务

1. 准备预发布环境
2. 配置服务器
3. 部署各中间件
4. 部署应用服务
5. 配置监控告警
6. 数据迁移
7. 服务切换
8. 编写部署文档

### 输出物

- 预发布/生产环境
- `docs/deployment.md` - 部署文档
- `docs/ops-manual.md` - 运维手册
- `logs/deployment-record.md` - 部署记录

### 依赖

- 测试评审通过

### 验收标准

- 环境部署成功
- 服务正常运行
- 监控配置完成

---

## 12. tech-writer (技术文档工程师)

### 任务概要

| 项目 | 内容 |
|------|------|
| 阶段 | 阶段15: 文档编写 |
| 预计周期 | 3天 |
| 工作空间 | `workspaces/tech-writer/` |
| 输出目录 | `docs/` |

### 核心任务

1. 编写用户手册
2. 编写API文档
3. 编写运维手册
4. 编写部署文档
5. 编写故障处理手册
6. 编写FAQ文档
7. 整理项目文档

### 输出物

- `docs/user-manual.md` - 用户手册
- `docs/api/` - API文档
- `docs/ops-manual.md` - 运维手册
- `docs/troubleshooting.md` - 故障处理手册
- `docs/faq.md` - FAQ文档

### 依赖

- 上线验收通过

### 验收标准

- 文档完整
- 内容准确
- 易于理解

---

## 变更记录

| 版本 | 日期 | 变更内容 | 变更人 |
|------|------|---------|--------|
| v1.0 | 2026-03-15 | 初始版本 | Director |
