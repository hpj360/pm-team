/**
 * 分析相关 API 服务
 * - 分析任务（CRUD）
 * - YARA 规则列表 + 扫描
 * - NER 实体识别
 */
import { get, post } from '@/utils/request';
import type {
  AnalyzeTask,
  AnalyzeResult,
  AnalyzeStatistics,
  AnalyzeType,
  ApiResponse,
  YaraRule,
  YaraScanResult,
  NerResult,
  IocInfo,
} from '@/types';
import { mockYaraRules, generateMockYaraScanResult } from '@/mock/yara';
import { generateMockNerResult } from '@/mock/ner';
import { generateMockAnalyzeResult } from '@/mock/analyze';

/**
 * 创建分析任务
 */
export function createAnalyzeTask(
  fileId: string,
  type: AnalyzeType,
): Promise<ApiResponse<AnalyzeTask>> {
  return post<AnalyzeTask>('/analyze', { fileId, type });
}

/**
 * 获取分析任务列表
 */
export function getAnalyzeTasks(params?: {
  fileId?: string;
  status?: string;
  page?: number;
  pageSize?: number;
}): Promise<ApiResponse<{ list: AnalyzeTask[]; total: number }>> {
  return get<{ list: AnalyzeTask[]; total: number }>('/analyze/tasks', params);
}

/**
 * 获取分析任务详情
 */
export function getAnalyzeTaskDetail(taskId: string): Promise<ApiResponse<AnalyzeTask>> {
  return get<AnalyzeTask>(`/analyze/tasks/${taskId}`);
}

/**
 * 获取分析结果
 */
export async function getAnalyzeResult(
  taskId: string,
  fileId?: string,
): Promise<ApiResponse<AnalyzeResult>> {
  try {
    return await get<AnalyzeResult>(`/analyze/tasks/${taskId}/result`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockAnalyzeResult(taskId, fileId ?? taskId),
    };
  }
}

/**
 * 取消分析任务
 */
export function cancelAnalyzeTask(taskId: string): Promise<ApiResponse<void>> {
  return post<void>(`/analyze/tasks/${taskId}/cancel`);
}

/**
 * 获取分析统计信息
 */
export function getAnalyzeStatistics(): Promise<ApiResponse<AnalyzeStatistics>> {
  return get<AnalyzeStatistics>('/analyze/statistics');
}

/**
 * 获取分析类型列表
 */
export function getAnalyzeTypes(): Promise<
  ApiResponse<{ type: AnalyzeType; name: string; description: string }[]>
> {
  return get<{ type: AnalyzeType; name: string; description: string }[]>('/analyze/types');
}

/**
 * 导出分析报告 URL
 */
export function exportAnalyzeReport(
  taskId: string,
  format: 'pdf' | 'html' | 'json',
): string {
  return `/api/analyze/tasks/${taskId}/export?format=${format}`;
}

/* ===== YARA ===== */

/**
 * 列出 YARA 规则
 */
export async function listYaraRules(): Promise<ApiResponse<YaraRule[]>> {
  try {
    return await get<YaraRule[]>('/analyze/yara/rules');
  } catch {
    return { code: 200, message: 'success', data: mockYaraRules };
  }
}

/**
 * 扫描文件（YARA）
 */
export async function scanFile(
  fileId: string,
  fileName?: string,
): Promise<ApiResponse<YaraScanResult>> {
  try {
    return await post<YaraScanResult>(`/analyze/yara/scan/${fileId}`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockYaraScanResult(fileId, fileName ?? fileId, ['yr0001', 'yr0004']),
    };
  }
}

/* ===== NER ===== */

/**
 * 获取 NER 结果
 */
export async function getNerResult(
  fileId: string,
  fileName?: string,
): Promise<ApiResponse<NerResult>> {
  try {
    return await get<NerResult>(`/analyze/ner/${fileId}`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockNerResult(fileId, fileName ?? fileId),
    };
  }
}

/**
 * 触发文件解析（包含文本提取 + YARA + NER 一站式）
 */
export async function parseFile(
  fileId: string,
): Promise<ApiResponse<AnalyzeResult>> {
  try {
    return await post<AnalyzeResult>(`/analyze/parse/${fileId}`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockAnalyzeResult(fileId, fileId),
    };
  }
}

/**
 * 获取 IOC 列表（兼容旧版）
 */
export function getIocList(params?: {
  type?: string;
  fileId?: string;
  page?: number;
  pageSize?: number;
}): Promise<ApiResponse<{ list: IocInfo[]; total: number }>> {
  return get<{ list: IocInfo[]; total: number }>('/analyze/iocs', params);
}
