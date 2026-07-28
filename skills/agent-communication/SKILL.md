# Skill: Agent Communication

## 描述

Agent间通信工具，支持Agent之间的直接消息传递和协作。基于OpenClaw多Agent协作框架，为团队成员（director、requirement-analyst、architect、backend-developer、frontend-developer、tester等）提供灵活可靠的通信能力，实现跨角色协同与信息流转。

## 功能

### 1. 点对点消息发送

支持指定发送方和接收方的直接消息传递。

**特点**:
- 精确投递到目标Agent
- 支持同步/异步模式
- 可携带任意结构化内容
- 自动记录通信日志

### 2. 广播消息

向所有Agent或指定分组发送通知。

**特点**:
- 一对多通知
- 支持按角色分组广播
- 适用于阶段完成、状态变更等场景
- 接收方无需订阅即可获取

### 3. 请求-响应模式

发送方发起请求并等待接收方返回响应结果。

**特点**:
- 同步阻塞等待
- 支持超时控制
- 自动关联请求与响应
- 失败可自动重试

### 4. 事件订阅和发布

基于事件总线的发布订阅模型。

**特点**:
- 解耦事件生产者与消费者
- 支持多订阅者
- 按事件类型过滤
- 支持通配符订阅

### 5. 消息队列管理

管理消息队列、消息持久化和消息回溯。

**特点**:
- 离线消息存储
- 消息优先级调度
- 死信队列处理
- 消息历史查询

## 通信模式

### 同步通信

发送方等待接收方响应后才继续执行。

**适用场景**:
- 需要立即获取结果（如API查询、状态确认）
- 强一致性要求的操作
- 关键决策点协同

### 异步通信

发送方发送消息后不等待响应，立即继续后续工作。

**适用场景**:
- 通知类消息（如阶段完成通知）
- 非阻塞性协作（如代码提交通知）
- 并行任务调度

### 广播通信

一对多通知模式，向多个Agent同时发送相同消息。

**适用场景**:
- 全员状态同步
- 阶段切换通知
- 紧急事件通告

### 订阅模式

基于事件的通知，Agent订阅感兴趣的事件，事件触发时自动接收通知。

**适用场景**:
- 关注特定阶段产出（如代码审查完成）
- 解耦的协作流程
- 跨角色任务依赖触发

## 使用示例

### 发送消息

点对点异步消息发送，后端工程师通知前端工程师API已就绪：

```json
{
  "action": "send",
  "from": "backend-developer",
  "to": "frontend-developer",
  "type": "api-ready",
  "content": {
    "apiDocs": "/docs/api.md"
  },
  "mode": "async"
}
```

### 广播消息

项目总监向所有成员广播阶段完成通知：

```json
{
  "action": "broadcast",
  "from": "director",
  "type": "stage-complete",
  "content": {
    "stage": "需求分析"
  }
}
```

### 请求响应

前端工程师向后端工程师发起同步请求并设置超时：

```json
{
  "action": "request",
  "from": "frontend-developer",
  "to": "backend-developer",
  "type": "api-query",
  "content": {
    "endpoint": "/api/login"
  },
  "timeout": 30000
}
```

### 订阅事件

测试工程师订阅代码审查完成事件：

```json
{
  "action": "subscribe",
  "subscriber": "tester",
  "event": "code-review-complete"
}
```

## 消息格式定义

### 标准消息结构

```json
{
  "messageId": "msg-{timestamp}-{random}",
  "action": "send | broadcast | request | subscribe | publish",
  "from": "agent-id",
  "to": "agent-id | group-id | *",
  "type": "message-type",
  "content": {
    "key": "value"
  },
  "mode": "sync | async | broadcast | subscribe",
  "priority": "high | normal | low",
  "timeout": 30000,
  "timestamp": "2024-03-15T10:30:00Z",
  "traceId": "trace-{uuid}",
  "replyTo": "message-id",
  "status": "sent | delivered | read | processed"
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| messageId | string | 是 | 消息唯一标识 |
| action | string | 是 | 消息动作类型 |
| from | string | 是 | 发送方Agent ID |
| to | string | 否 | 接收方Agent ID（broadcast/subscribe可不填） |
| type | string | 是 | 消息类型（如 api-ready、stage-complete） |
| content | object | 否 | 消息体内容 |
| mode | string | 否 | 通信模式 |
| priority | string | 否 | 消息优先级，默认normal |
| timeout | number | 否 | 同步请求超时时间（毫秒） |
| timestamp | string | 是 | 消息创建时间（ISO 8601） |
| traceId | string | 否 | 链路追踪ID，用于关联消息流 |
| replyTo | string | 否 | 关联的请求消息ID |
| status | string | 否 | 消息当前状态 |

## 消息优先级

| 优先级 | 值 | 调度策略 | 适用场景 |
|--------|-----|----------|----------|
| high | `high` | 优先投递，抢占队列头部 | 紧急修复、阻塞问题、审批通知 |
| normal | `normal` | 按发送顺序投递（默认） | 常规协作、状态通知、文档交付 |
| low | `low` | 队列空闲时投递，可被高优先级抢占 | 非紧急通知、统计上报、日志同步 |

**优先级使用示例**:

```json
{
  "action": "send",
  "from": "director",
  "to": "security-engineer",
  "type": "urgent-audit",
  "content": {
    "issue": "发现高危漏洞，需立即审计"
  },
  "mode": "async",
  "priority": "high"
}
```

## 消息状态

消息在生命周期中经历的状态流转：

| 状态 | 值 | 说明 |
|------|-----|------|
| 已发送 | `sent` | 发送方已将消息投递到通信通道 |
| 已送达 | `delivered` | 接收方已收到消息（确认接收） |
| 已读 | `read` | 接收方已查看消息内容 |
| 已处理 | `processed` | 接收方已完成消息对应的处理动作 |

**状态流转图**:

```
sent → delivered → read → processed
```

**状态查询示例**:

```json
{
  "action": "query-status",
  "messageId": "msg-1710489000000-abc123"
}
```

**状态查询响应**:

```json
{
  "messageId": "msg-1710489000000-abc123",
  "status": "processed",
  "history": [
    { "status": "sent", "timestamp": "2024-03-15T10:30:00Z" },
    { "status": "delivered", "timestamp": "2024-03-15T10:30:01Z" },
    { "status": "read", "timestamp": "2024-03-15T10:30:05Z" },
    { "status": "processed", "timestamp": "2024-03-15T10:31:20Z" }
  ]
}
```

## 输出格式

### 通信执行结果

```json
{
  "success": true,
  "messageId": "msg-1710489000000-abc123",
  "traceId": "trace-550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-03-15T10:30:00Z",
  "delivered": true,
  "ack": {
    "from": "backend-developer",
    "to": "frontend-developer",
    "receivedAt": "2024-03-15T10:30:01Z"
  }
}
```

## 配置

```json
{
  "channel": "default",
  "persistence": {
    "enabled": true,
    "store": "./logs/communication",
    "retentionDays": 90
  },
  "retry": {
    "maxRetries": 3,
    "backoff": "exponential",
    "initialDelayMs": 1000,
    "maxDelayMs": 30000
  },
  "defaultTimeout": 30000,
  "maxPayloadSize": 1024,
  "tracing": {
    "enabled": true,
    "sampleRate": 1.0
  }
}
```
