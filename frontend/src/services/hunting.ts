/**
 * 威胁狩猎 API 服务（V5.3）
 * 对应后端 analyze-service（端口 8084）HuntingController
 * 接口前缀：/api/hunting
 *
 * 降级策略：后端请求失败时回退到 Mock 数据（ATT&CK 矩阵/假设/规则）
 */
import { get, post } from '@/utils/request';
import type {
  ApiResponse,
  AttackMatrix,
  AttackTechnique,
  HypothesisDetail,
  HuntingHypothesis,
  HuntingRule,
  RuleTestResult,
  RuleStats,
  CreateHypothesisPayload,
  ImportRulePayload,
} from '@/types';
import {
  mockAttackMatrix,
  mockHypotheses,
  mockHuntingRules,
  getMockHypothesisById,
  mockValidateHypothesis,
  mockCreateHypothesis,
  mockTechniquesByTactic,
  mockSearchTechniques,
  getMockHuntingRuleById,
  mockTestRule,
  mockRuleStats,
  mockImportSigmaRule,
  mockImportYaraRule,
  mockRulesByTechnique,
} from '@/mock';

/* ==================== 狩猎假设 ==================== */

/**
 * 创建狩猎假设
 * POST /api/hunting/hypothesis
 *
 * @param payload 假设描述 / 技术 ID / 创建人
 * @returns 假设ID
 */
export async function createHypothesis(
  payload: CreateHypothesisPayload,
): Promise<ApiResponse<string>> {
  try {
    return await post<string>('/hunting/hypothesis', payload as unknown as Record<string, unknown>);
  } catch {
    const id = mockCreateHypothesis(payload.description, payload.techniqueId, payload.userId);
    return { code: 200, message: 'success', data: id };
  }
}

/**
 * 获取狩猎假设列表
 * GET /api/hunting/hypothesis
 *
 * @returns 假设 VO 列表
 */
export async function listHypotheses(): Promise<ApiResponse<HypothesisDetail[]>> {
  try {
    return await get<HypothesisDetail[]>('/hunting/hypothesis');
  } catch {
    return { code: 200, message: 'success', data: mockHypotheses };
  }
}

/**
 * 获取狩猎假设详情
 * GET /api/hunting/hypothesis/{id}
 *
 * @param id 假设ID
 * @returns 假设 VO
 */
export async function getHypothesis(
  id: string,
): Promise<ApiResponse<HypothesisDetail>> {
  try {
    return await get<HypothesisDetail>(`/hunting/hypothesis/${id}`);
  } catch {
    const data = getMockHypothesisById(id) ?? mockHypotheses[0];
    return { code: 200, message: 'success', data };
  }
}

/**
 * 触发狩猎假设验证
 * POST /api/hunting/hypothesis/{id}/validate
 *
 * @param id 假设ID
 * @returns 验证后的假设实体
 */
export async function validateHypothesis(
  id: string,
): Promise<ApiResponse<HuntingHypothesis>> {
  try {
    return await post<HuntingHypothesis>(`/hunting/hypothesis/${id}/validate`);
  } catch {
    return { code: 200, message: 'success', data: mockValidateHypothesis(id) };
  }
}

/* ==================== ATT&CK 矩阵 ==================== */

/**
 * 获取 ATT&CK 矩阵（含战术列表 + 技术列表 + 统计）
 * GET /api/hunting/attack-matrix
 *
 * @returns 矩阵数据
 */
export async function getAttackMatrix(): Promise<ApiResponse<AttackMatrix>> {
  try {
    return await get<AttackMatrix>('/hunting/attack-matrix');
  } catch {
    return { code: 200, message: 'success', data: mockAttackMatrix };
  }
}

/**
 * 按战术查询 ATT&CK 技术
 * GET /api/hunting/attack-matrix/tactic/{tactic}
 *
 * @param tactic 战术 ID
 * @returns 技术列表
 */
export async function getTechniquesByTactic(
  tactic: string,
): Promise<ApiResponse<AttackTechnique[]>> {
  try {
    return await get<AttackTechnique[]>(`/hunting/attack-matrix/tactic/${tactic}`);
  } catch {
    return { code: 200, message: 'success', data: mockTechniquesByTactic(tactic) };
  }
}

