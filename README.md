# 产品全生命周期多角色协作团队

基于 OpenClaw 框架的多 Agent 协作系统，实现从需求分析到上线运维的完整产品开发流程。

## 版本信息

- **当前版本**: v2.2.0
- **更新日期**: 2024-03-15

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         用户/交互层                              │
│                    (飞书/钉钉/Slack等)                           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Director (项目总监)                         │
│                    任务调度与结果汇总                             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┐
│Require-│Architec│Product │  UI    │Database│Backend │Frontend│Security│ Code   │ Tester │Operati-│ Tech   │Director│
│ment    │        │Designer│Designer│Designer│Developer│Developer│Engineer│Reviewer│        │ons     │Writer  │(评审)  │
│Analyst │        │        │        │        │        │        │        │        │        │        │        │        │
│需求分析│架构师  │产品设计│UI设计 │数据库  │后端    │前端    │安全    │代码审查│测试    │运维    │文档    │评审节点│
│师      │        │师      │师      │设计师  │工程师  │工程师  │工程师  │员      │工程师  │工程师  │工程师  │        │
└────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┘
```

## 角色说明

| 角色 | ID | 职责 | 输出 |
|------|-----|------|------|
| 项目总监 | director | 任务调度、协调、汇总、评审 | 最终交付物 |
| 需求分析师 | requirement-analyst | 需求调研、分析 | PRD文档 |
| 技术架构师 | architect | 系统架构设计、技术选型 | 技术架构方案 |
| 产品设计师 | product-designer | 产品设计、原型、交互 | 原型图/交互文档 |
| UI设计师 | ui-designer | 视觉设计、切图标注 | 设计稿/切图 |
| 数据库设计师 | database-designer | 数据库架构、数据模型 | 数据库设计方案 |
| 后端工程师 | backend-developer | 后端服务开发、API | API/后端代码 |
| 前端工程师 | frontend-developer | 前端页面开发 | 前端代码 |
| 安全工程师 | security-engineer | 安全设计、安全审计 | 安全方案/审计报告 |
| 代码审查员 | code-reviewer | 代码审查 | 审查报告 |
| 测试工程师 | tester | 测试验证 | 测试报告 |
| 运维工程师 | operations | 部署运维 | 部署结果 |
| 技术文档工程师 | tech-writer | 文档编写 | 技术文档/API文档 |

## 工作流程

### 完整产品开发流程 (v2.2)

```
用户需求
    ↓
[需求分析] → requirement-analyst → PRD文档
    ↓
[用户故事设计] → requirement-analyst → 用户故事列表
    ↓
[验收标准定义] → requirement-analyst → 验收标准文档
    ↓
[需求评审] → director → 需求评审报告 ⚠️ 需审批
    ↓
[架构设计] → architect → 技术架构方案 ⚠️ 需审批
    ↓
[数据库设计] → database-designer → 数据库设计方案
    ↓
    ├──→ [产品设计] → product-designer → 原型图/交互文档 (并行)
    │         ↓
    │    [UI设计] → ui-designer → 高保真设计稿/切图
    │         ↓
    └──────────→ [设计评审] → director → 设计评审报告 ⚠️ 需审批
                    ↓
    ┌───────────────┴───────────────┐
    ↓                               ↓
[后端开发]                      [前端开发-Mock]
backend-developer               frontend-developer
    ↓                               ↓
API文档输出                     前端页面(Mock)
    ↓                               ↓
    └───────────────┬───────────────┘
                    ↓
[前后端联调] → backend + frontend → 联调报告
                    ↓
[代码审查] → code-reviewer → 审查报告
                    ↓
[安全审计] → security-engineer → 安全审计报告 ⚠️ 需审批
                    ↓
[测试验证] → tester → 测试报告
                    ↓
[测试评审] → director → 测试评审报告 ⚠️ 需审批
                    ↓
[预发布部署] → operations → 预发布环境
                    ↓
