/**
 * 文件评审 API 服务
 * 对应后端 workflow-service FileReviewService（端口 8094）：
 * - POST /api/workflow/file-reviews/submit（提交评审）
 * - POST /api/workflow/file-reviews/{instanceId}/decision（审批决策）
 * - GET  /api/workflow/file-reviews/{fileId}/status（查询评审状态）
 * - GET  /api/workflow/file-reviews/{instanceId}/opinions（评审意见列表）
 *
 * 所有方法均通过 try-catch 失败降级到 Mock，保证页面不阻塞。
 */
import { post, get } from '@/utils/request';
import type { ApiResponse } from '@/types';
import { ReviewStatus, ReviewDecision } from '@/types';
import type {
  ReviewInstance,
  ReviewOpinion,
  ApprovalTimelineNode,
} from '@/types';
import {
  getMockReviewInstanceByFileId,
  getMockReviewOpinionsByInstance,
  submitMockReview,
  processMockDecision,
  getMockApprovalTimelineByTaskId,
} from '@/mock/fileReview';

/**
 * 提交文件评审
 * 后端：POST /api/workflow/file-reviews/submit
 * @param fileId 文件 ID
 * @param comment 评审备注
 */
export async function submitFileReview(
  fileId: string,
  comment: string,
): Promise<ApiResponse<ReviewInstance>> {
  try {
    return await post<ReviewInstance>('/workflow/file-reviews/submit', {
      fileId,
      comment,
    });
  } catch {
    // 降级到 Mock：本地生成评审实例
    return {
      code: 200,
      message: 'success',
      data: submitMockReview(fileId, comment),
    };
  }
}

/**
 * 处理审批决策
 * 后端：POST /api/workflow/file-reviews/{instanceId}/decision
 * @param instanceId 评审实例 ID
 * @param decision 决策结果（APPROVE / REJECT）
 * @param comment 评审意见
 */
export async function processDecision(
  instanceId: string,
  decision: ReviewDecision,
  comment: string,
): Promise<ApiResponse<ReviewOpinion>> {
  try {
    return await post<ReviewOpinion>(
      `/workflow/file-reviews/${instanceId}/decision`,
      { decision, comment },
    );
  } catch {
    return {
      code: 200,
      message: 'success',
      data: processMockDecision(instanceId, decision, comment),
    };
  }
}

/**
 * 查询文件评审状态
 * 后端：GET /api/workflow/file-reviews/{fileId}/status
 * @param fileId 文件 ID
 */
export async function getReviewStatus(
  fileId: string,
): Promise<ApiResponse<ReviewInstance>> {
  try {
    return await get<ReviewInstance>(
      `/workflow/file-reviews/${fileId}/status`,
    );
  } catch {
    return {
      code: 200,
      message: 'success',
      data: getMockReviewInstanceByFileId(fileId),
    };
  }
}

/**
 * 获取评审意见列表
 * 后端：GET /api/workflow/file-reviews/{instanceId}/opinions
 * @param instanceId 评审实例 ID
 */
export async function getReviewOpinions(
  instanceId: string,
): Promise<ApiResponse<ReviewOpinion[]>> {
  try {
    return await get<ReviewOpinion[]>(
      `/workflow/file-reviews/${instanceId}/opinions`,
    );
  } catch {
    return {
      code: 200,
      message: 'success',
      data: getMockReviewOpinionsByInstance(instanceId),
    };
  }
}

/**
 * 获取任务审批进度时间轴
 * 后端目前未提供独立接口，使用 mock 数据；
 * 当后端 workflow-service 后续提供 /workflow/tasks/{taskId}/approval-timeline 时，
 * 可在此处追加 try 块切换为真实请求。
 * @param taskId 任务 ID
 */
export async function getApprovalTimeline(
  taskId: string,
): Promise<ApiResponse<ApprovalTimelineNode[]>> {
  // 当前阶段直接返回 Mock，与已通过的「降级 Mock」语义一致
  return {
    code: 200,
    message: 'success',
    data: getMockApprovalTimelineByTaskId(taskId),
  };
}

/** 便捷导出：评审状态枚举与决策枚举（避免上层重复 import） */
export { ReviewStatus, ReviewDecision };
