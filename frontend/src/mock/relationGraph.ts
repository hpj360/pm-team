/**
 * Mock 数据 - 关系图谱
 * 目标关系网络：组织 / 人员 / 资产 / 域名 / IP / 漏洞
 */
import type { RelationGraphData, GraphNode, GraphEdge, GraphNodeType } from '@/types';

/** 图谱节点（24 个） */
export const mockGraphNodes: GraphNode[] = [
  // 组织（4）
  {
    id: 'org_meta',
    name: 'MetaTech 集团',
    type: 'organization',
    value: 60,
    riskLevel: 'high',
    description: '本次红队作业主目标，金融科技集团。',
    properties: { industry: '金融科技', region: '北京', employees: 5000 },
    tags: ['主目标', '金融'],
  },
  {
    id: 'org_nova',
    name: 'NovaCloud',
    type: 'organization',
    value: 40,
    riskLevel: 'critical',
    description: 'MetaTech 子公司，云服务提供商。',
    properties: { industry: '云计算', region: '上海' },
    tags: ['子公司', '云'],
  },
  {
    id: 'org_sub_dc',
    name: '数据中心运维公司',
    type: 'organization',
    value: 25,
    riskLevel: 'medium',
    description: '为 MetaTech 提供机房运维服务。',
    properties: { industry: '运维', region: '北京' },
  },
  {
    id: 'org_partner',
    name: '合作伙伴：SkyNet',
    type: 'organization',
    value: 30,
    riskLevel: 'high',
    description: 'MetaTech 关键合作伙伴，工控供应商。',
    properties: { industry: '工控', region: '深圳' },
    tags: ['供应链'],
  },

  // 人员（6）
  {
    id: 'person_ceo',
    name: '王某某（CEO）',
    type: 'person',
    value: 30,
    riskLevel: 'medium',
    description: 'MetaTech 集团 CEO。',
    properties: { title: 'CEO', department: '高管' },
    tags: ['社工目标'],
  },
  {
    id: 'person_cto',
    name: '李某某（CTO）',
    type: 'person',
    value: 28,
    riskLevel: 'high',
    description: 'MetaTech CTO，负责整体技术战略。',
    properties: { title: 'CTO', department: '技术' },
  },
  {
    id: 'person_admin',
    name: '张运维',
    type: 'person',
    value: 22,
    riskLevel: 'critical',
    description: 'MetaTech 运维管理员，凭据已泄露。',
    properties: { title: '运维工程师', department: '运维' },
    tags: ['凭据已获取'],
  },
  {
    id: 'person_dev',
    name: '陈开发',
    type: 'person',
    value: 18,
    riskLevel: 'medium',
    description: 'NovaCloud 后端开发，GitHub 泄露 AK/SK。',
    properties: { title: '后端开发', department: '研发' },
    tags: ['GitHub 泄露'],
  },
  {
    id: 'person_partner',
    name: '孙工控',
    type: 'person',
    value: 20,
    riskLevel: 'high',
    description: 'SkyNet 工控工程师。',
    properties: { title: 'OT 工程师', department: 'OT' },
  },
  {
    id: 'person_hr',
    name: '周HR',
    type: 'person',
    value: 14,
    riskLevel: 'low',
    description: 'MetaTech HR，钓鱼邮件目标。',
    properties: { title: 'HR 专员', department: 'HR' },
    tags: ['钓鱼'],
  },

  // 资产（6）
  {
    id: 'asset_portal',
    name: '员工门户',
    type: 'asset',
    value: 26,
    riskLevel: 'high',
    description: 'MetaTech 员工自助门户，存在 SSRF。',
    properties: { stack: 'Java/Spring', url: 'https://portal.meta.tech' },
    tags: ['SSRF'],
  },
  {
    id: 'asset_api',
    name: '开放 API 网关',
    type: 'asset',
    value: 28,
    riskLevel: 'critical',
    description: 'NovaCloud API 网关，越权可获取全部租户数据。',
    properties: { stack: 'Go/Kong', url: 'https://api.nova.cloud' },
    tags: ['越权', 'API'],
  },
  {
    id: 'asset_admin',
    name: '运维管理后台',
    type: 'asset',
    value: 24,
    riskLevel: 'critical',
    description: 'MetaTech 运维后台，actuator 泄露凭证。',
    properties: { stack: 'Java/Spring Boot', url: 'https://admin.meta.tech' },
    tags: ['actuator'],
  },
  {
    id: 'asset_db',
    name: 'MySQL 主库',
    type: 'asset',
    value: 30,
    riskLevel: 'critical',
    description: 'MetaTech 核心数据库，存储用户PII。',
    properties: { version: 'MySQL 8.0', port: 3306 },
    tags: ['PII'],
  },
  {
    id: 'asset_jira',
    name: '内部 Jira',
    type: 'asset',
    value: 16,
    riskLevel: 'medium',
    description: 'MetaTech 内部任务系统。',
    properties: { url: 'https://jira.meta.tech' },
  },
  {
    id: 'asset_scada',
    name: 'SCADA 控制系统',
    type: 'asset',
    value: 26,
    riskLevel: 'critical',
    description: 'SkyNet SCADA 系统，Modbus 未授权。',
    properties: { protocol: 'Modbus', port: 502 },
    tags: ['OT', '未授权'],
  },

  // 域名（4）
  {
    id: 'domain_meta',
    name: 'meta.tech',
    type: 'domain',
    value: 22,
    riskLevel: 'medium',
    description: 'MetaTech 主域名。',
    properties: { registrar: 'Alibaba', expires: '2027-01-01' },
  },
  {
    id: 'domain_nova',
    name: 'nova.cloud',
    type: 'domain',
    value: 20,
    riskLevel: 'medium',
    description: 'NovaCloud 主域名。',
  },
  {
    id: 'domain_admin',
    name: 'admin.meta.tech',
    type: 'domain',
    value: 18,
    riskLevel: 'high',
    description: 'MetaTech 运维后台域名。',
  },
  {
    id: 'domain_skynet',
    name: 'skynet-ot.com',
    type: 'domain',
    value: 16,
    riskLevel: 'medium',
    description: 'SkyNet 工控对外域名。',
  },

  // IP（4）
  {
    id: 'ip_wan1',
    name: '203.0.113.10',
    type: 'ip',
    value: 20,
    riskLevel: 'medium',
    description: 'MetaTech 公网出口 IP。',
    properties: { isp: '电信', geo: '北京' },
  },
  {
    id: 'ip_wan2',
    name: '203.0.113.20',
    type: 'ip',
    value: 18,
    riskLevel: 'medium',
    description: 'NovaCloud 公网 IP。',
  },
  {
    id: 'ip_db',
    name: '10.0.1.20',
    type: 'ip',
    value: 24,
    riskLevel: 'critical',
    description: 'MySQL 主库内网 IP，已横向到达。',
    properties: { os: 'CentOS 7', services: 'MySQL/SSH' },
  },
  {
    id: 'ip_scada',
    name: '10.10.20.5',
    type: 'ip',
    value: 22,
    riskLevel: 'critical',
    description: 'SCADA 控制器 IP。',
    properties: { os: '嵌入式', services: 'Modbus' },
  },

  // 漏洞（4）
  {
    id: 'vuln_cve_2021_26855',
    name: 'CVE-2021-26855',
    type: 'vulnerability',
    value: 26,
    riskLevel: 'critical',
    description: 'Exchange ProxyLogon SSRF。',
    properties: { cvss: 9.8, exploited: true },
    tags: ['已利用'],
  },
  {
    id: 'vuln_cve_2020_1472',
    name: 'CVE-2020-1472',
    type: 'vulnerability',
    value: 24,
    riskLevel: 'critical',
    description: 'Zerologon 域控提权。',
    properties: { cvss: 10.0, exploited: false },
  },
  {
    id: 'vuln_actuator',
    name: 'Spring Boot Actuator 泄露',
    type: 'vulnerability',
    value: 20,
    riskLevel: 'high',
    description: '运维后台 actuator 端点未鉴权。',
    properties: { cvss: 7.5, exploited: true },
    tags: ['已利用'],
  },
  {
    id: 'vuln_ms17_010',
    name: 'CVE-2017-0144 (MS17-010)',
    type: 'vulnerability',
    value: 22,
    riskLevel: 'critical',
    description: '永恒之蓝，SCADA 上位机利用。',
    properties: { cvss: 8.1, exploited: true },
    tags: ['OT', '已利用'],
  },
];

