-- ============================================================
-- V2.8.0 report-service 定时报告调度迁移
-- 1. 新增 report_schedule 表：定时报告配置（cron + 收件人 + 模板）
-- 2. 索引：status / cron_expression 以支持调度扫描
-- 兼容 PostgreSQL：CREATE INDEX IF NOT EXISTS
-- ============================================================

-- 定时报告配置表
CREATE TABLE IF NOT EXISTS report_schedule (
    id BIGSERIAL PRIMARY KEY,
    report_name VARCHAR(200) NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    recipients TEXT NOT NULL,
    template_name VARCHAR(100),
    target_id BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    last_run_time TIMESTAMP,
    last_run_status VARCHAR(20),
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 状态索引：调度扫描按 ACTIVE 过滤
CREATE INDEX IF NOT EXISTS idx_report_schedule_status ON report_schedule (status);
-- cron 表达式索引：便于按表达式聚合分析
CREATE INDEX IF NOT EXISTS idx_report_schedule_cron ON report_schedule (cron_expression);
