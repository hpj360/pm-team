/**
 * Ant Design 主题配置
 * 基于 Design Token 系统生成 light/dark 主题
 */
import type { ThemeConfig } from 'antd';
import { colors, typography, radius } from './tokens';

/** 浅色主题 */
export const lightTheme: ThemeConfig = {
  token: {
    colorPrimary: colors.primary[500],
    colorSuccess: colors.success,
    colorWarning: colors.warning,
    colorError: colors.error,
    colorInfo: colors.info,
    fontSize: typography.fontSize.base,
    fontFamily: typography.fontFamily.sans,
    borderRadius: radius.md,
    colorBgBase: '#ffffff',
    colorTextBase: '#000000',
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 56,
      headerPadding: '0 20px',
      siderBg: '#001529',
      bodyBg: colors.light.bg,
    },
    Menu: {
      darkItemBg: '#001529',
      darkSubMenuItemBg: '#000c17',
      darkItemSelectedBg: colors.primary[700],
    },
    Card: {
      borderRadiusLG: radius.lg,
    },
    Button: {
      borderRadius: radius.md,
      controlHeight: 32,
    },
    Table: {
      headerBg: '#fafafa',
      headerColor: 'rgba(0, 0, 0, 0.85)',
      borderColor: colors.neutral[200],
    },
  },
};

/** 暗黑主题 */
export const darkTheme: ThemeConfig = {
  algorithm: undefined, // 由调用方注入 theme.darkAlgorithm
  token: {
    colorPrimary: colors.primary[400],
    colorSuccess: colors.success,
    colorWarning: colors.warning,
    colorError: colors.error,
    colorInfo: colors.info,
    fontSize: typography.fontSize.base,
    fontFamily: typography.fontFamily.sans,
    borderRadius: radius.md,
    colorBgBase: colors.dark.bg,
    colorTextBase: colors.dark.text,
  },
  components: {
    Layout: {
      headerBg: colors.dark.surface,
      headerHeight: 56,
      headerPadding: '0 20px',
      siderBg: '#000000',
      bodyBg: colors.dark.bg,
    },
    Card: {
      borderRadiusLG: radius.lg,
    },
    Button: {
      borderRadius: radius.md,
      controlHeight: 32,
    },
  },
};

export type AppThemeMode = 'light' | 'dark';

export default { lightTheme, darkTheme };
