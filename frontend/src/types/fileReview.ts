/**
 * 文件评审模块类型定义
 * 对应后端 workflow-service FileReviewService（端口 8094）
 * - 评审实例（ReviewInstance）：一次文件评审流程
 * - 评审意见（ReviewOpinion）：每个审批节点的决策与意见
 */

/** 评审状态 */
export enum ReviewStatus {
  /** 待提交 */
  PENDING = 'PENDING',
  /** 审批中 */
  APPROVING = 'APPROVING',
  /** 已通过 */
  APPROVED = 'APPROVED',
  /** 已驳回 */
  REJECTED = 'REJECTED',
}

/** 评审状态中文标签 */
export const ReviewStatusLabel: Record<ReviewStatus, string> = {
  [ReviewStatus.PENDING]: '待提交',
  [ReviewStatus.APPROVING]: '审批中',
  [ReviewStatus.APPROVED]: '已通过',
  [ReviewStatus.REJECTED]: '已驳回',
};

/** 评审状态对应的 antd Tag 颜色 */
export const ReviewStatusColor: Record<ReviewStatus, string> = {
  [ReviewStatus.PENDING]: 'gold',
  [ReviewStatus.APPROVING]: 'blue',
  [ReviewStatus.APPROVED]: 'green',
  [ReviewStatus.REJECTED]: 'red',
};

/** 审批决策（审批人提交） */
export enum ReviewDecision {
  /** 通过 */
  APPROVE = 'APPROVE',
  /** 驳回 */
  REJECT = 'REJECT',
}

/** 审批决策中文标签 */
export const ReviewDecisionLabel: Record<ReviewDecision, string> = {
  [ReviewDecision.APPROVE]: '通过',
  [ReviewDecision.REJECT]: '驳回',
};

/** 审批决策对应的 antd Tag 颜色 */
export const ReviewDecisionColor: Record<ReviewDecision, string> = {
  [ReviewDecision.APPROVE]: 'green',
  [ReviewDecision.REJECT]: 'red',
};

/**
 * 评审实例
 * 描述一次文件评审流程的总体状态
 */
export interface ReviewInstance {
  /** 评审实例 ID */
  instanceId: string;
  /** 关联文件 ID */
  fileId: string;
  /** 评审状态 */
  status: ReviewStatus;
  /** 当前审批节点名称（如：初审 / 复审 / 终审） */
  currentNode?: string;
  /** 提交人（用户名或姓名） */
  submitter: string;
  /** 提交时间 ISO 字符串 */
  submittedAt: string;
  /** 当前审批人列表（用于判断当前用户是否可操作） */
  currentReviewers?: string[];
  /** 评审完成时间（已通过/已驳回时存在） */
  completedAt?: string;
}

/**
 * 评审意见
 * 每个审批节点的决策记录
 */
export interface ReviewOpinion {
  /** 意见 ID */
  opinionId: string;
  /** 关联评审实例 ID */
  instanceId: string;
  /** 审批节点名称 */
  nodeName: string;
  /** 审批人（用户名或姓名） */
  reviewer: string;
  /** 决策结果 */
  decision: ReviewDecision;
  /** 评审意见/备注 */
  comment: string;
  /** 创建时间 ISO 字符串 */
  createdAt: string;
}

/**
 * 审批进度节点（用于任务详情页时间轴展示）
 * 与 ReviewOpinion 类似，但额外携带节点状态字段以便渲染
 */
export interface ApprovalTimelineNode {
  /** 节点 ID */
  nodeId: string;
  /** 节点名称 */
  nodeName: string;
  /** 审批人 */
  reviewer: string;
  /** 节点状态：pending 等待中 / processing 处理中 / approved 通过 / rejected 驳回 */
  status: 'pending' | 'processing' | 'approved' | 'rejected';
  /** 处理时间（已处理节点存在） */
  handledAt?: string;
  /** 评审意见（已处理节点存在） */
  comment?: string;
}

/** 提交评审请求参数 */
export interface SubmitReviewParams {
  /** 文件 ID */
  fileId: string;
  /** 评审备注 */
  comment: string;
}

/** 处理审批决策请求参数 */
export interface ProcessDecisionParams {
  /** 评审实例 ID */
  instanceId: string;
  /** 决策结果 */
  decision: ReviewDecision;
  /** 评审意见 */
  comment: string;
}
