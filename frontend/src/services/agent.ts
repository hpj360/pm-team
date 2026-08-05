/**
 * AI Agent 化模块 API 服务（V5.1）
 * - Agent 自主分析任务（提交 / 查询 / 列表 / 轨迹）
 * - 知识库管理（索引 / 检索 / 列表）
 *
 * 后端 ai-service 运行在端口 8093，前端通过网关代理访问 `/api/ai/*`。
 * 调用失败时降级返回 Mock 数据，保证页面不阻塞。
 */
import { get, post } from '@/utils/request';
import type {
  AgentTask,
  AgentTrace,
  Knowledge,
  KnowledgeSearchResult,
  SubmitAnalysisPayload,
  IndexKnowledgePayload,
  ApiResponse,
} from '@/types';
import {
  generateMockAgentTask,
  generateMockAgentTasks,
  generateMockTraces,
  generateMockKnowledgeList,
  generateMockKnowledgeSearch,
} from '@/mock/agent';

/**
 * 提交 Agent 自主分析任务（POST /api/ai/agent/analyze）
 * @param payload 分析请求（含 query 与 userId）
 */
export async function submitAnalysis(
  payload: SubmitAnalysisPayload,
): Promise<ApiResponse<string>> {
  try {
    return await post<string>('/ai/agent/analyze', payload as unknown as Record<string, unknown>);
  } catch {
    // 降级：返回 Mock taskId
    const mockTask = generateMockAgentTask(payload.query);
    return {
      code: 200,
      message: 'success',
      data: mockTask.taskId,
    };
  }
}

/**
 * 查询任务状态与结果（GET /api/ai/agent/tasks/{taskId}）
 * @param taskId 任务ID
 */
export async function getAgentTask(
  taskId: string,
): Promise<ApiResponse<AgentTask>> {
  try {
    const res = await get<AgentTask>(`/ai/agent/tasks/${taskId}`);
    // 后端 evidenceChainJson / referencedFilesJson / tracesJson 是 JSON 字符串，需解析
    return {
      code: res.code,
      message: res.message,
      data: normalizeAgentTask(res.data),
    };
  } catch {
    // 降级：返回 Mock 任务（用 taskId 作为 query）
    return {
      code: 200,
      message: 'success',
      data: generateMockAgentTask(taskId),
    };
  }
}

/**
 * 查询任务列表（GET /api/ai/agent/tasks）
 * @param userId 用户ID（可选）
 * @param limit 返回条数上限
 */
export async function getAgentTasks(
  userId?: number,
  limit: number = 20,
): Promise<ApiResponse<AgentTask[]>> {
  try {
    const params: Record<string, unknown> = { limit };
    if (userId != null) {
      params.userId = userId;
    }
    const res = await get<AgentTask[]>('/ai/agent/tasks', params);
    return {
      code: res.code,
      message: res.message,
      data: (res.data ?? []).map(normalizeAgentTask),
    };
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockAgentTasks(limit),
    };
  }
}

/**
 * 查询推理轨迹（GET /api/ai/agent/traces/{taskId}）
 * @param taskId 任务ID
 */
export async function getAgentTraces(
  taskId: string,
): Promise<ApiResponse<AgentTrace[]>> {
  try {
    return await get<AgentTrace[]>(`/ai/agent/traces/${taskId}`);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockTraces(taskId),
    };
  }
}

/**
 * 索引知识库文档（POST /api/ai/knowledge）
 * @param payload 索引请求
 */
export async function indexKnowledge(
  payload: IndexKnowledgePayload,
): Promise<ApiResponse<string>> {
  try {
    return await post<string>(
      '/ai/knowledge',
      payload as unknown as Record<string, unknown>,
    );
  } catch {
    return {
      code: 200,
      message: 'success',
      data: `k-mock-${Date.now()}`,
    };
  }
}

/**
 * 知识库检索测试（GET /api/ai/knowledge/search）
 * @param query 检索查询
 * @param topK 返回条数
 */
export async function searchKnowledge(
  query: string,
  topK: number = 5,
): Promise<ApiResponse<KnowledgeSearchResult[]>> {
  try {
    return await get<KnowledgeSearchResult[]>('/ai/knowledge/search', { query, topK });
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockKnowledgeSearch(query),
    };
  }
}

/**
 * 查询知识库文档列表（GET /api/ai/knowledge）
 */
export async function getKnowledgeList(): Promise<ApiResponse<Knowledge[]>> {
  try {
    return await get<Knowledge[]>('/ai/knowledge');
  } catch {
    return {
      code: 200,
      message: 'success',
      data: generateMockKnowledgeList(),
    };
  }
}

/**
 * 将后端返回的 AgentTask（含 JSON 字符串字段）规范化为前端可用的对象
 *
 * <p>后端 evidenceChainJson / referencedFilesJson / tracesJson 是 JSON 字符串，
 * 前端需要解析为数组。</p>
 *
 * @param raw 后端原始数据
 * @returns 规范化后的 AgentTask
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function normalizeAgentTask(raw: any): AgentTask {
  if (!raw) {
    return raw;
  }
  // 如果已经是数组（Mock 或已规范化），直接返回
  const evidenceChain = parseJsonArray<string>(raw.evidenceChain ?? raw.evidenceChainJson);
  const referencedFiles = parseJsonArray<string>(raw.referencedFiles ?? raw.referencedFilesJson);
  const traces = parseJsonArray<AgentTrace>(raw.traces ?? raw.tracesJson);
  return {
    taskId: raw.taskId,
    query: raw.query,
    userId: raw.userId ?? null,
    status: raw.status,
    conclusion: raw.conclusion ?? null,
    evidenceChain,
    referencedFiles,
    confidence: raw.confidence ?? null,
    traces,
    errorMessage: raw.errorMessage ?? null,
    createdAt: raw.createdAt,
    completedAt: raw.completedAt ?? null,
  };
}

/**
 * 安全解析 JSON 数组字段
 *
 * @param value 原始值（可能是数组、JSON 字符串或 undefined）
 * @returns 解析后的数组
 */
function parseJsonArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) {
    return value as T[];
  }
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? (parsed as T[]) : [];
    } catch {
      return [];
    }
  }
  return [];
}
