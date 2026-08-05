/**
 * Mock 数据 - 目标画像
 */
import { TargetType } from '@/types';
import type { TargetProfile } from '@/types';

/** Mock 目标画像列表（8 条） */
export const mockTargetProfiles: TargetProfile[] = [
  {
    id: 'tp001',
    name: 'MetaTech 金融科技集团',
    type: TargetType.ORGANIZATION,
    industry: '金融科技',
    region: '华东 / 上海',
    description: '一家提供数字支付与跨境结算服务的金融科技公司，核心系统暴露在公网。',
    tags: ['金融', '高价值目标', '存在历史漏洞'],
    riskLevel: 'critical',
    organization: [
      { id: 'o1', name: '张志远', title: 'CEO', department: '管理层', level: 1 },
      { id: 'o2', name: '李婉如', title: 'CTO', department: '技术部', level: 2, parentId: 'o1' },
      { id: 'o3', name: '王浩然', title: '安全总监', department: '安全部', level: 3, parentId: 'o2' },
      { id: 'o4', name: '陈思齐', title: '运维经理', department: '运维部', level: 3, parentId: 'o2' },
      { id: 'o5', name: '刘晓东', title: 'DBA', department: '运维部', level: 4, parentId: 'o4' },
      { id: 'o6', name: '赵敏', title: '前端组长', department: '研发部', level: 4, parentId: 'o2' },
    ],
    techAssets: [
      { id: 'a1', type: 'domain', value: 'www.mf-tech.cn', exposure: 'internet', lastSeen: '2026-07-25T10:00:00Z' },
      { id: 'a2', type: 'ip', value: '47.92.121.18', os: 'Linux', port: 443, service: 'HTTPS', exposure: 'internet', lastSeen: '2026-07-26T08:00:00Z' },
      { id: 'a3', type: 'webapp', value: '统一支付网关 v2.3', service: 'Java/Tomcat', exposure: 'internet', lastSeen: '2026-07-26T09:00:00Z' },
      { id: 'a4', type: 'database', value: 'MySQL 5.7 主库', os: 'CentOS', port: 3306, exposure: 'intranet', lastSeen: '2026-07-26T07:30:00Z' },
      { id: 'a5', type: 'cloud', value: '阿里云 OSS 桶 metatech-backup', exposure: 'internet', lastSeen: '2026-07-20T10:00:00Z' },
    ],
    attackSurfaces: [
      { id: 's1', category: 'network', vector: '外网暴露端口 8080/Tomcat', description: '管理后台对外开放，存在弱口令', riskScore: 9.2, status: 'exploited' },
      { id: 's2', category: 'application', vector: 'Spring Boot Actuator 泄露', description: '/actuator/env 端点未授权访问', riskScore: 8.5, status: 'validated' },
      { id: 's3', category: 'host', vector: 'Windows 2008 R2 EOL', description: '运维主机系统已停止支持', riskScore: 7.0, status: 'open' },
      { id: 's4', category: 'human', vector: '钓鱼邮件', description: '财务部门曾收到仿冒附件', riskScore: 6.5, status: 'remediated' },
    ],
    timeline: [
      { id: 't1', time: '2026-05-12T09:30:00Z', title: '初步侦察', description: '通过 OSINT 收集组织架构与子域名信息', category: 'recon' },
      { id: 't2', time: '2026-06-03T14:00:00Z', title: '边界突破', description: '利用 Tomcat 弱口令进入管理后台', category: 'intrusion' },
      { id: 't3', time: '2026-06-15T16:20:00Z', title: '内网横向', description: '通过 actuator 泄露凭证横向至 DB 服务器', category: 'action' },
      { id: 't4', time: '2026-07-20T10:00:00Z', title: '数据发现', description: '发现 OSS 桶内含历史备份与明文凭证', category: 'discovery' },
    ],
    createTime: '2026-04-01T08:00:00Z',
    updateTime: '2026-07-26T09:00:00Z',
  },
  {
    id: 'tp002',
    name: 'NovaCloud 云服务公司',
    type: TargetType.ORGANIZATION,
    industry: '云计算',
    region: '华南 / 深圳',
    description: '提供 SaaS 与对象存储服务的云厂商，攻击面广。',
    tags: ['云原生', '多租户', 'API 暴露'],
    riskLevel: 'high',
    organization: [
      { id: 'o1', name: '周宇翔', title: 'CEO', department: '管理层', level: 1 },
      { id: 'o2', name: '黄子韬', title: 'CISO', department: '安全部', level: 2, parentId: 'o1' },
      { id: 'o3', name: '林浩', title: 'SRE 主管', department: '运维部', level: 3, parentId: 'o2' },
    ],
    techAssets: [
      { id: 'a1', type: 'domain', value: 'api.nova-cloud.com', exposure: 'internet', lastSeen: '2026-07-26T09:00:00Z' },
      { id: 'a2', type: 'webapp', value: '控制台 Console v3', service: 'Node.js', exposure: 'internet', lastSeen: '2026-07-26T08:30:00Z' },
      { id: 'a3', type: 'cloud', value: 'K8s 集群 prod-cluster', exposure: 'intranet', lastSeen: '2026-07-25T10:00:00Z' },
    ],
    attackSurfaces: [
      { id: 's1', category: 'application', vector: 'API 未鉴权', description: '/v1/users 接口可枚举租户信息', riskScore: 8.0, status: 'validated' },
      { id: 's2', category: 'network', vector: 'K8s API Server 公网暴露', description: '6443 端口可被互联网访问', riskScore: 9.0, status: 'open' },
    ],
    timeline: [
      { id: 't1', time: '2026-06-01T08:00:00Z', title: '资产收集', description: '通过证书透明度日志发现子域', category: 'recon' },
      { id: 't2', time: '2026-07-10T11:00:00Z', title: 'API 越权', description: '越权访问获取租户列表', category: 'intrusion' },
    ],
    createTime: '2026-05-01T08:00:00Z',
    updateTime: '2026-07-26T09:00:00Z',
  },
  {
    id: 'tp003',
    name: 'SkyNet 能源集团',
    type: TargetType.ORGANIZATION,
    industry: '能源 / 工控',
    region: '华北 / 北京',
    description: '传统工控网络与 IT 网络混合，OT 安全薄弱。',
    tags: ['工控', 'OT/IT 融合', '遗留系统'],
    riskLevel: 'critical',
    organization: [
      { id: 'o1', name: '高建华', title: '总经理', department: '管理层', level: 1 },
      { id: 'o2', name: '孙磊', title: 'IT 部长', department: '信息中心', level: 2, parentId: 'o1' },
      { id: 'o3', name: '马晓辉', title: 'SCADA 工程师', department: '生产部', level: 3, parentId: 'o1' },
    ],
    techAssets: [
      { id: 'a1', type: 'host', value: 'SCADA 上位机 WinXP', os: 'Windows XP', exposure: 'intranet', lastSeen: '2026-07-25T08:00:00Z' },
      { id: 'a2', type: 'ip', value: '10.20.30.5', port: 502, service: 'Modbus', exposure: 'intranet', lastSeen: '2026-07-26T07:00:00Z' },
    ],
    attackSurfaces: [
      { id: 's1', category: 'host', vector: 'Windows XP 永恒之蓝', description: 'MS17-010 未打补丁', riskScore: 9.5, status: 'exploited' },
      { id: 's2', category: 'network', vector: 'Modbus 无认证', description: '工控协议明文无鉴权', riskScore: 8.8, status: 'open' },
    ],
    timeline: [
      { id: 't1', time: '2026-04-15T08:00:00Z', title: 'OT 资产测绘', description: '识别工控网络拓扑', category: 'recon' },
      { id: 't2', time: '2026-06-20T14:00:00Z', title: '工控入侵', description: '通过永恒之蓝进入上位机', category: 'intrusion' },
    ],
    createTime: '2026-03-15T08:00:00Z',
    updateTime: '2026-07-25T08:00:00Z',
  },
  {
    id: 'tp004',
    name: '张伟（运维负责人）',
    type: TargetType.PERSON,
    industry: '金融',
    region: '华东 / 上海',
    description: '某金融机构运维负责人，持有 DBA 与生产环境权限。',
    tags: ['高权限', '社工目标'],
    riskLevel: 'high',
    organization: [
      { id: 'o1', name: '张伟', title: '运维负责人', department: 'IT 部', level: 2 },
    ],
    techAssets: [
      { id: 'a1', type: 'ip', value: '192.168.10.5', os: 'CentOS 7', port: 22, service: 'SSH', exposure: 'intranet', lastSeen: '2026-07-26T08:00:00Z' },
    ],
    attackSurfaces: [
      { id: 's1', category: 'human', vector: '钓鱼邮件', description: '收到伪装为 HR 通知的钓鱼邮件', riskScore: 7.5, status: 'validated' },
      { id: 's2', category: 'human', vector: '社交平台泄露', description: 'GitHub 仓库泄露 .ssh 目录', riskScore: 6.8, status: 'open' },
    ],
    timeline: [
      { id: 't1', time: '2026-05-20T08:00:00Z', title: '人员画像', description: '收集社交账号与技术栈信息', category: 'recon' },
    ],
    createTime: '2026-04-20T08:00:00Z',
    updateTime: '2026-07-20T08:00:00Z',
  },
  {
    id: 'tp005',
    name: 'GlobalShop 电商平台',
    type: TargetType.ASSET,
    industry: '电商',
    region: '华东 / 杭州',
    description: '面向 C 端的电商系统，含支付与积分模块。',
    tags: ['Web 应用', '支付'],
    riskLevel: 'medium',
    organization: [
      { id: 'o1', name: '徐峰', title: '技术负责人', department: '研发部', level: 1 },
    ],
    techAssets: [
      { id: 'a1', type: 'webapp', value: '支付收银台 v1.8', service: 'PHP/Laravel', exposure: 'internet', lastSeen: '2026-07-26T09:00:00Z' },
    ],
    attackSurfaces: [
      { id: 's1', category: 'application', vector: 'SQL 注入', description: '商品搜索接口存在布尔盲注', riskScore: 8.2, status: 'validated' },
    ],
    timeline: [
      { id: 't1', time: '2026-06-10T08:00:00Z', title: 'Web 漏洞扫描', description: '发现 SQL 注入', category: 'recon' },
    ],
    createTime: '2026-05-10T08:00:00Z',
    updateTime: '2026-07-25T08:00:00Z',
  },
  {
    id: 'tp006',
    name: 'HealthPlus 医疗系统',
    type: TargetType.ASSET,
    industry: '医疗',
    region: '西南 / 成都',
    description: '医院 HIS 系统，含敏感患者数据。',
    tags: ['医疗', '敏感数据'],
    riskLevel: 'high',
    organization: [
      { id: 'o1', name: '吴医生', title: '信息科主任', department: '信息科', level: 1 },
    ],
    techAssets: [
      { id: 'a1', type: 'webapp', value: 'HIS 系统 v5.2', service: '.NET', exposure: 'intranet', lastSeen: '2026-07-26T08:00:00Z' },
    ],
    attackSurfaces: [
      { id: 's1', category: 'application', vector: '越权访问', description: '患者接口 ID 可枚举', riskScore: 8.5, status: 'open' },
    ],
    timeline: [
      { id: 't1', time: '2026-06-25T08:00:00Z', title: '资产发现', description: '识别 HIS 系统版本', category: 'recon' },
    ],
    createTime: '2026-05-25T08:00:00Z',
    updateTime: '2026-07-25T08:00:00Z',
  },
  {
    id: 'tp007',
    name: 'EduLink 在线教育',
    type: TargetType.ORGANIZATION,
    industry: '教育',
    region: '华中 / 武汉',
    description: '在线教育平台，用户量较大。',
    tags: ['Web', '用户数据'],
    riskLevel: 'low',
    organization: [
      { id: 'o1', name: '钱校长', title: 'CEO', department: '管理层', level: 1 },
    ],
    techAssets: [
      { id: 'a1', type: 'domain', value: 'www.edu-link.cn', exposure: 'internet', lastSeen: '2026-07-26T08:00:00Z' },
    ],
    attackSurfaces: [
      { id: 's1', category: 'application', vector: 'XSS', description: '课程评论存在存储型 XSS', riskScore: 5.5, status: 'open' },
    ],
    timeline: [
      { id: 't1', time: '2026-07-01T08:00:00Z', title: '初步侦察', description: '资产收集', category: 'recon' },
    ],
    createTime: '2026-06-01T08:00:00Z',
    updateTime: '2026-07-25T08:00:00Z',
  },
  {
    id: 'tp008',
    name: 'GovCity 政务云',
    type: TargetType.ORGANIZATION,
    industry: '政府',
    region: '华北 / 北京',
    description: '某市政府政务云平台，承载多部门业务系统。',
    tags: ['政务', '高敏感'],
    riskLevel: 'critical',
    organization: [
      { id: 'o1', name: '何局长', title: '信息中心主任', department: '信息中心', level: 1 },
      { id: 'o2', name: '罗工', title: '运维工程师', department: '信息中心', level: 2, parentId: 'o1' },
    ],
    techAssets: [
      { id: 'a1', type: 'ip', value: '203.95.x.x', port: 443, service: 'HTTPS', exposure: 'internet', lastSeen: '2026-07-26T08:00:00Z' },
      { id: 'a2', type: 'cloud', value: '政务云资源池', exposure: 'intranet', lastSeen: '2026-07-25T08:00:00Z' },
    ],
    attackSurfaces: [
      { id: 's1', category: 'application', vector: 'SSRF', description: '内部接口存在 SSRF', riskScore: 8.8, status: 'validated' },
    ],
    timeline: [
      { id: 't1', time: '2026-04-10T08:00:00Z', title: '资产测绘', description: '端口扫描与服务识别', category: 'recon' },
    ],
    createTime: '2026-03-10T08:00:00Z',
    updateTime: '2026-07-26T08:00:00Z',
  },
];

/** 根据关键字过滤 */
export function searchTargetProfiles(keyword: string): TargetProfile[] {
  if (!keyword) return mockTargetProfiles;
  const kw = keyword.toLowerCase();
  return mockTargetProfiles.filter(
    (t) =>
      t.name.toLowerCase().includes(kw) ||
      t.industry.toLowerCase().includes(kw) ||
      t.tags.some((tag) => tag.toLowerCase().includes(kw)),
  );
}

/** 根据 ID 查询画像 */
export function getTargetProfileById(id: string): TargetProfile | undefined {
  return mockTargetProfiles.find((t) => t.id === id);
}

export default {
  mockTargetProfiles,
  searchTargetProfiles,
  getTargetProfileById,
};
