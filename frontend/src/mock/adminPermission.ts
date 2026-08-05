/**
 * Mock 数据 - 后台权限
 */
import type { AdminPermission } from '@/types';

export const mockAdminPermissions: AdminPermission[] = [
  { id: 'p_all', name: '全部权限', code: '*', type: 'action', resource: '*', description: '通配权限', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_dashboard', name: '工作台', code: 'dashboard:view', type: 'menu', parentId: 'p_root', resource: '/dashboard', description: '查看工作台', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_files', name: '文件管理', code: 'files:view', type: 'menu', parentId: 'p_root', resource: '/files', description: '查看文件列表', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_files_upload', name: '文件上传', code: 'files:upload', type: 'action', parentId: 'p_files', resource: '/files/upload', description: '上传新文件', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_files_delete', name: '文件删除', code: 'files:delete', type: 'action', parentId: 'p_files', resource: '/files/*', description: '删除文件', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_search', name: '文件检索', code: 'search:view', type: 'menu', parentId: 'p_root', resource: '/search', description: '文件全文检索', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_analyze', name: '文件分析', code: 'analyze:view', type: 'menu', parentId: 'p_root', resource: '/analyze', description: '文件深度分析', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_ioc', name: '威胁情报', code: 'ioc:view', type: 'menu', parentId: 'p_root', resource: '/ioc', description: 'IOC 中心', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_redteam', name: '红方作战', code: 'redteam:view', type: 'menu', parentId: 'p_root', resource: '/redteam/*', description: '红方模块访问', createTime: '2026-02-01T00:00:00Z' },
  { id: 'p_monitor', name: '监控看板', code: 'monitor:view', type: 'menu', parentId: 'p_root', resource: '/monitor', description: '系统监控', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_admin', name: '后台管理', code: 'admin:view', type: 'menu', parentId: 'p_root', resource: '/admin/*', description: '后台管理访问', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_admin_user', name: '用户管理', code: 'admin:user', type: 'api', parentId: 'p_admin', resource: '/api/admin/users', description: '用户 CRUD', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_admin_role', name: '角色管理', code: 'admin:role', type: 'api', parentId: 'p_admin', resource: '/api/admin/roles', description: '角色 CRUD', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_admin_yara', name: 'YARA 管理', code: 'admin:yara', type: 'api', parentId: 'p_admin', resource: '/api/admin/yara', description: 'YARA 规则管理', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_admin_config', name: '系统配置', code: 'admin:config', type: 'api', parentId: 'p_admin', resource: '/api/admin/config', description: '系统配置修改', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_audit', name: '审计日志', code: 'audit:view', type: 'menu', parentId: 'p_root', resource: '/admin/audit-log', description: '审计日志查看', createTime: '2026-01-01T00:00:00Z' },
  { id: 'p_root', name: '根权限', code: 'root', type: 'menu', resource: '/', description: '根节点', createTime: '2026-01-01T00:00:00Z' },
];

export default { mockAdminPermissions };
