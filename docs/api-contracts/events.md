# Kafka 事件契约

> 本文档定义红方文件汇聚平台各微服务间异步通信的事件契约。
> 规范：CloudEvents 1.0
> 版本：v1.0

---

## 一、命名规范

### 1.1 Topic 命名

格式：`{domain}.{event}.{version}`

- `domain`：业务域，小写（如 `redteam.file`、`redteam.task`）
- `event`：事件名，小写，过去时态（如 `uploaded`、`parsed`）
- `version`：事件版本，`v1`、`v2`

示例：
- `redteam.file.uploaded.v1`
- `redteam.task.completed.v1`
- `redteam.target.created.v1`

### 1.2 Topic 列表

| Topic                              | 生产者               | 消费者                                  | 分区数 | 保留时间 |
| ---------------------------------- | -------------------- | --------------------------------------- | ------ | -------- |
| redteam.file.uploaded.v1           | upload-service       | parse-service, notification-service     | 12     | 7 天     |
| redteam.file.parsed.v1             | parse-service        | search-service, analyze-service         | 12     | 7 天     |
| redteam.file.analyzed.v1           | analyze-service      | search-service, profile-service, notification-service | 12 | 7 天 |
| redteam.file.deleted.v1            | upload-service       | search-service, analyze-service         | 6      | 7 天     |
| redteam.task.created.v1            | task-service         | notification-service                    | 6      | 7 天     |
| redteam.task.started.v1            | task-service         | notification-service                    | 6      | 7 天     |
| redteam.task.paused.v1             | task-service         | notification-service                    | 6      | 7 天     |
| redteam.task.completed.v1          | task-service         | report-service, notification-service    | 6      | 7 天     |
| redteam.task.cancelled.v1          | task-service         | notification-service                    | 6      | 7 天     |
| redteam.task.failed.v1             | task-service         | notification-service                    | 6      | 7 天     |
| redteam.target.created.v1          | profile-service      | notification-service                    | 6      | 7 天     |
| redteam.target.updated.v1          | profile-service      | search-service, notification-service    | 6      | 7 天     |
| redteam.target.deleted.v1          | profile-service      | search-service                          | 6      | 7 天     |
| redteam.user.login.v1              | auth-service         | notification-service                    | 6      | 30 天    |
| redteam.user.logout.v1             | auth-service         | -                                       | 6      | 30 天    |
| redteam.user.created.v1            | auth-service         | notification-service                    | 6      | 30 天    |
| redteam.user.updated.v1            | auth-service         | -                                       | 6      | 30 天    |
| redteam.system.alert.v1            | 任意服务             | notification-service, feishu-service    | 3      | 30 天    |
| redteam.system.maintenance.v1      | common / 运维平台    | notification-service                    | 3      | 30 天    |

---

## 二、事件格式（CloudEvents 1.0）

