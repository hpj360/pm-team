# Skill: Deployer

## 描述

部署健康检查与运行时验证工具，帮助运维工程师在部署后执行健康检查、监控验证与回滚决策。作为 `cicd-pipeline` 的下游 Skill，专注于部署后的运行时验证，而非部署执行本身。

> **职责边界说明**: 原 `deployer` 的"部署执行"和"部署策略"功能已委托给 `cicd-pipeline` Skill（其 `环境部署` 和 `回滚机制` 能力覆盖本 Skill 的部署执行与回滚操作）。本 Skill 现专注于**健康检查**这一独有能力，作为部署流水线的验证环节。

## 与现有 .gitlab-ci.yml 的关系（必读）

本项目的部署流水线已由 `.gitlab-ci.yml` 的 `deploy` 和 `verify` 阶段完整实现。**本 Skill 是部署验证的编排文档层与策略定义层，而非执行层。**

| Skill 角色 | 实际执行方（.gitlab-ci.yml） | 职责划分 |
|-----------|-------------------------------|---------|
| 健康检查策略（URL/重试/超时） | `verify:health-check` job（curl 轮询 `/healthz`，5分钟超时） | Skill 定义检查策略，.gitlab-ci.yml 已实现 |
| 回滚决策（proceed/rollback/observe） | `verify:rollback` job（kubectl rollout undo） | Skill 提供决策建议，.gitlab-ci.yml 执行回滚 |
| K8s 滚动部署 | `deploy:staging` / `deploy:production`（kubectl set image + rollout status） | 部署执行已委托给 cicd-pipeline → .gitlab-ci.yml |
| 金丝雀发布 | `deploy:production` 的金丝雀策略（10%流量） | 策略由 Skill 定义，执行由 .gitlab-ci.yml 完成 |

**调用约定**：
- 当需要了解"部署后如何验证"时，查询本 Skill 的健康检查策略与决策模型
- 当需要实际执行健康检查时，由 `.gitlab-ci.yml` 的 `verify:health-check` job 完成
- 当健康检查失败需要回滚时，由 `.gitlab-ci.yml` 的 `verify:rollback` job 自动触发 kubectl undo
- 本 Skill 的 `action: "healthCheck"` API 仅作为编排接口保留，实际执行由 .gitlab-ci.yml 完成

## 功能

### 1. 健康检查（核心能力）

部署后自动执行多维度健康检查，验证服务可用性。

**检查类型**:
- HTTP健康检查
- TCP端口检查
- 命令执行检查
- 自定义脚本检查

### 2. 部署验证

验证部署结果是否符合预期，包括版本、配置、资源状态。

**验证内容**:
- 版本号校验
- 配置项加载验证
- 资源就绪状态（Kubernetes pods ready）
- 服务注册发现状态

### 3. 回滚决策支持

基于健康检查结果，为 cicd-pipeline 提供回滚决策建议。

**决策输出**:
- `proceed` - 健康检查通过，可继续推进
- `rollback` - 健康检查失败，建议触发回滚
- `observe` - 边缘指标异常，建议观察但暂不回滚

> **注意**: 实际回滚执行由 `cicd-pipeline` 的"回滚机制"功能完成，本 Skill 仅提供决策建议。

### 4. 委托至 cicd-pipeline 的能力（兼容引用）

以下能力保留 API 兼容性，但实际委托给 `cicd-pipeline`：

- **部署执行**（`action: "deploy"`）→ 转发至 cicd-pipeline 的"环境部署"
- **回滚操作**（`action: "rollback"`）→ 转发至 cicd-pipeline 的"回滚机制"
- **部署策略**（蓝绿/金丝雀/滚动/A-B）→ 转发至 cicd-pipeline 的"环境部署"配置

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
