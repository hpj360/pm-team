/**
 * 红方作战模块 API 服务
 * 包含目标画像、威胁情报、攻击链路、漏洞利用、武器库、协同作战
 * 全部接口在请求失败时回退到 Mock 数据。
 */
import { get, post, del } from '@/utils/request';
import type {
  ApiResponse,
  TargetProfile,
  ThreatActor,
  IntelFeed,
  ThreatIntelItem,
  AttackChain,
  Vulnerability,
  ArsenalItem,
  CollaborationTask,
  TeamMember,
  CollaborationMessage,
  RelationGraphData,
  TaskItem,
  TaskStatus,
  GraphNode,
  GraphEdge,
  GraphNodeType,
  Neo4jRelationGraphData,
  GraphRelationType,
  GraphQueryDepth,
} from '@/types';
import {
  mockTargetProfiles,
  searchTargetProfiles,
  getTargetProfileById,
  mockThreatActors,
  mockIntelFeeds,
  mockThreatIntelItems,
  getThreatIntelById,
  mockAttackChains,
  getAttackChainById,
  mockVulnerabilities,
  getVulnerabilityById,
  mockArsenal,
  getArsenalItemById,
  mockCollaborationTasks,
  mockTeamMembers,
  mockCollaborationMessages,
  mockRelationGraph,
  getGraphNodeById,
  getEdgesByNodeId,
  filterNodesByTypes,
  filterEdgesByRelations,
  mockTasks,
  getTaskById,
  filterTasks,
  groupManageTasksByStatus,
} from '@/mock';

/* ===================== 目标画像 ===================== */

/** 获取目标列表 */
export async function getTargetProfiles(keyword?: string): Promise<ApiResponse<TargetProfile[]>> {
  try {
    return await get<TargetProfile[]>('/redteam/target-profiles', keyword ? { keyword } : undefined);
  } catch {
    return { code: 200, message: 'success', data: searchTargetProfiles(keyword ?? '') };
  }
}

/** 获取目标画像详情 */
export async function getTargetProfileDetail(id: string): Promise<ApiResponse<TargetProfile>> {
  try {
    return await get<TargetProfile>(`/redteam/target-profiles/${id}`);
  } catch {
    const data = getTargetProfileById(id) ?? mockTargetProfiles[0];
    return { code: 200, message: 'success', data };
  }
}

/* ===================== 威胁情报 ===================== */

/** 获取威胁情报列表 */
export async function getThreatIntelList(): Promise<ApiResponse<ThreatIntelItem[]>> {
  try {
    return await get<ThreatIntelItem[]>('/redteam/threat-intel');
  } catch {
    return { code: 200, message: 'success', data: mockThreatIntelItems };
  }
}

/** 获取威胁情报详情 */
export async function getThreatIntelDetail(id: string): Promise<ApiResponse<ThreatIntelItem>> {
  try {
    return await get<ThreatIntelItem>(`/redteam/threat-intel/${id}`);
  } catch {
    const data = getThreatIntelById(id) ?? mockThreatIntelItems[0];
    return { code: 200, message: 'success', data };
  }
}

/** 获取威胁行为者列表 */
export async function getThreatActors(): Promise<ApiResponse<ThreatActor[]>> {
  try {
    return await get<ThreatActor[]>('/redteam/threat-actors');
  } catch {
    return { code: 200, message: 'success', data: mockThreatActors };
  }
}

/** 获取情报订阅源列表 */
export async function getIntelFeeds(): Promise<ApiResponse<IntelFeed[]>> {
  try {
    return await get<IntelFeed[]>('/redteam/intel-feeds');
  } catch {
    return { code: 200, message: 'success', data: mockIntelFeeds };
  }
}

/** 同步情报源 */
export function syncIntelFeed(id: string): Promise<ApiResponse<void>> {
  return post<void>(`/redteam/intel-feeds/${id}/sync`, { id });
}

/* ===================== 攻击链路 ===================== */

/** 获取攻击链路列表 */
export async function getAttackChains(): Promise<ApiResponse<AttackChain[]>> {
  try {
    return await get<AttackChain[]>('/redteam/attack-chains');
  } catch {
    return { code: 200, message: 'success', data: mockAttackChains };
  }
}

