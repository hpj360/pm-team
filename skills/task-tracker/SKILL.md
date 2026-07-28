# Skill: Task Tracker

## 描述

任务进度跟踪工具，实时监控任务执行状态和进度。适用于 OpenClaw 多 Agent 协作框架中各 Agent 任务的全生命周期跟踪、依赖编排与进度汇总。

## 功能

### 1. 任务状态管理

维护任务的全生命周期状态机，支持状态流转与校验。

**任务状态**:
- `pending` - 已创建，等待执行（依赖未完成或资源未就绪）
- `running` - 正在执行中
- `completed` - 已成功完成
- `failed` - 执行失败
- `blocked` - 被阻塞（依赖失败、缺少资源、人工干预）

**状态流转规则**:
- `pending` → `running`（依赖全部完成且资源就绪）
- `running` → `completed` / `failed` / `blocked`
- `blocked` → `running`（阻塞解除后恢复）
- `failed` → `pending`（重试时回退）
- `completed` 为终态，不可回退

### 2. 进度实时更新

支持任务执行过程中进度数据的实时上报与聚合。

**更新字段**:
- `progress` - 进度百分比 (0-100)
- `currentStage` - 当前执行阶段名称
- `estimatedTime` - 预计完成时间 (ISO 8601)
- `updatedAt` - 最后更新时间戳
- `message` - 阶段性说明或备注

### 3. 任务依赖关系追踪

维护任务之间的 DAG（有向无环图）依赖关系，自动判定可执行任务与阻塞任务。

**依赖类型**:
- `hard` - 强依赖：上游任务必须 `completed` 才能启动
- `soft` - 弱依赖：上游任务 `completed` 或 `failed` 均可启动
- `resource` - 资源依赖：等待共享资源释放

**检测能力**:
- 循环依赖检测
- 关键路径计算
- 阻塞链路回溯
- 并行可执行任务识别

### 4. 历史记录和统计

记录任务状态变更历史，提供多维度的统计能力。

**统计维度**:
- 按 Agent 统计任务数、平均耗时、成功率
- 按状态统计任务分布
- 按时间段统计吞吐量
- 任务耗时分布（P50 / P90 / P99）
- 阻塞时长 Top N

### 5. 进度报告生成

生成结构化的进度报告，支持 Markdown 表格、JSON 数据等多种输出格式。

**报告类型**:
- `progress` - 整体进度概览
- `agent` - 按 Agent 维度统计
- `dependency` - 依赖关系视图
- `history` - 状态变更历史
- `summary` - 阶段性汇总报告

## 使用示例

### 创建任务

```json
{
  "action": "create",
  "task": {
    "id": "task-001",
    "name": "需求分析",
    "agent": "requirement-analyst",
    "dependencies": []
  }
}
```

### 更新进度

```json
{
  "action": "update",
  "taskId": "task-001",
  "status": "running",
  "progress": 50,
  "currentStage": "分析需求"
}
```

### 查询状态

```json
{
  "action": "query",
  "taskId": "task-001"
}
```

### 生成报告

```json
{
  "action": "report",
  "type": "progress"
}
```

### 创建带依赖的任务

```json
{
  "action": "create",
  "task": {
    "id": "task-002",
    "name": "接口设计",
    "agent": "architect",
    "dependencies": [
      { "taskId": "task-001", "type": "hard" }
    ],
    "priority": "high",
    "estimatedHours": 8
  }
}
```

### 批量查询任务

```json
{
  "action": "query",
  "filter": {
    "agent": "backend-developer",
    "status": ["running", "blocked"],
    "updatedAtAfter": "2026-07-01T00:00:00Z"
  }
}
```

### 标记阻塞

```json
{
  "action": "block",
  "taskId": "task-003",
  "reason": "等待数据库实例审批",
  "blockedBy": "task-001"
}
```

## 任务状态模型

