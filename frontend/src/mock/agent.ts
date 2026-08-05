/**
 * Mock 数据 - AI Agent 化模块（V5.1）
 * 当后端 ai-service AgentController 不可达时，由 services/agent.ts 降级调用本文件
 */
import type {
  AgentTask,
  AgentTaskStatus,
  AgentTrace,
  Knowledge,
  KnowledgeSearchResult,
} from '@/types';

/** Agent 任务候选模板 */
const AGENT_TASK_TEMPLATES: Array<Omit<AgentTask, 'taskId' | 'query' | 'createdAt'>> = [
  {
    userId: 1001,
    status: 'COMPLETED' as AgentTaskStatus,
    conclusion:
      '## 分析结论\n\n基于多源情报关联分析，本次识别的样本与 APT28 组织存在高度关联。样本通过钓鱼邮件投递，携带宏代码载荷，运行后建立 C2 通道并尝试横向移动。\n\n**关键发现：**\n- C2 域名与已知 APT28 基础设施重叠\n- 宏代码使用 CVE-2017-11882 漏洞利用\n- 横向移动目标为域控制器',
    evidenceChain: [
      '步骤1 调用 search_files: 检索到 3 份关联样本，fileId=f-001/f-002/f-003',
      '步骤2 调用 get_threat_intel: C2 域名 evil.example.com 关联 APT28 组织',
      '步骤3 调用 search_knowledge: ATT&CK T1566 鱼叉式钓鱼与样本行为匹配',
    ],
    referencedFiles: ['f-001', 'f-002', 'f-003'],
    confidence: 0.87,
    traces: [
      {
        step: 1,
        thought: '用户希望分析最近的钓鱼攻击，我先检索相关文件样本',
        action: 'search_files',
        actionInput: '{"query": "钓鱼 邮件 恶意"}',
        observation:
          '[{"fileId":"f-001","fileName":"phishing_eml.eml","score":0.95},{"fileId":"f-002","fileName":"macro_doc.docm","score":0.88}]',
      },
      {
        step: 2,
        thought: '已检索到钓鱼文件，现查询其中的 C2 域名威胁情报',
        action: 'get_threat_intel',
        actionInput: '{"indicator": "evil.example.com", "type": "domain"}',
        observation:
          '{"indicator":"evil.example.com","threatActor":"APT28","techniques":["T1566","T1059"]}',
      },
      {
        step: 3,
        thought: '情报确认 APT28 关联，检索知识库补充 ATT&CK 技术细节',
        action: 'search_knowledge',
        actionInput: '{"query": "ATT&CK T1566 鱼叉式钓鱼"}',
        observation:
          '[{"knowledgeId":"k1","title":"T1566 Phishing","content":"鱼叉式钓鱼攻击技术...","score":0.92}]',
      },
      {
        step: 4,
        thought: '已收集足够证据，生成最终分析结论',
        action: 'FINAL_ANSWER',
        actionInput: '',
        observation: '基于多源情报关联分析，本次识别的样本与 APT28 组织存在高度关联。',
      },
    ],
    errorMessage: null,
    completedAt: new Date().toISOString(),
  },
  {
    userId: 1001,
    status: 'COMPLETED' as AgentTaskStatus,
    conclusion:
      '## 分析结论\n\n经分析，本次提交的样本为 Linux 平台挖矿木马，集成 SSH 横向移动模块。建议立即隔离受影响主机并更新 EDR 规则。',
    evidenceChain: [
      '步骤1 调用 run_ner: 识别到矿池地址 pool.minexmr.com 与 SSH 凭据',
      '步骤2 调用 query_neo4j: 矿池地址关联 3 个历史恶意样本',
    ],
    referencedFiles: ['f-101'],
    confidence: 0.78,
    traces: [
      {
        step: 1,
        thought: '先对样本内容进行 NER 实体识别',
        action: 'run_ner',
        actionInput: '{"text": "样本内容..."}',
        observation: '[{"entityType":"DOMAIN","entityText":"pool.minexmr.com"},{"entityType":"CREDENTIAL","entityText":"ssh_pass"}]',
      },
      {
        step: 2,
        thought: '查询矿池地址的关系图谱',
        action: 'query_neo4j',
        actionInput: '{"entity": "pool.minexmr.com", "depth": 2}',
        observation: '{"nodes":3,"edges":2,"relations":["关联历史样本 x3"]}',
      },
      {
        step: 3,
        thought: '生成最终结论',
        action: 'FINAL_ANSWER',
        actionInput: '',
        observation: '经分析，本次提交的样本为 Linux 平台挖矿木马。',
      },
    ],
    errorMessage: null,
    completedAt: new Date().toISOString(),
  },
];

