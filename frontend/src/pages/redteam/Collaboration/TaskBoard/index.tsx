/**
 * 任务看板页
 * - 看板视图：四列 Kanban（待办 / 进行中 / 已完成 / 阻塞）
 * - 任务详情侧边栏：点击任务卡片可查看
 * - 任务统计：按状态/优先级/负责人统计
 */
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Row,
  Col,
  Statistic,
  Drawer,
  Empty,
  Spin,
  List,
  Avatar,
  Tooltip,
  message,
  Descriptions,
  Progress,
  Segmented,
  Input,
  Badge,
  Divider,
} from 'antd';
import {
  ArrowLeftOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  PlusOutlined,
  CommentOutlined,
  PaperClipOutlined,
  SearchOutlined,
  CalendarOutlined,
  UserOutlined,
  FireOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import {
  mockCollaborationTasks,
  mockTeamMembers,
} from '@/mock/collaboration';
import type { CollaborationTask, CollaborationStatus } from '@/types';
import { CollaborationStatusLabel } from '@/types';
import { formatDate } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 任务状态颜色 */
const statusColor: Record<CollaborationStatus, string> = {
  todo: colors.neutral[400],
  doing: colors.info,
  done: colors.success,
  blocked: colors.error,
};

/** 任务状态背景色 */
const statusBg: Record<CollaborationStatus, string> = {
  todo: '#fafafa',
  doing: '#e6f7ff',
  done: '#f6ffed',
  blocked: '#fff1f0',
};

/** 任务状态边框色 */
const statusBorder: Record<CollaborationStatus, string> = {
  todo: '#e8e8e8',
  doing: '#91d5ff',
  done: '#b7eb8f',
  blocked: '#ffa39e',
};

/** 优先级颜色 */
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
const boardColumns: Array<{ status: CollaborationStatus; title: string; icon: React.ReactNode }> = [
  { status: 'todo', title: '待办', icon: <ClockCircleOutlined /> },
  { status: 'doing', title: '进行中', icon: <FireOutlined /> },
  { status: 'done', title: '已完成', icon: <CheckCircleOutlined /> },
  { status: 'blocked', title: '阻塞', icon: <WarningOutlined /> },
];

const TaskBoardPage: React.FC = () => {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<CollaborationTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState<'board' | 'list'>('board');
  const [keyword, setKeyword] = useState('');
  const [selectedTask, setSelectedTask] = useState<CollaborationTask | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      setTasks([...mockCollaborationTasks]);
      setLoading(false);
    }, 200);
  }, []);

  /** 过滤后的任务 */
  const filteredTasks = useMemo(() => {
    if (!keyword.trim()) return tasks;
    const kw = keyword.toLowerCase();
    return tasks.filter(
      (t) =>
        t.title.toLowerCase().includes(kw) ||
        t.description.toLowerCase().includes(kw) ||
        t.assignee.toLowerCase().includes(kw) ||
        t.tags.some((tag) => tag.toLowerCase().includes(kw)),
    );
  }, [tasks, keyword]);

  /** 按状态分组 */
  const grouped = useMemo(() => {
    const map: Record<CollaborationStatus, CollaborationTask[]> = {
      todo: [],
      doing: [],
      done: [],
      blocked: [],
    };
    filteredTasks.forEach((t) => {
      map[t.status].push(t);
    });
    return map;
  }, [filteredTasks]);

  /** 统计 */
  const stats = useMemo(() => {
    const total = filteredTasks.length;
    const done = grouped.done.length;
    const doing = grouped.doing.length;
    const blocked = grouped.blocked.length;
    const urgent = filteredTasks.filter((t) => t.priority === 'urgent').length;
    return { total, done, doing, blocked, urgent };
  }, [filteredTasks, grouped]);

  /** 优先级分布图 */
  const priorityChartOption: EChartsOption = {
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
          { value: filteredTasks.filter((t) => t.priority === 'urgent').length, name: '紧急', itemStyle: { color: colors.error } },
          { value: filteredTasks.filter((t) => t.priority === 'high').length, name: '高', itemStyle: { color: colors.warning } },
          { value: filteredTasks.filter((t) => t.priority === 'medium').length, name: '中', itemStyle: { color: colors.info } },
          { value: filteredTasks.filter((t) => t.priority === 'low').length, name: '低', itemStyle: { color: colors.neutral[400] } },
        ],
      },
    ],
  };

  /** 处理任务卡片点击 */
  const handleTaskClick = (task: CollaborationTask) => {
    setSelectedTask(task);
    setDrawerOpen(true);
  };

  /** 渲染任务卡片 */
  const renderTaskCard = (task: CollaborationTask) => {
    const assigneeMember = mockTeamMembers.find((m) => m.name === task.assignee);
    return (
      <Card
        key={task.id}
        size="small"
        hoverable
        onClick={() => handleTaskClick(task)}
        style={{
          marginBottom: spacing[3],
          borderLeft: `4px solid ${statusBorder[task.status]}`,
          background: statusBg[task.status],
          cursor: 'pointer',
        }}
      >
        <div style={{ marginBottom: 8 }}>
          <Text strong>{task.title}</Text>
        </div>
        <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ fontSize: 12, margin: 0, marginBottom: 8 }}>
          {task.description}
        </Paragraph>
        <Space wrap size={[4, 4]} style={{ marginBottom: 8 }}>
          {task.tags.map((tag) => (
            <Tag key={tag} style={{ fontSize: 11 }}>{tag}</Tag>
          ))}
        </Space>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
          <Space size={8}>
            <Avatar size="small" icon={<UserOutlined />} src={assigneeMember?.avatar} />
            <Text type="secondary" style={{ fontSize: 12 }}>{task.assignee}</Text>
          </Space>
          <Space size={8}>
            <Tooltip title={`评论 ${task.comments}`}>
              <Badge count={task.comments} size="small" offset={[2, 0]}>
                <CommentOutlined style={{ fontSize: 12 }} />
              </Badge>
            </Tooltip>
            <Tooltip title={`附件 ${task.attachments}`}>
              <PaperClipOutlined style={{ fontSize: 12 }} />
            </Tooltip>
            <Tooltip title={`截止 ${formatDate(task.dueDate)}`}>
              <CalendarOutlined style={{ fontSize: 12, color: new Date(task.dueDate) < new Date() ? colors.error : undefined }} />
            </Tooltip>
          </Space>
        </div>
        <div style={{ marginTop: 8 }}>
          <Tag color={priorityColor[task.priority]} style={{ fontSize: 11 }}>{priorityText[task.priority]}</Tag>
        </div>
      </Card>
    );
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载任务看板..." /></div>;
  }

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/redteam/collaboration')}>返回</Button>
          <TeamOutlined style={{ fontSize: 24, color: colors.info }} />
          <Title level={4} style={{ margin: 0 }}>任务看板</Title>
          <Tag color="blue">总 {stats.total} 项</Tag>
        </Space>
        <Space>
          <Input
            placeholder="搜索任务标题/描述/负责人/标签"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            allowClear
            prefix={<SearchOutlined />}
            style={{ width: 280 }}
          />
          <Segmented
            options={[
              { label: '看板', value: 'board' },
              { label: '列表', value: 'list' },
            ]}
            value={view}
            onChange={(v) => setView(v as 'board' | 'list')}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => message.success('新建任务...')}>新建任务</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="任务总数" value={stats.total} prefix={<TeamOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="已完成" value={stats.done} prefix={<CheckCircleOutlined />} valueStyle={{ color: colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="进行中" value={stats.doing} prefix={<FireOutlined />} valueStyle={{ color: colors.warning }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="阻塞 / 紧急" value={`${stats.blocked} / ${stats.urgent}`} prefix={<WarningOutlined />} valueStyle={{ color: colors.error }} /></Card>
        </Col>
      </Row>

      {/* 主体内容 */}
      {view === 'board' ? (
        <Row gutter={16}>
          {boardColumns.map((col) => (
            <Col xs={24} sm={12} lg={6} key={col.status}>
              <Card
                size="small"
                title={
                  <Space>
                    <span style={{ color: statusColor[col.status] }}>{col.icon}</span>
                    <span>{col.title}</span>
                    <Tag>{grouped[col.status].length}</Tag>
                  </Space>
                }
                styles={{ body: { background: statusBg[col.status], minHeight: 400 } }}
              >
                {grouped[col.status].length === 0 ? (
                  <Empty description={`暂无${col.title}任务`} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  grouped[col.status].map(renderTaskCard)
                )}
              </Card>
            </Col>
          ))}
        </Row>
      ) : (
        <Card size="small">
          <List
            dataSource={filteredTasks}
            renderItem={(task) => (
              <List.Item
                actions={[
                  <Button
                    key="detail"
                    type="link"
                    size="small"
                    onClick={() => handleTaskClick(task)}
                  >
                    详情
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  avatar={
                    <Avatar
                      style={{ backgroundColor: statusColor[task.status] }}
                      icon={task.status === 'done' ? <CheckCircleOutlined /> : task.status === 'blocked' ? <WarningOutlined /> : <ClockCircleOutlined />}
                    />
                  }
                  title={
                    <Space>
                      <Text strong>{task.title}</Text>
                      <Tag color={priorityColor[task.priority]}>{priorityText[task.priority]}</Tag>
                      <Tag>{CollaborationStatusLabel[task.status]}</Tag>
                    </Space>
                  }
                  description={
                    <Space split={<Divider type="vertical" />}>
                      <Text type="secondary">{task.description}</Text>
                      <Text type="secondary"><UserOutlined /> {task.assignee}</Text>
                      <Text type="secondary"><CalendarOutlined /> {formatDate(task.dueDate)}</Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        </Card>
      )}

      {/* 优先级分布图 */}
      <Card size="small" title={<Space><ThunderboltOutlined /> 任务优先级分布</Space>} style={{ marginTop: spacing[4] }}>
        <ReactECharts option={priorityChartOption} style={{ height: 280, width: '100%' }} notMerge lazyUpdate />
      </Card>

      {/* 任务详情抽屉 */}
      <Drawer
        title="任务详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
        extra={
          selectedTask && (
            <Tag color={priorityColor[selectedTask.priority]}>{priorityText[selectedTask.priority]}</Tag>
          )
        }
      >
        {selectedTask && (
          <div>
            <Title level={5}>{selectedTask.title}</Title>
            <Paragraph type="secondary">{selectedTask.description}</Paragraph>
            <Descriptions column={1} size="small" bordered style={{ marginTop: 16 }}>
              <Descriptions.Item label="任务 ID">{selectedTask.id}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor[selectedTask.status]}>{CollaborationStatusLabel[selectedTask.status]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="优先级">
                <Tag color={priorityColor[selectedTask.priority]}>{priorityText[selectedTask.priority]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="负责人">
                <Space><Avatar size="small" icon={<UserOutlined />} />{selectedTask.assignee}</Space>
              </Descriptions.Item>
              <Descriptions.Item label="截止日期">{formatDate(selectedTask.dueDate)}</Descriptions.Item>
              <Descriptions.Item label="评论数">{selectedTask.comments}</Descriptions.Item>
              <Descriptions.Item label="附件数">{selectedTask.attachments}</Descriptions.Item>
              <Descriptions.Item label="标签">
                <Space wrap>
                  {selectedTask.tags.map((t) => <Tag key={t}>{t}</Tag>)}
                </Space>
              </Descriptions.Item>
            </Descriptions>
            <Divider />
            <Title level={5}>进度跟踪</Title>
            <Progress
              percent={
                selectedTask.status === 'done' ? 100 :
                selectedTask.status === 'doing' ? 50 :
                selectedTask.status === 'blocked' ? 30 : 0
              }
              status={selectedTask.status === 'blocked' ? 'exception' : selectedTask.status === 'done' ? 'success' : 'active'}
            />
            <Divider />
            <Space>
              <Button type="primary" onClick={() => message.success('已更新任务状态')}>更新状态</Button>
              <Button onClick={() => message.success('添加评论...')}>添加评论</Button>
              <Button onClick={() => message.success('上传附件...')}>上传附件</Button>
            </Space>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default TaskBoardPage;
