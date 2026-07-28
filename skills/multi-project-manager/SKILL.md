# Skill: Multi-Project Manager

## 描述

多项目管理系统，支持多个项目的并行管理和资源协调。适用于 OpenClaw 多 Agent 协作框架中多项目并行推进场景，提供项目生命周期管理、Agent 资源跨项目分配、优先级排序、进度监控、资源冲突解决以及多项目可视化看板能力，确保有限的 Agent 资源在多个项目间高效流转，避免资源争抢与项目阻塞。

## 功能

### 1. 项目生命周期管理

维护项目的全生命周期状态机，支持项目的创建、启动、暂停、完成、归档等关键流转，并对状态变更进行校验与记录。

**项目状态**:
- `planning` - 规划中：项目已创建，正在进行需求分析、方案设计与资源筹备
- `active` - 进行中：项目已启动，Agent 正在执行任务
- `paused` - 已暂停：项目暂停执行，等待资源、决策或外部依赖
- `completed` - 已完成：项目目标达成，产出已交付验收
- `archived` - 已归档：项目已归档存储，关联资源已释放

**状态流转规则**:
- `planning` → `active`（资源就绪且启动条件满足）
- `active` → `paused`（资源冲突、外部阻塞或人工干预）
- `paused` → `active`（阻塞解除后恢复）
- `active` → `completed`（所有里程碑达成且产出验收通过）
- `completed` → `archived`（归档存储并释放资源）
- `archived` 为终态，不可回退

### 2. 资源池管理

Agent 资源跨项目分配，维护全局资源池与各项目的资源占用关系，支持独占、共享、按比例三种分配模式，并提供资源占用率、负载均衡度等监控指标。

**资源池能力**:
- 全局 Agent 资源注册与发现
- 跨项目资源分配与回收
- 资源占用率实时统计
- 资源负载均衡分析
- 资源释放与回收策略

### 3. 优先级管理

项目间优先级排序，支持基于紧急程度和重要性的多维度优先级评估，为资源分配和冲突解决提供决策依据。

**优先级模型**:
- `critical` - 紧急且重要：战略级项目，资源优先保障
- `high` - 重要不紧急：关键业务项目，优先调度
- `medium` - 紧急不重要：常规迭代项目，按需调度
- `low` - 不紧急不重要：探索性项目，空闲资源调度

### 4. 进度监控

多项目进度总览，实时监控各项目进展状态、里程碑达成情况，并提供项目级、资源级、时间级的多维度进度分析。

**监控维度**:
- 项目维度：各项目整体进度、状态分布、里程碑达成率
- 资源维度：Agent 资源占用率、负载分布、空闲率
- 时间维度：项目时间线、关键里程碑、即将到期任务
- 优先级维度：各优先级项目进展与健康度

### 5. 资源冲突解决

处理 Agent 资源冲突，当多个项目同时争抢同一 Agent 资源时，通过优先级、时间片、资源共享等策略自动或人工解决冲突，并记录冲突处理历史。

**冲突检测能力**:
- 资源占用冲突实时检测
- 冲突影响范围分析
- 冲突严重程度评估
- 冲突解决建议生成

### 6. 项目看板

多项目可视化展示，提供项目卡片、进度条、资源占用、时间线等多维度视图，支持 HTML 交互式看板、Markdown 表格、ASCII 终端三种输出格式。

**看板视图**:
- 多项目总览看板
- 资源占用热力图
- 项目时间线甘特图
- 优先级矩阵图
- 冲突预警面板

## 管理维度

| 维度 | 说明 | 关键指标 |
|------|------|----------|
| 项目维度 | 每个项目独立的工作流和产出 | 项目数、状态分布、平均进度 |
| 资源维度 | Agent 资源的分配和调度 | 资源占用率、负载均衡度、冲突数 |
| 时间维度 | 项目时间线和里程碑 | 里程碑达成率、即将到期数、延期数 |
| 优先级维度 | 项目紧急程度和重要性 | 各优先级项目数、资源倾斜度 |

## 使用示例

### 创建项目

```json
{ "action": "create", "project": { "name": "电商平台", "priority": "high", "deadline": "2024-06-01" } }
```

### 分配资源

```json
{ "action": "allocate", "project": "proj-001", "agents": ["backend-developer", "frontend-developer"], "percentage": 50 }
```

### 查询进度

```json
{ "action": "progress", "scope": "all" }
```

### 解决冲突

```json
{ "action": "resolve", "conflict": { "agent": "backend-developer", "projects": ["proj-001", "proj-002"] } }
```

### 启动项目

```json
{
  "action": "start",
  "projectId": "proj-001",
  "config": {
    "strategy": "proportional",
    "milestones": [
      { "name": "MVP 交付", "deadline": "2024-04-15" },
      { "name": "正式上线", "deadline": "2024-06-01" }
    ]
  }
}
```

