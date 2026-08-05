/**
 * E2E 测试：主布局导航
 * 覆盖：
 * - 主菜单分组（文件管理 / 红方作战 / 后台管理）
 * - 菜单项点击跳转
 * - 侧边栏折叠 / 展开
 * - Skip to content 链接
 * - 顶部主题切换 / 通知 / 用户菜单按钮
 * - 用户菜单下拉项（个人中心 / 账户设置 / 退出登录）
 * - 子菜单展开/收起
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import MainLayout from '@/components/layout/MainLayout';
import { useUserStore } from '@/stores';

/** 包装组件：MemoryRouter + 占位页面 */
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
                path="files"
                element={<div data-testid="page-files">文件列表</div>}
              />
              <Route
                path="files/upload"
                element={<div data-testid="page-upload">文件上传</div>}
              />
              <Route
                path="search"
                element={<div data-testid="page-search">文件检索</div>}
              />
              <Route
                path="redteam/relation-graph"
                element={<div data-testid="page-relation">关系图谱</div>}
              />
              <Route
                path="redteam/tasks"
                element={<div data-testid="page-tasks">任务管理</div>}
              />
              <Route
                path="admin/reports"
                element={<div data-testid="page-reports">报告中心</div>}
              />
              <Route
                path="admin/notifications"
                element={<div data-testid="page-notifications">通知中心</div>}
              />
              <Route
                path="settings"
                element={<div data-testid="page-settings">系统设置</div>}
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </AntdApp>
    </ConfigProvider>,
  );
};

