/**
 * Mock 数据 - 任务管理（红方协同）
 */
import type { TaskItem, TaskStatus } from '@/types';

/** 任务列表（12 条） */
export const mockTasks: TaskItem[] = [
  {
    id: 'task_001',
    title: 'MetaTech 边界资产测绘',
    description: '完成外网端口扫描、子域枚举、CDN 识别，输出资产清单。',
    status: 'done',
    priority: 'high',
    assignee: '王浩然',
    collaborators: ['周宇翔'],
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    fileIds: ['file_001'],
    fileNames: ['asset_scan_report.pdf'],
    progress: 100,
    startTime: '2026-05-01T00:00:00Z',
    dueDate: '2026-05-15',
    completedAt: '2026-05-14T18:00:00Z',
    tags: ['侦察', 'MetaTech'],
    timeline: [
      { id: 'tl1', time: '2026-05-01 10:00', title: '任务创建', description: '王浩然 创建任务', operator: '王浩然', type: 'create' },
      { id: 'tl2', time: '2026-05-05 14:00', title: '上传报告', description: '周宇翔 上传资产扫描报告', operator: '周宇翔', type: 'upload' },
      { id: 'tl3', time: '2026-05-14 18:00', title: '状态变更', description: '王浩然 标记为已完成', operator: '王浩然', type: 'status_change' },
    ],
    comments: 5,
    attachments: 2,
    createTime: '2026-05-01T10:00:00Z',
    updateTime: '2026-05-14T18:00:00Z',
  },
  {
    id: 'task_002',
    title: 'Tomcat 后台弱口令验证',
    description: '尝试 admin/123456、tomcat/tomcat 等弱口令登录后台。',
    status: 'done',
    priority: 'urgent',
    assignee: '陈思齐',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    progress: 100,
    startTime: '2026-05-20T00:00:00Z',
    dueDate: '2026-06-05',
    completedAt: '2026-06-03T20:00:00Z',
    tags: ['Web', '弱口令'],
    timeline: [
      { id: 'tl1', time: '2026-05-20 09:00', title: '任务创建', description: '陈思齐 创建任务', operator: '陈思齐', type: 'create' },
      { id: 'tl2', time: '2026-06-03 20:00', title: '已完成', description: '成功获取后台权限', operator: '陈思齐', type: 'status_change' },
    ],
    comments: 3,
    attachments: 1,
    createTime: '2026-05-20T09:00:00Z',
    updateTime: '2026-06-03T20:00:00Z',
  },
  {
    id: 'task_003',
    title: 'DB 服务器横向移动',
    description: '基于 actuator 泄露凭证横向至 MySQL 主机，导出 PII 数据样本。',
    status: 'doing',
    priority: 'high',
    assignee: '刘晓东',
    collaborators: ['王浩然'],
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    fileIds: ['file_002'],
    fileNames: ['mysql_dump_sample.xlsx'],
    progress: 60,
    startTime: '2026-06-10T00:00:00Z',
    dueDate: '2026-07-20',
    tags: ['内网', '横向', 'MySQL'],
    timeline: [
      { id: 'tl1', time: '2026-06-10 10:00', title: '任务创建', description: '王浩然 创建任务并指派给刘晓东', operator: '王浩然', type: 'create' },
      { id: 'tl2', time: '2026-06-12 14:00', title: '指派更新', description: '王浩然 加入协作', operator: '王浩然', type: 'assign' },
      { id: 'tl3', time: '2026-07-01 16:00', title: '进度更新', description: '已成功登入 MySQL，正在导出样本', operator: '刘晓东', type: 'comment' },
    ],
    comments: 8,
    attachments: 3,
    createTime: '2026-06-10T10:00:00Z',
    updateTime: '2026-07-15T16:00:00Z',
  },
  {
    id: 'task_004',
    title: '钓鱼邮件模板设计',
    description: '针对财务部门设计 HR 通知类钓鱼邮件，附 Word 宏载荷。',
    status: 'doing',
    priority: 'medium',
    assignee: '赵敏',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    progress: 45,
    startTime: '2026-07-01T00:00:00Z',
    dueDate: '2026-07-30',
    tags: ['社工', '钓鱼'],
    timeline: [
      { id: 'tl1', time: '2026-07-01 10:00', title: '任务创建', description: '赵敏 创建任务', operator: '赵敏', type: 'create' },
      { id: 'tl2', time: '2026-07-10 15:00', title: '进度更新', description: '已完成 3 套模板', operator: '赵敏', type: 'comment' },
    ],
    comments: 2,
    attachments: 1,
    createTime: '2026-07-01T10:00:00Z',
    updateTime: '2026-07-15T15:00:00Z',
  },
  {
    id: 'task_005',
    title: 'SkyNet OT 协议分析',
    description: '分析 Modbus 协议交互并验证指令下发可行性。',
    status: 'doing',
    priority: 'high',
    assignee: '孙磊',
    targetId: 'tgt_002',
    targetName: 'SkyNet 工控',
    progress: 70,
    startTime: '2026-07-05T00:00:00Z',
    dueDate: '2026-07-25',
    tags: ['OT', 'Modbus'],
    timeline: [
      { id: 'tl1', time: '2026-07-05 09:00', title: '任务创建', description: '孙磊 创建任务', operator: '孙磊', type: 'create' },
      { id: 'tl2', time: '2026-07-15 11:00', title: '进度更新', description: 'Modbus 未授权确认', operator: '孙磊', type: 'comment' },
    ],
    comments: 6,
    attachments: 4,
    createTime: '2026-07-05T09:00:00Z',
    updateTime: '2026-07-20T11:00:00Z',
  },
  {
    id: 'task_006',
    title: 'NovaCloud API 越权 PoC 撰写',
    description: '编写 /v1/users 越权 PoC 与影响评估，输出技术报告。',
    status: 'todo',
    priority: 'urgent',
    assignee: '陈思齐',
    targetId: 'tgt_003',
    targetName: 'NovaCloud',
    progress: 0,
    startTime: '2026-07-25T00:00:00Z',
    dueDate: '2026-07-30',
    tags: ['API', '云', 'PoC'],
    timeline: [
      { id: 'tl1', time: '2026-07-25 10:00', title: '任务创建', description: '王浩然 创建任务并指派给陈思齐', operator: '王浩然', type: 'create' },
    ],
    comments: 1,
    attachments: 0,
    createTime: '2026-07-25T10:00:00Z',
    updateTime: '2026-07-25T10:00:00Z',
  },
  {
    id: 'task_007',
    title: '渗透报告初稿撰写',
    description: '汇总 MetaTech 阶段性成果并产出初版报告。',
    status: 'todo',
    priority: 'medium',
    assignee: '林浩',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    progress: 0,
    startTime: '2026-07-28T00:00:00Z',
    dueDate: '2026-08-05',
    tags: ['报告'],
    timeline: [
      { id: 'tl1', time: '2026-07-28 09:00', title: '任务创建', description: '林浩 创建任务', operator: '林浩', type: 'create' },
    ],
    comments: 0,
    attachments: 0,
    createTime: '2026-07-28T09:00:00Z',
    updateTime: '2026-07-28T09:00:00Z',
  },
  {
    id: 'task_008',
    title: 'GovCity 政务云侦察',
    description: '资产测绘与端口扫描。',
    status: 'todo',
    priority: 'medium',
    assignee: '周宇翔',
    targetId: 'tgt_004',
    targetName: 'GovCity 政务云',
    progress: 0,
    startTime: '2026-08-01T00:00:00Z',
    dueDate: '2026-08-10',
    tags: ['侦察', '政务'],
    timeline: [
      { id: 'tl1', time: '2026-08-01 10:00', title: '任务创建', description: '周宇翔 创建任务', operator: '周宇翔', type: 'create' },
    ],
    comments: 0,
    attachments: 0,
    createTime: '2026-08-01T10:00:00Z',
    updateTime: '2026-08-01T10:00:00Z',
  },
  {
    id: 'task_009',
    title: '永恒之蓝利用与持久化',
    description: '在 WinXP 上位机利用 MS17-010 验证持久化方案。',
    status: 'done',
    priority: 'urgent',
    assignee: '孙磊',
    targetId: 'tgt_002',
    targetName: 'SkyNet 工控',
    progress: 100,
    startTime: '2026-06-15T00:00:00Z',
    dueDate: '2026-06-25',
    completedAt: '2026-06-24T17:00:00Z',
    tags: ['OT', 'MS17-010'],
    timeline: [
      { id: 'tl1', time: '2026-06-15 09:00', title: '任务创建', description: '孙磊 创建任务', operator: '孙磊', type: 'create' },
      { id: 'tl2', time: '2026-06-24 17:00', title: '已完成', description: '持久化方案验证通过', operator: '孙磊', type: 'status_change' },
    ],
    comments: 4,
    attachments: 1,
    createTime: '2026-06-15T09:00:00Z',
    updateTime: '2026-06-24T17:00:00Z',
  },
  {
    id: 'task_010',
    title: '内网 C2 隐蔽性测试',
    description: '验证 Cobalt Strike Malleable C2 隐蔽性，避免 EDR 检测。',
    status: 'blocked',
    priority: 'high',
    assignee: '刘晓东',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    progress: 30,
    startTime: '2026-07-10T00:00:00Z',
    dueDate: '2026-07-20',
    tags: ['C2', 'EDR'],
    timeline: [
      { id: 'tl1', time: '2026-07-10 10:00', title: '任务创建', description: '刘晓东 创建任务', operator: '刘晓东', type: 'create' },
      { id: 'tl2', time: '2026-07-15 14:00', title: '状态变更', description: 'EDR 多次告警，任务阻塞', operator: '刘晓东', type: 'status_change' },
    ],
    comments: 7,
    attachments: 2,
    createTime: '2026-07-10T10:00:00Z',
    updateTime: '2026-07-22T14:00:00Z',
  },
  {
    id: 'task_011',
    title: 'Exchange ProxyLogon 利用',
    description: '针对员工门户邮件系统验证 CVE-2021-26855。',
    status: 'done',
    priority: 'urgent',
    assignee: '陈思齐',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    progress: 100,
    startTime: '2026-06-20T00:00:00Z',
    dueDate: '2026-06-30',
    completedAt: '2026-06-28T16:00:00Z',
    tags: ['Exchange', 'CVE-2021-26855'],
    timeline: [
      { id: 'tl1', time: '2026-06-20 10:00', title: '任务创建', description: '陈思齐 创建任务', operator: '陈思齐', type: 'create' },
      { id: 'tl2', time: '2026-06-28 16:00', title: '已完成', description: '获取 mailbox 与 webshell', operator: '陈思齐', type: 'status_change' },
    ],
    comments: 5,
    attachments: 3,
    createTime: '2026-06-20T10:00:00Z',
    updateTime: '2026-06-28T16:00:00Z',
  },
  {
    id: 'task_012',
    title: '员工凭据钓鱼演练',
    description: '面向 200 名员工开展定向钓鱼演练并统计点击率。',
    status: 'cancelled',
    priority: 'low',
    assignee: '赵敏',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    progress: 0,
    startTime: '2026-07-15T00:00:00Z',
    dueDate: '2026-07-25',
    tags: ['钓鱼', '演练'],
    timeline: [
      { id: 'tl1', time: '2026-07-15 10:00', title: '任务创建', description: '赵敏 创建任务', operator: '赵敏', type: 'create' },
      { id: 'tl2', time: '2026-07-18 14:00', title: '状态变更', description: '客户取消演练授权', operator: '王浩然', type: 'status_change' },
    ],
    comments: 2,
    attachments: 0,
    createTime: '2026-07-15T10:00:00Z',
    updateTime: '2026-07-18T14:00:00Z',
  },
];

