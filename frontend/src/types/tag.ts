/**
 * 标签字典相关类型定义
 * - L1-L6 六层标签体系
 * - 树形结构 + 列表 CRUD
 */

/** 标签字典实体 */
export interface TagDict {
  id: number;
  tagCode: string;
  tagName: string;
  /** 层级 L1-L6 */
  layer: string;
  category: string;
  /** 值类型：ENUM/TEXT/NUMBER/BOOL/DATE */
  valueType: string;
  /** 适用对象：FILE/ENTITY/TARGET/TASK/ALL */
  applicableObject: string;
  identifyRule?: string;
  /** 是否多值：0/1 */
  isMulti: number;
  parentCode?: string;
  /** 启用状态：0/1 */
  enabled: number;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 标签树形结构 */
export interface TagTreeNode extends TagDict {
  children?: TagTreeNode[];
}

/** 创建/更新标签 DTO */
export interface TagDictPayload {
  tagCode: string;
  tagName: string;
  layer: string;
  category: string;
  valueType: string;
  applicableObject: string;
  identifyRule?: string;
  isMulti: number;
  parentCode?: string;
  enabled: number;
  description?: string;
}

/** 层级标签 */
export const LayerLabels: Record<string, string> = {
  L1: 'L1 文件属性',
  L2: 'L2 业务流程',
  L3: 'L3 实体识别',
  L4: 'L4 业务场景',
  L5: 'L5 情报关联',
  L6: 'L6 安全合规',
};

/** 值类型标签 */
export const ValueTypeLabels: Record<string, string> = {
  ENUM: '枚举',
  TEXT: '文本',
  NUMBER: '数值',
  BOOL: '布尔',
  DATE: '日期',
};

/** 适用对象标签 */
export const ApplicableObjectLabels: Record<string, string> = {
  FILE: '文件',
  ENTITY: '实体',
  TARGET: '目标',
  TASK: '任务',
  ALL: '全部',
};

/** 层级颜色（antd Tag color） */
export const LayerColors: Record<string, string> = {
  L1: 'blue',
  L2: 'green',
  L3: 'orange',
  L4: 'purple',
  L5: 'red',
  L6: 'cyan',
};

/** 标签来源 */
export type FileTagSource = 'AUTO' | 'MANUAL';

/** 文件标签 VO */
export interface FileTagVO {
  fileId: number;
  tagId: number;
  tagCode: string;
  tagName: string;
  layer: string;
  source: string; // AUTO / MANUAL
  createdAt: string;
}
