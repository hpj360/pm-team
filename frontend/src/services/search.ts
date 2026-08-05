/**
 * 搜索相关 API 服务
 * - 关键词 / 语义 / 模糊 / 正则 四种搜索模式
 * - 搜索建议、历史
 * - 后端不可达时降级到 Mock 数据（基于 mock/file.ts 的文件列表）
 */
import { get, post, del } from '@/utils/request';
import type {
  SearchParams,
  SearchResult,
  SearchSuggestion,
  SearchHistory,
  SearchTemplate,
  SaveSearchTemplatePayload,
  ApiResponse,
} from '@/types';
import { SearchType } from '@/types';
import {
  mockSearch,
  getMockSearchSuggestions,
  mockSearchTemplates,
} from '@/mock/search';

/**
 * 搜索文件（支持四种模式）
 */
export async function searchFiles(params: SearchParams): Promise<ApiResponse<SearchResult>> {
  try {
    return await post<SearchResult>('/search', params as unknown as Record<string, unknown>);
  } catch {
    // Mock 降级
    return {
      code: 200,
      message: 'success',
      data: mockSearch(params),
    };
  }
}

/**
 * 获取搜索建议
 */
export async function getSearchSuggestions(
  keyword: string,
): Promise<ApiResponse<SearchSuggestion[]>> {
  try {
    return await get<SearchSuggestion[]>('/search/suggestions', { keyword });
  } catch {
    return {
      code: 200,
      message: 'success',
      data: getMockSearchSuggestions(keyword),
    };
  }
}

/**
 * 获取搜索历史
 */
export async function getSearchHistory(): Promise<ApiResponse<SearchHistory[]>> {
  try {
    return await get<SearchHistory[]>('/search/history');
  } catch {
    return { code: 200, message: 'success', data: [] };
  }
}

/**
 * 清除搜索历史
 */
export async function clearSearchHistory(): Promise<ApiResponse<void>> {
  try {
    return await post<void>('/search/history/clear');
  } catch {
    return { code: 200, message: 'success', data: undefined as unknown as void };
  }
}

/**
 * 删除单条搜索历史
 */
export async function deleteSearchHistory(id: string): Promise<ApiResponse<void>> {
  try {
    return await post<void>(`/search/history/${id}/delete`);
  } catch {
    return { code: 200, message: 'success', data: undefined as unknown as void };
  }
}

/**
 * 高级搜索（带 filters）
 */
export async function advancedSearch(
  params: SearchParams,
): Promise<ApiResponse<SearchResult>> {
  try {
    return await post<SearchResult>('/search/advanced', params as unknown as Record<string, unknown>);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: mockSearch(params),
    };
  }
}

/**
 * 语义搜索（独立端点，转换为统一 SearchParams 调用 Mock）
 */
export async function semanticSearch(params: {
  query: string;
  topK?: number;
  threshold?: number;
}): Promise<ApiResponse<SearchResult>> {
  try {
    return await post<SearchResult>('/search/semantic', params as unknown as Record<string, unknown>);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: mockSearch({
        keyword: params.query,
        type: SearchType.SEMANTIC,
        page: 1,
        pageSize: params.topK ?? 20,
        threshold: params.threshold,
        topK: params.topK,
        highlight: true,
      }),
    };
  }
}

/**
 * 相似文件搜索
 */
export async function findSimilarFiles(
  fileId: string,
  params?: { topK?: number },
): Promise<ApiResponse<SearchResult>> {
  try {
    return await get<SearchResult>(`/search/similar/${fileId}`, params as Record<string, unknown> | undefined);
  } catch {
    // Mock：返回空结果（无相似文件数据源）
    return {
      code: 200,
      message: 'success',
      data: {
        items: [],
        total: 0,
        page: 1,
        pageSize: params?.topK ?? 10,
        cost: 30,
        aggregations: [],
      },
    };
  }
}

/**
 * 内存中的模板列表（API 不可用时降级使用，保证会话内一致性）
 */
let inMemoryTemplates: SearchTemplate[] = [...mockSearchTemplates];

/** 生成当前时间字符串（YYYY-MM-DD HH:mm:ss） */
function nowString(): string {
  return new Date().toISOString().replace('T', ' ').slice(0, 19);
}

/** 根据请求构造一个 Mock 模板对象 */
function createMockTemplate(payload: SaveSearchTemplatePayload): SearchTemplate {
  const now = nowString();
  return {
    id: Date.now(),
    name: payload.name,
    paramsJson: payload.paramsJson,
    createdAt: now,
    updatedAt: now,
  };
}

/**
 * 获取搜索模板列表
 */
export async function fetchSearchTemplates(): Promise<SearchTemplate[]> {
  try {
    const res = await get<SearchTemplate[]>('/search/templates');
    if (res.code === 200 || res.code === 0) {
      return res.data;
    }
    return [...inMemoryTemplates];
  } catch {
    // Mock 降级
    return [...inMemoryTemplates];
  }
}

/**
 * 保存搜索模板
 */
export async function saveSearchTemplate(
  payload: SaveSearchTemplatePayload,
): Promise<SearchTemplate> {
  try {
    const res = await post<SearchTemplate>(
      '/search/templates',
      payload as unknown as Record<string, unknown>,
    );
    if (res.code === 200 || res.code === 0) {
      return res.data;
    }
    // 降级：写入内存模板列表
    const tpl = createMockTemplate(payload);
    inMemoryTemplates = [...inMemoryTemplates, tpl];
    return tpl;
  } catch {
    // Mock 降级
    const tpl = createMockTemplate(payload);
    inMemoryTemplates = [...inMemoryTemplates, tpl];
    return tpl;
  }
}

/**
 * 删除搜索模板
 */
export async function deleteSearchTemplate(id: number): Promise<void> {
  try {
    await del<void>(`/search/templates/${id}`);
  } catch {
    // Mock 降级：从内存列表移除
    inMemoryTemplates = inMemoryTemplates.filter((t) => t.id !== id);
  }
}
