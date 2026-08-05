/**
 * Mock 数据 - 后台角色
 */
import type { AdminRole } from '@/types';

export const mockAdminRoles: AdminRole[] = [
  {
    id: 'r1',
    name: '超级管理员',
    code: 'super_admin',
    description: '拥有系统全部权限，默认继承所有权限',
    permissionIds: ['p_all'],
    userCount: 1,
    builtin: true,
    createTime: '2026-01-01T00:00:00Z',
  },
  {
    id: 'r2',
    name: '红队队长',
    code: 'redteam_lead',
    description: '红队队长，可指挥任务、查看全量数据',
    permissionIds: ['p_dashboard', 'p_files', 'p_search', 'p_analyze', 'p_ioc', 'p_redteam', 'p_monitor'],
    userCount: 1,
    builtin: false,
    createTime: '2026-02-01T00:00:00Z',
  },
  {
    id: 'r3',
    name: '红队队员',
    code: 'redteam_member',
    description: '红队队员，可上传/分析文件并参与作战',
    permissionIds: ['p_dashboard', 'p_files', 'p_search', 'p_analyze', 'p_ioc', 'p_redteam'],
    userCount: 2,
    builtin: false,
    createTime: '2026-02-05T00:00:00Z',
  },
  {
    id: 'r4',
    name: '分析师',
    code: 'analyst',
    description: '查看与分析数据，无管理权限',
    permissionIds: ['p_dashboard', 'p_files', 'p_search', 'p_analyze', 'p_ioc'],
    userCount: 2,
    builtin: false,
    createTime: '2026-03-01T00:00:00Z',
  },
  {
    id: 'r5',
    name: '审计员',
    code: 'auditor',
    description: '只读权限，可查看审计日志',
    permissionIds: ['p_dashboard', 'p_audit'],
    userCount: 1,
    builtin: false,
    createTime: '2026-04-01T00:00:00Z',
  },
];

export default { mockAdminRoles };
