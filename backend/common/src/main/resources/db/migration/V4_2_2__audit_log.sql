-- ============================================================
-- V4.2.2 审计日志表迁移脚本
-- 功能：记录用户操作行为（查看/上传/下载/打标/删除/检索/导出/登录）
-- 作者：红方团队
-- 日期：2026-07-31
-- 方言：PostgreSQL（参考 V3_2__search_template.sql 风格）
-- ============================================================

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100),
    resource_name VARCHAR(200),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    detail TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_resource_type ON audit_log (resource_type);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_log (created_at);

-- 表与字段注释
COMMENT ON TABLE audit_log IS '审计日志表（用户操作行为记录）';
COMMENT ON COLUMN audit_log.id IS '主键ID';
COMMENT ON COLUMN audit_log.user_id IS '操作用户ID';
COMMENT ON COLUMN audit_log.username IS '用户名';
COMMENT ON COLUMN audit_log.action IS '操作类型：VIEW/UPLOAD/DOWNLOAD/TAG/DELETE/SEARCH/EXPORT/LOGIN';
COMMENT ON COLUMN audit_log.resource_type IS '资源类型：FILE/TAG/REPORT/TASK/USER/CONFIG';
COMMENT ON COLUMN audit_log.resource_id IS '资源ID';
COMMENT ON COLUMN audit_log.resource_name IS '资源名称';
COMMENT ON COLUMN audit_log.ip_address IS '客户端IP';
COMMENT ON COLUMN audit_log.user_agent IS 'User-Agent';
COMMENT ON COLUMN audit_log.detail IS '操作详情JSON';
COMMENT ON COLUMN audit_log.status IS '操作结果：SUCCESS/FAILED';
COMMENT ON COLUMN audit_log.created_at IS '创建时间';
