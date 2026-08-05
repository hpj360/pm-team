/**
 * 应用运维模块 Mock 数据
 * 对应 docs/app-ops-detailed-design.md D1-D7
 * 用于请求失败时的本地降级
 */
import type {
  TeamSpace,
  SpaceHealthDetail,
  SpaceMember,
  QuotaLog,
  SpaceEvent,
  ConsistencyCheck,
  ConsistencyDiff,
  HealJob,
  HealPreview,
  HealTargetFile,
  LifecyclePolicy,
  ColdCandidate,
  CapacityData,
  ConfigItem,
  ConfigChange,
  ConfigImpact,
  StalePermission,
  DownloadAnomaly,
  SensitiveAccess,
  ExportRequest,
  SpaceReport,
  ReportSubscription,
  OpsTicket,
  OpsQueryParams,
  OpsPageResult,
  HealJobType,
  CheckType,
  TicketType,
  TicketStatus,
  ExportStatus,
  DiffStatus,
  DiffSuggestedAction,
  HealJobStatus,
  ConfigChangeStatus,
} from '@/types/ops';

// ===================== D1 空间台账 =====================

export const mockSpaces: TeamSpace[] = [
  {
    id: 1, code: 'SPACE-A', name: '红方作战A组', owner_id: 101, owner_name: '张三',
    member_count: 12, file_count: 45230, storage_used: 234 * 1024 ** 3,
    storage_quota: 500 * 1024 ** 3, cold_file_count: 12300, archived_bytes: 45 * 1024 ** 3,
    health_score: 88, lifecycle_status: 'active', version: 6, created_at: '2024-08-12T08:00:00Z',
  },
  {
    id: 2, code: 'SPACE-B', name: '红方作战B组', owner_id: 102, owner_name: '李四',
    member_count: 8, file_count: 28100, storage_used: 156 * 1024 ** 3,
    storage_quota: 300 * 1024 ** 3, cold_file_count: 8200, archived_bytes: 22 * 1024 ** 3,
    health_score: 76, lifecycle_status: 'active', version: 4, created_at: '2024-09-01T08:00:00Z',
  },
  {
    id: 3, code: 'SPACE-C', name: '威胁情报库', owner_id: 103, owner_name: '王五',
    member_count: 15, file_count: 132800, storage_used: 412 * 1024 ** 3,
    storage_quota: 500 * 1024 ** 3, cold_file_count: 56000, archived_bytes: 180 * 1024 ** 3,
    health_score: 92, lifecycle_status: 'active', version: 12, created_at: '2024-05-20T08:00:00Z',
  },
  {
    id: 4, code: 'SPACE-D', name: '历史归档空间', owner_id: 104, owner_name: '赵六',
    member_count: 3, file_count: 89000, storage_used: 320 * 1024 ** 3,
    storage_quota: 500 * 1024 ** 3, cold_file_count: 78000, archived_bytes: 280 * 1024 ** 3,
    health_score: 65, lifecycle_status: 'frozen', version: 3, created_at: '2023-12-01T08:00:00Z',
  },
  {
    id: 5, code: 'SPACE-E', name: '应急响应空间', owner_id: 105, owner_name: '钱七',
    member_count: 6, file_count: 5600, storage_used: 28 * 1024 ** 3,
    storage_quota: 100 * 1024 ** 3, cold_file_count: 1200, archived_bytes: 8 * 1024 ** 3,
    health_score: 95, lifecycle_status: 'active', version: 2, created_at: '2025-03-15T08:00:00Z',
  },
];

export const mockSpaceHealth: SpaceHealthDetail = {
  score: 88,
  dimension: [
    { name: '存储一致性', score: 95, full: 100, reason: 'PG↔MinIO 校验通过' },
    { name: '索引完整性', score: 82, full: 100, reason: '存在 12 条索引积压' },
    { name: '解析成功率', score: 91, full: 100, reason: '过去 7 天解析成功率 99.1%' },
    { name: '配额健康', score: 78, full: 100, reason: '配额使用 78%，建议扩容' },
    { name: '链路完整', score: 90, full: 100, reason: 'trace 断链 3 条' },
    { name: '权限合规', score: 88, full: 100, reason: '存在 2 个越权成员' },
  ],
  suggestions: [
    '建议清理 12 条索引积压，避免检索延迟',
    '建议对 3 条断链 trace 进行修复',
    '建议对 2 个越权成员进行权限回收',
  ],
};

