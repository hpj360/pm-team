/**
 * Mock 数据 - 脱敏规则
 * - 内置 8 条常见脱敏规则（手机号 / 身份证 / 邮箱 / IP / 银行卡 / 姓名 / 地址 / CVE）
 * - 提供 generateMockRules 生成器，支持动态生成测试数据
 * - 提供 applyMockRule 用于规则测试预览（本地正则替换）
 */
import type {
  DataMaskingRule,
  DataMaskingRulePayload,
  DataMaskingTestResult,
} from '@/types';

/** Mock 脱敏规则列表（覆盖常见敏感字段） */
export const mockDataMaskingRules: DataMaskingRule[] = [
  {
    id: 1,
    ruleName: '手机号脱敏',
    pattern: '(1[3-9])\\d{4}(\\d{4})',
    replacement: '$1****$2',
    classificationLevel: 'INTERNAL',
    enabled: true,
    description: '保留前 3 位和后 4 位，中间 4 位用 * 替换',
    createdAt: '2026-07-01T10:00:00Z',
    updatedAt: '2026-07-01T10:00:00Z',
  },
  {
    id: 2,
    ruleName: '身份证号脱敏',
    pattern: '([1-9]\\d{5})\\d{8}(\\d{4})',
    replacement: '$1********$2',
    classificationLevel: 'CONFIDENTIAL',
    enabled: true,
    description: '保留前 6 位和后 4 位，中间 8 位用 * 替换',
    createdAt: '2026-07-01T10:05:00Z',
    updatedAt: '2026-07-01T10:05:00Z',
  },
  {
    id: 3,
    ruleName: '邮箱地址脱敏',
    pattern: '([a-zA-Z0-9._%+-]{1,3})[a-zA-Z0-9._%+-]*(@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})',
    replacement: '$1***$2',
    classificationLevel: 'INTERNAL',
    enabled: true,
    description: '保留邮箱名前 3 位，其余用 * 替换',
    createdAt: '2026-07-01T10:10:00Z',
    updatedAt: '2026-07-01T10:10:00Z',
  },
  {
    id: 4,
    ruleName: 'IPv4 地址脱敏',
    pattern: '\\b(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\.(\\d{1,3})\\b',
    replacement: '$1.*.*.$2',
    classificationLevel: 'CONFIDENTIAL',
    enabled: true,
    description: '保留首尾两段，中间两段用 * 替换',
    createdAt: '2026-07-01T10:15:00Z',
    updatedAt: '2026-07-01T10:15:00Z',
  },
  {
    id: 5,
    ruleName: '银行卡号脱敏',
    pattern: '(\\d{4})\\d{8,12}(\\d{4})',
    replacement: '$1********$2',
    classificationLevel: 'SECRET',
    enabled: true,
    description: '保留前 4 位和后 4 位，中间用 * 替换',
    createdAt: '2026-07-01T10:20:00Z',
    updatedAt: '2026-07-01T10:20:00Z',
  },
  {
    id: 6,
    ruleName: '中文姓名脱敏',
    pattern: '([\\u4e00-\\u9fa5])[\\u4e00-\\u9fa5]{1,3}',
    replacement: '$1**',
    classificationLevel: 'CONFIDENTIAL',
    enabled: false,
    description: '保留姓氏，名字用 * 替换（已禁用，误判率高）',
    createdAt: '2026-07-01T10:25:00Z',
    updatedAt: '2026-07-02T08:00:00Z',
  },
  {
    id: 7,
    ruleName: '家庭住址脱敏',
    pattern: '([\\u4e00-\\u9fa5]{2,6}(?:省|市|区|县|镇|乡))([\\u4e00-\\u9fa5\\d]+)',
    replacement: '$1****',
    classificationLevel: 'SECRET',
    enabled: true,
    description: '保留省市区县，详细地址用 * 替换',
    createdAt: '2026-07-01T10:30:00Z',
    updatedAt: '2026-07-01T10:30:00Z',
  },
  {
    id: 8,
    ruleName: 'CVE 编号脱敏',
    pattern: 'CVE-(\\d{4})-(\\d+)',
    replacement: 'CVE-$1-****',
    classificationLevel: 'PUBLIC',
    enabled: true,
    description: '保留 CVE 与年份，编号用 * 替换',
    createdAt: '2026-07-01T10:35:00Z',
    updatedAt: '2026-07-01T10:35:00Z',
  },
];

/** 内存可变副本（供 create / update / delete / toggle 操作使用） */
let inMemoryRules: DataMaskingRule[] = mockDataMaskingRules.map((r) => ({ ...r }));

/** 自增 ID 计数器 */
let nextRuleId = 1000;

/** 按 ID 获取规则 */
export function getRuleById(id: number): DataMaskingRule | undefined {
  return inMemoryRules.find((r) => r.id === id);
}

/**
 * 生成 Mock 规则列表
 * @param count 生成数量，默认 8
 * @returns 生成的规则数组
 */
