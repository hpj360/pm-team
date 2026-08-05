/**
 * 用户状态管理
 * - 登录 / 登出
 * - MFA 两阶段登录状态
 */
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserInfo } from '@/types';

interface UserState {
  user: UserInfo | null;
  token: string | null;
  isLoggedIn: boolean;
  /** MFA 两阶段登录临时 token */
  mfaToken: string | null;
  /** 是否处于 MFA 验证阶段 */
  mfaPending: boolean;

  setUser: (user: UserInfo | null) => void;
  setToken: (token: string | null) => void;
  setMfaToken: (token: string | null) => void;
  setMfaPending: (pending: boolean) => void;
  login: (user: UserInfo, token: string) => void;
  logout: () => void;
  updateUser: (user: Partial<UserInfo>) => void;
}

export const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isLoggedIn: false,
      mfaToken: null,
      mfaPending: false,

      setUser: (user) => set({ user, isLoggedIn: !!user }),

      setToken: (token) => {
        if (token) {
          localStorage.setItem('token', token);
        } else {
          localStorage.removeItem('token');
        }
        set({ token });
      },

      setMfaToken: (mfaToken) => set({ mfaToken }),
      setMfaPending: (mfaPending) => set({ mfaPending }),

      login: (user, token) => {
        localStorage.setItem('token', token);
        set({
          user,
          token,
          isLoggedIn: true,
          mfaToken: null,
          mfaPending: false,
        });
      },

      logout: () => {
        localStorage.removeItem('token');
        set({
          user: null,
          token: null,
          isLoggedIn: false,
          mfaToken: null,
          mfaPending: false,
        });
      },

      updateUser: (userData) =>
        set((state) => ({
          user: state.user ? { ...state.user, ...userData } : null,
        })),
    }),
    {
      name: 'user-storage',
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isLoggedIn: state.isLoggedIn,
      }),
    },
  ),
);