任务对象采用如下 JSON Schema 定义：

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Task",
  "type": "object",
  "required": ["id", "name", "agent", "status", "progress", "createdAt", "updatedAt"],
  "properties": {
    "id": {
      "type": "string",
      "description": "任务唯一标识",
      "pattern": "^task-[a-zA-Z0-9_-]+$"
    },
    "name": {
      "type": "string",
      "description": "任务名称",
      "minLength": 1,
      "maxLength": 100
    },
    "description": {
      "type": "string",
      "description": "任务详细描述"
    },
    "agent": {
      "type": "string",
      "description": "负责执行的 Agent 标识",
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
    "status": {
      "type": "string",
      "enum": ["pending", "running", "completed", "failed", "blocked"],
      "default": "pending"
    },
    "progress": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100,
      "default": 0,
      "description": "任务进度百分比"
    },
    "currentStage": {
      "type": "string",
      "description": "当前执行阶段名称"
    },
    "priority": {
      "type": "string",
      "enum": ["low", "medium", "high", "critical"],
      "default": "medium"
    },
    "dependencies": {
      "type": "array",
      "description": "依赖任务列表",
      "items": {
        "type": "object",
        "required": ["taskId", "type"],
        "properties": {
          "taskId": { "type": "string" },
          "type": {
            "type": "string",
            "enum": ["hard", "soft", "resource"],
            "default": "hard"
          }
        }
      },
      "default": []
    },
    "assignee": {
      "type": "string",
      "description": "任务责任人（人工负责人）"
    },
    "estimatedHours": {
      "type": "number",
      "minimum": 0,
      "description": "预估工时（小时）"
    },
    "estimatedTime": {
      "type": "string",
      "format": "date-time",
      "description": "预计完成时间 (ISO 8601)"
    },
    "startedAt": {
      "type": "string",
      "format": "date-time"
    },
    "completedAt": {
      "type": "string",
      "format": "date-time"
    },
    "blockedReason": {
      "type": "string",
      "description": "阻塞原因说明"
    },
    "blockedBy": {
      "type": "string",
      "description": "阻塞来源任务 ID"
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "default": []
    },
    "createdAt": {
      "type": "string",
      "format": "date-time"
    },
    "updatedAt": {
      "type": "string",
      "format": "date-time"
    },
    "history": {
      "type": "array",
      "description": "状态变更历史",
      "items": {
        "type": "object",
        "required": ["status", "timestamp"],
        "properties": {
          "status": { "type": "string" },
          "progress": { "type": "integer" },
          "timestamp": { "type": "string", "format": "date-time" },
          "message": { "type": "string" }
        }
      }
    }
  }
}
```

## 进度计算规则

### 单任务进度

- 任务自身 `progress` 字段由执行 Agent 直接上报，取值 0-100。
- 状态与进度对应约束：
  - `pending` → `progress` 应为 0
  - `running` → `progress` 应在 (0, 100) 区间
  - `completed` → `progress` 应为 100
  - `failed` / `blocked` → `progress` 保留最后一次上报值
- 当 Agent 仅上报 `currentStage` 而未上报 `progress` 时，可按阶段映射估算：
  `progress = (已完成阶段数 / 总阶段数) × 100`

### 父子任务进度

若任务被拆分为子任务，父任务进度按子任务权重聚合：

```
父任务进度 = Σ (子任务进度 × 子任务权重) / Σ 子任务权重
```

未指定权重时采用等权平均。仅统计状态非 `failed` 的子任务；`failed` 子任务不计入进度，但需在报告中标记。

### 全局进度

按任务数量加权计算：

```
全局进度 = Σ (各任务 progress) / 任务总数
```

按 Agent 维度统计时：

```
Agent 进度 = 该 Agent 名下任务 progress 加权平均
```

### 预计完成时间

- 单任务：`预计完成时间 = 当前时间 + (预估剩余工时 × (1 - progress/100))`
- 若历史数据充足，可基于同类型任务 P50 耗时进行修正。
- 全局预计完成时间取所有 `running` 任务中最晚的 `estimatedTime`。

## 报告输出格式

### 整体进度报告 (Markdown 表格)

```markdown
# 任务进度报告

**生成时间**: 2026-07-26 15:30:00
**任务总数**: 12
**整体进度**: 65%

## 任务概览

| 任务ID    | 任务名称       | 负责Agent          | 状态      | 进度  | 当前阶段     | 预计完成时间        |
| --------- | -------------- | ------------------- | --------- | ----- | ------------ | ------------------- |
| task-001  | 需求分析       | requirement-analyst | completed | 100%  | -            | 2026-07-25 18:00    |
| task-002  | 接口设计       | architect           | completed | 100%  | -            | 2026-07-26 10:00    |
| task-003  | 数据库设计     | database-designer   | running   | 75%   | 表结构评审   | 2026-07-26 20:00    |
| task-004  | 前端开发       | frontend-developer  | running   | 40%   | 页面搭建     | 2026-07-28 12:00    |
| task-005  | 后端开发       | backend-developer   | running   | 35%   | 接口实现     | 2026-07-28 18:00    |
| task-006  | 安全审计       | security-engineer   | blocked   | 0%    | -            | -                   |
| task-007  | 测试用例编写   | tester              | pending   | 0%    | -            | -                   |

## 状态分布

| 状态      | 数量 | 占比   |
| --------- | ---- | ------ |
| completed | 2    | 28.6%  |
| running   | 3    | 42.9%  |
| pending   | 1    | 14.3%  |
| blocked   | 1    | 14.3%  |
| failed    | 0    | 0.0%   |

## 阻塞任务

| 任务ID   | 任务名称   | 阻塞原因           | 阻塞来源  | 持续时长 |
| -------- | ---------- | ------------------ | --------- | -------- |
| task-006 | 安全审计   | 等待代码评审完成   | task-005  | 2h 15m   |