/** 按状态分组 */
export function groupManageTasksByStatus(tasks: TaskItem[]): Record<TaskStatus, TaskItem[]> {
  return {
    todo: tasks.filter((t) => t.status === 'todo'),
    doing: tasks.filter((t) => t.status === 'doing'),
    done: tasks.filter((t) => t.status === 'done'),
    blocked: tasks.filter((t) => t.status === 'blocked'),
    cancelled: tasks.filter((t) => t.status === 'cancelled'),
  };
}

/** 按 ID 获取任务 */
export function getTaskById(id: string): TaskItem | undefined {
  return mockTasks.find((t) => t.id === id);
}

/** 筛选参数 */
export interface TaskFilterParams {
  keyword?: string;
  status?: TaskStatus;
  assignee?: string;
  priority?: TaskItem['priority'];
}

/** 按条件筛选任务 */
export function filterTasks(params: TaskFilterParams): TaskItem[] {
  let arr = [...mockTasks];
  if (params.keyword) {
    const kw = params.keyword.toLowerCase();
    arr = arr.filter(
      (t) =>
        t.title.toLowerCase().includes(kw) ||
        t.description.toLowerCase().includes(kw) ||
        t.assignee.toLowerCase().includes(kw) ||
        (t.targetName ?? '').toLowerCase().includes(kw),
    );
  }
  if (params.status) arr = arr.filter((t) => t.status === params.status);
  if (params.assignee) arr = arr.filter((t) => t.assignee === params.assignee);
  if (params.priority) arr = arr.filter((t) => t.priority === params.priority);
  return arr;
}

/** 任务负责人列表 */
export const mockTaskAssignees = Array.from(new Set(mockTasks.map((t) => t.assignee)));

export default {
  mockTasks,
  groupManageTasksByStatus,
  getTaskById,
  filterTasks,
  mockTaskAssignees,
};
