/**
 * D4 数据生命周期
 * 1. 容量看板：趋势图、分层占比、空间预警
 * 2. 策略管理：冷热分层、过期清理策略
 * 3. 冷数据转冷：候选列表 + 批量转冷
 * 4. 配额管理：空间配额列表 + 调整
 */
import React, { useState } from 'react';
import {
  Card, Tabs, Typography, Space, Button, Table, Tag, Progress, Modal, Form, Input, InputNumber, Switch, message, Row, Col, Statistic, Empty,
} from 'antd';
import type { TabsProps } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import {
  ReloadOutlined, PlusOutlined, HddOutlined, FireOutlined, InboxOutlined, ArrowUpOutlined,
} from '@ant-design/icons';
import LazyECharts from '@/components/common/LazyECharts';
import {
  useCapacity, useLifecyclePolicies, useColdCandidates, useQuotaList, useCreateLifecyclePolicy, useArchiveFiles,
} from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import {
  LifecyclePolicy, ColdCandidate, TeamSpace,
} from '@/types/ops';
import { formatDateTime, formatFileSize } from '@/utils';
import OpsTicketButton from '@/components/ops/OpsTicketButton';

const { Title, Text } = Typography;

const LifecyclePage: React.FC = () => {
  const { can } = useOpsPermission();
  const [activeTab, setActiveTab] = useState('capacity');

  // 容量看板
  const capacityQ = useCapacity();
  const capacity = capacityQ.data?.data;

  // 策略管理
  const [policyParams, setPolicyParams] = useState<{ page: number; pageSize: number }>({ page: 1, pageSize: 10 });
  const policiesQ = useLifecyclePolicies(policyParams);
  const policies = policiesQ.data?.data?.list ?? [];
  const policyTotal = policiesQ.data?.data?.total ?? 0;
  const createPolicyM = useCreateLifecyclePolicy();
  const [policyOpen, setPolicyOpen] = useState(false);
  const [policyForm] = Form.useForm();

  // 冷数据转冷
  const [coldParams, setColdParams] = useState<{ page: number; pageSize: number; team_space_id?: number }>({ page: 1, pageSize: 10 });
  const coldQ = useColdCandidates(coldParams);
  const coldCandidates = coldQ.data?.data?.list ?? [];
  const coldTotal = coldQ.data?.data?.total ?? 0;
  const archiveM = useArchiveFiles();
  const [selectedColdIds, setSelectedColdIds] = useState<number[]>([]);

  // 配额管理
  const [quotaParams, setQuotaParams] = useState<{ page: number; pageSize: number }>({ page: 1, pageSize: 10 });
  const quotaQ = useQuotaList(quotaParams);
  const quotas = quotaQ.data?.data?.list ?? [];
  const quotaTotal = quotaQ.data?.data?.total ?? 0;

  /** 容量趋势图 */
  const trendOption = capacity ? {
    tooltip: { trigger: 'axis' },
    legend: { data: ['热存储', '冷存储', '归档'] },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: capacity.trend.map((t) => t.date) },
    yAxis: { type: 'value', name: 'GB' },
    series: [
      { name: '热存储', type: 'line', smooth: true, data: capacity.trend.map((t) => t.hot), itemStyle: { color: '#ff4d4f' } },
      { name: '冷存储', type: 'line', smooth: true, data: capacity.trend.map((t) => t.cold), itemStyle: { color: '#1677ff' } },
      { name: '归档', type: 'line', smooth: true, data: capacity.trend.map((t) => t.archived), itemStyle: { color: '#8c8c8c' } },
    ],
  } : null;

  /** 分层占比饼图 */
  const tierOption = capacity ? {
    tooltip: { trigger: 'item', formatter: '{b}: {c} GB ({d}%)' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie', radius: ['50%', '75%'],
      data: capacity.tierRatio.map((t) => ({ name: t.name, value: t.value })),
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
    }],
  } : null;

  /** 容量看板 Tab */
  const capacityTab: NonNullable<TabsProps['items']>[number] = {
    key: 'capacity',
    label: <Space><FireOutlined /><span>容量看板</span></Space>,
    children: (
      <div>
        <Row gutter={12} style={{ marginBottom: 12 }}>
          <Col span={6}><Card size="small"><Statistic title="热存储总量" value={capacity?.tierRatio.find((t) => t.name === '热存储')?.value ?? 0} suffix="GB" valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
          <Col span={6}><Card size="small"><Statistic title="冷存储总量" value={capacity?.tierRatio.find((t) => t.name === '冷存储')?.value ?? 0} suffix="GB" valueStyle={{ color: '#1677ff' }} /></Card></Col>
          <Col span={6}><Card size="small"><Statistic title="归档总量" value={capacity?.tierRatio.find((t) => t.name === '归档存储')?.value ?? 0} suffix="GB" valueStyle={{ color: '#8c8c8c' }} /></Card></Col>
          <Col span={6}>
            <Card size="small" title="容量预警（剩余天数）">
              {capacity?.predict.map((p) => (
                <Tag key={p.space} color={p.days < 15 ? 'red' : p.days < 30 ? 'orange' : 'green'} style={{ marginBottom: 4 }}>
                  {p.space}: {p.days} 天
                </Tag>
              ))}
            </Card>
          </Col>
        </Row>
        <Row gutter={12}>
          <Col span={16}>
            <Card title="存储趋势（近 7 天，单位 GB）" size="small">
              {trendOption ? <LazyECharts option={trendOption} style={{ height: 280 }} /> : <Empty />}
            </Card>
          </Col>
          <Col span={8}>
            <Card title="分层占比" size="small">
              {tierOption ? <LazyECharts option={tierOption} style={{ height: 280 }} /> : <Empty />}
            </Card>
          </Col>
        </Row>
        <Card title="Top 空间容量" size="small" style={{ marginTop: 12 }}>
          <Table
            rowKey="id"
            size="small"
            pagination={false}
            dataSource={capacity?.topSpaces ?? []}
            columns={[
              { title: '空间', dataIndex: 'name', key: 'name' },
              { title: '已用 (GB)', dataIndex: 'used', key: 'used' },
              { title: '配额 (GB)', dataIndex: 'quota', key: 'quota' },
              {
                title: '使用率', key: 'rate',
                render: (_, r: { used: number; quota: number }) => (
                  <Progress percent={Math.round((r.used / r.quota) * 100)} size="small" status={r.used / r.quota > 0.85 ? 'exception' : 'normal'} />
                ),
              },
            ]}
          />
        </Card>
      </div>
    ),
  };

  /** 策略管理 Tab */
  const policyColumns: ProColumns<LifecyclePolicy>[] = [
    { title: '策略 ID', dataIndex: 'id', width: 80 },
    { title: '策略名称', dataIndex: 'policy_name', width: 160 },
    { title: '空间', dataIndex: 'team_space_name', width: 140 },
    { title: '转冷天数', dataIndex: 'cold_after_days', width: 100, render: (v) => `${v} 天` },
    { title: '过期天数', dataIndex: 'expire_after_days', width: 100, render: (v) => `${v} 天` },
    { title: '存储类别', dataIndex: 'archive_storage_class', width: 140 },
    {
      title: '启用', dataIndex: 'enabled', width: 80,
      render: (v) => <Tag color={Number(v) === 1 ? 'green' : 'default'}>{Number(v) === 1 ? '启用' : '禁用'}</Tag>,
    },
    { title: '创建时间', dataIndex: 'created_at', width: 160, render: (v) => formatDateTime(v as string) },
  ];
  const handleCreatePolicy = async () => {
    try {
      const values = await policyForm.validateFields();
      await createPolicyM.mutateAsync(values);
      message.success('策略已创建');
      setPolicyOpen(false);
      policyForm.resetFields();
      policiesQ.refetch();
    } catch (err) {
      if (err instanceof Error) message.error(err.message);
    }
  };
  const policyTab: NonNullable<TabsProps['items']>[number] = {
    key: 'policy',
    label: <Space><HddOutlined /><span>策略管理</span></Space>,
    children: (
      <ProTable<LifecyclePolicy>
        rowKey="id"
        columns={policyColumns}
        dataSource={policies}
        loading={policiesQ.isLoading}
        search={false}
        scroll={{ x: 1100 }}
        pagination={{
          current: policyParams.page, pageSize: policyParams.pageSize, total: policyTotal,
          onChange: (page, pageSize) => setPolicyParams({ page, pageSize }),
        }}
        headerTitle={<Title level={5} style={{ margin: 0 }}>生命周期策略</Title>}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => policiesQ.refetch()}>刷新</Button>,
          can('lifecycle') && (
            <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => setPolicyOpen(true)}>
              新建策略
            </Button>
          ),
        ]}
      />
    ),
  };

  /** 冷数据转冷 Tab */
  const coldColumns: ProColumns<ColdCandidate>[] = [
    { title: '文件 ID', dataIndex: 'file_id', width: 100 },
    { title: '文件名', dataIndex: 'file_name', width: 240, ellipsis: true },
    { title: '空间', dataIndex: 'team_space_name', width: 140 },
    { title: '大小', dataIndex: 'size', width: 100, render: (v) => formatFileSize(Number(v)) },
    { title: '最后访问', dataIndex: 'last_access_at', width: 160, render: (v) => formatDateTime(v as string) },
  ];
  const handleArchive = async () => {
    if (selectedColdIds.length === 0) {
      message.warning('请选择要转冷的文件');
      return;
    }
    try {
      const res = await archiveM.mutateAsync({ file_ids: selectedColdIds });
      const jobId = (res as { data?: { job_id?: number } })?.data?.job_id ?? 0;
      message.success(`已提交转冷任务 #${jobId}`);
      setSelectedColdIds([]);
      coldQ.refetch();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    }
  };
  const coldTab: NonNullable<TabsProps['items']>[number] = {
    key: 'cold',
    label: <Space><InboxOutlined /><span>冷数据转冷</span></Space>,
    children: (
      <ProTable<ColdCandidate>
        rowKey="id"
        columns={coldColumns}
        dataSource={coldCandidates}
        loading={coldQ.isLoading}
        search={false}
        scroll={{ x: 800 }}
        rowSelection={{
          selectedRowKeys: selectedColdIds,
          onChange: (keys) => setSelectedColdIds(keys as number[]),
        }}
        pagination={{
          current: coldParams.page, pageSize: coldParams.pageSize, total: coldTotal,
          onChange: (page, pageSize) => setColdParams({ ...coldParams, page, pageSize }),
        }}
        headerTitle={<Title level={5} style={{ margin: 0 }}>冷数据候选</Title>}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => coldQ.refetch()}>刷新</Button>,
          can('lifecycle') && (
            <Button key="archive" type="primary" icon={<InboxOutlined />} onClick={handleArchive} loading={archiveM.isPending} disabled={selectedColdIds.length === 0}>
              批量转冷（{selectedColdIds.length}）
            </Button>
          ),
        ]}
      />
    ),
  };

  /** 配额管理 Tab */
  const quotaColumns: ProColumns<TeamSpace>[] = [
    { title: '空间编码', dataIndex: 'code', width: 120, render: (v) => <Text code>{v as string}</Text> },
    { title: '空间名称', dataIndex: 'name', width: 180 },
    { title: '负责人', dataIndex: 'owner_name', width: 100 },
    { title: '文件数', dataIndex: 'file_count', width: 100, render: (v) => Number(v).toLocaleString() },
    {
      title: '存储用量', dataIndex: 'storage_used', width: 200,
      render: (_, r) => (
        <Progress
          percent={Math.round((r.storage_used / r.storage_quota) * 100)}
          size="small"
          format={() => `${formatFileSize(r.storage_used)} / ${formatFileSize(r.storage_quota)}`}
          status={r.storage_used / r.storage_quota > 0.85 ? 'exception' : 'normal'}
        />
      ),
    },
    {
      title: '操作', width: 120, fixed: 'right',
      render: (_, r) => (
        <OpsTicketButton
          ticketType="QUOTA"
          teamSpaceId={r.id}
          teamSpaceName={r.name}
          targetRef={`space:${r.id}`}
          impactPreview={{
            current_quota_gb: Math.round(r.storage_quota / 1024 ** 3),
            current_used_gb: Math.round(r.storage_used / 1024 ** 3),
          }}
          buttonType="link"
          buttonSize="small"
          buttonText="申请扩容"
        />
      ),
    },
  ];
  const quotaTab: NonNullable<TabsProps['items']>[number] = {
    key: 'quota',
    label: <Space><ArrowUpOutlined /><span>配额管理</span></Space>,
    children: (
      <ProTable<TeamSpace>
        rowKey="id"
        columns={quotaColumns}
        dataSource={quotas}
        loading={quotaQ.isLoading}
        search={false}
        scroll={{ x: 900 }}
        pagination={{
          current: quotaParams.page, pageSize: quotaParams.pageSize, total: quotaTotal,
          onChange: (page, pageSize) => setQuotaParams({ page, pageSize }),
        }}
        headerTitle={<Title level={5} style={{ margin: 0 }}>空间配额</Title>}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => quotaQ.refetch()}>刷新</Button>,
        ]}
      />
    ),
  };

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Space>
          <HddOutlined style={{ fontSize: 20, color: '#1677ff' }} />
          <Title level={5} style={{ margin: 0 }}>数据生命周期</Title>
          <Text type="secondary">D4 · 容量看板 / 策略管理 / 冷数据转冷 / 配额管理</Text>
        </Space>
      </Card>

      <Card bordered={false}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[capacityTab, policyTab, coldTab, quotaTab]}
        />
      </Card>

      <Modal
        open={policyOpen}
        title="新建生命周期策略"
        onCancel={() => setPolicyOpen(false)}
        onOk={handleCreatePolicy}
        confirmLoading={createPolicyM.isPending}
        destroyOnClose
        width={560}
      >
        <Form form={policyForm} layout="vertical" preserve={false}>
          <Form.Item name="policy_name" label="策略名称" rules={[{ required: true }]}>
            <Input placeholder="如 A组标准策略" />
          </Form.Item>
          <Form.Item name="team_space_id" label="目标空间 ID（留空为全局）">
            <InputNumber placeholder="如 1" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="cold_after_days" label="转冷天数" rules={[{ required: true }]}>
            <InputNumber placeholder="如 30" min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="expire_after_days" label="过期天数" rules={[{ required: true }]}>
            <InputNumber placeholder="如 365" min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="archive_storage_class" label="归档存储类别" rules={[{ required: true }]}>
            <Input placeholder="如 STANDARD_IA / GLACIER" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default LifecyclePage;
