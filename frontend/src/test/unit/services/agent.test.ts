/**
 * 单元测试：AI Agent 服务层 src/services/agent.ts
 * 覆盖 7 个 Agent 端点封装函数的 happy path 与失败降级：
 * - submitAnalysis（POST /ai/agent/analyze）
 * - getAgentTask（GET /ai/agent/tasks/{taskId}）
 * - getAgentTasks（GET /ai/agent/tasks）
 * - getAgentTraces（GET /ai/agent/traces/{taskId}）
 * - indexKnowledge（POST /ai/knowledge）
 * - searchKnowledge（GET /ai/knowledge/search）
 * - getKnowledgeList（GET /ai/knowledge）
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { get, post } from '@/utils/request';
import {
  submitAnalysis,
  getAgentTask,
  getAgentTasks,
  getAgentTraces,
  indexKnowledge,
  searchKnowledge,
  getKnowledgeList,
} from '@/services/agent';
import {
  generateMockAgentTask,
  generateMockAgentTasks,
  generateMockTraces,
  generateMockKnowledgeList,
  generateMockKnowledgeSearch,
} from '@/mock/agent';

// Mock 请求工具
vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

const mockedGet = vi.mocked(get);
const mockedPost = vi.mocked(post);

describe('AI Agent 服务层 src/services/agent.ts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  /* ===== 1. submitAnalysis ===== */
  describe('submitAnalysis', () => {
    it('happy path: 后端成功时返回 taskId', async () => {
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: 'task-001' });
      const res = await submitAnalysis({ query: '分析最近的钓鱼攻击' });
      expect(mockedPost).toHaveBeenCalledWith('/ai/agent/analyze', {
        query: '分析最近的钓鱼攻击',
      });
      expect(res.code).toBe(200);
      expect(res.data).toBe('task-001');
    });

    it('降级: 请求抛错时返回 Mock taskId 且不阻塞', async () => {
      mockedPost.mockRejectedValueOnce(new Error('network error'));
      const res = await submitAnalysis({ query: '测试降级' });
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
      expect(typeof res.data).toBe('string');
    });
  });

  /* ===== 2. getAgentTask ===== */
  describe('getAgentTask', () => {
    it('happy path: 后端返回任务详情（含 JSON 字符串字段应被规范化）', async () => {
      const mockTask = generateMockAgentTask('task-happy');
      // 模拟后端返回 JSON 字符串字段
      const rawTask = {
        ...mockTask,
        evidenceChainJson: JSON.stringify(mockTask.evidenceChain),
        referencedFilesJson: JSON.stringify(mockTask.referencedFiles),
        tracesJson: JSON.stringify(mockTask.traces),
        evidenceChain: undefined,
        referencedFiles: undefined,
        traces: undefined,
      };
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: rawTask });
      const res = await getAgentTask('task-happy');
      expect(mockedGet).toHaveBeenCalledWith('/ai/agent/tasks/task-happy');
      expect(res.code).toBe(200);
      expect(res.data.taskId).toBe(mockTask.taskId);
      // JSON 字符串应被解析为数组
      expect(Array.isArray(res.data.evidenceChain)).toBe(true);
      expect(res.data.evidenceChain.length).toBe(mockTask.evidenceChain.length);
      expect(Array.isArray(res.data.traces)).toBe(true);
    });

    it('降级: 请求抛错时返回 Mock 任务', async () => {
      mockedGet.mockRejectedValueOnce(new Error('503'));
      const res = await getAgentTask('task-fallback');
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
      expect(res.data.taskId).toBeTruthy();
      expect(Array.isArray(res.data.evidenceChain)).toBe(true);
    });
  });

  /* ===== 3. getAgentTasks ===== */
  describe('getAgentTasks', () => {
    it('happy path: 后端返回任务列表', async () => {
      const mockTasks = generateMockAgentTasks(3);
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: mockTasks });
      const res = await getAgentTasks(1001, 10);
      expect(res.code).toBe(200);
      expect(res.data.length).toBe(3);
    });

    it('降级: 请求抛错时返回 Mock 任务列表', async () => {
      mockedGet.mockRejectedValueOnce(new Error('timeout'));
      const res = await getAgentTasks(undefined, 5);
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
    });
  });

  /* ===== 4. getAgentTraces ===== */
  describe('getAgentTraces', () => {
    it('happy path: 后端返回推理轨迹', async () => {
      const mockTraces = generateMockTraces('trace-task');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: mockTraces });
      const res = await getAgentTraces('trace-task');
      expect(mockedGet).toHaveBeenCalledWith('/ai/agent/traces/trace-task');
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
    });

    it('降级: 请求抛错时返回 Mock 轨迹', async () => {
      mockedGet.mockRejectedValueOnce(new Error('down'));
      const res = await getAgentTraces('trace-fallback');
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
    });
  });

  /* ===== 5. indexKnowledge ===== */
  describe('indexKnowledge', () => {
    it('happy path: 后端成功时返回 knowledgeId', async () => {
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: 'k-001' });
      const res = await indexKnowledge({
        title: '测试知识',
        content: '测试内容',
        source: 'ATT&CK',
      });
      expect(mockedPost).toHaveBeenCalledWith('/ai/knowledge', expect.any(Object));
      expect(res.code).toBe(200);
      expect(res.data).toBe('k-001');
    });

    it('降级: 请求抛错时返回 Mock knowledgeId', async () => {
      mockedPost.mockRejectedValueOnce(new Error('500'));
      const res = await indexKnowledge({
        title: '测试',
        content: '内容',
      });
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
    });
  });

  /* ===== 6. searchKnowledge ===== */
  describe('searchKnowledge', () => {
    it('happy path: 后端返回检索结果', async () => {
      const mockResults = generateMockKnowledgeSearch('APT28');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: mockResults });
      const res = await searchKnowledge('APT28', 5);
      expect(mockedGet).toHaveBeenCalledWith('/ai/knowledge/search', {
        query: 'APT28',
        topK: 5,
      });
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
    });

    it('降级: 请求抛错时返回 Mock 检索结果', async () => {
      mockedGet.mockRejectedValueOnce(new Error('404'));
      const res = await searchKnowledge('钓鱼');
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
    });
  });

  /* ===== 7. getKnowledgeList ===== */
  describe('getKnowledgeList', () => {
    it('happy path: 后端返回知识库列表', async () => {
      const mockList = generateMockKnowledgeList();
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: mockList });
      const res = await getKnowledgeList();
      expect(mockedGet).toHaveBeenCalledWith('/ai/knowledge');
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
    });

    it('降级: 请求抛错时返回 Mock 知识库列表', async () => {
      mockedGet.mockRejectedValueOnce(new Error('503'));
      const res = await getKnowledgeList();
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
    });
  });

  /* ===== 综合降级断言 ===== */
  it('所有降级路径返回 code 200 且 data 非空，保证页面不阻塞', async () => {
    mockedPost.mockRejectedValue(new Error('all down'));
    mockedGet.mockRejectedValue(new Error('all down'));
    const [s1, t1, ts1, tr1, k1, sk1, kl1] = await Promise.all([
      submitAnalysis({ query: 'x1' }),
      getAgentTask('x2'),
      getAgentTasks(),
      getAgentTraces('x3'),
      indexKnowledge({ title: 'x', content: 'x' }),
      searchKnowledge('x4'),
      getKnowledgeList(),
    ]);
    [s1, t1, ts1, tr1, k1, sk1, kl1].forEach((res) => {
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
    });
  });
});
