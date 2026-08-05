/**
 * Design Token 入口
 * 统一导出所有 token 模块
 */
export * from './colors';
export * from './typography';
export * from './spacing';
export * from './radius';
export * from './shadows';
export * from './motion';

import { colors } from './colors';
import { typography } from './typography';
import { spacing } from './spacing';
import { radius } from './radius';
import { shadows } from './shadows';
import { motion } from './motion';

/** 全量 Token 聚合对象 */
export const tokens = {
  colors,
  typography,
  spacing,
  radius,
  shadows,
  motion,
} as const;

export type Tokens = typeof tokens;

export default tokens;
