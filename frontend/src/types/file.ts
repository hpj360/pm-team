/**
 * 文件状态枚举
 */
export enum FileStatus {
  PENDING = 'pending',       // 待处理
  PROCESSING = 'processing', // 处理中
  COMPLETED = 'completed',   // 已完成
  FAILED = 'failed',         // 失败
}

/**
 * 文件类型枚举
 */
export enum FileType {
  DOCUMENT = 'document',     // 文档
  IMAGE = 'image',           // 图片
  VIDEO = 'video',           // 视频
  AUDIO = 'audio',           // 音频
  ARCHIVE = 'archive',       // 压缩包
  CODE = 'code',             // 代码
  OTHER = 'other',           // 其他
}

/**
 * 文件信息
 */
export interface FileInfo {
  id: string;
  name: string;
  originalName: string;
  size: number;
  type: FileType;
  mimeType: string;
  status: FileStatus;
  path: string;
  hash: string;
  tags: string[];
  description?: string;
  uploaderId: string;
  uploaderName: string;
  createTime: string;
  updateTime: string;
}

/**
 * 文件上传参数
 */
export interface FileUploadParams {
  file: File;
  tags?: string[];
  description?: string;
  onProgress?: (percent: number) => void;
}

/**
 * 文件列表查询参数
 */
export interface FileListParams {
  keyword?: string;
  type?: FileType;
  status?: FileStatus;
  tags?: string[];
  startTime?: string;
  endTime?: string;
  page: number;
  pageSize: number;
}

/**
 * 文件解析结果
 */
export interface ParseResult {
  fileId: string;
  content: string;
  metadata: Record<string, unknown>;
  extractedText?: string;
  keywords: string[];
  entities: EntityInfo[];
}

/**
 * 实体信息
 */
export interface EntityInfo {
  type: string;
  value: string;
  position: {
    start: number;
    end: number;
  };
  confidence: number;
}
