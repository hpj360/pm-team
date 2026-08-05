/**
 * 工作流设计器页面
 * - 顶部：工作流名称输入框 + 保存按钮 + 启用/禁用 Switch + 返回按钮
 * - 左侧：节点类型面板（可拖拽到画布）—— 发起人 / 审批人 / 抄送人 / 条件分支 / 结束
 * - 中间：React Flow 画布（拖拽节点 + 连线）
 * - 右侧：节点属性面板（选中节点时可编辑 name / approverIds；选中连线时可编辑 condition）
 * 路由：
 *   /admin/workflows/new  新建
 *   /admin/workflows/:id  编辑
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Button,
  Space,
  Input,
  Switch,
  Select,
  message,
  Tag,
  Divider,
  Empty,
  Tooltip,
  Form,
} from 'antd';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  DeleteOutlined,
  ApartmentOutlined,
} from '@ant-design/icons';
import ReactFlow, {
  ReactFlowProvider,
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  useReactFlow,
  Handle,
  Position,
  type Node,
  type Edge,
  type Connection,
  type NodeProps,
  type OnConnect,
} from 'reactflow';
import 'reactflow/dist/style.css';
import {
  getWorkflowDefinition,
  saveWorkflowDefinition,
} from '@/services/workflow';
import type {
  WorkflowDefinition,
  WorkflowDefinitionPayload,
  WorkflowNode,
  WorkflowEdge,
  WorkflowNodeType,
} from '@/types';
import {
  WorkflowNodeTypeLabels,
  WorkflowNodeTypeColors,
  WorkflowNodeTypeShapes,
} from '@/types';

const { Title, Text } = Typography;

/* ===================== 类型与常量 ===================== */

/** React Flow 节点数据（承载 name / approverIds） */
interface WorkflowNodeData {
  name: string;
  approverIds: string[];
  [key: string]: unknown;
}

/** React Flow 连线数据（承载 condition） */
interface WorkflowEdgeData {
  condition?: string;
  [key: string]: unknown;
}

/** 左侧节点面板配置 */
const NODE_PALETTE: {
  type: WorkflowNodeType;
  name: string;
  desc: string;
}[] = [
  { type: 'START', name: '发起人', desc: '流程发起节点' },
  { type: 'APPROVER', name: '审批人', desc: '审批处理节点' },
  { type: 'CC', name: '抄送人', desc: '抄送通知节点' },
  { type: 'CONDITION', name: '条件分支', desc: '条件判断节点' },
  { type: 'END', name: '结束', desc: '流程结束节点' },
];

/* ===================== 类型转换工具 ===================== */

/** 业务节点 -> React Flow 节点 */
function toFlowNode(node: WorkflowNode): Node<WorkflowNodeData> {
  return {
    id: node.id,
    type: node.type,
    position: { x: node.position.x, y: node.position.y },
    data: { name: node.name, approverIds: [...node.approverIds] },
  };
}

/** React Flow 节点 -> 业务节点 */
function fromFlowNode(node: Node<WorkflowNodeData>): WorkflowNode {
  return {
    id: node.id,
    type: (node.type ?? 'APPROVER') as WorkflowNodeType,
    name: node.data?.name ?? '',
    approverIds: node.data?.approverIds ?? [],
    position: { x: node.position.x, y: node.position.y },
  };
}

/** 业务连线 -> React Flow 连线 */
function toFlowEdge(edge: WorkflowEdge): Edge<WorkflowEdgeData> {
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label: edge.condition,
    data: { condition: edge.condition },
  };
}

/** React Flow 连线 -> 业务连线 */
function fromFlowEdge(edge: Edge<WorkflowEdgeData>): WorkflowEdge {
  const condition = edge.data?.condition ?? (typeof edge.label === 'string' ? edge.label : undefined);
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    condition,
  };
}

/* ===================== 自定义节点组件 ===================== */