export const mockSpaceMembers: SpaceMember[] = [
  { id: 1, team_space_id: 1, user_id: 101, username: 'zhangsan', nickname: '张三', role: 'OWNER', joined_at: '2024-08-12T08:00:00Z', last_active_at: '2026-07-29T10:00:00Z' },
  { id: 2, team_space_id: 1, user_id: 201, username: 'lisi', nickname: '李四', role: 'MAINTAINER', joined_at: '2024-08-13T08:00:00Z', last_active_at: '2026-07-28T15:00:00Z' },
  { id: 3, team_space_id: 1, user_id: 301, username: 'wangwu', nickname: '王五', role: 'MAINTAINER', joined_at: '2024-09-01T08:00:00Z', last_active_at: '2026-07-25T09:00:00Z' },
  { id: 4, team_space_id: 1, user_id: 401, username: 'zhaoliu', nickname: '赵六', role: 'VIEWER', joined_at: '2024-10-15T08:00:00Z', last_active_at: '2026-07-20T14:00:00Z' },
];

export const mockQuotaLogs: QuotaLog[] = [
  { id: 1, team_space_id: 1, old_storage_quota: 300 * 1024 ** 3, new_storage_quota: 500 * 1024 ** 3, old_file_quota: 50000, new_file_quota: 80000, operator_name: '系统管理员', ticket_id: 1001, reason: '业务量增长，配额扩容', created_at: '2026-06-15T10:00:00Z' },
  { id: 2, team_space_id: 1, old_storage_quota: 200 * 1024 ** 3, new_storage_quota: 300 * 1024 ** 3, old_file_quota: 30000, new_file_quota: 50000, operator_name: '系统管理员', ticket_id: 902, reason: '季度扩容', created_at: '2026-03-15T10:00:00Z' },
];

export const mockSpaceEvents: SpaceEvent[] = [
  { id: 1, file_id: 9001, event_type: 'UPLOAD', team_space_id: 1, operator_name: '张三', meta: { size: 1024000 }, created_at: '2026-07-29T08:00:00Z' },
  { id: 2, file_id: 9002, event_type: 'PARSE_COMPLETE', team_space_id: 1, operator_name: 'system', meta: { duration: 2300 }, created_at: '2026-07-29T08:05:00Z' },
  { id: 3, file_id: 9003, event_type: 'INDEX_FAIL', team_space_id: 1, operator_name: 'system', meta: { error: 'es_timeout' }, created_at: '2026-07-29T08:10:00Z' },
  { id: 4, file_id: 9004, event_type: 'DELETE', team_space_id: 1, operator_name: '李四', meta: {}, created_at: '2026-07-28T16:00:00Z' },
];

// ===================== D2 一致性对账 =====================

export const mockConsistencyChecks: ConsistencyCheck[] = [
  { id: 1, check_type: 'PG_MINIO', team_space_id: 1, team_space_name: '红方作战A组', started_at: '2026-07-29T08:00:00Z', finished_at: '2026-07-29T08:30:00Z', status: 2, total_checked: 45230, diff_count: 3 },
  { id: 2, check_type: 'PG_ES', team_space_id: 1, team_space_name: '红方作战A组', started_at: '2026-07-29T09:00:00Z', finished_at: '2026-07-29T09:15:00Z', status: 1, total_checked: 45230, diff_count: 0 },
  { id: 3, check_type: 'PG_NEO4J', team_space_id: 3, team_space_name: '威胁情报库', started_at: '2026-07-29T08:00:00Z', finished_at: '2026-07-29T08:45:00Z', status: 2, total_checked: 132800, diff_count: 5 },
  { id: 4, check_type: 'TRACE_BROKEN', team_space_id: 1, team_space_name: '红方作战A组', started_at: '2026-07-29T10:00:00Z', finished_at: '2026-07-29T10:05:00Z', status: 2, total_checked: 45230, diff_count: 3 },
  { id: 5, check_type: 'INDEX_LAG', team_space_id: 2, team_space_name: '红方作战B组', started_at: '2026-07-29T11:00:00Z', finished_at: '2026-07-29T11:02:00Z', status: 2, total_checked: 28100, diff_count: 12 },
  { id: 6, check_type: 'PG_MILVUS', team_space_id: 3, team_space_name: '威胁情报库', started_at: '2026-07-29T08:50:00Z', finished_at: '2026-07-29T09:00:00Z', status: 1, total_checked: 132800, diff_count: 0 },
  { id: 7, check_type: 'ORPHAN_OBJECT', team_space_id: 4, team_space_name: '历史归档空间', started_at: '2026-07-29T12:00:00Z', finished_at: '2026-07-29T12:30:00Z', status: 2, total_checked: 89000, diff_count: 8 },
  { id: 8, check_type: 'PARSE_LAG', team_space_id: 1, team_space_name: '红方作战A组', started_at: '2026-07-29T13:00:00Z', finished_at: '', status: 0, total_checked: 0, diff_count: 0 },
];

