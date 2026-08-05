-- ============================================================
-- V4.2.1 数据脱敏规则表迁移脚本
-- 功能：存储数据分级脱敏规则（PHONE/IDCARD/IP/EMAIL/CUSTOM）
-- 作者：红方团队
-- 日期：2026-07-31
-- ============================================================

-- 数据脱敏规则表
CREATE TABLE IF NOT EXISTS data_masking_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    pattern VARCHAR(500) NOT NULL,
    replacement VARCHAR(200) NOT NULL,
    classification_level VARCHAR(20) NOT NULL DEFAULT 'CONFIDENTIAL',
    enabled SMALLINT NOT NULL DEFAULT 1,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 表与字段注释
COMMENT ON TABLE data_masking_rule IS '数据脱敏规则表';
COMMENT ON COLUMN data_masking_rule.id IS '主键ID';
COMMENT ON COLUMN data_masking_rule.rule_name IS '规则名称';
COMMENT ON COLUMN data_masking_rule.rule_type IS '类型：PHONE/IDCARD/IP/EMAIL/CUSTOM';
COMMENT ON COLUMN data_masking_rule.pattern IS '正则表达式';
COMMENT ON COLUMN data_masking_rule.replacement IS '替换模板，如 $1****$2';
COMMENT ON COLUMN data_masking_rule.classification_level IS '适用密级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET';
COMMENT ON COLUMN data_masking_rule.enabled IS '启用：0/1';
COMMENT ON COLUMN data_masking_rule.description IS '规则描述';
COMMENT ON COLUMN data_masking_rule.created_at IS '创建时间';
COMMENT ON COLUMN data_masking_rule.updated_at IS '更新时间';

-- 种子数据
INSERT INTO data_masking_rule (rule_name, rule_type, pattern, replacement, classification_level, description) VALUES
('手机号脱敏', 'PHONE', '(\d{3})\d{4}(\d{4})', '$1****$2', 'CONFIDENTIAL', '手机号中间4位替换为****'),
('身份证脱敏', 'IDCARD', '(\d{4})\d{10}(\d{4})', '$1**********$2', 'CONFIDENTIAL', '身份证中间10位替换为*'),
('IP地址脱敏', 'IP', '(\d{1,3})\.(\d{1,3})\.\d{1,3}\.(\d{1,3})', '$1.$2.x.$3', 'SECRET', 'IP第三段替换为x'),
('邮箱脱敏', 'EMAIL', '(\w{1,2})\w*@(\w+)', '$1***@$2', 'CONFIDENTIAL', '邮箱用户名部分脱敏')
ON CONFLICT DO NOTHING;
