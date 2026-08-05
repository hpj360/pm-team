/**
 * 后台管理模块类型定义
 * 涵盖用户、角色、权限、YARA 规则、配置、审计日志、数据源、模型、健康检查
 */

/* ===================== 1. 用户管理 ===================== */

/** 用户状态 */
export type UserStatus = 'active' | 'disabled' | 'locked';

export const UserStatusLabel: Record<UserStatus, string> = {
  active: '启用',
  disabled: '禁用',
  locked: '锁定',
};

/** 后台用户 */
export interface AdminUser {
  id: string;
  username: string;
  nickname: string;
  email: string;
  phone?: string;
  avatar?: string;
  roleIds: string[];
  dept: string;
  status: UserStatus;
  lastLoginAt?: string;
  lastLoginIp?: string;
  createTime: string;
}

/* ===================== 2. 角色管理 ===================== */

/** 角色 */
export interface AdminRole {
  id: string;
  name: string;
  code: string;
  description: string;
  permissionIds: string[];
  userCount: number;
  builtin: boolean;
  createTime: string;
}

/* ===================== 3. 权限管理 ===================== */

/** 权限类型 */
export type PermissionType = 'menu' | 'api' | 'action' | 'data';

export const PermissionTypeLabel: Record<PermissionType, string> = {
  menu: '菜单',
  api: '接口',
  action: '操作',
  data: '数据',
};

/** 权限 */
export interface AdminPermission {
  id: string;
  name: string;
  code: string;
  type: PermissionType;
  parentId?: string;
  resource: string;
  description: string;
  createTime: string;
}

/* ===================== 4. YARA 规则管理 ===================== */

/** 后台 YARA 规则（管理视图，扩展已有的 YaraRule） */
export interface AdminYaraRule {
  id: string;
  name: string;
  description: string;
  author: string;
  severity: 'info' | 'low' | 'medium' | 'high' | 'critical';
  tags: string[];
  source: string;
  enabled: boolean;
  matchCount: number;
  isCustom: boolean;
  createTime: string;
  updateTime: string;
}

/** YARA 规则测试结果 */
export interface YaraTestResult {
  matched: boolean;
  matchedRules: string[];
  costMs: number;
  output: string;
}

/* ===================== 5. 系统配置 ===================== */

/** 配置项 */
export interface SystemConfigItem {
  key: string;
  label: string;
  value: string | number | boolean;
  type: 'string' | 'number' | 'switch' | 'select';
  options?: string[];
  description: string;
  group: 'basic' | 'security' | 'storage' | 'search';
}

/* ===================== 6. 审计日志 ===================== */

/** 操作类型 */
export type AuditAction =
  | 'login'
  | 'logout'
  | 'create'
  | 'update'
  | 'delete'
  | 'export'
  | 'import'
  | 'execute';

export const AuditActionLabel: Record<AuditAction, string> = {
  login: '登录',
  logout: '登出',
  create: '新增',
  update: '更新',
  delete: '删除',
  export: '导出',
  import: '导入',
  execute: '执行',
};

/** 审计日志 */
export interface AuditLogItem {
  id: string;
  userId: string;
  username: string;
  action: AuditAction;
  resource: string;
  resourceId?: string;
  detail: string;
  ip: string;
  userAgent: string;
  status: 'success' | 'failed';
  costMs: number;
  createdAt: string;
}

/* ===================== 7. 数据源 ===================== */

/** 数据源类型 */
export type DataSourceType = 'elasticsearch' | 'milvus' | 'minio' | 'kafka' | 'mysql' | 'redis';

export const DataSourceTypeLabel: Record<DataSourceType, string> = {
  elasticsearch: 'Elasticsearch',
  milvus: 'Milvus',
  minio: 'MinIO',
  kafka: 'Kafka',
  mysql: 'MySQL',
  redis: 'Redis',
};

/** 数据源状态 */
export type DataSourceStatus = 'connected' | 'disconnected' | 'error' | 'checking' | 'degraded';

/** 数据源 */
export interface DataSource {
  id: string;
  name: string;
  type: DataSourceType;
  endpoint: string;
  port: number;
  database?: string;
  status: DataSourceStatus;
  latencyMs?: number;
  version?: string;
  lastError?: string;
  lastCheckAt: string;
  description: string;
}

