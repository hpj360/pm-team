/**
 * 单元测试：认证 Store（src/stores/user.ts 中的认证相关 actions）
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { useUserStore } from '@/stores';
import type { UserInfo } from '@/types';

const mockUser: UserInfo = {
  id: 'u1',
  username: 'admin',
  nickname: '红方管理员',
  email: 'admin@redteam.local',
  role: 'admin',
  createTime: '2026-01-01 00:00:00',
};

describe('useUserStore - 认证', () => {
  beforeEach(() => {
    // 重置 store 到初始状态
    useUserStore.getState().logout();
    localStorage.clear();
  });

  describe('setUser', () => {
    it('设置用户后 isLoggedIn 为 true', () => {
      useUserStore.getState().setUser(mockUser);
      expect(useUserStore.getState().user).toEqual(mockUser);
      expect(useUserStore.getState().isLoggedIn).toBe(true);
    });

    it('传入 null 重置用户与登录态', () => {
      useUserStore.getState().setUser(mockUser);
      useUserStore.getState().setUser(null);
      expect(useUserStore.getState().user).toBeNull();
      expect(useUserStore.getState().isLoggedIn).toBe(false);
    });
  });

  describe('setToken', () => {
    it('设置 token 时同步写入 localStorage', () => {
      useUserStore.getState().setToken('abc-token');
      expect(useUserStore.getState().token).toBe('abc-token');
      expect(localStorage.getItem('token')).toBe('abc-token');
    });

    it('传 null 清除 token 与 localStorage', () => {
      useUserStore.getState().setToken('abc-token');
      useUserStore.getState().setToken(null);
      expect(useUserStore.getState().token).toBeNull();
      expect(localStorage.getItem('token')).toBeNull();
    });
  });

  describe('setMfaToken / setMfaPending', () => {
    it('setMfaToken 设置 mfaToken', () => {
      useUserStore.getState().setMfaToken('mfa-tmp');
      expect(useUserStore.getState().mfaToken).toBe('mfa-tmp');
    });

    it('setMfaPending 切换 mfaPending 状态', () => {
      useUserStore.getState().setMfaPending(true);
      expect(useUserStore.getState().mfaPending).toBe(true);
      useUserStore.getState().setMfaPending(false);
      expect(useUserStore.getState().mfaPending).toBe(false);
    });
  });

  describe('login', () => {
    it('login 同时设置 user、token、isLoggedIn', () => {
      useUserStore.getState().login(mockUser, 'login-token');
      expect(useUserStore.getState().user).toEqual(mockUser);
      expect(useUserStore.getState().token).toBe('login-token');
      expect(useUserStore.getState().isLoggedIn).toBe(true);
      expect(localStorage.getItem('token')).toBe('login-token');
    });

    it('login 重置 MFA 临时状态', () => {
      useUserStore.getState().setMfaToken('tmp');
      useUserStore.getState().setMfaPending(true);
      useUserStore.getState().login(mockUser, 'login-token');
      expect(useUserStore.getState().mfaToken).toBeNull();
      expect(useUserStore.getState().mfaPending).toBe(false);
    });
  });

  describe('logout', () => {
    it('logout 清空所有认证状态', () => {
      useUserStore.getState().login(mockUser, 'login-token');
      useUserStore.getState().logout();
      expect(useUserStore.getState().user).toBeNull();
      expect(useUserStore.getState().token).toBeNull();
      expect(useUserStore.getState().isLoggedIn).toBe(false);
      expect(useUserStore.getState().mfaToken).toBeNull();
      expect(useUserStore.getState().mfaPending).toBe(false);
      expect(localStorage.getItem('token')).toBeNull();
    });

    it('logout 在未登录状态调用不抛错', () => {
      expect(() => useUserStore.getState().logout()).not.toThrow();
    });
  });

  describe('updateUser', () => {
    it('部分更新用户字段', () => {
      useUserStore.getState().setUser(mockUser);
      useUserStore.getState().updateUser({ nickname: '新昵称' });
      expect(useUserStore.getState().user?.nickname).toBe('新昵称');
      expect(useUserStore.getState().user?.username).toBe('admin');
    });

    it('user 为 null 时不抛错', () => {
      useUserStore.getState().setUser(null);
      useUserStore.getState().updateUser({ nickname: 'x' });
      expect(useUserStore.getState().user).toBeNull();
    });
  });
});
