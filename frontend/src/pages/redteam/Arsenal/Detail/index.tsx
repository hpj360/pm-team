/**
 * 武器详情页
 * - 顶部：武器基本信息 + 分类 + 评分
 * - 使用方式：命令行示例
 * - 检测规则：蓝队检测建议
 * - 关联 CVE / 关联攻击链
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
  List,
  Rate,
  message,
  Descriptions,
  Divider,
  Tabs,
} from 'antd';
import {
  ArrowLeftOutlined,
  ToolOutlined,
  BugOutlined,
  FireOutlined,
  CodeOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  WindowsOutlined,
  AppleOutlined,
  LinuxOutlined,
  EnvironmentOutlined,
  ClockCircleOutlined,
  UserOutlined,
  TagOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { getArsenalItemById } from '@/mock/arsenal';
import { mockVulnerabilities } from '@/mock/vulnerability';
import { mockAttackChains } from '@/mock/attackChain';
import type { ArsenalItem, ArsenalCategory } from '@/types';
import { ArsenalCategoryLabel } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 平台图标映射 */
const platformIcon: Record<string, React.ReactNode> = {
  Windows: <WindowsOutlined />,
  macOS: <AppleOutlined />,
  Linux: <LinuxOutlined />,
};

/** 分类颜色 */
const categoryColor: Record<ArsenalCategory, string> = {
  exploit: 'red',
  backdoor: 'magenta',
  scanner: 'blue',
  cracker: 'orange',
  proxy: 'cyan',
  c2: 'purple',
  utility: 'default',
};

const ArsenalDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [item, setItem] = useState<ArsenalItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('usage');

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = getArsenalItemById(id) ?? null;
      setItem(data);
      setLoading(false);
    }, 200);
  }, [id]);

  /** 关联 CVE 列表 */
  const relatedCves = (item?.relatedCves ?? []).map((cve) =>
    mockVulnerabilities.find((v) => v.cve === cve),
  ).filter((v): v is NonNullable<typeof v> => !!v);

  /** 关联攻击链（stages.technique 中包含武器名称或 id） */
  const relatedChains = mockAttackChains.filter((c) => {
    if (!item) return false;
    return c.stages.some((s) => s.description?.includes(item.name) || s.technique?.includes(item.id));
  });

  /** 复制使用方式 */
  const handleCopyUsage = () => {
    if (item?.usage) {
      navigator.clipboard?.writeText(item.usage);
      message.success('使用命令已复制');
    }
  };

  /** 评分分布图（基于评分与分类生成示例数据） */
  const ratingChartOption: EChartsOption = {
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
          { value: item?.rating === 5 ? 70 : 25, name: '功能完整', itemStyle: { color: colors.success } },
          { value: 15, name: '易用性', itemStyle: { color: colors.info } },
          { value: 10, name: '稳定性', itemStyle: { color: colors.warning } },
          { value: 5, name: '隐蔽性', itemStyle: { color: colors.error } },
        ],
      },
    ],
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载武器详情..." /></div>;
  }

  if (!item) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到武器">
          <Button type="primary" onClick={() => navigate('/redteam/arsenal')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/redteam/arsenal')}>返回</Button>
          <ToolOutlined style={{ fontSize: 24, color: colors.primary[500] }} />
          <Title level={4} style={{ margin: 0 }}>{item.name}</Title>
          <Tag color={categoryColor[item.category]}>{ArsenalCategoryLabel[item.category]}</Tag>
          <Tag color={item.enabled ? 'success' : 'default'} icon={item.enabled ? <CheckCircleOutlined /> : undefined}>
            {item.enabled ? '已启用' : '已禁用'}
          </Tag>
        </Space>
        <Space>
          <Button icon={<CopyOutlined />} onClick={handleCopyUsage}>复制命令</Button>
          <Button type="primary" onClick={() => message.success('已加入作战工具集')}>加入工具集</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="综合评分"
              valueRender={() => <Rate disabled value={item.rating} />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="版本" value={item.version} valueStyle={{ fontSize: 16 }} prefix={<TagOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="关联 CVE" value={relatedCves.length} prefix={<BugOutlined />} valueStyle={{ color: colors.error }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="关联攻击链" value={relatedChains.length} prefix={<FireOutlined />} valueStyle={{ color: colors.warning }} /></Card>
        </Col>
      </Row>

      {/* 基本信息 */}
      <Card size="small" title={<Space><ToolOutlined /> 武器基本信息</Space>} style={{ marginBottom: spacing[4] }}>
        <ProDescriptions
          column={2}
          bordered
          size="small"
          dataSource={{
            id: item.id,
            name: item.name,
            category: ArsenalCategoryLabel[item.category],
            version: item.version,
            author: item.author,
            platforms: item.platforms.join(' / '),
            rating: `${item.rating} / 5`,
            enabled: item.enabled ? '已启用' : '已禁用',
            updateTime: formatDateTime(item.updateTime),
          }}
          columns={[
            { title: '武器 ID', dataIndex: 'id', key: 'id' },
            { title: '名称', dataIndex: 'name', key: 'name' },
            { title: '分类', dataIndex: 'category', key: 'category' },
            { title: '版本', dataIndex: 'version', key: 'version' },
            { title: '作者', dataIndex: 'author', key: 'author', render: (v: React.ReactNode) => <Space><UserOutlined />{v}</Space> },
            { title: '评分', dataIndex: 'rating', key: 'rating' },
            { title: '支持平台', dataIndex: 'platforms', key: 'platforms', span: 2 },
            { title: '状态', dataIndex: 'enabled', key: 'enabled' },
            { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
          ]}
        />
      </Card>

      {/* 描述 */}
      <Card size="small" title={<Space><CodeOutlined /> 武器描述</Space>} style={{ marginBottom: spacing[4] }}>
        <Paragraph>{item.description}</Paragraph>
      </Card>

      {/* Tabs：使用方式 / 检测规则 / 关联信息 */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'usage',
            label: <span><CodeOutlined /> 使用方式</span>,
            children: (
              <Card
                size="small"
                title={<Space><CodeOutlined /> 命令行示例</Space>}
                extra={<Button size="small" icon={<CopyOutlined />} onClick={handleCopyUsage}>复制</Button>}
              >
                <pre
                  style={{
                    background: '#0c0c0c',
                    color: '#1890ff',
                    padding: 16,
                    borderRadius: 6,
                    fontSize: 13,
                    overflow: 'auto',
                    fontFamily: 'Consolas, Monaco, monospace',
                  }}
                >
                  {item.usage}
                </pre>
                <Divider style={{ margin: '12px 0' }} />
                <Space wrap>
                  <Text type="secondary">支持平台：</Text>
                  {item.platforms.map((p) => (
                    <Tag key={p} icon={platformIcon[p] ?? <EnvironmentOutlined />}>{p}</Tag>
                  ))}
                </Space>
              </Card>
            ),
          },
          {
            key: 'detection',
            label: <span><BugOutlined /> 检测规则</span>,
            children: (
              <Card size="small" title={<Space><BugOutlined /> 蓝队检测建议</Space>}>
                <List
                  size="small"
                  dataSource={item.detectionRules}
                  renderItem={(rule, idx) => (
                    <List.Item>
                      <Space>
                        <Tag color="red" icon={<BugOutlined />}>规则 {idx + 1}</Tag>
                        <Text>{rule}</Text>
                      </Space>
                    </List.Item>
                  )}
                />
              </Card>
            ),
          },
          {
            key: 'related',
            label: <span><FireOutlined /> 关联信息</span>,
            children: (
              <Row gutter={16}>
                <Col xs={24} lg={12}>
                  <Card size="small" title={<Space><BugOutlined /> 关联 CVE ({relatedCves.length})</Space>} style={{ marginBottom: spacing[4] }}>
                    {relatedCves.length === 0 ? (
                      <Empty description="无关联 CVE" image={Empty.PRESENTED_IMAGE_SIMPLE} />
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
                            <Tag>{chain.target}</Tag>
                          </List.Item>
                        )}
                      />
                    )}
                  </Card>
                </Col>
              </Row>
            ),
          },
          {
            key: 'analytics',
            label: <span><ClockCircleOutlined /> 评分分析</span>,
            children: (
              <Card size="small" title={<Space><ClockCircleOutlined /> 评分维度分析</Space>}>
                <ReactECharts option={ratingChartOption} style={{ height: 300, width: '100%' }} notMerge lazyUpdate />
                <Divider />
                <Descriptions column={2} size="small">
                  <Descriptions.Item label="功能完整度">{item.rating >= 4 ? '优秀' : '一般'}</Descriptions.Item>
                  <Descriptions.Item label="更新频率">{formatDateTime(item.updateTime)}</Descriptions.Item>
                  <Descriptions.Item label="作者">{item.author}</Descriptions.Item>
                  <Descriptions.Item label="分类">{ArsenalCategoryLabel[item.category]}</Descriptions.Item>
                </Descriptions>
              </Card>
            ),
          },
        ]}
      />
    </div>
  );
};

export default ArsenalDetailPage;
