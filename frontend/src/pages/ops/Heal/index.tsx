/**
 * D3 链路治愈操作台
 * - 三栏布局：左（操作类型选择）+ 中（目标筛选与列表）+ 右（执行与进度）
 * - 单文件免审批操作：单文件重索引、单文件重解析
 * - 批量高风险操作：批量重索引、重建图、重建向量、清理孤儿、强制删除
 *   走工单审批流程，OpsTicketButton 触发
 * - 实时进度轮询（运行中 2s 刷新）
 */
import React, { useState } from 'react';
import {
  Card, Row, Col, Typography, Space, Button, Input, Tag, List, Progress, Empty, message, Descriptions, Statistic, Tooltip, Divider, Select,
} from 'antd';
import {
  ThunderboltOutlined, ReloadOutlined, PlayCircleOutlined, StopOutlined, EyeOutlined, CheckCircleOutlined, WarningOutlined,
} from '@ant-design/icons';
import {
  useHealJobs, useHealJobProgress, useHealPreview, useBatchHeal, useRetryIndex, useRetryParse, useCancelHealJob, useSpaces,
} from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import { useOpsStore } from '@/stores/ops';
import {
  HealJobType, HealJobTypeLabel, BATCH_HEAL_TYPES, HealJobStatusTag,
  HealJob, HealTargetFile,
} from '@/types/ops';
import { mockHealTargetFiles } from '@/mock/ops';
import { formatDateTime } from '@/utils';
import StatusTag from '@/components/ops/StatusTag';
import OpsTicketButton from '@/components/ops/OpsTicketButton';

const { Title, Text, Paragraph } = Typography;

/** 操作类型卡片定义 */
const HEAL_TYPE_GROUPS: Array<{ group: string; types: HealJobType[]; description: string }> = [
  {
    group: '免审批（单文件）',
    types: ['RETRY_INDEX', 'RETRY_PARSE'],
    description: '适用于单文件失败重试，无需工单审批',
  },
  {
    group: '需工单审批（批量）',
    types: BATCH_HEAL_TYPES,
    description: '适用于批量修复操作，需提交工单并经审批后执行',
  },
];

