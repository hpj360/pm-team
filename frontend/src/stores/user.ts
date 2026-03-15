/**
 * 用户状态管理
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserInfo } from '@/types';

interface UserState {
  // 状态
  user: UserInfo | null;
  token: string | null;
  isLoggedIn: boolean;
  
  // 操作
  setUser: (user: UserInfo | null) => void;
  setToken: (token: string | null) => void;
  login: (user: UserInfo, token: string) => void;
  logout: () => void;
  updateUser: (user: Partial<UserInfo>) => void;
}

export const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      // 初始状态
      user: null,
      token: null,
      isLoggedIn: false,

      // 设置用户信息
      setUser: (user) => set({ user, isLoggedIn: !!user }),

      // 设置Token
      setToken: (token) => {
        if (token) {
          localStorage.setItem('token', token);
        } else {
          localStorage.removeItem('token');
        }
        set({ token });
      },

      // 登录
      login: (user, token) => {
        localStorage.setItem('token', token);
        set({ user, token, isLoggedIn: true });
      },

      // 登出
      logout: () => {
        localStorage.removeItem('token');
        set({ user: null, token: null, isLoggedIn: false });
      },

      // 更新用户信息
      updateUser: (userData) =>
        set((state) => ({
          user: state.user ? { ...state.user, ...userData } : null,
        })),
    }),
    {
      name: 'user-storage', // localStorage key
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isLoggedIn: state.isLoggedIn,
      }),
    }
  )
);
