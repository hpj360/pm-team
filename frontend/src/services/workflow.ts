/**
 * 工作流相关 API 服务
 * - 工作流定义 CRUD：列表 / 详情 / 保存（新建+更新）/ 启停 / 删除
 * 对应后端 workflow-service（端口 8094）的 REST 端点：
 *   POST   /api/workflow/definitions          保存工作流
 *   GET    /api/workflow/definitions          列表
 *   GET    /api/workflow/definitions/{id}     详情
 *   PUT    /api/workflow/definitions/{id}/toggle  启用/禁用
 *   DELETE /api/workflow/definitions/{id}     删除
 * 后端不可达时降级到 Mock 数据
 */
import { get, post, put, del } from '@/utils/request';
import type {
  ApiResponse,
  WorkflowDefinition,
  WorkflowDefinitionPayload,
} from '@/types';
import {
  listMockWorkflows,
  getMockWorkflowById,
  saveMockWorkflow,
  toggleMockWorkflow,
  deleteMockWorkflow,
} from '@/mock/workflow';

/* ===================== 工作流定义 CRUD ===================== */

/**
 * 获取工作流定义列表
 * GET /api/workflow/definitions
 */
export async function listWorkflowDefinitions(): Promise<
  ApiResponse<WorkflowDefinition[]>
> {
  try {
    return await get<WorkflowDefinition[]>('/workflow/definitions');
  } catch {
    return {
      code: 200,
      message: 'success',
      data: listMockWorkflows(),
    };
  }
}

/**
 * 获取工作流定义详情
 * GET /api/workflow/definitions/{id}
 */
export async function getWorkflowDefinition(
  id: string,
): Promise<ApiResponse<WorkflowDefinition>> {
  try {
    return await get<WorkflowDefinition>(`/workflow/definitions/${id}`);
  } catch {
    const data = getMockWorkflowById(id);
    if (!data) {
      return {
        code: 404,
        message: '工作流不存在',
        data: undefined as unknown as WorkflowDefinition,
      };
    }
    return { code: 200, message: 'success', data };
  }
}

/**
 * 保存工作流定义（新建或更新）
 * POST /api/workflow/definitions
 * 当 payload.id 存在时为更新，否则为新建
 */
export async function saveWorkflowDefinition(
  dto: WorkflowDefinitionPayload,
): Promise<ApiResponse<WorkflowDefinition>> {
  try {
    return await post<WorkflowDefinition>(
      '/workflow/definitions',
      dto as unknown as Record<string, unknown>,
    );
  } catch {
    const data = saveMockWorkflow(dto);
    return { code: 200, message: 'success', data };
  }
}

/**
 * 启用/禁用工作流定义
 * PUT /api/workflow/definitions/{id}/toggle
 */
export async function toggleWorkflowDefinition(
  id: string,
): Promise<ApiResponse<void>> {
  try {
    return await put<void>(`/workflow/definitions/${id}/toggle`);
  } catch {
    toggleMockWorkflow(id);
    return { code: 200, message: 'success', data: undefined };
  }
}

/**
 * 删除工作流定义
 * DELETE /api/workflow/definitions/{id}
 */
export async function deleteWorkflowDefinition(
  id: string,
): Promise<ApiResponse<void>> {
  try {
    return await del<void>(`/workflow/definitions/${id}`);
  } catch {
    deleteMockWorkflow(id);
    return { code: 200, message: 'success', data: undefined };
  }
}

export default {
  listWorkflowDefinitions,
  getWorkflowDefinition,
  saveWorkflowDefinition,
  toggleWorkflowDefinition,
  deleteWorkflowDefinition,
};