/** 图谱边（38 条） */
export const mockGraphEdges: GraphEdge[] = [
  // 组织 - 子公司
  { id: 'e1', source: 'org_meta', target: 'org_nova', relation: 'own', weight: 5, description: '100% 控股' },
  { id: 'e2', source: 'org_meta', target: 'org_sub_dc', relation: 'own', weight: 3, description: '70% 控股' },
  { id: 'e3', source: 'org_meta', target: 'org_partner', relation: 'relate', weight: 4, description: '战略合作伙伴' },

  // 组织 - 人员
  { id: 'e4', source: 'person_ceo', target: 'org_meta', relation: 'belong_to', weight: 5 },
  { id: 'e5', source: 'person_cto', target: 'org_meta', relation: 'belong_to', weight: 4 },
  { id: 'e6', source: 'person_admin', target: 'org_meta', relation: 'belong_to', weight: 4 },
  { id: 'e7', source: 'person_dev', target: 'org_nova', relation: 'belong_to', weight: 3 },
  { id: 'e8', source: 'person_partner', target: 'org_partner', relation: 'belong_to', weight: 3 },
  { id: 'e9', source: 'person_hr', target: 'org_meta', relation: 'belong_to', weight: 2 },

  // 人员 - 人员（管理）
  { id: 'e10', source: 'person_ceo', target: 'person_cto', relation: 'manage', weight: 3 },
  { id: 'e11', source: 'person_cto', target: 'person_admin', relation: 'manage', weight: 3 },
  { id: 'e12', source: 'person_cto', target: 'person_dev', relation: 'manage', weight: 2 },

  // 资产 - 组织
  { id: 'e13', source: 'asset_portal', target: 'org_meta', relation: 'belong_to', weight: 4 },
  { id: 'e14', source: 'asset_admin', target: 'org_meta', relation: 'belong_to', weight: 4 },
  { id: 'e15', source: 'asset_db', target: 'org_meta', relation: 'belong_to', weight: 5 },
  { id: 'e16', source: 'asset_jira', target: 'org_meta', relation: 'belong_to', weight: 2 },
  { id: 'e17', source: 'asset_api', target: 'org_nova', relation: 'belong_to', weight: 5 },
  { id: 'e18', source: 'asset_scada', target: 'org_partner', relation: 'belong_to', weight: 4 },

  // 资产 - 人员
  { id: 'e19', source: 'person_admin', target: 'asset_admin', relation: 'manage', weight: 4 },
  { id: 'e20', source: 'person_admin', target: 'asset_db', relation: 'manage', weight: 4 },
  { id: 'e21', source: 'person_dev', target: 'asset_api', relation: 'manage', weight: 3 },
  { id: 'e22', source: 'person_partner', target: 'asset_scada', relation: 'manage', weight: 3 },

  // 域名 - 资产
  { id: 'e23', source: 'domain_meta', target: 'asset_portal', relation: 'resolve', weight: 3 },
  { id: 'e24', source: 'domain_admin', target: 'asset_admin', relation: 'resolve', weight: 3 },
  { id: 'e25', source: 'domain_nova', target: 'asset_api', relation: 'resolve', weight: 3 },
  { id: 'e26', source: 'domain_skynet', target: 'asset_scada', relation: 'resolve', weight: 2 },

  // IP - 资产
  { id: 'e27', source: 'ip_wan1', target: 'asset_portal', relation: 'host', weight: 3 },
  { id: 'e28', source: 'ip_wan1', target: 'asset_admin', relation: 'host', weight: 3 },
  { id: 'e29', source: 'ip_wan2', target: 'asset_api', relation: 'host', weight: 3 },
  { id: 'e30', source: 'ip_db', target: 'asset_db', relation: 'host', weight: 4 },
  { id: 'e31', source: 'ip_scada', target: 'asset_scada', relation: 'host', weight: 4 },

  // 漏洞 - 资产
  { id: 'e32', source: 'vuln_cve_2021_26855', target: 'asset_portal', relation: 'exploit', weight: 5, description: '已利用获取 shell' },
  { id: 'e33', source: 'vuln_actuator', target: 'asset_admin', relation: 'exploit', weight: 5, description: '泄露运维凭据' },
  { id: 'e34', source: 'vuln_cve_2020_1472', target: 'asset_db', relation: 'exploit', weight: 4, description: '可横向域控' },
  { id: 'e35', source: 'vuln_ms17_010', target: 'asset_scada', relation: 'exploit', weight: 5, description: 'OT 持久化' },
  { id: 'e36', source: 'vuln_actuator', target: 'person_admin', relation: 'relate', weight: 3, description: '凭据泄露' },

  // 跨域连接
  { id: 'e37', source: 'asset_api', target: 'asset_db', relation: 'connect', weight: 4, description: 'API 调用 MySQL' },
  { id: 'e38', source: 'asset_admin', target: 'asset_db', relation: 'connect', weight: 3, description: '管理面板连接 DB' },
];

