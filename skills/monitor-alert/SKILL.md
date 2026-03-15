# Skill: Monitor Alert

## 描述

监控告警工具，帮助运维工程师建立监控体系和告警机制。

## 功能

### 1. 监控配置

配置各类监控指标。

**监控类型**:
- 基础设施监控 (CPU、内存、磁盘、网络)
- 应用性能监控 (响应时间、吞吐量、错误率)
- 业务指标监控 (订单量、用户数、转化率)
- 日志监控 (错误日志、访问日志)

### 2. 告警规则

定义告警规则和阈值。

**规则类型**:
- 阈值告警
- 趋势告警
- 异常检测
- 复合条件

### 3. 告警通知

配置告警通知渠道。

**通知渠道**:
- 邮件
- 短信
- 电话
- 钉钉/飞书/企业微信
- Webhook

### 4. 监控大盘

生成监控可视化面板。

**面板类型**:
- 实时监控
- 历史趋势
- 拓扑视图
- 告警列表

## 使用示例

### 配置监控指标

```json
{
  "action": "addMetric",
  "metrics": [
    {
      "name": "cpu_usage",
      "type": "gauge",
      "labels": ["host", "env"],
      "interval": "10s"
    },
    {
      "name": "http_request_duration",
      "type": "histogram",
      "labels": ["method", "path", "status"],
      "buckets": [0.1, 0.5, 1, 2, 5]
    }
  ]
}
```

### 配置告警规则

```json
{
  "action": "addAlert",
  "alerts": [
    {
      "name": "high_cpu_usage",
      "expr": "cpu_usage > 80",
      "duration": "5m",
      "severity": "warning",
      "annotations": {
        "summary": "CPU使用率过高",
        "description": "主机 {{ $labels.host }} CPU使用率 {{ $value }}%"
      }
    },
    {
      "name": "high_error_rate",
      "expr": "rate(http_requests_total{status=~\"5..\"}[5m]) > 0.05",
      "severity": "critical",
      "annotations": {
        "summary": "错误率过高",
        "description": "5xx错误率超过5%"
      }
    }
  ]
}
```

### 配置通知渠道

```json
{
  "action": "addNotification",
  "channels": [
    {
      "name": "ops-team",
      "type": "feishu",
      "config": {
        "webhook": "https://open.feishu.cn/xxx"
      },
      "severity": ["warning", "critical"]
    },
    {
      "name": "oncall",
      "type": "phone",
      "config": {
        "phones": ["13800138000"]
      },
      "severity": ["critical"]
    }
  ]
}
```

## 告警规则模板

### 基础设施告警

```yaml
# CPU告警
- alert: HighCPUUsage
  expr: cpu_usage > 80
  for: 5m
  severity: warning
  annotations:
    summary: "CPU使用率过高"

# 内存告警
- alert: HighMemoryUsage
  expr: memory_usage > 85
  for: 5m
  severity: warning

# 磁盘告警
- alert: DiskSpaceLow
  expr: disk_free_percent < 15
  for: 5m
  severity: critical
```

### 应用告警

```yaml
# 响应时间告警
- alert: HighLatency
  expr: http_request_duration_p99 > 1000
  for: 2m
  severity: warning

# 错误率告警
- alert: HighErrorRate
  expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
  for: 1m
  severity: critical

# 服务不可用告警
- alert: ServiceDown
  expr: up == 0
  for: 1m
  severity: critical
```

## 输出格式

```json
{
  "monitoring": {
    "status": "active",
    "metrics": 50,
    "alerts": 20,
    "dashboards": 5
  },
  "alertStatus": {
    "firing": 2,
    "pending": 1,
    "resolved": 5
  },
  "recentAlerts": [
    {
      "name": "HighCPUUsage",
      "severity": "warning",
      "startsAt": "2024-03-15T10:30:00Z",
      "status": "firing",
      "value": "85%"
    }
  ]
}
```

## 配置

```json
{
  "monitoring": {
    "backend": "prometheus",
    "interval": "10s",
    "retention": "30d"
  },
  "alerting": {
    "backend": "alertmanager",
    "evaluationInterval": "30s",
    "resolveTimeout": "5m"
  },
  "notification": {
    "groupWait": "30s",
    "groupInterval": "5m",
    "repeatInterval": "1h"
  },
  "dashboards": {
    "backend": "grafana",
    "refresh": "30s"
  }
}
```