/** 获取攻击链路详情 */
export async function getAttackChainDetail(id: string): Promise<ApiResponse<AttackChain>> {
  try {
    return await get<AttackChain>(`/redteam/attack-chains/${id}`);
  } catch {
    const data = getAttackChainById(id) ?? mockAttackChains[0];
    return { code: 200, message: 'success', data };
  }
}

/* ===================== 漏洞利用 ===================== */

/** 获取漏洞列表 */
export async function getVulnerabilities(): Promise<ApiResponse<Vulnerability[]>> {
  try {
    return await get<Vulnerability[]>('/redteam/vulnerabilities');
  } catch {
    return { code: 200, message: 'success', data: mockVulnerabilities };
  }
}

/** 获取漏洞详情 */
export async function getVulnerabilityDetail(id: string): Promise<ApiResponse<Vulnerability>> {
  try {
    return await get<Vulnerability>(`/redteam/vulnerabilities/${id}`);
  } catch {
    const data = getVulnerabilityById(id) ?? mockVulnerabilities[0];
    return { code: 200, message: 'success', data };
  }
}

/* ===================== 武器库 ===================== */

/** 获取武器库列表 */
export async function getArsenal(): Promise<ApiResponse<ArsenalItem[]>> {
  try {
    return await get<ArsenalItem[]>('/redteam/arsenal');
  } catch {
    return { code: 200, message: 'success', data: mockArsenal };
  }
}

/** 获取武器详情 */
export async function getArsenalDetail(id: string): Promise<ApiResponse<ArsenalItem>> {
  try {
    return await get<ArsenalItem>(`/redteam/arsenal/${id}`);
  } catch {
    const data = getArsenalItemById(id) ?? mockArsenal[0];
    return { code: 200, message: 'success', data };
  }
}

/* ===================== 协同作战 ===================== */

/** 获取协同任务看板 */
export async function getCollaborationTasks(): Promise<ApiResponse<CollaborationTask[]>> {
  try {
    return await get<CollaborationTask[]>('/redteam/collaboration/tasks');
  } catch {
    return { code: 200, message: 'success', data: mockCollaborationTasks };
  }
}

/** 获取团队成员 */
export async function getTeamMembers(): Promise<ApiResponse<TeamMember[]>> {
  try {
    return await get<TeamMember[]>('/redteam/collaboration/members');
  } catch {
    return { code: 200, message: 'success', data: mockTeamMembers };
  }
}

/** 获取消息流 */
export async function getCollaborationMessages(): Promise<ApiResponse<CollaborationMessage[]>> {
  try {
    return await get<CollaborationMessage[]>('/redteam/collaboration/messages');
  } catch {
    return { code: 200, message: 'success', data: mockCollaborationMessages };
  }
}

/* ===================== 关系图谱 ===================== */

/**
 * 获取关系图谱数据
 * @param nodeTypes 节点类型筛选（空数组或 undefined 表示全部）
 * @param relations 关系类型筛选（空数组或 undefined 表示全部）
 */
export async function getRelationGraph(
  nodeTypes?: GraphNodeType[],
  relations?: string[],
): Promise<ApiResponse<RelationGraphData>> {
  try {
    return await get<RelationGraphData>('/redteam/relation-graph', {
      nodeTypes: nodeTypes ?? [],
      relations: relations ?? [],
    });
  } catch {
    const nodes: GraphNode[] =
      nodeTypes && nodeTypes.length > 0 ? filterNodesByTypes(nodeTypes) : mockRelationGraph.nodes;
    const filteredNodeIds = new Set(nodes.map((n) => n.id));
    const allEdges: GraphEdge[] =
      relations && relations.length > 0 ? filterEdgesByRelations(relations) : mockRelationGraph.edges;
    const edges = allEdges.filter(
      (e) => filteredNodeIds.has(e.source) && filteredNodeIds.has(e.target),
    );
    const typeDistribution: Record<GraphNodeType, number> = {
      organization: 0,
      person: 0,
      asset: 0,
      domain: 0,
      ip: 0,
      vulnerability: 0,
    };
    for (const n of nodes) typeDistribution[n.type] += 1;
    return {
      code: 200,
      message: 'success',
      data: {
        nodes,
        edges,
        stats: {
          nodeCount: nodes.length,
          edgeCount: edges.length,
          typeDistribution,
        },
      },
    };
  }
}

