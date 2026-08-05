/**
 * 威胁情报模块（红方）
 * - IOC 列表 + 详情抽屉（关联文件、攻击组织、CVE）
 * - 威胁行为者列表
 * - 情报订阅源管理
 */
import React, { useEffect, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Table,
  Tag,
  Drawer,
  Descriptions,
  Button,
  Space,
  Tabs,
  List,
  Avatar,
  Typography,
  Tooltip,
  Badge,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  BugOutlined,
  TeamOutlined,
  ApiOutlined,
  ReloadOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import { getThreatIntelList, getThreatIntelDetail, getThreatActors, getIntelFeeds, syncIntelFeed } from '@/services';
import type { ThreatIntelItem, ThreatActor, IntelFeed } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Paragraph, Text } = Typography;

/** 情报类型颜色 */
const intelTypeColor: Record<ThreatIntelItem['type'], string> = {
  ip: 'red',
  domain: 'orange',
  url: 'volcano',
  hash: 'purple',
  email: 'blue',
  cve: 'magenta',
};

const intelTypeText: Record<ThreatIntelItem['type'], string> = {
  ip: 'IP',
  domain: '域名',
  url: 'URL',
  hash: '哈希',
  email: '邮箱',
  cve: 'CVE',
};

/** 行为者成熟度颜色 */
const sophisticationColor: Record<ThreatActor['sophistication'], string> = {
  low: 'default',
  medium: 'blue',
  high: 'orange',
  advanced: 'red',
};

const sophisticationText: Record<ThreatActor['sophistication'], string> = {
  low: '低',
  medium: '中',
  high: '高',
  advanced: '高级',
};

/** 订阅源状态颜色 */
const feedStatusColor: Record<IntelFeed['status'], string> = {
  active: 'success',
  paused: 'default',
  error: 'error',
};

const feedStatusText: Record<IntelFeed['status'], string> = {
  active: '活跃',
  paused: '已暂停',
  error: '错误',
};

