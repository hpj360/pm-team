-- ============================================================
-- V2.5.0 分析服务 分析任务/分析结果/沙箱报告 迁移脚本
-- 功能：支撑文件内容分析（关键词/NER/情感/摘要/向量嵌入）与沙箱分析
-- 作者：红方团队
-- 日期：2026-07-27
-- ============================================================

-- 分析任务表（记录异步分析任务请求与状态）
CREATE TABLE IF NOT EXISTS t_analyze_task (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    analyze_type INTEGER DEFAULT 5,
    status INTEGER DEFAULT 0,
    progress INTEGER DEFAULT 0,
    text_content TEXT,
    file_path VARCHAR(512),
    generate_embedding INTEGER DEFAULT 0,
    error_message VARCHAR(1024),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_analyze_task_file_id ON t_analyze_task (file_id);
CREATE INDEX IF NOT EXISTS idx_analyze_task_status ON t_analyze_task (status);
CREATE INDEX IF NOT EXISTS idx_analyze_task_create_time ON t_analyze_task (create_time);

-- 分析结果表（记录每次分析的结果 JSON 与状态）
CREATE TABLE IF NOT EXISTS t_analyze_result (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    analyze_type INTEGER DEFAULT 5,
    status INTEGER DEFAULT 0,
    progress INTEGER DEFAULT 0,
    result_json TEXT,
    error_message VARCHAR(1024),
    duration BIGINT,
    finish_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_analyze_result_task_id ON t_analyze_result (task_id);
CREATE INDEX IF NOT EXISTS idx_analyze_result_file_id ON t_analyze_result (file_id);
CREATE INDEX IF NOT EXISTS idx_analyze_result_status ON t_analyze_result (status);

-- 沙箱报告表（记录 Cuckoo 沙箱分析报告，含降级结果）
CREATE TABLE IF NOT EXISTS t_sandbox_report (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    file_id BIGINT NOT NULL,
    status VARCHAR(32) DEFAULT 'PENDING',
    score DOUBLE PRECISION DEFAULT 0,
    report_json TEXT,
    summary VARCHAR(1024),
    degraded INTEGER DEFAULT 0,
    error_message VARCHAR(1024),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sandbox_report_task_id ON t_sandbox_report (task_id);
CREATE INDEX IF NOT EXISTS idx_sandbox_report_file_id ON t_sandbox_report (file_id);
CREATE INDEX IF NOT EXISTS idx_sandbox_report_status ON t_sandbox_report (status);
