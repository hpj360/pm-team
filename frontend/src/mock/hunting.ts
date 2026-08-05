/**
 * Mock 数据 - 威胁狩猎（V5.3）
 * 对应后端 analyze-service HuntingController
 */
import type {
  AttackTactic,
  AttackTechnique,
  AttackMatrix,
  HypothesisDetail,
  HuntingHypothesis,
  HuntingRule,
  RuleTestResult,
  RuleStats,
  HuntingHit,
} from '@/types';
import { HypothesisStatus, HuntingRuleType } from '@/types';

/** Mock ATT&CK 战术（14 战术） */
export const mockAttackTactics: AttackTactic[] = [
  { id: 'reconnaissance', name: 'Reconnaissance', nameCn: '侦察' },
  { id: 'resource-development', name: 'Resource Development', nameCn: '资源开发' },
  { id: 'initial-access', name: 'Initial Access', nameCn: '初始访问' },
  { id: 'execution', name: 'Execution', nameCn: '执行' },
  { id: 'persistence', name: 'Persistence', nameCn: '持久化' },
  { id: 'privilege-escalation', name: 'Privilege Escalation', nameCn: '权限提升' },
  { id: 'defense-evasion', name: 'Defense Evasion', nameCn: '防御规避' },
  { id: 'credential-access', name: 'Credential Access', nameCn: '凭据访问' },
  { id: 'discovery', name: 'Discovery', nameCn: '发现' },
  { id: 'lateral-movement', name: 'Lateral Movement', nameCn: '横向移动' },
  { id: 'collection', name: 'Collection', nameCn: '收集' },
  { id: 'command-and-control', name: 'Command and Control', nameCn: '命令控制' },
  { id: 'exfiltration', name: 'Exfiltration', nameCn: '外传' },
  { id: 'impact', name: 'Impact', nameCn: '影响' },
];

