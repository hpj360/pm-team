/**
 * 文件类型 + 状态 + 信息
 */
export enum FileStatus {
  PENDING = 'pending',
  PROCESSING = 'processing',
  COMPLETED = 'completed',
  FAILED = 'failed',
}

export enum FileType {
  DOCUMENT = 'document',
  IMAGE = 'image',
  VIDEO = 'video',
  AUDIO = 'audio',
  ARCHIVE = 'archive',
  CODE = 'code',
  OTHER = 'other',
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
  /** SM3 哈希（国密） */
  sm3?: string;
  tags: string[];
  /** 文件标签 VO 列表（含层级与来源） */
  fileTags?: import('./tag').FileTagVO[];
  description?: string;
  uploaderId: string;
  uploaderName: string;
  /** 敏感等级 */
  sensitivity?: import('./common').SensitivityLevel;
  /**
   * 文件密级（对应后端 classification 字段）
   * 取值：PUBLIC / INTERNAL / CONFIDENTIAL / SECRET
   * 缺省表示未分级
   */
  classification?: import('./common').FileClassification;
  /** 关联目标 ID */
  targetId?: string;
  /** 关联目标名称 */
  targetName?: string;
  /** 是否公开 */
  isPublic?: boolean;
  /** 解析状态 */
  parseStatus?: FileStatus;
  /** 解析完成时间 */
  parsedAt?: string;
  createTime: string;
  updateTime: string;
}

/**
 * 文件上传参数（基础）
 */
export interface FileUploadParams {
  file: File;
  tags?: string[];
  description?: string;
  sensitivity?: import('./common').SensitivityLevel;
  targetId?: string;
  isPublic?: boolean;
  onProgress?: (percent: number) => void;
}

/**
 * 文件列表查询参数
 */
export interface FileListParams {
  keyword?: string;
  type?: FileType;
  status?: FileStatus;
  sensitivity?: import('./common').SensitivityLevel;
  targetId?: string;
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
