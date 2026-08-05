/**
 * Mock 数据 - 协同作战
 */
import type { CollaborationTask, TeamMember, CollaborationMessage } from '@/types';

/** 团队成员 */
export const mockTeamMembers: TeamMember[] = [
  { id: 'm1', name: '王浩然', role: '红队队长', online: true, lastActive: '2026-07-27T09:00:00Z', currentTask: '指挥 MetaTech 渗透' },
  { id: 'm2', name: '陈思齐', role: 'Web 渗透', online: true, lastActive: '2026-07-27T09:05:00Z', currentTask: 'NovaCloud API 越权' },
  { id: 'm3', name: '刘晓东', role: '内网 / 横向', online: true, lastActive: '2026-07-27T09:10:00Z', currentTask: '横向至 DB 服务器' },
  { id: 'm4', name: '赵敏', role: '社工 / 钓鱼', online: false, lastActive: '2026-07-26T18:00:00Z' },
  { id: 'm5', name: '孙磊', role: '工控 / OT', online: true, lastActive: '2026-07-27T09:15:00Z', currentTask: 'SkyNet SCADA 测试' },
  { id: 'm6', name: '林浩', role: '取证 / 报告', online: false, lastActive: '2026-07-26T20:00:00Z' },
  { id: 'm7', name: '周宇翔', role: '红队实习生', online: true, lastActive: '2026-07-27T09:20:00Z', currentTask: '侦察资产收集' },
];

/** 协同任务（Kanban） */
export const mockCollaborationTasks: CollaborationTask[] = [
  {
    id: 'ct001',
    title: 'MetaTech 边界资产测绘',
    description: '完成外网端口扫描、子域枚举、CDN 识别。',
    status: 'done',
    priority: 'high',
    assignee: '王浩然',
    assigneeAvatar: '',
    dueDate: '2026-05-15',
    tags: ['侦察', 'MetaTech'],
    comments: 5,
    attachments: 2,
  },
  {
    id: 'ct002',
    title: 'Tomcat 后台弱口令验证',
    description: '尝试 admin/123456、tomcat/tomcat 等弱口令登录后台。',
    status: 'done',
    priority: 'urgent',
    assignee: '陈思齐',
    dueDate: '2026-06-05',
    tags: ['Web', '弱口令'],
    comments: 3,
    attachments: 1,
  },
  {
    id: 'ct003',
    title: 'DB 服务器横向',
    description: '基于 actuator 泄露凭证横向至 MySQL 主机。',
    status: 'doing',
    priority: 'high',
    assignee: '刘晓东',
    dueDate: '2026-06-20',
    tags: ['内网', '横向'],
    comments: 8,
    attachments: 3,
  },
  {
    id: 'ct004',
    title: '钓鱼邮件模板设计',
    description: '针对财务部门设计 HR 通知类钓鱼邮件。',
    status: 'doing',
    priority: 'medium',
    assignee: '赵敏',
    dueDate: '2026-07-15',
    tags: ['社工', '钓鱼'],
    comments: 2,
    attachments: 1,
  },
  {
    id: 'ct005',
    title: 'SkyNet OT 协议分析',
    description: '分析 Modbus 协议交互并验证指令下发可行性。',
    status: 'doing',
    priority: 'high',
    assignee: '孙磊',
    dueDate: '2026-07-25',
    tags: ['OT', 'Modbus'],
    comments: 6,
    attachments: 4,
  },
  {
    id: 'ct006',
    title: 'NovaCloud API 越权 PoC 撰写',
    description: '编写 /v1/users 越权 PoC 与影响评估。',
    status: 'todo',
    priority: 'urgent',
    assignee: '陈思齐',
    dueDate: '2026-07-30',
    tags: ['API', '云'],
    comments: 1,
    attachments: 0,
  },
  {
    id: 'ct007',
    title: '渗透报告初稿撰写',
    description: '汇总 MetaTech 阶段性成果并产出初版报告。',
    status: 'todo',
    priority: 'medium',
    assignee: '林浩',
    dueDate: '2026-08-05',
    tags: ['报告'],
    comments: 0,
    attachments: 0,
  },
  {
    id: 'ct008',
    title: 'GovCity 政务云侦察',
    description: '资产测绘与端口扫描。',
    status: 'todo',
    priority: 'medium',
    assignee: '周宇翔',
    dueDate: '2026-08-10',
    tags: ['侦察', '政务'],
    comments: 0,
    attachments: 0,
  },
  {
    id: 'ct009',
    title: '永恒之蓝利用与持久化',
    description: '在 WinXP 上位机利用 MS17-010 验证持久化方案。',
    status: 'done',
    priority: 'urgent',
    assignee: '孙磊',
    dueDate: '2026-06-25',
    tags: ['OT', 'MS17-010'],
    comments: 4,
    attachments: 1,
  },
  {
    id: 'ct010',
    title: '内网 C2 隐蔽性测试',
    description: '验证 Cobalt Strike Malleable C2 隐蔽性，避免 EDR 检测。',
    status: 'blocked',
    priority: 'high',
    assignee: '刘晓东',
    dueDate: '2026-07-20',
    tags: ['C2', 'EDR'],
    comments: 7,
    attachments: 2,
  },
];

/** 实时消息流 */
export const mockCollaborationMessages: CollaborationMessage[] = [
  { id: 'msg1', sender: '王浩然', content: '今天 MetaTech 阶段任务推进，大家同步进展。', time: '2026-07-27T09:00:00Z', isMine: false },
  { id: 'msg2', sender: '陈思齐', content: 'NovaCloud 的 API 越权我已写完 PoC，正在整理报告。', time: '2026-07-27T09:05:00Z', isMine: false },
  { id: 'msg3', sender: '我', content: 'DB 服务器横向已打通，凭证放在加密压缩包里。', time: '2026-07-27T09:08:00Z', isMine: true },
  { id: 'msg4', sender: '孙磊', content: 'SkyNet 那边 Modbus 测试基本通了，要小心不要触发告警。', time: '2026-07-27T09:10:00Z', isMine: false },
  { id: 'msg5', sender: '王浩然', content: '收到，明天开复盘会，请各自准备材料。', time: '2026-07-27T09:15:00Z', isMine: false },
];

/** 按状态分组任务 */
export function groupTasksByStatus(): Record<string, CollaborationTask[]> {
  return {
    todo: mockCollaborationTasks.filter((t) => t.status === 'todo'),
    doing: mockCollaborationTasks.filter((t) => t.status === 'doing'),
    done: mockCollaborationTasks.filter((t) => t.status === 'done'),
    blocked: mockCollaborationTasks.filter((t) => t.status === 'blocked'),
  };
}

export default {
  mockTeamMembers,
  mockCollaborationTasks,
  mockCollaborationMessages,
  groupTasksByStatus,
};
