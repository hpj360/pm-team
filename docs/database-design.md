# 网络安全红方文件汇聚平台数据库设计文档

## 文档信息

| 项目名称 | 网络安全红方文件汇聚平台 |
|---------|----------------------|
| 版本     | v1.0                 |
| 创建日期 | 2026-03-15           |
| 文档状态 | 初稿                  |
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
-- 用户表
CREATE TABLE users (
    id              BIGSERIAL,
    user_id         VARCHAR(64) NOT NULL,
    username        VARCHAR(64) NOT NULL,
    password_hash   VARCHAR(256) NOT NULL,
    email           VARCHAR(128),
    phone           VARCHAR(32),
    real_name       VARCHAR(64),
    department      VARCHAR(128),
    status          SMALLINT NOT NULL DEFAULT 1,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    last_login_ip   VARCHAR(64),
    login_fail_count SMALLINT DEFAULT 0,
    locked_until    TIMESTAMP WITH TIME ZONE,
    password_changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT,
    
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_user_id UNIQUE (user_id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 索引
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_department ON users(department);
CREATE INDEX idx_users_created_at ON users(created_at);

-- 注释
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '自增主键';
COMMENT ON COLUMN users.user_id IS '用户唯一标识(UUID)';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.password_hash IS '密码哈希值(bcrypt)';
COMMENT ON COLUMN users.status IS '状态: 0-禁用, 1-启用, 2-锁定';
COMMENT ON COLUMN users.login_fail_count IS '连续登录失败次数';
COMMENT ON COLUMN users.locked_until IS '锁定截止时间';
```

##### 角色表 (roles)

```sql
-- 角色表
CREATE TABLE roles (
    id              BIGSERIAL,
    role_id         VARCHAR(64) NOT NULL,
    role_name       VARCHAR(64) NOT NULL,
    role_code       VARCHAR(64) NOT NULL,
    description     TEXT,
    parent_id       BIGINT,
    level           SMALLINT DEFAULT 1,
    status          SMALLINT NOT NULL DEFAULT 1,
    is_system       BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT,
    
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_role_id UNIQUE (role_id),
    CONSTRAINT uk_roles_role_code UNIQUE (role_code),
    CONSTRAINT fk_roles_parent FOREIGN KEY (parent_id) REFERENCES roles(id)
);

-- 索引
CREATE INDEX idx_roles_status ON roles(status);
CREATE INDEX idx_roles_parent_id ON roles(parent_id);

-- 注释
COMMENT ON TABLE roles IS '角色表';
COMMENT ON COLUMN roles.role_code IS '角色编码(如: ADMIN, OPERATOR, ANALYST)';
COMMENT ON COLUMN roles.is_system IS '是否系统内置角色';
COMMENT ON COLUMN roles.level IS '角色层级';
```

##### 权限表 (permissions)

```sql
-- 权限表
CREATE TABLE permissions (
    id              BIGSERIAL,
    perm_id         VARCHAR(64) NOT NULL,
    perm_name       VARCHAR(128) NOT NULL,
    perm_code       VARCHAR(128) NOT NULL,
    resource_type   VARCHAR(64) NOT NULL,
    resource_key    VARCHAR(256),
    action          VARCHAR(32) NOT NULL,
    parent_id       BIGINT,
    description     TEXT,
    status          SMALLINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permissions_perm_id UNIQUE (perm_id),
    CONSTRAINT uk_permissions_perm_code UNIQUE (perm_code),
    CONSTRAINT fk_permissions_parent FOREIGN KEY (parent_id) REFERENCES permissions(id)
);

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
CREATE TABLE user_roles (
    id              BIGSERIAL,
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    
    CONSTRAINT pk_user_roles PRIMARY KEY (id),
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- 索引
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

COMMENT ON TABLE user_roles IS '用户角色关联表';
```

##### 角色权限关联表 (role_permissions)

```sql
-- 角色权限关联表
CREATE TABLE role_permissions (
    id              BIGSERIAL,
    role_id         BIGINT NOT NULL,
    perm_id         BIGINT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    
    CONSTRAINT pk_role_permissions PRIMARY KEY (id),
    CONSTRAINT uk_role_permissions_role_perm UNIQUE (role_id, perm_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_role_permissions_perm FOREIGN KEY (perm_id) REFERENCES permissions(id)
);

-- 索引
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_perm_id ON role_permissions(perm_id);

COMMENT ON TABLE role_permissions IS '角色权限关联表';
```

#### 2.3.2 文件元数据表

##### 文件元数据表 (file_metadata)

```sql
-- 文件元数据表 (分布式表)
CREATE TABLE file_metadata (
    id              BIGSERIAL,
    file_id         VARCHAR(64) NOT NULL,
    file_name       VARCHAR(512) NOT NULL,
    file_size       BIGINT NOT NULL,
    file_type       VARCHAR(64),
    content_type    VARCHAR(128),
    extension       VARCHAR(32),
    
    -- 哈希值
    md5_hash        VARCHAR(32),
    sha256_hash     VARCHAR(64) NOT NULL,
    
    -- 存储信息
    storage_path    VARCHAR(1024) NOT NULL,
    storage_bucket  VARCHAR(128),
    storage_tier    VARCHAR(32) DEFAULT 'hot',
    
    -- 目录信息
    directory_id    BIGINT,
    directory_path  VARCHAR(1024),
    
    -- 所有者信息
    owner_id        BIGINT NOT NULL,
    owner_name      VARCHAR(64),
    
    -- 文件状态
    status          SMALLINT NOT NULL DEFAULT 1,
    is_deleted      BOOLEAN DEFAULT FALSE,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    deleted_by      BIGINT,
    
    -- 引用计数(用于秒传)
    ref_count       INTEGER DEFAULT 1,
    
    -- 安全标记
    security_level  SMALLINT DEFAULT 1,
    is_sensitive    BOOLEAN DEFAULT FALSE,
    is_malicious    BOOLEAN DEFAULT FALSE,
    
    -- 时间戳
    upload_time     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 元数据扩展(JSONB)
    extra_metadata  JSONB,
    
    CONSTRAINT pk_file_metadata PRIMARY KEY (id),
    CONSTRAINT uk_file_metadata_file_id UNIQUE (file_id),
    CONSTRAINT uk_file_metadata_sha256 UNIQUE (sha256_hash)
);

-- 创建分布式表(Citus)
SELECT create_distributed_table('file_metadata', 'file_id');

-- 索引
CREATE INDEX idx_file_metadata_owner_id ON file_metadata(owner_id);
CREATE INDEX idx_file_metadata_status ON file_metadata(status);
CREATE INDEX idx_file_metadata_file_type ON file_metadata(file_type);
CREATE INDEX idx_file_metadata_directory_id ON file_metadata(directory_id);
CREATE INDEX idx_file_metadata_upload_time ON file_metadata(upload_time DESC);
CREATE INDEX idx_file_metadata_created_at ON file_metadata(created_at DESC);
CREATE INDEX idx_file_metadata_sha256 ON file_metadata(sha256_hash);

-- GIN索引(JSONB)
CREATE INDEX idx_file_metadata_extra_metadata ON file_metadata USING GIN(extra_metadata);

-- 注释
COMMENT ON TABLE file_metadata IS '文件元数据表(分布式)';
COMMENT ON COLUMN file_metadata.storage_tier IS '存储层级: hot, warm, cold, archive';
COMMENT ON COLUMN file_metadata.status IS '状态: 0-上传中, 1-正常, 2-解析中, 3-分析中, 4-已删除';
COMMENT ON COLUMN file_metadata.security_level IS '安全等级: 1-公开, 2-内部, 3-机密, 4-绝密';
COMMENT ON COLUMN file_metadata.ref_count IS '引用计数(秒传时多个文件共享同一物理存储)';
```

##### 文件标签表 (file_tags)

```sql
-- 文件标签表
CREATE TABLE file_tags (
    id              BIGSERIAL,
    file_id         VARCHAR(64) NOT NULL,
    tag_name        VARCHAR(64) NOT NULL,
    tag_type        VARCHAR(32) DEFAULT 'user',
    tag_color       VARCHAR(16),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    
    CONSTRAINT pk_file_tags PRIMARY KEY (id),
    CONSTRAINT uk_file_tags_file_tag UNIQUE (file_id, tag_name)
);

-- 创建分布式表
SELECT create_distributed_table('file_tags', 'file_id');

-- 索引
CREATE INDEX idx_file_tags_tag_name ON file_tags(tag_name);
CREATE INDEX idx_file_tags_tag_type ON file_tags(tag_type);

COMMENT ON TABLE file_tags IS '文件标签表';
COMMENT ON COLUMN file_tags.tag_type IS '标签类型: user-用户标签, system-系统标签';
```

##### 文件版本表 (file_versions)

```sql
-- 文件版本表
CREATE TABLE file_versions (
    id              BIGSERIAL,
    version_id      VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    version_number  INTEGER NOT NULL,
    sha256_hash     VARCHAR(64) NOT NULL,
    storage_path    VARCHAR(1024) NOT NULL,
    file_size       BIGINT NOT NULL,
    change_summary  TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    
    CONSTRAINT pk_file_versions PRIMARY KEY (id),
    CONSTRAINT uk_file_versions_version_id UNIQUE (version_id),
    CONSTRAINT uk_file_versions_file_version UNIQUE (file_id, version_number)
);

-- 创建分布式表
SELECT create_distributed_table('file_versions', 'file_id');

-- 索引
CREATE INDEX idx_file_versions_file_id ON file_versions(file_id);

COMMENT ON TABLE file_versions IS '文件版本表';
```

##### 目录表 (directories)

```sql
-- 目录表
CREATE TABLE directories (
    id              BIGSERIAL,
    dir_id          VARCHAR(64) NOT NULL,
    dir_name        VARCHAR(256) NOT NULL,
    dir_path        VARCHAR(1024) NOT NULL,
    parent_id       BIGINT,
    owner_id        BIGINT NOT NULL,
    status          SMALLINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_directories PRIMARY KEY (id),
    CONSTRAINT uk_directories_dir_id UNIQUE (dir_id),
    CONSTRAINT uk_directories_path UNIQUE (dir_path),
    CONSTRAINT fk_directories_parent FOREIGN KEY (parent_id) REFERENCES directories(id)
);

-- 索引
CREATE INDEX idx_directories_owner_id ON directories(owner_id);
CREATE INDEX idx_directories_parent_id ON directories(parent_id);

COMMENT ON TABLE directories IS '目录表';
```

#### 2.3.3 解析结果表

##### 解析结果表 (parse_results)

```sql
-- 解析结果表 (分布式表)
CREATE TABLE parse_results (
    id              BIGSERIAL,
    parse_id        VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    
    -- 解析状态
    parse_status    SMALLINT NOT NULL DEFAULT 0,
    parse_progress  SMALLINT DEFAULT 0,
    parse_start_at  TIMESTAMP WITH TIME ZONE,
    parse_end_at    TIMESTAMP WITH TIME ZONE,
    parse_duration  INTEGER,
    
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
    file_metadata   JSONB,
    exif_data       JSONB,
    
    -- 实体信息(JSONB)
    entities        JSONB,
    entity_count    INTEGER DEFAULT 0,
    
    -- 错误信息
    error_code      VARCHAR(32),
    error_message   TEXT,
    
    -- 时间戳
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_parse_results PRIMARY KEY (id),
    CONSTRAINT uk_parse_results_parse_id UNIQUE (parse_id),
    CONSTRAINT uk_parse_results_file_id UNIQUE (file_id)
);

-- 创建分布式表
SELECT create_distributed_table('parse_results', 'file_id');

-- 索引
CREATE INDEX idx_parse_results_parse_status ON parse_results(parse_status);
CREATE INDEX idx_parse_results_created_at ON parse_results(created_at DESC);

-- GIN索引(JSONB)
CREATE INDEX idx_parse_results_entities ON parse_results USING GIN(entities);
CREATE INDEX idx_parse_results_file_metadata ON parse_results USING GIN(file_metadata);

COMMENT ON TABLE parse_results IS '文件解析结果表';
COMMENT ON COLUMN parse_results.parse_status IS '解析状态: 0-待解析, 1-解析中, 2-成功, 3-失败';
COMMENT ON COLUMN parse_results.detected_format IS '检测到的格式(基于扩展名)';
COMMENT ON COLUMN parse_results.actual_format IS '实际格式(基于魔数)';
```

##### 实体表 (entities)

```sql
-- 实体表 (分布式表)
CREATE TABLE entities (
    id              BIGSERIAL,
    entity_id       VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    
    -- 实体类型
    entity_type     VARCHAR(32) NOT NULL,
    entity_value    VARCHAR(1024) NOT NULL,
    normalized_value VARCHAR(1024),
    
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
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_entities PRIMARY KEY (id),
    CONSTRAINT uk_entities_entity_id UNIQUE (entity_id)
);

-- 创建分布式表
SELECT create_distributed_table('entities', 'file_id');

-- 索引
CREATE INDEX idx_entities_file_id ON entities(file_id);
CREATE INDEX idx_entities_entity_type ON entities(entity_type);
CREATE INDEX idx_entities_entity_value ON entities(entity_value);
CREATE INDEX idx_entities_type_value ON entities(entity_type, entity_value);

COMMENT ON TABLE entities IS '实体表';
COMMENT ON COLUMN entities.entity_type IS '实体类型: IP, DOMAIN, EMAIL, URL, CVE, MD5, SHA256, PORT, PERSON, ORG';
```

##### 压缩包内容表 (archive_contents)

```sql
-- 压缩包内容表
CREATE TABLE archive_contents (
    id              BIGSERIAL,
    archive_id      VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    
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
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_archive_contents PRIMARY KEY (id),
    CONSTRAINT uk_archive_contents_archive_path UNIQUE (archive_id, inner_path)
);

-- 创建分布式表
SELECT create_distributed_table('archive_contents', 'archive_id');

-- 索引
CREATE INDEX idx_archive_contents_file_id ON archive_contents(file_id);

COMMENT ON TABLE archive_contents IS '压缩包内容表';
```

##### 流量会话表 (network_sessions)

```sql
-- 流量会话表
CREATE TABLE network_sessions (
    id              BIGSERIAL,
    session_id      VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    
    -- 五元组
    src_ip          VARCHAR(64) NOT NULL,
    src_port        INTEGER,
    dst_ip          VARCHAR(64) NOT NULL,
    dst_port        INTEGER,
    protocol        VARCHAR(16) NOT NULL,
    
    -- 会话信息
    session_start   TIMESTAMP WITH TIME ZONE,
    session_end     TIMESTAMP WITH TIME ZONE,
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
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_network_sessions PRIMARY KEY (id),
    CONSTRAINT uk_network_sessions_session_id UNIQUE (session_id)
);

-- 创建分布式表
SELECT create_distributed_table('network_sessions', 'file_id');

-- 索引
CREATE INDEX idx_network_sessions_file_id ON network_sessions(file_id);
CREATE INDEX idx_network_sessions_src_ip ON network_sessions(src_ip);
CREATE INDEX idx_network_sessions_dst_ip ON network_sessions(dst_ip);
CREATE INDEX idx_network_sessions_protocol ON network_sessions(protocol);
CREATE INDEX idx_network_sessions_session_start ON network_sessions(session_start);

COMMENT ON TABLE network_sessions IS '网络流量会话表';
```

#### 2.3.4 分析结果表

##### 分析结果表 (analysis_results)

```sql
-- 分析结果表 (分布式表)
CREATE TABLE analysis_results (
    id              BIGSERIAL,
    analysis_id     VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    
    -- 分析类型
    analysis_type   VARCHAR(64) NOT NULL,
    analysis_name   VARCHAR(128),
    
    -- 分析状态
    analysis_status SMALLINT NOT NULL DEFAULT 0,
    analysis_progress SMALLINT DEFAULT 0,
    analysis_start_at TIMESTAMP WITH TIME ZONE,
    analysis_end_at TIMESTAMP WITH TIME ZONE,
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
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_analysis_results PRIMARY KEY (id),
    CONSTRAINT uk_analysis_results_analysis_id UNIQUE (analysis_id)
);

-- 创建分布式表
SELECT create_distributed_table('analysis_results', 'file_id');

-- 索引
CREATE INDEX idx_analysis_results_file_id ON analysis_results(file_id);
CREATE INDEX idx_analysis_results_analysis_type ON analysis_results(analysis_type);
CREATE INDEX idx_analysis_results_risk_level ON analysis_results(risk_level);
CREATE INDEX idx_analysis_results_analysis_status ON analysis_results(analysis_status);
CREATE INDEX idx_analysis_results_created_at ON analysis_results(created_at DESC);

-- GIN索引(JSONB)
CREATE INDEX idx_analysis_results_result ON analysis_results USING GIN(result);

COMMENT ON TABLE analysis_results IS '文件分析结果表';
COMMENT ON COLUMN analysis_results.analysis_type IS '分析类型: MALWARE_DETECTION, VULNERABILITY, SENSITIVE_DATA, THREAT_INTEL';
COMMENT ON COLUMN analysis_results.analysis_status IS '分析状态: 0-待分析, 1-分析中, 2-成功, 3-失败';
COMMENT ON COLUMN analysis_results.risk_level IS '风险等级: 0-未知, 1-低, 2-中, 3-高, 4-严重';
```

##### 漏洞信息表 (vulnerabilities)

```sql
-- 漏洞信息表
CREATE TABLE vulnerabilities (
    id              BIGSERIAL,
    vuln_id         VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    
    -- 漏洞标识
    cve_id          VARCHAR(32),
    cnvd_id         VARCHAR(32),
    cnnvd_id        VARCHAR(32),
    vuln_name       VARCHAR(256),
    
    -- 漏洞分类
    vuln_type       VARCHAR(64),
    vuln_category   VARCHAR(64),
    
    -- 严重程度
    severity        VARCHAR(16),
    cvss_score      DECIMAL(3,1),
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
    discovered_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_vulnerabilities PRIMARY KEY (id),
    CONSTRAINT uk_vulnerabilities_vuln_id UNIQUE (vuln_id)
);

-- 创建分布式表
SELECT create_distributed_table('vulnerabilities', 'file_id');

-- 索引
CREATE INDEX idx_vulnerabilities_file_id ON vulnerabilities(file_id);
CREATE INDEX idx_vulnerabilities_cve_id ON vulnerabilities(cve_id);
CREATE INDEX idx_vulnerabilities_severity ON vulnerabilities(severity);
CREATE INDEX idx_vulnerabilities_status ON vulnerabilities(status);

COMMENT ON TABLE vulnerabilities IS '漏洞信息表';
```

##### 敏感信息表 (sensitive_findings)

```sql
-- 敏感信息表
CREATE TABLE sensitive_findings (
    id              BIGSERIAL,
    finding_id      VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    
    -- 敏感信息类型
    finding_type    VARCHAR(64) NOT NULL,
    finding_name    VARCHAR(128),
    
    -- 敏感内容
    content_hash    VARCHAR(64),
    content_preview VARCHAR(256),
    content_full    TEXT,
    
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
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_sensitive_findings PRIMARY KEY (id),
    CONSTRAINT uk_sensitive_findings_finding_id UNIQUE (finding_id)
);

-- 创建分布式表
SELECT create_distributed_table('sensitive_findings', 'file_id');

-- 索引
CREATE INDEX idx_sensitive_findings_file_id ON sensitive_findings(file_id);
CREATE INDEX idx_sensitive_findings_type ON sensitive_findings(finding_type);
CREATE INDEX idx_sensitive_findings_status ON sensitive_findings(status);

COMMENT ON TABLE sensitive_findings IS '敏感信息发现表';
COMMENT ON COLUMN sensitive_findings.finding_type IS '类型: PASSWORD, API_KEY, CERTIFICATE, CREDENTIAL, PII, CREDIT_CARD';
```

#### 2.3.5 目标画像表

##### 目标表 (targets)

```sql
-- 目标表
CREATE TABLE targets (
    id              BIGSERIAL,
    target_id       VARCHAR(64) NOT NULL,
    target_name     VARCHAR(256) NOT NULL,
    target_type     VARCHAR(64) NOT NULL,
    
    -- 基本信息
    description     TEXT,
    industry        VARCHAR(128),
    region          VARCHAR(128),
    country         VARCHAR(64),
    
    -- 标签(JSONB)
    tags            JSONB,
    
    -- 负责人
    owner_id        BIGINT NOT NULL,
    owner_name      VARCHAR(64),
    
    -- 团队成员(JSONB)
    team_members    JSONB,
    
    -- 画像统计
    file_count      INTEGER DEFAULT 0,
    asset_count     INTEGER DEFAULT 0,
    vuln_count      INTEGER DEFAULT 0,
    
    -- 状态
    status          SMALLINT NOT NULL DEFAULT 1,
    
    -- 时间戳
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_targets PRIMARY KEY (id),
    CONSTRAINT uk_targets_target_id UNIQUE (target_id)
);

-- 创建分布式表
SELECT create_distributed_table('targets', 'target_id');

-- 索引
CREATE INDEX idx_targets_owner_id ON targets(owner_id);
CREATE INDEX idx_targets_target_type ON targets(target_type);
CREATE INDEX idx_targets_status ON targets(status);
CREATE INDEX idx_targets_created_at ON targets(created_at DESC);

-- GIN索引(JSONB)
CREATE INDEX idx_targets_tags ON targets USING GIN(tags);

COMMENT ON TABLE targets IS '目标表';
COMMENT ON COLUMN targets.target_type IS '目标类型: ENTERPRISE, ORGANIZATION, SYSTEM, PERSON, DOMAIN';
```

##### 目标文件关联表 (target_files)

```sql
-- 目标文件关联表
CREATE TABLE target_files (
    id              BIGSERIAL,
    target_id       VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64) NOT NULL,
    
    -- 关联类型
    relation_type   VARCHAR(32) DEFAULT 'related',
    relation_desc   TEXT,
    
    -- 关联置信度
    confidence      DECIMAL(5,4),
    
    -- 时间戳
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    
    CONSTRAINT pk_target_files PRIMARY KEY (id),
    CONSTRAINT uk_target_files_target_file UNIQUE (target_id, file_id)
);

-- 创建分布式表
SELECT create_distributed_table('target_files', 'target_id');

-- 索引
CREATE INDEX idx_target_files_file_id ON target_files(file_id);
CREATE INDEX idx_target_files_relation_type ON target_files(relation_type);

COMMENT ON TABLE target_files IS '目标文件关联表';
```

##### 目标资产表 (target_assets)

```sql
-- 目标资产表
CREATE TABLE target_assets (
    id              BIGSERIAL,
    asset_id        VARCHAR(64) NOT NULL,
    target_id       VARCHAR(64) NOT NULL,
    
    -- 资产类型
    asset_type      VARCHAR(32) NOT NULL,
    asset_value     VARCHAR(512) NOT NULL,
    
    -- 资产属性(JSONB)
    properties      JSONB,
    
    -- 来源信息
    source_file_id  VARCHAR(64),
    discovered_at   TIMESTAMP WITH TIME ZONE,
    
    -- 状态
    status          VARCHAR(32) DEFAULT 'active',
    
    -- 标签
    tags            JSONB,
    
    -- 时间戳
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_target_assets PRIMARY KEY (id),
    CONSTRAINT uk_target_assets_asset_id UNIQUE (asset_id)
);

-- 创建分布式表
SELECT create_distributed_table('target_assets', 'target_id');

-- 索引
CREATE INDEX idx_target_assets_target_id ON target_assets(target_id);
CREATE INDEX idx_target_assets_asset_type ON target_assets(asset_type);
CREATE INDEX idx_target_assets_asset_value ON target_assets(asset_value);
CREATE INDEX idx_target_assets_status ON target_assets(status);

COMMENT ON TABLE target_assets IS '目标资产表';
COMMENT ON COLUMN target_assets.asset_type IS '资产类型: IP, DOMAIN, PORT, SERVICE, HOST, NETWORK';
```

##### 目标人员表 (target_persons)

```sql
-- 目标人员表
CREATE TABLE target_persons (
    id              BIGSERIAL,
    person_id       VARCHAR(64) NOT NULL,
    target_id       VARCHAR(64) NOT NULL,
    
    -- 人员信息
    name            VARCHAR(128),
    email           VARCHAR(256),
    phone           VARCHAR(64),
    position        VARCHAR(128),
    department      VARCHAR(256),
    
    -- 来源信息
    source_file_id  VARCHAR(64),
    discovered_at   TIMESTAMP WITH TIME ZONE,
    
    -- 属性(JSONB)
    properties      JSONB,
    
    -- 时间戳
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_target_persons PRIMARY KEY (id),
    CONSTRAINT uk_target_persons_person_id UNIQUE (person_id)
);

-- 创建分布式表
SELECT create_distributed_table('target_persons', 'target_id');

-- 索引
CREATE INDEX idx_target_persons_target_id ON target_persons(target_id);
CREATE INDEX idx_target_persons_email ON target_persons(email);

COMMENT ON TABLE target_persons IS '目标人员表';
```

#### 2.3.6 审计日志表

##### 审计日志表 (audit_logs)

```sql
-- 审计日志表 (分布式表)
CREATE TABLE audit_logs (
    id              BIGSERIAL,
    log_id          VARCHAR(64) NOT NULL,
    
    -- 用户信息
    user_id         BIGINT,
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
    
    -- 请求信息
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    request_id      VARCHAR(64),
    
    -- 时间戳
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT uk_audit_logs_log_id UNIQUE (log_id)
);

-- 创建分布式表
SELECT create_distributed_table('audit_logs', 'user_id');

-- 索引
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_resource_type ON audit_logs(resource_type);
CREATE INDEX idx_audit_logs_resource_id ON audit_logs(resource_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_result ON audit_logs(result);

-- GIN索引(JSONB)
CREATE INDEX idx_audit_logs_details ON audit_logs USING GIN(details);

-- 分区(按月)
CREATE TABLE audit_logs_y2026m03 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

COMMENT ON TABLE audit_logs IS '审计日志表';
COMMENT ON COLUMN audit_logs.action_category IS '操作分类: AUTH, FILE, SEARCH, ANALYSIS, TARGET, SYSTEM';
COMMENT ON COLUMN audit_logs.result IS '操作结果: SUCCESS, FAILURE, DENIED';
```

##### 上传任务表 (upload_tasks)

```sql
-- 上传任务表
CREATE TABLE upload_tasks (
    id              BIGSERIAL,
    upload_id       VARCHAR(64) NOT NULL,
    file_id         VARCHAR(64),
    
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
    user_id         BIGINT NOT NULL,
    
    -- 过期时间
    expires_at      TIMESTAMP WITH TIME ZONE,
    
    -- 时间戳
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT pk_upload_tasks PRIMARY KEY (id),
    CONSTRAINT uk_upload_tasks_upload_id UNIQUE (upload_id)
);

-- 索引
CREATE INDEX idx_upload_tasks_user_id ON upload_tasks(user_id);
CREATE INDEX idx_upload_tasks_status ON upload_tasks(status);
CREATE INDEX idx_upload_tasks_expires_at ON upload_tasks(expires_at);

COMMENT ON TABLE upload_tasks IS '上传任务表';
COMMENT ON COLUMN upload_tasks.status IS '状态: 0-进行中, 1-完成, 2-取消, 3-过期';
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
      "number_of_replicas": 1,
      "refresh_interval": "5s",
      "index.lifecycle.name": "file_content_policy",
      "index.lifecycle.rollover_alias": "file_content",
      "analysis": {
        "analyzer": {
          "default": {
            "type": "ik_max_word"
          },
          "ik_smart_analyzer": {
            "type": "ik_smart"
          },
          "ik_max_word_analyzer": {
            "type": "ik_max_word"
          },
          "keyword_analyzer": {
            "type": "keyword"
          },
          "standard_analyzer": {
            "type": "standard"
          }
        },
        "tokenizer": {
          "ik_max_word": {
            "type": "ik_max_word"
          },
          "ik_smart": {
            "type": "ik_smart"
          }
        }
      }
    },
    "mappings": {
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
          "search_analyzer": "ik_smart_analyzer",
          "term_vector": "with_positions_offsets"
        },
        "text_content_en": {
          "type": "text",
          "analyzer": "standard_analyzer"
        },
        "summary": {
          "type": "text",
          "analyzer": "ik_max_word_analyzer"
        },
        "tags": {
          "type": "keyword"
        },
        "entities": {
          "type": "nested",
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
          "enabled": true
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

### 3.3 实体索引 (entity_index)

```json
{
  "index_patterns": ["entity_index-*"],
  "template": {
    "settings": {
      "number_of_shards": 6,
      "number_of_replicas": 1,
      "refresh_interval": "5s",
      "index.lifecycle.name": "entity_policy"
    },
    "mappings": {
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
      "number_of_replicas": 1,
      "refresh_interval": "5s",
      "index.lifecycle.name": "analysis_result_policy"
    },
    "mappings": {
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
          "enabled": true
        },
        "vulnerabilities": {
          "type": "nested",
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
      "number_of_replicas": 1,
      "refresh_interval": "5s"
    },
    "mappings": {
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
            "number_of_shards": 1
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
          "set_priority": {
            "priority": 0
          }
        }
      },
      "delete": {
        "min_age": "365d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```

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
|  索引类型: HNSW (高性能) / IVF_FLAT (高精度)                                  |
|  向量维度: 768 (BGE-large) / 1024 (M3E-large)                               |
|  相似度度量: COSINE (余弦相似度)                                              |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 4.2 文件内容向量Collection (file_content_vector)

```python
from pymilvus import CollectionSchema, FieldSchema, DataType, Collection

# 定义字段
fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
    FieldSchema(name="vector_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="file_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="chunk_index", dtype=DataType.INT32),
    FieldSchema(name="chunk_text", dtype=DataType.VARCHAR, max_length=2048),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=768),
    FieldSchema(name="language", dtype=DataType.VARCHAR, max_length=16),
    FieldSchema(name="source_section", dtype=DataType.VARCHAR, max_length=128),
    FieldSchema(name="created_at", dtype=DataType.INT64)
]

# 创建Schema
schema = CollectionSchema(
    fields=fields,
    description="文件内容向量索引",
    enable_dynamic_field=True
)

# 创建Collection
collection = Collection(
    name="file_content_vector",
    schema=schema,
    using='default',
    shards_num=6
)

# 创建索引
index_params = {
    "metric_type": "COSINE",
    "index_type": "HNSW",
    "params": {
        "M": 16,
        "efConstruction": 256
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
```

**Collection参数说明：**

| 参数 | 值 | 说明 |
|-----|-----|------|
| 向量维度 | 768 | BGE-large模型输出维度 |
| 相似度度量 | COSINE | 余弦相似度 |
| 索引类型 | HNSW | 高性能图索引 |
| M参数 | 16 | HNSW图节点连接数 |
| efConstruction | 256 | 构建索引时的搜索范围 |
| 分片数 | 6 | 支持并行查询 |

### 4.3 目标画像向量Collection (target_profile_vector)

```python
# 定义字段
fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
    FieldSchema(name="vector_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="target_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="target_name", dtype=DataType.VARCHAR, max_length=256),
    FieldSchema(name="target_type", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="profile_text", dtype=DataType.VARCHAR, max_length=4096),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=768),
    FieldSchema(name="industry", dtype=DataType.VARCHAR, max_length=128),
    FieldSchema(name="region", dtype=DataType.VARCHAR, max_length=128),
    FieldSchema(name="created_at", dtype=DataType.INT64),
    FieldSchema(name="updated_at", dtype=DataType.INT64)
]

# 创建Schema
schema = CollectionSchema(
    fields=fields,
    description="目标画像向量索引",
    enable_dynamic_field=True
)

# 创建Collection
collection = Collection(
    name="target_profile_vector",
    schema=schema,
    using='default',
    shards_num=4
)

# 创建索引
index_params = {
    "metric_type": "COSINE",
    "index_type": "HNSW",
    "params": {
        "M": 16,
        "efConstruction": 256
    }
}

collection.create_index(
    field_name="embedding",
    index_params=index_params,
    index_name="embedding_index"
)
```

### 4.4 实体向量Collection (entity_vector)

```python
# 定义字段
fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
    FieldSchema(name="vector_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="entity_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="entity_type", dtype=DataType.VARCHAR, max_length=32),
    FieldSchema(name="entity_value", dtype=DataType.VARCHAR, max_length=512),
    FieldSchema(name="context_text", dtype=DataType.VARCHAR, max_length=1024),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=768),
    FieldSchema(name="file_id", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="target_ids", dtype=DataType.VARCHAR, max_length=2048),
    FieldSchema(name="created_at", dtype=DataType.INT64)
]

# 创建Schema
schema = CollectionSchema(
    fields=fields,
    description="实体向量索引",
    enable_dynamic_field=True
)

# 创建Collection
collection = Collection(
    name="entity_vector",
    schema=schema,
    using='default',
    shards_num=6
)

# 创建索引
index_params = {
    "metric_type": "COSINE",
    "index_type": "IVF_FLAT",
    "params": {
        "nlist": 1024
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
(:Port {
    port_number: Integer,      // 端口号
    protocol: String,          // 协议: TCP, UDP
    service: String,           // 服务名称
    description: String        // 描述
})

// 6. 服务节点 (Service)
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
(:URL {
    url: String,               // 完整URL
    domain: String,            // 域名
    path: String,              // 路径
    protocol: String,          // 协议
    is_malicious: Boolean,     // 是否恶意
    first_seen: DateTime,      // 首次发现时间
    last_seen: DateTime        // 最后发现时间
})

// 12. 用户节点 (User)
CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.user_id IS UNIQUE;

(:User {
    user_id: String,           // 用户ID
    username: String,          // 用户名
    email: String,             // 邮箱
    department: String,        // 部门
    status: Integer            // 状态
})
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

// 文件包含实体
(:File)-[:CONTAINS {
    position: Integer,         // 位置
    context: String,           // 上下文
    confidence: Float,         // 置信度
    created_at: DateTime
}]->(:IPAddress|:Domain|:Hash|:URL|:Person|:Organization)

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

// 目标拥有资产
(:Target)-[:OWNS {
    discovered_at: DateTime,   // 发现时间
    source_file: String,       // 来源文件
    status: String             // 状态
}]->(:IPAddress|:Domain|:Service)

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

// IP解析到域名
(:IPAddress)-[:RESOLVES_TO {
    record_type: String,       // 记录类型: A, AAAA, PTR
    first_seen: DateTime,
    last_seen: DateTime
}]->(:Domain)

// 域名解析到IP
(:Domain)-[:RESOLVES_TO {
    record_type: String,       // 记录类型: A, AAAA
    first_seen: DateTime,
    last_seen: DateTime
}]->(:IPAddress)

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
(:Vulnerability)-[:AFFECTS {
    affected_version: String,  // 受影响版本
    created_at: DateTime
}]->(:Service)

// 漏洞影响资产
(:Vulnerability)-[:AFFECTS {
    affected_version: String,
    created_at: DateTime
}]->(:IPAddress|:Domain)


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
(:Hash)-[:RELATED_TO {
    threat_type: String,       // 威胁类型
    confidence: Float,         // 置信度
    source: String,            // 情报来源
    created_at: DateTime
}]->(:Threat)

// URL关联威胁
(:URL)-[:RELATED_TO {
    threat_type: String,
    confidence: Float,
    source: String,
    created_at: DateTime
}]->(:Threat)

// IP关联威胁
(:IPAddress)-[:RELATED_TO {
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
// 1. 查询目标的所有资产
MATCH (t:Target {target_id: $target_id})-[r:OWNS]->(asset)
RETURN asset, r
ORDER BY r.discovered_at DESC;

// 2. 查询目标的所有漏洞
MATCH (t:Target {target_id: $target_id})-[r:HAS_VULNERABILITY]->(v:Vulnerability)
RETURN v.cve_id, v.vuln_name, v.severity, v.cvss_score, r.status
ORDER BY v.cvss_score DESC;

// 3. 查询IP的所有关联域名
MATCH (ip:IPAddress {address: $ip_address})-[r:RESOLVES_TO]-(d:Domain)
RETURN d.name, r.record_type, r.first_seen, r.last_seen
ORDER BY r.last_seen DESC;

// 4. 查询文件中提取的所有实体
MATCH (f:File {file_id: $file_id})-[r:CONTAINS]->(entity)
RETURN entity, r.context, r.confidence
ORDER BY r.position;

// 5. 查询两个目标之间的关联路径
MATCH path = shortestPath(
    (t1:Target {target_id: $target_id1})-[*..10]-(t2:Target {target_id: $target_id2})
)
RETURN path;

// 6. 查询相似文件
MATCH (f1:File {file_id: $file_id})-[r:SIMILAR_TO]->(f2:File)
RETURN f2.file_id, f2.file_name, r.similarity, r.similarity_type
ORDER BY r.similarity DESC
LIMIT 10;

// 7. 查询目标的攻击面
MATCH (t:Target {target_id: $target_id})-[:OWNS]->(asset)-[:HAS_PORT]->(p:Port)-[:RUNS_SERVICE]->(s:Service)
OPTIONAL MATCH (asset)-[:RESOLVES_TO]-(d:Domain)
RETURN asset, collect(DISTINCT p.port_number) as ports, collect(DISTINCT s.service_name) as services, collect(DISTINCT d.name) as domains;

// 8. 查询漏洞影响范围
MATCH (v:Vulnerability {cve_id: $cve_id})-[:AFFECTS]->(s:Service)<-[:RUNS_SERVICE]-(p:Port)<-[:HAS_PORT]-(ip:IPAddress)<-[:OWNS]-(t:Target)
RETURN t.target_name, ip.address, p.port_number, s.service_name;

// 9. 查询人员关联的所有资产
MATCH (p:Person {person_id: $person_id})-[:WORKS_FOR]->(o:Organization)<-[:BELONGS_TO]-(d:Domain)
OPTIONAL MATCH (p)-[:REGISTERED]->(d2:Domain)
RETURN p.name, o.name, collect(DISTINCT d.name) as org_domains, collect(DISTINCT d2.name) as registered_domains;

// 10. 查询威胁情报关联
MATCH (h:Hash {value: $hash_value})-[r:RELATED_TO]->(threat:Threat)
RETURN threat.threat_type, threat.description, r.confidence, r.source;
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
   | 表名              | 分片键        | 分片数量 | 分片方式    | 说明        |
   ─────────────────────────────────────────────────────────────────────────
   | file_metadata     | file_id       | 32      | Hash        | 文件元数据  |
   | file_tags         | file_id       | 32      | Hash        | 文件标签    |
   | file_versions     | file_id       | 16      | Hash        | 文件版本    |
   | parse_results     | file_id       | 32      | Hash        | 解析结果    |
   | entities          | file_id       | 32      | Hash        | 实体信息    |
   | archive_contents  | archive_id    | 16      | Hash        | 压缩包内容  |
   | network_sessions  | file_id       | 32      | Hash        | 网络会话    |
   | analysis_results  | file_id       | 32      | Hash        | 分析结果    |
   | vulnerabilities   | file_id       | 16      | Hash        | 漏洞信息    |
   | sensitive_findings| file_id       | 16      | Hash        | 敏感信息    |
   | targets           | target_id     | 16      | Hash        | 目标        |
   | target_files      | target_id     | 16      | Hash        | 目标文件    |
   | target_assets     | target_id     | 16      | Hash        | 目标资产    |
   | target_persons    | target_id     | 16      | Hash        | 目标人员    |
   | audit_logs        | user_id       | 32      | Hash        | 审计日志    |
   ─────────────────────────────────────────────────────────────────────────


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


3. 分片配置示例

   -- 创建分布式表
   SELECT create_distributed_table('file_metadata', 'file_id', 'hash', 32);
   
   -- 创建参考表(广播表)
   SELECT create_reference_table('users');
   SELECT create_reference_table('roles');
   SELECT create_reference_table('permissions');
   SELECT create_reference_table('directories');
   
   -- 创建Colocation组(同组分片位置相同)
   SELECT create_distributed_table('file_metadata', 'file_id', colocate_with => 'none');
   SELECT create_distributed_table('file_tags', 'file_id', colocate_with => 'file_metadata');
   SELECT create_distributed_table('parse_results', 'file_id', colocate_with => 'file_metadata');


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

### 7.2 Elasticsearch分片策略

```
+-----------------------------------------------------------------------------+
|                      Elasticsearch分片策略                                   |
+-----------------------------------------------------------------------------+

1. 索引分片配置

   ─────────────────────────────────────────────────────────────────────────
   | 索引名称          | 主分片数 | 副本数 | 单分片大小 | 说明            |
   ─────────────────────────────────────────────────────────────────────────
   | file_content      | 6        | 1      | 50GB       | 文件内容索引    |
   | entity_index      | 6        | 1      | 30GB       | 实体索引        |
   | analysis_result   | 4        | 1      | 30GB       | 分析结果索引    |
   | target_profile    | 4        | 1      | 20GB       | 目标画像索引    |
   ─────────────────────────────────────────────────────────────────────────


2. 分片策略原则

   2.1 分片数量
   ─────────────────────────────────────────────────────────────────────────
   - 单个分片大小控制在10-50GB
   - 分片数量 = 数据量 / 单分片目标大小
   - 避免分片过多(影响性能)或过少(无法扩展)

   2.2 副本配置
   ─────────────────────────────────────────────────────────────────────────
   - 生产环境: 副本数 >= 1
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
   | Hot  | NVMe SSD   | 0-30天   | 1      | 原始   | 高频访问数据      |
   | Warm | HDD        | 30-90天  | 1      | 缩减   | 中频访问数据      |
   | Cold | HDD        | 90-365天 | 0      | 1      | 低频访问数据      |
   | Delete | -        | >365天   | -      | -      | 删除              |
   ─────────────────────────────────────────────────────────────────────────
```

### 7.3 Milvus分片策略

```
+-----------------------------------------------------------------------------+
|                        Milvus分片策略                                        |
+-----------------------------------------------------------------------------+

1. Collection分片配置

   ─────────────────────────────────────────────────────────────────────────
   | Collection名称           | 分片数 | 分片键    | 说明              |
   ─────────────────────────────────────────────────────────────────────────
   | file_content_vector      | 6      | file_id   | 文件内容向量      |
   | target_profile_vector    | 4      | target_id | 目标画像向量      |
   | entity_vector            | 6      | entity_id | 实体向量          |
   ─────────────────────────────────────────────────────────────────────────


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

   -- HNSW索引(高性能)
   {
     "metric_type": "COSINE",
     "index_type": "HNSW",
     "params": {
       "M": 16,
       "efConstruction": 256
     }
   }
   
   -- IVF_FLAT索引(高精度)
   {
     "metric_type": "COSINE",
     "index_type": "IVF_FLAT",
     "params": {
       "nlist": 1024
     }
   }
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

   2.1 file_metadata表
   ─────────────────────────────────────────────────────────────────────────
   -- 主键索引
   CREATE UNIQUE INDEX pk_file_metadata ON file_metadata(id);
   
   -- 唯一索引
   CREATE UNIQUE INDEX uk_file_metadata_file_id ON file_metadata(file_id);
   CREATE UNIQUE INDEX uk_file_metadata_sha256 ON file_metadata(sha256_hash);
   
   -- 普通索引
   CREATE INDEX idx_file_metadata_owner_id ON file_metadata(owner_id);
   CREATE INDEX idx_file_metadata_status ON file_metadata(status);
   CREATE INDEX idx_file_metadata_file_type ON file_metadata(file_type);
   CREATE INDEX idx_file_metadata_directory_id ON file_metadata(directory_id);
   
   -- 时间索引(降序)
   CREATE INDEX idx_file_metadata_upload_time ON file_metadata(upload_time DESC);
   CREATE INDEX idx_file_metadata_created_at ON file_metadata(created_at DESC);
   
   -- 复合索引
   CREATE INDEX idx_file_metadata_owner_status ON file_metadata(owner_id, status);
   CREATE INDEX idx_file_metadata_type_status ON file_metadata(file_type, status);
   
   -- JSONB索引
   CREATE INDEX idx_file_metadata