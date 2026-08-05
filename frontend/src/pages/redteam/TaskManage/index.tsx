/**
 * 任务管理页面（红方协同）
 * - ProTable 视图：搜索 + 列表 + 进度条 + 操作
 * - 看板视图：待办 / 进行中 / 已完成 / 阻塞 / 已取消 五列
 * - 任务详情抽屉：基本信息 + 时间线 + 关联文件
 * - 新建任务模态框：标题 / 描述 / 负责人 / 优先级 / 截止日期
 * - 可访问性：WCAG AA，关键控件带 aria-label
 */
import React, { useMemo, useRef, useState } from 'react';
import {
  Card,
  Typography,
  Button,
  Space,
  Tag,
  Drawer,
  Modal,
  Form,
  Input,
  Select,
  DatePicker,
  Progress,
  Timeline,
  Popconfirm,
  message,
  Segmented,
  Empty,
  Avatar,
  Tooltip,
} from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import {
  PlusOutlined,
  ReloadOutlined,
  EyeOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  StopOutlined,
  TeamOutlined,
  PaperClipOutlined,
  CommentOutlined,
} from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { getTasks, getTaskDetail, saveTask, updateTaskStatus, deleteTask } from '@/services';
import {
  TaskStatusLabel,
  TaskPriorityLabel,
} from '@/types';
import type { TaskItem, TaskStatus, TaskPriority } from '@/types';
import { formatDateTime } from '@/utils';
import { getAriaLabel } from '@/utils/accessibility';
import { colors } from '@/styles/tokens';
// V4.7-P1-2 审批进度时间轴组件
import ApprovalTimeline from './components/ApprovalTimeline';

const { Title, Text, Paragraph } = Typography;

/** 任务状态颜色 */
const statusColor: Record<TaskStatus, string> = {
  todo: 'default',
  doing: 'processing',
  done: 'success',
  blocked: 'error',
  cancelled: 'warning',
};

/** 任务状态图标 */
const statusIcon: Record<TaskStatus, React.ReactNode> = {
  todo: <ClockCircleOutlined />,
  doing: <ClockCircleOutlined />,
  done: <CheckCircleOutlined />,
  blocked: <ExclamationCircleOutlined />,
  cancelled: <StopOutlined />,
};

/** 任务优先级颜色 */
const priorityColor: Record<TaskPriority, string> = {
  low: 'default',
  medium: 'blue',
  high: 'orange',
  urgent: 'red',
};

/** 看板列定义 */
const kanbanColumns: Array<{ status: TaskStatus; color: string; icon: React.ReactNode }> = [
  { status: 'todo', color: colors.neutral[500], icon: <ClockCircleOutlined /> },
  { status: 'doing', color: colors.severity.info, icon: <ClockCircleOutlined /> },
  { status: 'done', color: colors.success, icon: <CheckCircleOutlined /> },
  { status: 'blocked', color: colors.severity.critical, icon: <ExclamationCircleOutlined /> },
  { status: 'cancelled', color: colors.warning, icon: <StopOutlined /> },
];

/** 视图模式 */
type ViewMode = 'table' | 'kanban';

/** 新建任务表单值 */
interface TaskFormValues {
  title: string;
  description: string;
  assignee: string;
  priority: TaskPriority;
  status: TaskStatus;
  targetName?: string;
  dueDate?: Dayjs;
  tags?: string[];
}

/**
 * 任务管理主组件
 */
