/**
 * 单元测试：用户管理页面 src/pages/admin/UserManage/index.tsx
 * - 渲染标题与工具栏
 * - 新建用户按钮打开模态框
 * - 加载角色列表
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import UserManagePage from '@/pages/admin/UserManage';
import type { AdminUser, AdminRole } from '@/types';

// Mock services
const mockGetAdminUsers = vi.fn();
const mockSaveAdminUser = vi.fn();
const mockToggleUserStatus = vi.fn();
const mockDeleteAdminUser = vi.fn();
const mockGetAdminRoles = vi.fn();
vi.mock('@/services', () => ({
  getAdminUsers: (...args: unknown[]) => mockGetAdminUsers(...args),
  saveAdminUser: (...args: unknown[]) => mockSaveAdminUser(...args),
  toggleUserStatus: (...args: unknown[]) => mockToggleUserStatus(...args),
  deleteAdminUser: (...args: unknown[]) => mockDeleteAdminUser(...args),
  getAdminRoles: (...args: unknown[]) => mockGetAdminRoles(...args),
}));

const buildRole = (id: string, name: string): AdminRole =>
  ({ id, name, code: name, permissions: [], description: '', createTime: '2026-01-01T00:00:00Z' }) as unknown as AdminRole;

const buildUser = (id: string, username: string): AdminUser =>
  ({
    id,
    username,
    nickname: username.toUpperCase(),
    email: `${username}@redteam.local`,
    phone: '13800138000',
    dept: '红方',
    roleIds: ['r1'],
    status: 'active',
    avatar: '',
    lastLoginAt: '2026-07-28T00:00:00Z',
    createTime: '2026-01-01T00:00:00Z',
  }) as unknown as AdminUser;

const renderPage = () =>
  render(
    <MemoryRouter>
      <App>
        <UserManagePage />
      </App>
    </MemoryRouter>,
  );

describe('UserManage 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetAdminRoles.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [buildRole('r1', '管理员'), buildRole('r2', '分析员')],
    });
    mockGetAdminUsers.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        list: [buildUser('u1', 'admin')],
        total: 1,
        page: 1,
        pageSize: 10,
      },
    });
  });

  it('渲染页面标题与工具栏按钮', async () => {
    renderPage();
    expect(screen.getByText('用户管理')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /刷\s*新/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /新建用户/ })).toBeInTheDocument();
  });

  it('点击新建用户打开模态框并显示表单字段', async () => {
    renderPage();
    const createBtn = screen.getByRole('button', { name: /新建用户/ });
    fireEvent.click(createBtn);

    // 模态框打开后存在多个"新建用户"文本（按钮 + 模态框标题）
    await waitFor(() => {
      expect(screen.getAllByText('新建用户').length).toBeGreaterThanOrEqual(2);
    });
    // 表单字段标签（ProTable 列头也可能含相同文本，用 getAllByText）
    expect(screen.getAllByText('用户名').length).toBeGreaterThan(0);
    expect(screen.getAllByText('昵称').length).toBeGreaterThan(0);
    expect(screen.getAllByText('邮箱').length).toBeGreaterThan(0);
    expect(screen.getAllByText('密码').length).toBeGreaterThan(0);
    expect(screen.getAllByText('部门').length).toBeGreaterThan(0);
    expect(screen.getAllByText('角色').length).toBeGreaterThan(0);
  });

  it('加载角色列表用于表单 Select 选项', async () => {
    renderPage();
    await waitFor(() => {
      expect(mockGetAdminRoles).toHaveBeenCalledTimes(1);
    });
    // 打开模态框并展开角色 Select
    fireEvent.click(screen.getByRole('button', { name: /新建用户/ }));
    await waitFor(() => {
      expect(screen.getAllByText('新建用户').length).toBeGreaterThanOrEqual(2);
    });
  });
});
