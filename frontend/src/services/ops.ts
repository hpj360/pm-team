/**
 * 应用运维模块 API 服务（D1-D7 + 工单）
 * 所有接口在请求失败时回退到 Mock 数据。
 * 高风险操作附带幂等头（Idempotency-Key / X-Request-Nonce / X-Request-Timestamp）
 * 对齐 docs/app-ops-detailed-design.md §9.5
 */
import { get, post, patch } from '@/utils/request';
import type { ApiResponse, PageResult } from '@/types/common';
import type {
  TeamSpace,
  SpaceHealthDetail,
  SpaceMember,
  QuotaLog,
  SpaceEvent,
  ConsistencyCheck,
  ConsistencyDiff,
  CheckType,
  HealJob,
  HealPreview,
  HealJobType,
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
  SpaceLifecycleStatus,
} from '@/types/ops';
import {
  getMockSpaces,
  findMockSpace,
  getMockConsistencyChecks,
  getMockConsistencyDiffs,
  getMockHealJobs,
  findMockHealJob,
  getMockLifecyclePolicies,
  getMockColdCandidates,
  getMockQuotaList,
  getMockConfigItems,
  getMockConfigChanges,
  getMockStalePermissions,
  getMockDownloadAnomalies,
  getMockSensitiveAccess,
  getMockExportRequests,
  getMockReports,
  findMockReport,
  getMockTickets,
  mockSpaceHealth,
  mockSpaceMembers,
  mockQuotaLogs,
  mockSpaceEvents,
  mockHealPreview,
  mockHealTargetFiles,
  mockCapacityData,
  mockConfigImpact,
  mockConfigChanges,
  mockReportSubscriptions,
} from '@/mock/ops';

/** 生成幂等头（用于高风险操作） */
function idempotencyHeaders(): Record<string, string> {
  const ts = Date.now().toString();
  const nonce = Math.random().toString(36).slice(2, 12);
  const key = `${ts}-${nonce}`;
  return {
    'Idempotency-Key': key,
    'X-Request-Nonce': nonce,
    'X-Request-Timestamp': ts,
  };
}

// ===================== D1 空间台账 =====================

export async function getSpaces(params: OpsQueryParams): Promise<ApiResponse<PageResult<TeamSpace>>> {
  try {
    return await get<PageResult<TeamSpace>>('/ops/spaces', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockSpaces(params) };
  }
}

export async function getSpace(id: number): Promise<ApiResponse<TeamSpace>> {
  try {
    return await get<TeamSpace>(`/ops/spaces/${id}`);
  } catch {
    const data = findMockSpace(id);
    if (!data) return { code: 404, message: 'space not found', data: null as unknown as TeamSpace };
    return { code: 200, message: 'success', data };
  }
}

export async function getSpaceHealth(id: number): Promise<ApiResponse<SpaceHealthDetail>> {
  try {
    return await get<SpaceHealthDetail>(`/ops/spaces/${id}/health`);
  } catch {
    return { code: 200, message: 'success', data: mockSpaceHealth };
  }
}

export async function getSpaceMembers(id: number): Promise<ApiResponse<SpaceMember[]>> {
  try {
    return await get<SpaceMember[]>(`/ops/spaces/${id}/members`);
  } catch {
    return { code: 200, message: 'success', data: mockSpaceMembers };
  }
}

export async function getSpaceQuotaLog(id: number, params: OpsQueryParams): Promise<ApiResponse<PageResult<QuotaLog>>> {
  try {
    return await get<PageResult<QuotaLog>>(`/ops/spaces/${id}/quota-logs`, params as unknown as Record<string, unknown>);
  } catch {
    const page = params.page ?? 1;
    const pageSize = params.pageSize ?? 10;
    return { code: 200, message: 'success', data: { list: mockQuotaLogs, total: mockQuotaLogs.length, page, pageSize } };
  }
}

export async function getSpaceEvents(id: number, params: OpsQueryParams): Promise<ApiResponse<PageResult<SpaceEvent>>> {
  try {
    return await get<PageResult<SpaceEvent>>(`/ops/spaces/${id}/events`, params as unknown as Record<string, unknown>);
  } catch {
    const page = params.page ?? 1;
    const pageSize = params.pageSize ?? 10;
    return { code: 200, message: 'success', data: { list: mockSpaceEvents, total: mockSpaceEvents.length, page, pageSize } };
  }
}

