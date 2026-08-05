/**
 * Mock 数据 - 文件评审（评审实例 + 评审意见）
 * 对应后端 workflow-service FileReviewService（端口 8094）
 *
 * 数据结构：
 * - 一个 fileId 对应一个 ReviewInstance（同一文件不重复发起评审）
 * - 一个 ReviewInstance 关联多条 ReviewOpinion（按审批节点顺序）
 *
 * 提供的查询/操作函数（同步实现，供 service 层降级使用）：
 * - getMockReviewInstanceByFileId(fileId)
 * - getMockReviewOpinionsByInstance(instanceId)
 * - submitMockReview(fileId, comment)
 * - processMockDecision(instanceId, decision, comment)
 * - getMockApprovalTimelineByTaskId(taskId)
 */
import { ReviewStatus, ReviewDecision } from '@/types';
import type {
  ReviewInstance,
  ReviewOpinion,
  ApprovalTimelineNode,
} from '@/types';

/** 当前登录用户名（mock，用于判断当前用户是否是审批人） */
export const MOCK_CURRENT_USER = '王浩然';

/** 评审实例 Mock 列表 */
export const mockReviewInstances: ReviewInstance[] = [
  {
    instanceId: 'rv_inst_001',
    fileId: 'f0001',
    status: ReviewStatus.APPROVING,
    currentNode: '复审',
    submitter: '陈思齐',
    submittedAt: '2026-07-20T09:30:00Z',
    currentReviewers: [MOCK_CURRENT_USER, '林浩'],
  },
  {
    instanceId: 'rv_inst_002',
    fileId: 'f0002',
    status: ReviewStatus.APPROVED,
    currentNode: '终审',
    submitter: '王浩然',
    submittedAt: '2026-07-05T14:00:00Z',
    completedAt: '2026-07-08T16:30:00Z',
    currentReviewers: [],
  },
  {
    instanceId: 'rv_inst_003',
    fileId: 'f0003',
    status: ReviewStatus.REJECTED,
    currentNode: '初审',
    submitter: '刘晓东',
    submittedAt: '2026-07-10T10:00:00Z',
    completedAt: '2026-07-11T11:20:00Z',
    currentReviewers: [],
  },
  {
    instanceId: 'rv_inst_004',
    fileId: 'f0004',
    status: ReviewStatus.PENDING,
    submitter: '',
    submittedAt: '',
    currentReviewers: [],
  },
];

/** 评审意见 Mock 列表 */
export const mockReviewOpinions: ReviewOpinion[] = [
  // rv_inst_001（审批中）：初审通过
  {
    opinionId: 'op_001',
    instanceId: 'rv_inst_001',
    nodeName: '初审',
    reviewer: '林浩',
    decision: ReviewDecision.APPROVE,
    comment: '文件元数据完整，初步判定为有效证据，建议进入复审。',
    createdAt: '2026-07-21T10:00:00Z',
  },
  // rv_inst_002（已通过）：初审 + 复审 + 终审
  {
    opinionId: 'op_002',
    instanceId: 'rv_inst_002',
    nodeName: '初审',
    reviewer: '陈思齐',
    decision: ReviewDecision.APPROVE,
    comment: '攻击链推理结果与情报库匹配，证据链可信。',
    createdAt: '2026-07-06T09:00:00Z',
  },
  {
    opinionId: 'op_003',
    instanceId: 'rv_inst_002',
    nodeName: '复审',
    reviewer: '王浩然',
    decision: ReviewDecision.APPROVE,
    comment: '复核无误，可纳入红方作战成果库。',
    createdAt: '2026-07-07T15:30:00Z',
  },
  {
    opinionId: 'op_004',
    instanceId: 'rv_inst_002',
    nodeName: '终审',
    reviewer: '林浩',
    decision: ReviewDecision.APPROVE,
    comment: '同意归档。',
    createdAt: '2026-07-08T16:30:00Z',
  },
  // rv_inst_003（已驳回）：初审驳回
  {
    opinionId: 'op_005',
    instanceId: 'rv_inst_003',
    nodeName: '初审',
    reviewer: '王浩然',
    decision: ReviewDecision.REJECT,
    comment: '文件来源不可信，缺少采集链路记录，需补充证据后重新提交。',
    createdAt: '2026-07-11T11:20:00Z',
  },
];

/**
 * 任务审批进度时间轴 Mock（按 taskId 索引）
 * 用于任务详情页「审批进度」Card
 */
