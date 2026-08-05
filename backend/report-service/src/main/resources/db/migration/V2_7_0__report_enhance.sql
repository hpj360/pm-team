-- ============================================================
-- V2.7.0 report-service 报告服务增强迁移
-- 1. redteam_reports 表新增字段：summary/metadata/version/is_shared/shared_with/failure_reason
-- 2. 补充状态、类型、格式、共享相关索引以支持查询与统计
-- 兼容 PostgreSQL：CREATE INDEX IF NOT EXISTS，不使用内联 INDEX
-- ============================================================

-- 1. 补字段（已存在则忽略）
ALTER TABLE redteam_reports ADD COLUMN IF NOT EXISTS summary        VARCHAR(512);
ALTER TABLE redteam_reports ADD COLUMN IF NOT EXISTS metadata       VARCHAR(1024);
ALTER TABLE redteam_reports ADD COLUMN IF NOT EXISTS version        INTEGER DEFAULT 1;
ALTER TABLE redteam_reports ADD COLUMN IF NOT EXISTS is_shared      INTEGER DEFAULT 0;
ALTER TABLE redteam_reports ADD COLUMN IF NOT EXISTS shared_with    VARCHAR(512);
ALTER TABLE redteam_reports ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(512);

-- 历史数据兜底
UPDATE redteam_reports SET version = 1 WHERE version IS NULL;
UPDATE redteam_reports SET is_shared = 0 WHERE is_shared IS NULL;

-- 2. 索引（用于查询过滤与统计）
CREATE INDEX IF NOT EXISTS idx_redteam_reports_status     ON redteam_reports (status);
CREATE INDEX IF NOT EXISTS idx_redteam_reports_type       ON redteam_reports (report_type);
CREATE INDEX IF NOT EXISTS idx_redteam_reports_format     ON redteam_reports (format);
CREATE INDEX IF NOT EXISTS idx_redteam_reports_task_id    ON redteam_reports (task_id);
CREATE INDEX IF NOT EXISTS idx_redteam_reports_target_id  ON redteam_reports (target_id);
CREATE INDEX IF NOT EXISTS idx_redteam_reports_shared     ON redteam_reports (is_shared);
CREATE INDEX IF NOT EXISTS idx_redteam_reports_create_time ON redteam_reports (create_time);

-- 复合索引：状态 + 类型（常用过滤场景）
CREATE INDEX IF NOT EXISTS idx_redteam_reports_status_type ON redteam_reports (status, report_type);
-- 复合索引：任务 + 创建时间倒序（按任务查询报告历史）
CREATE INDEX IF NOT EXISTS idx_redteam_reports_task_time ON redteam_reports (task_id, create_time DESC);
