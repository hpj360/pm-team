/**
 * 单元测试：应用运维权限 Hook（src/hooks/useOpsPermission.ts）
 * - 各角色权限判断
 * - 通配权限
 * - 跨空间操作权限
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import { useUserStore } from '@/stores';
import type { UserInfo } from '@/types';

const makeUser = (role: string): UserInfo & { role: string } => ({
  id: 'u1', username: 'tester', nickname: '测试用户', email: 't@e.com',
  role, createTime: '2026-01-01',
});

describe('useOpsPermission', () => {
  beforeEach(() => {
    useUserStore.getState().logout();
  });

  it('未登录用户：无任何权限', () => {
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.role).toBe('');
    expect(result.current.perms).toEqual([]);
    expect(result.current.isPlatformAdmin).toBe(false);
    expect(result.current.canCrossSpace).toBe(false);
    expect(result.current.can('heal')).toBe(false);
    expect(result.current.can('view')).toBe(false);
  });

  it('PlatformAdmin：全权限（* 通配）', () => {
    useUserStore.getState().setUser(makeUser('PlatformAdmin'));
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.isPlatformAdmin).toBe(true);
    expect(result.current.can('heal')).toBe(true);
    expect(result.current.can('any-unknown-perm')).toBe(true);
    expect(result.current.canCrossSpace).toBe(true);
  });

  it('SRE：拥有 system/heal/alert/logs 权限', () => {
    useUserStore.getState().setUser(makeUser('SRE'));
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.can('heal')).toBe(true);
    expect(result.current.can('system')).toBe(true);
    expect(result.current.can('alert')).toBe(true);
    expect(result.current.can('consistency')).toBe(false);
    expect(result.current.canCrossSpace).toBe(true);
  });

  it('DBA：拥有 system/middleware/logs 权限，不可跨空间', () => {
    useUserStore.getState().setUser(makeUser('DBA'));
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.can('system')).toBe(true);
    expect(result.current.can('middleware')).toBe(true);
    expect(result.current.can('heal')).toBe(false);
    expect(result.current.canCrossSpace).toBe(false);
  });

  it('DataGovernance：拥有 consistency/lifecycle/heal/report 权限，可跨空间', () => {
    useUserStore.getState().setUser(makeUser('DataGovernance'));
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.can('consistency')).toBe(true);
    expect(result.current.can('lifecycle')).toBe(true);
    expect(result.current.can('heal')).toBe(true);
    expect(result.current.can('report')).toBe(true);
    expect(result.current.canCrossSpace).toBe(true);
  });

  it('SpaceOwner：拥有 heal:self / report:self / ticket:apply，不可跨空间', () => {
    useUserStore.getState().setUser(makeUser('SpaceOwner'));
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.can('heal:self')).toBe(true);
    expect(result.current.can('report:self')).toBe(true);
    expect(result.current.can('ticket:apply')).toBe(true);
    expect(result.current.can('config:view')).toBe(true);
    expect(result.current.can('heal')).toBe(false); // 注意：heal:self != heal
    expect(result.current.canCrossSpace).toBe(false);
  });

  it('SecurityEngineer：拥有 security / export:approve / alert 权限', () => {
    useUserStore.getState().setUser(makeUser('SecurityEngineer'));
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.can('security')).toBe(true);
    expect(result.current.can('export:approve')).toBe(true);
    expect(result.current.can('alert')).toBe(true);
    expect(result.current.can('heal')).toBe(false);
  });

  it('Viewer：仅拥有 view 权限', () => {
    useUserStore.getState().setUser(makeUser('Viewer'));
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.can('view')).toBe(true);
    expect(result.current.can('heal')).toBe(false);
    expect(result.current.can('consistency')).toBe(false);
  });

  it('未知角色：无任何权限', () => {
    useUserStore.getState().setUser(makeUser('UnknownRole'));
    const { result } = renderHook(() => useOpsPermission());
    expect(result.current.perms).toEqual([]);
    expect(result.current.can('view')).toBe(false);
  });
});
