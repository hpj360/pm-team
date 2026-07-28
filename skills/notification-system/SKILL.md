# Skill: Notification System

## 描述

多渠道通知系统，支持多种通知方式和通知规则配置。为多 Agent 协作框架提供统一的消息触达能力，确保关键事件（阶段完成、错误、审批请求等）能够及时、准确地通知到相关角色，并支持通知模板管理、优先级升级与历史记录追踪。

## 功能

### 1. 多渠道通知

支持通过多种渠道并发发送通知，满足不同场景下的触达需求。

**支持渠道**:
- 飞书（feishu）：通过飞书机器人发送消息
- 邮件（email）：SMTP 发送邮件
- Webhook（webhook）：HTTP 回调通知
- 站内信（in-app）：系统内部消息
- SMS（sms）：短信通知（预留）

### 2. 通知模板管理

通过模板统一管理通知内容，支持变量替换，避免重复构造消息体。

**模板能力**:
- 模板注册与更新
- 变量替换（`{{ variable }}` 语法）
- 多渠道差异化模板（同一事件在不同渠道呈现不同格式）
- 模板版本管理

### 3. 通知规则配置

声明式配置"何时通知、通知谁、通知什么"，将事件与通知行为解耦。

**规则要素**:
- 触发事件（event）：如 `stage.complete`、`error.occurred`
- 目标接收人（recipients）：角色、用户列表或动态解析
- 通知渠道（channels）：一个或多个渠道
- 通知模板（template）：引用已注册模板
- 优先级（priority）：low / normal / high / urgent
- 过滤条件（filter）：基于事件数据的条件表达式

### 4. 通知优先级和升级机制

按优先级分级处理通知，并在未及时处理时自动升级。

**优先级**:
- low：常规通知，可延迟
- normal：标准通知
- high：重要通知，需尽快处理
- urgent：紧急通知，多渠道并发 + 重复提醒

**升级机制**:
- 超时未读自动升级渠道（站内信 → 飞书 → 邮件 → SMS）
- 升级链路可配置
- 升级间隔可配置（默认 5m / 15m / 30m）

### 5. 通知历史记录

完整记录通知生命周期，便于审计、追溯与统计分析。

**记录字段**:
- 通知 ID、时间戳
- 事件来源、模板、渠道、接收人
- 当前状态与状态变更历史
- 重试次数与失败原因

## 通知渠道

| 渠道 | 标识 | 说明 | 实现方式 |
|------|------|------|----------|
| 飞书 | `feishu` | 通过飞书机器人发送消息 | 调用飞书开放 API / Webhook |
| 邮件 | `email` | SMTP 发送邮件 | SMTP 客户端 |
| Webhook | `webhook` | HTTP 回调通知 | POST 请求到指定 URL |
| 站内信 | `in-app` | 系统内部消息 | 写入系统消息表 |
| SMS | `sms` | 短信通知（预留） | 短信网关接口（预留扩展） |

## 通知类型

- **阶段完成通知**（`stage.complete`）：某个工作流阶段完成时触发
- **错误通知**（`error.occurred`）：执行过程中发生错误时触发
- **审批请求通知**（`approval.required`）：需要人工审批时触发
- **任务分配通知**（`task.assigned`）：任务被分配给某角色时触发
- **里程碑达成通知**（`milestone.reached`）：项目里程碑达成时触发

## 使用示例

### 1. 发送单条通知

```json
{
  "action": "send",
  "channel": "feishu",
  "to": ["user1"],
  "template": "stage-complete",
  "data": {
    "stage": "需求分析",
    "status": "完成"
  }
}
```

### 2. 批量广播通知

```json
{
  "action": "broadcast",
  "channels": ["feishu", "email"],
  "template": "approval-required",
  "data": {
    "stage": "架构设计",
    "approvers": ["director", "architect"],
    "deadline": "2026-07-28T18:00:00+08:00"
  }
}
```

### 3. 注册通知规则

```json
{
  "action": "addRule",
  "rule": {
    "event": "stage.complete",
    "channels": ["feishu"],
    "template": "stage-complete"
  }
}
```

### 4. 注册通知模板

```json
{
  "action": "addTemplate",
  "template": {
    "name": "stage-complete",
    "title": "阶段完成通知",
    "channels": {
      "feishu": "【阶段完成】{{ stage }}阶段已{{ status }}，请关注后续工作。",
      "email": {
        "subject": "[PM-Team] {{ stage }}阶段完成",
        "body": "阶段：{{ stage }}\n状态：{{ status }}\n时间：{{ timestamp }}"
      }
    }
  }
}
```

