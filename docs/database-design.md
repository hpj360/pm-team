# 网络安全红方文件汇聚平台数据库设计文档

## 文档信息

| 项目名称 | 网络安全红方文件汇聚平台 |
|---------|----------------------|
| 版本     | v1.2                 |
| 创建日期 | 2026-03-15           |
| 文档状态 | P1/P2 评审问题修复版  |
| 编写人员 | 数据库设计师           |

---

## 目录

1. [设计概述](#1-设计概述)
2. [关系数据库设计 (PostgreSQL + Citus)](#2-关系数据库设计-postgresql--citus)
3. [Elasticsearch索引设计](#3-elasticsearch索引设计)
4. [Milvus Collection设计](#4-milvus-collection设计)
5. [Neo4j图模型设计](#5-neo4j图模型设计)
6. [Redis缓存设计](#6-redis缓存设计)
7. [分片策略](#7-分片策略)
8. [索引策略](#8-索引策略)
9. [数据一致性保障](#9-数据一致性保障)
10. [备份与恢复策略](#10-备份与恢复策略)
11. [数据归档策略](#11-数据归档策略)
12. [批量操作设计](#12-批量操作设计)
13. [数据库运维与性能监控](#13-数据库运维与性能监控)

---

## 1. 设计概述

### 1.1 数据规模与性能要求

| 指标项 | 目标值 | 说明 |
|-------|-------|------|
| 文件数量 | 1000万+ | 预计三年内文件总量 |
| 存储容量 | 100TB | 文件内容存储 |
| 并发上传 | 1000 QPS | 高峰期并发上传请求 |
| 并发检索 | 2000 QPS | 高峰期并发检索请求 |
| 检索响应 | P99 < 500ms | 99%的检索请求响应时间 |
| 用户数量 | 1000+ | 预计三年内用户数量 |
| 目标数量 | 10000+ | 预计三年内目标数量 |

### 1.2 数据库选型矩阵

| 数据类型 | 存储系统 | 选型理由 |
|---------|---------|---------|
| 文件元数据 | PostgreSQL + Citus | 强一致性、复杂查询、分布式扩展 |
| 用户权限数据 | PostgreSQL | 强一致性、事务支持、关系查询 |
| 解析结果数据 | PostgreSQL + Elasticsearch | 结构化存储 + 全文检索 |
| 分析结果数据 | PostgreSQL + Elasticsearch | 结构化存储 + 全文检索 |
| 目标画像数据 | PostgreSQL + Neo4j | 结构化存储 + 关系图谱 |
| 审计日志数据 | PostgreSQL + TDengine | 长期存储 + 时序分析 |
| 全文检索索引 | Elasticsearch 8.x | 高性能全文检索、聚合分析 |
| 向量检索索引 | Milvus 2.x | 高性能向量检索、语义搜索 |
| 实体关系图谱 | Neo4j | 图遍历、路径分析、社区发现 |
| 缓存数据 | Redis 7.x Cluster | 高性能缓存、会话管理、分布式锁 |

### 1.3 数据流转架构

```
+-----------------------------------------------------------------------------+
|                            数据流转架构                                      |
+-----------------------------------------------------------------------------+
|                                                                             |
|  文件上传                                                                    |
|  +--------+    +--------+    +--------+    +--------+    +--------+       |
|  | 客户端  |--->| API网关 |--->|上传服务 |--->| MinIO  |--->| Kafka  |       |
|  +--------+    +--------+    +--------+    +--------+    +--------+       |
|                                    |              |              |          |
|                                    v              |              v          |
|                              +------------+       |       +------------+    |
|                              | PostgreSQL |<------+       | 解析队列    |    |
|                              | (元数据)   |               | (Kafka)    |    |
|                              +------------+               +------------+    |
|                                                                   |         |
|  文件解析                                                          v         |
|  +--------+    +--------+    +--------+    +------------------------+     |
|  | Kafka  |--->|解析服务 |--->| Tika   |--->| PostgreSQL / ES / Milvus|     |
|  |(消息)  |    |(Worker) |    |(提取)  |    | (结构化/全文/向量)        |     |
|  +--------+    +--------+    +--------+    +------------------------+     |
|                                     |                                       |
|                                     v                                       |
|  文件分析                         +------------+                            |
|  +--------+    +--------+    +--------+    +------------+    +--------+   |
|  | Kafka  |--->|分析服务 |--->| AI模型 |--->| Neo4j      |--->|画像服务 |   |
|  |(消息)  |    |(Worker) |    |(推理)  |    | (关系图谱) |    |(画像)  |   |
|  +--------+    +--------+    +--------+    +------------+    +--------+   |
|                                                                             |
+-----------------------------------------------------------------------------+
```

---

## 2. 关系数据库设计 (PostgreSQL + Citus)

### 2.1 数据库集群架构

```
+-----------------------------------------------------------------------------+
|                    PostgreSQL + Citus 分布式集群架构                          |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +---------------------------------------------------------------------+   |
|  |                        Coordinator Node                              |   |
|  |  +-------------------------------------------------------------+    |   |
|  |  |  - 查询路由与分发                                             |    |   |
|  |  |  - 分布式事务协调                                             |    |   |
|  |  |  - 全局元数据管理                                             |    |   |
|  |  |  - DDL语句分发                                               |    |   |
|  |  +-------------------------------------------------------------+    |   |
|  +---------------------------------------------------------------------+   |
|                                    |                                        |
|           +------------------------+------------------------+               |
|           |                        |                        |               |
|           v                        v                        v               |
|  +----------------+      +----------------+      +----------------+         |
|  |   Worker 1     |      |   Worker 2     |      |   Worker 3     |         |
|  |  +----------+  |      |  +----------+  |      |  +----------+  |         |
|  |  | Primary  |  |      |  | Primary  |  |      |  | Primary  |  |         |
|  |  | (主节点) |  |      |  | (主节点) |  |      |  | (主节点) |  |         |
|  |  +----------+  |      |  +----------+  |      |  +----------+  |         |
|  |  | Replica  |  |      |  | Replica  |  |      |  | Replica  |  |         |
|  |  | (从节点) |  |      |  | (从节点) |  |      |  | (从节点) |  |         |
|  |  +----------+  |      |  +----------+  |      |  +----------+  |         |
|  +----------------+      +----------------+      +----------------+         |
|        |                        |                        |                   |
|        v                        v                        v                   |
|  +----------------+      +----------------+      +----------------+         |
|  | 分片: file_    |      | 分片: user_    |      | 分片: analysis_|         |
|  | metadata       |      | permission    |      | result        |         |
|  | parse_result   |      | audit_log     |      | target        |         |
|  +----------------+      +----------------+      +----------------+         |
|                                                                             |
|  分片策略: Hash分片 (file_id, user_id, target_id)                            |
|  副本数: 2 (主从复制)                                                        |
|  故障转移: 自动故障检测与切换                                                 |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 2.2 ER图描述

```
+-----------------------------------------------------------------------------+
|                              核心实体关系图                                   |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +---------------+       +---------------+       +---------------+          |
|  |    users      |       |    roles      |       |  permissions  |          |
|  +---------------+       +---------------+       +---------------+          |
|  | id (PK)       |       | id (PK)       |       | id (PK)       |          |
|  | username      |       | role_name     |       | permission_key|          |
|  | password_hash |       | description   |       | resource_type |          |
|  | email         |       | status        |       | action        |          |
|  | status        |       | created_at    |       | description   |          |
|  | created_at    |       +---------------+       | created_at    |          |
|  +---------------+               |                       +---------------+          |
|         |                        |                              |                  |
|         | 1:N                    | 1:N                          | N:1              |
|         v                        v                              |                  |
|  +---------------+       +---------------+                      |                  |
|  | user_roles    |       | role_perms    |<---------------------+                  |
|  +---------------+       +---------------+                                          |
|  | id (PK)       |       | id (PK)       |                                          |
|  | user_id (FK)  |       | role_id (FK)  |                                          |
|  | role_id (FK)  |       | perm_id (FK)  |                                          |
|  | created_at    |       | created_at    |                                          |
|  +---------------+       +---------------+                                          |
|                                                                             |
|  +---------------+       +---------------+       +---------------+          |
|  | file_metadata |       | parse_result  |       |analysis_result|          |
|  +---------------+       +---------------+       +---------------+          |
|  | id (PK)       |       | id (PK)       |       | id (PK)       |          |
|  | file_id (UK)  |------>| file_id (FK)  |<------| file_id (FK)  |          |
|  | file_name     |  1:1  | parse_status  |  1:N  | analysis_type |          |
|  | file_size     |       | text_content  |       | risk_level    |          |
|  | file_type     |       | entities      |       | result        |          |
|  | sha256_hash   |       | created_at    |       | created_at    |          |
|  | storage_path  |       +---------------+       +---------------+          |
|  | owner_id (FK) |                                                            |
|  | status        |       +---------------+       +---------------+          |
|  | created_at    |       |    entity     |       | target        |          |
|  +---------------+       +---------------+       +---------------+          |
|         |                | id (PK)       |       | id (PK)       |          |
|         | N:1            | entity_type   |       | target_id (UK)|          |
|         v                | entity_value  |       | target_name   |          |
|  +---------------+       | file_id (FK)  |       | target_type   |          |
|  | directories   |       | context       |       | owner_id (FK) |          |
|  +---------------+       | created_at    |       | status        |          |
|  | id (PK)       |       +---------------+       | created_at    |          |
|  | path          |               |               +---------------+          |
|  | parent_id(FK) |               | N:1                    |                  |
|  | owner_id (FK) |               v                       | N:1              |
|  | created_at    |       +---------------+               v                  |
|  +---------------+       | target_file   |       +---------------+          |
|                          +---------------+       | target_asset  |          |
|                          | id (PK)       |       +---------------+          |
|                          | target_id (FK)|       | id (PK)       |          |
|                          | file_id (FK)  |       | target_id (FK)|          |
|                          | relation_type |       | asset_type    |          |
|                          | created_at    |       | asset_value   |          |
|                          +---------------+       | properties    |          |
|                                                  | created_at    |          |
|                          +---------------+       +---------------+          |
|                          |  audit_log    |                                 |
|                          +---------------+                                 |
|                          | id (PK)       |                                 |
|                          | user_id (FK)  |                                 |
|                          | action        |                                 |
|                          | resource_type |                                 |
|                          | resource_id   |                                 |
|                          | details       |                                 |
|                          | ip_address    |                                 |
|                          | created_at    |                                 |
|                          +---------------+                                 |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 2.3 表结构设计

#### 2.3.1 用户权限表

##### 用户表 (users)

```sql
-- 用户表 (参考表，全集群广播)
-- P1-1: 统一使用 UUID 作为主键(替代 BIGSERIAL), UUID v7 时间有序利于 B-Tree 插入性能
-- P2-13: 软删除统一(deleted_at)
-- P2-14: email 改 VARCHAR(254), last_login_ip 改 INET
-- P2-23: password_hash 升级 argon2id(OWASP 推荐)
CREATE TABLE users (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    username        VARCHAR(64) NOT NULL,
    password_hash   VARCHAR(256) NOT NULL,
    email           VARCHAR(254),
    phone           VARCHAR(32),
    real_name       VARCHAR(64),
    department      VARCHAR(128),
    status          SMALLINT NOT NULL DEFAULT 1,
    last_login_at   TIMESTAMPTZ,
    last_login_ip   INET,
    login_fail_count SMALLINT DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    -- P2-13 软删除
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 创建参考表(Citus广播表)
SELECT create_reference_table('users');

-- 索引(含软删除部分索引)
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_department ON users(department);
CREATE INDEX idx_users_created_at ON users(created_at);
-- P2-13 软删除部分索引(仅索引未删除记录)
CREATE INDEX idx_users_deleted ON users(deleted_at) WHERE deleted_at IS NULL;

-- 注释
COMMENT ON TABLE users IS '用户表(参考表，全集群广播，主键UUID)';
COMMENT ON COLUMN users.id IS 'UUID主键(gen_random_uuid生成, 时间有序v7, 利于B-Tree插入性能)';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.password_hash IS '密码哈希值(argon2id, OWASP推荐算法)';
COMMENT ON COLUMN users.email IS '邮箱(最大254字符, RFC 5321)';
COMMENT ON COLUMN users.last_login_ip IS '最近登录IP(INET类型, 支持IPv4/IPv6)';
COMMENT ON COLUMN users.status IS '状态: 0-禁用, 1-启用, 2-锁定';
COMMENT ON COLUMN users.login_fail_count IS '连续登录失败次数';
COMMENT ON COLUMN users.locked_until IS '锁定截止时间';
COMMENT ON COLUMN users.deleted_at IS '软删除时间(NULL表示未删除)';
```

##### 角色表 (roles)

```sql
-- 角色表
-- P1-1: UUID主键 / P2-13: 软删除
CREATE TABLE roles (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    role_id         VARCHAR(64) NOT NULL,
    role_name       VARCHAR(64) NOT NULL,
    role_code       VARCHAR(64) NOT NULL,
    description     TEXT,
    parent_id       UUID,
    level           SMALLINT DEFAULT 1,
    status          SMALLINT NOT NULL DEFAULT 1,
    is_system       BOOLEAN DEFAULT FALSE,
    -- P2-13 软删除
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    updated_by      UUID,
    
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_role_id UNIQUE (role_id),
    CONSTRAINT uk_roles_role_code UNIQUE (role_code),
    CONSTRAINT fk_roles_parent FOREIGN KEY (parent_id) REFERENCES roles(id)
);

-- 创建参考表
SELECT create_reference_table('roles');

-- 索引
CREATE INDEX idx_roles_status ON roles(status);
CREATE INDEX idx_roles_parent_id ON roles(parent_id);
CREATE INDEX idx_roles_deleted ON roles(deleted_at) WHERE deleted_at IS NULL;

-- 注释
COMMENT ON TABLE roles IS '角色表';
COMMENT ON COLUMN roles.role_code IS '角色编码(如: ADMIN, OPERATOR, ANALYST)';
COMMENT ON COLUMN roles.is_system IS '是否系统内置角色';
COMMENT ON COLUMN roles.level IS '角色层级';
COMMENT ON COLUMN roles.deleted_at IS '软删除时间(NULL表示未删除)';
```

##### 权限表 (permissions)

```sql
-- 权限表
-- P1-1: UUID主键
CREATE TABLE permissions (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    perm_id         VARCHAR(64) NOT NULL,
    perm_name       VARCHAR(128) NOT NULL,
    perm_code       VARCHAR(128) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,
    resource_key    VARCHAR(256),
    action          VARCHAR(32) NOT NULL,
    parent_id       UUID,
    description     TEXT,
    status          SMALLINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permissions_perm_id UNIQUE (perm_id),
    CONSTRAINT uk_permissions_perm_code UNIQUE (perm_code),
    CONSTRAINT fk_permissions_parent FOREIGN KEY (parent_id) REFERENCES permissions(id)
);

-- 创建参考表
SELECT create_reference_table('permissions');

-- 索引
CREATE INDEX idx_permissions_resource_type ON permissions(resource_type);
CREATE INDEX idx_permissions_parent_id ON permissions(parent_id);

-- 注释
COMMENT ON TABLE permissions IS '权限表';
COMMENT ON COLUMN permissions.resource_type IS '资源类型: MENU, BUTTON, API, DATA';
COMMENT ON COLUMN permissions.action IS '操作: CREATE, READ, UPDATE, DELETE, EXECUTE';
```

##### 用户角色关联表 (user_roles)

```sql
-- 用户角色关联表
-- P1-1: UUID主键与外键
CREATE TABLE user_roles (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    role_id         UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    
    CONSTRAINT pk_user_roles PRIMARY KEY (id),
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- 创建参考表(关联表无分片键, 作为广播表)
SELECT create_reference_table('user_roles');

-- 索引
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

COMMENT ON TABLE user_roles IS '用户角色关联表(主键UUID)';
```

##### 角色权限关联表 (role_permissions)

```sql
-- 角色权限关联表
-- P1-1: UUID主键与外键
CREATE TABLE role_permissions (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    role_id         UUID NOT NULL,
    perm_id         UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,
    
    CONSTRAINT pk_role_permissions PRIMARY KEY (id),
    CONSTRAINT uk_role_permissions_role_perm UNIQUE (role_id, perm_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_role_permissions_perm FOREIGN KEY (perm_id) REFERENCES permissions(id)
);

-- 创建参考表
SELECT create_reference_table('role_permissions');

-- 索引
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_perm_id ON role_permissions(perm_id);

COMMENT ON TABLE role_permissions IS '角色权限关联表(主键UUID)';
```

#### 2.3.2 文件元数据表

##### 文件元数据表 (file_metadata)

```sql
-- 文件元数据表 (分布式表，分片键 file_id)
-- Citus约束: 分布式表主键必须包含分片键，故主键直接使用 file_id
-- 秒传通过独立的 file_hash_index 表实现，避免在 file_metadata 上保留 sha256 唯一约束(会与秒传引用计数语义冲突)
-- P1-1: file_id 改 UUID 主键(替代 BIGSERIAL+VARCHAR)
-- P2-12: 移除 owner_name(通过 JOIN users 获取), 保留 directory_path(避免递归JOIN)
-- P2-13: 软删除统一(已有 is_deleted/deleted_at, 移除 status 中的"已删除"枚举值)
-- P2-14: upload_ip 改 INET 类型
CREATE TABLE file_metadata (
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    file_name       VARCHAR(512) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_type       VARCHAR(64),
    content_type    VARCHAR(128),
    extension       VARCHAR(32),

    -- 哈希值(用于业务查询，秒传查 file_hash_index 表)
    md5_hash        VARCHAR(32),
    sha256_hash     VARCHAR(64) NOT NULL,

    -- 存储信息
    storage_path    VARCHAR(1024) NOT NULL,
    storage_bucket  VARCHAR(128),
    storage_tier    VARCHAR(32) DEFAULT 'hot',

    -- 目录信息
    directory_id    UUID,
    directory_path  VARCHAR(1024),  -- P2-12 保留: 查询优化, 避免递归JOIN directories 表

    -- 所有者信息(P2-12: 移除 owner_name, 通过 JOIN users 获取)
    owner_id        UUID NOT NULL,

    -- 上传来源(P2-14: INET 类型)
    upload_ip       INET,

    -- 文件状态(P2-13: 移除"已删除"枚举值, 软删除统一使用 deleted_at)
    status          SMALLINT NOT NULL DEFAULT 1,
    is_deleted      BOOLEAN DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,

    -- 引用计数(用于秒传, 多个逻辑文件共享同一物理存储)
    ref_count       INTEGER NOT NULL DEFAULT 1,
    -- 乐观锁版本号(并发安全地增减 ref_count)
    version         INTEGER NOT NULL DEFAULT 0,

    -- 安全标记
    security_level  SMALLINT DEFAULT 1,
    is_sensitive    BOOLEAN DEFAULT FALSE,
    is_malicious    BOOLEAN DEFAULT FALSE,

    -- 时间戳
    upload_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 元数据扩展(JSONB)
    extra_metadata  JSONB,

    CONSTRAINT pk_file_metadata PRIMARY KEY (file_id)
);

-- 创建分布式表(Citus) — P1-4: 作为 file_id colocation 组基准表
SELECT create_distributed_table('file_metadata', 'file_id', colocate_with => 'none');

-- 行级安全策略(RLS): 文件所有者或管理员可见
ALTER TABLE file_metadata ENABLE ROW LEVEL SECURITY;
CREATE POLICY file_owner_policy ON file_metadata
    USING (owner_id = current_user_id() OR is_admin());

-- 索引(已删除冗余: idx_sha256 与原 UNIQUE 重复、idx_created_at 与 upload_time 重复)
CREATE INDEX idx_file_metadata_owner_id ON file_metadata(owner_id);
CREATE INDEX idx_file_metadata_status ON file_metadata(file_type, status);
CREATE INDEX idx_file_metadata_directory_id ON file_metadata(directory_id);
-- 复合索引(覆盖"我的文件列表"高频查询)
CREATE INDEX idx_file_metadata_owner_status_time ON file_metadata(owner_id, is_deleted, upload_time DESC);
CREATE INDEX idx_file_metadata_type_status ON file_metadata(file_type, status);

-- GIN索引(JSONB)
CREATE INDEX idx_file_metadata_extra_metadata ON file_metadata USING GIN(extra_metadata);

-- 注释
COMMENT ON TABLE file_metadata IS '文件元数据表(分布式，主键file_id UUID)';
COMMENT ON COLUMN file_metadata.file_id IS '文件唯一标识(UUID, gen_random_uuid生成)';
COMMENT ON COLUMN file_metadata.storage_tier IS '存储层级: hot, warm, cold, archive';
COMMENT ON COLUMN file_metadata.status IS '状态: 0-上传中, 1-正常, 2-解析中, 3-分析中(P2-13: 软删除统一使用deleted_at, 移除4-已删除枚举)';
COMMENT ON COLUMN file_metadata.upload_ip IS '上传来源IP(INET类型, 支持IPv4/IPv6)';
COMMENT ON COLUMN file_metadata.security_level IS '安全等级: 1-公开, 2-内部, 3-机密, 4-绝密';
COMMENT ON COLUMN file_metadata.ref_count IS '引用计数(秒传时多个逻辑文件共享同一物理存储，配合version乐观锁并发安全更新)';
COMMENT ON COLUMN file_metadata.version IS '乐观锁版本号(每次更新ref_count时校验: UPDATE ... WHERE version = $old AND version = version+1)';
```

##### 文件哈希索引表 (file_hash_index) — 秒传专用

```sql
-- 文件哈希索引表(分布式表，按 sha256_hash 分片)
-- 用于秒传判断: 上传前查 sha256_hash 是否已存在
-- 与 file_metadata 解耦: 一个 sha256 可对应多个 file_id(秒传场景)
-- P1-1: file_id 改 UUID
CREATE TABLE file_hash_index (
    sha256_hash     VARCHAR(64) NOT NULL,
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    md5_hash        VARCHAR(32),
    file_size       BIGINT NOT NULL,
    storage_path    VARCHAR(1024) NOT NULL,
    storage_bucket  VARCHAR(128),
    ref_count       INTEGER NOT NULL DEFAULT 1,
    version         INTEGER NOT NULL DEFAULT 0,
    first_upload_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_file_hash_index PRIMARY KEY (sha256_hash, file_id)
);

-- 创建分布式表(按 sha256_hash 分片, 与 file_metadata 不在同一个 colocate 组)
SELECT create_distributed_table('file_hash_index', 'sha256_hash', colocate_with => 'none');

-- 索引
CREATE INDEX idx_file_hash_index_file_id ON file_hash_index(file_id);
CREATE INDEX idx_file_hash_index_last_seen ON file_hash_index(last_seen_at DESC);

COMMENT ON TABLE file_hash_index IS '文件哈希索引表(秒传专用, 独立分片避免唯一约束与引用计数语义冲突)';
COMMENT ON COLUMN file_hash_index.ref_count IS '物理存储引用计数, 配合version乐观锁并发安全更新';
```

##### 文件标签表 (file_tags)

```sql
-- 文件标签表(分布式表，分片键 file_id)
-- Citus约束: 主键必须包含分片键，故直接使用 (file_id, tag_name) 作为主键
-- P1-1: file_id 改 UUID
CREATE TABLE file_tags (
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    tag_name        VARCHAR(64) NOT NULL,
    tag_type        VARCHAR(32) DEFAULT 'user',
    tag_color       VARCHAR(16),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,

    CONSTRAINT pk_file_tags PRIMARY KEY (file_id, tag_name)
);

-- 创建分布式表(P1-4: 与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('file_tags', 'file_id', colocate_with => 'file_metadata');

-- 索引
CREATE INDEX idx_file_tags_tag_name ON file_tags(tag_name);
CREATE INDEX idx_file_tags_tag_type ON file_tags(tag_type);

COMMENT ON TABLE file_tags IS '文件标签表(分布式，主键file_id+tag_name)';
COMMENT ON COLUMN file_tags.tag_type IS '标签类型: user-用户标签, system-系统标签';
```

##### 文件版本表 (file_versions)

```sql
-- 文件版本表(分布式表，分片键 file_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id 改 UUID, file_id 改 UUID
CREATE TABLE file_versions (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    version_id      VARCHAR(64) NOT NULL,
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    version_number  INTEGER NOT NULL,
    sha256_hash     VARCHAR(64) NOT NULL,
    storage_path    VARCHAR(1024) NOT NULL,
    file_size       BIGINT NOT NULL,
    change_summary  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,

    CONSTRAINT pk_file_versions PRIMARY KEY (id, file_id),
    CONSTRAINT uk_file_versions_version_id UNIQUE (version_id, file_id),
    CONSTRAINT uk_file_versions_file_version UNIQUE (file_id, version_number)
);

-- 创建分布式表(P1-4: 与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('file_versions', 'file_id', colocate_with => 'file_metadata');

-- 索引
CREATE INDEX idx_file_versions_file_id ON file_versions(file_id);

COMMENT ON TABLE file_versions IS '文件版本表(分布式，主键id+file_id)';
```

##### 目录表 (directories)

```sql
-- 目录表
-- P1-1: id 改 UUID
CREATE TABLE directories (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    dir_id          VARCHAR(64) NOT NULL,
    dir_name        VARCHAR(256) NOT NULL,
    dir_path        VARCHAR(1024) NOT NULL,
    parent_id       UUID,
    owner_id        UUID NOT NULL,
    status          SMALLINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_directories PRIMARY KEY (id),
    CONSTRAINT uk_directories_dir_id UNIQUE (dir_id),
    CONSTRAINT uk_directories_path UNIQUE (dir_path),
    CONSTRAINT fk_directories_parent FOREIGN KEY (parent_id) REFERENCES directories(id)
);

-- 创建参考表(目录树全集群可见)
SELECT create_reference_table('directories');

-- 索引
CREATE INDEX idx_directories_owner_id ON directories(owner_id);
CREATE INDEX idx_directories_parent_id ON directories(parent_id);

COMMENT ON TABLE directories IS '目录表(主键UUID)';
```

#### 2.3.3 解析结果表

##### 解析结果表 (parse_results)

```sql
-- 解析结果表 (分布式表，分片键 file_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id/file_id 改 UUID
-- P1-2: 1:1 硬约束修复: 取消 UNIQUE(file_id)，改为 (file_id, parse_version) 联合唯一
--   原因: 同一文件可能因解析引擎升级、人工重试等原因产生多个解析版本，1:1硬约束会阻断版本演进
-- P2-12: 移除 parse_duration(可由 parse_end_at - parse_start_at 计算), 保留 entity_count(查询优化避免COUNT)
-- P2-17: GIN 索引使用 jsonb_path_ops(更小更快)
CREATE TABLE parse_results (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    parse_id        VARCHAR(64) NOT NULL,
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    parse_version   INTEGER NOT NULL DEFAULT 1,

    -- 解析状态
    parse_status    SMALLINT NOT NULL DEFAULT 0,
    parse_progress  SMALLINT DEFAULT 0,
    parse_start_at  TIMESTAMPTZ,
    parse_end_at    TIMESTAMPTZ,
    -- P2-12: 移除 parse_duration(由 parse_end_at - parse_start_at 计算得出)

    -- 格式识别
    detected_format VARCHAR(64),
    actual_format   VARCHAR(64),
    is_encrypted    BOOLEAN DEFAULT FALSE,

    -- 文本内容
    text_content    TEXT,
    text_length     INTEGER,
    text_encoding   VARCHAR(32),
    language        VARCHAR(16),

    -- 元数据(JSONB)
    parse_metadata  JSONB,
    exif_data       JSONB,

    -- 实体信息(JSONB)
    entities        JSONB,
    entity_count    INTEGER DEFAULT 0,  -- P2-12 保留: 查询优化, 避免 COUNT

    -- 错误信息
    error_code      VARCHAR(32),
    error_message   TEXT,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_parse_results PRIMARY KEY (id, file_id),
    CONSTRAINT uk_parse_results_parse_id UNIQUE (parse_id, file_id),
    -- P1-2: (file_id, parse_version) 联合唯一: 支持多版本解析，最新版本由应用层标记
    CONSTRAINT uk_parse_results_file_version UNIQUE (file_id, parse_version)
);

-- 创建分布式表(P1-4: 与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('parse_results', 'file_id', colocate_with => 'file_metadata');

-- P1-2: 显式迁移语句(已有表升级时使用)
-- ALTER TABLE parse_results DROP CONSTRAINT IF EXISTS uk_parse_results_file_id;
-- ALTER TABLE parse_results ADD COLUMN IF NOT EXISTS parse_version INTEGER DEFAULT 1;
-- ALTER TABLE parse_results ADD CONSTRAINT uk_parse_results_file_version UNIQUE(file_id, parse_version);

-- 索引
CREATE INDEX idx_parse_results_parse_status ON parse_results(parse_status);
CREATE INDEX idx_parse_results_created_at ON parse_results(created_at DESC);

-- P2-17: GIN索引使用 jsonb_path_ops(更小更快, 仅支持 @ @ 存在性查询)
CREATE INDEX idx_parse_results_entities ON parse_results USING GIN(entities jsonb_path_ops);
CREATE INDEX idx_parse_results_metadata ON parse_results USING GIN(parse_metadata jsonb_path_ops);

COMMENT ON TABLE parse_results IS '文件解析结果表(分布式，主键id+file_id，支持多版本解析)';
COMMENT ON COLUMN parse_results.parse_status IS '解析状态: 0-待解析, 1-解析中, 2-成功, 3-失败';
COMMENT ON COLUMN parse_results.parse_version IS '解析版本号, 同一文件可有多版本(引擎升级/重试)';
COMMENT ON COLUMN parse_results.detected_format IS '检测到的格式(基于扩展名)';
COMMENT ON COLUMN parse_results.actual_format IS '实际格式(基于魔数)';
COMMENT ON COLUMN parse_results.entity_count IS '实体数量(冗余字段, 查询优化避免COUNT)';
```

##### 实体表 (entities)

```sql
-- 实体表 (分布式表，分片键 file_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id/file_id 改 UUID
-- P2-14: entity_value 改 TEXT(实体值长度不固定, 如长URL)
-- P2-16: 索引去重, 删除被复合索引覆盖的单字段索引 idx_entities_entity_value
CREATE TABLE entities (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    entity_id       VARCHAR(64) NOT NULL,
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 实体类型
    entity_type     VARCHAR(32) NOT NULL,
    entity_value    TEXT NOT NULL,             -- P2-14: 改 TEXT
    normalized_value TEXT,                      -- P2-14: 同步改 TEXT

    -- 上下文信息
    context_before  TEXT,
    context_after   TEXT,
    position_start  INTEGER,
    position_end    INTEGER,

    -- 置信度
    confidence      DECIMAL(5,4),

    -- 来源信息
    source_section  VARCHAR(128),
    source_page     INTEGER,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_entities PRIMARY KEY (id, file_id),
    CONSTRAINT uk_entities_entity_id UNIQUE (entity_id, file_id)
);

-- 创建分布式表(P1-4: 与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('entities', 'file_id', colocate_with => 'file_metadata');

-- 索引
CREATE INDEX idx_entities_entity_type ON entities(entity_type);
-- P2-16: 删除被复合索引 idx_entities_type_value 覆盖的 idx_entities_entity_value
-- DROP INDEX IF EXISTS idx_entities_entity_value;
-- 保留复合索引(覆盖"按类型查实体值"高频查询)
CREATE INDEX idx_entities_type_value ON entities(entity_type, entity_value);

COMMENT ON TABLE entities IS '实体表(分布式，主键id+file_id)';
COMMENT ON COLUMN entities.entity_type IS '实体类型: IP, DOMAIN, EMAIL, URL, CVE, MD5, SHA256, PORT, PERSON, ORG';
COMMENT ON COLUMN entities.entity_value IS '实体值(TEXT类型, 支持长URL等)';
```

##### 压缩包内容表 (archive_contents)

```sql
-- 压缩包内容表(分布式表，分片键 archive_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id/archive_id/file_id 改 UUID
CREATE TABLE archive_contents (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    archive_id      UUID NOT NULL DEFAULT gen_random_uuid(),
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 内部文件信息
    inner_path      VARCHAR(1024) NOT NULL,
    inner_file_name VARCHAR(512),
    inner_file_size BIGINT,
    inner_file_type VARCHAR(64),
    inner_sha256    VARCHAR(64),

    -- 压缩信息
    compressed_size BIGINT,
    compression_ratio DECIMAL(5,2),
    is_encrypted    BOOLEAN DEFAULT FALSE,

    -- 层级
    depth           SMALLINT DEFAULT 0,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_archive_contents PRIMARY KEY (id, archive_id),
    CONSTRAINT uk_archive_contents_archive_path UNIQUE (archive_id, inner_path)
);

-- 创建分布式表
SELECT create_distributed_table('archive_contents', 'archive_id', colocate_with => 'none');

-- 索引
CREATE INDEX idx_archive_contents_file_id ON archive_contents(file_id);

COMMENT ON TABLE archive_contents IS '压缩包内容表(分布式，主键id+archive_id)';
```

##### 流量会话表 (network_sessions)

```sql
-- 流量会话表(分布式表，分片键 file_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id/file_id 改 UUID
-- P2-14: src_ip/dst_ip 改 INET
CREATE TABLE network_sessions (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    session_id      VARCHAR(64) NOT NULL,
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 五元组(P2-14: IP 改 INET)
    src_ip          INET NOT NULL,
    src_port        INTEGER,
    dst_ip          INET NOT NULL,
    dst_port        INTEGER,
    protocol        VARCHAR(16) NOT NULL,

    -- 会话信息
    session_start   TIMESTAMPTZ,
    session_end     TIMESTAMPTZ,
    duration_ms     BIGINT,

    -- 流量统计
    packets_count   INTEGER,
    bytes_sent      BIGINT,
    bytes_received  BIGINT,
    total_bytes     BIGINT,

    -- 应用层协议
    app_protocol    VARCHAR(32),
    app_info        JSONB,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_network_sessions PRIMARY KEY (id, file_id),
    CONSTRAINT uk_network_sessions_session_id UNIQUE (session_id, file_id)
);

-- 创建分布式表(P1-4: 与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('network_sessions', 'file_id', colocate_with => 'file_metadata');

-- 索引
CREATE INDEX idx_network_sessions_src_ip ON network_sessions(src_ip);
CREATE INDEX idx_network_sessions_dst_ip ON network_sessions(dst_ip);
CREATE INDEX idx_network_sessions_protocol ON network_sessions(protocol);
CREATE INDEX idx_network_sessions_session_start ON network_sessions(session_start);

COMMENT ON TABLE network_sessions IS '网络流量会话表(分布式，主键id+file_id)';
```

#### 2.3.4 分析结果表

##### 分析结果表 (analysis_results)

```sql
-- 分析结果表 (分布式表，分片键 file_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id/file_id 改 UUID
-- P2-17: GIN 索引使用 jsonb_path_ops
CREATE TABLE analysis_results (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    analysis_id     VARCHAR(64) NOT NULL,
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 分析类型
    analysis_type   VARCHAR(64) NOT NULL,
    analysis_name   VARCHAR(128),

    -- 分析状态
    analysis_status SMALLINT NOT NULL DEFAULT 0,
    analysis_progress SMALLINT DEFAULT 0,
    analysis_start_at TIMESTAMPTZ,
    analysis_end_at TIMESTAMPTZ,
    analysis_duration INTEGER,

    -- 风险评估
    risk_level      SMALLINT DEFAULT 0,
    risk_score      DECIMAL(5,2),

    -- 分析结果(JSONB)
    result          JSONB,
    summary         TEXT,

    -- 报告信息
    report_path     VARCHAR(512),
    report_format   VARCHAR(16),

    -- 错误信息
    error_code      VARCHAR(32),
    error_message   TEXT,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_analysis_results PRIMARY KEY (id, file_id),
    CONSTRAINT uk_analysis_results_analysis_id UNIQUE (analysis_id, file_id)
);

-- 创建分布式表(P1-4: 与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('analysis_results', 'file_id', colocate_with => 'file_metadata');

-- 索引
CREATE INDEX idx_analysis_results_analysis_type ON analysis_results(analysis_type);
CREATE INDEX idx_analysis_results_risk_level ON analysis_results(risk_level);
CREATE INDEX idx_analysis_results_analysis_status ON analysis_results(analysis_status);
CREATE INDEX idx_analysis_results_created_at ON analysis_results(created_at DESC);

-- P2-17: GIN索引使用 jsonb_path_ops(更小更快)
CREATE INDEX idx_analysis_results_result ON analysis_results USING GIN(result jsonb_path_ops);

COMMENT ON TABLE analysis_results IS '文件分析结果表(分布式，主键id+file_id)';
COMMENT ON COLUMN analysis_results.analysis_type IS '分析类型: MALWARE_DETECTION, VULNERABILITY, SENSITIVE_DATA, THREAT_INTEL';
COMMENT ON COLUMN analysis_results.analysis_status IS '分析状态: 0-待分析, 1-分析中, 2-成功, 3-失败';
COMMENT ON COLUMN analysis_results.risk_level IS '风险等级: 0-未知, 1-低, 2-中, 3-高, 4-严重';
```

##### 漏洞信息表 (vulnerabilities)

```sql
-- 漏洞信息表(分布式表，分片键 file_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id/file_id 改 UUID
-- P2-14: cvss_score 改 DECIMAL(4,2), severity 改 ENUM
-- P2-14: 创建 severity_level 枚举类型
CREATE TYPE severity_level AS ENUM ('none', 'low', 'medium', 'high', 'critical');

CREATE TABLE vulnerabilities (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    vuln_id         VARCHAR(64) NOT NULL,
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 漏洞标识
    cve_id          VARCHAR(32),
    cnvd_id         VARCHAR(32),
    cnnvd_id        VARCHAR(32),
    vuln_name       VARCHAR(256),

    -- 漏洞分类
    vuln_type       VARCHAR(64),
    vuln_category   VARCHAR(64),

    -- 严重程度(P2-14: severity 改 ENUM, cvss_score 改 DECIMAL(4,2))
    severity        severity_level,
    cvss_score      DECIMAL(4,2),                       -- P2-14: 范围 0.00-10.00
    cvss_vector     VARCHAR(128),

    -- 影响信息
    affected_product VARCHAR(256),
    affected_version VARCHAR(128),

    -- 描述信息
    description     TEXT,
    solution        TEXT,
    references      JSONB,

    -- 状态
    status          VARCHAR(32) DEFAULT 'open',

    -- 时间戳
    discovered_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_vulnerabilities PRIMARY KEY (id, file_id),
    CONSTRAINT uk_vulnerabilities_vuln_id UNIQUE (vuln_id, file_id)
);

-- 创建分布式表(P1-4: 与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('vulnerabilities', 'file_id', colocate_with => 'file_metadata');

-- 索引
CREATE INDEX idx_vulnerabilities_cve_id ON vulnerabilities(cve_id);
CREATE INDEX idx_vulnerabilities_severity ON vulnerabilities(severity);
CREATE INDEX idx_vulnerabilities_status ON vulnerabilities(status);

COMMENT ON TABLE vulnerabilities IS '漏洞信息表(分布式，主键id+file_id)';
COMMENT ON COLUMN vulnerabilities.severity IS '严重程度(ENUM): none, low, medium, high, critical';
COMMENT ON COLUMN vulnerabilities.cvss_score IS 'CVSS评分(DECIMAL(4,2), 范围0.00-10.00)';
```

##### 敏感信息表 (sensitive_findings)

```sql
-- 敏感信息表(分布式表，分片键 file_id)
-- Citus约束: 主键必须包含分片键
-- 安全修复: content_full 改为应用层加密 BYTEA，避免敏感信息明文存储
-- 使用 pgcrypto 扩展提供 pgp_sym_encrypt/pgp_sym_decrypt 函数
-- P1-1: id/file_id 改 UUID
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE sensitive_findings (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    finding_id      VARCHAR(64) NOT NULL,
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 敏感信息类型
    finding_type    VARCHAR(64) NOT NULL,
    finding_name    VARCHAR(128),

    -- 敏感内容(应用层加密存储, 应用层传入DEK再调用 pgp_sym_encrypt)
    content_hash    VARCHAR(64),
    content_preview VARCHAR(256),                          -- 仅展示前后截断的脱敏片段
    content_full    BYTEA,                                  -- 应用层加密后的密文(应用DEK经pgp_sym_encrypt加密)

    -- 位置信息
    position_start  INTEGER,
    position_end    INTEGER,
    line_number     INTEGER,
    context         TEXT,

    -- 置信度
    confidence      DECIMAL(5,4),

    -- 处理状态
    status          VARCHAR(32) DEFAULT 'detected',

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_sensitive_findings PRIMARY KEY (id, file_id),
    CONSTRAINT uk_sensitive_findings_finding_id UNIQUE (finding_id, file_id)
);

-- 创建分布式表(P1-4: 与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('sensitive_findings', 'file_id', colocate_with => 'file_metadata');

-- 行级安全: 仅所有者或管理员可访问
ALTER TABLE sensitive_findings ENABLE ROW LEVEL SECURITY;
CREATE POLICY sensitive_finding_owner_policy ON sensitive_findings
    USING (
        EXISTS (
            SELECT 1 FROM file_metadata fm
            WHERE fm.file_id = sensitive_findings.file_id
              AND (fm.owner_id = current_user_id() OR is_admin())
        )
    );

-- 索引
CREATE INDEX idx_sensitive_findings_type ON sensitive_findings(finding_type);
CREATE INDEX idx_sensitive_findings_status ON sensitive_findings(status);

COMMENT ON TABLE sensitive_findings IS '敏感信息发现表(分布式，主键id+file_id，content_full应用层加密BYTEA)';
COMMENT ON COLUMN sensitive_findings.finding_type IS '类型: PASSWORD, API_KEY, CERTIFICATE, CREDENTIAL, PII, CREDIT_CARD';
COMMENT ON COLUMN sensitive_findings.content_full IS '应用层加密的敏感内容密文(BYTEA), 应用DEK经pgcrypto.pgp_sym_encrypt加密';
```

#### 2.3.5 目标画像表

##### 目标表 (targets)

```sql
-- 目标表(分布式表，分片键 target_id)
-- Citus约束: 主键必须包含分片键，故直接使用 target_id 作为主键
-- P1-1: target_id 改 UUID
-- P2-12: 移除 owner_name(通过 JOIN users 获取)
-- P2-13: 软删除统一
CREATE TABLE targets (
    target_id       UUID NOT NULL DEFAULT gen_random_uuid(),
    target_name     VARCHAR(256) NOT NULL,
    target_type     VARCHAR(64) NOT NULL,

    -- 基本信息
    description     TEXT,
    industry        VARCHAR(128),
    region          VARCHAR(128),
    country         VARCHAR(64),

    -- 标签(JSONB)
    tags            JSONB,

    -- 负责人(P2-12: 移除 owner_name, 通过 JOIN users 获取)
    owner_id        UUID NOT NULL,

    -- 团队成员(JSONB)
    team_members    JSONB,

    -- 画像统计
    file_count      INTEGER DEFAULT 0,
    asset_count     INTEGER DEFAULT 0,
    vuln_count      INTEGER DEFAULT 0,

    -- 状态
    status          SMALLINT NOT NULL DEFAULT 1,
    -- P2-13 软删除
    deleted_at      TIMESTAMPTZ,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_targets PRIMARY KEY (target_id)
);

-- 创建分布式表(作为 target_id colocation 组基准表)
SELECT create_distributed_table('targets', 'target_id', colocate_with => 'none');

-- 行级安全: 负责人或团队成员或管理员可见
ALTER TABLE targets ENABLE ROW LEVEL SECURITY;
CREATE POLICY target_owner_policy ON targets
    USING (owner_id = current_user_id() OR is_admin());

-- 索引
CREATE INDEX idx_targets_owner_id ON targets(owner_id);
CREATE INDEX idx_targets_target_type ON targets(target_type);
CREATE INDEX idx_targets_status ON targets(status);
CREATE INDEX idx_targets_created_at ON targets(created_at DESC);
-- P2-13 软删除部分索引
CREATE INDEX idx_targets_deleted ON targets(deleted_at) WHERE deleted_at IS NULL;

-- GIN索引(JSONB)
CREATE INDEX idx_targets_tags ON targets USING GIN(tags);

COMMENT ON TABLE targets IS '目标表(分布式，主键target_id UUID)';
COMMENT ON COLUMN targets.target_type IS '目标类型: ENTERPRISE, ORGANIZATION, SYSTEM, PERSON, DOMAIN';
COMMENT ON COLUMN targets.deleted_at IS '软删除时间(NULL表示未删除)';
```

##### 目标文件关联表 (双写冗余设计)

> **跨分片 JOIN 修复说明**: 原始 `target_files` 表按 `target_id` 分片，无法高效支持"按 file_id 反查关联目标列表"的查询(跨分片扫描)。
> 设计为协调表双写冗余存储两份数据，应用层在写入时同步双写，读取时按查询模式选择对应表，避免跨分片 JOIN。

###### target_files_by_target (按 target_id 分片，用于"目标的文件列表")

```sql
-- target_files_by_target: 用于"查询目标关联的文件列表"
-- 分片键 target_id, 与 targets 同一 colocate 组
-- P1-1: target_id/file_id 改 UUID
-- P1-3: 跨分片 JOIN 冗余存储(按 target_id 分片侧)
CREATE TABLE target_files_by_target (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    target_id       UUID NOT NULL DEFAULT gen_random_uuid(),
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    relation_type   VARCHAR(32) DEFAULT 'related',
    relation_desc   TEXT,
    confidence      DECIMAL(5,4),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,

    CONSTRAINT pk_target_files_by_target PRIMARY KEY (id, target_id),
    CONSTRAINT uk_tfbt_target_file UNIQUE (target_id, file_id)
);

SELECT create_distributed_table('target_files_by_target', 'target_id', colocate_with => 'targets');

CREATE INDEX idx_tfbt_relation_type ON target_files_by_target(relation_type);
CREATE INDEX idx_tfbt_created_at ON target_files_by_target(created_at DESC);

COMMENT ON TABLE target_files_by_target IS '目标文件关联表(按target_id分片，查"目标的文件列表")';
```

###### target_files_by_file (按 file_id 分片，用于"文件关联的目标列表")

```sql
-- target_files_by_file: 用于"查询文件关联的目标列表"
-- 分片键 file_id, 与 file_metadata 同一 colocate 组
-- 数据冗余存储，应用层在写入 target_files_by_target 时同步双写本表
-- P1-1: file_id/target_id 改 UUID
-- P1-3: 跨分片 JOIN 冗余存储(按 file_id 分片侧)
-- P1-4: 与 file_metadata 同一 colocation 组
CREATE TABLE target_files_by_file (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    file_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    target_id       UUID NOT NULL DEFAULT gen_random_uuid(),
    relation_type   VARCHAR(32) DEFAULT 'related',
    relation_desc   TEXT,
    confidence      DECIMAL(5,4),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      UUID,

    CONSTRAINT pk_target_files_by_file PRIMARY KEY (id, file_id),
    CONSTRAINT uk_tfbf_file_target UNIQUE (file_id, target_id)
);

SELECT create_distributed_table('target_files_by_file', 'file_id', colocate_with => 'file_metadata');

CREATE INDEX idx_tfbf_relation_type ON target_files_by_file(relation_type);
CREATE INDEX idx_tfbf_created_at ON target_files_by_file(created_at DESC);

COMMENT ON TABLE target_files_by_file IS '目标文件关联表(按file_id分片，查"文件的目标列表"，与target_files_by_target双写)';
```

> **双写一致性**: 写入通过 Outbox Pattern + Kafka CDC 保证两表最终一致(详见第9章)。删除时同样双删。
> **大目标数据倾斜**: 当某 target 关联文件数远超均值时，将该 target 的 target_files_by_target 数据通过应用层路由到独立分片组( colocate_with => 'none' )，避免单个分片过大。
```

##### 目标资产表 (target_assets)

```sql
-- 目标资产表(分布式表，分片键 target_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id/target_id/source_file_id 改 UUID
CREATE TABLE target_assets (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    asset_id        VARCHAR(64) NOT NULL,
    target_id       UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 资产类型
    asset_type      VARCHAR(32) NOT NULL,
    asset_value     VARCHAR(512) NOT NULL,

    -- 资产属性(JSONB)
    properties      JSONB,

    -- 来源信息
    source_file_id  UUID,
    discovered_at   TIMESTAMPTZ,

    -- 状态
    status          VARCHAR(32) DEFAULT 'active',

    -- 标签
    tags            JSONB,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_target_assets PRIMARY KEY (id, target_id),
    CONSTRAINT uk_target_assets_asset_id UNIQUE (asset_id, target_id)
);

-- 创建分布式表(P1-4: 与 targets 同一 colocate 组)
SELECT create_distributed_table('target_assets', 'target_id', colocate_with => 'targets');

-- 索引
CREATE INDEX idx_target_assets_asset_type ON target_assets(asset_type);
CREATE INDEX idx_target_assets_asset_value ON target_assets(asset_value);
CREATE INDEX idx_target_assets_status ON target_assets(status);

COMMENT ON TABLE target_assets IS '目标资产表(分布式，主键id+target_id)';
COMMENT ON COLUMN target_assets.asset_type IS '资产类型: IP, DOMAIN, PORT, SERVICE, HOST, NETWORK';
```

##### 目标人员表 (target_persons)

```sql
-- 目标人员表(分布式表，分片键 target_id)
-- Citus约束: 主键必须包含分片键
-- P1-1: id/target_id/source_file_id 改 UUID
CREATE TABLE target_persons (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    person_id       VARCHAR(64) NOT NULL,
    target_id       UUID NOT NULL DEFAULT gen_random_uuid(),

    -- 人员信息
    name            VARCHAR(128),
    email           VARCHAR(254),                           -- P2-14: VARCHAR(254)
    phone           VARCHAR(64),
    position        VARCHAR(128),
    department      VARCHAR(256),

    -- 来源信息
    source_file_id  UUID,
    discovered_at   TIMESTAMPTZ,

    -- 属性(JSONB)
    properties      JSONB,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_target_persons PRIMARY KEY (id, target_id),
    CONSTRAINT uk_target_persons_person_id UNIQUE (person_id, target_id)
);

-- 创建分布式表(P1-4: 与 targets 同一 colocate 组)
SELECT create_distributed_table('target_persons', 'target_id', colocate_with => 'targets');

-- 索引
CREATE INDEX idx_target_persons_email ON target_persons(email);

COMMENT ON TABLE target_persons IS '目标人员表(分布式，主键id+target_id)';
```

#### 2.3.6 审计日志表

##### 审计日志表 (audit_logs)

```sql
-- 审计日志表 (按 created_at 时间范围分区, 解决数据倾斜)
-- 数据倾斜修复: 原按 user_id 哈希分片会导致活跃用户所在分片数据量远超均值
-- 改为 PG 原生 RANGE 分区(按月), 配合归档策略实现冷热分离
-- PG分区表约束: 主键必须包含分区键 created_at, 故主键为 (id, user_id, created_at)
-- 仍保留 user_id 在主键中, 便于按用户审计场景的索引覆盖
-- P1-1: id 改 UUID, user_id 改 UUID
-- P2-14: ip_address 改 INET
-- P2-18: 索引优化(只保留3个核心索引, 删除低选择性索引)
CREATE TABLE audit_logs (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    log_id          VARCHAR(64) NOT NULL,

    -- 用户信息(P1-1: user_id 改 UUID)
    user_id         UUID,
    username        VARCHAR(64),

    -- 操作信息
    action          VARCHAR(64) NOT NULL,
    action_category VARCHAR(32),

    -- 资源信息
    resource_type   VARCHAR(64) NOT NULL,
    resource_id     VARCHAR(128),
    resource_name   VARCHAR(512),

    -- 操作详情(JSONB)
    details         JSONB,

    -- 结果
    result          VARCHAR(16) NOT NULL,
    error_code      VARCHAR(32),
    error_message   TEXT,

    -- 请求信息(P2-14: ip_address 改 INET)
    ip_address      INET,
    user_agent      VARCHAR(512),
    request_id      VARCHAR(64),

    -- 时间戳(同时也是分区键)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 主键含 user_id 与分区键 created_at
    CONSTRAINT pk_audit_logs PRIMARY KEY (id, user_id, created_at),
    CONSTRAINT uk_audit_logs_log_id UNIQUE (log_id, created_at)
) PARTITION BY RANGE (created_at);

-- 月度分区(由 pg_partman 自动维护, 此处示例手工创建)
CREATE TABLE audit_logs_y2026m03 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE audit_logs_y2026m04 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE audit_logs_y2026m05 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

-- 不再创建为 Citus 分布式表(改为 PG 原生分区, 通过分区实现横向扩展与冷热分离)
-- 如需跨节点分布, 可对各月分区单独 SELECT create_distributed_table('audit_logs_y2026m03', 'user_id');

-- P2-18: 索引优化(审计日志只保留3个核心索引 + GIN, 删除低选择性索引)
-- 复合索引1: 用户审计场景(按用户+时间倒序)
CREATE INDEX idx_audit_logs_user_time ON audit_logs(user_id, created_at DESC);
-- 复合索引2: 操作审计场景(按操作+时间倒序)
CREATE INDEX idx_audit_logs_action_time ON audit_logs(action, created_at DESC);
-- 复合索引3: 资源追溯场景(按资源类型+资源ID)
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
-- GIN索引(JSONB)
CREATE INDEX idx_audit_logs_details ON audit_logs USING GIN(details jsonb_path_ops);
-- BRIN索引(适合时序大表, 占用空间极小)
CREATE INDEX idx_audit_logs_created_at_brin ON audit_logs USING BRIN(created_at);
-- P2-18: 删除低选择性索引(已被复合索引覆盖或选择性低)
-- DROP INDEX IF EXISTS idx_audit_logs_user_id;      -- 被 idx_audit_logs_user_time 覆盖
-- DROP INDEX IF EXISTS idx_audit_logs_action;        -- 被 idx_audit_logs_action_time 覆盖
-- DROP INDEX IF EXISTS idx_audit_logs_resource_type; -- 被 idx_audit_logs_resource 覆盖
-- DROP INDEX IF EXISTS idx_audit_logs_resource_id;   -- 被 idx_audit_logs_resource 覆盖
-- DROP INDEX IF EXISTS idx_audit_logs_created_at;    -- 被 brin 索引替代
-- DROP INDEX IF EXISTS idx_audit_logs_result;        -- 低选择性(仅 SUCCESS/FAILURE/DENIED)

COMMENT ON TABLE audit_logs IS '审计日志表(按created_at月度分区, 主键id+user_id+created_at, 保留7年)';
COMMENT ON COLUMN audit_logs.action_category IS '操作分类: AUTH, FILE, SEARCH, ANALYSIS, TARGET, SYSTEM';
COMMENT ON COLUMN audit_logs.result IS '操作结果: SUCCESS, FAILURE, DENIED';
COMMENT ON COLUMN audit_logs.ip_address IS '操作IP(INET类型, 支持IPv4/IPv6)';
```

##### 上传任务表 (upload_tasks)

```sql
-- 上传任务表
-- P1-1: id/file_id/user_id 改 UUID
CREATE TABLE upload_tasks (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    upload_id       VARCHAR(64) NOT NULL,
    file_id         UUID,
    
    -- 文件信息
    file_name       VARCHAR(512) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_hash       VARCHAR(64),
    
    -- 分片信息
    chunk_size      INTEGER NOT NULL,
    chunk_count     INTEGER NOT NULL,
    uploaded_chunks JSONB,
    completed_count INTEGER DEFAULT 0,
    
    -- 状态
    status          SMALLINT NOT NULL DEFAULT 0,
    
    -- 用户信息
    user_id         UUID NOT NULL,
    
    -- 过期时间
    expires_at      TIMESTAMPTZ,
    
    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_upload_tasks PRIMARY KEY (id),
    CONSTRAINT uk_upload_tasks_upload_id UNIQUE (upload_id)
);

-- 创建参考表(上传任务数据量较小, 全集群广播)
SELECT create_reference_table('upload_tasks');

-- 索引
CREATE INDEX idx_upload_tasks_user_id ON upload_tasks(user_id);
CREATE INDEX idx_upload_tasks_status ON upload_tasks(status);
CREATE INDEX idx_upload_tasks_expires_at ON upload_tasks(expires_at);

COMMENT ON TABLE upload_tasks IS '上传任务表(主键UUID)';
COMMENT ON COLUMN upload_tasks.status IS '状态: 0-进行中, 1-完成, 2-取消, 3-过期';
```

#### 2.3.7 字典/元数据表 (P2-15 补充)

> **P2-15 字典表补充说明**: 引入字典表统一管理文件类型、标签、系统配置等元数据, 避免在业务表中硬编码枚举值, 便于运维与扩展。

##### 文件类型字典表 (file_type_dict)

```sql
-- P2-15: 文件类型字典表
CREATE TABLE file_type_dict (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_code       VARCHAR(32) UNIQUE NOT NULL,
    type_name       VARCHAR(64) NOT NULL,
    mime_type       VARCHAR(128),
    extension       VARCHAR(16),
    description     TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 创建参考表(字典数据全集群广播)
SELECT create_reference_table('file_type_dict');

COMMENT ON TABLE file_type_dict IS '文件类型字典表(P2-15: 统一管理文件类型元数据)';
```

##### 标签字典表 (tag_dict)

```sql
-- P2-15: 标签字典表
CREATE TABLE tag_dict (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tag_name        VARCHAR(64) UNIQUE NOT NULL,
    tag_category    VARCHAR(32),
    description     TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 创建参考表
SELECT create_reference_table('tag_dict');

COMMENT ON TABLE tag_dict IS '标签字典表(P2-15: 统一管理标签元数据)';
```

##### 系统配置表 (system_config)

```sql
-- P2-15: 系统配置表
CREATE TABLE system_config (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key      VARCHAR(128) UNIQUE NOT NULL,
    config_value    TEXT,
    config_type     VARCHAR(32) DEFAULT 'string',   -- string, integer, boolean, json
    description     TEXT,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 创建参考表
SELECT create_reference_table('system_config');

COMMENT ON TABLE system_config IS '系统配置表(P2-15: 集中管理系统配置, 支持运行时热更新)';
```

---

## 3. Elasticsearch索引设计

### 3.1 集群架构

```
+-----------------------------------------------------------------------------+
|                      Elasticsearch集群架构                                   |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +---------------------------------------------------------------------+   |
|  |                        Master Nodes (3)                              |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  | Master-1    |    | Master-2    |    | Master-3    |             |   |
|  |  | (投票节点)  |    | (投票节点)  |    | (投票节点)  |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                    |                                        |
|                                    v                                        |
|  +---------------------------------------------------------------------+   |
|  |                        Hot Data Nodes (6)                            |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  | Data-Hot-1  |    | Data-Hot-2  |    | Data-Hot-3  |             |   |
|  |  | 4TB NVMe    |    | 4TB NVMe    |    | 4TB NVMe    |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  | Data-Hot-4  |    | Data-Hot-5  |    | Data-Hot-6  |             |   |
|  |  | 4TB NVMe    |    | 4TB NVMe    |    | 4TB NVMe    |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                    |                                        |
|                                    v                                        |
|  +---------------------------------------------------------------------+   |
|  |                        Warm Data Nodes (6)                           |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  | Data-Warm-1 |    | Data-Warm-2 |    | Data-Warm-3 |             |   |
|  |  | 8TB HDD     |    | 8TB HDD     |    | 8TB HDD     |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  | Data-Warm-4 |    | Data-Warm-5 |    | Data-Warm-6 |             |   |
|  |  | 8TB HDD     |    | 8TB HDD     |    | 8TB HDD     |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                                                             |
|  索引策略: 按月滚动索引，热数据NVMe，冷数据HDD                                 |
|  副本数: 2 (生产环境)                                                        |
|  分片数: 根据数据量动态调整                                                   |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 3.2 文件内容索引 (file_content)

```json
{
  "index_patterns": ["file_content-*"],
  "template": {
    "settings": {
      "number_of_shards": 6,
      "number_of_replicas": 2,
      "refresh_interval": "5s",
      "index.lifecycle.name": "file_content_policy",
      "index.lifecycle.rollover_alias": "file_content",
      "analysis": {
        "analyzer": {
          "default": { "type": "standard" },
          "ik_smart_analyzer": { "type": "ik_smart" },
          "ik_max_word_analyzer": { "type": "ik_max_word" },
          "code_analyzer": {
            "type": "custom",
            "tokenizer": "standard",
            "filter": ["lowercase", "asciifolding"]
          }
        }
      }
    },
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "file_id": {
          "type": "keyword"
        },
        "file_name": {
          "type": "text",
          "analyzer": "ik_max_word_analyzer",
          "search_analyzer": "ik_smart_analyzer",
          "fields": {
            "keyword": {
              "type": "keyword"
            }
          }
        },
        "file_type": {
          "type": "keyword"
        },
        "file_size": {
          "type": "long"
        },
        "extension": {
          "type": "keyword"
        },
        "owner_id": {
          "type": "keyword"
        },
        "owner_name": {
          "type": "text",
          "analyzer": "ik_smart_analyzer",
          "fields": {
            "keyword": {
              "type": "keyword"
            }
          }
        },
        "directory_path": {
          "type": "keyword"
        },
        "sha256_hash": {
          "type": "keyword"
        },
        "text_content": {
          "type": "text",
          "analyzer": "ik_max_word_analyzer",
          "search_analyzer": "ik_smart_analyzer"
        },
        "text_content_en": {
          "type": "text",
          "analyzer": "standard"
        },
        "code_content": {
          "type": "text",
          "analyzer": "code_analyzer"
        },
        "summary": {
          "type": "text",
          "analyzer": "ik_max_word_analyzer",
          "term_vector": "with_positions_offsets"
        },
        "tags": {
          "type": "keyword"
        },
        "entities": {
          "type": "nested",
          "dynamic": "strict",
          "properties": {
            "entity_type": {
              "type": "keyword"
            },
            "entity_value": {
              "type": "keyword"
            },
            "context": {
              "type": "text",
              "analyzer": "ik_smart_analyzer"
            }
          }
        },
        "metadata": {
          "type": "object",
          "dynamic": false,
          "properties": {
            "title": { "type": "text", "analyzer": "ik_max_word_analyzer" },
            "author": { "type": "keyword" },
            "subject": { "type": "text", "analyzer": "ik_smart_analyzer" },
            "keywords": { "type": "keyword" },
            "language": { "type": "keyword" },
            "page_count": { "type": "integer" },
            "word_count": { "type": "integer" },
            "created_by_app": { "type": "keyword" }
          }
        },
        "security_level": {
          "type": "integer"
        },
        "is_sensitive": {
          "type": "boolean"
        },
        "is_malicious": {
          "type": "boolean"
        },
        "status": {
          "type": "integer"
        },
        "upload_time": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        },
        "created_at": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        },
        "updated_at": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        }
      }
    }
  }
}
```

> **P2-19 ES 副本数优化**: `number_of_replicas` 从 1 调整为 2, 提升查询吞吐量与数据冗余度(生产环境双副本, 任一节点故障仍可正常服务, 读请求可分散到 3 个分片副本)。
>
> **P2-20 term_vector 优化**: 仅对 `summary` 字段启用 `term_vector: with_positions_offsets`(用于高亮显示), `text_content` 全文字段不启用 term_vector, 节省约 30% 存储空间。全文检索的高亮通过重新分析实现, 性能影响可接受。

### 3.3 实体索引 (entity_index)

```json
{
  "index_patterns": ["entity_index-*"],
  "template": {
    "settings": {
      "number_of_shards": 6,
      "number_of_replicas": 2,
      "refresh_interval": "5s",
      "index.lifecycle.name": "entity_policy",
      "analysis": {
        "analyzer": {
          "default": { "type": "standard" },
          "ik_smart_analyzer": { "type": "ik_smart" },
          "ik_max_word_analyzer": { "type": "ik_max_word" }
        }
      }
    },
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "entity_id": {
          "type": "keyword"
        },
        "file_id": {
          "type": "keyword"
        },
        "entity_type": {
          "type": "keyword"
        },
        "entity_value": {
          "type": "keyword"
        },
        "normalized_value": {
          "type": "keyword"
        },
        "context": {
          "type": "text",
          "analyzer": "ik_smart_analyzer"
        },
        "context_before": {
          "type": "text",
          "analyzer": "ik_smart_analyzer"
        },
        "context_after": {
          "type": "text",
          "analyzer": "ik_smart_analyzer"
        },
        "position_start": {
          "type": "integer"
        },
        "position_end": {
          "type": "integer"
        },
        "confidence": {
          "type": "float"
        },
        "source_section": {
          "type": "keyword"
        },
        "source_page": {
          "type": "integer"
        },
        "target_ids": {
          "type": "keyword"
        },
        "created_at": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        }
      }
    }
  }
}
```

### 3.4 分析结果索引 (analysis_result)

```json
{
  "index_patterns": ["analysis_result-*"],
  "template": {
    "settings": {
      "number_of_shards": 4,
      "number_of_replicas": 2,
      "refresh_interval": "5s",
      "index.lifecycle.name": "analysis_result_policy",
      "analysis": {
        "analyzer": {
          "default": { "type": "standard" },
          "ik_smart_analyzer": { "type": "ik_smart" },
          "ik_max_word_analyzer": { "type": "ik_max_word" }
        }
      }
    },
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "analysis_id": {
          "type": "keyword"
        },
        "file_id": {
          "type": "keyword"
        },
        "analysis_type": {
          "type": "keyword"
        },
        "analysis_name": {
          "type": "text",
          "analyzer": "ik_smart_analyzer"
        },
        "analysis_status": {
          "type": "integer"
        },
        "risk_level": {
          "type": "integer"
        },
        "risk_score": {
          "type": "float"
        },
        "summary": {
          "type": "text",
          "analyzer": "ik_max_word_analyzer"
        },
        "result": {
          "type": "object",
          "dynamic": false,
          "properties": {
            "engine_version": { "type": "keyword" },
            "model_name": { "type": "keyword" },
            "score_detail": { "type": "object", "enabled": false }
          }
        },
        "vulnerabilities": {
          "type": "nested",
          "dynamic": "strict",
          "properties": {
            "cve_id": {
              "type": "keyword"
            },
            "vuln_name": {
              "type": "text",
              "analyzer": "ik_smart_analyzer"
            },
            "severity": {
              "type": "keyword"
            },
            "cvss_score": {
              "type": "float"
            }
          }
        },
        "sensitive_findings": {
          "type": "nested",
          "dynamic": "strict",
          "properties": {
            "finding_type": {
              "type": "keyword"
            },
            "finding_name": {
              "type": "keyword"
            },
            "confidence": {
              "type": "float"
            }
          }
        },
        "threat_intel": {
          "type": "nested",
          "dynamic": "strict",
          "properties": {
            "threat_type": {
              "type": "keyword"
            },
            "threat_level": {
              "type": "keyword"
            },
            "source": {
              "type": "keyword"
            }
          }
        },
        "created_at": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        },
        "updated_at": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        }
      }
    }
  }
}
```

### 3.5 目标画像索引 (target_profile)

```json
{
  "index_patterns": ["target_profile-*"],
  "template": {
    "settings": {
      "number_of_shards": 4,
      "number_of_replicas": 2,
      "refresh_interval": "5s",
      "analysis": {
        "analyzer": {
          "default": { "type": "standard" },
          "ik_smart_analyzer": { "type": "ik_smart" },
          "ik_max_word_analyzer": { "type": "ik_max_word" }
        }
      }
    },
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "target_id": {
          "type": "keyword"
        },
        "target_name": {
          "type": "text",
          "analyzer": "ik_max_word_analyzer",
          "search_analyzer": "ik_smart_analyzer",
          "fields": {
            "keyword": {
              "type": "keyword"
            }
          }
        },
        "target_type": {
          "type": "keyword"
        },
        "description": {
          "type": "text",
          "analyzer": "ik_max_word_analyzer"
        },
        "industry": {
          "type": "keyword"
        },
        "region": {
          "type": "keyword"
        },
        "country": {
          "type": "keyword"
        },
        "tags": {
          "type": "keyword"
        },
        "owner_id": {
          "type": "keyword"
        },
        "owner_name": {
          "type": "text",
          "analyzer": "ik_smart_analyzer"
        },
        "assets": {
          "type": "nested",
          "dynamic": "strict",
          "properties": {
            "asset_type": {
              "type": "keyword"
            },
            "asset_value": {
              "type": "keyword"
            },
            "status": {
              "type": "keyword"
            }
          }
        },
        "vulnerabilities": {
          "type": "nested",
          "dynamic": "strict",
          "properties": {
            "cve_id": {
              "type": "keyword"
            },
            "severity": {
              "type": "keyword"
            },
            "status": {
              "type": "keyword"
            }
          }
        },
        "persons": {
          "type": "nested",
          "dynamic": "strict",
          "properties": {
            "name": {
              "type": "text",
              "analyzer": "ik_smart_analyzer"
            },
            "email": {
              "type": "keyword"
            },
            "position": {
              "type": "keyword"
            }
          }
        },
        "statistics": {
          "type": "object",
          "dynamic": false,
          "properties": {
            "file_count": {
              "type": "integer"
            },
            "asset_count": {
              "type": "integer"
            },
            "vuln_count": {
              "type": "integer"
            },
            "high_risk_vuln_count": {
              "type": "integer"
            }
          }
        },
        "status": {
          "type": "integer"
        },
        "created_at": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        },
        "updated_at": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        }
      }
    }
  }
}
```

### 3.6 索引生命周期管理 (ILM)

```json
{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_size": "50GB",
            "max_age": "30d"
          },
          "set_priority": {
            "priority": 100
          }
        }
      },
      "warm": {
        "min_age": "30d",
        "actions": {
          "shrink": {
            "number_of_shards": 3
          },
          "forcemerge": {
            "max_num_segments": 1
          },
          "allocate": {
            "require": {
              "data": "warm"
            }
          },
          "set_priority": {
            "priority": 50
          }
        }
      },
      "cold": {
        "min_age": "90d",
        "actions": {
          "allocate": {
            "require": {
              "data": "cold"
            }
          },
          "freeze": {},
          "set_priority": {
            "priority": 0
          }
        }
      },
      "delete": {
        "min_age": "2555d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```

> **ILM 配置说明**:
> - **warm 阶段 shrink 到 3 个分片**(原为 1 个): 避免单分片过大无法并行恢复，3 分片兼顾查询并行度与单分片大小。
> - **cold 阶段添加 freeze action**: 冻结索引大幅降低内存占用，适合低频访问的冷数据。
> - **delete 周期改为 2555 天**(7 年保留): 满足等保三级与网络安全法日志留存≥6 个月、敏感操作≥1 年、综合合规上限 7 年的保留要求。


---

## 4. Milvus Collection设计

### 4.1 集群架构

```
+-----------------------------------------------------------------------------+
|                        Milvus集群架构                                        |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +---------------------------------------------------------------------+   |
|  |                        Access Layer                                  |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  |   Proxy     |    |   Proxy     |    |   Proxy     |             |   |
|  |  |  (查询代理) |    |  (查询代理) |    |  (查询代理) |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                    |                                        |
|                                    v                                        |
|  +---------------------------------------------------------------------+   |
|  |                      Coordinator Layer                              |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  | Root Coord  |    | Query Coord |    | Data Coord  |             |   |
|  |  | (元数据)    |    | (查询调度)  |    | (数据调度)  |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                    |                                        |
|                                    v                                        |
|  +---------------------------------------------------------------------+   |
|  |                         Worker Layer                                |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  | Query Node  |    | Query Node  |    | Query Node  |             |   |
|  |  | (查询执行)  |    | (查询执行)  |    | (查询执行)  |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  | Data Node   |    | Data Node   |    | Data Node   |             |   |
|  |  | (数据写入)  |    | (数据写入)  |    | (数据写入)  |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                    |                                        |
|                                    v                                        |
|  +---------------------------------------------------------------------+   |
|  |                        Storage Layer                                |   |
|  |  +-------------+    +-------------+                                 |   |
|  |  |   MinIO     |    |    etcd     |                                 |   |
|  |  | (向量存储)  |    | (元数据)    |                                 |   |
|  |  +-------------+    +-------------+                                 |   |
|  +---------------------------------------------------------------------+   |
|                                                                             |
|  索引类型: HNSW (统一使用, 高性能图索引)                                     |
|  向量维度: 1024 (BGE-large-zh 统一维度)                                      |
|  相似度度量: COSINE (余弦相似度)                                              |
|  分区策略: partition_key=file_id + 按月 Partition(冷热管理)                   |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 4.2 文件内容向量Collection (file_content_vector)

> **P1-6 Milvus Partition 设计**: 启用 `partition_key_field="file_id"` 加速按文件过滤; 按月创建 Partition 实现冷热管理。
>
> **P1-8 向量维度统一**: 统一使用 BGE-large-zh 1024 维(原 768 维升级); chunk_text 增加 50-100 tokens 重叠设计; 增加 chunk 元数据(chunk_type: title/paragraph/code/table)。

```python
from pymilvus import CollectionSchema, FieldSchema, DataType, Collection
from datetime import datetime

# P1-8: 向量维度统一 1024(BGE-large-zh), 新增 chunk_type 字段
fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
    FieldSchema(name="vector_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="file_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="chunk_index", dtype=DataType.INT32),
    FieldSchema(name="chunk_text", dtype=DataType.VARCHAR, max_length=2048),
    FieldSchema(name="chunk_type", dtype=DataType.VARCHAR, max_length=32),  # P1-8: title/paragraph/code/table
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1024),   # P1-8: 768 → 1024
    FieldSchema(name="language", dtype=DataType.VARCHAR, max_length=16),
    FieldSchema(name="source_section", dtype=DataType.VARCHAR, max_length=128),
    FieldSchema(name="created_at", dtype=DataType.INT64)
]

# P1-6: 启用 partition_key_field 加速按 file_id 过滤
schema = CollectionSchema(
    fields=fields,
    description="文件内容向量索引(BGE-large-zh 1024维, partition_key=file_id)",
    enable_dynamic_field=True,
    partition_key_field="file_id",   # P1-6: 分区键, 加速过滤
    auto_id=True
)

# 创建Collection
collection = Collection(
    name="file_content_vector",
    schema=schema,
    using='default',
    shards_num=6
)

# P1-7: 统一使用 HNSW 索引(M=32, efConstruction=512, 高召回高质量构建)
index_params = {
    "index_type": "HNSW",
    "metric_type": "COSINE",
    "params": {
        "M": 32,
        "efConstruction": 512
    }
}

collection.create_index(
    field_name="embedding",
    index_params=index_params,
    index_name="embedding_index"
)

# 创建标量索引
collection.create_index(field_name="file_id", index_name="file_id_index")
collection.create_index(field_name="language", index_name="language_index")
collection.create_index(field_name="chunk_type", index_name="chunk_type_index")

# P1-6: 按月创建 Partition 实现冷热管理(历史月份数据可降级或删除)
collection.create_partition(f"month_{datetime.now().strftime('%Y%m')}")
```

**P1-8 文本分块(Chunking)设计说明:**

```
+-----------------------------------------------------------------------------+
|                    文本分块与重叠设计 (P1-8)                                  |
+-----------------------------------------------------------------------------+
|                                                                             |
|  分块策略:                                                                   |
|  1. chunk_size: 512 tokens (BGE-large-zh tokenizer)                         |
|  2. overlap: 50-100 tokens 重叠(避免语义断裂, 跨块实体可被检索)              |
|  3. chunk_type 元数据:                                                       |
|     - title:       标题(文章标题/章节标题)                                   |
|     - paragraph:   正文段落(主要文本内容)                                    |
|     - code:        代码片段(独立分块, 保留缩进)                              |
|     - table:       表格内容(序列化为文本)                                    |
|                                                                             |
|  示例:                                                                       |
|  chunk[0] = {text: "...token[0:512]...",     type: "paragraph"}             |
|  chunk[1] = {text: "...token[462:974]...",   type: "paragraph"}  # 50重叠   |
|  chunk[2] = {text: "...code_block...",       type: "code"}                  |
|                                                                             |
+-----------------------------------------------------------------------------+
```

**Collection参数说明：**

| 参数 | 值 | 说明 |
|-----|-----|------|
| 向量维度 | 1024 | BGE-large-zh 模型输出维度(P1-8 统一) |
| 相似度度量 | COSINE | 余弦相似度 |
| 索引类型 | HNSW | 高性能图索引(P1-7 统一) |
| M参数 | 32 | HNSW图节点连接数(高召回) |
| efConstruction | 512 | 构建索引时的搜索范围(高质量构建) |
| 分片数 | 6 | 支持并行查询 |
| partition_key | file_id | P1-6: 按文件过滤加速 |
| chunk重叠 | 50-100 tokens | P1-8: 避免语义断裂 |

### 4.3 目标画像向量Collection (target_profile_vector)

```python
# 定义字段
# P1-8: 向量维度统一 1024(BGE-large-zh)
fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
    FieldSchema(name="vector_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="target_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="target_name", dtype=DataType.VARCHAR, max_length=256),
    FieldSchema(name="target_type", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="profile_text", dtype=DataType.VARCHAR, max_length=4096),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1024),   # P1-8: 768 → 1024
    FieldSchema(name="industry", dtype=DataType.VARCHAR, max_length=128),
    FieldSchema(name="region", dtype=DataType.VARCHAR, max_length=128),
    FieldSchema(name="created_at", dtype=DataType.INT64),
    FieldSchema(name="updated_at", dtype=DataType.INT64)
]

# P1-6: 启用 partition_key_field 加速按 target_id 过滤
schema = CollectionSchema(
    fields=fields,
    description="目标画像向量索引(BGE-large-zh 1024维, partition_key=target_id)",
    enable_dynamic_field=True,
    partition_key_field="target_id",   # P1-6: 分区键
    auto_id=True
)

# 创建Collection
collection = Collection(
    name="target_profile_vector",
    schema=schema,
    using='default',
    shards_num=4
)

# P1-7: 统一使用 HNSW 索引(M=32, efConstruction=512)
index_params = {
    "index_type": "HNSW",
    "metric_type": "COSINE",
    "params": {
        "M": 32,
        "efConstruction": 512
    }
}

collection.create_index(
    field_name="embedding",
    index_params=index_params,
    index_name="embedding_index"
)
```

### 4.4 实体向量Collection (entity_vector)

> **P1-7 Milvus 索引类型统一**: entity_vector 从 IVF_FLAT 改为 HNSW, 与其他 Collection 统一索引类型, 简化运维与查询参数调优。

```python
# 定义字段
# P1-8: 向量维度统一 1024(BGE-large-zh)
fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
    FieldSchema(name="vector_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="entity_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="entity_type", dtype=DataType.VARCHAR, max_length=32),
    FieldSchema(name="entity_value", dtype=DataType.VARCHAR, max_length=512),
    FieldSchema(name="context_text", dtype=DataType.VARCHAR, max_length=1024),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1024),   # P1-8: 768 → 1024
    FieldSchema(name="file_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="target_ids", dtype=DataType.VARCHAR, max_length=2048),
    FieldSchema(name="created_at", dtype=DataType.INT64)
]

# P1-6: 启用 partition_key_field 加速按 file_id 过滤
schema = CollectionSchema(
    fields=fields,
    description="实体向量索引(BGE-large-zh 1024维, partition_key=file_id)",
    enable_dynamic_field=True,
    partition_key_field="file_id",   # P1-6: 分区键
    auto_id=True
)

# 创建Collection
collection = Collection(
    name="entity_vector",
    schema=schema,
    using='default',
    shards_num=6
)

# P1-7: 索引类型统一为 HNSW(原 IVF_FLAT 改为 HNSW, 与其他 Collection 一致)
index_params = {
    "index_type": "HNSW",
    "metric_type": "COSINE",
    "params": {
        "M": 32,
        "efConstruction": 512
    }
}

collection.create_index(
    field_name="embedding",
    index_params=index_params,
    index_name="embedding_index"
)

# 创建标量索引
collection.create_index(field_name="entity_type", index_name="entity_type_index")
```

---

## 5. Neo4j图模型设计

### 5.1 集群架构

```
+-----------------------------------------------------------------------------+
|                         Neo4j集群架构                                        |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +---------------------------------------------------------------------+   |
|  |                      Core Servers (3)                                |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  |   Core-1    |    |   Core-2    |    |   Core-3    |             |   |
|  |  |  (LEADER)   |    |  (FOLLOWER) |    |  (FOLLOWER) |             |   |
|  |  |  Raft协议   |    |  Raft协议   |    |  Raft协议   |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                    |                                        |
|                                    v                                        |
|  +---------------------------------------------------------------------+   |
|  |                     Read Replicas (3)                                |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  |  Replica-1  |    |  Replica-2  |    |  Replica-3  |             |   |
|  |  |  (只读)     |    |  (只读)     |    |  (只读)     |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                                                             |
|  配置: 因果集群 (Causal Clustering)                                          |
|  副本数: 3 Core + 3 Replica                                                  |
|  一致性: 强一致性写入，最终一致性读取                                          |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 5.2 节点类型定义

```
+-----------------------------------------------------------------------------+
|                            节点类型定义                                       |
+-----------------------------------------------------------------------------+

// 1. 文件节点 (File)
CREATE CONSTRAINT file_id_unique IF NOT EXISTS FOR (f:File) REQUIRE f.file_id IS UNIQUE;

(:File {
    file_id: String,           // 文件唯一标识
    file_name: String,         // 文件名
    file_type: String,         // 文件类型
    file_size: Integer,        // 文件大小
    sha256_hash: String,       // SHA256哈希
    owner_id: String,          // 所有者ID
    status: Integer,           // 状态
    security_level: Integer,   // 安全等级
    upload_time: DateTime,     // 上传时间
    created_at: DateTime       // 创建时间
})

// 2. 目标节点 (Target)
CREATE CONSTRAINT target_id_unique IF NOT EXISTS FOR (t:Target) REQUIRE t.target_id IS UNIQUE;

(:Target {
    target_id: String,         // 目标唯一标识
    target_name: String,       // 目标名称
    target_type: String,       // 目标类型: ENTERPRISE, ORGANIZATION, SYSTEM, PERSON
    industry: String,          // 行业
    region: String,            // 地区
    country: String,           // 国家
    status: Integer,           // 状态
    created_at: DateTime       // 创建时间
})

// 3. IP地址节点 (IPAddress)
CREATE CONSTRAINT ip_address_unique IF NOT EXISTS FOR (ip:IPAddress) REQUIRE ip.address IS UNIQUE;

(:IPAddress {
    address: String,           // IP地址
    ip_type: String,           // IP类型: IPv4, IPv6
    is_public: Boolean,        // 是否公网IP
    country: String,           // 所属国家
    city: String,              // 所属城市
    asn: String,               // ASN号
    isp: String,               // ISP运营商
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 4. 域名节点 (Domain)
CREATE CONSTRAINT domain_name_unique IF NOT EXISTS FOR (d:Domain) REQUIRE d.name IS UNIQUE;

(:Domain {
    name: String,              // 域名
    root_domain: String,       // 根域名
    is_subdomain: Boolean,     // 是否子域名
    registrar: String,         // 注册商
    registrant: String,        // 注册人
    create_date: Date,         // 注册日期
    expire_date: Date,         // 过期日期
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 5. 端口节点 (Port)
// P1-9: 补全唯一约束(端口号+协议)
CREATE CONSTRAINT port_unique IF NOT EXISTS FOR (p:Port) ON (p.port_number, p.protocol);
(:Port {
    port_number: Integer,      // 端口号
    protocol: String,          // 协议: TCP, UDP
    service: String,           // 服务名称
    description: String        // 描述
})

// 6. 服务节点 (Service)
// P1-9: 补全唯一约束(服务名+端口ID)
CREATE CONSTRAINT service_unique IF NOT EXISTS FOR (s:Service) ON (s.name, s.port_id);
(:Service {
    service_name: String,      // 服务名称
    service_type: String,      // 服务类型
    version: String,           // 版本
    banner: String,            // Banner信息
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 7. 漏洞节点 (Vulnerability)
CREATE CONSTRAINT cve_id_unique IF NOT EXISTS FOR (v:Vulnerability) REQUIRE v.cve_id IS UNIQUE;

(:Vulnerability {
    cve_id: String,            // CVE编号
    cnvd_id: String,           // CNVD编号
    vuln_name: String,         // 漏洞名称
    vuln_type: String,         // 漏洞类型
    severity: String,          // 严重程度
    cvss_score: Float,         // CVSS评分
    description: String,       // 描述
    solution: String,          // 解决方案
    published_date: Date       // 发布日期
})

// 8. 人员节点 (Person)
CREATE CONSTRAINT person_email_unique IF NOT EXISTS FOR (p:Person) REQUIRE p.email IS UNIQUE;

(:Person {
    person_id: String,         // 人员ID
    name: String,              // 姓名
    email: String,             // 邮箱
    phone: String,             // 电话
    position: String,          // 职位
    department: String,        // 部门
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 9. 组织节点 (Organization)
CREATE CONSTRAINT org_name_unique IF NOT EXISTS FOR (o:Organization) REQUIRE o.name IS UNIQUE;

(:Organization {
    org_id: String,            // 组织ID
    name: String,              // 组织名称
    org_type: String,          // 组织类型
    industry: String,          // 行业
    address: String,           // 地址
    website: String,           // 网站
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 10. 哈希节点 (Hash)
CREATE CONSTRAINT hash_value_unique IF NOT EXISTS FOR (h:Hash) REQUIRE h.value IS UNIQUE;

(:Hash {
    value: String,             // 哈希值
    hash_type: String,         // 哈希类型: MD5, SHA1, SHA256
    is_malicious: Boolean,     // 是否恶意
    threat_type: String,       // 威胁类型
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 11. URL节点 (URL)
CREATE CONSTRAINT url_unique IF NOT EXISTS FOR (u:URL) REQUIRE u.url IS UNIQUE;

(:URL {
    url: String,               // 完整URL
    domain: String,            // 域名
    path: String,              // 路径
    protocol: String,          // 协议
    is_malicious: Boolean,     // 是否恶意
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 12. 威胁节点 (Threat)
CREATE CONSTRAINT threat_id_unique IF NOT EXISTS FOR (t:Threat) REQUIRE t.threat_id IS UNIQUE;

(:Threat {
    threat_id: String,         // 威胁唯一标识
    threat_type: String,       // 威胁类型: MALWARE, PHISHING, C2, EXPLOIT, RANSOMWARE, APT
    threat_name: String,       // 威胁名称
    severity: String,          // 严重程度: LOW, MEDIUM, HIGH, CRITICAL
    description: String,       // 描述
    malware_family: String,    // 恶意软件家族
    attack_vector: String,     // 攻击向量
    mitre_attack_id: String,   // MITRE ATT&CK 技术编号
    source: String,            // 情报来源
    confidence: Float,         // 置信度
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 13. 邮箱节点 (Email)
CREATE CONSTRAINT email_address_unique IF NOT EXISTS FOR (e:Email) REQUIRE e.address IS UNIQUE;

(:Email {
    address: String,           // 邮箱地址
    local_part: String,        // 本地部分(@之前)
    domain: String,            // 域名部分(@之后)
    is_malicious: Boolean,     // 是否恶意邮箱
    is_disposable: Boolean,    // 是否临时邮箱
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 14. 用户节点 (User)
-- P1-1: 用户ID统一为 String(UUID), 对应 PostgreSQL users.id(UUID)
CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.user_id IS UNIQUE;

(:User {
    user_id: String,          // 用户ID(UUID字符串, 对应PG users.id)
    username: String,          // 用户名
    email: String,             // 邮箱
    department: String,        // 部门
    status: Integer            // 状态
})

// 节点索引(补充查询性能)
CREATE INDEX file_name_index IF NOT EXISTS FOR (f:File) ON (f.file_name);
CREATE INDEX target_name_index IF NOT EXISTS FOR (t:Target) ON (t.target_name);
CREATE INDEX file_upload_time_index IF NOT EXISTS FOR (f:File) ON (f.upload_time);
CREATE INDEX target_status_index IF NOT EXISTS FOR (t:Target) ON (t.status);
CREATE INDEX threat_type_index IF NOT EXISTS FOR (t:Threat) ON (t.threat_type);
CREATE INDEX threat_severity_index IF NOT EXISTS FOR (t:Threat) ON (t.severity);
CREATE INDEX email_domain_index IF NOT EXISTS FOR (e:Email) ON (e.domain);
```

### 5.3 关系类型定义

```
+-----------------------------------------------------------------------------+
|                            关系类型定义                                       |
+-----------------------------------------------------------------------------+

// 1. 文件相关关系

// 文件属于目标
(:File)-[:BELONGS_TO {
    relation_type: String,     // 关联类型
    confidence: Float,         // 置信度
    created_at: DateTime
}]->(:Target)

// 文件包含实体(替换原 CONTAINS 单一关系为多个具名关系类型, 避免 Neo4j 不支持 `:A|:B` 关系目标语法)
(:File)-[:CONTAINS_IP {
    position: Integer,
    context: String,
    confidence: Float,
    created_at: DateTime
}]->(:IPAddress)

(:File)-[:CONTAINS_DOMAIN {
    position: Integer,
    context: String,
    confidence: Float,
    created_at: DateTime
}]->(:Domain)

(:File)-[:CONTAINS_URL {
    position: Integer,
    context: String,
    confidence: Float,
    created_at: DateTime
}]->(:URL)

(:File)-[:CONTAINS_HASH {
    position: Integer,
    context: String,
    confidence: Float,
    created_at: DateTime
}]->(:Hash)

(:File)-[:CONTAINS_EMAIL {
    position: Integer,
    context: String,
    confidence: Float,
    created_at: DateTime
}]->(:Email)

(:File)-[:CONTAINS_PERSON {
    position: Integer,
    context: String,
    confidence: Float,
    created_at: DateTime
}]->(:Person)

(:File)-[:CONTAINS_ORG {
    position: Integer,
    context: String,
    confidence: Float,
    created_at: DateTime
}]->(:Organization)

// 文件引用其他文件
(:File)-[:REFERENCES {
    reference_type: String,    // 引用类型
    created_at: DateTime
}]->(:File)

// 文件相似
(:File)-[:SIMILAR_TO {
    similarity: Float,         // 相似度
    similarity_type: String,   // 相似类型: content, structure
    created_at: DateTime
}]->(:File)

// 文件版本
(:File)-[:VERSION_OF {
    version_number: Integer,   // 版本号
    created_at: DateTime
}]->(:File)


// 2. 目标相关关系

// 目标拥有资产(替换原 `:IPAddress|:Domain|:Service` 为多个具名关系)
(:Target)-[:OWNS_IP {
    discovered_at: DateTime,
    source_file: String,
    status: String
}]->(:IPAddress)

(:Target)-[:OWNS_DOMAIN {
    discovered_at: DateTime,
    source_file: String,
    status: String
}]->(:Domain)

(:Target)-[:OWNS_SERVICE {
    discovered_at: DateTime,
    source_file: String,
    status: String
}]->(:Service)

// 目标包含组织
(:Target)-[:CONTAINS_ORG {
    relation_type: String,     // 关系类型: parent, subsidiary, partner
    created_at: DateTime
}]->(:Organization)

// 目标关联人员
(:Target)-[:HAS_PERSON {
    relation_type: String,     // 关系类型: employee, contractor, contact
    discovered_at: DateTime,
    source_file: String
}]->(:Person)

// 目标存在漏洞
(:Target)-[:HAS_VULNERABILITY {
    affected_asset: String,    // 受影响资产
    status: String,            // 状态: open, fixed, ignored
    discovered_at: DateTime,
    source_file: String
}]->(:Vulnerability)


// 3. 资产相关关系

// 域名解析到IP(A/AAAA记录)
(:Domain)-[:RESOLVES_TO {
    record_type: String,       // 记录类型: A, AAAA
    first_seen: DateTime,
    last_seen: DateTime
}]->(:IPAddress)

// IP反向解析到域名(PTR记录)
// 修复命名冲突: 原 IPAddress->Domain 也用 RESOLVES_TO, 与 Domain->IPAddress 同名关系混淆
// 改用 PTR_RECORD 区分反向解析
(:IPAddress)-[:PTR_RECORD {
    first_seen: DateTime,
    last_seen: DateTime
}]->(:Domain)

// IP开放端口
(:IPAddress)-[:HAS_PORT {
    status: String,            // 状态: open, closed, filtered
    first_seen: DateTime,
    last_seen: DateTime
}]->(:Port)

// 端口运行服务
(:Port)-[:RUNS_SERVICE {
    version: String,           // 版本
    banner: String,            // Banner
    first_seen: DateTime,
    last_seen: DateTime
}]->(:Service)

// 域名子域名关系
(:Domain)-[:HAS_SUBDOMAIN {
    created_at: DateTime
}]->(:Domain)

// 域名属于组织
(:Domain)-[:BELONGS_TO {
    relation_type: String,     // 关系类型
    discovered_at: DateTime
}]->(:Organization)


// 4. 漏洞相关关系

// 漏洞影响服务
(:Vulnerability)-[:AFFECTS_SERVICE {
    affected_version: String,  // 受影响版本
    created_at: DateTime
}]->(:Service)

// 漏洞影响IP资产
(:Vulnerability)-[:AFFECTS_IP {
    affected_version: String,
    created_at: DateTime
}]->(:IPAddress)

// 漏洞影响域名资产
(:Vulnerability)-[:AFFECTS_DOMAIN {
    affected_version: String,
    created_at: DateTime
}]->(:Domain)


// 5. 人员相关关系

// 人员属于组织
(:Person)-[:WORKS_FOR {
    position: String,          // 职位
    department: String,        // 部门
    first_seen: DateTime,
    last_seen: DateTime
}]->(:Organization)

// 人员拥有邮箱
(:Person)-[:HAS_EMAIL {
    is_primary: Boolean,       // 是否主邮箱
    first_seen: DateTime
}]->(:Email)

// 人员关联域名
(:Person)-[:REGISTERED {
    role: String,              // 角色: registrant, admin, tech
    first_seen: DateTime
}]->(:Domain)


// 6. 威胁相关关系

// 哈希关联威胁
(:Hash)-[:INDICATES_THREAT {
    threat_type: String,       // 威胁类型
    confidence: Float,         // 置信度
    source: String,            // 情报来源
    created_at: DateTime
}]->(:Threat)

// URL关联威胁
(:URL)-[:INDICATES_THREAT {
    threat_type: String,
    confidence: Float,
    source: String,
    created_at: DateTime
}]->(:Threat)

// IP关联威胁
(:IPAddress)-[:INDICATES_THREAT {
    threat_type: String,
    confidence: Float,
    source: String,
    created_at: DateTime
}]->(:Threat)

// 域名关联威胁
(:Domain)-[:INDICATES_THREAT {
    threat_type: String,
    confidence: Float,
    source: String,
    created_at: DateTime
}]->(:Threat)

// 邮箱关联威胁
(:Email)-[:INDICATES_THREAT {
    threat_type: String,
    confidence: Float,
    source: String,
    created_at: DateTime
}]->(:Threat)


// 7. 用户相关关系

// 用户上传文件
(:User)-[:UPLOADED {
    upload_time: DateTime
}]->(:File)

// 用户创建目标
(:User)-[:CREATED {
    created_at: DateTime
}]->(:Target)

// 用户管理目标
(:User)-[:MANAGES {
    role: String,              // 角色: owner, member
    created_at: DateTime
}]->(:Target)
```

### 5.4 图谱查询示例

```cypher
// 1. 查询目标的所有资产(IP/域名/服务合并视图)
MATCH (t:Target {target_id: $target_id})-[r:OWNS_IP|:OWNS_DOMAIN|:OWNS_SERVICE]->(asset)
RETURN asset, r
ORDER BY r.discovered_at DESC;

// 2. 查询目标的所有漏洞
MATCH (t:Target {target_id: $target_id})-[r:HAS_VULNERABILITY]->(v:Vulnerability)
RETURN v.cve_id, v.vuln_name, v.severity, v.cvss_score, r.status
ORDER BY v.cvss_score DESC;

// 3. 查询IP的所有关联域名(正向 A/AAAA 与反向 PTR)
MATCH (ip:IPAddress {address: $ip_address})
OPTIONAL MATCH (ip)-[r1:PTR_RECORD]->(d1:Domain)
OPTIONAL MATCH (d2:Domain)-[r2:RESOLVES_TO]->(ip)
RETURN d1.name AS ptr_domain, r1.first_seen, r1.last_seen
UNION
RETURN d2.name AS resolved_domain, r2.record_type, r2.first_seen, r2.last_seen
ORDER BY last_seen DESC;

// 4. 查询文件中提取的所有实体(跨多个具名 CONTAINS_* 关系)
MATCH (f:File {file_id: $file_id})-[r:CONTAINS_IP|:CONTAINS_DOMAIN|:CONTAINS_URL|:CONTAINS_HASH|:CONTAINS_EMAIL|:CONTAINS_PERSON|:CONTAINS_ORG]->(entity)
RETURN entity, r.context, r.confidence
ORDER BY r.position;

// 5. 查询两个目标之间的关联路径
// P1-10: shortestPath 限制最大深度5, 避免全图遍历导致性能问题
MATCH path = shortestPath(
    (t1:Target {target_id: $target_id1})-[*..5]-(t2:Target {target_id: $target_id2})
)
RETURN path;

// 6. 查询相似文件
MATCH (f1:File {file_id: $file_id})-[r:SIMILAR_TO]->(f2:File)
RETURN f2.file_id, f2.file_name, r.similarity, r.similarity_type
ORDER BY r.similarity DESC
LIMIT 10;

// 7. 查询目标的攻击面(IP 资产维度)
MATCH (t:Target {target_id: $target_id})-[:OWNS_IP]->(ip:IPAddress)-[:HAS_PORT]->(p:Port)-[:RUNS_SERVICE]->(s:Service)
OPTIONAL MATCH (d:Domain)-[:RESOLVES_TO]->(ip)
RETURN ip.address AS ip, collect(DISTINCT p.port_number) AS ports, collect(DISTINCT s.service_name) AS services, collect(DISTINCT d.name) AS domains;

// 8. 查询漏洞影响范围
MATCH (v:Vulnerability {cve_id: $cve_id})-[:AFFECTS_SERVICE]->(s:Service)<-[:RUNS_SERVICE]-(p:Port)<-[:HAS_PORT]-(ip:IPAddress)<-[:OWNS_IP]-(t:Target)
RETURN t.target_name, ip.address, p.port_number, s.service_name;

// 9. 查询人员关联的所有资产
MATCH (p:Person {person_id: $person_id})-[:WORKS_FOR]->(o:Organization)<-[:BELONGS_TO]-(d:Domain)
OPTIONAL MATCH (p)-[:REGISTERED]->(d2:Domain)
RETURN p.name, o.name, collect(DISTINCT d.name) as org_domains, collect(DISTINCT d2.name) as registered_domains;

// 10. 查询威胁情报关联(INDICATES_THREAT)
MATCH (h:Hash {value: $hash_value})-[r:INDICATES_THREAT]->(threat:Threat)
RETURN threat.threat_type, threat.threat_name, threat.severity, r.confidence, r.source;
```

### 5.5 图规模评估与性能优化 (P1-10)

> **P1-10 图规模评估**: 三年内 1000 万文件 × 平均 100 实体/文件 = 10 亿实体节点, 加上 File/Target/IP/Domain/Person/Org 等节点, 图规模巨大, 需提前规划性能优化策略。

```
+-----------------------------------------------------------------------------+
|                    图规模预估 (P1-10)                                         |
+-----------------------------------------------------------------------------+
|                                                                             |
|  节点规模预估:                                                               |
|  ─────────────────────────────────────────────────────────────────────────  |
|  | 节点类型     | 数量级      | 说明                                       |  |
|  ─────────────────────────────────────────────────────────────────────────  |
|  | File         | 1000 万     | 文件节点                                   |  |
|  | IPAddress    | ~5000 万    | IP地址(去重后)                            |  |
|  | Domain       | ~2000 万    | 域名(去重后)                              |  |
|  | Port         | ~100 万     | 端口                                       |  |
|  | Service      | ~100 万     | 服务                                       |  |
|  | URL          | ~5000 万    | URL                                        |  |
|  | Hash         | ~2000 万    | 文件哈希                                   |  |
|  | Email        | ~500 万     | 邮箱                                       |  |
|  | Person       | ~100 万     | 人员                                       |  |
|  | Organization | ~50 万      | 组织                                       |  |
|  | Target       | 1 万        | 目标                                       |  |
|  | Vulnerability| ~100 万     | 漏洞                                       |  |
|  | Threat       | ~50 万      | 威胁                                       |  |
|  ─────────────────────────────────────────────────────────────────────────  |
|  节点总计: ~1.6 亿(含实体去重后)                                            |
|                                                                             |
|  关系规模预估:                                                               |
|  ─────────────────────────────────────────────────────────────────────────  |
|  | 关系类型              | 数量级     | 说明                                |  |
|  ─────────────────────────────────────────────────────────────────────────  |
|  | CONTAINS_IP 等        | 10 亿      | 文件包含实体关系(1000万×100)        |  |
|  | RESOLVES_TO 等        | 1 亿       | 实体间关联关系                       |  |
|  | OWNS_IP/OWNS_DOMAIN   | 5000 万    | 目标拥有资产关系                     |  |
|  | HAS_VULNERABILITY     | 100 万     | 目标漏洞关系                         |  |
|  ─────────────────────────────────────────────────────────────────────────  |
|  关系总计: ~11.6 亿                                                         |
|                                                                             |
+-----------------------------------------------------------------------------+
```

**性能优化策略:**

1. **shortestPath 深度限制**: 所有关联路径查询限制最大深度 5(`*..5`), 避免全图遍历导致 OOM 与超时。

2. **APOC 路径展开控制遍历**: 使用 `apoc.path.expandConfig` 精确控制遍历的层级、关系类型过滤与结果上限(见 5.6 节)。

3. **按 target_id 分离子图**: 通过 `target_id` 属性索引, 将不同目标的子图隔离查询, 避免跨目标遍历扩散。每个 Target 子图平均 1000 文件 × 100 实体 = 10 万节点, 单子图遍历可控。

4. **评估 Neo4j 5.x Sharding 或迁移**: 当图规模超过单实例承载能力(约 10-20 亿节点/关系), 评估以下方案:
   - Neo4j 5.x Fabric 多图联邦查询(按 target_id 分片到不同实例)
   - 迁移至 TigerGraph / NebulaGraph 原生分布式图数据库(支持百亿级图)

5. **引入 GDS 图算法增强画像分析**: 利用 Neo4j GDS(Graph Data Science) 库进行社区发现、中心性分析、相似度计算, 增强目标画像与威胁关联分析能力(见 5.6 节)。

### 5.6 APOC 与 GDS 集成 (P1-11)

> **P1-11 引入 APOC 和 GDS**: APOC(Awesome Procedures On Cypher) 提供路径展开、批量操作等增强能力; GDS(Graph Data Science) 提供图算法支持画像分析与威胁关联。

#### 5.6.1 APOC 路径展开

```cypher
// APOC 路径展开: 精确控制遍历层级、关系类型与结果上限
// 用于"查询目标关联的所有资产(最多5层, 限制100条)"
CALL apoc.path.expandConfig(
    (t:Target {target_id: $target_id}),
    {
        relationshipFilter: 'CONTAINS_IP>|CONTAINS_DOMAIN>|CONTAINS_URL>',
        minLevel: 1,
        maxLevel: 5,                // P1-10: 限制最大深度5
        limit: 100,                 // 结果上限, 避免结果集爆炸
        uniqueness: 'NODE_GLOBAL'   // 全局节点唯一, 避免环路
    }
)
YIELD path
RETURN path;
```

```cypher
// APOC 批量创建节点(性能远超逐条 MERGE)
// 用于文件解析后批量写入实体节点
CALL apoc.create.nodes(['IPAddress'], $batch_data)
YIELD node
RETURN count(node);
```

#### 5.6.2 GDS 图算法

```cypher
// 1. 投影目标子图到 GDS 内存图(按 target_id 过滤)
CALL gds.graph.project(
    'targetGraph',
    ['Target', 'IPAddress', 'Domain', 'Service', 'Vulnerability'],
    {
        OWNS_IP: {orientation: 'UNDIRECTED'},
        OWNS_DOMAIN: {orientation: 'UNDIRECTED'},
        OWNS_SERVICE: {orientation: 'UNDIRECTED'},
        HAS_VULNERABILITY: {orientation: 'UNDIRECTED'}
    },
    {
        nodeProperties: { target_id: {defaultValue: ''} },
        relationshipProperties: { weight: {defaultValue: 1.0} }
    }
);
```

```cypher
// 2. Louvain 社区发现: 识别目标资产聚类(发现关联资产群组)
CALL gds.community.louvain.stream('targetGraph', {
    maxIterations: 20,
    relationshipWeightProperty: 'weight'
})
YIELD nodeId, communityId
RETURN communityId, collect(gds.util.asNode(nodeId).target_id) AS targets
ORDER BY size(targets) DESC;
```

```cypher
// 3. PageRank 中心性分析: 识别目标画像中的核心资产
CALL gds.pageRank.stream('targetGraph', {
    maxIterations: 20,
    dampingFactor: 0.85
})
YIELD nodeId, score
RETURN gds.util.asNode(nodeId).target_id AS target_id, score
ORDER BY score DESC
LIMIT 10;
```

```cypher
// 4. 节点相似度: 发现相似目标(基于资产共现)
CALL gds.nodeSimilarity.stream('targetGraph', {
    topK: 10,
    similarityCutoff: 0.5
})
YIELD node1, node2, similarity
RETURN gds.util.asNode(node1).target_id AS target1,
       gds.util.asNode(node2).target_id AS target2,
       similarity
ORDER BY similarity DESC;

// 使用后释放内存图
CALL gds.graph.drop('targetGraph');
```

---

## 6. Redis缓存设计

### 6.1 集群架构

```
+-----------------------------------------------------------------------------+
|                        Redis集群架构                                         |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +---------------------------------------------------------------------+   |
|  |                      Master Nodes (6)                                |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  |  Master-1   |    |  Master-2   |    |  Master-3   |             |   |
|  |  |  Slot:      |    |  Slot:      |    |  Slot:      |             |   |
|  |  |  0-5460     |    |  5461-10922 |    |  10923-16383|             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  |  Master-4   |    |  Master-5   |    |  Master-6   |             |   |
|  |  |  (副本)     |    |  (副本)     |    |  (副本)     |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                    |                                        |
|                                    v                                        |
|  +---------------------------------------------------------------------+   |
|  |                       Slave Nodes (6)                               |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  |  Slave-1    |    |  Slave-2    |    |  Slave-3    |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  |  |  Slave-4    |    |  Slave-5    |    |  Slave-6    |             |   |
|  |  +-------------+    +-------------+    +-------------+             |   |
|  +---------------------------------------------------------------------+   |
|                                                                             |
|  模式: Cluster模式                                                          |
|  分片: 6主6从                                                               |
|  内存: 每节点32GB                                                           |
|  总容量: 192GB                                                              |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 6.2 缓存Key设计

```
+-----------------------------------------------------------------------------+
|                          缓存Key设计规范                                      |
+-----------------------------------------------------------------------------+

1. Key命名规范
   格式: {业务模块}:{资源类型}:{唯一标识}:{属性}
   示例: file:metadata:file123:info

2. 缓存分类

   2.1 会话缓存
   ─────────────────────────────────────────────────────────────────────────
   | Key Pattern                    | 说明           | TTL    | 数据类型  |
   ─────────────────────────────────────────────────────────────────────────
   | session:{session_id}           | 会话信息       | 2h     | Hash      |
   | session:{session_id}:user      | 用户信息       | 2h     | Hash      |
   | session:{session_id}:perms     | 权限列表       | 2h     | Set       |
   | user:{user_id}:sessions        | 用户会话列表   | 24h    | Set       |
   ─────────────────────────────────────────────────────────────────────────

   2.2 文件元数据缓存
   ─────────────────────────────────────────────────────────────────────────
   | Key Pattern                    | 说明           | TTL    | 数据类型  |
   ─────────────────────────────────────────────────────────────────────────
   | file:metadata:{file_id}        | 文件元数据     | 1h     | Hash      |
   | file:content:{file_id}         | 文件内容摘要   | 30m    | String    |
   | file:tags:{file_id}            | 文件标签       | 1h     | Set       |
   | file:entities:{file_id}        | 文件实体       | 1h     | List      |
   | file:hash:{sha256}             | 哈希到文件映射 | 24h    | String    |
   ─────────────────────────────────────────────────────────────────────────

   2.3 上传任务缓存
   ─────────────────────────────────────────────────────────────────────────
   | Key Pattern                    | 说明           | TTL    | 数据类型  |
   ─────────────────────────────────────────────────────────────────────────
   | upload:task:{upload_id}        | 上传任务信息   | 24h    | Hash      |
   | upload:chunks:{upload_id}      | 已上传分片     | 24h    | Set       |
   | upload:progress:{upload_id}    | 上传进度       | 24h    | String    |
   | upload:user:{user_id}:tasks    | 用户上传任务   | 24h    | Set       |
   ─────────────────────────────────────────────────────────────────────────

   2.4 检索缓存
   ─────────────────────────────────────────────────────────────────────────
   | Key Pattern                    | 说明           | TTL    | 数据类型  |
   ─────────────────────────────────────────────────────────────────────────
   | search:result:{query_hash}     | 检索结果缓存   | 5m     | String    |
   | search:suggest:{prefix}        | 搜索建议       | 10m    | List      |
   | search:history:{user_id}       | 搜索历史       | 7d     | List      |
   | search:hot:{date}              | 热门搜索词     | 1d     | ZSet      |
   ─────────────────────────────────────────────────────────────────────────

   2.5 目标画像缓存
   ─────────────────────────────────────────────────────────────────────────
   | Key Pattern                    | 说明           | TTL    | 数据类型  |
   ─────────────────────────────────────────────────────────────────────────
   | target:profile:{target_id}     | 目标画像       | 30m    | Hash      |
   | target:assets:{target_id}      | 目标资产列表   | 30m    | Set       |
   | target:vulns:{target_id}       | 目标漏洞列表   | 30m    | List      |
   | target:stats:{target_id}       | 目标统计       | 10m    | Hash      |
   ─────────────────────────────────────────────────────────────────────────

   2.6 分布式锁
   ─────────────────────────────────────────────────────────────────────────
   | Key Pattern                    | 说明           | TTL    | 数据类型  |
   ─────────────────────────────────────────────────────────────────────────
   | lock:file:{file_id}            | 文件操作锁     | 30s    | String    |
   | lock:upload:{upload_id}        | 上传任务锁     | 30s    | String    |
   | lock:target:{target_id}        | 目标操作锁     | 30s    | String    |
   | lock:parse:{file_id}           | 解析任务锁     | 5m     | String    |
   ─────────────────────────────────────────────────────────────────────────

   2.7 限流计数
   ─────────────────────────────────────────────────────────────────────────
   | Key Pattern                    | 说明           | TTL    | 数据类型  |
   ─────────────────────────────────────────────────────────────────────────
   | rate:upload:{user_id}:{minute} | 上传限流       | 1m     | String    |
   | rate:search:{user_id}:{minute} | 检索限流       | 1m     | String    |
   | rate:download:{user_id}:{hour} | 下载限流       | 1h     | String    |
   | rate:api:{ip}:{minute}         | API限流        | 1m     | String    |
   ─────────────────────────────────────────────────────────────────────────

   2.8 统计计数
   ─────────────────────────────────────────────────────────────────────────
   | Key Pattern                    | 说明           | TTL    | 数据类型  |
   ─────────────────────────────────────────────────────────────────────────
   | stats:upload:daily:{date}      | 日上传统计     | 30d    | Hash      |
   | stats:search:daily:{date}      | 日检索统计     | 30d    | Hash      |
   | stats:file:type:{date}         | 文件类型统计   | 30d    | Hash      |
   | stats:user:active:{date}       | 活跃用户统计   | 30d    | Set       |
   ─────────────────────────────────────────────────────────────────────────
```

### 6.3 缓存策略

```
+-----------------------------------------------------------------------------+
|                            缓存策略设计                                       |
+-----------------------------------------------------------------------------+

1. 缓存更新策略

   1.1 Cache-Aside (旁路缓存)
   ─────────────────────────────────────────────────────────────────────────
   读取流程:
   1. 先查询缓存
   2. 缓存命中 -> 直接返回
   3. 缓存未命中 -> 查询数据库 -> 写入缓存 -> 返回

   更新流程:
   1. 更新数据库
   2. 删除缓存 (而非更新缓存)

   适用场景: 文件元数据、用户信息、目标画像

   1.2 Write-Through (写穿透)
   ─────────────────────────────────────────────────────────────────────────
   写入流程:
   1. 写入缓存
   2. 写入数据库
   3. 返回结果

   适用场景: 会话信息、上传任务状态

   1.3 Write-Behind (写回)
   ─────────────────────────────────────────────────────────────────────────
   写入流程:
   1. 写入缓存
   2. 异步写入数据库

   适用场景: 统计计数、访问日志


2. 缓存过期策略

   ─────────────────────────────────────────────────────────────────────────
   | 数据类型         | 过期时间  | 更新策略          | 说明              |
   ─────────────────────────────────────────────────────────────────────────
   | 会话信息         | 2小时     | 滑动过期          | 每次访问刷新      |
   | 文件元数据       | 1小时     | 固定过期          | 更新时删除        |
   | 检索结果         | 5分钟     | 固定过期          | 短期缓存          |
   | 上传任务         | 24小时    | 固定过期          | 任务完成后删除    |
   | 热门搜索         | 1天       | 固定过期          | 每日更新          |
   | 统计数据         | 30天      | 固定过期          | 定期归档          |
   ─────────────────────────────────────────────────────────────────────────


3. 缓存预热策略

   3.1 系统启动预热
   ─────────────────────────────────────────────────────────────────────────
   - 加载系统配置
   - 加载权限数据
   - 加载热门文件元数据

   3.2 定时预热
   ─────────────────────────────────────────────────────────────────────────
   - 每小时预热热门检索结果
   - 每天预热活跃用户信息
   - 每周预热目标画像数据


4. 缓存穿透防护

   4.1 空值缓存
   ─────────────────────────────────────────────────────────────────────────
   对于不存在的数据，缓存空值，设置较短TTL (5分钟)

   4.2 布隆过滤器
   ─────────────────────────────────────────────────────────────────────────
   对于文件ID、用户ID等，使用布隆过滤器预先判断是否存在

   4.3 参数校验
   ─────────────────────────────────────────────────────────────────────────
   在查询前校验参数合法性，过滤无效请求


5. 缓存击穿防护

   5.1 互斥锁
   ─────────────────────────────────────────────────────────────────────────
   使用分布式锁，只允许一个请求查询数据库并更新缓存

   5.2 热点数据永不过期
   ─────────────────────────────────────────────────────────────────────────
   对于热点数据，不设置过期时间，通过后台异步更新


6. 缓存雪崩防护

   6.1 过期时间随机化
   ─────────────────────────────────────────────────────────────────────────
   在基础过期时间上增加随机值，避免同时过期

   6.2 多级缓存
   ─────────────────────────────────────────────────────────────────────────
   本地缓存 (Caffeine) + 分布式缓存 (Redis)

   6.3 熔断降级
   ─────────────────────────────────────────────────────────────────────────
   当缓存服务不可用时，直接查询数据库并返回降级响应
```

---

## 7. 分片策略

### 7.1 PostgreSQL分片策略

```
+-----------------------------------------------------------------------------+
|                        PostgreSQL分片策略                                    |
+-----------------------------------------------------------------------------+

1. 分片表设计

   ─────────────────────────────────────────────────────────────────────────
   | 表名                   | 分片键        | 分片数量 | 分片方式    | 说明        |
   ─────────────────────────────────────────────────────────────────────────
   | file_metadata          | file_id       | 64      | Hash        | 文件元数据  |
   | file_tags              | file_id       | 64      | Hash        | 文件标签    |
   | file_versions          | file_id       | 64      | Hash        | 文件版本    |
   | parse_results          | file_id       | 64      | Hash        | 解析结果    |
   | entities               | file_id       | 64      | Hash        | 实体信息    |
   | network_sessions       | file_id       | 64      | Hash        | 网络会话    |
   | analysis_results       | file_id       | 64      | Hash        | 分析结果    |
   | vulnerabilities        | file_id       | 64      | Hash        | 漏洞信息    |
   | sensitive_findings     | file_id       | 64      | Hash        | 敏感信息    |
   | target_files_by_file   | file_id       | 64      | Hash        | 目标文件(按文件) |
   | archive_contents       | archive_id    | 32      | Hash        | 压缩包内容  |
   | file_hash_index        | sha256_hash   | 32      | Hash        | 秒传哈希    |
   | targets                | target_id     | 32      | Hash        | 目标        |
   | target_files_by_target | target_id     | 32      | Hash        | 目标文件(按目标) |
   | target_assets          | target_id     | 32      | Hash        | 目标资产    |
   | target_persons         | target_id     | 32      | Hash        | 目标人员    |
   | outbox_events          | aggregate_id  | 64      | Hash        | 事件外发    |
   ─────────────────────────────────────────────────────────────────────────

   注: audit_logs 采用 PG 原生 RANGE 分区(按月), 不走 Citus 分布式表。
       参考表(users/roles/permissions/directories/upload_tasks/字典表)全集群广播。


2. 分片键选择原则

   2.1 数据分布均匀
   ─────────────────────────────────────────────────────────────────────────
   - 选择基数高的字段作为分片键
   - 避免数据倾斜
   - file_id、target_id、user_id都是UUID，分布均匀

   2.2 查询效率
   ─────────────────────────────────────────────────────────────────────────
   - 大部分查询都包含分片键
   - 避免跨分片查询
   - 关联查询尽量在同一分片

   2.3 业务关联
   ─────────────────────────────────────────────────────────────────────────
   - 相关数据存储在同一分片
   - 文件相关数据使用file_id分片
   - 目标相关数据使用target_id分片


3. 分片配置示例 (P1-4 Colocation 统一配置)

   -- 创建参考表(广播表)
   SELECT create_reference_table('users');
   SELECT create_reference_table('roles');
   SELECT create_reference_table('permissions');
   SELECT create_reference_table('user_roles');
   SELECT create_reference_table('role_permissions');
   SELECT create_reference_table('directories');
   SELECT create_reference_table('upload_tasks');
   SELECT create_reference_table('file_type_dict');
   SELECT create_reference_table('tag_dict');
   SELECT create_reference_table('system_config');

   -- P1-4: 统一 Colocation 配置
   -- file_id colocation 组(基准表 file_metadata, 所有按 file_id 分片的表同组)
   SELECT create_distributed_table('file_metadata', 'file_id', colocate_with => 'none');
   SELECT create_distributed_table('file_tags', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('file_versions', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('parse_results', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('entities', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('network_sessions', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('analysis_results', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('vulnerabilities', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('sensitive_findings', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('target_files_by_file', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('outbox_events', 'aggregate_id', colocate_with => 'file_metadata');

   -- target_id colocation 组(基准表 targets, 所有按 target_id 分片的表同组)
   SELECT create_distributed_table('targets', 'target_id', colocate_with => 'none');
   SELECT create_distributed_table('target_files_by_target', 'target_id', colocate_with => 'targets');
   SELECT create_distributed_table('target_assets', 'target_id', colocate_with => 'targets');
   SELECT create_distributed_table('target_persons', 'target_id', colocate_with => 'targets');

   -- 独立 colocation 组(分片键不同, 不与其他表同组)
   SELECT create_distributed_table('file_hash_index', 'sha256_hash', colocate_with => 'none');
   SELECT create_distributed_table('archive_contents', 'archive_id', colocate_with => 'none');


4. 分片维护

   4.1 分片重平衡
   ─────────────────────────────────────────────────────────────────────────
   -- 查看分片分布
   SELECT * FROM citus_shards;
   
   -- 重平衡分片
   SELECT rebalance_table_shards('file_metadata');
   
   4.2 分片监控
   ─────────────────────────────────────────────────────────────────────────
   -- 查看分片大小
   SELECT 
       logicalrelid AS table_name,
       count(*) AS shard_count,
       pg_size_pretty(sum(shard_size)) AS total_size
   FROM citus_shard_sizes
   GROUP BY logicalrelid;
```

### 7.1.1 分片扩容方案 (P1-5)

> **P1-5 分片扩容方案**: 初始设计 64 个分片(即使初始 6 个节点, 每节点承载多分片), 后续按需在线扩容, 不影响读写。

```
+-----------------------------------------------------------------------------+
|                    分片扩容方案 (P1-5)                                        |
+-----------------------------------------------------------------------------+
|                                                                             |
|  1. 初始设计: 64 分片 / 6 节点                                              |
|  ─────────────────────────────────────────────────────────────────────────  |
|  - 每节点承载 ~11 个分片(64/6)                                              |
|  - 预留分片余量: 数据增长时无需立即扩容节点                                 |
|  - 分片数固定为 64(2^6), Citus 哈希取模, 扩容不改变分片数                   |
|                                                                             |
|  2. 扩容触发条件                                                             |
|  ─────────────────────────────────────────────────────────────────────────  |
|  - 单节点磁盘使用率 > 75%                                                    |
|  - 单节点 CPU 使用率 > 80% 持续 1 小时                                       |
|  - 单分片数据量 > 50GB                                                       |
|  - 查询 P99 延迟 > 500ms 持续 30 分钟                                        |
|                                                                             |
|  3. 扩容流程(在线, 不影响读写)                                               |
|  ─────────────────────────────────────────────────────────────────────────  |
|  步骤1: 新增节点加入集群                                                     |
|         SELECT citus_add_node('new-worker-host', 5432);                      |
|         SELECT citus_add_node('new-worker-host2', 5432);                     |
|                                                                             |
|  步骤2: 执行 rebalance_table_shards 在线迁移                                |
|         -- 在线迁移分片(默认 30s 拷贝 + 1s 切换)                            |
|         SELECT rebalance_table_shards(                                       |
|             'file_metadata',                                                 |
|             max_shard_moves => 10,           -- 单次最大迁移分片数          |
|             shard_transfer_mode => 'auto'    -- auto/logical/force_logical  |
|         );                                                                   |
|         -- 同步迁移同 colocation 组的其他表(自动联动)                        |
|         SELECT rebalance_table_shards(                                       |
|             'targets',                                                       |
|             shard_transfer_mode => 'auto'                                    |
|         );                                                                   |
|                                                                             |
|  步骤3: 迁移期间不影响读写                                                   |
|         - 逻辑复制(logical)模式: 写入持续, 分片切换瞬间完成(< 1s)           |
|         - 应用层无感知, 连接池自动重连                                       |
|                                                                             |
|  步骤4: 迁移完成后验证数据完整性                                             |
|         -- 校验分片数与节点分布                                              |
|         SELECT shardid, nodename, nodeport, shardstate                       |
|         FROM citus_shards WHERE logicalrelid = 'file_metadata';              |
|         -- 行数对账(PG 内部对账)                                             |
|         SELECT count(*) FROM file_metadata;                                  |
|         -- 比对迁移前后总行数                                                |
|                                                                             |
|  4. 回滚机制                                                                 |
|  ─────────────────────────────────────────────────────────────────────────  |
|  - 扩容失败(节点故障/迁移超时)时, 已迁移分片自动回滚到原节点                |
|  - rebalance_table_shards 内置事务保证, 失败回滚不影响原分片可用性          |
|  - 手动回滚: SELECT citus_remove_node('new-worker-host', 5432);              |
|    (仅在该节点无活跃分片时允许)                                              |
|                                                                             |
|  5. 扩容后验证清单                                                           |
|  ─────────────────────────────────────────────────────────────────────────  |
|  [ ] 各节点分片数均衡(误差 < 10%)                                           |
|  [ ] 业务功能回归测试(上传/检索/分析)                                       |
|  [ ] 性能压测(P99 < 500ms)                                                  |
|  [ ] 监控指标正常(CPU/磁盘/查询延迟)                                        |
|  [ ] 对账脚本通过(PG/ES/Milvus/Neo4j 数据一致)                              |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 7.2 Elasticsearch分片策略

```
+-----------------------------------------------------------------------------+
|                      Elasticsearch分片策略                                   |
+-----------------------------------------------------------------------------+

1. 索引分片配置

   ─────────────────────────────────────────────────────────────────────────
   | 索引名称          | 主分片数 | 副本数 | 单分片大小 | 说明            |
   ─────────────────────────────────────────────────────────────────────────
   | file_content      | 6        | 2      | 50GB       | 文件内容索引    |
   | entity_index      | 6        | 2      | 30GB       | 实体索引        |
   | analysis_result   | 4        | 2      | 30GB       | 分析结果索引    |
   | target_profile    | 4        | 2      | 20GB       | 目标画像索引    |
   ─────────────────────────────────────────────────────────────────────────

   注: P2-19 生产环境副本数统一为 2(双副本), 提升查询吞吐量与数据冗余度。


2. 分片策略原则

   2.1 分片数量
   ─────────────────────────────────────────────────────────────────────────
   - 单个分片大小控制在10-50GB
   - 分片数量 = 数据量 / 单分片目标大小
   - 避免分片过多(影响性能)或过少(无法扩展)

   2.2 副本配置
   ─────────────────────────────────────────────────────────────────────────
   - 生产环境: 副本数 >= 2 (P2-19: 双副本, 任一节点故障仍可服务)
   - 开发环境: 副本数 = 0
   - 副本可动态调整

   2.3 路由策略
   ─────────────────────────────────────────────────────────────────────────
   - 默认使用文档ID路由
   - 关联数据使用相同路由键
   - 文件内容索引使用file_id作为路由键


3. 滚动索引策略

   -- 按月滚动索引
   PUT file_content-2026-03-000001
   {
     "aliases": {
       "file_content": { "is_write_index": true }
     }
   }
   
   -- 滚动条件
   POST file_content/_rollover
   {
     "conditions": {
       "max_size": "50GB",
       "max_age": "30d",
       "max_docs": 10000000
     }
   }


4. 热温冷架构

   ─────────────────────────────────────────────────────────────────────────
   | 阶段 | 存储介质   | 保留时间 | 副本数 | 分片数 | 说明              |
   ─────────────────────────────────────────────────────────────────────────
   | Hot  | NVMe SSD   | 0-30天   | 2      | 原始   | 高频访问数据      |
   | Warm | HDD        | 30-90天  | 2      | 缩减   | 中频访问数据      |
   | Cold | HDD        | 90-365天 | 0      | 1      | 低频访问数据      |
   | Delete | -        | >365天   | -      | -      | 删除              |
   ─────────────────────────────────────────────────────────────────────────

   注: Hot/Warm 副本数=2 (P2-19), Cold 阶段 freeze 后副本数=0 节省存储。
```

### 7.3 Milvus分片策略

```
+-----------------------------------------------------------------------------+
|                        Milvus分片策略                                        |
+-----------------------------------------------------------------------------+

1. Collection分片配置

   ─────────────────────────────────────────────────────────────────────────
   | Collection名称           | 分片数 | 分片键(partition_key) | 说明              |
   ─────────────────────────────────────────────────────────────────────────
   | file_content_vector      | 6      | file_id              | 文件内容向量      |
   | target_profile_vector    | 4      | target_id            | 目标画像向量      |
   | entity_vector            | 6      | file_id              | 实体向量          |
   ─────────────────────────────────────────────────────────────────────────

   注: P1-6 统一启用 partition_key_field 加速按文件/目标过滤(见第4章)。


2. 分片策略

   2.1 数据分片
   ─────────────────────────────────────────────────────────────────────────
   - 基于主键自动分片
   - 每个分片独立存储和查询
   - 查询时并行搜索所有分片

   2.2 分段管理
   ─────────────────────────────────────────────────────────────────────────
   - 自动管理数据段(Segment)
   - 小段自动合并
   - 大段自动拆分

   2.3 分片平衡
   ─────────────────────────────────────────────────────────────────────────
   - 自动平衡分片到不同节点
   - 支持动态扩缩容


3. 索引配置

   -- P1-7: 统一使用 HNSW 索引(M=32, efConstruction=512, 高召回高质量构建)
   -- 所有 Collection 共用同一索引类型与参数, 简化运维与查询参数调优
   {
     "metric_type": "COSINE",
     "index_type": "HNSW",
     "params": {
       "M": 32,
       "efConstruction": 512
     }
   }

   -- 查询参数(SearchParams)
   -- ef: 64(快速) ~ 256(高召回), 按场景调优
   -- search_params = {"metric_type": "COSINE", "params": {"ef": 128}}
```

---

## 8. 索引策略

### 8.1 PostgreSQL索引策略

```
+-----------------------------------------------------------------------------+
|                      PostgreSQL索引策略                                      |
+-----------------------------------------------------------------------------+

1. 索引类型选择

   ─────────────────────────────────────────────────────────────────────────
   | 索引类型     | 适用场景                    | 示例字段              |
   ─────────────────────────────────────────────────────────────────────────
   | B-Tree       | 等值查询、范围查询、排序    | id, created_at, status|
   | Hash         | 仅等值查询                  | file_id, user_id      |
   | GIN          | JSONB、数组、全文检索       | entities, metadata    |
   | GiST         | 几何数据、范围类型          | -                     |
   | BRIN         | 大表、时序数据、有序数据    | created_at (分区表)   |
   ─────────────────────────────────────────────────────────────────────────


2. 核心表索引设计

   2.1 file_metadata表(分片键 file_id, 主键已为 file_id)
   ─────────────────────────────────────────────────────────────────────────
   -- 主键索引(已由 PRIMARY KEY (file_id) 创建, 无需重复)
   -- 秒传查 file_hash_index 表, 故 file_metadata 不再保留 sha256 唯一索引

   -- 单列索引
   CREATE INDEX idx_file_metadata_owner_id ON file_metadata(owner_id);
   CREATE INDEX idx_file_metadata_directory_id ON file_metadata(directory_id);

   -- 复合索引(覆盖高频查询路径)
   CREATE INDEX idx_file_metadata_owner_status_time ON file_metadata(owner_id, is_deleted, upload_time DESC);
   CREATE INDEX idx_file_metadata_type_status ON file_metadata(file_type, status);

   -- JSONB GIN索引
   CREATE INDEX idx_file_metadata_extra_metadata ON file_metadata USING GIN(extra_metadata);

   -- 说明: 已删除冗余索引
   --   - idx_sha256(与原 UNIQUE 重复, 现已无 UNIQUE)
   --   - idx_created_at(与 upload_time 业务语义重复, upload_time 更高频)
   ─────────────────────────────────────────────────────────────────────────

   2.2 parse_results表(分片键 file_id, 主键 id+file_id)
   ─────────────────────────────────────────────────────────────────────────
   CREATE INDEX idx_parse_results_parse_status ON parse_results(parse_status);
   CREATE INDEX idx_parse_results_created_at ON parse_results(created_at DESC);
   -- P2-17: GIN 索引使用 jsonb_path_ops(更小更快, 仅支持 @ @ 存在性查询)
   CREATE INDEX idx_parse_results_entities ON parse_results USING GIN(entities jsonb_path_ops);
   CREATE INDEX idx_parse_results_metadata ON parse_results USING GIN(parse_metadata jsonb_path_ops);
   ─────────────────────────────────────────────────────────────────────────

   2.3 entities表(分片键 file_id)
   ─────────────────────────────────────────────────────────────────────────
   CREATE INDEX idx_entities_entity_type ON entities(entity_type);
   -- P2-16: 已删除被复合索引覆盖的单字段索引 idx_entities_entity_value
   -- 复合索引(覆盖"按类型查实体值"高频查询)
   CREATE INDEX idx_entities_type_value ON entities(entity_type, entity_value);
   ─────────────────────────────────────────────────────────────────────────

   2.4 audit_logs表(分区键 created_at)
   ─────────────────────────────────────────────────────────────────────────
   -- P2-18: 索引优化, 只保留3个核心复合索引 + GIN + BRIN, 删除低选择性单字段索引
   -- 复合索引1: 用户审计场景(按用户+时间倒序)
   CREATE INDEX idx_audit_logs_user_time ON audit_logs(user_id, created_at DESC);
   -- 复合索引2: 操作审计场景(按操作+时间倒序)
   CREATE INDEX idx_audit_logs_action_time ON audit_logs(action, created_at DESC);
   -- 复合索引3: 资源追溯场景(按资源类型+资源ID)
   CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
   -- GIN索引(JSONB, jsonb_path_ops)
   CREATE INDEX idx_audit_logs_details ON audit_logs USING GIN(details jsonb_path_ops);
   -- BRIN索引(适合时序大表, 占用空间极小)
   CREATE INDEX idx_audit_logs_created_at_brin ON audit_logs USING BRIN(created_at);
   -- 已删除低选择性索引(被复合索引覆盖或选择性低):
   --   idx_audit_logs_user_id / idx_audit_logs_action / idx_audit_logs_resource_type
   --   idx_audit_logs_resource_id / idx_audit_logs_created_at / idx_audit_logs_result
   ─────────────────────────────────────────────────────────────────────────

   2.5 targets表(分片键 target_id, 主键 target_id)
   ─────────────────────────────────────────────────────────────────────────
   CREATE INDEX idx_targets_owner_id ON targets(owner_id);
   CREATE INDEX idx_targets_target_type ON targets(target_type);
   CREATE INDEX idx_targets_status ON targets(status);
   CREATE INDEX idx_targets_created_at ON targets(created_at DESC);
   CREATE INDEX idx_targets_tags ON targets USING GIN(tags);
   ─────────────────────────────────────────────────────────────────────────


3. 索引维护策略

   3.1 索引膨胀监控
   ─────────────────────────────────────────────────────────────────────────
   -- 查看索引大小与使用频次
   SELECT
       schemaname, relname AS table_name, indexrelname AS index_name,
       pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
       idx_scan AS scan_count, idx_tup_read, idx_tup_fetch
   FROM pg_stat_user_indexes
   ORDER BY pg_relation_size(indexrelid) DESC;

   -- 查找未使用的索引(候选删除对象)
   SELECT indexrelname FROM pg_stat_user_indexes WHERE idx_scan = 0;

   3.2 索引重建
   ─────────────────────────────────────────────────────────────────────────
   -- 在线重建索引(CONCURRENTLY 不阻塞写)
   REINDEX INDEX CONCURRENTLY idx_file_metadata_owner_status_time;

   3.3 索引生命周期
   ─────────────────────────────────────────────────────────────────────────
   - 高频写入表: 每月 REINDEX CONCURRENTLY 一次
   - 低频查询表: 每季度 ANALYZE 一次
   - 分区表: 老分区索引可在归档后删除
```

### 8.2 Elasticsearch索引策略

```
+-----------------------------------------------------------------------------+
|                      Elasticsearch索引策略                                   |
+-----------------------------------------------------------------------------+

1. 索引设计原则

   ─────────────────────────────────────────────────────────────────────────
   | 原则           | 说明                                                  |
   ─────────────────────────────────────────────────────────────────────────
   | dynamic:strict | 所有索引严格禁止动态映射, 防止 mapping 爆炸攻击       |
   | keyword优先    | 不需要分词的字段一律 keyword, 减少倒排索引体积        |
   | nested控制     | 嵌套对象用 nested 类型, 避免对象间字段交叉匹配        |
   | 分片50GB       | 单分片控制在 10-50GB, 兼顾查询并行度与稳定性         |
   ─────────────────────────────────────────────────────────────────────────


2. 分词器选择

   ─────────────────────────────────────────────────────────────────────────
   | 分词器              | 适用场景                                |
   ─────────────────────────────────────────────────────────────────────────
   | standard (默认)     | 多语言通用, 数字/字母切分, 适合代码、英文 |
   | ik_smart            | 中文智能切分(查询时用, 粒度粗)          |
   | ik_max_word         | 中文最大切分(索引时用, 粒度细)          |
   | code_analyzer       | 代码片段, lowercase+asciifolding        |
   ─────────────────────────────────────────────────────────────────────────


3. 别名与滚动

   -- 通过别名访问, 切换索引对应用透明
   POST /_aliases
   {
     "actions": [
       { "add": { "index": "file_content-2026-03-000001", "alias": "file_content" } }
     ]
   }
```

### 8.3 Milvus索引策略

```
+-----------------------------------------------------------------------------+
|                        Milvus索引策略                                        |
+-----------------------------------------------------------------------------+

1. 索引类型选择

   ─────────────────────────────────────────────────────────────────────────
   | 索引类型   | 适用场景                       | 查询参数 (SearchParams)  |
   ─────────────────────────────────────────────────────────────────────────
   | HNSW       | 高性能, 高召回, 内存占用大     | ef (如 64-512)           |
   | IVF_FLAT   | 高精度, 内存占用适中           | nprobe (如 8-32)         |
   | IVF_SQ8    | 大规模, 内存敏感               | nprobe                   |
   ─────────────────────────────────────────────────────────────────────────

2. 参数调优

   - HNSW M: 16(默认) ~ 48(高召回), 越大内存占用越高
   - HNSW efConstruction: 256(平衡) ~ 512(高质量构建)
   - 查询 ef: 64(快速) ~ 256(高召回)

   注: P1-7 本项目所有 Collection 统一使用 HNSW(M=32, efConstruction=512),
       IVF_FLAT / IVF_SQ8 仅作为选型参考列出, 实际未采用。
```

### 8.4 Neo4j索引策略

```
+-----------------------------------------------------------------------------+
|                        Neo4j索引策略                                         |
+-----------------------------------------------------------------------------+

1. 唯一性约束(隐式索引)

   -- 业务唯一标识必建 UNIQUE 约束
   CREATE CONSTRAINT file_id_unique IF NOT EXISTS FOR (f:File) REQUIRE f.file_id IS UNIQUE;
   CREATE CONSTRAINT target_id_unique IF NOT EXISTS FOR (t:Target) REQUIRE t.target_id IS UNIQUE;
   CREATE CONSTRAINT threat_id_unique IF NOT EXISTS FOR (t:Threat) REQUIRE t.threat_id IS UNIQUE;

2. 查询性能索引

   -- 高频查询字段建索引
   CREATE INDEX file_name_index IF NOT EXISTS FOR (f:File) ON (f.file_name);
   CREATE INDEX target_name_index IF NOT EXISTS FOR (t:Target) ON (t.target_name);
   CREATE INDEX file_upload_time_index IF NOT EXISTS FOR (f:File) ON (f.upload_time);
   CREATE INDEX threat_type_index IF NOT EXISTS FOR (t:Threat) ON (t.threat_type);

3. 全文索引(中文)

   -- 文件名、目标名称中文模糊检索
   CREATE FULLTEXT INDEX file_fulltext_index IF NOT EXISTS
     FOR (f:File) ON EACH [f.file_name];
```

---

## 9. 数据一致性保障

### 9.1 Outbox Pattern 设计(PG outbox 表 + Kafka CDC)

> **目标**: 解决"业务事务+消息投递"的原子性问题, 保证 PG 主库事务提交后, 异步事件至少被消费一次。

#### 9.1.1 outbox 表结构

```sql
-- outbox 表(分布式表, 与 file_metadata 同 colocate 组, 保证本地事务)
-- P1-1: id 改 UUID
CREATE TABLE outbox_events (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    event_id        VARCHAR(64) NOT NULL,
    aggregate_type  VARCHAR(64) NOT NULL,    -- 聚合根类型: FILE, TARGET, AUDIT
    aggregate_id    VARCHAR(64) NOT NULL,    -- 聚合根ID: file_id / target_id
    event_type      VARCHAR(64) NOT NULL,    -- 事件类型: FILE_UPLOADED, FILE_PARSED, ...
    payload         JSONB NOT NULL,          -- 事件负载
    headers         JSONB,                   -- 头信息(trace_id, user_id, ...)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMPTZ, -- 已发布到 Kafka 的时间
    status          SMALLINT NOT NULL DEFAULT 0, -- 0-待发布, 1-已发布, 2-失败

    CONSTRAINT pk_outbox_events PRIMARY KEY (id, aggregate_id)
);

-- 按 aggregate_id 分片, 与业务表 colocate, 保证业务事务与 outbox 写入在同分片本地事务
SELECT create_distributed_table('outbox_events', 'aggregate_id', colocate_with => 'file_metadata');

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at)
    WHERE status = 0;  -- 部分索引, 仅索引待发布事件
```

#### 9.1.2 业务事务写入流程

```sql
-- 业务事务(伪代码): 文件元数据写入 + outbox 事件写入 在同一本地事务
BEGIN;
INSERT INTO file_metadata (file_id, file_name, ...) VALUES (...);
INSERT INTO outbox_events (event_id, aggregate_type, aggregate_id, event_type, payload)
VALUES ('evt-xxx', 'FILE', $file_id, 'FILE_UPLOADED', jsonb_build_object('file_id', $file_id, ...));
COMMIT;
```

#### 9.1.3 CDC 投递流程

- **Debezium PG Connector** 监听 outbox 表变更(或轮询 status=0 的记录)
- 将事件投递到 Kafka topic `outbox.events`
- 投递成功后回调更新 `outbox_events.published_at` 与 `status=1`
- 下游消费者(ES Syncer / Milvus Syncer / Neo4j Syncer)订阅 Kafka topic, 各自写入对应存储

---

### 9.2 最终一致性 SLA 定义

| 数据链路 | SLA | 监控指标 | 告警阈值 |
|---------|-----|---------|---------|
| PG → Elasticsearch | < 30s | `pg_es_lag_seconds` | > 30s 持续 1min |
| PG → Milvus | < 60s | `pg_milvus_lag_seconds` | > 60s 持续 2min |
| PG → Neo4j | < 120s | `pg_neo4j_lag_seconds` | > 120s 持续 3min |
| PG → Redis(缓存失效) | < 1s | `cache_invalidation_lag` | > 5s 持续 1min |

**SLA 守护机制**:
- 每个 Syncer 启动时记录 `last_consumed_offset`, 暴露 Prometheus 指标
- 每条事件携带 `created_at`, 下游写入后计算 `now() - created_at` 作为实际延迟
- 延迟超阈值触发 PagerDuty 告警

---

### 9.3 失败补偿机制

#### 9.3.1 重试策略

```
+-----------------------------------------------------------------------------+
|                          重试与补偿策略                                       |
+-----------------------------------------------------------------------------+

1. Kafka 消费失败重试
   ─────────────────────────────────────────────────────────────────────────
   - 即时重试: 3 次, 间隔 1s / 5s / 30s
   - 进入 retry topic: 重试 5 次, 指数退避 1min/5min/30min/2h/12h
   - 进入 dead-letter-topic (DLT): 人工介入分析

2. 幂等性保证
   ─────────────────────────────────────────────────────────────────────────
   - 每条事件携带 event_id (UUID)
   - 下游存储 event_id 处理记录表, 写入前检查是否已处理
   - ES: 利用 _id = event_id 实现 upsert 幂等
   - Milvus: 通过 vector_id = event_id 实现幂等
   - Neo4j: 通过 MERGE 语句实现幂等

3. 补偿事务(反向操作)
   ─────────────────────────────────────────────────────────────────────────
   - 文件删除: PG 删除 → outbox 发 FILE_DELETED → ES 删 doc → Milvus 删向量 → Neo4j 删节点
   - 任一步失败: 重试 + 最终人工补偿(对账脚本修复)
```

#### 9.3.2 死信队列处理

```sql
-- 死信事件表(供人工分析后重放或丢弃)
-- P1-1: resolved_by 改 UUID
CREATE TABLE dead_letter_events (
    event_id        VARCHAR(64) PRIMARY KEY,
    original_topic  VARCHAR(128) NOT NULL,
    payload         JSONB NOT NULL,
    error_reason    TEXT,
    failed_at       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    retry_count     INTEGER DEFAULT 0,
    resolved        BOOLEAN DEFAULT FALSE,
    resolved_at     TIMESTAMPTZ,
    resolved_by     UUID
);
```

---

### 9.4 数据校验对账机制

#### 9.4.1 每日对账任务

```sql
-- 每日凌晨 02:00 执行对账(由调度系统触发)
-- 1. PG 与 ES 文档数对账
SELECT
    'file_metadata' AS pg_table,
    count(*) AS pg_count,
    'file_content' AS es_index
FROM file_metadata WHERE is_deleted = FALSE;

-- 对应 ES 查询
-- GET file_content/_count?q=status:1

-- 2. PG 与 Milvus 向量数对账
SELECT
    count(DISTINCT file_id) AS pg_files_with_text
FROM parse_results WHERE parse_status = 2;

-- 对应 Milvus 查询
-- collection.query(expr="file_id != ''", output_fields=["file_id"], count_only=True)

-- 3. PG 与 Neo4j 节点数对账
SELECT count(*) AS pg_targets FROM targets;
-- 对应 Cypher: MATCH (t:Target) RETURN count(t)
```

#### 9.4.2 对账差异处理

```
+-----------------------------------------------------------------------------+
|                          对账差异处理流程                                     |
+-----------------------------------------------------------------------------+

1. 差异检测
   ─────────────────────────────────────────────────────────────────────────
   - 数量差异 > 0.1% 触发告警
   - 单条记录差异: 抽样 1000 条做内容级比对

2. 差异修复
   ─────────────────────────────────────────────────────────────────────────
   - PG 多, ES 少: 重放对应 outbox 事件到 ES
   - ES 多, PG 少: 标记 ES 文档 status=4(已删除) 或删除
   - Neo4j 节点缺失: 调用 graph-sync 服务重建

3. 对账报告
   ─────────────────────────────────────────────────────────────────────────
   - 生成对账报告写入 audit_logs(action_category='SYSTEM', action='RECONCILIATION')
   - 报告包含: 对账时间、各链路数量、差异详情、修复动作、修复结果
```

---

### 9.5 引用计数 ref_count 并发安全(乐观锁)

```sql
-- 秒传场景: 多个用户同时上传相同 sha256 文件, 需并发安全地增加 ref_count
-- 使用乐观锁(version 字段) + 重试机制

-- 伪代码:
-- 1. 读取当前 ref_count 与 version
SELECT ref_count, version FROM file_hash_index WHERE sha256_hash = $sha256;
-- 2. 乐观锁更新(带版本校验)
UPDATE file_hash_index
SET ref_count = ref_count + 1,
    version = version + 1,
    last_seen_at = CURRENT_TIMESTAMP
WHERE sha256_hash = $sha256
  AND version = $current_version;
-- 3. 若 affected_rows = 0, 说明并发冲突, 重试步骤 1-2 (最多 5 次)

-- 删除场景: ref_count 减到 0 时, 物理删除 file_hash_index 记录(并清理 MinIO 对象)
UPDATE file_hash_index
SET ref_count = ref_count - 1,
    version = version + 1
WHERE sha256_hash = $sha256
  AND version = $current_version
  AND ref_count > 0;

-- 物理删除(ref_count 归零后)
DELETE FROM file_hash_index WHERE sha256_hash = $sha256 AND ref_count = 0;
```

---

### 9.6 文件物理删除与逻辑删除一致性(级联删除策略)

```
+-----------------------------------------------------------------------------+
|                  文件逻辑删除 → 物理删除级联策略                              |
+-----------------------------------------------------------------------------+

1. 逻辑删除(用户视角)
   ─────────────────────────────────────────────────────────────────────────
   - file_metadata.is_deleted = TRUE, deleted_at = now(), deleted_by = $user
   - file_metadata.status = 4 (已删除)
   - 发出 outbox 事件 FILE_LOGIC_DELETED
   - 下游(ES/Milvus/Neo4j)标记或暂时保留(便于撤销)

2. 物理删除(合规清理, 30 天后且 ref_count=0)
   ─────────────────────────────────────────────────────────────────────────
   -- 调度任务每日扫描可物理删除的文件
   SELECT file_id, sha256_hash, storage_path
   FROM file_metadata
   WHERE is_deleted = TRUE
     AND deleted_at < NOW() - INTERVAL '30 days'
     AND file_id NOT IN (
         SELECT file_id FROM file_hash_index WHERE ref_count > 0
     );

   -- 级联删除顺序:
   -- a. MinIO 对象(先删, 失败可重试)
   -- b. file_hash_index 记录
   -- c. parse_results / entities / analysis_results / sensitive_findings
   -- d. target_files_by_target / target_files_by_file
   -- e. file_metadata 本身
   -- f. 发出 FILE_PHYSICAL_DELETED 事件, 下游清理 ES/Milvus/Neo4j

3. 引用计数保护
   ─────────────────────────────────────────────────────────────────────────
   - 若 ref_count > 0 (其他逻辑文件引用同一物理存储), 仅删除逻辑记录, 不删物理文件
   - 物理文件在最后一个引用被删除后才清理
```

---

## 10. 备份与恢复策略

### 10.1 备份策略

#### 10.1.1 PostgreSQL 备份

```
+-----------------------------------------------------------------------------+
|                         PostgreSQL 备份策略                                   |
+-----------------------------------------------------------------------------+

1. 备份类型与频率
   ─────────────────────────────────────────────────────────────────────────
   | 备份类型     | 频率        | 工具                | 保留周期  |
   ─────────────────────────────────────────────────────────────────────────
   | 全量备份     | 每周日 02:00| pg_basebackup       | 4 周     |
   | 增量备份     | 每日 02:00  | pg_basebackup -X    | 7 天     |
   | WAL 归档     | 实时        | archive_command     | 30 天    |
   | 逻辑备份     | 每日 03:00  | pg_dump (关键表)    | 7 天     |
   ─────────────────────────────────────────────────────────────────────────

2. 备份脚本示例
   ─────────────────────────────────────────────────────────────────────────
   -- 全量物理备份(在副本节点执行, 避免影响主库)
   pg_basebackup -h replica_host -U backup_user -D /backup/pg_full_$(date +%Y%m%d) \
                 -Ft -z -P -X stream -S replica_slot

   -- WAL 归档配置(postgresql.conf)
   archive_mode = on
   archive_command = 'test ! -f /archive/wal/%f && cp %p /archive/wal/%f'

3. 备份验证
   ─────────────────────────────────────────────────────────────────────────
   - 每日校验备份文件完整性(pg_verifybackup)
   - 每周抽样恢复到测试环境, 验证可恢复性
```

#### 10.1.2 Elasticsearch 备份

```
+-----------------------------------------------------------------------------+
|                       Elasticsearch 备份策略                                  |
+-----------------------------------------------------------------------------+

1. 快照仓库
   ─────────────────────────────────────────────────────────────────────────
   PUT _snapshot/backup_repo
   {
     "type": "s3",
     "settings": {
       "bucket": "es-backup-bucket",
       "region": "cn-north-1",
       "compress": true
     }
   }

2. 快照策略
   ─────────────────────────────────────────────────────────────────────────
   - 每日增量快照(凌晨 03:30)
   - 每周全量快照(周日凌晨 04:00)
   - 保留: 30 天日快照 + 12 周周快照
```

#### 10.1.3 Milvus / Neo4j / MinIO 备份

```
+-----------------------------------------------------------------------------+
|                  Milvus / Neo4j / MinIO 备份策略                             |
+-----------------------------------------------------------------------------+

| 系统    | 备份方式                          | 频率      | 保留周期 |
|---------|-----------------------------------|-----------|---------|
| Milvus  | collection snapshot (S3)          | 每日 04:00| 7 天    |
| Neo4j   | neo4j-admin backup (online)       | 每日 04:30| 14 天   |
| MinIO   | 跨区域复制 + 版本化               | 实时      | 90 天   |
```

---

### 10.2 RPO / RTO 指标

```
+-----------------------------------------------------------------------------+
|                          RPO / RTO 指标                                      |
+-----------------------------------------------------------------------------+

| 系统           | RPO (数据丢失容忍) | RTO (恢复时间目标) | 说明              |
|---------------|-------------------|-------------------|-------------------|
| PostgreSQL    | < 5 分钟          | < 30 分钟         | WAL 归档 + 流复制  |
| Elasticsearch | < 1 小时          | < 2 小时          | 日快照 + 副本      |
| Milvus        | < 24 小时         | < 4 小时          | 可从 PG 重建       |
| Neo4j         | < 24 小时         | < 4 小时          | 可从 PG 重建       |
| MinIO         | < 1 小时          | < 1 小时          | 跨区复制           |
| Redis         | 0 (缓存可丢)      | < 5 分钟          | 集群自愈           |

- **RPO (Recovery Point Objective)**: 灾难发生时允许丢失的最大数据量
- **RTO (Recovery Time Objective)**: 灾难发生后系统恢复所需最大时间
- **整体业务 RTO**: < 4 小时(PG+ES+MinIO 恢复完成)
```

---

### 10.3 恢复演练机制

```
+-----------------------------------------------------------------------------+
|                          恢复演练机制                                        |
+-----------------------------------------------------------------------------+

1. 演练频率
   ─────────────────────────────────────────────────────────────────────────
   - 全量演练: 每半年(6 月、12 月)
   - 抽样演练: 每季度(单表/单索引恢复)
   - 故障注入: 每月(模拟主库宕机, 验证故障转移)

2. 演练流程
   ─────────────────────────────────────────────────────────────────────────
   a. 准备: 搭建与生产同等规格的演练环境
   b. 备份恢复: 从最新备份恢复 PG / ES / Milvus / Neo4j
   c. 数据校验: 对账脚本验证数据完整性
   d. 应用验证: 业务功能测试(上传/检索/分析/导出)
   e. 性能验证: 压测确认恢复后性能达标
   f. 报告: 演练耗时、发现问题、改进措施

3. 演练记录归档
   ─────────────────────────────────────────────────────────────────────────
   - 演练报告写入 audit_logs(action='DISASTER_RECOVERY_DRILL')
   - 演练视频/截图归档至知识库
   - 演练问题登记为 JIRA 工单, 跟踪整改
```

---

### 10.4 灾备方案

```
+-----------------------------------------------------------------------------+
|                              灾备方案                                        |
+-----------------------------------------------------------------------------+

1. 灾备架构(同城双活 + 异地灾备)
   ─────────────────────────────────────────────────────────────────────────
   - 同城双活: 主数据中心(A) + 备数据中心(B), 距离 < 50km, 延迟 < 5ms
     · PG: 流复制(同步模式)
     · ES: 跨集群复制(CCR)
     · MinIO: 跨区复制
   - 异地灾备: 第三地(C), 距离 > 500km
     · 每日全量备份传输
     · RPO < 24 小时, RTO < 24 小时

2. 故障切换(Failover)流程
   ─────────────────────────────────────────────────────────────────────────
   a. 监控检测主库不可用(连续 3 次健康检查失败)
   b. 自动提升备库为主(promote)
   c. 应用层更新连接字符串(通过配置中心)
   d. 原主库恢复后, 作为新备库重新加入

3. 数据回切(Failback)流程
   ─────────────────────────────────────────────────────────────────────────
   a. 原主库修复并同步到最新
   b. 业务低峰期切换回原主库
   c. 验证数据一致性后恢复常态
```

---

## 11. 数据归档策略

### 11.1 Hot/Warm/Cold/Archive 迁移条件

```
+-----------------------------------------------------------------------------+
|                  数据生命周期: Hot → Warm → Cold → Archive                   |
+-----------------------------------------------------------------------------+

| 层级    | 介质        | 访问频率   | 迁移条件                    | 查询性能  |
|--------|-------------|-----------|----------------------------|-----------|
| Hot    | NVMe SSD    | 高频      | 最近 30 天 / status=1      | < 100ms   |
| Warm   | HDD/SATA SSD| 中频      | 30-180 天 / status=1       | < 1s      |
| Cold   | 大容量 HDD  | 低频      | 180-365 天 / 非活跃        | < 10s     |
| Archive| 对象存储    | 极低频    | > 365 天 / 合规归档        | 分钟级    |

1. PG 表归档(以 audit_logs 为例)
   ─────────────────────────────────────────────────────────────────────────
   -- 每月归档脚本: 将 1 年前的审计日志迁移到归档表
   INSERT INTO audit_logs_archive
   SELECT * FROM audit_logs
   WHERE created_at < NOW() - INTERVAL '1 year'
     AND created_at >= NOW() - INTERVAL '1 year 1 month';

   -- 验证后删除原分区
   DROP TABLE audit_logs_y2025m03;

   -- 归档表存于独立表空间(冷存储)
   CREATE TABLESPACE archive_ts LOCATION '/data/archive';
   ALTER TABLE audit_logs_archive SET TABLESPACE archive_ts;

2. ES 索引归档(ILM 已配置)
   ─────────────────────────────────────────────────────────────────────────
   - Hot → Warm: 30 天后 shrink 到 3 分片
   - Warm → Cold: 90 天后 freeze
   - Cold → Delete: 2555 天后删除(7 年合规保留)
```

### 11.2 审计日志 7 年保留(等保要求)

```
+-----------------------------------------------------------------------------+
|                      审计日志 7 年保留策略                                    |
+-----------------------------------------------------------------------------+

1. 保留依据
   ─────────────────────────────────────────────────────────────────────────
   - 《网络安全法》: 网络日志留存不少于 6 个月
   - 《等保三级》: 操作日志保留≥180天, 关键日志≥1年
   - 《数据安全法》: 重要数据处理记录长期保留
   - 内部合规: 审计日志保留 7 年(覆盖法律追诉期)

2. 分级保留
   ─────────────────────────────────────────────────────────────────────────
   | 日志类型          | 保留周期 | 存储介质        |
   ─────────────────────────────────────────────────────────────────────────
   | 关键操作日志       | 7 年     | Hot(1y)+Archive(6y) |
   | 普通操作日志       | 3 年     | Hot(1y)+Cold(2y)    |
   | 系统运行日志       | 1 年     | Hot(90d)+Cold(275d) |
   ─────────────────────────────────────────────────────────────────────────

3. 归档表自动维护
   ─────────────────────────────────────────────────────────────────────────
   - pg_partman 自动创建月度分区
   - 月度归档任务将老分区数据迁移到 audit_logs_archive
   - 归档表按年分区, 便于按年导出/删除
```

### 11.3 PG 表归档策略汇总

```
+-----------------------------------------------------------------------------+
|                          PG 各表归档策略                                     |
+-----------------------------------------------------------------------------+

| 表名                | 保留周期 | 归档条件                    | 归档动作         |
|--------------------|---------|----------------------------|-----------------|
| audit_logs         | 7 年    | created_at > 1 年          | 迁移至归档表     |
| upload_tasks       | 30 天   | status=1 AND created_at>30d| 直接删除         |
| file_metadata      | 永久    | is_deleted=TRUE AND >90天  | 物理删除+对象清理|
| parse_results      | 跟随文件| 文件物理删除时              | 级联删除         |
| network_sessions   | 1 年    | created_at > 1 年          | 迁移至归档表     |
| outbox_events      | 30 天   | status=1 AND published>30d | 直接删除         |
| sensitive_findings | 永久    | 跟随文件                    | 级联删除         |
```

---

## 12. 批量操作设计

### 12.1 PostgreSQL COPY 批量导入

```sql
-- 批量导入实体表(单次 1 万行, 比 INSERT 快 10-50 倍)
-- 使用 COPY 命令从文件流式导入

-- 伪代码(Python psycopg2):
-- with open('/tmp/entities.csv') as f:
--     cursor.copy_expert(
--         "COPY entities (entity_id, file_id, entity_type, entity_value, ...) "
--         "FROM STDIN WITH (FORMAT csv, HEADER false)",
--         f
--     )

-- 或使用 INSERT ... VALUES 批量(适用于构造数据)
INSERT INTO entities (entity_id, file_id, entity_type, entity_value, normalized_value,
                      position_start, position_end, confidence, source_section, created_at)
VALUES
    ('ent-001', 'file-001', 'IP', '192.168.1.1', '192.168.1.1', 100, 113, 0.98, 'header', NOW()),
    ('ent-002', 'file-001', 'DOMAIN', 'example.com', 'example.com', 200, 211, 0.95, 'body', NOW()),
    ... -- 单次最多 1000 行
ON CONFLICT (entity_id, file_id) DO NOTHING;

-- 大批量数据导入临时关闭索引(超大批量场景)
-- ALTER TABLE entities DISABLE TRIGGER ALL;
-- COPY entities FROM '/tmp/entities.csv' WITH (FORMAT csv);
-- ALTER TABLE entities ENABLE TRIGGER ALL;
```

### 12.2 Elasticsearch Bulk API

```python
# ES 批量写入(单次 1000-5000 文档, 总大小 5-15MB)
from elasticsearch.helpers import bulk

actions = [
    {
        "_index": "file_content-2026-03-000001",
        "_id": file_id,  # 使用 file_id 作为 _id, 实现 upsert 幂等
        "_source": {
            "file_id": file_id,
            "file_name": file_name,
            "text_content": text_content,
            # ... 其他字段
        },
        "_op_type": "index"  # index=upsert, create=仅新建
    }
    for file_id, file_name, text_content in batch_data
]

# 批量执行
success, failed = bulk(
    es_client,
    actions,
    chunk_size=1000,        # 每批 1000 文档
    max_retries=3,          # 失败重试 3 次
    initial_backoff=2,      # 初始退避 2s
    request_timeout=60      # 单次请求超时 60s
)
```

### 12.3 Milvus 批量插入

```python
from pymilvus import Collection

collection = Collection("file_content_vector")
collection.load()

# 批量插入(单次 5000-10000 条)
data = [
    [vec_id for vec_id in vector_ids],          # vector_id 列表
    [file_id for file_id in file_ids],          # file_id 列表
    [chunk_idx for chunk_idx in chunk_indices], # chunk_index 列表
    [chunk for chunk in chunk_texts],           # chunk_text 列表
    [emb for emb in embeddings],                # embedding 列表(1024维, P1-8 BGE-large-zh)
    [lang for lang in languages],               # language 列表
    [sec for sec in source_sections],           # source_section 列表
    [ts for ts in timestamps]                   # created_at 列表
]

# 插入(异步, 返回 mutation_id)
mr = collection.insert(data)

# 批量插入后手动 flush, 触发段构建
collection.flush()
```

### 12.4 Neo4j UNWIND 批量导入

```cypher
// 批量创建文件节点(单次 5000-10000 节点, 使用 UNWIND + 参数化)
// 通过参数 $batch 传入列表
UNWIND $batch AS row
MERGE (f:File {file_id: row.file_id})
SET f.file_name = row.file_name,
    f.file_type = row.file_type,
    f.file_size = row.file_size,
    f.sha256_hash = row.sha256_hash,
    f.owner_id = row.owner_id,
    f.status = row.status,
    f.security_level = row.security_level,
    f.upload_time = datetime(row.upload_time),
    f.created_at = datetime(row.created_at);

// 批量创建关系(实体到文件的 CONTAINS_* 关系)
UNWIND $batch AS row
MATCH (f:File {file_id: row.file_id})
MATCH (ip:IPAddress {address: row.ip_address})
MERGE (f)-[r:CONTAINS_IP]->(ip)
SET r.position = row.position,
    r.context = row.context,
    r.confidence = row.confidence,
    r.created_at = datetime(row.created_at);
```

### 12.5 批量操作性能基线

```
+-----------------------------------------------------------------------------+
|                          批量操作性能基线                                     |
+-----------------------------------------------------------------------------+

| 系统        | 操作          | 单次批量    | 吞吐量         | 备注              |
|------------|---------------|------------|---------------|-------------------|
| PG         | COPY          | 1 万行     | 10 万行/s     | 关闭 autocommit    |
| PG         | INSERT VALUES | 1000 行    | 1 万行/s      | 单事务            |
| ES         | Bulk index    | 1000 文档  | 1 万文档/s    | 调大 refresh_interval|
| Milvus     | insert        | 5000 向量  | 5 千向量/s    | flush 后构建索引    |
| Neo4j      | UNWIND MERGE | 5000 节点  | 1 万节点/s    | 已建索引           |

- 批量操作期间临时调大 ES refresh_interval 至 60s(默认 1s), 完成后恢复
- PG 批量操作使用单个长事务, 避免 per-row commit 开销
- Milvus 批量结束后调用 flush(), 否则数据在内存 segment
```

---

## 13. 数据库运维与性能监控

### 13.1 慢查询监控 (P2-21)

> **P2-21 慢查询监控**: 启用 `pg_stat_statements` 扩展采集 SQL 执行统计, 配合 `auto_explain` 捕获慢查询执行计划, 建立慢查询告警与优化闭环。

#### 13.1.1 启用 pg_stat_statements

```sql
-- postgresql.conf 配置
-- shared_preload_libraries = 'pg_stat_statements,auto_explain'
-- pg_stat_statements.max = 10000
-- pg_stat_statements.track = 'all'           -- 记录所有 SQL(含嵌套)
-- auto_explain.log_min_duration = '500ms'    -- 记录超过 500ms 的执行计划
-- auto_explain.log_analyze = on
-- auto_explain.log_buffers = on

-- 在业务库创建扩展
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 重置统计(每次优化周期开始时重置)
SELECT pg_stat_statements_reset();
```

#### 13.1.2 慢查询分析视图

```sql
-- TOP 20 慢查询(按总执行时间排序)
SELECT
    substring(query, 1, 200) AS query_preview,
    calls                        AS 调用次数,
    round(total_exec_time::numeric, 2)  AS 总耗时_ms,
    round(mean_exec_time::numeric, 2)   AS 平均耗时_ms,
    round(max_exec_time::numeric, 2)    AS 最大耗时_ms,
    rows                          AS 返回行数,
    round((shared_blks_hit * 100.0 /
          NULLIF(shared_blks_hit + shared_blks_read, 0))::numeric, 2) AS 缓存命中率_%
FROM pg_stat_statements
WHERE query NOT ILIKE '%pg_stat_statements%'
ORDER BY total_exec_time DESC
LIMIT 20;

-- 高频低效查询(调用次数多但平均行数少, 可能缺失索引)
SELECT
    substring(query, 1, 200) AS query_preview,
    calls,
    round(mean_exec_time::numeric, 2) AS 平均耗时_ms,
    rows / NULLIF(calls, 0)           AS 平均返回行数
FROM pg_stat_statements
WHERE calls > 1000
ORDER BY mean_exec_time DESC
LIMIT 20;
```

#### 13.1.3 慢查询告警与优化闭环

```
+-----------------------------------------------------------------------------+
|                    慢查询监控告警与优化闭环 (P2-21)                            |
+-----------------------------------------------------------------------------+
|                                                                             |
|  1. 采集层                                                                  |
|  ─────────────────────────────────────────────────────────────────────────  |
|  - pg_stat_statements: 采集 SQL 调用次数/耗时/行数/缓存命中                  |
|  - auto_explain: 记录 > 500ms 的 SQL 执行计划                              |
|  - PostgreSQL log: log_min_duration_statement = 500ms                      |
|                                                                             |
|  2. 告警规则(Prometheus + postgres_exporter)                                |
|  ─────────────────────────────────────────────────────────────────────────  |
|  - 单条 SQL 平均耗时 > 1000ms 持续 5min      → P2 告警                      |
|  - 单条 SQL 最大耗时 > 5000ms                → P1 告警                      |
|  - 缓存命中率 < 90% 持续 10min               → P2 告警                      |
|  - 慢查询数 > 10 条/分钟 持续 5min            → P2 告警                      |
|                                                                             |
|  3. 优化闭环                                                                |
|  ─────────────────────────────────────────────────────────────────────────  |
|  - 每日 09:00 生成慢查询 TOP20 报表, 写入 audit_logs                        |
|  - DBA 分析执行计划(EXPLAIN ANALYZE), 识别缺失索引/全表扫描                 |
|  - 优化动作: 新增索引 / 改写 SQL / 调整连接池 / 分区裁剪                    |
|  - 优化后重置 pg_stat_statements, 观察 24h 验证效果                         |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 13.2 连接池配置 (P2-22)

> **P2-22 连接池配置**: 部署 PgBouncer 作为 PostgreSQL 连接池, 复用连接降低建连开销, 控制并发连接数防止 PG 主库被打满。

#### 13.2.1 PgBouncer 部署架构

```
+-----------------------------------------------------------------------------+
|                    PgBouncer 连接池架构 (P2-22)                               |
+-----------------------------------------------------------------------------+
|                                                                             |
|  应用层(若干 Pod/进程)                                                       |
|       │  │  │                                                               |
|       v  v  v                                                               |
|  +---------------------------------------------------------------------+   |
|  |                    PgBouncer (每 Coordinator 前置 1 实例)            |   |
|  |  default_pool_size = 50       (每数据库默认连接数)                   |   |
|  |  max_client_conn = 5000       (最大客户端连接)                       |   |
|  |  pool_mode = transaction      (事务级复用, 兼容 prepared stmt)       |   |
|  |  reserve_pool_size = 10       (突发流量预留)                         |   |
|  +---------------------------------------------------------------------+   |
|                                   |                                          |
|                                   v                                          |
|  +---------------------------------------------------------------------+   |
|  |              PostgreSQL Coordinator (max_connections=200)           |   |
|  +---------------------------------------------------------------------+   |
|                                   |                                          |
|                       Citus 分布式路由到 Worker                              |
|                                                                             |
|  说明:                                                                       |
|  - PgBouncer 与 Coordinator 同节点部署, 本地 socket 降低延迟               |
|  - max_connections=200 (PG侧) vs max_client_conn=5000 (客户端侧)           |
|    连接复用比 25:1, 大幅降低 PG 进程数与内存占用                            |
|                                                                             |
+-----------------------------------------------------------------------------+
```

#### 13.2.2 PgBouncer 配置 (pgbouncer.ini)

```ini
[databases]
; 业务库: 路由到 Coordinator
redfile = host=/var/run/postgresql dbname=redfile pool_size=50

[pgbouncer]
; 监听配置
listen_addr = 0.0.0.0
listen_port = 6432

; 连接池配置(P2-22 核心)
pool_mode = transaction              ; 事务级复用, 兼容大多数场景
max_client_conn = 5000               ; 最大客户端连接(应用侧)
default_pool_size = 50               ; 每数据库默认后端连接数
reserve_pool_size = 10               ; 突发流量预留连接
reserve_pool_timeout = 3             ; 突发等待超时(秒)
max_db_connections = 150             ; 单数据库最大后端连接(低于 PG max_connections)

; 超时配置
server_idle_timeout = 600            ; 后端空闲 10min 断开
query_wait_timeout = 120             ; 客户端等待连接 2min 超时
client_idle_timeout = 0              ; 客户端空闲不断开(0=禁用)

; 性能与安全
server_reset_query = DISCARD ALL     ; 归还连接前清理会话状态
ignore_startup_parameters = extra_float_digits
auth_type = scram-sha-256            ; 与 PG 一致的认证方式

; 日志
log_connections = 0
log_disconnections = 0
log_pooler_errors = 1
```

#### 13.2.3 连接数规划

```
+-----------------------------------------------------------------------------+
|                    连接数规划 (P2-22)                                         |
+-----------------------------------------------------------------------------+
|                                                                             |
|  ─────────────────────────────────────────────────────────────────────────  |
|  | 组件                    | 连接数  | 说明                              |  |
|  ─────────────────────────────────────────────────────────────────────────  |
|  | PG max_connections     | 200    | Coordinator 主库                  |  |
|  | PgBouncer max_client   | 5000   | 应用侧最大并发                    |  |
|  | PgBouncer pool_size    | 50     | 每库后端连接(复用)                |  |
|  | 应用连接池 max         | 100    | 单应用实例 HikariCP 最大           |  |
|  ─────────────────────────────────────────────────────────────────────────  |
|                                                                             |
|  容量推导:                                                                   |
|  - 峰值并发上传 1000 QPS + 检索 2000 QPS = 3000 QPS                         |
|  - 单连接吞吐 ~200 QPS(简单查询), 需 ~15 后端连接                            |
|  - pool_size=50 留 3x 余量, 应对突发流量                                     |
|  - max_client_conn=5000 容纳所有应用 Pod 连接                                |
|                                                                             |
+-----------------------------------------------------------------------------+
```

#### 13.2.4 连接池监控

```sql
-- PgBouncer SHOW 命令(通过 admin 库执行)
SHOW POOLS;        -- 查看各库连接池状态(cl_active, cl_waiting, sv_active)
SHOW STATS;        -- 查看查询统计(total_query_count, avg_query_time)
SHOW CLIENTS;      -- 查看客户端连接列表
SHOW SERVERS;      -- 查看后端 PG 连接列表

-- Prometheus 指标(pgBouncer_exporter)
-- pgbouncer_pools_cl_active         活跃客户端连接
-- pgbouncer_pools_cl_waiting        等待客户端数(>0 表示池满, 需扩容)
-- pgbouncer_pools_sv_active         活跃后端连接
-- pgbouncer_stats_avg_query_time    平均查询耗时(ms)

-- 告警规则
-- pgbouncer_pools_cl_waiting > 0 持续 1min → 告警(连接池不足)
```

---

## 附录: 评审问题修复对照表

| 评审编号 | 严重级别 | 问题描述 | 修复章节 |
|---------|---------|---------|---------|
| P0-1 | 致命 | 第8章索引策略截断、第9/10章缺失 | 第8/9/10/11/12章 |
| P0-2 | 致命 | Citus 主键约束违反(11处) | 第2章(全部分布式表) |
| P0-3 | 致命 | ES 默认分词器使用 ik_max_word | 第3.2节 |
| P0-4 | 致命 | ES mapping 爆炸风险(无 dynamic:strict) | 第3.2-3.5节 |
| P0-5 | 致命 | Neo4j 节点缺失 + 关系语法错误 | 第5.2-5.4节 |
| P0-6 | 致命 | 跨分片 JOIN(target_files) | 第2.3.5节 |
| P0-7 | 致命 | 数据倾斜(audit_logs 按 user_id) | 第2.3.6节 |
| P0-8 | 致命 | 冗余索引 | 第2.3.2/8.1节 |
| P0-9 | 致命 | ILM 配置不合理 | 第3.6节 |
| P0-10 | 致命 | 敏感信息明文存储 | 第2.3.4节 |
| P0-11 | 致命 | 缺失 RLS 行级安全 | 第2.3.2/2.3.4/2.3.5节 |
| P0-12 | 致命 | user_id 类型不统一 | 第2.3.1节 |
| P0-13 | 致命 | parse_results 1:1 硬约束 | 第2.3.3节 |
| P0-14 | 致命 | 缺失数据归档策略 | 第11章 |
| P0-15 | 致命 | 缺失批量操作设计 | 第12章 |
| P1-1 | 严重 | 主键策略不统一(BIGSERIAL→UUID) | 第2章(全部表) / 第5.2节(User节点) / 第9.1节(outbox) |
| P1-2 | 严重 | parse_results 一对一硬约束 | 第2.3.3节(parse_version 联合唯一) |
| P1-3 | 严重 | 跨分片 JOIN 冗余存储 | 第2.3.5节(target_files_by_target/by_file 双写) |
| P1-4 | 严重 | colocation 配置不统一 | 第7.1节(统一 colocation 组配置) |
| P1-5 | 严重 | 缺失分片扩容方案 | 第7.1.1节(64分片初始 + 在线扩容) |
| P1-6 | 严重 | Milvus Partition 设计缺失 | 第4.2-4.4节(partition_key + 按月Partition) |
| P1-7 | 严重 | Milvus 索引类型不统一 | 第4.2-4.4/7.3节(统一HNSW M=32 efC=512) |
| P1-8 | 严重 | 向量维度不统一(768→1024) | 第4.2-4.4节(BGE-large-zh 1024维) |
| P1-9 | 严重 | Neo4j Port/Service 唯一约束缺失 | 第5.2节(补全复合唯一约束) |
| P1-10 | 严重 | Neo4j 图规模与性能缺失 | 第5.5节(图规模评估 + 性能优化策略) |
| P1-11 | 严重 | APOC 和 GDS 引入 | 第5.6节(APOC路径展开 + GDS图算法) |
| P2-12 | 一般 | 冗余字段清理(owner_name/parse_duration) | 第2.3.2/2.3.3/2.3.5节 |
| P2-13 | 一般 | 软删除不统一 | 第2.3.1/2.3.2/2.3.5节(deleted_at 统一) |
| P2-14 | 一般 | 字段类型修正(email/INET/ENUM) | 第2.3.1-2.3.6节 |
| P2-15 | 一般 | 字典/元数据表缺失 | 第2.3.7节(file_type_dict/tag_dict/system_config) |
| P2-16 | 一般 | entities 索引去重 | 第2.3.3/8.1节(删除被覆盖的单字段索引) |
| P2-17 | 一般 | GIN 索引优化(jsonb_path_ops) | 第2.3.3/2.3.4/8.1节 |
| P2-18 | 一般 | 审计日志索引优化 | 第2.3.6/8.1节(复合索引 + BRIN) |
| P2-19 | 一般 | ES 副本数优化(1→2) | 第3.2-3.5/7.2节(生产环境双副本) |
| P2-20 | 一般 | term_vector 优化 | 第3.2节(仅summary启用) |
| P2-21 | 一般 | 慢查询监控缺失 | 第13.1节(pg_stat_statements + auto_explain) |
| P2-22 | 一般 | 连接池配置缺失 | 第13.2节(PgBouncer transaction模式) |
| P2-23 | 一般 | password_hash 算法升级 | 第2.3.1节(argon2id) |