/**
 * 关系图谱页面
 * - ECharts Graph 力导向布局展示目标关系网络
 * - 节点：组织 / 人员 / 资产 / 域名 / IP / 漏洞（不同类型不同颜色）
 * - 边：隶属 / 管理 / 拥有 / 连接 / 解析 / 托管 / 利用 / 关联（不同样式）
 * - 交互：点击节点显示详情、缩放、拖拽
 * - 左侧：节点类型 + 关系类型筛选
 * - 右侧：选中节点详情（ProDescriptions）
 * - 顶部：数据源切换（Mock 数据 / Neo4j 实时）
 */
import React, { useEffect, useMemo, useState } from 'react';
import { Card, Row, Col, Typography, Spin, Empty, Tag, Space, Divider, Checkbox, Button, Radio, InputNumber, App } from 'antd';
import { ProDescriptions } from '@ant-design/pro-components';
import { LazyECharts as ReactECharts } from '@/components/common';
import type { EChartsOption } from '@/utils/echarts';
import {
  GraphNodeTypeLabel,
  GraphRelationTypeLabel,
} from '@/types';
import type {
  RelationGraphData,
  GraphNode,
  GraphEdge,
  GraphNodeType,
  GraphRelationType,
  GraphDataSource,
  GraphQueryDepth,
} from '@/types';
import { getRelationGraph, getRelationGraphFromNeo4j, getRelationGraphMockFallback } from '@/services';
import { colors } from '@/styles/tokens';
import { getAriaLabel } from '@/utils/accessibility';
import { formatDateTime } from '@/utils';
import { useSearchParams } from 'react-router-dom';

const { Title, Text, Paragraph } = Typography;

/** 节点类型颜色映射 */
const nodeTypeColor: Record<GraphNodeType, string> = {
  organization: colors.primary[500],
  person: colors.severity.info,
  asset: colors.severity.medium,
  domain: colors.severity.low,
  ip: colors.severity.high,
  vulnerability: colors.severity.critical,
};

/** 节点类型符号大小 */
const nodeTypeSize: Record<GraphNodeType, number> = {
  organization: 60,
  person: 36,
  asset: 44,
  domain: 30,
  ip: 30,
  vulnerability: 40,
};

/** 风险等级颜色 */
const riskLevelColor: Record<string, string> = {
  low: colors.severity.low,
  medium: colors.severity.medium,
  high: colors.severity.high,
  critical: colors.severity.critical,
};

/** 关系类型边样式 */
const relationLineStyle: Record<GraphRelationType, { type: 'solid' | 'dashed' | 'dotted'; color: string }> = {
  belong_to: { type: 'solid', color: colors.neutral[600] },
  manage: { type: 'solid', color: colors.severity.info },
  own: { type: 'solid', color: colors.primary[500] },
  connect: { type: 'dashed', color: colors.neutral[500] },
  resolve: { type: 'dotted', color: colors.severity.low },
  host: { type: 'dashed', color: colors.severity.medium },
  exploit: { type: 'solid', color: colors.severity.critical },
  relate: { type: 'dotted', color: colors.neutral[400] },
};

/** 所有节点类型选项 */
const NODE_TYPE_OPTIONS = (Object.keys(GraphNodeTypeLabel) as GraphNodeType[]).map((t) => ({
  label: GraphNodeTypeLabel[t],
  value: t,
}));

/** 所有关系类型选项 */
const RELATION_OPTIONS = (Object.keys(GraphRelationTypeLabel) as GraphRelationType[]).map((r) => ({
  label: GraphRelationTypeLabel[r],
  value: r,
}));

/**
 * 关系图谱主组件
 */