const TaskManagePage: React.FC = () => {
  const actionRef = useRef<ActionType>(null);
  const [viewMode, setViewMode] = useState<ViewMode>('table');
  const [taskList, setTaskList] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<TaskItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<TaskFormValues>();

  /** 加载任务列表（看板视图使用） */
  const fetchTasks = async () => {
    setLoading(true);
    try {
      const res = await getTasks();
      if (res.code === 200 || res.code === 0) {
        setTaskList(res.data);
      }
    } finally {
      setLoading(false);
    }
  };

  /** 切换到看板视图时加载 */
  React.useEffect(() => {
    if (viewMode === 'kanban') {
      fetchTasks();
    }
  }, [viewMode]);

  /** 按状态分组（看板视图） */
  const taskGroups = useMemo(() => {
    const groups: Record<TaskStatus, TaskItem[]> = {
      todo: [],
      doing: [],
      done: [],
      blocked: [],
      cancelled: [],
    };
    for (const t of taskList) groups[t.status].push(t);
    return groups;
  }, [taskList]);

  /** 打开详情抽屉 */
  const openDetail = (record: TaskItem) => {
    getTaskDetail(record.id).then((res) => {
      setDetail(res.data);
      setDrawerOpen(true);
    });
  };

  /** 打开新建模态框 */
  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({
      status: 'todo' as TaskStatus,
      priority: 'medium' as TaskPriority,
    });
    setModalOpen(true);
  };

  /** 提交新建任务 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await saveTask({
        title: values.title,
        description: values.description,
        assignee: values.assignee,
        priority: values.priority,
        status: values.status,
        targetName: values.targetName,
        dueDate: values.dueDate?.format('YYYY-MM-DD'),
        tags: values.tags ?? [],
        progress: 0,
      });
      message.success('任务已创建');
      setModalOpen(false);
      if (viewMode === 'kanban') {
        fetchTasks();
      } else {
        actionRef.current?.reload();
      }
    } catch {
      // 校验失败
    } finally {
      setSubmitting(false);
    }
  };

  /** 切换状态 */
  const handleStatusChange = async (id: string, status: TaskStatus) => {
    await updateTaskStatus(id, status);
    message.success(`状态已更新为：${TaskStatusLabel[status]}`);
    if (viewMode === 'kanban') {
      fetchTasks();
    } else {
      actionRef.current?.reload();
    }
  };

  /** 删除任务 */
  const handleDelete = async (id: string) => {
    await deleteTask(id);
    message.success('任务已删除');
    if (viewMode === 'kanban') {
      fetchTasks();
    } else {
      actionRef.current?.reload();
    }
  };

  /** 表格列定义 */
  const columns: ProColumns<TaskItem>[] = useMemo(
    () => [
      {
        title: '任务标题',
        dataIndex: 'title',
        key: 'title',
        width: 220,
        copyable: false,
        render: (_, record) => (
          <Button type="link" size="small" style={{ padding: 0 }} onClick={() => openDetail(record)}>
            <Text strong>{record.title}</Text>
          </Button>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 110,
        valueType: 'select',
        valueEnum: {
          todo: { text: TaskStatusLabel.todo, status: 'Default' },
          doing: { text: TaskStatusLabel.doing, status: 'Processing' },
          done: { text: TaskStatusLabel.done, status: 'Success' },
          blocked: { text: TaskStatusLabel.blocked, status: 'Error' },
          cancelled: { text: TaskStatusLabel.cancelled, status: 'Warning' },
        },
        render: (_, record) => (
          <Tag color={statusColor[record.status]} icon={statusIcon[record.status]}>
            {TaskStatusLabel[record.status]}
          </Tag>
        ),
      },
      {
        title: '优先级',
        dataIndex: 'priority',
        key: 'priority',
        width: 90,
        valueType: 'select',
        valueEnum: {
          low: { text: TaskPriorityLabel.low, status: 'Default' },
          medium: { text: TaskPriorityLabel.medium, status: 'Processing' },
          high: { text: TaskPriorityLabel.high, status: 'Warning' },
          urgent: { text: TaskPriorityLabel.urgent, status: 'Error' },
        },
        render: (_, record) => (
          <Tag color={priorityColor[record.priority]}>
            {TaskPriorityLabel[record.priority]}
          </Tag>
        ),
      },
      {
        title: '负责人',
        dataIndex: 'assignee',
        key: 'assignee',
        width: 110,
        render: (v: unknown) => (
          <Space size={4}>
            <Avatar size={20} icon={<TeamOutlined />} style={{ background: colors.primary[500] }} />
            <Text style={{ fontSize: 12 }}>{v as string}</Text>
          </Space>
        ),
      },
      {
        title: '目标',
        dataIndex: 'targetName',
        key: 'targetName',
        width: 140,
        ellipsis: true,
        hideInSearch: true,
        render: (v: unknown) =>
          v ? <Tag color="blue">{v as string}</Tag> : <Text type="secondary">-</Text>,
      },
      {
        title: '进度',
        dataIndex: 'progress',
        key: 'progress',
        width: 160,
        hideInSearch: true,
        sorter: (a, b) => a.progress - b.progress,
        render: (_, record) => (
          <Progress
            percent={record.progress}
            size="small"
            status={
              record.status === 'done'
                ? 'success'
                : record.status === 'blocked'
                  ? 'exception'
                  : 'active'
            }
          />
        ),
      },
      {
        title: '截止日期',
        dataIndex: 'dueDate',
        key: 'dueDate',
        width: 120,
        hideInSearch: true,
        sorter: (a, b) => (a.dueDate < b.dueDate ? -1 : 1),
        render: (v: unknown) => <Text style={{ fontSize: 12 }}>{v as string}</Text>,
      },
      {
        title: '评论/附件',
        key: 'meta',
        width: 100,
        hideInSearch: true,
        render: (_, record) => (
          <Space size={8} style={{ fontSize: 11, color: colors.neutral[500] }}>
            <Tooltip title="评论数">
              <span>
                <CommentOutlined /> {record.comments}
              </span>
            </Tooltip>
            <Tooltip title="附件数">
              <span>
                <PaperClipOutlined /> {record.attachments}
              </span>
            </Tooltip>
          </Space>
        ),
      },
      {
        title: '操作',
        key: 'action',
        width: 220,
        fixed: 'right',
        hideInSearch: true,
        render: (_, record) => (
          <Space size={4}>
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => openDetail(record)}
              aria-label={getAriaLabel('button.view', { label: record.title })}
            >
              详情
            </Button>
            {record.status !== 'done' && (
              <Button
                type="link"
                size="small"
                icon={<CheckCircleOutlined />}
                onClick={() => handleStatusChange(record.id, 'done')}
              >
                完成
              </Button>
            )}
            <Popconfirm
              title="确认删除该任务？"
              onConfirm={() => handleDelete(record.id)}
              aria-label={getAriaLabel('button.delete', { label: record.title })}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [viewMode],
  );

  /** 状态选项 */
  const statusOptions = (Object.keys(TaskStatusLabel) as TaskStatus[]).map((s) => ({
    label: TaskStatusLabel[s],
    value: s,
  }));

  /** 优先级选项 */
  const priorityOptions = (Object.keys(TaskPriorityLabel) as TaskPriority[]).map((p) => ({
    label: TaskPriorityLabel[p],
    value: p,
  }));

  return (
    <div>
      <Title level={4}>任务管理</Title>
      <Paragraph type="secondary" style={{ marginTop: -4, marginBottom: 16 }}>
        红方协同任务调度：支持列表视图与看板视图切换，可新建、详情、状态流转与删除。
      </Paragraph>

      <Card>
        <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between' }}>
          <Segmented
            options={[
              { label: '列表视图', value: 'table' },
              { label: '看板视图', value: 'kanban' },
            ]}
            value={viewMode}
            onChange={(v) => setViewMode(v as ViewMode)}
            aria-label="切换任务视图"
          />
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => (viewMode === 'kanban' ? fetchTasks() : actionRef.current?.reload())}
              aria-label={getAriaLabel('button.refresh')}
            >
              刷新
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
              aria-label={getAriaLabel('button.submit')}
            >
              新建任务
            </Button>
          </Space>
        </div>

        {viewMode === 'table' ? (
          <ProTable<TaskItem>
            actionRef={actionRef}
            columns={columns}
            rowKey="id"
            search={{ labelWidth: 80 }}
            request={async (params) => {
              const res = await getTasks({
                keyword: params.title,
                status: params.status as TaskStatus | undefined,
                priority: params.priority as TaskPriority | undefined,
              });
              return {
                data: res.data,
                total: res.data.length,
                success: true,
              };
            }}
            pagination={{ pageSize: 10, showSizeChanger: true }}
            scroll={{ x: 1400 }}
            toolBarRender={false}
          />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <div style={{ display: 'flex', gap: 12, minWidth: 1000 }}>
              {kanbanColumns.map((col) => (
                <div
                  key={col.status}
                  style={{
                    flex: 1,
                    minWidth: 220,
                    background: colors.neutral[50],
                    borderRadius: 8,
                    padding: 12,
                  }}
                  role="region"
                  aria-label={`${TaskStatusLabel[col.status]}列`}
                >
                  <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ color: col.color }}>{col.icon}</span>
                    <Text strong>{TaskStatusLabel[col.status]}</Text>
                    <Tag style={{ marginLeft: 'auto' }}>{taskGroups[col.status]?.length ?? 0}</Tag>
                  </div>
                  {loading ? (
                    <div style={{ textAlign: 'center', padding: 24, color: colors.neutral[500] }}>
                      加载中...
                    </div>
                  ) : taskGroups[col.status]?.length === 0 ? (
                    <Empty
                      description="暂无任务"
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      style={{ margin: '12px 0' }}
                    />
                  ) : (
                    taskGroups[col.status].map((task) => (
                      <Card
                        key={task.id}
                        size="small"
                        hoverable
                        style={{ marginBottom: 8, cursor: 'pointer' }}
                        onClick={() => openDetail(task)}
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
                            {TaskPriorityLabel[task.priority]}
                          </Tag>
                          {task.tags.slice(0, 2).map((t) => (
                            <Tag key={t} style={{ fontSize: 11 }}>
                              {t}
                            </Tag>
                          ))}
                        </Space>
                        <Progress
                          percent={task.progress}
                          size="small"
                          status={
                            task.status === 'done'
                              ? 'success'
                              : task.status === 'blocked'
                                ? 'exception'
                                : 'active'
                          }
                        />
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            marginTop: 6,
                            fontSize: 11,
                            color: colors.neutral[500],
                          }}
                        >
                          <Space size={4}>
                            <Avatar size={18} icon={<TeamOutlined />} />
                            <span>{task.assignee}</span>
                          </Space>
                          <span>{task.dueDate}</span>
                        </div>
                      </Card>
                    ))
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </Card>

      {/* 任务详情抽屉 */}
      <Drawer
        title="任务详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={640}
        destroyOnClose
      >
        {detail && (
          <>
            <div style={{ marginBottom: 12 }}>
              <Title level={5} style={{ marginBottom: 4 }}>
                {detail.title}
              </Title>
              <Space size={6}>
                <Tag color={statusColor[detail.status]} icon={statusIcon[detail.status]}>
                  {TaskStatusLabel[detail.status]}
                </Tag>
                <Tag color={priorityColor[detail.priority]}>
                  {TaskPriorityLabel[detail.priority]}
                </Tag>
                {detail.targetName && <Tag color="blue">{detail.targetName}</Tag>}
              </Space>
            </div>

            <Paragraph type="secondary">{detail.description}</Paragraph>

            <div style={{ margin: '16px 0' }}>
              <Text strong>基本信息</Text>
              <div style={{ marginTop: 8, fontSize: 13, lineHeight: 2 }}>
                <div>
                  <Text type="secondary">负责人：</Text>
                  <Text>{detail.assignee}</Text>
                </div>
                {detail.collaborators && detail.collaborators.length > 0 && (
                  <div>
                    <Text type="secondary">协作人：</Text>
                    <Text>{detail.collaborators.join('、')}</Text>
                  </div>
                )}
                <div>
                  <Text type="secondary">开始时间：</Text>
                  <Text>{formatDateTime(detail.startTime, 'YYYY-MM-DD')}</Text>
                </div>
                <div>
                  <Text type="secondary">截止时间：</Text>
                  <Text>{detail.dueDate}</Text>
                </div>
                {detail.completedAt && (
                  <div>
                    <Text type="secondary">完成时间：</Text>
                    <Text>{formatDateTime(detail.completedAt)}</Text>
                  </div>
                )}
                <div>
                  <Text type="secondary">进度：</Text>
                  <Progress
                    percent={detail.progress}
                    size="small"
                    style={{ display: 'inline-flex', width: 200, marginLeft: 8 }}
                  />
                </div>
              </div>
            </div>

            {detail.tags.length > 0 && (
              <div style={{ margin: '16px 0' }}>
                <Text strong>标签</Text>
                <div style={{ marginTop: 8 }}>
                  <Space wrap size={[4, 4]}>
                    {detail.tags.map((t) => (
                      <Tag key={t}>{t}</Tag>
                    ))}
                  </Space>
                </div>
              </div>
            )}

            {detail.fileNames && detail.fileNames.length > 0 && (
              <div style={{ margin: '16px 0' }}>
                <Text strong>关联文件</Text>
                <div style={{ marginTop: 8 }}>
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    {detail.fileNames.map((f, idx) => (
                      <div
                        key={f}
                        style={{
                          padding: '6px 10px',
                          background: colors.neutral[50],
                          borderRadius: 4,
                          fontSize: 12,
                        }}
                      >
                        <PaperClipOutlined style={{ marginRight: 6 }} />
                        {f}
                        {detail.fileIds?.[idx] && (
                          <Text type="secondary" style={{ marginLeft: 8, fontSize: 11 }}>
                            #{detail.fileIds[idx]}
                          </Text>
                        )}
                      </div>
                    ))}
                  </Space>
                </div>
              </div>
            )}

            <div style={{ margin: '16px 0' }} data-testid="task-approval-timeline-wrapper">
              <ApprovalTimeline taskId={detail.id} />
            </div>

            <div style={{ margin: '16px 0' }}>
              <Text strong>时间线</Text>
              <div style={{ marginTop: 12 }}>
                <Timeline
                  items={detail.timeline.map((tl) => ({
                    color:
                      tl.type === 'status_change'
                        ? 'green'
                        : tl.type === 'create'
                          ? 'blue'
                          : tl.type === 'upload'
                            ? 'orange'
                            : 'gray',
                    children: (
                      <div>
                        <div>
                          <Text strong style={{ fontSize: 13 }}>
                            {tl.title}
                          </Text>
                        </div>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {tl.description}
                        </Text>
                        <div style={{ fontSize: 11, color: colors.neutral[500] }}>
                          {tl.time} · {tl.operator}
                        </div>
                      </div>
                    ),
                  }))}
                />
              </div>
            </div>

            <div style={{ marginTop: 24, borderTop: `1px solid ${colors.neutral[200]}`, paddingTop: 12 }}>
              <Space>
                {detail.status !== 'done' && (
                  <Button
                    type="primary"
                    icon={<CheckCircleOutlined />}
                    onClick={() => {
                      handleStatusChange(detail.id, 'done');
                      setDrawerOpen(false);
                    }}
                  >
                    标记完成
                  </Button>
                )}
                {detail.status === 'done' && (
                  <Button
                    onClick={() => {
                      handleStatusChange(detail.id, 'doing');
                      setDrawerOpen(false);
                    }}
                  >
                    重新打开
                  </Button>
                )}
                <Popconfirm
                  title="确认删除该任务？"
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

      {/* 新建任务模态框 */}
      <Modal
        title="新建任务"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="title"
            label="任务标题"
            rules={[{ required: true, message: '请输入任务标题' }]}
          >
            <Input placeholder="例如：边界资产测绘" />
          </Form.Item>
          <Form.Item
            name="description"
            label="任务描述"
            rules={[{ required: true, message: '请输入任务描述' }]}
          >
            <Input.TextArea rows={3} placeholder="任务详细描述" />
          </Form.Item>
          <Form.Item
            name="assignee"
            label="负责人"
            rules={[{ required: true, message: '请输入负责人' }]}
          >
            <Input placeholder="负责人姓名" />
          </Form.Item>
          <Form.Item name="targetName" label="关联目标">
            <Input placeholder="目标名称（可选）" />
          </Form.Item>
          <Form.Item name="priority" label="优先级" rules={[{ required: true }]}>
            <Select options={priorityOptions} />
          </Form.Item>
          <Form.Item name="status" label="初始状态" rules={[{ required: true }]}>
            <Select options={statusOptions} />
          </Form.Item>
          <Form.Item name="dueDate" label="截止日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="按回车添加标签" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TaskManagePage;
