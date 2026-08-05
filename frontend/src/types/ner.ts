/**
 * NER（命名实体识别）类型定义
 */

/**
 * NER 实体类型
 */
export enum NerEntityType {
  IP = 'IP',
  DOMAIN = 'DOMAIN',
  URL = 'URL',
  EMAIL = 'EMAIL',
  HASH = 'HASH',
  CVE = 'CVE',
  FILE_PATH = 'FILE_PATH',
  REGISTRY = 'REGISTRY',
  PERSON = 'PERSON',
  ORGANIZATION = 'ORGANIZATION',
  LOCATION = 'LOCATION',
  MONEY = 'MONEY',
  DATE = 'DATE',
  PHONE = 'PHONE',
  BITCOIN = 'BITCOIN',
}

export const NerEntityTypeLabel: Record<NerEntityType, string> = {
  [NerEntityType.IP]: 'IP 地址',
  [NerEntityType.DOMAIN]: '域名',
  [NerEntityType.URL]: 'URL',
  [NerEntityType.EMAIL]: '邮箱',
  [NerEntityType.HASH]: '哈希',
  [NerEntityType.CVE]: 'CVE 漏洞',
  [NerEntityType.FILE_PATH]: '文件路径',
  [NerEntityType.REGISTRY]: '注册表',
  [NerEntityType.PERSON]: '人名',
  [NerEntityType.ORGANIZATION]: '组织',
  [NerEntityType.LOCATION]: '位置',
  [NerEntityType.MONEY]: '金额',
  [NerEntityType.DATE]: '日期',
  [NerEntityType.PHONE]: '电话',
  [NerEntityType.BITCOIN]: '比特币地址',
};

/**
 * NER 实体
 */
export interface NerEntity {
  id: string;
  type: NerEntityType;
  value: string;
  /** 在文本中的起始位置 */
  start: number;
  /** 在文本中的结束位置 */
  end: number;
  /** 置信度 0-1 */
  confidence: number;
  /** 标准化后的值（如 IP 标准化、域名小写等） */
  normalized?: string;
}

/**
 * NER 识别结果
 */
export interface NerResult {
  fileId: string;
  fileName: string;
  /** 文本总长度 */
  textLength: number;
  /** 实体总数 */
  totalEntities: number;
  /** 按类型分组的实体数 */
  typeDistribution: Array<{ type: NerEntityType; count: number }>;
  /** 实体列表 */
  entities: NerEntity[];
  /** 处理耗时(ms) */
  costMs: number;
  processedAt: string;
}
