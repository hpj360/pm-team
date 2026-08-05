/**
 * 脱敏规则类型定义
 * 对应后端 DataMaskingService 与脱敏规则表
 * 字段：rule_name / pattern / replacement / enabled / classification_level
 */
import type { FileClassification } from './common';

/**
 * 脱敏规则实体
 */
export interface DataMaskingRule {
  /** 规则 ID */
  id: number;
  /** 规则名称 */
  ruleName: string;
  /** 正则匹配模式（用于定位需要脱敏的文本片段） */
  pattern: string;
  /** 替换文本（如 *** 或 $1***$2） */
  replacement: string;
  /**
   * 适用密级：仅对不低于该密级的文件生效
   * 缺省表示对所有密级生效
   */
  classificationLevel?: FileClassification | string;
  /** 是否启用 */
  enabled: boolean;
  /** 规则描述 */
  description?: string;
  /** 创建时间 */
  createdAt?: string;
  /** 更新时间 */
  updatedAt?: string;
}

/**
 * 创建/更新脱敏规则的请求负载
 */
export interface DataMaskingRulePayload {
  ruleName: string;
  pattern: string;
  replacement: string;
  classificationLevel?: FileClassification | string;
  enabled: boolean;
  description?: string;
}

/**
 * 规则测试请求参数
 */
export interface DataMaskingTestParams {
  /** 样例输入文本 */
  input: string;
  /** 规则 ID（可选，缺省时对所有启用规则生效） */
  ruleId?: number;
}

/**
 * 规则测试响应结果
 */
export interface DataMaskingTestResult {
  /** 原始输入文本 */
  input: string;
  /** 脱敏后输出文本 */
  output: string;
  /** 命中的规则 ID 列表 */
  matchedRuleIds: number[];
  /** 命中的规则名称列表 */
  matchedRuleNames: string[];
  /** 总命中次数 */
  matchCount: number;
  /** 测试耗时（ms） */
  costMs?: number;
}

/**
 * 脱敏规则密级选项（用于 Select）
 */
export const DataMaskingClassificationOptions: {
  label: string;
  value: string;
}[] = [
  { label: '全部密级', value: 'PUBLIC' },
  { label: '内部及以上', value: 'INTERNAL' },
  { label: '秘密及以上', value: 'CONFIDENTIAL' },
  { label: '机密', value: 'SECRET' },
];
