# 红方数据空间 · 监控指标体系与数据模型设计（最终版）

> 文档版本：v1.0
> 适用范围：红方文件汇聚管理系统「数据空间」
> 业务流程：文件上传 → 文件索引 → 文件解析 → 文件搜索
> 隔离模型：按团队空间（team_space）隔离文件数据

---

## 目录

1. [设计原则与监控分层](#1-设计原则与监控分层)
2. [统一维度字典](#2-统一维度字典)
3. [指标体系](#3-指标体系)
4. [错误码规范](#4-错误码规范)
5. [数据模型](#5-数据模型)
6. [数据汇总与保留策略](#6-数据汇总与保留策略)
7. [看板设计](#7-看板设计)
8. [采集与暴露规范](#8-采集与暴露规范)
9. [实施路线](#9-实施路线)

---

## 1. 设计原则与监控分层

### 1.1 设计原则

| 原则 | 说明 |
|------|------|
| 业务流程闭环 | 围绕 上传→索引→解析→搜索 四阶段，每阶段独立计量成功率/耗时/吞吐 |
| 团队空间隔离 | 所有指标默认带 `team_space_id` 维度，可单空间下钻、可全局聚合 |
| 四维监控 | 基础设施(USE) + 接口(RED) + 业务流程 + SLO |
| 高基数安全 | Prometheus 标签白名单，禁止 `file_id/user_id/query_text` 入标签 |
| 可低成本落地 | 复用 Spring Boot Actuator + Micrometer + Prometheus + ES + PostgreSQL |

### 1.2 监控分层

| 层级 | 名称 | 关注点 |
|------|------|--------|
| L1 | 业务流程指标 | 上传/索引/解析/搜索四阶段业务计量 |
| L2 | 接口与服务（RED） | Rate / Errors / Duration |
| L3 | 基础设施（USE） | Utilization / Saturation / Errors |
| L4 | 安全与合规 | 越权/敏感数据访问/审计 |
| L5 | SLO | 可用性、时延、错误预算 |
| L6 | 数据质量与容量成本 | 覆盖率、准确率、容量增长、成本 |

---

## 2. 统一维度字典

所有指标共享以下维度标签。

| 维度代码 | 中文名 | 说明 | 取值示例 |
|---------|--------|------|---------|
| `team_space_id` | 团队空间ID | 文件数据隔离主键 | 1001 |
| `team_space_code` | 团队空间编码 | 可读编码 | RED-A-01 |
| `tenant_id` | 租户ID | 多租户隔离 | 1 |
| `service` | 服务名 | 微服务标识 | upload-service |
| `stage` | 业务阶段 | UPLOAD/INDEX/PARSE/SEARCH | UPLOAD |
| `file_type` | 文件类型 | 扩展名分桶 | pdf/docx/zip/eml |
| `source_type` | 来源类型 | 1上传 2爬取 3导入 | 1 |
| `error_code` | 错误码 | 见 [§4 错误码规范](#4-错误码规范) | UPLOAD.QUOTA.EXCEED |
| `operator_id` | 操作人ID | 触发用户（不入Prom标签，入事件表） | 20012 |
| `query_type` | 查询类型 | keyword/advanced/semantic | keyword |
| `sensitive_level` | 敏感等级 | 1低 2中 3高 | 3 |
| `stat_period` | 统计周期 | HOUR/DAY | HOUR |
| `is_cross_team` | 是否跨团队 | 安全审计 | 0/1 |
| `ioc_type` | IOC类型 | IP/DOMAIN/HASH/URL | IP |
| `entity_type` | 实体类型 | PERSON/ORG/EMAIL | PERSON |

**Prometheus 标签白名单**（仅允许以下高基数安全的标签）：
`team_space_id`、`service`、`stage`、`file_type`、`source_type`、`error_code`、`query_type`、`sensitive_level`。

**禁止入 Prometheus 标签**：`file_id`、`operator_id`、`query_keyword`、`trace_id`（落入 `t_file_event` 事实表）。

---

## 3. 指标体系

### 3.1 L1 业务流程指标

#### 3.1.1 上传阶段（UPLOAD）

| 指标中文名 | 指标代码 | 类型 | 单位 | 维度 | 口径定义 | 采集点 |
|----------|---------|------|------|------|---------|--------|
| 上传文件数 | `file.upload.count` | Counter | 个 | team_space_id, source_type, file_type | 上传成功落库的文件总数 | FileService.upload 成功后 |
| 上传字节数 | `file.upload.bytes` | Counter | B | team_space_id, source_type | 上传文件大小累加 | 同上 |
| 上传请求数 | `file.upload.request.count` | Counter | 次 | team_space_id, status | 含失败的 HTTP 请求总数 | Controller 入口 |
| 上传耗时 | `file.upload.duration` | Timer | ms | team_space_id, file_type | 请求到落库+对象存储完成 | @Timed |
| 上传失败数 | `file.upload.fail.count` | Counter | 个 | team_space_id, error_code | 失败原因按错误码分桶 | 异常分支 |
| 秒传命中数 | `file.upload.dedup.hit` | Counter | 个 | team_space_id | MD5/SHA256 命中已有文件 | 秒传分支 |
| 在传并发数 | `file.upload.concurrent` | Gauge | 个 | team_space_id | 当前正在上传的并发数 | AtomicLong |
| 团队空间文件数 | `team.space.file.total` | Gauge | 个 | team_space_id | 团队空间文件总数 | 定时聚合 |
| 团队空间存储用量 | `team.space.storage.used` | Gauge | B | team_space_id | 已用存储字节 | SUM(file_size) |
| 团队空间配额使用率 | `team.space.quota.usage` | Gauge | % | team_space_id | used / quota × 100 | 派生 |
| 上传 QPS | `file.upload.qps` | Rate | 次/s | team_space_id | count / 60s | 派生 |

#### 3.1.2 索引阶段（INDEX）

| 指标中文名 | 指标代码 | 类型 | 单位 | 维度 | 口径定义 | 采集点 |
|----------|---------|------|------|------|---------|--------|
| 索引请求数 | `file.index.count` | Counter | 次 | team_space_id, file_type | 触发索引的总次数 | FileIndexService |
| 索引成功数 | `file.index.success.count` | Counter | 次 | team_space_id | 写入 ES 成功 | 成功分支 |
| 索引失败数 | `file.index.fail.count` | Counter | 次 | team_space_id, error_code | 按 ES_REJECTED/MAPPING_ERR/TIMEOUT 分桶 | 失败分支 |
| 索引耗时 | `file.index.duration` | Timer | ms | team_space_id, file_type | 单文件索引写入 ES 耗时 | @Timed |
| 索引积压数 | `file.index.lag` | Gauge | 个 | team_space_id | upload 成功但 indexStatus=0 的文件数 | 定时 COUNT |
| 索引可搜时延 | `file.index.freshness` | Timer | s | team_space_id | upload.success → index.success 间隔 | trace 串联 |
| ES 写入拒绝数 | `es.reject.count` | Counter | 次 | team_space_id | ES 429 拒绝 | ES 客户端拦截 |
| 索引成功率 | `file.index.success.rate` | Gauge | % | team_space_id | success / count × 100 | 派生 |

#### 3.1.3 解析阶段（PARSE）

| 指标中文名 | 指标代码 | 类型 | 单位 | 维度 | 口径定义 | 采集点 |
|----------|---------|------|------|------|---------|--------|
| 解析任务数 | `file.parse.count` | Counter | 个 | team_space_id, file_type | 提交解析的任务数 | FileParseService |
| 解析成功数 | `file.parse.success.count` | Counter | 个 | team_space_id, file_type | parseStatus=已解析 | 成功分支 |
| 解析失败数 | `file.parse.fail.count` | Counter | 个 | team_space_id, file_type, error_code | 按 CORRUPT/PASSWORD/OOM/TIMEOUT/UNSUPPORTED 分桶 | 失败分支 |
| 解析耗时 | `file.parse.duration` | Timer | ms | team_space_id, file_type | 单文件解析总耗时 | @Timed |
| 解析队列积压 | `file.parse.queue.lag` | Gauge | 个 | team_space_id | parseStatus=1 超过 N 分钟的文件数 | 定时扫描 |
| IOC 抽取数 | `file.parse.ioc.count` | Counter | 个 | team_space_id, ioc_type | 抽取的 IP/域名/Hash/URL 数 | 解析器 |
| 实体抽取数 | `file.parse.entity.count` | Counter | 个 | team_space_id, entity_type | 抽取的人名/组织/邮箱 | 解析器 |
| 解析重试次数 | `file.parse.retry.count` | Counter | 次 | team_space_id | 重试调度次数 | 重试器 |
| 解析成功率 | `file.parse.success.rate` | Gauge | % | team_space_id, file_type | success / count × 100 | 派生 |

#### 3.1.4 搜索阶段（SEARCH）

| 指标中文名 | 指标代码 | 类型 | 单位 | 维度 | 口径定义 | 采集点 |
|----------|---------|------|------|------|---------|--------|
| 搜索请求数 | `file.search.count` | Counter | 次 | team_space_id, query_type | 搜索请求总数 | FileSearchService |
| 搜索成功数 | `file.search.success.count` | Counter | 次 | team_space_id | 返回 200 的搜索 | 成功分支 |
| 搜索失败数 | `file.search.fail.count` | Counter | 次 | team_space_id, error_code | 按 ES_TIMEOUT/QUERY_ERR/PERMISSION 分桶 | 失败分支 |
| 搜索耗时 | `file.search.duration` | Timer | ms | team_space_id, query_type | 端到端搜索耗时 P50/P95/P99 | @Timed |
| 零结果查询数 | `file.search.zero_hit.count` | Counter | 次 | team_space_id, query_type | hits.total = 0 | 结果分支 |
| 单次结果数 | `file.search.result.size` | Summary | 个 | team_space_id | 单次返回 hits 分布 | 结果分支 |
| 搜索结果点击数 | `file.search.click.count` | Counter | 次 | team_space_id, query_type | 用户点击结果文件 | 前端埋点 |
| 热门查询词 | `file.search.top_query` | TopN | 次 | team_space_id | 查询词频次 TopN | 异步聚合 |
| 搜索无命中率 | `file.search.zero_hit.rate` | Gauge | % | team_space_id | zero_hit / count × 100 | 派生 |

### 3.2 L2 接口与服务（RED）

| 指标中文名 | 指标代码 | 说明 |
|----------|---------|------|
| HTTP 请求 | `http.server.requests` | QPS、错误率、P95/P99（按 service/endpoint/status） |
| 服务存活 | `service.<name>.up` | upload/parse/index/search/auth 存活 |
| 数据库连接池 | `db.pool.active` / `db.pool.idle` | HikariCP |
| ES 客户端 | `es.client.requests` | QPS、耗时、429 限流 |
| MinIO 读写 | `minio.object.put` / `minio.object.get` | 读写耗时、错误 |

### 3.3 L3 基础设施（USE）

| 指标中文名 | 指标代码 | 说明 |
|----------|---------|------|
| JVM CPU | `process.cpu.usage` | 进程 CPU 占用 |
| JVM 内存 | `jvm.memory.*` | 堆/非堆/已用/最大 |
| 磁盘用量 | `disk.usage` | MinIO 存储卷用量 |
| PostgreSQL | `pg.stat.*` | 复用 pg_stat_statements |
| Elasticsearch | `es.health` | 集群健康、分片状态 |
| Milvus | `milvus.collection.*` | 集合大小、查询延迟 |

### 3.4 L4 安全与合规

| 指标中文名 | 指标代码 | 类型 | 维度 | 口径定义 |
|----------|---------|------|------|---------|
| 登录失败数 | `auth.login.fail.count` | Counter | user_id, team_space_id | 登录失败次数 |
| 越权访问数 | `access.denied.count` | Counter | team_space_id, is_cross_team | 跨团队空间访问被拒 |
| 跨团队下载数 | `file.download.cross_team` | Counter | team_space_id | 跨团队下载（应=0） |
| 高敏感文件访问数 | `sensitive.file.access` | Counter | team_space_id, sensitive_level=3 | 高敏感文件访问审计 |
| 高敏感文件下载数 | `sensitive.file.download` | Counter | team_space_id, sensitive_level=3 | 高敏感文件下载 |

### 3.5 L5 SLO

| SLO 中文名 | SLO 代码 | 目标 | 计算口径 | 错误预算 |
|----------|---------|------|---------|---------|
| 上传可用性 | `slo.upload.availability` | 99.9% | 1 − fail/total | 0.1% |
| 索引可搜时延 P95 | `slo.index.freshness.p95` | ≤ 60s | index.freshness P95 | — |
| 解析成功率 | `slo.parse.success.rate` | 95% | success/total | 5% |
| 搜索 P95 | `slo.search.latency.p95` | ≤ 500ms | search.duration P95 | — |
| 搜索可用性 | `slo.search.availability` | 99.5% | 1 − fail/total | 0.5% |

### 3.6 L6 数据质量与容量成本（新增）

#### 3.6.1 数据质量指标

| 指标中文名 | 指标代码 | 类型 | 单位 | 维度 | 口径定义 |
|----------|---------|------|------|------|---------|
| 索引覆盖率 | `quality.index.coverage` | Gauge | % | team_space_id | indexStatus=2 文件数 / 文件总数 × 100 |
| 解析覆盖率 | `quality.parse.coverage` | Gauge | % | team_space_id, file_type | parseStatus=2 文件数 / 应解析文件数 × 100 |
| 解析失败重试率 | `quality.parse.retry.rate` | Gauge | % | team_space_id | retry.count / parse.count × 100 |
| IOC 召回率 | `quality.ioc.recall` | Gauge | % | team_space_id | 已抽取 IOC 文件数 / 含 IOC 文件总数 × 100（抽样评测） |
| 元数据完整率 | `quality.metadata.complete` | Gauge | % | team_space_id | 关键字段齐全的文件数 / 文件总数 × 100 |
| 哈希重复率 | `quality.dedup.ratio` | Gauge | % | team_space_id | dedup.hit / upload.count × 100 |

#### 3.6.2 容量与成本指标

| 指标中文名 | 指标代码 | 类型 | 单位 | 维度 | 口径定义 |
|----------|---------|------|------|------|---------|
| 存储增长率 | `capacity.storage.growth` | Gauge | % | team_space_id | 今日用量 / 昨日用量 − 1 |
| 文件数增长率 | `capacity.file.growth` | Gauge | % | team_space_id | 今日文件数 / 昨日 − 1 |
| 预计耗尽天数 | `capacity.quota.exhaust.days` | Gauge | 天 | team_space_id | (quota − used) / 近7d日均增长 |
| ES 索引体积 | `capacity.es.size` | Gauge | B | index_name | ES 索引占用磁盘 |
| 单文件平均解析成本 | `cost.parse.per_file` | Gauge | ms | team_space_id | parse.duration.avg |
| 单次搜索平均成本 | `cost.search.per_query` | Gauge | ms | team_space_id | search.duration.avg |

---

## 4. 错误码规范

### 4.1 编码规则

格式：`<STAGE>.<CATEGORY>.<SUB>`

- `STAGE`：UPLOAD / INDEX / PARSE / SEARCH / AUTH / COMMON
- `CATEGORY`：业务类别（大写）
- `SUB`：具体子类（大写）

### 4.2 错误码字典

| 错误码 | 中文名 | 阶段 | 触发条件 |
|--------|--------|------|---------|
| `UPLOAD.QUOTA.EXCEED` | 团队空间配额超限 | 上传 | storage 或 file 数超配额 |
| `UPLOAD.MIME.REJECT` | MIME 类型被拒 | 上传 | 文件类型不在白名单 |
| `UPLOAD.VIRUS.DETECT` | 病毒检测阳性 | 上传 | 杀毒检测命中 |
| `UPLOAD.HASH.DUP` | 哈希重复 | 上传 | 秒传命中（信息码，非失败） |
| `UPLOAD.STORAGE.ERR` | 对象存储异常 | 上传 | MinIO 写入失败 |
| `INDEX.ES.REJECTED` | ES 写入拒绝 | 索引 | ES 429 队列满 |
| `INDEX.MAPPING.ERR` | 映射错误 | 索引 | 字段类型不匹配 |
| `INDEX.TIMEOUT` | 索引超时 | 索引 | 写入 ES 超时 |
| `PARSE.CORRUPT` | 文件损坏 | 解析 | 文件无法打开 |
| `PARSE.PASSWORD` | 加密文件 | 解析 | 需密码未提供 |
| `PARSE.OOM` | 内存溢出 | 解析 | 解析器 OOM |
| `PARSE.TIMEOUT` | 解析超时 | 解析 | 解析超过时限 |
| `PARSE.UNSUPPORTED` | 类型不支持 | 解析 | 文件类型无解析器 |
| `SEARCH.ES.TIMEOUT` | 搜索超时 | 搜索 | ES 查询超时 |
| `SEARCH.QUERY.ERR` | 查询语法错误 | 搜索 | DSL 构造失败 |
| `SEARCH.PERMISSION` | 无权限 | 搜索 | 跨团队空间被拒 |
| `AUTH.LOGIN.FAIL` | 登录失败 | 认证 | 凭证错误 |
| `AUTH.TOKEN.EXPIRE` | Token 过期 | 认证 | JWT 过期 |

---

## 5. 数据模型

### 5.1 表清单与职责

| 表名 | 中文名 | 职责 | 数据量级 | 存储 | 保留期 |
|------|--------|------|---------|------|--------|
| `t_team_space` | 团队空间表 | 空间元数据与配额 | 10² | PG | 永久 |
| `t_file`（补强） | 文件主表 | 业务实体，加 team_space_id | 10⁷ | PG | 永久 |
| `t_file_event` | 文件事件事实表 | 全链路事件流 | 10⁸/年 | PG 按月分区 | hot 90d / cold 1y |
| `t_metric_hourly` | 小时指标聚合表 | 看板高频查询 | 10⁶/年 | PG | 90d |
| `t_metric_daily` | 日指标聚合表 | 看板低频/对比 | 10⁵/年 | PG | 2y |
| `t_topn_record` | TopN 记录表 | 热门查询等 | 10⁶/年 | PG | 1y |
| `t_dim_error_code` | 错误码维度表 | 错误码元数据 | 10² | PG | 永久 |
| `t_dim_file_type` | 文件类型维度表 | 类型元数据 | 10² | PG | 永久 |
| `t_metric_dict` | 指标元数据表 | 指标字典 | 10² | PG | 永久 |
| `t_alert_rule` | 告警规则表 | 规则配置 | 10² | PG | 永久 |
| `t_alert_event` | 告警事件表 | 告警历史 | 10⁵/年 | PG | 1y |
| `t_slo_definition` | SLO 定义表 | SLO 配置 | 10¹ | PG | 永久 |
| `t_slo_record` | SLO 记录表 | SLO 燃烧率每日快照 | 10³/年 | PG | 2y |
| Prometheus TSDB | 时序指标 | Counter/Timer/Gauge 原始 | — | Prometheus | 15d |

### 5.2 详细表结构

#### 5.2.1 t_team_space 团队空间表

```sql
CREATE TABLE t_team_space (
  id              BIGINT       PRIMARY KEY COMMENT '团队空间ID',
  tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
  space_code      VARCHAR(64)  NOT NULL COMMENT '空间编码',
  space_name      VARCHAR(128) NOT NULL COMMENT '空间名称',
  storage_quota   BIGINT       COMMENT '存储配额(字节);NULL不限',
  file_quota      INT          COMMENT '文件数配额;NULL不限',
  status          SMALLINT     DEFAULT 1 COMMENT '状态:0禁用 1启用',
  owner_id        BIGINT       COMMENT '空间负责人ID',
  created_at      TIMESTAMP    DEFAULT NOW() COMMENT '创建时间',
  updated_at      TIMESTAMP    DEFAULT NOW() COMMENT '更新时间',
  UNIQUE(tenant_id, space_code)
);
```

#### 5.2.2 t_file 文件主表（补强字段）

```sql
ALTER TABLE t_file ADD COLUMN team_space_id BIGINT NOT NULL COMMENT '团队空间ID';
ALTER TABLE t_file ADD COLUMN trace_id VARCHAR(64) COMMENT '链路追踪ID';
ALTER TABLE t_file ADD INDEX idx_file_space_status (team_space_id, parse_status, index_status);
```

#### 5.2.3 t_file_event 文件事件事实表（核心）

```sql
CREATE TABLE t_file_event (
  id              BIGINT       NOT NULL COMMENT '事件ID',
  trace_id        VARCHAR(64)  NOT NULL COMMENT '链路追踪ID',
  team_space_id   BIGINT       NOT NULL COMMENT '团队空间ID',
  file_id         BIGINT       NOT NULL COMMENT '文件ID',
  stage           VARCHAR(16)  NOT NULL COMMENT '业务阶段:UPLOAD/INDEX/PARSE/SEARCH',
  event_type      VARCHAR(16)  NOT NULL COMMENT '事件类型:START/SUCCESS/FAIL',
  duration_ms     INT          COMMENT '耗时(毫秒)',
  file_size       BIGINT       COMMENT '文件大小(字节)',
  file_type       VARCHAR(32)  COMMENT '文件类型',
  source_type     SMALLINT     COMMENT '来源类型:1上传 2爬取 3导入',
  operator_id     BIGINT       COMMENT '操作人ID',
  error_code      VARCHAR(64)  COMMENT '错误码',
  error_msg       TEXT         COMMENT '错误信息',
  query_type      VARCHAR(16)  COMMENT '查询类型(SEARCH阶段)',
  query_keyword   VARCHAR(256) COMMENT '查询关键词(脱敏后,SEARCH阶段)',
  result_count    INT          COMMENT '结果数(SEARCH阶段)',
  ioc_count       INT          COMMENT 'IOC抽取数(PARSE阶段)',
  meta            JSONB        COMMENT '扩展元数据',
  created_at      TIMESTAMP    DEFAULT NOW() COMMENT '事件时间',
  PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_event_space_stage_time ON t_file_event(team_space_id, stage, created_at);
CREATE INDEX idx_event_trace ON t_file_event(trace_id);
CREATE INDEX idx_event_file ON t_file_event(file_id);
```

#### 5.2.4 t_metric_hourly 小时指标聚合表

```sql
CREATE TABLE t_metric_hourly (
  id              BIGINT       PRIMARY KEY COMMENT '聚合ID',
  stat_time       TIMESTAMP    NOT NULL COMMENT '统计小时(整点)',
  team_space_id   BIGINT       NOT NULL COMMENT '团队空间ID',
  stage           VARCHAR(16)  NOT NULL COMMENT '业务阶段',
  metric_code     VARCHAR(64)  NOT NULL COMMENT '指标代码',
  total_count     BIGINT       DEFAULT 0 COMMENT '总数',
  success_count   BIGINT       DEFAULT 0 COMMENT '成功数',
  fail_count      BIGINT       DEFAULT 0 COMMENT '失败数',
  bytes_total     BIGINT       DEFAULT 0 COMMENT '字节数累加',
  duration_p50    INT          COMMENT '耗时P50(毫秒)',
  duration_p95    INT          COMMENT '耗时P95(毫秒)',
  duration_p99    INT          COMMENT '耗时P99(毫秒)',
  duration_avg    INT          COMMENT '平均耗时(毫秒)',
  fail_top_code   VARCHAR(64)  COMMENT '失败Top错误码',
  dim_file_type   VARCHAR(32)  COMMENT '文件类型维度',
  dim_query_type  VARCHAR(16)  COMMENT '查询类型维度',
  UNIQUE(stat_time, team_space_id, stage, metric_code, dim_file_type, dim_query_type)
);
CREATE INDEX idx_hourly_space_time ON t_metric_hourly(team_space_id, stat_time);
```

#### 5.2.5 t_metric_daily 日指标聚合表

```sql
CREATE TABLE t_metric_daily (
  id              BIGINT       PRIMARY KEY COMMENT '聚合ID',
  stat_date       DATE         NOT NULL COMMENT '统计日期',
  team_space_id   BIGINT       NOT NULL COMMENT '团队空间ID',
  stage           VARCHAR(16)  NOT NULL COMMENT '业务阶段',
  metric_code     VARCHAR(64)  NOT NULL COMMENT '指标代码',
  total_count     BIGINT       DEFAULT 0 COMMENT '总数',
  success_count   BIGINT       DEFAULT 0 COMMENT '成功数',
  fail_count      BIGINT       DEFAULT 0 COMMENT '失败数',
  bytes_total     BIGINT       DEFAULT 0 COMMENT '字节数累加',
  duration_p50    INT          COMMENT '耗时P50(毫秒)',
  duration_p95    INT          COMMENT '耗时P95(毫秒)',
  duration_p99    INT          COMMENT '耗时P99(毫秒)',
  success_rate    DECIMAL(5,2) COMMENT '成功率(%)',
  fail_top_code   VARCHAR(64)  COMMENT '失败Top错误码',
  UNIQUE(stat_date, team_space_id, stage, metric_code)
);
```

#### 5.2.6 t_topn_record TopN 记录表

```sql
CREATE TABLE t_topn_record (
  id              BIGINT       PRIMARY KEY COMMENT '记录ID',
  stat_date       DATE         NOT NULL COMMENT '统计日期',
  team_space_id   BIGINT       NOT NULL COMMENT '团队空间ID',
  topn_type       VARCHAR(32)  NOT NULL COMMENT 'TopN类型:HOT_QUERY/ZERO_HIT_QUERY/FAIL_FILE',
  rank_no         INT          NOT NULL COMMENT '排名',
  item_key        VARCHAR(512) NOT NULL COMMENT '项标识(如查询词)',
  item_count      BIGINT       NOT NULL COMMENT '出现次数',
  extra           JSONB        COMMENT '附加信息',
  UNIQUE(stat_date, team_space_id, topn_type, rank_no)
);
```

#### 5.2.7 t_dim_error_code 错误码维度表

```sql
CREATE TABLE t_dim_error_code (
  error_code      VARCHAR(64)  PRIMARY KEY COMMENT '错误码',
  error_name      VARCHAR(128) NOT NULL COMMENT '错误名称',
  stage           VARCHAR(16)  NOT NULL COMMENT '所属阶段',
  category        VARCHAR(32)  COMMENT '错误类别',
  severity        SMALLINT     COMMENT '严重级别:1致命 2一般 3提示',
  suggestion      TEXT         COMMENT '处理建议',
  enabled         SMALLINT     DEFAULT 1 COMMENT '启用:0否 1是'
);
```

#### 5.2.8 t_dim_file_type 文件类型维度表

```sql
CREATE TABLE t_dim_file_type (
  file_type       VARCHAR(32)  PRIMARY KEY COMMENT '文件类型',
  type_name       VARCHAR(64)  NOT NULL COMMENT '类型中文名',
  category        VARCHAR(32)  COMMENT '分类:DOCUMENT/IMAGE/VIDEO/AUDIO/ARCHIVE/CODE/OTHER',
  parse_supported SMALLINT     DEFAULT 1 COMMENT '是否支持解析:0否 1是',
  icon            VARCHAR(64)  COMMENT '图标标识'
);
```

#### 5.2.9 t_metric_dict 指标元数据表

```sql
CREATE TABLE t_metric_dict (
  metric_code     VARCHAR(64)  PRIMARY KEY COMMENT '指标代码',
  metric_name     VARCHAR(128) NOT NULL COMMENT '指标中文名',
  metric_type     VARCHAR(16)  NOT NULL COMMENT '类型:COUNTER/GAUGE/TIMER/TOPN',
  unit            VARCHAR(16)  COMMENT '单位',
  layer           VARCHAR(16)  COMMENT '所属层:L1业务/L2接口/L3基础/L4安全/L5SLO/L6质量',
  stage           VARCHAR(16)  COMMENT '业务阶段',
  description     TEXT         COMMENT '口径定义',
  dims            VARCHAR(256) COMMENT '允许的维度列表(逗号分隔)',
  enabled         SMALLINT     DEFAULT 1 COMMENT '启用:0否 1是'
);
```

#### 5.2.10 t_alert_rule 告警规则表

```sql
CREATE TABLE t_alert_rule (
  id              BIGINT       PRIMARY KEY COMMENT '规则ID',
  rule_name       VARCHAR(128) NOT NULL COMMENT '规则名称',
  rule_code       VARCHAR(64)  NOT NULL COMMENT '规则编码',
  team_space_id   BIGINT       COMMENT '团队空间ID;NULL=全局',
  stage           VARCHAR(16)  COMMENT '业务阶段',
  metric_expr     TEXT         NOT NULL COMMENT '指标表达式(PromQL/SQL)',
  condition_expr  VARCHAR(128) NOT NULL COMMENT '触发条件(如 >0.05)',
  window_min      INT          DEFAULT 5 COMMENT '时间窗口(分钟)',
  severity        SMALLINT     NOT NULL COMMENT '严重级别:1 P0 2 P1 3 P2',
  notify_channels VARCHAR(256) COMMENT '通知渠道(飞书webhook等)',
  enabled         SMALLINT     DEFAULT 1 COMMENT '启用:0否 1是',
  created_at      TIMESTAMP    DEFAULT NOW() COMMENT '创建时间'
);
```

#### 5.2.11 t_alert_event 告警事件表

```sql
CREATE TABLE t_alert_event (
  id              BIGINT       PRIMARY KEY COMMENT '事件ID',
  rule_id         BIGINT       NOT NULL COMMENT '规则ID',
  team_space_id   BIGINT       COMMENT '团队空间ID',
  severity        SMALLINT     NOT NULL COMMENT '严重级别',
  fired_at        TIMESTAMP    NOT NULL COMMENT '触发时间',
  resolved_at     TIMESTAMP    COMMENT '恢复时间',
  trigger_value   VARCHAR(128) COMMENT '触发时实际值',
  context         JSONB        COMMENT '上下文(如错误样本)',
  status          SMALLINT     NOT NULL COMMENT '状态:0触发中 1已恢复 2已忽略',
  notify_status   SMALLINT     DEFAULT 0 COMMENT '通知状态:0未发 1已发 2失败'
);
CREATE INDEX idx_alert_event_status ON t_alert_event(status, fired_at);
```

#### 5.2.12 t_slo_definition 与 t_slo_record

```sql
CREATE TABLE t_slo_definition (
  id              BIGINT       PRIMARY KEY COMMENT 'SLO ID',
  slo_name        VARCHAR(128) NOT NULL COMMENT 'SLO名称',
  slo_code        VARCHAR(64)  NOT NULL COMMENT 'SLO编码',
  stage           VARCHAR(16)  COMMENT '业务阶段',
  target_value    DECIMAL(10,2) NOT NULL COMMENT '目标值',
  target_unit     VARCHAR(16)  NOT NULL COMMENT '目标单位:%/ms/s',
  calc_expr       TEXT         NOT NULL COMMENT '计算表达式',
  error_budget    DECIMAL(5,2) COMMENT '错误预算(%)',
  window_days     INT          DEFAULT 30 COMMENT '滚动窗口(天)',
  enabled         SMALLINT     DEFAULT 1 COMMENT '启用:0否 1是'
);

CREATE TABLE t_slo_record (
  id              BIGINT       PRIMARY KEY COMMENT '记录ID',
  stat_date       DATE         NOT NULL COMMENT '统计日期',
  slo_id          BIGINT       NOT NULL COMMENT 'SLO ID',
  team_space_id   BIGINT       NOT NULL COMMENT '团队空间ID',
  actual_value    DECIMAL(10,2) COMMENT '实际值',
  error_budget_remaining DECIMAL(5,2) COMMENT '剩余错误预算(%)',
  burn_rate_2h    DECIMAL(8,2) COMMENT '2小时燃烧率',
  burn_rate_6h    DECIMAL(8,2) COMMENT '6小时燃烧率',
  status          SMALLINT     NOT NULL COMMENT '状态:0正常 1告警 2违约',
  UNIQUE(stat_date, slo_id, team_space_id)
);
```

---

## 6. 数据汇总与保留策略

### 6.1 ETL 汇总拓扑

```
原始层   t_file_event (实时)  +  Prometheus (15s)
            │
            ▼  定时任务(整点 +5min)
聚合层   t_metric_hourly  ← 按小时聚合 COUNT/SUM/分位数
            │
            ▼  定时任务(每日 00:30)
汇总层   t_metric_daily   ← 按天汇总 + 计算成功率
            │
            ▼  TopN 算子
         t_topn_record    ← 热门查询/零命中/失败TopN
            │
            ▼  SLO 引擎
         t_slo_record     ← 燃烧率 + 错误预算
```

### 6.2 聚合规则

| 字段 | 计算方式 |
|------|---------|
| `success_count` | COUNT(event_type='SUCCESS') |
| `fail_count` | COUNT(event_type='FAIL') |
| `duration_p95` | 由 Prometheus `histogram_quantile(0.95, …)` 拉取 |
| `success_rate` | success_count / total_count × 100 |
| `fail_top_code` | GROUP BY error_code ORDER BY count DESC LIMIT 1 |
| `burn_rate_2h` | 实际错误率 / 错误预算（2h 窗口） |

### 6.3 保留策略

| 数据 | Hot | Warm | Cold | 归档 |
|------|-----|------|------|------|
| Prometheus | 15d | — | — | — |
| t_file_event | 90d (PG) | — | 1y (归档至对象存储) | 1y+ |
| t_metric_hourly | 90d | — | — | — |
| t_metric_daily | 2y | — | — | — |
| t_alert_event | 1y | — | — | — |
| t_slo_record | 2y | — | — | — |

---

## 7. 看板设计

### 7.1 看板矩阵

| 看板名 | 受众 | 刷新 | 数据源 |
|--------|------|------|--------|
| 业务总览看板 | 全员 | 1min | Prometheus + PG |
| 团队空间详情看板 | 空间负责人 | 1min | PG |
| 业务链路漏斗看板 | 研发/运维 | 1min | PG |
| 搜索体验看板 | 产品/搜索优化 | 5min | PG + Prometheus |
| 安全合规看板 | 安全/审计 | 5min | PG |
| SLO 监控看板 | SRE/负责人 | 5min | PG |
| 告警事件看板 | 运维 | 30s | PG |
| 数据质量看板 | 数据治理 | 1h | PG |
| 容量成本看板 | 运维/管理者 | 1h | PG |

### 7.2 全局筛选器

所有看板共享：
- 团队空间下拉（多选，默认全部）
- 时间范围（1h/6h/24h/7d/30d 自定义）
- 业务阶段筛选（UPLOAD/INDEX/PARSE/SEARCH 多选）
- 自动刷新开关（30s/1min/5min）

### 7.3 业务总览看板

| 序号 | 图表标题 | 图形 | 指标 | 维度/筛选 |
|------|---------|------|------|----------|
| 1 | 全局 KPI 卡片 | Stat 大数字 | 上传文件数 / 总存储 / 在线空间数 / 今日搜索数 | 时间筛选(今日) |
| 2 | 四阶段成功率趋势 | 时序折线图 | success_rate | X:时间(1h粒度) Y:% 分色:stage |
| 3 | 四阶段耗时 P95 趋势 | 时序折线图 | duration_p95 | X:时间 Y:ms 分色:stage |
| 4 | 团队空间存储用量排行 | 水平条形图 | storage_used | X:team_space Y:字节 Top10 |
| 5 | 各文件类型分布 | 环形图 | upload count | file_type |
| 6 | 业务链路漏斗 | 漏斗图 | 上传→索引→解析 各阶段 success_count | stage |

**下钻路径**：图表 4 行点击 → 跳转「团队空间详情看板」并锁定该空间。

### 7.4 团队空间详情看板

| 序号 | 图表标题 | 图形 | 指标 | 维度/筛选 |
|------|---------|------|------|----------|
| 1 | 空间概览卡片 | Stat | 文件数 / 存储用量 / 配额使用率 / 今日上传 | team_space_id 筛选 |
| 2 | 配额使用率仪表盘 | Gauge 仪表盘 | quota_usage % | 0-100% 阈值色 |
| 3 | 上传趋势 | 时序面积图 | upload count + bytes | X:24h/7d |
| 4 | 索引积压趋势 | 时序折线 | index.lag | X:近1h |
| 5 | 解析成功率按文件类型 | 堆叠柱状 | success/fail | X:file_type Y:count |
| 6 | 失败原因 Top5 | 饼图 | fail count | error_code |
| 7 | 搜索 P95 与零命中率 | 双轴折线 | duration_p95 / zero_hit_rate | X:时间 |
| 8 | 最近 20 条事件 | 表格 | trace_id/stage/status/duration | 倒序 |

**下钻路径**：图表 8 行点击 → 跳转「链路追踪详情」展开该 trace 全部事件。

### 7.5 业务链路漏斗看板

| 序号 | 图表标题 | 图形 | 指标 | 维度 |
|------|---------|------|------|------|
| 1 | 上传→索引→解析 漏斗 | 漏斗图 | success_count | stage |
| 2 | 各阶段失败率对比 | 分组柱状 | fail_rate | X:stage 分组:team_space |
| 3 | 端到端时延瀑布 | 瀑布图 | duration_avg | stage 顺序 |
| 4 | 索引可搜时延 P95 | 时序折线 | index.freshness P95 | X:时间 |
| 5 | 解析队列积压 Top 空间 | 表格 | parse.queue.lag | team_space 排序 |
| 6 | 链路追踪详情 | 表格 | trace_id 串联事件 | trace_id 筛选 |

> 说明：搜索阶段不纳入漏斗（搜索是消费侧，非文件流转下游），漏斗仅含 上传→索引→解析。

### 7.6 搜索体验看板

| 序号 | 图表标题 | 图形 | 指标 | 维度 |
|------|---------|------|------|------|
| 1 | 搜索 QPS 趋势 | 时序折线 | search.count rate | X:1min |
| 2 | 搜索耗时分位数 | 多线折线 | P50/P95/P99 | X:时间 |
| 3 | 零命中率趋势 | 时序面积 | zero_hit_rate | X:1h |
| 4 | 热门查询词 Top20 | 水平条形 | query count | item_key |
| 5 | 零命中查询词 Top20 | 水平条形 | zero_hit count | item_key |
| 6 | 结果数分布 | 直方图 | result.size 分桶 | 0/1-10/11-50/50+ |
| 7 | 搜索结果点击率 | 折线 | click / search | X:时间 |
| 8 | 查询类型分布 | 环形 | count | query_type |

### 7.7 安全合规看板

| 序号 | 图表标题 | 图形 | 指标 | 维度 |
|------|---------|------|------|------|
| 1 | 越权访问趋势 | 时序柱状 | access.denied count | X:1h |
| 2 | 跨团队下载告警 | 表格 | cross_team download | 时间/操作人 |
| 3 | 高敏感文件访问排行 | 水平条形 | sensitive.access | file Top10 |
| 4 | 登录失败 Top 用户 | 表格 | login.fail | user Top10 |
| 5 | 敏感等级分布 | 堆叠面积 | file count | sensitive_level |
| 6 | 安全事件时间线 | 时间线 | alert_event(severity) | 时间 |

### 7.8 SLO 监控看板

| 序号 | 图表标题 | 图形 | 指标 | 维度 |
|------|---------|------|------|------|
| 1 | SLO 达标状态卡片 | Stat 着色 | actual vs target | slo_code |
| 2 | 错误预算剩余 | Gauge | error_budget_remaining | 0-100% |
| 3 | 燃烧率多窗口 | 分组柱状 | burn_2h / burn_6h | X:slo |
| 4 | SLO 实际值趋势 | 时序折线 | actual_value + target 参考线 | X:30d |
| 5 | SLO 违约事件 | 表格 | status=2 | 时间倒序 |

### 7.9 数据质量看板

| 序号 | 图表标题 | 图形 | 指标 | 维度 |
|------|---------|------|------|------|
| 1 | 索引覆盖率 | Gauge | quality.index.coverage | team_space |
| 2 | 解析覆盖率按类型 | 分组柱状 | quality.parse.coverage | X:file_type |
| 3 | IOC 召回率趋势 | 折线 | quality.ioc.recall | X:30d |
| 4 | 元数据完整率 | Gauge | quality.metadata.complete | team_space |
| 5 | 哈希重复率 | 折线 | quality.dedup.ratio | X:30d |
| 6 | 解析失败重试率 | 折线 | quality.parse.retry.rate | X:7d |

### 7.10 容量成本看板

| 序号 | 图表标题 | 图形 | 指标 | 维度 |
|------|---------|------|------|------|
| 1 | 存储用量趋势 | 时序面积 | storage.used | X:30d |
| 2 | 存储增长率 | 折线 | capacity.storage.growth | X:30d |
| 3 | 预计耗尽天数排行 | 水平条形 | exhaust.days | team_space |
| 4 | ES 索引体积 | 饼图 | capacity.es.size | index_name |
| 5 | 单文件平均解析成本 | 折线 | cost.parse.per_file | X:7d |
| 6 | 单次搜索平均成本 | 折线 | cost.search.per_query | X:7d |

### 7.11 图形选型规范

| 数据特征 | 推荐图形 |
|---------|---------|
| 时间趋势 | 时序折线/面积图 |
| 占比构成 | 环形图/饼图 |
| 排名对比 | 水平条形图 |
| 阈值监控 | Gauge 仪表盘 |
| 流程转化 | 漏斗图 |
| 多维对比 | 堆叠/分组柱状 |
| 明细查询 | 表格 |
| 分布统计 | 直方图 |
| 事件序列 | 时间线 |

---

## 8. 采集与暴露规范

### 8.1 暴露端点

每个微服务暴露 `/actuator/prometheus`，集成 `micrometer-registry-prometheus`。

### 8.2 埋点规范

- 业务事件埋点统一走 `common` 模块 `FileEventPublisher`，发布至 `t_file_event`（异步）。
- 接口耗时统一用 `@Timed` 注解。
- 关键失败分支显式调用 `Counter.increment()` 并附 `error_code` 标签。
- 链路串联：上传成功后生成 `trace_id` 并写入 `t_file.trace_id`，后续索引/解析复用。

### 8.3 标签白名单（再次强调）

允许：`team_space_id, service, stage, file_type, source_type, error_code, query_type, sensitive_level`

禁止：`file_id, operator_id, query_keyword, trace_id`（落入事实表）

---

## 9. 实施路线

| 阶段 | 内容 | 产出 |
|------|------|------|
| 阶段1 | 数据模型落地：建表 + `t_file` 补强 + 维度表初始化 | DDL 脚本 |
| 阶段2 | 监控埋点：`common` 模块 `FileEventPublisher` + 各服务 Micrometer | 代码改造 |
| 阶段3 | ETL 聚合任务：小时/日聚合 + TopN + SLO 引擎 | 定时任务 |
| 阶段4 | 看板搭建：Grafana + 业务看板（前端 Monitor 页面） | 看板 |
| 阶段5 | 告警接入：Alertmanager → 飞书 webhook | 告警闭环 |
| 阶段6 | 验收与调优：SLO 校验、看板评审、性能压测 | 验收报告 |

---

## 附录 A：与现有系统映射

| 设计项 | 落点 |
|--------|------|
| 团队空间隔离 | `t_team_space` + `FileEntity` 改继承 `BaseTenantEntity` + `MyBatisPlusConfig` 租户插件 |
| 指标暴露 | 各服务 `pom.xml` 加 `micrometer-registry-prometheus`，`application.yml` 暴露 actuator |
| 业务埋点 | `common` 模块新增 `FileEventPublisher`，关键方法 `@Timed` + 发布事件 |
| 事件存储 | `t_file_event` 表 + Mapper；高频写可先入 Kafka 再批量落库 |
| 看板 | Grafana + 前端 `Monitor` 页面（ECharts） |
| 告警 | Alertmanager → 飞书 webhook（复用 `feishu-service` 已有 IM 卡片能力） |