export const mockConsistencyDiffs: ConsistencyDiff[] = [
  { id: 1, check_id: 1, team_space_id: 1, team_space_name: '红方作战A组', file_id: 8001, object_key: 'space-a/files/8001.bin', diff_type: 'PG_EXISTS_MINIO_MISSING', detail: { reason: 'MinIO 对象缺失' }, suggested_action: 'PURGE_ORPHAN', status: 0, found_at: '2026-07-29T08:30:00Z' },
  { id: 2, check_id: 1, team_space_id: 1, team_space_name: '红方作战A组', file_id: 8002, object_key: 'space-a/files/8002.bin', diff_type: 'PG_EXISTS_MINIO_MISSING', detail: { reason: 'MinIO 对象缺失' }, suggested_action: 'PURGE_ORPHAN', status: 0, found_at: '2026-07-29T08:31:00Z' },
  { id: 3, check_id: 1, team_space_id: 1, team_space_name: '红方作战A组', file_id: 8003, object_key: 'space-a/files/8003.bin', diff_type: 'MINIO_EXISTS_PG_MISSING', detail: { reason: 'PG 记录缺失' }, suggested_action: 'MANUAL', status: 0, found_at: '2026-07-29T08:32:00Z' },
  { id: 4, check_id: 3, team_space_id: 3, team_space_name: '威胁情报库', file_id: 7001, object_key: 'space-c/files/7001.json', diff_type: 'NEO4J_NODE_MISSING', detail: { reason: 'Neo4j 节点缺失' }, suggested_action: 'REINDEX', status: 0, found_at: '2026-07-29T08:45:00Z' },
  { id: 5, check_id: 5, team_space_id: 2, team_space_name: '红方作战B组', file_id: 6001, object_key: 'space-b/files/6001.bin', diff_type: 'INDEX_LAG', detail: { lag_seconds: 320 }, suggested_action: 'REINDEX', status: 0, found_at: '2026-07-29T11:02:00Z' },
];

// ===================== D3 链路治愈 =====================

export const mockHealJobs: HealJob[] = [
  { id: 1, job_type: 'RETRY_INDEX', team_space_id: 2, team_space_name: '红方作战B组', ticket_id: 0, operator_name: '李四', target_count: 12, success_count: 12, failed_count: 0, skipped_count: 0, status: 2, progress: 100, started_at: '2026-07-29T11:05:00Z', finished_at: '2026-07-29T11:10:00Z', error_summary: {} },
  { id: 2, job_type: 'REBUILD_GRAPH', team_space_id: 3, team_space_name: '威胁情报库', ticket_id: 2001, operator_name: '王五', target_count: 5, success_count: 3, failed_count: 2, skipped_count: 0, status: 3, progress: 100, started_at: '2026-07-29T09:05:00Z', finished_at: '2026-07-29T09:30:00Z', error_summary: { neo4j_timeout: 2 } },
  { id: 3, job_type: 'PURGE_ORPHAN', team_space_id: 4, team_space_name: '历史归档空间', ticket_id: 2002, operator_name: '赵六', target_count: 8, success_count: 5, failed_count: 0, skipped_count: 3, status: 1, progress: 62, started_at: '2026-07-29T12:35:00Z', finished_at: '', error_summary: {} },
];

export const mockHealPreview: HealPreview = {
  target_count: 8,
  est_minutes: 3,
  risk: 'low',
};

export const mockHealTargetFiles: HealTargetFile[] = [
  { id: 1, file_id: 8001, file_name: 'sample_8001.bin', error_code: 'PG_EXISTS_MINIO_MISSING', team_space_id: 1, team_space_name: '红方作战A组', status: 'pending', created_at: '2026-07-29T08:30:00Z' },
  { id: 2, file_id: 8002, file_name: 'sample_8002.bin', error_code: 'PG_EXISTS_MINIO_MISSING', team_space_id: 1, team_space_name: '红方作战A组', status: 'pending', created_at: '2026-07-29T08:31:00Z' },
  { id: 3, file_id: 8003, file_name: 'sample_8003.bin', error_code: 'MINIO_EXISTS_PG_MISSING', team_space_id: 1, team_space_name: '红方作战A组', status: 'pending', created_at: '2026-07-29T08:32:00Z' },
];

// ===================== D4 生命周期 =====================

