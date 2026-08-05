/**
 * 单元测试：文件评审区域组件（src/pages/FileList/Detail/components/FileReviewSection.tsx）
 * 覆盖 4 种评审状态的渲染：
 * - PENDING（待提交）：显示「提交评审」按钮
 * - APPROVING（审批中）：当前用户是审批人时显示「通过」/「驳回」按钮
 * - APPROVED（已通过）：仅展示，显示「评审已完成」
 * - REJECTED（已驳回）：仅展示，显示「评审已完成」
 *
 * 同时覆盖：
 * - 评审状态 Tag 渲染（4 种颜色）
 * - 评审意见列表渲染
 * - Modal 交互：提交评审 / 通过 / 驳回
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import FileReviewSection from '@/pages/FileList/Detail/components/FileReviewSection';
import { ReviewStatus, ReviewDecision } from '@/types';
import type { ReviewInstance, ReviewOpinion } from '@/types';

// ===== Mock 服务（受控模式下不会被调用，但兜底避免真实请求） =====
vi.mock('@/services/fileReview', () => ({
  submitFileReview: vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: {} }),
  processDecision: vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: {} }),
  getReviewStatus: vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: {} }),
  getReviewOpinions: vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: [] }),
  getApprovalTimeline: vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: [] }),
}));

// ===== Mock 工具函数 =====
vi.mock('@/utils', () => ({
  formatDateTime: (date: string) => date || '-',
  formatFileSize: (bytes: number) => `${bytes} B`,
  copyToClipboard: vi.fn().mockResolvedValue(true),
}));

/** 构造 PENDING 实例 */
function buildPendingInstance(): ReviewInstance {
  return {
    instanceId: 'rv_inst_pending_f0001',
    fileId: 'f0001',
    status: ReviewStatus.PENDING,
    submitter: '',
    submittedAt: '',
    currentReviewers: [],
  };
}

/** 构造 APPROVING 实例 */
function buildApprovingInstance(): ReviewInstance {
  return {
    instanceId: 'rv_inst_001',
    fileId: 'f0001',
    status: ReviewStatus.APPROVING,
    currentNode: '复审',
    submitter: '陈思齐',
    submittedAt: '2026-07-20T09:30:00Z',
    currentReviewers: ['王浩然', '林浩'],
  };
}

/** 构造 APPROVED 实例 */
function buildApprovedInstance(): ReviewInstance {
  return {
    instanceId: 'rv_inst_002',
    fileId: 'f0002',
    status: ReviewStatus.APPROVED,
    currentNode: '终审',
    submitter: '王浩然',
    submittedAt: '2026-07-05T14:00:00Z',
    completedAt: '2026-07-08T16:30:00Z',
    currentReviewers: [],
  };
}

/** 构造 REJECTED 实例 */
function buildRejectedInstance(): ReviewInstance {
  return {
    instanceId: 'rv_inst_003',
    fileId: 'f0003',
    status: ReviewStatus.REJECTED,
    currentNode: '初审',
    submitter: '刘晓东',
    submittedAt: '2026-07-10T10:00:00Z',
    completedAt: '2026-07-11T11:20:00Z',
    currentReviewers: [],
  };
}

/** 构造评审意见列表 */
function buildOpinions(instanceId: string): ReviewOpinion[] {
  return [
    {
      opinionId: 'op_001',
      instanceId,
      nodeName: '初审',
      reviewer: '林浩',
      decision: ReviewDecision.APPROVE,
      comment: '文件元数据完整，建议进入复审。',
      createdAt: '2026-07-21T10:00:00Z',
    },
    {
      opinionId: 'op_002',
      instanceId,
      nodeName: '复审',
      reviewer: '王浩然',
      decision: ReviewDecision.REJECT,
      comment: '需补充采集链路记录。',
      createdAt: '2026-07-22T11:00:00Z',
    },
  ];
}

