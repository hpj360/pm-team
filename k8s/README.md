# 红方文件汇聚平台 - Kubernetes 部署说明

本文档描述红方文件汇聚平台在 Kubernetes 集群上的部署架构、配置说明与运维流程。

## 一、架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                     集群入口 (Istio Gateway)                     │
│                      redteam.example.com                         │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                  ┌──────────────┴──────────────┐
                  │                             │
            ┌─────▼─────┐               ┌──────▼──────┐
            │ frontend  │               │   gateway   │
            │  (Nginx)  │               │ (API 网关)  │
            └─────┬─────┘               └──────┬──────┘
                  │                            │
                  └───────────┬────────────────┘
                              │
   ┌──────────┬──────────┬────┴────┬──────────┬──────────┐
   │          │          │         │          │          │
auth-svc  upload-svc  parse-svc search-svc analyze-svc  ...
(11 个微服务, Istio Service Mesh, mTLS, 灰度发布)
```

### 命名空间划分

| 命名空间 | 用途 | Istio 注入 |
|---|---|---|
| `redteam-platform` | 业务微服务 + 前端 | 启用 |
| `redteam-middleware` | PostgreSQL/ES/Redis/Milvus/Neo4j/Kafka/MinIO/Nacos | 禁用 |
| `redteam-monitoring` | Prometheus / Grafana / Alertmanager | 禁用 |
| `istio-system` | Istio 控制面 + Ingress Gateway | - |

## 二、文件清单

```
k8s/
├── namespace.yaml                      # 命名空间定义
├── configmap.yaml                      # 公共配置 (非敏感)
├── secret.yaml                         # 密钥 (占位符, 生产用 SealedSecret)
├── auth-service.yaml                   # auth-service: Deployment+Service+HPA+PDB+SA
├── frontend.yaml                       # frontend: Deployment+Service+Ingress+HPA+PDB
├── istio/
│   ├── gateway.yaml                    # Istio Gateway (对外入口)
│   ├── destination-rule.yaml           # DestinationRule (子集/熔断/连接池)
│   ├── virtual-service.yaml            # VirtualService (灰度 v1 90% / v2 10%)
│   └── peer-authentication.yaml        # mTLS + 授权策略
└── monitoring/
    └── servicemonitor.yaml             # Prometheus ServiceMonitor + 告警规则
```

> 其他后端服务 (upload/parse/search/analyze/profile/task/notification/report/feishu) 的部署清单结构与 `auth-service.yaml` 完全一致，复制后修改 `name`、`port`、`resources`、`replicas` 即可。

## 三、前置准备

### 3.1 集群要求

- Kubernetes >= 1.26
- 已安装 Istio >= 1.18 (含 ingressgateway)
- 已安装 Prometheus Operator (kube-prometheus-stack)
- 已安装 metrics-server (HPA 依赖)
- 已配置 StorageClass (中间件 PVC)
- 已配置 cert-manager (TLS 证书, 可选)

### 3.2 本地工具

```bash
kubectl version --client
istioctl version
helm version
```

### 3.3 镜像仓库凭证

```bash
kubectl create secret docker-registry registry-credentials \
  --docker-server=registry.example.com \
  --docker-username=<user> \
  --docker-password=<password> \
  --docker-email=<email> \
  -n redteam-platform
```

## 四、部署流程

### 4.1 首次部署

```bash
# 1. 创建命名空间 (会自动注入 Istio sidecar)
kubectl apply -f k8s/namespace.yaml

# 2. 应用公共配置与密钥
#    注意: 生产环境请先用 SealedSecret/ExternalSecret 替换 secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml

# 3. 部署中间件 (StatefulSet/Deployment, 此处假设已在 redteam-middleware 命名空间)
#    中间件清单可使用 Bitnami Helm Chart 部署, 此处略

# 4. 部署业务服务
kubectl apply -f k8s/auth-service.yaml
kubectl apply -f k8s/frontend.yaml
#    其他服务: kubectl apply -f k8s/<service>.yaml

# 5. 部署 Istio 配置
kubectl apply -f k8s/istio/gateway.yaml
kubectl apply -f k8s/istio/destination-rule.yaml
kubectl apply -f k8s/istio/virtual-service.yaml
kubectl apply -f k8s/istio/peer-authentication.yaml

# 6. 部署监控
kubectl apply -f k8s/monitoring/servicemonitor.yaml

# 7. 检查状态
kubectl get pods -n redteam-platform
kubectl get svc -n redteam-platform
kubectl get hpa -n redteam-platform
```

### 4.2 滚动更新 (使用部署脚本)

```powershell
# Windows PowerShell
.\scripts\deploy.ps1 -Service auth-service -Tag v1.0.1
.\scripts\deploy.ps1 -Service all -Tag v1.0.1
```

```bash
# Linux/macOS
./scripts/deploy.sh --service auth-service --tag v1.0.1
./scripts/deploy.sh --service all --tag v1.0.1
```

### 4.3 健康检查

```powershell
.\scripts\health-check.ps1
.\scripts\health-check.ps1 -Service auth-service
.\scripts\health-check.ps1 -Watch           # 持续监控
.\scripts\health-check.ps1 -ExitOnUnhealthy # CI/CD 模式
```

## 五、灰度发布 (Istio)

当前配置: 稳定版 `v1` 90% + 金丝雀 `v2` 10%。

### 5.1 发布金丝雀版本

```bash
# 1. 部署 v2 (修改 Deployment 的 version 标签为 v2)
kubectl apply -f k8s/auth-service-v2.yaml

