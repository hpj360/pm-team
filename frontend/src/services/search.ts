/**
 * 搜索相关API服务
 */

import { get, post } from '@/utils/request';
import type {
  SearchParams,
  SearchResult,
  SearchSuggestion,
  SearchHistory,
  ApiResponse,
} from '@/types';

/**
 * 搜索文件
 */
export function searchFiles(params: SearchParams): Promise<ApiResponse<SearchResult>> {
  return post<SearchResult>('/search', params);
}

/**
 * 获取搜索建议
 */
export function getSearchSuggestions(keyword: string): Promise<ApiResponse<SearchSuggestion[]>> {
  return get<SearchSuggestion[]>('/search/suggestions', { keyword });
}

/**
 * 获取搜索历史
 */
export function getSearchHistory(): Promise<ApiResponse<SearchHistory[]>> {
  return get<SearchHistory[]>('/search/history');
}

/**
 * 清除搜索历史
 */
export function clearSearchHistory(): Promise<ApiResponse<void>> {
  return post<void>('/search/history/clear');
}

/**
 * 删除单条搜索历史
 */
export function deleteSearchHistory(id: string): Promise<ApiResponse<void>> {
  return post<void>(`/search/history/${id}/delete`);
}

/**
 * 高级搜索
 */
export function advancedSearch(params: SearchParams): Promise<ApiResponse<SearchResult>> {
  return post<SearchResult>('/search/advanced', params);
}

/**
 * 语义搜索
 */
export function semanticSearch(params: {
  query: string;
  topK?: number;
  threshold?: number;
}): Promise<ApiResponse<SearchResult>> {
  return post<SearchResult>('/search/semantic', params);
}

/**
 * 相似文件搜索
 */
export function findSimilarFiles(
  fileId: string,
  params?: { topK?: number }
): Promise<ApiResponse<SearchResult>> {
  return get<SearchResult>(`/search/similar/${fileId}`, params);
}