const RelationGraphPage: React.FC = () => {
  const [data, setData] = useState<RelationGraphData | null>(null);
  const [loading, setLoading] = useState(false);
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [relatedEdges, setRelatedEdges] = useState<GraphEdge[]>([]);

  // 筛选状态
  const [selectedNodeTypes, setSelectedNodeTypes] = useState<GraphNodeType[]>([]);
  const [selectedRelations, setSelectedRelations] = useState<GraphRelationType[]>([]);

  // 数据源 & Neo4j 查询参数
  const [dataSource, setDataSource] = useState<GraphDataSource>('mock');
  const [targetId, setTargetId] = useState<number>(1);
  const [depth, setDepth] = useState<GraphQueryDepth>(3);

  // 从 URL params 读取 targetId（如 ?targetId=5），默认 1
  const [searchParams] = useSearchParams();
  useEffect(() => {
    const tid = searchParams.get('targetId');
    if (tid !== null) {
      const num = Number(tid);
      if (!Number.isNaN(num) && num > 0) {
        setTargetId(num);
      }
    }
  }, [searchParams]);

  const { message } = App.useApp();

  /** 加载图谱数据（按数据源分发） */
  const fetchData = async () => {
    setLoading(true);
    try {
      if (dataSource === 'neo4j') {
        // Neo4j 实时数据：失败时降级回 Mock 数据并提示
        try {
          const res = await getRelationGraphFromNeo4j(targetId, depth);
          if (res.code === 200 || res.code === 0) {
            setData(res.data);
            return;
          }
          throw new Error(res.message || 'Neo4j 接口返回异常');
        } catch (err) {
          // 降级到 Mock 数据
          const fallback = await getRelationGraphMockFallback(
            selectedNodeTypes.length > 0 ? selectedNodeTypes : undefined,
            selectedRelations.length > 0 ? selectedRelations : undefined,
          );
          setData(fallback.data);
          const reason = err instanceof Error ? err.message : '未知错误';
          message.warning(`Neo4j 实时数据获取失败，已降级到 Mock 数据：${reason}`);
          return;
        }
      }
      // Mock 数据源
      const res = await getRelationGraph(
        selectedNodeTypes.length > 0 ? selectedNodeTypes : undefined,
        selectedRelations.length > 0 ? selectedRelations : undefined,
      );
      if (res.code === 200 || res.code === 0) {
        setData(res.data);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedNodeTypes, selectedRelations, dataSource, targetId, depth]);

  /** ECharts 配置 */
  const option = useMemo<EChartsOption>(() => {
    if (!data) return {};
    return {
      tooltip: {
        formatter: (params: unknown) => {
          const p = params as { dataType?: string; data?: GraphNode | GraphEdge };
          if (p.dataType === 'node' && p.data) {
            const node = p.data as GraphNode;
            return `<div style="max-width:240px;">
              <strong>${node.name}</strong><br/>
              <span style="color:${nodeTypeColor[node.type]}">●</span>
              类型：${GraphNodeTypeLabel[node.type]}<br/>
              ${node.riskLevel ? `风险：${node.riskLevel}<br/>` : ''}
              ${node.description ?? ''}
            </div>`;
          }
          if (p.dataType === 'edge' && p.data) {
            const edge = p.data as GraphEdge;
            return `<div>
              <strong>${GraphRelationTypeLabel[edge.relation]}</strong><br/>
              ${edge.description ?? '无描述'}
            </div>`;
          }
          return '';
        },
      },
      legend: [
        {
          data: (Object.keys(GraphNodeTypeLabel) as GraphNodeType[]).map((t) => ({
            name: GraphNodeTypeLabel[t],
            itemStyle: { color: nodeTypeColor[t] },
          })),
          orient: 'vertical',
          right: 10,
          top: 20,
          textStyle: { fontSize: 11 },
        },
      ],
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          label: {
            show: true,
            position: 'right',
            fontSize: 11,
            formatter: (p: unknown) => {
              const node = (p as { data?: GraphNode }).data;
              return node ? node.name : '';
            },
          },
          emphasis: {
            focus: 'adjacency',
            label: { fontSize: 13, fontWeight: 'bold' },
            lineStyle: { width: 4 },
          },
          force: {
            repulsion: 220,
            edgeLength: [80, 180],
            gravity: 0.05,
            layoutAnimation: true,
          },
          data: data.nodes.map((n) => ({
            id: n.id,
            name: n.name,
            symbolSize: n.value ?? nodeTypeSize[n.type],
            itemStyle: {
              color: nodeTypeColor[n.type],
              borderColor: n.riskLevel ? riskLevelColor[n.riskLevel] : colors.neutral[800],
              borderWidth: n.riskLevel ? 3 : 1,
              shadowBlur: 10,
              shadowColor: 'rgba(0,0,0,0.1)',
            },
            category: GraphNodeTypeLabel[n.type],
          })),
          edges: data.edges.map((e) => ({
            id: e.id,
            source: e.source,
            target: e.target,
            lineStyle: {
              ...relationLineStyle[e.relation],
              width: e.weight ?? 2,
              curveness: 0.2,
            },
            label: {
              show: false,
              formatter: GraphRelationTypeLabel[e.relation],
              fontSize: 10,
              color: colors.neutral[600],
            },
          })),
        },
      ],
    };
  }, [data]);

  /** 节点点击事件 */
  const handleChartEvent = (event: { name?: string; data?: GraphNode | GraphEdge; dataType?: string }) => {
    const e = event as { dataType?: string; data?: GraphNode | GraphEdge };
    if (e.dataType === 'node' && e.data) {
      const node = e.data as GraphNode;
      setSelectedNode(node);
      // 计算与该节点相关的边
      const edges = data?.edges.filter((ed) => ed.source === node.id || ed.target === node.id) ?? [];
      setRelatedEdges(edges);
    }
  };

  /** 重置筛选 */
  const handleReset = () => {
    setSelectedNodeTypes([]);
    setSelectedRelations([]);
    setSelectedNode(null);
    setRelatedEdges([]);
  };

  /** 获取边的另一端节点名 */
  const getOtherEndName = (edge: GraphEdge, currentNodeId: string): string => {
    const otherId = edge.source === currentNodeId ? edge.target : edge.source;
    const otherNode = data?.nodes.find((n) => n.id === otherId);
    return otherNode?.name ?? otherId;
  };

  return (
    <div>
      <Title level={4}>关系图谱</Title>
      <Text type="secondary">
        目标关系网络可视化：节点 = 目标实体，边 = 关系（力导向布局，可缩放/拖拽）
      </Text>

      {/* 数据源切换 */}
      <Card size="small" style={{ marginTop: 12 }}>
        <Space wrap>
          <Text strong>数据源：</Text>
          <Radio.Group
            value={dataSource}
            onChange={(e) => setDataSource(e.target.value as GraphDataSource)}
            optionType="button"
            buttonStyle="solid"
            aria-label="数据源切换"
          >
            <Radio.Button value="mock">Mock 数据</Radio.Button>
            <Radio.Button value="neo4j">Neo4j 实时</Radio.Button>
          </Radio.Group>
          {dataSource === 'neo4j' && (
            <>
              <Divider type="vertical" />
              <Text strong>目标 ID：</Text>
              <InputNumber
                value={targetId}
                min={1}
                onChange={(v) => setTargetId(Number(v) || 1)}
                style={{ width: 100 }}
                aria-label="目标 ID"
              />
              <Text strong>查询深度：</Text>
              <Radio.Group
                value={depth}
                onChange={(e) => setDepth(e.target.value as GraphQueryDepth)}
                optionType="button"
                aria-label="查询深度"
              >
                <Radio.Button value={1}>1 跳</Radio.Button>
                <Radio.Button value={2}>2 跳</Radio.Button>
                <Radio.Button value={3}>3 跳</Radio.Button>
              </Radio.Group>
              <Text type="secondary" style={{ fontSize: 12 }}>
                调用 GET /api/profile/relations/{`{targetId}`}?depth={depth}
              </Text>
            </>
          )}
        </Space>
      </Card>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {/* 左侧：筛选面板 */}
        <Col xs={24} lg={6}>
          <Card
            size="small"
            title="筛选"
            extra={
              <Button size="small" onClick={handleReset}>
                重置
              </Button>
            }
          >
            <div style={{ marginBottom: 12 }}>
              <Text strong>节点类型</Text>
              <Divider style={{ margin: '8px 0' }} />
              <Checkbox.Group
                value={selectedNodeTypes}
                onChange={(values) => setSelectedNodeTypes(values as GraphNodeType[])}
                style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                aria-label="节点类型筛选"
              >
                {NODE_TYPE_OPTIONS.map((opt) => (
                  <Checkbox key={opt.value} value={opt.value}>
                    <Space>
                      <span style={{ color: nodeTypeColor[opt.value] }}>●</span>
                      {opt.label}
                    </Space>
                  </Checkbox>
                ))}
              </Checkbox.Group>
            </div>

            <Divider />

            <div>
              <Text strong>关系类型</Text>
              <Divider style={{ margin: '8px 0' }} />
              <Checkbox.Group
                value={selectedRelations}
                onChange={(values) => setSelectedRelations(values as GraphRelationType[])}
                style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                aria-label="关系类型筛选"
              >
                {RELATION_OPTIONS.map((opt) => (
                  <Checkbox key={opt.value} value={opt.value}>
                    <Space>
                      <span style={{ color: relationLineStyle[opt.value].color }}>
                        {relationLineStyle[opt.value].type === 'solid' ? '—' : relationLineStyle[opt.value].type === 'dashed' ? '--' : '··'}
                      </span>
                      {opt.label}
                    </Space>
                  </Checkbox>
                ))}
              </Checkbox.Group>
            </div>

            {data?.stats && (
              <>
                <Divider />
                <div>
                  <Text strong>统计</Text>
                  <div style={{ marginTop: 8, fontSize: 12 }}>
                    <div>节点总数：<Text strong>{data.stats.nodeCount}</Text></div>
                    <div>关系总数：<Text strong>{data.stats.edgeCount}</Text></div>
                  </div>
                </div>
              </>
            )}
          </Card>
        </Col>

        {/* 中间：图谱画布 */}
        <Col xs={24} lg={selectedNode ? 12 : 18}>
          <Card
            size="small"
            title="关系网络"
            bodyStyle={{ padding: 0 }}
            styles={{ body: { padding: 0 } }}
          >
            <div
              role="application"
              aria-label={getAriaLabel('graph.canvas')}
              tabIndex={0}
              style={{ height: 640, outline: 'none' }}
            >
              {loading ? (
                <div
                  style={{
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Spin tip="加载图谱中..." />
                </div>
              ) : data && data.nodes.length > 0 ? (
                <ReactECharts
                  option={option}
                  style={{ height: '100%', width: '100%' }}
                  notMerge
                  lazyUpdate
                  onEvents={{ click: handleChartEvent as never }}
                />
              ) : (
                <div
                  style={{
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Empty description="未匹配到节点" />
                </div>
              )}
            </div>
          </Card>
        </Col>

        {/* 右侧：节点详情 */}
        {selectedNode && (
          <Col xs={24} lg={6}>
            <Card
              size="small"
              title={
                <Space>
                  <span style={{ color: nodeTypeColor[selectedNode.type] }}>●</span>
                  <span>节点详情</span>
                </Space>
              }
              extra={
                <Button size="small" onClick={() => setSelectedNode(null)}>
                  关闭
                </Button>
              }
            >
              <ProDescriptions
                column={1}
                size="small"
                dataSource={selectedNode}
              >
                <ProDescriptions.Item label="名称" dataIndex="name" />
                <ProDescriptions.Item label="类型" dataIndex="type"
                  render={(t) => (
                    <Tag color="blue">{GraphNodeTypeLabel[t as GraphNodeType]}</Tag>
                  )}
                />
                <ProDescriptions.Item label="风险等级" dataIndex="riskLevel"
                  render={(r) =>
                    r ? (
                      <Tag color={r === 'critical' ? 'red' : r === 'high' ? 'orange' : r === 'medium' ? 'gold' : 'green'}>
                        {r as string}
                      </Tag>
                    ) : (
                      '-'
                    )
                  }
                />
                <ProDescriptions.Item label="权重" dataIndex="value" />
                <ProDescriptions.Item label="描述" dataIndex="description"
                  render={(d) => (d ? <Paragraph style={{ fontSize: 12, marginBottom: 0 }}>{d as string}</Paragraph> : '-')}
                />
              </ProDescriptions>

              {selectedNode.tags && selectedNode.tags.length > 0 && (
                <>
                  <Divider style={{ margin: '12px 0 8px' }} />
                  <Text strong>标签</Text>
                  <div style={{ marginTop: 8 }}>
                    <Space wrap size={[4, 4]}>
                      {selectedNode.tags.map((t) => (
                        <Tag key={t}>{t}</Tag>
                      ))}
                    </Space>
                  </div>
                </>
              )}

              {selectedNode.properties && (
                <>
                  <Divider style={{ margin: '12px 0 8px' }} />
                  <Text strong>属性</Text>
                  <div style={{ marginTop: 8 }}>
                    {Object.entries(selectedNode.properties).map(([k, v]) => (
                      <div key={k} style={{ fontSize: 12, marginBottom: 4 }}>
                        <Text type="secondary">{k}：</Text>
                        <Text>{String(v)}</Text>
                      </div>
                    ))}
                  </div>
                </>
              )}

              <Divider style={{ margin: '12px 0 8px' }} />
              <Text strong>关联关系（{relatedEdges.length}）</Text>
              <div style={{ marginTop: 8, maxHeight: 240, overflow: 'auto' }}>
                {relatedEdges.length === 0 ? (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    无关联关系
                  </Text>
                ) : (
                  relatedEdges.map((edge) => (
                    <div
                      key={edge.id}
                      style={{
                        fontSize: 12,
                        marginBottom: 8,
                        padding: 8,
                        background: colors.neutral[50],
                        borderRadius: 4,
                      }}
                    >
                      <Space>
                        <Tag
                          color={
                            edge.source === selectedNode.id ? 'default' : 'processing'
                          }
                        >
                          {edge.source === selectedNode.id ? '出' : '入'}
                        </Tag>
                        <Tag
                          style={{
                            color: relationLineStyle[edge.relation].color,
                            borderColor: relationLineStyle[edge.relation].color,
                          }}
                        >
                          {GraphRelationTypeLabel[edge.relation]}
                        </Tag>
                        <Text strong>{getOtherEndName(edge, selectedNode.id)}</Text>
                      </Space>
                      {edge.description && (
                        <div style={{ marginTop: 4, color: colors.neutral[600] }}>
                          {edge.description}
                        </div>
                      )}
                      {edge.createTime && (
                        <div style={{ marginTop: 2, fontSize: 11, color: colors.neutral[500] }}>
                          {formatDateTime(edge.createTime)}
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>
            </Card>
          </Col>
        )}
      </Row>
    </div>
  );
};

export default RelationGraphPage;
