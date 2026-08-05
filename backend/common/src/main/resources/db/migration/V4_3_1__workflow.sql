-- ============================================================
-- V4.3.1 审批工作流引擎迁移脚本
-- 1. workflow_definition  工作流定义表（节点/边 JSON 存储）
-- 2. workflow_instance    审批实例表
-- 3. workflow_review      审批意见表
-- 兼容 PostgreSQL：BIGSERIAL + COMMENT ON COLUMN
-- 作者：红方团队
-- 日期：2026-07-31
-- ============================================================

-- 工作流定义表
CREATE TABLE IF NOT EXISTS workflow_definition (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    business_type VARCHAR(50) NOT NULL,
    nodes_json TEXT NOT NULL,
    edges_json TEXT NOT NULL,
    created_by BIGINT,
    created_by_name VARCHAR(100),
    enabled SMALLINT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  workflow_definition IS '工作流定义表';
COMMENT ON COLUMN workflow_definition.id               IS '主键ID';
COMMENT ON COLUMN workflow_definition.name             IS '工作流名称';
COMMENT ON COLUMN workflow_definition.description      IS '描述';
COMMENT ON COLUMN workflow_definition.business_type    IS '业务类型：FILE_REVIEW/TASK_APPROVAL/REPORT_REVIEW';
COMMENT ON COLUMN workflow_definition.nodes_json      IS '节点定义JSON';
COMMENT ON COLUMN workflow_definition.edges_json       IS '边定义JSON';
COMMENT ON COLUMN workflow_definition.created_by       IS '创建人ID';
COMMENT ON COLUMN workflow_definition.created_by_name  IS '创建人姓名';
COMMENT ON COLUMN workflow_definition.enabled          IS '启用：0/1';
COMMENT ON COLUMN workflow_definition.version         IS '版本号';
COMMENT ON COLUMN workflow_definition.created_at      IS '创建时间';
COMMENT ON COLUMN workflow_definition.updated_at      IS '更新时间';

-- 审批实例表
CREATE TABLE IF NOT EXISTS workflow_instance (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    workflow_name VARCHAR(100),
    business_id VARCHAR(100) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    submitter_id BIGINT,
    submitter_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    current_node_id VARCHAR(50),
    current_node_name VARCHAR(100),
    current_approvers TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  workflow_instance IS '审批实例表';
COMMENT ON COLUMN workflow_instance.id                  IS '主键ID';
COMMENT ON COLUMN workflow_instance.workflow_id         IS '工作流定义ID';
COMMENT ON COLUMN workflow_instance.workflow_name       IS '工作流名称';
COMMENT ON COLUMN workflow_instance.business_id         IS '业务ID（如文件ID/任务ID）';
COMMENT ON COLUMN workflow_instance.business_type       IS '业务类型';
COMMENT ON COLUMN workflow_instance.submitter_id        IS '提交人ID';
COMMENT ON COLUMN workflow_instance.submitter_name      IS '提交人姓名';
COMMENT ON COLUMN workflow_instance.status              IS '状态：PENDING/APPROVED/REJECTED/CANCELLED';
COMMENT ON COLUMN workflow_instance.current_node_id    IS '当前节点ID';
COMMENT ON COLUMN workflow_instance.current_node_name  IS '当前节点名称';
COMMENT ON COLUMN workflow_instance.current_approvers  IS '当前审批人ID列表(逗号分隔)';
COMMENT ON COLUMN workflow_instance.created_at         IS '创建时间';
COMMENT ON COLUMN workflow_instance.updated_at         IS '更新时间';

-- 审批意见表
CREATE TABLE IF NOT EXISTS workflow_review (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    node_id VARCHAR(50) NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewer_name VARCHAR(100),
    decision VARCHAR(20) NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  workflow_review IS '审批意见表';
COMMENT ON COLUMN workflow_review.id             IS '主键ID';
COMMENT ON COLUMN workflow_review.instance_id    IS '实例ID';
COMMENT ON COLUMN workflow_review.node_id        IS '节点ID';
COMMENT ON COLUMN workflow_review.reviewer_id    IS '审批人ID';
COMMENT ON COLUMN workflow_review.reviewer_name  IS '审批人姓名';
COMMENT ON COLUMN workflow_review.decision       IS '决定：APPROVE/REJECT';
COMMENT ON COLUMN workflow_review.comment       IS '审批意见';
COMMENT ON COLUMN workflow_review.created_at    IS '创建时间';

-- 索引
CREATE INDEX IF NOT EXISTS idx_workflow_instance_business ON workflow_instance (business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_workflow_instance_status  ON workflow_instance (status);
CREATE INDEX IF NOT EXISTS idx_workflow_review_instance  ON workflow_review (instance_id);