const HealPage: React.FC = () => {
  const { can } = useOpsPermission();
  const storeSpaceId = useOpsStore((s) => s.currentSpaceId);
  const setHealJobType = useOpsStore((s) => s.setHealJobType);

  const [selectedType, setSelectedType] = useState<HealJobType>('RETRY_INDEX');
  const [selectedSpaceId, setSelectedSpaceId] = useState<number | undefined>(storeSpaceId ?? undefined);
  const [jobListParams, setJobListParams] = useState<{ page: number; pageSize: number }>({ page: 1, pageSize: 10 });
  const [selectedJobId, setSelectedJobId] = useState<number | undefined>(undefined);

  const spacesQ = useSpaces({ page: 1, pageSize: 100 });
  const spaces = spacesQ.data?.data?.list ?? [];

  const jobsQ = useHealJobs({ ...jobListParams, team_space_id: selectedSpaceId });
  const jobProgressQ = useHealJobProgress(selectedJobId);
  const previewM = useHealPreview();
  const batchHealM = useBatchHeal();
  const retryIndexM = useRetryIndex();
  const retryParseM = useRetryParse();
  const cancelM = useCancelHealJob();

  const jobs = jobsQ.data?.data?.list ?? [];
  const jobsTotal = jobsQ.data?.data?.total ?? 0;
  const currentJob = jobProgressQ.data?.data;

  /** 判断是否免审批类型 */
  const isNoApproval = ['RETRY_INDEX', 'RETRY_PARSE'].includes(selectedType);

  /** 模拟目标列表（实际应调用 useHealTargets） */
  const targets: HealTargetFile[] = mockHealTargetFiles;

  /** 单文件免审批操作 */
  const handleRetrySingle = async (fileId: number, type: 'RETRY_INDEX' | 'RETRY_PARSE') => {
    try {
      const m = type === 'RETRY_INDEX' ? retryIndexM : retryParseM;
      const res = await m.mutateAsync(fileId);
      const jobId = (res as { data?: { job_id?: number } })?.data?.job_id ?? 0;
      message.success(`已提交${HealJobTypeLabel[type]}任务 #${jobId}`);
      setSelectedJobId(jobId);
      jobsQ.refetch();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    }
  };

  /** 批量治愈预览 */
  const handlePreview = async () => {
    try {
      await previewM.mutateAsync({ job_type: selectedType, filter: { team_space_id: selectedSpaceId } });
    } catch (err) {
      message.error(err instanceof Error ? err.message : '预览失败');
    }
  };

  /** 批量治愈直接执行（仅用于 demo，实际应通过工单审批后触发） */
  const handleBatchHeal = async () => {
    if (!selectedSpaceId) {
      message.warning('请先选择目标空间');
      return;
    }
    try {
      const res = await batchHealM.mutateAsync({
        job_type: selectedType,
        team_space_id: selectedSpaceId,
        filter: {},
      });
      const jobId = (res as { data?: { job_id?: number } })?.data?.job_id ?? 0;
      message.success(`已提交批量治愈任务 #${jobId}`);
      setSelectedJobId(jobId);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    }
  };

  /** 取消任务 */
  const handleCancel = async (id: number) => {
    try {
      await cancelM.mutateAsync(id);
      message.success('已取消任务');
      jobsQ.refetch();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '取消失败');
    }
  };

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Space>
          <ThunderboltOutlined style={{ fontSize: 20, color: '#1677ff' }} />
          <Title level={5} style={{ margin: 0 }}>链路治愈操作台</Title>
          <Text type="secondary">D3 · 文件解析/索引/图谱/向量链路修复</Text>
        </Space>
      </Card>

      <Row gutter={12}>
        {/* 左栏：操作类型 */}
        <Col span={6}>
          <Card title="选择操作类型" size="small" bordered={false} style={{ height: '100%' }}>
            {HEAL_TYPE_GROUPS.map((group) => (
              <div key={group.group} style={{ marginBottom: 16 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>{group.group}</Text>
                <Tooltip title={group.description}>
                  <List
                    size="small"
                    dataSource={group.types}
                    renderItem={(t) => (
                      <List.Item
                        style={{
                          cursor: 'pointer',
                          background: selectedType === t ? '#e6f4ff' : undefined,
                          borderLeft: selectedType === t ? '3px solid #1677ff' : '3px solid transparent',
                          paddingLeft: 8,
                        }}
                        onClick={() => { setSelectedType(t); setHealJobType(t); }}
                      >
                        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                          <span>{HealJobTypeLabel[t]}</span>
                          {selectedType === t && <CheckCircleOutlined style={{ color: '#1677ff' }} />}
                        </Space>
                      </List.Item>
                    )}
                  />
                </Tooltip>
              </div>
            ))}
            <Divider style={{ margin: '8px 0' }} />
            <Text type="secondary" style={{ fontSize: 12 }}>目标空间</Text>
            <Input
              placeholder="空间 ID"
              type="number"
              value={selectedSpaceId}
              onChange={(e) => setSelectedSpaceId(e.target.value ? Number(e.target.value) : undefined)}
              style={{ marginTop: 4 }}
            />
            {spaces.length > 0 && (
              <Select
                placeholder="或选择空间"
                style={{ width: '100%', marginTop: 4 }}
                value={selectedSpaceId}
                onChange={(v) => setSelectedSpaceId(v)}
                options={spaces.map((s) => ({ value: s.id, label: `${s.name} (${s.code})` }))}
              />
            )}
          </Card>
        </Col>

        {/* 中栏：目标筛选与列表 */}
        <Col span={10}>
          <Card
            title="目标列表"
            size="small"
            bordered={false}
            extra={
              <Space>
                <Button size="small" icon={<ReloadOutlined />} onClick={handlePreview}>预览影响</Button>
              </Space>
            }
          >
            {previewM.data?.data && (
              <Descriptions size="small" column={3} bordered style={{ marginBottom: 8 }}>
                <Descriptions.Item label="目标数">{previewM.data.data.target_count}</Descriptions.Item>
                <Descriptions.Item label="预计耗时">{previewM.data.data.est_minutes} 分钟</Descriptions.Item>
                <Descriptions.Item label="风险">
                  <Tag color={previewM.data.data.risk === 'low' ? 'green' : previewM.data.data.risk === 'mid' ? 'orange' : 'red'}>
                    {previewM.data.data.risk}
                  </Tag>
                </Descriptions.Item>
              </Descriptions>
            )}

            <List
              size="small"
              dataSource={targets}
              renderItem={(t) => (
                <List.Item
                  actions={
                    isNoApproval && can('heal:self')
                      ? [
                          <Button
                            key="retry"
                            type="link"
                            size="small"
                            onClick={() => handleRetrySingle(t.file_id, selectedType as 'RETRY_INDEX' | 'RETRY_PARSE')}
                            loading={retryIndexM.isPending || retryParseM.isPending}
                          >
                            执行
                          </Button>,
                        ]
                      : undefined
                  }
                >
                  <List.Item.Meta
                    title={<Text>{t.file_name} <Tag color="red">{t.error_code}</Tag></Text>}
                    description={
                      <Space size={4}>
                        <Text type="secondary">#{t.file_id}</Text>
                        <Text type="secondary">{t.team_space_name}</Text>
                        <Text type="secondary">{formatDateTime(t.created_at)}</Text>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />

            {!isNoApproval && can('heal') && (
              <div style={{ marginTop: 12, padding: 8, background: '#fffbe6', border: '1px solid #ffe58f', borderRadius: 4 }}>
                <Paragraph style={{ margin: 0, marginBottom: 8 }}>
                  <WarningOutlined /> 该操作需通过工单审批后执行
                </Paragraph>
                <Space>
                  <OpsTicketButton
                    ticketType={selectedType as never}
                    teamSpaceId={selectedSpaceId}
                    teamSpaceName={spaces.find((s) => s.id === selectedSpaceId)?.name}
                    targetRef={`space:${selectedSpaceId ?? 0}`}
                    params={{ job_type: selectedType }}
                    impactPreview={(previewM.data?.data ?? {}) as Record<string, unknown>}
                    buttonText="提交工单"
                  />
                  <Button onClick={handleBatchHeal} loading={batchHealM.isPending}>
                    直接执行（仅 demo）
                  </Button>
                </Space>
              </div>
            )}
          </Card>
        </Col>

        {/* 右栏：执行进度 */}
        <Col span={8}>
          <Card title="执行进度" size="small" bordered={false}>
            {currentJob ? (
              <div>
                <Descriptions size="small" column={1} bordered>
                  <Descriptions.Item label="任务 ID">{currentJob.id}</Descriptions.Item>
                  <Descriptions.Item label="类型">{HealJobTypeLabel[currentJob.job_type]}</Descriptions.Item>
                  <Descriptions.Item label="空间">{currentJob.team_space_name}</Descriptions.Item>
                  <Descriptions.Item label="操作人">{currentJob.operator_name}</Descriptions.Item>
                  <Descriptions.Item label="工单号">{currentJob.ticket_id || '免审批'}</Descriptions.Item>
                  <Descriptions.Item label="状态">
                    {(() => {
                      const tag = HealJobStatusTag[currentJob.status];
                      return <StatusTag color={tag.color} text={tag.text} />;
                    })()}
                  </Descriptions.Item>
                </Descriptions>

                <div style={{ marginTop: 12 }}>
                  <Text>进度</Text>
                  <Progress percent={currentJob.progress} status={currentJob.status === 4 ? 'exception' : currentJob.status === 2 ? 'success' : 'active'} />
                </div>

                <Row gutter={8} style={{ marginTop: 12 }}>
                  <Col span={6}><Statistic title="目标" value={currentJob.target_count} /></Col>
                  <Col span={6}><Statistic title="成功" value={currentJob.success_count} valueStyle={{ color: '#52c41a' }} /></Col>
                  <Col span={6}><Statistic title="失败" value={currentJob.failed_count} valueStyle={{ color: currentJob.failed_count > 0 ? '#ff4d4f' : undefined }} /></Col>
                  <Col span={6}><Statistic title="跳过" value={currentJob.skipped_count} /></Col>
                </Row>

                {Object.keys(currentJob.error_summary).length > 0 && (
                  <Card type="inner" title="错误汇总" size="small" style={{ marginTop: 12 }}>
                    {Object.entries(currentJob.error_summary).map(([k, v]) => (
                      <Tag key={k} color="red">{k}: {v}</Tag>
                    ))}
                  </Card>
                )}

                <Space style={{ marginTop: 12 }}>
                  <Button
                    icon={<StopOutlined />}
                    danger
                    onClick={() => handleCancel(currentJob.id)}
                    disabled={currentJob.status !== 0 && currentJob.status !== 1}
                    loading={cancelM.isPending}
                  >
                    取消任务
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={() => jobProgressQ.refetch()}>刷新</Button>
                </Space>
              </div>
            ) : (
              <Empty description="选择任务以查看进度" />
            )}
          </Card>
        </Col>
      </Row>

      <Card
        bordered={false}
        style={{ marginTop: 12 }}
        title={<Space><PlayCircleOutlined /><span>治愈任务历史</span></Space>}
        extra={<Button icon={<ReloadOutlined />} onClick={() => jobsQ.refetch()}>刷新</Button>}
      >
        <List
          dataSource={jobs}
          renderItem={(j: HealJob) => {
            const tag = HealJobStatusTag[j.status];
            return (
              <List.Item
                actions={[
                  <Button
                    key="view"
                    type="link"
                    size="small"
                    icon={<EyeOutlined />}
                    onClick={() => setSelectedJobId(j.id)}
                  >
                    查看进度
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <Text strong>#{j.id}</Text>
                      <Tag color="blue">{HealJobTypeLabel[j.job_type]}</Tag>
                      <StatusTag color={tag.color} text={tag.text} />
                      <Text type="secondary">{j.team_space_name}</Text>
                    </Space>
                  }
                  description={
                    <Space size={12}>
                      <Text type="secondary">操作人: {j.operator_name}</Text>
                      <Text type="secondary">目标: {j.target_count}</Text>
                      <Text type="secondary" style={{ color: j.success_count === j.target_count ? '#52c41a' : undefined }}>
                        成功: {j.success_count}
                      </Text>
                      {j.failed_count > 0 && <Text type="secondary" style={{ color: '#ff4d4f' }}>失败: {j.failed_count}</Text>}
                      <Text type="secondary">{formatDateTime(j.started_at)}</Text>
                    </Space>
                  }
                />
              </List.Item>
            );
          }}
          pagination={{
            current: jobListParams.page, pageSize: jobListParams.pageSize, total: jobsTotal,
            onChange: (page, pageSize) => setJobListParams({ page, pageSize }),
          }}
        />
      </Card>
    </div>
  );
};

export default HealPage;
