# 红方文件分析管理平台 - 可观测性运维手册 (v5.4)

> 版本：v5.4 | 适用模块：`backend/common` 的 `com.redteam.common.telemetry` 包
> 维护：红方团队 | 最后更新：2026-08

---

## 1. 架构概览

V5.4 可观测性体系基于 OpenTelemetry + Micrometer + Prometheus + Loki + Jaeger 构建，覆盖
**Metrics / Trace / Logs** 三大支柱，统一在 `common` 模块装配，所有业务服务零改造接入。

```
 业务服务 (auth/upload/parse/.../ai-service)
   │  TelemetryAutoConfiguration 自动装配
   │  ├─ OpenTelemetryConfig    → OTLP/HTTP 上报 Span
   │  ├─ TraceContextPropagator → HTTP/Kafka 跨服务 traceparent 传播
   │  ├─ PrometheusMetricsConfig→ /actuator/prometheus 暴露指标
   │  ├─ UnifiedLogConfig       → JSON 日志 + MDC(traceId/service)
   │  ├─ BusinessMetricsRecorder→ 业务指标记录
   │  └─ AlertNotifier          → 飞书 Webhook 告警
   ▼
 OTel Collector ──► Jaeger (Trace)
 Prometheus   ◄─── 业务服务 (/actuator/prometheus)  → Grafana
 Loki         ◄─── Promtail/filebeat (JSON stdout)  → Grafana
 Alertmanager ◄─── Prometheus alert rules           → 飞书 Webhook
```

### 1.1 核心组件清单

| 组件 | 类 | 职责 |
|------|----|------|
| 自动装配入口 | `TelemetryAutoConfiguration` | 条件加载所有可观测性组件 |
| OTel 配置 | `OpenTelemetryConfig` | SDK + OTLP Span Exporter + Tracer Bean |
| Trace 传播 | `TraceContextPropagator` | HTTP 入站 Filter / 出站 RestTemplate / Kafka 拦截器 |
| Kafka Trace | `TraceKafkaProducerInterceptor` / `TraceKafkaConsumerInterceptor` | 消息收发时注入/提取 traceId |
| Metrics 配置 | `PrometheusMetricsConfig` | 公共标签、JVM 指标、HTTP 直方图 |
| 业务指标 | `BusinessMetricsRecorder` | file_parse_total / ai_invoke_total / workflow / kafka_lag |
| 统一日志 | `UnifiedLogConfig` + `LogFieldConstants` | JSON 日志字段标准化 |
| 告警 | `AlertRule` / `AlertSeverity` / `AlertNotifier` | 分级告警 + 飞书卡片 |
| 环境注入 | `TelemetryEnvironmentPostProcessor` | 启动前注入 actuator 端点默认配置 |

---

## 2. 接入指南

### 2.1 服务启用（默认开启）

`common` 模块被所有服务依赖，`TelemetryAutoConfiguration` 通过 `@ConditionalOnProperty(prefix="redteam.telemetry", name="enabled", havingValue="true", matchIfMissing=true)` 默认装配。
**服务无需任何配置即可获得 Metrics + Trace + 日志能力。**

在服务 `application.yml` 中加载默认配置（推荐）：

```yaml
spring:
  config:
    import: optional:classpath:application-telemetry.yml
  application:
    name: parse-service   # 服务名，作为 service 标签

redteam:
  telemetry:
    service-name: ${spring.application.name}
    otlp:
      endpoint: http://otel-collector:4318   # 或 http://jaeger:4318
      enabled: true
    alert:
      enabled: true
      feishu:
        webhook: https://open.feishu.cn/open-apis/bot/v2/hook/<your-webhook-token>
```

### 2.2 关闭可观测性

整体禁用：

```yaml
redteam:
  telemetry:
    enabled: false
```

仅禁用 OTLP 上报（Tracer 仍可用但不导出 Span）：

```yaml
redteam:
  telemetry:
    otlp:
      enabled: false
```

### 2.3 业务侧记录指标