[预发布验证] → tester + operations → 预发布验证报告
                    ↓
[正式部署] → operations → 生产环境
                    ↓
[上线验收] → director → 验收报告 ⚠️ 需审批
                    ↓
[文档编写] → tech-writer → 完整技术文档
                    ↓
最终交付
```

### 工作流程阶段表

| 阶段 | Agent | 输出 | 审批 | 并行 |
|------|-------|------|------|------|
| 需求分析 | requirement-analyst | PRD文档 | - | - |
| 用户故事设计 | requirement-analyst | 用户故事列表 | - | - |
| 验收标准定义 | requirement-analyst | 验收标准文档 | - | - |
| 需求评审 | director | 需求评审报告 | ⚠️ | - |
| 架构设计 | architect | 技术架构方案 | ⚠️ | - |
| 数据库设计 | database-designer | 数据库设计方案 | - | - |
| 产品设计 | product-designer | 原型图/交互文档 | - | ✓ |
| UI设计 | ui-designer | 设计稿/切图 | - | - |
| 设计评审 | director | 设计评审报告 | ⚠️ | - |
| 后端开发 | backend-developer | API/后端代码 | - | ✓ |
| 前端开发 | frontend-developer | 前端代码(Mock) | - | ✓ |
| 前后端联调 | backend+frontend | 联调报告 | - | - |
| 代码审查 | code-reviewer | 审查报告 | - | - |
| 安全审计 | security-engineer | 安全审计报告 | ⚠️ | - |
| 测试验证 | tester | 测试报告 | - | - |
| 测试评审 | director | 测试评审报告 | ⚠️ | - |
| 预发布部署 | operations | 预发布环境 | - | - |
| 预发布验证 | tester+operations | 预发布验证报告 | - | - |
| 正式部署 | operations | 生产环境 | - | - |
| 上线验收 | director | 验收报告 | ⚠️ | - |
| 文档编写 | tech-writer | 技术文档 | - | - |

## 评审节点

系统在关键阶段设置了6个评审节点：

| 评审节点 | 时机 | 审批人 | 审批内容 |
|----------|------|--------|----------|
| 需求评审 | 需求分析后 | director + requirement-analyst | 需求完整性、可行性 |
| 架构设计评审 | 架构设计后 | director + architect | 技术选型、架构合理性 |
| 设计评审 | UI设计后 | director + product-designer + ui-designer | 设计满足需求、可实现性 |
| 安全审计评审 | 安全审计后 | director + security-engineer | 安全漏洞、合规性 |
| 测试评审 | 测试验证后 | director + tester | 测试覆盖、是否可上线 |
| 上线验收 | 正式部署后 | director | 线上环境、功能正常 |

## 并行调度策略

### 设计阶段并行
```
架构设计 + 数据库设计完成
    ↓
    ├──→ product-designer (产品设计)
    │         ↓
    │    ui-designer (UI设计)
    │
    └──→ (等待设计完成)
              ↓
         设计评审
```

### 开发阶段并行
```
设计评审通过
    ↓
    ├──→ backend-developer (开发API)
    │         ↓
    │    API文档输出
    │
    └──→ frontend-developer (使用Mock开发前端)
              ↓
         前端页面输出
              ↓
         前后端联调
