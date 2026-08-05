/**
 * AI 模型详情页
 * - 顶部：模型基本信息 + 状态
 * - 性能指标：准确率/延迟
 * - 加载信息 / 框架
 * - 调用统计
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
  Progress,
  Descriptions,
  Tabs,
  message,
} from 'antd';
import {
  ArrowLeftOutlined,
  ExperimentOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  DatabaseOutlined,
  ApiOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { mockAiModels } from '@/mock/adminModel';
import type { AiModel, ModelStatus } from '@/types';
import { ModelStatusLabel } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 模型状态颜色 */
const statusColor: Record<ModelStatus, string> = {
  loaded: 'success',
  unloaded: 'default',
  loading: 'processing',
  error: 'error',
};

/** 模型类型中文映射 */
const modelTypeText: Record<AiModel['type'], string> = {
  ner: 'NER 实体识别',
  classify: '文本分类',
  embedding: '向量嵌入',
  llm: '大语言模型',
  detect: '威胁检测',
};

/** 框架中文映射 */
const frameworkText: Record<AiModel['framework'], string> = {
  pytorch: 'PyTorch',
  onnx: 'ONNX',
  tensorflow: 'TensorFlow',
};

/** 生成调用统计 Mock */
function generateCallStats(): Array<{ time: string; calls: number; latency: number }> {
  const stats: Array<{ time: string; calls: number; latency: number }> = [];
  const now = Date.now();
  for (let i = 0; i < 24; i++) {
    stats.push({
      time: new Date(now - (23 - i) * 60 * 60 * 1000).toISOString(),
      calls: Math.floor(Math.random() * 200) + 50,
      latency: 20 + Math.floor(Math.random() * 80),
    });
  }
  return stats;
}

const ModelDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [item, setItem] = useState<AiModel | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('detail');
  const [callStats, setCallStats] = useState<Array<{ time: string; calls: number; latency: number }>>([]);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = mockAiModels.find((m) => m.id === id) ?? null;
      setItem(data);
      if (data) {
        setCallStats(generateCallStats());
      }
      setLoading(false);
    }, 200);
  }, [id]);

  /** 调用次数趋势图 */
  const callsChartOption: EChartsOption = {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: ['调用次数', '延迟 (ms)'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: callStats.map((s) => formatDateTime(s.time).slice(11, 16)),
    },
    yAxis: [
      { type: 'value', name: '次数' },
      { type: 'value', name: 'ms', position: 'right' },
    ],
    series: [
      {
        type: 'bar',
        name: '调用次数',
        data: callStats.map((s) => s.calls),
        itemStyle: { color: colors.info, borderRadius: [4, 4, 0, 0] },
      },
      {
        type: 'line',
        name: '延迟 (ms)',
        yAxisIndex: 1,
        smooth: true,
        data: callStats.map((s) => s.latency),
        itemStyle: { color: colors.warning },
      },
    ],
  };

  /** 性能雷达图 */
  const performanceRadarOption: EChartsOption = {
    tooltip: {},
    radar: {
      indicator: [
        { name: '准确率', max: 100 },
        { name: '响应速度', max: 100 },
        { name: '稳定性', max: 100 },
        { name: '吞吐量', max: 100 },
        { name: '资源占用', max: 100 },
      ],
      shape: 'polygon',
      splitNumber: 5,
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: [
              (item?.accuracy ?? 0) * 100,
              item?.latencyMs ? Math.max(0, 100 - item.latencyMs) : 50,
              85,
              75,
              100 - Math.min(100, (item?.sizeMb ?? 0) / 50),
            ],
            name: item?.name ?? '模型',
            areaStyle: { color: colors.info + '40' },
            lineStyle: { color: colors.info },
          },
        ],
      },
    ],
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载模型详情..." /></div>;
  }

  if (!item) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到模型">
          <Button type="primary" onClick={() => navigate('/admin/models')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  /** 总调用次数 */
  const totalCalls = callStats.reduce((sum, s) => sum + s.calls, 0);
  const avgLatency = callStats.length === 0 ? 0 : Math.round(callStats.reduce((sum, s) => sum + s.latency, 0) / callStats.length);

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/models')}>返回</Button>
          <ExperimentOutlined style={{ fontSize: 24, color: colors.info }} />
          <Title level={4} style={{ margin: 0 }}>{item.name}</Title>
          <Tag color={statusColor[item.status]} icon={item.status === 'loaded' ? <CheckCircleOutlined /> : item.status === 'error' ? <CloseCircleOutlined /> : item.status === 'loading' ? <LoadingOutlined /> : undefined}>
            {ModelStatusLabel[item.status]}
          </Tag>
          <Tag color="blue">{modelTypeText[item.type]}</Tag>
          <Tag color="purple">{frameworkText[item.framework]}</Tag>
        </Space>
        <Space>
          <Button icon={<EditOutlined />} onClick={() => message.success('编辑模型...')}>编辑</Button>
          {item.status === 'loaded' ? (
            <Button danger icon={<PauseCircleOutlined />} onClick={() => message.success('已卸载模型')}>卸载</Button>
          ) : (
            <Button type="primary" icon={<PlayCircleOutlined />} loading={item.status === 'loading'} onClick={() => message.success('已加载模型')}>加载</Button>
          )}
          <Button danger icon={<DeleteOutlined />} onClick={() => message.success('删除模型...')}>删除</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="准确率" value={item.accuracy ?? 0} precision={2} suffix="%" prefix={<BarChartOutlined />} valueStyle={{ color: colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="平均延迟" value={item.latencyMs ?? 0} suffix="ms" prefix={<ThunderboltOutlined />} valueStyle={{ color: (item.latencyMs ?? 0) > 100 ? colors.warning : colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="模型大小" value={item.sizeMb} suffix="MB" prefix={<DatabaseOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="24h 调用次数" value={totalCalls} prefix={<ApiOutlined />} valueStyle={{ color: colors.warning }} /></Card>
        </Col>
      </Row>

      {/* Tabs：详情 / 性能 / 调用统计 */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'detail',
            label: <span><ExperimentOutlined /> 基本信息</span>,
            children: (
              <Card size="small" title={<Space><ExperimentOutlined /> 模型基本信息</Space>}>
                <ProDescriptions
                  column={2}
                  bordered
                  size="small"
                  dataSource={{
                    id: item.id,
                    name: item.name,
                    version: item.version,
                    type: modelTypeText[item.type],
                    framework: frameworkText[item.framework],
                    sizeMb: `${item.sizeMb} MB`,
                    status: ModelStatusLabel[item.status],
                    accuracy: item.accuracy ? `${(item.accuracy * 100).toFixed(2)}%` : '-',
                    latencyMs: item.latencyMs ? `${item.latencyMs} ms` : '-',
                    loadedAt: item.loadedAt ? formatDateTime(item.loadedAt) : '-',
                    createTime: formatDateTime(item.createTime),
                    description: item.description,
                  }}
                  columns={[
                    { title: '模型 ID', dataIndex: 'id', key: 'id' },
                    { title: '名称', dataIndex: 'name', key: 'name' },
                    { title: '版本', dataIndex: 'version', key: 'version', render: (v: React.ReactNode) => <Tag color="blue">v{v}</Tag> },
                    { title: '类型', dataIndex: 'type', key: 'type' },
                    { title: '框架', dataIndex: 'framework', key: 'framework', render: (v: React.ReactNode) => <Tag color="purple">{v}</Tag> },
                    { title: '大小', dataIndex: 'sizeMb', key: 'sizeMb' },
                    { title: '状态', dataIndex: 'status', key: 'status' },
                    { title: '准确率', dataIndex: 'accuracy', key: 'accuracy' },
                    { title: '延迟', dataIndex: 'latencyMs', key: 'latencyMs' },
                    { title: '加载时间', dataIndex: 'loadedAt', key: 'loadedAt', render: (v: React.ReactNode) => <Space><ClockCircleOutlined />{v}</Space> },
                    { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
                    { title: '描述', dataIndex: 'description', key: 'description', span: 2 },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'performance',
            label: <span><BarChartOutlined /> 性能指标</span>,
            children: (
              <Row gutter={16}>
                <Col xs={24} lg={12}>
                  <Card size="small" title={<Space><BarChartOutlined /> 性能雷达图</Space>} style={{ marginBottom: spacing[4] }}>
                    <ReactECharts option={performanceRadarOption} style={{ height: 320, width: '100%' }} notMerge lazyUpdate />
                  </Card>
                </Col>
                <Col xs={24} lg={12}>
                  <Card size="small" title={<Space><ThunderboltOutlined /> 关键指标</Space>}>
                    <Descriptions column={1} size="small" bordered>
                      <Descriptions.Item label="准确率">
                        {item.accuracy ? (
                          <Space>
                            <Progress percent={Math.round(item.accuracy * 100)} size="small" style={{ width: 120 }} status="success" />
                            <Text strong>{(item.accuracy * 100).toFixed(2)}%</Text>
                          </Space>
                        ) : <Text type="secondary">-</Text>}
                      </Descriptions.Item>
                      <Descriptions.Item label="平均延迟">
                        {item.latencyMs ? (
                          <Space>
                            <Progress percent={Math.min(100, Math.max(0, 100 - item.latencyMs))} size="small" style={{ width: 120 }} status={(item.latencyMs ?? 0) > 100 ? 'exception' : 'active'} />
                            <Text strong>{item.latencyMs} ms</Text>
                          </Space>
                        ) : <Text type="secondary">-</Text>}
                      </Descriptions.Item>
                      <Descriptions.Item label="模型大小">
                        <Space>
                          <Progress percent={Math.min(100, Math.round((item.sizeMb / 5000) * 100))} size="small" style={{ width: 120 }} />
                          <Text strong>{item.sizeMb} MB</Text>
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label="状态">
                        <Tag color={statusColor[item.status]}>{ModelStatusLabel[item.status]}</Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="框架">
                        <Tag color="purple">{frameworkText[item.framework]}</Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="类型">
                        <Tag color="blue">{modelTypeText[item.type]}</Tag>
                      </Descriptions.Item>
                    </Descriptions>
                  </Card>
                </Col>
              </Row>
            ),
          },
          {
            key: 'calls',
            label: <span><ApiOutlined /> 调用统计</span>,
            children: (
              <Card size="small" title={<Space><ApiOutlined /> 24 小时调用趋势</Space>}>
                <ReactECharts option={callsChartOption} style={{ height: 360, width: '100%' }} notMerge lazyUpdate />
                <Row gutter={16} style={{ marginTop: spacing[4] }}>
                  <Col xs={12} sm={6}>
                    <Statistic title="24h 总调用" value={totalCalls} prefix={<ApiOutlined />} />
                  </Col>
                  <Col xs={12} sm={6}>
                    <Statistic title="平均延迟" value={avgLatency} suffix="ms" prefix={<ThunderboltOutlined />} />
                  </Col>
                  <Col xs={12} sm={6}>
                    <Statistic title="峰值调用" value={Math.max(...callStats.map((s) => s.calls))} prefix={<BarChartOutlined />} />
                  </Col>
                  <Col xs={12} sm={6}>
                    <Statistic title="最低调用" value={Math.min(...callStats.map((s) => s.calls))} prefix={<BarChartOutlined />} />
                  </Col>
                </Row>
              </Card>
            ),
          },
          {
            key: 'actions',
            label: <span><ThunderboltOutlined /> 操作</span>,
            children: (
              <Card size="small" title={<Space><ThunderboltOutlined /> 模型操作</Space>}>
                <Paragraph type="secondary">可对模型进行加载、卸载、测试等操作。</Paragraph>
                <Row gutter={16}>
                  <Col xs={12} sm={6}>
                    <Card size="small" hoverable onClick={() => message.success('加载中...')}>
                      <div style={{ textAlign: 'center', padding: 16 }}>
                        <PlayCircleOutlined style={{ fontSize: 32, color: colors.success }} />
                        <div style={{ marginTop: 8 }}><Text strong>加载模型</Text></div>
                        <Text type="secondary" style={{ fontSize: 12 }}>将模型加载到内存</Text>
                      </div>
                    </Card>
                  </Col>
                  <Col xs={12} sm={6}>
                    <Card size="small" hoverable onClick={() => message.success('卸载中...')}>
                      <div style={{ textAlign: 'center', padding: 16 }}>
                        <PauseCircleOutlined style={{ fontSize: 32, color: colors.warning }} />
                        <div style={{ marginTop: 8 }}><Text strong>卸载模型</Text></div>
                        <Text type="secondary" style={{ fontSize: 12 }}>从内存中释放</Text>
                      </div>
                    </Card>
                  </Col>
                  <Col xs={12} sm={6}>
                    <Card size="small" hoverable onClick={() => message.success('重新加载...')}>
                      <div style={{ textAlign: 'center', padding: 16 }}>
                        <ReloadOutlined style={{ fontSize: 32, color: colors.info }} />
                        <div style={{ marginTop: 8 }}><Text strong>重新加载</Text></div>
                        <Text type="secondary" style={{ fontSize: 12 }}>卸载后重新加载</Text>
                      </div>
                    </Card>
                  </Col>
                  <Col xs={12} sm={6}>
                    <Card size="small" hoverable onClick={() => message.success('开始测试...')}>
                      <div style={{ textAlign: 'center', padding: 16 }}>
                        <ThunderboltOutlined style={{ fontSize: 32, color: colors.error }} />
                        <div style={{ marginTop: 8 }}><Text strong>模型测试</Text></div>
                        <Text type="secondary" style={{ fontSize: 12 }}>验证模型可用性</Text>
                      </div>
                    </Card>
                  </Col>
                </Row>
              </Card>
            ),
          },
        ]}
      />
    </div>
  );
};

export default ModelDetailPage;
