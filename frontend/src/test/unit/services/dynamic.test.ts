/**
 * 单元测试：沙箱动态分析服务层 src/services/dynamic.ts（V5.2）
 * 覆盖 5 个端点封装函数的 happy path 与失败降级：
 * - submitDynamicAnalysis（POST /analyze/dynamic/submit?fileId=xxx）
 * - getDynamicTask（GET /analyze/dynamic/{taskId}）
 * - getDynamicReport（GET /analyze/dynamic/{taskId}/report）
 * - pollDynamicTask（POST /analyze/dynamic/{taskId}/poll）
 * - listDynamicTasks（GET /analyze/dynamic）
 *
 * 降级策略：Cuckoo 沙箱不可用时后端返回 degraded=true 的任务；
 * 前端请求抛错时回退到 Mock 数据，保证主流程不阻塞。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { get, post } from '@/utils/request';
import {
  submitDynamicAnalysis,
  getDynamicTask,
  getDynamicReport,
  pollDynamicTask,
  listDynamicTasks,
} from '@/services/dynamic';
import {
  mockDynamicTasks,
  getMockDynamicTaskById,
  getMockDynamicReport,
} from '@/mock';
import type { DynamicAnalysisTask, DynamicReport } from '@/types';
import { DynamicTaskStatus } from '@/types';

// Mock 请求工具
vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

const mockedGet = vi.mocked(get);
const mockedPost = vi.mocked(post);

describe('沙箱动态分析服务层 src/services/dynamic.ts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  /* ===== 1. submitDynamicAnalysis ===== */
  describe('submitDynamicAnalysis', () => {
    it('happy path: 后端成功时返回平台任务ID', async () => {
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: 'dyn-backend-001' });
      const res = await submitDynamicAnalysis(101);
      expect(mockedPost).toHaveBeenCalledWith(
        '/analyze/dynamic/submit',
        undefined,
        { params: { fileId: 101 } },
      );
      expect(res.code).toBe(200);
      expect(res.data).toBe('dyn-backend-001');
    });

    it('降级: 请求抛错时返回 Mock taskId 且不阻塞', async () => {
      mockedPost.mockRejectedValueOnce(new Error('cuckoo down'));
      const res = await submitDynamicAnalysis(102);
      expect(res.code).toBe(200);
      expect(typeof res.data).toBe('string');
      expect(res.data.length).toBeGreaterThan(0);
    });

    it('降级路径: fileId 为 3 的倍数时返回 degraded 任务ID（验证 Mock 降级策略）', async () => {
      mockedPost.mockRejectedValueOnce(new Error('503'));
      const res = await submitDynamicAnalysis(3);
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
      // mockSubmitDynamicAnalysis 会将降级任务 unshift 到 mockDynamicTasks
      const created = mockDynamicTasks.find((t) => t.taskId === res.data);
      expect(created).toBeDefined();
      expect(created?.degraded).toBe(true);
      expect(created?.status).toBe(DynamicTaskStatus.DEGRADED);
      expect(created?.errorMessage).toContain('降级');
    });
  });

  /* ===== 2. getDynamicTask ===== */
  describe('getDynamicTask', () => {
    it('happy path: 后端成功时返回任务详情', async () => {
      const task = getMockDynamicTaskById('dyn-001') as DynamicAnalysisTask;
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: task });
      const res = await getDynamicTask('dyn-001');
      expect(mockedGet).toHaveBeenCalledWith('/analyze/dynamic/dyn-001');
      expect(res.code).toBe(200);
      expect(res.data.taskId).toBe('dyn-001');
      expect(res.data.status).toBe(DynamicTaskStatus.PARSED);
      expect(res.data.processTree.length).toBeGreaterThan(0);
      expect(res.data.attackTechniques.length).toBeGreaterThan(0);
    });

    it('happy path: 返回降级任务时 degraded=true', async () => {
      const task = getMockDynamicTaskById('dyn-003') as DynamicAnalysisTask;
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: task });
      const res = await getDynamicTask('dyn-003');
      expect(res.data.degraded).toBe(true);
      expect(res.data.status).toBe(DynamicTaskStatus.DEGRADED);
      expect(res.data.errorMessage).toBeTruthy();
    });

    it('降级: 请求抛错时回退到 Mock 任务', async () => {
      mockedGet.mockRejectedValueOnce(new Error('timeout'));
      const res = await getDynamicTask('dyn-001');
      expect(res.code).toBe(200);
      expect(res.data.taskId).toBe('dyn-001');
      expect(res.data.processTree.length).toBeGreaterThan(0);
    });

    it('降级: 未知 taskId 时回退到 mockDynamicTasks[0]', async () => {
      mockedGet.mockRejectedValueOnce(new Error('404'));
      const res = await getDynamicTask('not-exist');
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
      // 应回退到列表首个任务
      expect(res.data.taskId).toBe(mockDynamicTasks[0].taskId);
    });
  });

  /* ===== 3. getDynamicReport ===== */
  describe('getDynamicReport', () => {
    it('happy path: 后端成功时返回完整报告', async () => {
      const report = getMockDynamicReport('dyn-001');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: report });
      const res = await getDynamicReport('dyn-001');
      expect(mockedGet).toHaveBeenCalledWith('/analyze/dynamic/dyn-001/report');
      expect(res.code).toBe(200);
      expect(res.data.taskId).toBe('dyn-001');
      expect(res.data.score).toBeGreaterThan(0);
      expect(res.data.processTree.length).toBeGreaterThan(0);
      expect(res.data.networkConnections.length).toBeGreaterThan(0);
      expect(res.data.attackTechniques.length).toBeGreaterThan(0);
      expect(res.data.iocs.length).toBeGreaterThan(0);
      expect(res.data.stixObjects.length).toBeGreaterThan(0);
    });

    it('happy path: 降级任务返回降级报告（空行为指标）', async () => {
      const report = getMockDynamicReport('dyn-003');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: report });
      const res = await getDynamicReport('dyn-003');
      expect(res.data.degraded).toBe(true);
      expect(res.data.processTree).toEqual([]);
      expect(res.data.errorMessage).toBeTruthy();
    });

    it('降级: 请求抛错时回退到 Mock 报告', async () => {
      mockedGet.mockRejectedValueOnce(new Error('500'));
      const res = await getDynamicReport('dyn-002');
      expect(res.code).toBe(200);
      expect(res.data.taskId).toBe('dyn-002');
      expect(res.data.processTree).toBeDefined();
    });

    it('降级报告包含 STIX 2.1 对象（process / network-traffic）', async () => {
      mockedGet.mockRejectedValueOnce(new Error('503'));
      const res = await getDynamicReport('dyn-001');
      const stixTypes = res.data.stixObjects.map((s) => s.type);
      expect(stixTypes).toContain('process');
      expect(stixTypes).toContain('network-traffic');
    });
  });

  /* ===== 4. pollDynamicTask ===== */
  describe('pollDynamicTask', () => {
    it('happy path: 后端成功时返回当前状态字符串', async () => {
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: 'RUNNING' });
      const res = await pollDynamicTask('dyn-001');
      expect(mockedPost).toHaveBeenCalledWith('/analyze/dynamic/dyn-001/poll');
      expect(res.code).toBe(200);
      expect(res.data).toBe('RUNNING');
    });

    it('降级: 请求抛错时回退到 Mock 任务状态', async () => {
      mockedPost.mockRejectedValueOnce(new Error('network error'));
      const res = await pollDynamicTask('dyn-001');
      expect(res.code).toBe(200);
      // dyn-001 状态为 PARSED
      expect(res.data).toBe(DynamicTaskStatus.PARSED);
    });

    it('降级: 未知 taskId 时回退到默认 PARSED', async () => {
      mockedPost.mockRejectedValueOnce(new Error('404'));
      const res = await pollDynamicTask('not-exist');
      expect(res.code).toBe(200);
      expect(res.data).toBe('PARSED');
    });
  });

  /* ===== 5. listDynamicTasks ===== */
  describe('listDynamicTasks', () => {
    it('happy path: 后端成功时返回任务列表', async () => {
      const list = mockDynamicTasks.slice(0, 2);
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: list });
      const res = await listDynamicTasks();
      expect(mockedGet).toHaveBeenCalledWith('/analyze/dynamic');
      expect(res.code).toBe(200);
      expect(Array.isArray(res.data)).toBe(true);
      expect(res.data.length).toBe(2);
    });

    it('降级: 请求抛错时回退到 Mock 任务列表', async () => {
      mockedGet.mockRejectedValueOnce(new Error('502'));
      const res = await listDynamicTasks();
      expect(res.code).toBe(200);
      expect(Array.isArray(res.data)).toBe(true);
      expect(res.data.length).toBeGreaterThan(0);
      // Mock 列表应包含已解析 / 已完成 / 降级 三种状态任务
      const statuses = res.data.map((t) => t.status);
      expect(statuses).toContain(DynamicTaskStatus.PARSED);
      expect(statuses).toContain(DynamicTaskStatus.DEGRADED);
    });
  });

  /* ===== 综合断言 ===== */
  it('所有降级路径返回 code 200 且 data 非空，保证页面不阻塞', async () => {
    mockedPost.mockRejectedValue(new Error('all down'));
    mockedGet.mockRejectedValue(new Error('all down'));
    const [submit, task, report, poll, list] = await Promise.all([
      submitDynamicAnalysis(999),
      getDynamicTask('dyn-001'),
      getDynamicReport('dyn-001'),
      pollDynamicTask('dyn-001'),
      listDynamicTasks(),
    ]);
    [submit, task, report, poll, list].forEach((res) => {
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
    });
    // 类型断言：确保降级返回结构正确
    expect(typeof submit.data).toBe('string');
    expect((task.data as DynamicAnalysisTask).taskId).toBeTruthy();
    expect((report.data as DynamicReport).taskId).toBeTruthy();
    expect(typeof poll.data).toBe('string');
    expect(Array.isArray(list.data)).toBe(true);
  });
});