```java
@Autowired
private BusinessMetricsRecorder recorder;

public void parseFile(String fileType) {
    try {
        // ... 解析逻辑
        recorder.recordFileParse("success", fileType);
    } catch (Exception e) {
        recorder.recordFileParse("fail", fileType);
        throw e;
    }
}

public AiResult invokeNer(String text) {
    try {
        AiResult r = aiClient.ner(text);
        recorder.recordAiInvoke("ner", BusinessMetricsRecorder.AI_RESULT_SUCCESS);
        return r;
    } catch (AiDegradedException e) {
        recorder.recordAiInvoke("ner", BusinessMetricsRecorder.AI_RESULT_DEGRADED);
        return fallback();
    }
}
```

### 2.4 业务侧主动触发告警

```java
@Autowired
private AlertNotifier alertNotifier;

public void onKafkaLagExceeded() {
    AlertRule rule = AlertRule.builtinRules().get("kafka_lag_high");
    alertNotifier.sendAlert(rule,
        Map.of("topic", "file-events", "lag", "15000"),
        Map.of("runbook", "https://wiki.redteam/ops/runbook/kafka-lag"));
}
```

---

## 3. 部署可观测性后端

### 3.1 一键启动

```bash
cd backend/docs/ops
docker compose -f docker-compose-observability.yml up -d
```

服务端口：

| 服务 | 端口 | 用途 |
|------|------|------|
| Grafana | http://localhost:3000 | 可视化面板（admin/admin） |
| Prometheus | http://localhost:9090 | 指标查询与告警评估 |
| Jaeger UI | http://localhost:16686 | Trace 检索与拓扑 |
| Alertmanager | http://localhost:9093 | 告警路由 |
| Loki | http://localhost:3100 | 日志后端（Grafana 数据源） |
| OTel Collector | http://localhost:14318 | OTLP/HTTP 接收 |
| Node Exporter | http://localhost:9100 | 主机指标 |

### 3.2 导入 Grafana Dashboard

1. 登录 Grafana → Dashboards → Import
2. 上传 `docs/ops/grafana-dashboards/` 下的 4 个 JSON：
   - `system-overview.json` — 系统总览
   - `trace-detail.json` — Trace 与慢请求分析
   - `business-metrics.json` — 业务指标
   - `alert-infra.json` — 告警与基础设施
3. 选择 Prometheus 数据源后保存

### 3.3 飞书 Webhook 配置

1. 飞书群 → 设置 → 群机器人 → 添加自定义机器人
2. 复制 Webhook 地址填入 `redteam.telemetry.alert.feishu.webhook`
3. 规则级 Webhook 可在 `AlertRule.feishuWebhook` 中覆盖

---

## 4. 日志规范

### 4.1 标准字段

JSON 日志字段（`LogFieldConstants`）：

| 字段 | 说明 |
|------|------|
| `@timestamp` | ISO-8601 时间戳 |
| `level` | INFO/WARN/ERROR |
| `traceId` | 分布式 Trace ID（关联 Jaeger） |
| `spanId` | Span ID |
| `service` | 服务名 |
| `msg` | 日志正文 |
| `logger` | 日志器名 |
| `thread` | 线程名 |

### 4.2 日志查询示例（Loki/LogQL）

按 traceId 检索全链路日志：

```logql
{service="parse-service"} |= "traceId" | json | traceId="a1b2c3..."
```

按服务 + 级别过滤：

```logql
{service=~"auth-service|parse-service"} | json | level="ERROR"
```

### 4.3 切换日志格式

- 开发环境文本日志：`spring.profiles.active=dev`（logback-spring.xml 中 dev profile 用 CONSOLE_PATTERN）
- 生产 JSON 日志：默认即 JSON（CONSOLE_JSON）
- 启用文件输出：`redteam.telemetry.log.file.enabled=true` 并激活 `file-logging` profile

---

## 5. 告警分级与响应

### 5.1 分级策略

| 级别 | 含义 | 触达 | 响应 SLA |
|------|------|------|----------|
| P0 | 致命：核心服务不可用 | 电话（预留）+ 飞书红色加急 + @all | 5 分钟响应 |
| P1 | 严重：核心功能受损 | 飞书橙/红色加急 + @all | 15 分钟响应 |
| P2 | 一般：需关注 | 飞书蓝色普通卡片 | 工作日内处理 |

