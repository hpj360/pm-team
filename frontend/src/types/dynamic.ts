/**
 * 沙箱动态分析模块类型定义（V5.2）
 * 对应后端 analyze-service（端口 8084）com.redteam.analyze.dynamic 包
 *
 * 状态机：PENDING → SUBMITTED → RUNNING → COMPLETED → PARSED
 * 降级状态：DEGRADED（Cuckoo 沙箱不可用）
 * 失败状态：FAILED
 */

/* ===================== 1. 任务状态 ===================== */

/** 动态分析任务状态 */
export enum DynamicTaskStatus {
  PENDING = 'PENDING',
  SUBMITTED = 'SUBMITTED',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  PARSED = 'PARSED',
  FAILED = 'FAILED',
  DEGRADED = 'DEGRADED',
}

export const DynamicTaskStatusLabel: Record<DynamicTaskStatus, string> = {
  [DynamicTaskStatus.PENDING]: '待处理',
  [DynamicTaskStatus.SUBMITTED]: '已提交',
  [DynamicTaskStatus.RUNNING]: '运行中',
  [DynamicTaskStatus.COMPLETED]: '已完成',
  [DynamicTaskStatus.PARSED]: '已解析',
  [DynamicTaskStatus.FAILED]: '失败',
  [DynamicTaskStatus.DEGRADED]: '已降级',
};

export const DynamicTaskStatusColor: Record<DynamicTaskStatus, string> = {
  [DynamicTaskStatus.PENDING]: 'default',
  [DynamicTaskStatus.SUBMITTED]: 'processing',
  [DynamicTaskStatus.RUNNING]: 'processing',
  [DynamicTaskStatus.COMPLETED]: 'success',
  [DynamicTaskStatus.PARSED]: 'success',
  [DynamicTaskStatus.FAILED]: 'error',
  [DynamicTaskStatus.DEGRADED]: 'warning',
};

/* ===================== 2. 行为指标 ===================== */

/** 进程树节点 */
export interface ProcessTreeNode {
  /** 进程 PID */
  pid: number;
  /** 进程名 */
  name: string;
  /** 父进程 PID */
  parentPid: number;
  /** 进程路径 */
  imagePath?: string;
  /** 命令行 */
  commandLine?: string;
  /** 行为描述 */
  action?: string;
  /** 是否恶意 */
  malicious?: boolean;
  /** 子进程（树形展开） */
  children?: ProcessTreeNode[];
}

/** 网络连接 */
export interface NetworkConnection {
  /** 目标 IP */
  dstIp: string;
  /** 目标端口 */
  dstPort: number;
  /** 目标域名 */
  dstDomain?: string;
  /** 协议（TCP/UDP/HTTP/HTTPS/DNS） */
  protocol: string;
  /** 流量字节数 */
  bytes: number;
  /** 是否恶意 */
  malicious?: boolean;
}

/** 文件操作 */
export interface FileOperation {
  /** 操作类型（create/write/delete/read/move/copy） */
  type: string;
  /** 目标路径 */
  path: string;
  /** 进程名 */
  processName?: string;
  /** 是否恶意 */
  malicious?: boolean;
}

/** ATT&CK 技术映射 */
export interface AttackTechniqueMapping {
  /** 技术 ID（如 T1059） */
  techniqueId: string;
  /** 技术 ID（兼容字段） */
  technique?: string;
  /** 战术（如 execution） */
  tactic: string;
  /** 技术名称 */
  name?: string;
  /** 描述 */
  description: string;
}

/** 动态分析提取的 IOC */
export interface DynamicIocItem {
  /** IOC 类型（ip/domain/url/hash/path） */
  type: string;
  /** IOC 值 */
  value: string;
  /** 来源（进程名 / 网络连接） */
  source?: string;
  /** 描述 */
  description?: string;
  /** 技术ID */
  techniqueId?: string;
}

/** STIX 2.1 对象（进程 / 网络流量） */
export interface StixObject {
  /** STIX 对象类型（process / network-traffic） */
  type: string;
  /** STIX ID */
  id: string;
  /** 其他属性 */
  [key: string]: unknown;
}

/* ===================== 3. 动态分析任务 ===================== */

/** 动态分析任务（对应后端 DynamicAnalysisTask） */
export interface DynamicAnalysisTask {
  /** 平台侧动态分析任务ID */
  taskId: string;
  /** 文件ID */
  fileId: number;
  /** Cuckoo 沙箱返回的任务ID（降级时为 degraded- 前缀） */
  cuckooTaskId?: string;
  /** 当前状态 */
  status: DynamicTaskStatus | string;
  /** Cuckoo 报告原始 JSON */
  rawReport?: string;
  /** 行为指标解析结果 */
  indicators?: Record<string, unknown>;
  /** 进程树节点 */
  processTree: ProcessTreeNode[];
  /** 网络连接列表 */
  networkConnections: NetworkConnection[];
  /** 文件操作列表 */
  fileOperations: FileOperation[];
  /** ATT&CK 技术映射列表 */
  attackTechniques: AttackTechniqueMapping[];
  /** 提取的 IOC 列表 */
  iocs: DynamicIocItem[];
  /** 是否降级 */
  degraded: boolean;
  /** 错误信息 */
  errorMessage?: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
  /** 解析完成时间 */
  parsedTime?: string;
}

/* ===================== 4. 动态分析报告 VO ===================== */

/** 动态分析报告（对应后端 DynamicReportVO） */
export interface DynamicReport {
  /** 平台侧动态分析任务ID */
  taskId: string;
  /** 文件ID */
  fileId: number;
  /** Cuckoo 任务ID */
  cuckooTaskId?: string;
  /** 当前状态 */
  status: DynamicTaskStatus | string;
  /** 是否降级 */
  degraded: boolean;
  /** 威胁评分（0-10，Cuckoo score 转换） */
  score?: number;
  /** 摘要 */
  summary?: string;
  /** 进程树节点 */
  processTree: ProcessTreeNode[];
  /** 网络连接列表 */
  networkConnections: NetworkConnection[];
  /** 文件操作列表 */
  fileOperations: FileOperation[];
  /** ATT&CK 技术映射列表 */
  attackTechniques: AttackTechniqueMapping[];
  /** 提取的 IOC 列表 */
  iocs: DynamicIocItem[];
  /** STIX 2.1 对象列表 */
  stixObjects: StixObject[];
  /** 错误信息 */
  errorMessage?: string;
  /** 创建时间（ISO 字符串） */
  createTime: string;
  /** 解析完成时间（ISO 字符串） */
  parsedTime?: string;
}