export const mockLifecyclePolicies: LifecyclePolicy[] = [
  { id: 1, team_space_id: 1, team_space_name: '红方作战A组', policy_name: 'A组标准策略', cold_after_days: 30, expire_after_days: 365, archive_storage_class: 'STANDARD_IA', enabled: 1, created_at: '2024-08-12T08:00:00Z' },
  { id: 2, team_space_id: 3, team_space_name: '威胁情报库', policy_name: '情报库长期保留', cold_after_days: 90, expire_after_days: 1095, archive_storage_class: 'GLACIER', enabled: 1, created_at: '2024-05-20T08:00:00Z' },
  { id: 3, team_space_id: null, team_space_name: '全局默认', policy_name: '全局默认策略', cold_after_days: 60, expire_after_days: 730, archive_storage_class: 'STANDARD_IA', enabled: 1, created_at: '2024-01-01T08:00:00Z' },
];

export const mockColdCandidates: ColdCandidate[] = [
  { id: 1, file_id: 5001, file_name: 'historical_report_2023.pdf', team_space_id: 1, team_space_name: '红方作战A组', size: 52428800, last_access_at: '2024-12-01T08:00:00Z' },
  { id: 2, file_id: 5002, file_name: 'archive_data_q3.bin', team_space_id: 1, team_space_name: '红方作战A组', size: 102400000, last_access_at: '2024-11-15T08:00:00Z' },
  { id: 3, file_id: 5003, file_name: 'evidence_old.json', team_space_id: 4, team_space_name: '历史归档空间', size: 8192, last_access_at: '2024-09-20T08:00:00Z' },
  { id: 4, file_id: 5004, file_name: 'sample_2023_001.bin', team_space_id: 3, team_space_name: '威胁情报库', size: 25600000, last_access_at: '2024-10-10T08:00:00Z' },
];

export const mockCapacityData: CapacityData = {
  trend: [
    { date: '2026-07-23', hot: 580, cold: 320, archived: 180 },
    { date: '2026-07-24', hot: 590, cold: 325, archived: 185 },
    { date: '2026-07-25', hot: 600, cold: 330, archived: 190 },
    { date: '2026-07-26', hot: 605, cold: 335, archived: 195 },
    { date: '2026-07-27', hot: 610, cold: 340, archived: 200 },
    { date: '2026-07-28', hot: 620, cold: 345, archived: 205 },
    { date: '2026-07-29', hot: 630, cold: 350, archived: 210 },
  ],
  tierRatio: [
    { name: '热存储', value: 630 },
    { name: '冷存储', value: 350 },
    { name: '归档存储', value: 210 },
  ],
  predict: [
    { space: '红方作战A组', days: 45 },
    { space: '威胁情报库', days: 28 },
    { space: '历史归档空间', days: 12 },
  ],
  topSpaces: [
    { id: 3, name: '威胁情报库', used: 412, quota: 500 },
    { id: 4, name: '历史归档空间', used: 320, quota: 500 },
    { id: 1, name: '红方作战A组', used: 234, quota: 500 },
    { id: 2, name: '红方作战B组', used: 156, quota: 300 },
  ],
};

// ===================== D5 应用配置 =====================

export const mockConfigItems: ConfigItem[] = [
  { id: 1, config_type: 'PARSER', config_key: 'parser.pdf.mode', value: 'ocr+text', version: 3, scope_type: 'GLOBAL', scope_space_ids: [], change_id: 0, effective_at: '2026-06-01T00:00:00Z' },
  { id: 2, config_type: 'MODEL', config_key: 'model.ner.endpoint', value: 'http://ner-svc:8000/v2', version: 5, scope_type: 'TEAM_SPACE', scope_space_ids: [1, 3], change_id: 0, effective_at: '2026-07-15T00:00:00Z' },
  { id: 3, config_type: 'YARA', config_key: 'yara.ruleset.version', value: '2026.07', version: 12, scope_type: 'GLOBAL', scope_space_ids: [], change_id: 0, effective_at: '2026-07-01T00:00:00Z' },
  { id: 4, config_type: 'UPLOAD', config_key: 'upload.max_size_mb', value: '2048', version: 2, scope_type: 'GLOBAL', scope_space_ids: [], change_id: 0, effective_at: '2026-04-01T00:00:00Z' },
  { id: 5, config_type: 'INDEX', config_key: 'index.batch_size', value: '500', version: 4, scope_type: 'TEAM_SPACE', scope_space_ids: [3], change_id: 0, effective_at: '2026-05-20T00:00:00Z' },
  { id: 6, config_type: 'RETRY', config_key: 'retry.parse.max_attempts', value: '3', version: 1, scope_type: 'GLOBAL', scope_space_ids: [], change_id: 0, effective_at: '2026-01-01T00:00:00Z' },
];

