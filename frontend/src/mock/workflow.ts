/**
 * Mock 数据 - 工作流定义
 * - 提供 3 条示例工作流（文件上传审批 / 高危文件会签 / 涉密文件或签）
 * - 覆盖线性 / 会签 / 或签三种审批模式
 * - 提供 generateMockWorkflowDefinitions 工厂函数
 * 后端 workflow-service（端口 8094）不可达时降级使用
 */
import type { WorkflowDefinition, WorkflowNode, WorkflowEdge } from '@/types';

/** 生成唯一节点 ID */
function makeNodeId(prefix: string, idx: number): string {
  return `${prefix}_node_${idx}_${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * 生成 Mock 工作流定义列表
 * - 工作流 1：文件上传审批（线性流程：发起人 -> 审批人 -> 结束）
 * - 工作流 2：高危文件会签（会签流程：发起人 -> 审批人A + 审批人B -> 结束）
 * - 工作流 3：涉密文件或签（或签流程：发起人 -> 条件分支 -> 审批人A / 审批人B -> 结束）
 */
export function generateMockWorkflowDefinitions(): WorkflowDefinition[] {
  // 工作流 1：文件上传审批（线性）
  const wf1Nodes: WorkflowNode[] = [
    {
      id: 'wf1_start',
      type: 'START',
      name: '发起人',
      approverIds: ['u_1001'],
      position: { x: 250, y: 0 },
    },
    {
      id: 'wf1_approver_1',
      type: 'APPROVER',
      name: '部门主管审批',
      approverIds: ['u_2001'],
      position: { x: 250, y: 120 },
    },
    {
      id: 'wf1_end',
      type: 'END',
      name: '结束',
      approverIds: [],
      position: { x: 250, y: 240 },
    },
  ];
  const wf1Edges: WorkflowEdge[] = [
    { id: 'wf1_e1', source: 'wf1_start', target: 'wf1_approver_1' },
    { id: 'wf1_e2', source: 'wf1_approver_1', target: 'wf1_end' },
  ];

  // 工作流 2：高危文件会签（会签：两个审批人都需通过）
  const wf2Nodes: WorkflowNode[] = [
    {
      id: 'wf2_start',
      type: 'START',
      name: '发起人',
      approverIds: ['u_1001'],
      position: { x: 300, y: 0 },
    },
    {
      id: 'wf2_approver_a',
      type: 'APPROVER',
      name: '安全主管审批',
      approverIds: ['u_2002'],
      position: { x: 180, y: 130 },
    },
    {
      id: 'wf2_approver_b',
      type: 'APPROVER',
      name: '业务主管审批',
      approverIds: ['u_2003'],
      position: { x: 420, y: 130 },
    },
    {
      id: 'wf2_cc_1',
      type: 'CC',
      name: '抄送档案室',
      approverIds: ['u_3001'],
      position: { x: 300, y: 260 },
    },
    {
      id: 'wf2_end',
      type: 'END',
      name: '结束',
      approverIds: [],
      position: { x: 300, y: 380 },
    },
  ];
  const wf2Edges: WorkflowEdge[] = [
    { id: 'wf2_e1', source: 'wf2_start', target: 'wf2_approver_a' },
    { id: 'wf2_e2', source: 'wf2_start', target: 'wf2_approver_b' },
    { id: 'wf2_e3', source: 'wf2_approver_a', target: 'wf2_cc_1' },
    { id: 'wf2_e4', source: 'wf2_approver_b', target: 'wf2_cc_1' },
    { id: 'wf2_e5', source: 'wf2_cc_1', target: 'wf2_end' },
  ];

  // 工作流 3：涉密文件或签（或签：条件分支任一通过即可）
  const wf3Nodes: WorkflowNode[] = [
    {
      id: 'wf3_start',
      type: 'START',
      name: '发起人',
      approverIds: ['u_1001'],
      position: { x: 320, y: 0 },
    },
    {
      id: 'wf3_condition_1',
      type: 'CONDITION',
      name: '密级判断',
      approverIds: [],
      position: { x: 320, y: 120 },
    },
    {
      id: 'wf3_approver_a',
      type: 'APPROVER',
      name: '机密审批人',
      approverIds: ['u_2004'],
      position: { x: 160, y: 260 },
    },
    {
      id: 'wf3_approver_b',
      type: 'APPROVER',
      name: '秘密审批人',
      approverIds: ['u_2005'],
      position: { x: 480, y: 260 },
    },
    {
      id: 'wf3_end',
      type: 'END',
      name: '结束',
      approverIds: [],
      position: { x: 320, y: 400 },
    },
  ];
  const wf3Edges: WorkflowEdge[] = [
    { id: 'wf3_e1', source: 'wf3_start', target: 'wf3_condition_1' },
    { id: 'wf3_e2', source: 'wf3_condition_1', target: 'wf3_approver_a', condition: 'level==机密' },
    { id: 'wf3_e3', source: 'wf3_condition_1', target: 'wf3_approver_b', condition: 'level==秘密' },
    { id: 'wf3_e4', source: 'wf3_approver_a', target: 'wf3_end' },
    { id: 'wf3_e5', source: 'wf3_approver_b', target: 'wf3_end' },
  ];

  return [
    {
      id: 'wf-001',
      name: '文件上传审批流程',
      nodes: wf1Nodes,
      edges: wf1Edges,
      enabled: true,
      createdBy: 'admin',
      createdAt: '2026-07-15T09:30:00Z',
    },
    {
      id: 'wf-002',
      name: '高危文件会签流程',
      nodes: wf2Nodes,
      edges: wf2Edges,
      enabled: true,
      createdBy: 'admin',
      createdAt: '2026-07-20T14:10:00Z',
    },
    {
      id: 'wf-003',
      name: '涉密文件或签流程',
      nodes: wf3Nodes,
      edges: wf3Edges,
      enabled: false,
      createdBy: 'security_officer',
      createdAt: '2026-07-28T16:45:00Z',
    },
  ];
}

/** Mock 工作流定义列表（静态缓存） */
export const mockWorkflowDefinitions: WorkflowDefinition[] = generateMockWorkflowDefinitions();

/** 内存中可变的工作流列表（供新增/更新/删除操作使用） */
let inMemoryWorkflows: WorkflowDefinition[] = [...mockWorkflowDefinitions];

/** 按 ID 获取 Mock 工作流 */
export function getMockWorkflowById(id: string): WorkflowDefinition | undefined {
  return inMemoryWorkflows.find((w) => w.id === id);
}

/** 获取全部 Mock 工作流（返回副本） */
export function listMockWorkflows(): WorkflowDefinition[] {
  return inMemoryWorkflows.map((w) => ({ ...w }));
}

/** Mock 保存工作流（新建或更新） */
export function saveMockWorkflow(payload: {
  id?: string;
  name: string;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  enabled: boolean;
}): WorkflowDefinition {
  const now = new Date().toISOString();
  if (payload.id) {
    // 更新
    const idx = inMemoryWorkflows.findIndex((w) => w.id === payload.id);
    const updated: WorkflowDefinition = {
      id: payload.id,
      name: payload.name,
      nodes: payload.nodes,
      edges: payload.edges,
      enabled: payload.enabled,
      createdBy: idx >= 0 ? inMemoryWorkflows[idx].createdBy : 'admin',
      createdAt: idx >= 0 ? inMemoryWorkflows[idx].createdAt : now,
    };
    if (idx >= 0) {
      inMemoryWorkflows[idx] = updated;
    } else {
      inMemoryWorkflows.push(updated);
    }
    return updated;
  }
  // 新建
  const created: WorkflowDefinition = {
    id: `wf-${Date.now().toString(36)}`,
    name: payload.name,
    nodes: payload.nodes,
    edges: payload.edges,
    enabled: payload.enabled,
    createdBy: 'admin',
    createdAt: now,
  };
  inMemoryWorkflows.push(created);
  return created;
}

/** Mock 切换工作流启用状态 */
export function toggleMockWorkflow(id: string): void {
  const wf = inMemoryWorkflows.find((w) => w.id === id);
  if (wf) {
    wf.enabled = !wf.enabled;
  }
}

/** Mock 删除工作流 */
export function deleteMockWorkflow(id: string): void {
  inMemoryWorkflows = inMemoryWorkflows.filter((w) => w.id !== id);
}

/** 重置内存工作流数据为初始状态（测试用） */
export function resetMockWorkflows(): void {
  inMemoryWorkflows = generateMockWorkflowDefinitions();
}

export default {
  generateMockWorkflowDefinitions,
  mockWorkflowDefinitions,
  getMockWorkflowById,
  listMockWorkflows,
  saveMockWorkflow,
  toggleMockWorkflow,
  deleteMockWorkflow,
  resetMockWorkflows,
  makeNodeId,
};