### 暂停项目

```json
{
  "action": "pause",
  "projectId": "proj-002",
  "reason": "等待外部 API 联调结果",
  "releaseAgents": true
}
```

### 调整优先级

```json
{
  "action": "reprioritize",
  "projects": [
    { "id": "proj-001", "priority": "critical" },
    { "id": "proj-002", "priority": "medium" },
    { "id": "proj-003", "priority": "low" }
  ]
}
```

### 回收资源

```json
{
  "action": "release",
  "projectId": "proj-003",
  "agents": ["database-designer"],
  "reason": "项目已进入测试阶段，释放设计资源"
}
```

### 生成看板

```json
{
  "action": "dashboard",
  "type": "overview",
  "outputFormat": "ascii"
}
```

## 项目状态

### 状态定义

| 状态 | 状态码 | 说明 | 资源占用 |
|------|--------|------|----------|
| 规划中 | `planning` | 项目已创建，正在进行需求分析和方案设计 | 可预占，未实际占用 |
| 进行中 | `active` | 项目已启动，Agent 正在执行任务 | 实际占用资源 |
| 已暂停 | `paused` | 项目暂停执行，等待资源或决策 | 资源可临时释放或预占 |
| 已完成 | `completed` | 项目目标达成，产出已交付 | 资源待回收 |
| 已归档 | `archived` | 项目已归档存储，资源已释放 | 资源已全部释放 |

### 状态流转图

```
┌───────────┐
│ planning  │
└─────┬─────┘
      │ start
      ▼
┌───────────┐  pause   ┌───────────┐
│  active   │ ───────► │  paused   │
└─────┬─────┘          └─────┬─────┘
      │                       │ resume
      │ complete              │
      ▼                       └──────► active
┌───────────┐
│ completed │
└─────┬─────┘
      │ archive
      ▼
┌───────────┐
│ archived  │ (终态)
└───────────┘
```

## 资源分配策略

### 独占 (exclusive)

Agent 资源被单一项目独占使用，期间不可被其他项目调用。

- **适用场景**：高优先级、关键路径任务，需要 Agent 全程专注
- **资源利用率**：单项目 100%，全局可能存在空闲
- **冲突风险**：低（无并发占用）
- **配置示例**：

```json
{
  "project": "proj-001",
  "agent": "backend-developer",
  "strategy": "exclusive",
  "duration": "2024-03-01/2024-04-15"
}
```

### 共享 (shared)

Agent 资源被多个项目共享使用，通过任务队列和上下文切换实现并发处理。

- **适用场景**：低耦合、可并行的任务，Agent 可在不同项目间快速切换
- **资源利用率**：高，但存在上下文切换成本
- **冲突风险**：中（需队列调度）
- **配置示例**：

```json
{
  "agent": "frontend-developer",
  "strategy": "shared",
  "projects": ["proj-001", "proj-002", "proj-003"],
  "scheduling": "round-robin",
  "contextPreservation": true
}
```

### 按比例 (proportional)

Agent 资源按指定比例分配给多个项目，例如 50%/30%/20%，确保各项目按权重获得资源时间。

- **适用场景**：资源有限但需均衡推进多项目的场景
- **资源利用率**：高，按权重分配
- **冲突风险**：低（已预先约定比例）
- **配置示例**：

```json
{
  "agent": "architect",
  "strategy": "proportional",
  "allocations": [
    { "project": "proj-001", "percentage": 50 },
    { "project": "proj-002", "percentage": 30 },
    { "project": "proj-003", "percentage": 20 }
  ]
}
```

## 冲突解决策略

### 优先级策略

基于项目优先级自动判定资源归属，高优先级项目优先获得资源。

- **决策依据**：项目优先级（critical > high > medium > low）
- **触发条件**：多个项目同时申请同一独占资源
- **处理方式**：高优先级项目获得资源，低优先级项目进入等待队列
- **回退机制**：低优先级项目可申请临时共享或降级运行

```json
{
  "strategy": "priority",
  "conflict": {
    "agent": "backend-developer",
    "projects": [
      { "id": "proj-001", "priority": "critical" },
      { "id": "proj-002", "priority": "high" }
    ]
  },
  "resolution": "proj-001 获得独占资源，proj-002 进入等待队列"
}
```

### 时间片策略

按时间片轮转分配资源，每个项目获得固定时间窗口，循环执行。

- **决策依据**：时间片轮转调度
- **触发条件**：多个同等优先级项目争抢资源
- **处理方式**：按时间片（如 4 小时）轮转分配，循环执行
- **适用场景**：优先级相同且任务可中断恢复的场景

```json
{
  "strategy": "time-slice",
  "conflict": {
    "agent": "tester",
    "projects": ["proj-001", "proj-002", "proj-003"]
  },
  "config": {
    "sliceDuration": "4h",
    "rotationOrder": ["proj-001", "proj-002", "proj-003"],
    "contextPreservation": true
  }
}
```

