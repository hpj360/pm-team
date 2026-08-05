-- ============================================================
-- V2.3.0 文件上传服务增强：分片上传（断点续传） + 秒传（SM3 去重）
-- 作者：红方团队
-- 说明：
--   1. 扩展 t_file 表：新增 SM3/分片/上传状态等字段
--   2. 新增 t_file_chunk：分片上传记录表（断点续传依据）
--   3. 新增 t_upload_task：上传任务表（断点续传任务持久化）
--   4. 在 t_file 上建立 SM3 唯一部分索引，保证秒传原子性
-- 兼容：PostgreSQL 12+
-- ============================================================

-- 1. 文件表字段扩展
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS file_md5        VARCHAR(64);
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS file_sm3        VARCHAR(128);
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS storage_path    VARCHAR(512);
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS upload_id       VARCHAR(128);
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS chunk_count     INTEGER;
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS chunk_size      BIGINT;
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS upload_status   VARCHAR(32) DEFAULT 'COMPLETED';
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS is_public       INTEGER DEFAULT 0;
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS sensitive_level INTEGER DEFAULT 1;
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS target_id       BIGINT;
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS tags            VARCHAR(512);

-- 2. 创建分片记录表
CREATE TABLE IF NOT EXISTS t_file_chunk (
    id            BIGSERIAL PRIMARY KEY,
    file_id       BIGINT       NOT NULL,
    upload_id     VARCHAR(128) NOT NULL,
    chunk_number  INTEGER      NOT NULL,
    chunk_size    BIGINT,
    etag          VARCHAR(128),
    uploaded      BOOLEAN      DEFAULT FALSE,
    uploaded_at   TIMESTAMP,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (upload_id, chunk_number)
);

CREATE INDEX IF NOT EXISTS idx_file_chunk_file_id ON t_file_chunk(file_id);

-- 3. 创建秒传唯一索引（SM3 哈希唯一，仅对已完成上传生效）
-- 部分索引避免 NULL 与未完成上传干扰
CREATE UNIQUE INDEX IF NOT EXISTS uk_file_sm3
    ON t_file(file_sm3)
    WHERE file_sm3 IS NOT NULL AND upload_status = 'COMPLETED';

-- 4. 创建上传任务表（断点续传任务持久化）
CREATE TABLE IF NOT EXISTS t_upload_task (
    id                BIGSERIAL PRIMARY KEY,
    upload_id         VARCHAR(128) UNIQUE NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    file_size         BIGINT       NOT NULL,
    file_md5          VARCHAR(64),
    file_sm3          VARCHAR(128),
    chunk_count       INTEGER      NOT NULL,
    chunk_size        BIGINT       NOT NULL,
    target_id         BIGINT,
    user_id           BIGINT       NOT NULL,
    status            VARCHAR(32)  DEFAULT 'UPLOADING',
    completed_chunks  INTEGER      DEFAULT 0,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_upload_task_user_status ON t_upload_task(user_id, status);
CREATE INDEX IF NOT EXISTS idx_upload_task_sm3         ON t_upload_task(file_sm3);
