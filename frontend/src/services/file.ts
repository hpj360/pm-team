/**
 * 文件相关 API 服务
 * - 文件 CRUD
 * - 单文件上传 + 分片上传 + 秒传
 */
import { get, post, del, upload } from '@/utils/request';
import type {
  FileInfo,
  FileListParams,
  FileUploadParams,
  ParseResult,
  PageResult,
  ApiResponse,
  MultipartInitParams,
  MultipartInitResult,
  CompleteMultipartParams,
  FileUploadMetadata,
} from '@/types';
import { getMockFileList, getMockFileById, mockFileList } from '@/mock/file';
import { FileType, FileStatus, SensitivityLevel } from '@/types';

/**
 * 获取文件列表（后端不可达降级 Mock）
 */
export async function getFileList(
  params: FileListParams,
): Promise<ApiResponse<PageResult<FileInfo>>> {
  try {
    return await get<PageResult<FileInfo>>('/files', params as unknown as Record<string, unknown>);
  } catch {
    const { list, total } = getMockFileList(
      params.page,
      params.pageSize,
      params.keyword,
      params.type,
      params.status,
      params.sensitivity,
    );
    return {
      code: 200,
      message: 'success',
      data: { list, total, page: params.page, pageSize: params.pageSize },
    };
  }
}

/**
 * 获取文件详情
 */
export async function getFileDetail(id: string): Promise<ApiResponse<FileInfo>> {
  try {
    return await get<FileInfo>(`/files/${id}`);
  } catch {
    const file = getMockFileById(id) ?? mockFileList[0];
    return { code: 200, message: 'success', data: file };
  }
}

/**
 * 上传文件（单文件直传，<5MB 场景）
 */