/** 自定义工作流节点（按形状渲染：圆形 / 矩形 / 菱形） */
const WorkflowNodeComponent: React.FC<NodeProps<WorkflowNodeData>> = ({
  data,
  selected,
  type,
}) => {
  const nodeType = (type ?? 'APPROVER') as WorkflowNodeType;
  const color = WorkflowNodeTypeColors[nodeType] ?? '#1677ff';
  const shape = WorkflowNodeTypeShapes[nodeType] ?? 'rect';
  const fallbackLabel = WorkflowNodeTypeLabels[nodeType] ?? nodeType;
  const display = data?.name || fallbackLabel;

  // 公共句柄样式
  const handleStyle: React.CSSProperties = {
    background: color,
    width: 10,
    height: 10,
    border: '2px solid #fff',
  };

  if (shape === 'diamond') {
    // 菱形：用旋转的正方形作为背景，文本与句柄叠加在上层
    return (
      <div
        style={{
          position: 'relative',
          width: 130,
          height: 90,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div
          style={{
            position: 'absolute',
            width: 88,
            height: 88,
            background: color,
            transform: 'rotate(45deg)',
            borderRadius: 10,
            boxShadow: selected
              ? `0 0 0 3px ${color}, 0 4px 12px rgba(0,0,0,0.2)`
              : '0 2px 8px rgba(0,0,0,0.2)',
          }}
        />
        <div
          style={{
            position: 'relative',
            zIndex: 1,
            color: '#fff',
            fontWeight: 600,
            fontSize: 13,
            textAlign: 'center',
            maxWidth: 100,
            wordBreak: 'break-all',
          }}
        >
          {display}
        </div>
        {nodeType !== 'START' && (
          <Handle type="target" position={Position.Top} style={handleStyle} />
        )}
        {nodeType !== 'END' && (
          <Handle type="source" position={Position.Bottom} style={handleStyle} />
        )}
      </div>
    );
  }

  // 圆形 / 矩形
  const isCircle = shape === 'circle';
  const nodeStyle: React.CSSProperties = {
    width: isCircle ? 90 : 150,
    height: isCircle ? 90 : 50,
    borderRadius: isCircle ? '50%' : 8,
    borderColor: color,
    background: isCircle ? color : '#fff',
    color: isCircle ? '#fff' : color,
    borderWidth: 2,
    borderStyle: 'solid',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    textAlign: 'center',
    fontSize: 13,
    fontWeight: 600,
    padding: isCircle ? 0 : '0 8px',
    boxShadow: selected
      ? `0 0 0 2px ${color}`
      : '0 2px 8px rgba(0,0,0,0.12)',
    cursor: 'grab',
    wordBreak: 'break-all',
  };

  return (
    <div style={nodeStyle}>
      {nodeType !== 'START' && (
        <Handle type="target" position={Position.Top} style={handleStyle} />
      )}
      <span>{display}</span>
      {nodeType !== 'END' && (
        <Handle type="source" position={Position.Bottom} style={handleStyle} />
      )}
    </div>
  );
};

/** 节点类型映射（必须在组件外定义，避免 React Flow 重新创建导致性能问题） */
const nodeTypes = {
  START: WorkflowNodeComponent,
  APPROVER: WorkflowNodeComponent,
  CC: WorkflowNodeComponent,
  CONDITION: WorkflowNodeComponent,
  END: WorkflowNodeComponent,
};

/* ===================== 设计器内部组件 ===================== */

const WorkflowDesignerInner: React.FC = () => {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const flowWrapperRef = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition } = useReactFlow();

  const [name, setName] = useState<string>('');
  const [enabled, setEnabled] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);

  const [nodes, setNodes, onNodesChange] = useNodesState<WorkflowNodeData>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<WorkflowEdgeData>([]);

  /** 当前选中的节点（用于右侧属性面板） */
  const [selectedNode, setSelectedNode] = useState<Node<WorkflowNodeData> | null>(null);
  /** 当前选中的连线（用于右侧条件编辑） */
  const [selectedEdge, setSelectedEdge] = useState<Edge<WorkflowEdgeData> | null>(null);

  const isEdit = useMemo(() => !!id, [id]);

  /** 加载已有工作流 */
  useEffect(() => {
    if (!id) {
      // 新建模式：默认放入一个发起人节点
      const startNode: Node<WorkflowNodeData> = {
        id: `START_${Date.now()}`,
        type: 'START',
        position: { x: 320, y: 40 },
        data: { name: '发起人', approverIds: [] },
      };
      setNodes([startNode]);
      return;
    }
    getWorkflowDefinition(id)
      .then((res) => {
        if (res.code === 200 && res.data) {
          const def = res.data as WorkflowDefinition;
          setName(def.name);
          setEnabled(def.enabled);
          setNodes(def.nodes.map(toFlowNode));
          setEdges(def.edges.map(toFlowEdge));
        } else {
          message.error('工作流不存在或加载失败');
        }
      })
      .catch(() => {
        message.error('加载工作流失败');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  /** 拖拽开始：写入节点类型到 dataTransfer */
  const onDragStart = (event: React.DragEvent, nodeType: WorkflowNodeType) => {
    event.dataTransfer.setData('application/reactflow', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  };

  /** 拖拽悬停：阻止默认行为以允许 drop */
  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  /** 拖拽释放：在画布上创建新节点 */
  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      const nodeType = event.dataTransfer.getData('application/reactflow') as WorkflowNodeType;
      if (!nodeType) return;
      const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
      const newNode: Node<WorkflowNodeData> = {
        id: `${nodeType}_${Date.now()}`,
        type: nodeType,
        position,
        data: { name: WorkflowNodeTypeLabels[nodeType], approverIds: [] },
      };
      setNodes((nds) => nds.concat(newNode));
    },
    [screenToFlowPosition, setNodes],
  );

  /** 连线：连接两个节点 */
  const onConnect: OnConnect = useCallback(
    (params: Connection) => {
      const newEdge: Edge<WorkflowEdgeData> = {
        ...params,
        id: `edge_${Date.now()}`,
        data: { condition: undefined },
      } as Edge<WorkflowEdgeData>;
      setEdges((eds) => addEdge(newEdge, eds));
    },
    [setEdges],
  );

  /** 节点点击：选中节点 */
  const onNodeClick = useCallback((_: React.MouseEvent, node: Node<WorkflowNodeData>) => {
    setSelectedNode(node);
    setSelectedEdge(null);
  }, []);

  /** 连线点击：选中连线 */
  const onEdgeClick = useCallback((_: React.MouseEvent, edge: Edge<WorkflowEdgeData>) => {
    setSelectedEdge(edge);
    setSelectedNode(null);
  }, []);

  /** 画布点击：清除选中 */
  const onPaneClick = useCallback(() => {
    setSelectedNode(null);
    setSelectedEdge(null);
  }, []);

  /** 更新选中节点数据 */
  const updateNodeData = useCallback(
    (patch: Partial<WorkflowNodeData>) => {
      if (!selectedNode) return;
      const next = { ...selectedNode, data: { ...selectedNode.data, ...patch } };
      setSelectedNode(next);
      setNodes((nds) =>
        nds.map((n) => (n.id === selectedNode.id ? { ...n, data: { ...n.data, ...patch } } : n)),
      );
    },
    [selectedNode, setNodes],
  );

  /** 更新选中连线条件 */
  const updateEdgeCondition = useCallback(
    (condition: string) => {
      if (!selectedEdge) return;
      const next: Edge<WorkflowEdgeData> = {
        ...selectedEdge,
        label: condition || undefined,
        data: { condition: condition || undefined },
      };
      setSelectedEdge(next);
      setEdges((eds) =>
        eds.map((e) => (e.id === selectedEdge.id ? next : e)),
      );
    },
    [selectedEdge, setEdges],
  );

  /** 删除选中节点 */
  const handleDeleteNode = useCallback(() => {
    if (!selectedNode) return;
    const nodeId = selectedNode.id;
    setNodes((nds) => nds.filter((n) => n.id !== nodeId));
    // 同时删除关联的连线
    setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId));
    setSelectedNode(null);
    message.success('节点已删除');
  }, [selectedNode, setNodes, setEdges]);

  /** 删除选中连线 */
  const handleDeleteEdge = useCallback(() => {
    if (!selectedEdge) return;
    const edgeId = selectedEdge.id;
    setEdges((eds) => eds.filter((e) => e.id !== edgeId));
    setSelectedEdge(null);
    message.success('连线已删除');
  }, [selectedEdge, setEdges]);

  /** 保存工作流 */
  const handleSave = useCallback(async () => {
    if (!name.trim()) {
      message.warning('请输入工作流名称');
      return;
    }
    // 校验：至少包含一个发起人与一个结束节点
    const hasStart = nodes.some((n) => n.type === 'START');
    const hasEnd = nodes.some((n) => n.type === 'END');
    if (!hasStart || !hasEnd) {
      message.warning('工作流至少需要包含一个发起人节点和一个结束节点');
      return;
    }
    setSaving(true);
    try {
      const dto: WorkflowDefinitionPayload = {
        id: id || undefined,
        name: name.trim(),
        nodes: nodes.map(fromFlowNode),
        edges: edges.map(fromFlowEdge),
        enabled,
      };
      const res = await saveWorkflowDefinition(dto);
      if (res.code === 200) {
        message.success(isEdit ? '工作流已更新' : '工作流已创建');
        navigate('/admin/workflows');
      } else {
        message.error(res.message || '保存失败');
      }
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  }, [name, nodes, edges, enabled, id, isEdit, navigate]);

  /** 当前选中节点的类型标签信息 */
  const selectedNodeMeta = useMemo(() => {
    if (!selectedNode) return null;
    const t = (selectedNode.type ?? 'APPROVER') as WorkflowNodeType;
    return {
      type: t,
      label: WorkflowNodeTypeLabels[t],
      color: WorkflowNodeTypeColors[t],
    };
  }, [selectedNode]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 'calc(100vh - 120px)' }}>
      {/* 顶部工具栏 */}
      <Card size="small" style={{ marginBottom: 12 }}>
        <Space wrap size="middle">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/workflows')}>
            返回列表
          </Button>
          <Space>
            <Text strong>工作流名称：</Text>
            <Input
              placeholder="请输入工作流名称"
              value={name}
              onChange={(e) => setName(e.target.value)}
              style={{ width: 260 }}
              maxLength={50}
            />
          </Space>
          <Space>
            <Text>启用：</Text>
            <Switch
              checked={enabled}
              onChange={(v) => setEnabled(v)}
              checkedChildren="启用"
              unCheckedChildren="禁用"
            />
          </Space>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            onClick={handleSave}
          >
            保存工作流
          </Button>
        </Space>
      </Card>

      <div style={{ display: 'flex', flex: 1, gap: 12, minHeight: 0 }}>
        {/* 左侧：节点类型面板 */}
        <Card
          size="small"
          title={<Space><ApartmentOutlined />节点类型</Space>}
          style={{ width: 220, flexShrink: 0 }}
          bodyStyle={{ padding: 12 }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            拖拽节点到画布
          </Text>
          <Divider style={{ margin: '8px 0' }} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {NODE_PALETTE.map((item) => {
              const color = WorkflowNodeTypeColors[item.type];
              const shape = WorkflowNodeTypeShapes[item.type];
              return (
                <Tooltip key={item.type} title={item.desc}>
                  <div
                    draggable
                    onDragStart={(e) => onDragStart(e, item.type)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '8px 10px',
                      border: '1px solid #f0f0f0',
                      borderRadius: 6,
                      background: '#fafafa',
                      cursor: 'grab',
                      transition: 'all 0.2s',
                    }}
                    onMouseEnter={(e) => {
                      (e.currentTarget as HTMLDivElement).style.borderColor = color;
                      (e.currentTarget as HTMLDivElement).style.background = '#fff';
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLDivElement).style.borderColor = '#f0f0f0';
                      (e.currentTarget as HTMLDivElement).style.background = '#fafafa';
                    }}
                  >
                    <div
                      style={{
                        width: shape === 'circle' ? 24 : shape === 'diamond' ? 22 : 28,
                        height: shape === 'circle' ? 24 : shape === 'diamond' ? 22 : 16,
                        borderRadius: shape === 'circle' ? '50%' : shape === 'diamond' ? 3 : 4,
                        background: color,
                        transform: shape === 'diamond' ? 'rotate(45deg)' : undefined,
                        flexShrink: 0,
                      }}
                    />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: '#262626' }}>
                        {item.name}
                      </div>
                      <div style={{ fontSize: 11, color: '#8c8c8c' }}>{item.desc}</div>
                    </div>
                  </div>
                </Tooltip>
              );
            })}
          </div>
        </Card>

        {/* 中间：React Flow 画布 */}
        <div
          ref={flowWrapperRef}
          style={{
            flex: 1,
            minWidth: 0,
            background: '#fff',
            border: '1px solid #f0f0f0',
            borderRadius: 8,
            overflow: 'hidden',
          }}
          onDrop={onDrop}
          onDragOver={onDragOver}
        >
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
            onEdgeClick={onEdgeClick}
            onPaneClick={onPaneClick}
            nodeTypes={nodeTypes}
            fitView
            minZoom={0.2}
            maxZoom={2}
            deleteKeyCode={['Backspace', 'Delete']}
            style={{ background: '#f7f9fc' }}
          >
            <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="#d9d9d9" />
            <Controls />
            <MiniMap
              nodeStrokeColor={(n) =>
                WorkflowNodeTypeColors[(n.type ?? 'APPROVER') as WorkflowNodeType] ?? '#1677ff'
              }
              nodeColor={(n) =>
                WorkflowNodeTypeColors[(n.type ?? 'APPROVER') as WorkflowNodeType] ?? '#1677ff'
              }
            />
          </ReactFlow>
        </div>

        {/* 右侧：属性面板 */}
        <Card
          size="small"
          title="属性面板"
          style={{ width: 280, flexShrink: 0 }}
          bodyStyle={{ padding: 16 }}
        >
          {!selectedNode && !selectedEdge && (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="选择节点或连线查看属性"
              style={{ marginTop: 40 }}
            />
          )}

          {selectedNode && selectedNodeMeta && (
            <div>
              <div style={{ marginBottom: 12 }}>
                <Tag color="blue">{selectedNodeMeta.label}</Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  ID: {selectedNode.id}
                </Text>
              </div>
              <Form layout="vertical">
                <Form.Item label="节点名称">
                  <Input
                    value={selectedNode.data?.name ?? ''}
                    onChange={(e) => updateNodeData({ name: e.target.value })}
                    placeholder="请输入节点名称"
                    maxLength={30}
                  />
                </Form.Item>
                <Form.Item
                  label="审批人/抄送人 ID"
                  extra="按回车添加，支持多个"
                >
                  <Select
                    mode="tags"
                    placeholder="输入审批人 ID"
                    value={selectedNode.data?.approverIds ?? []}
                    onChange={(vals: string[]) => updateNodeData({ approverIds: vals })}
                    style={{ width: '100%' }}
                    tokenSeparators={[',', ' ']}
                  />
                </Form.Item>
              </Form>
              <Divider style={{ margin: '12px 0' }} />
              <Button
                danger
                block
                icon={<DeleteOutlined />}
                onClick={handleDeleteNode}
              >
                删除节点
              </Button>
            </div>
          )}

          {selectedEdge && (
            <div>
              <div style={{ marginBottom: 12 }}>
                <Tag color="orange">连线</Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {selectedEdge.source} → {selectedEdge.target}
                </Text>
              </div>
              <Form layout="vertical">
                <Form.Item
                  label="条件表达式"
                  extra="条件分支节点出边可填写，如 level==机密"
                >
                  <Input
                    value={
                      (selectedEdge.data?.condition ??
                        (typeof selectedEdge.label === 'string'
                          ? selectedEdge.label
                          : '')) as string
                    }
                    onChange={(e) => updateEdgeCondition(e.target.value)}
                    placeholder="如 level==机密"
                    maxLength={100}
                  />
                </Form.Item>
              </Form>
              <Divider style={{ margin: '12px 0' }} />
              <Button
                danger
                block
                icon={<DeleteOutlined />}
                onClick={handleDeleteEdge}
              >
                删除连线
              </Button>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
};

/* ===================== 设计器入口（包裹 Provider） ===================== */

const WorkflowDesigner: React.FC = () => (
  <div>
    <Title level={4}>工作流设计器</Title>
    <ReactFlowProvider>
      <WorkflowDesignerInner />
    </ReactFlowProvider>
  </div>
);

export default WorkflowDesigner;
