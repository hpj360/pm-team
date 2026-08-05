/**
 * E2E 测试：认证流程
 * 覆盖：
 * - 用户名密码 + MFA 两阶段登录（admin）
 * - 无 MFA 直接登录（analyst）
 * - 错误账号 / 错误 MFA 验证码场景
 * - 退出登录后 token 清理
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import Login from '@/pages/Login';
import { useUserStore } from '@/stores';
import * as authService from '@/services/auth';

/** 包装组件：提供 Router + Antd 上下文 */
const renderLogin = (initialPath = '/login') => {
  return render(
    <ConfigProvider locale={zhCN}>
      <AntdApp>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<div data-testid="home">Home</div>} />
          </Routes>
        </MemoryRouter>
      </AntdApp>
    </ConfigProvider>,
  );
};

/** 在 Antd Button 上查找按钮（兼容 CJK 字间距 "登 录" / "验 证"） */
const findButtonByText = (text: RegExp): HTMLElement => {
  const buttons = screen.getAllByRole('button');
  const matched = buttons.find((btn) => text.test(btn.textContent ?? ''));
  if (!matched) {
    throw new Error(`未找到匹配 ${text} 的按钮`);
  }
  return matched;
};

/** 设置输入框值（绕过 user.type 在 password 输入上的兼容性问题） */
const setInputValue = (input: HTMLElement, value: string) => {
  fireEvent.change(input, { target: { value } });
  fireEvent.blur(input);
};