describe('FileReviewSection 文件评审区域组件', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('PENDING 状态：显示「待提交」Tag 与「提交评审」按钮', () => {
    const instance = buildPendingInstance();
    render(
      <FileReviewSection
        fileId="f0001"
        currentUser="王浩然"
        instance={instance}
        opinions={[]}
        loading={false}
      />,
    );

    // 评审区域渲染
    expect(screen.getByTestId('file-review-section')).toBeInTheDocument();
    // 状态 Tag 显示「待提交」
    const statusTag = screen.getByTestId('review-status-tag');
    expect(statusTag.textContent).toContain('待提交');
    // 提交评审按钮存在
    expect(screen.getByTestId('submit-review-btn')).toBeInTheDocument();
    // 不应显示通过/驳回按钮
    expect(screen.queryByTestId('approve-review-btn')).toBeNull();
    expect(screen.queryByTestId('reject-review-btn')).toBeNull();
  });

  it('APPROVING 状态：当前用户是审批人时显示「通过」/「驳回」按钮', () => {
    const instance = buildApprovingInstance();
    const opinions = buildOpinions(instance.instanceId);
    render(
      <FileReviewSection
        fileId="f0001"
        currentUser="王浩然"
        instance={instance}
        opinions={opinions}
        loading={false}
      />,
    );

    // 状态 Tag 显示「审批中」
    const statusTag = screen.getByTestId('review-status-tag');
    expect(statusTag.textContent).toContain('审批中');
    // 当前用户「王浩然」在 currentReviewers 中，应显示通过/驳回按钮
    expect(screen.getByTestId('approve-review-btn')).toBeInTheDocument();
    expect(screen.getByTestId('reject-review-btn')).toBeInTheDocument();
    // 不应显示提交评审按钮
    expect(screen.queryByTestId('submit-review-btn')).toBeNull();
    // 评审意见列表渲染（2 条意见）
    const opinionsList = screen.getByTestId('review-opinions-list');
    expect(opinionsList).toBeInTheDocument();
    // 验证意见中的审批人姓名渲染
    expect(screen.getByText('林浩')).toBeInTheDocument();
    expect(screen.getByText('王浩然')).toBeInTheDocument();
  });

  it('APPROVING 状态：当前用户不是审批人时不显示操作按钮', () => {
    const instance = buildApprovingInstance();
    render(
      <FileReviewSection
        fileId="f0001"
        currentUser="赵敏"
        instance={instance}
        opinions={[]}
        loading={false}
      />,
    );

    // 赵敏 不在 currentReviewers 中，不应显示通过/驳回按钮
    expect(screen.queryByTestId('approve-review-btn')).toBeNull();
    expect(screen.queryByTestId('reject-review-btn')).toBeNull();
    // 也不应显示提交按钮（已提交状态）
    expect(screen.queryByTestId('submit-review-btn')).toBeNull();
  });

  it('APPROVED 状态：显示「已通过」Tag 与「评审已完成」提示', () => {
    const instance = buildApprovedInstance();
    const opinions = buildOpinions(instance.instanceId);
    render(
      <FileReviewSection
        fileId="f0002"
        currentUser="王浩然"
        instance={instance}
        opinions={opinions}
        loading={false}
      />,
    );

    // 状态 Tag 显示「已通过」
    const statusTag = screen.getByTestId('review-status-tag');
    expect(statusTag.textContent).toContain('已通过');
    // 不应显示任何操作按钮
    expect(screen.queryByTestId('submit-review-btn')).toBeNull();
    expect(screen.queryByTestId('approve-review-btn')).toBeNull();
    expect(screen.queryByTestId('reject-review-btn')).toBeNull();
    // 显示「评审已完成」
    expect(screen.getByText('评审已完成')).toBeInTheDocument();
  });

  it('REJECTED 状态：显示「已驳回」Tag 与「评审已完成」提示', () => {
    const instance = buildRejectedInstance();
    render(
      <FileReviewSection
        fileId="f0003"
        currentUser="王浩然"
        instance={instance}
        opinions={[]}
        loading={false}
      />,
    );

    // 状态 Tag 显示「已驳回」
    const statusTag = screen.getByTestId('review-status-tag');
    expect(statusTag.textContent).toContain('已驳回');
    // 不应显示任何操作按钮
    expect(screen.queryByTestId('submit-review-btn')).toBeNull();
    expect(screen.queryByTestId('approve-review-btn')).toBeNull();
    expect(screen.queryByTestId('reject-review-btn')).toBeNull();
    // 显示「评审已完成」
    expect(screen.getByText('评审已完成')).toBeInTheDocument();
  });

  it('点击「提交评审」打开 Modal，填写意见后调用 submitFileReview', async () => {
    const { submitFileReview } = await import('@/services/fileReview');
    const instance = buildPendingInstance();
    const onChange = vi.fn();
    render(
      <FileReviewSection
        fileId="f0001"
        currentUser="王浩然"
        instance={instance}
        opinions={[]}
        loading={false}
        onChange={onChange}
      />,
    );

    // 点击提交评审
    fireEvent.click(screen.getByTestId('submit-review-btn'));
    // Modal 出现
    const commentInput = await screen.findByTestId('review-comment-input');
    expect(commentInput).toBeInTheDocument();
    // 填写意见
    fireEvent.change(commentInput, { target: { value: '该文件需要评审' } });
    // 点击确认（antd Button 默认 autoInsertSpace，会在两个中文字符间插入空格，实际渲染为 "确 认"）
    const okBtn = screen.getByRole('button', { name: /确\s?认/ });
    fireEvent.click(okBtn);

    // 验证 submitFileReview 被调用
    await waitFor(() => {
      expect(submitFileReview).toHaveBeenCalledWith('f0001', '该文件需要评审');
    });
  });

  it('点击「通过」打开 Modal，填写意见后调用 processDecision（APPROVE）', async () => {
    const { processDecision } = await import('@/services/fileReview');
    const instance = buildApprovingInstance();
    render(
      <FileReviewSection
        fileId="f0001"
        currentUser="王浩然"
        instance={instance}
        opinions={[]}
        loading={false}
      />,
    );

    // 点击通过
    fireEvent.click(screen.getByTestId('approve-review-btn'));
    const commentInput = await screen.findByTestId('review-comment-input');
    fireEvent.change(commentInput, { target: { value: '同意通过' } });
    // antd autoInsertSpace：按钮文字 "确认" 渲染为 "确 认"
    fireEvent.click(screen.getByRole('button', { name: /确\s?认/ }));

    await waitFor(() => {
      expect(processDecision).toHaveBeenCalledWith(
        'rv_inst_001',
        ReviewDecision.APPROVE,
        '同意通过',
      );
    });
  });

  it('评审意见列表正确渲染 reviewer / decision Tag / comment', () => {
    const instance = buildApprovedInstance();
    const opinions = buildOpinions(instance.instanceId);
    render(
      <FileReviewSection
        fileId="f0002"
        currentUser="王浩然"
        instance={instance}
        opinions={opinions}
        loading={false}
      />,
    );

    // 验证意见内容渲染
    expect(screen.getByText('文件元数据完整，建议进入复审。')).toBeInTheDocument();
    expect(screen.getByText('需补充采集链路记录。')).toBeInTheDocument();
    // 验证决策 Tag 存在（通过 / 驳回）
    const decisionTags = screen.getAllByText(/通过|驳回/);
    expect(decisionTags.length).toBeGreaterThanOrEqual(2);
    // 验证头像渲染
    expect(screen.getByTestId('opinion-avatar-op_001')).toBeInTheDocument();
    expect(screen.getByTestId('opinion-avatar-op_002')).toBeInTheDocument();
  });
});