export const mockConfigChanges: ConfigChange[] = [
  { id: 9001, config_type: 'PARSER', config_key: 'parser.pdf.mode', version: 4, old_value: 'ocr+text', new_value: 'ocr+text+layout', operator_name: '王五', reason: '优化 PDF 解析准确率', status: 2, effective_at: '', ticket_id: 3001 },
  { id: 9002, config_type: 'MODEL', config_key: 'model.ner.endpoint', version: 6, old_value: 'http://ner-svc:8000/v2', new_value: 'http://ner-svc:8000/v3', operator_name: '李四', reason: '升级 NER 模型到 v3', status: 1, effective_at: '', ticket_id: 3002 },
  { id: 9003, config_type: 'YARA', config_key: 'yara.ruleset.version', version: 13, old_value: '2026.07', new_value: '2026.08', operator_name: '系统管理员', reason: '月度规则集更新', status: 3, effective_at: '2026-08-01T00:00:00Z', ticket_id: 3003 },
  { id: 9004, config_type: 'INDEX', config_key: 'index.batch_size', version: 3, old_value: '500', new_value: '200', operator_name: '王五', reason: '减小批量避免 OOM', status: 4, effective_at: '2026-05-25T00:00:00Z', ticket_id: 3004 },
];

export const mockConfigImpact: ConfigImpact = {
  affected_files: 45230,
  affected_spaces: 2,
};

// ===================== D6 数据安全 =====================

export const mockStalePermissions: StalePermission[] = [
  { id: 1, user_id: 501, username: 'former_emp_01', nickname: '离职员工01', team_space_id: 1, team_space_name: '红方作战A组', role: 'MAINTAINER', stale_type: 'RESIGNED_MEMBER', found_at: '2026-07-29T08:00:00Z' },
  { id: 2, user_id: 502, username: 'former_emp_02', nickname: '离职员工02', team_space_id: 3, team_space_name: '威胁情报库', role: 'VIEWER', stale_type: 'RESIGNED_MEMBER', found_at: '2026-07-29T08:00:00Z' },
  { id: 3, user_id: 601, username: 'over_perm_01', nickname: '越权用户01', team_space_id: 1, team_space_name: '红方作战A组', role: 'OWNER', stale_type: 'OVER_PRIVILEGE', found_at: '2026-07-29T08:00:00Z' },
  { id: 4, user_id: 602, username: 'over_perm_02', nickname: '越权用户02', team_space_id: 2, team_space_name: '红方作战B组', role: 'MAINTAINER', stale_type: 'OVER_PRIVILEGE', found_at: '2026-07-29T08:00:00Z' },
  { id: 5, user_id: 0, username: '', nickname: '过期分享链接', team_space_id: 4, team_space_name: '历史归档空间', role: 'LINK', stale_type: 'EXPIRED_LINK', found_at: '2026-07-29T08:00:00Z' },
];

export const mockDownloadAnomalies: DownloadAnomaly[] = [
  { id: 1, user_id: 301, username: 'wangwu', team_space_id: 1, team_space_name: '红方作战A组', count: 523, time: '2026-07-29T03:00:00Z', rule: '夜间异常下载 (>100/小时)', risk_score: 92 },
  { id: 2, user_id: 602, username: 'over_perm_02', team_space_id: 2, team_space_name: '红方作战B组', count: 280, time: '2026-07-28T22:00:00Z', rule: '非工作时段批量下载', risk_score: 85 },
  { id: 3, user_id: 301, username: 'wangwu', team_space_id: 3, team_space_name: '威胁情报库', count: 1500, time: '2026-07-27T14:00:00Z', rule: '单日下载量异常 (>1000)', risk_score: 78 },
];

export const mockSensitiveAccess: SensitiveAccess[] = [
  { id: 1, file_id: 8001, file_name: 'critical_vuln_report.pdf', sensitivity_level: 5, user_id: 301, username: 'wangwu', access_count: 23, last_access_at: '2026-07-29T08:00:00Z', team_space_id: 1, team_space_name: '红方作战A组' },
  { id: 2, file_id: 8002, file_name: 'zero_day_evidence.bin', sensitivity_level: 5, user_id: 602, username: 'over_perm_02', access_count: 18, last_access_at: '2026-07-28T20:00:00Z', team_space_id: 2, team_space_name: '红方作战B组' },
  { id: 3, file_id: 8003, file_name: 'internal_strategy.md', sensitivity_level: 4, user_id: 201, username: 'lisi', access_count: 45, last_access_at: '2026-07-29T10:00:00Z', team_space_id: 1, team_space_name: '红方作战A组' },
];

