/**
 * 应用运维 react-query Hooks
 * 数据获取与变更，含治愈任务进度轮询
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as opsApi from '@/services/ops';
import type { OpsQueryParams, CheckType, HealJobType, SpaceLifecycleStatus } from '@/types/ops';

/** 查询键前缀 */
const K = ['ops'] as const;

// ===== D1 空间台账 =====

/** 空间列表 */
export const useSpaces = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'spaces', params],
    queryFn: () => opsApi.getSpaces(params),
  });

/** 空间详情 */
export const useSpace = (id: number | undefined) =>
  useQuery({
    queryKey: [...K, 'space', id],
    queryFn: () => opsApi.getSpace(id!),
    enabled: !!id,
  });

/** 空间健康分（缓存5min，对齐上游§13.8大空间性能） */
export const useSpaceHealth = (id: number | undefined) =>
  useQuery({
    queryKey: [...K, 'space', id, 'health'],
    queryFn: () => opsApi.getSpaceHealth(id!),
    enabled: !!id,
    staleTime: 5 * 60 * 1000,
  });

/** 空间成员 */
export const useSpaceMembers = (id: number | undefined) =>
  useQuery({
    queryKey: [...K, 'space', id, 'members'],
    queryFn: () => opsApi.getSpaceMembers(id!),
    enabled: !!id,
  });

/** 配额变更历史 */
export const useSpaceQuotaLog = (id: number | undefined, params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'space', id, 'quota-log', params],
    queryFn: () => opsApi.getSpaceQuotaLog(id!, params),
    enabled: !!id,
  });

/** 空间操作事件 */
export const useSpaceEvents = (id: number | undefined, params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'space', id, 'events', params],
    queryFn: () => opsApi.getSpaceEvents(id!, params),
    enabled: !!id,
  });

/** 空间状态变更 */
export const usePatchSpaceStatus = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: number; status: SpaceLifecycleStatus; version: number }) =>
      opsApi.patchSpaceStatus(args.id, { status: args.status, version: args.version }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'spaces'] }),
  });
};

/** 负责人移交 */
export const useTransferSpace = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: number; new_owner_id: number }) =>
      opsApi.transferSpaceOwner(args.id, { new_owner_id: args.new_owner_id }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'spaces'] }),
  });
};

// ===== D2 一致性对账 =====

/** 对账结果列表 */
export const useConsistencyResults = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'consistency', params],
    queryFn: () => opsApi.getConsistencyResults(params),
  });

/** 不一致明细 */
export const useConsistencyDiffs = (checkId: number | undefined, params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'consistency', checkId, 'diffs', params],
    queryFn: () => opsApi.getConsistencyDiffs(checkId!, params),
    enabled: !!checkId,
  });

/** 触发对账 */
export const useRunConsistency = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { check_type: CheckType; team_space_id?: number }) =>
      opsApi.runConsistency(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'consistency'] }),
  });
};

/** 一键修复 */
export const useFixConsistency = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { check_id: number; diff_ids: number[] }) =>
      opsApi.fixConsistency(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'consistency'] }),
  });
};

// ===== D3 链路治愈 =====

/** 治愈任务列表 */
export const useHealJobs = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'heal-jobs', params],
    queryFn: () => opsApi.getHealJobs(params),
  });

/** 治愈任务详情+进度（运行中2s轮询，结束停止） */
export const useHealJobProgress = (id: number | undefined) =>
  useQuery({
    queryKey: [...K, 'heal-job', id],
    queryFn: () => opsApi.getHealJob(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = query.state.data?.data?.status;
      return status === 0 || status === 1 ? 2000 : false;
    },
  });

/** 影响预览 */
export const useHealPreview = () =>
  useMutation({
    mutationFn: (data: { job_type: HealJobType; filter: Record<string, unknown> }) =>
      opsApi.previewHeal(data),
  });

/** 批量治愈（幂等头由 service 处理） */
export const useBatchHeal = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: opsApi.batchHeal,
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'heal-jobs'] }),
  });
};

/** 单文件重索引（免审批） */
export const useRetryIndex = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (fileId: number) => opsApi.retryIndex(fileId),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'heal-jobs'] }),
  });
};

/** 单文件重解析（免审批） */
export const useRetryParse = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (fileId: number) => opsApi.retryParse(fileId),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'heal-jobs'] }),
  });
};

/** 取消治愈任务 */
export const useCancelHealJob = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => opsApi.cancelHealJob(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'heal-jobs'] }),
  });
};