export async function uploadFile(params: FileUploadParams): Promise<ApiResponse<FileInfo>> {
  try {
    const formData = new FormData();
    formData.append('file', params.file);
    if (params.tags?.length) formData.append('tags', JSON.stringify(params.tags));
    if (params.description) formData.append('description', params.description);
    if (params.sensitivity) formData.append('sensitivity', params.sensitivity);
    if (params.targetId) formData.append('targetId', params.targetId);
    if (params.isPublic !== undefined) formData.append('isPublic', String(params.isPublic));

    return await upload<FileInfo>('/files/upload', formData, params.onProgress);
  } catch {
    // Mock：构造一个 FileInfo 返回
    const id = `f${Date.now().toString().padStart(4, '0')}`;
    const mockFile: FileInfo = {
      id,
      name: `file_${id}`,
      originalName: params.file.name,
      size: params.file.size,
      type: detectFileType(params.file.name),
      mimeType: params.file.type || 'application/octet-stream',
      status: FileStatus.COMPLETED,
      path: `/storage/files/${id}`,
      hash: Array.from({ length: 32 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join(''),
      sm3: Array.from({ length: 64 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join(''),
      tags: params.tags ?? [],
      description: params.description,
      uploaderId: 'u1',
      uploaderName: '红方管理员',
      sensitivity: params.sensitivity ?? SensitivityLevel.L2,
      targetId: params.targetId,
      isPublic: params.isPublic ?? false,
      parseStatus: FileStatus.PENDING,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    };
    // 模拟进度
    params.onProgress?.(100);
    return { code: 200, message: 'success', data: mockFile };
  }
}

/** 根据文件名推断 FileType */
function detectFileType(filename: string): FileType {
  const ext = filename.split('.').pop()?.toLowerCase() ?? '';
  if (['pdf', 'doc', 'docx', 'txt', 'md', 'eml'].includes(ext)) return FileType.DOCUMENT;
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(ext)) return FileType.IMAGE;
  if (['mp4', 'avi', 'mov', 'mkv'].includes(ext)) return FileType.VIDEO;
  if (['mp3', 'wav', 'flac'].includes(ext)) return FileType.AUDIO;
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return FileType.ARCHIVE;
  if (['py', 'js', 'ts', 'java', 'c', 'cpp', 'go', 'rs'].includes(ext)) return FileType.CODE;
  return FileType.OTHER;
}

/**
 * 批量上传文件
 */
export function uploadFiles(
  files: File[],
  options?: FileUploadMetadata,
): Promise<ApiResponse<FileInfo[]>> {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));
  if (options?.tags?.length) formData.append('tags', JSON.stringify(options.tags));
  if (options?.description) formData.append('description', options.description);

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
  data: Partial<Pick<FileInfo, 'tags' | 'description' | 'sensitivity' | 'isPublic'>>,
): Promise<ApiResponse<FileInfo>> {
  return post<FileInfo>(`/files/${id}`, data as unknown as Record<string, unknown>);
}

/**
 * 下载文件 URL
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
 * 获取文件预览 URL
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

/* ===== 分片上传 + 秒传 ===== */

/**
 * 秒传检查（基于 MD5/SM3 命中）
 */
export async function checkFile(
  params: { md5?: string; sm3?: string; fileName: string; fileSize: number },
): Promise<ApiResponse<{ hit: boolean; file?: FileInfo }>> {
  try {
    return await post<{ hit: boolean; file?: FileInfo }>('/files/check', params);
  } catch {
    // Mock：大文件名包含 "instant" 时命中秒传
    const hit = params.fileName.toLowerCase().includes('instant') && params.fileSize > 0;
    return {
      code: 200,
      message: 'success',
      data: { hit, file: hit ? mockFileList[0] : undefined },
    };
  }
}

/**
 * 初始化分片上传
 */
export async function listMultipart(
  params: MultipartInitParams,
): Promise<ApiResponse<MultipartInitResult>> {
  try {
    return await post<MultipartInitResult>('/files/multipart/init', params as unknown as Record<string, unknown>);
  } catch {
    // Mock：返回 uploadId 和分片预签名地址
    const uploadId = `upload_${Date.now()}`;
    const fileId = `f${Date.now().toString().padStart(4, '0')}`;
    const parts = Array.from({ length: params.chunkCount }, (_, i) => ({
      partNumber: i + 1,
      uploadUrl: `/api/files/multipart/${uploadId}/${i + 1}`,
    }));
    return {
      code: 200,
      message: 'success',
      data: { uploadId, fileId, instantHit: false, parts },
    };
  }
}

/**
 * 上传单个分片
 */
export async function uploadPart(
  uploadId: string,
  partNumber: number,
  chunk: Blob,
  onProgress?: (percent: number) => void,
): Promise<ApiResponse<{ etag: string }>> {
  try {
    const formData = new FormData();
    formData.append('chunk', chunk);
    formData.append('partNumber', String(partNumber));
    const res = await upload<{ etag: string }>(
      `/files/multipart/${uploadId}/${partNumber}`,
      formData,
      onProgress,
    );
    return res;
  } catch {
    onProgress?.(100);
    return {
      code: 200,
      message: 'success',
      data: { etag: `${uploadId}-${partNumber}-${Date.now()}` },
    };
  }
}

/**
 * 完成分片上传
 */
export async function completeMultipart(
  params: CompleteMultipartParams,
): Promise<ApiResponse<FileInfo>> {
  try {
    return await post<FileInfo>('/files/multipart/complete', params as unknown as Record<string, unknown>);
  } catch {
    const id = params.fileId;
    const mockFile: FileInfo = {
      id,
      name: `file_${id}`,
      originalName: `multipart_upload_${id}.bin`,
      size: 0,
      type: FileType.OTHER,
      mimeType: 'application/octet-stream',
      status: FileStatus.COMPLETED,
      path: `/storage/files/${id}`,
      hash: Array.from({ length: 32 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join(''),
      tags: [],
      uploaderId: 'u1',
      uploaderName: '红方管理员',
      sensitivity: SensitivityLevel.L2,
      parseStatus: FileStatus.PENDING,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    };
    return { code: 200, message: 'success', data: mockFile };
  }
}
