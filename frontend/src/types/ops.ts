/**
 * 应用运维模块类型定义
 * 对应 docs/app-ops-detailed-design.md D1-D7
 * 对齐现有 ApiResponse / PageResult / PageParams 规范
 */
import type { PageParams, PageResult } from './common';

/** 运维通用分页查询参数（对齐 PageParams + 上游 §9.5） */
export interface OpsQueryParams extends Partial<PageParams> {
  sort?: string;
  fields?: string;
  q?: string;
  team_space_id?: number;
}

// ===================== D1 空间台账 =====================

export type SpaceLifecycleStatus =
  | 'active'
  | 'frozen'
  | 'archived'
  | 'destroyed'
  | 'partial_destroyed';

export const SpaceLifecycleLabel: Record<SpaceLifecycleStatus, string> = {
  active: '活跃',
  frozen: '冻结',
  archived: '已归档',
  destroyed: '已销毁',
  partial_destroyed: '销毁中',
};

export const SpaceLifecycleTag: Record<SpaceLifecycleStatus, { color: string; text: string }> = {
  active: { color: 'success', text: '活跃' },
  frozen: { color: 'warning', text: '冻结' },
  archived: { color: 'cyan', text: '已归档' },
  destroyed: { color: 'default', text: '已销毁' },
  partial_destroyed: { color: 'error', text: '销毁中' },
};

export type SpaceMemberRole = 'OWNER' | 'MAINTAINER' | 'VIEWER';

export const SpaceMemberRoleLabel: Record<SpaceMemberRole, string> = {
  OWNER: '负责人',
  MAINTAINER: '维护者',
  VIEWER: '查看者',
};

export interface TeamSpace {
  id: number;
  code: string;
  name: string;
  owner_id: number;
  owner_name: string;
  member_count: number;
  file_count: number;
  storage_used: number; // bytes
  storage_quota: number; // bytes
  cold_file_count: number;
  archived_bytes: number;
  health_score: number; // 0-100
  lifecycle_status: SpaceLifecycleStatus;
  version: number; // 乐观锁
  created_at: string;
}

export interface SpaceHealthDimension {
  name: string;
  score: number;
  full: number;
  reason: string;
}

export interface SpaceHealthDetail {
  score: number;
  dimension: SpaceHealthDimension[];
  suggestions: string[];
}

export interface SpaceMember {
  id: number;
  team_space_id: number;
  user_id: number;
  username: string;
  nickname: string;
  role: SpaceMemberRole;
  joined_at: string;
  last_active_at: string;
}

export interface QuotaLog {
  id: number;
  team_space_id: number;
  old_storage_quota: number;
  new_storage_quota: number;
  old_file_quota: number;
  new_file_quota: number;
  operator_name: string;
  ticket_id: number;
  reason: string;
  created_at: string;
}

/** 空间操作事件（来自 t_file_event） */
export interface SpaceEvent {
  id: number;
  file_id: number;
  event_type: string;
  team_space_id: number;
  operator_name: string;
  meta: Record<string, unknown>;
  created_at: string;
}

// ===================== D2 一致性对账 =====================

export type CheckType =
  | 'PG_MINIO'
  | 'PG_ES'
  | 'PG_NEO4J'
  | 'PG_MILVUS'
  | 'TRACE_BROKEN'
  | 'ORPHAN_OBJECT'
  | 'INDEX_LAG'
  | 'PARSE_LAG';

export const CheckTypeLabel: Record<CheckType, string> = {
  PG_MINIO: 'PG↔MinIO',
  PG_ES: 'PG↔ES',
  PG_NEO4J: 'PG↔Neo4j',
  PG_MILVUS: 'PG↔Milvus',
  TRACE_BROKEN: 'trace 断链',
  ORPHAN_OBJECT: '孤儿对象',
  INDEX_LAG: '索引积压',
  PARSE_LAG: '解析积压',
};

export const ALL_CHECK_TYPES: CheckType[] = [
  'PG_MINIO',
  'PG_ES',
  'PG_NEO4J',
  'PG_MILVUS',
  'TRACE_BROKEN',
  'ORPHAN_OBJECT',
  'INDEX_LAG',
  'PARSE_LAG',
];