// ===== D4 生命周期 =====

export const useLifecyclePolicies = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'lifecycle-policies', params],
    queryFn: () => opsApi.getLifecyclePolicies(params),
  });

export const useColdCandidates = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'cold-candidates', params],
    queryFn: () => opsApi.getColdCandidates(params),
  });

export const useQuotaList = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'quota', params],
    queryFn: () => opsApi.getQuotaList(params),
  });

export const useCapacity = () =>
  useQuery({
    queryKey: [...K, 'capacity'],
    queryFn: () => opsApi.getCapacity(),
    staleTime: 5 * 60 * 1000,
  });

export const useCreateLifecyclePolicy = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: opsApi.createLifecyclePolicy,
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'lifecycle-policies'] }),
  });
};

export const useArchiveFiles = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: opsApi.archiveFiles,
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K] }),
  });
};

export const useRestoreArchive = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: opsApi.restoreArchive,
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K] }),
  });
};

// ===== D5 应用配置 =====

export const useConfigList = (type: string, params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'config', type, params],
    queryFn: () => opsApi.getConfigList(type, params),
  });

export const useConfigChanges = (params: OpsQueryParams & { config_type?: string }) =>
  useQuery({
    queryKey: [...K, 'config-changes', params],
    queryFn: () => opsApi.getConfigChanges(params),
  });

export const useConfigImpact = () =>
  useMutation({ mutationFn: (changeId: number) => opsApi.configImpact(changeId) });

export const useConfigCanary = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { changeId: number; canary_space_ids: number[]; validation_rule?: string }) =>
      opsApi.configCanary(args.changeId, {
        canary_space_ids: args.canary_space_ids,
        validation_rule: args.validation_rule ?? '',
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'config'] }),
  });
};

export const useConfigPromote = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (changeId: number) => opsApi.configPromote(changeId),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'config'] }),
  });
};

export const useConfigRollback = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (changeId: number) => opsApi.configRollback(changeId),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'config'] }),
  });
};

// ===== D6 数据安全 =====

export const useStalePermissions = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'stale-permissions', params],
    queryFn: () => opsApi.getStalePermissions(params),
  });

export const useDownloadAnomalies = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'download-anomalies', params],
    queryFn: () => opsApi.getDownloadAnomalies(params),
  });

export const useSensitiveAccess = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'sensitive-access', params],
    queryFn: () => opsApi.getSensitiveAccess(params),
  });

export const useExportRequests = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'export-requests', params],
    queryFn: () => opsApi.getExportRequests(params),
  });

export const useCleanPermissions = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { member_ids: number[] }) => opsApi.cleanPermissions(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'stale-permissions'] }),
  });
};

export const useApplyExport = () =>
  useMutation({
    mutationFn: (data: { team_space_id: number; export_scope: Record<string, unknown> }) =>
      opsApi.applyExport(data),
  });

export const useApproveExport = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: number; approved: boolean; comment?: string }) =>
      opsApi.approveExport(args.id, { approved: args.approved, comment: args.comment }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'export-requests'] }),
  });
};

// ===== D7 空间报告 =====

export const useReports = (params: OpsQueryParams & { report_type?: string }) =>
  useQuery({
    queryKey: [...K, 'reports', params],
    queryFn: () => opsApi.getReports(params),
  });

export const useReport = (id: number | undefined) =>
  useQuery({
    queryKey: [...K, 'report', id],
    queryFn: () => opsApi.getReport(id!),
    enabled: !!id,
  });

export const useReportSubscriptions = () =>
  useQuery({
    queryKey: [...K, 'report-subscriptions'],
    queryFn: () => opsApi.getSubscriptions(),
  });

export const useApplySuggestion = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (suggestionId: number) => opsApi.applySuggestion(suggestionId),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'reports'] }),
  });
};

export const useCreateSubscription = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { report_types: string; team_space_id?: number }) =>
      opsApi.createSubscription(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'report-subscriptions'] }),
  });
};

export const useDeleteSubscription = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => opsApi.deleteSubscription(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'report-subscriptions'] }),
  });
};

// ===== 运维工单 =====

export const useOpsTickets = (params: OpsQueryParams) =>
  useQuery({
    queryKey: [...K, 'tickets', params],
    queryFn: () => opsApi.getTickets(params),
  });

export const useCreateTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: opsApi.createTicket,
    onSuccess: () => qc.invalidateQueries({ queryKey: [...K, 'tickets'] }),
  });
};
