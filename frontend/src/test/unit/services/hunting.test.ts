/**
 * 单元测试：威胁狩猎服务层 src/services/hunting.ts（V5.3）
 * 覆盖 13 个端点封装函数的 happy path 与失败降级：
 *
 * 狩猎假设：
 * - createHypothesis（POST /hunting/hypothesis）
 * - listHypotheses（GET /hunting/hypothesis）
 * - getHypothesis（GET /hunting/hypothesis/{id}）
 * - validateHypothesis（POST /hunting/hypothesis/{id}/validate）
 *
 * ATT&CK 矩阵：
 * - getAttackMatrix（GET /hunting/attack-matrix）
 * - getTechniquesByTactic（GET /hunting/attack-matrix/tactic/{tactic}）
 * - searchAttackTechniques（GET /hunting/attack-matrix/search?keyword=xxx）
 *
 * 狩猎规则：
 * - listHuntingRules（GET /hunting/rules）
 * - getHuntingRule（GET /hunting/rules/{id}）
 * - importSigmaRule（POST /hunting/rules/sigma/import）
 * - importYaraRule（POST /hunting/rules/yara/import）
 * - testHuntingRule（POST /hunting/rules/{id}/test?fileId=xxx）
 * - getHuntingRuleStats（GET /hunting/rules/{id}/stats）
 * - findRulesByTechnique（GET /hunting/rules/by-technique/{techniqueId}）
 *
 * 降级策略：后端请求失败时回退到 Mock 数据（ATT&CK 矩阵/假设/规则）
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { get, post } from '@/utils/request';
import {
  createHypothesis,
  listHypotheses,
  getHypothesis,
  validateHypothesis,
  getAttackMatrix,
  getTechniquesByTactic,
  searchAttackTechniques,
  listHuntingRules,
  getHuntingRule,
  importSigmaRule,
  importYaraRule,
  testHuntingRule,
  getHuntingRuleStats,
  findRulesByTechnique,
} from '@/services/hunting';
import {
  mockAttackMatrix,
  mockHypotheses,
  mockHuntingRules,
  getMockHypothesisById,
  getMockHuntingRuleById,
  mockTechniquesByTactic,
  mockSearchTechniques,
  mockRulesByTechnique,
} from '@/mock';
import type {
  AttackMatrix,
  HypothesisDetail,
  HuntingHypothesis,
  HuntingRule,
  RuleTestResult,
  RuleStats,
} from '@/types';
import { HypothesisStatus, HuntingRuleType } from '@/types';

// Mock 请求工具
vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

const mockedGet = vi.mocked(get);
const mockedPost = vi.mocked(post);

describe('威胁狩猎服务层 src/services/hunting.ts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  /* ==================== 狩猎假设 ==================== */

  /* ===== 1. createHypothesis ===== */
  describe('createHypothesis', () => {
    it('happy path: 后端成功时返回假设ID', async () => {
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: 'hyp-backend-001' });
      const payload = { description: '检测 PowerShell 编码命令执行', techniqueId: 'T1059.001', userId: 1 };
      const res = await createHypothesis(payload);
      expect(mockedPost).toHaveBeenCalledWith(
        '/hunting/hypothesis',
        payload as unknown as Record<string, unknown>,
      );
      expect(res.code).toBe(200);
      expect(res.data).toBe('hyp-backend-001');
    });

    it('降级: 请求抛错时返回 Mock 假设ID 且不阻塞', async () => {
      mockedPost.mockRejectedValueOnce(new Error('network error'));
      const res = await createHypothesis({
        description: '检测 LSASS 内存转储',
        techniqueId: 'T1003.001',
        userId: 1,
      });
      expect(res.code).toBe(200);
      expect(typeof res.data).toBe('string');
      expect(res.data.startsWith('hyp-')).toBe(true);
    });
  });

  /* ===== 2. listHypotheses ===== */
  describe('listHypotheses', () => {
    it('happy path: 后端成功时返回假设列表', async () => {
      const list = mockHypotheses.slice(0, 2);
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: list });
      const res = await listHypotheses();
      expect(mockedGet).toHaveBeenCalledWith('/hunting/hypothesis');
      expect(res.code).toBe(200);
      expect(res.data.length).toBe(2);
    });

    it('降级: 请求抛错时回退到 Mock 假设列表', async () => {
      mockedGet.mockRejectedValueOnce(new Error('503'));
      const res = await listHypotheses();
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
      // Mock 列表应包含 CONFIRMED / REFUTED / DRAFT 三种状态
      const statuses = res.data.map((h) => h.status);
      expect(statuses).toContain(HypothesisStatus.CONFIRMED);
      expect(statuses).toContain(HypothesisStatus.REFUTED);
      expect(statuses).toContain(HypothesisStatus.DRAFT);
    });
  });

  /* ===== 3. getHypothesis ===== */
  describe('getHypothesis', () => {
    it('happy path: 后端成功时返回假设详情', async () => {
      const detail = getMockHypothesisById('hyp-001') as HypothesisDetail;
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: detail });
      const res = await getHypothesis('hyp-001');
      expect(mockedGet).toHaveBeenCalledWith('/hunting/hypothesis/hyp-001');
      expect(res.code).toBe(200);
      expect(res.data.id).toBe('hyp-001');
      expect(res.data.techniqueId).toBe('T1059.001');
      expect(res.data.confidence).toBeGreaterThan(0);
    });

    it('降级: 请求抛错时回退到 Mock 假设', async () => {
      mockedGet.mockRejectedValueOnce(new Error('404'));
      const res = await getHypothesis('hyp-002');
      expect(res.code).toBe(200);
      expect(res.data.id).toBe('hyp-002');
      expect(res.data.techniqueId).toBe('T1129');
    });

    it('降级: 未知 id 时回退到 mockHypotheses[0]', async () => {
      mockedGet.mockRejectedValueOnce(new Error('500'));
      const res = await getHypothesis('not-exist');
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
      expect(res.data.id).toBe(mockHypotheses[0].id);
    });
  });

  /* ===== 4. validateHypothesis ===== */
  describe('validateHypothesis', () => {
    it('happy path: 后端成功时返回验证后的假设实体', async () => {
      const validated: HuntingHypothesis = {
        id: 'hyp-001',
        description: 'desc',
        techniqueId: 'T1059.001',
        userId: 1,
        userName: 'admin',
        status: HypothesisStatus.CONFIRMED,
        confidence: 0.95,
        hits: [],
        recommendedIocs: [],
        createTime: '2026-07-26T08:00:00Z',
        updateTime: '2026-07-26T09:00:00Z',
        validatedTime: '2026-07-26T09:00:00Z',
      };
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: validated });
      const res = await validateHypothesis('hyp-001');
      expect(mockedPost).toHaveBeenCalledWith('/hunting/hypothesis/hyp-001/validate');
      expect(res.code).toBe(200);
      expect(res.data.status).toBe(HypothesisStatus.CONFIRMED);
      expect(res.data.confidence).toBeGreaterThan(0.7);
    });

    it('降级: 请求抛错时回退到 Mock 验证结果（命中时为 CONFIRMED）', async () => {
      mockedPost.mockRejectedValueOnce(new Error('timeout'));
      const res = await validateHypothesis('hyp-001');
      expect(res.code).toBe(200);
      // hyp-001 在 Mock 中有 hits，应被确认为 CONFIRMED
      expect(res.data.status).toBe(HypothesisStatus.CONFIRMED);
      expect(res.data.confidence).toBeGreaterThan(0);
      expect(res.data.validatedTime).toBeTruthy();
    });

    it('降级: 无命中时回退到 Mock 验证结果为 REFUTED', async () => {
      mockedPost.mockRejectedValueOnce(new Error('502'));
      // hyp-003 在 Mock 中 hits 为空，应被否定
      const res = await validateHypothesis('hyp-003');
      expect(res.code).toBe(200);
      expect(res.data.status).toBe(HypothesisStatus.REFUTED);
    });
  });

  /* ==================== ATT&CK 矩阵 ==================== */

  /* ===== 5. getAttackMatrix ===== */
  describe('getAttackMatrix', () => {
    it('happy path: 后端成功时返回矩阵数据', async () => {
      const matrix: AttackMatrix = {
        tactics: mockAttackMatrix.tactics,
        techniques: mockAttackMatrix.techniques,
        tacticCount: mockAttackMatrix.tacticCount,
        techniqueCount: mockAttackMatrix.techniqueCount,
      };
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: matrix });
      const res = await getAttackMatrix();
      expect(mockedGet).toHaveBeenCalledWith('/hunting/attack-matrix');
      expect(res.code).toBe(200);
      expect(res.data.tactics.length).toBe(14);
      expect(res.data.techniques.length).toBeGreaterThan(0);
      expect(res.data.tacticCount).toBe(res.data.tactics.length);
      expect(res.data.techniqueCount).toBe(res.data.techniques.length);
    });

    it('降级: 请求抛错时回退到 Mock 矩阵', async () => {
      mockedGet.mockRejectedValueOnce(new Error('500'));
      const res = await getAttackMatrix();
      expect(res.code).toBe(200);
      expect(res.data.tactics.length).toBe(14);
      expect(res.data.techniques.length).toBeGreaterThan(10);
    });
  });

  /* ===== 6. getTechniquesByTactic ===== */
  describe('getTechniquesByTactic', () => {
    it('happy path: 后端成功时返回战术下的技术列表', async () => {
      const techniques = mockTechniquesByTactic('execution');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: techniques });
      const res = await getTechniquesByTactic('execution');
      expect(mockedGet).toHaveBeenCalledWith('/hunting/attack-matrix/tactic/execution');
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
      res.data.forEach((t) => expect(t.tactic).toBe('execution'));
    });

    it('降级: 请求抛错时回退到 Mock 按战术筛选', async () => {
      mockedGet.mockRejectedValueOnce(new Error('404'));
      const res = await getTechniquesByTactic('credential-access');
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
      res.data.forEach((t) => expect(t.tactic).toBe('credential-access'));
    });

    it('降级: 不存在的战术返回空数组', async () => {
      mockedGet.mockRejectedValueOnce(new Error('500'));
      const res = await getTechniquesByTactic('non-existent-tactic');
      expect(res.code).toBe(200);
      expect(res.data).toEqual([]);
    });
  });

  /* ===== 7. searchAttackTechniques ===== */
  describe('searchAttackTechniques', () => {
    it('happy path: 后端成功时返回搜索结果', async () => {
      const results = mockSearchTechniques('PowerShell');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: results });
      const res = await searchAttackTechniques('PowerShell');
      expect(mockedGet).toHaveBeenCalledWith(
        '/hunting/attack-matrix/search',
        { keyword: 'PowerShell' },
      );
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
    });

    it('降级: 请求抛错时回退到 Mock 关键词搜索', async () => {
      mockedGet.mockRejectedValueOnce(new Error('500'));
      const res = await searchAttackTechniques('T1059');
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
      // 搜索 T1059 应包含 T1059 及其子技术
      const ids = res.data.map((t) => t.techniqueId);
      expect(ids.some((id) => id.startsWith('T1059'))).toBe(true);
    });

    it('降级: 无匹配关键词返回空数组', async () => {
      mockedGet.mockRejectedValueOnce(new Error('502'));
      const res = await searchAttackTechniques('zzz_no_match_xxx');
      expect(res.code).toBe(200);
      expect(res.data).toEqual([]);
    });
  });

  /* ==================== 狩猎规则 ==================== */

  /* ===== 8. listHuntingRules ===== */
  describe('listHuntingRules', () => {
    it('happy path: 后端成功时返回规则列表', async () => {
      const list = mockHuntingRules.slice(0, 2);
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: list });
      const res = await listHuntingRules();
      expect(mockedGet).toHaveBeenCalledWith('/hunting/rules');
      expect(res.code).toBe(200);
      expect(res.data.length).toBe(2);
    });

    it('降级: 请求抛错时回退到 Mock 规则列表', async () => {
      mockedGet.mockRejectedValueOnce(new Error('503'));
      const res = await listHuntingRules();
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
      // Mock 规则应包含 SIGMA 与 YARA 两种类型
      const types = res.data.map((r) => r.type);
      expect(types).toContain(HuntingRuleType.SIGMA);
      expect(types).toContain(HuntingRuleType.YARA);
    });
  });

  /* ===== 9. getHuntingRule ===== */
  describe('getHuntingRule', () => {
    it('happy path: 后端成功时返回规则详情', async () => {
      const rule = getMockHuntingRuleById('rule-sigma-001') as HuntingRule;
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: rule });
      const res = await getHuntingRule('rule-sigma-001');
      expect(mockedGet).toHaveBeenCalledWith('/hunting/rules/rule-sigma-001');
      expect(res.code).toBe(200);
      expect(res.data.id).toBe('rule-sigma-001');
      expect(res.data.type).toBe(HuntingRuleType.SIGMA);
      expect(res.data.attackTechniqueIds).toContain('T1059.001');
    });

    it('降级: 请求抛错时回退到 Mock 规则', async () => {
      mockedGet.mockRejectedValueOnce(new Error('404'));
      const res = await getHuntingRule('rule-yara-001');
      expect(res.code).toBe(200);
      expect(res.data.id).toBe('rule-yara-001');
      expect(res.data.type).toBe(HuntingRuleType.YARA);
    });

    it('降级: 未知 id 时回退到 mockHuntingRules[0]', async () => {
      mockedGet.mockRejectedValueOnce(new Error('500'));
      const res = await getHuntingRule('not-exist');
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
      expect(res.data.id).toBe(mockHuntingRules[0].id);
    });
  });

  /* ===== 10. importSigmaRule ===== */
  describe('importSigmaRule', () => {
    it('happy path: 后端成功时返回规则ID', async () => {
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: 'rule-sigma-new' });
      const payload = { content: 'title: Test Rule\ndetection:\n  condition: selection' };
      const res = await importSigmaRule(payload);
      expect(mockedPost).toHaveBeenCalledWith(
        '/hunting/rules/sigma/import',
        payload as unknown as Record<string, unknown>,
      );
      expect(res.code).toBe(200);
      expect(res.data).toBe('rule-sigma-new');
    });

    it('降级: 请求抛错时返回 Mock 规则ID（前缀 rule-sigma-）', async () => {
      mockedPost.mockRejectedValueOnce(new Error('500'));
      const res = await importSigmaRule({ content: 'title: Sigma Test' });
      expect(res.code).toBe(200);
      expect(typeof res.data).toBe('string');
      expect(res.data.startsWith('rule-sigma-')).toBe(true);
    });
  });

  /* ===== 11. importYaraRule ===== */
  describe('importYaraRule', () => {
    it('happy path: 后端成功时返回规则ID', async () => {
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: 'rule-yara-new' });
      const payload = { content: 'rule Test { condition: true }' };
      const res = await importYaraRule(payload);
      expect(mockedPost).toHaveBeenCalledWith(
        '/hunting/rules/yara/import',
        payload as unknown as Record<string, unknown>,
      );
      expect(res.code).toBe(200);
      expect(res.data).toBe('rule-yara-new');
    });

    it('降级: 请求抛错时返回 Mock 规则ID（前缀 rule-yara-）', async () => {
      mockedPost.mockRejectedValueOnce(new Error('502'));
      const res = await importYaraRule({ content: 'rule MyRule { strings: $a = "test" condition: $a }' });
      expect(res.code).toBe(200);
      expect(typeof res.data).toBe('string');
      expect(res.data.startsWith('rule-yara-')).toBe(true);
    });
  });

  /* ===== 12. testHuntingRule ===== */
  describe('testHuntingRule', () => {
    it('happy path: 后端成功时返回测试结果', async () => {
      const result: RuleTestResult = {
        matched: true,
        matchCount: 1,
        details: '命中',
        costMs: 100,
        ruleId: 'rule-sigma-001',
        ruleName: 'Test',
        fileId: 'f001',
      };
      mockedPost.mockResolvedValueOnce({ code: 200, message: 'ok', data: result });
      const res = await testHuntingRule('rule-sigma-001', 'f001');
      expect(mockedPost).toHaveBeenCalledWith(
        '/hunting/rules/rule-sigma-001/test',
        undefined,
        { params: { fileId: 'f001' } },
      );
      expect(res.code).toBe(200);
      expect(res.data.matched).toBe(true);
    });

    it('降级: 请求抛错时回退到 Mock 测试结果（偶数 fileId 命中）', async () => {
      mockedPost.mockRejectedValueOnce(new Error('timeout'));
      const res = await testHuntingRule('rule-sigma-001', 'f002');
      expect(res.code).toBe(200);
      expect(res.data.matched).toBe(true);
      expect(res.data.matchCount).toBe(1);
      expect(res.data.details).toContain('命中');
    });

    it('降级: 请求抛错时回退到 Mock 测试结果（奇数 fileId 未命中）', async () => {
      mockedPost.mockRejectedValueOnce(new Error('500'));
      const res = await testHuntingRule('rule-sigma-001', 'f001');
      expect(res.code).toBe(200);
      expect(res.data.matched).toBe(false);
      expect(res.data.matchCount).toBe(0);
    });
  });

  /* ===== 13. getHuntingRuleStats ===== */
  describe('getHuntingRuleStats', () => {
    it('happy path: 后端成功时返回统计信息', async () => {
      const stats: RuleStats = {
        ruleId: 'rule-sigma-001',
        matchCount: 14,
        testCount: 28,
        version: 2,
        enabled: true,
        lastMatchTime: '2026-07-26T08:00:00Z',
      };
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: stats });
      const res = await getHuntingRuleStats('rule-sigma-001');
      expect(mockedGet).toHaveBeenCalledWith('/hunting/rules/rule-sigma-001/stats');
      expect(res.code).toBe(200);
      expect(res.data.matchCount).toBe(14);
      expect(res.data.testCount).toBe(28);
    });

    it('降级: 请求抛错时回退到 Mock 统计信息', async () => {
      mockedGet.mockRejectedValueOnce(new Error('503'));
      const res = await getHuntingRuleStats('rule-yara-001');
      expect(res.code).toBe(200);
      // rule-yara-001 的 matchCount=23, testCount=50
      expect(res.data.ruleId).toBe('rule-yara-001');
      expect(res.data.matchCount).toBe(23);
      expect(res.data.testCount).toBe(50);
    });
  });

  /* ===== 14. findRulesByTechnique ===== */
  describe('findRulesByTechnique', () => {
    it('happy path: 后端成功时返回关联规则列表', async () => {
      const rules = mockRulesByTechnique('T1059.001');
      mockedGet.mockResolvedValueOnce({ code: 200, message: 'ok', data: rules });
      const res = await findRulesByTechnique('T1059.001');
      expect(mockedGet).toHaveBeenCalledWith('/hunting/rules/by-technique/T1059.001');
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
      res.data.forEach((r) =>
        expect(r.attackTechniqueIds).toContain('T1059.001'),
      );
    });

    it('降级: 请求抛错时回退到 Mock 反向查询', async () => {
      mockedGet.mockRejectedValueOnce(new Error('404'));
      const res = await findRulesByTechnique('T1071');
      expect(res.code).toBe(200);
      expect(res.data.length).toBeGreaterThan(0);
      res.data.forEach((r) =>
        expect(r.attackTechniqueIds).toContain('T1071'),
      );
    });

    it('降级: 无关联规则的技术返回空数组', async () => {
      mockedGet.mockRejectedValueOnce(new Error('500'));
      const res = await findRulesByTechnique('T9999');
      expect(res.code).toBe(200);
      expect(res.data).toEqual([]);
    });
  });

  /* ===== 综合断言 ===== */
  it('所有降级路径返回 code 200 且 data 非空，保证页面不阻塞', async () => {
    mockedPost.mockRejectedValue(new Error('all down'));
    mockedGet.mockRejectedValue(new Error('all down'));
    const [
      created, listed, detail, validated, matrix, byTactic, searched,
      rules, rule, sigma, yara, tested, stats, related,
    ] = await Promise.all([
      createHypothesis({ description: 'x', techniqueId: 'T1059', userId: 1 }),
      listHypotheses(),
      getHypothesis('hyp-001'),
      validateHypothesis('hyp-001'),
      getAttackMatrix(),
      getTechniquesByTactic('execution'),
      searchAttackTechniques('T1059'),
      listHuntingRules(),
      getHuntingRule('rule-sigma-001'),
      importSigmaRule({ content: 'title: x' }),
      importYaraRule({ content: 'rule x {}' }),
      testHuntingRule('rule-sigma-001', 'f002'),
      getHuntingRuleStats('rule-sigma-001'),
      findRulesByTechnique('T1059.001'),
    ]);
    [
      created, listed, detail, validated, matrix, byTactic, searched,
      rules, rule, sigma, yara, tested, stats, related,
    ].forEach((res) => {
      expect(res.code).toBe(200);
      expect(res.data).toBeTruthy();
    });
    // 类型断言：确保降级返回结构正确
    expect(typeof created.data).toBe('string');
    expect(Array.isArray(listed.data)).toBe(true);
    expect((detail.data as HypothesisDetail).id).toBeTruthy();
    expect((validated.data as HuntingHypothesis).status).toBeTruthy();
    expect((matrix.data as AttackMatrix).tactics.length).toBe(14);
    expect(Array.isArray(byTactic.data)).toBe(true);
    expect(Array.isArray(searched.data)).toBe(true);
    expect(Array.isArray(rules.data)).toBe(true);
    expect((rule.data as HuntingRule).id).toBeTruthy();
    expect(typeof sigma.data).toBe('string');
    expect(typeof yara.data).toBe('string');
    expect((tested.data as RuleTestResult).matched !== undefined).toBe(true);
    expect((stats.data as RuleStats).matchCount).toBeGreaterThanOrEqual(0);
    expect(Array.isArray(related.data)).toBe(true);
  });
});