/* ===================== 8. 模型管理 ===================== */

/** 模型状态 */
export type ModelStatus = 'loaded' | 'unloaded' | 'loading' | 'error';

export const ModelStatusLabel: Record<ModelStatus, string> = {
  loaded: '已加载',
  unloaded: '已卸载',
  loading: '加载中',
  error: '异常',
};

/** AI 模型 */
export interface AiModel {
  id: string;
  name: string;
  version: string;
  type: 'ner' | 'classify' | 'embedding' | 'llm' | 'detect';
  framework: 'pytorch' | 'onnx' | 'tensorflow';
  sizeMb: number;
  status: ModelStatus;
  accuracy?: number;
  latencyMs?: number;
  loadedAt?: string;
  description: string;
  createTime: string;
}

/* ===================== 9. 健康检查 ===================== */

/** 微服务健康状态 */
export type ServiceHealth = 'healthy' | 'degraded' | 'unhealthy' | 'unknown';

export const ServiceHealthLabel: Record<ServiceHealth, string> = {
  healthy: '健康',
  degraded: '降级',
  unhealthy: '异常',
  unknown: '未知',
};

/** 健康检查项 */
export interface HealthCheckItem {
  id: string;
  service: string;
  name: string;
  status: ServiceHealth;
  latencyMs: number;
  uptime: number;
  lastError?: string;
  lastCheckAt: string;
  version: string;
  dependencies: string[];
}

/** 健康总览 */
export interface HealthOverview {
  totalServices: number;
  healthyCount: number;
  degradedCount: number;
  unhealthyCount: number;
  items: HealthCheckItem[];
}

/* ===================== 10. 报告中心 ===================== */

/** 报告类型 */
export type ReportType =
  | 'penetration'
  | 'vulnerability'
  | 'threat_intel'
  | 'attack_chain'
  | 'asset'
  | 'audit';

export const ReportTypeLabel: Record<ReportType, string> = {
  penetration: '渗透测试报告',
  vulnerability: '漏洞分析报告',
  threat_intel: '威胁情报报告',
  attack_chain: '攻击链路报告',
  asset: '资产测绘报告',
  audit: '审计合规报告',
};

/** 报告状态 */
export type ReportStatus = 'draft' | 'generating' | 'completed' | 'failed' | 'archived';

export const ReportStatusLabel: Record<ReportStatus, string> = {
  draft: '草稿',
  generating: '生成中',
  completed: '已完成',
  failed: '失败',
  archived: '已归档',
};

/** 报告导出格式 */
export type ReportFormat = 'pdf' | 'html' | 'markdown';

export const ReportFormatLabel: Record<ReportFormat, string> = {
  pdf: 'PDF',
  html: 'HTML',
  markdown: 'Markdown',
};

/** 报告模板 */
export interface ReportTemplate {
  id: string;
  name: string;
  type: ReportType;
  description: string;
  /** 模板字段 */
  fields: Array<{
    key: string;
    label: string;
    required: boolean;
  }>;
  /** 默认导出格式 */
  defaultFormat: ReportFormat;
  /** 是否内置 */
  builtin: boolean;
  updateTime: string;
}

/** 报告项 */
export interface ReportItem {
  id: string;
  title: string;
  type: ReportType;
  status: ReportStatus;
  /** 关联模板 */
  templateId: string;
  templateName: string;
  /** 关联目标 */
  targetId?: string;
  targetName?: string;
  /** 关联文件 */
  fileIds?: string[];
  fileNames?: string[];
  /** 生成者 */
  creator: string;
  /** 生成时间 */
  generatedAt?: string;
  /** 文件大小（字节） */
  fileSize?: number;
  /** 导出格式 */
  format: ReportFormat;
  /** 下载链接 */
  downloadUrl?: string;
  /** HTML 预览内容 */
  htmlContent?: string;
  /** 摘要 */
  summary?: string;
  /** 标签 */
  tags?: string[];
  createTime: string;
  updateTime: string;
}

/* ===================== 11. 通知中心 ===================== */

