-- ============================================================
-- V4.1.1 AI 威胁摘要表迁移脚本
-- 功能：存储基于 LLM 生成的文件威胁分析摘要
-- 作者：红方团队
-- 日期：2026-07-31
-- 方言：PostgreSQL（参考 V4_2_2__audit_log.sql 风格）
-- ============================================================

-- AI 威胁摘要表
CREATE TABLE IF NOT EXISTS ai_threat_summary (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    summary TEXT,
    key_findings TEXT,
    suggested_actions TEXT,
    model VARCHAR(100),
    tokens_used INTEGER,
    status SMALLINT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_ai_threat_summary_file_id ON ai_threat_summary (file_id);
CREATE INDEX IF NOT EXISTS idx_ai_threat_summary_status ON ai_threat_summary (status);
CREATE INDEX IF NOT EXISTS idx_ai_threat_summary_created_at ON ai_threat_summary (created_at);

-- 表与字段注释
COMMENT ON TABLE ai_threat_summary IS 'AI 威胁摘要表（LLM 生成的文件威胁分析摘要）';
COMMENT ON COLUMN ai_threat_summary.id IS '主键ID';
COMMENT ON COLUMN ai_threat_summary.file_id IS '文件ID';
COMMENT ON COLUMN ai_threat_summary.summary IS 'LLM 生成的威胁摘要';
COMMENT ON COLUMN ai_threat_summary.key_findings IS '关键发现 JSON 数组';
COMMENT ON COLUMN ai_threat_summary.suggested_actions IS '建议行动 JSON 数组';
COMMENT ON COLUMN ai_threat_summary.model IS '使用的 LLM 模型';
COMMENT ON COLUMN ai_threat_summary.tokens_used IS '消耗 token 数';
COMMENT ON COLUMN ai_threat_summary.status IS '状态：0-生成中 1-成功 2-失败';
COMMENT ON COLUMN ai_threat_summary.error_message IS '失败原因';
COMMENT ON COLUMN ai_threat_summary.created_at IS '创建时间';
COMMENT ON COLUMN ai_threat_summary.updated_at IS '更新时间';
