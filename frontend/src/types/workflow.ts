/**
 * 工作流相关类型定义
 * - 工作流定义（WorkflowDefinition）
 * - 工作流节点（WorkflowNode）：发起人 / 审批人 / 抄送人 / 条件分支 / 结束
 * - 工作流连线（WorkflowEdge）：支持条件分支表达式
 * 对应后端 workflow-service（端口 8094）的数据模型
 */

/** 工作流节点类型 */
export type WorkflowNodeType = 'START' | 'APPROVER' | 'CC' | 'CONDITION' | 'END';

/** 节点位置坐标 */
export interface WorkflowNodePosition {
  x: number;
  y: number;
}

/** 工作流节点 */
export interface WorkflowNode {
  /** 节点唯一标识 */
  id: string;
  /** 节点类型：START 发起人 / APPROVER 审批人 / CC 抄送人 / CONDITION 条件分支 / END 结束 */
  type: WorkflowNodeType;
  /** 节点名称 */
  name: string;
  /** 审批人/抄送人 ID 列表（START/END 节点可为空） */
  approverIds: string[];
  /** 节点在画布上的位置坐标 */
  position: WorkflowNodePosition;
}

/** 工作流连线 */
export interface WorkflowEdge {
  /** 连线唯一标识 */
  id: string;
  /** 源节点 ID */
  source: string;
  /** 目标节点 ID */
  target: string;
  /** 条件表达式（仅 CONDITION 节点的出边需要） */
  condition?: string;
}

/** 工作流定义 */
export interface WorkflowDefinition {
  /** 工作流 ID（新建时由后端生成） */
  id?: string;
  /** 工作流名称 */
  name: string;
  /** 节点列表 */
  nodes: WorkflowNode[];
  /** 连线列表 */
  edges: WorkflowEdge[];
  /** 是否启用 */
  enabled: boolean;
  /** 创建人 */
  createdBy?: string;
  /** 创建时间 */
  createdAt?: string;
}

/** 保存工作流的 DTO（对应后端 POST /api/workflow/definitions） */
export interface WorkflowDefinitionPayload {
  /** 工作流 ID（更新时传入，新建时省略） */
  id?: string;
  /** 工作流名称 */
  name: string;
  /** 节点列表（序列化为 nodes_json） */
  nodes: WorkflowNode[];
  /** 连线列表（序列化为 edges_json） */
  edges: WorkflowEdge[];
  /** 是否启用 */
  enabled: boolean;
}

/** 节点类型标签映射 */
export const WorkflowNodeTypeLabels: Record<WorkflowNodeType, string> = {
  START: '发起人',
  APPROVER: '审批人',
  CC: '抄送人',
  CONDITION: '条件分支',
  END: '结束',
};

/** 节点类型颜色映射（用于左侧面板与画布节点配色） */
export const WorkflowNodeTypeColors: Record<WorkflowNodeType, string> = {
  START: '#52c41a', // 绿色
  APPROVER: '#1677ff', // 蓝色
  CC: '#8c8c8c', // 灰色
  CONDITION: '#faad14', // 黄色
  END: '#ff4d4f', // 红色
};

/** 节点类型形状映射（圆形 / 矩形 / 菱形） */
export const WorkflowNodeTypeShapes: Record<WorkflowNodeType, 'circle' | 'rect' | 'diamond'> = {
  START: 'circle',
  APPROVER: 'rect',
  CC: 'rect',
  CONDITION: 'diamond',
  END: 'circle',
};