### 5. 查询通知历史

```json
{
  "action": "history",
  "filter": {
    "event": "stage.complete",
    "status": "delivered",
    "since": "2026-07-01T00:00:00+08:00"
  },
  "limit": 50
}
```

## 通知模板格式

模板支持变量替换，使用 `{{ variable }}` 语法。变量来源于事件 `data` 字段以及系统上下文（如 `timestamp`、`project`、`operator`）。

**模板结构**:

```json
{
  "name": "stage-complete",
  "title": "阶段完成通知",
  "priority": "normal",
  "channels": {
    "feishu": "【阶段完成】{{ stage }}阶段已{{ status }}，由 {{ operator }} 完成。",
    "email": {
      "subject": "[PM-Team] {{ stage }}阶段完成",
      "body": "阶段：{{ stage }}\n状态：{{ status }}\n操作人：{{ operator }}\n时间：{{ timestamp }}"
    },
    "webhook": {
      "method": "POST",
      "body": {
        "event": "stage.complete",
        "stage": "{{ stage }}",
        "status": "{{ status }}"
      }
    },
    "in-app": {
      "title": "阶段完成",
      "content": "{{ stage }}阶段已{{ status }}"
    }
  }
}
```

**变量替换规则**:
- 嵌套字段使用点号访问：`{{ user.name }}`
- 未定义变量保留原样，不报错
- 数组类型变量会以逗号拼接为字符串

## 通知状态追踪

每条通知具备完整的状态机，记录从入队到终态的全过程。

| 状态 | 标识 | 说明 |
|------|------|------|
| 已入队 | `queued` | 通知已生成，等待发送 |
| 已发送 | `sent` | 已通过渠道发出，未确认送达 |
| 已送达 | `delivered` | 渠道返回送达确认 |
| 已读 | `read` | 接收人已查看（站内信/邮件） |
| 失败 | `failed` | 发送失败，达到最大重试次数 |

**状态流转**:

```
queued → sent → delivered → read
   │       │       │
   └───────┴───────┴──→ failed
```

**状态查询示例**:

```json
{
  "action": "status",
  "notificationId": "ntf-20260726-0001"
}
```

**状态查询响应**:

```json
{
  "notificationId": "ntf-20260726-0001",
  "event": "stage.complete",
  "channel": "feishu",
  "to": ["user1"],
  "priority": "normal",
  "status": "delivered",
  "history": [
    { "status": "queued", "at": "2026-07-26T10:00:00+08:00" },
    { "status": "sent", "at": "2026-07-26T10:00:01+08:00" },
    { "status": "delivered", "at": "2026-07-26T10:00:02+08:00" }
  ],
  "retries": 0
}
```

## 输出格式

```json
{
  "summary": {
    "total": 100,
    "byStatus": {
      "queued": 5,
      "sent": 10,
      "delivered": 75,
      "read": 8,
      "failed": 2
    },
    "byChannel": {
      "feishu": 60,
      "email": 25,
      "in-app": 10,
      "webhook": 5
    }
  },
  "recentNotifications": [
    {
      "notificationId": "ntf-20260726-0001",
      "event": "stage.complete",
      "channel": "feishu",
      "status": "delivered",
      "at": "2026-07-26T10:00:02+08:00"
    }
  ]
}
```

## 配置

```json
{
  "channels": {
    "feishu": {
      "enabled": true,
      "webhook": "https://open.feishu.cn/xxx",
      "botName": "pm-team-bot"
    },
    "email": {
      "enabled": true,
      "smtp": {
        "host": "smtp.example.com",
        "port": 465,
        "from": "pm-team@example.com"
      }
    },
    "webhook": {
      "enabled": true,
      "timeout": "10s",
      "retry": 3
    },
    "in-app": {
      "enabled": true,
      "retention": "30d"
    },
    "sms": {
      "enabled": false,
      "provider": "reserved"
    }
  },
  "escalation": {
    "enabled": true,
    "chain": ["in-app", "feishu", "email", "sms"],
    "intervals": ["5m", "15m", "30m"]
  },
  "retry": {
    "maxRetries": 3,
    "backoff": "exponential",
    "initialDelayMs": 1000,
    "maxDelayMs": 30000
  },
  "history": {
    "retention": "90d",
    "storage": "logs/notifications"
  }
}
```