/** 0运行中 1正常 2异常 3失败 */
export type CheckStatus = 0 | 1 | 2 | 3;

export const CheckStatusTag: Record<CheckStatus, { color: string; text: string }> = {
  0: { color: 'processing', text: '运行中' },
  1: { color: 'success', text: '正常' },
  2: { color: 'error', text: '异常' },
  3: { color: 'default', text: '失败' },
};

export interface ConsistencyCheck {
  id: number;
  check_type: CheckType;
  team_space_id: number;
  team_space_name: string;
  started_at: string;
  finished_at: string;
  status: CheckStatus;
  total_checked: number;
  diff_count: number;
}

export type DiffSuggestedAction = 'REINDEX' | 'REPARSE' | 'PURGE_ORPHAN' | 'MANUAL';

export const DiffActionLabel: Record<DiffSuggestedAction, string> = {
  REINDEX: '重索引',
  REPARSE: '重解析',
  PURGE_ORPHAN: '清理孤儿',
  MANUAL: '人工处理',
};

/** 0待处理 1已修复 2已忽略 */
export type DiffStatus = 0 | 1 | 2;

export const DiffStatusTag: Record<DiffStatus, { color: string; text: string }> = {
  0: { color: 'error', text: '待处理' },
  1: { color: 'success', text: '已修复' },
  2: { color: 'default', text: '已忽略' },
};

export interface ConsistencyDiff {
  id: number;
  check_id: number;
  team_space_id: number;
  team_space_name: string;
  file_id: number;
  object_key: string;
  diff_type: string;
  detail: Record<string, unknown>;
  suggested_action: DiffSuggestedAction;
  status: DiffStatus;
  found_at: string;
}

// ===================== D3 链路治愈 =====================

export type HealJobType =
  | 'RETRY_INDEX'
  | 'RETRY_PARSE'
  | 'REBUILD_GRAPH'
  | 'REBUILD_VECTOR'
  | 'PURGE_ORPHAN'
  | 'DELETE_FILE'
  | 'FIX_TRACE';

export const HealJobTypeLabel: Record<HealJobType, string> = {
  RETRY_INDEX: '单文件重索引',
  RETRY_PARSE: '单文件重解析',
  REBUILD_GRAPH: '重建图关系',
  REBUILD_VECTOR: '重建向量索引',
  PURGE_ORPHAN: '清理孤儿对象',
  DELETE_FILE: '强制删除文件',
  FIX_TRACE: '修复 trace 断链',
};

/** 批量治愈可选类型（不含单文件免审批类） */
export const BATCH_HEAL_TYPES: HealJobType[] = [
  'RETRY_PARSE',
  'REBUILD_GRAPH',
  'REBUILD_VECTOR',
  'PURGE_ORPHAN',
  'DELETE_FILE',
];

/** 0排队 1运行中 2完成 3部分失败 4失败 5已取消 */
export type HealJobStatus = 0 | 1 | 2 | 3 | 4 | 5;

export const HealJobStatusTag: Record<HealJobStatus, { color: string; text: string }> = {
  0: { color: 'default', text: '排队' },
  1: { color: 'processing', text: '运行中' },
  2: { color: 'success', text: '完成' },
  3: { color: 'warning', text: '部分失败' },
  4: { color: 'error', text: '失败' },
  5: { color: 'default', text: '已取消' },
};

export interface HealJob {
  id: number;
  job_type: HealJobType;
  team_space_id: number;
  team_space_name: string;
  ticket_id: number;
  operator_name: string;
  target_count: number;
  success_count: number;
  failed_count: number;
  skipped_count: number;
  status: HealJobStatus;
  progress: number;
  started_at: string;
  finished_at: string;
  error_summary: Record<string, number>;
}

export interface HealPreview {
  target_count: number;
  est_minutes: number;
  risk: 'low' | 'mid' | 'high';
}

export interface HealTargetFile {
  id: number;
  file_id: number;
  file_name: string;
  error_code: string;
  team_space_id: number;
  team_space_name: string;
  status: string;
  created_at: string;
}

