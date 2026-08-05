/**
 * 武器库模块
 * - 武器/工具列表（分类：漏洞利用/后门/扫描/密码破解等）
 * - 武器详情（使用说明、关联漏洞、检测规则）
 * - 卡片式展示
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Typography,
  Input,
  Segmented,
  Tag,
  Drawer,
  Descriptions,
  Space,
  Rate,
  Badge,
  Empty,
  Tooltip,
  Avatar,
} from 'antd';
import type { SegmentedValue } from 'antd/es/segmented';
import {
  SearchOutlined,
  CodeOutlined,
  BugOutlined,
  ThunderboltOutlined,
  RadarChartOutlined,
  KeyOutlined,
  CloudOutlined,
  ToolOutlined,
  GithubOutlined,
  ExperimentOutlined,
} from '@ant-design/icons';
import { getArsenal, getArsenalDetail } from '@/services';
import { ArsenalCategoryLabel } from '@/types';
import type { ArsenalItem, ArsenalCategory } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Paragraph, Text } = Typography;

/** 分类图标 */
const categoryIcon: Record<ArsenalCategory, React.ReactNode> = {
  exploit: <BugOutlined />,
  backdoor: <CodeOutlined />,
  scanner: <RadarChartOutlined />,
  cracker: <KeyOutlined />,
  proxy: <CloudOutlined />,
  c2: <ThunderboltOutlined />,
  utility: <ToolOutlined />,
};

/** 分类颜色 */
const categoryColor: Record<ArsenalCategory, string> = {
  exploit: colors.severity.high,
  backdoor: colors.severity.critical,
  scanner: colors.severity.info,
  cracker: colors.severity.medium,
  proxy: colors.severity.low,
  c2: colors.primary[500],
  utility: colors.neutral[600],
};

