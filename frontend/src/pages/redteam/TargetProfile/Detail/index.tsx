/**
 * 目标详情页
 * - 顶部：目标基本信息 + 风险等级
 * - 左侧：组织架构（树）
 * - 中部：技术资产 / 攻击面 / 历史事件时间线
 * - 右侧：关联文件 / 关联攻击链
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Table,
  Empty,
  Spin,
  Row,
  Col,
  Statistic,
  Timeline,
  Avatar,
  List,
  Tree,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import {
  ArrowLeftOutlined,
  UserOutlined,
  ClusterOutlined,
  RadarChartOutlined,
  HistoryOutlined,
  AimOutlined,
  FileTextOutlined,
  NodeIndexOutlined,
  FireOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import { getTargetProfileById } from '@/mock/targetProfile';
import { mockAttackChains } from '@/mock/attackChain';
import { mockFileList } from '@/mock/file';
import type { TargetProfile, TechAsset, AttackSurface, TargetTimelineEvent } from '@/types';
import { TargetTypeLabel } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 风险等级颜色 */
const riskColor: Record<TargetProfile['riskLevel'], string> = {
  low: colors.success,
  medium: colors.warning,
  high: colors.severity.high,
  critical: colors.severity.critical,
};

const riskText: Record<TargetProfile['riskLevel'], string> = {
  low: '低危',
  medium: '中危',
  high: '高危',
  critical: '严重',
};

/** 攻击面状态 */
const surfaceStatusColor: Record<AttackSurface['status'], string> = {
  open: 'default',
  validated: 'processing',
  exploited: 'error',
  remediated: 'success',
};

const surfaceStatusText: Record<AttackSurface['status'], string> = {
  open: '未验证',
  validated: '已验证',
  exploited: '已利用',
  remediated: '已修复',
};

const TargetDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [target, setTarget] = useState<TargetProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = getTargetProfileById(id) ?? null;
      setTarget(data);
      setLoading(false);
    }, 200);
  }, [id]);

  /** 组织架构树 */
  const orgTreeData: DataNode[] = target
    ? buildOrgTree(target.organization)
    : [];

  /** 构建组织树 */
  function buildOrgTree(nodes: TargetProfile['organization']): DataNode[] {
    const rootNodes = nodes.filter((n) => !n.parentId);
    const buildChildren = (parentId: string): DataNode[] =>
      nodes
        .filter((n) => n.parentId === parentId)
        .map((n) => ({
          key: n.id,
          title: (
            <Space>
              <Avatar size="small" icon={<UserOutlined />} />
              <Text strong>{n.name}</Text>
              <Tag color="blue">{n.title}</Tag>
              <Text type="secondary">{n.department}</Text>
            </Space>
          ),
          children: buildChildren(n.id),
        }));
    return rootNodes.map((n) => ({
      key: n.id,
      title: (
        <Space>
          <Avatar size="small" icon={<UserOutlined />} />
          <Text strong>{n.name}</Text>
          <Tag color="gold">{n.title}</Tag>
          <Text type="secondary">{n.department}</Text>
        </Space>
      ),
      children: buildChildren(n.id),
    }));
  }

  /** 资产列 */
  const assetColumns: ColumnsType<TechAsset> = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: (v: TechAsset['type']) => {
        const color = v === 'ip' ? 'red' : v === 'domain' ? 'blue' : v === 'webapp' ? 'purple' : v === 'database' ? 'orange' : 'green';
        return <Tag color={color}>{v}</Tag>;
      },
    },
    { title: '值', dataIndex: 'value', ellipsis: true, render: (v: string) => <code>{v}</code> },
    { title: 'OS', dataIndex: 'os', width: 120, render: (v?: string) => v ?? '-' },
    { title: '端口', dataIndex: 'port', width: 80, render: (v?: number) => v ?? '-' },
    { title: '服务', dataIndex: 'service', width: 140, render: (v?: string) => v ?? '-' },
    {
      title: '暴露面',
      dataIndex: 'exposure',
      width: 100,
      render: (v: TechAsset['exposure']) => (
        <Tag color={v === 'internet' ? 'red' : v === 'intranet' ? 'orange' : 'default'}>{v}</Tag>
      ),
    },
    {
      title: '最后发现',
      dataIndex: 'lastSeen',
      width: 170,
      render: (v: string) => formatDateTime(v),
    },
  ];

  /** 攻击面列 */
  const surfaceColumns: ColumnsType<AttackSurface> = [
    {
      title: '类别',
      dataIndex: 'category',
      width: 100,
      render: (v: AttackSurface['category']) => <Tag color="blue">{v}</Tag>,
    },
    { title: '攻击向量', dataIndex: 'vector', width: 200, render: (v: string) => <Text strong>{v}</Text> },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '风险评分',
      dataIndex: 'riskScore',
      width: 100,
      sorter: (a, b) => a.riskScore - b.riskScore,
      render: (v: number) => (
        <Tag color={v >= 9 ? 'red' : v >= 7 ? 'volcano' : v >= 5 ? 'orange' : 'default'}>{v.toFixed(1)}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: AttackSurface['status']) => <Tag color={surfaceStatusColor[v]}>{surfaceStatusText[v]}</Tag>,
    },
  ];

  /** 时间线类别颜色 */
  const timelineColor: Record<TargetTimelineEvent['category'], string> = {
    recon: 'blue',
    intrusion: 'red',
    action: 'orange',
    discovery: 'green',
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载目标详情..." /></div>;
  }

  if (!target) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到目标">
          <Button type="primary" onClick={() => navigate('/redteam/target-profile')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/redteam/target-profile')}>返回</Button>
          <AimOutlined style={{ fontSize: 24, color: colors.primary[500] }} />
          <Title level={4} style={{ margin: 0 }}>{target.name}</Title>
          <Tag color="blue">{TargetTypeLabel[target.type]}</Tag>
          <Tag color={riskColor[target.riskLevel]}>{riskText[target.riskLevel]}</Tag>
        </Space>
        <Space>
          <Button icon={<NodeIndexOutlined />} onClick={() => navigate('/redteam/attack-chain')}>查看攻击链</Button>
          <Button type="primary" icon={<FileTextOutlined />} onClick={() => message.success('生成报告...')}>生成报告</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="组织成员" value={target.organization.length} prefix={<UserOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="技术资产" value={target.techAssets.length} prefix={<ClusterOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="攻击面" value={target.attackSurfaces.length} prefix={<RadarChartOutlined />} valueStyle={{ color: colors.warning }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="历史事件" value={target.timeline.length} prefix={<HistoryOutlined />} valueStyle={{ color: colors.error }} /></Card>
        </Col>
      </Row>

      {/* 基本信息 */}
      <Card size="small" title={<Space><AimOutlined /> 基本信息</Space>} style={{ marginBottom: spacing[4] }}>
        <ProDescriptions
          column={3}
          bordered
          size="small"
          dataSource={{
            id: target.id,
            name: target.name,
            type: TargetTypeLabel[target.type],
            industry: target.industry,
            region: target.region,
            riskLevel: riskText[target.riskLevel],
            description: target.description,
            tags: target.tags.join(' / '),
            createTime: formatDateTime(target.createTime),
            updateTime: formatDateTime(target.updateTime),
          }}
          columns={[
            { title: '目标 ID', dataIndex: 'id', key: 'id' },
            { title: '名称', dataIndex: 'name', key: 'name' },
            { title: '类型', dataIndex: 'type', key: 'type' },
            { title: '行业', dataIndex: 'industry', key: 'industry' },
            { title: '区域', dataIndex: 'region', key: 'region' },
            { title: '风险等级', dataIndex: 'riskLevel', key: 'riskLevel' },
            { title: '描述', dataIndex: 'description', key: 'description', span: 3 },
            { title: '标签', dataIndex: 'tags', key: 'tags', span: 2 },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
            { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
          ]}
        />
      </Card>

      <Row gutter={16}>
        {/* 左侧：组织架构 */}
        <Col xs={24} lg={8}>
          <Card size="small" title={<Space><UserOutlined /> 组织架构</Space>} style={{ marginBottom: spacing[4] }}>
            <Tree treeData={orgTreeData} defaultExpandAll showLine={{ showLeafIcon: false }} />
          </Card>

          {/* 关联文件 */}
          <Card size="small" title={<Space><FileTextOutlined /> 关联文件</Space>} style={{ marginBottom: spacing[4] }}>
            <List
              size="small"
              dataSource={mockFileList.filter((f) => f.targetId === target.id).slice(0, 5)}
              renderItem={(item) => (
                <List.Item>
                  <Space>
                    <FileTextOutlined />
                    <a onClick={() => navigate(`/files/${item.id}`)}>{item.originalName}</a>
                  </Space>
                  <Tag>{item.uploaderName}</Tag>
                </List.Item>
              )}
              locale={{ emptyText: <Empty description="无关联文件" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
            />
          </Card>

          {/* 关联攻击链 */}
          <Card size="small" title={<Space><NodeIndexOutlined /> 关联攻击链</Space>}>
            <List
              size="small"
              dataSource={mockAttackChains.filter((c) => c.target === target.name || c.stages.some((s) => s.targetId === target.id))}
              renderItem={(item) => (
                <List.Item>
                  <Space>
                    <FireOutlined style={{ color: colors.error }} />
                    <a onClick={() => navigate(`/redteam/attack-chain/${item.id}`)}>{item.name}</a>
                  </Space>
                  <Tag color={item.status === 'success' ? 'success' : item.status === 'active' ? 'processing' : 'default'}>{item.status}</Tag>
                </List.Item>
              )}
              locale={{ emptyText: <Empty description="无关联攻击链" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
            />
          </Card>
        </Col>

        {/* 中部：技术资产 + 攻击面 */}
        <Col xs={24} lg={10}>
          <Card size="small" title={<Space><ClusterOutlined /> 技术资产 ({target.techAssets.length})</Space>} style={{ marginBottom: spacing[4] }}>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              columns={assetColumns}
              dataSource={target.techAssets}
              scroll={{ x: 800 }}
            />
          </Card>

          <Card size="small" title={<Space><RadarChartOutlined /> 攻击面 ({target.attackSurfaces.length})</Space>}>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              columns={surfaceColumns}
              dataSource={target.attackSurfaces}
              scroll={{ x: 800 }}
            />
          </Card>
        </Col>

        {/* 右侧：历史事件 */}
        <Col xs={24} lg={6}>
          <Card size="small" title={<Space><HistoryOutlined /> 历史事件时间线</Space>}>
            <Timeline
              items={target.timeline.map((event) => ({
                color: timelineColor[event.category],
                children: (
                  <div>
                    <Text strong>{event.title}</Text>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{formatDateTime(event.time)}</div>
                    <Paragraph style={{ margin: '4px 0 0 0', fontSize: 13 }}>{event.description}</Paragraph>
                    <Tag color={timelineColor[event.category]} style={{ marginTop: 4 }}>{event.category}</Tag>
                  </div>
                ),
              }))}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default TargetDetailPage;