// ===================== D4 生命周期 =====================

export type StorageTier = 'hot' | 'cold' | 'archived';

export const StorageTierLabel: Record<StorageTier, string> = {
  hot: '热存储',
  cold: '冷存储',
  archived: '归档',
};

export interface LifecyclePolicy {
  id: number;
  team_space_id: number | null;
  team_space_name: string;
  policy_name: string;
  cold_after_days: number;
  expire_after_days: number;
  archive_storage_class: string;
  enabled: number;
  created_at: string;
}

export interface ColdCandidate {
  id: number;
  file_id: number;
  file_name: string;
  team_space_id: number;
  team_space_name: string;
  size: number;
  last_access_at: string;
}

export interface CapacityData {
  trend: Array<{ date: string; hot: number; cold: number; archived: number }>;
  tierRatio: Array<{ name: string; value: number }>;
  predict: Array<{ space: string; days: number }>;
  topSpaces: Array<{ id: number; name: string; used: number; quota: number }>;
}

// ===================== D5 应用配置 =====================

export type ConfigType =
  | 'PARSER'
  | 'MODEL'
  | 'YARA'
  | 'TAG'
  | 'UPLOAD'
  | 'INDEX'
  | 'RETRY';

export const ConfigTypeLabel: Record<ConfigType, string> = {
  PARSER: '解析器',
  MODEL: '模型路由',
  YARA: 'YARA 规则',
  TAG: '标签规则',
  UPLOAD: '上传策略',
  INDEX: '索引策略',
  RETRY: '重试策略',
};

export const ALL_CONFIG_TYPES: ConfigType[] = [
  'PARSER',
  'MODEL',
  'YARA',
  'TAG',
  'UPLOAD',
  'INDEX',
  'RETRY',
];

/** 0草稿 1审批中 2灰度中 3已生效 4已回滚 5已废弃 */
export type ConfigChangeStatus = 0 | 1 | 2 | 3 | 4 | 5;

export const ConfigChangeStatusTag: Record<ConfigChangeStatus, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  1: { color: 'processing', text: '审批中' },
  2: { color: 'processing', text: '灰度中' },
  3: { color: 'success', text: '已生效' },
  4: { color: 'error', text: '已回滚' },
  5: { color: 'default', text: '已废弃' },
};

export interface ConfigItem {
  id: number;
  config_type: ConfigType;
  config_key: string;
  value: string;
  version: number;
  scope_type: 'GLOBAL' | 'TEAM_SPACE';
  scope_space_ids: number[];
  change_id: number;
  effective_at: string;
}

export interface ConfigChange {
  id: number;
  config_type: ConfigType;
  config_key: string;
  version: number;
  old_value: string;
  new_value: string;
  operator_name: string;
  reason: string;
  status: ConfigChangeStatus;
  effective_at: string;
  ticket_id: number;
}

export interface ConfigImpact {
  affected_files: number;
  affected_spaces: number;
}

// ===================== D6 数据安全 =====================

export type StaleType = 'RESIGNED_MEMBER' | 'EXPIRED_LINK' | 'OVER_PRIVILEGE';

export const StaleTypeLabel: Record<StaleType, string> = {
  RESIGNED_MEMBER: '离职成员',
  EXPIRED_LINK: '过期链接',
  OVER_PRIVILEGE: '越权成员',
};

export interface StalePermission {
  id: number;
  user_id: number;
  username: string;
  nickname: string;
  team_space_id: number;
  team_space_name: string;
  role: string;
  stale_type: StaleType;
  found_at: string;
}

export interface DownloadAnomaly {
  id: number;
  user_id: number;
  username: string;
  team_space_id: number;
  team_space_name: string;
  count: number;
  time: string;
  rule: string;
  risk_score: number;
}

export interface SensitiveAccess {
  id: number;
  file_id: number;
  file_name: string;
  sensitivity_level: number;
  user_id: number;
  username: string;
  access_count: number;
  last_access_at: string;
  team_space_id: number;
  team_space_name: string;
}

