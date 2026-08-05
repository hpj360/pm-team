/**
 * 单元测试：AI 服务层 src/services/ai.ts
 * 覆盖 6 个 AI 端点封装函数的 happy path 与失败降级：
 * - generateThreatSummary（POST /ai/threat-summary/generate）
 * - getThreatSummary（GET /ai/threat-summary/{fileId}）
 * - inferAttackChain（POST /ai/attack-chain/infer）
 * - nlSearch（POST /ai/nlsearch）
 * - generateReportDraft（POST /ai/report-draft/generate）
 * - getReportDraft（GET /ai/report-draft/{reportId}）
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { get, post } from '@/utils/request';
import {
  generateThreatSummary,
  getThreatSummary,
  inferAttackChain,
  nlSearch,
  generateReportDraft,
  getReportDraft,
} from '@/services/ai';
import {
  generateMockThreatSummary,
  generateMockAttackChain,
  generateMockNlSearchResult,
  generateMockReportDraft,
} from '@/mock/ai';

// Mock 请求工具
vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

const mockedGet = vi.mocked(get);
const mockedPost = vi.mocked(post);

describe('AI 服务层 src/services/ai.ts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  /* ===== 1. generateThreatSummary ===== */
  describe('generateThreatSummary', () => {
    it('happy path: 后端成功时返回后端数据', async () => {
      const data = generateMockThreatSummary('f-001');
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data });
      const res = await generateThreatSummary('f-001');
      expect(mockedPost).toHaveBeenCalledWith('/ai/threat-summary/generate', {
        fileId: 'f-001',
      });
      expect(res.code).toBe(200);
      expect(res.data.fileId).toBe('f-001');
      expect(res.data.summary).toBe(data.summary);
      expect(res.data.keyFindings.length).toBeGreaterThan(0);
    });

    it('降级: 请求抛错时返回 Mock 数据且不阻塞', async () => {
      mockedPost.mockRejectedValueOnce(new Error('network error'));
      const res = await generateThreatSummary('f-002');
      expect(res.code).toBe(200);
      expect(res.data.fileId).toBe('f-002');
      expect(res.data.model).toBeTruthy();
      expect(res.data.tokens).toBeGreaterThan(0);
    });
  });

  /* ===== 2. getThreatSummary ===== */
  describe('getThreatSummary', () => {
    it('happy path: 后端成功时返回后端数据', async () => {
      const data = generateMockThreatSummary('f-010');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data });
      const res = await getThreatSummary('f-010');
      expect(mockedGet).toHaveBeenCalledWith('/ai/threat-summary/f-010');
      expect(res.code).toBe(200);
      expect(res.data.fileId).toBe('f-010');
    });

    it('降级: 请求抛错时返回 Mock 数据', async () => {
      mockedGet.mockRejectedValueOnce(new Error('503'));
      const res = await getThreatSummary('f-011');
      expect(res.code).toBe(200);
      expect(res.data.fileId).toBe('f-011');
      expect(res.data.summary).toBeTruthy();
    });
  });

  /* ===== 3. inferAttackChain ===== */
  describe('inferAttackChain', () => {
    it('happy path: 后端成功时返回后端数据', async () => {
      const data = generateMockAttackChain('f-100');
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data });
      const res = await inferAttackChain('f-100');
      expect(mockedPost).toHaveBeenCalledWith('/ai/attack-chain/infer', {
        fileId: 'f-100',
      });
      expect(res.code).toBe(200);
      expect(res.data.attackPaths.length).toBeGreaterThan(0);
      expect(res.data.confidence).toBeGreaterThan(0);
      expect(res.data.reasoning).toContain('f-100');
    });

    it('降级: 请求抛错时返回 Mock 攻击链', async () => {
      mockedPost.mockRejectedValueOnce(new Error('timeout'));
      const res = await inferAttackChain('f-101');
      expect(res.code).toBe(200);
      expect(res.data.attackPaths.length).toBeGreaterThan(0);
      expect(res.data.confidence).toBeLessThanOrEqual(1);
    });
  });

  /* ===== 4. nlSearch ===== */
  describe('nlSearch', () => {
    it('happy path: 后端成功时返回结构化搜索结果', async () => {
      const data = generateMockNlSearchResult('APT28 相关 IP');
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data });
      const res = await nlSearch('APT28 相关 IP');
      expect(mockedPost).toHaveBeenCalledWith('/ai/nlsearch', {
        query: 'APT28 相关 IP',
      });
      expect(res.code).toBe(200);
      expect(res.data.translatedQuery).toContain('APT28 相关 IP');
      expect(res.data.searchResults.length).toBeGreaterThan(0);
    });

    it('降级: 请求抛错时返回 Mock 搜索结果', async () => {
      mockedPost.mockRejectedValueOnce(new Error('ai-service down'));
      const res = await nlSearch('查找钓鱼邮件');
      expect(res.code).toBe(200);
      expect(res.data.searchResults.length).toBeGreaterThan(0);
      expect(res.data.translatedQuery).toContain('查找钓鱼邮件');
    });
  });

  /* ===== 5. generateReportDraft ===== */
  describe('generateReportDraft', () => {
    it('happy path: 后端成功时返回报告草稿', async () => {
      const data = generateMockReportDraft('r-200');
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data });
      const res = await generateReportDraft({
        reportId: 'r-200',
        statsJson: '{}',
        fileListJson: '[]',
        tagDistributionJson: '[]',
      });
      expect(res.code).toBe(200);
      expect(res.data.reportId).toBe('r-200');
      expect(res.data.conclusion).toContain('r-200');
      expect(res.data.recommendations.length).toBeGreaterThan(0);
    });

    it('降级: 请求抛错时返回 Mock 草稿', async () => {
      mockedPost.mockRejectedValueOnce(new Error('500'));
      const res = await generateReportDraft({ reportId: 'r-201' });
      expect(res.code).toBe(200);
      expect(res.data.reportId).toBe('r-201');
      expect(res.data.conclusion).toBeTruthy();
      expect(res.data.recommendations.length).toBeGreaterThan(0);
    });
  });

  /* ===== 6. getReportDraft ===== */
  describe('getReportDraft', () => {
    it('happy path: 后端成功时返回报告草稿', async () => {
      const data = generateMockReportDraft('r-300');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data });
      const res = await getReportDraft('r-300');
      expect(mockedGet).toHaveBeenCalledWith('/ai/report-draft/r-300');
      expect(res.code).toBe(200);
      expect(res.data.reportId).toBe('r-300');
    });

    it('降级: 请求抛错时返回 Mock 草稿', async () => {
      mockedGet.mockRejectedValueOnce(new Error('404'));
      const res = await getReportDraft('r-301');
      expect(res.code).toBe(200);
      expect(res.data.reportId).toBe('r-301');
      expect(res.data.createdAt).toBeTruthy();
    });
  });

  /* ===== 综合断言 ===== */
  it('所有降级路径返回 code 200 且 data 非空，保证页面不阻塞', async () => {
    mockedPost.mockRejectedValue(new Error('all down'));
    mockedGet.mockRejectedValue(new Error('all down'));
    const [s1, s2, c1, n1, d1] = await Promise.all([
      generateThreatSummary('x1'),
      getThreatSummary('x2'),
      inferAttackChain('x3'),
      nlSearch('x4'),
      generateReportDraft({ reportId: 'x5' }),
    ]);
    const d2 = await getReportDraft('x6');
    [s1, s2, c1, n1, d1, d2].forEach((res) => {
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
    });
  });
});
