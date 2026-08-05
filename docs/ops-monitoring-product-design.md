# 红方文件汇聚管理平台 · 运维监控体系产品设计方案

> 文档版本：v1.0
> 编写日期：2026-07-29
> 适用范围：平台运维监控体系（系统运维层 + 应用运维层）
> 上游依赖：[monitor-design.md](./monitor-design.md) 监控指标体系、[ops-manual.md](./ops-manual.md) 系统运维手册
> 适用对象：产品经理、前端/后端研发、SRE、DBA、安全工程师、空间负责人

---

## 目录

1. [设计目标与边界划分](#1-设计目标与边界划分)
2. [体系总览架构](#2-体系总览架构)
3. [信息架构与导航整合](#3-信息架构与导航整合)
4. [系统运维层产品设计（L1–L3）](#4-系统运维层产品设计l1l3)
5. [应用运维层产品设计（L4–L8）](#5-应用运维层产品设计l4l8)
6. [统一告警与事件中心](#6-统一告警与事件中心)
7. [运维工单与审批闭环](#7-运维工单与审批闭环)
8. [数据模型扩展](#8-数据模型扩展)
9. [API 设计要点](#9-api-设计要点)
10. [权限模型](#10-权限模型)
11. [实施路线与验收标准](#11-实施路线与验收标准)
12. [与现有资产映射](#12-与现有资产映射)

---

## 1. 设计目标与边界划分

### 1.1 设计目标

| 目标 | 指标 |
|------|------|
| 双层覆盖 | 系统运维（基础设施/服务/中间件）+ 应用运维（数据空间/文件链路/应用配置） |
| 闭环能力 | 监控 → 告警 → 工单 → 操作 → 验证 → 归档，全流程线上化 |
| 故障定位 | P0 故障根因定位 ≤ 10 分钟，单空间数据异常发现 ≤ 5 分钟 |
| 操作可审计 | 所有运维操作 100% 留痕，写入 `t_file_event` 与审计日志 |
| 受众分层 | SRE/DBA 看系统层，空间负责人看应用层，互不干扰 |

### 1.2 边界划分

| 维度 | 系统运维层 | 应用运维层 |
|------|-----------|-----------|
| 运维对象 | 节点、Pod、微服务进程、中间件实例 | 团队空间、文件资产、数据链路、应用配置 |
| 健康判定 | 进程存活、CPU/内存/磁盘、副本数、连接池 | 配额、索引一致性、解析覆盖率、孤儿文件率 |
| 故障单位 | 节点/实例/Pod | 单空间/单批文件/单条链路 |
| 操作粒度 | 重启、扩缩容、回滚镜像、主从切换 | 重解析、重索引、清理孤儿、归档冷数据、回收权限 |
| 变更对象 | 镜像、K8s 资源、系统配置 | 解析器开关、模型路由、YARA 规则、标签规则版本 |
| SLA 视角 | 平台可用性 ≥ 99.9%、MTTR ≤ 30min | 单空间数据完整性 100%、可搜时延 P95 ≤ 60s |
| 受众 | SRE、DBA、网络工程师 | 空间负责人、数据治理、安全审计 |
| 产品入口 | `/ops/system/*` | `/ops/data/*`（数据空间应用运维） |

### 1.3 设计原则

1. **分层不重叠**：系统层只管"系统活着"，应用层只管"数据正确"。同一指标不在两层重复建设，按对象归属。
2. **复用不重复**：指标体系复用 [monitor-design.md](./monitor-design.md) 的 L1–L6；事件流复用 `t_file_event`；通知复用 `feishu-service`。
3. **操作即审计**：所有写操作（重启、重解析、删除、配置变更）必须产生工单或事件记录，禁止裸操作。
4. **空间隔离优先**：应用运维所有视图默认带 `team_space_id` 维度，跨空间操作需显式授权。
5. **渐进落地**：先监控、再操作台、后工单审批，避免一次性过度建设。

---

## 2. 体系总览架构

### 2.1 双层架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    运维监控统一门户 /ops                          │
│        全局搜索 · 个人值班视图 · 告警事件墙 · 工单待办             │
└─────────────────────────────────────────────────────────────────┘
        │                                       │
        ▼                                       ▼
┌───────────────────────────┐         ┌───────────────────────────┐
│   系统运维层 /ops/system   │         │  应用运维层 /ops/data      │
│   (SRE/DBA 视角)           │         │  (空间负责人/数据治理视角)  │
├───────────────────────────┤         ├───────────────────────────┤
│ S1 系统总览大屏            │         │ D1 数据空间资产台账         │
│ S2 服务健康中心            │         │ D2 数据一致性对账中心       │
│ S3 基础设施监控(USE)        │         │ D3 数据链路治愈操作台       │
│ S4 中间件监控              │         │ D4 数据生命周期治理         │
│ S5 日志中心                │         │ D5 应用配置运维中心         │
│ S6 容量与拓扑              │         │ D6 数据安全运维             │
│                           │         │ D7 空间负责人报告           │
└─────────────┬─────────────┘         └─────────────┬─────────────┘
              │                                     │
              ▼                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                统一告警与事件中心 /ops/alerts                    │
│        告警规则 · 事件墙 · 值班排班 · 飞书通知 · 订阅              │
└─────────────────────────────────────────────────────────────────┘
              ▼
┌─────────────────────────────────────────────────────────────────┐
│                运维工单与审批中心 /ops/tickets                   │
│        工单发起 · 多级审批 · 执行验证 · 归档 · 审计                │
└─────────────────────────────────────────────────────────────────┘
              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      底层数据底座                                │
│  Prometheus(时序) · PG(事件/聚合/工单) · ES(日志) · MinIO(归档)   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 分层与 monitor-design.md 指标层映射

| 本方案产品层 | 对应 monitor-design 指标层 | 主要受众 |
|-------------|--------------------------|---------|
| 系统运维层 S3/S4 | L3 基础设施(USE)、L2 接口与服务(RED) | SRE/DBA |
| 系统运维层 S2 | L2 服务存活 | SRE |
| 应用运维层 D1/D2/D4 | L1 业务流程、L6 数据质量与容量 | 空间负责人/数据治理 |
| 应用运维层 D3 | L1 业务流程（治愈侧） | SRE/数据治理 |
| 应用运维层 D5 | （新增）应用配置 | 平台管理员 |
| 应用运维层 D6 | L4 安全与合规 | 安全工程师 |
| 应用运维层 D7 | L5 SLO + L6 数据质量 | 空间负责人 |
| 统一告警中心 | L5 SLO 错误预算 | 全员 |

---

## 3. 信息架构与导航整合

### 3.1 导航重构方案

当前产品已有 `/monitor`（数据空间监控）与 `/admin/health`（健康检查），但分散。建议整合为统一的「运维监控」一级菜单，**按职能分 4 组**避免菜单过长（17 项扁平化难以扫读）：

```
运维监控
├─ 运维门户              /ops
├─ 系统运维组
│   ├─ 系统总览          /ops/system/overview
│   ├─ 服务健康          /ops/system/health
│   ├─ 基础设施          /ops/system/infra
│   ├─ 中间件            /ops/system/middleware
│   ├─ 日志中心          /ops/system/logs
│   ├─ 容量拓扑          /ops/system/capacity
│   └─ 运维效能          /ops/system/efficiency        (新增,见 S7)
├─ 应用运维组
│   ├─ 空间台账          /ops/data/spaces
│   ├─ 一致性对账        /ops/data/consistency
│   ├─ 链路治愈          /ops/data/heal
│   ├─ 生命周期          /ops/data/lifecycle
│   ├─ 应用配置          /ops/data/config
│   ├─ 数据安全          /ops/data/security
│   └─ 空间报告          /ops/data/reports
├─ 监控看板组
│   ├─ 数据空间监控      /ops/monitor                  (迁移自 /monitor)
│   └─ SOP 知识库        /ops/runbook                  (新增)
└─ 告警与工单组
    ├─ 告警中心          /ops/alerts
    ├─ 工单中心          /ops/tickets
    └─ 故障复盘          /ops/postmortem               (新增)
```

| 分组 | 二级菜单 | 路由 | 现状 | 动作 |
|------|---------|------|------|------|
| — | 运维门户 | `/ops` | 新增 | 新建 |
| 系统运维组 | 系统总览 | `/ops/system/overview` | 新增 | 新建 |
| 系统运维组 | 服务健康 | `/ops/system/health` | 已有 `/admin/health` | 迁移 |
| 系统运维组 | 基础设施 | `/ops/system/infra` | 新增 | 新建 |
| 系统运维组 | 中间件 | `/ops/system/middleware` | 新增 | 新建 |
| 系统运维组 | 日志中心 | `/ops/system/logs` | 新增 | 新建 |
| 系统运维组 | 容量拓扑 | `/ops/system/capacity` | 新增 | 新建 |
| 系统运维组 | 运维效能 | `/ops/system/efficiency` | 新增 | 新建（见 S7） |
| 应用运维组 | 空间台账 | `/ops/data/spaces` | 新增 | 新建 |
| 应用运维组 | 一致性对账 | `/ops/data/consistency` | 新增 | 新建 |
| 应用运维组 | 链路治愈 | `/ops/data/heal` | 新增 | 新建 |
| 应用运维组 | 生命周期 | `/ops/data/lifecycle` | 新增 | 新建 |
| 应用运维组 | 应用配置 | `/ops/data/config` | 部分已有 `/admin/config`、`/admin/models`、`/admin/yara-rules` | 整合 |
| 应用运维组 | 数据安全 | `/ops/data/security` | 新增 | 新建 |
| 应用运维组 | 空间报告 | `/ops/data/reports` | 新增 | 新建 |
| 监控看板组 | 数据空间监控 | `/ops/monitor` | 已有 `/monitor` | 迁移 |
| 监控看板组 | SOP 知识库 | `/ops/runbook` | 新增 | 新建 |
| 告警与工单组 | 告警中心 | `/ops/alerts` | 部分已有 `/admin/notifications` | 整合 |
| 告警与工单组 | 工单中心 | `/ops/tickets` | 新增 | 新建 |
| 告警与工单组 | 故障复盘 | `/ops/postmortem` | 新增 | 新建 |

> 兼容策略：原有 `/monitor`、`/admin/health`、`/admin/notifications` 路由保留 30 天重定向到新路径，避免书签失效。

### 3.2 角色与默认落地页

| 角色 | 默认落地页 | 关注模块 |
|------|-----------|---------|
| SRE/值班工程师 | `/ops`（运维门户） | 系统运维、告警中心 |
| DBA | `/ops/system/middleware` | 中间件、日志中心 |
| 空间负责人 | `/ops/data/reports` | 空间报告、链路治愈 |
| 数据治理 | `/ops/data/consistency`、`/ops/data/lifecycle` | 一致性、生命周期 |
| 安全工程师 | `/ops/data/security` | 数据安全、告警中心 |
| 平台管理员 | `/ops/data/config` | 应用配置、工单中心 |

### 3.3 运维门户 `/ops` 设计

| 区域 | 内容 |
|------|------|
| 顶部 | 当前值班人、值班时段、交接班按钮 |
| KPI 带 | 平台可用性、P0 告警数、待处理工单数、系统健康分、数据健康分 |
| 左侧 | 我的待办（工单 + 告警确认）、我负责的空间健康概览 |
| 中部 | 系统拓扑缩略图（红/黄/绿节点）+ 数据空间漏斗缩略图 |
| 右侧 | 最近 1h 告警事件流、最近工单动态 |
| 底部 | 快捷操作（重启服务、重解析、建工单、查看值班表） |

---

## 4. 系统运维层产品设计（L1–L3）

### 4.1 S1 系统总览大屏 `/ops/system/overview`

**定位**：SRE 一眼看清平台系统层健康度。

| 模块 | 图表 | 数据源 |
|------|------|--------|
| 平台 KPI | 可用性、P0/P1 告警数、在线服务数、宕机服务数 | Prometheus + `t_alert_event` |
| 服务健康矩阵 | 11 个微服务 × 状态色块（健康/降级/异常） | `service.*.up` |
| 中间件健康矩阵 | PG/ES/MinIO/Neo4j/Milvus/Redis/Kafka 状态色块 | 中间件 exporter |
| 节点资源热力图 | K8s 节点 CPU/内存/磁盘热力图 | node_exporter |
| 实时告警流 | 最近 1h P0/P1 告警滚动 | `t_alert_event` |
| 容量水位 | 存储卷用量、ES 磁盘、PG 表空间 Top10 | USE 指标 |
| 近 24h 故障时间线 | 故障事件时间轴 | `t_alert_event` |

**交互**：任意色块点击 → 下钻到对应服务/中间件详情页。

### 4.2 S2 服务健康中心 `/ops/system/health`（增强现有 HealthCheck）

**现状**：已有 `/admin/health` 提供服务卡片 + 重新检查。**增强项**：

| 增强点 | 说明 |
|--------|------|
| 服务依赖拓扑 | 可视化服务间依赖（auth→mysql/redis，parse→minio/es），依赖异常高亮 |
| 实时指标嵌入 | 卡片下沉 RED 指标 mini 图（QPS/错误率/P95） |
| 实例级下钻 | 服务下多实例（Pod）列表，单实例 CPU/内存/线程池 |
| 历史可用率 | 7/30 天可用率折线，标注故障窗口 |
| 一键操作 | 重启实例、扩缩容、查看日志、回滚版本（均触发工单） |

**卡片信息模型**：

```ts
interface ServiceHealthCard {
  id: string;
  service: string;          // service 标识
  name: string;             // 中文名
  status: 'healthy' | 'degraded' | 'unhealthy' | 'unknown';
  version: string;
  latencyMs: number;        // 健康探针延迟
  uptime: number;           // 30天可用率
  redMetrics: {             // 近5min RED
    rate: number;           // QPS
    errorRate: number;      // 错误率
    p95: number;            // P95
  };
  instances: InstanceHealth[];  // Pod 实例列表
  dependencies: DepStatus[];    // 依赖服务状态
  lastCheckAt: string;
  lastError?: string;
  recentIncidents: AlertEvent[]; // 近24h告警
}
```

### 4.3 S3 基础设施监控 `/ops/system/infra`

**定位**：USE 方法论落地页。

| 子页 | 内容 | 指标 |
|------|------|------|
| 节点列表 | K8s 节点 CPU/内存/磁盘/网络 | `process.cpu.usage`、`jvm.memory.*`、`disk.usage` |
| Pod 列表 | 按 namespace/workload 分组的 Pod 状态、重启次数、CPU/内存 limit 使用率 | kube-state-metrics |
| 网络 | 入/出流量、TCP 连接数、重传率 | node_network_* |
| 磁盘 IO | 读写 IOPS、延迟 | node_disk_* |

**告警阈值**（默认，可配置）：CPU > 80% 持续 5min、内存 > 90%、磁盘 > 85%。

### 4.4 S4 中间件监控 `/ops/system/middleware`

按中间件分 Tab：

#### 4.4.1 PostgreSQL
| 指标 | 说明 |
|------|------|
| 连接数 / 连接池使用率 | active/idle/pending |
| QPS / 慢查询 Top10 | `pg_stat_statements` |
| 表空间占用 Top10 | 大表预警 |
| 复制延迟 | 主从延迟 |
| 死锁 / 长事务 | 实时列表 |
| 锁等待 | 阻塞链 |

#### 4.4.2 Elasticsearch
| 指标 | 说明 |
|------|------|
| 集群健康 | green/yellow/red、分片数、未分配分片 |
| 索引体积 Top10 | `capacity.es.size` |
| 写入拒绝 | `es.reject.count` |
| 查询延迟 P95 | 按索引 |
| 节点磁盘 / JVM 堆 | 节点级 |

#### 4.4.3 MinIO
| 指标 | 说明 |
|------|------|
| 桶列表 | 文件数、体积、生命周期规则 |
| PUT/GET 延迟 / 错误率 | `minio.object.put/get` |
| 磁盘使用率 | 节点级 |
| 在线磁盘数 | 降级预警 |

#### 4.4.4 Neo4j
| 指标 | 说明 |
|------|------|
| 节点/关系数 | 按标签 |
| 事务率 / 锁等待 | |
| 堆内存 / 页缓存命中率 | |
| 慢查询 Top10 | |

#### 4.4.5 Milvus
| 指标 | 说明 |
|------|------|
| 集合数 / 向量数 | `milvus.collection.*` |
| 查询延迟 P95 | |
| 索引构建进度 | |
| 内存使用 | |

#### 4.4.6 Redis / Kafka
- Redis：内存使用、QPS、慢命令、键过期
- Kafka：Topic 积压（Lag）、消费组延迟、分区健康

### 4.5 S5 日志中心 `/ops/system/logs`

**定位**：统一日志检索与上下文关联。

| 功能 | 说明 |
|------|------|
| 日志检索 | 按 service/level/time/keyword 过滤，ES 后端 |
| trace_id 串联 | 输入 trace_id 还原文件全链路日志（Upload→Index→Parse→Search） |
| 上下文展开 | 单条日志向前/向后 N 行 |
| 字段过滤 | 按 team_space_id / error_code / file_id 过滤 |
| 日志告警 | 关键字命中自动生成告警事件 |
| 日志导出 | 导出选中范围（需工单） |

### 4.6 S6 容量与拓扑 `/ops/system/capacity`

| 模块 | 内容 |
|------|------|
| 资源水位 | CPU/内存/磁盘/存储 30 天趋势 + 预计耗尽天数 |
| 服务拓扑 | 完整服务依赖图（含中间件），可下钻 |
| 容量预测 | 基于近 30 天增长趋势预测 30/60/90 天水位 |
| 扩容建议 | 自动给出扩容建议（如「ES 磁盘 45 天后耗尽，建议扩容 500GB」） |

### 4.7 S7 运维效能看板 `/ops/system/efficiency`（审查优化新增）

**定位**：度量运维体系自身健康度，避免"运维黑盒"。

| 指标 | 定义 | 目标 |
|------|------|------|
| MTTR | 故障平均恢复时间（P0/P1） | P0 ≤ 30min，P1 ≤ 2h |
| MTBF | 平均故障间隔 | ≥ 720h |
| 告警噪声率 | 误告警 / 总告警 | ≤ 5% |
| 告警收敛率 | 1 − 实际通知 / 原始触发 | ≥ 70% |
| 自动化率 | 自动治愈 / 总治愈操作 | ≥ 60% |
| 工单平均处理时长 | 发起 → 完成 | ≤ 4h |
| SOP 覆盖率 | 有 SOP 的告警类型 / 总告警类型 | ≥ 80% |
| 故障复盘完成率 | 已复盘 P0/P1 / 总 P0/P1 | 100% |

**看板图表**：MTTR 趋势、告警噪声率趋势、自动化率趋势、工单处理时长分布、SOP 覆盖率环形图、故障复盘列表。

---

## 5. 应用运维层产品设计（L4–L8）

### 5.1 D1 数据空间资产台账 `/ops/data/spaces`

**定位**：把"团队空间"当资产管起来。

| 模块 | 功能 |
|------|------|
| 空间列表 | 空间 ID/编码/名称/负责人/成员数/文件数/存储用量/配额使用率/生命周期状态 |
| 空间详情 | 概览 / 文件分布 / 链路健康 / 成员 / 配额变更历史 / 操作事件 |
| 生命周期状态机 | `active` → `frozen` → `archived` → `destroyed`，状态变更需审批 |
| 负责人移交 | 离职/调岗场景的空间归属变更，避免孤儿空间 |
| 空间健康分 | 综合（配额水位 + 索引覆盖率 + 解析成功率 + 安全合规）0–100 分 |
| 批量操作 | 批量冻结、批量归档（需工单） |

**空间健康分算法**：

```
Score = 0.25×(100 - quota_usage%) 
      + 0.25×index_coverage%
      + 0.25×parse_success_rate%
      + 0.25×(100 - security_risk_score%)
```

### 5.2 D2 数据一致性对账中心 `/ops/data/consistency`

**定位**：文件管理系统多存储一致性巡检，**应用运维最核心模块**。

#### 5.2.1 对账任务类型

| 对账类型 | 检查内容 | 频率 | 严重级别 |
|---------|---------|------|---------|
| PG ↔ MinIO | t_file 有记录但对象丢失 / 对象存在但无元数据 | 每日 02:00 | P1 |
| PG ↔ ES | index_status=2 但 ES 查不到 / ES 文档但 PG 已删 | 每日 02:30 | P1 |
| PG ↔ Neo4j | 实体表 vs 图节点数差异 | 每日 03:00 | P2 |
| PG ↔ Milvus | 向量索引 vs 文件记录差异 | 每周 03:30 | P2 |
| trace_id 断链 | UPLOAD 成功但 INDEX/PARSE 无后续事件 | 每小时 | P1 |
| 孤儿对象 | MinIO 对象无 PG 记录 | 每日 04:00 | P2 |
| 索引积压 | index_status=0 超 N 分钟的文件 | 每 10min | P1 |
| 解析积压 | parse_status=1 超 N 分钟 | 每 10min | P1 |

#### 5.2.2 对账结果页

| 区域 | 内容 |
|------|------|
| 顶部 KPI | 今日对账任务数、不一致数、已修复数、待处理数 |
| 任务列表 | 任务名 / 上次执行时间 / 状态（正常/异常/运行中）/ 不一致数 / 操作 |
| 不一致明细 | 按 team_space_id 分组，列出 file_id / 差异类型 / 发现时间 / 建议操作 |
| 一键修复 | 对可自动修复项（如重索引、删孤儿）发起治愈工单 |

#### 5.2.3 对账结果数据模型

```sql
CREATE TABLE t_consistency_check (
  id              BIGINT PRIMARY KEY,
  check_type      VARCHAR(32) NOT NULL COMMENT '对账类型: PG_MINIO/PG_ES/PG_NEO4J/TRACE_BROKEN/ORPHAN_OBJECT/INDEX_LAG/PARSE_LAG',
  team_space_id   BIGINT,
  started_at      TIMESTAMP NOT NULL,
  finished_at     TIMESTAMP,
  status          SMALLINT NOT NULL COMMENT '0运行中 1正常 2异常 3失败',
  total_checked   BIGINT COMMENT '检查总数',
  diff_count      BIGINT COMMENT '不一致数',
  diff_sample     JSONB COMMENT '差异样本(前100条)',
  report_url      VARCHAR(512) COMMENT '完整报告URL'
);
CREATE INDEX idx_check_type_time ON t_consistency_check(check_type, started_at);
```

### 5.3 D3 数据链路治愈操作台 `/ops/data/heal`

**定位**：监控/对账发现问题后，对单文件/单批文件执行治愈操作。

#### 5.3.1 治愈操作清单

| 操作 | 入参 | 影响 | 审批要求 |
|------|------|------|---------|
| 单文件重索引 | file_id | 重建 ES 文档 | 免审批（低危） |
| 单文件重解析 | file_id | 清空 parseStatus 重新入队 | 免审批 |
| 批量重解析 | file_id[] / 错误码筛选 | 批量重新解析 | 工单审批（>50 条） |
| 重建图关系 | team_space_id | 重跑 NER → Neo4j 入图 | 工单审批 |
| 重建向量索引 | team_space_id | 重新生成 Milvus 向量 | 工单审批 |
| 删除孤儿对象 | object_key[] | 物理删除 MinIO 孤儿 | 工单审批 |
| 强制删除文件 | file_id[] | 联动删除 PG/MinIO/ES/Neo4j | 高危工单 + 二次确认 |
| 修复 trace 断链 | trace_id | 补发缺失事件 | 免审批 |

#### 5.3.2 操作台交互

- 左侧：操作类型选择 + 参数表单
- 中部：目标文件预览（表格，支持按错误码/空间/状态筛选）
- 右侧：执行计划（影响文件数、预计耗时、风险评估）+ 执行按钮
- 底部：执行历史（操作人/时间/结果/事件 ID）

#### 5.3.3 操作留痕

所有治愈操作写入 `t_file_event`，`event_type` 取值扩展：

| event_type | 含义 |
|-----------|------|
| `OPS_RETRY_INDEX` | 重索引 |
| `OPS_RETRY_PARSE` | 重解析 |
| `OPS_REBUILD_GRAPH` | 重建图关系 |
| `OPS_REBUILD_VECTOR` | 重建向量 |
| `OPS_PURGE_ORPHAN` | 清理孤儿 |
| `OPS_DELETE_FILE` | 强制删除 |
| `OPS_FIX_TRACE` | 修复链路 |
| `OPS_ARCHIVE` | 归档冷数据 |
| `OPS_RESTORE` | 恢复归档 |

### 5.4 D4 数据生命周期治理 `/ops/data/lifecycle`

**定位**：数据空间持续膨胀的主动治理。

| 子模块 | 功能 |
|--------|------|
| 冷热分层 | N 天未访问文件从 MinIO 热桶迁到归档桶，PG 标记 `storage_tier=cold` |
| 过期清理 | 按空间/项目周期到期标记，审批后物理删除（四存储联动） |
| 孤儿治理 | MinIO 孤儿对象扫描 + 清理工单 |
| 冗余回收 | 秒传重复对象去重、解析中间产物清理 |
| 归档恢复 | 归档文件恢复到热存储（异步任务，预计耗时提示） |
| 生命周期策略 | 按空间配置策略（如「90 天未访问转冷，1 年过期」） |
| 配额管理 | 配额预警（80%/90%）+ 扩容工单 + 限制上传策略 |

#### 5.4.1 生命周期策略模型

```sql
CREATE TABLE t_lifecycle_policy (
  id              BIGINT PRIMARY KEY,
  team_space_id   BIGINT COMMENT 'NULL=全局默认',
  policy_name     VARCHAR(128) NOT NULL,
  cold_after_days INT COMMENT 'N天未访问转冷',
  expire_after_days INT COMMENT 'N天过期',
  archive_storage_class VARCHAR(32) COMMENT 'MinIO归档存储类',
  enabled         SMALLINT DEFAULT 1,
  created_at      TIMESTAMP DEFAULT NOW()
);
```

### 5.5 D5 应用配置运维中心 `/ops/data/config`

**定位**：文件管理系统的"业务参数"运维，区别于系统配置。

#### 5.5.1 配置分类

| 配置类 | 内容 | 现状 | 动作 |
|--------|------|------|------|
| 解析器开关矩阵 | 按 file_type 启用/禁用解析器、版本切换、灰度比例 | 新增 | 新建 |
| 模型路由 | NER/IOC 抽取的模型选择与降级路由 | 已有 `/admin/models` | 整合 |
| YARA 规则 | 规则上下线、版本回滚、命中回归 | 已有 `/admin/yara-rules` | 整合 |
| 标签规则版本 | 标签体系变更影响评估、重打标 | 已有 tag-system | 整合 |
| 上传策略 | MIME 白名单、敏感规则、单文件大小限制 | 已有 `/admin/config` | 整合 |
| 索引策略 | ES 分片数、刷新间隔、字段映射 | 新增 | 新建 |
| 重试策略 | 解析失败重试次数、退避策略 | 新增 | 新建 |

#### 5.5.2 配置变更流程

```
草稿 → 影响评估 → 审批 → 灰度（指定空间） → 验证 → 全量生效
                                              ↘ 回滚
```

- 所有变更记录前后值、操作人、生效范围
- 灰度阶段支持按 `team_space_id` 灰度
- 高危变更（如标签规则版本、YARA 规则）强制双签

#### 5.5.3 配置变更记录模型

```sql
CREATE TABLE t_config_change (
  id              BIGINT PRIMARY KEY,
  config_type     VARCHAR(32) NOT NULL COMMENT 'PARSER/MODEL/YARA/TAG/UPLOAD/INDEX/RETRY',
  config_key      VARCHAR(128) NOT NULL,
  old_value       TEXT,
  new_value       TEXT,
  operator_id     BIGINT NOT NULL,
  reason          VARCHAR(512),
  scope_type      VARCHAR(16) COMMENT 'GLOBAL/TEAM_SPACE',
  scope_space_id  BIGINT,
  status          SMALLINT COMMENT '0草稿 1审批中 2灰度 3生效 4回滚',
  ticket_id       BIGINT COMMENT '关联工单ID',
  created_at      TIMESTAMP DEFAULT NOW(),
  effective_at    TIMESTAMP
);
```

### 5.6 D6 数据安全运维 `/ops/data/security`

**定位**：把安全从系统层下沉到数据层。

| 子模块 | 功能 | 关联指标 |
|--------|------|---------|
| 敏感文件再识别 | 标签体系升级后对历史文件批量重打敏感等级 | `sensitive.file.access` |
| 权限回收巡检 | 离职成员自动清理、过期分享链接回收 | `access.denied.count` |
| 异常下载检测 | 单用户短时大批量下载、跨空间聚集下载告警 | `file.download.cross_team` |
| 最小权限校验 | 定期扫描"角色 > 实际需要"的成员 | L4 安全 |
| 数据导出审批 | 整空间导出/批量下载需审批 + 水印 | |
| 敏感访问审计 | 高敏感文件访问 Top 列表 | `sensitive.file.access` |
| 安全事件时间线 | 安全类告警事件时间轴 | `t_alert_event` |

### 5.7 D7 空间负责人报告 `/ops/data/reports`

**定位**：面向"我的空间健康吗"的报告，区别于 SRE 视角。

| 报告类型 | 频率 | 内容 |
|---------|------|------|
| 周报 | 每周一 | 文件增长、解析成功率、Top 失败原因、配额趋势、安全事件 |
| 月报 | 每月 1 日 | 数据质量评分（覆盖率/完整率/重复率）、容量预测、治理建议 |
| 异常通报 | 实时 | 突发索引积压、解析失败率飙升的定向飞书通知 |
| 空间健康分日报 | 每日 | 健康分趋势 + 失分项 + 改进建议 |

**报告订阅**：空间负责人可订阅飞书推送，平台管理员可订阅全空间概览。

---

## 6. 统一告警与事件中心 `/ops/alerts`

### 6.1 告警体系架构

```
Prometheus Alertmanager ──┐
                          ├──→ 告警聚合去重 ──→ 事件墙 ──→ 飞书 IM 卡片
应用运维巡检任务 ─────────┤                  └──→ 工单（高危） ──→ 审批
                          │
手动告警（SRE 触发） ─────┘
```

### 6.2 告警分级

| 级别 | 含义 | 响应时效 | 通知渠道 | 示例 |
|------|------|---------|---------|------|
| P0 | 紧急：平台不可用 / 数据丢失 | 立即 | 飞书加急 + 电话 | 某服务全部实例宕机、MinIO 磁盘满 |
| P1 | 严重：核心功能受损 | 15min | 飞书加急 | 搜索 P95 > 2s、索引积压 > 1000 |
| P2 | 警告：潜在风险 | 1h | 飞书 IM | 配额使用率 > 85%、解析失败率 > 10% |
| P3 | 提示：需关注 | 4h | 飞书 IM 摘要 | 单空间健康分下降、孤儿文件率 > 0.1% |

### 6.3 告警事件页

| 区域 | 内容 |
|------|------|
| 事件墙 | P0/P1 红黄高亮卡片，按时间倒序 |
| 筛选器 | 级别 / 状态（触发中/已恢复/已忽略）/ 层（系统/应用）/ 空间 |
| 事件详情 | 触发时间 / 规则 / 实际值 / 上下文样本 / 关联 trace / 处理建议 |
| 操作 | 确认认领 / 转工单 / 忽略 / 静默 1h |
| 值班排班 | 当前值班人、轮换表、换班申请 |

### 6.4 告警规则分类

| 类别 | 来源 | 示例规则 |
|------|------|---------|
| 系统层 | Prometheus | `up == 0` for 1m、`process_cpu_usage > 0.9` for 5m |
| 中间件层 | exporter | ES `cluster_status != green`、PG `replication_lag > 10s` |
| 业务层 | t_metric_hourly + PromQL | `file.parse.success.rate < 0.95` for 10min |
| SLO 层 | t_slo_record | `burn_rate_2h > 14`（错误预算快速燃烧） |
| 应用运维层 | 巡检任务 | 对账不一致数 > 0、孤儿文件率 > 0.1% |

### 6.5 飞书通知卡片

复用 `feishu-service` 已有 IM 卡片能力，卡片包含：
- 告警标题 + 级别色
- 触发时间 / 空间 / 服务
- 实际值 vs 阈值
- 「查看详情」「确认认领」「转工单」三个按钮（card.action.trigger 回调）

### 6.6 告警治理与降噪（审查优化新增）

告警噪声是运维体系最大杀手，必须从设计阶段内置治理能力。

| 治理能力 | 说明 | 配置示例 |
|---------|------|---------|
| **告警聚合** | 同一规则 + 同一对象在窗口内多次触发，聚合为一条事件 | 同一 service 5min 内多次 CPU>80% 聚合为 1 条 |
| **告警去重** | 相同标签指纹的事件去重，仅保留最新 | 按 `rule_code + label_fingerprint` 去重 |
| **告警抑制** | P0 告警触发时抑制其下游 P1/P2 告警 | ES 宕机时抑制"索引失败率升高"P1 |
| **维护窗口** | 变更/巡检期间对指定服务/空间静默告警 | 变更 parse-service 时静默 30min，需工单关联 |
| **告警静默** | 手动对单条事件或规则静默 N 分钟 | 静默 1h，超时自动恢复 |
| **告警分组路由** | 按层（系统/应用）+ 空间路由到不同值班人 | 应用层告警路由到空间负责人 |
| **告警收敛率度量** | 度量治理效果，目标收敛率 ≥ 70% | 原始触发数 / 实际通知数 |

**维护窗口模型**：

```sql
CREATE TABLE t_alert_maintenance (
  id              BIGINT PRIMARY KEY,
  scope_type      VARCHAR(16) NOT NULL COMMENT 'SERVICE/SPACE/GLOBAL',
  scope_ref       VARCHAR(128) NOT NULL COMMENT '服务名或空间ID',
  start_at        TIMESTAMP NOT NULL,
  end_at          TIMESTAMP NOT NULL,
  reason          VARCHAR(256) NOT NULL,
  ticket_id       BIGINT COMMENT '关联变更工单',
  created_by      BIGINT NOT NULL,
  status          SMALLINT DEFAULT 1 COMMENT '0失效 1生效'
);
```

**治理流程**：Prometheus 告警 → Alertmanager 路由 → 治理层（聚合/去重/抑制/维护窗口过滤）→ 事件墙 + 通知。

---

## 7. 运维工单与审批闭环 `/ops/tickets`

### 7.1 工单类型

| 类型 | 触发场景 | 审批流 |
|------|---------|--------|
| 配额扩容 | 空间用量 90% / 手动申请 | 空间负责人 → 平台管理员 |
| 空间销毁 | 生命周期到期 / 手动 | 空间负责人 → 平台管理员 → 二次确认 |
| 批量删除 | 数据清理 | 空间负责人 → 平台管理员 |
| 批量重解析 | 治愈操作 > 50 条 | 数据治理 → 平台管理员 |
| 数据导出 | 整空间导出 | 空间负责人 → 安全工程师 |
| 配置变更 | 高危应用配置 | 平台管理员 → 双签 |
| 重启服务 | 系统运维操作 | SRE 负责人 |
| 回滚版本 | 系统运维操作 | SRE 负责人 + 发布负责人 |

### 7.2 工单状态机

```
草稿 → 待审批 → 审批通过 → 执行中 → 验证中 → 已完成
                ↓            ↓
              已拒绝       执行失败 → 重试 / 关闭
```

### 7.3 高危操作保护

| 保护措施 | 适用场景 | 实现要点 |
|---------|---------|---------|
| 二次确认弹窗 | 所有删除类操作 | 强制输入空间编码或文件数确认 |
| **身份二次验证** | 高危工单执行（销毁/批量删除/配置变更） | 飞书扫码 / 短信验证码，5min 有效 |
| **操作幂等性** | 所有写操作 | 请求带 `Idempotency-Key`，服务端 24h 去重 |
| **防重放** | 所有写操作 | timestamp + nonce，5min 窗口拒绝重复 |
| 延时执行（24h 撤回窗口） | 整空间销毁、批量物理删除 | 执行前 24h 仅锁定不删除，可撤回 |
| 影响范围预览 | 执行前 | 展示受影响文件数/空间数/存储量 |
| 操作复核人 | 高危操作 | 第二人复核执行（dual control） |
| 操作前自动备份 | 删除类 | 删除前导出元数据快照到 MinIO，保留 30 天 |
| **频率限制** | 免审批操作 | 单用户单接口 10 次/min，超限触发告警 |
| **操作冷却期** | 配置变更 | 同一配置 5min 内仅允许变更 1 次 |

**幂等与防重放请求头**：

```
Idempotency-Key: <uuid>          # 客户端生成,24h内同key同参数返回同结果
X-Request-Nonce: <random>        # 服务端校验,5min内拒绝重复nonce
X-Request-Timestamp: <epoch>     # ±5min容差
```

### 7.4 工单数据模型

```sql
CREATE TABLE t_ops_ticket (
  id              BIGINT PRIMARY KEY,
  ticket_no       VARCHAR(32) UNIQUE NOT NULL COMMENT '工单号 OPS-YYYYMMDD-XXXX',
  ticket_type     VARCHAR(32) NOT NULL COMMENT 'QUOTA/DESTROY/DELETE/REPARSE/EXPORT/CONFIG/RESTART/ROLLBACK',
  title           VARCHAR(256) NOT NULL,
  description     TEXT,
  team_space_id   BIGINT,
  target_ref      VARCHAR(512) COMMENT '操作目标(file_id列表/服务名等)',
  params          JSONB COMMENT '操作参数',
  impact_preview  JSONB COMMENT '影响预览(文件数/空间数等)',
  status          SMALLINT NOT NULL COMMENT '0草稿 1待审批 2通过 3执行中 4验证中 5完成 6拒绝 7失败',
  created_by      BIGINT NOT NULL,
  assignee_id     BIGINT COMMENT '处理人',
  approver_ids    VARCHAR(256) COMMENT '审批人ID列表',
  created_at      TIMESTAMP DEFAULT NOW(),
  approved_at     TIMESTAMP,
  executed_at     TIMESTAMP,
  finished_at     TIMESTAMP
);
CREATE INDEX idx_ticket_status ON t_ops_ticket(status, created_at);

CREATE TABLE t_ops_ticket_log (
  id              BIGINT PRIMARY KEY,
  ticket_id       BIGINT NOT NULL,
  action          VARCHAR(32) COMMENT 'CREATE/APPROVE/REJECT/EXECUTE/VERIFY/COMMENT',
  operator_id     BIGINT NOT NULL,
  comment         TEXT,
  created_at      TIMESTAMP DEFAULT NOW()
);
```

### 7.5 工单与事件联动

- 工单执行产生的所有操作写入 `t_file_event`，`meta.ticket_id` 关联工单
- 工单执行后自动触发验证任务（如重解析后检查 parseStatus）
- 验证通过 → 工单自动流转到「已完成」

---

## 8. 数据模型扩展

基于 [monitor-design.md](./monitor-design.md) 已有表，新增以下表支撑运维体系：

| 表名 | 用途 | 章节 |
|------|------|------|
| `t_consistency_check` | 一致性对账结果 | 5.2.3 |
| `t_lifecycle_policy` | 生命周期策略 | 5.4.1 |
| `t_config_change` | 应用配置变更记录 | 5.5.3 |
| `t_ops_ticket` | 运维工单 | 7.4 |
| `t_ops_ticket_log` | 工单操作日志 | 7.4 |
| `t_ops_runbook` | 运维手册/SOP 知识库 | 新增 |

### 8.1 t_file 表补强

```sql
ALTER TABLE t_file ADD COLUMN storage_tier VARCHAR(16) DEFAULT 'hot' COMMENT '存储层:hot/cold/archived';
ALTER TABLE t_file ADD COLUMN last_access_at TIMESTAMP COMMENT '最近访问时间(用于冷热分层)';
ALTER TABLE t_file ADD COLUMN lifecycle_status VARCHAR(16) DEFAULT 'active' COMMENT 'active/archived/pending_destroy';
ALTER TABLE t_file ADD COLUMN health_score INT COMMENT '文件健康分(派生)';
```

### 8.2 t_team_space 表补强

```sql
ALTER TABLE t_team_space ADD COLUMN lifecycle_status VARCHAR(16) DEFAULT 'active' COMMENT 'active/frozen/archived/destroyed';
ALTER TABLE t_team_space ADD COLUMN health_score INT COMMENT '空间健康分(派生,定时刷新)';
ALTER TABLE t_team_space ADD COLUMN cold_file_count INT DEFAULT 0 COMMENT '冷存储文件数';
ALTER TABLE t_team_space ADD COLUMN archived_bytes BIGINT DEFAULT 0 COMMENT '归档存储用量';
```

### 8.3 t_file_event event_type 扩展

在原有 `START/SUCCESS/FAIL` 基础上，新增 `OPS_*` 类型（见 5.3.3），并扩展 `meta` 字段：

```json
{
  "ticket_id": 12345,
  "operator_id": 20012,
  "reason": "对账发现ES文档缺失",
  "before": {"parse_status": 2},
  "after": {"parse_status": 1}
}
```

### 8.4 运维手册知识库

```sql
CREATE TABLE t_ops_runbook (
  id              BIGINT PRIMARY KEY,
  title           VARCHAR(256) NOT NULL COMMENT 'SOP标题',
  runbook_type    VARCHAR(32) COMMENT 'INCIDENT/OPS/RECOVERY',
  scenario        VARCHAR(256) COMMENT '适用场景(如:ES集群red)',
  severity        SMALLINT COMMENT '关联告警级别',
  content         TEXT COMMENT 'SOP正文(Markdown)',
  related_alert_codes VARCHAR(512) COMMENT '关联告警规则编码',
  version         INT DEFAULT 1,
  updated_by      BIGINT,
  updated_at      TIMESTAMP DEFAULT NOW()
);
```

---

## 9. API 设计要点

### 9.1 API 分组

| 分组 | 前缀 | 说明 |
|------|------|------|
| 系统运维 | `/api/ops/system/**` | 服务健康、基础设施、中间件、日志、容量 |
| 应用运维 | `/api/ops/data/**` | 空间台账、对账、治愈、生命周期、配置、安全、报告 |
| 告警 | `/api/ops/alerts/**` | 告警规则、事件、值班、订阅 |
| 工单 | `/api/ops/tickets/**` | 工单 CRUD、审批、执行、验证 |

### 9.2 关键接口示例

```
# 一致性对账
POST   /api/ops/data/consistency/run          # 手动触发对账任务
GET    /api/ops/data/consistency/results       # 对账结果列表
GET    /api/ops/data/consistency/results/{id}  # 对账详情
POST   /api/ops/data/consistency/fix            # 发起修复工单

# 链路治愈
POST   /api/ops/data/heal/retry-index          # 单文件重索引
POST   /api/ops/data/heal/retry-parse          # 单文件重解析
POST   /api/ops/data/heal/batch-retry          # 批量重解析(触发工单)
POST   /api/ops/data/heal/rebuild-graph        # 重建图关系
POST   /api/ops/data/heal/purge-orphan         # 清理孤儿

# 生命周期
GET    /api/ops/data/lifecycle/policies        # 策略列表
POST   /api/ops/data/lifecycle/policies        # 创建策略
POST   /api/ops/data/lifecycle/archive         # 归档指定文件
POST   /api/ops/data/lifecycle/restore         # 恢复归档

# 工单
POST   /api/ops/tickets                        # 创建工单
POST   /api/ops/tickets/{id}/approve           # 审批
POST   /api/ops/tickets/{id}/execute           # 执行
GET    /api/ops/tickets/my-todo                # 我的待办

# 告警
GET    /api/ops/alerts/events                  # 事件列表
POST   /api/ops/alerts/events/{id}/ack         # 确认认领
POST   /api/ops/alerts/events/{id}/mute        # 静默
```

### 9.3 统一响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... },
  "trace_id": "abc123"
}
```

### 9.4 鉴权与限流

- 所有 `/api/ops/**` 接口需携带 JWT，按角色鉴权（见第 10 章）
- 高危操作接口（删除、批量操作）强制二次校验 + 操作日志
- 查询类接口限流 100 QPS/用户，操作类 10 QPS/用户

### 9.5 通用查询规范（审查优化新增）

所有列表查询接口统一支持以下查询参数：

| 参数 | 说明 | 示例 |
|------|------|------|
| `page` / `size` | 分页，默认 1/20，size 上限 100 | `page=1&size=20` |
| `sort` | 排序，格式 `field,asc|desc`，支持多字段 | `sort=created_at,desc&sort=team_space_id,asc` |
| `fields` | 字段过滤，逗号分隔，按需返回 | `fields=id,name,status` |
| `q` | 关键词模糊搜索（针对有文本字段的接口） | `q=APT` |
| `team_space_id` | 空间过滤（应用运维层强制） | `team_space_id=1001` |

**统一分页响应**：

```json
{
  "code": 0,
  "data": {
    "items": [ ... ],
    "total": 1234,
    "page": 1,
    "size": 20,
    "has_next": true
  }
}
```

### 9.6 并发控制（审查优化新增）

| 场景 | 风险 | 控制策略 |
|------|------|---------|
| 工单多人并发审批 | 重复审批/状态竞争 | 乐观锁 `version` 字段 + 状态前置校验，CAS 更新 |
| 同一文件并发治愈 | 重复重解析 | 文件级分布式锁 `lock:file:{id}`，TTL 5min |
| 同一空间并发重建 | 资源冲突 | 空间级锁 `lock:space:{id}:rebuild`，互斥 |
| 配置并发变更 | 相互覆盖 | 配置键级乐观锁 + 变更前后值校验 |
| 工单并发执行 | 重复执行 | 工单状态机 CAS，仅 `APPROVED` 可流转到 `EXECUTING` |

**乐观锁示例**：

```sql
UPDATE t_ops_ticket
SET status = 3, version = version + 1, executed_at = NOW()
WHERE id = ? AND status = 2 AND version = ?;
-- 影响行数=0 表示状态已变或版本冲突,需重试
```

---

## 10. 权限模型

### 10.1 角色定义

| 角色 | 权限范围 |
|------|---------|
| `SRE` | 系统运维全部 + 告警中心 + 系统类工单 |
| `DBA` | 中间件监控 + 日志中心 + 数据库类工单 |
| `PlatformAdmin` | 应用配置 + 全部工单审批 + 全空间台账 |
| `SpaceOwner` | 本空间报告 + 本空间治愈 + 本空间工单发起 |
| `DataGovernance` | 一致性对账 + 生命周期 + 数据质量 |
| `SecurityEngineer` | 数据安全 + 安全告警 + 导出审批 |
| `Viewer` | 只读所有运维监控视图 |

### 10.2 空间级权限隔离

应用运维层接口默认按 `team_space_id` 隔离：
- `SpaceOwner` 只能操作自己负责的空间
- `PlatformAdmin` / `DataGovernance` 可跨空间
- 跨空间操作必须显式声明且记录审计

### 10.3 操作权限矩阵（节选）

| 操作 | SRE | DBA | PlatformAdmin | SpaceOwner | DataGovernance |
|------|-----|-----|--------------|-----------|---------------|
| 重启服务 | ✓ | ✗ | ✗ | ✗ | ✗ |
| 单文件重索引 | ✗ | ✗ | ✓ | ✓(本空间) | ✓ |
| 批量重解析 | ✗ | ✗ | ✓ | ✓(本空间,工单) | ✓(工单) |
| 配置变更 | ✗ | ✗ | ✓ | ✗ | ✗ |
| 空间销毁 | ✗ | ✗ | ✓ | ✓(发起) | ✗ |
| 清理孤儿 | ✗ | ✗ | ✓ | ✗ | ✓(工单) |

---

## 11. 实施路线与验收标准

### 11.1 三期实施路线

> 审查优化：工单能力前置到第 1 期，因 D3 治愈批量操作依赖工单审批；原第 3 期仅保留配置与安全深化。

| 期次 | 范围 | 关键交付 |
|------|------|---------|
| **第 1 期：监控+告警+工单底座** | 信息架构整合 + 系统运维层补全 + 告警中心 + 工单基础 | 运维门户、系统总览、服务健康增强、中间件监控、日志中心、告警事件墙（含治理降噪）、飞书通知闭环、**工单中心基础（CRUD+审批+执行）**、SOP 知识库 |
| **第 2 期：应用运维核心** | 数据空间应用运维 + 治愈闭环 | 空间台账、一致性对账、链路治愈（对接工单）、生命周期治理、空间报告、故障复盘 |
| **第 3 期：配置与安全深化** | 应用配置 + 数据安全 + 运维效能 | 应用配置运维中心（灰度）、数据安全运维、运维效能看板、全量优化上线 |

**依赖关系**：第 2 期 D3 治愈操作 → 依赖第 1 期工单中心；第 3 期 D5 灰度 → 依赖第 2 期 D1 空间台账。

### 11.2 验收标准

| 维度 | 标准 |
|------|------|
| 功能完整性 | 12 个产品模块全部上线，覆盖系统运维 6 个 + 应用运维 7 个 + 告警 + 工单 |
| 指标覆盖 | 复用 monitor-design.md 的 L1–L6 全部指标，无遗漏 |
| 闭环验证 | P0 告警 → 工单 → 操作 → 验证 全流程 ≤ 30min 跑通 |
| 数据一致性 | 对账任务覆盖 7 类，PG/MinIO/ES/Neo4j/Milvus 全覆盖 |
| 操作留痕 | 所有运维操作 100% 写入 `t_file_event` + 工单日志 |
| 权限隔离 | 空间级权限 100% 隔离，跨空间操作 100% 审计 |
| 性能 | 告警事件墙 P95 加载 ≤ 1s，对账明细查询 ≤ 2s |
| 文档 | 本方案 + API 文档 + SOP 手册齐备 |

### 11.3 质量评分对照（目标 ≥ 95）

| 评分维度 | 权重 | 目标分 | 评分依据 |
|---------|------|--------|---------|
| 体系完整性 | 20% | 19 | 系统运维+应用运维双层全覆盖，无功能缺口 |
| 边界清晰度 | 15% | 14 | 两层边界、职责、受众明确，无重叠 |
| 可落地性 | 20% | 19 | 数据模型、API、页面、权限均可直接研发 |
| 复用度 | 10% | 10 | 复用 monitor-design 指标、t_file_event、feishu-service |
| 闭环能力 | 15% | 14 | 监控→告警→工单→操作→验证→归档全链路 |
| 安全合规 | 10% | 10 | 权限隔离、审计留痕、高危操作保护 |
| 文档质量 | 10% | 9 | 结构清晰、图表完整、与现有资产映射 |
| **合计** | 100% | **95** | |

---

## 12. 与现有资产映射

| 现有资产 | 本方案复用方式 |
|---------|--------------|
| [monitor-design.md](./monitor-design.md) L1–L6 指标 | 系统层用 L2/L3，应用层用 L1/L4/L5/L6，告警规则基于 L5 |
| [ops-manual.md](./ops-manual.md) 系统运维手册 | 作为系统运维层 SOP 知识库底座，迁入 `t_ops_runbook` |
| `t_file_event` 事件流 | 扩展 `OPS_*` 事件类型，运维操作全程留痕 |
| `t_team_space` 表 | 补强 `lifecycle_status`、`health_score` 字段 |
| `t_alert_rule` / `t_alert_event` | 告警中心直接复用，新增应用运维类规则 |
| `t_slo_definition` / `t_slo_record` | 空间报告与告警燃烧率复用 |
| `/monitor` 监控看板 | 迁移至 `/ops/monitor`，作为应用运维监控视图 |
| `/admin/health` 健康检查 | 迁移至 `/ops/system/health`，增强 RED 指标与实例下钻 |
| `/admin/notifications` 通知中心 | 整合进告警中心，飞书卡片复用 |
| `/admin/models`、`/admin/yara-rules`、`/admin/config` | 整合进应用配置运维中心 |
| `feishu-service` | 飞书 IM 卡片 + 审批流 + 加急通知 |
| `common` 模块 `FileEventPublisher` | 扩展发布 `OPS_*` 事件 |
| Spring Boot Actuator + Micrometer | 系统层指标采集复用 |
| K8s/Istio 已有监控 | 系统总览与基础设施页直接接入 |

---

## 13. 补充设计（第二轮审查优化）

第二轮深度审查发现 17 项补充点，归类为 8 个主题，统一补充如下。

### 13.1 数据迁移与上线策略

| 项 | 策略 |
|----|------|
| 新表上线 | 9 张新表（t_space_member/t_space_quota_log/t_consistency_check_diff/t_heal_job/t_config_version/t_security_scan/t_export_request/t_space_report/t_report_subscription/t_alert_maintenance/t_ops_runbook）通过 Flyway 迁移脚本上线，每个表一个版本号 |
| t_file 字段回填 | `storage_tier` 默认 'hot'、`last_access_at` 回填为 `created_at`、`lifecycle_status` 默认 'active'、`health_score` 异步计算回填 |
| t_team_space 字段回填 | `lifecycle_status` 默认 'active'、`health_score` 异步计算、`cold_file_count`/`archived_bytes` 默认 0 |
| space_member 回填 | 从现有 t_team_space.owner_id + 现有成员关系一次性回填 |
| 迁移回滚 | 每个迁移脚本配套 down 脚本，灰度环境验证后生产执行 |
| 灰度上线 | 运维功能按 §11.1 三期上线，每期先灰度 1 个空间验证 |

### 13.2 API 版本与兼容

- 所有 `/api/ops/**` 接口加 `/v1` 前缀：`/api/ops/v1/data/**`
- Breaking change 走 `/v2`，`/v1` 保留 6 个月
- 前端 `utils/request.ts` 的 `ApiResponse` 现有 `{code, message, data}`，后端可扩展 `trace_id` 字段，前端忽略未声明字段不影响

### 13.3 国际化与移动端

| 项 | 策略 |
|----|------|
| 文案集中管理 | 状态枚举、错误码、角色名等文案集中到 `types/*Label` 映射（参考现有 `UserStatusLabel`、`SensitivityLabel`） |
| 错误码字典 | 复用 monitor-design 的错误码字典，前端展示统一从字典查 |
| 移动端适配 | 空间报告页、告警飞书卡片详情页做响应式（antd Grid + breakpoints）；操作台/对账等重操作页仅 PC |
| 飞书卡片 | 卡片内嵌 H5 详情页，移动端可读 |

### 13.4 成本核算

- 新增 `t_space_cost` 表记录空间存储成本（热/冷/归档单价 × 用量 × 天）
- D4 容量看板增加"成本趋势"图表
- D7 月报增加"本月存储成本"与"归档节省"
- 成本数据每日快照，支持按月结算

### 13.5 元监控（运维体系自身可用性）

| 监控对象 | 指标 | 告警 |
|---------|------|------|
| 对账任务 | 任务成功率、耗时 | 失败率 > 5% 告警 |
| 治愈任务 | 任务堆积数 | 排队 > 100 告警 |
| 工单服务 | 工单 API 可用性 | 不可用 P0 |
| 报告生成 | 生成失败率 | > 1% 告警 |
| 飞书推送 | 推送失败率 | > 2% 告警 |
| 配置中心 | 配置读取延迟 | > 100ms 告警 |

元监控指标纳入 S7 运维效能看板。

### 13.6 飞书推送治理

| 治理 | 策略 |
|------|------|
| 频率控制 | 同一用户单飞书群 5min 内最多 10 条，超限聚合为摘要 |
| 夜间免打扰 | 22:00–08:00 仅推 P0，P1/P2 延后到 08:00 摘要推送 |
| 分级路由 | P0 加急+电话，P1 加急，P2 IM，P3 摘要 |
| 降噪 | 复用 §6.6 告警治理，通知前先去重/聚合 |
| 失败重试 | 推送失败重试 3 次，仍失败落库待补发 |

### 13.7 配置中心与热加载

| 项 | 方案 |
|----|------|
| 配置中心选型 | 复用现有 `SystemConfig` + 新增 `t_config_version`，应用侧通过本地缓存 + 长轮询/定时拉取（5s）实现准实时 |
| 热加载 | 业务服务启动时加载配置到内存，后台线程每 5s 拉取版本号，版本变化时 reload |
| 灰度路由 | 配置版本带 `scope_space_ids`，服务端按 `team_space_id` 匹配走灰度版本 |
| 强一致场景 | 配置变更后通过 Redis Pub/Sub 主动通知各实例立即 reload（<1s 生效） |
| 不支持热加载 | 标记 `require_restart=true`，变更工单提示需重启 |

### 13.8 其他补充

| 项 | 说明 |
|----|------|
| 报告模板可配置 | 周报/月报模板存 `t_ops_runbook` 或独立模板表，支持变量插值（{space_name}/{health_score}） |
| 值班排班规则 | 支持轮换（按周/按人）、节假日表、调班申请、跨时区（按北京时间统一） |
| SOP 检索与关联 | SOP 知识库支持全文检索；告警事件按 `related_alert_codes` 自动推荐 SOP |
| 大空间性能 | 单空间 > 100 万文件：对账拆子任务并行；列表强制带筛选，禁止全量；健康分计算异步 + 缓存 5min |
| 审计日志保留 | `t_file_event` 保留 180 天后归档 MinIO；工单日志永久保留；导出审计永久 |
| 操作可回滚性边界 | 配置类可回滚（版本表）；删除类不可回滚（靠 30 天备份恢复）；治愈类可重试 |
| 配额实时性与并发 | 上传时实时扣减（Redis 原子计数器），定时（每 5min）与 PG 对账修正；并发上传用 Redis 原子操作避免超卖 |
| 告警确认 SLA | P0 5min 未确认自动升级（电话+上级）；P1 30min 未确认升级；P2 4h 未确认提醒 |

---

## 附录 A：双层运维对照速查

| 场景 | 系统运维层动作 | 应用运维层动作 |
|------|--------------|--------------|
| ES 集群 red | S1 告警 → 重启节点 / 扩分片 | D2 对账 → 重索引缺失文档 |
| 搜索 P95 飙升 | S2 查 ES 负载 → 扩容 | D7 通知空间负责人 + D5 调整索引策略 |
| 解析服务 OOM | S2 重启 parse-service | D3 批量重解析失败文件 |
| 空间配额将满 | S6 容量预测告警 | D1 配额预警 → D4 归档冷数据 / 扩容工单 |
| MinIO 磁盘满 | S3/S4 告警 → 扩容磁盘 | D4 冷热分层 + 孤儿清理 |
| 离职成员权限残留 | — | D6 权限回收巡检 |
| 标签规则升级 | — | D5 配置变更 → D3 批量重打标 |

## 附录 B：术语表

| 术语 | 含义 |
|------|------|
| USE | Utilization/Saturation/Errors，基础设施监控方法论 |
| RED | Rate/Errors/Duration，服务监控方法论 |
| SLO | Service Level Objective，服务等级目标 |
| 错误预算 | SLO 允许的不可用时间配额 |
| 燃烧率 | 实际错误率 / 错误预算，>14 表示快速消耗 |
| 孤儿文件 | MinIO 有对象但 PG 无记录 |
| trace 断链 | 文件链路事件缺失（如 UPLOAD 后无 INDEX 事件） |
| 冷热分层 | 按访问频率将文件在热存储/归档存储间迁移 |
| 治愈 | 对数据链路异常的修复操作（重索引/重解析等） |