/** 0待审批 1已通过 2已拒绝 3生成中 4已完成 5失败 */
export type ExportStatus = 0 | 1 | 2 | 3 | 4 | 5;

export const ExportStatusTag: Record<ExportStatus, { color: string; text: string }> = {
  0: { color: 'processing', text: '待审批' },
  1: { color: 'success', text: '已通过' },
  2: { color: 'error', text: '已拒绝' },
  3: { color: 'processing', text: '生成中' },
  4: { color: 'success', text: '已完成' },
  5: { color: 'error', text: '失败' },
};

export interface ExportRequest {
  id: number;
  requester_id: number;
  requester_name: string;
  team_space_id: number;
  team_space_name: string;
  export_scope: Record<string, unknown>;
  data_size: number;
  sensitive_level_max: number;
  status: ExportStatus;
  ticket_id: number;
  package_url: string;
  watermark: string;
  expires_at: string;
  created_at: string;
}

// ===================== D7 空间报告 =====================

export type ReportType = 'WEEKLY' | 'MONTHLY' | 'DAILY' | 'ALERT';

export const ReportTypeLabel: Record<ReportType, string> = {
  WEEKLY: '周报',
  MONTHLY: '月报',
  DAILY: '日报',
  ALERT: '异常通报',
};

export const ALL_REPORT_TYPES: ReportType[] = ['WEEKLY', 'MONTHLY', 'DAILY', 'ALERT'];

export interface ReportSuggestion {
  id: number;
  type: string;
  desc: string;
  ticket_type: string;
}

export interface SpaceReport {
  id: number;
  team_space_id: number;
  team_space_name: string;
  report_type: ReportType;
  period_start: string;
  period_end: string;
  health_score: number;
  summary: {
    file_growth: number;
    storage_growth_gb: number;
    parse_success_rate: number;
    quota_usage: number;
    top_failures: Array<{ error_code: string; count: number }>;
  };
  suggestions: ReportSuggestion[];
  pdf_url: string;
  created_at: string;
}

export interface ReportSubscription {
  id: number;
  user_id: number;
  team_space_id: number | null;
  report_types: string;
  channel: string;
  enabled: number;
  created_at: string;
}

// ===================== 运维工单（D3/D5/D6 联动） =====================

export type TicketType =
  | 'QUOTA'
  | 'DESTROY'
  | 'DELETE'
  | 'REPARSE'
  | 'REINDEX'
  | 'REBUILD_GRAPH'
  | 'EXPORT'
  | 'CONFIG'
  | 'ARCHIVE';

export const TicketTypeLabel: Record<TicketType, string> = {
  QUOTA: '配额扩容',
  DESTROY: '空间销毁',
  DELETE: '批量删除',
  REPARSE: '批量重解析',
  REINDEX: '批量重索引',
  REBUILD_GRAPH: '重建图关系',
  EXPORT: '数据导出',
  CONFIG: '配置变更',
  ARCHIVE: '归档',
};

/** 0草稿 1待审批 2通过 3执行中 4验证中 5完成 6拒绝 7失败 */
export type TicketStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7;

export const TicketStatusTag: Record<TicketStatus, { color: string; text: string }> = {
  0: { color: 'default', text: '草稿' },
  1: { color: 'processing', text: '待审批' },
  2: { color: 'success', text: '已通过' },
  3: { color: 'processing', text: '执行中' },
  4: { color: 'processing', text: '验证中' },
  5: { color: 'success', text: '已完成' },
  6: { color: 'error', text: '已拒绝' },
  7: { color: 'error', text: '失败' },
};

export interface OpsTicket {
  id: number;
  ticket_no: string;
  ticket_type: TicketType;
  title: string;
  description: string;
  team_space_id: number;
  team_space_name: string;
  target_ref: string;
  params: Record<string, unknown>;
  impact_preview: Record<string, unknown>;
  status: TicketStatus;
  created_by: number;
  created_by_name: string;
  assignee_name: string;
  created_at: string;
  approved_at: string;
  executed_at: string;
  finished_at: string;
}

// 复用通用分页响应
export type OpsPageResult<T> = PageResult<T>;
