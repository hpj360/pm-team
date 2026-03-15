/**
 * 搜索相关Hook
 */

import { useCallback } from 'react';
import { message } from 'antd';
import { useSearchStore } from '@/stores';
import { searchFiles, getSearchSuggestions } from '@/services';
import type { SearchParams, SearchType } from '@/types';

export function useSearch() {
  const {
    keyword,
    searchType,
    results,
    total,
    loading,
    params,
    history,
    setKeyword,
    setSearchType,
    setResults,
    setTotal,
    setLoading,
    setParams,
    addHistory,
    clearHistory,
    removeHistory,
  } = useSearchStore();

  // 执行搜索
  const search = useCallback(async (searchParams?: Partial<SearchParams>) => {
    const mergedParams: SearchParams = {
      ...params,
      ...searchParams,
      keyword: searchParams?.keyword ?? keyword,
      type: searchParams?.type ?? searchType,
    };

    if (!mergedParams.keyword.trim()) {
      message.warning('请输入搜索关键词');
      return;
    }

    setLoading(true);
    try {
      const res = await searchFiles(mergedParams);
      if (res.code === 200 || res.code === 0) {
        setResults(res.data.items);
        setTotal(res.data.total);
        setParams(mergedParams);

        // 添加搜索历史
        addHistory({
          id: Date.now().toString(),
          keyword: mergedParams.keyword,
          type: mergedParams.type,
          resultCount: res.data.total,
          searchTime: new Date().toISOString(),
        });
      }
    } catch (error) {
      message.error('搜索失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, [keyword, searchType, params, setResults, setTotal, setLoading, setParams, addHistory]);

  // 获取搜索建议
  const fetchSuggestions = useCallback(async (query: string) => {
    if (!query.trim()) return [];

    try {
      const res = await getSearchSuggestions(query);
      if (res.code === 200 || res.code === 0) {
        return res.data;
      }
      return [];
    } catch {
      return [];
    }
  }, []);

  // 切换搜索类型
  const changeSearchType = useCallback((type: SearchType) => {
    setSearchType(type);
    setParams({ type });
  }, [setSearchType, setParams]);

  // 清空搜索结果
  const clearResults = useCallback(() => {
    setResults([]);
    setTotal(0);
    setKeyword('');
  }, [setResults, setTotal, setKeyword]);

  return {
    keyword,
    searchType,
    results,
    total,
    loading,
    params,
    history,
    search,
    fetchSuggestions,
    changeSearchType,
    setKeyword,
    clearResults,
    clearHistory,
    removeHistory,
  };
}
