/**
 * 标签相关 API 服务
 * - 标签字典 CRUD：列表查询 / 层级树 / 详情 / 创建 / 更新 / 启停 / 删除
 * - 获取启用的标签字典
 * - 获取文件标签 / 文件打标 / 取消打标
 * - 按标签检索文件
 * 后端不可达时降级到 Mock 数据
 */
import { get, post, put, del, patch } from '@/utils/request';
import type { ApiResponse, TagDict, TagTreeNode, TagDictPayload, FileTagVO } from '@/types';
import {
  mockTagDictList,
  mockTagTree,
  getTagById,
  filterMockTags,
  getMockFileTagsByNumericId,
  getMockFilesByTag,
  mockAddFileTags,
  mockRemoveFileTags,
  parseFileIdToNumber,
} from '@/mock/tag';

/* ===================== 标签字典 CRUD ===================== */

/** 获取标签列表（支持按 layer / category / enabled 过滤） */
export async function fetchTags(params?: {
  layer?: string;
  category?: string;
  enabled?: number;
}): Promise<ApiResponse<TagDict[]>> {
  try {
    return await get<TagDict[]>('/tags', params as unknown as Record<string, unknown>);
  } catch {
    return {
      code: 200,
      message: 'success',
      data: filterMockTags(params),
    };
  }
}

/** 获取标签层级树 */
export async function fetchTagTree(): Promise<ApiResponse<TagTreeNode[]>> {
  try {
    return await get<TagTreeNode[]>('/tags/tree');
  } catch {
    return {
      code: 200,
      message: 'success',
      data: mockTagTree,
    };
  }
}

/** 获取标签详情 */
export async function fetchTagDetail(id: number): Promise<ApiResponse<TagDict>> {
  try {
    return await get<TagDict>(`/tags/${id}`);
  } catch {
    const data = getTagById(id) ?? mockTagDictList[0];
    return { code: 200, message: 'success', data };
  }
}

/** 创建标签 */
export async function createTag(payload: TagDictPayload): Promise<ApiResponse<TagDict>> {
  try {
    return await post<TagDict>('/tags', payload as unknown as Record<string, unknown>);
  } catch {
    const newTag: TagDict = {
      id: Math.floor(Math.random() * 100000) + 1000,
      ...payload,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    return { code: 200, message: 'success', data: newTag };
  }
}

/** 更新标签 */
export async function updateTag(
  id: number,
  payload: TagDictPayload,
): Promise<ApiResponse<TagDict>> {
  try {
    return await put<TagDict>(`/tags/${id}`, payload as unknown as Record<string, unknown>);
  } catch {
    const existing = getTagById(id);
    const data: TagDict = {
      ...(existing ?? { id, createdAt: new Date().toISOString() }),
      ...payload,
      id,
      updatedAt: new Date().toISOString(),
    };
    return { code: 200, message: 'success', data };
  }
}

/** 启用/禁用标签 */
export async function toggleTag(id: number): Promise<ApiResponse<void>> {
  try {
    return await patch<void>(`/tags/${id}/toggle`);
  } catch {
    return { code: 200, message: 'success', data: undefined };
  }
}

/** 删除标签 */
export async function deleteTag(id: number): Promise<ApiResponse<void>> {
  try {
    return await del<void>(`/tags/${id}`);
  } catch {
    return { code: 200, message: 'success', data: undefined };
  }
}

/**
 * 获取启用的标签字典列表
 * GET /api/tags?enabled=1
 */
export async function fetchEnabledTags(): Promise<TagDict[]> {
  try {
    const res = await get<TagDict[]>('/tags', { enabled: 1 });
    if (res.code === 200 || res.code === 0) {
      return res.data;
    }
    return [...mockTagDictList];
  } catch {
    return [...mockTagDictList];
  }
}

/**
 * 获取文件标签
 * GET /api/tags/files/{fileId}
 * @param fileId 文件 ID（数字）
 */
export async function fetchFileTags(fileId: number): Promise<FileTagVO[]> {
  try {
    const res = await get<FileTagVO[]>(`/tags/files/${fileId}`);
    if (res.code === 200 || res.code === 0) {
      return res.data;
    }
    return getMockFileTagsByNumericId(fileId);
  } catch {
    return getMockFileTagsByNumericId(fileId);
  }
}

/**
 * 文件打标
 * POST /api/tags/files/{fileId}  body: tagIds[]
 * @param fileId 文件 ID（数字）
 * @param tagIds 标签 ID 列表
 */
export async function addFileTags(
  fileId: number,
  tagIds: number[],
): Promise<void> {
  try {
    await post<void>(`/tags/files/${fileId}`, { tagIds });
  } catch {
    // Mock 降级：写入内存
    mockAddFileTags(fileId, tagIds);
  }
}

/**
 * 取消文件标签
 * DELETE /api/tags/files/{fileId}/{tagId}
 */
export async function removeFileTag(
  fileId: number,
  tagId: number,
): Promise<void> {
  try {
    await del<void>(`/tags/files/${fileId}/${tagId}`);
  } catch {
    // Mock 降级：从内存移除
    mockRemoveFileTags(fileId, tagId);
  }
}

/**
 * 按标签检索文件
 * GET /api/tags/{tagId}/files
 * @returns 文件 ID 列表
 */
export async function fetchFilesByTag(tagId: number): Promise<number[]> {
  try {
    const res = await get<number[]>(`/tags/${tagId}/files`);
    if (res.code === 200 || res.code === 0) {
      return res.data;
    }
    return getMockFilesByTag(tagId);
  } catch {
    return getMockFilesByTag(tagId);
  }
}

/**
 * 根据文件字符串 ID 获取文件标签（便捷方法）
 * 内部将字符串 ID 转换为数字
 */
export async function fetchFileTagsByStringId(
  fileId: string,
): Promise<FileTagVO[]> {
  const numId = parseFileIdToNumber(fileId);
  return fetchFileTags(numId);
}

export default {
  fetchTags,
  fetchTagTree,
  fetchTagDetail,
  createTag,
  updateTag,
  toggleTag,
  deleteTag,
  fetchEnabledTags,
  fetchFileTags,
  addFileTags,
  removeFileTag,
  fetchFilesByTag,
  fetchFileTagsByStringId,
};
