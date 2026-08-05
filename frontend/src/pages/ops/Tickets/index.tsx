/**
 * 运维工单中心
 * - 展示当前用户的工单列表
 * - 按状态/类型筛选
 * - 支持查看工单详情与影响预览
 */
import React, { useState } from 'react';
import { Card, Typography, Space, Button, Tag, Modal, Descriptions, Select, Row, Col, Statistic } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { ReloadOutlined, SolutionOutlined, EyeOutlined } from '@ant-design/icons';
import { useOpsTickets } from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import {
  OpsTicket, TicketType, TicketTypeLabel, TicketStatus, TicketStatusTag,
} from '@/types/ops';
import { formatDateTime } from '@/utils';
import StatusTag from '@/components/ops/StatusTag';

const { Title, Text } = Typography;

const TicketsPage: React.FC = () => {
  useOpsPermission();
  const [params, setParams] = useState<{ page: number; pageSize: number; q?: string; ticket_type?: TicketType; status?: TicketStatus }>({
    page: 1, pageSize: 10,
  });
  const [detailOpen, setDetailOpen] = useState(false);
  const [current, setCurrent] = useState<OpsTicket | null>(null);

  const { data, isLoading, refetch, isFetching } = useOpsTickets(params);
  const tickets = data?.data?.list ?? [];
  const total = data?.data?.total ?? 0;

  /** 概览统计 */
  const stats = {
    total: tickets.length,
    pending: tickets.filter((t) => t.status === 1).length,
    executing: tickets.filter((t) => t.status === 3 || t.status === 4).length,
    done: tickets.filter((t) => t.status === 5).length,
  };

  const columns: ProColumns<OpsTicket>[] = [
    { title: '工单号', dataIndex: 'ticket_no', width: 160, render: (v) => <Text code>{v as string}</Text> },
    {
      title: '类型', dataIndex: 'ticket_type', width: 120,
      render: (_, r) => <Tag color="blue">{TicketTypeLabel[r.ticket_type]}</Tag>,
    },
    { title: '标题', dataIndex: 'title', width: 240, ellipsis: true },
    { title: '空间', dataIndex: 'team_space_name', width: 140, render: (v) => v || '-' },
    { title: '申请人', dataIndex: 'created_by_name', width: 100 },
    { title: '处理人', dataIndex: 'assignee_name', width: 100, render: (v) => v || '-' },
    {
      title: '状态', dataIndex: 'status', width: 110,
      render: (_, r) => {
        const tag = TicketStatusTag[r.status];
        return <StatusTag color={tag.color} text={tag.text} />;
      },
    },
    { title: '创建时间', dataIndex: 'created_at', width: 160, render: (v) => formatDateTime(v as string) },
    {
      title: '操作', width: 100, fixed: 'right',
      render: (_, r) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => { setCurrent(r); setDetailOpen(true); }}
        >
          详情
        </Button>
      ),
    },
  ];

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Row gutter={16}>
          <Col span={6}><Statistic title="工单总数" value={stats.total} /></Col>
          <Col span={6}><Statistic title="待审批" value={stats.pending} valueStyle={{ color: stats.pending > 0 ? '#1677ff' : undefined }} /></Col>
          <Col span={6}><Statistic title="执行中" value={stats.executing} valueStyle={{ color: stats.executing > 0 ? '#faad14' : undefined }} /></Col>
          <Col span={6}><Statistic title="已完成" value={stats.done} valueStyle={{ color: stats.done > 0 ? '#52c41a' : undefined }} /></Col>
        </Row>
      </Card>

      <Card bordered={false}>
        <ProTable<OpsTicket>
          rowKey="id"
          columns={columns}
          dataSource={tickets}
          loading={isLoading || isFetching}
          search={false}
          scroll={{ x: 1400 }}
          pagination={{
            current: params.page, pageSize: params.pageSize, total,
            showSizeChanger: true,
            onChange: (page, pageSize) => setParams({ ...params, page, pageSize }),
          }}
          headerTitle={
            <Space>
              <SolutionOutlined style={{ fontSize: 18, color: '#1677ff' }} />
              <Title level={5} style={{ margin: 0 }}>运维工单</Title>
              <Text type="secondary">配额/销毁/重试/导出/配置等审批流程</Text>
            </Space>
          }
          toolBarRender={() => [
            <SolutionOutlined key="type-icon" />,
            <Select
              key="type"
              placeholder="工单类型"
              allowClear
              style={{ width: 140 }}
              onChange={(v) => setParams({ ...params, ticket_type: (v ?? undefined) as TicketType | undefined, page: 1 })}
              options={[{ value: '', label: '全部类型' }, ...Object.entries(TicketTypeLabel).map(([k, v]) => ({ value: k, label: v }))]}
            />,
            <Select
              key="status"
              placeholder="状态"
              allowClear
              style={{ width: 120 }}
              onChange={(v) => setParams({ ...params, status: (v === '' || v === undefined ? undefined : Number(v)) as TicketStatus | undefined, page: 1 })}
              options={[{ value: '', label: '全部状态' }, ...Object.entries(TicketStatusTag).map(([k, v]) => ({ value: k, label: v.text }))]}
            />,
            <Button key="refresh" icon={<ReloadOutlined />} onClick={() => refetch()}>刷新</Button>,
          ]}
        />
      </Card>

      <Modal
        open={detailOpen}
        title={current ? `工单详情 ${current.ticket_no}` : '工单详情'}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={640}
        destroyOnClose
      >
        {current && (
          <div>
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="工单号" span={2}><Text code>{current.ticket_no}</Text></Descriptions.Item>
              <Descriptions.Item label="类型"><Tag color="blue">{TicketTypeLabel[current.ticket_type]}</Tag></Descriptions.Item>
              <Descriptions.Item label="状态">
                {(() => {
                  const tag = TicketStatusTag[current.status];
                  return <StatusTag color={tag.color} text={tag.text} />;
                })()}
              </Descriptions.Item>
              <Descriptions.Item label="标题" span={2}>{current.title}</Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>{current.description}</Descriptions.Item>
              <Descriptions.Item label="目标空间">{current.team_space_name || '-'}</Descriptions.Item>
              <Descriptions.Item label="目标引用"><Text code>{current.target_ref}</Text></Descriptions.Item>
              <Descriptions.Item label="申请人">{current.created_by_name}</Descriptions.Item>
              <Descriptions.Item label="处理人">{current.assignee_name || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(current.created_at)}</Descriptions.Item>
              <Descriptions.Item label="审批时间">{current.approved_at ? formatDateTime(current.approved_at) : '-'}</Descriptions.Item>
              <Descriptions.Item label="执行时间">{current.executed_at ? formatDateTime(current.executed_at) : '-'}</Descriptions.Item>
              <Descriptions.Item label="完成时间">{current.finished_at ? formatDateTime(current.finished_at) : '-'}</Descriptions.Item>
            </Descriptions>

            <Card title="工单参数" size="small" style={{ marginTop: 12 }}>
              <pre style={{ background: '#fafafa', padding: 8, borderRadius: 4, fontSize: 12, margin: 0 }}>
                {JSON.stringify(current.params, null, 2)}
              </pre>
            </Card>

            <Card title="影响预览" size="small" style={{ marginTop: 12 }}>
              <pre style={{ background: '#fafafa', padding: 8, borderRadius: 4, fontSize: 12, margin: 0 }}>
                {JSON.stringify(current.impact_preview, null, 2)}
              </pre>
            </Card>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default TicketsPage;
