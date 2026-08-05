/**
 * 协同作战模块
 * - 任务看板（Kanban：待办/进行中/已完成/阻塞）
 * - 团队成员列表 + 在线状态
 * - 实时消息（Mock）
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Typography,
  Tag,
  Avatar,
  Badge,
  List,
  Input,
  Button,
  Space,
  Spin,
  Tooltip,
  Empty,
} from 'antd';
import {
  TeamOutlined,
  MessageOutlined,
  SendOutlined,
  PaperClipOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { ProCard } from '@ant-design/pro-components';
import { getCollaborationTasks, getTeamMembers, getCollaborationMessages } from '@/services';
import { CollaborationStatusLabel } from '@/types';
import type { CollaborationTask, TeamMember, CollaborationMessage, CollaborationStatus } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 任务优先级颜色 */
const priorityColor: Record<CollaborationTask['priority'], string> = {
  low: 'default',
  medium: 'blue',
  high: 'orange',
  urgent: 'red',
};

const priorityText: Record<CollaborationTask['priority'], string> = {
  low: '低',
  medium: '中',
  high: '高',
  urgent: '紧急',
};

/** 看板列定义 */
const kanbanColumns: Array<{ status: CollaborationStatus; color: string; icon: React.ReactNode }> = [
  { status: 'todo', color: colors.neutral[400], icon: <ClockCircleOutlined /> },
  { status: 'doing', color: colors.severity.info, icon: <ClockCircleOutlined /> },
  { status: 'done', color: colors.success, icon: <CheckCircleOutlined /> },
  { status: 'blocked', color: colors.severity.critical, icon: <ExclamationCircleOutlined /> },
];

