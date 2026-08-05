/**
 * 分片上传相关类型
 */

/**
 * 分片大小阈值（5MB 以上自动分片）
 */
export const MULTIPART_THRESHOLD = 5 * 1024 * 1024;

/**
 * 默认分片大小（5MB）
 */
export const DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;

/**
 * 分片上传初始化参数
 */
export interface MultipartInitParams {
  fileName: string;
  fileSize: number;
  /** 文件 MD5（用于秒传判断） */
  md5?: string;
  /** 文件 SM3 */
  sm3?: string;
  mimeType: string;
  /** 分片大小 */
  chunkSize: number;
  /** 分片总数 */
  chunkCount: number;
  /** 文件元数据 */
  metadata: FileUploadMetadata;
}

/**
 * 文件上传元数据
 */
export interface FileUploadMetadata {
  tags?: string[];
  description?: string;
  sensitivity?: import('./common').SensitivityLevel;
  targetId?: string;
  isPublic?: boolean;
}

/**
 * 分片上传初始化响应
 */
export interface MultipartInitResult {
  uploadId: string;
  fileId: string;
  /** 是否秒传命中 */
  instantHit: boolean;
  /** 已存在的文件信息（秒传时返回） */
  existedFile?: import('./file').FileInfo;
  /** 各分片上传地址（预签名） */
  parts?: Array<{ partNumber: number; uploadUrl: string }>;
}

/**
 * 单个分片上传结果
 */
export interface PartUploadResult {
  uploadId: string;
  partNumber: number;
  etag: string;
  /** 该分片上传进度 */
  percent: number;
}

/**
 * 分片上传完成参数
 */
export interface CompleteMultipartParams {
  uploadId: string;
  fileId: string;
  parts: Array<{ partNumber: number; etag: string }>;
}

/**
 * 分片上传状态
 */
export type UploadTaskStatus =
  | 'pending' // 等待中
  | 'uploading' // 上传中
  | 'completed' // 已完成
  | 'failed' // 失败
  | 'paused' // 已暂停
  | 'instant'; // 秒传

/**
 * 上传任务（前端维护的状态）
 */
export interface UploadTask {
  uid: string;
  file: File;
  fileName: string;
  fileSize: number;
  /** 是否分片上传 */
  isMultipart: boolean;
  /** 分片大小 */
  chunkSize: number;
  /** 分片总数 */
  chunkCount: number;
  /** 已完成分片数 */
  completedChunks: number;
  /** 当前上传进度 0-100 */
  percent: number;
  /** 上传 ID（分片上传初始化后获得） */
  uploadId?: string;
  /** 文件 ID */
  fileId?: string;
  /** 状态 */
  status: UploadTaskStatus;
  /** 错误信息 */
  error?: string;
  /** 是否秒传命中 */
  instantHit: boolean;
  /** MD5 */
  md5?: string;
  /** 元数据 */
  metadata: FileUploadMetadata;
  /** 上传完成的文件信息 */
  result?: import('./file').FileInfo;
  /** 各分片上传进度 */
  partPercents: number[];
}
