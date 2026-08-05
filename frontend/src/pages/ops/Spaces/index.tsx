/**
 * D1 空间台账 - 列表页
 * - 全部空间健康度概览
 * - 支持名称/编码搜索、生命周期筛选、健康分排序
 * - 行点击进入详情页（含 6 个 Tab）
 */
import React, { useMemo, useState } from 'react';
import { Card, Input, Select, Space, Button, Typography, message, Tooltip, Progress, Row, Col, Statistic } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { ReloadOutlined, EyeOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useSpaces, usePatchSpaceStatus } from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import { useOpsStore } from '@/stores/ops';
import {
  TeamSpace,
  SpaceLifecycleStatus,
  SpaceLifecycleTag,
  SpaceLifecycleLabel,
} from '@/types/ops';
import { formatDateTime, formatFileSize } from '@/utils';
import StatusTag from '@/components/ops/StatusTag';
import HealthScoreGauge from '@/components/ops/HealthScoreGauge';
import OpsTicketButton from '@/components/ops/OpsTicketButton';

const { Title, Text } = Typography;

/** 健康分小条 */
const HealthBar: React.FC<{ score: number }> = ({ score }) => {
  const color = score >= 90 ? '#52c41a' : score >= 70 ? '#1677ff' : score >= 50 ? '#faad14' : '#ff4d4f';
  return (
    <Tooltip title={`健康分 ${score}`}>
      <Progress percent={score} size="small" strokeColor={color} format={(p) => `${p}`} />
    </Tooltip>
  );
};