### 5.2 内置告警规则

| 规则名 | 表达式 | 持续 | 级别 |
|--------|--------|------|------|
| `service_down` | `up == 0` | 1m | P0 |
| `error_rate_high` | 5xx 速率 > 5% | 5m | P1 |
| `ai_degraded` | AI 降级调用 > 0 | 1m | P1 |
| `ai_failure_rate_high` | AI 失败率 > 10% | 5m | P1 |
| `kafka_lag_high` | Kafka 积压 > 10000 | 5m | P1 |
| `jvm_heap_usage_high` | 堆使用率 > 90% | 5m | P1 |
| `http_p95_latency_high` | P95 > 2s | 10m | P2 |
| `file_parse_failure_high` | 解析失败率 > 20% | 10m | P2 |
| `rate_limit_triggered` | 限流 > 100 次/2m | 2m | P2 |
| `jvm_gc_pause_high` | GC 均耗时 > 0.5s | 10m | P2 |

### 5.3 Runbook 索引

- 服务宕机：https://wiki.redteam/ops/runbook/service-down
- HTTP 5xx：https://wiki.redteam/ops/runbook/http-5xx
- AI 降级：https://wiki.redteam/ops/runbook/ai-degraded
- Kafka 积压：https://wiki.redteam/ops/runbook/kafka-lag
- JVM OOM：https://wiki.redteam/ops/runbook/jvm-heap

---

## 6. 故障排查

### 6.1 Trace 串联缺失

**现象**：Jaeger 中下游服务 Span 与上游无父子关系。

**排查**：
1. 确认下游服务收到 `traceparent` 请求头：在下游日志中查 `traceId` 字段
2. 检查 `TraceContextPropagator.traceContextFilter` 是否注册（启动日志含 Bean 装配）
3. Kafka 链路：确认生产者配置了 `TraceKafkaProducerInterceptor`，消费者配置了 `TraceKafkaConsumerInterceptor`
4. RestTemplate 出站：确认未自定义 `RestTemplate` 实例绕过 `traceRestTemplateCustomizer`

### 6.2 指标缺失

**现象**：`/actuator/prometheus` 中无业务指标。

**排查**：
1. 访问 `http://<service>:<port>/actuator/prometheus` 确认端点暴露
2. 检查 `management.endpoints.web.exposure.include` 是否含 `prometheus`
3. 确认 `BusinessMetricsRecorder` 已被调用（业务方法是否执行）
4. 指标采集失败默认吞掉异常，可临时调高日志级别：
   `logging.level.com.redteam.common.telemetry=DEBUG`

### 6.3 告警未触达飞书

**现象**：Prometheus 触发告警但飞书群无消息。

**排查**：
1. 检查 `redteam.telemetry.alert.enabled` 是否为 `true`
2. 检查 `redteam.telemetry.alert.feishu.webhook` 是否配置且 token 有效
3. 查看 `AlertNotifier` 日志：`发送飞书告警失败` 提示网络/token 问题
4. 飞书机器人安全设置需允许自定义关键词或 IP 白名单（卡片标题含 `[P1]` 等关键词）

### 6.4 日志无 traceId

**现象**：JSON 日志中 `traceId` 字段缺失。

**排查**：
1. 确认请求经过 `traceContextFilter`（非异步线程）
2. 异步线程需手动调用 `propagator.applyMdc()` / `clearMdc()`
3. Kafka 消费线程：确认 `TraceKafkaConsumerInterceptor` 已装配
4. 检查 `logback-spring.xml` 是否包含 `<includeMdcKeyName>traceId</includeMdcKeyName>`

---

