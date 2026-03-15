/**
 * 搜索类型枚举
 */
export enum SearchType {
  KEYWORD = 'keyword',     // 关键词搜索
  SEMANTIC = 'semantic',   // 语义搜索
  FUZZY = 'fuzzy',         // 模糊搜索
  REGEX = 'regex',         // 正则搜索
}

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
}

/**
 * 搜索过滤器
 */
export interface SearchFilter {
  field: string;
  operator: 'eq' | 'ne' | 'gt' | 'lt' | 'gte' | 'lte' | 'in' | 'contains';
  value: string | number | string[] | number[];
}

/**
 * 搜索结果项
 */
export interface SearchResultItem {
  id: string;
  fileId: string;
  fileName: string;
  score: number;
  highlights: HighlightInfo[];
  snippet: string;
  metadata: Record<string, unknown>;
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
