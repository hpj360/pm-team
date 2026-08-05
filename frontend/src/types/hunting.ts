/**
 * 威胁狩猎模块类型定义（V5.3）
 * 对应后端 analyze-service（端口 8084）com.redteam.analyze.hunting 包
 *
 * 包含：ATT&CK 矩阵、狩猎假设、狩猎规则（Sigma/YARA）
 */

/* ===================== 1. ATT&CK 矩阵 ===================== */

/** ATT&CK 战术 */
export interface AttackTactic {
  /** 战术 ID（如 execution） */
  id: string;
  /** 战术名称（英文） */
  name: string;
  /** 战术中文名 */
  nameCn: string;
}

/** ATT&CK 技术（对应后端 AttackTechniqueEntity） */
export interface AttackTechnique {
  /** 技术 ID（如 T1059） */
  techniqueId: string;
  /** 技术名称 */
  name: string;
  /** 技术描述 */
  description: string;
  /** 所属战术（tactic，如 execution / persistence） */
  tactic: string;
  /** 战术中文名（如 执行 / 持久化） */
  tacticName: string;
  /** 子技术 ID（如 T1059.001），可为空 */
  subTechniqueId?: string;
  /** 是否子技术 */
  subTechnique: boolean;
  /** 数据源（用于狩猎假设检索） */
  dataSource?: string;
}

/** ATT&CK 矩阵数据（GET /api/hunting/attack-matrix 返回） */
export interface AttackMatrix {
  /** 战术列表 */
  tactics: AttackTactic[];
  /** 技术列表 */
  techniques: AttackTechnique[];
  /** 战术数量 */
  tacticCount: number;
  /** 技术数量 */
  techniqueCount: number;
}

/* ===================== 2. 狩猎假设 ===================== */

/** 狩猎假设状态 */
export enum HypothesisStatus {
  DRAFT = 'DRAFT',
  VALIDATING = 'VALIDATING',
  CONFIRMED = 'CONFIRMED',
  REFUTED = 'REFUTED',
}

export const HypothesisStatusLabel: Record<HypothesisStatus, string> = {
  [HypothesisStatus.DRAFT]: '草稿',
  [HypothesisStatus.VALIDATING]: '验证中',
  [HypothesisStatus.CONFIRMED]: '已确认',
  [HypothesisStatus.REFUTED]: '已否定',
};

export const HypothesisStatusColor: Record<HypothesisStatus, string> = {
  [HypothesisStatus.DRAFT]: 'default',
  [HypothesisStatus.VALIDATING]: 'processing',
  [HypothesisStatus.CONFIRMED]: 'success',
  [HypothesisStatus.REFUTED]: 'error',
};

/** 命中实体类型 */
export type HuntingHitEntityType = 'FILE' | 'NETWORK' | 'ENTITY';

/** 狩猎命中项（对应后端 HuntingHypothesisEntity.HuntingHit） */
export interface HuntingHit {
  /** 实体类型（FILE / NETWORK / ENTITY） */
  entityType: HuntingHitEntityType | string;
  /** 实体ID */
  entityId: string;
  /** 实体名称 */
  entityName?: string;
  /** 命中描述 */
  description: string;
  /** 命中评分（0-1） */
  score: number;
  /** 命中证据 */
  evidence?: string;
}

/** 狩猎假设详情 VO（对应后端 HypothesisVO） */
export interface HypothesisDetail {
  /** 假设ID */
  id: string;
  /** 假设描述 */
  description: string;
  /** 关联 ATT&CK 技术 ID */
  techniqueId: string;
  /** 关联 ATT&CK 技术名称 */
  techniqueName?: string;
  /** 关联 ATT&CK 战术 */
  tactic?: string;
  /** 战术中文名 */
  tacticName?: string;
  /** 创建人ID */
  userId?: number;
  /** 创建人姓名 */
  userName?: string;
  /** 当前状态 */
  status: HypothesisStatus | string;
  /** 置信度（0-1） */
  confidence?: number;
  /** 命中清单 */
  hits: HuntingHit[];
  /** 推荐 IOC 列表 */
  recommendedIocs: string[];
  /** 验证时间（ISO 字符串） */
  validatedTime?: string;
  /** 创建时间（ISO 字符串） */
  createTime: string;
  /** 更新时间（ISO 字符串） */
  updateTime: string;
}

/** 狩猎假设实体（对应后端 HuntingHypothesisEntity，validate 接口返回） */
export interface HuntingHypothesis {
  /** 假设ID */
  id: string;
  /** 假设描述 */
  description: string;
  /** 关联 ATT&CK 技术 ID */
  techniqueId: string;
  /** 创建人ID */
  userId?: number;
  /** 创建人姓名 */
  userName?: string;
  /** 当前状态 */
  status: HypothesisStatus | string;
  /** 置信度（0-1） */
  confidence?: number;
  /** 命中清单 */
  hits: HuntingHit[];
  /** 推荐 IOC 列表 */
  recommendedIocs: string[];
  /** 验证时间 */
  validatedTime?: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
}

/** 创建狩猎假设请求体 */
export interface CreateHypothesisPayload {
  /** 假设描述 */
  description: string;
  /** ATT&CK 技术 ID */
  techniqueId: string;
  /** 创建人ID */
  userId?: number;
}

/* ===================== 3. 狩猎规则 ===================== */

/** 狩猎规则类型 */
export enum HuntingRuleType {
  SIGMA = 'SIGMA',
  YARA = 'YARA',
}

export const HuntingRuleTypeLabel: Record<HuntingRuleType, string> = {
  [HuntingRuleType.SIGMA]: 'Sigma',
  [HuntingRuleType.YARA]: 'YARA',
};

/** 狩猎规则（对应后端 HuntingRuleEntity） */
export interface HuntingRule {
  /** 规则ID */
  id: string;
  /** 规则名称 */
  name: string;
  /** 规则类型（SIGMA / YARA） */
  type: HuntingRuleType | string;
  /** 规则内容（源码） */
  content: string;
  /** 规则描述 */
  description?: string;
  /** 作者 */
  author?: string;
  /** 严重等级（info/low/medium/high/critical） */
  severity?: string;
  /** 标签 */
  tags: string[];
  /** 关联 ATT&CK 技术 ID 列表 */
  attackTechniqueIds: string[];
  /** 是否启用 */
  enabled: boolean;
  /** 版本号 */
  version: number;
  /** 命中次数 */
  matchCount: number;
  /** 测试次数 */
  testCount: number;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
  /** 最近命中时间 */
  lastMatchTime?: string;
}

/** 规则测试结果 */
export interface RuleTestResult {
  /** 是否命中 */
  matched: boolean;
  /** 命中数 */
  matchCount?: number;
  /** 命中详情 */
  details?: string;
  /** 耗时(ms) */
  costMs?: number;
  /** 规则ID */
  ruleId?: string;
  /** 规则名 */
  ruleName?: string;
  /** 文件ID */
  fileId?: string;
  [key: string]: unknown;
}

/** 规则统计信息 */
export interface RuleStats {
  /** 规则ID */
  ruleId?: string;
  /** 命中次数 */
  matchCount: number;
  /** 测试次数 */
  testCount: number;
  /** 版本号 */
  version: number;
  /** 是否启用 */
  enabled: boolean;
  /** 最近命中时间 */
  lastMatchTime?: string;
  [key: string]: unknown;
}

/** 导入规则请求体 */
export interface ImportRulePayload {
  /** 规则内容 */
  content: string;
}