/** Mock ATT&CK 技术 */
export const mockAttackTechniques: AttackTechnique[] = [
  {
    techniqueId: 'T1595',
    name: 'Active Scanning',
    description: '攻击者扫描目标网络以收集可利用信息',
    tactic: 'reconnaissance',
    tacticName: '侦察',
    subTechnique: false,
    dataSource: 'Process execution logs, network traffic',
  },
  {
    techniqueId: 'T1592',
    name: 'Gather Victim Host Info',
    description: '收集目标主机信息（操作系统、硬件、软件）',
    tactic: 'reconnaissance',
    tacticName: '侦察',
    subTechnique: false,
    dataSource: 'Network traffic, application logs',
  },
  {
    techniqueId: 'T1588',
    name: 'Obtain Capabilities',
    description: '获取恶意软件、漏洞利用、证书等攻击能力',
    tactic: 'resource-development',
    tacticName: '资源开发',
    subTechnique: false,
    dataSource: 'Threat intel feeds',
  },
  {
    techniqueId: 'T1566',
    name: 'Phishing',
    description: '通过钓鱼邮件获取初始访问权限',
    tactic: 'initial-access',
    tacticName: '初始访问',
    subTechnique: false,
    dataSource: 'Email gateway logs, application logs',
  },
  {
    techniqueId: 'T1566.001',
    name: 'Spearphishing Attachment',
    description: '通过带附件的钓鱼邮件获取初始访问',
    tactic: 'initial-access',
    tacticName: '初始访问',
    subTechniqueId: 'T1566.001',
    subTechnique: true,
    dataSource: 'Email gateway logs',
  },
  {
    techniqueId: 'T1190',
    name: 'Exploit Public-Facing App',
    description: '利用面向公网应用的漏洞',
    tactic: 'initial-access',
    tacticName: '初始访问',
    subTechnique: false,
    dataSource: 'Web server logs, IDS/IPS alerts',
  },
  {
    techniqueId: 'T1059',
    name: 'Command and Scripting Interpreter',
    description: '通过命令行/脚本解释器执行命令',
    tactic: 'execution',
    tacticName: '执行',
    subTechnique: false,
    dataSource: 'Process execution logs',
  },
  {
    techniqueId: 'T1059.001',
    name: 'PowerShell',
    description: '通过 PowerShell 执行命令与脚本',
    tactic: 'execution',
    tacticName: '执行',
    subTechniqueId: 'T1059.001',
    subTechnique: true,
    dataSource: 'PowerShell logs, process execution logs',
  },
  {
    techniqueId: 'T1059.003',
    name: 'Windows Command Shell',
    description: '通过 Windows cmd.exe 执行命令',
    tactic: 'execution',
    tacticName: '执行',
    subTechniqueId: 'T1059.003',
    subTechnique: true,
    dataSource: 'Process execution logs',
  },
  {
    techniqueId: 'T1129',
    name: 'Shared Modules',
    description: '通过 rundll32/ld-linux 加载恶意 DLL',
    tactic: 'execution',
    tacticName: '执行',
    subTechnique: false,
    dataSource: 'Process execution logs, module load events',
  },
  {
    techniqueId: 'T1547',
    name: 'Boot or Logon Autostart',
    description: '通过注册表自启动项实现持久化',
    tactic: 'persistence',
    tacticName: '持久化',
    subTechnique: false,
    dataSource: 'Registry events, file system logs',
  },
  {
    techniqueId: 'T1547.001',
    name: 'Registry Run Keys',
    description: '通过注册表 Run 键实现自启动',
    tactic: 'persistence',
    tacticName: '持久化',
    subTechniqueId: 'T1547.001',
    subTechnique: true,
    dataSource: 'Registry events',
  },
  {
    techniqueId: 'T1055',
    name: 'Process Injection',
    description: '进程注入以执行恶意代码',
    tactic: 'privilege-escalation',
    tacticName: '权限提升',
    subTechnique: false,
    dataSource: 'Process events, memory analysis',
  },
  {
    techniqueId: 'T1070',
    name: 'Indicator Removal',
    description: '删除日志/痕迹以规避检测',
    tactic: 'defense-evasion',
    tacticName: '防御规避',
    subTechnique: false,
    dataSource: 'File system logs, event logs',
  },
  {
    techniqueId: 'T1027',
    name: 'Obfuscated Files or Information',
    description: '混淆文件或信息以规避检测',
    tactic: 'defense-evasion',
    tacticName: '防御规避',
    subTechnique: false,
    dataSource: 'File analysis, process execution logs',
  },
  {
    techniqueId: 'T1003',
    name: 'OS Credential Dumping',
    description: '转储操作系统凭据（LSASS / SAM）',
    tactic: 'credential-access',
    tacticName: '凭据访问',
    subTechnique: false,
    dataSource: 'Process events, memory analysis',
  },
  {
    techniqueId: 'T1003.001',
    name: 'LSASS Memory',
    description: '转储 LSASS 进程内存以获取凭据',
    tactic: 'credential-access',
    tacticName: '凭据访问',
    subTechniqueId: 'T1003.001',
    subTechnique: true,
    dataSource: 'Process events, memory analysis',
  },
  {
    techniqueId: 'T1087',
    name: 'Account Discovery',
    description: '发现系统账户信息',
    tactic: 'discovery',
    tacticName: '发现',
    subTechnique: false,
    dataSource: 'Process execution logs',
  },
  {
    techniqueId: 'T1021',
    name: 'Remote Services',
    description: '通过远程服务横向移动（RDP/SMB/SSH）',
    tactic: 'lateral-movement',
    tacticName: '横向移动',
    subTechnique: false,
    dataSource: 'Network traffic, authentication logs',
  },
  {
    techniqueId: 'T1071',
    name: 'Application Layer Protocol',
    description: '通过应用层协议与 C2 通信',
    tactic: 'command-and-control',
    tacticName: '命令控制',
    subTechnique: false,
    dataSource: 'Network traffic',
  },
  {
    techniqueId: 'T1071.001',
    name: 'Web Protocols',
    description: '通过 HTTP/HTTPS 与 C2 通信',
    tactic: 'command-and-control',
    tacticName: '命令控制',
    subTechniqueId: 'T1071.001',
    subTechnique: true,
    dataSource: 'Network traffic, proxy logs',
  },
  {
    techniqueId: 'T1041',
    name: 'Exfiltration Over C2 Channel',
    description: '通过 C2 通道外传数据',
    tactic: 'exfiltration',
    tacticName: '外传',
    subTechnique: false,
    dataSource: 'Network traffic',
  },
  {
    techniqueId: 'T1486',
    name: 'Data Encrypted for Impact',
    description: '加密数据以造成影响（勒索）',
    tactic: 'impact',
    tacticName: '影响',
    subTechnique: false,
    dataSource: 'File system logs',
  },
];

/** Mock ATT&CK 矩阵 */
export const mockAttackMatrix: AttackMatrix = {
  tactics: mockAttackTactics,
  techniques: mockAttackTechniques,
  tacticCount: mockAttackTactics.length,
  techniqueCount: mockAttackTechniques.length,
};