所有事件遵循 [CloudEvents 1.0](https://cloudevents.io/) 规范，使用 JSON 编码。

### 2.1 必填属性

| 属性          | 类型    | 说明                                       |
| ------------- | ------- | ------------------------------------------ |
| id            | string  | 事件唯一 ID（UUID）                        |
| source        | string  | 事件源（URI，如 `redteam.upload-service`） |
| type          | string  | 事件类型（如 `redteam.file.uploaded`）     |
| specversion   | string  | CloudEvents 版本，固定 `1.0`               |
| time          | string  | 事件发生时间（RFC 3339，UTC）              |
| datacontenttype | string | 数据内容类型，固定 `application/json`     |
| subject       | string  | 事件主体（通常是资源 ID）                  |
| data          | object  | 事件负载                                   |

### 2.2 扩展属性

| 属性          | 类型    | 说明                       |
| ------------- | ------- | -------------------------- |
| tenantid      | string  | 租户 ID                    |
| userid        | string  | 操作人用户 ID              |
| traceid       | string  | 链路追踪 ID                |
| dataversion   | string  | 数据版本（如 `v1`）        |

### 2.3 通用事件骨架

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "source": "redteam.upload-service",
  "type": "redteam.file.uploaded",
  "specversion": "1.0",
  "time": "2026-07-27T08:00:00.000Z",
  "datacontenttype": "application/json",
  "subject": "file_001",
  "tenantid": "tenant_001",
  "userid": "u_001",
  "traceid": "trace_abc123",
  "dataversion": "v1",
  "data": {
  }
}
```

---

## 三、文件域事件

### 3.1 file.uploaded

- **Topic**: `redteam.file.uploaded.v1`
- **生产者**: upload-service
- **消费者**: parse-service（触发解析）、notification-service（通知上传者）
- **触发时机**: 文件上传完成（含分片合并完成）

**Payload Schema**:
```json
{
  "file_id": "string",
  "name": "string",
  "size": "integer",
  "mime_type": "string",
  "extension": "string",
  "md5": "string",
  "sha256": "string",
  "status": "string",
  "uploader_id": "string",
  "uploader_name": "string",
  "target_id": "string|null",
  "tags": ["string"],
  "is_sensitive": "boolean",
  "version": "integer",
  "bucket": "string",
  "uploaded_at": "integer"
}
```

**示例**:
```json
{
  "id": "evt-001",
  "source": "redteam.upload-service",
  "type": "redteam.file.uploaded",
  "specversion": "1.0",
  "time": "2026-07-27T08:00:00.000Z",
  "datacontenttype": "application/json",
  "subject": "file_001",
  "tenantid": "tenant_001",
  "userid": "u_001",
  "traceid": "trace_abc123",
  "dataversion": "v1",
  "data": {
    "file_id": "file_001",
    "name": "sample.exe",
    "size": 1048576,
    "mime_type": "application/x-msdownload",
    "extension": "exe",
    "md5": "d41d8cd98f00b204e9800998ecf8427e",
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "status": "UPLOADED",
    "uploader_id": "u_001",
    "uploader_name": "分析师A",
    "target_id": "target_001",
    "tags": ["malware", "apt"],
    "is_sensitive": true,
    "version": 1,
    "bucket": "redteam-files",
    "uploaded_at": 1785086825000
  }
}
```

### 3.2 file.parsed

- **Topic**: `redteam.file.parsed.v1`
- **生产者**: parse-service
- **消费者**: search-service（建立索引）、analyze-service（触发分析）
- **触发时机**: 文件解析完成

**Payload Schema**:
```json
{
  "file_id": "string",
  "task_id": "string",
  "status": "string",
  "parse_type": "string",
  "text_length": "integer",
  "metadata": "object",
  "language": "string",
  "encoding": "string",
  "image_count": "integer",
  "duration_ms": "integer",
  "parsed_at": "integer",
  "error_message": "string|null"
}
```

**示例**:
```json
{
  "id": "evt-002",
  "source": "redteam.parse-service",
  "type": "redteam.file.parsed",
  "specversion": "1.0",
  "time": "2026-07-27T08:01:00.000Z",
  "datacontenttype": "application/json",
  "subject": "file_001",
  "tenantid": "tenant_001",
  "userid": "u_001",
  "traceid": "trace_abc123",
  "dataversion": "v1",
  "data": {
    "file_id": "file_001",
    "task_id": "task_parse_001",
    "status": "SUCCESS",
    "parse_type": "BINARY",
    "text_length": 10240,
    "metadata": {
      "title": "Sample",
      "author": "Unknown"
    },
    "language": "en",
    "encoding": "UTF-8",
    "image_count": 0,
    "duration_ms": 3500,
    "parsed_at": 1785086860000,
    "error_message": null
  }
}
```

### 3.3 file.analyzed

- **Topic**: `redteam.file.analyzed.v1`
- **生产者**: analyze-service
- **消费者**: search-service（更新威胁等级索引）、profile-service（更新目标画像）、notification-service（通知分析完成）
- **触发时机**: 文件分析完成

**Payload Schema**:
```json
{
  "file_id": "string",
  "task_id": "string",
  "status": "string",
  "threat_level": "string",
  "threat_score": "number",
  "summary": "string",
  "entity_count": "integer",
  "ioc_count": "integer",
  "malicious_ioc_count": "integer",
  "analyze_types": ["string"],
  "duration_ms": "integer",
  "analyzed_at": "integer",
  "error_message": "string|null"
}
```

**示例**:
```json
{
  "id": "evt-003",
  "source": "redteam.analyze-service",
  "type": "redteam.file.analyzed",
  "specversion": "1.0",
  "time": "2026-07-27T08:05:00.000Z",
  "datacontenttype": "application/json",
  "subject": "file_001",
  "tenantid": "tenant_001",
  "userid": "u_001",
  "traceid": "trace_abc123",
  "dataversion": "v1",
  "data": {
    "file_id": "file_001",
    "task_id": "task_analyze_001",
    "status": "SUCCESS",
    "threat_level": "HIGH",
    "threat_score": 85.5,
    "summary": "检测到勒索病毒特征，包含 3 个恶意 IOC",
    "entity_count": 12,
    "ioc_count": 5,
    "malicious_ioc_count": 3,
    "analyze_types": ["NER", "IOC", "THREAT_INTEL"],
    "duration_ms": 25000,
    "analyzed_at": 1785087100000,
    "error_message": null
  }
}
```

### 3.4 file.deleted

- **Topic**: `redteam.file.deleted.v1`
- **生产者**: upload-service
- **消费者**: search-service（删除索引）、analyze-service（标记数据失效）
- **触发时机**: 文件被删除（逻辑删除）

**Payload Schema**:
```json
{
  "file_id": "string",
  "name": "string",
  "deleter_id": "string",
  "deleter_name": "string",
  "reason": "string|null",
  "deleted_at": "integer"
}
```

**示例**:
```json
{
  "id": "evt-004",
  "source": "redteam.upload-service",
  "type": "redteam.file.deleted",
  "specversion": "1.0",
  "time": "2026-07-27T09:00:00.000Z",
  "datacontenttype": "application/json",
  "subject": "file_001",
  "tenantid": "tenant_001",
  "userid": "u_002",
  "traceid": "trace_def456",
  "dataversion": "v1",
  "data": {
    "file_id": "file_001",
    "name": "sample.exe",
    "deleter_id": "u_002",
    "deleter_name": "管理员",
    "reason": "误传文件，删除",
    "deleted_at": 1785090000000
  }
}
```

---

## 四、任务域事件

### 4.1 task.created

- **Topic**: `redteam.task.created.v1`
- **生产者**: task-service
- **消费者**: notification-service
- **触发时机**: 任务创建成功

**Payload Schema**:
```json
{
  "task_id": "string",
  "name": "string",
  "task_type": "string",
  "priority": "string",
  "creator_id": "string",
  "creator_name": "string",
  "refs": [{"ref_type": "string", "ref_id": "string"}],
  "scheduled_at": "integer|null",
  "created_at": "integer"
}
```

### 4.2 task.started

- **Topic**: `redteam.task.started.v1`
- **生产者**: task-service
- **消费者**: notification-service
- **触发时机**: 任务从 PENDING/PAUSED 转为 RUNNING

**Payload Schema**:
```json
{
  "task_id": "string",
  "name": "string",
  "task_type": "string",
  "status": "RUNNING",
  "previous_status": "string",
  "operator_id": "string",
  "operator_name": "string",
  "started_at": "integer"
}
```

### 4.3 task.paused

- **Topic**: `redteam.task.paused.v1`
- **生产者**: task-service
- **消费者**: notification-service
- **触发时机**: 任务从 RUNNING 转为 PAUSED

**Payload Schema**:
```json
{
  "task_id": "string",
  "name": "string",
  "status": "PAUSED",
  "progress": "integer",
  "operator_id": "string",
  "operator_name": "string",
  "reason": "string|null",
  "paused_at": "integer"
}
```

### 4.4 task.completed

- **Topic**: `redteam.task.completed.v1`
- **生产者**: task-service
- **消费者**: report-service（生成报告）、notification-service（通知）
- **触发时机**: 任务从 RUNNING 转为 COMPLETED

**Payload Schema**:
```json
{
  "task_id": "string",
  "name": "string",
  "task_type": "string",
  "status": "COMPLETED",
  "output": "string",
  "summary": "string",
  "progress": 100,
  "operator_id": "string",
  "operator_name": "string",
  "duration_ms": "integer",
  "started_at": "integer",
  "completed_at": "integer"
}
```

**示例**:
```json
{
  "id": "evt-task-001",
  "source": "redteam.task-service",
  "type": "redteam.task.completed",
  "specversion": "1.0",
  "time": "2026-07-27T10:00:00.000Z",
  "datacontenttype": "application/json",
  "subject": "task_001",
  "tenantid": "tenant_001",
  "userid": "u_001",
  "traceid": "trace_task001",
  "dataversion": "v1",
  "data": {
    "task_id": "task_001",
    "name": "APT28 样本分析",
    "task_type": "ANALYZE",
    "status": "COMPLETED",
    "output": "{\"threat_level\":\"HIGH\"}",
    "summary": "分析完成，发现 3 个恶意 IOC",
    "progress": 100,
    "operator_id": "u_001",
    "operator_name": "分析师A",
    "duration_ms": 120000,
    "started_at": 1785090000000,
    "completed_at": 1785090120000
  }
}
```

### 4.5 task.cancelled

- **Topic**: `redteam.task.cancelled.v1`
- **生产者**: task-service
- **消费者**: notification-service
- **触发时机**: 任务被取消

**Payload Schema**:
```json
{
  "task_id": "string",
  "name": "string",
  "status": "CANCELLED",
  "previous_status": "string",
  "progress": "integer",
  "operator_id": "string",
  "operator_name": "string",
  "reason": "string",
  "cancelled_at": "integer"
}
```

### 4.6 task.failed

- **Topic**: `redteam.task.failed.v1`
- **生产者**: task-service
- **消费者**: notification-service
- **触发时机**: 任务执行失败

**Payload Schema**:
```json
{
  "task_id": "string",
  "name": "string",
  "task_type": "string",
  "status": "FAILED",
  "progress": "integer",
  "error_message": "string",
  "retry_count": "integer",
  "max_retry": "integer",
  "operator_id": "string",
  "started_at": "integer",
  "failed_at": "integer"
}
```

---

## 五、目标域事件

### 5.1 target.created

- **Topic**: `redteam.target.created.v1`
- **生产者**: profile-service
- **消费者**: notification-service
- **触发时机**: 目标创建成功

**Payload Schema**:
```json
{
  "target_id": "string",
  "name": "string",
  "type": "string",
  "aliases": ["string"],
  "threat_level": "string",
  "tags": ["string"],
  "creator_id": "string",
  "creator_name": "string",
  "created_at": "integer"
}
```

### 5.2 target.updated

- **Topic**: `redteam.target.updated.v1`
- **生产者**: profile-service
- **消费者**: search-service（更新索引）、notification-service
- **触发时机**: 目标信息更新（含威胁等级变更）

**Payload Schema**:
```json
{
  "target_id": "string",
  "name": "string",
  "changes": {
    "threat_level": {"old": "string", "new": "string"},
    "status": {"old": "string", "new": "string"},
    "tags": {"old": ["string"], "new": ["string"]}
  },
  "operator_id": "string",
  "operator_name": "string",
  "updated_at": "integer"
}
```

### 5.3 target.deleted

- **Topic**: `redteam.target.deleted.v1`
- **生产者**: profile-service
- **消费者**: search-service（清理关联索引）
- **触发时机**: 目标被删除

**Payload Schema**:
```json
{
  "target_id": "string",
  "name": "string",
  "deleter_id": "string",
  "deleter_name": "string",
  "reason": "string|null",
  "deleted_at": "integer"
}
```

---

## 六、用户域事件

### 6.1 user.login

- **Topic**: `redteam.user.login.v1`
- **生产者**: auth-service
- **消费者**: notification-service（异地登录告警）
- **触发时机**: 用户登录成功

**Payload Schema**:
```json
{
  "user_id": "string",
  "username": "string",
  "display_name": "string",
  "login_method": "string",
  "client_ip": "string",
  "user_agent": "string",
  "device": "string",
  "location": "string|null",
  "mfa_used": "boolean",
  "login_at": "integer"
}
```

### 6.2 user.logout

- **Topic**: `redteam.user.logout.v1`
- **生产者**: auth-service
- **消费者**: 无（仅审计）
- **触发时机**: 用户登出

**Payload Schema**:
```json
{
  "user_id": "string",
  "username": "string",
  "logout_method": "string",
  "client_ip": "string",
  "logout_at": "integer"
}
```

### 6.3 user.created

- **Topic**: `redteam.user.created.v1`
- **生产者**: auth-service
- **消费者**: notification-service（欢迎通知）
- **触发时机**: 用户创建成功

**Payload Schema**:
```json
{
  "user_id": "string",
  "username": "string",
  "display_name": "string",
  "email": "string",
  "department_id": "string",
  "role_ids": ["string"],
  "creator_id": "string",
  "creator_name": "string",
  "created_at": "integer"
}
```

### 6.4 user.updated

- **Topic**: `redteam.user.updated.v1`
- **生产者**: auth-service
- **消费者**: 无（仅审计）
- **触发时机**: 用户信息更新

**Payload Schema**:
```json
{
  "user_id": "string",
  "username": "string",
  "changes": {
    "status": {"old": "string", "new": "string"},
    "department_id": {"old": "string", "new": "string"}
  },
  "operator_id": "string",
  "operator_name": "string",
  "updated_at": "integer"
}
```

---

## 七、系统域事件

### 7.1 system.alert

- **Topic**: `redteam.system.alert.v1`
- **生产者**: 任意服务（异常、阈值告警等）
- **消费者**: notification-service、feishu-service
- **触发时机**: 系统异常、安全告警、阈值突破

**Payload Schema**:
```json
{
  "alert_id": "string",
  "alert_type": "string",
  "severity": "string",
  "source_service": "string",
  "title": "string",
  "description": "string",
  "metrics": "object",
  "suggested_action": "string|null",
  "triggered_at": "integer"
}
```

**示例**:
```json
{
  "id": "evt-alert-001",
  "source": "redteam.analyze-service",
  "type": "redteam.system.alert",
  "specversion": "1.0",
  "time": "2026-07-27T11:00:00.000Z",
  "datacontenttype": "application/json",
  "subject": "alert_001",
  "tenantid": "tenant_001",
  "traceid": "trace_alert001",
  "dataversion": "v1",
  "data": {
    "alert_id": "alert_001",
    "alert_type": "RATE_LIMIT_EXCEEDED",
    "severity": "WARN",
    "source_service": "analyze-service",
    "title": "分析服务 QPS 超限",
    "description": "当前 QPS 250，超过阈值 200",
    "metrics": {
      "current_qps": 250,
      "threshold": 200,
      "tenant_id": "tenant_001"
    },
    "suggested_action": "检查是否有异常批量分析任务",
    "triggered_at": 1785096000000
  }
}
```

### 7.2 system.maintenance

- **Topic**: `redteam.system.maintenance.v1`
- **生产者**: common / 运维平台
- **消费者**: notification-service
- **触发时机**: 系统进入维护模式 / 维护结束

**Payload Schema**:
```json
{
  "maintenance_id": "string",
  "action": "string",
  "scope": ["string"],
  "start_time": "integer",
  "end_time": "integer|null",
  "reason": "string",
  "impact": "string",
  "announced_at": "integer"
}
```

**示例**:
```json
{
  "id": "evt-maint-001",
  "source": "redteam.ops",
  "type": "redteam.system.maintenance",
  "specversion": "1.0",
  "time": "2026-07-27T12:00:00.000Z",
  "datacontenttype": "application/json",
  "subject": "maint_001",
  "tenantid": "tenant_001",
  "dataversion": "v1",
  "data": {
    "maintenance_id": "maint_001",
    "action": "START",
    "scope": ["search-service", "analyze-service"],
    "start_time": 1785100000000,
    "end_time": 1785103000000,
    "reason": "Elasticsearch 版本升级",
    "impact": "检索与分析服务暂停 30 分钟",
    "announced_at": 1785099600000
  }
}
```

---

## 八、消费约定

### 8.1 幂等性

消费者必须实现幂等处理，依据 `id` 字段去重。建议使用 Redis 记录已处理事件 ID，TTL 7 天。

### 8.2 顺序性

- 同一 `subject`（资源 ID）的事件按时间顺序到达同一分区，消费者应保证分区内顺序消费。
- 跨分区事件不保证顺序，消费者应处理乱序场景（如基于版本号或时间戳丢弃过期事件）。

### 8.3 失败重试

- 消费失败的事件进入重试队列（DLQ：`redteam.dlq.v1`）。
- 重试策略：指数退避，最大重试 3 次，超时后告警。
- DLQ 事件需人工介入或定时任务补偿。

### 8.4 Schema 演进

- 新增字段为兼容变更，消费者应忽略未知字段。
- 删除字段或修改字段类型为破坏性变更，需升 Topic 版本（`v2`）。
- 升版本后新旧 Topic 并存至少 30 天，消费者逐步迁移。

### 8.5 消费组命名

格式：`redteam.{consumer-service}-{purpose}`

示例：
- `redteam.parse-service-file-consumer`
- `redteam.notification-service-all-consumer`
- `redteam.search-service-index-consumer`
