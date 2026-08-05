/**
 * 仪表盘统计类型
 */

/**
 * 顶部统计卡片数据
 */
export interface DashboardStats {
  /** 文件总数 */
  totalFiles: number;
  /** 总大小（字节） */
  totalSize: number;
  /** 解析完成数 */
  parsedCount: number;
  /** 在线任务数 */
  activeTasks: number;
}

/**
 * 上传趋势数据点
 */
export interface UploadTrendPoint {
  /** 日期 YYYY-MM-DD */
  date: string;
  /** 上传文件数 */
  count: number;
  /** 上传大小（字节） */
  size: number;
}

/**
 * 文件类型分布
 */
export interface FileTypeDistribution {
  type: import('./file').FileType;
  typeName: string;
  count: number;
  /** 占比 0-1 */
  ratio: number;
}

/**
 * 红方任务进度
 */
export interface RedTeamTaskProgress {
  id: string;
  name: string;
  /** 任务类型 */
  type: string;
  /** 进度 0-100 */
  progress: number;
  /** 状态 */
  status: 'pending' | 'running' | 'completed' | 'failed';
  /** 负责人 */
  owner: string;
  /** 创建时间 */
  createTime: string;
}

/**
 * 系统状态指标
 */
export interface SystemStatus {
  /** CPU 使用率 0-100 */
  cpuUsage: number;
  /** 内存使用率 0-100 */
  memoryUsage: number;
  /** 磁盘使用率 0-100 */
  diskUsage: number;
  /** 磁盘总容量（字节） */
  diskTotal: number;
  /** 磁盘已用（字节） */
  diskUsed: number;
  /** 内存总量（字节） */
  memoryTotal: number;
  /** 内存已用（字节） */
  memoryUsed: number;
}

/**
 * 最近上传文件（精简版 FileInfo）
 */
export interface RecentFile {
  id: string;
  name: string;
  size: number;
  type: import('./file').FileType;
  uploader: string;
  uploadTime: string;
  status: import('./file').FileStatus;
}

/**
 * 仪表盘完整数据
 */
export interface DashboardData {
  stats: DashboardStats;
  uploadTrend: UploadTrendPoint[];
  typeDistribution: FileTypeDistribution[];
  taskProgress: RedTeamTaskProgress[];
  systemStatus: SystemStatus;
  recentFiles: RecentFile[];
}
