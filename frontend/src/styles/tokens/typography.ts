/**
 * 排版 Design Token
 */
export const typography = {
  fontFamily: {
    sans: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "PingFang SC", "Microsoft YaHei", sans-serif',
    mono: '"SF Mono", Monaco, Menlo, Consolas, "Courier New", monospace',
  },
  fontSize: {
    xs: 12,
    sm: 14,
    base: 14,
    md: 16,
    lg: 18,
    xl: 20,
    '2xl': 24,
    '3xl': 30,
    '4xl': 38,
  },
  lineHeight: {
    tight: 1.2,
    snug: 1.4,
    normal: 1.5,
    relaxed: 1.625,
    loose: 2,
  },
  fontWeight: {
    normal: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  },
} as const;

export type Typography = typeof typography;
