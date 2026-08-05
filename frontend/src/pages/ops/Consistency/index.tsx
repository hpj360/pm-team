/**
 * D2 一致性对账
 * - 上：对账任务列表（按检查类型/空间/状态筛选）
 * - 下：选中任务的不一致明细 + 一键修复
 * - 触发对账：选择类型 + 空间，提交后轮询任务状态
 */
import React, { useState } from 'react';
import {
  Card, Row, Col, Space, Button, Select, Input, Typography, message, Tag, Modal, Empty, Statistic,
} from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { PlayCircleOutlined, ReloadOutlined, ThunderboltOutlined, CheckCircleOutlined } from '@ant-design/icons';
import {
  useConsistencyResults, useConsistencyDiffs, useRunConsistency, useFixConsistency,
} from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import {
  ConsistencyCheck, ConsistencyDiff, CheckType, CheckTypeLabel, CheckStatusTag,
  ALL_CHECK_TYPES, DiffStatusTag, DiffActionLabel,
} from '@/types/ops';
import { formatDateTime } from '@/utils';
import StatusTag from '@/components/ops/StatusTag';

const { Title, Text, Paragraph } = Typography;

const ConsistencyPage: React.FC = () => {
  const { can } = useOpsPermission();

  const [listParams, setListParams] = useState<{ q?: string; team_space_id?: number; check_type?: CheckType; page: number; pageSize: number }>({
    page: 1, pageSize: 10,
  });
  const [selectedCheckId, setSelectedCheckId] = useState<number | undefined>(undefined);
  const [diffParams, setDiffParams] = useState<{ page: number; pageSize: number }>({ page: 1, pageSize: 10 });
  const [runOpen, setRunOpen] = useState(false);
  const [runForm, setRunForm] = useState<{ check_type?: CheckType; team_space_id?: number }>({});
  const [selectedDiffIds, setSelectedDiffIds] = useState<number[]>([]);

  const listQ = useConsistencyResults(listParams);
  const diffQ = useConsistencyDiffs(selectedCheckId, diffParams);
  const runMutation = useRunConsistency();
  const fixMutation = useFixConsistency();

  const checks = listQ.data?.data?.list ?? [];
  const total = listQ.data?.data?.total ?? 0;
  const diffs = diffQ.data?.data?.list ?? [];
  const diffTotal = diffQ.data?.data?.total ?? 0;

  /** 概览统计 */
  const stats = {
    total: checks.length,
    abnormal: checks.filter((c) => c.status === 2).length,
    running: checks.filter((c) => c.status === 0).length,
    totalDiffs: checks.reduce((s, c) => s + c.diff_count, 0),
  };

  const handleRun = async () => {
    if (!runForm.check_type) {
      message.warning('请选择对账类型');
      return;
    }
    try {
      const res = await runMutation.mutateAsync({
        check_type: runForm.check_type,
        team_space_id: runForm.team_space_id,
      });
      const checkId = (res as { data?: { check_id?: number } })?.data?.check_id ?? 0;
      message.success(`对账任务已创建 (#${checkId})`);
      setRunOpen(false);
      setRunForm({});
      listQ.refetch();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '触发对账失败');
    }
  };

  const handleFix = async () => {
    if (!selectedCheckId || selectedDiffIds.length === 0) {
      message.warning('请先选择要修复的差异项');
      return;
    }
    try {
      const res = await fixMutation.mutateAsync({ check_id: selectedCheckId, diff_ids: selectedDiffIds });
      const jobId = (res as { data?: { job_id?: number } })?.data?.job_id ?? 0;
      message.success(`已触发修复任务 (#${jobId})，可在治愈操作台查看进度`);
      setSelectedDiffIds([]);
      diffQ.refetch();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '修复失败');
    }
  };

  const checkColumns: ProColumns<ConsistencyCheck>[] = [
    {
      title: '任务 ID',
      dataIndex: 'id',
      width: 80,
    },
    {
      title: '检查类型',
      dataIndex: 'check_type',
      width: 130,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(CheckTypeLabel).map(([k, v]) => [k, { text: v }])),
      render: (_, r) => <Tag color="blue">{CheckTypeLabel[r.check_type]}</Tag>,
    },
    { title: '空间', dataIndex: 'team_space_name', width: 140 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, r) => {
        const tag = CheckStatusTag[r.status];
        return <StatusTag color={tag.color} text={tag.text} />;
      },
    },
    { title: '检查总数', dataIndex: 'total_checked', width: 100, render: (v) => Number(v).toLocaleString() },
    {
      title: '差异数',
      dataIndex: 'diff_count',
      width: 90,
      render: (v, r) => (
        <a
          style={{ color: r.diff_count > 0 ? '#ff4d4f' : undefined, fontWeight: r.diff_count > 0 ? 'bold' : undefined }}
          onClick={() => { setSelectedCheckId(r.id); setDiffParams({ page: 1, pageSize: 10 }); }}
        >
          {Number(v).toLocaleString()}
        </a>
      ),
    },
    { title: '开始时间', dataIndex: 'started_at', width: 160, render: (v) => v ? formatDateTime(v as string) : '-' },
    { title: '结束时间', dataIndex: 'finished_at', width: 160, render: (v) => v ? formatDateTime(v as string) : '-' },
    {
      title: '操作',
      width: 120,
      fixed: 'right',
      render: (_, r) => (
        <Button
          type="link"
          size="small"
          onClick={() => { setSelectedCheckId(r.id); setDiffParams({ page: 1, pageSize: 10 }); }}
          disabled={r.diff_count === 0}
        >
          查看差异
        </Button>
      ),
    },
  ];

  const diffColumns: ProColumns<ConsistencyDiff>[] = [
    { title: '差异 ID', dataIndex: 'id', width: 80 },
    { title: '文件 ID', dataIndex: 'file_id', width: 100 },
    { title: '对象 Key', dataIndex: 'object_key', width: 240, ellipsis: true },
    { title: '差异类型', dataIndex: 'diff_type', width: 200 },
    {
      title: '详情',
      dataIndex: 'detail',
      width: 200,
      render: (v) => <Text code style={{ fontSize: 12 }}>{JSON.stringify(v)}</Text>,
    },
    {
      title: '建议动作',
      dataIndex: 'suggested_action',
      width: 110,
      render: (_, r) => <Tag color="orange">{DiffActionLabel[r.suggested_action]}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, r) => {
        const tag = DiffStatusTag[r.status];
        return <StatusTag color={tag.color} text={tag.text} />;
      },
    },
    { title: '发现时间', dataIndex: 'found_at', width: 160, render: (v) => formatDateTime(v as string) },
  ];

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Row gutter={16}>
          <Col span={6}><Statistic title="对账任务总数" value={stats.total} /></Col>
          <Col span={6}><Statistic title="异常任务" value={stats.abnormal} valueStyle={{ color: stats.abnormal > 0 ? '#ff4d4f' : undefined }} /></Col>
          <Col span={6}><Statistic title="运行中" value={stats.running} valueStyle={{ color: stats.running > 0 ? '#1677ff' : undefined }} /></Col>
          <Col span={6}><Statistic title="差异总数" value={stats.totalDiffs} valueStyle={{ color: stats.totalDiffs > 0 ? '#faad14' : undefined }} /></Col>
        </Row>
      </Card>

      <Card bordered={false} style={{ marginBottom: 12 }}>
        <ProTable<ConsistencyCheck>
          rowKey="id"
          columns={checkColumns}
          dataSource={checks}
          loading={listQ.isLoading}
          scroll={{ x: 1300 }}
          search={false}
          pagination={{
            current: listParams.page, pageSize: listParams.pageSize, total,
            showSizeChanger: true,
            onChange: (page, pageSize) => setListParams({ ...listParams, page, pageSize }),
          }}
          headerTitle={
            <Space>
              <Title level={5} style={{ margin: 0 }}>一致性对账</Title>
              <Text type="secondary">D2 · 多存储一致性检查与差异修复</Text>
            </Space>
          }
          toolBarRender={() => [
            <Select<CheckType>
              key="type"
              placeholder="检查类型"
              allowClear
              style={{ width: 140 }}
              onChange={(v) => setListParams({ ...listParams, check_type: v, page: 1 })}
              options={ALL_CHECK_TYPES.map((t) => ({ value: t, label: CheckTypeLabel[t] }))}
            />,
            <Input.Search
              key="space"
              placeholder="空间名称"
              allowClear
              style={{ width: 180 }}
              onSearch={(v) => setListParams({ ...listParams, q: v || undefined, page: 1 })}
            />,
            <Button key="refresh" icon={<ReloadOutlined />} onClick={() => listQ.refetch()}>刷新</Button>,
            can('consistency') && (
              <Button key="run" type="primary" icon={<PlayCircleOutlined />} onClick={() => setRunOpen(true)}>
                触发对账
              </Button>
            ),
          ]}
        />
      </Card>

      <Card
        bordered={false}
        title={
          <Space>
            <ThunderboltOutlined />
            <span>差异明细</span>
            {selectedCheckId && <Tag color="blue">任务 #{selectedCheckId}</Tag>}
          </Space>
        }
        extra={
          <Space>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={handleFix}
              disabled={selectedDiffIds.length === 0 || !can('heal')}
              loading={fixMutation.isPending}
            >
              一键修复（{selectedDiffIds.length}）
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => diffQ.refetch()} disabled={!selectedCheckId}>
              刷新
            </Button>
          </Space>
        }
      >
        {!selectedCheckId ? (
          <Empty description="请选择对账任务查看差异明细" />
        ) : (
          <ProTable<ConsistencyDiff>
            rowKey="id"
            columns={diffColumns}
            dataSource={diffs}
            loading={diffQ.isLoading}
            scroll={{ x: 1300 }}
            search={false}
            rowSelection={{
              selectedRowKeys: selectedDiffIds,
              onChange: (keys) => setSelectedDiffIds(keys as number[]),
              getCheckboxProps: (r) => ({ disabled: r.status !== 0 }),
            }}
            pagination={{
              current: diffParams.page, pageSize: diffParams.pageSize, total: diffTotal,
              onChange: (page, pageSize) => setDiffParams({ page, pageSize }),
            }}
          />
        )}
      </Card>

      <Modal
        open={runOpen}
        title="触发一致性对账"
        onCancel={() => setRunOpen(false)}
        onOk={handleRun}
        confirmLoading={runMutation.isPending}
        destroyOnClose
      >
        <Paragraph type="secondary">选择对账类型与目标空间后提交。大空间对账可能耗时几分钟。</Paragraph>
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Select<CheckType>
            placeholder="选择检查类型"
            style={{ width: '100%' }}
            value={runForm.check_type}
            onChange={(v) => setRunForm({ ...runForm, check_type: v })}
            options={ALL_CHECK_TYPES.map((t) => ({ value: t, label: CheckTypeLabel[t] }))}
          />
          <Input
            placeholder="空间 ID（可选，留空则全空间）"
            type="number"
            value={runForm.team_space_id}
            onChange={(e) => setRunForm({ ...runForm, team_space_id: e.target.value ? Number(e.target.value) : undefined })}
          />
        </Space>
      </Modal>
    </div>
  );
};

export default ConsistencyPage;
