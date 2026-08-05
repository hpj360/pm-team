/**
 * 认证相关 Hook
 * - 登录（含 MFA 两阶段）
 * - 登出
 * - 当前用户
 */
import { useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { message } from 'antd';
import { useUserStore } from '@/stores';
import {
  login as loginApi,
  logout as logoutApi,
  getCurrentUser,
  mfaVerify as mfaVerifyApi,
} from '@/services';
import type { LoginParams, MfaVerifyParams } from '@/types';

export function useAuth() {
  const navigate = useNavigate();
  const {
    user,
    token,
    isLoggedIn,
    mfaToken,
    mfaPending,
    setUser,
    setMfaToken,
    setMfaPending,
    login: setLogin,
    logout: setLogout,
  } = useUserStore();

  /**
   * 第一步：用户名密码登录
   * - 若账号启用了 MFA，会进入 MFA 验证阶段
   * - 否则直接登录成功
   */
  const login = useCallback(
    async (params: LoginParams): Promise<boolean> => {
      try {
        const res = await loginApi(params);
        if (res.code !== 200 && res.code !== 0) {
          message.error(res.message || '登录失败');
          return false;
        }
        const data = res.data;
        if (data.mfaRequired) {
          setMfaToken(data.mfaToken ?? null);
          setMfaPending(true);
          message.info('请输入 MFA 验证码');
          return false;
        }
        if (data.token && data.user) {
          setLogin(data.user, data.token);
          message.success('登录成功');
          navigate('/');
          return true;
        }
        message.error('登录响应异常');
        return false;
      } catch {
        message.error('登录失败，请稍后重试');
        return false;
      }
    },
    [setLogin, setMfaToken, setMfaPending, navigate],
  );

  /**
   * 第二步：MFA 验证
   */
  const verifyMfa = useCallback(
    async (params: MfaVerifyParams): Promise<boolean> => {
      try {
        const res = await mfaVerifyApi(params);
        if (res.code !== 200 && res.code !== 0) {
          message.error(res.message || 'MFA 验证失败');
          return false;
        }
        const data = res.data;
        if (data.token && data.user) {
          setLogin(data.user, data.token);
          message.success('登录成功');
          navigate('/');
          return true;
        }
        message.error('MFA 响应异常');
        return false;
      } catch {
        message.error('MFA 验证失败，请稍后重试');
        return false;
      }
    },
    [setLogin, navigate],
  );

  /** 取消 MFA 流程，返回到登录页 */
  const cancelMfa = useCallback(() => {
    setMfaToken(null);
    setMfaPending(false);
  }, [setMfaToken, setMfaPending]);

  /** 登出 */
  const logout = useCallback(async () => {
    try {
      await logoutApi();
    } catch {
      /* 忽略 */
    } finally {
      setLogout();
      navigate('/login');
      message.success('已退出登录');
    }
  }, [setLogout, navigate]);

  /** 获取当前用户信息 */
  const fetchCurrentUser = useCallback(async () => {
    if (!token) return;
    try {
      const res = await getCurrentUser();
      if (res.code === 200 || res.code === 0) {
        setUser(res.data);
      } else {
        setLogout();
      }
    } catch {
      setLogout();
    }
  }, [token, setUser, setLogout]);

  useEffect(() => {
    if (token && !user) {
      fetchCurrentUser();
    }
  }, [token, user, fetchCurrentUser]);

  return {
    user,
    token,
    isLoggedIn,
    mfaToken,
    mfaPending,
    login,
    verifyMfa,
    cancelMfa,
    logout,
    fetchCurrentUser,
  };
}
