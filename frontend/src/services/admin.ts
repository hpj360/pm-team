/**
 * 后台管理模块 API 服务
 * 包含用户、角色、权限、YARA 规则、系统配置、审计日志、数据源、模型、健康检查
 * 全部接口在请求失败时回退到 Mock 数据。
 */
import { get, post, put, del } from '@/utils/request';
import type {
  ApiResponse,
  AdminUser,
  AdminRole,
  AdminPermission,
  AdminYaraRule,
  YaraTestResult,
  SystemConfigItem,
  AuditLogItem,
  DataSource,
  AiModel,
  HealthCheckItem,
  HealthOverview,
  ReportItem,
  ReportTemplate,
  ReportType,
  ReportFormat,
  NotificationItem,
  NotificationType,
  ReportSchedule,
  ReportScheduleHistory,
  CreateReportSchedulePayload,
  ReportScheduleQueryParams,
} from '@/types';
import {
  getMockAdminUserList,
  mockAdminRoles,
  mockAdminPermissions,
  mockAdminYaraRules,
  testYaraRule,
  mockSystemConfigs,
  mockAuditLogs,
  getMockAuditLogs,
  mockDataSources,
  mockAiModels,
  mockHealthChecks,
  mockHealthOverview,
  mockReports,
  mockReportTemplates,
  getReportById,
  getReportTemplateById,
  getTemplatesByType,
  mockNotifications,
  getNotificationById,
  mockReportSchedules,
  getReportScheduleById,
  getReportScheduleHistoryByScheduleId,
  getMockReportSchedules,
} from '@/mock';

/* ===================== 用户管理 ===================== */

/** 获取用户列表 */
export async function getAdminUsers(params: {
  keyword?: string;
  status?: AdminUser['status'];
  page: number;
  pageSize: number;
}): Promise<ApiResponse<{ list: AdminUser[]; total: number }>> {
  try {
    return await get<{ list: AdminUser[]; total: number }>('/admin/users', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockAdminUserList(params) };
  }
}

/** 新增/编辑用户（Mock 直接返回成功） */
export function saveAdminUser(user: Partial<AdminUser>): Promise<ApiResponse<void>> {
  return post<void>('/admin/users', user as unknown as Record<string, unknown>);
}

/** 切换用户状态 */
export function toggleUserStatus(id: string, status: AdminUser['status']): Promise<ApiResponse<void>> {
  return put<void>(`/admin/users/${id}/status`, { status });
}

/** 删除用户 */
export function deleteAdminUser(id: string): Promise<ApiResponse<void>> {
  return del<void>(`/admin/users/${id}`);
}

/* ===================== 角色管理 ===================== */

/** 获取角色列表 */
export async function getAdminRoles(): Promise<ApiResponse<AdminRole[]>> {
  try {
    return await get<AdminRole[]>('/admin/roles');
  } catch {
    return { code: 200, message: 'success', data: mockAdminRoles };
  }
}

/** 保存角色 */
export function saveAdminRole(role: Partial<AdminRole>): Promise<ApiResponse<void>> {
  return post<void>('/admin/roles', role as unknown as Record<string, unknown>);
}

/** 删除角色 */
export function deleteAdminRole(id: string): Promise<ApiResponse<void>> {
  return del<void>(`/admin/roles/${id}`);
}

/* ===================== 权限管理 ===================== */

/** 获取权限列表 */
export async function getAdminPermissions(): Promise<ApiResponse<AdminPermission[]>> {
  try {
    return await get<AdminPermission[]>('/admin/permissions');
  } catch {
    return { code: 200, message: 'success', data: mockAdminPermissions };
  }
}

/* ===================== YARA 规则管理 ===================== */

/** 获取 YARA 规则列表 */
export async function getAdminYaraRules(): Promise<ApiResponse<AdminYaraRule[]>> {
  try {
    return await get<AdminYaraRule[]>('/admin/yara-rules');
  } catch {
    return { code: 200, message: 'success', data: mockAdminYaraRules };
  }
}

/** 获取 YARA 规则详情 */
export async function getAdminYaraRuleDetail(id: string): Promise<ApiResponse<AdminYaraRule>> {
  try {
    return await get<AdminYaraRule>(`/admin/yara-rules/${id}`);
  } catch {
    const data = mockAdminYaraRules.find((r) => r.id === id) ?? mockAdminYaraRules[0];
    return { code: 200, message: 'success', data };
  }
}

