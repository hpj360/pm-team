/**
 * 攻击链路详情页
 * - 顶部：链路基本信息 + 状态 + 目标
 * - Kill Chain 时间线：分阶段展示
 * - 攻击流程桑基图：阶段流转
 * - 各阶段详情：战术/技术/操作员/时间
 * - 关联文件 / 关联漏洞
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
  Timeline,
  List,
  Table,
  message,
  Descriptions,
  Tabs,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  FireOutlined,
  NodeIndexOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  AimOutlined,
  BugOutlined,
  FileTextOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { getAttackChainById } from '@/mock/attackChain';
import { mockVulnerabilities } from '@/mock/vulnerability';
import { mockFileList } from '@/mock/file';
import type { AttackChain, AttackStage } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 攻击链状态颜色 */
const chainStatusColor: Record<AttackChain['status'], string> = {
  planning: 'default',
  active: 'processing',
  success: 'success',
  failed: 'error',
};

const chainStatusText: Record<AttackChain['status'], string> = {
  planning: '计划中',
  active: '进行中',
  success: '成功',
  failed: '失败',
};

/** 攻击阶段状态颜色 */
const stageStatusColor: Record<AttackStage['status'], string> = {
  planned: 'default',
  'in-progress': 'processing',
  completed: 'success',
  failed: 'error',
};

const stageStatusText: Record<AttackStage['status'], string> = {
  planned: '未开始',
  'in-progress': '进行中',
  completed: '已完成',
  failed: '失败',
};

const AttackChainDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [item, setItem] = useState<AttackChain | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('timeline');

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = getAttackChainById(id) ?? null;
      setItem(data);
      setLoading(false);
    }, 200);
  }, [id]);

  /** 关联漏洞：根据 stages.technique 匹配 CVE 编号 */
  const relatedCves = (item?.stages ?? []).flatMap((s) => {
    const matches = s.technique?.match(/CVE-\d{4}-\d+/g) ?? [];
    return matches;
  }).filter((v, i, arr) => arr.indexOf(v) === i).map((cve) =>
    mockVulnerabilities.find((v) => v.cve === cve),
  ).filter((v): v is NonNullable<typeof v> => !!v);

  /** 关联文件（基于目标名称匹配） */
  const relatedFiles = mockFileList
    .filter((f) => (item ? f.tags?.includes(item.target) || f.tags?.some((t) => t.includes('malware')) : false))
    .slice(0, 5);

  /** 桑基图配置 */
  const sankeyOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'sankey',
        emphasis: { focus: 'adjacency' },
        data: (item?.flow ?? []).flatMap((f) => [f.from, f.to])
          .filter((v, i, arr) => arr.indexOf(v) === i)
          .map((name) => ({ name })),
        links: (item?.flow ?? []).map((f) => ({ source: f.from, target: f.to, value: f.value })),
        label: { color: '#fff' },
        lineStyle: { color: '#aaa', curveness: 0.5 },
      },
    ],
  };

  /** 阶段状态统计（饼图） */
  const stageStatusOption: EChartsOption = {
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
          (item?.stages ?? []).forEach((s) => {
            map.set(stageStatusText[s.status], (map.get(stageStatusText[s.status]) ?? 0) + 1);
          });
          return Array.from(map.entries()).map(([name, value]) => ({
            name,
            value,
            itemStyle: {
              color: name === '已完成' ? colors.success : name === '进行中' ? colors.info : name === '失败' ? colors.error : colors.neutral[400],
            },
          }));
        })(),
      },
    ],
  };

  /** 阶段表格列 */
  const stageColumns: ColumnsType<AttackStage> = [
    { title: '#', dataIndex: 'phase', width: 50, render: (v: number) => <Tag color="red">阶段 {v}</Tag> },
    { title: '名称', dataIndex: 'name', width: 120, render: (v: string) => <Text strong>{v}</Text> },
    { title: '战术', dataIndex: 'tactic', render: (v: string) => <Tag color="blue">{v}</Tag> },
    {
      title: '技术',
      dataIndex: 'technique',
      render: (v: string) => {
        const cves = v.match(/CVE-\d{4}-\d+/g) ?? [];
        if (cves.length > 0) {
          return (
            <Space wrap>
              {cves.map((cve) => (
                <a key={cve} onClick={() => {
                  const vItem = mockVulnerabilities.find((v) => v.cve === cve);
                  if (vItem) navigate(`/redteam/vulnerability/${vItem.id}`);
                }}><Tag color="magenta">{cve}</Tag></a>
              ))}
            </Space>
          );
        }
        return <Text>{v}</Text>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: AttackStage['status']) => (
        <Tag color={stageStatusColor[v]}>{stageStatusText[v]}</Tag>
      ),
    },
    {
      title: '操作员',
      dataIndex: 'operator',
      width: 120,
      render: (v: string) => v ? <Space><TeamOutlined />{v}</Space> : <Text type="secondary">-</Text>,
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 160,
      render: (v: string) => v ? formatDateTime(v) : <Text type="secondary">-</Text>,
    },
  ];

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载攻击链详情..." /></div>;
  }

  if (!item) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到攻击链">
          <Button type="primary" onClick={() => navigate('/redteam/attack-chain')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  /** 阶段进度 */
  const completedStages = item.stages.filter((s) => s.status === 'completed').length;
  const inProgressStages = item.stages.filter((s) => s.status === 'in-progress').length;

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/redteam/attack-chain')}>返回</Button>
          <NodeIndexOutlined style={{ fontSize: 24, color: colors.error }} />
          <Title level={4} style={{ margin: 0 }}>{item.name}</Title>
          <Tag color={chainStatusColor[item.status]}>{chainStatusText[item.status]}</Tag>
          <Tag color="red" icon={<AimOutlined />}>目标：{item.target}</Tag>
        </Space>
        <Space>
          <Button icon={<FileTextOutlined />} onClick={() => message.success('生成报告...')}>生成报告</Button>
          <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => message.success('已启动新阶段')}>启动新阶段</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="总阶段数" value={item.stages.length} prefix={<NodeIndexOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="已完成" value={completedStages} prefix={<CheckCircleOutlined />} valueStyle={{ color: colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="进行中" value={inProgressStages} prefix={<PlayCircleOutlined />} valueStyle={{ color: colors.warning }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="关联 CVE" value={relatedCves.length} prefix={<BugOutlined />} valueStyle={{ color: colors.error }} /></Card>
        </Col>
      </Row>

      {/* 基本信息 */}
      <Card size="small" title={<Space><NodeIndexOutlined /> 攻击链基本信息</Space>} style={{ marginBottom: spacing[4] }}>
        <ProDescriptions
          column={2}
          bordered
          size="small"
          dataSource={{
            id: item.id,
            name: item.name,
            target: item.target,
            objective: item.objective,
            status: chainStatusText[item.status],
            startTime: formatDateTime(item.startTime),
            endTime: item.endTime ? formatDateTime(item.endTime) : '-',
            stages: `${item.stages.length} 个阶段`,
            completed: `${completedStages} 个已完成`,
          }}
          columns={[
            { title: '链路 ID', dataIndex: 'id', key: 'id' },
            { title: '名称', dataIndex: 'name', key: 'name' },
            { title: '目标', dataIndex: 'target', key: 'target', render: (v: React.ReactNode) => <Space><AimOutlined />{v}</Space> },
            { title: '作战目标', dataIndex: 'objective', key: 'objective' },
            { title: '状态', dataIndex: 'status', key: 'status' },
            { title: '阶段数', dataIndex: 'stages', key: 'stages' },
            { title: '已完成阶段', dataIndex: 'completed', key: 'completed' },
            { title: '开始时间', dataIndex: 'startTime', key: 'startTime' },
            { title: '结束时间', dataIndex: 'endTime', key: 'endTime' },
          ]}
        />
      </Card>

      {/* Tabs：时间线 / 阶段表 / 关联信息 / 可视化 */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'timeline',
            label: <span><ClockCircleOutlined /> Kill Chain 时间线</span>,
            children: (
              <Card size="small">
                <Timeline
                  items={item.stages.map((stage) => ({
                    color: stage.status === 'completed' ? 'green' : stage.status === 'in-progress' ? 'blue' : stage.status === 'failed' ? 'red' : 'gray',
                    dot: stage.status === 'in-progress' ? <PlayCircleOutlined style={{ fontSize: 16, color: colors.info }} /> : undefined,
                    children: (
                      <div>
                        <Space>
                          <Tag color="red">阶段 {stage.phase}</Tag>
                          <Text strong>{stage.name}</Text>
                          <Tag color={stageStatusColor[stage.status]}>{stageStatusText[stage.status]}</Tag>
                        </Space>
                        <div style={{ marginTop: 4, marginBottom: 4 }}>
                          <Space>
                            <Tag color="blue" icon={<NodeIndexOutlined />}>{stage.tactic}</Tag>
                            <Tag color="purple">{stage.technique}</Tag>
                          </Space>
                        </div>
                        <Paragraph style={{ margin: '4px 0', color: '#595959' }}>{stage.description}</Paragraph>
                        <Space size={16} style={{ fontSize: 12, color: '#8c8c8c' }}>
                          {stage.operator && <span><TeamOutlined /> {stage.operator}</span>}
                          {stage.startTime && <span><ClockCircleOutlined /> 开始：{formatDateTime(stage.startTime)}</span>}
                          {stage.endTime && <span><CheckCircleOutlined /> 结束：{formatDateTime(stage.endTime)}</span>}
                        </Space>
                      </div>
                    ),
                  }))}
                />
              </Card>
            ),
          },
          {
            key: 'stages',
            label: <span><NodeIndexOutlined /> 阶段详情</span>,
            children: (
              <Card size="small">
                <Table
                  size="small"
                  rowKey="id"
                  pagination={false}
                  columns={stageColumns}
                  dataSource={item.stages}
                  scroll={{ x: 1200 }}
                  expandable={{
                    expandedRowRender: (record) => (
                      <Descriptions column={2} size="small" bordered>
                        <Descriptions.Item label="阶段 ID">{record.id}</Descriptions.Item>
                        <Descriptions.Item label="关联目标">{record.targetId ?? '-'}</Descriptions.Item>
                        <Descriptions.Item label="开始时间">{record.startTime ? formatDateTime(record.startTime) : '-'}</Descriptions.Item>
                        <Descriptions.Item label="结束时间">{record.endTime ? formatDateTime(record.endTime) : '-'}</Descriptions.Item>
                        <Descriptions.Item label="操作员">{record.operator ?? '-'}</Descriptions.Item>
                        <Descriptions.Item label="状态">{stageStatusText[record.status]}</Descriptions.Item>
                        <Descriptions.Item label="详细描述" span={2}>{record.description}</Descriptions.Item>
                      </Descriptions>
                    ),
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'visual',
            label: <span><ThunderboltOutlined /> 可视化</span>,
            children: (
              <Row gutter={16}>
                <Col xs={24} lg={14}>
                  <Card size="small" title={<Space><NodeIndexOutlined /> 攻击流程桑基图</Space>} style={{ marginBottom: spacing[4] }}>
                    <ReactECharts option={sankeyOption} style={{ height: 360, width: '100%' }} notMerge lazyUpdate />
                  </Card>
                </Col>
                <Col xs={24} lg={10}>
                  <Card size="small" title={<Space><CheckCircleOutlined /> 阶段状态分布</Space>}>
                    <ReactECharts option={stageStatusOption} style={{ height: 360, width: '100%' }} notMerge lazyUpdate />
                  </Card>
                </Col>
              </Row>
            ),
          },
          {
            key: 'related',
            label: <span><FireOutlined /> 关联信息</span>,
            children: (
              <Row gutter={16}>
                <Col xs={24} lg={12}>
                  <Card size="small" title={<Space><BugOutlined /> 关联漏洞 ({relatedCves.length})</Space>} style={{ marginBottom: spacing[4] }}>
                    {relatedCves.length === 0 ? (
                      <Empty description="无关联漏洞" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                      <List
                        size="small"
                        dataSource={relatedCves}
                        renderItem={(v) => (
                          <List.Item>
                            <Space>
                              <BugOutlined style={{ color: colors.error }} />
                              <a onClick={() => navigate(`/redteam/vulnerability/${v.id}`)}><Text strong>{v.cve}</Text></a>
                              <Text type="secondary">{v.name}</Text>
                            </Space>
                            <Tag color={v.severity === 'critical' ? 'red' : v.severity === 'high' ? 'orange' : 'default'}>
                              {v.severity}
                            </Tag>
                          </List.Item>
                        )}
                      />
                    )}
                  </Card>
                </Col>
                <Col xs={24} lg={12}>
                  <Card size="small" title={<Space><FileTextOutlined /> 关联文件 ({relatedFiles.length})</Space>}>
                    {relatedFiles.length === 0 ? (
                      <Empty description="无关联文件" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                      <List
                        size="small"
                        dataSource={relatedFiles}
                        renderItem={(f) => (
                          <List.Item>
                            <Space>
                              <FileTextOutlined />
                              <a onClick={() => navigate(`/files/${f.id}`)}><Text strong>{f.name}</Text></a>
                            </Space>
                            <Tag>{(f.size / 1024).toFixed(1)} KB</Tag>
                          </List.Item>
                        )}
                      />
                    )}
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

export default AttackChainDetailPage;