/** Mock 狩猎命中项 */
const mockHuntingHits: HuntingHit[] = [
  {
    entityType: 'FILE',
    entityId: 'f0001',
    entityName: 'malware_sample_001.exe',
    description: '文件行为命中 PowerShell 编码命令执行',
    score: 0.92,
    evidence: 'cmd.exe → powershell.exe -enc SGVsbG8=',
  },
  {
    entityType: 'FILE',
    entityId: 'f0005',
    entityName: 'network_traffic_005.pcap',
    description: '网络流量命中 HTTPS C2 通信模式',
    score: 0.85,
    evidence: 'dst=45.155.205.233:443, beacon interval=60s',
  },
  {
    entityType: 'NETWORK',
    entityId: 'net-001',
    entityName: '45.155.205.233',
    description: 'IP 与已知 C2 基础设施匹配',
    score: 0.95,
    evidence: '威胁情报命中：APT41 C2',
  },
];

/** Mock 狩猎假设列表 */
export const mockHypotheses: HypothesisDetail[] = [
  {
    id: 'hyp-001',
    description: '检测环境中是否存在 PowerShell 编码命令执行行为（无文件攻击）',
    techniqueId: 'T1059.001',
    techniqueName: 'PowerShell',
    tactic: 'execution',
    tacticName: '执行',
    userId: 1,
    userName: 'admin',
    status: HypothesisStatus.CONFIRMED,
    confidence: 0.92,
    hits: mockHuntingHits.slice(0, 2),
    recommendedIocs: ['45.155.205.233', 'malicious-update.example-evil.com'],
    validatedTime: '2026-07-26T09:00:00Z',
    createTime: '2026-07-26T08:30:00Z',
    updateTime: '2026-07-26T09:00:00Z',
  },
  {
    id: 'hyp-002',
    description: '检测是否有进程通过 rundll32 加载恶意 DLL（共享模块执行）',
    techniqueId: 'T1129',
    techniqueName: 'Shared Modules',
    tactic: 'execution',
    tacticName: '执行',
    userId: 1,
    userName: 'admin',
    status: HypothesisStatus.CONFIRMED,
    confidence: 0.78,
    hits: [mockHuntingHits[0]],
    recommendedIocs: ['C:\\Users\\Public\\payload.dll'],
    validatedTime: '2026-07-26T09:10:00Z',
    createTime: '2026-07-26T08:40:00Z',
    updateTime: '2026-07-26T09:10:00Z',
  },
  {
    id: 'hyp-003',
    description: '检测 LSASS 内存转储行为（凭据窃取）',
    techniqueId: 'T1003.001',
    techniqueName: 'LSASS Memory',
    tactic: 'credential-access',
    tacticName: '凭据访问',
    userId: 2,
    userName: 'analyst01',
    status: HypothesisStatus.REFUTED,
    confidence: 0.15,
    hits: [],
    recommendedIocs: [],
    validatedTime: '2026-07-26T09:20:00Z',
    createTime: '2026-07-26T08:50:00Z',
    updateTime: '2026-07-26T09:20:00Z',
  },
  {
    id: 'hyp-004',
    description: '检测通过 HTTPS 协议的 C2 通信（Web 协议）',
    techniqueId: 'T1071.001',
    techniqueName: 'Web Protocols',
    tactic: 'command-and-control',
    tacticName: '命令控制',
    userId: 1,
    userName: 'admin',
    status: HypothesisStatus.DRAFT,
    hits: [],
    recommendedIocs: [],
    createTime: '2026-07-26T10:00:00Z',
    updateTime: '2026-07-26T10:00:00Z',
  },
];

