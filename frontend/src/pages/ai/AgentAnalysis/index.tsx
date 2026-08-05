/**
 * AI Agent 自主分析任务页（V5.1）
 * - 任务列表 + 提交新任务输入框
 * - 任务详情：结论 + 证据链 + 引用文件 + 置信度
 * - 推理轨迹可视化（Timeline 展示 Plan → Act → Observe 链路）
 */
import React, { useEffect, useState, useCallback } from 'react';
import {
  Card,
  Row,
  Col,
  Input,
  Button,
  Table,
  Tag,
  Timeline,
  Tabs,
  Typography,
  Space,
  Statistic,
  List,
  Empty,
  Spin,
  message,
  Progress,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  RobotOutlined,
  SendOutlined,
  ReloadOutlined,
  FileSearchOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import {
  submitAnalysis,
  getAgentTasks,
  getAgentTask,
  getAgentTraces,
} from '@/services/agent';
import type { AgentTask, AgentTrace, AgentTaskStatus } from '@/types';
import {
  AgentTaskStatusLabel,
  AgentTaskStatusColor,
} from '@/types';

const { Title, Paragraph, Text } = Typography;
const { TextArea } = Input;

/** 状态图标 */
const StatusIcon: React.FC<{ status: AgentTaskStatus }> = ({ status }) => {
  switch (status) {
    case 'COMPLETED':
      return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
    case 'RUNNING':
      return <LoadingOutlined style={{ color: '#1677ff' }} />;
    case 'FAILED':
      return <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />;
    default:
      return <ClockCircleOutlined style={{ color: '#8c8c8c' }} />;
  }
};

/** Timeline 项颜色 */
const traceColor = (action: string): string => {
  if (action === 'FINAL_ANSWER') return 'green';
  if (action === 'UNKNOWN' || action === 'EMPTY') return 'red';
  return 'blue';
};

const AgentAnalysis: React.FC = () => {
  const [tasks, setTasks] = useState<AgentTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedTask, setSelectedTask] = useState<AgentTask | null>(null);
  const [traces, setTraces] = useState<AgentTrace[]>([]);
  const [query, setQuery] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  /** 加载任务列表 */
  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAgentTasks(undefined, 20);
      if (res.code === 200 && res.data) {
        setTasks(res.data);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  /** 提交分析任务 */
  const handleSubmit = async () => {
    if (!query.trim()) {
      message.warning('请输入分析请求');
      return;
    }
    setSubmitting(true);
    try {
      const res = await submitAnalysis({ query: query.trim() });
      if (res.code === 200 && res.data) {
        message.success('分析任务已提交');
        setQuery('');
        // 刷新列表
        await loadTasks();
        // 选中新任务
        const taskRes = await getAgentTask(res.data);
        if (taskRes.code === 200 && taskRes.data) {
          setSelectedTask(taskRes.data);
          setTraces(taskRes.data.traces ?? []);
        }
      } else {
        message.error(res.message || '提交失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  /** 查看任务详情与轨迹 */
  const handleViewTask = async (taskId: string) => {
    setDetailLoading(true);
    try {
      const [taskRes, tracesRes] = await Promise.all([
        getAgentTask(taskId),
        getAgentTraces(taskId),
      ]);
      if (taskRes.code === 200 && taskRes.data) {
        setSelectedTask(taskRes.data);
      }
      if (tracesRes.code === 200 && tracesRes.data) {
        setTraces(tracesRes.data);
      }
    } finally {
      setDetailLoading(false);
    }
  };

  /** 轮询 RUNNING 任务 */
  useEffect(() => {
    const hasRunning = tasks.some((t) => t.status === 'RUNNING' || t.status === 'PENDING');
    if (!hasRunning) return;
    const timer = setInterval(loadTasks, 5000);
    return () => clearInterval(timer);
  }, [tasks, loadTasks]);

  useEffect(() => {
    loadTasks();
  }, [loadTasks]);

  /** 任务列表表格列 */
  const columns: ColumnsType<AgentTask> = [
    {
      title: '任务ID',
      dataIndex: 'taskId',
      key: 'taskId',
      width: 180,
      ellipsis: true,
      render: (text: string) => <Text copyable code>{text.substring(0, 16)}...</Text>,
    },
    {
      title: '分析请求',
      dataIndex: 'query',
      key: 'query',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: AgentTaskStatus) => (
        <Space>
          <StatusIcon status={status} />
          <Tag color={AgentTaskStatusColor[status]}>
            {AgentTaskStatusLabel[status]}
          </Tag>
        </Space>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      key: 'confidence',
      width: 100,
      render: (val: number | null) =>
        val != null ? `${(val * 100).toFixed(0)}%` : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: unknown, record: AgentTask) => (
        <Button
          type="link"
          size="small"
          onClick={() => handleViewTask(record.taskId)}
        >
          查看详情
        </Button>
      ),
    },
  ];

  /** 推理轨迹 Timeline 项 */
  const timelineItems = traces.map((trace) => ({
    key: trace.step,
    color: traceColor(trace.action),
    dot: trace.action === 'FINAL_ANSWER' ? <CheckCircleOutlined /> : undefined,
    children: (
      <div>
        <div style={{ marginBottom: 4 }}>
          <Tag color={trace.action === 'FINAL_ANSWER' ? 'green' : 'blue'}>
            步骤 {trace.step}
          </Tag>
          <Tag>{trace.action}</Tag>
        </div>
        <div style={{ marginBottom: 4 }}>
          <Text strong>Thought：</Text>
          <Text>{trace.thought}</Text>
        </div>
        {trace.action !== 'FINAL_ANSWER' && trace.actionInput && (
          <div style={{ marginBottom: 4 }}>
            <Text strong>Action Input：</Text>
            <Text code>{trace.actionInput}</Text>
          </div>
        )}
        <div>
          <Text strong>
            {trace.action === 'FINAL_ANSWER' ? '结论：' : 'Observation：'}
          </Text>
          <Paragraph
            style={{ marginBottom: 0, marginTop: 4, whiteSpace: 'pre-wrap' }}
          >
            {trace.observation}
          </Paragraph>
        </div>
      </div>
    ),
  }));

  return (
    <div>
      <Title level={4}>
        <RobotOutlined /> AI Agent 自主分析
      </Title>

      <Row gutter={16}>
        {/* 左侧：任务列表 + 提交 */}
        <Col span={10}>
          <Card
            title="分析任务"
            extra={
              <Button
                icon={<ReloadOutlined />}
                onClick={loadTasks}
                loading={loading}
                size="small"
              >
                刷新
              </Button>
            }
          >
            <div style={{ marginBottom: 16 }}>
              <TextArea
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="输入分析请求，例如：分析最近一周与 APT28 相关的钓鱼文件"
                rows={3}
                maxLength={500}
                showCount
              />
              <Button
                type="primary"
                icon={<SendOutlined />}
                onClick={handleSubmit}
                loading={submitting}
                style={{ marginTop: 8 }}
                block
              >
                提交分析任务
              </Button>
            </div>

            <Table
              columns={columns}
              dataSource={tasks}
              rowKey="taskId"
              loading={loading}
              size="small"
              pagination={{ pageSize: 5, showSizeChanger: false }}
            />
          </Card>
        </Col>

        {/* 右侧：任务详情 */}
        <Col span={14}>
          <Card title="任务详情">
            {detailLoading ? (
              <div style={{ textAlign: 'center', padding: 48 }}>
                <Spin tip="加载中..." />
              </div>
            ) : selectedTask ? (
              <Tabs
                items={[
                  {
                    key: 'overview',
                    label: '分析概览',
                    children: (
                      <div>
                        <Row gutter={16} style={{ marginBottom: 16 }}>
                          <Col span={6}>
                            <Statistic
                              title="状态"
                              valueRender={() => (
                                <Space>
                                  <StatusIcon status={selectedTask.status} />
                                  <Tag color={AgentTaskStatusColor[selectedTask.status]}>
                                    {AgentTaskStatusLabel[selectedTask.status]}
                                  </Tag>
                                </Space>
                              )}
                            />
                          </Col>
                          <Col span={6}>
                            <Statistic
                              title="置信度"
                              value={
                                selectedTask.confidence != null
                                  ? `${(selectedTask.confidence * 100).toFixed(0)}%`
                                  : '-'
                              }
                            />
                          </Col>
                          <Col span={6}>
                            <Statistic
                              title="引用文件"
                              value={selectedTask.referencedFiles?.length ?? 0}
                              prefix={<FileSearchOutlined />}
                            />
                          </Col>
                          <Col span={6}>
                            <Statistic
                              title="推理步数"
                              value={selectedTask.traces?.length ?? 0}
                            />
                          </Col>
                        </Row>

                        {selectedTask.confidence != null && (
                          <div style={{ marginBottom: 16 }}>
                            <Text>置信度评估：</Text>
                            <Progress
                              percent={Math.round(selectedTask.confidence * 100)}
                              status={
                                selectedTask.confidence >= 0.7
                                  ? 'success'
                                  : selectedTask.confidence >= 0.4
                                  ? 'normal'
                                  : 'exception'
                              }
                              size="small"
                            />
                          </div>
                        )}

                        <div style={{ marginBottom: 16 }}>
                          <Title level={5}>分析请求</Title>
                          <Paragraph>{selectedTask.query}</Paragraph>
                        </div>

                        {selectedTask.conclusion && (
                          <div style={{ marginBottom: 16 }}>
                            <Title level={5}>分析结论</Title>
                            <Paragraph
                              style={{
                                whiteSpace: 'pre-wrap',
                                background: '#fafafa',
                                padding: 12,
                                borderRadius: 6,
                              }}
                            >
                              {selectedTask.conclusion}
                            </Paragraph>
                          </div>
                        )}

                        {selectedTask.evidenceChain &&
                          selectedTask.evidenceChain.length > 0 && (
                            <div style={{ marginBottom: 16 }}>
                              <Title level={5}>证据链</Title>
                              <List
                                size="small"
                                bordered
                                dataSource={selectedTask.evidenceChain}
                                renderItem={(item, idx) => (
                                  <List.Item>
                                    <Text>
                                      <Text strong>{idx + 1}.</Text> {item}
                                    </Text>
                                  </List.Item>
                                )}
                              />
                            </div>
                          )}

                        {selectedTask.referencedFiles &&
                          selectedTask.referencedFiles.length > 0 && (
                            <div style={{ marginBottom: 16 }}>
                              <Title level={5}>引用文件</Title>
                              <Space wrap>
                                {selectedTask.referencedFiles.map((f) => (
                                  <Tag key={f} color="blue" icon={<FileSearchOutlined />}>
                                    {f}
                                  </Tag>
                                ))}
                              </Space>
                            </div>
                          )}

                        {selectedTask.errorMessage && (
                          <div style={{ marginBottom: 16 }}>
                            <Title level={5}>错误信息</Title>
                            <Text type="danger">{selectedTask.errorMessage}</Text>
                          </div>
                        )}
                      </div>
                    ),
                  },
                  {
                    key: 'traces',
                    label: '推理轨迹',
                    children:
                      timelineItems.length > 0 ? (
                        <Timeline items={timelineItems} />
                      ) : (
                        <Empty description="暂无推理轨迹" />
                      ),
                  },
                ]}
              />
            ) : (
              <Empty
                description="请从左侧选择任务查看详情，或提交新的分析任务"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default AgentAnalysis;