export const mockApprovalTimelines: Record<string, ApprovalTimelineNode[]> = {
  task_001: [
    {
      nodeId: 'n1',
      nodeName: '初审',
      reviewer: '陈思齐',
      status: 'approved',
      handledAt: '2026-05-05T10:00:00Z',
      comment: '任务范围明确，资料齐备。',
    },
    {
      nodeId: 'n2',
      nodeName: '复审',
      reviewer: '王浩然',
      status: 'approved',
      handledAt: '2026-05-10T14:00:00Z',
      comment: '通过，进入终审。',
    },
    {
      nodeId: 'n3',
      nodeName: '终审',
      reviewer: '林浩',
      status: 'approved',
      handledAt: '2026-05-14T18:00:00Z',
      comment: '归档完成。',
    },
  ],
  task_003: [
    {
      nodeId: 'n1',
      nodeName: '初审',
      reviewer: '王浩然',
      status: 'approved',
      handledAt: '2026-06-11T09:00:00Z',
      comment: '横向路径合理。',
    },
    {
      nodeId: 'n2',
      nodeName: '复审',
      reviewer: '林浩',
      status: 'processing',
    },
    {
      nodeId: 'n3',
      nodeName: '终审',
      reviewer: '陈思齐',
      status: 'pending',
    },
  ],
  task_006: [
    {
      nodeId: 'n1',
      nodeName: '初审',
      reviewer: '王浩然',
      status: 'pending',
    },
    {
      nodeId: 'n2',
      nodeName: '终审',
      reviewer: '林浩',
      status: 'pending',
    },
  ],
};

/** 按文件 ID 获取评审实例（未找到时返回未提交状态的实例） */
export function getMockReviewInstanceByFileId(fileId: string): ReviewInstance {
  const inst = mockReviewInstances.find((i) => i.fileId === fileId);
  if (inst) return inst;
  // 默认返回一个 PENDING 实例（前端展示「提交评审」按钮）
  return {
    instanceId: `rv_inst_pending_${fileId}`,
    fileId,
    status: ReviewStatus.PENDING,
    submitter: '',
    submittedAt: '',
    currentReviewers: [],
  };
}

/** 按评审实例 ID 获取评审意见列表 */
export function getMockReviewOpinionsByInstance(instanceId: string): ReviewOpinion[] {
  return mockReviewOpinions.filter((o) => o.instanceId === instanceId);
}

/** 提交评审（Mock：将实例状态改为 APPROVING，生成 instanceId） */
export function submitMockReview(fileId: string, comment: string): ReviewInstance {
  const now = new Date().toISOString();
  const instance: ReviewInstance = {
    instanceId: `rv_inst_${Date.now()}`,
    fileId,
    status: ReviewStatus.APPROVING,
    currentNode: '初审',
    submitter: MOCK_CURRENT_USER,
    submittedAt: now,
    currentReviewers: ['林浩', MOCK_CURRENT_USER],
  };
  // 同步写入 mock 列表，便于后续查询
  const idx = mockReviewInstances.findIndex((i) => i.fileId === fileId);
  if (idx >= 0) {
    mockReviewInstances[idx] = instance;
  } else {
    mockReviewInstances.push(instance);
  }
  // 提交备注作为一条 APPROVE 意见记录（保留原始备注）
  mockReviewOpinions.push({
    opinionId: `op_${Date.now()}`,
    instanceId: instance.instanceId,
    nodeName: '提交',
    reviewer: MOCK_CURRENT_USER,
    decision: ReviewDecision.APPROVE,
    comment,
    createdAt: now,
  });
  return instance;
}

/** 处理审批决策（Mock：写入意见，并更新实例状态） */
export function processMockDecision(
  instanceId: string,
  decision: ReviewDecision,
  comment: string,
): ReviewOpinion {
  const now = new Date().toISOString();
  const idx = mockReviewInstances.findIndex((i) => i.instanceId === instanceId);
  let nodeName = '审批';
  if (idx >= 0) {
    nodeName = mockReviewInstances[idx].currentNode ?? '审批';
    if (decision === ReviewDecision.APPROVE) {
      // 简化 mock：通过后流转到下一个节点或最终 APPROVED
      mockReviewInstances[idx] = {
        ...mockReviewInstances[idx],
        status: ReviewStatus.APPROVED,
        currentNode: '终审',
        completedAt: now,
        currentReviewers: [],
      };
    } else {
      mockReviewInstances[idx] = {
        ...mockReviewInstances[idx],
        status: ReviewStatus.REJECTED,
        completedAt: now,
        currentReviewers: [],
      };
    }
  }
  const opinion: ReviewOpinion = {
    opinionId: `op_${Date.now()}`,
    instanceId,
    nodeName,
    reviewer: MOCK_CURRENT_USER,
    decision,
    comment,
    createdAt: now,
  };
  mockReviewOpinions.push(opinion);
  return opinion;
}

/** 按 taskId 获取审批进度时间轴（未配置时返回空数组） */
export function getMockApprovalTimelineByTaskId(taskId: string): ApprovalTimelineNode[] {
  return mockApprovalTimelines[taskId] ?? [];
}

export default {
  mockReviewInstances,
  mockReviewOpinions,
  mockApprovalTimelines,
  MOCK_CURRENT_USER,
  getMockReviewInstanceByFileId,
  getMockReviewOpinionsByInstance,
  submitMockReview,
  processMockDecision,
  getMockApprovalTimelineByTaskId,
};
