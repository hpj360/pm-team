-- ============================================================
-- V4.2.3 文件分级访问控制：文件密级 + 用户许可等级
-- 作者：红方团队
-- 说明：
--   1. t_file 表新增 classification 字段（密级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET）
--   2. t_user 表新增 clearance_level 字段（许可等级：1-PUBLIC 2-INTERNAL 3-CONFIDENTIAL 4-SECRET 99-管理员）
--   3. 默认值保证兼容性：现有文件默认 PUBLIC，现有用户许可等级默认 1（仅可访问 PUBLIC 文件）
-- 兼容：PostgreSQL 12+
-- ============================================================

-- 1. 文件表新增密级字段
ALTER TABLE t_file ADD COLUMN IF NOT EXISTS classification VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
COMMENT ON COLUMN t_file.classification IS '密级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET';

-- 2. 用户表新增许可等级
--    1-PUBLIC 2-INTERNAL 3-CONFIDENTIAL 4-SECRET 99-管理员（绕过密级校验）
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS clearance_level SMALLINT NOT NULL DEFAULT 1;
COMMENT ON COLUMN t_user.clearance_level IS '许可等级：1-PUBLIC 2-INTERNAL 3-CONFIDENTIAL 4-SECRET 99-管理员';

-- 3. 为 t_file.classification 创建索引（便于按密级筛选）
CREATE INDEX IF NOT EXISTS idx_file_classification ON t_file(classification);

-- 4. 为 t_user.clearance_level 创建索引（便于按许可等级筛选）
CREATE INDEX IF NOT EXISTS idx_user_clearance_level ON t_user(clearance_level);
