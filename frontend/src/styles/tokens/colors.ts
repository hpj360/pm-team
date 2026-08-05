/**
 * 颜色 Design Token
 * 包含品牌色、中性色、功能色、严重程度、敏感等级、暗黑主题等
 * 参考 Ant Design 5 token 体系 + 红方安全行业特性
 */
export const colors = {
  /** 品牌色（红方主题，深红/警示色） */
  primary: {
    50: '#fff1f0',
    100: '#ffccc7',
    200: '#ffa39e',
    300: '#ff7875',
    400: '#ff4d4f',
    500: '#f5222d', // 主色
    600: '#cf1322',
    700: '#a8071a',
    800: '#820014',
    900: '#5c0011',
  },

  /** 中性色 */
  neutral: {
    50: '#fafafa',
    100: '#f5f5f5',
    200: '#e8e8e8',
    300: '#d9d9d9',
    400: '#bfbfbf',
    500: '#8c8c8c',
    600: '#595959',
    700: '#434343',
    800: '#262626',
    900: '#141414',
  },

  /** 功能色 */
  success: '#52c41a',
  warning: '#faad14',
  error: '#f5222d',
  info: '#1890ff',

  /** 严重程度（红方业务） */
  severity: {
    info: '#1890ff',
    low: '#52c41a',
    medium: '#faad14',
    high: '#fa541c',
    critical: '#f5222d',
  },

  /** 敏感等级 */
  sensitivity: {
    L1: '#52c41a', // 公开
    L2: '#1890ff', // 内部
    L3: '#faad14', // 机密
    L4: '#fa541c', // 秘密
    L5: '#f5222d', // 绝密
  },

  /** 暗黑主题 */
  dark: {
    bg: '#141414',
    surface: '#1f1f1f',
    border: '#303030',
    text: '#f0f0f0',
  },

  /** 浅色主题背景 */
  light: {
    bg: '#f0f2f5',
    surface: '#ffffff',
    border: '#f0f0f0',
    text: 'rgba(0, 0, 0, 0.85)',
  },
} as const;

export type ColorPalette = typeof colors;
export type PrimaryScale = typeof colors.primary;
export type NeutralScale = typeof colors.neutral;
export type SeverityLevel = keyof typeof colors.severity;
export type SensitivityLevel = keyof typeof colors.sensitivity;