/** 知识库文档 Mock 列表 */
const KNOWLEDGE_TEMPLATES: Knowledge[] = [
  {
    knowledgeId: 'k-attack-t1059',
    title: 'ATT&CK T1059 - 命令与脚本解释器',
    content:
      '攻击者可能滥用命令和脚本解释器来执行命令、脚本或二进制文件。常见技术包括 PowerShell、Bash、Python 等。T1059 是 ATT&CK 矩阵中执行战术下的标准技术编号。',
    source: 'ATT&CK',
    metadata: { tactic: 'TA0002', technique: 'T1059' },
    createdAt: '2026-07-15T08:00:00Z',
  },
  {
    knowledgeId: 'k-cve-2024-1234',
    title: 'CVE-2024-1234 - 远程代码执行漏洞',
    content:
      'CVE-2024-1234 是某流行框架的反序列化远程代码执行漏洞，CVSS 评分 9.8。攻击者可通过构造恶意序列化数据在目标服务器上执行任意代码。',
    source: 'CVE',
    metadata: { cvss: 9.8, vector: 'AV:N/AC:L/PR:N/UI:N' },
    createdAt: '2026-07-20T10:30:00Z',
  },
  {
    knowledgeId: 'k-apt28-profile',
    title: 'APT28 组织档案',
    content:
      'APT28（Fancy Bear / Sofacy）是一个疑似俄罗斯关联的高级持续性威胁组织，主要针对政府、军方与媒体目标。常用技术包括鱼叉式钓鱼、零日漏洞利用与凭据窃取。',
    source: 'APT',
    metadata: { origin: 'Russia', active: '2004-present', aliases: ['Fancy Bear', 'Sofacy'] },
    createdAt: '2026-07-10T14:00:00Z',
  },
];

/** 简单字符串哈希 */
function hashString(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

/**
 * 生成 Mock Agent 任务
 * @param query 用户分析请求
 */
export function generateMockAgentTask(query: string): AgentTask {
  const idx = hashString(query) % AGENT_TASK_TEMPLATES.length;
  const template = AGENT_TASK_TEMPLATES[idx];
  return {
    ...template,
    taskId: `task-${Date.now()}-${idx}`,
    query,
    createdAt: new Date().toISOString(),
  };
}

/**
 * 生成 Mock Agent 任务列表
 * @param count 返回条数
 */
export function generateMockAgentTasks(count: number = 5): AgentTask[] {
  const queries = [
    '分析最近一周与 APT28 相关的钓鱼文件',
    '检索包含 C2 通信特征的样本',
    '分析挖矿木马的横向移动行为',
    '关联 CVE-2024-1234 漏洞利用链',
    '生成本月威胁态势报告',
  ];
  return queries.slice(0, count).map((q) => generateMockAgentTask(q));
}

/**
 * 生成 Mock 推理轨迹
 * @param taskId 任务ID
 */
export function generateMockTraces(taskId: string): AgentTrace[] {
  return generateMockAgentTask(taskId).traces;
}

/**
 * 生成 Mock 知识库文档列表
 */
export function generateMockKnowledgeList(): Knowledge[] {
  return KNOWLEDGE_TEMPLATES;
}

/**
 * 生成 Mock 知识库检索结果
 * @param query 检索查询
 */
export function generateMockKnowledgeSearch(query: string): KnowledgeSearchResult[] {
  return KNOWLEDGE_TEMPLATES.filter(
    (k) =>
      (k.content ?? '').toLowerCase().includes(query.toLowerCase()) ||
      (k.title ?? '').toLowerCase().includes(query.toLowerCase()),
  ).map((k) => ({
    knowledgeId: k.knowledgeId,
    title: k.title,
    content: (k.content ?? '').substring(0, 200) + '...',
    source: k.source,
    score: 0.85,
  }));
}

export default {
  generateMockAgentTask,
  generateMockAgentTasks,
  generateMockTraces,
  generateMockKnowledgeList,
  generateMockKnowledgeSearch,
};