/** 计算节点类型分布 */
function computeTypeDistribution(nodes: GraphNode[]): Record<GraphNodeType, number> {
  const dist: Record<GraphNodeType, number> = {
    organization: 0,
    person: 0,
    asset: 0,
    domain: 0,
    ip: 0,
    vulnerability: 0,
  };
  for (const n of nodes) dist[n.type] += 1;
  return dist;
}

/** 完整关系图谱数据 */
export const mockRelationGraph: RelationGraphData = {
  nodes: mockGraphNodes,
  edges: mockGraphEdges,
  stats: {
    nodeCount: mockGraphNodes.length,
    edgeCount: mockGraphEdges.length,
    typeDistribution: computeTypeDistribution(mockGraphNodes),
  },
};

/** 按 ID 获取节点 */
export function getGraphNodeById(id: string): GraphNode | undefined {
  return mockGraphNodes.find((n) => n.id === id);
}

/** 按 ID 获取与节点相关的边 */
export function getEdgesByNodeId(id: string): GraphEdge[] {
  return mockGraphEdges.filter((e) => e.source === id || e.target === id);
}

/** 按节点类型筛选 */
export function filterNodesByTypes(types: GraphNodeType[]): GraphNode[] {
  if (types.length === 0) return mockGraphNodes;
  return mockGraphNodes.filter((n) => types.includes(n.type));
}

/** 按关系类型筛选 */
export function filterEdgesByRelations(relations: string[]): GraphEdge[] {
  if (relations.length === 0) return mockGraphEdges;
  return mockGraphEdges.filter((e) => relations.includes(e.relation));
}

export default {
  mockGraphNodes,
  mockGraphEdges,
  mockRelationGraph,
  getGraphNodeById,
  getEdgesByNodeId,
  filterNodesByTypes,
  filterEdgesByRelations,
};
