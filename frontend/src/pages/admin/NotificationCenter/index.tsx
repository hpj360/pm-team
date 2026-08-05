/**
 * 通知中心页面
 * - 通知列表（List）：按时间倒序展示
 * - 已读/未读筛选 + 类型筛选
 * - 通知详情抽屉
 * - 标记单条已读 / 全部已读 / 删除
 * - 未读数统计卡片
 * - 可访问性：WCAG AA
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Typography,
  Button,
  Space,
  Tag,
  List,
  Avatar,
  Badge,
  Drawer,
  Segmented,
  Popconfirm,
  message,
  Row,
  Col,
  Statistic,
  Empty,
  Tooltip,
} from 'antd';
import {
  ReloadOutlined,
  CheckOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  BellOutlined,
  SafetyCertificateOutlined,
  FileTextOutlined,
  TeamOutlined,
  SettingOutlined,
  MessageOutlined,
  AuditOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import {
  getNotifications,
  getNotificationDetail,
  markNotificationRead,
  markAllNotificationsRead,
  deleteNotification,
} from '@/services';
import {
  NotificationTypeLabel,
  NotificationPriorityLabel,
} from '@/types';
import type { NotificationItem, NotificationType, NotificationPriority } from '@/types';
import { formatDateTime } from '@/utils';
import { getAriaLabel } from '@/utils/accessibility';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 通知类型图标 */
const typeIcon: Record<NotificationType, React.ReactNode> = {
  system: <SettingOutlined />,
  task: <TeamOutlined />,
  file: <FileTextOutlined />,
  security: <SafetyCertificateOutlined />,
  approval: <AuditOutlined />,
  mention: <MessageOutlined />,
};

/** 通知类型颜色 */
const typeColor: Record<NotificationType, string> = {
  system: 'blue',
  task: 'cyan',
  file: 'geekblue',
  security: 'red',
  approval: 'purple',
  mention: 'orange',
};

/** 通知优先级颜色 */
const priorityColor: Record<NotificationPriority, string> = {
  low: 'default',
  normal: 'blue',
  high: 'orange',
  urgent: 'red',
};

/** 已读筛选 */
type ReadFilter = 'all' | 'unread' | 'read';

/**
 * 通知中心主组件
 */
