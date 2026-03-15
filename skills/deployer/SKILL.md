# Skill: Deployer

## 描述

自动化部署工具，帮助运维工程师执行部署、监控和回滚操作。

## 功能

### 1. 部署执行

执行应用的自动化部署。

**支持平台**:
- Docker
- Kubernetes
- AWS
- Azure
- GCP
- 阿里云

### 2. 部署策略

支持多种部署策略。

**策略类型**:
- 蓝绿部署
- 金丝雀发布
- 滚动更新
- A/B测试

### 3. 健康检查

部署后自动执行健康检查。

**检查类型**:
- HTTP健康检查
- TCP端口检查
- 命令执行检查
- 自定义脚本检查

### 4. 回滚操作

支持快速回滚到上一版本。

**回滚方式**:
- 自动回滚（健康检查失败时）
- 手动回滚
- 定时回滚

## 使用示例

### Docker部署

```json
{
  "action": "deploy",
  "platform": "docker",
  "config": {
    "image": "myapp:latest",
    "container": "myapp-prod",
    "ports": ["3000:3000"],
    "env": {
      "NODE_ENV": "production",
      "DATABASE_URL": "${DB_URL}"
    },
    "volumes": ["./data:/app/data"],
    "network": "app-network"
  }
}
```

### Kubernetes部署

```json
{
  "action": "deploy",
  "platform": "kubernetes",
  "config": {
    "namespace": "production",
    "deployment": {
      "name": "myapp",
      "replicas": 3,
      "image": "myapp:v1.0.0",
      "ports": [3000],
      "resources": {
        "requests": {
          "cpu": "100m",
          "memory": "128Mi"
        },
        "limits": {
          "cpu": "500m",
          "memory": "512Mi"
        }
      }
    },
    "service": {
      "type": "LoadBalancer",
      "port": 80,
      "targetPort": 3000
    }
  }
}
```

### 金丝雀发布

```json
{
  "action": "deploy",
  "strategy": "canary",
  "config": {
    "baseline": "myapp:v1.0.0",
    "canary": "myapp:v2.0.0",
    "canaryWeight": 10,
    "stages": [
      { "weight": 10, "duration": "5m" },
      { "weight": 30, "duration": "10m" },
      { "weight": 50, "duration": "10m" },
      { "weight": 100, "duration": "5m" }
    ],
    "analysis": {
      "metrics": ["error-rate", "latency-p95"],
      "threshold": 0.05
    }
  }
}
```

### 回滚操作

```json
{
  "action": "rollback",
  "target": "myapp",
  "version": "previous"
}
```

## 输出格式

```json
{
  "deploymentId": "deploy-20240315-001",
  "status": "success",
  "version": "v1.0.0",
  "timestamp": "2024-03-15T10:30:00Z",
  "duration": "45s",
  "steps": [
    {
      "name": "Pull Image",
      "status": "success",
      "duration": "10s"
    },
    {
      "name": "Create Container",
      "status": "success",
      "duration": "5s"
    },
    {
      "name": "Start Container",
      "status": "success",
      "duration": "3s"
    },
    {
      "name": "Health Check",
      "status": "success",
      "duration": "27s"
    }
  ],
  "healthCheck": {
    "status": "healthy",
    "responseTime": "45ms",
    "checks": [
      { "name": "HTTP /health", "status": "passed" },
      { "name": "Database Connection", "status": "passed" }
    ]
  }
}
```

## 配置

```json
{
  "environments": {
    "dev": {
      "platform": "docker",
      "autoRollback": true
    },
    "prod": {
      "platform": "kubernetes",
      "strategy": "canary",
      "autoRollback": true,
      "approval": true
    }
  },
  "notifications": {
    "slack": "#deployments",
    "email": ["devops@example.com"]
  },
  "healthCheck": {
    "interval": "10s",
    "timeout": "5s",
    "retries": 3
  }
}
```
