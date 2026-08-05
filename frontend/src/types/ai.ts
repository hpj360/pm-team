/**
 * AI 分析模块类型定义
 * 对应后端 ai-service（端口 8093）的 6 个端点：
 * - 威胁摘要（生成 / 获取）
 * - 攻击链推理
 * - 自然语言搜索
 * - 报告草稿（生成 / 获取）
 */
import type { SearchResultItem } from './search';

/**
 * 威胁摘要（POST /api/ai/threat-summary/generate / GET /api/ai/threat-summary/{fileId}）
 */
export interface ThreatSummary {
  /** 关联文件 ID */
  fileId: string;
  /** 摘要正文 */
  summary: string;
  /** 关键发现列表 */
  keyFindings: string[];
  /** 使用的模型名称 */
  model: string;
  /** 消耗的 token 数 */
  tokens: number;
  /** 生成时间（ISO 字符串） */
  createdAt: string;
}

/**
 * 单条攻击路径
 */
export interface AttackPath {
  /** 路径名称 */
  name: string;
  /** 路径描述 */
  description: string;
  /** 攻击步骤列表 */
  steps: string[];
}

/**
 * 攻击链推理结果（POST /api/ai/attack-chain/infer）
 */
export interface AttackChain {
  /** 攻击路径列表 */
  attackPaths: AttackPath[];
  /** 整体置信度（0~1） */
  confidence: number;
  /** 推理过程说明 */
  reasoning: string;
}

/**
 * 自然语言搜索结果（POST /api/ai/nlsearch）
 */
export interface NlSearchResult {
  /** 翻译/改写后的查询语句 */
  translatedQuery: string;
  /** 命中的搜索结果列表（复用 SearchResultItem 结构） */
  searchResults: SearchResultItem[];
}

/**
 * 报告草稿（POST /api/ai/report-draft/generate / GET /api/ai/report-draft/{reportId}）
 */
export interface ReportDraft {
  /** 关联报告 ID */
  reportId: string;
  /** 结论正文（Markdown 文本） */
  conclusion: string;
  /** 建议措施列表 */
  recommendations: string[];
  /** 生成时间（ISO 字符串） */
  createdAt: string;
}

/**
 * 生成报告草稿请求载荷
 */
export interface GenerateReportDraftPayload {
  reportId: string;
  /** 统计数据 JSON 字符串 */
  statsJson?: string;
  /** 文件列表 JSON 字符串 */
  fileListJson?: string;
  /** 标签分布 JSON 字符串 */
  tagDistributionJson?: string;
}
