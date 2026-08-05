/**
 * D7 空间报告
 * 1. 报告列表：周报、月报、日报、异常通报
 * 2. 报告详情：摘要 + 治理建议（一键转工单）
 * 3. 订阅管理：报告订阅配置
 */
import React, { useState } from 'react';
import {
  Card, Tabs, Typography, Space, Button, Tag, Modal, Form, Select, Switch, message, Row, Col, Descriptions, List, Empty, Table,
} from 'antd';
import type { TabsProps } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import {
  ReloadOutlined, ProfileOutlined, EyeOutlined, ScheduleOutlined, DownloadOutlined, BellOutlined, PlusOutlined, DeleteOutlined,
} from '@ant-design/icons';
import {
  useReports, useReport, useReportSubscriptions, useApplySuggestion, useCreateSubscription, useDeleteSubscription,
} from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import {
  SpaceReport, ReportType, ReportTypeLabel, ALL_REPORT_TYPES, ReportSuggestion,
} from '@/types/ops';
import { formatDateTime } from '@/utils';
import HealthScoreGauge from '@/components/ops/HealthScoreGauge';

const { Title, Text } = Typography;

const ReportsPage: React.FC = () => {
  const { can } = useOpsPermission();
  const [activeTab, setActiveTab] = useState('list');

  // 报告列表
  const [listParams, setListParams] = useState<{ page: number; pageSize: number; report_type?: ReportType; team_space_id?: number }>({ page: 1, pageSize: 10 });
  const listQ = useReports(listParams);
  const reports = listQ.data?.data?.list ?? [];
  const total = listQ.data?.data?.total ?? 0;

  // 报告详情
  const [detailId, setDetailId] = useState<number | undefined>(undefined);
  const detailQ = useReport(detailId);
  const detail = detailQ.data?.data;

  // 订阅管理
  const subsQ = useReportSubscriptions();
  const subs = subsQ.data?.data ?? [];
  const applySuggestionM = useApplySuggestion();
  const createSubM = useCreateSubscription();
  const deleteSubM = useDeleteSubscription();
  const [subOpen, setSubOpen] = useState(false);
  const [subForm] = Form.useForm();

  /** 应用建议（转工单） */
  const handleApplySuggestion = async (s: ReportSuggestion) => {
    try {
      const res = await applySuggestionM.mutateAsync(s.id);
      const ticketId = (res as { data?: { ticket_id?: number } })?.data?.ticket_id ?? 0;
      message.success(`已生成工单 #${ticketId}`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    }
  };

  /** 创建订阅 */
  const handleCreateSub = async () => {
    try {
      const values = await subForm.validateFields();
      await createSubM.mutateAsync({
        report_types: Array.isArray(values.report_types) ? values.report_types.join(',') : values.report_types,
        team_space_id: values.team_space_id,
      });
      message.success('订阅已创建');
      setSubOpen(false);
      subForm.resetFields();
      subsQ.refetch();
    } catch (err) {
      if (err instanceof Error) message.error(err.message);
    }
  };

  /** 删除订阅 */
  const handleDeleteSub = async (id: number) => {
    Modal.confirm({
      title: '确认删除该订阅？',
      onOk: async () => {
        try {
          await deleteSubM.mutateAsync(id);
          message.success('已删除');
          subsQ.refetch();
        } catch (err) {
          message.error(err instanceof Error ? err.message : '删除失败');
        }
      },
    });
  };

  /** 报告列表 Tab */
  const listColumns: ProColumns<SpaceReport>[] = [
    { title: '报告 ID', dataIndex: 'id', width: 80 },
    {
      title: '类型', dataIndex: 'report_type', width: 100,
      render: (_, r) => {
        const color = r.report_type === 'ALERT' ? 'red' : r.report_type === 'MONTHLY' ? 'purple' : 'blue';
        return <Tag color={color}>{ReportTypeLabel[r.report_type]}</Tag>;
      },
    },
    { title: '空间', dataIndex: 'team_space_name', width: 140 },
    {
      title: '周期', key: 'period', width: 220,
      render: (_, r) => `${formatDateTime(r.period_start).slice(0, 10)} ~ ${formatDateTime(r.period_end).slice(0, 10)}`,
    },
    {
      title: '健康分', dataIndex: 'health_score', width: 110,
      render: (v) => {
        const score = Number(v);
        const color = score >= 90 ? '#52c41a' : score >= 70 ? '#1677ff' : score >= 50 ? '#faad14' : '#ff4d4f';
        return <Text strong style={{ color }}>{score}</Text>;
      },
    },
    {
      title: '关键指标', key: 'metrics', width: 280,
      render: (_, r) => (
        <Space size={8} wrap>
          <Tag>文件增长 {r.summary.file_growth.toLocaleString()}</Tag>
          <Tag>存储增长 {r.summary.storage_growth_gb} GB</Tag>
          <Tag color={r.summary.parse_success_rate >= 0.99 ? 'green' : 'orange'}>
            解析成功率 {(r.summary.parse_success_rate * 100).toFixed(1)}%
          </Tag>
        </Space>
      ),
    },
    { title: '生成时间', dataIndex: 'created_at', width: 160, render: (v) => formatDateTime(v as string) },
    {
      title: '操作', width: 180, fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => setDetailId(r.id)}>
            查看
          </Button>
          {r.pdf_url && (
            <Button type="link" size="small" icon={<DownloadOutlined />} href={r.pdf_url}>
              PDF
            </Button>
          )}
        </Space>
      ),
    },
  ];
  const listTab: NonNullable<TabsProps['items']>[number] = {
    key: 'list',
    label: <Space><ProfileOutlined /><span>报告列表</span></Space>,
    children: (
      <ProTable<SpaceReport>
        rowKey="id"
        columns={listColumns}
        dataSource={reports}
        loading={listQ.isLoading}
        search={false}
        scroll={{ x: 1300 }}
        pagination={{
          current: listParams.page, pageSize: listParams.pageSize, total,
          onChange: (page, pageSize) => setListParams({ ...listParams, page, pageSize }),
        }}
        headerTitle={<Title level={5} style={{ margin: 0 }}>空间报告</Title>}
        toolBarRender={() => [
          <Select<ReportType>
            key="type"
            placeholder="报告类型"
            allowClear
            style={{ width: 130 }}
            onChange={(v) => setListParams({ ...listParams, report_type: v, page: 1 })}
            options={ALL_REPORT_TYPES.map((t) => ({ value: t, label: ReportTypeLabel[t] }))}
          />,
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => listQ.refetch()}>刷新</Button>,
        ]}
      />
    ),
  };

  /** 报告详情 Tab */
  const detailTab: NonNullable<TabsProps['items']>[number] = {
    key: 'detail',
    label: <Space><EyeOutlined /><span>报告详情</span></Space>,
    children: (
      <Card>
        {!detailId ? (
          <Empty description="请在报告列表中点击查看" />
        ) : detailQ.isLoading ? (
          <Empty description="加载中..." />
        ) : detail ? (
          <div>
            <Row gutter={12}>
              <Col span={6}>
                <Card size="small">
                  <HealthScoreGauge value={detail.health_score} title="周期健康分" width={200} height={160} />
                </Card>
              </Col>
              <Col span={18}>
                <Descriptions bordered column={2} size="small">
                  <Descriptions.Item label="报告 ID">{detail.id}</Descriptions.Item>
                  <Descriptions.Item label="类型">{ReportTypeLabel[detail.report_type]}</Descriptions.Item>
                  <Descriptions.Item label="空间">{detail.team_space_name}</Descriptions.Item>
                  <Descriptions.Item label="周期">
                    {formatDateTime(detail.period_start).slice(0, 10)} ~ {formatDateTime(detail.period_end).slice(0, 10)}
                  </Descriptions.Item>
                  <Descriptions.Item label="文件增长">{detail.summary.file_growth.toLocaleString()}</Descriptions.Item>
                  <Descriptions.Item label="存储增长">{detail.summary.storage_growth_gb} GB</Descriptions.Item>
                  <Descriptions.Item label="解析成功率">{(detail.summary.parse_success_rate * 100).toFixed(1)}%</Descriptions.Item>
                  <Descriptions.Item label="配额使用率">{(detail.summary.quota_usage * 100).toFixed(1)}%</Descriptions.Item>
                </Descriptions>
              </Col>
            </Row>

            <Card title="Top 错误" size="small" style={{ marginTop: 12 }}>
              <List
                size="small"
                dataSource={detail.summary.top_failures}
                renderItem={(f) => (
                  <List.Item>
                    <Space>
                      <Tag color="red">{f.error_code}</Tag>
                      <Text>出现 {f.count} 次</Text>
                    </Space>
                  </List.Item>
                )}
              />
            </Card>

            <Card
              title="治理建议"
              size="small"
              style={{ marginTop: 12 }}
              extra={can('ticket:apply') ? <Text type="secondary">支持一键转工单</Text> : null}
            >
              <List
                dataSource={detail.suggestions}
                renderItem={(s, idx) => (
                  <List.Item
                    actions={
                      can('ticket:apply')
                        ? [
                            <Button
                              key="apply"
                              type="link"
                              size="small"
                              icon={<ScheduleOutlined />}
                              loading={applySuggestionM.isPending}
                              onClick={() => handleApplySuggestion(s)}
                            >
                              转工单
                            </Button>,
                          ]
                        : undefined
                    }
                  >
                    <List.Item.Meta
                      avatar={<Tag color="blue">{idx + 1}</Tag>}
                      title={<Text>{s.desc}</Text>}
                      description={<Text type="secondary">类型：{s.type} · 工单类型：{s.ticket_type}</Text>}
                    />
                  </List.Item>
                )}
              />
            </Card>
          </div>
        ) : (
          <Empty description="报告不存在" />
        )}
      </Card>
    ),
  };

  /** 订阅管理 Tab */
  const subTab: NonNullable<TabsProps['items']>[number] = {
    key: 'subscriptions',
    label: <Space><BellOutlined /><span>订阅管理</span></Space>,
    children: (
      <Card
        title={<Title level={5} style={{ margin: 0 }}>报告订阅</Title>}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => subsQ.refetch()}>刷新</Button>
            {can('report:self') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setSubOpen(true)}>
                新建订阅
              </Button>
            )}
          </Space>
        }
      >
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={subs}
          columns={[
            { title: 'ID', dataIndex: 'id', width: 70 },
            {
              title: '报告类型', dataIndex: 'report_types', width: 200,
              render: (v: string) => v.split(',').map((t) => <Tag key={t} color="blue">{ReportTypeLabel[t as ReportType] ?? t}</Tag>),
            },
            {
              title: '目标空间', dataIndex: 'team_space_id', width: 140,
              render: (v) => v ? `空间 #${v}` : '全部空间',
            },
            { title: '渠道', dataIndex: 'channel', width: 100 },
            {
              title: '状态', dataIndex: 'enabled', width: 80,
              render: (v) => <Tag color={Number(v) === 1 ? 'green' : 'default'}>{Number(v) === 1 ? '启用' : '禁用'}</Tag>,
            },
            { title: '创建时间', dataIndex: 'created_at', width: 160, render: (v: string) => formatDateTime(v) },
            {
              title: '操作', width: 100,
              render: (_, r) => (
                <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteSub(r.id)}>
                  删除
                </Button>
              ),
            },
          ]}
        />
      </Card>
    ),
  };

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Space>
          <ProfileOutlined style={{ fontSize: 20, color: '#1677ff' }} />
          <Title level={5} style={{ margin: 0 }}>空间报告</Title>
          <Text type="secondary">D7 · 周报/月报/异常通报与订阅</Text>
        </Space>
      </Card>

      <Card bordered={false}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[listTab, detailTab, subTab]}
        />
      </Card>

      <Modal
        open={subOpen}
        title="新建报告订阅"
        onCancel={() => setSubOpen(false)}
        onOk={handleCreateSub}
        confirmLoading={createSubM.isPending}
        destroyOnClose
      >
        <Form form={subForm} layout="vertical" preserve={false}>
          <Form.Item name="report_types" label="报告类型" rules={[{ required: true, message: '请选择报告类型' }]}>
            <Select mode="multiple" placeholder="选择报告类型" options={ALL_REPORT_TYPES.map((t) => ({ value: t, label: ReportTypeLabel[t] }))} />
          </Form.Item>
          <Form.Item name="team_space_id" label="目标空间 ID（留空订阅全部）">
            <Select
              placeholder="选择空间（可留空）"
              allowClear
              options={[]}
              mode="tags"
              maxCount={1}
              tokenSeparators={[',']}
            />
          </Form.Item>
          <Form.Item name="channel" label="订阅渠道" initialValue="email">
            <Select
              options={[
                { value: 'email', label: '邮件' },
                { value: 'sms', label: '短信' },
                { value: 'im', label: '即时通讯' },
              ]}
            />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ReportsPage;
