/**
 * 文件评审区域组件
 * 用于文件详情页底部，展示评审状态、评审意见列表，并提供提交/审批操作。
 *
 * 功能：
 * - 顶部：评审状态 Tag（PENDING 黄色 / APPROVING 蓝色 / APPROVED 绿色 / REJECTED 红色）
 * - 中间：评审意见列表（List，每条显示 reviewer / decision Tag / comment / 时间）
 * - 底部操作区：
 *   - 未提交（PENDING）：显示「提交评审」按钮 + Modal
 *   - 审批中（APPROVING）且当前用户是审批人：显示「通过」/「驳回」按钮 + Modal
 *   - 已完成（APPROVED / REJECTED）：仅展示
 *
 * 数据来源：services/fileReview（失败时降级到 Mock）
 */
import React, { useCallback, useEffect, useState } from 'react';
import {
  Card,
  Tag,
  List,
  Button,
  Space,
  Modal,
  Input,
  Avatar,
  Typography,
  Empty,
  Spin,
  Descriptions,
  message,
} from 'antd';
import {
  AuditOutlined,
  CheckOutlined,
  CloseOutlined,
  FileDoneOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  ReviewStatus,
  ReviewStatusLabel,
  ReviewStatusColor,
  ReviewDecision,
  ReviewDecisionLabel,
  ReviewDecisionColor,
} from '@/types';
import type { ReviewInstance, ReviewOpinion } from '@/types';
import {
  submitFileReview,
  processDecision,
  getReviewStatus,
  getReviewOpinions,
} from '@/services/fileReview';
import { formatDateTime } from '@/utils';

const { Text, Paragraph } = Typography;

/** Modal 操作类型 */
type ModalAction = 'submit' | 'approve' | 'reject' | null;

/** FileReviewSection 组件 Props */
export interface FileReviewSectionProps {
  /** 文件 ID */
  fileId: string;
  /** 当前登录用户名（用于判断是否可审批） */
  currentUser?: string;
  /** 评审实例（受控模式：父组件传入时直接使用，否则组件内自行加载） */
  instance?: ReviewInstance;
  /** 评审意见（受控模式） */
  opinions?: ReviewOpinion[];
  /** 加载中（受控模式） */
  loading?: boolean;
  /** 评审状态变更后的回调（用于父组件刷新） */
  onChange?: () => void;
}

/**
 * 根据评审实例判断当前用户是否可审批
 */
function canReview(instance: ReviewInstance | null, currentUser?: string): boolean {
  if (!instance || !currentUser) return false;
  if (instance.status !== ReviewStatus.APPROVING) return false;
  const reviewers = instance.currentReviewers ?? [];
  return reviewers.includes(currentUser);
}

/**
 * 文件评审区域组件
 */