### 资源共享策略

通过上下文切换实现资源在多个项目间共享，需额外维护上下文状态。

- **决策依据**：资源复用，按需切换
- **触发条件**：任务粒度较细，可频繁切换
- **处理方式**：Agent 在多个项目间按任务粒度切换，维护各项目上下文
- **适用场景**：任务独立、上下文切换成本低的场景

```json
{
  "strategy": "resource-sharing",
  "conflict": {
    "agent": "code-reviewer",
    "projects": ["proj-001", "proj-002"]
  },
  "config": {
    "taskGranularity": "per-task",
    "maxContexts": 3,
    "contextStore": "./workspaces/.multi-project-manager/contexts"
  }
}
```

## 多项目看板输出格式

### ASCII 总览看板

```
┌──────────────────────────────────────────────────────────────────────────┐
│                       多项目看板 - 总览 (2024-W12)                         │
├──────────────────────────────────────────────────────────────────────────┤
│ 项目ID    项目名称      状态      优先级    进度    资源占用    截止日期    │
├──────────────────────────────────────────────────────────────────────────┤
│ proj-001  电商平台      active    critical  65%     3/5 agents   06-01    │
│ proj-002  CRM系统       active    high      40%     2/5 agents   06-15    │
│ proj-003  数据中台      planning  medium    10%     0/5 agents   07-30    │
│ proj-004  营销活动      paused    medium    30%     1/5 agents   08-01    │
│ proj-005  内部工具      archived  low       100%    0/5 agents   -        │
├──────────────────────────────────────────────────────────────────────────┤
│ 资源池: 5 agents | 已分配 6 | 空闲 0 | 冲突 1                              │
│ 时间线: 2024-W12 | 本周里程碑: 3 | 即将到期: 1 | 延期: 0                    │
│ 健康度: ● 电商平台 ● CRM系统 ● 数据中台 ● 营销活动 ● 内部工具              │
└──────────────────────────────────────────────────────────────────────────┘
```

### 资源占用矩阵

```
┌─────────────────────────────────────────────────────────────────┐
│                  资源占用矩阵 - Agent × 项目                      │
├─────────────────────┬────────┬────────┬────────┬────────┬───────┤
│ Agent               │ proj-001│ proj-002│ proj-003│ proj-004│ 空闲  │
├─────────────────────┼────────┼────────┼────────┼────────┼───────┤
│ requirement-analyst │   -    │  50%   │  50%   │   -    │  0%   │
│ architect           │  100%  │   -    │   -    │   -    │  0%   │
│ database-designer   │  50%   │  50%   │   -    │   -    │  0%   │
│ frontend-developer  │  100%  │   -    │   -    │   -    │  0%   │
│ backend-developer   │  50%   │  50%   │   -    │   ⚠    │  0%   │
│ tester              │   -    │   -    │   -    │  100%  │  0%   │
├─────────────────────┼────────┼────────┼────────┼────────┼───────┤
│ 项目占用合计         │  3.0   │  1.5   │  0.5   │  1.0   │       │
└─────────────────────┴────────┴────────┴────────┴────────┴───────┘
图例: 数字为占用百分比 | ⚠ 表示资源冲突 | - 表示未分配
```

### 项目时间线甘特图

```
┌──────────────────────────────────────────────────────────────────────┐
│                         项目时间线 - 2024 Q2                          │
├──────────────────────────────────────────────────────────────────────┤
│ 项目       │ 03月            │ 04月            │ 05月            │ 06月 │
├──────────────────────────────────────────────────────────────────────┤
│ 电商平台   │████████████████│████████████████│████████████████│★上线 │
│ CRM系统    │      ████████████│████████████████│████████████████│██   │
│ 数据中台   │░░░░░░░░░░░░░░░░│░░░░░░░░░░░░░░░░│████████████████│████ │
│ 营销活动   │      ░░░░░░░░░░░│░░░░░░░░░░░░░░░│░░░░░░░░░░░░░░░░│████ │
├──────────────────────────────────────────────────────────────────────┤
│ 图例: █ 进行中  ░ 规划中  ★ 里程碑  ▓ 已完成  ⚠ 延期                │
└──────────────────────────────────────────────────────────────────────┘
```

### JSON 结构化输出

