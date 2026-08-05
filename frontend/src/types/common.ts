/**
 * 通用 API 响应类型
 */
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
}

/**
 * 分页请求参数
 */
export interface PageParams {
  page: number;
  pageSize: number;
}

/**
 * 分页响应数据
 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * 用户信息
 */
export interface UserInfo {
  id: string;
  username: string;
  nickname: string;
  email: string;
  avatar?: string;
  role: string;
  createTime: string;
  /** 是否启用 MFA */
  mfaEnabled?: boolean;
}

/**
 * 登录参数
 */
export interface LoginParams {
  username: string;
  password: string;
}

/**
 * 登录响应
 */
export interface LoginResult {
  /** MFA 已启用时返回 mfaToken，需要进一步验证 */
  mfaRequired?: boolean;
  mfaToken?: string;
  /** 未启用 MFA 时直接返回 token + user */
  token?: string;
  user?: UserInfo;
}

/**
 * MFA 验证参数
 */
export interface MfaVerifyParams {
  mfaToken: string;
  code: string;
}

/**
 * MFA 设置响应（绑定 TOTP）
 */
export interface MfaSetupResult {
  secret: string;
  otpauthUrl: string;
  qrCodeUrl: string;
  backupCodes: string[];
}

/**
 * 敏感等级
 */
export enum SensitivityLevel {
  L1 = 'L1', // 公开
  L2 = 'L2', // 内部
  L3 = 'L3', // 机密
  L4 = 'L4', // 秘密
  L5 = 'L5', // 绝密
}

export const SensitivityLabel: Record<SensitivityLevel, string> = {
  [SensitivityLevel.L1]: '公开',
  [SensitivityLevel.L2]: '内部',
  [SensitivityLevel.L3]: '机密',
  [SensitivityLevel.L4]: '秘密',
  [SensitivityLevel.L5]: '绝密',
};

/**
 * 排序方向
 */
export type SortOrder = 'asc' | 'desc';

/**
 * 文件密级（对应后端 classification 字段）
 * 与用户 clearanceLevel（1-4）的映射关系：
 * - clearanceLevel 1 -> PUBLIC
 * - clearanceLevel 2 -> INTERNAL
 * - clearanceLevel 3 -> CONFIDENTIAL
 * - clearanceLevel 4 -> SECRET
 */
export enum FileClassification {
  PUBLIC = 'PUBLIC', // 公开
  INTERNAL = 'INTERNAL', // 内部
  CONFIDENTIAL = 'CONFIDENTIAL', // 秘密
  SECRET = 'SECRET', // 机密
}

/** 密级中文标签 */
export const FileClassificationLabel: Record<FileClassification, string> = {
  [FileClassification.PUBLIC]: '公开',
  [FileClassification.INTERNAL]: '内部',
  [FileClassification.CONFIDENTIAL]: '秘密',
  [FileClassification.SECRET]: '机密',
};

/** 密级 Tag 颜色映射（antd Tag 内置色板） */
export const FileClassificationColor: Record<FileClassification, string> = {
  [FileClassification.PUBLIC]: 'blue',
  [FileClassification.INTERNAL]: 'green',
  [FileClassification.CONFIDENTIAL]: 'orange',
  [FileClassification.SECRET]: 'red',
};

/**
 * 密级数值（与后端 clearanceLevel 1-4 对应），数值越大越敏感
 * 用于越权判断与排序
 */
export const FileClassificationLevel: Record<FileClassification, number> = {
  [FileClassification.PUBLIC]: 1,
  [FileClassification.INTERNAL]: 2,
  [FileClassification.CONFIDENTIAL]: 3,
  [FileClassification.SECRET]: 4,
};