## Agent 维度统计

| Agent                | 任务数 | 平均进度 | 完成数 | 运行中 | 阻塞 |
| --------------------- | ------ | -------- | ------ | ------ | ---- |
| requirement-analyst   | 1      | 100%     | 1      | 0      | 0    |
| architect             | 1      | 100%     | 1      | 0      | 0    |
| database-designer     | 1      | 75%      | 0      | 1      | 0    |
| frontend-developer    | 1      | 40%      | 0      | 1      | 0    |
| backend-developer     | 1      | 35%      | 0      | 1      | 0    |
| security-engineer     | 1      | 0%       | 0      | 0      | 1    |
| tester                | 1      | 0%       | 0      | 0      | 0    |
```

## 输出格式

JSON 结构化输出，便于上游 Agent 解析与二次加工：

```json
{
  "summary": {
    "totalTasks": 12,
    "overallProgress": 65,
    "statusCounts": {
      "pending": 1,
      "running": 3,
      "completed": 2,
      "failed": 0,
      "blocked": 1
    },
    "generatedAt": "2026-07-26T15:30:00+08:00"
  },
  "tasks": [
    {
      "id": "task-003",
      "name": "数据库设计",
      "agent": "database-designer",
      "status": "running",
      "progress": 75,
      "currentStage": "表结构评审",
      "estimatedTime": "2026-07-26T20:00:00+08:00",
      "updatedAt": "2026-07-26T15:00:00+08:00"
    }
  ],
  "blockedTasks": [
    {
      "id": "task-006",
      "name": "安全审计",
      "reason": "等待代码评审完成",
      "blockedBy": "task-005",
      "blockedDuration": "2h 15m"
    }
  ]
}
```

## 配置

```json
{
  "storage": {
    "backend": "file",
    "path": "./workspaces/.task-tracker",
    "autosave": true,
    "autosaveInterval": "10s"
  },
  "history": {
    "enabled": true,
    "retention": "30d",
    "maxRecordsPerTask": 100
  },
  "progress": {
    "autoEstimate": true,
    "staleThreshold": "1h",
    "defaultWeight": 1
  },
  "report": {
    "defaultType": "progress",
    "format": "markdown",
    "timezone": "Asia/Shanghai"
  },
  "dependency": {
    "detectCycle": true,
    "autoStart": true,
    "criticalPath": true
  }
}
```

> **配置对齐说明**: 本 Skill 的配置与 `openclaw.json` 的 `taskTracking` 节点保持一致。存储路径统一为 `./workspaces/.task-tracker`（而非 `./logs/tasks`），保留期统一为 `"30d"` 字符串格式。`openclaw.json` 中的 `taskTracking.integrations` 节点声明了本 Skill 与其他 Skill 的集成关系。

## 集成关系

本 Skill 作为 OpenClaw 框架的任务跟踪中枢，与项目内其他 Skill 形成数据闭环。集成关系在 `openclaw.json` 的 `taskTracking.integrations` 节点中声明，并在 `skills.bindings` 中绑定到 Director Agent 作为主要使用者。

### 与 smart-scheduler 的集成

任务跟踪数据为智能调度提供历史样本，调度结果反哺任务预估。

| 数据流向 | 内容 | 触发时机 |
| -------- | ---- | -------- |
| task-tracker → smart-scheduler | 任务历史记录（耗时、成功率、阻塞时长） | 任务 `completed` 时推送 |
| task-tracker → smart-scheduler | 当前负载快照（各 Agent 运行中任务数） | 调度器请求时实时查询 |
| smart-scheduler → task-tracker | 预估耗时（基于 P50/P90 历史数据） | 任务创建时回写 `estimatedHours` |
| smart-scheduler → task-tracker | 调度决策结果（分配的 Agent） | 调度完成后回写 `task.agent` |

**协作约束**: 当 smart-scheduler 重新分配任务时，task-tracker 需保留原 Agent 的历史记录并记录变更轨迹，避免历史数据丢失影响调度学习。

### 与 quality-assessor 的集成

任务完成时自动触发质量评估，评估结果影响任务是否真正标记为完成。

| 数据流向 | 内容 | 触发时机 |
| -------- | ---- | -------- |
| task-tracker → quality-assessor | 任务完成事件（taskId / agent / 产出物路径） | 任务 `running → completed` 状态流转时 |
| quality-assessor → task-tracker | 质量评分与等级（A/B/C/D/F） | 评估完成后回写至 `task.history` |
| quality-assessor → task-tracker | 是否通过基线（passed: true/false） | 评估完成后回写至任务元数据 |

**协作约束**: 若质量评估未通过基线（`passed: false`），任务不应标记为 `completed`，而应回退至 `pending` 状态并记录原因，等待 Agent 修复后重新提交。此回退逻辑由 Director 在评审节点决策。

### 与 multi-project-manager 的集成

任务视图与项目视图交叉聚合，支撑跨项目资源决策。

| 数据流向 | 内容 | 触发时机 |
| -------- | ---- | -------- |
| task-tracker → multi-project-manager | 各项目下的任务进度汇总 | 项目进度查询时实时聚合 |
| multi-project-manager → task-tracker | 资源分配策略（exclusive/shared/proportional） | 资源分配变更时通知 |
| multi-project-manager → task-tracker | 项目优先级（critical/high/medium/low） | 优先级调整时回写至任务 `priority` 字段 |

**协作约束**: 当多项目资源冲突触发 `time-slice` 策略时，task-tracker 需暂停被切换出项目的运行中任务（标记为 `blocked`，原因为"资源时间片切换"），待时间片恢复时自动回退至 `running`。

### 与 notification-system 的集成

任务状态变更事件驱动通知发送，确保关键节点信息及时触达。

| 事件 | 通知渠道 | 优先级 | 触发条件 |
| ---- | -------- | ------ | -------- |
| `task.created` | feishu | normal | 新任务创建并分配给 Agent |
| `task.completed` | feishu | normal | 任务成功完成 |
| `task.blocked` | feishu, email | high | 任务被阻塞，需人工干预 |
| `task.failed` | feishu, email | high | 任务执行失败 |
| `task.approval_required` | feishu, email | high | 任务进入评审节点，需审批 |

**协作约束**: 通知内容应包含任务 ID、任务名称、负责 Agent、当前状态、阻塞原因（若有）及预计恢复时间。通知模板在 `openclaw.json` 的 `notifications.templates` 节点中定义。

### 与 dashboard-visualizer 的集成

任务进度数据实时推送至可视化看板，提供全局视图。

| 数据流向 | 内容 | 触发时机 |
| -------- | ---- | -------- |
| task-tracker → dashboard-visualizer | 任务状态变更增量 | 任何状态变更时实时推送 |
| task-tracker → dashboard-visualizer | 全量任务快照 | 看板初始化或刷新时查询 |
| dashboard-visualizer → task-tracker | 用户操作（如手动标记阻塞/恢复） | 看板交互回调 |

**协作约束**: 看板数据刷新间隔为 30 秒（与 `openclaw.json` 的 `dashboard.refreshInterval` 一致），但关键事件（`task.failed` / `task.blocked`）应立即推送，不等下一个刷新周期。

### 与 openclaw.json 工作流的对齐

本 Skill 的任务模型与 `openclaw.json` 的 `workflow.stages` 一一对应。Director 在流程启动时应为每个工作流阶段创建对应任务，并按工作流定义建立依赖关系：

| 工作流阶段 | 任务 ID 模式 | 负责Agent | 依赖类型 |
| ---------- | ----------- | --------- | -------- |
| 需求分析 | `task-req-analysis` | requirement-analyst | - |
| 需求评审 | `task-req-review` | director | hard ← 需求分析 |
| 架构设计 | `task-arch-design` | architect | hard ← 需求评审 |
| 数据库设计 | `task-db-design` | database-designer | hard ← 架构设计 |
| 产品设计 | `task-product-design` | product-designer | hard ← 架构设计 |
| UI设计 | `task-ui-design` | ui-designer | hard ← 产品设计 |
| 设计评审 | `task-design-review` | director | hard ← UI设计 |
| 后端开发 | `task-backend-dev` | backend-developer | hard ← 设计评审 |
| 前端开发 | `task-frontend-dev` | frontend-developer | hard ← 设计评审 |
| 前后端联调 | `task-integration` | backend-developer | hard ← 后端开发, hard ← 前端开发 |
| 代码审查 | `task-code-review` | code-reviewer | hard ← 前后端联调 |
| 安全审计 | `task-security-audit` | security-engineer | hard ← 代码审查 |
| 测试验证 | `task-testing` | tester | hard ← 安全审计 |
| 测试评审 | `task-test-review` | director | hard ← 测试验证 |
| 预发布部署 | `task-staging-deploy` | operations | hard ← 测试评审 |
| 预发布验证 | `task-staging-verify` | tester | hard ← 预发布部署 |
| 正式部署 | `task-production-deploy` | operations | hard ← 预发布验证 |
| 上线验收 | `task-acceptance` | director | hard ← 正式部署 |
| 文档编写 | `task-docs` | tech-writer | soft ← 上线验收 |

> **并行阶段说明**: 后端开发与前端开发互为并行（均仅依赖设计评审），task-tracker 应识别二者均可执行并允许同时 `running`。产品设计依赖架构设计，但 UI 设计依赖产品设计而非架构设计，形成 `架构设计 → 产品设计 → UI设计` 的串行链路。
