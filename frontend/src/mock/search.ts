/**
 * Mock数据 - 检索结果
 */

import type { SearchResultItem, SearchSuggestion, SearchHistory, SearchType } from '@/types';

// 高亮片段模板
const snippetTemplates = [
  '该文件包含可疑的{keyword}代码片段，经分析确认为恶意软件...',
  '在{keyword}攻击活动中发现此文件，关联多个APT组织...',
  '文件中提取到{keyword}相关的配置信息，包含C2服务器地址...',
  '此样本展示了{keyword}行为特征，建议进一步分析...',
  '检测到{keyword}相关的网络通信，目标指向外部服务器...',
];

/**
 * 生成单个Mock搜索结果
 */
export const generateMockSearchResult = (id: string, keyword: string): SearchResultItem => {
  const score = 0.5 + Math.random() * 0.5; // 0.5 - 1.0
  const template = snippetTemplates[Math.floor(Math.random() * snippetTemplates.length)];
  const snippet = template.replace('{keyword}', keyword);

  return {
    id,
    fileId: `f${id}`,
    fileName: `search_result_${id}.bin`,
    score,
    highlights: [
      {
        field: 'content',
        fragments: [`<em>${keyword}</em>相关的分析报告`],
      },
    ],
    snippet,
    metadata: {
      fileType: 'executable',
      size: Math.floor(Math.random() * 10 * 1024 * 1024),
      uploadTime: new Date().toISOString(),
    },
  };
};

/**
 * 生成Mock搜索结果列表
 */
export const generateMockSearchResults = (
  keyword: string,
  count: number = 50
): SearchResultItem[] => {
  return Array.from({ length: count }, (_, index) =>
    generateMockSearchResult(`${(index + 1).toString().padStart(4, '0')}`, keyword)
  );
};

/**
 * 获取Mock搜索结果（分页）
 */
export const getMockSearchResults = (
  keyword: string,
  page: number = 1,
  pageSize: number = 20
): { items: SearchResultItem[]; total: number; cost: number } => {
  const allResults = generateMockSearchResults(keyword, 100);
  const total = allResults.length;
  const start = (page - 1) * pageSize;
  const items = allResults.slice(start, start + pageSize);
  const cost = Math.floor(50 + Math.random() * 200); // 50-250ms

  return { items, total, cost };
};

/**
 * Mock搜索建议
 */
export const mockSearchSuggestions: SearchSuggestion[] = [
  { text: 'malware', score: 0.95, type: 'keyword' },
  { text: 'malware analysis', score: 0.88, type: 'keyword' },
  { text: 'malware sample', score: 0.82, type: 'keyword' },
  { text: 'APT攻击', score: 0.78, type: 'keyword' },
  { text: '钓鱼邮件', score: 0.72, type: 'keyword' },
];

/**
 * 获取Mock搜索建议
 */
export const getMockSearchSuggestions = (keyword: string): SearchSuggestion[] => {
  if (!keyword) return [];
  
  return mockSearchSuggestions
    .filter((s) => s.text.toLowerCase().includes(keyword.toLowerCase()))
    .slice(0, 5);
};

/**
 * Mock搜索历史
 */
export const mockSearchHistory: SearchHistory[] = [
  {
    id: 'h1',
    keyword: 'malware',
    type: SearchType.KEYWORD,
    resultCount: 156,
    searchTime: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
  },
  {
    id: 'h2',
    keyword: 'APT攻击',
    type: SearchType.SEMANTIC,
    resultCount: 89,
    searchTime: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
  },
  {
    id: 'h3',
    keyword: '钓鱼邮件',
    type: SearchType.KEYWORD,
    resultCount: 234,
    searchTime: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
  },
  {
    id: 'h4',
    keyword: '勒索软件',
    type: SearchType.FUZZY,
    resultCount: 67,
    searchTime: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
  },
  {
    id: 'h5',
    keyword: 'C2服务器',
    type: SearchType.KEYWORD,
    resultCount: 45,
    searchTime: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(),
  },
];

export default {
  generateMockSearchResults,
  getMockSearchResults,
  getMockSearchSuggestions,
  mockSearchHistory,
};
