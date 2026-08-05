/**
 * YARA 规则与匹配结果类型
 */

/**
 * YARA 规则严重程度
 */
export type YaraSeverity = 'info' | 'low' | 'medium' | 'high' | 'critical';

/**
 * YARA 规则
 */
export interface YaraRule {
  id: string;
  name: string;
  description: string;
  author: string;
  severity: YaraSeverity;
  tags: string[];
  /** 规则源码 */
  source: string;
  /** 是否启用 */
  enabled: boolean;
  /** 命中次数 */
  matchCount: number;
  createTime: string;
  updateTime: string;
}

/**
 * YARA 匹配字符串
 */
export interface YaraMatchString {
  /** 匹配到的字符串 */
  value: string;
  /** 在文件中的偏移 */
  offset: number;
  /** 匹配长度 */
  length: number;
  /** 字符串标识 */
  identifier?: string;
}

/**
 * YARA 匹配结果
 */
export interface YaraMatchResult {
  /** 规则 ID */
  ruleId: string;
  /** 规则名称 */
  ruleName: string;
  /** 严重程度 */
  severity: YaraSeverity;
  /** 规则描述 */
  description: string;
  /** 规则标签 */
  tags: string[];
  /** 匹配到的字符串列表 */
  matchedStrings: YaraMatchString[];
  /** 匹配时间 */
  matchedAt: string;
}

/**
 * YARA 扫描结果
 */
export interface YaraScanResult {
  fileId: string;
  fileName: string;
  /** 总扫描规则数 */
  totalRules: number;
  /** 命中规则数 */
  matchedRules: number;
  /** 匹配详情列表 */
  matches: YaraMatchResult[];
  /** 扫描耗时(ms) */
  costMs: number;
  scannedAt: string;
}
