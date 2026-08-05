/**
 * IOC 详情页
 * - 顶部：IOC 基本信息（类型/值/置信度/出现次数/首次出现/最后出现）
 * - 关联文件 / 关联组织 / 关联 CVE / 关联攻击链
 * - 攻击链可视化（基于关联威胁行为者）
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Table,
  Empty,
  Spin,
  Row,
  Col,
  Statistic,
  List,
  Tooltip,
  message,
  Descriptions,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  BugOutlined,
  FileTextOutlined,
  TeamOutlined,
  WarningOutlined,
  FireOutlined,
  EyeOutlined,
  GlobalOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { getThreatIntelById, mockThreatActors } from '@/mock/threatIntel';
import { mockAttackChains as mockChains } from '@/mock/attackChain';
import type { ThreatIntelItem } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text } = Typography;

/** IOC 类型映射 */
const iocTypeMap: Record<ThreatIntelItem['type'], { color: string; text: string }> = {
  ip: { color: 'red', text: 'IP 地址' },
  domain: { color: 'blue', text: '域名' },
  url: { color: 'purple', text: 'URL' },
  hash: { color: 'orange', text: '哈希' },
  email: { color: 'cyan', text: '邮箱' },
  cve: { color: 'magenta', text: 'CVE' },
};

const IocDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [item, setItem] = useState<ThreatIntelItem | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = getThreatIntelById(id) ?? null;
      setItem(data);
      setLoading(false);
    }, 200);
  }, [id]);

  /** 关联文件 */
  const relatedFiles = item?.relatedFiles ?? [];

  /** 关联 CVE */
  const relatedCves = item?.relatedCves ?? [];

  /** 关联威胁行为者 */
  const relatedActors = (item?.threatActors ?? [])
    .map((name) => mockThreatActors.find((a) => a.name === name))
    .filter((a): a is NonNullable<typeof a> => !!a);

  /** 关联攻击链 */
  const relatedChains = mockChains.filter((c) => {
    if (!item) return false;
    return (
      c.stages.some((s) => s.technique?.includes(item.value)) ||
      c.target.includes(item.value) ||
      c.name.includes(item.value)
    );
  });

  /** 攻击链可视化（基于 IOC 出现频次） */
  const attackChainOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { top: 0 },
    series: [
      {
        type: 'sankey',
        emphasis: { focus: 'adjacency' },
        data: [
          { name: '攻击者' },
          { name: 'IOC' },
          { name: '文件' },
          { name: '目标' },
          ...(relatedActors.map((a) => ({ name: a.name }))),
          ...relatedFiles.map((f) => ({ name: f.name })),
          ...(item?.threatActors?.map((t) => ({ name: t })) ?? []),
        ],
        links: [
          ...(relatedActors.map((a) => ({ source: a.name, target: '攻击者', value: 10 }))),
          { source: '攻击者', target: 'IOC', value: 20 },
          { source: 'IOC', target: '文件', value: 15 },
          { source: '文件', target: '目标', value: 10 },
          ...relatedFiles.map((f) => ({ source: '文件', target: f.name, value: 5 })),
        ],
        label: { color: '#fff' },
        lineStyle: { color: '#aaa', curveness: 0.5 },
      },
    ],
  };

  /** 文件列 */
  const fileColumns: ColumnsType<ThreatIntelItem['relatedFiles'][number]> = [
    { title: '文件 ID', dataIndex: 'id', width: 120, render: (v: string) => <code>{v}</code> },
    {
      title: '文件名',
      dataIndex: 'name',
      ellipsis: true,
      render: (v: string, record) => <a onClick={() => navigate(`/files/${record.id}`)}>{v}</a>,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/files/${record.id}`)}>详情</Button>
      ),
    },
  ];

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载 IOC 详情..." /></div>;
  }

  if (!item) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到 IOC">
          <Button type="primary" onClick={() => navigate('/redteam/threat-intel')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  const typeMeta = iocTypeMap[item.type];

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/redteam/threat-intel')}>返回</Button>
          <BugOutlined style={{ fontSize: 24, color: colors.error }} />
          <Title level={4} style={{ margin: 0 }}>IOC 详情</Title>
          <Tag color={typeMeta.color}>{typeMeta.text}</Tag>
          <Tag color={item.confidence >= 0.9 ? 'red' : item.confidence >= 0.7 ? 'orange' : 'default'}>
            置信度 {(item.confidence * 100).toFixed(0)}%
          </Tag>
        </Space>
        <Space>
          <Button icon={<GlobalOutlined />} onClick={() => message.success('已加入封堵名单')}>加入封堵</Button>
          <Button type="primary" icon={<FileTextOutlined />} onClick={() => message.success('生成 IOC 报告...')}>生成报告</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="出现次数" value={item.occurrences} prefix={<WarningOutlined />} valueStyle={{ color: colors.error }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="关联文件" value={relatedFiles.length} prefix={<FileTextOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="关联组织" value={relatedActors.length} prefix={<TeamOutlined />} valueStyle={{ color: colors.warning }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="关联 CVE" value={relatedCves.length} prefix={<BugOutlined />} valueStyle={{ color: colors.severity.high }} /></Card>
        </Col>
      </Row>

      {/* 基本信息 */}
      <Card size="small" title={<Space><BugOutlined /> IOC 基本信息</Space>} style={{ marginBottom: spacing[4] }}>
        <ProDescriptions
          column={2}
          bordered
          size="small"
          dataSource={{
            id: item.id,
            type: typeMeta.text,
            value: item.value,
            confidence: `${(item.confidence * 100).toFixed(0)}%`,
            occurrences: item.occurrences,
            firstSeen: formatDateTime(item.firstSeen),
            lastSeen: formatDateTime(item.lastSeen),
            tags: item.tags.join(' / '),
            sources: item.sources.join(' / '),
            threatActors: item.threatActors.join(' / '),
          }}
          columns={[
            { title: 'IOC ID', dataIndex: 'id', key: 'id' },
            { title: '类型', dataIndex: 'type', key: 'type' },
            { title: '值', dataIndex: 'value', key: 'value', render: (v: React.ReactNode) => <code>{v}</code> },
            { title: '置信度', dataIndex: 'confidence', key: 'confidence' },
            { title: '出现次数', dataIndex: 'occurrences', key: 'occurrences' },
            { title: '首次出现', dataIndex: 'firstSeen', key: 'firstSeen' },
            { title: '最后出现', dataIndex: 'lastSeen', key: 'lastSeen' },
            { title: '来源', dataIndex: 'sources', key: 'sources' },
            { title: '标签', dataIndex: 'tags', key: 'tags' },
            { title: '威胁行为者', dataIndex: 'threatActors', key: 'threatActors' },
          ]}
        />
      </Card>

      <Row gutter={16}>
        {/* 左侧：关联文件 + 关联 CVE */}
        <Col xs={24} lg={12}>
          <Card size="small" title={<Space><FileTextOutlined /> 关联文件 ({relatedFiles.length})</Space>} style={{ marginBottom: spacing[4] }}>
            {relatedFiles.length === 0 ? (
              <Empty description="无关联文件" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <Table
                size="small"
                rowKey="id"
                pagination={false}
                columns={fileColumns}
                dataSource={relatedFiles}
              />
            )}
          </Card>

          <Card size="small" title={<Space><BugOutlined /> 关联 CVE ({relatedCves.length})</Space>}>
            {relatedCves.length === 0 ? (
              <Empty description="无关联 CVE" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <List
                size="small"
                dataSource={relatedCves}
                renderItem={(cve) => (
                  <List.Item>
                    <Space>
                      <BugOutlined style={{ color: colors.error }} />
                      <a onClick={() => navigate(`/redteam/vulnerability`)}><Text strong>{cve}</Text></a>
                    </Space>
                    <Tooltip title="查看详情">
                      <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate('/redteam/vulnerability')} />
                    </Tooltip>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>

        {/* 右侧：关联威胁行为者 + 关联攻击链 */}
        <Col xs={24} lg={12}>
          <Card size="small" title={<Space><TeamOutlined /> 关联威胁行为者 ({relatedActors.length})</Space>} style={{ marginBottom: spacing[4] }}>
            {relatedActors.length === 0 ? (
              <Empty description="无关联威胁行为者" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <List
                dataSource={relatedActors}
                renderItem={(actor) => (
                  <List.Item>
                    <List.Item.Meta
                      avatar={<BugOutlined style={{ fontSize: 20, color: colors.error }} />}
                      title={<Space><Text strong>{actor.name}</Text><Tag color="red">{actor.sophistication}</Tag></Space>}
                      description={
                        <div>
                          <Text type="secondary">{actor.motivation}</Text>
                          <div style={{ marginTop: 4 }}>
                            <Tag>起源：{actor.origin}</Tag>
                            <Tag>活跃自：{actor.activeSince}</Tag>
                          </div>
                          <div style={{ marginTop: 4 }}>
                            <Text type="secondary" style={{ fontSize: 12 }}>目标：</Text>
                            {actor.targets.map((t) => <Tag key={t} color="orange">{t}</Tag>)}
                          </div>
                          <div style={{ marginTop: 4 }}>
                            <Text type="secondary" style={{ fontSize: 12 }}>TTPs：</Text>
                            {actor.ttps.map((t) => <Tag key={t} color="blue">{t}</Tag>)}
                          </div>
                        </div>
                      }
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>

          <Card size="small" title={<Space><FireOutlined /> 关联攻击链 ({relatedChains.length})</Space>}>
            {relatedChains.length === 0 ? (
              <Empty description="无关联攻击链" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <List
                size="small"
                dataSource={relatedChains}
                renderItem={(chain) => (
                  <List.Item>
                    <Space>
                      <FireOutlined style={{ color: colors.error }} />
                      <a onClick={() => navigate(`/redteam/attack-chain/${chain.id}`)}><Text strong>{chain.name}</Text></a>
                    </Space>
                    <Space direction="vertical" size={0} style={{ alignItems: 'flex-end' }}>
                      <Tag color={chain.status === 'success' ? 'success' : chain.status === 'active' ? 'processing' : 'default'}>
                        {chain.status === 'success' ? '成功' : chain.status === 'active' ? '进行中' : '计划'}
                      </Tag>
                      <Text type="secondary" style={{ fontSize: 11 }}>{chain.target}</Text>
                    </Space>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* 攻击链可视化 */}
      <Card size="small" title={<Space><ClockCircleOutlined /> IOC 关联攻击链可视化</Space>} style={{ marginTop: spacing[4] }}>
        <ReactECharts option={attackChainOption} style={{ height: 320, width: '100%' }} notMerge lazyUpdate />
      </Card>

      {/* 标签与来源 */}
      <Card size="small" title="标签与情报源" style={{ marginTop: spacing[4] }}>
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="标签">
            <Space wrap>
              {item.tags.map((t) => <Tag key={t} color="blue">{t}</Tag>)}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="情报源">
            <Space wrap>
              {item.sources.map((s) => <Tag key={s} color="purple">{s}</Tag>)}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  );
};

export default IocDetailPage;