/**
 * 获取指定节点的关联边
 */
export async function getNodeEdges(
  nodeId: string,
): Promise<ApiResponse<{ edges: GraphEdge[]; node: GraphNode | undefined }>> {
  try {
    return await get(`/redteam/relation-graph/nodes/${nodeId}/edges`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: { edges: getEdgesByNodeId(nodeId), node: getGraphNodeById(nodeId) },
    };
  }
}

/* ===================== Neo4j 实时关系图谱 ===================== */

/**
 * Neo4j 节点类型 → 内部 GraphNodeType 映射
 * 后端返回 TARGET / FILE 等大写枚举，需映射到前端图谱节点类型枚举。
 */
const neo4jNodeTypeMap: Record<string, GraphNodeType> = {
  TARGET: 'organization',
  ORGANIZATION: 'organization',
  PERSON: 'person',
  ASSET: 'asset',
  FILE: 'asset',
  DOMAIN: 'domain',
  IP: 'ip',
  VULNERABILITY: 'vulnerability',
};

/**
 * Neo4j 关系类型 → 内部 GraphRelationType 映射
 * 后端返回 CONTAINS / BELONGS_TO 等大写关系，映射到前端枚举。
 */
const neo4jRelationMap: Record<string, GraphRelationType> = {
  CONTAINS: 'own',
  BELONGS_TO: 'belong_to',
  BELONG_TO: 'belong_to',
  MANAGES: 'manage',
  MANAGE: 'manage',
  OWNS: 'own',
  OWN: 'own',
  CONNECTS: 'connect',
  CONNECT: 'connect',
  RESOLVES: 'resolve',
  RESOLVE: 'resolve',
  HOSTS: 'host',
  HOST: 'host',
  EXPLOITS: 'exploit',
  EXPLOIT: 'exploit',
  RELATES: 'relate',
  RELATE: 'relate',
  RELATED_TO: 'relate',
};

/**
 * 将 Neo4j 原始响应转换为前端 RelationGraphData（ECharts 力导向图格式）
 * - 节点 id 统一转为 string
 * - type/relation 通过映射表落到前端枚举；未识别的 type 默认 'asset'，未识别 relation 默认 'relate'
 * - 自动补全 stats 统计信息
 */
export function transformNeo4jToGraphData(raw: Neo4jRelationGraphData): RelationGraphData {
  const typeDistribution: Record<GraphNodeType, number> = {
    organization: 0,
    person: 0,
    asset: 0,
    domain: 0,
    ip: 0,
    vulnerability: 0,
  };

  const nodes: GraphNode[] = raw.nodes.map((n, idx) => {
    const mappedType: GraphNodeType = neo4jNodeTypeMap[String(n.type).toUpperCase()] ?? 'asset';
    typeDistribution[mappedType] += 1;
    // 收集除 id/name/type 外的额外属性
    const properties: Record<string, string | number | boolean> = {};
    for (const [k, v] of Object.entries(n)) {
      if (k === 'id' || k === 'name' || k === 'type') continue;
      if (v === null || v === undefined) continue;
      if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') {
        properties[k] = v;
      } else {
        properties[k] = JSON.stringify(v);
      }
    }
    return {
      id: String(n.id),
      name: n.name ?? String(n.id),
      type: mappedType,
      value: idx === 0 ? 60 : 30,
      description: n.description ? String(n.description) : undefined,
      properties: Object.keys(properties).length > 0 ? properties : undefined,
    };
  });

  const nodeIdSet = new Set(nodes.map((n) => n.id));
  const edges: GraphEdge[] = raw.edges
    .filter((e) => nodeIdSet.has(String(e.source)) && nodeIdSet.has(String(e.target)))
    .map((e, idx) => {
      const relation: GraphRelationType =
        neo4jRelationMap[String(e.relation).toUpperCase()] ?? 'relate';
      return {
        id: `neo4j_e_${idx}`,
        source: String(e.source),
        target: String(e.target),
        relation,
        weight: 2,
        description: e.description ? String(e.description) : undefined,
      };
    });

  return {
    nodes,
    edges,
    stats: {
      nodeCount: nodes.length,
      edgeCount: edges.length,
      typeDistribution,
    },
  };
}