/** 保存 YARA 规则 */
export function saveAdminYaraRule(rule: Partial<AdminYaraRule>): Promise<ApiResponse<void>> {
  return post<void>('/admin/yara-rules', rule as unknown as Record<string, unknown>);
}

/** 切换 YARA 规则启用状态 */
export function toggleYaraRuleStatus(id: string, enabled: boolean): Promise<ApiResponse<void>> {
  return put<void>(`/admin/yara-rules/${id}/status`, { enabled });
}

/** 删除 YARA 规则 */
export function deleteAdminYaraRule(id: string): Promise<ApiResponse<void>> {
  return del<void>(`/admin/yara-rules/${id}`);
}

/** 测试 YARA 规则 */
export async function testYaraRuleSource(source: string): Promise<ApiResponse<YaraTestResult>> {
  try {
    return await post<YaraTestResult>('/admin/yara-rules/test', { source });
  } catch {
    return { code: 200, message: 'success', data: testYaraRule(source) };
  }
}

/* ===================== 系统配置 ===================== */

/** 获取系统配置 */
export async function getSystemConfigs(): Promise<ApiResponse<SystemConfigItem[]>> {
  try {
    return await get<SystemConfigItem[]>('/admin/config');
  } catch {
    return { code: 200, message: 'success', data: mockSystemConfigs };
  }
}

/** 保存系统配置 */
export function saveSystemConfigs(items: SystemConfigItem[]): Promise<ApiResponse<void>> {
  return post<void>('/admin/config', { items: items as unknown as Record<string, unknown> });
}

/* ===================== 审计日志 ===================== */

/** 获取审计日志 */
export async function getAuditLogs(params: {
  username?: string;
  action?: AuditLogItem['action'];
  startTime?: string;
  endTime?: string;
  page: number;
  pageSize: number;
}): Promise<ApiResponse<{ list: AuditLogItem[]; total: number }>> {
  try {
    return await get<{ list: AuditLogItem[]; total: number }>('/admin/audit-logs', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockAuditLogs(params) };
  }
}

/** 获取审计日志详情 */
export async function getAuditLogDetail(id: string): Promise<ApiResponse<AuditLogItem>> {
  try {
    return await get<AuditLogItem>(`/admin/audit-logs/${id}`);
  } catch {
    const data = mockAuditLogs.find((l) => l.id === id) ?? mockAuditLogs[0];
    return { code: 200, message: 'success', data };
  }
}

/** 导出审计日志 */
export function exportAuditLogs(params: {
  username?: string;
  action?: AuditLogItem['action'];
  startTime?: string;
  endTime?: string;
}): Promise<ApiResponse<{ url: string }>> {
  return post<{ url: string }>('/admin/audit-logs/export', params as unknown as Record<string, unknown>);
}

/* ===================== 数据源管理 ===================== */

/** 获取数据源列表 */
export async function getDataSources(): Promise<ApiResponse<DataSource[]>> {
  try {
    return await get<DataSource[]>('/admin/data-sources');
  } catch {
    return { code: 200, message: 'success', data: mockDataSources };
  }
}

/** 测试数据源连接 */
export async function testDataSource(id: string): Promise<ApiResponse<{ status: DataSource['status']; latencyMs: number }>> {
  try {
    return await post<{ status: DataSource['status']; latencyMs: number }>(`/admin/data-sources/${id}/test`);
  } catch {
    const ds = mockDataSources.find((d) => d.id === id) ?? mockDataSources[0];
    return {
      code: 200,
      message: 'success',
      data: { status: ds.status, latencyMs: ds.latencyMs ?? 0 },
    };
  }
}

/* ===================== 模型管理 ===================== */

/** 获取 AI 模型列表 */
export async function getAiModels(): Promise<ApiResponse<AiModel[]>> {
  try {
    return await get<AiModel[]>('/admin/models');
  } catch {
    return { code: 200, message: 'success', data: mockAiModels };
  }
}

/** 加载模型 */
export function loadModel(id: string): Promise<ApiResponse<void>> {
  return post<void>(`/admin/models/${id}/load`);
}

/** 卸载模型 */
export function unloadModel(id: string): Promise<ApiResponse<void>> {
  return post<void>(`/admin/models/${id}/unload`);
}

