-- ============================================================
-- V2.7.0 notification-service 通知服务增强迁移
-- 1. redteam_notifications 表新增字段：sender_id/send_status/retry_count/expired_time/metadata
-- 2. 补充索引以支持按用户、已读、过期、发送状态查询
-- 兼容 PostgreSQL：CREATE INDEX IF NOT EXISTS，不使用内联 INDEX
-- ============================================================

-- 1. 补字段（已存在则忽略）
ALTER TABLE redteam_notifications ADD COLUMN IF NOT EXISTS sender_id    BIGINT;
ALTER TABLE redteam_notifications ADD COLUMN IF NOT EXISTS send_status  VARCHAR(16)  DEFAULT 'PENDING';
ALTER TABLE redteam_notifications ADD COLUMN IF NOT EXISTS retry_count  INTEGER      DEFAULT 0;
ALTER TABLE redteam_notifications ADD COLUMN IF NOT EXISTS expired_time TIMESTAMP;
ALTER TABLE redteam_notifications ADD COLUMN IF NOT EXISTS metadata     VARCHAR(1024);

-- 历史数据兜底：未设置发送状态的标记为 SENT
UPDATE redteam_notifications SET send_status = 'SENT' WHERE send_status IS NULL;
UPDATE redteam_notifications SET retry_count = 0 WHERE retry_count IS NULL;

-- 2. 索引（用于查询过滤与统计）
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_user_id     ON redteam_notifications (user_id);
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_is_read     ON redteam_notifications (is_read);
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_type        ON redteam_notifications (type);
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_level       ON redteam_notifications (level);
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_channel     ON redteam_notifications (channel);
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_send_status ON redteam_notifications (send_status);
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_expired_time ON redteam_notifications (expired_time);
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_create_time ON redteam_notifications (create_time);

-- 复合索引：用户 + 已读（最常用查询场景）
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_user_read ON redteam_notifications (user_id, is_read);
-- 复合索引：用户 + 创建时间倒序（分页查询常用）
CREATE INDEX IF NOT EXISTS idx_redteam_notifications_user_time ON redteam_notifications (user_id, create_time DESC);
