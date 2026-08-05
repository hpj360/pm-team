/**
 * 攻击链路模块
 * - 攻击链路可视化（Kill Chain 阶段流程图 + 桑基图）
 * - 攻击阶段详情（Step 条 + 描述）
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Typography,
  Spin,
  Empty,
  Tag,
  Steps,
  Descriptions,
  List,
  Space,
  Select,
  Badge,
} from 'antd';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import {
  CheckCircleFilled,
  SyncOutlined,
  ClockCircleOutlined,
  CloseCircleFilled,
} from '@ant-design/icons';
import { getAttackChains, getAttackChainDetail } from '@/services';
import type { AttackChain, AttackStage } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Paragraph, Text } = Typography;

const { Option } = Select;

/** 阶段状态颜色与图标 */
const stageStatusMap: Record<AttackStage['status'], { color: string; icon: React.ReactNode; text: string }> = {
  planned: { color: 'default', icon: <ClockCircleOutlined />, text: '计划中' },
  'in-progress': { color: 'processing', icon: <SyncOutlined spin />, text: '进行中' },
  completed: { color: 'success', icon: <CheckCircleFilled />, text: '已完成' },
  failed: { color: 'error', icon: <CloseCircleFilled />, text: '失败' },
};

/** 攻击链总状态颜色 */
const chainStatusColor: Record<AttackChain['status'], string> = {
  planning: 'default',
  active: 'processing',
  success: 'success',
  failed: 'error',
};

const chainStatusText: Record<AttackChain['status'], string> = {
  planning: '规划中',
  active: '进行中',
  success: '成功',
  failed: '失败',
};

