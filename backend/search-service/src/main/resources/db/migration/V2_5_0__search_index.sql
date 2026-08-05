-- ============================================================
-- V2.5.0 检索服务 索引任务 / 检索历史 / 热门检索词 迁移脚本
-- 功能：支撑 ES + Milvus 混合检索的元数据与行为分析
-- 作者：红方团队
-- 日期：2026-07-27
-- ============================================================

-- 检索任务记录表（用于追踪索引状态）
CREATE TABLE IF NOT EXISTS t_search_index_task (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL UNIQUE,
    file_name VARCHAR(255),
    file_sm3 VARCHAR(128),
    es_indexed BOOLEAN DEFAULT FALSE,
    milvus_indexed BOOLEAN DEFAULT FALSE,
    index_status VARCHAR(32) DEFAULT 'PENDING',
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_search_index_task_status ON t_search_index_task (index_status);
CREATE INDEX IF NOT EXISTS idx_search_index_task_file_id ON t_search_index_task (file_id);

-- 检索历史记录表（用户行为分析）
CREATE TABLE IF NOT EXISTS t_search_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    search_type VARCHAR(32) DEFAULT 'KEYWORD',
    query_text TEXT,
    filters VARCHAR(1024),
    result_count INTEGER,
    response_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_search_history_user_time ON t_search_history (user_id, created_at);

-- 热门检索词表
CREATE TABLE IF NOT EXISTS t_search_hot_words (
    id BIGSERIAL PRIMARY KEY,
    word VARCHAR(128) NOT NULL,
    search_count INTEGER DEFAULT 1,
    last_searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_search_hot_words_word UNIQUE (word)
);

CREATE INDEX IF NOT EXISTS idx_search_hot_words_count ON t_search_hot_words (search_count DESC);