/**
 * 关键词搜索 ATT&CK 技术
 * GET /api/hunting/attack-matrix/search?keyword=xxx
 *
 * @param keyword 关键词
 * @returns 技术列表
 */
export async function searchAttackTechniques(
  keyword: string,
): Promise<ApiResponse<AttackTechnique[]>> {
  try {
    return await get<AttackTechnique[]>('/hunting/attack-matrix/search', { keyword });
  } catch {
    return { code: 200, message: 'success', data: mockSearchTechniques(keyword) };
  }
}

/* ==================== 狩猎规则 ==================== */

/**
 * 获取狩猎规则列表
 * GET /api/hunting/rules
 *
 * @returns 规则列表
 */
export async function listHuntingRules(): Promise<ApiResponse<HuntingRule[]>> {
  try {
    return await get<HuntingRule[]>('/hunting/rules');
  } catch {
    return { code: 200, message: 'success', data: mockHuntingRules };
  }
}

/**
 * 获取狩猎规则详情
 * GET /api/hunting/rules/{id}
 *
 * @param id 规则ID
 * @returns 规则实体
 */
export async function getHuntingRule(
  id: string,
): Promise<ApiResponse<HuntingRule>> {
  try {
    return await get<HuntingRule>(`/hunting/rules/${id}`);
  } catch {
    const data = getMockHuntingRuleById(id) ?? mockHuntingRules[0];
    return { code: 200, message: 'success', data };
  }
}

/**
 * 导入 Sigma 规则
 * POST /api/hunting/rules/sigma/import
 *
 * @param payload 规则内容
 * @returns 规则ID
 */
export async function importSigmaRule(
  payload: ImportRulePayload,
): Promise<ApiResponse<string>> {
  try {
    return await post<string>('/hunting/rules/sigma/import', payload as unknown as Record<string, unknown>);
  } catch {
    const id = mockImportSigmaRule(payload.content);
    return { code: 200, message: 'success', data: id };
  }
}

/**
 * 导入 YARA 规则
 * POST /api/hunting/rules/yara/import
 *
 * @param payload 规则内容
 * @returns 规则ID
 */
export async function importYaraRule(
  payload: ImportRulePayload,
): Promise<ApiResponse<string>> {
  try {
    return await post<string>('/hunting/rules/yara/import', payload as unknown as Record<string, unknown>);
  } catch {
    const id = mockImportYaraRule(payload.content);
    return { code: 200, message: 'success', data: id };
  }
}

/**
 * 测试规则命中
 * POST /api/hunting/rules/{id}/test?fileId=xxx
 *
 * @param id 规则ID
 * @param fileId 文件ID
 * @returns 测试结果
 */
export async function testHuntingRule(
  id: string,
  fileId: string,
): Promise<ApiResponse<RuleTestResult>> {
  try {
    return await post<RuleTestResult>(`/hunting/rules/${id}/test`, undefined, {
      params: { fileId },
    });
  } catch {
    return { code: 200, message: 'success', data: mockTestRule(id, fileId) };
  }
}

/**
 * 获取规则命中统计
 * GET /api/hunting/rules/{id}/stats
 *
 * @param id 规则ID
 * @returns 统计信息
 */
export async function getHuntingRuleStats(
  id: string,
): Promise<ApiResponse<RuleStats>> {
  try {
    return await get<RuleStats>(`/hunting/rules/${id}/stats`);
  } catch {
    return { code: 200, message: 'success', data: mockRuleStats(id) };
  }
}

/**
 * 按 ATT&CK 技术反向查询规则
 * GET /api/hunting/rules/by-technique/{techniqueId}
 *
 * @param techniqueId 技术 ID
 * @returns 关联规则列表
 */
export async function findRulesByTechnique(
  techniqueId: string,
): Promise<ApiResponse<HuntingRule[]>> {
  try {
    return await get<HuntingRule[]>(`/hunting/rules/by-technique/${techniqueId}`);
  } catch {
    return { code: 200, message: 'success', data: mockRulesByTechnique(techniqueId) };
  }
}