const ThreatIntelPage: React.FC = () => {
  const [intelList, setIntelList] = useState<ThreatIntelItem[]>([]);
  const [actors, setActors] = useState<ThreatActor[]>([]);
  const [feeds, setFeeds] = useState<IntelFeed[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ThreatIntelItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [syncingId, setSyncingId] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    Promise.all([getThreatIntelList(), getThreatActors(), getIntelFeeds()])
      .then(([intel, actor, feed]) => {
        setIntelList(intel.data);
        setActors(actor.data);
        setFeeds(feed.data);
      })
      .finally(() => setLoading(false));
  }, []);

  /** 打开详情抽屉 */
  const openDetail = (record: ThreatIntelItem) => {
    getThreatIntelDetail(record.id).then((res) => {
      setDetail(res.data);
      setDrawerOpen(true);
    });
  };

  /** 同步情报源 */
  const handleSync = (feed: IntelFeed) => {
    setSyncingId(feed.id);
    syncIntelFeed(feed.id)
      .then(() => {
        message.success(`已触发同步：${feed.name}`);
        setFeeds((prev) =>
          prev.map((f) =>
            f.id === feed.id ? { ...f, lastSync: new Date().toISOString() } : f,
          ),
        );
      })
      .catch(() => {
        message.success(`已触发同步：${feed.name}（Mock 模拟成功）`);
      })
      .finally(() => setSyncingId(null));
  };

  /** IOC 表格列 */
  const intelColumns: ColumnsType<ThreatIntelItem> = [
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 80,
      render: (type: ThreatIntelItem['type']) => (
        <Tag color={intelTypeColor[type]}>{intelTypeText[type]}</Tag>
      ),
    },
    {
      title: '值',
      dataIndex: 'value',
      key: 'value',
      ellipsis: true,
      render: (text: string, record) => (
        <a onClick={() => openDetail(record)}>{text}</a>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      key: 'confidence',
      width: 100,
      render: (v: number) => (
        <Tag color={v >= 0.9 ? 'red' : v >= 0.8 ? 'orange' : 'default'}>
          {(v * 100).toFixed(0)}%
        </Tag>
      ),
    },
    {
      title: '关联组织',
      dataIndex: 'threatActors',
      key: 'threatActors',
      ellipsis: true,
      render: (arr: string[]) =>
        arr.length === 0 ? (
          <Text type="secondary">-</Text>
        ) : (
          <Space size={4} wrap>
            {arr.map((a) => (
              <Tag key={a} color="red">{a}</Tag>
            ))}
          </Space>
        ),
    },
    {
      title: '出现次数',
      dataIndex: 'occurrences',
      key: 'occurrences',
      width: 90,
      sorter: (a, b) => a.occurrences - b.occurrences,
    },
    {
      title: '最近出现',
      dataIndex: 'lastSeen',
      key: 'lastSeen',
      width: 160,
      render: (t: string) => formatDateTime(t),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => openDetail(record)}>
          查看详情
        </Button>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>威胁情报</Title>
      <Tabs
        defaultActiveKey="ioc"
        items={[
          {
            key: 'ioc',
            label: (
              <span>
                <BugOutlined /> IOC 列表
              </span>
            ),
            children: (
              <Card>
                <Table<ThreatIntelItem>
                  columns={intelColumns}
                  dataSource={intelList}
                  rowKey="id"
                  loading={loading}
                  size="middle"
                  pagination={{ pageSize: 10, showSizeChanger: true }}
                />
              </Card>
            ),
          },
          {
            key: 'actors',
            label: (
              <span>
                <TeamOutlined /> 威胁行为者
              </span>
            ),
            children: (
              <Row gutter={[16, 16]}>
                {actors.map((actor) => (
                  <Col xs={24} md={12} lg={8} key={actor.id}>
                    <Card
                      hoverable
                      title={
                        <Space>
                          <Avatar style={{ background: colors.severity.critical }}>
                            {actor.name.charAt(0)}
                          </Avatar>
                          <span>{actor.name}</span>
                        </Space>
                      }
                    >
                      <Paragraph type="secondary" style={{ marginBottom: 8 }}>
                        别名：{actor.aliases.join(' / ')}
                      </Paragraph>
                      <Descriptions column={1} size="small">
                        <Descriptions.Item label="来源">{actor.origin}</Descriptions.Item>
                        <Descriptions.Item label="动机">{actor.motivation}</Descriptions.Item>
                        <Descriptions.Item label="活跃起始">{actor.activeSince}</Descriptions.Item>
                        <Descriptions.Item label="成熟度">
                          <Tag color={sophisticationColor[actor.sophistication]}>
                            {sophisticationText[actor.sophistication]}
                          </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="目标行业">
                          <Space wrap size={4}>
                            {actor.targets.map((t) => (
                              <Tag key={t} color="blue">{t}</Tag>
                            ))}
                          </Space>
                        </Descriptions.Item>
                        <Descriptions.Item label="TTPs">
                          <Space wrap size={4}>
                            {actor.ttps.map((t) => (
                              <Tag key={t}>{t}</Tag>
                            ))}
                          </Space>
                        </Descriptions.Item>
                      </Descriptions>
                    </Card>
                  </Col>
                ))}
              </Row>
            ),
          },
          {
            key: 'feeds',
            label: (
              <span>
                <ApiOutlined /> 情报订阅源
              </span>
            ),
            children: (
              <Card>
                <List
                  dataSource={feeds}
                  renderItem={(feed) => (
                    <List.Item
                      actions={[
                        <Tooltip title="立即同步" key="sync">
                          <Button
                            type="primary"
                            ghost
                            size="small"
                            icon={<ReloadOutlined />}
                            loading={syncingId === feed.id}
                            onClick={() => handleSync(feed)}
                          >
                            同步
                          </Button>
                        </Tooltip>,
                      ]}
                    >
                      <List.Item.Meta
                        avatar={<Badge status={feedStatusColor[feed.status] === 'success' ? 'success' : feedStatusColor[feed.status] === 'error' ? 'error' : 'default'} />}
                        title={
                          <Space>
                            <Text strong>{feed.name}</Text>
                            <Tag color={feedStatusColor[feed.status]}>{feedStatusText[feed.status]}</Tag>
                            <Tag>{feed.type.toUpperCase()}</Tag>
                            <Tag color="purple">可靠性 {feed.reliability}</Tag>
                          </Space>
                        }
                        description={
                          <Space direction="vertical" size={0}>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {feed.url}
                            </Text>
                            <Space size={16} style={{ fontSize: 12 }}>
                              <Text type="secondary">指标数：{feed.indicators.toLocaleString()}</Text>
                              <Text type="secondary">
                                最近同步：{formatDateTime(feed.lastSync)}
                              </Text>
                            </Space>
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 详情抽屉 */}
      <Drawer
        title="情报详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
      >
        {detail && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="类型">
              <Tag color={intelTypeColor[detail.type]}>{intelTypeText[detail.type]}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="值">{detail.value}</Descriptions.Item>
            <Descriptions.Item label="置信度">{(detail.confidence * 100).toFixed(0)}%</Descriptions.Item>
            <Descriptions.Item label="首次出现">{formatDateTime(detail.firstSeen)}</Descriptions.Item>
            <Descriptions.Item label="最近出现">{formatDateTime(detail.lastSeen)}</Descriptions.Item>
            <Descriptions.Item label="出现次数">{detail.occurrences}</Descriptions.Item>
            <Descriptions.Item label="标签">
              <Space wrap size={4}>
                {detail.tags.map((t) => (
                  <Tag key={t} color="red">{t}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="来源">
              <Space wrap size={4}>
                {detail.sources.map((s) => (
                  <Tag key={s} color="blue">{s}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="关联攻击组织">
              <Space wrap size={4}>
                {detail.threatActors.length === 0 ? (
                  <Text type="secondary">-</Text>
                ) : (
                  detail.threatActors.map((a) => (
                    <Tag key={a} color="red">
                      <LinkOutlined /> {a}
                    </Tag>
                  ))
                )}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="关联 CVE">
              <Space wrap size={4}>
                {detail.relatedCves.length === 0 ? (
                  <Text type="secondary">-</Text>
                ) : (
                  detail.relatedCves.map((c) => (
                    <Tag key={c} color="magenta">{c}</Tag>
                  ))
                )}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="关联文件">
              {detail.relatedFiles.length === 0 ? (
                <Text type="secondary">-</Text>
              ) : (
                <Space direction="vertical" size={4}>
                  {detail.relatedFiles.map((f) => (
                    <Tag key={f.id} icon={<LinkOutlined />}>{f.name}</Tag>
                  ))}
                </Space>
              )}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default ThreatIntelPage;