const FileReviewSection: React.FC<FileReviewSectionProps> = ({
  fileId,
  currentUser,
  instance: instanceProp,
  opinions: opinionsProp,
  loading: loadingProp,
  onChange,
}) => {
  // 非受控模式下的内部状态
  const [instanceState, setInstanceState] = useState<ReviewInstance | null>(null);
  const [opinionsState, setOpinionsState] = useState<ReviewOpinion[]>([]);
  const [loadingState, setLoadingState] = useState(false);

  // Modal 状态
  const [modalOpen, setModalOpen] = useState(false);
  const [modalAction, setModalAction] = useState<ModalAction>(null);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const isControlled = instanceProp !== undefined;
  const instance = isControlled ? instanceProp! : instanceState;
  const opinions = opinionsProp !== undefined ? opinionsProp : opinionsState;
  const loading = loadingProp !== undefined ? loadingProp : loadingState;

  /** 加载评审实例 + 意见 */
  const fetchReview = useCallback(async () => {
    if (isControlled) return;
    if (!fileId) return;
    setLoadingState(true);
    try {
      const instRes = await getReviewStatus(fileId);
      let inst: ReviewInstance | null = null;
      if (instRes.code === 200 || instRes.code === 0) {
        inst = instRes.data;
        setInstanceState(inst);
      }
      // 拉取意见（仅当存在真实 instanceId 时）
      if (inst && inst.instanceId && !inst.instanceId.startsWith('rv_inst_pending_')) {
        const opRes = await getReviewOpinions(inst.instanceId);
        if (opRes.code === 200 || opRes.code === 0) {
          setOpinionsState(opRes.data);
        } else {
          setOpinionsState([]);
        }
      } else {
        setOpinionsState([]);
      }
    } catch {
      // 静默降级
    } finally {
      setLoadingState(false);
    }
  }, [fileId, isControlled]);

  useEffect(() => {
    fetchReview();
  }, [fetchReview]);

  /** 打开 Modal */
  const openModal = (action: Exclude<ModalAction, null>) => {
    setModalAction(action);
    setComment('');
    setModalOpen(true);
  };

  /** 提交 Modal */
  const handleModalOk = async () => {
    if (!modalAction) return;
    if (!comment.trim()) {
      message.warning('请填写评审意见');
      return;
    }
    setSubmitting(true);
    try {
      if (modalAction === 'submit') {
        await submitFileReview(fileId, comment);
        message.success('评审已提交');
      } else if (modalAction === 'approve' || modalAction === 'reject') {
        if (!instance) {
          message.error('评审实例不存在');
          return;
        }
        const decision =
          modalAction === 'approve' ? ReviewDecision.APPROVE : ReviewDecision.REJECT;
        await processDecision(instance.instanceId, decision, comment);
        message.success(modalAction === 'approve' ? '已通过' : '已驳回');
      }
      setModalOpen(false);
      // 刷新数据
      await fetchReview();
      onChange?.();
    } catch {
      message.error('操作失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  /** Modal 标题 */
  const modalTitle =
    modalAction === 'submit'
      ? '提交评审'
      : modalAction === 'approve'
        ? '通过审批'
        : modalAction === 'reject'
          ? '驳回审批'
          : '';

  /** 当前用户是否可审批 */
  const canUserReview = canReview(instance, currentUser);

  return (
    <Card
      title={
        <Space>
          <AuditOutlined />
          <span>文件评审</span>
          {instance && (
            <Tag color={ReviewStatusColor[instance.status]} data-testid="review-status-tag">
              {ReviewStatusLabel[instance.status]}
            </Tag>
          )}
        </Space>
      }
      data-testid="file-review-section"
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin tip="加载评审信息..." />
        </div>
      ) : (
        <>
          {/* 顶部：评审实例信息 */}
          {instance && instance.status !== ReviewStatus.PENDING && (
            <Descriptions
              size="small"
              column={3}
              bordered
              style={{ marginBottom: 16 }}
            >
              <Descriptions.Item label="评审实例">
                <code>{instance.instanceId}</code>
              </Descriptions.Item>
              <Descriptions.Item label="提交人">
                {instance.submitter || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="提交时间">
                {instance.submittedAt ? formatDateTime(instance.submittedAt) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="当前节点">
                {instance.currentNode || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="完成时间">
                {instance.completedAt ? formatDateTime(instance.completedAt) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="当前审批人">
                {instance.currentReviewers && instance.currentReviewers.length > 0
                  ? instance.currentReviewers.join('、')
                  : '-'}
              </Descriptions.Item>
            </Descriptions>
          )}

          {/* 中间：评审意见列表 */}
          <div style={{ marginBottom: 16 }}>
            <Text strong>评审意见</Text>
            <div style={{ marginTop: 8 }} data-testid="review-opinions-list">
              {opinions && opinions.length > 0 ? (
                <List
                  itemLayout="vertical"
                  size="small"
                  dataSource={opinions}
                  renderItem={(op) => (
                    <List.Item key={op.opinionId}>
                      <List.Item.Meta
                        avatar={
                          <Avatar icon={<UserOutlined />} data-testid={`opinion-avatar-${op.opinionId}`} />
                        }
                        title={
                          <Space>
                            <Text strong>{op.reviewer}</Text>
                            <Tag color={ReviewDecisionColor[op.decision]} data-testid={`opinion-decision-${op.opinionId}`}>
                              {ReviewDecisionLabel[op.decision]}
                            </Tag>
                            <Tag>{op.nodeName}</Tag>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {formatDateTime(op.createdAt)}
                            </Text>
                          </Space>
                        }
                        description={<Paragraph style={{ marginBottom: 0 }}>{op.comment}</Paragraph>}
                      />
                    </List.Item>
                  )}
                />
              ) : (
                <Empty
                  description={instance && instance.status === ReviewStatus.PENDING ? '尚未提交评审' : '暂无评审意见'}
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              )}
            </div>
          </div>

          {/* 底部：操作区 */}
          <div
            style={{
              borderTop: '1px solid #f0f0f0',
              paddingTop: 12,
              display: 'flex',
              justifyContent: 'flex-end',
            }}
            data-testid="review-actions"
          >
            <Space>
              {(!instance || instance.status === ReviewStatus.PENDING) && (
                <Button
                  type="primary"
                  icon={<FileDoneOutlined />}
                  onClick={() => openModal('submit')}
                  data-testid="submit-review-btn"
                >
                  提交评审
                </Button>
              )}
              {instance && instance.status === ReviewStatus.APPROVING && canUserReview && (
                <>
                  <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    onClick={() => openModal('approve')}
                    data-testid="approve-review-btn"
                  >
                    通过
                  </Button>
                  <Button
                    danger
                    icon={<CloseOutlined />}
                    onClick={() => openModal('reject')}
                    data-testid="reject-review-btn"
                  >
                    驳回
                  </Button>
                </>
              )}
              {instance &&
                (instance.status === ReviewStatus.APPROVED ||
                  instance.status === ReviewStatus.REJECTED) && (
                  <Text type="secondary">评审已完成</Text>
                )}
            </Space>
          </div>
        </>
      )}

      {/* Modal：提交 / 通过 / 驳回 */}
      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        okText="确认"
        cancelText="取消"
        destroyOnHidden
      >
        <Input.TextArea
          rows={4}
          placeholder="请输入评审意见（必填）"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={500}
          showCount
          data-testid="review-comment-input"
        />
      </Modal>
    </Card>
  );
};

export default FileReviewSection;