/**
 * 调用 profile-service 的 Neo4j 实时关系图谱接口
 * GET /api/profile/relations/{targetId}?depth=3
 *
 * 注意：该接口失败时不做自动 Mock 降级（由调用方决定是否降级并提示），
 * 以便上层展示 Toast 提示并切换回 Mock 数据。
 *
 * @param targetId 目标 ID（默认 1）
 * @param depth 查询深度，1/2/3
 * @returns 转换后的 RelationGraphData（已映射为 ECharts 力导向图格式）
 */
export async function getRelationGraphFromNeo4j(
  targetId: number | string = 1,
  depth: GraphQueryDepth = 3,
): Promise<ApiResponse<RelationGraphData>> {
  const raw = await get<Neo4jRelationGraphData>(
    `/profile/relations/${targetId}`,
    { depth },
  );
  return {
    code: raw.code,
    message: raw.message,
    data: transformNeo4jToGraphData(raw.data ?? { nodes: [], edges: [] }),
  };
}

/**
 * 获取 Neo4j 数据失败时的 Mock 降级数据
 * 复用现有 mockRelationGraph，并应用节点/关系筛选逻辑。
 */
export async function getRelationGraphMockFallback(
  nodeTypes?: GraphNodeType[],
  relations?: string[],
): Promise<ApiResponse<RelationGraphData>> {
  const nodes: GraphNode[] =
    nodeTypes && nodeTypes.length > 0 ? filterNodesByTypes(nodeTypes) : mockRelationGraph.nodes;
  const filteredNodeIds = new Set(nodes.map((n) => n.id));
  const allEdges: GraphEdge[] =
    relations && relations.length > 0 ? filterEdgesByRelations(relations) : mockRelationGraph.edges;
  const edges = allEdges.filter(
    (e) => filteredNodeIds.has(e.source) && filteredNodeIds.has(e.target),
  );
  const typeDistribution: Record<GraphNodeType, number> = {
    organization: 0,
    person: 0,
    asset: 0,
    domain: 0,
    ip: 0,
    vulnerability: 0,
  };
  for (const n of nodes) typeDistribution[n.type] += 1;
  return {
    code: 200,
    message: 'success',
    data: {
      nodes,
      edges,
      stats: {
        nodeCount: nodes.length,
        edgeCount: edges.length,
        typeDistribution,
      },
    },
  };
}

/* ===================== 任务管理 ===================== */

/** 获取任务列表 */
export async function getTasks(params?: {
  keyword?: string;
  status?: TaskStatus;
  assignee?: string;
  priority?: TaskItem['priority'];
}): Promise<ApiResponse<TaskItem[]>> {
  try {
    return await get<TaskItem[]>('/redteam/tasks', params as unknown as Record<string, unknown>);
  } catch {
    return { code: 200, message: 'success', data: filterTasks(params ?? {}) };
  }
}

/** 获取任务详情 */
export async function getTaskDetail(id: string): Promise<ApiResponse<TaskItem>> {
  try {
    return await get<TaskItem>(`/redteam/tasks/${id}`);
  } catch {
    const data = getTaskById(id) ?? mockTasks[0];
    return { code: 200, message: 'success', data };
  }
}

/** 创建/更新任务（Mock 直接返回成功） */
export function saveTask(task: Partial<TaskItem>): Promise<ApiResponse<void>> {
  return post<void>('/redteam/tasks', task as unknown as Record<string, unknown>);
}

/** 切换任务状态 */
export function updateTaskStatus(id: string, status: TaskStatus): Promise<ApiResponse<void>> {
  return post<void>(`/redteam/tasks/${id}/status`, { status });
}

/** 删除任务 */
export function deleteTask(id: string): Promise<ApiResponse<void>> {
  return del<void>(`/redteam/tasks/${id}`);
}

/** 按状态分组 */
export async function getTasksGrouped(): Promise<ApiResponse<Record<TaskStatus, TaskItem[]>>> {
  try {
    return await get<Record<TaskStatus, TaskItem[]>>('/redteam/tasks/grouped');
  } catch {
    return { code: 200, message: 'success', data: groupManageTasksByStatus(mockTasks) };
  }
}