## 7. 配置项参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `redteam.telemetry.enabled` | `true` | 可观测性总开关 |
| `redteam.telemetry.service-name` | `${spring.application.name}` | 服务名标签 |
| `redteam.telemetry.otlp.endpoint` | `http://localhost:4317` | OTLP/HTTP 端点 |
| `redteam.telemetry.otlp.enabled` | `true` | 是否启用 OTLP 上报 |
| `redteam.telemetry.alert.enabled` | `true` | 是否启用告警发送 |
| `redteam.telemetry.alert.feishu.webhook` | 空 | 飞书 Webhook |
| `redteam.telemetry.log.appender` | `CONSOLE_JSON` | 日志 appender |
| `redteam.telemetry.log.file.enabled` | `false` | 是否启用文件日志 |
| `redteam.telemetry.log.file.path` | `logs/${APP_NAME}.log` | 日志文件路径 |
| `management.endpoints.web.exposure.include` | `health,info,prometheus,metrics` | Actuator 暴露端点 |
| `management.metrics.export.prometheus.step` | `30s` | 指标抓取步长 |

---

## 8. 验收与测试

### 8.1 单元测试

```bash
mvn -pl common test -Dtest='com.redteam.common.telemetry.*Test'
```

覆盖 6 个测试类，共 44 个用例：

| 测试类 | 用例数 | 覆盖点 |
|--------|--------|--------|
| `BusinessMetricsRecorderTest` | 6 | 文件解析/AI/工作流/Kafka 积压/null 兜底 |
| `TraceContextPropagatorTest` | 7 | Span ID/inject-extract 往返/MDC/null 载体 |
| `AlertNotifierTest` | 10 | 禁用/无 webhook/P0/规则级优先/卡片结构/异常吞掉 |
| `OpenTelemetryConfigTest` | 8 | 端点规范化/OTLP 禁用启用/tracer Bean |
| `PrometheusMetricsConfigTest` | 7 | 公共标签/HTTP 直方图/JVM Binder |
| `UnifiedLogConfigTest` | 6 | MDC 写入清理/null 安全/Filter 异常清理 |

### 8.2 编译验证

```bash
mvn -pl common -am compile -DskipTests
mvn -pl common -am test-compile
```

### 8.3 端到端验证清单

- [ ] `/actuator/prometheus` 返回 200 且含 `file_parse_total` 等业务指标
- [ ] Grafana 系统总览面板可见 QPS / P95 / JVM 指标
- [ ] Jaeger UI 可检索到带 `service.name=parse-service` 的 Trace
- [ ] 日志中 `traceId` 与 Jaeger Trace ID 一致
- [ ] 手动触发 `kafka_lag_high` 规则后飞书群收到 P1 加急卡片
- [ ] 关闭某服务实例 1 分钟后触发 `service_down` P0 告警

---

## 9. 附：文件清单

```
backend/common/src/main/java/com/redteam/common/telemetry/
├── TelemetryAutoConfiguration.java      自动装配入口
├── TelemetryEnvironmentPostProcessor.java  Actuator 端点注入
├── OpenTelemetryConfig.java             OTel SDK + OTLP Exporter
├── TraceContextPropagator.java          HTTP/Kafka Trace 传播
├── TraceKafkaProducerInterceptor.java   Kafka 生产者 Trace 注入
├── TraceKafkaConsumerInterceptor.java   Kafka 消费者 Trace 提取
├── PrometheusMetricsConfig.java         Prometheus 指标配置
├── BusinessMetricsRecorder.java         业务指标记录器
├── UnifiedLogConfig.java                统一日志 MDC 配置
├── LogFieldConstants.java               日志字段常量
├── AlertRule.java                       告警规则定义
├── AlertSeverity.java                   告警分级枚举
└── AlertNotifier.java                   飞书 Webhook 通知器

backend/common/src/main/resources/
├── application-telemetry.yml            可观测性默认配置
├── logback-spring.xml                   统一 JSON 日志配置
├── alert-rules.yml                      内置告警规则（声明式）
└── META-INF/spring.factories            EnvironmentPostProcessor 注册

backend/docs/ops/
├── grafana-dashboards/
│   ├── system-overview.json             系统总览
│   ├── trace-detail.json                Trace 与慢请求分析
│   ├── business-metrics.json            业务指标
│   └── alert-infra.json                 告警与基础设施
├── prometheus-alert-rules.yml           Prometheus 告警规则
└── docker-compose-observability.yml     可观测性栈编排
```