/** Mock 狩猎规则列表 */
export const mockHuntingRules: HuntingRule[] = [
  {
    id: 'rule-sigma-001',
    name: 'Suspicious PowerShell Encoded Command',
    type: HuntingRuleType.SIGMA,
    content: `title: Suspicious PowerShell Encoded Command
status: experimental
description: Detects PowerShell executing encoded commands
author: redteam
severity: high
tags:
  - attack.execution
  - attack.t1059.001
logsource:
  product: windows
  category: process_creation
detection:
  selection:
    Image|endswith: '\\\\powershell.exe'
    CommandLine|contains: '-enc'
  condition: selection`,
    description: '检测 PowerShell 执行编码命令的行为',
    author: 'admin',
    severity: 'high',
    tags: ['attack.execution', 'attack.t1059.001'],
    attackTechniqueIds: ['T1059.001'],
    enabled: true,
    version: 2,
    matchCount: 14,
    testCount: 28,
    createTime: '2026-07-20T08:00:00Z',
    updateTime: '2026-07-25T10:00:00Z',
    lastMatchTime: '2026-07-26T08:00:00Z',
  },
  {
    id: 'rule-sigma-002',
    name: 'Rundll32 Loading Suspicious DLL',
    type: HuntingRuleType.SIGMA,
    content: `title: Rundll32 Loading Suspicious DLL
status: experimental
description: Detects rundll32 loading DLL from suspicious locations
author: redteam
severity: medium
tags:
  - attack.execution
  - attack.t1129
logsource:
  product: windows
  category: process_creation
detection:
  selection:
    Image|endswith: '\\\\rundll32.exe'
    CommandLine|contains:
      - 'Users\\\\Public'
      - 'Temp'
  condition: selection`,
    description: '检测 rundll32 从可疑路径加载 DLL',
    author: 'admin',
    severity: 'medium',
    tags: ['attack.execution', 'attack.t1129'],
    attackTechniqueIds: ['T1129'],
    enabled: true,
    version: 1,
    matchCount: 8,
    testCount: 15,
    createTime: '2026-07-22T08:00:00Z',
    updateTime: '2026-07-25T10:00:00Z',
    lastMatchTime: '2026-07-25T08:00:00Z',
  },
  {
    id: 'rule-yara-001',
    name: 'CobaltStrike Beacon Pattern',
    type: HuntingRuleType.YARA,
    content: `rule CobaltStrike_Beacon {
  meta:
    description = "CobaltStrike Beacon pattern"
    author = "redteam"
    severity = "critical"
    date = "2026-07-20"
  strings:
    $a1 = { 4D 5A 90 00 03 00 00 00 }
    $a2 = "ReflectiveLoader" nocase
    $a3 = "beacon.dll" nocase
  condition:
    $a1 and ($a2 or $a3)
}`,
    description: '检测 CobaltStrike Beacon 特征字符串',
    author: 'analyst01',
    severity: 'critical',
    tags: ['malware', 'cobaltstrike'],
    attackTechniqueIds: ['T1071', 'T1059'],
    enabled: true,
    version: 3,
    matchCount: 23,
    testCount: 50,
    createTime: '2026-07-18T08:00:00Z',
    updateTime: '2026-07-26T08:00:00Z',
    lastMatchTime: '2026-07-26T07:00:00Z',
  },
  {
    id: 'rule-yara-002',
    name: 'Custom Backdoor Marker',
    type: HuntingRuleType.YARA,
    content: `rule Custom_Backdoor {
  meta:
    description = "Custom backdoor marker"
    author = "redteam"
  strings:
    $s1 = "backdoor_active" nocase
    $s2 = { 63 6d 64 2e 65 78 65 }
  condition:
    $s1 or $s2
}`,
    description: '检测自定义后门标记',
    author: 'analyst01',
    severity: 'high',
    tags: ['malware', 'backdoor'],
    attackTechniqueIds: [],
    enabled: false,
    version: 1,
    matchCount: 0,
    testCount: 5,
    createTime: '2026-07-24T08:00:00Z',
    updateTime: '2026-07-25T10:00:00Z',
  },
];

/** 根据假设ID获取 Mock 假设详情 */
export function getMockHypothesisById(id: string): HypothesisDetail | undefined {
  return mockHypotheses.find((h) => h.id === id);
}

/**
 * 模拟验证假设：返回验证后的假设实体
 * 已确认/已否定的假设维持原状态；草稿/验证中的假设按命中数决定
 */
export function mockValidateHypothesis(id: string): HuntingHypothesis {
  const detail = getMockHypothesisById(id) ?? mockHypotheses[0];
  const confirmed = detail.hits.length > 0;
  return {
    id: detail.id,
    description: detail.description,
    techniqueId: detail.techniqueId,
    userId: detail.userId,
    userName: detail.userName,
    status: confirmed ? HypothesisStatus.CONFIRMED : HypothesisStatus.REFUTED,
    confidence: detail.confidence ?? (confirmed ? 0.8 : 0.1),
    hits: detail.hits,
    recommendedIocs: detail.recommendedIocs,
    validatedTime: new Date().toISOString(),
    createTime: detail.createTime,
    updateTime: new Date().toISOString(),
  };
}

/**
 * 模拟创建狩猎假设，返回假设ID
 */
