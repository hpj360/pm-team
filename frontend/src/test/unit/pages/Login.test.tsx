/**
 * 单元测试：登录页面 src/pages/Login/index.tsx
 * - 渲染基础元素
 * - 用户名密码阶段提交
 * - MFA 验证阶段切换与提交
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import Login from '@/pages/Login';

// Mock react-router-dom 的 useNavigate
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: vi.fn(),
  };
});

// Mock useAuth hook
const mockLogin = vi.fn();
const mockVerifyMfa = vi.fn();
const mockCancelMfa = vi.fn();
vi.mock('@/hooks', () => ({
  useAuth: () => ({
    login: mockLogin,
    verifyMfa: mockVerifyMfa,
    cancelMfa: mockCancelMfa,
    mfaToken: 'mock-mfa-token',
    mfaPending: false,
    user: null,
    token: null,
    isLoggedIn: false,
    themeMode: 'light',
    logout: vi.fn(),
    fetchCurrentUser: vi.fn(),
    toggleTheme: vi.fn(),
    setThemeMode: vi.fn(),
  }),
}));

// Mock Login.module.less
vi.mock('@/pages/Login/Login.module.less', () => ({
  default: {
    container: 'container',
    background: 'background',
    loginCard: 'loginCard',
    header: 'header',
    logo: 'logo',
    form: 'form',
    footer: 'footer',
  },
}));

const renderLogin = () =>
  render(
    <MemoryRouter>
      <App>
        <Login />
      </App>
    </MemoryRouter>,
  );

describe('Login 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useNavigate as unknown as ReturnType<typeof vi.fn>).mockReturnValue(vi.fn());
  });

  it('渲染标题与登录表单默认值', () => {
    renderLogin();
    expect(screen.getByText('网络安全红方文件汇聚平台')).toBeInTheDocument();
    // 默认账号 admin / admin123
    expect(screen.getByDisplayValue('admin')).toBeInTheDocument();
    expect(screen.getByDisplayValue('admin123')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /登\s*录/ })).toBeInTheDocument();
  });

  it('点击登录调用 useAuth.login 并传入表单值', async () => {
    mockLogin.mockResolvedValueOnce(true);
    renderLogin();

    const loginBtn = screen.getByRole('button', { name: /登\s*录/ });
    fireEvent.click(loginBtn);

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledTimes(1);
      expect(mockLogin).toHaveBeenCalledWith({
        username: 'admin',
        password: 'admin123',
      });
    });
  });

  it('login 返回 false 时切换到 MFA 阶段', async () => {
    mockLogin.mockResolvedValueOnce(false);
    renderLogin();

    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));

    await waitFor(() => {
      expect(screen.getByPlaceholderText('请输入 6 位 MFA 验证码')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /验\s*证/ })).toBeInTheDocument();
    });
  });

  it('MFA 阶段点击返回重新登录调用 cancelMfa 并回到凭据阶段', async () => {
    mockLogin.mockResolvedValueOnce(false);
    renderLogin();

    // 进入 MFA 阶段
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));
    await waitFor(() =>
      expect(screen.getByPlaceholderText('请输入 6 位 MFA 验证码')).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByRole('button', { name: /返回重新登录/ }));
    expect(mockCancelMfa).toHaveBeenCalled();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /登\s*录/ })).toBeInTheDocument();
    });
  });
});
