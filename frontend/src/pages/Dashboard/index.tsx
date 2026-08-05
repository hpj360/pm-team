/**
 * 工作台页面
 * - 4 个统计卡片（文件总数/总大小/解析完成数/在线任务数）
 * - 文件上传趋势折线图（最近 7 天）
 * - 文件类型分布饼图
 * - 红方任务进度（最近 5 个，进度条）
 * - 系统状态（CPU/内存/磁盘）
 * - 最近上传文件列表（5 条）
 */
import React, { useMemo } from 'react';
import { Card, Row, Col, Statistic, Typography, Progress, Table, Tag, Space, Spin } from 'antd';
import {
  FileTextOutlined,
  DatabaseOutlined,
  CheckCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { useQuery } from '@tanstack/react-query';
import { getDashboardData } from '@/services';
import { formatFileSize, formatDateTime } from '@/utils';
import { FileType, FileStatus } from '@/types';
import type { RecentFile, RedTeamTaskProgress } from '@/types';

const { Title } = Typography;

const fileTypeColor: Record<FileType, string> = {
  [FileType.DOCUMENT]: '#1890ff',
  [FileType.IMAGE]: '#52c41a',
  [FileType.VIDEO]: '#722ed1',
  [FileType.AUDIO]: '#13c2c2',
  [FileType.ARCHIVE]: '#fa8c16',
  [FileType.CODE]: '#eb2f96',
  [FileType.OTHER]: '#8c8c8c',
};

const fileTypeText: Record<FileType, string> = {
  [FileType.DOCUMENT]: '文档',
  [FileType.IMAGE]: '图片',
  [FileType.VIDEO]: '视频',
  [FileType.AUDIO]: '音频',
  [FileType.ARCHIVE]: '压缩包',
  [FileType.CODE]: '代码',
  [FileType.OTHER]: '其他',
};

const taskStatusColor: Record<RedTeamTaskProgress['status'], string> = {
  pending: 'default',
  running: 'processing',
  completed: 'success',
  failed: 'error',
};

const taskStatusText: Record<RedTeamTaskProgress['status'], string> = {
  pending: '待开始',
  running: '进行中',
  completed: '已完成',
  failed: '失败',
};

const fileStatusColor: Record<FileStatus, string> = {
  [FileStatus.PENDING]: 'default',
  [FileStatus.PROCESSING]: 'processing',
  [FileStatus.COMPLETED]: 'success',
  [FileStatus.FAILED]: 'error',
};

const fileStatusText: Record<FileStatus, string> = {
  [FileStatus.PENDING]: '待处理',
  [FileStatus.PROCESSING]: '处理中',
  [FileStatus.COMPLETED]: '已完成',
  [FileStatus.FAILED]: '失败',
};

const Dashboard: React.FC = () => {
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: async () => {
      const res = await getDashboardData();
      return res.data;
    },
  });

  /** 上传趋势折线图配置 */
  const uploadTrendOption = useMemo<EChartsOption>(() => {
    const dates = (data?.uploadTrend ?? []).map((p) => p.date.slice(5));
    const counts = (data?.uploadTrend ?? []).map((p) => p.count);
    return {
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: dates, boundaryGap: false },
      yAxis: { type: 'value', name: '文件数' },
      series: [
        {
          name: '上传文件数',
          type: 'line',
          smooth: true,
          data: counts,
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(245,34,45,0.35)' },
                { offset: 1, color: 'rgba(245,34,45,0.02)' },
              ],
            },
          },
          lineStyle: { color: '#f5222d', width: 2 },
          itemStyle: { color: '#f5222d' },
        },
      ],
    };
  }, [data]);

  /** 文件类型分布饼图配置 */
  const typeDistributionOption = useMemo<EChartsOption>(() => {
    const dist = data?.typeDistribution ?? [];
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          name: '文件类型',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: false, position: 'center' },
          emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
          labelLine: { show: false },
          data: dist.map((d) => ({
            name: d.typeName,
            value: d.count,
            itemStyle: { color: fileTypeColor[d.type] },
          })),
        },
      ],
    };
  }, [data]);

  if (isLoading || !data) {
    return (
      <div className="loading-container">
        <Spin size="large" tip="加载工作台数据..." />
      </div>
    );
  }

  /** 最近文件表格列 */
  const recentColumns = [
    {
      title: '文件名',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (text: string) => <a>{text}</a>,
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (size: number) => formatFileSize(size),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 80,
      render: (type: FileType) => (
        <Tag color={fileTypeColor[type]}>{fileTypeText[type]}</Tag>
      ),
    },
    {
      title: '上传者',
      dataIndex: 'uploader',
      key: 'uploader',
      width: 80,
    },
    {
      title: '上传时间',
      dataIndex: 'uploadTime',
      key: 'uploadTime',
      width: 160,
      render: (time: string) => formatDateTime(time),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: FileStatus) => (
        <Tag color={fileStatusColor[status]}>{fileStatusText[status]}</Tag>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>工作台</Title>

      {/* 4 个统计卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable>
            <Statistic
              title="文件总数"
              value={data.stats.totalFiles}
              prefix={<FileTextOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable>
            <Statistic
              title="总大小"
              value={formatFileSize(data.stats.totalSize)}
              prefix={<DatabaseOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable>
            <Statistic
              title="解析完成数"
              value={data.stats.parsedCount}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable>
            <Statistic
              title="在线任务数"
              value={data.stats.activeTasks}
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: '#f5222d' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 上传趋势 + 类型分布 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title="文件上传趋势（最近 7 天）">
            <ReactECharts option={uploadTrendOption} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="文件类型分布">
            <ReactECharts option={typeDistributionOption} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      {/* 任务进度 + 系统状态 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title="红方任务进度">
            <Space direction="vertical" style={{ width: '100%' }} size="middle">
              {data.taskProgress.map((task) => (
                <div key={task.id}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      marginBottom: 4,
                    }}
                  >
                    <span style={{ fontWeight: 500 }}>{task.name}</span>
                    <Tag color={taskStatusColor[task.status]}>
                      {taskStatusText[task.status]}
                    </Tag>
                  </div>
                  <Progress
                    percent={task.progress}
                    status={
                      task.status === 'failed'
                        ? 'exception'
                        : task.status === 'completed'
                          ? 'success'
                          : 'active'
                    }
                  />
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 2 }}>
                    {task.type} · 负责人：{task.owner} · 创建于{' '}
                    {formatDateTime(task.createTime)}
                  </div>
                </div>
              ))}
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="系统状态">
            <Space direction="vertical" style={{ width: '100%' }} size="large">
              <div>
                <div style={{ marginBottom: 4 }}>
                  CPU 使用率：{data.systemStatus.cpuUsage}%
                </div>
                <Progress
                  percent={data.systemStatus.cpuUsage}
                  strokeColor="#1890ff"
                />
              </div>
              <div>
                <div style={{ marginBottom: 4 }}>
                  内存使用率：{data.systemStatus.memoryUsage}%（
                  {formatFileSize(data.systemStatus.memoryUsed)} /{' '}
                  {formatFileSize(data.systemStatus.memoryTotal)}）
                </div>
                <Progress
                  percent={data.systemStatus.memoryUsage}
                  strokeColor="#52c41a"
                />
              </div>
              <div>
                <div style={{ marginBottom: 4 }}>
                  磁盘使用率：{data.systemStatus.diskUsage}%（
                  {formatFileSize(data.systemStatus.diskUsed)} /{' '}
                  {formatFileSize(data.systemStatus.diskTotal)}）
                </div>
                <Progress
                  percent={data.systemStatus.diskUsage}
                  strokeColor="#fa541c"
                />
              </div>
            </Space>
          </Card>
        </Col>
      </Row>

      {/* 最近上传文件 */}
      <Card title="最近上传文件" style={{ marginTop: 16 }}>
        <Table<RecentFile>
          columns={recentColumns}
          dataSource={data.recentFiles}
          rowKey="id"
          pagination={false}
          size="middle"
        />
      </Card>
    </div>
  );
};

export default Dashboard;
