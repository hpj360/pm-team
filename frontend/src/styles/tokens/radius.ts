/**
 * 圆角 Design Token
 */
export const radius = {
  none: 0,
  sm: 2,
  base: 4,
  md: 6,
  lg: 8,
  xl: 12,
  full: 9999,
} as const;

export type Radius = typeof radius;
