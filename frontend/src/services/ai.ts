/**
 * AI 分析相关 API 服务
 * - 威胁摘要（生成 / 获取）
 * - 攻击链推理
 * - 自然语言搜索
 * - 报告草稿（生成 / 获取）
 *
 * 后端 ai-service 运行在端口 8093，前端通过网关代理访问 `/api/ai/*`。
 * 调用失败时降级返回 Mock 数据，保证页面不阻塞。
 */
import { get, post } from '@/utils/request';
import type {
  ApiResponse,
  ThreatSummary,
  AiAttackChain,
  NlSearchResult,
  ReportDraft,
  GenerateReportDraftPayload,
} from '@/types';
import {
  generateMockThreatSummary,
  generateMockAttackChain,
  generateMockNlSearchResult,
  generateMockReportDraft,
} from '@/mock/ai';

/**
 * 生成威胁摘要（POST /api/ai/threat-summary/generate）
 * @param fileId 文件 ID
 */
export async function generateThreatSummary(
  fileId: string,
): Promise<ApiResponse<ThreatSummary>> {
  try {
    return await post<ThreatSummary>('/ai/threat-summary/generate', { fileId });
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockThreatSummary(fileId),
    };
  }
}

/**
 * 获取已生成的威胁摘要（GET /api/ai/threat-summary/{fileId}）
 * @param fileId 文件 ID
 */
export async function getThreatSummary(
  fileId: string,
): Promise<ApiResponse<ThreatSummary>> {
  try {
    return await get<ThreatSummary>(`/ai/threat-summary/${fileId}`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockThreatSummary(fileId),
    };
  }
}

/**
 * 攻击链推理（POST /api/ai/attack-chain/infer）
 * @param fileId 文件 ID
 */
export async function inferAttackChain(
  fileId: string,
): Promise<ApiResponse<AiAttackChain>> {
  try {
    return await post<AiAttackChain>('/ai/attack-chain/infer', { fileId });
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockAttackChain(fileId),
    };
  }
}

/**
 * 自然语言搜索（POST /api/ai/nlsearch）
 * @param query 自然语言查询语句
 */
export async function nlSearch(
  query: string,
): Promise<ApiResponse<NlSearchResult>> {
  try {
    return await post<NlSearchResult>('/ai/nlsearch', { query });
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockNlSearchResult(query),
    };
  }
}

/**
 * 生成报告草稿（POST /api/ai/report-draft/generate）
 * @param payload 报告草稿请求载荷
 */
export async function generateReportDraft(
  payload: GenerateReportDraftPayload,
): Promise<ApiResponse<ReportDraft>> {
  try {
    return await post<ReportDraft>(
      '/ai/report-draft/generate',
      payload as unknown as Record<string, unknown>,
    );
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockReportDraft(payload.reportId),
    };
  }
}

/**
 * 获取已生成的报告草稿（GET /api/ai/report-draft/{reportId}）
 * @param reportId 报告 ID
 */
export async function getReportDraft(
  reportId: string,
): Promise<ApiResponse<ReportDraft>> {
  try {
    return await get<ReportDraft>(`/ai/report-draft/${reportId}`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockReportDraft(reportId),
    };
  }
}