export const mockExportRequests: ExportRequest[] = [
  { id: 1, requester_id: 301, requester_name: '王五', team_space_id: 1, team_space_name: '红方作战A组', export_scope: { filter: 'tag:malware' }, data_size: 1024 ** 3 * 5, sensitive_level_max: 4, status: 0, ticket_id: 4001, package_url: '', watermark: 'WANGWU-20260729', expires_at: '', created_at: '2026-07-29T09:00:00Z' },
  { id: 2, requester_id: 201, requester_name: '李四', team_space_id: 3, team_space_name: '威胁情报库', export_scope: { filter: 'date:2026-07' }, data_size: 1024 ** 3 * 12, sensitive_level_max: 3, status: 3, ticket_id: 4002, package_url: '', watermark: 'LISI-20260728', expires_at: '2026-08-04T00:00:00Z', created_at: '2026-07-28T14:00:00Z' },
  { id: 3, requester_id: 101, requester_name: '张三', team_space_id: 1, team_space_name: '红方作战A组', export_scope: { filter: 'all' }, data_size: 1024 ** 3 * 2, sensitive_level_max: 2, status: 4, ticket_id: 4003, package_url: 'https://minio.example/exports/exp_3.zip', watermark: 'ZHANGSAN-20260720', expires_at: '2026-08-20T00:00:00Z', created_at: '2026-07-20T10:00:00Z' },
];

// ===================== D7 空间报告 =====================

export const mockReports: SpaceReport[] = [
  {
    id: 1, team_space_id: 1, team_space_name: '红方作战A组', report_type: 'WEEKLY',
    period_start: '2026-07-22T00:00:00Z', period_end: '2026-07-28T23:59:59Z',
    health_score: 88, summary: { file_growth: 1200, storage_growth_gb: 8, parse_success_rate: 99.1, quota_usage: 0.78, top_failures: [{ error_code: 'es_timeout', count: 12 }, { error_code: 'parse_oom', count: 3 }] },
    suggestions: [{ id: 1, type: 'HEAL', desc: '建议清理 12 条索引积压', ticket_type: 'REINDEX' }, { id: 2, type: 'QUOTA', desc: '配额使用 78%，建议提前扩容', ticket_type: 'QUOTA' }],
    pdf_url: 'https://reports.example/weekly_1.pdf', created_at: '2026-07-29T02:00:00Z',
  },
  {
    id: 2, team_space_id: 3, team_space_name: '威胁情报库', report_type: 'MONTHLY',
    period_start: '2026-07-01T00:00:00Z', period_end: '2026-07-31T23:59:59Z',
    health_score: 92, summary: { file_growth: 8500, storage_growth_gb: 35, parse_success_rate: 99.5, quota_usage: 0.82, top_failures: [{ error_code: 'neo4j_timeout', count: 5 }] },
    suggestions: [{ id: 3, type: 'HEAL', desc: '建议修复 5 条 Neo4j 节点缺失', ticket_type: 'REBUILD_GRAPH' }],
    pdf_url: 'https://reports.example/monthly_3.pdf', created_at: '2026-07-31T02:00:00Z',
  },
  {
    id: 3, team_space_id: 4, team_space_name: '历史归档空间', report_type: 'ALERT',
    period_start: '2026-07-29T00:00:00Z', period_end: '2026-07-29T08:00:00Z',
    health_score: 65, summary: { file_growth: 0, storage_growth_gb: 0, parse_success_rate: 0, quota_usage: 0.64, top_failures: [{ error_code: 'orphan_object', count: 8 }] },
    suggestions: [{ id: 4, type: 'HEAL', desc: '检测到 8 个孤儿对象，建议清理', ticket_type: 'DELETE' }],
    pdf_url: 'https://reports.example/alert_4.pdf', created_at: '2026-07-29T08:30:00Z',
  },
];

export const mockReportSubscriptions: ReportSubscription[] = [
  { id: 1, user_id: 101, team_space_id: 1, report_types: 'WEEKLY,MONTHLY', channel: 'email', enabled: 1, created_at: '2026-06-01T00:00:00Z' },
  { id: 2, user_id: 103, team_space_id: 3, report_types: 'MONTHLY', channel: 'email', enabled: 1, created_at: '2026-05-15T00:00:00Z' },
  { id: 3, user_id: 104, team_space_id: null, report_types: 'ALERT', channel: 'sms', enabled: 1, created_at: '2026-04-10T00:00:00Z' },
];