const ArsenalPage: React.FC = () => {
  const [list, setList] = useState<ArsenalItem[]>([]);
  const [filtered, setFiltered] = useState<ArsenalItem[]>([]);
  const [category, setCategory] = useState<SegmentedValue>('all');
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ArsenalItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    setLoading(true);
    getArsenal()
      .then((res) => {
        setList(res.data);
        setFiltered(res.data);
      })
      .finally(() => setLoading(false));
  }, []);

  /** 过滤 */
  useEffect(() => {
    let arr = [...list];
    if (category !== 'all') arr = arr.filter((w) => w.category === category);
    if (keyword) {
      const kw = keyword.toLowerCase();
      arr = arr.filter(
        (w) =>
          w.name.toLowerCase().includes(kw) ||
          w.description.toLowerCase().includes(kw) ||
          w.author.toLowerCase().includes(kw) ||
          w.relatedCves.some((c) => c.toLowerCase().includes(kw)),
      );
    }
    setFiltered(arr);
  }, [list, category, keyword]);

  /** 打开详情 */
  const openDetail = (item: ArsenalItem) => {
    getArsenalDetail(item.id).then((res) => {
      setDetail(res.data);
      setDrawerOpen(true);
    });
  };

  /** 分类选项 */
  const categoryOptions = useMemo(
    () => [
      { label: '全部', value: 'all' },
      ...(Object.keys(ArsenalCategoryLabel) as ArsenalCategory[]).map((c) => ({
        label: ArsenalCategoryLabel[c],
        value: c,
      })),
    ],
    [],
  );

  return (
    <div>
      <Title level={4}>武器库</Title>

      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 12]} align="middle">
          <Col xs={24} md={12}>
            <Input
              placeholder="搜索武器名称 / 描述 / 作者 / CVE"
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} md={12}>
            <Segmented
              options={categoryOptions}
              value={category}
              onChange={setCategory}
              block
            />
          </Col>
        </Row>
      </Card>

      <Card loading={loading}>
        {filtered.length === 0 ? (
          <Empty description="未找到匹配武器" />
        ) : (
          <Row gutter={[16, 16]}>
            {filtered.map((w) => (
              <Col xs={24} sm={12} lg={8} xl={6} key={w.id}>
                <Badge.Ribbon
                  text={ArsenalCategoryLabel[w.category]}
                  color={categoryColor[w.category]}
                >
                  <Card
                    hoverable
                    onClick={() => openDetail(w)}
                    style={{ minHeight: 220 }}
                  >
                    <Card.Meta
                      avatar={
                        <Avatar
                          size={48}
                          style={{ background: categoryColor[w.category] }}
                          icon={categoryIcon[w.category]}
                        />
                      }
                      title={
                        <Space>
                          <Text strong>{w.name}</Text>
                          {!w.enabled && <Tag color="default">已禁用</Tag>}
                        </Space>
                      }
                      description={
                        <Paragraph
                          ellipsis={{ rows: 2 }}
                          style={{ marginBottom: 0, minHeight: 44 }}
                        >
                          {w.description}
                        </Paragraph>
                      }
                    />
                    <div style={{ marginTop: 12 }}>
                      <Space size={16} style={{ fontSize: 12 }}>
                        <Text type="secondary">v{w.version}</Text>
                        <Text type="secondary">{w.author}</Text>
                      </Space>
                      <div style={{ marginTop: 8 }}>
                        <Rate disabled value={w.rating} />
                        <span style={{ marginLeft: 8, fontSize: 12, color: colors.neutral[500] }}>
                          评分 {w.rating}
                        </span>
                      </div>
                      {w.relatedCves.length > 0 && (
                        <div style={{ marginTop: 8 }}>
                          <Space wrap size={4}>
                            {w.relatedCves.slice(0, 2).map((cve) => (
                              <Tag key={cve} color="magenta" style={{ fontSize: 11 }}>
                                {cve}
                              </Tag>
                            ))}
                            {w.relatedCves.length > 2 && (
                              <Tag style={{ fontSize: 11 }}>+{w.relatedCves.length - 2}</Tag>
                            )}
                          </Space>
                        </div>
                      )}
                    </div>
                  </Card>
                </Badge.Ribbon>
              </Col>
            ))}
          </Row>
        )}
      </Card>

      {/* 详情抽屉 */}
      <Drawer
        title={detail ? `${detail.name} v${detail.version}` : '武器详情'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
        extra={
          detail ? (
            <Tooltip title={detail.enabled ? '已启用' : '已禁用'}>
              <Badge status={detail.enabled ? 'success' : 'default'} />
            </Tooltip>
          ) : null
        }
      >
        {detail && (
          <>
            <Paragraph>{detail.description}</Paragraph>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="分类">
                <Tag color={categoryColor[detail.category]}>
                  {ArsenalCategoryLabel[detail.category]}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="版本">{detail.version}</Descriptions.Item>
              <Descriptions.Item label="作者">{detail.author}</Descriptions.Item>
              <Descriptions.Item label="支持平台">
                <Space wrap size={4}>
                  {detail.platforms.map((p) => (
                    <Tag key={p} icon={<GithubOutlined />}>{p}</Tag>
                  ))}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="评分">
                <Rate disabled value={detail.rating} />
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {formatDateTime(detail.updateTime)}
              </Descriptions.Item>
              <Descriptions.Item label="关联漏洞">
                {detail.relatedCves.length === 0 ? (
                  <Text type="secondary">无</Text>
                ) : (
                  <Space wrap size={4}>
                    {detail.relatedCves.map((cve) => (
                      <Tag key={cve} color="magenta">{cve}</Tag>
                    ))}
                  </Space>
                )}
              </Descriptions.Item>
            </Descriptions>

            <Title level={5} style={{ marginTop: 16 }}>
              <ExperimentOutlined /> 使用说明
            </Title>
            <pre
              style={{
                background: colors.dark.surface,
                color: colors.dark.text,
                padding: 12,
                borderRadius: 4,
                overflow: 'auto',
                fontSize: 12,
              }}
            >
              {detail.usage}
            </pre>

            <Title level={5}>
              <BugOutlined /> 检测规则
            </Title>
            {detail.detectionRules.length === 0 ? (
              <Text type="secondary">暂无检测规则</Text>
            ) : (
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                {detail.detectionRules.map((rule) => (
                  <Card key={rule} size="small" style={{ background: colors.neutral[50] }}>
                    <Text style={{ fontSize: 12 }}>{rule}</Text>
                  </Card>
                ))}
              </Space>
            )}
          </>
        )}
      </Drawer>
    </div>
  );
};

export default ArsenalPage;