export function mockCreateHypothesis(
  description: string,
  techniqueId: string,
  userId = 1,
): string {
  const id = `hyp-${Date.now().toString(36)}`;
  const technique = mockAttackTechniques.find((t) => t.techniqueId === techniqueId);
  const now = new Date().toISOString();
  mockHypotheses.unshift({
    id,
    description,
    techniqueId,
    techniqueName: technique?.name,
    tactic: technique?.tactic,
    tacticName: technique?.tacticName,
    userId,
    userName: userId === 1 ? 'admin' : 'analyst01',
    status: HypothesisStatus.DRAFT,
    hits: [],
    recommendedIocs: [],
    createTime: now,
    updateTime: now,
  });
  return id;
}

/**
 * 模拟按战术筛选技术
 */
export function mockTechniquesByTactic(tactic: string): AttackTechnique[] {
  return mockAttackTechniques.filter((t) => t.tactic === tactic);
}

/**
 * 模拟关键词搜索技术
 */
export function mockSearchTechniques(keyword: string): AttackTechnique[] {
  const kw = keyword.toLowerCase();
  return mockAttackTechniques.filter(
    (t) =>
      t.techniqueId.toLowerCase().includes(kw) ||
      t.name.toLowerCase().includes(kw) ||
      t.description.toLowerCase().includes(kw),
  );
}

/** 根据规则ID获取 Mock 规则 */
export function getMockHuntingRuleById(id: string): HuntingRule | undefined {
  return mockHuntingRules.find((r) => r.id === id);
}

/**
 * 模拟测试规则命中
 */
export function mockTestRule(id: string, fileId: string): RuleTestResult {
  const rule = getMockHuntingRuleById(id);
  // 偶数 fileId 视为命中
  const num = parseInt(fileId.replace(/[^0-9]/g, ''), 10) || 0;
  const matched = num % 2 === 0;
  return {
    matched,
    matchCount: matched ? 1 : 0,
    details: matched ? `规则 ${rule?.name ?? id} 命中文件 ${fileId}` : '未命中',
    costMs: 120 + (num % 200),
    ruleId: id,
    ruleName: rule?.name,
    fileId,
  };
}

/**
 * 模拟获取规则统计
 */
export function mockRuleStats(id: string): RuleStats {
  const rule = getMockHuntingRuleById(id) ?? mockHuntingRules[0];
  return {
    ruleId: id,
    matchCount: rule.matchCount,
    testCount: rule.testCount,
    version: rule.version,
    enabled: rule.enabled,
    lastMatchTime: rule.lastMatchTime,
  };
}

/**
 * 模拟导入 Sigma 规则
 */
export function mockImportSigmaRule(content: string): string {
  const id = `rule-sigma-${Date.now().toString(36)}`;
  const titleMatch = content.match(/title:\s*(.+)/);
  const now = new Date().toISOString();
  mockHuntingRules.unshift({
    id,
    name: titleMatch ? titleMatch[1].trim() : `Sigma Rule ${id}`,
    type: HuntingRuleType.SIGMA,
    content,
    description: '导入的 Sigma 规则',
    author: 'admin',
    severity: 'medium',
    tags: [],
    attackTechniqueIds: [],
    enabled: true,
    version: 1,
    matchCount: 0,
    testCount: 0,
    createTime: now,
    updateTime: now,
  });
  return id;
}

/**
 * 模拟导入 YARA 规则
 */
export function mockImportYaraRule(content: string): string {
  const id = `rule-yara-${Date.now().toString(36)}`;
  const nameMatch = content.match(/rule\s+(\w+)/);
  const now = new Date().toISOString();
  mockHuntingRules.unshift({
    id,
    name: nameMatch ? nameMatch[1] : `YARA Rule ${id}`,
    type: HuntingRuleType.YARA,
    content,
    description: '导入的 YARA 规则',
    author: 'admin',
    severity: 'high',
    tags: [],
    attackTechniqueIds: [],
    enabled: true,
    version: 1,
    matchCount: 0,
    testCount: 0,
    createTime: now,
    updateTime: now,
  });
  return id;
}

/**
 * 模拟按技术ID反向查询规则
 */
export function mockRulesByTechnique(techniqueId: string): HuntingRule[] {
  return mockHuntingRules.filter((r) => r.attackTechniqueIds.includes(techniqueId));
}

export default {
  mockAttackTactics,
  mockAttackTechniques,
  mockAttackMatrix,
  mockHypotheses,
  mockHuntingRules,
  getMockHypothesisById,
  mockValidateHypothesis,
  mockCreateHypothesis,
  mockTechniquesByTactic,
  mockSearchTechniques,
  getMockHuntingRuleById,
  mockTestRule,
  mockRuleStats,
  mockImportSigmaRule,
  mockImportYaraRule,
  mockRulesByTechnique,
};
