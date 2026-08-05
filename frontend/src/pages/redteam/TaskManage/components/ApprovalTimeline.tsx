/**
 * 审批进度时间轴组件
 * 用于任务详情页，展示任务关联的审批节点流转。
 *
 * - antd Timeline 组件展示审批节点
 * - 每个节点：节点名 + 审批人 + 状态 Tag + 时间 + 意见（如有）
 * - 节点状态：pending 等待中 / processing 处理中 / approved 通过 / rejected 驳回
 *
 * 数据来源：services/fileReview.getApprovalTimeline（基于 Mock）
 */
import React, { useEffect, useState } from 'react';
import { Card, Timeline, Tag, Typography, Empty, Spin, Space } from 'antd';
import {
  ClockCircleOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import type { ApprovalTimelineNode } from '@/types';
import { getApprovalTimeline } from '@/services/fileReview';
import { formatDateTime } from '@/utils';

const { Text, Paragraph } = Typography;

/** 节点状态 → Tag 颜色 */
const statusColor: Record<ApprovalTimelineNode['status'], string> = {
  pending: 'default',
  processing: 'processing',
  approved: 'green',
  rejected: 'red',
};

/** 节点状态 → 中文标签 */
const statusLabel: Record<ApprovalTimelineNode['status'], string> = {
  pending: '等待中',
  processing: '处理中',
  approved: '已通过',
  rejected: '已驳回',
};

/** 节点状态 → Timeline 颜色（antd Timeline 支持的 color 值） */
const timelineColor: Record<ApprovalTimelineNode['status'], string> = {
  pending: 'gray',
  processing: 'blue',
  approved: 'green',
  rejected: 'red',
};

/** 节点状态 → 图标 */
const statusIcon: Record<ApprovalTimelineNode['status'], React.ReactNode> = {
  pending: <ClockCircleOutlined />,
  processing: <LoadingOutlined />,
  approved: <CheckCircleOutlined />,
  rejected: <CloseCircleOutlined />,
};

/** ApprovalTimeline Props */
export interface ApprovalTimelineProps {
  /** 任务 ID */
  taskId: string;
  /** 受控模式：父组件传入节点数据 */
  nodes?: ApprovalTimelineNode[];
  /** 加载中（受控模式） */
  loading?: boolean;
}

/**
 * 审批进度时间轴组件
 */
const ApprovalTimeline: React.FC<ApprovalTimelineProps> = ({
  taskId,
  nodes: nodesProp,
  loading: loadingProp,
}) => {
  const [nodesState, setNodesState] = useState<ApprovalTimelineNode[]>([]);
  const [loadingState, setLoadingState] = useState(false);

  const isControlled = nodesProp !== undefined;
  const nodes = isControlled ? nodesProp : nodesState;
  const loading = loadingProp !== undefined ? loadingProp : loadingState;

  /** 加载审批进度 */
  const fetchTimeline = async () => {
    if (isControlled || !taskId) return;
    setLoadingState(true);
    try {
      const res = await getApprovalTimeline(taskId);
      if (res.code === 200 || res.code === 0) {
        setNodesState(res.data);
      } else {
        setNodesState([]);
      }
    } catch {
      setNodesState([]);
    } finally {
      setLoadingState(false);
    }
  };

  useEffect(() => {
    fetchTimeline();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId]);

  return (
    <Card
      size="small"
      title={
        <Space>
          <AuditOutlined />
          <span>审批进度</span>
        </Space>
      }
      data-testid="approval-timeline-card"
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin tip="加载审批进度..." />
        </div>
      ) : nodes && nodes.length > 0 ? (
        <Timeline
          data-testid="approval-timeline"
          items={nodes.map((node) => ({
            color: timelineColor[node.status],
            dot: statusIcon[node.status],
            children: (
              <div data-testid={`approval-node-${node.nodeId}`}>
                <div style={{ marginBottom: 4 }}>
                  <Space>
                    <Text strong>{node.nodeName}</Text>
                    <Tag color={statusColor[node.status]} icon={statusIcon[node.status]}>
                      {statusLabel[node.status]}
                    </Tag>
                  </Space>
                </div>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>
                  <Space split={<span>·</span>}>
                    <span>审批人：{node.reviewer}</span>
                    {node.handledAt && <span>{formatDateTime(node.handledAt)}</span>}
                  </Space>
                </div>
                {node.comment && (
                  <Paragraph
                    type="secondary"
                    style={{ fontSize: 12, marginBottom: 0, marginTop: 4 }}
                  >
                    意见：{node.comment}
                  </Paragraph>
                )}
              </div>
            ),
          }))}
        />
      ) : (
        <Empty
          description="暂无审批进度"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          data-testid="approval-timeline-empty"
        />
      )}
    </Card>
  );
};

export default ApprovalTimeline;