/** 通知类型 */
export type NotificationType =
  | 'system'
  | 'task'
  | 'file'
  | 'security'
  | 'approval'
  | 'mention';

export const NotificationTypeLabel: Record<NotificationType, string> = {
  system: '系统通知',
  task: '任务通知',
  file: '文件通知',
  security: '安全告警',
  approval: '审批通知',
  mention: '提及',
};

/** 通知优先级 */
export type NotificationPriority = 'low' | 'normal' | 'high' | 'urgent';

export const NotificationPriorityLabel: Record<NotificationPriority, string> = {
  low: '低',
  normal: '普通',
  high: '高',
  urgent: '紧急',
};

/** 通知项 */
export interface NotificationItem {
  id: string;
  type: NotificationType;
  priority: NotificationPriority;
  title: string;
  content: string;
  /** 是否已读 */
  read: boolean;
  /** 来源（发送者） */
  sender: string;
  /** 跳转链接 */
  link?: string;
  /** 关联资源类型 */
  resourceType?: string;
  /** 关联资源 ID */
  resourceId?: string;
  /** 创建时间 */
  createTime: string;
  /** 读取时间 */
  readTime?: string;
}

/* ===================== 12. 定时报告 ===================== */

/** 定时报告类型（与后端 report-service 对齐，区别于 ReportType） */
export type ScheduleReportType =
  | 'target-profile'
  | 'penetration-test'
  | 'vulnerability-scan'
  | 'attack-chain'
  | 'task-summary';

export const ScheduleReportTypeLabel: Record<ScheduleReportType, string> = {
  'target-profile': '目标画像报告',
  'penetration-test': '渗透测试报告',
  'vulnerability-scan': '漏洞扫描报告',
  'attack-chain': '攻击链路报告',
  'task-summary': '任务汇总报告',
};

/** 定时报告状态 */
export type ScheduleStatus = 'ACTIVE' | 'INACTIVE';

export const ScheduleStatusLabel: Record<ScheduleStatus, string> = {
  ACTIVE: '启用',
  INACTIVE: '停用',
};

/** 上次执行状态 */
export type ScheduleRunStatus = 'SUCCESS' | 'FAILED' | 'RUNNING' | 'PENDING';

export const ScheduleRunStatusLabel: Record<ScheduleRunStatus, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  RUNNING: '运行中',
  PENDING: '等待中',
};

/** 定时报告配置 */
export interface ReportSchedule {
  id: number | string;
  reportName: string;
  reportType: ScheduleReportType;
  cronExpression: string;
  /** 收件人邮箱列表（逗号分隔字符串） */
  recipients: string;
  /** 模板名称（可选） */
  templateName?: string;
  /** 关联目标 ID（可选） */
  targetId?: number;
  /** 启用状态 */
  status: ScheduleStatus;
  /** 上次执行时间 */
  lastRunTime?: string;
  /** 上次执行状态 */
  lastRunStatus?: ScheduleRunStatus;
  /** 上次执行错误信息 */
  lastRunError?: string;
  /** 创建者 */
  creator?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/** 创建定时报告请求参数 */
export interface CreateReportSchedulePayload {
  reportName: string;
  reportType: ScheduleReportType;
  cronExpression: string;
  recipients: string;
  templateName?: string;
  targetId?: number;
}

/** 定时报告执行历史 */
export interface ReportScheduleHistory {
  id: number | string;
  scheduleId: number | string;
  /** 执行时间 */
  runTime: string;
  /** 执行状态 */
  status: ScheduleRunStatus;
  /** 耗时（毫秒） */
  costMs?: number;
  /** 生成的报告 ID */
  reportId?: string;
  /** 错误信息 */
  errorMessage?: string;
  /** 触发方式（cron / manual） */
  trigger?: 'cron' | 'manual';
}

/** 定时报告分页查询参数 */
export interface ReportScheduleQueryParams {
  page?: number;
  size?: number;
  /** 关键字（按报告名称模糊查询） */
  keyword?: string;
  /** 报告类型筛选 */
  reportType?: ScheduleReportType;
  /** 状态筛选 */
  status?: ScheduleStatus;
}
