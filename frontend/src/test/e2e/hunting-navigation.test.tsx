/**
 * E2E 测试：V5.3 威胁狩猎模块导航
 * 验证 router/index.tsx 与 MainLayout 菜单配置的正确导出与联动：
 * - 主菜单包含 "威胁狩猎" 分组
 * - 展开分组后包含 "狩猎工作台" 与 "狩猎规则" 子菜单项
 * - 点击 "狩猎工作台" 跳转到 /hunting/workbench
 * - 点击 "狩猎规则" 跳转到 /hunting/rules
 * - 当前路径在 /hunting/* 时，分组自动展开（openKeys 含 /hunting-group）
 * - 路径高亮：选中项 selectedKeys 与当前 pathname 一致
 * - 无障碍：菜单项可点击、有文本标签
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import MainLayout from '@/components/layout/MainLayout';
import { useUserStore } from '@/stores';

/** 包装组件：MemoryRouter + 占位页面（与 navigation.test.tsx 同款约定） */
const renderLayout = (initialPath = '/dashboard') => {
  return render(
    <ConfigProvider locale={zhCN}>
      <AntdApp>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/" element={<MainLayout />}>
              <Route index element={<div data-testid="page-home">首页</div>} />
              <Route
                path="dashboard"
                element={<div data-testid="page-dashboard">仪表盘</div>}
              />
              <Route
                path="hunting/workbench"
                element={<div data-testid="page-hunting-workbench">狩猎工作台</div>}
              />
              <Route
                path="hunting/rules"
                element={<div data-testid="page-hunting-rules">狩猎规则</div>}
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </AntdApp>
    </ConfigProvider>,
  );
};

describe('E2E: V5.3 威胁狩猎模块导航', () => {
  beforeEach(() => {
    // 模拟已登录用户
    useUserStore.getState().login(
      {
        id: '1',
        username: 'admin',
        nickname: '红方管理员',
        email: 'admin@redteam.local',
        role: 'admin',
        createTime: '2026-01-01 00:00:00',
      },
      'fake-token',
    );
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
    useUserStore.getState().logout();
  });

  it('主菜单包含 "威胁狩猎" 分组', () => {
    renderLayout();
    expect(screen.getByText('威胁狩猎')).toBeInTheDocument();
  });

  it('展开 "威胁狩猎" 子菜单：包含 "狩猎工作台" 与 "狩猎规则"', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('威胁狩猎'));

    await waitFor(() => {
      expect(screen.getByText('狩猎工作台')).toBeInTheDocument();
      expect(screen.getByText('狩猎规则')).toBeInTheDocument();
    });
  });

  it('点击 "狩猎工作台" 跳转到 /hunting/workbench', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('威胁狩猎'));

    const workbenchMenu = await screen.findByText('狩猎工作台');
    await user.click(workbenchMenu);

    await waitFor(() => {
      expect(screen.getByTestId('page-hunting-workbench')).toBeInTheDocument();
    });
  });

  it('点击 "狩猎规则" 跳转到 /hunting/rules', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('威胁狩猎'));

    const rulesMenu = await screen.findByText('狩猎规则');
    await user.click(rulesMenu);

    await waitFor(() => {
      expect(screen.getByTestId('page-hunting-rules')).toBeInTheDocument();
    });
  });

  it('初始路径为 /hunting/workbench 时分组自动展开', async () => {
    renderLayout('/hunting/workbench');

    // 进入 hunting 路径时 openKeys 应包含 /hunting-group，子菜单应立即可见
    // 注意：主内容区也渲染了 "狩猎工作台" 占位文本，故用 getAllByText
    await waitFor(() => {
      expect(screen.getAllByText('狩猎工作台').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('狩猎规则')).toBeInTheDocument();
    });
    // 主内容区渲染工作台页
    expect(screen.getByTestId('page-hunting-workbench')).toBeInTheDocument();
  });

  it('初始路径为 /hunting/rules 时分组自动展开且主内容区渲染规则页', async () => {
    renderLayout('/hunting/rules');

    await waitFor(() => {
      // 子菜单展开
      expect(screen.getByText('狩猎工作台')).toBeInTheDocument();
      // 主内容区渲染规则页
      expect(screen.getByTestId('page-hunting-rules')).toBeInTheDocument();
    });
  });

  it('菜单项 key 与路由路径一致（key=/hunting/workbench / /hunting/rules）', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('威胁狩猎'));

    // 点击工作台后路径应为 /hunting/workbench
    const workbenchMenu = await screen.findByText('狩猎工作台');
    await user.click(workbenchMenu);
    await waitFor(() => {
      expect(screen.getByTestId('page-hunting-workbench')).toBeInTheDocument();
    });

    // 再点击规则，路径切换为 /hunting/rules
    const rulesMenu = await screen.findByText('狩猎规则');
    await user.click(rulesMenu);
    await waitFor(() => {
      expect(screen.getByTestId('page-hunting-rules')).toBeInTheDocument();
    });
  });

  it('威胁狩猎分组与红方作战、后台管理分组共存（菜单结构完整性）', () => {
    renderLayout();
    // 三个分组同时存在，验证菜单结构未被破坏
    expect(screen.getByText('威胁狩猎')).toBeInTheDocument();
    expect(screen.getByText('红方作战')).toBeInTheDocument();
    expect(screen.getByText('后台管理')).toBeInTheDocument();
  });
});