```

## 目录结构

```
pm-team/
├── openclaw.json              # 主配置文件
├── package.json               # 项目配置
├── README.md                  # 使用文档
├── agents/                    # Agent 配置目录 (13个)
│   ├── director/              # 项目总监
│   ├── requirement-analyst/   # 需求分析师
│   ├── architect/             # 技术架构师
│   ├── product-designer/      # 产品设计师
│   ├── ui-designer/           # UI设计师
│   ├── database-designer/     # 数据库设计师
│   ├── backend-developer/     # 后端工程师
│   ├── frontend-developer/    # 前端工程师
│   ├── security-engineer/     # 安全工程师
│   ├── code-reviewer/         # 代码审查员
│   ├── tester/                # 测试工程师
│   ├── operations/            # 运维工程师
│   └── tech-writer/           # 技术文档工程师
├── skills/                    # 技能定义目录 (10个)
│   ├── requirement-analyzer/  # 需求分析技能
│   ├── code-analyzer/         # 代码分析技能
│   ├── test-runner/           # 测试执行技能
│   ├── deployer/              # 部署技能
│   ├── ui-design-toolkit/     # UI设计工具
│   ├── frontend-builder/      # 前端构建工具
│   ├── api-mock/              # API Mock工具
│   ├── monitor-alert/         # 监控告警工具
│   ├── security-scanner/      # 安全扫描工具
│   └── database-designer/     # 数据库设计工具
├── workspaces/               # 工作空间目录 (13个)
│   ├── director/
│   ├── requirement-analyst/
│   ├── architect/
│   ├── product-designer/
│   ├── ui-designer/
│   ├── database-designer/
│   ├── backend-developer/
│   ├── frontend-developer/
│   ├── security-engineer/
│   ├── code-reviewer/
│   ├── tester/
│   ├── operations/
│   └── tech-writer/
└── logs/                     # 工作日志目录
    ├── tasks/                # 任务日志
    ├── workflow/             # 工作流执行日志
    ├── approvals/            # 审批记录
    └── reports/              # 汇总报告
```

## 快速开始

### 1. 安装 OpenClaw

```bash
npm install -g openclaw
# 或
pip install openclaw
```

### 2. 启动系统

```bash
cd pm-team
openclaw start
```

### 3. 发起任务

通过配置的交互渠道发送需求：

```
请帮我开发一个用户登录功能，支持邮箱和手机号登录
```

Director 会自动接收任务，分解并调度各个 Agent 完成工作。

## 使用示例

### 示例1: 完整产品开发

**输入**:
```
开发一个博客系统，支持文章发布、评论、点赞功能
```

**执行流程**:
1. 需求分析 → PRD文档
2. 需求评审 ⚠️
3. 架构设计 ⚠️
4. 数据库设计
5. 产品设计 + UI设计 (并行)
6. 设计评审 ⚠️
7. 后端开发 + 前端开发(Mock) (并行)
8. 前后端联调
9. 代码审查
10. 安全审计 ⚠️
11. 测试验证
12. 测试评审 ⚠️
13. 预发布部署
14. 预发布验证
15. 正式部署
16. 上线验收 ⚠️
17. 文档编写
18. 最终交付

### 示例2: 仅架构设计

**输入**:
```
设计一个高可用的电商系统架构
```

**执行流程**:
1. 需求分析
2. 需求评审 ⚠️
3. 架构设计 ⚠️
4. 返回架构方案

## 错误处理

### 重试策略
| 错误类型 | 处理方式 | 最大重试 |
|----------|----------|----------|
| 网络超时 | 指数退避重试 | 3次 |
| Agent失败 | 分析原因后重试 | 2次 |
| 评审不通过 | 返回修改 | 无限制 |

### 降级策略
- 单Agent失败: 重试或降级到简化方案
- 多Agent失败: 暂停流程，通知用户
- 关键节点失败: 阻断流程，等待人工介入

## 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v2.2.0 | 2024-03-15 | 新增用户故事设计和验收标准定义环节；完善需求分析师的用户故事和验收标准规范；支持Gherkin格式的验收标准 |
| v2.1.0 | 2024-03-15 | 优化工作流程：新增需求评审、设计评审、前后端联调、预发布验证、上线验收环节；新增4个评审节点；调整代码审查在安全审计之前；前端开发使用Mock数据 |
| v2.0.0 | 2024-03-15 | 新增架构师、安全工程师、数据库设计师、文档工程师；完善Skills体系；增加审批节点和错误处理 |
| v1.0.0 | 2024-03-15 | 初始版本，包含9个基础Agent |

## 许可证

MIT License
