/**
 * Mock 数据 - 仪表盘统计
 */
import { FileType, FileStatus } from '@/types';
import type {
  DashboardData,
  DashboardStats,
  UploadTrendPoint,
  FileTypeDistribution,
  RedTeamTaskProgress,
  SystemStatus,
  RecentFile,
} from '@/types';

/** 顶部统计卡片 */
export const mockDashboardStats: DashboardStats = {
  totalFiles: 12867,
  totalSize: 856 * 1024 * 1024 * 1024, // 856 GB
  parsedCount: 11240,
  activeTasks: 27,
};

/** 最近 7 天上传趋势 */
export const mockUploadTrend: UploadTrendPoint[] = (() => {
  const points: UploadTrendPoint[] = [];
  const now = new Date();
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now);
    d.setDate(now.getDate() - i);
    const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const base = 80 + Math.random() * 60;
    points.push({
      date: dateStr,
      count: Math.round(base),
      size: Math.round(base * (2 + Math.random() * 4) * 1024 * 1024),
    });
  }
  return points;
})();

/** 文件类型分布 */
export const mockTypeDistribution: FileTypeDistribution[] = [
  { type: FileType.DOCUMENT, typeName: '文档', count: 5234, ratio: 0.407 },
  { type: FileType.IMAGE, typeName: '图片', count: 3120, ratio: 0.242 },
  { type: FileType.ARCHIVE, typeName: '压缩包', count: 1845, ratio: 0.143 },
  { type: FileType.CODE, typeName: '代码', count: 1102, ratio: 0.086 },
  { type: FileType.VIDEO, typeName: '视频', count: 768, ratio: 0.06 },
  { type: FileType.AUDIO, typeName: '音频', count: 512, ratio: 0.04 },
  { type: FileType.OTHER, typeName: '其他', count: 286, ratio: 0.022 },
];

/** 红方任务进度（最近 5 个） */
export const mockTaskProgress: RedTeamTaskProgress[] = [
  {
    id: 't0001',
    name: 'APT-41 鱼叉钓鱼邮件溯源',
    type: '溯源分析',
    progress: 85,
    status: 'running',
    owner: '张三',
    createTime: '2026-07-20T08:30:00Z',
  },
  {
    id: 't0002',
    name: '勒索软件样本逆向',
    type: '逆向工程',
    progress: 100,
    status: 'completed',
    owner: '李四',
    createTime: '2026-07-18T10:00:00Z',
  },
  {
    id: 't0003',
    name: 'C2 服务器指纹提取',
    type: '情报提取',
    progress: 62,
    status: 'running',
    owner: '王五',
    createTime: '2026-07-22T14:20:00Z',
  },
  {
    id: 't0004',
    name: '内网横向移动行为分析',
    type: '行为分析',
    progress: 35,
    status: 'running',
    owner: '赵六',
    createTime: '2026-07-24T09:15:00Z',
  },
  {
    id: 't0005',
    name: '0day 漏洞 PoC 验证',
    type: '漏洞分析',
    progress: 0,
    status: 'pending',
    owner: '钱七',
    createTime: '2026-07-26T16:00:00Z',
  },
];

/** 系统状态 */
export const mockSystemStatus: SystemStatus = {
  cpuUsage: 42,
  memoryUsage: 68,
  diskUsage: 75,
  diskTotal: 2 * 1024 * 1024 * 1024 * 1024, // 2 TB
  diskUsed: 1.5 * 1024 * 1024 * 1024 * 1024, // 1.5 TB
  memoryTotal: 64 * 1024 * 1024 * 1024, // 64 GB
  memoryUsed: 43 * 1024 * 1024 * 1024, // 43 GB
};

/** 最近上传文件 */
export const mockRecentFiles: RecentFile[] = [
  {
    id: 'f0001',
    name: 'malware_sample_0001.exe',
    size: 4 * 1024 * 1024,
    type: FileType.OTHER,
    uploader: '张三',
    uploadTime: '2026-07-27T10:15:30Z',
    status: FileStatus.COMPLETED,
  },
  {
    id: 'f0002',
    name: 'phishing_email_0002.eml',
    size: 256 * 1024,
    type: FileType.DOCUMENT,
    uploader: '李四',
    uploadTime: '2026-07-27T09:42:11Z',
    status: FileStatus.COMPLETED,
  },
  {
    id: 'f0003',
    name: 'network_traffic_0003.pcap',
    size: 128 * 1024 * 1024,
    type: FileType.OTHER,
    uploader: '王五',
    uploadTime: '2026-07-27T08:30:00Z',
    status: FileStatus.PROCESSING,
  },
  {
    id: 'f0004',
    name: 'attack_report_0004.pdf',
    size: 2 * 1024 * 1024,
    type: FileType.DOCUMENT,
    uploader: '赵六',
    uploadTime: '2026-07-27T07:18:45Z',
    status: FileStatus.COMPLETED,
  },
  {
    id: 'f0005',
    name: 'exploit_code_0005.py',
    size: 32 * 1024,
    type: FileType.CODE,
    uploader: '钱七',
    uploadTime: '2026-07-26T22:05:19Z',
    status: FileStatus.COMPLETED,
  },
];

/** 仪表盘完整数据 */
export const mockDashboardData: DashboardData = {
  stats: mockDashboardStats,
  uploadTrend: mockUploadTrend,
  typeDistribution: mockTypeDistribution,
  taskProgress: mockTaskProgress,
  systemStatus: mockSystemStatus,
  recentFiles: mockRecentFiles,
};

export default mockDashboardData;
