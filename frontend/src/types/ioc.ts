/**
 * IOC（威胁情报）类型定义
 * 扩展自 analyze.ts 中的 IocInfo，增加来源文件、出现次数等业务字段
 */
import type { IocType } from './analyze';

/**
 * IOC 详情
 */
export interface IocItem {
  id: string;
  type: IocType;
  value: string;
  /** 置信度 0-1 */
  confidence: number;
  /** 来源文件 ID */
  sourceFileId: string;
  /** 来源文件名 */
  sourceFileName: string;
  /** 标签 */
  tags: string[];
  /** 首次出现时间 */
  firstSeen: string;
  /** 最近出现时间 */
  lastSeen: string;
  /** 出现次数 */
  occurrences: number;
  /** 是否已确认恶意 */
  malicious: boolean;
  /** 威胁分类（如 C2、Phishing、Malware 等） */
  threatCategory?: string;
  /** 关联情报源 */
  intelligenceSources?: string[];
}

/**
 * IOC 查询参数
 */
export interface IocListParams {
  keyword?: string;
  type?: IocType;
  malicious?: boolean;
  threatCategory?: string;
  startTime?: string;
  endTime?: string;
  sortBy?: 'occurrences' | 'firstSeen' | 'lastSeen' | 'confidence';
  order?: 'asc' | 'desc';
  page: number;
  pageSize: number;
}

/**
 * IOC 聚合统计
 */
export interface IocAggregation {
  /** 按类型分布 */
  typeDistribution: Array<{ type: IocType; count: number }>;
  /** 按威胁分类分布 */
  categoryDistribution: Array<{ category: string; count: number }>;
  /** 高置信度 IOC 数 */
  highConfidenceCount: number;
  /** 恶意 IOC 数 */
  maliciousCount: number;
  /** 总数 */
  total: number;
}