// ===================== 运维工单 =====================

export const mockTickets: OpsTicket[] = [
  { id: 1001, ticket_no: 'OPS-2026-1001', ticket_type: 'QUOTA', title: 'A组配额扩容至500G', description: '业务量增长，申请季度扩容', team_space_id: 1, team_space_name: '红方作战A组', target_ref: 'space:1', params: { new_quota_gb: 500 }, impact_preview: { old_quota_gb: 300, diff_gb: 200 }, status: 5, created_by: 101, created_by_name: '张三', assignee_name: '系统管理员', created_at: '2026-06-15T10:00:00Z', approved_at: '2026-06-15T11:00:00Z', executed_at: '2026-06-15T11:30:00Z', finished_at: '2026-06-15T11:35:00Z' },
  { id: 2001, ticket_no: 'OPS-2026-2001', ticket_type: 'REBUILD_GRAPH', title: '威胁情报库重建图关系', description: '5 条 Neo4j 节点缺失，需重建', team_space_id: 3, team_space_name: '威胁情报库', target_ref: 'space:3', params: { target_count: 5 }, impact_preview: { est_minutes: 5 }, status: 5, created_by: 103, created_by_name: '王五', assignee_name: 'DBA', created_at: '2026-07-29T09:00:00Z', approved_at: '2026-07-29T09:02:00Z', executed_at: '2026-07-29T09:05:00Z', finished_at: '2026-07-29T09:30:00Z' },
  { id: 2002, ticket_no: 'OPS-2026-2002', ticket_type: 'DELETE', title: '历史归档空间清理孤儿对象', description: '8 个孤儿对象待清理', team_space_id: 4, team_space_name: '历史归档空间', target_ref: 'space:4', params: { target_count: 8 }, impact_preview: { est_minutes: 3 }, status: 3, created_by: 104, created_by_name: '赵六', assignee_name: 'SRE', created_at: '2026-07-29T12:00:00Z', approved_at: '2026-07-29T12:30:00Z', executed_at: '2026-07-29T12:35:00Z', finished_at: '' },
  { id: 3001, ticket_no: 'OPS-2026-3001', ticket_type: 'CONFIG', title: 'PDF 解析模式升级', description: '升级至 ocr+text+layout 模式', team_space_id: 0, team_space_name: '', target_ref: 'config:9001', params: { config_change_id: 9001 }, impact_preview: { affected_files: 45230, affected_spaces: 2 }, status: 2, created_by: 103, created_by_name: '王五', assignee_name: '系统管理员', created_at: '2026-07-29T14:00:00Z', approved_at: '', executed_at: '', finished_at: '' },
  { id: 4001, ticket_no: 'OPS-2026-4001', ticket_type: 'EXPORT', title: 'A组 malware 标签数据导出', description: '导出 tag:malware 文件', team_space_id: 1, team_space_name: '红方作战A组', target_ref: 'export:1', params: { export_id: 1 }, impact_preview: { data_size_gb: 5, sensitive_level_max: 4 }, status: 1, created_by: 301, created_by_name: '王五', assignee_name: '安全工程师', created_at: '2026-07-29T09:00:00Z', approved_at: '', executed_at: '', finished_at: '' },
];

// ===================== Mock 查询辅助 =====================

/** 通用分页过滤（基于 list + total 字段） */
function paginate<T>(list: T[], params: OpsQueryParams): OpsPageResult<T> {
  const page = params.page ?? 1;
  const pageSize = params.pageSize ?? 10;
  const start = (page - 1) * pageSize;
  return { list: list.slice(start, start + pageSize), total: list.length, page, pageSize };
}

export function getMockSpaces(params: OpsQueryParams): OpsPageResult<TeamSpace> {
  let list = mockSpaces;
  if (params.team_space_id) list = list.filter((s) => s.id === params.team_space_id);
  if (params.q) {
    const q = params.q.toLowerCase();
    list = list.filter((s) => s.name.toLowerCase().includes(q) || s.code.toLowerCase().includes(q));
  }
  return paginate(list, params);
}

export function getMockConsistencyChecks(params: OpsQueryParams): OpsPageResult<ConsistencyCheck> {
  let list = mockConsistencyChecks;
  if (params.team_space_id) list = list.filter((c) => c.team_space_id === params.team_space_id);
  return paginate(list, params);
}

