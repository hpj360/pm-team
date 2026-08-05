/**
 * Mock 数据 - 后台用户
 */
import type { AdminUser } from '@/types';

/** 用户列表 */
export const mockAdminUsers: AdminUser[] = [
  {
    id: 'u001',
    username: 'admin',
    nickname: '系统管理员',
    email: 'admin@redteam.local',
    phone: '13800138000',
    roleIds: ['r1'],
    dept: '运维部',
    status: 'active',
    lastLoginAt: '2026-07-27T08:30:00Z',
    lastLoginIp: '10.0.0.1',
    createTime: '2026-01-01T00:00:00Z',
  },
  {
    id: 'u002',
    username: 'redteam_lead',
    nickname: '王浩然',
    email: 'wang.hr@redteam.local',
    phone: '13900139001',
    roleIds: ['r2'],
    dept: '红队',
    status: 'active',
    lastLoginAt: '2026-07-27T09:00:00Z',
    lastLoginIp: '10.0.0.10',
    createTime: '2026-02-01T00:00:00Z',
  },
  {
    id: 'u003',
    username: 'redteam_web',
    nickname: '陈思齐',
    email: 'chen.sq@redteam.local',
    phone: '13900139002',
    roleIds: ['r3'],
    dept: '红队',
    status: 'active',
    lastLoginAt: '2026-07-27T09:05:00Z',
    lastLoginIp: '10.0.0.11',
    createTime: '2026-02-05T00:00:00Z',
  },
  {
    id: 'u004',
    username: 'redteam_ot',
    nickname: '孙磊',
    email: 'sun.lei@redteam.local',
    roleIds: ['r3'],
    dept: '红队',
    status: 'active',
    lastLoginAt: '2026-07-27T09:15:00Z',
    lastLoginIp: '10.0.0.12',
    createTime: '2026-02-10T00:00:00Z',
  },
  {
    id: 'u005',
    username: 'analyst_01',
    nickname: '林浩',
    email: 'lin.hao@redteam.local',
    roleIds: ['r4'],
    dept: '分析组',
    status: 'disabled',
    lastLoginAt: '2026-07-26T20:00:00Z',
    lastLoginIp: '10.0.0.20',
    createTime: '2026-03-01T00:00:00Z',
  },
  {
    id: 'u006',
    username: 'viewer',
    nickname: '只读访客',
    email: 'viewer@redteam.local',
    roleIds: ['r5'],
    dept: '审计部',
    status: 'locked',
    lastLoginAt: '2026-07-20T10:00:00Z',
    lastLoginIp: '10.0.0.30',
    createTime: '2026-04-01T00:00:00Z',
  },
  {
    id: 'u007',
    username: 'redteam_intern',
    nickname: '周宇翔',
    email: 'zhou.yx@redteam.local',
    roleIds: ['r4'],
    dept: '红队',
    status: 'active',
    lastLoginAt: '2026-07-27T09:20:00Z',
    lastLoginIp: '10.0.0.13',
    createTime: '2026-05-01T00:00:00Z',
  },
];

/** 分页查询 */
export function getMockAdminUserList(params: {
  keyword?: string;
  status?: AdminUser['status'];
  page: number;
  pageSize: number;
}): { list: AdminUser[]; total: number } {
  let list = [...mockAdminUsers];
  if (params.keyword) {
    const kw = params.keyword.toLowerCase();
    list = list.filter(
      (u) =>
        u.username.toLowerCase().includes(kw) ||
        u.nickname.toLowerCase().includes(kw) ||
        u.email.toLowerCase().includes(kw),
    );
  }
  if (params.status) list = list.filter((u) => u.status === params.status);
  const total = list.length;
  const start = (params.page - 1) * params.pageSize;
  return { list: list.slice(start, start + params.pageSize), total };
}

export default {
  mockAdminUsers,
  getMockAdminUserList,
};