const CollaborationPage: React.FC = () => {
  const [tasks, setTasks] = useState<CollaborationTask[]>([]);
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [messages, setMessages] = useState<CollaborationMessage[]>([]);
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    Promise.all([getCollaborationTasks(), getTeamMembers(), getCollaborationMessages()])
      .then(([task, member, msg]) => {
        setTasks(task.data);
        setMembers(member.data);
        setMessages(msg.data);
      })
      .finally(() => setLoading(false));
  }, []);

  /** 按状态分组 */
  const taskGroups = useMemo(() => {
    const groups: Record<CollaborationStatus, CollaborationTask[]> = {
      todo: [],
      doing: [],
      done: [],
      blocked: [],
    };
    for (const t of tasks) groups[t.status].push(t);
    return groups;
  }, [tasks]);

  /** 在线人数 */
  const onlineCount = useMemo(() => members.filter((m) => m.online).length, [members]);

  /** 发送消息（Mock，仅追加到列表） */
  const handleSend = () => {
    const text = inputText.trim();
    if (!text) return;
    const newMsg: CollaborationMessage = {
      id: `msg_${Date.now()}`,
      sender: '我',
      content: text,
      time: new Date().toISOString(),
      isMine: true,
    };
    setMessages((prev) => [...prev, newMsg]);
    setInputText('');
  };

  return (
    <div>
      <Title level={4}>
        <Space>
          <TeamOutlined />
          协同作战
        </Space>
      </Title>

      <Row gutter={[16, 16]}>
        {/* 左侧：任务看板 */}
        <Col xs={24} xl={18}>
          <ProCard
            title="任务看板"
            headerBordered
            bordered
            extra={
              <Button type="primary" size="small" icon={<PlusOutlined />}>
                新建任务
              </Button>
            }
          >
            <Spin spinning={loading}>
            <Row gutter={[12, 12]}>
              {kanbanColumns.map((col) => (
                <Col xs={24} sm={12} lg={6} key={col.status}>
                  <Card
                    size="small"
                    title={
                      <Space>
                        <span style={{ color: col.color }}>{col.icon}</span>
                        <Text strong>{CollaborationStatusLabel[col.status]}</Text>
                        <Tag>{taskGroups[col.status]?.length ?? 0}</Tag>
                      </Space>
                    }
                    headStyle={{ background: colors.neutral[50] }}
                    bodyStyle={{ padding: 8, minHeight: 360 }}
                  >
                    {taskGroups[col.status]?.length === 0 ? (
                      <Empty description="暂无任务" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                      taskGroups[col.status].map((task) => (
                        <Card
                          key={task.id}
                          size="small"
                          hoverable
                          style={{ marginBottom: 8 }}
                        >
                          <div style={{ marginBottom: 6 }}>
                            <Text strong style={{ fontSize: 13 }}>
                              {task.title}
                            </Text>
                          </div>
                          <Paragraph
                            type="secondary"
                            ellipsis={{ rows: 2 }}
                            style={{ fontSize: 12, marginBottom: 8, minHeight: 32 }}
                          >
                            {task.description}
                          </Paragraph>
                          <Space size={4} wrap style={{ marginBottom: 6 }}>
                            <Tag color={priorityColor[task.priority]}>
                              {priorityText[task.priority]}
                            </Tag>
                            {task.tags.map((t) => (
                              <Tag key={t} style={{ fontSize: 11 }}>{t}</Tag>
                            ))}
                          </Space>
                          <div
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              borderTop: `1px solid ${colors.neutral[200]}`,
                              paddingTop: 6,
                            }}
                          >
                            <Space size={4}>
                              <Avatar size={20} icon={<TeamOutlined />} />
                              <Text type="secondary" style={{ fontSize: 11 }}>
                                {task.assignee}
                              </Text>
                            </Space>
                            <Space size={8} style={{ fontSize: 11, color: colors.neutral[500] }}>
                              <Tooltip title={`${task.comments} 条评论`}>
                                <span>
                                  <MessageOutlined /> {task.comments}
                                </span>
                              </Tooltip>
                              <Tooltip title={`${task.attachments} 个附件`}>
                                <span>
                                  <PaperClipOutlined /> {task.attachments}
                                </span>
                              </Tooltip>
                              <Text type="secondary">{task.dueDate}</Text>
                            </Space>
                          </div>
                        </Card>
                      ))
                    )}
                  </Card>
                </Col>
              ))}
            </Row>
            </Spin>
          </ProCard>
        </Col>

        {/* 右侧：成员 + 消息 */}
        <Col xs={24} xl={6}>
          <Card
            title={
              <Space>
                <TeamOutlined />
                <span>团队成员</span>
                <Tag color="success">{onlineCount} 在线</Tag>
              </Space>
            }
            size="small"
            style={{ marginBottom: 16 }}
          >
            <List
              dataSource={members}
              renderItem={(m) => (
                <List.Item>
                  <List.Item.Meta
                    avatar={
                      <Badge
                        dot
                        status={m.online ? 'success' : 'default'}
                        offset={[-2, 32]}
                      >
                        <Avatar size={32} icon={<TeamOutlined />} />
                      </Badge>
                    }
                    title={<Text strong style={{ fontSize: 13 }}>{m.name}</Text>}
                    description={
                      <Space direction="vertical" size={0}>
                        <Text type="secondary" style={{ fontSize: 11 }}>{m.role}</Text>
                        {m.currentTask && (
                          <Text type="secondary" style={{ fontSize: 11 }}>
                            正在：{m.currentTask}
                          </Text>
                        )}
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>

          <Card
            title={
              <Space>
                <MessageOutlined />
                <span>实时消息</span>
              </Space>
            }
            size="small"
            bodyStyle={{ padding: 0 }}
          >
            <div style={{ maxHeight: 360, overflow: 'auto', padding: 12 }}>
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  style={{
                    display: 'flex',
                    flexDirection: msg.isMine ? 'row-reverse' : 'row',
                    marginBottom: 12,
                  }}
                >
                  <Avatar
                    size={28}
                    style={{
                      background: msg.isMine ? colors.primary[500] : colors.neutral[400],
                      margin: msg.isMine ? '0 0 0 8px' : '0 8px 0 0',
                    }}
                  >
                    {msg.sender.charAt(0)}
                  </Avatar>
                  <div style={{ maxWidth: '70%' }}>
                    <div
                      style={{
                        background: msg.isMine ? colors.primary[50] : colors.neutral[50],
                        padding: '6px 10px',
                        borderRadius: 6,
                        fontSize: 12,
                      }}
                    >
                      <Text strong style={{ fontSize: 11 }}>
                        {msg.sender}
                      </Text>
                      <div>{msg.content}</div>
                    </div>
                    <Text type="secondary" style={{ fontSize: 10 }}>
                      {formatDateTime(msg.time, 'MM-DD HH:mm')}
                    </Text>
                  </div>
                </div>
              ))}
            </div>
            <div style={{ padding: 8, borderTop: `1px solid ${colors.neutral[200]}` }}>
              <Space.Compact style={{ width: '100%' }}>
                <Input
                  placeholder="输入消息..."
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  onPressEnter={handleSend}
                  size="small"
                />
                <Button type="primary" size="small" icon={<SendOutlined />} onClick={handleSend} />
              </Space.Compact>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default CollaborationPage;