/** 测试模型 */
export async function testModel(id: string): Promise<ApiResponse<{ success: boolean; latencyMs: number }>> {
  try {
    return await post<{ success: boolean; latencyMs: number }>(`/admin/models/${id}/test`);
  } catch {
    const m = mockAiModels.find((x) => x.id === id) ?? mockAiModels[0];
    return { code: 200, message: 'success', data: { success: m.status !== 'error', latencyMs: m.latencyMs ?? 30 } };
  }
}

/* ===================== 健康检查 ===================== */

/** 获取微服务健康状态列表 */
export async function getHealthChecks(): Promise<ApiResponse<HealthCheckItem[]>> {
  try {
    return await get<HealthCheckItem[]>('/admin/health');
  } catch {
    return { code: 200, message: 'success', data: mockHealthChecks };
  }
}

/** 获取健康总览 */
export async function getHealthOverview(): Promise<ApiResponse<HealthOverview>> {
  try {
    return await get<HealthOverview>('/admin/health/overview');
  } catch {
    return { code: 200, message: 'success', data: mockHealthOverview };
  }
}

/** 重新检查指定服务 */
export async function recheckService(id: string): Promise<ApiResponse<HealthCheckItem>> {
  try {
    return await get<HealthCheckItem>(`/admin/health/${id}`);
  } catch {
    const data = mockHealthChecks.find((h) => h.id === id) ?? mockHealthChecks[0];
    return { code: 200, message: 'success', data };
  }
}

/* ===================== 报告中心 ===================== */

/** 获取报告列表 */
export async function getReports(params?: {
  keyword?: string;
  type?: ReportType;
  status?: ReportItem['status'];
}): Promise<ApiResponse<ReportItem[]>> {
  try {
    return await get<ReportItem[]>('/admin/reports', params as unknown as Record<string, unknown>);
  } catch {
    let arr = [...mockReports];
    if (params?.keyword) {
      const kw = params.keyword.toLowerCase();
      arr = arr.filter(
        (r) =>
          r.title.toLowerCase().includes(kw) ||
          (r.targetName ?? '').toLowerCase().includes(kw) ||
          (r.creator ?? '').toLowerCase().includes(kw),
      );
    }
    if (params?.type) arr = arr.filter((r) => r.type === params.type);
    if (params?.status) arr = arr.filter((r) => r.status === params.status);
    return { code: 200, message: 'success', data: arr };
  }
}

/** 获取报告详情 */
export async function getReportDetail(id: string): Promise<ApiResponse<ReportItem>> {
  try {
    return await get<ReportItem>(`/admin/reports/${id}`);
  } catch {
    const data = getReportById(id) ?? mockReports[0];
    return { code: 200, message: 'success', data };
  }
}

/** 获取报告模板列表 */
export async function getReportTemplates(): Promise<ApiResponse<ReportTemplate[]>> {
  try {
    return await get<ReportTemplate[]>('/admin/report-templates');
  } catch {
    return { code: 200, message: 'success', data: mockReportTemplates };
  }
}

/** 按类型获取报告模板 */
export async function getReportTemplatesByType(type: ReportType): Promise<ApiResponse<ReportTemplate[]>> {
  try {
    return await get<ReportTemplate[]>(`/admin/report-templates?type=${type}`);
  } catch {
    return { code: 200, message: 'success', data: getTemplatesByType(type) };
  }
}

/** 按 ID 获取报告模板 */
export async function getReportTemplateDetail(id: string): Promise<ApiResponse<ReportTemplate>> {
  try {
    return await get<ReportTemplate>(`/admin/report-templates/${id}`);
  } catch {
    const data = getReportTemplateById(id) ?? mockReportTemplates[0];
    return { code: 200, message: 'success', data };
  }
}

/**
 * 生成报告
 * - Mock：直接返回成功，1s 后报告变为 completed
 */
export function generateReport(params: {
  templateId: string;
  title: string;
  targetId?: string;
  fileIds?: string[];
  format: ReportFormat;
}): Promise<ApiResponse<{ reportId: string }>> {
  return post<{ reportId: string }>('/admin/reports/generate', params as unknown as Record<string, unknown>);
}

/** 导出/下载报告 */
export async function exportReport(id: string, format: ReportFormat): Promise<ApiResponse<{ url: string }>> {
  try {
    return await post<{ url: string }>(`/admin/reports/${id}/export`, { format });
  } catch {
    const report = getReportById(id) ?? mockReports[0];
    const ext = format === 'pdf' ? 'pdf' : format === 'html' ? 'html' : 'md';
    return {
      code: 200,
      message: 'success',
      data: { url: report.downloadUrl ?? `/downloads/${id}.${ext}` },
    };
  }
}

