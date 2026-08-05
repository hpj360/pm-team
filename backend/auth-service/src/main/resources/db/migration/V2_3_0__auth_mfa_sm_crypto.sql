-- ============================================================
-- V2.3.0 认证服务 MFA + 国密算法迁移脚本
-- 功能：用户表新增 MFA 字段、登录失败记录表、MFA 备用码备份表
-- 作者：红方团队
-- 日期：2026-07-27
-- ============================================================

-- 用户表新增 MFA 字段
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(512);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS password_updated_at TIMESTAMP;

-- 创建登录失败记录表（用于风控）
CREATE TABLE IF NOT EXISTS t_login_attempt (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    ip VARCHAR(45),
    user_agent VARCHAR(256),
    success BOOLEAN,
    fail_reason VARCHAR(64),
    attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_attempt_user_time ON t_login_attempt (user_id, attempt_time);
CREATE INDEX IF NOT EXISTS idx_login_attempt_ip_time ON t_login_attempt (ip, attempt_time);

-- 创建 MFA 备用码表（Redis 替代，此处仅作备份）
CREATE TABLE IF NOT EXISTS t_mfa_backup_code (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mfa_backup_code_user ON t_mfa_backup_code (user_id);