export function patchSpaceStatus(id: number, body: { status: SpaceLifecycleStatus; version: number }): Promise<ApiResponse<void>> {
  return patch<void>(`/ops/spaces/${id}/status`, body as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function transferSpaceOwner(id: number, body: { new_owner_id: number }): Promise<ApiResponse<void>> {
  return post<void>(`/ops/spaces/${id}/transfer`, body as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

// ===================== D2 一致性对账 =====================

export async function getConsistencyResults(params: OpsQueryParams): Promise<ApiResponse<PageResult<ConsistencyCheck>>> {
  try {
    return await get<PageResult<ConsistencyCheck>>('/ops/consistency/checks', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockConsistencyChecks(params) };
  }
}

export async function getConsistencyDiffs(checkId: number, params: OpsQueryParams): Promise<ApiResponse<PageResult<ConsistencyDiff>>> {
  try {
    return await get<PageResult<ConsistencyDiff>>(`/ops/consistency/checks/${checkId}/diffs`, params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockConsistencyDiffs(checkId, params) };
  }
}

export function runConsistency(data: { check_type: CheckType; team_space_id?: number }): Promise<ApiResponse<{ check_id: number }>> {
  return post<{ check_id: number }>('/ops/consistency/run', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function fixConsistency(data: { check_id: number; diff_ids: number[] }): Promise<ApiResponse<{ job_id: number }>> {
  return post<{ job_id: number }>('/ops/consistency/fix', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

// ===================== D3 链路治愈 =====================

export async function getHealJobs(params: OpsQueryParams): Promise<ApiResponse<PageResult<HealJob>>> {
  try {
    return await get<PageResult<HealJob>>('/ops/heal/jobs', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockHealJobs(params) };
  }
}

export async function getHealJob(id: number): Promise<ApiResponse<HealJob>> {
  try {
    return await get<HealJob>(`/ops/heal/jobs/${id}`);
  } catch {
    const data = findMockHealJob(id);
    if (!data) return { code: 404, message: 'job not found', data: null as unknown as HealJob };
    return { code: 200, message: 'success', data };
  }
}

export async function getHealTargets(params: OpsQueryParams): Promise<ApiResponse<typeof mockHealTargetFiles>> {
  try {
    return await get<typeof mockHealTargetFiles>('/ops/heal/targets', params as unknown as Record<string, unknown>);
  } catch {
    const page = params.page ?? 1;
    const pageSize = params.pageSize ?? 10;
    return { code: 200, message: 'success', data: { list: mockHealTargetFiles, total: mockHealTargetFiles.length, page, pageSize } as unknown as typeof mockHealTargetFiles };
  }
}

export async function previewHeal(data: { job_type: HealJobType; filter: Record<string, unknown> }): Promise<ApiResponse<HealPreview>> {
  try {
    return await post<HealPreview>('/ops/heal/preview', data as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: mockHealPreview };
  }
}

export function batchHeal(data: {
  job_type: HealJobType;
  team_space_id: number;
  filter: Record<string, unknown>;
  ticket_id?: number;
}): Promise<ApiResponse<{ job_id: number }>> {
  return post<{ job_id: number }>('/ops/heal/batch', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function retryIndex(fileId: number): Promise<ApiResponse<{ job_id: number }>> {
  return post<{ job_id: number }>(`/ops/heal/retry-index`, { file_id: fileId }, { headers: idempotencyHeaders() });
}

export function retryParse(fileId: number): Promise<ApiResponse<{ job_id: number }>> {
  return post<{ job_id: number }>(`/ops/heal/retry-parse`, { file_id: fileId }, { headers: idempotencyHeaders() });
}

export function cancelHealJob(id: number): Promise<ApiResponse<void>> {
  return post<void>(`/ops/heal/jobs/${id}/cancel`, {}, { headers: idempotencyHeaders() });
}

// ===================== D4 生命周期 =====================

export async function getLifecyclePolicies(params: OpsQueryParams): Promise<ApiResponse<PageResult<LifecyclePolicy>>> {
  try {
    return await get<PageResult<LifecyclePolicy>>('/ops/lifecycle/policies', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockLifecyclePolicies(params) };
  }
}

export async function getColdCandidates(params: OpsQueryParams): Promise<ApiResponse<PageResult<ColdCandidate>>> {
  try {
    return await get<PageResult<ColdCandidate>>('/ops/lifecycle/cold-candidates', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockColdCandidates(params) };
  }
}

export async function getQuotaList(params: OpsQueryParams): Promise<ApiResponse<PageResult<TeamSpace>>> {
  try {
    return await get<PageResult<TeamSpace>>('/ops/lifecycle/quotas', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockQuotaList(params) };
  }
}

export async function getCapacity(): Promise<ApiResponse<CapacityData>> {
  try {
    return await get<CapacityData>('/ops/lifecycle/capacity');
  } catch {
    return { code: 200, message: 'success', data: mockCapacityData };
  }
}

export function createLifecyclePolicy(data: Partial<LifecyclePolicy>): Promise<ApiResponse<LifecyclePolicy>> {
  return post<LifecyclePolicy>('/ops/lifecycle/policies', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function archiveFiles(data: { file_ids: number[]; storage_class?: string }): Promise<ApiResponse<{ job_id: number }>> {
  return post<{ job_id: number }>('/ops/lifecycle/archive', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function restoreArchive(data: { file_ids: number[] }): Promise<ApiResponse<{ job_id: number }>> {
  return post<{ job_id: number }>('/ops/lifecycle/restore', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

// ===================== D5 应用配置 =====================

export async function getConfigList(type: string, params: OpsQueryParams): Promise<ApiResponse<PageResult<ConfigItem>>> {
  try {
    return await get<PageResult<ConfigItem>>(`/ops/config/items`, { ...params, type } as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockConfigItems(type, params) };
  }
}

export async function getConfigChanges(params: OpsQueryParams & { config_type?: string }): Promise<ApiResponse<PageResult<ConfigChange>>> {
  try {
    return await get<PageResult<ConfigChange>>('/ops/config/changes', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockConfigChanges(params) };
  }
}

export async function configImpact(changeId: number): Promise<ApiResponse<ConfigImpact>> {
  try {
    return await get<ConfigImpact>(`/ops/config/changes/${changeId}/impact`);
  } catch {
    return { code: 200, message: 'success', data: mockConfigImpact };
  }
}

export function configCanary(changeId: number, data: { canary_space_ids: number[]; validation_rule: string }): Promise<ApiResponse<void>> {
  return post<void>(`/ops/config/changes/${changeId}/canary`, data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function configPromote(changeId: number): Promise<ApiResponse<void>> {
  return post<void>(`/ops/config/changes/${changeId}/promote`, {}, { headers: idempotencyHeaders() });
}

export function configRollback(changeId: number): Promise<ApiResponse<void>> {
  return post<void>(`/ops/config/changes/${changeId}/rollback`, {}, { headers: idempotencyHeaders() });
}

/** 类型导出辅助（避免未使用警告） */
export const _ensureConfigChangesType = mockConfigChanges;

// ===================== D6 数据安全 =====================

export async function getStalePermissions(params: OpsQueryParams): Promise<ApiResponse<PageResult<StalePermission>>> {
  try {
    return await get<PageResult<StalePermission>>('/ops/security/stale-permissions', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockStalePermissions(params) };
  }
}

export async function getDownloadAnomalies(params: OpsQueryParams): Promise<ApiResponse<PageResult<DownloadAnomaly>>> {
  try {
    return await get<PageResult<DownloadAnomaly>>('/ops/security/download-anomalies', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockDownloadAnomalies(params) };
  }
}

export async function getSensitiveAccess(params: OpsQueryParams): Promise<ApiResponse<PageResult<SensitiveAccess>>> {
  try {
    return await get<PageResult<SensitiveAccess>>('/ops/security/sensitive-access', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockSensitiveAccess(params) };
  }
}

export async function getExportRequests(params: OpsQueryParams): Promise<ApiResponse<PageResult<ExportRequest>>> {
  try {
    return await get<PageResult<ExportRequest>>('/ops/security/exports', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockExportRequests(params) };
  }
}

export function cleanPermissions(data: { member_ids: number[] }): Promise<ApiResponse<void>> {
  return post<void>('/ops/security/clean-permissions', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function applyExport(data: { team_space_id: number; export_scope: Record<string, unknown> }): Promise<ApiResponse<{ export_id: number; ticket_id: number }>> {
  return post<{ export_id: number; ticket_id: number }>('/ops/security/exports', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function approveExport(id: number, data: { approved: boolean; comment?: string }): Promise<ApiResponse<void>> {
  return post<void>(`/ops/security/exports/${id}/approve`, data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

// ===================== D7 空间报告 =====================

export async function getReports(params: OpsQueryParams & { report_type?: string }): Promise<ApiResponse<PageResult<SpaceReport>>> {
  try {
    return await get<PageResult<SpaceReport>>('/ops/reports', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockReports(params) };
  }
}

export async function getReport(id: number): Promise<ApiResponse<SpaceReport>> {
  try {
    return await get<SpaceReport>(`/ops/reports/${id}`);
  } catch {
    const data = findMockReport(id);
    if (!data) return { code: 404, message: 'report not found', data: null as unknown as SpaceReport };
    return { code: 200, message: 'success', data };
  }
}

export async function getSubscriptions(): Promise<ApiResponse<ReportSubscription[]>> {
  try {
    return await get<ReportSubscription[]>('/ops/reports/subscriptions');
  } catch {
    return { code: 200, message: 'success', data: mockReportSubscriptions };
  }
}

export function applySuggestion(suggestionId: number): Promise<ApiResponse<{ ticket_id: number }>> {
  return post<{ ticket_id: number }>(`/ops/reports/suggestions/${suggestionId}/apply`, {}, { headers: idempotencyHeaders() });
}

export function createSubscription(data: { report_types: string; team_space_id?: number }): Promise<ApiResponse<ReportSubscription>> {
  return post<ReportSubscription>('/ops/reports/subscriptions', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}

export function deleteSubscription(id: number): Promise<ApiResponse<void>> {
  return post<void>(`/ops/reports/subscriptions/${id}/delete`, {}, { headers: idempotencyHeaders() });
}

// ===================== 运维工单 =====================

export async function getTickets(params: OpsQueryParams): Promise<ApiResponse<PageResult<OpsTicket>>> {
  try {
    return await get<PageResult<OpsTicket>>('/ops/tickets', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: getMockTickets(params) };
  }
}

export function createTicket(data: Partial<OpsTicket>): Promise<ApiResponse<OpsTicket>> {
  return post<OpsTicket>('/ops/tickets', data as unknown as Record<string, unknown>, { headers: idempotencyHeaders() });
}