/** 删除报告 */
export function deleteReport(id: string): Promise<ApiResponse<void>> {
  return del<void>(`/admin/reports/${id}`);
}

/** 归档报告 */
export function archiveReport(id: string): Promise<ApiResponse<void>> {
  return post<void>(`/admin/reports/${id}/archive`);
}

/* ===================== 定时报告 ===================== */

/**
 * 获取定时报告列表（分页）
 * GET /api/report/schedules?page=1&size=10
 */
export async function getReportSchedules(
  params?: ReportScheduleQueryParams,
): Promise<ApiResponse<{ list: ReportSchedule[]; total: number }>> {
  try {
    return await get<{ list: ReportSchedule[]; total: number }>('/report/schedules', {
      page: params?.page ?? 1,
      size: params?.size ?? 10,
      keyword: params?.keyword,
      reportType: params?.reportType,
      status: params?.status,
    } as unknown as Record<string, unknown>);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: getMockReportSchedules({
        page: params?.page,
        size: params?.size,
        keyword: params?.keyword,
        reportType: params?.reportType,
        status: params?.status,
      }),
    };
  }
}

/**
 * 获取定时报告详情
 * GET /api/report/schedules/{id}
 */
export async function getReportScheduleDetail(
  id: number | string,
): Promise<ApiResponse<ReportSchedule>> {
  try {
    return await get<ReportSchedule>(`/report/schedules/${id}`);
  } catch {
    const data = getReportScheduleById(id) ?? mockReportSchedules[0];
    return { code: 200, message: 'success', data };
  }
}

/**
 * 创建定时报告配置
 * POST /api/report/schedules
 */
export function createReportSchedule(
  payload: CreateReportSchedulePayload,
): Promise<ApiResponse<{ id: number | string }>> {
  return post<{ id: number | string }>('/report/schedules', payload as unknown as Record<string, unknown>);
}

/**
 * 启停切换定时报告
 * PUT /api/report/schedules/{id}/toggle
 */
export function toggleReportSchedule(
  id: number | string,
): Promise<ApiResponse<ReportSchedule>> {
  return put<ReportSchedule>(`/report/schedules/${id}/toggle`);
}

/**
 * 删除定时报告
 * DELETE /api/report/schedules/{id}
 */
export function deleteReportSchedule(
  id: number | string,
): Promise<ApiResponse<void>> {
  return del<void>(`/report/schedules/${id}`);
}

/**
 * 获取定时报告执行历史
 * GET /api/report/schedules/{id}/history
 */
export async function getReportScheduleHistory(
  id: number | string,
): Promise<ApiResponse<ReportScheduleHistory[]>> {
  try {
    return await get<ReportScheduleHistory[]>(`/report/schedules/${id}/history`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: getReportScheduleHistoryByScheduleId(id),
    };
  }
}

/* ===================== 通知中心 ===================== */

/** 获取通知列表 */
export async function getNotifications(params?: {
  type?: NotificationType;
  read?: boolean;
}): Promise<ApiResponse<NotificationItem[]>> {
  try {
    return await get<NotificationItem[]>('/admin/notifications', params as unknown as Record<string, unknown>);
  } catch {
    let arr = [...mockNotifications];
    if (params?.type) arr = arr.filter((n) => n.type === params.type);
    if (params?.read !== undefined) arr = arr.filter((n) => n.read === params.read);
    // 按时间倒序
    arr.sort((a, b) => (a.createTime < b.createTime ? 1 : -1));
    return { code: 200, message: 'success', data: arr };
  }
}

/** 获取通知详情 */
export async function getNotificationDetail(id: string): Promise<ApiResponse<NotificationItem>> {
  try {
    return await get<NotificationItem>(`/admin/notifications/${id}`);
  } catch {
    const data = getNotificationById(id) ?? mockNotifications[0];
    return { code: 200, message: 'success', data };
  }
}

/** 标记通知为已读 */
export function markNotificationRead(id: string): Promise<ApiResponse<void>> {
  return post<void>(`/admin/notifications/${id}/read`);
}

/** 全部标记为已读 */
export function markAllNotificationsRead(): Promise<ApiResponse<void>> {
  return post<void>('/admin/notifications/read-all');
}

/** 删除通知 */
export function deleteNotification(id: string): Promise<ApiResponse<void>> {
  return del<void>(`/admin/notifications/${id}`);
}