const AttackChainPage: React.FC = () => {
  const [chains, setChains] = useState<AttackChain[]>([]);
  const [selectedId, setSelectedId] = useState<string>('');
  const [detail, setDetail] = useState<AttackChain | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    getAttackChains()
      .then((res) => {
        setChains(res.data);
        if (res.data.length > 0) setSelectedId(res.data[0].id);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!selectedId) return;
    getAttackChainDetail(selectedId).then((res) => setDetail(res.data));
  }, [selectedId]);

  /** ECharts 桑基图配置 */
  const sankeyOption = useMemo<EChartsOption>(() => {
    if (!detail) return {};
    const nodes = Array.from(
      new Set(detail.flow.flatMap((f) => [f.from, f.to])),
    ).map((name) => ({ name }));
    return {
      tooltip: { trigger: 'item', triggerOn: 'mousemove' },
      series: [
        {
          type: 'sankey',
          data: nodes,
          links: detail.flow.map((f) => ({ source: f.from, target: f.to, value: f.value })),
          emphasis: { focus: 'adjacency' },
          lineStyle: { color: 'gradient', curveness: 0.5 },
          itemStyle: { color: colors.primary[500], borderColor: colors.primary[700] },
          label: { color: colors.neutral[800], fontSize: 12 },
        },
      ],
    };
  }, [detail]);

  /** 当前选中的 stage（Steps current） */
  const currentStep = useMemo(() => {
    if (!detail) return 0;
    const idx = detail.stages.findIndex((s) => s.status === 'in-progress');
    if (idx >= 0) return idx;
    const completed = detail.stages.filter((s) => s.status === 'completed').length;
    return completed;
  }, [detail]);

  return (
    <div>
      <Title level={4}>攻击链路</Title>

      <Card style={{ marginBottom: 16 }}>
        <Space wrap>
          <Text strong>选择攻击链：</Text>
          <Select
            style={{ minWidth: 320 }}
            value={selectedId || undefined}
            onChange={setSelectedId}
            placeholder="请选择攻击链"
          >
            {chains.map((c) => (
              <Option key={c.id} value={c.id}>
                {c.name} · {c.target}
              </Option>
            ))}
          </Select>
        </Space>
      </Card>

      <Spin spinning={loading}>
        {!detail ? (
          <Empty description="暂无数据" />
        ) : (
          <>
            {/* 攻击链概览 */}
            <Card style={{ marginBottom: 16 }}>
              <Descriptions title="攻击链概览" column={{ xs: 1, sm: 2, md: 3 }}>
                <Descriptions.Item label="名称">{detail.name}</Descriptions.Item>
                <Descriptions.Item label="目标">{detail.target}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Badge status={chainStatusColor[detail.status] as 'success' | 'processing' | 'error' | 'default'} text={chainStatusText[detail.status]} />
                </Descriptions.Item>
                <Descriptions.Item label="目标说明" span={2}>{detail.objective}</Descriptions.Item>
                <Descriptions.Item label="开始时间">{formatDateTime(detail.startTime)}</Descriptions.Item>
                <Descriptions.Item label="结束时间">
                  {detail.endTime ? formatDateTime(detail.endTime) : <Text type="secondary">进行中</Text>}
                </Descriptions.Item>
              </Descriptions>
            </Card>

            <Row gutter={[16, 16]}>
              {/* Kill Chain 步骤 */}
              <Col xs={24} lg={12}>
                <Card title="Kill Chain 攻击阶段">
                  <Steps
                    current={currentStep}
                    direction="vertical"
                    size="small"
                    items={detail.stages.map((s) => ({
                      title: (
                        <Space>
                          <Text strong>阶段 {s.phase}: {s.name}</Text>
                          <Tag color={stageStatusMap[s.status].color}>
                            {stageStatusMap[s.status].icon}
                            {stageStatusMap[s.status].text}
                          </Tag>
                        </Space>
                      ),
                      description: (
                        <div style={{ paddingBottom: 8 }}>
                          <Paragraph style={{ marginBottom: 4, color: colors.neutral[600] }}>
                            {s.description}
                          </Paragraph>
                          <Space size={16} style={{ fontSize: 12 }}>
                            <Text type="secondary">战术：{s.tactic}</Text>
                            <Text type="secondary">技术：{s.technique}</Text>
                          </Space>
                          <br />
                          <Space size={16} style={{ fontSize: 12 }}>
                            {s.operator && (
                              <Text type="secondary">执行人：{s.operator}</Text>
                            )}
                            {s.startTime && (
                              <Text type="secondary">开始：{formatDateTime(s.startTime)}</Text>
                            )}
                            {s.endTime && (
                              <Text type="secondary">结束：{formatDateTime(s.endTime)}</Text>
                            )}
                          </Space>
                        </div>
                      ),
                      status:
                        s.status === 'completed'
                          ? 'finish'
                          : s.status === 'in-progress'
                            ? 'process'
                            : s.status === 'failed'
                              ? 'error'
                              : 'wait',
                    }))}
                  />
                </Card>
              </Col>

              {/* 桑基图 + 阶段列表 */}
              <Col xs={24} lg={12}>
                <Card title="攻击流向（桑基图）" style={{ marginBottom: 16 }}>
                  <ReactECharts option={sankeyOption} style={{ height: 280 }} />
                </Card>

                <Card title="阶段详情">
                  <List
                    dataSource={detail.stages}
                    renderItem={(s) => (
                      <List.Item>
                        <List.Item.Meta
                          avatar={<Tag color={stageStatusMap[s.status].color}>P{s.phase}</Tag>}
                          title={
                            <Space>
                              <Text strong>{s.name}</Text>
                              <Tag>{s.tactic}</Tag>
                            </Space>
                          }
                          description={
                            <Space direction="vertical" size={0}>
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                技术：{s.technique}
                              </Text>
                              <Text style={{ fontSize: 12 }}>{s.description}</Text>
                            </Space>
                          }
                        />
                      </List.Item>
                    )}
                  />
                </Card>
              </Col>
            </Row>
          </>
        )}
      </Spin>
    </div>
  );
};

export default AttackChainPage;
