/**
 * 数据源管理页面
 * 数据源列表（ES/Milvus/MinIO/Kafka 状态）+ 连接测试
 */
import React, { useEffect, useState } from 'react';
import {
  Card,
  Typography,
  Row,
  Col,
  Tag,
  Button,
  Space,
  Statistic,
  message,
  Modal,
  Descriptions,
} from 'antd';
import {
  DatabaseOutlined,
  ReloadOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  CloudServerOutlined,
} from '@ant-design/icons';
import { getDataSources, testDataSource } from '@/services';
import { DataSourceTypeLabel } from '@/types';
import type { DataSource, DataSourceType, DataSourceStatus } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Text } = Typography;

/** 类型图标 */
const typeIcon: Record<DataSourceType, React.ReactNode> = {
  elasticsearch: <CloudServerOutlined />,
  milvus: <ApiOutlined />,
  minio: <CloudServerOutlined />,
  kafka: <ApiOutlined />,
  mysql: <DatabaseOutlined />,
  redis: <DatabaseOutlined />,
};

/** 类型颜色 */
const typeColor: Record<DataSourceType, string> = {
  elasticsearch: '#52c41a',
  milvus: '#722ed1',
  minio: '#fa541c',
  kafka: '#13c2c2',
  mysql: '#1890ff',
  redis: '#f5222d',
};

/** 状态颜色与文本 */
const statusMap: Record<DataSourceStatus, { color: string; text: string; icon: React.ReactNode }> = {
  connected: { color: 'success', text: '已连接', icon: <CheckCircleOutlined /> },
  disconnected: { color: 'default', text: '未连接', icon: <CloseCircleOutlined /> },
  error: { color: 'error', text: '错误', icon: <ExclamationCircleOutlined /> },
  checking: { color: 'processing', text: '检测中', icon: <ReloadOutlined spin /> },
  degraded: { color: 'warning', text: '降级', icon: <ExclamationCircleOutlined /> },
};

const DataSourcePage: React.FC = () => {
  const [list, setList] = useState<DataSource[]>([]);
  const [loading, setLoading] = useState(false);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [detail, setDetail] = useState<DataSource | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const load = () => {
    setLoading(true);
    getDataSources()
      .then((res) => setList(res.data))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  /** 测试连接 */
  const handleTest = async (ds: DataSource) => {
    setTestingId(ds.id);
    setList((prev) => prev.map((x) => (x.id === ds.id ? { ...x, status: 'checking' } : x)));
    try {
      const res = await testDataSource(ds.id);
      const result = res.data;
      setList((prev) =>
        prev.map((x) =>
          x.id === ds.id ? { ...x, status: result.status, latencyMs: result.latencyMs } : x,
        ),
      );
      message.success(
        `${ds.name} ${result.status === 'connected' ? '连接成功' : '连接失败'}（${result.latencyMs} ms）`,
      );
    } catch {
      setList((prev) => prev.map((x) => (x.id === ds.id ? { ...x, status: 'error' } : x)));
      message.error(`${ds.name} 测试失败`);
    } finally {
      setTestingId(null);
    }
  };

  /** 打开详情 */
  const openDetail = (ds: DataSource) => {
    setDetail(ds);
    setDetailOpen(true);
  };

  /** 统计 */
  const connected = list.filter((x) => x.status === 'connected').length;
  const errorCount = list.filter((x) => x.status === 'error').length;
  const avgLatency = list.filter((x) => x.latencyMs).reduce((s, x) => s + (x.latencyMs ?? 0), 0) / (list.filter((x) => x.latencyMs).length || 1);

  return (
    <div>
      <Title level={4}>数据源管理</Title>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="数据源总数" value={list.length} prefix={<DatabaseOutlined />} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="已连接"
              value={connected}
              valueStyle={{ color: colors.success }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="异常"
              value={errorCount}
              valueStyle={{ color: colors.severity.critical }}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="平均延迟"
              value={avgLatency.toFixed(0)}
              suffix="ms"
              valueStyle={{ color: colors.severity.info }}
              prefix={<ApiOutlined />}
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
        <Row gutter={[16, 16]}>
          {list.map((ds) => {
            const st = statusMap[ds.status];
            return (
              <Col xs={24} sm={12} lg={8} xl={6} key={ds.id}>
                <Card
                  hoverable
                  onClick={() => openDetail(ds)}
                  title={
                    <Space>
                      <span style={{ color: typeColor[ds.type], fontSize: 18 }}>
                        {typeIcon[ds.type]}
                      </span>
                      <Text strong>{ds.name}</Text>
                    </Space>
                  }
                  extra={<Tag color={st.color} icon={st.icon}>{st.text}</Tag>}
                >
                  <Descriptions column={1} size="small" style={{ fontSize: 12 }}>
                    <Descriptions.Item label="类型">
                      {DataSourceTypeLabel[ds.type]}
                    </Descriptions.Item>
                    <Descriptions.Item label="端点">
                      <Text code style={{ fontSize: 11 }}>
                        {ds.endpoint}:{ds.port}
                      </Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="延迟">
                      {ds.latencyMs !== undefined ? (
                        <Text type={ds.latencyMs > 100 ? 'danger' : 'success'} style={{ fontSize: 12 }}>
                          {ds.latencyMs} ms
                        </Text>
                      ) : (
                        <Text type="secondary">-</Text>
                      )}
                    </Descriptions.Item>
                    <Descriptions.Item label="最近检查">
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {formatDateTime(ds.lastCheckAt, 'MM-DD HH:mm:ss')}
                      </Text>
                    </Descriptions.Item>
                  </Descriptions>
                  <div style={{ marginTop: 12, textAlign: 'right' }}>
                    <Button
                      type="primary"
                      size="small"
                      ghost
                      icon={<ApiOutlined />}
                      loading={testingId === ds.id}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleTest(ds);
                      }}
                    >
                      测试连接
                    </Button>
                  </div>
                </Card>
              </Col>
            );
          })}
        </Row>
      </Card>

      {/* 详情弹窗 */}
      <Modal
        title={detail ? detail.name : '数据源详情'}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={<Button type="primary" onClick={() => setDetailOpen(false)}>关闭</Button>}
        width={560}
      >
        {detail && (
          <>
            <Tag color={statusMap[detail.status].color} icon={statusMap[detail.status].icon} style={{ marginBottom: 12 }}>
              {statusMap[detail.status].text}
            </Tag>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="ID">{detail.id}</Descriptions.Item>
              <Descriptions.Item label="名称">{detail.name}</Descriptions.Item>
              <Descriptions.Item label="类型">{DataSourceTypeLabel[detail.type]}</Descriptions.Item>
              <Descriptions.Item label="端点">{detail.endpoint}:{detail.port}</Descriptions.Item>
              <Descriptions.Item label="数据库">{detail.database}</Descriptions.Item>
              <Descriptions.Item label="版本">{detail.version ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="延迟">{detail.latencyMs ?? '-'} ms</Descriptions.Item>
              <Descriptions.Item label="最近检查">{formatDateTime(detail.lastCheckAt)}</Descriptions.Item>
              <Descriptions.Item label="描述">{detail.description}</Descriptions.Item>
              {detail.lastError && (
                <Descriptions.Item label="错误信息">
                  <Text type="danger">{detail.lastError}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>
          </>
        )}
      </Modal>
    </div>
  );
};

export default DataSourcePage;
