-- ============================================================
-- V3.2 搜索模板表迁移脚本
-- 功能：存储用户保存的搜索条件模板（关键词/模式/布尔条件/标签等）
-- 作者：红方团队
-- 日期：2026-07-31
-- ============================================================

-- 搜索模板表
CREATE TABLE IF NOT EXISTS search_template (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    params_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户ID索引（用于按用户查询模板列表）
CREATE INDEX IF NOT EXISTS idx_search_template_user_id ON search_template (user_id);

-- 表与字段注释
COMMENT ON TABLE search_template IS '搜索模板表';
COMMENT ON COLUMN search_template.id IS '主键ID';
COMMENT ON COLUMN search_template.user_id IS '用户ID';
COMMENT ON COLUMN search_template.name IS '模板名称';
COMMENT ON COLUMN search_template.params_json IS '搜索条件JSON（关键词/模式/布尔条件/标签等）';
COMMENT ON COLUMN search_template.created_at IS '创建时间';
COMMENT ON COLUMN search_template.updated_at IS '更新时间';