const SpacesPage: React.FC = () => {
  const navigate = useNavigate();
  const { can, isPlatformAdmin } = useOpsPermission();
  const setCurrentSpaceId = useOpsStore((s) => s.setCurrentSpaceId);

  const [params, setParams] = useState<{ q?: string; lifecycle?: SpaceLifecycleStatus; page: number; pageSize: number }>({
    page: 1,
    pageSize: 10,
  });

  const { data, isLoading, refetch, isFetching } = useSpaces(params);
  const patchStatus = usePatchSpaceStatus();

  const spaces = data?.data?.list ?? [];
  const total = data?.data?.total ?? 0;

  /** 概览统计 */
  const stats = useMemo(() => {
    const totalSpaces = spaces.length;
    const totalFiles = spaces.reduce((s, x) => s + x.file_count, 0);
    const totalUsed = spaces.reduce((s, x) => s + x.storage_used, 0);
    const totalQuota = spaces.reduce((s, x) => s + x.storage_quota, 0);
    const avgHealth = totalSpaces ? Math.round(spaces.reduce((s, x) => s + x.health_score, 0) / totalSpaces) : 0;
    return { totalSpaces, totalFiles, totalUsed, totalQuota, avgHealth };
  }, [spaces]);

  const handleRowClick = (record: TeamSpace) => {
    setCurrentSpaceId(record.id);
    navigate(`/ops/spaces/${record.id}`);
  };

  const handleLifecycleChange = async (record: TeamSpace, status: SpaceLifecycleStatus) => {
    try {
      await patchStatus.mutateAsync({ id: record.id, status, version: record.version });
      message.success(`空间状态已更新为 ${SpaceLifecycleLabel[status]}`);
      refetch();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    }
  };

  const columns: ProColumns<TeamSpace>[] = [
    {
      title: '空间编码',
      dataIndex: 'code',
      width: 120,
      fixed: 'left',
      render: (_, r) => <Text code>{r.code}</Text>,
    },
    {
      title: '空间名称',
      dataIndex: 'name',
      width: 180,
      render: (_, r) => (
        <a onClick={() => handleRowClick(r)}>{r.name}</a>
      ),
    },
    {
      title: '负责人',
      dataIndex: 'owner_name',
      width: 100,
      search: false,
    },
    {
      title: '成员数',
      dataIndex: 'member_count',
      width: 80,
      search: false,
      sorter: true,
    },
    {
      title: '文件数',
      dataIndex: 'file_count',
      width: 100,
      search: false,
      sorter: true,
      render: (v) => Number(v).toLocaleString(),
    },
    {
      title: '存储用量',
      dataIndex: 'storage_used',
      width: 140,
      search: false,
      render: (_, r) => (
        <Tooltip title={`配额 ${formatFileSize(r.storage_quota)}`}>
          <Progress
            percent={Math.round((r.storage_used / r.storage_quota) * 100)}
            size="small"
            format={() => `${formatFileSize(r.storage_used)}`}
            strokeColor={r.storage_used / r.storage_quota > 0.85 ? '#ff4d4f' : '#1677ff'}
          />
        </Tooltip>
      ),
    },
    {
      title: '冷文件数',
      dataIndex: 'cold_file_count',
      width: 100,
      search: false,
      render: (v) => Number(v).toLocaleString(),
    },
    {
      title: '健康分',
      dataIndex: 'health_score',
      width: 140,
      search: false,
      sorter: true,
      render: (_, r) => <HealthBar score={r.health_score} />,
    },
    {
      title: '生命周期',
      dataIndex: 'lifecycle_status',
      width: 110,
      valueType: 'select',
      valueEnum: Object.fromEntries(
        Object.entries(SpaceLifecycleLabel).map(([k, v]) => [k, { text: v }]),
      ),
      render: (_, r) => {
        const tag = SpaceLifecycleTag[r.lifecycle_status];
        return <StatusTag color={tag.color} text={tag.text} />;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'created_at',
      width: 160,
      search: false,
      render: (v) => formatDateTime(v as string),
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      search: false,
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleRowClick(r)}>
            详情
          </Button>
          {isPlatformAdmin && r.lifecycle_status === 'active' && (
            <Button type="link" size="small" danger onClick={() => handleLifecycleChange(r, 'frozen')}>
              冻结
            </Button>
          )}
          {isPlatformAdmin && r.lifecycle_status === 'frozen' && (
            <Button type="link" size="small" onClick={() => handleLifecycleChange(r, 'active')}>
              解冻
            </Button>
          )}
          {can('heal') && r.lifecycle_status === 'frozen' && (
            <OpsTicketButton
              ticketType="DESTROY"
              teamSpaceId={r.id}
              teamSpaceName={r.name}
              targetRef={`space:${r.id}`}
              params={{ space_id: r.id }}
              impactPreview={{ file_count: r.file_count, storage_gb: Math.round(r.storage_used / 1024 ** 3) }}
              buttonType="link"
              buttonSize="small"
              buttonText="销毁"
            />
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Row gutter={16}>
          <Col span={4}>
            <Statistic title="空间总数" value={stats.totalSpaces} />
          </Col>
          <Col span={4}>
            <Statistic title="文件总数" value={stats.totalFiles} />
          </Col>
          <Col span={5}>
            <Statistic
              title="存储用量"
              value={formatFileSize(stats.totalUsed)}
              suffix={`/ ${formatFileSize(stats.totalQuota)}`}
            />
          </Col>
          <Col span={4}>
            <Statistic title="平均健康分" value={stats.avgHealth} suffix="/ 100" />
          </Col>
          <Col span={7} style={{ textAlign: 'right' }}>
            <HealthScoreGauge value={stats.avgHealth} title="整体健康度" width={200} height={140} />
          </Col>
        </Row>
      </Card>

      <ProTable<TeamSpace>
        rowKey="id"
        columns={columns}
        dataSource={spaces}
        loading={isLoading || isFetching}
        scroll={{ x: 1500 }}
        search={false}
        pagination={{
          current: params.page,
          pageSize: params.pageSize,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => setParams({ ...params, page, pageSize }),
        }}
        headerTitle={
          <Space>
            <Title level={5} style={{ margin: 0 }}>空间台账</Title>
            <Text type="secondary">D1 · 团队空间资产清单与健康度</Text>
          </Space>
        }
        toolBarRender={() => [
          <Input.Search
            key="search"
            placeholder="空间名称/编码"
            allowClear
            style={{ width: 200 }}
            onSearch={(v) => setParams({ ...params, q: v || undefined, page: 1 })}
          />,
          <Select<SpaceLifecycleStatus>
            key="lifecycle"
            placeholder="生命周期"
            allowClear
            style={{ width: 120 }}
            onChange={(v) => setParams({ ...params, lifecycle: v, page: 1 })}
            options={Object.entries(SpaceLifecycleLabel).map(([k, v]) => ({ value: k as SpaceLifecycleStatus, label: v }))}
          />,
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => refetch()}>
            刷新
          </Button>,
          isPlatformAdmin && (
            <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => message.info('新建空间功能待接入')}>
              新建空间
            </Button>
          ),
        ]}
      />
    </div>
  );
};

export default SpacesPage;
