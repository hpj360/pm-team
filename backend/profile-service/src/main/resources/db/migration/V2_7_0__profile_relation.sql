-- ============================================================
-- V2.7.0 profile-service 目标画像增强迁移
-- 1. t_target 表新增字段：industry/attack_surface/tech_assets/org_structure
-- 2. 新建 t_target_relation 表，用于关系图谱
-- 兼容 PostgreSQL：CREATE INDEX IF NOT EXISTS，不使用内联 INDEX
-- ============================================================

-- 1. 增强 t_target 表（若不存在则创建，若存在则补字段）
CREATE TABLE IF NOT EXISTS t_target (
    id              BIGINT       NOT NULL,
    name            VARCHAR(128) NOT NULL,
    type            SMALLINT,
    industry        VARCHAR(64),
    description     VARCHAR(1024),
    attack_surface  TEXT,
    tech_assets     TEXT,
    org_structure   TEXT,
    file_count      INTEGER      DEFAULT 0,
    tags            VARCHAR(256),
    profile_data    TEXT,
    profile_status  SMALLINT     DEFAULT 0,
    risk_level      SMALLINT     DEFAULT 1,
    is_followed     SMALLINT     DEFAULT 0,
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    create_by       BIGINT,
    update_by       BIGINT,
    deleted         SMALLINT     DEFAULT 0,
    CONSTRAINT pk_t_target PRIMARY KEY (id)
);

-- 补字段（已存在则忽略）
ALTER TABLE t_target ADD COLUMN IF NOT EXISTS industry       VARCHAR(64);
ALTER TABLE t_target ADD COLUMN IF NOT EXISTS attack_surface TEXT;
ALTER TABLE t_target ADD COLUMN IF NOT EXISTS tech_assets    TEXT;
ALTER TABLE t_target ADD COLUMN IF NOT EXISTS org_structure  TEXT;

-- 索引
CREATE INDEX IF NOT EXISTS idx_t_target_type       ON t_target (type);
CREATE INDEX IF NOT EXISTS idx_t_target_industry   ON t_target (industry);
CREATE INDEX IF NOT EXISTS idx_t_target_risk_level ON t_target (risk_level);
CREATE INDEX IF NOT EXISTS idx_t_target_name       ON t_target (name);
CREATE INDEX IF NOT EXISTS idx_t_target_is_followed ON t_target (is_followed);

-- 2. 目标关系表
CREATE TABLE IF NOT EXISTS t_target_relation (
    id            BIGINT       NOT NULL,
    source_id     BIGINT       NOT NULL,
    target_id     BIGINT       NOT NULL,
    relation_type VARCHAR(32)  NOT NULL,
    weight        DOUBLE PRECISION DEFAULT 0.5,
    description   VARCHAR(256),
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    create_by     BIGINT,
    update_by     BIGINT,
    deleted       SMALLINT     DEFAULT 0,
    CONSTRAINT pk_t_target_relation PRIMARY KEY (id),
    CONSTRAINT fk_t_target_relation_source FOREIGN KEY (source_id) REFERENCES t_target (id),
    CONSTRAINT fk_t_target_relation_target FOREIGN KEY (target_id) REFERENCES t_target (id)
);

CREATE INDEX IF NOT EXISTS idx_t_target_relation_source ON t_target_relation (source_id);
CREATE INDEX IF NOT EXISTS idx_t_target_relation_target ON t_target_relation (target_id);
CREATE INDEX IF NOT EXISTS idx_t_target_relation_type   ON t_target_relation (relation_type);

-- 防重复关系唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_t_target_relation ON t_target_relation (source_id, target_id, relation_type) WHERE deleted = 0;
