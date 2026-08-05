-- ============================================================
-- V4.1.2 AI 攻击链推理结果表迁移脚本
-- 功能：存储 LLM 推理得到的攻击链路径、置信度及推理过程
-- 作者：红方团队
-- 日期：2026-07-31
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_attack_chain (
    id            BIGSERIAL    PRIMARY KEY,
    file_id       BIGINT       NOT NULL,
    attack_paths  TEXT,
    confidence    VARCHAR(16),
    reasoning     TEXT,
    model         VARCHAR(128),
    tokens_used   INTEGER,
    status        INTEGER      DEFAULT 0,
    error_message VARCHAR(1024),
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  ai_attack_chain IS 'AI 攻击链推理结果表';
COMMENT ON COLUMN ai_attack_chain.id            IS '主键ID';
COMMENT ON COLUMN ai_attack_chain.file_id       IS '文件ID';
COMMENT ON COLUMN ai_attack_chain.attack_paths  IS '推理的攻击路径 JSON 数组';
COMMENT ON COLUMN ai_attack_chain.confidence    IS '置信度 HIGH/MEDIUM/LOW';
COMMENT ON COLUMN ai_attack_chain.reasoning     IS '推理过程';
COMMENT ON COLUMN ai_attack_chain.model         IS '使用的模型名称';
COMMENT ON COLUMN ai_attack_chain.tokens_used   IS '消耗的 token 数';
COMMENT ON COLUMN ai_attack_chain.status        IS '状态：0-生成中 1-成功 2-失败';
COMMENT ON COLUMN ai_attack_chain.error_message IS '错误信息';
COMMENT ON COLUMN ai_attack_chain.created_at    IS '创建时间';
COMMENT ON COLUMN ai_attack_chain.updated_at    IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_ai_attack_chain_file_id ON ai_attack_chain (file_id);
CREATE INDEX IF NOT EXISTS idx_ai_attack_chain_status  ON ai_attack_chain (status);
