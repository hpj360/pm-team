/**
 * 审计日志详情页
 * - 顶部：日志基本信息 + 状态 + 操作类型
 * - 请求详情：URL/IP/UA/响应时间
 * - 用户上下文：操作者信息
 * - 操作历史：同用户近期操作
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Empty,
  Spin,
  Row,
  Col,
  Statistic,
  Tabs,
  Table,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  AuditOutlined,
  UserOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  EnvironmentOutlined,
  GlobalOutlined,
  DesktopOutlined,
  ThunderboltOutlined,
  ExportOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { mockAuditLogs } from '@/mock/adminAudit';
import { mockAdminUsers } from '@/mock/adminUser';
import type { AuditLogItem } from '@/types';
import { AuditActionLabel } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title } = Typography;

const AuditLogDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [log, setLog] = useState<AuditLogItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('detail');

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = mockAuditLogs.find((l) => l.id === id) ?? null;
      setLog(data);
      setLoading(false);
    }, 200);
  }, [id]);

  /** 同用户操作历史 */
  const userHistory = mockAuditLogs
    .filter((l) => l.username === log?.username && l.id !== log?.id)
    .slice(0, 10);

  /** 操作者信息 */
  const operator = mockAdminUsers.find((u) => u.username === log?.username) ?? null;

  /** 同资源最近 24 小时操作统计 */
  const resourceActivityChartOption: EChartsOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: ['24h 前', '20h 前', '16h 前', '12h 前', '8h 前', '4h 前', '当前'],
    },
    yAxis: { type: 'value', name: '操作次数' },
    series: [
      {
        type: 'bar',
        data: Array.from({ length: 7 }, () => Math.floor(Math.random() * 20) + 2),
        itemStyle: { color: colors.info, borderRadius: [4, 4, 0, 0] },
      },
    ],
  };

  /** 操作状态分布 */
  const statusChartOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { top: 0, left: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        data: [
          {
            value: mockAuditLogs.filter((l) => l.username === log?.username && l.status === 'success').length,
            name: '成功',
            itemStyle: { color: colors.success },
          },
          {
            value: mockAuditLogs.filter((l) => l.username === log?.username && l.status === 'failed').length,
            name: '失败',
            itemStyle: { color: colors.error },
          },
        ],
      },
    ],
  };

  /** 用户历史列 */
  const historyColumns: ColumnsType<AuditLogItem> = [
    { title: '时间', dataIndex: 'createdAt', width: 160, render: (v: string) => formatDateTime(v) },
    { title: '操作', dataIndex: 'action', width: 80, render: (v: string) => <Tag>{AuditActionLabel[v as keyof typeof AuditActionLabel] ?? v}</Tag> },
    { title: '资源', dataIndex: 'resource', render: (v: string) => <code>{v}</code> },
    { title: '详情', dataIndex: 'detail', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: string) => (
        <Tag color={v === 'success' ? 'success' : 'error'}>{v === 'success' ? '成功' : '失败'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_, record) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/admin/audit-log/${record.id}`)}>详情</Button>
      ),
    },
  ];

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载审计日志..." /></div>;
  }

  if (!log) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到审计日志">
          <Button type="primary" onClick={() => navigate('/admin/audit-log')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/audit-log')}>返回</Button>
          <AuditOutlined style={{ fontSize: 24, color: colors.info }} />
          <Title level={4} style={{ margin: 0 }}>审计日志详情</Title>
          <Tag color={log.status === 'success' ? 'success' : 'error'} icon={log.status === 'success' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}>
            {log.status === 'success' ? '成功' : '失败'}
          </Tag>
          <Tag color="blue">{AuditActionLabel[log.action]}</Tag>
        </Space>
        <Space>
          <Button icon={<ExportOutlined />} onClick={() => message.success('导出日志...')}>导出</Button>
          <Button icon={<UserOutlined />} onClick={() => operator && navigate(`/admin/users/${operator.id}`)}>查看用户</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="响应时间" value={log.costMs} suffix="ms" prefix={<ThunderboltOutlined />} valueStyle={{ color: log.costMs > 500 ? colors.error : colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="操作者" value={log.username} valueStyle={{ fontSize: 16 }} prefix={<UserOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="来源 IP" value={log.ip} valueStyle={{ fontSize: 14 }} prefix={<EnvironmentOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="操作时间" value={formatDateTime(log.createdAt)} valueStyle={{ fontSize: 14 }} prefix={<ClockCircleOutlined />} /></Card>
        </Col>
      </Row>

      {/* Tabs：详情 / 用户上下文 / 历史操作 / 分析 */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'detail',
            label: <span><AuditOutlined /> 日志详情</span>,
            children: (
              <Card size="small" title={<Space><AuditOutlined /> 审计日志详情</Space>}>
                <ProDescriptions
                  column={2}
                  bordered
                  size="small"
                  dataSource={{
                    id: log.id,
                    userId: log.userId,
                    username: log.username,
                    action: AuditActionLabel[log.action],
                    resource: log.resource,
                    resourceId: log.resourceId ?? '-',
                    detail: log.detail,
                    ip: log.ip,
                    userAgent: log.userAgent,
                    status: log.status === 'success' ? '成功' : '失败',
                    costMs: `${log.costMs} ms`,
                    createdAt: formatDateTime(log.createdAt),
                  }}
                  columns={[
                    { title: '日志 ID', dataIndex: 'id', key: 'id' },
                    { title: '操作类型', dataIndex: 'action', key: 'action' },
                    { title: '用户 ID', dataIndex: 'userId', key: 'userId' },
                    { title: '用户名', dataIndex: 'username', key: 'username', render: (v: React.ReactNode) => <Space><UserOutlined />{v}</Space> },
                    { title: '资源路径', dataIndex: 'resource', key: 'resource', render: (v: React.ReactNode) => <code>{v}</code> },
                    { title: '资源 ID', dataIndex: 'resourceId', key: 'resourceId' },
                    { title: '操作详情', dataIndex: 'detail', key: 'detail', span: 2 },
                    { title: 'IP 地址', dataIndex: 'ip', key: 'ip', render: (v: React.ReactNode) => <Space><EnvironmentOutlined />{v}</Space> },
                    { title: 'User-Agent', dataIndex: 'userAgent', key: 'userAgent', render: (v: React.ReactNode) => <Space><DesktopOutlined />{v}</Space> },
                    { title: '状态', dataIndex: 'status', key: 'status' },
                    { title: '响应时间', dataIndex: 'costMs', key: 'costMs' },
                    { title: '操作时间', dataIndex: 'createdAt', key: 'createdAt', render: (v: React.ReactNode) => <Space><ClockCircleOutlined />{v}</Space> },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'user',
            label: <span><UserOutlined /> 操作者信息</span>,
            children: (
              <Card size="small" title={<Space><UserOutlined /> 操作者信息</Space>}>
                {operator ? (
                  <ProDescriptions
                    column={2}
                    bordered
                    size="small"
                    dataSource={{
                      id: operator.id,
                      username: operator.username,
                      nickname: operator.nickname,
                      email: operator.email,
                      dept: operator.dept,
                      status: operator.status,
                      lastLoginAt: operator.lastLoginAt ? formatDateTime(operator.lastLoginAt) : '-',
                      lastLoginIp: operator.lastLoginIp ?? '-',
                    }}
                    columns={[
                      { title: '用户 ID', dataIndex: 'id', key: 'id' },
                      { title: '用户名', dataIndex: 'username', key: 'username' },
                      { title: '昵称', dataIndex: 'nickname', key: 'nickname' },
                      { title: '邮箱', dataIndex: 'email', key: 'email' },
                      { title: '部门', dataIndex: 'dept', key: 'dept' },
                      { title: '状态', dataIndex: 'status', key: 'status' },
                      { title: '最后登录', dataIndex: 'lastLoginAt', key: 'lastLoginAt' },
                      { title: '最后登录 IP', dataIndex: 'lastLoginIp', key: 'lastLoginIp' },
                    ]}
                  />
                ) : (
                  <Empty description="未找到操作者信息" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
                <div style={{ marginTop: 16 }}>
                  <Button type="primary" icon={<UserOutlined />} onClick={() => operator && navigate(`/admin/users/${operator.id}`)}>
                    查看用户详情
                  </Button>
                </div>
              </Card>
            ),
          },
          {
            key: 'history',
            label: <span><ClockCircleOutlined /> 历史操作 ({userHistory.length})</span>,
            children: (
              <Card size="small" title={<Space><ClockCircleOutlined /> 同用户近期操作</Space>}>
                <Table
                  size="small"
                  rowKey="id"
                  pagination={{ pageSize: 5 }}
                  columns={historyColumns}
                  dataSource={userHistory}
                  scroll={{ x: 800 }}
                />
              </Card>
            ),
          },
          {
            key: 'analytics',
            label: <span><ThunderboltOutlined /> 分析</span>,
            children: (
              <Row gutter={16}>
                <Col xs={24} lg={14}>
                  <Card size="small" title={<Space><GlobalOutlined /> 同资源 24 小时活动</Space>} style={{ marginBottom: spacing[4] }}>
                    <ReactECharts option={resourceActivityChartOption} style={{ height: 280, width: '100%' }} notMerge lazyUpdate />
                  </Card>
                </Col>
                <Col xs={24} lg={10}>
                  <Card size="small" title={<Space><CheckCircleOutlined /> 操作状态分布</Space>}>
                    <ReactECharts option={statusChartOption} style={{ height: 280, width: '100%' }} notMerge lazyUpdate />
                  </Card>
                </Col>
              </Row>
            ),
          },
        ]}
      />
    </div>
  );
};

export default AuditLogDetailPage;
