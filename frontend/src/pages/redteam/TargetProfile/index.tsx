/**
 * 目标画像模块
 * 左侧目标列表 + 右侧画像详情（基本信息、组织架构、技术资产、攻击面、时间线）
 * 使用 ProDescriptions + Tabs + Timeline + Design Token
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Row,
  Col,
  Card,
  Input,
  List,
  Avatar,
  Tabs,
  Typography,
  Spin,
  Empty,
  Table,
  Timeline,
  Tag as AntTag,
  Space,
  Progress,
} from 'antd';
import { ProDescriptions } from '@ant-design/pro-components';
import {
  SearchOutlined,
  UserOutlined,
  GlobalOutlined,
  ClusterOutlined,
  RadarChartOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import { getTargetProfiles, getTargetProfileDetail } from '@/services';
import { TargetTypeLabel } from '@/types';
import type { TargetProfile, OrgNode, TechAsset, AttackSurface, TargetTimelineEvent } from '@/types';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 风险等级颜色映射 */
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

/** 攻击面状态颜色 */
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

const TargetProfilePage: React.FC = () => {
  const [keyword, setKeyword] = useState('');
  const [profiles, setProfiles] = useState<TargetProfile[]>([]);
  const [selectedId, setSelectedId] = useState<string>('');
  const [detail, setDetail] = useState<TargetProfile | null>(null);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);

  /** 加载目标列表 */
  useEffect(() => {
    setLoadingList(true);
    getTargetProfiles(keyword)
      .then((res) => {
        setProfiles(res.data);
        if (res.data.length > 0 && !selectedId) {
          setSelectedId(res.data[0].id);
        }
      })
      .finally(() => setLoadingList(false));
  }, [keyword]); // eslint-disable-line react-hooks/exhaustive-deps

  /** 加载画像详情 */
  useEffect(() => {
    if (!selectedId) return;
    setLoadingDetail(true);
    getTargetProfileDetail(selectedId)
      .then((res) => setDetail(res.data))
      .finally(() => setLoadingDetail(false));
  }, [selectedId]);

  /** 攻击面表格列 */
  const surfaceColumns = useMemo(
    () => [
      { title: '向量', dataIndex: 'vector', key: 'vector', ellipsis: true },
      { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
      {
        title: '风险评分',
        dataIndex: 'riskScore',
        key: 'riskScore',
        width: 120,
        render: (score: number) => (
          <Space size={6}>
            <Progress percent={score * 10} size="small" strokeColor={riskColor[score >= 9 ? 'critical' : score >= 7 ? 'high' : score >= 5 ? 'medium' : 'low']} steps={10} showInfo={false} />
            <span>{score}</span>
          </Space>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 90,
        render: (status: AttackSurface['status']) => (
          <AntTag color={surfaceStatusColor[status]}>{surfaceStatusText[status]}</AntTag>
        ),
      },
    ],
    [],
  );

  /** 技术资产列 */
  const assetColumns = useMemo(
    () => [
      { title: '类型', dataIndex: 'type', key: 'type', width: 90 },
      { title: '值', dataIndex: 'value', key: 'value', ellipsis: true },
      { title: '服务', dataIndex: 'service', key: 'service', width: 120 },
      {
        title: '暴露面',
        dataIndex: 'exposure',
        key: 'exposure',
        width: 100,
        render: (v: TechAsset['exposure']) => {
          const map: Record<TechAsset['exposure'], string> = {
            internet: 'error',
            intranet: 'warning',
            isolated: 'default',
          };
          const textMap: Record<TechAsset['exposure'], string> = {
            internet: '公网',
            intranet: '内网',
            isolated: '隔离',
          };
          return <AntTag color={map[v]}>{textMap[v]}</AntTag>;
        },
      },
    ],
    [],
  );

  /** 组织架构列 */
  const orgColumns = useMemo(
    () => [
      { title: '姓名', dataIndex: 'name', key: 'name', width: 100 },
      { title: '职位', dataIndex: 'title', key: 'title', width: 120 },
      { title: '部门', dataIndex: 'department', key: 'department', width: 120 },
      { title: '层级', dataIndex: 'level', key: 'level', width: 80 },
    ],
    [],
  );

  /** 时间线渲染 */
  const renderTimeline = (events: TargetTimelineEvent[]) => {
    const colorByCategory: Record<TargetTimelineEvent['category'], string> = {
      recon: 'blue',
      intrusion: 'red',
      action: 'orange',
      discovery: 'green',
    };
    return (
      <Timeline
        items={events.map((e) => ({
          color: colorByCategory[e.category],
          label: e.time.slice(0, 16).replace('T', ' '),
          children: (
            <div>
              <Text strong>{e.title}</Text>
              <Paragraph style={{ marginTop: 4, marginBottom: 0, color: colors.neutral[600] }}>
                {e.description}
              </Paragraph>
            </div>
          ),
        }))}
      />
    );
  };

  return (
    <div>
      <Title level={4}>目标画像</Title>
      <Row gutter={[16, 16]}>
        {/* 左侧目标列表 */}
        <Col xs={24} lg={8} xl={6}>
          <Card
            title="目标列表"
            bodyStyle={{ padding: 0 }}
            extra={<Text type="secondary">{profiles.length} 个目标</Text>}
          >
            <div style={{ padding: 12 }}>
              <Input
                placeholder="搜索目标名称 / 行业 / 标签"
                prefix={<SearchOutlined />}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                allowClear
              />
            </div>
            <Spin spinning={loadingList}>
              {profiles.length === 0 && !loadingList ? (
                <Empty description="未找到匹配目标" style={{ padding: 24 }} />
              ) : (
                <List
                  dataSource={profiles}
                  renderItem={(item) => (
                    <List.Item
                      onClick={() => setSelectedId(item.id)}
                      style={{
                        padding: '12px 16px',
                        cursor: 'pointer',
                        borderLeft:
                          selectedId === item.id
                            ? `3px solid ${colors.primary[500]}`
                            : '3px solid transparent',
                        background:
                          selectedId === item.id ? colors.primary[50] : undefined,
                      }}
                    >
                      <List.Item.Meta
                        avatar={
                          <Avatar
                            icon={<UserOutlined />}
                            style={{ background: riskColor[item.riskLevel] }}
                          />
                        }
                        title={<Text strong>{item.name}</Text>}
                        description={
                          <Space size={4} wrap>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {TargetTypeLabel[item.type]}
                            </Text>
                            <Text type="secondary" style={{ fontSize: 12 }}>·</Text>
                            <Text type="secondary" style={{ fontSize: 12 }}>{item.industry}</Text>
                            <AntTag color={riskColor[item.riskLevel]} style={{ marginLeft: 4 }}>
                              {riskText[item.riskLevel]}
                            </AntTag>
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              )}
            </Spin>
          </Card>
        </Col>

        {/* 右侧画像详情 */}
        <Col xs={24} lg={16} xl={18}>
          <Card>
            <Spin spinning={loadingDetail}>
              {!detail ? (
                <Empty description="请选择左侧目标" />
              ) : (
                <>
                  {/* 基本信息 */}
                  <ProDescriptions
                    title={
                      <Space>
                        <GlobalOutlined />
                        <span>基本信息</span>
                      </Space>
                    }
                    column={{ xs: 1, sm: 2, md: 3 }}
                    style={{ marginBottom: 16 }}
                  >
                    <ProDescriptions.Item label="目标名称">{detail.name}</ProDescriptions.Item>
                    <ProDescriptions.Item label="类型">
                      <AntTag>{TargetTypeLabel[detail.type]}</AntTag>
                    </ProDescriptions.Item>
                    <ProDescriptions.Item label="所属行业">{detail.industry}</ProDescriptions.Item>
                    <ProDescriptions.Item label="地区">{detail.region}</ProDescriptions.Item>
                    <ProDescriptions.Item label="风险等级">
                      <AntTag color={riskColor[detail.riskLevel]}>{riskText[detail.riskLevel]}</AntTag>
                    </ProDescriptions.Item>
                    <ProDescriptions.Item label="更新时间">
                      {detail.updateTime.slice(0, 16).replace('T', ' ')}
                    </ProDescriptions.Item>
                    <ProDescriptions.Item label="标签" span={3}>
                      <Space wrap>
                        {detail.tags.map((t) => (
                          <AntTag key={t} color={colors.primary[500]}>{t}</AntTag>
                        ))}
                      </Space>
                    </ProDescriptions.Item>
                    <ProDescriptions.Item label="描述" span={3}>
                      {detail.description}
                    </ProDescriptions.Item>
                  </ProDescriptions>

                  <Tabs
                    defaultActiveKey="org"
                    items={[
                      {
                        key: 'org',
                        label: (
                          <span>
                            <ClusterOutlined /> 组织架构
                          </span>
                        ),
                        children: (
                          <Table<OrgNode>
                            columns={orgColumns}
                            dataSource={detail.organization}
                            rowKey="id"
                            size="small"
                            pagination={false}
                          />
                        ),
                      },
                      {
                        key: 'assets',
                        label: (
                          <span>
                            <GlobalOutlined /> 技术资产
                          </span>
                        ),
                        children: (
                          <Table<TechAsset>
                            columns={assetColumns}
                            dataSource={detail.techAssets}
                            rowKey="id"
                            size="small"
                            pagination={false}
                          />
                        ),
                      },
                      {
                        key: 'surface',
                        label: (
                          <span>
                            <RadarChartOutlined /> 攻击面
                          </span>
                        ),
                        children: (
                          <Table<AttackSurface>
                            columns={surfaceColumns}
                            dataSource={detail.attackSurfaces}
                            rowKey="id"
                            size="small"
                            pagination={false}
                          />
                        ),
                      },
                      {
                        key: 'timeline',
                        label: (
                          <span>
                            <HistoryOutlined /> 历史事件
                          </span>
                        ),
                        children: (
                          <div style={{ paddingTop: 12, paddingBottom: 24 }}>
                            {detail.timeline.length > 0 ? (
                              renderTimeline(detail.timeline)
                            ) : (
                              <Empty description="暂无历史事件" />
                            )}
                          </div>
                        ),
                      },
                    ]}
                  />
                </>
              )}
            </Spin>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default TargetProfilePage;
