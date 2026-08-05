/**
 * 应用运维权限 Hook
 * 基于用户角色判断运维操作权限（对齐上游方案 §10 权限模型）
 * 现有用户角色为单角色 string（UserInfo.role）
 */
import { useUserStore } from '@/stores';
import type { UserInfo } from '@/types';

/** 角色权限映射（对齐 ops-monitoring-product-design.md §10.1） */
const ROLE_PERM: Record<string, string[]> = {
  SRE: ['system', 'heal', 'alert', 'logs'],
  DBA: ['system', 'middleware', 'logs'],
  PlatformAdmin: ['*'],
  SpaceOwner: ['data', 'heal:self', 'report:self', 'ticket:apply', 'config:view'],
  DataGovernance: ['consistency', 'lifecycle', 'heal', 'report'],
  SecurityEngineer: ['security', 'export:approve', 'alert'],
  Viewer: ['view'],
};

export function useOpsPermission() {
  const user = useUserStore((s) => s.user) as (UserInfo & { role: string }) | null;
  const role = user?.role ?? '';
  const perms = ROLE_PERM[role] ?? [];

  /** 判断是否拥有某权限（* 为通配） */
  const can = (perm: string): boolean => perms.includes('*') || perms.includes(perm);

  /** 是否为平台管理员（全权限） */
  const isPlatformAdmin = perms.includes('*');

  /** 是否可跨空间操作 */
  const canCrossSpace = isPlatformAdmin || role === 'DataGovernance' || role === 'SRE';

  return { can, role, isPlatformAdmin, canCrossSpace, perms };
}
