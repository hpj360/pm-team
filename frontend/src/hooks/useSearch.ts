/**
 * 搜索相关 Hook
 * - 封装四种搜索模式调用
 * - 维护聚合 facets、耗时、facet 过滤
 * - 支持分页
 * - 支持布尔组合检索（AND / OR / NOT）
 * - 支持二次检索（在已有结果中搜索）
 */

import { useCallback } from 'react';
import { message } from 'antd';
import { useSearchStore } from '@/stores';
import { searchFiles, getSearchSuggestions } from '@/services';
import { SearchType } from '@/types';
import type { SearchParams, BooleanCondition, SearchTemplate } from '@/types';

export function useSearch() {
  const {
    keyword,
    searchType,
    results,
    total,
    loading,
    cost,
    aggregations,
    activeFilters,
    params,
    history,
    booleanConditions,
    booleanMode,
    refineQuery,
    lastResultIds,
    isRefining,
    // 搜索模板与历史
    searchTemplates,
    searchHistory,
    saveTemplateModalVisible,
    // 标签筛选
    selectedTagIds,
    availableTags,
    setKeyword,
    setSearchType,
    setResults,
    setTotal,
    setCost,
    setAggregations,
    setParams,
    toggleFilter,
    setFilters,
    clearFilters,
    clearHistory,
    removeHistory,
    addBooleanCondition,
    updateBooleanCondition,
    removeBooleanCondition,
    clearBooleanConditions,
    setBooleanMode,
    setRefineQuery,
    setIsRefining,
    exitRefine,
    loadSearchTemplates,
    saveSearchTemplate,
    deleteSearchTemplate,
    applySearchTemplate,
    setSaveTemplateModalVisible,
    clearSearchHistory,
    setSelectedTagIds,
    toggleSelectedTagId,
    loadAvailableTags,
  } = useSearchStore();

  /**
   * 执行搜索
   * @param searchParams 覆盖参数（keyword / type / page / pageSize / threshold / topK）
   * @param silent 静默模式（不弹消息），用于 facet 切换后重新搜索
   *
   * 注意：activeFilters / booleanConditions / refineQuery 通过 useSearchStore.getState() 实时读取，
   * 以避免状态切换后立即调用 search 时闭包捕获陈旧值的问题。
   */
  const search = useCallback(
    async (
      searchParams?: Partial<SearchParams>,
      silent: boolean = false,
    ) => {
      // 实时读取最新 store 状态（避免 facet 切换后的闭包陈旧问题）
      const latestState = useSearchStore.getState();
      const latestKeyword = searchParams?.keyword ?? latestState.keyword;
      const latestType = searchParams?.type ?? latestState.searchType;
      const latestFilters = latestState.activeFilters;
      const latestBooleanMode = latestState.booleanMode;
      const latestBooleanConditions = latestState.booleanConditions;
      const latestIsRefining = latestState.isRefining;
      const latestRefineQuery = latestState.refineQuery;
      const latestLastResultIds = latestState.lastResultIds;
      const latestSelectedTagIds = latestState.selectedTagIds;

      const mergedParams: SearchParams = {
        ...latestState.params,
        ...searchParams,
        keyword: latestKeyword,
        type: latestType,
        highlight: true,
      };

      // 空关键词且无 facet 过滤、无布尔条件、无标签筛选时不强制搜索
      const hasFilters = latestFilters.length > 0;
      const hasBooleanConditions =
        latestBooleanMode && latestBooleanConditions.length > 0;
      const hasTagFilters = latestSelectedTagIds.length > 0;
      if (
        !mergedParams.keyword.trim() &&
        !hasFilters &&
        !hasBooleanConditions &&
        !hasTagFilters
      ) {
        if (!silent) message.warning('请输入搜索关键词');
        return;
      }

      // 合并 facet 过滤条件
      if (latestFilters.length > 0) {
        mergedParams.filters = latestFilters;
      } else {
        delete mergedParams.filters;
      }

      // 附加布尔组合条件
      if (hasBooleanConditions) {
        mergedParams.booleanConditions = latestBooleanConditions;
      } else {
        delete mergedParams.booleanConditions;
      }

      // 附加二次检索条件（在已有结果中搜索）
      if (latestIsRefining && latestRefineQuery.trim()) {
        mergedParams.refineQuery = latestRefineQuery;
        mergedParams.refineFileIds = latestLastResultIds;
      } else {
        delete mergedParams.refineQuery;
        delete mergedParams.refineFileIds;
      }

      // 附加标签筛选条件（AND 逻辑）
      if (latestSelectedTagIds.length > 0) {
        mergedParams.tagIds = latestSelectedTagIds;
      } else {
        delete mergedParams.tagIds;
      }

      latestState.setLoading(true);
      try {
        const res = await searchFiles(mergedParams);
        if (res.code === 200 || res.code === 0) {
          latestState.setResults(res.data.items);
          latestState.setTotal(res.data.total);
          latestState.setCost(res.data.cost);
          latestState.setAggregations(res.data.aggregations ?? []);
          latestState.setParams(mergedParams);

          // 保存结果项的 fileId 列表，供二次检索使用（仅在非二次检索模式下更新基线）
          if (!latestIsRefining) {
            const ids = res.data.items.map((it) => it.fileId);
            latestState.setLastResultIds(ids);
          }

          // 仅在非空关键词时记录历史（二次检索不记录历史）
          if (mergedParams.keyword.trim() && !latestIsRefining) {
            latestState.addHistory({
              id: Date.now().toString(),
              keyword: mergedParams.keyword,
              type: mergedParams.type,
              resultCount: res.data.total,
              searchTime: new Date().toISOString(),
            });
            // 同步记录到 localStorage 搜索历史
            latestState.addSearchHistory(mergedParams.keyword, mergedParams.type);
          }
        }
      } catch {
        if (!silent) message.error('搜索失败，请稍后重试');
      } finally {
        latestState.setLoading(false);
      }
    },
    [],
  );

  /**
   * 切换 facet 过滤条件后重新搜索
   * -  facet 切换不重置页码到第 1 页会出问题（页码可能越界），因此重置到第 1 页
   */
  const toggleFilterAndSearch = useCallback(
    async (field: string, value: string) => {
      toggleFilter(field, value);
      // 重置到第 1 页
      await search({ page: 1 }, true);
    },
    [toggleFilter, search],
  );

  /** 清空所有 facet 过滤并重新搜索 */
  const clearFiltersAndSearch = useCallback(async () => {
    clearFilters();
    await search({ page: 1 }, true);
  }, [clearFilters, search]);

  /** 切换标签筛选后重新搜索 */
  const toggleTagFilter = useCallback(
    async (tagId: number) => {
      toggleSelectedTagId(tagId);
      await search({ page: 1 }, true);
    },
    [toggleSelectedTagId, search],
  );

  /** 切换分页 */
  const changePage = useCallback(
    async (page: number, pageSize: number) => {
      await search({ page, pageSize }, true);
    },
    [search],
  );

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
  const changeSearchType = useCallback(
    (type: SearchType) => {
      setSearchType(type);
      setParams({ type, page: 1 });
    },
    [setSearchType, setParams],
  );

  // 清空搜索结果
  const clearResults = useCallback(() => {
    setResults([]);
    setTotal(0);
    setCost(0);
    setAggregations([]);
    setKeyword('');
    clearFilters();
  }, [setResults, setTotal, setCost, setAggregations, setKeyword, clearFilters]);

  /**
   * 执行二次检索（在当前结果中搜索）
   * 设置二次检索状态后调用 search，复用上次结果的 fileId 列表
   */
  const executeRefine = useCallback(
    async (query: string) => {
      const trimmed = query.trim();
      if (!trimmed) {
        message.warning('请输入二次检索关键词');
        return;
      }
      setRefineQuery(trimmed);
      setIsRefining(true);
      // 二次检索从第 1 页开始
      await search({ page: 1 }, true);
    },
    [setRefineQuery, setIsRefining, search],
  );

  /**
   * 退出二次检索，恢复完整结果
   * 清空二次检索状态后重新执行一次搜索（基于当前 keyword / filters）
   */
  const exitRefineSearch = useCallback(async () => {
    exitRefine();
    await search({ page: 1 }, true);
  }, [exitRefine, search]);

  /** 添加布尔条件 */
  const addCondition = useCallback(
    (condition: BooleanCondition) => {
      addBooleanCondition(condition);
    },
    [addBooleanCondition],
  );

  /** 更新布尔条件 */
  const updateCondition = useCallback(
    (id: string, partial: Partial<BooleanCondition>) => {
      updateBooleanCondition(id, partial);
    },
    [updateBooleanCondition],
  );

  /** 删除布尔条件 */
  const removeCondition = useCallback(
    (id: string) => {
      removeBooleanCondition(id);
    },
    [removeBooleanCondition],
  );

  /** 切换布尔模式 */
  const toggleBooleanMode = useCallback(() => {
    setBooleanMode(!useSearchStore.getState().booleanMode);
  }, [setBooleanMode]);

  /** 清空所有布尔条件 */
  const clearConditions = useCallback(() => {
    clearBooleanConditions();
  }, [clearBooleanConditions]);

  /** 加载搜索模板列表 */
  const loadTemplates = useCallback(() => {
    return loadSearchTemplates();
  }, [loadSearchTemplates]);

  /** 保存当前搜索条件为模板 */
  const saveTemplate = useCallback(
    (name: string) => {
      return saveSearchTemplate(name);
    },
    [saveSearchTemplate],
  );

  /** 应用搜索模板：恢复搜索条件并执行搜索 */
  const applyTemplate = useCallback(
    async (template: SearchTemplate) => {
      applySearchTemplate(template);
      const state = useSearchStore.getState();
      await search(
        {
          keyword: state.keyword,
          type: state.searchType,
          page: 1,
        },
        true,
      );
    },
    [applySearchTemplate, search],
  );

  /** 删除搜索模板 */
  const deleteTemplate = useCallback(
    (id: number) => {
      return deleteSearchTemplate(id);
    },
    [deleteSearchTemplate],
  );

  /** 显示保存模板 Modal */
  const showSaveModal = useCallback(() => {
    setSaveTemplateModalVisible(true);
  }, [setSaveTemplateModalVisible]);

  /** 隐藏保存模板 Modal */
  const hideSaveModal = useCallback(() => {
    setSaveTemplateModalVisible(false);
  }, [setSaveTemplateModalVisible]);

  return {
    // 状态
    keyword,
    searchType,
    results,
    total,
    loading,
    cost,
    aggregations,
    activeFilters,
    params,
    history,
    // 布尔检索状态
    booleanConditions,
    booleanMode,
    // 二次检索状态
    refineQuery,
    lastResultIds,
    isRefining,
    // 操作
    search,
    toggleFilter: toggleFilterAndSearch,
    clearFilters: clearFiltersAndSearch,
    changePage,
    fetchSuggestions,
    changeSearchType,
    setKeyword,
    clearResults,
    clearHistory,
    removeHistory,
    // 布尔检索操作
    addCondition,
    updateCondition,
    removeCondition,
    toggleBooleanMode,
    clearConditions,
    // 二次检索操作
    executeRefine,
    exitRefine: exitRefineSearch,
    setRefineQuery,
    // 直接暴露原始 setter（供需要精细控制的场景）
    setFilters,
    // 搜索模板与历史
    searchTemplates,
    searchHistory,
    saveTemplateModalVisible,
    loadTemplates,
    saveTemplate,
    applyTemplate,
    deleteTemplate,
    showSaveModal,
    hideSaveModal,
    clearSearchHistory,
    // 标签筛选
    selectedTagIds,
    availableTags,
    setSelectedTagIds,
    toggleTagFilter,
    loadAvailableTags,
  };
}
