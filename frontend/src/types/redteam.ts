/**
 * 红方作战模块类型定义
 * 涵盖目标画像、威胁情报、攻击链路、漏洞利用、武器库、协同作战
 */

/* ===================== 1. 目标画像 ===================== */

/** 目标类型 */
export enum TargetType {
  ORGANIZATION = 'organization',
  PERSON = 'person',
  ASSET = 'asset',
}

export const TargetTypeLabel: Record<TargetType, string> = {
  [TargetType.ORGANIZATION]: '组织',
  [TargetType.PERSON]: '人员',
  [TargetType.ASSET]: '资产',
};

/** 组织架构节点 */
export interface OrgNode {
  id: string;
  name: string;
  title: string;
  department: string;
  level: number;
  parentId?: string;
}

/** 技术资产 */
export interface TechAsset {
  id: string;
  type: 'domain' | 'ip' | 'host' | 'webapp' | 'database' | 'cloud';
  value: string;
  os?: string;
  port?: number;
  service?: string;
  exposure: 'internet' | 'intranet' | 'isolated';
  lastSeen: string;
}

/** 攻击面项 */
export interface AttackSurface {
  id: string;
  category: 'network' | 'application' | 'host' | 'human' | 'wireless';
  vector: string;
  description: string;
  riskScore: number;
  status: 'open' | 'validated' | 'exploited' | 'remediated';
}

/** 历史事件时间线 */
export interface TargetTimelineEvent {
  id: string;
  time: string;
  title: string;
  description: string;
  category: 'recon' | 'intrusion' | 'action' | 'discovery';
}

/** 目标画像 */
export interface TargetProfile {
  id: string;
  name: string;
  type: TargetType;
  industry: string;
  region: string;
  avatar?: string;
  description: string;
  tags: string[];
  riskLevel: 'low' | 'medium' | 'high' | 'critical';
  organization: OrgNode[];
  techAssets: TechAsset[];
  attackSurfaces: AttackSurface[];
  timeline: TargetTimelineEvent[];
  createTime: string;
  updateTime: string;
}

/* ===================== 2. 威胁情报 ===================== */

/** 威胁行为者（Threat Actor） */
export interface ThreatActor {
  id: string;
  name: string;
  aliases: string[];
  origin: string;
  motivation: string;
  sophistication: 'low' | 'medium' | 'high' | 'advanced';
  targets: string[];
  ttps: string[];
  activeSince: string;
}

/** 情报订阅源 */
export interface IntelFeed {
  id: string;
  name: string;
  type: 'stix' | 'openioc' | 'misp' | 'csv' | 'json';
  url: string;
  status: 'active' | 'paused' | 'error';
  lastSync: string;
  indicators: number;
  reliability: 'A' | 'B' | 'C' | 'D' | 'E' | 'F';
}

/** 威胁情报详情（扩展 IOC） */
export interface ThreatIntelItem {
  id: string;
  type: 'ip' | 'domain' | 'url' | 'hash' | 'email' | 'cve';
  value: string;
  confidence: number;
  threatActors: string[];
  relatedCves: string[];
  relatedFiles: Array<{ id: string; name: string }>;
  firstSeen: string;
  lastSeen: string;
  occurrences: number;
  tags: string[];
  sources: string[];
}

/* ===================== 3. 攻击链路 ===================== */

/** 攻击阶段（Kill Chain） */
export interface AttackStage {
  id: string;
  phase: number;
  name: string;
  tactic: string;
  technique: string;
  description: string;
  status: 'planned' | 'in-progress' | 'completed' | 'failed';
  startTime?: string;
  endTime?: string;
  operator?: string;
  targetId?: string;
}

/** 攻击链路 */
export interface AttackChain {
  id: string;
  name: string;
  target: string;
  objective: string;
  startTime: string;
  endTime?: string;
  status: 'planning' | 'active' | 'success' | 'failed';
  stages: AttackStage[];
  flow: Array<{ from: string; to: string; value: number }>;
}

/* ===================== 4. 漏洞利用 ===================== */

/** 漏洞严重程度 */
export type VulnSeverity = 'info' | 'low' | 'medium' | 'high' | 'critical';

export const VulnSeverityLabel: Record<VulnSeverity, string> = {
  info: '信息',
  low: '低危',
  medium: '中危',
  high: '高危',
  critical: '严重',
};

/** 漏洞项 */
export interface Vulnerability {
  id: string;
  cve: string;
  name: string;
  severity: VulnSeverity;
  cvss: number;
  cwe: string;
  affectedProducts: string[];
  exploitAvailable: boolean;
  patched: boolean;
  publishedAt: string;
  updatedAt: string;
  description: string;
  exploitMethod?: string;
  poc?: string;
  remediation?: string;
  references: string[];
}

/* ===================== 5. 武器库 ===================== */

/** 武器分类 */
export type ArsenalCategory =
  | 'exploit'
  | 'backdoor'
  | 'scanner'
  | 'cracker'
  | 'proxy'
  | 'c2'
  | 'utility';

export const ArsenalCategoryLabel: Record<ArsenalCategory, string> = {
  exploit: '漏洞利用',
  backdoor: '后门',
  scanner: '扫描器',
  cracker: '密码破解',
  proxy: '代理',
  c2: '命令控制',
  utility: '工具',
};

/** 武器项 */
export interface ArsenalItem {
  id: string;
  name: string;
  category: ArsenalCategory;
  description: string;
  version: string;
  author: string;
  platforms: string[];
  relatedCves: string[];
  detectionRules: string[];
  usage: string;
  rating: number;
  enabled: boolean;
  updateTime: string;
}

/* ===================== 6. 协同作战 ===================== */

/** 协同任务状态 */
export type CollaborationStatus = 'todo' | 'doing' | 'done' | 'blocked';

