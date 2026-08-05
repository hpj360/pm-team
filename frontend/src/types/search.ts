/**
 * 搜索类型枚举
 */
export enum SearchType {
  KEYWORD = 'keyword',     // 关键词搜索
  SEMANTIC = 'semantic',  // 语义搜索
  FUZZY = 'fuzzy',         // 模糊搜索
  REGEX = 'regex',         // 正则搜索
}

/**
 * 搜索模式元信息（用于 UI 渲染）
 */
export interface SearchModeMeta {
  type: SearchType;
  label: string;
  description: string;
  placeholder: string;
  /** 是否为多行输入（语义搜索使用 textarea） */
  multiline?: boolean;
  /** 输入提示文案 */
  hint?: string;
}

/** 搜索模式配置表 */
export const SEARCH_MODES: SearchModeMeta[] = [
  {
    type: SearchType.KEYWORD,
    label: '关键词搜索',
    description: '基于精确关键词匹配文件名、标签、描述与内容',
    placeholder: '输入关键词，如 malware、APT、钓鱼邮件',
    hint: '支持空格分隔多关键词（AND 关系）',
  },
  {
    type: SearchType.SEMANTIC,
    label: '语义搜索',
    description: '基于向量的自然语言语义匹配，理解意图',
    placeholder: '描述你要找的内容，如"利用 Office 漏洞的钓鱼附件"',
    multiline: true,
    hint: '输入自然语言描述，系统将返回语义最相近的文件',
  },
  {
    type: SearchType.FUZZY,
    label: '模糊搜索',
    description: '容忍拼写错误的模糊匹配，适合 OCR / 乱码场景',
    placeholder: '输入可能不完整或不准确的关键词',
    hint: '支持 * 与 ? 通配符，最大编辑距离 2',
  },
  {
    type: SearchType.REGEX,
    label: '正则搜索',
    description: '基于正则表达式的精确模式匹配',
    placeholder: '输入正则表达式，如 \\bC2_[a-z0-9]{8}\\b',
    multiline: true,
    hint: '使用标准 ECMAScript 正则语法，注意转义反斜杠',
  },
];

/**
 * 搜索参数
 */
export interface SearchParams {
  keyword: string;
  type: SearchType;
  filters?: SearchFilter[];
  page: number;
  pageSize: number;
  highlight?: boolean;
  /** 语义搜索相似度阈值（0~1），仅 SEMANTIC 模式生效 */
  threshold?: number;
  /** 语义搜索返回数量上限 */
  topK?: number;
  /** 布尔组合条件（与 keyword 配合使用） */
  booleanConditions?: BooleanCondition[];
  /** 二次检索关键词（在已有结果中搜索） */
  refineQuery?: string;
  /** 二次检索文件ID范围 */
  refineFileIds?: string[];
  /** 标签ID列表（AND 筛选） */
  tagIds?: number[];
}

/**
 * 布尔逻辑操作符
 */
export type BooleanLogic = 'AND' | 'OR' | 'NOT';

/**
 * 布尔搜索字段
 */
export type BooleanSearchField = 'fileName' | 'textContent' | 'tags' | 'fileType';

/**
 * 布尔组合条件
 */
export interface BooleanCondition {
  /** 唯一标识（前端管理用） */
  id: string;
  /** 逻辑操作符 */
  logic: BooleanLogic;
  /** 搜索字段 */
  field: BooleanSearchField;
  /** 搜索值 */
  value: string;
}

/**
 * 布尔搜索字段标签
 */
export const BooleanSearchFieldLabel: Record<BooleanSearchField, string> = {
  fileName: '文件名',
  textContent: '文件内容',
  tags: '标签',
  fileType: '文件类型',
};

/**
 * 布尔逻辑操作符标签
 */
export const BooleanLogicLabel: Record<BooleanLogic, string> = {
  AND: '与 (AND)',
  OR: '或 (OR)',
  NOT: '非 (NOT)',
};

/**
 * 搜索过滤器
 */