export function generateMockRules(count: number = 8): DataMaskingRule[] {
  const templates = mockDataMaskingRules;
  const result: DataMaskingRule[] = [];
  for (let i = 0; i < count; i++) {
    const tpl = templates[i % templates.length];
    result.push({
      ...tpl,
      id: 2000 + i,
      ruleName: `${tpl.ruleName}_${i + 1}`,
      createdAt: new Date(Date.now() - i * 86400000).toISOString(),
      updatedAt: new Date().toISOString(),
    });
  }
  return result;
}

/** 获取全部 Mock 规则（内存副本） */
export function getMockRules(): DataMaskingRule[] {
  return inMemoryRules.map((r) => ({ ...r }));
}

/** 按 ID 列表过滤 */
export function filterMockRules(ids?: number[]): DataMaskingRule[] {
  if (!ids || ids.length === 0) return getMockRules();
  return inMemoryRules.filter((r) => ids.includes(r.id)).map((r) => ({ ...r }));
}

/** 新增 Mock 规则（写入内存） */
export function mockCreateRule(payload: DataMaskingRulePayload): DataMaskingRule {
  const now = new Date().toISOString();
  const newRule: DataMaskingRule = {
    id: nextRuleId++,
    ruleName: payload.ruleName,
    pattern: payload.pattern,
    replacement: payload.replacement,
    classificationLevel: payload.classificationLevel,
    enabled: payload.enabled,
    description: payload.description,
    createdAt: now,
    updatedAt: now,
  };
  inMemoryRules.push(newRule);
  return { ...newRule };
}

/** 更新 Mock 规则（写入内存） */
export function mockUpdateRule(
  id: number,
  payload: DataMaskingRulePayload,
): DataMaskingRule | null {
  const idx = inMemoryRules.findIndex((r) => r.id === id);
  if (idx < 0) return null;
  const updated: DataMaskingRule = {
    ...inMemoryRules[idx],
    ruleName: payload.ruleName,
    pattern: payload.pattern,
    replacement: payload.replacement,
    classificationLevel: payload.classificationLevel,
    enabled: payload.enabled,
    description: payload.description,
    updatedAt: new Date().toISOString(),
  };
  inMemoryRules[idx] = updated;
  return { ...updated };
}

/** 切换启用状态（写入内存） */
export function mockToggleRule(id: number): DataMaskingRule | null {
  const idx = inMemoryRules.findIndex((r) => r.id === id);
  if (idx < 0) return null;
  inMemoryRules[idx] = {
    ...inMemoryRules[idx],
    enabled: !inMemoryRules[idx].enabled,
    updatedAt: new Date().toISOString(),
  };
  return { ...inMemoryRules[idx] };
}

/** 删除 Mock 规则（写入内存） */
export function mockDeleteRule(id: number): boolean {
  const before = inMemoryRules.length;
  inMemoryRules = inMemoryRules.filter((r) => r.id !== id);
  return inMemoryRules.length < before;
}

/** 重置内存规则为初始状态（测试用） */
export function resetMockRules(): void {
  inMemoryRules = mockDataMaskingRules.map((r) => ({ ...r }));
  nextRuleId = 1000;
}

/**
 * 本地应用脱敏规则（用于规则测试预览）
 * - 当指定 ruleId 时仅应用该规则，否则按顺序应用所有启用规则
 * - 正则编译失败时跳过该规则并记录
 * - 直接使用 replacement 字符串（支持 $1/$2 等捕获组引用，由 String.replace 原生解析）
 */
export function applyMockRule(
  input: string,
  ruleId?: number,
): DataMaskingTestResult {
  const start = Date.now();
  let output = input;
  const matchedRuleIds: number[] = [];
  const matchedRuleNames: string[] = [];
  let matchCount = 0;

  let rulesToApply = inMemoryRules.filter((r) => r.enabled);
  if (ruleId !== undefined) {
    rulesToApply = rulesToApply.filter((r) => r.id === ruleId);
  }

  for (const rule of rulesToApply) {
    try {
      const regex = new RegExp(rule.pattern, 'g');
      // 先统计命中次数
      const before = output;
      // 直接传 replacement 字符串，$1 / $2 等捕获组引用由 replace 自动解析
      output = output.replace(regex, rule.replacement);
      if (output !== before) {
        // 简单估算命中次数：统计替换前匹配数
        const countRegex = new RegExp(rule.pattern, 'g');
        const matches = before.match(countRegex);
        const localCount = matches ? matches.length : 1;
        matchedRuleIds.push(rule.id);
        matchedRuleNames.push(rule.ruleName);
        matchCount += localCount;
      }
    } catch {
      // 正则编译失败，跳过该规则
    }
  }

  return {
    input,
    output,
    matchedRuleIds,
    matchedRuleNames,
    matchCount,
    costMs: Date.now() - start,
  };
}

export default {
  mockDataMaskingRules,
  generateMockRules,
  getMockRules,
  getRuleById,
  filterMockRules,
  mockCreateRule,
  mockUpdateRule,
  mockToggleRule,
  mockDeleteRule,
  resetMockRules,
  applyMockRule,
};