describe('E2E: 认证流程', () => {
  beforeEach(() => {
    // 清空 store 状态与 localStorage
    useUserStore.getState().logout();
    localStorage.clear();
    // 重置所有 spy
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('admin 账号：用户名密码阶段 → MFA 阶段 → 登录成功', async () => {
    const user = userEvent.setup();
    const loginSpy = vi.spyOn(authService, 'login');
    const mfaSpy = vi.spyOn(authService, 'mfaVerify');

    renderLogin();

    // 阶段 1：默认填充 admin，密码手动填充
    const usernameInput = screen.getByPlaceholderText('用户名');
    const passwordInput = screen.getByPlaceholderText('密码');

    // 确保 username 是 admin（initialValues 应用）
    await waitFor(() => {
      expect((usernameInput as HTMLInputElement).value).toBe('admin');
    });

    // password 初始可能为空，显式设置
    setInputValue(passwordInput, 'admin123');

    const submitButton = findButtonByText(/登\s*录/);
    await user.click(submitButton);

    // 调用登录 API
    await waitFor(() => {
      expect(loginSpy).toHaveBeenCalledWith({
        username: 'admin',
        password: 'admin123',
      });
    });

    // 进入 MFA 阶段：显示 MFA 验证码输入框
    const mfaInput = await screen.findByPlaceholderText(/MFA 验证码/);
    expect(mfaInput).toBeInTheDocument();

    // 阶段 2：输入 6 位验证码
    setInputValue(mfaInput, '123456');

    const verifyButton = findButtonByText(/验\s*证/);
    await user.click(verifyButton);

    // 调用 MFA 验证 API
    await waitFor(() => {
      expect(mfaSpy).toHaveBeenCalledWith({
        mfaToken: expect.any(String),
        code: '123456',
      });
    });

    // 登录成功：跳转到首页 / store 中已登录
    await waitFor(() => {
      expect(useUserStore.getState().isLoggedIn).toBe(true);
      expect(useUserStore.getState().user?.username).toBe('admin');
    });
  });

  it('analyst 账号：无 MFA 直接登录成功', async () => {
    const user = userEvent.setup();
    const loginSpy = vi.spyOn(authService, 'login');

    renderLogin();

    // 修改用户名为 analyst
    const usernameInput = screen.getByPlaceholderText('用户名');
    const passwordInput = screen.getByPlaceholderText('密码');

    setInputValue(usernameInput, 'analyst');
    setInputValue(passwordInput, 'analyst123');

    const submitButton = findButtonByText(/登\s*录/);
    await user.click(submitButton);

    await waitFor(() => {
      expect(loginSpy).toHaveBeenCalledWith({
        username: 'analyst',
        password: 'analyst123',
      });
    });

    // 未启用 MFA，直接登录成功
    await waitFor(() => {
      expect(useUserStore.getState().isLoggedIn).toBe(true);
      expect(useUserStore.getState().user?.username).toBe('analyst');
    });

    // 不应出现 MFA 输入框
    expect(screen.queryByPlaceholderText(/MFA 验证码/)).not.toBeInTheDocument();
  });

  it('错误密码：登录失败且不进入 MFA 阶段', async () => {
    const user = userEvent.setup();

    renderLogin();

    const passwordInput = screen.getByPlaceholderText('密码');
    setInputValue(passwordInput, 'wrong-password');

    const submitButton = findButtonByText(/登\s*录/);
    await user.click(submitButton);

    // 登录失败：store 中未登录
    await waitFor(() => {
      expect(useUserStore.getState().isLoggedIn).toBe(false);
    });

    // 仍停留在凭据输入阶段
    expect(screen.getByPlaceholderText('用户名')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/MFA 验证码/)).not.toBeInTheDocument();
  });

  it('MFA 阶段可返回到凭据阶段', async () => {
    const user = userEvent.setup();

    renderLogin();

    // 进入 MFA 阶段
    const passwordInput = screen.getByPlaceholderText('密码');
    setInputValue(passwordInput, 'admin123');

    const submitButton = findButtonByText(/登\s*录/);
    await user.click(submitButton);

    const mfaInput = await screen.findByPlaceholderText(/MFA 验证码/);
    expect(mfaInput).toBeInTheDocument();

    // 点击 "返回重新登录"
    const backButton = findButtonByText(/返回重新登录/);
    await user.click(backButton);

    // 回到凭据阶段
    await waitFor(() => {
      expect(screen.getByPlaceholderText('用户名')).toBeInTheDocument();
    });
    expect(screen.queryByPlaceholderText(/MFA 验证码/)).not.toBeInTheDocument();

    // MFA 状态被清理
    expect(useUserStore.getState().mfaPending).toBe(false);
    expect(useUserStore.getState().mfaToken).toBeNull();
  });

  it('MFA 验证码格式校验：非 6 位数字不能提交', async () => {
    const user = userEvent.setup();
    const mfaSpy = vi.spyOn(authService, 'mfaVerify');

    renderLogin();

    // 进入 MFA 阶段
    const passwordInput = screen.getByPlaceholderText('密码');
    setInputValue(passwordInput, 'admin123');

    const submitButton = findButtonByText(/登\s*录/);
    await user.click(submitButton);

    const mfaInput = await screen.findByPlaceholderText(/MFA 验证码/);

    // 输入不足 6 位
    setInputValue(mfaInput, '123');
    const verifyButton = findButtonByText(/验\s*证/);
    await user.click(verifyButton);

    // 不应调用 MFA API
    expect(mfaSpy).not.toHaveBeenCalled();
  });

  it('退出登录：清理 token 与用户状态', async () => {
    // 模拟已登录状态
    await act(async () => {
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
    });
    expect(useUserStore.getState().isLoggedIn).toBe(true);
    expect(localStorage.getItem('token')).toBe('fake-token');

    // 退出
    await act(async () => {
      useUserStore.getState().logout();
    });

    expect(useUserStore.getState().isLoggedIn).toBe(false);
    expect(useUserStore.getState().user).toBeNull();
    expect(useUserStore.getState().token).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('Steps 组件正确反映当前阶段', async () => {
    const user = userEvent.setup();
    renderLogin();

    // 凭据阶段：Steps 文案可见
    const credentialsStep = screen.getByText('账号密码');
    const mfaStep = screen.getByText('MFA 验证');
    expect(credentialsStep).toBeInTheDocument();
    expect(mfaStep).toBeInTheDocument();

    // 进入 MFA 阶段
    const passwordInput = screen.getByPlaceholderText('密码');
    setInputValue(passwordInput, 'admin123');

    const submitButton = findButtonByText(/登\s*录/);
    await user.click(submitButton);

    await screen.findByPlaceholderText(/MFA 验证码/);
    expect(screen.getByPlaceholderText(/MFA 验证码/)).toBeInTheDocument();
  });
});