export interface SearchFilter {
  field: string;
  operator: 'eq' | 'ne' | 'gt' | 'lt' | 'gte' | 'lte' | 'in' | 'contains';
  value: string | number | string[] | number[];
}

/**
 * 聚合字段名常量（与后端契约保持一致）
 */
export const AggregationField = {
  FILE_TYPE: 'fileType',
  TAGS: 'tags',
  SENSITIVITY: 'sensitivity',
  UPLOADER: 'uploader',
  TARGET: 'target',
  UPLOAD_MONTH: 'uploadMonth',
} as const;

export type AggregationFieldName =
  | typeof AggregationField.FILE_TYPE
  | typeof AggregationField.TAGS
  | typeof AggregationField.SENSITIVITY
  | typeof AggregationField.UPLOADER
  | typeof AggregationField.TARGET
  | typeof AggregationField.UPLOAD_MONTH;

/** 聚合字段中文标签 */
export const AggregationFieldLabel: Record<AggregationFieldName, string> = {
  [AggregationField.FILE_TYPE]: '文件类型',
  [AggregationField.TAGS]: '标签',
  [AggregationField.SENSITIVITY]: '敏感等级',
  [AggregationField.UPLOADER]: '上传者',
  [AggregationField.TARGET]: '关联目标',
  [AggregationField.UPLOAD_MONTH]: '上传月份',
};

/**
 * 搜索结果项
 * 富字段为可选，便于后端逐步接入；Mock 与已解析文件会完整返回
 */
export interface SearchResultItem {
  id: string;
  fileId: string;
  fileName: string;
  score: number;
  highlights: HighlightInfo[];
  snippet: string;
  metadata: Record<string, unknown>;
  /** 富字段：文件类型（FileType 枚举值） */
  fileType?: string;
  /** 富字段：文件大小（字节） */
  fileSize?: number;
  /** 富字段：MIME 类型 */
  mimeType?: string;
  /** 富字段：敏感等级 */
  sensitivity?: string;
  /** 富字段：标签列表 */
  tags?: string[];
  /** 富字段：文件标签 VO 列表（含层级与来源） */
  fileTags?: import('./tag').FileTagVO[];
  /** 富字段：上传者 */
  uploaderName?: string;
  /** 富字段：关联目标 */
  targetName?: string;
  /** 富字段：上传时间（ISO） */
  createTime?: string;
  /** 富字段：匹配到的字段列表（用于展示命中位置） */
  matchedFields?: string[];
}

/**
 * 高亮信息
 */
export interface HighlightInfo {
  field: string;
  fragments: string[];
}

/**
 * 搜索结果
 */
export interface SearchResult {
  items: SearchResultItem[];
  total: number;
  page: number;
  pageSize: number;
  cost: number; // 搜索耗时(ms)
  aggregations?: AggregationResult[];
}

/**
 * 聚合结果
 */
export interface AggregationResult {
  field: string;
  buckets: AggregationBucket[];
}

/**
 * 聚合桶
 */
export interface AggregationBucket {
  key: string;
  count: number;
}

/**
 * 搜索建议
 */
export interface SearchSuggestion {
  text: string;
  score: number;
  type: 'keyword' | 'history' | 'correction';
}

/**
 * 搜索历史
 */
export interface SearchHistory {
  id: string;
  keyword: string;
  type: SearchType;
  resultCount: number;
  searchTime: string;
}

/**
 * 搜索模板
 */
export interface SearchTemplate {
  id: number;
  name: string;
  paramsJson: string; // JSON 序列化的搜索条件
  createdAt: string;
  updatedAt: string;
}

/**
 * 保存搜索模板请求
 */
export interface SaveSearchTemplatePayload {
  name: string;
  paramsJson: string;
}

/**
 * 搜索历史记录（localStorage 持久化）
 */
export interface SearchHistoryItem {
  id: string; // 唯一标识（timestamp）
  keyword: string;
  searchMode: string;
  timestamp: number;
  displayName: string; // 用于展示的名称
}