export function getMockConsistencyDiffs(checkId: number, params: OpsQueryParams): OpsPageResult<ConsistencyDiff> {
  const list = mockConsistencyDiffs.filter((d) => d.check_id === checkId);
  return paginate(list, params);
}

export function getMockHealJobs(params: OpsQueryParams): OpsPageResult<HealJob> {
  let list = mockHealJobs;
  if (params.team_space_id) list = list.filter((j) => j.team_space_id === params.team_space_id);
  return paginate(list, params);
}

export function getMockLifecyclePolicies(params: OpsQueryParams): OpsPageResult<LifecyclePolicy> {
  let list = mockLifecyclePolicies;
  if (params.team_space_id) list = list.filter((p) => p.team_space_id === params.team_space_id);
  return paginate(list, params);
}

export function getMockColdCandidates(params: OpsQueryParams): OpsPageResult<ColdCandidate> {
  let list = mockColdCandidates;
  if (params.team_space_id) list = list.filter((c) => c.team_space_id === params.team_space_id);
  return paginate(list, params);
}

export function getMockQuotaList(params: OpsQueryParams): OpsPageResult<TeamSpace> {
  return getMockSpaces(params);
}

export function getMockConfigItems(type: string, params: OpsQueryParams): OpsPageResult<ConfigItem> {
  let list = mockConfigItems;
  if (type && type !== 'all') list = list.filter((c) => c.config_type === type);
  return paginate(list, params);
}

export function getMockConfigChanges(params: OpsQueryParams & { config_type?: string }): OpsPageResult<ConfigChange> {
  let list = mockConfigChanges;
  if (params.config_type) list = list.filter((c) => c.config_type === params.config_type);
  return paginate(list, params);
}

export function getMockStalePermissions(params: OpsQueryParams): OpsPageResult<StalePermission> {
  let list = mockStalePermissions;
  if (params.team_space_id) list = list.filter((s) => s.team_space_id === params.team_space_id);
  return paginate(list, params);
}

export function getMockDownloadAnomalies(params: OpsQueryParams): OpsPageResult<DownloadAnomaly> {
  let list = mockDownloadAnomalies;
  if (params.team_space_id) list = list.filter((d) => d.team_space_id === params.team_space_id);
  return paginate(list, params);
}

export function getMockSensitiveAccess(params: OpsQueryParams): OpsPageResult<SensitiveAccess> {
  let list = mockSensitiveAccess;
  if (params.team_space_id) list = list.filter((s) => s.team_space_id === params.team_space_id);
  return paginate(list, params);
}

export function getMockExportRequests(params: OpsQueryParams): OpsPageResult<ExportRequest> {
  let list = mockExportRequests;
  if (params.team_space_id) list = list.filter((e) => e.team_space_id === params.team_space_id);
  return paginate(list, params);
}

export function getMockReports(params: OpsQueryParams & { report_type?: string }): OpsPageResult<SpaceReport> {
  let list = mockReports;
  if (params.team_space_id) list = list.filter((r) => r.team_space_id === params.team_space_id);
  if (params.report_type) list = list.filter((r) => r.report_type === params.report_type);
  return paginate(list, params);
}

export function getMockTickets(params: OpsQueryParams): OpsPageResult<OpsTicket> {
  let list = mockTickets;
  if (params.team_space_id) list = list.filter((t) => t.team_space_id === params.team_space_id);
  if (params.q) {
    const q = params.q.toLowerCase();
    list = list.filter((t) => t.title.toLowerCase().includes(q) || t.ticket_no.toLowerCase().includes(q));
  }
  return paginate(list, params);
}

/** 按类型查找 mock 数据 */
export function findMockSpace(id: number): TeamSpace | undefined {
  return mockSpaces.find((s) => s.id === id);
}

export function findMockReport(id: number): SpaceReport | undefined {
  return mockReports.find((r) => r.id === id);
}

export function findMockHealJob(id: number): HealJob | undefined {
  return mockHealJobs.find((j) => j.id === id);
}

/** 用于类型推断的辅助常量（避免未使用警告） */
export const _typeCheck = {
  mockHealTargetFiles,
  mockCapacityData,
  mockConfigImpact,
  mockReportSubscriptions,
  mockSpaceHealth,
  mockSpaceMembers,
  mockQuotaLogs,
  mockSpaceEvents,
  mockHealPreview,
};

/** 类型导出辅助，确保枚举类型在 Mock 内部可用 */
export type {
  HealJobType,
  CheckType,
  TicketType,
  TicketStatus,
  ExportStatus,
  DiffStatus,
  DiffSuggestedAction,
  HealJobStatus,
  ConfigChangeStatus,
};
