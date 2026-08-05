/**
 * 搜索状态管理
 * - 支持四种搜索模式（KEYWORD / SEMANTIC / FUZZY / REGEX）
 * - 维护聚合 facets、搜索耗时、激活的 facet 过滤条件
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { SearchType, AggregationField } from '@/types';
import type {
  SearchParams,
  SearchResultItem,
  SearchHistory,
  AggregationResult,
  SearchFilter,
  BooleanCondition,
  SearchTemplate,
  SearchHistoryItem,
} from '@/types';
import {
  fetchSearchTemplates as fetchSearchTemplatesService,
  saveSearchTemplate as saveSearchTemplateService,
  deleteSearchTemplate as deleteSearchTemplateService,
} from '@/services';
import { fetchEnabledTags as fetchEnabledTagsService } from '@/services/tag';
import type { TagDict } from '@/types/tag';

/** 激活的 facet 过滤条件 key（`${field}__${value}`） */
export type FacetFilterKey = string;

/** 搜索历史 localStorage key */
const SEARCH_HISTORY_STORAGE_KEY = 'file_search_history';
/** 搜索历史最大保留条数 */
const MAX_SEARCH_HISTORY = 20;

/** 从 localStorage 读取搜索历史（兼容隐私模式） */
function loadSearchHistoryFromStorage(): SearchHistoryItem[] {
  try {
    if (typeof localStorage === 'undefined') return [];
    const raw = localStorage.getItem(SEARCH_HISTORY_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as SearchHistoryItem[]) : [];
  } catch {
    return [];
  }
}

/** 将搜索历史写入 localStorage（兼容隐私模式） */
function saveSearchHistoryToStorage(items: SearchHistoryItem[]): void {
  try {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(items));
  } catch {
    // 隐私模式或存储已满，忽略错误
  }
}

/** 从 localStorage 移除搜索历史 */
function removeSearchHistoryFromStorage(): void {
  try {
    if (typeof localStorage === 'undefined') return;
    localStorage.removeItem(SEARCH_HISTORY_STORAGE_KEY);
  } catch {
    // 忽略
  }
}

interface SearchState {
  // 状态
  keyword: string;
  searchType: SearchType;
  results: SearchResultItem[];
  total: number;
  loading: boolean;
  /** 本次搜索耗时(ms) */
  cost: number;
  /** 聚合 facets */
  aggregations: AggregationResult[];
  /** 激活的 facet 过滤条件集合 */
  activeFilters: SearchFilter[];
  params: SearchParams;
  history: SearchHistory[];
  /** 布尔条件列表 */
  booleanConditions: BooleanCondition[];
  /** 是否启用布尔查询模式 */
  booleanMode: boolean;
  /** 二次检索关键词 */
  refineQuery: string;
  /** 上次搜索结果文件ID列表（用于二次检索范围） */
  lastResultIds: string[];
  /** 是否处于二次检索状态 */
  isRefining: boolean;
  /** 搜索模板列表 */
  searchTemplates: SearchTemplate[];
  /** 搜索历史列表（localStorage 持久化） */
  searchHistory: SearchHistoryItem[];
  /** 保存搜索 Modal 可见性 */
  saveTemplateModalVisible: boolean;
  /** 选中的标签ID列表（AND 筛选） */
  selectedTagIds: number[];
  /** 可选标签列表（从API加载） */
  availableTags: TagDict[];

  // 操作
  setKeyword: (keyword: string) => void;
  setSearchType: (type: SearchType) => void;
  setResults: (results: SearchResultItem[]) => void;
  setTotal: (total: number) => void;
  setLoading: (loading: boolean) => void;
  setCost: (cost: number) => void;
  setAggregations: (aggregations: AggregationResult[]) => void;
  setParams: (params: Partial<SearchParams>) => void;
  /** 切换 facet 过滤条件（存在则移除，不存在则添加） */
  toggleFilter: (field: string, value: string) => void;
  /** 批量设置过滤条件（替换） */
  setFilters: (filters: SearchFilter[]) => void;
  /** 清空所有 facet 过滤条件 */
  clearFilters: () => void;
  addHistory: (history: SearchHistory) => void;
  clearHistory: () => void;
  removeHistory: (id: string) => void;
  /** 添加布尔条件 */
  addBooleanCondition: (condition: BooleanCondition) => void;
  /** 更新布尔条件 */
  updateBooleanCondition: (id: string, partial: Partial<BooleanCondition>) => void;
  /** 删除布尔条件 */
  removeBooleanCondition: (id: string) => void;
  /** 清空布尔条件 */
  clearBooleanConditions: () => void;
  /** 切换布尔模式 */
  setBooleanMode: (enabled: boolean) => void;
  /** 设置二次检索关键词 */
  setRefineQuery: (query: string) => void;
  /** 设置上次结果ID列表 */
  setLastResultIds: (ids: string[]) => void;
  /** 设置二次检索状态 */
  setIsRefining: (refining: boolean) => void;
  /** 退出二次检索 */
  exitRefine: () => void;
  /** 加载搜索模板 */
  loadSearchTemplates: () => Promise<void>;
  /** 保存搜索模板（序列化当前搜索条件） */
  saveSearchTemplate: (name: string) => Promise<void>;
  /** 删除搜索模板 */
  deleteSearchTemplate: (id: number) => Promise<void>;
  /** 应用搜索模板（恢复搜索条件到 store） */
  applySearchTemplate: (template: SearchTemplate) => void;
  /** 显示/隐藏保存 Modal */
  setSaveTemplateModalVisible: (visible: boolean) => void;
  /** 添加搜索历史（localStorage 持久化） */
  addSearchHistory: (keyword: string, searchMode: string) => void;
  /** 清空搜索历史 */
  clearSearchHistory: () => void;
  /** 设置选中的标签ID列表 */
  setSelectedTagIds: (ids: number[]) => void;
  /** 切换标签选中状态（存在则移除，不存在则添加） */
  toggleSelectedTagId: (tagId: number) => void;
  /** 加载可用标签列表 */
  loadAvailableTags: () => Promise<void>;
  reset: () => void;
}

