/**
 * 监控看板类型定义
 */

/** 业务阶段 */
export enum Stage {
  UPLOAD = 'UPLOAD',
  INDEX = 'INDEX',
  PARSE = 'PARSE',
  SEARCH = 'SEARCH',
}

/** 阶段中文名映射 */
export const StageName: Record<Stage, string> = {
  [Stage.UPLOAD]: '上传',
  [Stage.INDEX]: '索引',
  [Stage.PARSE]: '解析',
  [Stage.SEARCH]: '搜索',
};

/** 时间范围 */
export type TimeRange = '1h' | '6h' | '24h' | '7d' | '30d';

/** 团队空间 */
export interface TeamSpace {
  id: number;
  code: string;
  name: string;
  storageQuota: number; // 存储配额(字节)
  fileQuota: number;    // 文件数配额
  storageUsed: number;  // 已用存储(字节)
  fileCount: number;    // 文件数
  status: 0 | 1;
}

/** KPI 卡片数据 */
export interface KpiData {
  uploadCount: number;        // 上传文件数
  totalStorage: number;       // 总存储量(字节)
  spaceCount: number;         // 在线团队空间数
  searchCountToday: number;   // 今日搜索数
}

/** 阶段指标时间序列点 */
export interface StageMetricPoint {
  timestamp: string;
  successRate: number; // 成功率(%)
  durationP95: number; // P95耗时(毫秒)
  count: number;       // 数量
}

/** 阶段指标序列 */
export interface StageMetricSeries {
  stage: Stage;
  points: StageMetricPoint[];
}

/** 漏斗数据 */
export interface FunnelStage {
  stage: Stage;
  stageName: string;
  value: number;
}

/** 文件类型分布 */
export interface FileTypeDist {
  fileType: string;
  count: number;
}

/** TopN 项 */
export interface TopNItem {
  rankNo: number;
  itemKey: string;
  itemCount: number;
}

/** 搜索分位数序列点 */
export interface SearchPercentilePoint {
  timestamp: string;
  p50: number;
  p95: number;
  p99: number;
}

/** 搜索结果数分布桶 */
export interface SearchResultBucket {
  bucket: string;
  count: number;
}

/** SLO 状态 */
export interface SloStatus {
  sloCode: string;
  sloName: string;
  stage: Stage;
  targetValue: number;
  targetUnit: string;
  actualValue: number;
  errorBudgetRemaining: number; // 剩余错误预算(%)
  burnRate2h: number;
  burnRate6h: number;
  status: 0 | 1 | 2; // 0正常 1告警 2违约
}

/** 文件事件 */
export interface FileEvent {
  id: number;
  traceId: string;
  teamSpaceId: number;
  fileId: number;
  stage: Stage;
  eventType: 'START' | 'SUCCESS' | 'FAIL';
  durationMs: number;
  fileType: string;
  operatorId: number;
  errorCode?: string;
  createdAt: string;
}

/** 队列积压项 */
export interface QueueLagItem {
  teamSpaceId: number;
  teamSpaceName: string;
  lag: number;
}

/** 失败原因 Top 项 */
export interface FailReasonItem {
  errorCode: string;
  errorName: string;
  count: number;
}

/** 看板全局筛选条件 */
export interface MonitorFilter {
  timeRange: TimeRange;
  teamSpaceId?: number; // undefined=全部
}