describe('E2E: 主布局导航', () => {
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

  it('初始渲染：包含 logo、菜单分组、顶部按钮', () => {
    renderLayout();

    // Logo 标题
    expect(screen.getByText('红方文件平台')).toBeInTheDocument();

    // 一级菜单项
    expect(screen.getByText('工作台')).toBeInTheDocument();
    expect(screen.getByText('文件管理')).toBeInTheDocument();
    expect(screen.getByText('文件检索')).toBeInTheDocument();
    expect(screen.getByText('文件分析')).toBeInTheDocument();
    expect(screen.getByText('威胁情报')).toBeInTheDocument();
    expect(screen.getByText('监控看板')).toBeInTheDocument();
    expect(screen.getByText('红方作战')).toBeInTheDocument();
    expect(screen.getByText('后台管理')).toBeInTheDocument();
    expect(screen.getByText('系统设置')).toBeInTheDocument();

    // 顶部用户名
    expect(screen.getByText('红方管理员')).toBeInTheDocument();
  });

  it('Skip to content 链接存在且可访问', () => {
    renderLayout();

    const skipLink = screen.getByLabelText('跳到主内容');
    expect(skipLink).toBeInTheDocument();
    expect(skipLink).toHaveAttribute('href', '#main-content');
  });

  it('顶部按钮：主题切换 / 通知 / 用户菜单均有 aria-label', () => {
    renderLayout();

    expect(screen.getByLabelText('切换主题')).toBeInTheDocument();
    expect(screen.getByLabelText('通知中心')).toBeInTheDocument();
    expect(screen.getByLabelText('用户菜单')).toBeInTheDocument();
  });

  it('侧边栏折叠 / 展开按钮：aria-expanded 同步切换', async () => {
    const user = userEvent.setup();
    renderLayout();

    const collapseBtn = screen.getByLabelText('收起侧边栏');
    expect(collapseBtn).toHaveAttribute('aria-expanded', 'true');

    await user.click(collapseBtn);

    await waitFor(() => {
      const expandBtn = screen.getByLabelText('展开侧边栏');
      expect(expandBtn).toHaveAttribute('aria-expanded', 'false');
    });
  });

  it('点击 "工作台" 跳转到 /dashboard', async () => {
    const user = userEvent.setup();
    renderLayout('/files');

    const dashboardMenu = screen.getByText('工作台');
    await user.click(dashboardMenu);

    await waitFor(() => {
      expect(screen.getByTestId('page-dashboard')).toBeInTheDocument();
    });
  });

  it('点击 "文件检索" 跳转到 /search', async () => {
    const user = userEvent.setup();
    renderLayout();

    const searchMenu = screen.getByText('文件检索');
    await user.click(searchMenu);

    await waitFor(() => {
      expect(screen.getByTestId('page-search')).toBeInTheDocument();
    });
  });

  it('展开 "文件管理" 子菜单：显示文件列表与文件上传', async () => {
    const user = userEvent.setup();
    renderLayout();

    // 点击 文件管理 分组
    await user.click(screen.getByText('文件管理'));

    // 子菜单显示
    await waitFor(() => {
      expect(screen.getByText('文件列表')).toBeInTheDocument();
      expect(screen.getByText('文件上传')).toBeInTheDocument();
    });

    // 点击 文件列表
    await user.click(screen.getByText('文件列表'));
    await waitFor(() => {
      expect(screen.getByTestId('page-files')).toBeInTheDocument();
    });
  });

  it('展开 "红方作战" 子菜单：包含目标画像 / 关系图谱 / 任务管理 等', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('红方作战'));

    await waitFor(() => {
      expect(screen.getByText('目标画像')).toBeInTheDocument();
      // "威胁情报" 在主菜单和红方子菜单都存在，使用 getAllByText
      expect(screen.getAllByText('威胁情报').length).toBeGreaterThanOrEqual(2);
      expect(screen.getByText('攻击链路')).toBeInTheDocument();
      expect(screen.getByText('漏洞利用')).toBeInTheDocument();
      expect(screen.getByText('武器库')).toBeInTheDocument();
      expect(screen.getByText('协同作战')).toBeInTheDocument();
      expect(screen.getByText('关系图谱')).toBeInTheDocument();
      expect(screen.getByText('任务管理')).toBeInTheDocument();
    });
  });

  it('展开 "后台管理" 子菜单：包含报告中心与通知中心', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('后台管理'));

    await waitFor(() => {
      expect(screen.getByText('用户管理')).toBeInTheDocument();
      expect(screen.getByText('角色管理')).toBeInTheDocument();
      expect(screen.getByText('权限管理')).toBeInTheDocument();
      expect(screen.getByText('YARA规则')).toBeInTheDocument();
      expect(screen.getByText('系统配置')).toBeInTheDocument();
      expect(screen.getByText('审计日志')).toBeInTheDocument();
      expect(screen.getByText('数据源')).toBeInTheDocument();
      expect(screen.getByText('模型管理')).toBeInTheDocument();
      expect(screen.getByText('健康检查')).toBeInTheDocument();
      expect(screen.getByText('报告中心')).toBeInTheDocument();
      expect(screen.getByText('通知中心')).toBeInTheDocument();
    });
  });

  it('点击 "关系图谱" 菜单项跳转到 /redteam/relation-graph', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('红方作战'));

    const relationGraphMenu = await screen.findByText('关系图谱');
    await user.click(relationGraphMenu);

    await waitFor(() => {
      expect(screen.getByTestId('page-relation')).toBeInTheDocument();
    });
  });

  it('点击 "任务管理" 菜单项跳转到 /redteam/tasks', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('红方作战'));

    const taskMenu = await screen.findByText('任务管理');
    await user.click(taskMenu);

    await waitFor(() => {
      expect(screen.getByTestId('page-tasks')).toBeInTheDocument();
    });
  });

  it('点击 "报告中心" 菜单项跳转到 /admin/reports', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('后台管理'));

    const reportMenu = await screen.findByText('报告中心');
    await user.click(reportMenu);

    await waitFor(() => {
      expect(screen.getByTestId('page-reports')).toBeInTheDocument();
    });
  });

  it('点击 "通知中心" 菜单项跳转到 /admin/notifications', async () => {
    const user = userEvent.setup();
    renderLayout();

    await user.click(screen.getByText('后台管理'));

    const notificationMenu = await screen.findByText('通知中心');
    await user.click(notificationMenu);

    await waitFor(() => {
      expect(screen.getByTestId('page-notifications')).toBeInTheDocument();
    });
  });

  it('点击 "通知中心" 顶部按钮跳转到 /admin/notifications', async () => {
    const user = userEvent.setup();
    renderLayout();

    const notificationBtn = screen.getByLabelText('通知中心');
    await user.click(notificationBtn);

    await waitFor(() => {
      expect(screen.getByTestId('page-notifications')).toBeInTheDocument();
    });
  });

  it('用户下拉菜单：包含个人中心 / 账户设置 / 退出登录', async () => {
    const user = userEvent.setup();
    renderLayout();

    // 点击用户菜单触发器
    const userMenuTrigger = screen.getByLabelText('用户菜单');
    await user.click(userMenuTrigger);

    // 等待下拉菜单渲染
    await waitFor(() => {
      expect(screen.getByText('个人中心')).toBeInTheDocument();
      expect(screen.getByText('账户设置')).toBeInTheDocument();
      expect(screen.getByText('退出登录')).toBeInTheDocument();
    });
  });

  it('用户菜单点击 "账户设置" 跳转到 /settings', async () => {
    const user = userEvent.setup();
    renderLayout();

    const userMenuTrigger = screen.getByLabelText('用户菜单');
    await user.click(userMenuTrigger);

    await waitFor(() => {
      expect(screen.getByText('账户设置')).toBeInTheDocument();
    });

    await user.click(screen.getByText('账户设置'));

    await waitFor(() => {
      expect(screen.getByTestId('page-settings')).toBeInTheDocument();
    });
  });

  it('主内容区有 role=main 与 id=main-content（无障碍）', () => {
    renderLayout();

    const mainContent = screen.getByRole('main');
    expect(mainContent).toHaveAttribute('id', 'main-content');
    expect(mainContent).toHaveAttribute('tabIndex', '-1');
  });

  it('侧边栏有 aria-label="主菜单" 描述', () => {
    renderLayout();

    const nav = screen.getByLabelText('主导航');
    expect(nav).toBeInTheDocument();
  });

  it('顶部 Header 有 role="banner"（无障碍）', () => {
    renderLayout();

    const banner = screen.getByRole('banner');
    expect(banner).toBeInTheDocument();
  });
});
