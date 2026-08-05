/**
 * D1 空间台账 - 详情页（6 个 Tab）
 * 1. 概览：基础信息 + 健康分明细 + 建议
 * 2. 文件分布：按类型/大小/敏感等级分布
 * 3. 链路健康：trace 断链、索引积压、解析积压
 * 4. 成员管理：成员列表 + 角色变更
 * 5. 配额日志：配额变更历史
 * 6. 操作事件：文件事件流
 */
import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Tabs, Descriptions, Typography, Space, Button, Tag, List, Table, Progress, Empty, Spin, message, Input, Modal, Form,
} from 'antd';
import type { TabsProps } from 'antd';
import {
  ArrowLeftOutlined, ReloadOutlined, UserAddOutlined, ExportOutlined, TeamOutlined,
} from '@ant-design/icons';
import {
  useSpace, useSpaceHealth, useSpaceMembers, useSpaceQuotaLog, useSpaceEvents,
  useTransferSpace,
} from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import {
  SpaceMemberRoleLabel, SpaceLifecycleTag,
} from '@/types/ops';
import type { SpaceMember, QuotaLog } from '@/types/ops';
import { formatDateTime, formatFileSize } from '@/utils';
import HealthScoreGauge from '@/components/ops/HealthScoreGauge';
import StatusTag from '@/components/ops/StatusTag';
import OpsTicketButton from '@/components/ops/OpsTicketButton';

const { Title, Text, Paragraph } = Typography;

const SpaceDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const spaceId = Number(id);
  const navigate = useNavigate();
  const { can, isPlatformAdmin } = useOpsPermission();

  const [activeTab, setActiveTab] = useState('overview');
  const [quotaPage, setQuotaPage] = useState({ page: 1, pageSize: 10 });
  const [eventPage, setEventPage] = useState({ page: 1, pageSize: 10 });
  const [transferOpen, setTransferOpen] = useState(false);
  const [transferForm] = Form.useForm();

  const spaceQ = useSpace(spaceId);
  const healthQ = useSpaceHealth(spaceId);
  const membersQ = useSpaceMembers(spaceId);
  const quotaQ = useSpaceQuotaLog(spaceId, quotaPage);
  const eventsQ = useSpaceEvents(spaceId, eventPage);
  const transfer = useTransferSpace();

  const space = spaceQ.data?.data;
  const health = healthQ.data?.data;
  const members = membersQ.data?.data ?? [];
  const quotaLogs = quotaQ.data?.data?.list ?? [];
  const quotaTotal = quotaQ.data?.data?.total ?? 0;
  const events = eventsQ.data?.data?.list ?? [];
  const eventTotal = eventsQ.data?.data?.total ?? 0;

  if (spaceQ.isLoading || !space) {
    return <Card><Spin tip="加载空间详情中..." style={{ marginTop: 80 }} /></Card>;
  }

  const lifecycleTag = SpaceLifecycleTag[space.lifecycle_status];

  /** 概览 Tab */
  const overviewTab: NonNullable<TabsProps['items']>[number] = {
    key: 'overview',
    label: '概览',
    children: (
      <div>
        <Descriptions bordered column={2} size="small">
          <Descriptions.Item label="空间编码"><Text code>{space.code}</Text></Descriptions.Item>
          <Descriptions.Item label="空间名称">{space.name}</Descriptions.Item>
          <Descriptions.Item label="负责人">{space.owner_name}</Descriptions.Item>
          <Descriptions.Item label="成员数">{space.member_count}</Descriptions.Item>
          <Descriptions.Item label="文件数">{space.file_count.toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="冷文件数">{space.cold_file_count.toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="存储用量">
            {formatFileSize(space.storage_used)} / {formatFileSize(space.storage_quota)}
          </Descriptions.Item>
          <Descriptions.Item label="归档量">{formatFileSize(space.archived_bytes)}</Descriptions.Item>
          <Descriptions.Item label="生命周期">
            <StatusTag color={lifecycleTag.color} text={lifecycleTag.text} />
          </Descriptions.Item>
          <Descriptions.Item label="版本号（乐观锁）">v{space.version}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatDateTime(space.created_at)}</Descriptions.Item>
          <Descriptions.Item label="配额使用率">
            <Progress
              percent={Math.round((space.storage_used / space.storage_quota) * 100)}
              status={space.storage_used / space.storage_quota > 0.85 ? 'exception' : 'normal'}
            />
          </Descriptions.Item>
        </Descriptions>

        <Card title="健康分明细" size="small" style={{ marginTop: 16 }}>
          <Space align="start" wrap>
            <HealthScoreGauge value={health?.score ?? space.health_score} dimensions={health?.dimension} />
            <div style={{ flex: 1, minWidth: 320 }}>
              <Text strong>维度评分</Text>
              <List
                size="small"
                dataSource={health?.dimension ?? []}
                renderItem={(d) => (
                  <List.Item>
                    <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                      <Space direction="vertical" size={0}>
                        <Text strong>{d.name}</Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>{d.reason}</Text>
                      </Space>
                      <Progress
                        type="circle"
                        size={48}
                        percent={d.score}
                        format={() => `${d.score}`}
                        strokeColor={d.score >= 90 ? '#52c41a' : d.score >= 70 ? '#1677ff' : d.score >= 50 ? '#faad14' : '#ff4d4f'}
                      />
                    </Space>
                  </List.Item>
                )}
              />
              {health?.suggestions && health.suggestions.length > 0 && (
                <Card type="inner" title="治理建议" size="small" style={{ marginTop: 8 }}>
                  <ul style={{ margin: 0, paddingLeft: 20 }}>
                    {health.suggestions.map((s, i) => <li key={i}>{s}</li>)}
                  </ul>
                </Card>
              )}
            </div>
          </Space>
        </Card>
      </div>
    ),
  };

  /** 文件分布 Tab（基于空间汇总数据可视化） */
  const distributionTab: NonNullable<TabsProps['items']>[number] = {
    key: 'distribution',
    label: '文件分布',
    children: (
      <Card>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="文件总数">{space.file_count.toLocaleString()}</Descriptions.Item>
            <Descriptions.Item label="冷文件数">{space.cold_file_count.toLocaleString()}</Descriptions.Item>
            <Descriptions.Item label="冷文件占比">
              <Progress percent={Math.round((space.cold_file_count / Math.max(space.file_count, 1)) * 100)} size="small" />
            </Descriptions.Item>
            <Descriptions.Item label="归档存储量">{formatFileSize(space.archived_bytes)}</Descriptions.Item>
          </Descriptions>
          <Empty description="按类型/大小/敏感等级分布详细数据需调用文件分析接口" />
        </Space>
      </Card>
    ),
  };

  /** 链路健康 Tab */
  const chainTab: NonNullable<TabsProps['items']>[number] = {
    key: 'chain',
    label: '链路健康',
    children: (
      <Card>
        <Paragraph type="secondary">
          展示该空间下文件解析、索引、关系图谱、向量索引等链路的健康状态。
          点击"前往对账"可触发一致性检查；点击"前往治愈"可执行修复操作。
        </Paragraph>
        <Space wrap>
          <Button type="primary" onClick={() => navigate('/ops/consistency')}>
            前往对账
          </Button>
          <Button onClick={() => navigate('/ops/heal')}>
            前往治愈
          </Button>
        </Space>
      </Card>
    ),
  };

  /** 成员管理 Tab */
  const memberColumns = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '昵称', dataIndex: 'nickname', key: 'nickname' },
    {
      title: '角色', dataIndex: 'role', key: 'role',
      render: (role: SpaceMember['role']) => (
        <Tag color={role === 'OWNER' ? 'gold' : role === 'MAINTAINER' ? 'blue' : 'default'}>
          {SpaceMemberRoleLabel[role]}
        </Tag>
      ),
    },
    { title: '加入时间', dataIndex: 'joined_at', key: 'joined_at', render: (v: string) => formatDateTime(v) },
    { title: '最近活跃', dataIndex: 'last_active_at', key: 'last_active_at', render: (v: string) => formatDateTime(v) },
  ];
  const membersTab: NonNullable<TabsProps['items']>[number] = {
    key: 'members',
    label: '成员管理',
    children: (
      <Card
        title={<Space><TeamOutlined /><span>成员列表</span></Space>}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => membersQ.refetch()}>刷新</Button>
            {isPlatformAdmin && (
              <Button type="primary" icon={<UserAddOutlined />} onClick={() => message.info('添加成员功能待接入')}>
                添加成员
              </Button>
            )}
            {isPlatformAdmin && (
              <Button icon={<ExportOutlined />} onClick={() => setTransferOpen(true)}>
                移交负责人
              </Button>
            )}
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={memberColumns}
          dataSource={members}
          pagination={false}
          size="small"
        />
      </Card>
    ),
  };

  /** 配额日志 Tab */
  const quotaColumns = [
    { title: '变更时间', dataIndex: 'created_at', key: 'created_at', render: (v: string) => formatDateTime(v) },
    {
      title: '存储配额',
      key: 'storage',
      render: (_: unknown, r: QuotaLog) => `${formatFileSize(r.old_storage_quota)} → ${formatFileSize(r.new_storage_quota)}`,
    },
    {
      title: '文件配额',
      key: 'file_quota',
      render: (_: unknown, r: QuotaLog) => `${r.old_file_quota} → ${r.new_file_quota}`,
    },
    { title: '操作人', dataIndex: 'operator_name', key: 'operator_name' },
    { title: '工单号', dataIndex: 'ticket_id', key: 'ticket_id' },
    { title: '原因', dataIndex: 'reason', key: 'reason' },
  ];
  const quotaTab: NonNullable<TabsProps['items']>[number] = {
    key: 'quota',
    label: '配额日志',
    children: (
      <Card>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<ReloadOutlined />} onClick={() => quotaQ.refetch()}>刷新</Button>
          {can('ticket:apply') && (
            <OpsTicketButton
              ticketType="QUOTA"
              teamSpaceId={space.id}
              teamSpaceName={space.name}
              targetRef={`space:${space.id}`}
              impactPreview={{
                current_quota_gb: Math.round(space.storage_quota / 1024 ** 3),
                current_used_gb: Math.round(space.storage_used / 1024 ** 3),
                usage_rate: Math.round((space.storage_used / space.storage_quota) * 100) / 100,
              }}
              buttonText="申请扩容"
            />
          )}
        </Space>
        <Table
          rowKey="id"
          columns={quotaColumns}
          dataSource={quotaLogs}
          size="small"
          pagination={{
            current: quotaPage.page, pageSize: quotaPage.pageSize, total: quotaTotal,
            onChange: (page, pageSize) => setQuotaPage({ page, pageSize }),
          }}
        />
      </Card>
    ),
  };

  /** 操作事件 Tab */
  const eventColumns = [
    { title: '时间', dataIndex: 'created_at', key: 'created_at', render: (v: string) => formatDateTime(v) },
    {
      title: '事件类型', dataIndex: 'event_type', key: 'event_type',
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    { title: '操作人', dataIndex: 'operator_name', key: 'operator_name' },
    { title: '文件 ID', dataIndex: 'file_id', key: 'file_id' },
    {
      title: '元信息', dataIndex: 'meta', key: 'meta',
      render: (meta: Record<string, unknown>) => (
        <Text code style={{ fontSize: 12 }}>{JSON.stringify(meta)}</Text>
      ),
    },
  ];
  const eventsTab: NonNullable<TabsProps['items']>[number] = {
    key: 'events',
    label: '操作事件',
    children: (
      <Card>
        <Button icon={<ReloadOutlined />} onClick={() => eventsQ.refetch()} style={{ marginBottom: 12 }}>
          刷新
        </Button>
        <Table
          rowKey="id"
          columns={eventColumns}
          dataSource={events}
          size="small"
          pagination={{
            current: eventPage.page, pageSize: eventPage.pageSize, total: eventTotal,
            onChange: (page, pageSize) => setEventPage({ page, pageSize }),
          }}
        />
      </Card>
    ),
  };

  /** 移交负责人提交 */
  const handleTransfer = async () => {
    try {
      const values = await transferForm.validateFields();
      await transfer.mutateAsync({ id: space.id, new_owner_id: Number(values.new_owner_id) });
      message.success('负责人已移交');
      setTransferOpen(false);
      transferForm.resetFields();
      spaceQ.refetch();
    } catch (err) {
      if (err instanceof Error) message.error(err.message);
    }
  };

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/ops/spaces')}>返回</Button>
            <Title level={5} style={{ margin: 0 }}>{space.name}</Title>
            <Text code>{space.code}</Text>
            <StatusTag color={lifecycleTag.color} text={lifecycleTag.text} />
          </Space>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => { spaceQ.refetch(); healthQ.refetch(); }}>
              刷新
            </Button>
          </Space>
        </Space>
      </Card>

      <Card bordered={false}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[overviewTab, distributionTab, chainTab, membersTab, quotaTab, eventsTab]}
        />
      </Card>

      <Modal
        open={transferOpen}
        title="移交空间负责人"
        onCancel={() => setTransferOpen(false)}
        onOk={handleTransfer}
        confirmLoading={transfer.isPending}
        destroyOnClose
      >
        <Form form={transferForm} layout="vertical" preserve={false}>
          <Form.Item
            name="new_owner_id"
            label="新负责人用户 ID"
            rules={[{ required: true, message: '请输入新负责人用户 ID' }]}
          >
            <Input placeholder="如 102" type="number" />
          </Form.Item>
          <Paragraph type="warning">
            移交后，原负责人将变为 MAINTAINER 角色，新负责人将获得 OWNER 权限。
          </Paragraph>
        </Form>
      </Modal>
    </div>
  );
};

export default SpaceDetailPage;
