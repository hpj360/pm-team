/**
 * 认证相关Hook
 */

import { useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { message } from 'antd';
import { useUserStore } from '@/stores';
import { login as loginApi, logout as logoutApi, getCurrentUser } from '@/services';
import type { LoginParams } from '@/types';

export function useAuth() {
  const navigate = useNavigate();
  const { user, token, isLoggedIn, setUser, setToken, login: setLogin, logout: setLogout } = useUserStore();

  // 登录
  const login = useCallback(async (params: LoginParams) => {
    try {
      const res = await loginApi(params);
      if (res.code === 200 || res.code === 0) {
        setLogin(res.data.user, res.data.token);
        message.success('登录成功');
        navigate('/');
        return true;
      }
      message.error(res.message || '登录失败');
      return false;
    } catch (error) {
      message.error('登录失败，请稍后重试');
      return false;
    }
  }, [setLogin, navigate]);

  // 登出
  const logout = useCallback(async () => {
    try {
      await logoutApi();
    } catch {
      // 忽略登出错误
    } finally {
      setLogout();
      navigate('/login');
      message.success('已退出登录');
    }
  }, [setLogout, navigate]);

  // 获取当前用户信息
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

  // 检查登录状态
  useEffect(() => {
    if (token && !user) {
      fetchCurrentUser();
    }
  }, [token, user, fetchCurrentUser]);

  return {
    user,
    token,
    isLoggedIn,
    login,
    logout,
    fetchCurrentUser,
  };
}
