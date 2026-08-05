-- ============================================================
-- V2.7.0 task-service 任务服务增强迁移
-- 1. redteam_tasks 表新增字段：file_ids/deadline/progress
-- 2. 补充状态、优先级、负责人相关索引
-- 兼容 PostgreSQL：CREATE INDEX IF NOT EXISTS，不使用内联 INDEX
-- ============================================================

-- 1. 补字段（已存在则忽略）
ALTER TABLE redteam_tasks ADD COLUMN IF NOT EXISTS file_ids  VARCHAR(512);
ALTER TABLE redteam_tasks ADD COLUMN IF NOT EXISTS deadline  TIMESTAMP;
ALTER TABLE redteam_tasks ADD COLUMN IF NOT EXISTS progress  INTEGER DEFAULT 0;

-- 进度默认值兜底（仅对历史数据为 NULL 时填 0，不覆盖已有值）
UPDATE redteam_tasks SET progress = 0 WHERE progress IS NULL;

-- 2. 索引（用于查询过滤与统计）
CREATE INDEX IF NOT EXISTS idx_redteam_tasks_status    ON redteam_tasks (status);
CREATE INDEX IF NOT EXISTS idx_redteam_tasks_type      ON redteam_tasks (task_type);
CREATE INDEX IF NOT EXISTS idx_redteam_tasks_priority  ON redteam_tasks (priority);
CREATE INDEX IF NOT EXISTS idx_redteam_tasks_owner     ON redteam_tasks (owner_id);
CREATE INDEX IF NOT EXISTS idx_redteam_tasks_target    ON redteam_tasks (target_id);
CREATE INDEX IF NOT EXISTS idx_redteam_tasks_deadline  ON redteam_tasks (deadline);

-- 复合索引：状态 + 优先级（常用过滤排序场景）
CREATE INDEX IF NOT EXISTS idx_redteam_tasks_status_priority ON redteam_tasks (status, priority DESC);
