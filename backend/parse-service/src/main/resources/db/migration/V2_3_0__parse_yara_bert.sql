-- ============================================================
-- V2.3.0 文件解析服务 YARA + security-BERT NER 迁移脚本
-- 功能：文件解析结果、YARA 规则、YARA 扫描结果、NER 实体识别结果表
-- 作者：红方团队
-- 日期：2026-07-27
-- ============================================================
-- 说明：
-- 1. 审计列（create_time/update_time/create_by/update_by/deleted）
--    与 common 模块 BaseEntity + MyBatisPlusMetaObjectHandler 对齐，
--    支持自动填充与逻辑删除。
-- 2. 索引采用 PostgreSQL 标准语法 CREATE INDEX IF NOT EXISTS。
-- ============================================================

-- ------------------------------------------------------------
-- 文件解析结果表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_parse_result (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL UNIQUE,
    file_name VARCHAR(255),
    file_type VARCHAR(64),
    file_size BIGINT,
    text_content TEXT,
    text_hash VARCHAR(128),
    language VARCHAR(32),
    encoding VARCHAR(32),
    page_count INTEGER,
    parse_status VARCHAR(32) DEFAULT 'PENDING',
    parse_error TEXT,
    parse_duration_ms BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_parse_result_file_id ON t_parse_result (file_id);
CREATE INDEX IF NOT EXISTS idx_parse_result_status ON t_parse_result (parse_status);

COMMENT ON TABLE t_parse_result IS '文件解析结果表';
COMMENT ON COLUMN t_parse_result.text_hash IS '文本内容 SM3 哈希';
COMMENT ON COLUMN t_parse_result.parse_status IS '解析状态：PENDING/SUCCESS/FAILED';

-- ------------------------------------------------------------
-- YARA 规则表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_yara_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(128) NOT NULL UNIQUE,
    rule_content TEXT NOT NULL,
    rule_hash VARCHAR(128),
    description VARCHAR(512),
    severity VARCHAR(32) DEFAULT 'MEDIUM',
    category VARCHAR(64),
    enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_yara_rule_enabled ON t_yara_rule (enabled);
CREATE INDEX IF NOT EXISTS idx_yara_rule_category ON t_yara_rule (category);

COMMENT ON TABLE t_yara_rule IS 'YARA 规则表';
COMMENT ON COLUMN t_yara_rule.severity IS '严重级别：INFO/LOW/MEDIUM/HIGH/CRITICAL';
COMMENT ON COLUMN t_yara_rule.category IS '规则类别：MALWARE/EXPLOIT/LEAK/CREDENTIAL/OTHER';

-- ------------------------------------------------------------
-- YARA 扫描结果表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_yara_scan_result (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    matched BOOLEAN NOT NULL,
    matched_strings TEXT,
    severity VARCHAR(32),
    category VARCHAR(64),
    scanned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted INTEGER DEFAULT 0,
    CONSTRAINT uk_yara_scan_file_rule UNIQUE (file_id, rule_id)
);

CREATE INDEX IF NOT EXISTS idx_yara_scan_file_id ON t_yara_scan_result (file_id);
CREATE INDEX IF NOT EXISTS idx_yara_scan_matched ON t_yara_scan_result (matched);

COMMENT ON TABLE t_yara_scan_result IS 'YARA 扫描结果表';
COMMENT ON COLUMN t_yara_scan_result.matched_strings IS '匹配字符串 JSON 数组';

-- ------------------------------------------------------------
-- NER 实体识别结果表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_ner_result (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    entity_text VARCHAR(512) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_label VARCHAR(64),
    start_pos INTEGER,
    end_pos INTEGER,
    confidence REAL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ner_result_file_id ON t_ner_result (file_id);
CREATE INDEX IF NOT EXISTS idx_ner_result_entity_type ON t_ner_result (entity_type);

COMMENT ON TABLE t_ner_result IS 'NER 实体识别结果，红方重点：IP/域名/URL/邮箱/哈希/CVE/漏洞/工具/漏洞利用代码';
COMMENT ON COLUMN t_ner_result.entity_type IS '实体类型：IP/DOMAIN/URL/EMAIL/HASH/CVE/TOOL/EXPLOIT';
COMMENT ON COLUMN t_ner_result.confidence IS '置信度 0-1';