# 2. DestinationRule 已定义 v1/v2 子集, VirtualService 自动按权重切分
#    默认: v1 90% + v2 10%

# 3. 验证 v2 健康后, 逐步提升 v2 权重
#    编辑 k8s/istio/virtual-service.yaml, 修改 weight:
#      v1: 50  v2: 50  -> 观察
#      v1: 0   v2: 100 -> 全量切流
kubectl apply -f k8s/istio/virtual-service.yaml
```

### 5.2 灰度按请求头路由

VirtualService 中已配置: 携带请求头 `x-canary: true` 的请求 100% 路由到 v2，便于内部测试。

```bash
curl -H "x-canary: true" https://redteam.example.com/api/auth/me
```

### 5.3 回滚

```powershell
.\scripts\rollback.ps1 -Service auth-service
.\scripts\rollback.ps1 -Service auth-service -Revision 3
.\scripts\rollback.ps1 -Service all
```

灰度回滚: 将 VirtualService 中 v2 权重改回 0 即可立即切回 v1。

```bash
# 紧急回滚到 v1 全量
kubectl patch virtualservice auth-service-vs -n redteam-platform --type=json \
  -p='[{"op":"replace","path":"/spec/http/1/route/0/weight","value":100},{"op":"replace","path":"/spec/http/1/route/1/weight","value":0}]'
```

## 六、资源规划建议

| 服务 | 副本 | CPU (req/limit) | 内存 (req/limit) | HPA 范围 |
|---|---|---|---|---|
| auth-service | 2 | 250m / 1000m | 512Mi / 1Gi | 2-8 |
| upload-service | 3 | 500m / 2000m | 1Gi / 2Gi | 3-10 |
| parse-service | 3 | 1000m / 4000m | 2Gi / 4Gi | 3-10 |
| search-service | 2 | 500m / 2000m | 1Gi / 2Gi | 2-8 |
| analyze-service | 3 | 1000m / 4000m | 2Gi / 4Gi | 3-10 |
| frontend | 3 | 100m / 300m | 128Mi / 256Mi | 3-10 |

> 实际配额需根据集群总资源与压测结果调整。建议开启 `LimitRange` 与 `ResourceQuota`。

## 七、探针说明

每个后端 Pod 配置三类探针 (Spring Boot Actuator):

| 探针 | 路径 | 用途 |
|---|---|---|
| `startupProbe` | `/actuator/health/liveness` | 启动判定 (失败 12 次重启) |
| `livenessProbe` | `/actuator/health/liveness` | 存活判定 (失败 3 次重启) |
| `readinessProbe` | `/actuator/health/readiness` | 就绪判定 (失败 3 次摘流) |

应用侧需添加依赖:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

`application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

## 八、监控告警

ServiceMonitor 自动抓取 `/actuator/prometheus`。已配置告警:

| 告警 | 触发条件 | 严重级别 |
|---|---|---|
| RedteamServiceDown | Pod 离线 > 2m | critical |
| RedteamHighErrorRate | 5xx 错误率 > 5% (5m) | warning |
| RedteamHighLatency | P99 延迟 > 2s (5m) | warning |
| RedteamJvmMemoryHigh | 堆内存 > 85% (10m) | warning |
| RedteamPodRestart | 1h 内重启 > 3 次 | warning |

Grafana Dashboard 推荐导入:
- Spring Boot Statistics (ID: 11378)
- JVM (Micrometer) (ID: 4701)
- Istio Control Plane (ID: 7645)
- Istio Service Mesh (ID: 7639)

## 九、安全注意事项

1. **密钥管理**: `k8s/secret.yaml` 仅为占位符，生产环境必须使用 Sealed Secrets / External Secrets / HashiCorp Vault。
2. **mTLS**: `PeerAuthentication` 已配置 `STRICT` 模式，服务间通信强制双向 TLS。
3. **网络隔离**: `AuthorizationPolicy` 默认拒绝所有流量，仅允许同命名空间与 istio-system 网关访问。
4. **镜像安全**: CI 流水线中已集成 Trivy 镜像扫描，HIGH/CRITICAL 漏洞阻断发布。
5. **RBAC**: 生产环境为每个服务配置最小权限 ServiceAccount，避免使用 default SA。
6. **Pod Security**: 建议命名空间添加 `pod-security.kubernetes.io/enforce: restricted` 标签。

## 十、故障排查

```bash
# Pod 异常
kubectl describe pod <pod-name> -n redteam-platform
kubectl logs <pod-name> -n redteam-platform --previous
kubectl logs <pod-name> -n redteam-platform -c istio-proxy

# Deployment 异常
kubectl rollout status deployment/<svc> -n redteam-platform
kubectl rollout history deployment/<svc> -n redteam-platform

# Istio 异常
istioctl analyze -n redteam-platform
istioctl proxy-config routes <pod-name>.redteam-platform
istioctl proxy-config clusters <pod-name>.redteam-platform

# 事件
kubectl get events -n redteam-platform --sort-by='.lastTimestamp'
```

## 十一、清理

```bash
# 删除业务命名空间 (会级联删除所有资源)
kubectl delete namespace redteam-platform

# 仅删除 Istio 配置
kubectl delete -f k8s/istio/

# 删除监控配置
kubectl delete -f k8s/monitoring/
```
