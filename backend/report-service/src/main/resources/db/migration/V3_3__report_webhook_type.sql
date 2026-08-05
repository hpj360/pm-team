-- ============================================================
-- V3.3 report-service 定时报告推送通道增强
-- 1. report_schedule 表新增 webhook_type 字段：推送通道 EMAIL/SLACK/DINGTALK/ALL
-- 兼容 PostgreSQL：ADD COLUMN IF NOT EXISTS
-- ============================================================

-- 推送通道：EMAIL（默认）/ SLACK / DINGTALK / ALL
ALTER TABLE report_schedule ADD COLUMN IF NOT EXISTS webhook_type VARCHAR(20) NOT NULL DEFAULT 'EMAIL';

-- 历史数据兜底：NULL → EMAIL
UPDATE report_schedule SET webhook_type = 'EMAIL' WHERE webhook_type IS NULL;
