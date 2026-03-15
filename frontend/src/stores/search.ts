/**
 * 搜索状态管理
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { SearchParams, SearchResultItem, SearchType, SearchHistory } from '@/types';

interface SearchState {
  // 状态
  keyword: string;
  searchType: SearchType;
  results: SearchResultItem[];
  total: number;
  loading: boolean;
  params: SearchParams;
  history: SearchHistory[];
  
  // 操作
  setKeyword: (keyword: string) => void;
  setSearchType: (type: SearchType) => void;
  setResults: (results: SearchResultItem[]) => void;
  setTotal: (total: number) => void;
  setLoading: (loading: boolean) => void;
  setParams: (params: Partial<SearchParams>) => void;
  addHistory: (history: SearchHistory) => void;
  clearHistory: () => void;
  removeHistory: (id: string) => void;
  reset: () => void;
}

// 默认搜索参数
const defaultParams: SearchParams = {
  keyword: '',
  type: SearchType.KEYWORD,
  page: 1,
  pageSize: 20,
};

export const useSearchStore = create<SearchState>()(
  persist(
    (set) => ({
      // 初始状态
      keyword: '',
      searchType: SearchType.KEYWORD,
      results: [],
      total: 0,
      loading: false,
      params: defaultParams,
      history: [],

      // 设置搜索关键词
      setKeyword: (keyword) => set({ keyword }),

      // 设置搜索类型
      setSearchType: (searchType) => set({ searchType }),

      // 设置搜索结果
      setResults: (results) => set({ results }),

      // 设置总数
      setTotal: (total) => set({ total }),

      // 设置加载状态
      setLoading: (loading) => set({ loading }),

      // 设置搜索参数
      setParams: (params) =>
        set((state) => ({
          params: { ...state.params, ...params },
        })),

      // 添加搜索历史
      addHistory: (history) =>
        set((state) => {
          // 最多保留20条历史记录
          const newHistory = [history, ...state.history].slice(0, 20);
          return { history: newHistory };
        }),

      // 清空搜索历史
      clearHistory: () => set({ history: [] }),

      // 删除单条搜索历史
      removeHistory: (id) =>
        set((state) => ({
          history: state.history.filter((item) => item.id !== id),
        })),

      // 重置状态
      reset: () =>
        set({
          keyword: '',
          searchType: SearchType.KEYWORD,
          results: [],
          total: 0,
          loading: false,
          params: defaultParams,
        }),
    }),
    {
      name: 'search-storage', // localStorage key
      partialize: (state) => ({
        history: state.history,
      }),
    }
  )
);
