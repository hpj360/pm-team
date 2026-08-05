/**
 * 沙箱动态分析 API 服务（V5.2）
 * 对应后端 analyze-service（端口 8084）DynamicAnalysisController
 * 接口前缀：/api/analyze/dynamic
 *
 * 降级策略：后端 Cuckoo 沙箱不可用时返回降级任务（degraded=true）；
 * 前端请求失败时回退到 Mock 数据。
 */
import { get, post } from '@/utils/request';
import type { ApiResponse, DynamicAnalysisTask, DynamicReport } from '@/types';
import {
  mockDynamicTasks,
  getMockDynamicTaskById,
  getMockDynamicReport,
  mockSubmitDynamicAnalysis,
} from '@/mock';

/**
 * 提交动态分析任务
 * POST /api/analyze/dynamic/submit?fileId=xxx
 *
 * @param fileId 文件ID
 * @returns 平台侧动态分析任务ID
 */
export async function submitDynamicAnalysis(
  fileId: number,
): Promise<ApiResponse<string>> {
  try {
    return await post<string>('/analyze/dynamic/submit', undefined, {
      params: { fileId },
    });
  } catch {
    const taskId = mockSubmitDynamicAnalysis(fileId);
    return { code: 200, message: 'success', data: taskId };
  }
}

/**
 * 获取动态分析任务详情与状态
 * GET /api/analyze/dynamic/{taskId}
 *
 * @param taskId 任务ID
 * @returns 任务对象
 */
export async function getDynamicTask(
  taskId: string,
): Promise<ApiResponse<DynamicAnalysisTask>> {
  try {
    return await get<DynamicAnalysisTask>(`/analyze/dynamic/${taskId}`);
  } catch {
    const data = getMockDynamicTaskById(taskId) ?? mockDynamicTasks[0];
    return { code: 200, message: 'success', data };
  }
}

/**
 * 获取动态分析报告（含行为指标、ATT&CK 映射、IOC、STIX 对象）
 * GET /api/analyze/dynamic/{taskId}/report
 *
 * @param taskId 任务ID
 * @returns 报告 VO
 */
export async function getDynamicReport(
  taskId: string,
): Promise<ApiResponse<DynamicReport>> {
  try {
    return await get<DynamicReport>(`/analyze/dynamic/${taskId}/report`);
  } catch {
    return { code: 200, message: 'success', data: getMockDynamicReport(taskId) };
  }
}

/**
 * 触发任务轮询（手动触发状态推进）
 * POST /api/analyze/dynamic/{taskId}/poll
 *
 * @param taskId 任务ID
 * @returns 当前状态
 */
export async function pollDynamicTask(
  taskId: string,
): Promise<ApiResponse<string>> {
  try {
    return await post<string>(`/analyze/dynamic/${taskId}/poll`);
  } catch {
    const task = getMockDynamicTaskById(taskId);
    return { code: 200, message: 'success', data: task?.status ?? 'PARSED' };
  }
}

/**
 * 列出全部动态分析任务
 * GET /api/analyze/dynamic
 *
 * @returns 任务列表
 */
export async function listDynamicTasks(): Promise<ApiResponse<DynamicAnalysisTask[]>> {
  try {
    return await get<DynamicAnalysisTask[]>('/analyze/dynamic');
  } catch {
    return { code: 200, message: 'success', data: mockDynamicTasks };
  }
}
