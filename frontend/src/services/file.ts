/**
 * 文件相关API服务
 */

import { get, post, del, upload } from '@/utils/request';
import type {
  FileInfo,
  FileListParams,
  FileUploadParams,
  ParseResult,
  PageResult,
  ApiResponse,
} from '@/types';

/**
 * 获取文件列表
 */
export function getFileList(
  params: FileListParams
): Promise<ApiResponse<PageResult<FileInfo>>> {
  return get<PageResult<FileInfo>>('/files', params);
}

/**
 * 获取文件详情
 */
export function getFileDetail(id: string): Promise<ApiResponse<FileInfo>> {
  return get<FileInfo>(`/files/${id}`);
}

/**
 * 上传文件
 */
export function uploadFile(params: FileUploadParams): Promise<ApiResponse<FileInfo>> {
  const formData = new FormData();
  formData.append('file', params.file);
  if (params.tags?.length) {
    formData.append('tags', JSON.stringify(params.tags));
  }
  if (params.description) {
    formData.append('description', params.description);
  }
  
  return upload<FileInfo>('/files/upload', formData, params.onProgress);
}

/**
 * 批量上传文件
 */
export function uploadFiles(
  files: File[],
  options?: { tags?: string[]; description?: string }
): Promise<ApiResponse<FileInfo[]>> {
  const formData = new FormData();
  files.forEach((file) => {
    formData.append('files', file);
  });
  if (options?.tags?.length) {
    formData.append('tags', JSON.stringify(options.tags));
  }
  if (options?.description) {
    formData.append('description', options.description);
  }
  
  return upload<FileInfo[]>('/files/upload/batch', formData);
}

/**
 * 删除文件
 */
export function deleteFile(id: string): Promise<ApiResponse<void>> {
  return del<void>(`/files/${id}`);
}

/**
 * 批量删除文件
 */
export function deleteFiles(ids: string[]): Promise<ApiResponse<void>> {
  return del<void>('/files/batch', { ids });
}

/**
 * 更新文件信息
 */
export function updateFile(
  id: string,
  data: Partial<Pick<FileInfo, 'tags' | 'description'>>
): Promise<ApiResponse<FileInfo>> {
  return post<FileInfo>(`/files/${id}`, data);
}

/**
 * 下载文件
 */
export function downloadFile(id: string): string {
  return `/api/files/${id}/download`;
}

/**
 * 获取文件解析结果
 */
export function getFileParseResult(id: string): Promise<ApiResponse<ParseResult>> {
  return get<ParseResult>(`/files/${id}/parse`);
}

/**
 * 触发文件解析
 */
export function parseFile(id: string): Promise<ApiResponse<ParseResult>> {
  return post<ParseResult>(`/files/${id}/parse`);
}

/**
 * 获取文件预览URL
 */
export function getFilePreviewUrl(id: string): string {
  return `/api/files/${id}/preview`;
}

/**
 * 获取文件标签列表
 */
export function getFileTags(): Promise<ApiResponse<string[]>> {
  return get<string[]>('/files/tags');
}
