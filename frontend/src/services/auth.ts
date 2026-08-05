/**
 * 认证相关 API 服务
 * - 登录 / 登出 / 当前用户
 * - MFA 设置 / 验证 / 关闭
 * 后端不可达时降级到 Mock 数据
 */
import { get, post } from '@/utils/request';
import type { LoginParams, LoginResult, UserInfo, MfaVerifyParams, MfaSetupResult, ApiResponse } from '@/types';

/** Mock 预置账号 */
const MOCK_ACCOUNTS: Record<string, { password: string; user: UserInfo; mfaEnabled: boolean }> = {
  admin: {
    password: 'admin123',
    mfaEnabled: true,
    user: {
      id: '1',
      username: 'admin',
      nickname: '红方管理员',
      email: 'admin@redteam.local',
      avatar: '',
      role: 'admin',
      createTime: '2026-01-01 00:00:00',
      mfaEnabled: true,
    },
  },
  analyst: {
    password: 'analyst123',
    mfaEnabled: false,
    user: {
      id: '2',
      username: 'analyst',
      nickname: '红队分析师',
      email: 'analyst@redteam.local',
      avatar: '',
      role: 'analyst',
      createTime: '2026-02-01 00:00:00',
      mfaEnabled: false,
    },
  },
};

const MOCK_TOKEN = 'mock-token-admin-2026';
const MOCK_MFA_TOKEN = 'mock-mfa-token-';

/**
 * 用户登录
 * - MFA 未启用：直接返回 token + user
 * - MFA 已启用：返回 mfaRequired + mfaToken，需调用 mfaVerify
 */
export async function login(params: LoginParams): Promise<ApiResponse<LoginResult>> {
  try {
    return await post<LoginResult>('/auth/login', params as unknown as Record<string, unknown>);
  } catch {
    const account = MOCK_ACCOUNTS[params.username];
    if (account && account.password === params.password) {
      if (account.mfaEnabled) {
        const mfaToken = MOCK_MFA_TOKEN + Date.now();
        return {
          code: 200,
          message: 'success',
          data: { mfaRequired: true, mfaToken },
        };
      }
      return {
        code: 200,
        message: 'success',
        data: { token: MOCK_TOKEN, user: account.user },
      };
    }
    return { code: 401, message: '用户名或密码错误', data: null as unknown as LoginResult };
  }
}

/**
 * MFA 验证（两阶段登录第二步）
 */
export async function mfaVerify(params: MfaVerifyParams): Promise<ApiResponse<LoginResult>> {
  try {
    return await post<LoginResult>('/auth/mfa/verify', params as unknown as Record<string, unknown>);
  } catch {
    // Mock：任意 6 位数字验证码均通过
    if (/^\d{6}$/.test(params.code)) {
      return {
        code: 200,
        message: 'success',
        data: { token: MOCK_TOKEN, user: MOCK_ACCOUNTS.admin.user },
      };
    }
    return { code: 401, message: '验证码错误', data: null as unknown as LoginResult };
  }
}

/**
 * MFA 设置（绑定 TOTP）
 */
export async function mfaSetup(): Promise<ApiResponse<MfaSetupResult>> {
  try {
    return await post<MfaSetupResult>('/auth/mfa/setup');
  } catch {
    return {
      code: 200,
      message: 'success',
      data: {
        secret: 'JBSWY3DPEHPK3PXP',
        otpauthUrl: 'otpauth://totp/RedTeam:admin?secret=JBSWY3DPEHPK3PXP&issuer=RedTeam',
        qrCodeUrl: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4=',
        backupCodes: ['12345678', '87654321', '11112222', '33334444', '55556666', '77778888'],
      },
    };
  }
}

/**
 * MFA 关闭
 */
export async function mfaDisable(password: string): Promise<ApiResponse<void>> {
  try {
    return await post<void>('/auth/mfa/disable', { password });
  } catch {
    return { code: 200, message: 'success', data: undefined as unknown as void };
  }
}

/**
 * 用户登出
 */
export async function logout(): Promise<ApiResponse<void>> {
  try {
    return await post<void>('/auth/logout');
  } catch {
    return { code: 200, message: 'success', data: undefined as unknown as void };
  }
}

/**
 * 获取当前用户信息
 */
export async function getCurrentUser(): Promise<ApiResponse<UserInfo>> {
  try {
    return await get<UserInfo>('/auth/current');
  } catch {
    return {
      code: 200,
      message: 'success',
      data: MOCK_ACCOUNTS.admin.user,
    };
  }
}

/**
 * 刷新 Token
 */
export async function refreshToken(): Promise<ApiResponse<{ token: string }>> {
  try {
    return await post<{ token: string }>('/auth/refresh');
  } catch {
    return { code: 200, message: 'success', data: { token: MOCK_TOKEN } };
  }
}

/**
 * 修改密码
 */
export function changePassword(data: {
  oldPassword: string;
  newPassword: string;
}): Promise<ApiResponse<void>> {
  return post<void>('/auth/change-password', data);
}
