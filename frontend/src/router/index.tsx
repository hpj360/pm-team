/**
 * 路由配置
 */

import React, { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { MainLayout } from '@/components/layout';

// 懒加载页面组件
const Dashboard = lazy(() => import('@/pages/Dashboard'));
const FileList = lazy(() => import('@/pages/FileList'));
const FileUpload = lazy(() => import('@/pages/FileUpload'));
const FileSearch = lazy(() => import('@/pages/FileSearch'));
const FileAnalyze = lazy(() => import('@/pages/FileAnalyze'));
const IocCenter = lazy(() => import('@/pages/IocCenter'));
const Settings = lazy(() => import('@/pages/Settings'));
const Login = lazy(() => import('@/pages/Login'));
const NotFound = lazy(() => import('@/pages/NotFound'));

// 加载中组件
const Loading: React.FC = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
    <Spin size="large" tip="加载中..." />
  </div>
);

// 懒加载包装器
const LazyLoad = (Component: React.LazyExoticComponent<React.FC>) => (
  <Suspense fallback={<Loading />}>
    <Component />
  </Suspense>
);

// 路由守卫 - 检查登录状态
const AuthGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

// 路由配置
export const router = createBrowserRouter([
  {
    path: '/login',
    element: LazyLoad(Login),
  },
  {
    path: '/',
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: 'dashboard',
        element: LazyLoad(Dashboard),
      },
      {
        path: 'files',
        element: LazyLoad(FileList),
      },
      {
        path: 'upload',
        element: LazyLoad(FileUpload),
      },
      {
        path: 'search',
        element: LazyLoad(FileSearch),
      },
      {
        path: 'analyze',
        element: LazyLoad(FileAnalyze),
      },
      {
        path: 'ioc',
        element: LazyLoad(IocCenter),
      },
      {
        path: 'settings',
        element: LazyLoad(Settings),
      },
    ],
  },
  {
    path: '*',
    element: LazyLoad(NotFound),
  },
]);

export default router;
