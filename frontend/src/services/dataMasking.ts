/**
 * 脱敏规则相关 API 服务
 * - 规则 CRUD：列表查询 / 创建 / 更新 / 启停 / 删除
 * - 规则测试预览：testRule(input, ruleId?)
 * 后端不可达时降级到 Mock 数据（内存操作）
 */
import { get, post, put, del } from '@/utils/request';
import type {
  ApiResponse,
  DataMaskingRule,
  DataMaskingRulePayload,
  DataMaskingTestResult,
} from '@/types';
import {
  getMockRules,
  mockCreateRule,
  mockUpdateRule,
  mockDeleteRule,
  applyMockRule,
} from '@/mock/dataMasking';

/**
 * 获取脱敏规则列表
 * GET /api/admin/data-masking/rules
 */
export async function listRules(): Promise<ApiResponse<DataMaskingRule[]>> {
  try {
    return await get<DataMaskingRule[]>('/admin/data-masking/rules');
  } catch {
    return {
      code: 200,
      message: 'success',
      data: getMockRules(),
    };
  }
}

/**
 * 创建脱敏规则
 * POST /api/admin/data-masking/rules
 */
export async function createRule(
  payload: DataMaskingRulePayload,
): Promise<ApiResponse<DataMaskingRule>> {
  try {
    return await post<DataMaskingRule>(
      '/admin/data-masking/rules',
      payload as unknown as Record<string, unknown>,
    );
  } catch {
    const data = mockCreateRule(payload);
    return { code: 200, message: 'success', data };
  }
}

/**
 * 更新脱敏规则
 * PUT /api/admin/data-masking/rules/:id
 */
export async function updateRule(
  id: number,
  payload: DataMaskingRulePayload,
): Promise<ApiResponse<DataMaskingRule>> {
  try {
    return await put<DataMaskingRule>(
      `/admin/data-masking/rules/${id}`,
      payload as unknown as Record<string, unknown>,
    );
  } catch {
    const data = mockUpdateRule(id, payload);
    if (!data) {
      return { code: 404, message: '规则不存在', data: undefined as unknown as DataMaskingRule };
    }
    return { code: 200, message: 'success', data };
  }
}

/**
 * 删除脱敏规则
 * DELETE /api/admin/data-masking/rules/:id
 */
export async function deleteRule(id: number): Promise<ApiResponse<void>> {
  try {
    return await del<void>(`/admin/data-masking/rules/${id}`);
  } catch {
    mockDeleteRule(id);
    return { code: 200, message: 'success', data: undefined };
  }
}

/**
 * 切换脱敏规则启用状态
 * PATCH /api/admin/data-masking/rules/:id/toggle
 */
export async function toggleRule(id: number): Promise<ApiResponse<DataMaskingRule>> {
  try {
    return await post<DataMaskingRule>(
      `/admin/data-masking/rules/${id}/toggle`,
      {},
    );
  } catch {
    // Mock 降级：在内存中切换状态后通过更新接口语义返回
    const rules = getMockRules();
    const existing = rules.find((r) => r.id === id);
    if (!existing) {
      return { code: 404, message: '规则不存在', data: undefined as unknown as DataMaskingRule };
    }
    const data = mockUpdateRule(id, {
      ruleName: existing.ruleName,
      pattern: existing.pattern,
      replacement: existing.replacement,
      classificationLevel: existing.classificationLevel,
      enabled: !existing.enabled,
      description: existing.description,
    });
    return { code: 200, message: 'success', data: data as DataMaskingRule };
  }
}

/**
 * 测试脱敏规则（预览脱敏效果）
 * POST /api/admin/data-masking/test
 * body: { input, ruleId? }
 */
export async function testRule(
  input: string,
  ruleId?: number,
): Promise<ApiResponse<DataMaskingTestResult>> {
  try {
    return await post<DataMaskingTestResult>(
      '/admin/data-masking/test',
      { input, ruleId },
    );
  } catch {
    const data = applyMockRule(input, ruleId);
    return { code: 200, message: 'success', data };
  }
}

export default {
  listRules,
  createRule,
  updateRule,
  deleteRule,
  toggleRule,
  testRule,
};
