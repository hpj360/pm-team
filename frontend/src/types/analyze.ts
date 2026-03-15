/**
 * 分析任务状态
 */
export enum AnalyzeStatus {
  PENDING = 'pending',       // 待处理
  RUNNING = 'running',       // 分析中
  COMPLETED = 'completed',   // 已完成
  FAILED = 'failed',         // 失败
}

/**
 * 分析类型
 */
export enum AnalyzeType {
  CONTENT = 'content',       // 内容分析
  MALWARE = 'malware',       // 恶意软件分析
  NETWORK = 'network',       // 网络行为分析
  CRYPTO = 'crypto',         // 加密分析
  METADATA = 'metadata',     // 元数据分析
  IOC = 'ioc',               // IOC提取
}

/**
 * 分析任务
 */
export interface AnalyzeTask {
  id: string;
  fileId: string;
  fileName: string;
  type: AnalyzeType;
  status: AnalyzeStatus;
  progress: number;
  result?: AnalyzeResult;
  error?: string;
  createTime: string;
  updateTime: string;
  completeTime?: string;
}

/**
 * 分析结果
 */
export interface AnalyzeResult {
  taskId: string;
  fileId: string;
  type: AnalyzeType;
  summary: string;
  details: AnalyzeDetail[];
  iocs: IocInfo[];
  risks: RiskInfo[];
  charts?: ChartData[];
  recommendations: string[];
  createTime: string;
}

/**
 * 分析详情项
 */
export interface AnalyzeDetail {
  category: string;
  title: string;
  description: string;
  severity: 'info' | 'warning' | 'critical';
  evidence: string[];
}

/**
 * IOC信息 (威胁情报)
 */
export interface IocInfo {
  type: IocType;
  value: string;
  confidence: number;
  source?: string;
  tags: string[];
  firstSeen?: string;
  lastSeen?: string;
}

/**
 * IOC类型
 */
export enum IocType {
  IP = 'ip',                 // IP地址
  DOMAIN = 'domain',         // 域名
  URL = 'url',               // URL
  MD5 = 'md5',               // MD5
  SHA1 = 'sha1',             // SHA1
  SHA256 = 'sha256',         // SHA256
  EMAIL = 'email',           // 邮箱
  CVE = 'cve',               // CVE漏洞
  BTC = 'btc',               // 比特币地址
}

/**
 * 风险信息
 */
export interface RiskInfo {
  level: 'low' | 'medium' | 'high' | 'critical';
  category: string;
  description: string;
  score: number;
  vector?: string;
}

/**
 * 图表数据
 */
export interface ChartData {
  type: 'pie' | 'bar' | 'line' | 'radar' | 'tree';
  title: string;
  data: Record<string, unknown>;
}

/**
 * 分析统计信息
 */
export interface AnalyzeStatistics {
  totalTasks: number;
  completedTasks: number;
  runningTasks: number;
  failedTasks: number;
  avgCostTime: number;
  typeDistribution: Record<AnalyzeType, number>;
  statusDistribution: Record<AnalyzeStatus, number>;
}
