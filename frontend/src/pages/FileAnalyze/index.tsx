/**
 * 文件分析页面
 */

import React, { useState } from 'react';
import {
  Card,
  Row,
  Col,
  Table,
  Button,
  Space,
  Tag,
  Typography,
  Progress,
  Select,
  Modal,
  Descriptions,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlayCircleOutlined,
  EyeOutlined,
  DownloadOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { AnalyzeTask, AnalyzeType, AnalyzeStatus } from '@/types';
import { AnalyzeStatus as AS, AnalyzeType as AT } from '@/types';
import { formatDateTime } from '@/utils';

const { Title, Text } = Typography;

// 模拟数据
const mockTasks: AnalyzeTask[] = [
  {
    id: '1',
    fileId: 'f1',
    fileName: 'malware_sample.exe',
    type: AT.MALWARE,
    status: AS.COMPLETED,
    progress: 100,
    createTime: '2024-01-15T10:30:00Z',
    updateTime: '2024-01-15T10:45:00Z',
    completeTime: '2024-01-15T10:45:00Z',
  },
  {
    id: '2',
    fileId: 'f2',
    fileName: 'network_capture.pcap',
    type: AT.NETWORK,
    status: AS.RUNNING,
    progress: 65,
    createTime: '2024-01-15T11:00:00Z',
    updateTime: '2024-01-15T11:15:00Z',
  },
  {
    id: '3',
    fileId: 'f3',
    fileName: 'document.pdf',
    type: AT.CONTENT,
    status: AS.PENDING,
    progress: 0,
    createTime: '2024-01-15T11:30:00Z',
    updateTime: '2024-01-15T11:30:00Z',
  },
];

const FileAnalyze: React.FC = () => {
  const [tasks, setTasks] = useState<AnalyzeTask[]>(mockTasks);
  const [selectedTask, setSelectedTask] = useState<AnalyzeTask | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);

  // 分析类型映射
  const typeMap: Record<AnalyzeType, { color: string; text: string }> = {
    [AT.CONTENT]: { color: 'blue', text: '内容分析' },
    [AT.MALWARE]: { color: 'red', text: '恶意软件分析' },
    [AT.NETWORK]: { color: 'green', text: '网络行为分析' },
    [AT.CRYPTO]: { color: 'purple', text: '加密分析' },
    [AT.METADATA]: { color: 'orange', text: '元数据分析' },
    [AT.IOC]: { color: 'cyan', text: 'IOC提取' },
  };

  // 状态映射
  const statusMap: Record<AnalyzeStatus, { color: string; text: string }> = {
    [AS.PENDING]: { color: 'default', text: '待处理' },
    [AS.RUNNING]: { color: 'processing', text: '分析中' },
    [AS.COMPLETED]: { color: 'success', text: '已完成' },
    [AS.FAILED]: { color: 'error', text: '失败' },
  };

  // 表格列定义
  const columns: ColumnsType<AnalyzeTask> = [
    {
      title: '文件名',
      dataIndex: 'fileName',
      key: 'fileName',
      ellipsis: true,
      width: 200,
    },
    {
      title: '分析类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: AnalyzeType) => {
        const config = typeMap[type];
        return <Tag color={config.color}>{config.text}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: AnalyzeStatus) => {
        const config = statusMap[status];
        return <Tag color={config.color}>{config.text}</Tag>;
      },
    },
    {
      title: '进度',
      dataIndex: 'progress',
      key: 'progress',
      width: 150,
      render: (progress: number, record) => (
        <Progress
          percent={progress}
          size="small"
          status={record.status === AS.FAILED ? 'exception' : undefined}
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (time: string) => formatDateTime(time),
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size="small">
          {record.status === AS.PENDING && (
            <Button type="link" size="small" icon={<PlayCircleOutlined />}>
              开始
            </Button>
          )}
          {record.status === AS.COMPLETED && (
            <>
              <Button
                type="link"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => {
                  setSelectedTask(record);
                  setDetailVisible(true);
                }}
              >
                查看
              </Button>
              <Button type="link" size="small" icon={<DownloadOutlined />}>
                报告
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>文件分析</Title>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small">
            <Text type="secondary">待处理</Text>
            <Title level={3} style={{ margin: 0 }}>
              {tasks.filter((t) => t.status === AS.PENDING).length}
            </Title>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small">
            <Text type="secondary">分析中</Text>
            <Title level={3} style={{ margin: 0, color: '#1890ff' }}>
              {tasks.filter((t) => t.status === AS.RUNNING).length}
            </Title>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small">
            <Text type="secondary">已完成</Text>
            <Title level={3} style={{ margin: 0, color: '#52c41a' }}>
              {tasks.filter((t) => t.status === AS.COMPLETED).length}
            </Title>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card size="small">
            <Text type="secondary">失败</Text>
            <Title level={3} style={{ margin: 0, color: '#f5222d' }}>
              {tasks.filter((t) => t.status === AS.FAILED).length}
            </Title>
          </Card>
        </Col>
      </Row>

      <Card
        title="分析任务列表"
        extra={
          <Space>
            <Select
              placeholder="筛选类型"
              allowClear
              style={{ width: 140 }}
            >
              {Object.entries(typeMap).map(([key, value]) => (
                <Select.Option key={key} value={key}>
                  {value.text}
                </Select.Option>
              ))}
            </Select>
            <Button icon={<ReloadOutlined />}>刷新</Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={tasks}
          rowKey="id"
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
        />
      </Card>

      {/* 分析详情弹窗 */}
      <Modal
        title="分析结果详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        width={800}
        footer={[
          <Button key="close" onClick={() => setDetailVisible(false)}>
            关闭
          </Button>,
          <Button key="export" type="primary" icon={<DownloadOutlined />}>
            导出报告
          </Button>,
        ]}
      >
        {selectedTask && (
          <Descriptions bordered column={2}>
            <Descriptions.Item label="文件名">{selectedTask.fileName}</Descriptions.Item>
            <Descriptions.Item label="分析类型">
              <Tag color={typeMap[selectedTask.type].color}>
                {typeMap[selectedTask.type].text}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={statusMap[selectedTask.status].color}>
                {statusMap[selectedTask.status].text}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="进度">{selectedTask.progress}%</Descriptions.Item>
            <Descriptions.Item label="创建时间">
              {formatDateTime(selectedTask.createTime)}
            </Descriptions.Item>
            <Descriptions.Item label="完成时间">
              {selectedTask.completeTime ? formatDateTime(selectedTask.completeTime) : '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default FileAnalyze;
