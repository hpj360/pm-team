/**
 * 健康检查页面
 * 各微服务健康状态卡片 + 红绿状态 + 响应时间 + 详情
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
  Modal,
  Descriptions,
  List,
  Badge,
  Tooltip,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  HeartOutlined,
  ApiOutlined,
  ClockCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { getHealthChecks, recheckService } from '@/services';
import { ServiceHealthLabel } from '@/types';
import type { HealthCheckItem, ServiceHealth } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 健康状态颜色 */
const healthColor: Record<ServiceHealth, string> = {
  healthy: 'success',
  degraded: 'warning',
  unhealthy: 'error',
  unknown: 'default',
};

const healthBg: Record<ServiceHealth, string> = {
  healthy: colors.success,
  degraded: colors.severity.medium,
  unhealthy: colors.severity.critical,
  unknown: colors.neutral[400],
};

const healthIcon: Record<ServiceHealth, React.ReactNode> = {
  healthy: <CheckCircleOutlined />,
  degraded: <ExclamationCircleOutlined />,
  unhealthy: <CloseCircleOutlined />,
  unknown: <SyncOutlined />,
};

const HealthCheckPage: React.FC = () => {
  const [list, setList] = useState<HealthCheckItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<HealthCheckItem | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [recheckingId, setRecheckingId] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    getHealthChecks()
      .then((res) => setList(res.data))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  /** 重新检查 */
  const handleRecheck = async (item: HealthCheckItem) => {
    setRecheckingId(item.id);
    try {
      const res = await recheckService(item.id);
      setList((prev) => prev.map((x) => (x.id === item.id ? res.data : x)));
      message2(res.data);
    } finally {
      setRecheckingId(null);
    }
  };

  /** 内部消息提示 */
  const message2 = (item: HealthCheckItem) => {
    Modal.success({
      title: `${item.name} 重新检查完成`,
      content: `状态：${ServiceHealthLabel[item.status]}，延迟：${item.latencyMs} ms`,
    });
  };

  /** 统计 */
  const healthyCount = list.filter((x) => x.status === 'healthy').length;
  const degradedCount = list.filter((x) => x.status === 'degraded').length;
  const unhealthyCount = list.filter((x) => x.status === 'unhealthy').length;
  const avgLatency = list.reduce((s, x) => s + x.latencyMs, 0) / (list.length || 1);
  const avgUptime = list.reduce((s, x) => s + x.uptime, 0) / (list.length || 1);

  return (
    <div>
      <Title level={4}>
        <Space>
          <HeartOutlined />
          健康检查
        </Space>
      </Title>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="服务总数"
              value={list.length}
              prefix={<ApiOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="健康"
              value={healthyCount}
              valueStyle={{ color: colors.success }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="降级 / 异常"
              value={degradedCount + unhealthyCount}
              valueStyle={{ color: colors.severity.critical }}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="平均可用率"
              value={avgUptime.toFixed(2)}
              suffix="%"
              valueStyle={{ color: colors.severity.info }}
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              全部刷新
            </Button>
          </Space>
        }
      >
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          平均响应延迟 {avgLatency.toFixed(0)} ms。点击服务卡片可查看详细依赖与最近错误信息。
        </Paragraph>
        <Row gutter={[16, 16]}>
          {list.map((item) => {
            const status = item.status;
            return (
              <Col xs={24} sm={12} lg={8} xl={6} key={item.id}>
                <Card
                  hoverable
                  onClick={() => { setDetail(item); setDetailOpen(true); }}
                  style={{ borderTop: `3px solid ${healthBg[status]}` }}
                  title={
                    <Space>
                      <span style={{ color: healthBg[status], fontSize: 18 }}>
                        {healthIcon[status]}
                      </span>
                      <Text strong>{item.name}</Text>
                    </Space>
                  }
                  extra={
                    <Tag color={healthColor[status]}>
                      <Badge status={healthColor[status] as 'success' | 'warning' | 'error' | 'default'} text={ServiceHealthLabel[status]} />
                    </Tag>
                  }
                >
                  <Descriptions column={1} size="small">
                    <Descriptions.Item label="服务标识">
                      <Text code style={{ fontSize: 11 }}>{item.service}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="版本">v{item.version}</Descriptions.Item>
                    <Descriptions.Item label="响应延迟">
                      <Text type={item.latencyMs > 200 ? 'danger' : 'success'} style={{ fontSize: 12 }}>
                        {item.latencyMs} ms
                      </Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="可用率">
                      <Text style={{ fontSize: 12 }}>{item.uptime}%</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="最近检查">
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {formatDateTime(item.lastCheckAt, 'MM-DD HH:mm:ss')}
                      </Text>
                    </Descriptions.Item>
                  </Descriptions>
                  <div style={{ marginTop: 12, textAlign: 'right' }}>
                    <Button
                      type="primary"
                      size="small"
                      ghost
                      icon={<SyncOutlined />}
                      loading={recheckingId === item.id}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRecheck(item);
                      }}
                    >
                      重新检查
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
        title={detail ? `${detail.name} - 健康详情` : '服务详情'}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={<Button type="primary" onClick={() => setDetailOpen(false)}>关闭</Button>}
        width={560}
      >
        {detail && (
          <>
            <Tag color={healthColor[detail.status]} icon={healthIcon[detail.status]} style={{ marginBottom: 12 }}>
              {ServiceHealthLabel[detail.status]}
            </Tag>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="服务标识">{detail.service}</Descriptions.Item>
              <Descriptions.Item label="名称">{detail.name}</Descriptions.Item>
              <Descriptions.Item label="版本">v{detail.version}</Descriptions.Item>
              <Descriptions.Item label="状态">{ServiceHealthLabel[detail.status]}</Descriptions.Item>
              <Descriptions.Item label="响应延迟">{detail.latencyMs} ms</Descriptions.Item>
              <Descriptions.Item label="可用率">{detail.uptime}%</Descriptions.Item>
              <Descriptions.Item label="最近检查">{formatDateTime(detail.lastCheckAt)}</Descriptions.Item>
              {detail.lastError && (
                <Descriptions.Item label="最近错误">
                  <Text type="danger" style={{ fontSize: 12 }}>
                    {detail.lastError}
                  </Text>
                </Descriptions.Item>
              )}
            </Descriptions>
            <Title level={5} style={{ marginTop: 16 }}>
              <ApiOutlined /> 依赖服务
            </Title>
            <List
              size="small"
              bordered
              dataSource={detail.dependencies}
              renderItem={(dep) => (
                <List.Item>
                  <Space>
                    <ApiOutlined style={{ color: colors.primary[500] }} />
                    <Text code>{dep}</Text>
                  </Space>
                </List.Item>
              )}
            />
          </>
        )}
      </Modal>

      {/* 占位 Tooltip */}
      <Tooltip title="健康检查">
        <span style={{ display: 'none' }} />
      </Tooltip>
    </div>
  );
};

export default HealthCheckPage;
