-- ============================================================
-- V5.1 AI Agent 化模块迁移脚本
-- 功能：
--   1. ai_agent_task 表 - Agent 自主分析任务记录
--   2. ai_knowledge 表 - RAG 知识库文档元信息
-- 作者：红方团队
-- 日期：2026-08-05
-- ============================================================

-- Agent 自主分析任务表
CREATE TABLE IF NOT EXISTS ai_agent_task (
    task_id              VARCHAR(64)   PRIMARY KEY,
    query                TEXT          NOT NULL,
    user_id              BIGINT,
    status               VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    conclusion           TEXT,
    evidence_chain_json  TEXT,
    referenced_files_json TEXT,
    confidence           DOUBLE PRECISION,
    traces_json          TEXT,
    error_message        VARCHAR(2048),
    created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMP
);

COMMENT ON TABLE  ai_agent_task IS 'AI Agent 自主分析任务表';
COMMENT ON COLUMN ai_agent_task.task_id               IS '任务ID（UUID）';
COMMENT ON COLUMN ai_agent_task.query                 IS '用户分析请求（自然语言）';
COMMENT ON COLUMN ai_agent_task.user_id               IS '提交用户ID';
COMMENT ON COLUMN ai_agent_task.status                IS '任务状态 PENDING/RUNNING/COMPLETED/FAILED';
COMMENT ON COLUMN ai_agent_task.conclusion            IS '最终结论';
COMMENT ON COLUMN ai_agent_task.evidence_chain_json   IS '证据链 JSON 数组';
COMMENT ON COLUMN ai_agent_task.referenced_files_json IS '引用文件 JSON 数组';
COMMENT ON COLUMN ai_agent_task.confidence            IS '置信度 0.0~1.0';
COMMENT ON COLUMN ai_agent_task.traces_json           IS '推理轨迹 JSON 数组';
COMMENT ON COLUMN ai_agent_task.error_message         IS '错误信息';
COMMENT ON COLUMN ai_agent_task.created_at            IS '创建时间';
COMMENT ON COLUMN ai_agent_task.completed_at          IS '完成时间';

CREATE INDEX IF NOT EXISTS idx_ai_agent_task_user_id ON ai_agent_task (user_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_task_status  ON ai_agent_task (status);
CREATE INDEX IF NOT EXISTS idx_ai_agent_task_created ON ai_agent_task (created_at);

-- RAG 知识库文档表
CREATE TABLE IF NOT EXISTS ai_knowledge (
    knowledge_id   VARCHAR(64)  PRIMARY KEY,
    title          VARCHAR(512),
    content        TEXT,
    source         VARCHAR(128),
    metadata_json  TEXT,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  ai_knowledge IS 'RAG 知识库文档表（ATT&CK/CVE/APT/历史报告）';
COMMENT ON COLUMN ai_knowledge.knowledge_id IS '知识ID（UUID）';
COMMENT ON COLUMN ai_knowledge.title        IS '文档标题';
COMMENT ON COLUMN ai_knowledge.content      IS '文档内容';
COMMENT ON COLUMN ai_knowledge.source       IS '来源 ATT&CK/CVE/APT/REPORT';
COMMENT ON COLUMN ai_knowledge.metadata_json IS '元数据 JSON';
COMMENT ON COLUMN ai_knowledge.created_at   IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_source   ON ai_knowledge (source);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_created  ON ai_knowledge (created_at);
