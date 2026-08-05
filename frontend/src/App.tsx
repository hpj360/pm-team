/**
 * 应用主入口
 * - AntdApp（App + message/notification context）
 * - ConfigProvider（主题切换）
 * - React Query Provider
 * - Router
 */
import React, { useEffect, useMemo } from 'react';
import { RouterProvider } from 'react-router-dom';
import { ConfigProvider, App as AntdApp, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { router } from './router';
import { useThemeStore } from '@/stores';
import { lightTheme, darkTheme } from '@/styles/theme';

// 导入全局样式
import './styles/global.css';

/** React Query 客户端实例 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30 * 1000,
    },
  },
});

const App: React.FC = () => {
  const themeMode = useThemeStore((s) => s.mode);

  // 初始化时同步 data-theme 到 <html>
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', themeMode);
  }, [themeMode]);

  const themeConfig = useMemo(() => {
    if (themeMode === 'dark') {
      return {
        ...darkTheme,
        algorithm: antdTheme.darkAlgorithm,
      };
    }
    return lightTheme;
  }, [themeMode]);

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={zhCN} theme={themeConfig}>
        <AntdApp>
          <RouterProvider router={router} />
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>
  );
};

export default App;