export const CollaborationStatusLabel: Record<CollaborationStatus, string> = {
  todo: '待办',
  doing: '进行中',
  done: '已完成',
  blocked: '阻塞',
};

/** 协同任务 */
export interface CollaborationTask {
  id: string;
  title: string;
  description: string;
  status: CollaborationStatus;
  priority: 'low' | 'medium' | 'high' | 'urgent';
  assignee: string;
  assigneeAvatar?: string;
  dueDate: string;
  tags: string[];
  comments: number;
  attachments: number;
}

/** 团队成员 */
export interface TeamMember {
  id: string;
  name: string;
  avatar?: string;
  role: string;
  online: boolean;
  lastActive: string;
  currentTask?: string;
}

/** 实时消息 */
export interface CollaborationMessage {
  id: string;
  sender: string;
  avatar?: string;
  content: string;
  time: string;
  isMine: boolean;
}

/* ===================== 7. 关系图谱 ===================== */

/** 图谱节点类型 */
export type GraphNodeType = 'organization' | 'person' | 'asset' | 'domain' | 'ip' | 'vulnerability';

export const GraphNodeTypeLabel: Record<GraphNodeType, string> = {
  organization: '组织',
  person: '人员',
  asset: '资产',
  domain: '域名',
  ip: 'IP',
  vulnerability: '漏洞',
};

/** 图谱关系类型 */
export type GraphRelationType =
  | 'belong_to'
  | 'manage'
  | 'own'
  | 'connect'
  | 'resolve'
  | 'host'
  | 'exploit'
  | 'relate';

export const GraphRelationTypeLabel: Record<GraphRelationType, string> = {
  belong_to: '隶属',
  manage: '管理',
  own: '拥有',
  connect: '连接',
  resolve: '解析',
  host: '托管',
  exploit: '利用',
  relate: '关联',
};

/** 图谱节点 */
export interface GraphNode {
  id: string;
  name: string;
  type: GraphNodeType;
  /** 节点权重（影响大小） */
  value?: number;
  /** 风险等级 */
  riskLevel?: 'low' | 'medium' | 'high' | 'critical';
  /** 描述 */
  description?: string;
  /** 关联属性 */
  properties?: Record<string, string | number | boolean>;
  /** 标签 */
  tags?: string[];
}

/** 图谱边 */
export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  relation: GraphRelationType;
  /** 边权重（影响粗细） */
  weight?: number;
  /** 描述 */
  description?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 关系图谱数据 */
export interface RelationGraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
  /** 统计信息 */
  stats?: {
    nodeCount: number;
    edgeCount: number;
    typeDistribution: Record<GraphNodeType, number>;
  };
}

/* ===================== 7.1 Neo4j 实时关系图谱 ===================== */

/** Neo4j 原始节点类型（后端 profile-service 返回） */
export type Neo4jNodeType =
  | 'TARGET'
  | 'FILE'
  | 'PERSON'
  | 'ORGANIZATION'
  | 'ASSET'
  | 'DOMAIN'
  | 'IP'
  | 'VULNERABILITY'
  | string;

/** Neo4j 原始关系类型（后端返回，如 CONTAINS / RELATE 等） */
export type Neo4jRelationType = string;

/** Neo4j 原始节点 */
export interface Neo4jGraphNode {
  id: number | string;
  name: string;
  type: Neo4jNodeType;
  /** 其他可选属性 */
  [key: string]: unknown;
}

/** Neo4j 原始边 */
export interface Neo4jGraphEdge {
  source: number | string;
  target: number | string;
  relation: Neo4jRelationType;
  /** 其他可选属性 */
  [key: string]: unknown;
}

/** Neo4j 关系图谱响应数据 */
export interface Neo4jRelationGraphData {
  nodes: Neo4jGraphNode[];
  edges: Neo4jGraphEdge[];
}

/** 数据源类型 */
export type GraphDataSource = 'mock' | 'neo4j';

/** 图谱查询深度（1/2/3） */
export type GraphQueryDepth = 1 | 2 | 3;

/* ===================== 8. 任务管理（红方协同） ===================== */

/** 任务状态 */
export type TaskStatus = 'todo' | 'doing' | 'done' | 'blocked' | 'cancelled';

export const TaskStatusLabel: Record<TaskStatus, string> = {
  todo: '待办',
  doing: '进行中',
  done: '已完成',
  blocked: '阻塞',
  cancelled: '已取消',
};

/** 任务优先级 */
export type TaskPriority = 'low' | 'medium' | 'high' | 'urgent';

export const TaskPriorityLabel: Record<TaskPriority, string> = {
  low: '低',
  medium: '中',
  high: '高',
  urgent: '紧急',
};

/** 任务时间线事件 */
export interface TaskTimelineEvent {
  id: string;
  time: string;
  title: string;
  description: string;
  operator: string;
  /** 变更类型 */
  type: 'create' | 'assign' | 'status_change' | 'comment' | 'upload';
}

/** 任务管理项 */
export interface TaskItem {
  id: string;
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  /** 负责人 */
  assignee: string;
  assigneeAvatar?: string;
  /** 协作人 */
  collaborators?: string[];
  /** 关联目标 */
  targetId?: string;
  targetName?: string;
  /** 关联文件 */
  fileIds?: string[];
  fileNames?: string[];
  /** 进度（0-100） */
  progress: number;
  /** 计划开始时间 */
  startTime: string;
  /** 截止时间 */
  dueDate: string;
  /** 实际完成时间 */
  completedAt?: string;
  /** 标签 */
  tags: string[];
  /** 时间线 */
  timeline: TaskTimelineEvent[];
  /** 评论数 */
  comments: number;
  /** 附件数 */
  attachments: number;
  createTime: string;
  updateTime: string;
}
