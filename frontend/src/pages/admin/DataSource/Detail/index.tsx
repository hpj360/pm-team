/**
 * 数据源详情页
 * - 顶部：数据源基本信息 + 状态
 * - 连接信息：端点/端口/数据库
 * - 性能监控：延迟/吞吐
 * - 健康检查历史
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
  Descriptions,
  Tabs,
  Table,
  Progress,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  DatabaseOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  ApiOutlined,
  GlobalOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { mockDataSources } from '@/mock/adminDataSource';
import type { DataSource, DataSourceStatus } from '@/types';
import { DataSourceTypeLabel } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text } = Typography;

/** 数据源状态颜色 */
const statusColor: Record<DataSourceStatus, string> = {
  connected: 'success',
  disconnected: 'default',
  error: 'error',
  checking: 'processing',
  degraded: 'warning',
};

const statusText: Record<DataSourceStatus, string> = {
  connected: '已连接',
  disconnected: '未连接',
  error: '错误',
  checking: '检查中',
  degraded: '降级',
};

/** Mock 健康检查历史 */
interface HealthCheckRecord {
  id: string;
  time: string;
  status: DataSourceStatus;
  latencyMs: number;
  message?: string;
}

function generateHealthHistory(dsId: string): HealthCheckRecord[] {
  const records: HealthCheckRecord[] = [];
  const now = Date.now();
  for (let i = 0; i < 20; i++) {
    const statuses: DataSourceStatus[] = ['connected', 'connected', 'connected', 'connected', 'degraded', 'error'];
    const status = statuses[Math.floor(Math.random() * statuses.length)];
    records.push({
      id: `${dsId}_hc_${i}`,
      time: new Date(now - i * 5 * 60 * 1000).toISOString(),
      status,
      latencyMs: status === 'error' ? 0 : 10 + Math.floor(Math.random() * 80),
      message: status === 'error' ? '连接超时' : status === 'degraded' ? '响应缓慢' : undefined,
    });
  }
  return records;
}

const DataSourceDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [item, setItem] = useState<DataSource | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('detail');
  const [history, setHistory] = useState<HealthCheckRecord[]>([]);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = mockDataSources.find((d) => d.id === id) ?? null;
      setItem(data);
      if (data) {
        setHistory(generateHealthHistory(data.id));
      }
      setLoading(false);
    }, 200);
  }, [id]);

  /** 延迟趋势图 */
  const latencyChartOption: EChartsOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: history.slice(0, 20).reverse().map((h) => formatDateTime(h.time).slice(11, 16)),
    },
    yAxis: { type: 'value', name: '延迟 (ms)' },
    series: [
      {
        type: 'line',
        smooth: true,
        data: history.slice(0, 20).reverse().map((h) => h.latencyMs),
        itemStyle: { color: colors.info },
        areaStyle: { color: colors.info + '20' },
        markLine: {
          data: [
            { type: 'average', name: '平均' },
            { yAxis: 100, name: '告警阈值', lineStyle: { color: colors.error } },
          ],
        },
      },
    ],
  };

  /** 状态分布图 */
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
        data: (() => {
          const map = new Map<string, number>();
          history.forEach((h) => {
            map.set(statusText[h.status], (map.get(statusText[h.status]) ?? 0) + 1);
          });
          return Array.from(map.entries()).map(([name, value]) => ({ name, value }));
        })(),
      },
    ],
  };

  /** 健康历史列 */
  const healthColumns: ColumnsType<HealthCheckRecord> = [
    { title: '检查时间', dataIndex: 'time', width: 180, render: (v: string) => formatDateTime(v) },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: DataSourceStatus) => <Tag color={statusColor[v]}>{statusText[v]}</Tag>,
    },
    {
      title: '延迟',
      dataIndex: 'latencyMs',
      width: 100,
      render: (v: number) => v === 0 ? <Text type="secondary">-</Text> : `${v} ms`,
    },
    {
      title: '消息',
      dataIndex: 'message',
      render: (v?: string) => v ?? <Text type="secondary">正常</Text>,
    },
  ];

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载数据源详情..." /></div>;
  }

  if (!item) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到数据源">
          <Button type="primary" onClick={() => navigate('/admin/data-sources')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  /** 健康率 */
  const healthyCount = history.filter((h) => h.status === 'connected').length;
  const healthRate = history.length === 0 ? 0 : Math.round((healthyCount / history.length) * 100);
  const avgLatency = history.length === 0 ? 0 : Math.round(history.reduce((sum, h) => sum + h.latencyMs, 0) / history.length);

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/data-sources')}>返回</Button>
          <DatabaseOutlined style={{ fontSize: 24, color: colors.info }} />
          <Title level={4} style={{ margin: 0 }}>{item.name}</Title>
          <Tag color={statusColor[item.status]} icon={item.status === 'connected' ? <CheckCircleOutlined /> : item.status === 'error' ? <CloseCircleOutlined /> : undefined}>
            {statusText[item.status]}
          </Tag>
          <Tag color="blue">{DataSourceTypeLabel[item.type]}</Tag>
          {item.version && <Tag>v{item.version}</Tag>}
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={() => message.success('已触发健康检查')}>健康检查</Button>
          <Button icon={<EditOutlined />} onClick={() => message.success('编辑数据源...')}>编辑</Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => message.success('删除数据源...')}>删除</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="状态" value={statusText[item.status]} valueStyle={{ color: statusColor[item.status] === 'success' ? colors.success : statusColor[item.status] === 'error' ? colors.error : colors.warning }} prefix={item.status === 'connected' ? <CheckCircleOutlined /> : <CloseCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="当前延迟" value={item.latencyMs ?? 0} suffix="ms" prefix={<ThunderboltOutlined />} valueStyle={{ color: (item.latencyMs ?? 0) > 100 ? colors.error : colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="平均延迟" value={avgLatency} suffix="ms" prefix={<ClockCircleOutlined />} valueStyle={{ color: avgLatency > 100 ? colors.warning : colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="健康率" value={healthRate} suffix="%" prefix={<CheckCircleOutlined />} valueStyle={{ color: healthRate >= 95 ? colors.success : healthRate >= 80 ? colors.warning : colors.error }} /></Card>
        </Col>
      </Row>

      {/* Tabs：详情 / 健康检查 / 性能监控 / 关联资源 */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'detail',
            label: <span><DatabaseOutlined /> 基本信息</span>,
            children: (
              <Card size="small" title={<Space><DatabaseOutlined /> 数据源基本信息</Space>}>
                <ProDescriptions
                  column={2}
                  bordered
                  size="small"
                  dataSource={{
                    id: item.id,
                    name: item.name,
                    type: DataSourceTypeLabel[item.type],
                    endpoint: item.endpoint,
                    port: item.port,
                    database: item.database ?? '-',
                    status: statusText[item.status],
                    latencyMs: item.latencyMs ? `${item.latencyMs} ms` : '-',
                    version: item.version ?? '-',
                    lastError: item.lastError ?? '-',
                    lastCheckAt: formatDateTime(item.lastCheckAt),
                    description: item.description,
                  }}
                  columns={[
                    { title: '数据源 ID', dataIndex: 'id', key: 'id' },
                    { title: '名称', dataIndex: 'name', key: 'name' },
                    { title: '类型', dataIndex: 'type', key: 'type', render: (v: React.ReactNode) => <Tag color="blue">{v}</Tag> },
                    { title: '版本', dataIndex: 'version', key: 'version' },
                    { title: '端点', dataIndex: 'endpoint', key: 'endpoint', render: (v: React.ReactNode) => <Space><GlobalOutlined /><code>{v}</code></Space> },
                    { title: '端口', dataIndex: 'port', key: 'port', render: (v: React.ReactNode) => <code>{v}</code> },
                    { title: '数据库', dataIndex: 'database', key: 'database' },
                    { title: '状态', dataIndex: 'status', key: 'status' },
                    { title: '当前延迟', dataIndex: 'latencyMs', key: 'latencyMs' },
                    { title: '最后检查', dataIndex: 'lastCheckAt', key: 'lastCheckAt' },
                    { title: '最后错误', dataIndex: 'lastError', key: 'lastError', render: (v: React.ReactNode) => String(v) !== '-' ? <Text type="danger">{v}</Text> : '-' },
                    { title: '描述', dataIndex: 'description', key: 'description', span: 2 },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'health',
            label: <span><CheckCircleOutlined /> 健康检查历史 ({history.length})</span>,
            children: (
              <Card size="small">
                <Row gutter={16} style={{ marginBottom: spacing[4] }}>
                  <Col xs={24} sm={8}>
                    <Card size="small">
                      <div style={{ textAlign: 'center' }}>
                        <Text type="secondary">健康率</Text>
                        <Progress
                          type="circle"
                          percent={healthRate}
                          width={80}
                          status={healthRate >= 95 ? 'success' : healthRate >= 80 ? 'active' : 'exception'}
                          style={{ marginTop: 8 }}
                        />
                      </div>
                    </Card>
                  </Col>
                  <Col xs={24} sm={16}>
                    <Card size="small" title="状态分布">
                      <ReactECharts option={statusChartOption} style={{ height: 200, width: '100%' }} notMerge lazyUpdate />
                    </Card>
                  </Col>
                </Row>
                <Table
                  size="small"
                  rowKey="id"
                  pagination={{ pageSize: 10 }}
                  columns={healthColumns}
                  dataSource={history}
                />
              </Card>
            ),
          },
          {
            key: 'performance',
            label: <span><ThunderboltOutlined /> 性能监控</span>,
            children: (
              <Card size="small" title={<Space><ThunderboltOutlined /> 延迟趋势（最近 100 分钟）</Space>}>
                <ReactECharts option={latencyChartOption} style={{ height: 360, width: '100%' }} notMerge lazyUpdate />
                <div style={{ marginTop: spacing[4] }}>
                  <Descriptions column={3} size="small" bordered>
                    <Descriptions.Item label="平均延迟">{avgLatency} ms</Descriptions.Item>
                    <Descriptions.Item label="当前延迟">{item.latencyMs ?? 0} ms</Descriptions.Item>
                    <Descriptions.Item label="健康率">{healthRate}%</Descriptions.Item>
                    <Descriptions.Item label="检查次数">{history.length}</Descriptions.Item>
                    <Descriptions.Item label="健康次数">{healthyCount}</Descriptions.Item>
                    <Descriptions.Item label="异常次数">{history.length - healthyCount}</Descriptions.Item>
                  </Descriptions>
                </div>
              </Card>
            ),
          },
          {
            key: 'connection',
            label: <span><ApiOutlined /> 连接信息</span>,
            children: (
              <Card size="small" title={<Space><ApiOutlined /> 连接配置</Space>}>
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label="类型">
                    <Tag color="blue" icon={<DatabaseOutlined />}>{DataSourceTypeLabel[item.type]}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="端点">
                    <code>{item.endpoint}</code>
                  </Descriptions.Item>
                  <Descriptions.Item label="端口">
                    <code>{item.port}</code>
                  </Descriptions.Item>
                  {item.database && (
                    <Descriptions.Item label="数据库">
                      <Tag color="orange">{item.database}</Tag>
                    </Descriptions.Item>
                  )}
                  <Descriptions.Item label="完整连接字符串">
                    <code style={{ wordBreak: 'break-all' }}>
                      {item.type}://{item.endpoint}:{item.port}{item.database ? `/${item.database}` : ''}
                    </code>
                  </Descriptions.Item>
                  <Descriptions.Item label="状态">
                    <Tag color={statusColor[item.status]}>{statusText[item.status]}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="说明">{item.description}</Descriptions.Item>
                </Descriptions>
                <div style={{ marginTop: 16 }}>
                  <Space>
                    <Button type="primary" icon={<ReloadOutlined />} onClick={() => message.success('测试连接成功')}>测试连接</Button>
                    <Button icon={<ApiOutlined />} onClick={() => message.success('查看依赖服务...')}>查看依赖</Button>
                  </Space>
                </div>
              </Card>
            ),
          },
        ]}
      />
    </div>
  );
};

export default DataSourceDetailPage;
