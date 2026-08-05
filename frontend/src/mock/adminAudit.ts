/**
 * Mock 数据 - 审计日志
 */
import type { AuditLogItem } from '@/types';

const actions: AuditLogItem['action'][] = ['login', 'logout', 'create', 'update', 'delete', 'export', 'import', 'execute'];
const resources = ['/files', '/search', '/ioc', '/analyze', '/admin/users', '/admin/yara', '/admin/config', '/redteam/arsenal'];
const users = ['admin', 'redteam_lead', 'redteam_web', 'redteam_ot', 'analyst_01', 'viewer'];
const detailsMap: Record<string, string[]> = {
  login: ['使用账号密码登录', 'MFA 验证通过', '登录失败：密码错误'],
  logout: ['主动登出'],
  create: ['创建文件记录', '创建 YARA 规则', '新建用户'],
  update: ['更新文件标签', '修改系统配置', '更新用户角色'],
  delete: ['删除文件', '删除 YARA 规则'],
  export: ['导出 IOC 列表', '导出审计日志', '导出文件分析报告'],
  import: ['导入 YARA 规则集', '导入威胁情报包'],
  execute: ['执行文件解析任务', '执行 YARA 扫描', '执行 SQL 检测'],
};

function pick<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

function generateLog(index: number): AuditLogItem {
  const action = pick(actions);
  const resource = pick(resources);
  const user = pick(users);
  const success = Math.random() > 0.1;
  const date = new Date();
  date.setHours(date.getHours() - index * 2 - Math.floor(Math.random() * 2));
  return {
    id: `al_${index.toString().padStart(5, '0')}`,
    userId: `u_${user}`,
    username: user,
    action,
    resource,
    resourceId: `${resource.replace(/\//g, '')}_${index}`,
    detail: pick(detailsMap[action]),
    ip: `10.0.${Math.floor(Math.random() * 5)}.${Math.floor(Math.random() * 254) + 1}`,
    userAgent: pick(['Chrome/126', 'Firefox/127', 'Edge/126', 'curl/8.0']),
    status: success ? 'success' : 'failed',
    costMs: 20 + Math.floor(Math.random() * 200),
    createdAt: date.toISOString(),
  };
}

/** 审计日志列表（80 条） */
export const mockAuditLogs: AuditLogItem[] = Array.from({ length: 80 }, (_, i) => generateLog(i + 1));

/** 分页查询审计日志 */
export function getMockAuditLogs(params: {
  username?: string;
  action?: AuditLogItem['action'];
  startTime?: string;
  endTime?: string;
  page: number;
  pageSize: number;
}): { list: AuditLogItem[]; total: number } {
  let list = [...mockAuditLogs];
  if (params.username) {
    const kw = params.username.toLowerCase();
    list = list.filter((l) => l.username.toLowerCase().includes(kw));
  }
  if (params.action) list = list.filter((l) => l.action === params.action);
  if (params.startTime) list = list.filter((l) => l.createdAt >= params.startTime!);
  if (params.endTime) list = list.filter((l) => l.createdAt <= params.endTime!);
  list.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
  const total = list.length;
  const start = (params.page - 1) * params.pageSize;
  return { list: list.slice(start, start + params.pageSize), total };
}

export default { mockAuditLogs, getMockAuditLogs };