// 默认搜索参数
const defaultParams: SearchParams = {
  keyword: '',
  type: SearchType.KEYWORD,
  page: 1,
  pageSize: 20,
  highlight: true,
};

export const useSearchStore = create<SearchState>()(
  persist(
    (set, get) => ({
      // 初始状态
      keyword: '',
      searchType: SearchType.KEYWORD,
      results: [],
      total: 0,
      loading: false,
      cost: 0,
      aggregations: [],
      activeFilters: [],
      params: defaultParams,
      history: [],
      booleanConditions: [],
      booleanMode: false,
      refineQuery: '',
      lastResultIds: [],
      isRefining: false,
      searchTemplates: [],
      searchHistory: loadSearchHistoryFromStorage(),
      saveTemplateModalVisible: false,
      selectedTagIds: [],
      availableTags: [],

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

      // 设置耗时
      setCost: (cost) => set({ cost }),

      // 设置聚合结果
      setAggregations: (aggregations) => set({ aggregations }),

      // 设置搜索参数
      setParams: (params) =>
        set((state) => ({
          params: { ...state.params, ...params },
        })),

      // 切换 facet 过滤条件
      toggleFilter: (field, value) =>
        set((state) => {
          const exists = state.activeFilters.find(
            (f) => f.field === field && f.value === value,
          );
          let nextFilters: SearchFilter[];
          if (exists) {
            nextFilters = state.activeFilters.filter(
              (f) => !(f.field === field && f.value === value),
            );
          } else {
            // tags 字段使用 in 操作符，允许同时多选；其他字段使用 eq
            const op =
              field === AggregationField.TAGS
                ? ('in' as const)
                : ('eq' as const);
            // 对于 tags 的 in 操作，需要合并到一个 filter 里
            if (field === AggregationField.TAGS) {
              const existingTags = state.activeFilters.find(
                (f) => f.field === AggregationField.TAGS,
              );
              if (existingTags && Array.isArray(existingTags.value)) {
                const merged: string[] = [
                  ...(existingTags.value as string[]),
                  value,
                ];
                nextFilters = [
                  ...state.activeFilters.filter(
                    (f) => f.field !== AggregationField.TAGS,
                  ),
                  { field: AggregationField.TAGS, operator: 'in', value: merged },
                ];
              } else {
                nextFilters = [
                  ...state.activeFilters,
                  { field: AggregationField.TAGS, operator: 'in', value: [value] },
                ];
              }
            } else {
              // 同一非 tags 字段只保留最新一个（单选行为）
              nextFilters = [
                ...state.activeFilters.filter((f) => f.field !== field),
                { field, operator: op, value },
              ];
            }
          }
          return { activeFilters: nextFilters };
        }),

      // 批量设置过滤条件
      setFilters: (filters) => set({ activeFilters: filters }),

      // 清空过滤条件（同时清空标签筛选）
      clearFilters: () => set({ activeFilters: [], selectedTagIds: [] }),

      // 添加搜索历史
      addHistory: (history) =>
        set((state) => {
          // 最多保留 20 条历史记录，且去重（同关键词 + 同类型只保留最新一条）
          const deduped = state.history.filter(
            (h) => !(h.keyword === history.keyword && h.type === history.type),
          );
          const newHistory = [history, ...deduped].slice(0, 20);
          return { history: newHistory };
        }),

      // 清空搜索历史
      clearHistory: () => set({ history: [] }),

      // 删除单条搜索历史
      removeHistory: (id) =>
        set((state) => ({
          history: state.history.filter((item) => item.id !== id),
        })),

      // 添加布尔条件
      addBooleanCondition: (condition) =>
        set((state) => ({
          booleanConditions: [...state.booleanConditions, condition],
        })),

      // 更新布尔条件
      updateBooleanCondition: (id, partial) =>
        set((state) => ({
          booleanConditions: state.booleanConditions.map((c) =>
            c.id === id ? { ...c, ...partial } : c,
          ),
        })),

      // 删除布尔条件
      removeBooleanCondition: (id) =>
        set((state) => ({
          booleanConditions: state.booleanConditions.filter((c) => c.id !== id),
        })),

      // 清空布尔条件
      clearBooleanConditions: () => set({ booleanConditions: [] }),

      // 切换布尔模式（关闭时清空条件）
      setBooleanMode: (enabled) =>
        set(
          enabled
            ? { booleanMode: true }
            : { booleanMode: false, booleanConditions: [] },
        ),

      // 设置二次检索关键词
      setRefineQuery: (query) => set({ refineQuery: query }),

      // 设置上次结果ID列表
      setLastResultIds: (ids) => set({ lastResultIds: ids }),

      // 设置二次检索状态
      setIsRefining: (refining) => set({ isRefining: refining }),

      // 退出二次检索（清空关键词与状态，但保留 lastResultIds 以便再次进入）
      exitRefine: () =>
        set({ isRefining: false, refineQuery: '' }),

      // 加载搜索模板列表
      loadSearchTemplates: async () => {
        try {
          const templates = await fetchSearchTemplatesService();
          set({ searchTemplates: templates });
        } catch {
          // 忽略加载失败，保持空列表
        }
      },

      // 保存搜索模板（序列化当前搜索条件）
      saveSearchTemplate: async (name) => {
        const state = get();
        const paramsJson = JSON.stringify({
          keyword: state.keyword,
          searchMode: state.searchType,
          filters: state.activeFilters,
          booleanConditions: state.booleanConditions,
          booleanMode: state.booleanMode,
        });
        try {
          await saveSearchTemplateService({ name, paramsJson });
          // 保存后刷新模板列表
          const templates = await fetchSearchTemplatesService();
          set({ searchTemplates: templates });
        } catch {
          // 忽略保存失败
        }
      },

      // 删除搜索模板
      deleteSearchTemplate: async (id) => {
        try {
          await deleteSearchTemplateService(id);
          set((s) => ({
            searchTemplates: s.searchTemplates.filter((t) => t.id !== id),
          }));
        } catch {
          // 忽略删除失败
        }
      },

      // 应用搜索模板（恢复搜索条件到 store）
      applySearchTemplate: (template) => {
        try {
          const params = JSON.parse(template.paramsJson) as {
            keyword?: string;
            searchMode?: string;
            filters?: SearchFilter[];
            booleanConditions?: BooleanCondition[];
            booleanMode?: boolean;
          };
          set((state) => ({
            keyword: params.keyword ?? '',
            searchType:
              (params.searchMode as SearchType) ?? state.searchType,
            activeFilters: params.filters ?? [],
            booleanConditions: params.booleanConditions ?? [],
            booleanMode: params.booleanMode ?? false,
            // 应用模板时退出二次检索状态
            isRefining: false,
            refineQuery: '',
          }));
        } catch {
          // JSON 解析失败，忽略
        }
      },

      // 显示/隐藏保存 Modal
      setSaveTemplateModalVisible: (visible) =>
        set({ saveTemplateModalVisible: visible }),

      // 添加搜索历史（localStorage 持久化）
      addSearchHistory: (keyword, searchMode) => {
        const trimmed = (keyword ?? '').trim();
        const displayName = trimmed
          ? trimmed
          : `[${searchMode}] 无关键词`;
        const item: SearchHistoryItem = {
          id: Date.now().toString(),
          keyword: trimmed,
          searchMode,
          timestamp: Date.now(),
          displayName,
        };
        const next = [
          item,
          ...get().searchHistory.filter(
            (h) => !(h.keyword === trimmed && h.searchMode === searchMode),
          ),
        ].slice(0, MAX_SEARCH_HISTORY);
        set({ searchHistory: next });
        saveSearchHistoryToStorage(next);
      },

      // 清空搜索历史
      clearSearchHistory: () => {
        set({ searchHistory: [] });
        removeSearchHistoryFromStorage();
      },

      // 设置选中的标签ID列表
      setSelectedTagIds: (ids) => set({ selectedTagIds: ids }),

      // 切换标签选中状态
      toggleSelectedTagId: (tagId) =>
        set((state) => ({
          selectedTagIds: state.selectedTagIds.includes(tagId)
            ? state.selectedTagIds.filter((id) => id !== tagId)
            : [...state.selectedTagIds, tagId],
        })),

      // 加载可用标签列表
      loadAvailableTags: async () => {
        try {
          const tags = await fetchEnabledTagsService();
          set({ availableTags: tags });
        } catch {
          // 忽略加载失败，保持空列表
        }
      },

      // 重置状态
      reset: () =>
        set({
          keyword: '',
          searchType: SearchType.KEYWORD,
          results: [],
          total: 0,
          loading: false,
          cost: 0,
          aggregations: [],
          activeFilters: [],
          params: defaultParams,
          booleanConditions: [],
          booleanMode: false,
          refineQuery: '',
          lastResultIds: [],
          isRefining: false,
          selectedTagIds: [],
        }),
    }),
    {
      name: 'search-storage', // localStorage key
      partialize: (state) => ({
        history: state.history,
      }),
    },
  ),
);
