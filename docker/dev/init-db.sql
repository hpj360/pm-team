-- =============================================================================
-- 红方文件汇聚平台 - PostgreSQL 数据库初始化脚本
-- 在 postgres 容器首次启动时自动执行 (docker-entrypoint-initdb.d)
-- =============================================================================

-- 创建各微服务独立数据库
CREATE DATABASE auth_db;
CREATE DATABASE upload_db;
CREATE DATABASE parse_db;
CREATE DATABASE search_db;
CREATE DATABASE analyze_db;
CREATE DATABASE profile_db;
CREATE DATABASE task_db;
CREATE DATABASE notification_db;
CREATE DATABASE report_db;
CREATE DATABASE feishu_db;

-- 公共库 (用户/权限/审计)
CREATE DATABASE common_db;

-- 为每个数据库创建独立 owner (与业务用户解耦)
DO $$
DECLARE
    db_name TEXT;
    db_user TEXT;
BEGIN
    FOREACH db_name IN ARRAY ARRAY[
        'auth_db', 'upload_db', 'parse_db', 'search_db', 'analyze_db',
        'profile_db', 'task_db', 'notification_db', 'report_db', 'feishu_db',
        'common_db'
    ] LOOP
        db_user := replace(db_name, '_db', '_user');
        EXECUTE format('CREATE USER %I WITH PASSWORD %L', db_user, 'redteam123');
        EXECUTE format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', db_name, db_user);
    END LOOP;
END $$;

-- 启用 Citus 扩展 (如已安装)
-- CREATE EXTENSION IF NOT EXISTS citus;

-- 公共 schema 与扩展
\connect common_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\connect auth_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\connect upload_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect task_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 完成提示
SELECT 'Database initialization completed.' AS status;