```json
{
  "summary": {
    "totalProjects": 5,
    "statusCounts": {
      "planning": 1,
      "active": 2,
      "paused": 1,
      "completed": 0,
      "archived": 1
    },
    "resourcePool": {
      "totalAgents": 5,
      "allocated": 6,
      "idle": 0,
      "conflicts": 1
    },
    "timeline": {
      "week": "2024-W12",
      "milestonesThisWeek": 3,
      "expiringSoon": 1,
      "overdue": 0
    },
    "generatedAt": "2024-03-20T15:30:00+08:00"
  },
  "projects": [
    {
      "id": "proj-001",
      "name": "电商平台",
      "status": "active",
      "priority": "critical",
      "progress": 65,
      "deadline": "2024-06-01",
      "allocatedAgents": 3,
      "health": "green"
    },
    {
      "id": "proj-002",
      "name": "CRM系统",
      "status": "active",
      "priority": "high",
      "progress": 40,
      "deadline": "2024-06-15",
      "allocatedAgents": 2,
      "health": "yellow"
    }
  ],
  "conflicts": [
    {
      "agent": "backend-developer",
      "projects": ["proj-001", "proj-002"],
      "severity": "medium",
      "suggestedStrategy": "proportional"
    }
  ]
}
```

## 项目对象模型

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Project",
  "type": "object",
  "required": ["id", "name", "status", "priority", "progress", "createdAt", "updatedAt"],
  "properties": {
    "id": {
      "type": "string",
      "description": "项目唯一标识",
      "pattern": "^proj-[a-zA-Z0-9_-]+$"
    },
    "name": {
      "type": "string",
      "description": "项目名称",
      "minLength": 1,
      "maxLength": 100
    },
    "description": {
      "type": "string",
      "description": "项目详细描述"
    },
    "status": {
      "type": "string",
      "enum": ["planning", "active", "paused", "completed", "archived"],
      "default": "planning"
    },
    "priority": {
      "type": "string",
      "enum": ["low", "medium", "high", "critical"],
      "default": "medium"
    },
    "progress": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100,
      "default": 0,
      "description": "项目整体进度百分比"
    },
    "deadline": {
      "type": "string",
      "format": "date",
      "description": "项目截止日期"
    },
    "startDate": {
      "type": "string",
      "format": "date"
    },
    "completedAt": {
      "type": "string",
      "format": "date-time"
    },
    "allocatedAgents": {
      "type": "array",
      "description": "已分配的 Agent 列表",
      "items": {
        "type": "object",
        "required": ["agent", "strategy"],
        "properties": {
          "agent": {
            "type": "string",
            "enum": [
              "requirement-analyst",
              "product-designer",
              "ui-designer",
              "architect",
              "database-designer",
              "frontend-developer",
              "backend-developer",
              "tester",
              "code-reviewer",
              "security-engineer",
              "tech-writer",
              "operations",
              "director"
            ]
          },
          "strategy": {
            "type": "string",
            "enum": ["exclusive", "shared", "proportional"],
            "default": "shared"
          },
          "percentage": {
            "type": "integer",
            "minimum": 0,
            "maximum": 100,
            "description": "按比例分配时的占用百分比"
          }
        }
      },
      "default": []
    },
    "milestones": {
      "type": "array",
      "description": "项目里程碑列表",
      "items": {
        "type": "object",
        "required": ["name", "deadline"],
        "properties": {
          "name": { "type": "string" },
          "deadline": { "type": "string", "format": "date" },
          "achieved": { "type": "boolean", "default": false },
          "achievedAt": { "type": "string", "format": "date-time" }
        }
      },
      "default": []
    },
    "health": {
      "type": "string",
      "enum": ["green", "yellow", "red"],
      "description": "项目健康度：green 正常 / yellow 预警 / red 异常"
    },
    "owner": {
      "type": "string",
      "description": "项目责任人（人工负责人）"
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "default": []
    },
    "createdAt": { "type": "string", "format": "date-time" },
    "updatedAt": { "type": "string", "format": "date-time" }
  }
}
```

## 配置

```json
{
  "storage": {
    "backend": "file",
    "path": "./workspaces/.multi-project-manager",
    "autosave": true,
    "autosaveInterval": "30s"
  },
  "resourcePool": {
    "agents": [
      "requirement-analyst",
      "product-designer",
      "ui-designer",
      "architect",
      "database-designer",
      "frontend-developer",
      "backend-developer",
      "tester",
      "code-reviewer",
      "security-engineer",
      "tech-writer",
      "operations",
      "director"
    ],
    "defaultStrategy": "shared",
    "maxSharedProjects": 3,
    "contextPreservation": true
  },
  "conflictResolution": {
    "defaultStrategy": "priority",
    "autoResolve": true,
    "escalationThreshold": 3,
    "notificationOnConflict": true
  },
  "priority": {
    "weights": {
      "critical": 100,
      "high": 75,
      "medium": 50,
      "low": 25
    },
    "autoAdjustOnConflict": true
  },
  "dashboard": {
    "defaultFormat": "ascii",
    "refreshInterval": "60s",
    "timezone": "Asia/Shanghai"
  },
  "history": {
    "enabled": true,
    "retention": "90d",
    "maxRecordsPerProject": 500
  }
}
```
