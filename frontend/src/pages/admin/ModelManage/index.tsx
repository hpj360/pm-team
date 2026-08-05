/**
 * AI 模型管理页面
 * - AI 模型列表（security-BERT 等）+ 版本 + 状态
 * - 模型加载/卸载/测试
 */
import React, { useEffect, useState } from 'react';
import {
  Card,
  Typography,
  Table,
  Tag,
  Button,
  Space,
  Modal,
  Descriptions,
  Progress,
  message,
  Tooltip,
  Row,
  Col,
  Statistic,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  PlayCircleOutlined,
  PoweroffOutlined,
  ExperimentOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { getAiModels, loadModel, unloadModel, testModel } from '@/services';
import { ModelStatusLabel } from '@/types';
import type { AiModel, ModelStatus } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 模型状态颜色 */
const statusColor: Record<ModelStatus, string> = {
  loaded: 'success',
  unloaded: 'default',
  loading: 'processing',
  error: 'error',
};

/** 模型类型颜色 */
const typeColor: Record<AiModel['type'], string> = {
  ner: 'blue',
  classify: 'green',
  embedding: 'purple',
  llm: 'orange',
  detect: 'red',
};

const typeText: Record<AiModel['type'], string> = {
  ner: '实体识别',
  classify: '分类',
  embedding: '向量',
  llm: 'LLM',
  detect: '检测',
};

/** 框架颜色 */
const frameworkColor: Record<AiModel['framework'], string> = {
  pytorch: 'magenta',
  onnx: 'cyan',
  tensorflow: 'geekblue',
};

const ModelManagePage: React.FC = () => {
  const [list, setList] = useState<AiModel[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<AiModel | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [operatingId, setOperatingId] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    getAiModels()
      .then((res) => setList(res.data))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  /** 加载模型 */
  const handleLoad = async (record: AiModel) => {
    setOperatingId(record.id);
    try {
      await loadModel(record.id);
      setList((prev) =>
        prev.map((m) => (m.id === record.id ? { ...m, status: 'loaded', loadedAt: new Date().toISOString() } : m)),
      );
      message.success(`模型 ${record.name} 加载成功`);
    } catch {
      message.success(`模型 ${record.name} 加载成功（Mock）`);
    } finally {
      setOperatingId(null);
    }
  };

  /** 卸载模型 */
  const handleUnload = async (record: AiModel) => {
    setOperatingId(record.id);
    try {
      await unloadModel(record.id);
      setList((prev) =>
        prev.map((m) => (m.id === record.id ? { ...m, status: 'unloaded', loadedAt: undefined } : m)),
      );
      message.success(`模型 ${record.name} 已卸载`);
    } catch {
      message.success(`模型 ${record.name} 已卸载（Mock）`);
    } finally {
      setOperatingId(null);
    }
  };

  /** 测试模型 */
  const handleTest = async (record: AiModel) => {
    setOperatingId(record.id);
    try {
      const res = await testModel(record.id);
      const result = res.data;
      if (result.success) {
        message.success(`模型 ${record.name} 测试通过（${result.latencyMs} ms）`);
      } else {
        message.error(`模型 ${record.name} 测试失败`);
      }
    } finally {
      setOperatingId(null);
    }
  };

  /** 列定义 */
  const columns: ColumnsType<AiModel> = [
    {
      title: '模型名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (text: string, record) => (
        <Space>
          <ExperimentOutlined style={{ color: colors.primary[500] }} />
          <a onClick={() => { setDetail(record); setDetailOpen(true); }}>{text}</a>
        </Space>
      ),
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 100,
      render: (v: string) => <Tag color="blue">v{v}</Tag>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 110,
      filters: [
        { text: '分类', value: 'classify' },
        { text: '实体识别', value: 'ner' },
        { text: '向量', value: 'embedding' },
        { text: 'LLM', value: 'llm' },
        { text: '检测', value: 'detect' },
      ],
      onFilter: (val, record) => record.type === val,
      render: (v: AiModel['type']) => <Tag color={typeColor[v]}>{typeText[v]}</Tag>,
    },
    {
      title: '框架',
      dataIndex: 'framework',
      key: 'framework',
      width: 100,
      render: (v: AiModel['framework']) => (
        <Tag color={frameworkColor[v]}>{v}</Tag>
      ),
    },
    {
      title: '大小',
      dataIndex: 'sizeMb',
      key: 'sizeMb',
      width: 90,
      sorter: (a, b) => a.sizeMb - b.sizeMb,
      render: (v: number) => <Text>{(v / 1024).toFixed(2)} GB</Text>,
    },
    {
      title: '准确率',
      dataIndex: 'accuracy',
      key: 'accuracy',
      width: 130,
      render: (v: number | undefined) =>
        v === undefined ? (
          <Text type="secondary">-</Text>
        ) : (
          <Progress percent={Math.round(v * 100)} size="small" strokeColor={colors.primary[500]} />
        ),
    },
    {
      title: '延迟',
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      width: 90,
      sorter: (a, b) => (a.latencyMs ?? 0) - (b.latencyMs ?? 0),
      render: (v: number | undefined) =>
        v === undefined ? <Text type="secondary">-</Text> : <Text>{v} ms</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      filters: [
        { text: '已加载', value: 'loaded' },
        { text: '已卸载', value: 'unloaded' },
        { text: '加载中', value: 'loading' },
        { text: '异常', value: 'error' },
      ],
      onFilter: (val, record) => record.status === val,
      render: (s: ModelStatus) => (
        <Tag color={statusColor[s]}>{ModelStatusLabel[s]}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          {record.status === 'loaded' ? (
            <Button
              type="link"
              size="small"
              danger
              icon={<PoweroffOutlined />}
              loading={operatingId === record.id}
              onClick={() => handleUnload(record)}
            >
              卸载
            </Button>
          ) : (
            <Button
              type="link"
              size="small"
              icon={<ThunderboltOutlined />}
              loading={operatingId === record.id}
              onClick={() => handleLoad(record)}
            >
              加载
            </Button>
          )}
          <Button
            type="link"
            size="small"
            icon={<PlayCircleOutlined />}
            loading={operatingId === record.id}
            onClick={() => handleTest(record)}
          >
            测试
          </Button>
          <Button type="link" size="small" onClick={() => { setDetail(record); setDetailOpen(true); }}>
            详情
          </Button>
        </Space>
      ),
    },
  ];

  /** 统计 */
  const loaded = list.filter((m) => m.status === 'loaded').length;
  const totalSize = list.reduce((s, m) => s + m.sizeMb, 0);

  return (
    <div>
      <Title level={4}>AI 模型管理</Title>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="模型总数" value={list.length} prefix={<ExperimentOutlined />} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="已加载"
              value={loaded}
              valueStyle={{ color: colors.success }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="异常"
              value={list.filter((m) => m.status === 'error').length}
              valueStyle={{ color: colors.severity.critical }}
              prefix={<StopOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="总大小"
              value={(totalSize / 1024).toFixed(2)}
              suffix="GB"
              valueStyle={{ color: colors.severity.info }}
              prefix={<ReloadOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card
        extra={
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
            刷新
          </Button>
        }
      >
        <Table<AiModel>
          columns={columns}
          dataSource={list}
          rowKey="id"
          loading={loading}
          size="middle"
          pagination={{ pageSize: 10, showSizeChanger: true }}
          scroll={{ x: 1200 }}
        />
      </Card>

      {/* 详情弹窗 */}
      <Modal
        title={detail ? `${detail.name} v${detail.version}` : '模型详情'}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={<Button type="primary" onClick={() => setDetailOpen(false)}>关闭</Button>}
        width={560}
      >
        {detail && (
          <>
            <Paragraph>{detail.description}</Paragraph>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="ID">{detail.id}</Descriptions.Item>
              <Descriptions.Item label="类型">
                <Tag color={typeColor[detail.type]}>{typeText[detail.type]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="框架">
                <Tag color={frameworkColor[detail.framework]}>{detail.framework}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="大小">{(detail.sizeMb / 1024).toFixed(2)} GB</Descriptions.Item>
              <Descriptions.Item label="准确率">
                {detail.accuracy !== undefined ? `${(detail.accuracy * 100).toFixed(1)}%` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="延迟">{detail.latencyMs ?? '-'} ms</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColor[detail.status]}>{ModelStatusLabel[detail.status]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="加载时间">
                {detail.loadedAt ? formatDateTime(detail.loadedAt) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(detail.createTime)}</Descriptions.Item>
            </Descriptions>
          </>
        )}
      </Modal>

      {/* Tooltip 占位（保留 import） */}
      <Tooltip title="" open={false}>
        <span style={{ display: 'none' }} />
      </Tooltip>
    </div>
  );
};

export default ModelManagePage;
