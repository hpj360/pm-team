/**
 * 分析相关API服务
 */

import { get, post } from '@/utils/request';
import type {
  AnalyzeTask,
  AnalyzeResult,
  AnalyzeStatistics,
  AnalyzeType,
  ApiResponse,
} from '@/types';

/**
 * 创建分析任务
 */
export function createAnalyzeTask(
  fileId: string,
  type: AnalyzeType
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
export function getAnalyzeTaskDetail(
  taskId: string
): Promise<ApiResponse<AnalyzeTask>> {
  return get<AnalyzeTask>(`/analyze/tasks/${taskId}`);
}

/**
 * 获取分析结果
 */
export function getAnalyzeResult(
  taskId: string
): Promise<ApiResponse<AnalyzeResult>> {
  return get<AnalyzeResult>(`/analyze/tasks/${taskId}/result`);
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
export function getAnalyzeTypes(): Promise<ApiResponse<{ type: AnalyzeType; name: string; description: string }[]>> {
  return get<{ type: AnalyzeType; name: string; description: string }[]>('/analyze/types');
}

/**
 * 导出分析报告
 */
export function exportAnalyzeReport(
  taskId: string,
  format: 'pdf' | 'html' | 'json'
): string {
  return `/api/analyze/tasks/${taskId}/export?format=${format}`;
}

/**
 * 获取IOC列表
 */
export function getIocList(params?: {
  type?: string;
  fileId?: string;
  page?: number;
  pageSize?: number;
}): Promise<ApiResponse<{ list: unknown[]; total: number }>> {
  return get<{ list: unknown[]; total: number }>('/analyze/iocs', params);
}
