# 工作日志目录

此目录用于存储整体项目的工作日志和记录。

## 目录结构

```
logs/
├── tasks/           # 任务日志
│   ├── task-001.md  # 单个任务记录
│   ├── task-002.md
│   └── ...
│
├── workflow/        # 工作流执行日志
│   ├── 2024-03-15/  # 按日期组织
│   │   ├── workflow-001.md
│   │   └── workflow-002.md
│   └── ...
│
├── approvals/       # 审批记录
│   ├── approval-001.md  # 架构设计审批
│   ├── approval-002.md  # 安全审计审批
│   └── ...
│
└── reports/         # 汇总报告
    ├── daily/       # 日报
    ├── weekly/      # 周报
    └── monthly/     # 月报
```

## 日志格式

### 任务日志格式

```markdown
# 任务日志: {task-id}

## 基本信息
- 任务ID: {task-id}
- 任务名称: {name}
- 创建时间: {datetime}
- 完成时间: {datetime}
- 状态: pending/running/completed/failed

## 任务描述
{description}

## 执行流程
| 阶段 | Agent | 开始时间 | 结束时间 | 状态 |
|------|-------|----------|----------|------|
| 需求分析 | requirement-analyst | ... | ... | 完成 |

## 产出物
- [ ] PRD文档
- [ ] 架构设计
- [ ] 代码实现
- [ ] 测试报告

## 备注
{notes}
```

### 审批记录格式

```markdown
# 审批记录: {approval-id}

## 基本信息
- 审批ID: {approval-id}
- 审批类型: 架构设计/安全审计
- 提交时间: {datetime}
- 审批时间: {datetime}
- 审批状态: pending/approved/rejected

## 审批内容
{content summary}

## 审批意见
{comments}

## 审批人
- Director: {decision}
- Architect/Security Engineer: {decision}

## 后续行动
{actions}
```

### 汇总报告格式

```markdown
# {period}报告: {date}

## 概述
{summary}

## 完成任务
| 任务ID | 任务名称 | 状态 | 完成时间 |
|--------|----------|------|----------|
| task-001 | xxx | 完成 | 2024-03-15 |

## 进行中任务
| 任务ID | 任务名称 | 当前进度 | 预计完成 |
|--------|----------|----------|----------|
| task-002 | xxx | 50% | 2024-03-20 |

## 问题与风险
{issues}

## 下一步计划
{plans}
```