const NotificationCenterPage: React.FC = () => {
  const [list, setList] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<NotificationItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [readFilter, setReadFilter] = useState<ReadFilter>('all');
  const [typeFilter, setTypeFilter] = useState<NotificationType | undefined>();
  const [stats, setStats] = useState({ total: 0, unread: 0, urgent: 0 });

  /** 加载列表 */
  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await getNotifications({ type: typeFilter });
      if (res.code === 200 || res.code === 0) {
        setList(res.data);
        const all = res.data;
        setStats({
          total: all.length,
          unread: all.filter((n) => !n.read).length,
          urgent: all.filter((n) => n.priority === 'urgent' && !n.read).length,
        });
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [typeFilter]);

  /** 按已读筛选过滤列表 */
  const filteredList = useMemo(() => {
    if (readFilter === 'unread') return list.filter((n) => !n.read);
    if (readFilter === 'read') return list.filter((n) => n.read);
    return list;
  }, [list, readFilter]);

  /** 打开详情 */
  const openDetail = (record: NotificationItem) => {
    getNotificationDetail(record.id).then((res) => {
      setDetail(res.data);
      setDrawerOpen(true);
      // 自动标记为已读
      if (!record.read) {
        markNotificationRead(record.id).then(() => {
          fetchList();
        });
      }
    });
  };

  /** 标记单条已读 */
  const handleMarkRead = async (id: string) => {
    await markNotificationRead(id);
    message.success('已标记为已读');
    fetchList();
  };

  /** 全部标记为已读 */
  const handleMarkAllRead = async () => {
    await markAllNotificationsRead();
    message.success('全部通知已标记为已读');
    fetchList();
  };

  /** 删除 */
  const handleDelete = async (id: string) => {
    await deleteNotification(id);
    message.success('通知已删除');
    fetchList();
  };

  /** 类型选项 */
  const typeOptions = [
    { label: '全部', value: undefined as NotificationType | undefined },
    ...(Object.keys(NotificationTypeLabel) as NotificationType[]).map((t) => ({
      label: NotificationTypeLabel[t],
      value: t,
    })),
  ];

  return (
    <div>
      <Title level={4}>通知中心</Title>
      <Paragraph type="secondary" style={{ marginTop: -4, marginBottom: 16 }}>
        集中查看系统、任务、文件、安全、审批与提及通知，支持已读/未读筛选、批量标记已读与删除。
      </Paragraph>

      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={12} md={8}>
          <Card size="small">
            <Statistic title="通知总数" value={stats.total} prefix={<BellOutlined />} />
          </Card>
        </Col>
        <Col xs={12} md={8}>
          <Card size="small">
            <Statistic
              title="未读通知"
              value={stats.unread}
              valueStyle={{ color: colors.severity.high }}
              prefix={<BellOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size="small">
            <Statistic
              title="紧急未读"
              value={stats.urgent}
              valueStyle={{ color: colors.severity.critical }}
              prefix={<SafetyCertificateOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card>
        {/* 工具栏 */}
        <div
          style={{
            marginBottom: 16,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          <Space wrap>
            <Segmented
              options={[
                { label: '全部', value: 'all' },
                { label: '未读', value: 'unread' },
                { label: '已读', value: 'read' },
              ]}
              value={readFilter}
              onChange={(v) => setReadFilter(v as ReadFilter)}
              aria-label="按已读状态筛选"
            />
            <Segmented
              options={typeOptions.map((o) => ({
                label: o.label,
                value: o.value ?? 'all',
              }))}
              value={typeFilter ?? 'all'}
              onChange={(v) => {
                setTypeFilter(v === 'all' ? undefined : (v as NotificationType));
              }}
              aria-label="按类型筛选"
            />
          </Space>
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={fetchList}
              aria-label={getAriaLabel('button.refresh')}
            >
              刷新
            </Button>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={handleMarkAllRead}
              disabled={stats.unread === 0}
              aria-label="全部标记为已读"
            >
              全部已读
            </Button>
          </Space>
        </div>

        {/* 列表 */}
        <List
          loading={loading}
          dataSource={filteredList}
          locale={{
            emptyText: <Empty description="暂无通知" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
          }}
          renderItem={(item) => (
            <List.Item
              key={item.id}
              style={{
                background: item.read ? 'transparent' : colors.primary[50],
                padding: '12px 16px',
                borderRadius: 4,
                marginBottom: 8,
                border: `1px solid ${item.read ? colors.neutral[200] : colors.primary[100]}`,
              }}
              actions={[
                !item.read && (
                  <Tooltip title="标记为已读" key="mark-read">
                    <Button
                      type="text"
                      size="small"
                      icon={<CheckOutlined />}
                      onClick={() => handleMarkRead(item.id)}
                      aria-label={getAriaLabel('button.confirm', { label: item.title })}
                    />
                  </Tooltip>
                ),
                <Popconfirm
                  key="delete"
                  title="确认删除该通知？"
                  onConfirm={() => handleDelete(item.id)}
                  aria-label={getAriaLabel('button.delete', { label: item.title })}
                >
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>,
              ].filter(Boolean) as React.ReactNode[]}
              onClick={() => openDetail(item)}
            >
              <List.Item.Meta
                avatar={
                  <Badge dot={!item.read} offset={[-4, 4]}>
                    <Avatar
                      style={{
                        background:
                          item.type === 'security'
                            ? colors.severity.critical
                            : item.type === 'approval'
                              ? colors.severity.info
                              : colors.neutral[500],
                      }}
                      icon={typeIcon[item.type]}
                    />
                  </Badge>
                }
                title={
                  <Space size={6} wrap>
                    <Text strong={!!item.priority.match('high|urgent')}>
                      {item.title}
                    </Text>
                    <Tag color={typeColor[item.type]} style={{ fontSize: 11 }}>
                      {NotificationTypeLabel[item.type]}
                    </Tag>
                    <Tag color={priorityColor[item.priority]} style={{ fontSize: 11 }}>
                      {NotificationPriorityLabel[item.priority]}
                    </Tag>
                    {!item.read && (
                      <Tag color="red" style={{ fontSize: 11 }}>
                        未读
                      </Tag>
                    )}
                  </Space>
                }
                description={
                  <div>
                    <Paragraph
                      ellipsis={{ rows: 1 }}
                      style={{ marginBottom: 4, fontSize: 12, color: colors.neutral[600] }}
                    >
                      {item.content}
                    </Paragraph>
                    <Space size={8} style={{ fontSize: 11, color: colors.neutral[500] }}>
                      <span>来源：{item.sender}</span>
                      <span>{formatDateTime(item.createTime)}</span>
                      {item.link && <LinkOutlined />}
                    </Space>
                  </div>
                }
              />
            </List.Item>
          )}
        />
      </Card>

      {/* 详情抽屉 */}
      <Drawer
        title="通知详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
        destroyOnClose
      >
        {detail && (
          <>
            <div style={{ marginBottom: 12 }}>
              <Title level={5} style={{ marginBottom: 4 }}>
                {detail.title}
              </Title>
              <Space size={6} wrap>
                <Tag color={typeColor[detail.type]} icon={typeIcon[detail.type]}>
                  {NotificationTypeLabel[detail.type]}
                </Tag>
                <Tag color={priorityColor[detail.priority]}>
                  {NotificationPriorityLabel[detail.priority]}
                </Tag>
                {detail.read ? (
                  <Tag color="default">已读</Tag>
                ) : (
                  <Tag color="red">未读</Tag>
                )}
              </Space>
            </div>

            <Paragraph
              style={{
                background: colors.neutral[50],
                padding: 12,
                borderRadius: 4,
                marginBottom: 16,
              }}
            >
              {detail.content}
            </Paragraph>

            <div style={{ fontSize: 13, lineHeight: 2 }}>
              <div>
                <Text type="secondary">通知 ID：</Text>
                <Text code>{detail.id}</Text>
              </div>
              <div>
                <Text type="secondary">发送者：</Text>
                <Text>{detail.sender}</Text>
              </div>
              <div>
                <Text type="secondary">创建时间：</Text>
                <Text>{formatDateTime(detail.createTime)}</Text>
              </div>
              {detail.readTime && (
                <div>
                  <Text type="secondary">读取时间：</Text>
                  <Text>{formatDateTime(detail.readTime)}</Text>
                </div>
              )}
              {detail.resourceType && (
                <div>
                  <Text type="secondary">关联资源：</Text>
                  <Tag>{detail.resourceType}</Tag>
                  {detail.resourceId && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      #{detail.resourceId}
                    </Text>
                  )}
                </div>
              )}
              {detail.link && (
                <div>
                  <Text type="secondary">关联链接：</Text>
                  <Button
                    type="link"
                    size="small"
                    style={{ padding: 0 }}
                    onClick={() => {
                      window.location.href = detail.link ?? '/';
                    }}
                  >
                    前往查看 <LinkOutlined />
                  </Button>
                </div>
              )}
            </div>

            <div
              style={{
                marginTop: 24,
                borderTop: `1px solid ${colors.neutral[200]}`,
                paddingTop: 12,
              }}
            >
              <Space>
                {!detail.read && (
                  <Button
                    type="primary"
                    icon={<CheckOutlined />}
                    onClick={() => {
                      handleMarkRead(detail.id);
                      setDetail({ ...detail, read: true });
                    }}
                  >
                    标记已读
                  </Button>
                )}
                <Popconfirm
                  title="确认删除该通知？"
                  onConfirm={() => {
                    handleDelete(detail.id);
                    setDrawerOpen(false);
                  }}
                >
                  <Button danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            </div>
          </>
        )}
      </Drawer>
    </div>
  );
};

export default NotificationCenterPage;
