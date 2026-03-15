/**
 * Mock数据 - 分析结果
 */

import type {
  AnalyzeTask,
  AnalyzeResult,
  AnalyzeDetail,
  IocInfo,
  RiskInfo,
  AnalyzeStatistics,
  AnalyzeType,
  AnalyzeStatus,
  IocType,
} from '@/types';
import { AnalyzeStatus as AS, AnalyzeType as AT, IocType as IT } from '@/types';

// 分析类型映射
const analyzeTypeNames: Record<AnalyzeType, string> = {
  [AT.CONTENT]: '内容分析',
  [AT.MALWARE]: '恶意软件分析',
  [AT.NETWORK]: '网络行为分析',
  [AT.CRYPTO]: '加密分析',
  [AT.METADATA]: '元数据分析',
  [AT.IOC]: 'IOC提取',
};

/**
 * 生成Mock IOC信息
 */
const generateMockIocs = (): IocInfo[] => {
  const iocs: IocInfo[] = [
    {
      type: IT.IP,
      value: '192.168.1.100',
      confidence: 0.95,
      source: 'AlienVault OTX',
      tags: ['C2', 'malicious'],
    },
    {
      type: IT.DOMAIN,
      value: 'malicious.example.com',
      confidence: 0.88,
      source: 'VirusTotal',
      tags: ['phishing', 'C2'],
    },
    {
      type: IT.URL,
      value: 'http://malicious.example.com/payload.exe',
      confidence: 0.92,
      source: 'URLhaus',
      tags: ['malware', 'download'],
    },
    {
      type: IT.MD5,
      value: 'd41d8cd98f00b204e9800998ecf8427e',
      confidence: 0.99,
      source: 'Hybrid Analysis',
      tags: ['malware', 'rat'],
    },
    {
      type: IT.SHA256,
      value: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      confidence: 0.99,
      source: 'VirusTotal',
      tags: ['malware'],
    },
  ];
  
  return iocs.slice(0, 3 + Math.floor(Math.random() * 3));
};

/**
 * 生成Mock风险信息
 */
const generateMockRisks = (): RiskInfo[] => {
  const risks: RiskInfo[] = [
    {
      level: 'critical',
      category: '恶意行为',
      description: '检测到恶意代码注入行为，可能危害系统安全',
      score: 95,
      vector: 'CVE-2024-1234',
    },
    {
      level: 'high',
      category: '网络通信',
      description: '发现与已知恶意服务器的通信行为',
      score: 85,
    },
    {
      level: 'medium',
      category: '权限提升',
      description: '尝试获取系统管理员权限',
      score: 65,
    },
    {
      level: 'low',
      category: '信息泄露',
      description: '可能泄露系统配置信息',
      score: 35,
    },
  ];
  
  return risks.slice(0, 2 + Math.floor(Math.random() * 3));
};

/**
 * 生成Mock分析详情
 */
const generateMockDetails = (): AnalyzeDetail[] => {
  return [
    {
      category: '静态分析',
      title: 'PE文件结构分析',
      description: '检测到异常的PE节区，可能存在加壳或代码注入',
      severity: 'warning',
      evidence: ['.text节区异常大', '入口点位于非标准位置'],
    },
    {
      category: '动态分析',
      title: '进程行为分析',
      description: '样本运行时创建了多个子进程，并尝试注入代码',
      severity: 'critical',
      evidence: ['创建进程: svchost.exe', '注入目标: explorer.exe'],
    },
    {
      category: '网络分析',
      title: '网络通信分析',
      description: '检测到与外部服务器的HTTP通信',
      severity: 'warning',
      evidence: ['目标IP: 192.168.1.100', '协议: HTTP', '端口: 8080'],
    },
  ];
};

/**
 * 生成Mock分析结果
 */
export const generateMockAnalyzeResult = (taskId: string, fileId: string): AnalyzeResult => {
  return {
    taskId,
    fileId,
    type: AT.MALWARE,
    summary: '该文件被识别为恶意软件，具有远程控制功能。建议立即隔离并进一步分析。',
    details: generateMockDetails(),
    iocs: generateMockIocs(),
    risks: generateMockRisks(),
    charts: [
      {
        type: 'pie',
        title: '威胁类型分布',
        data: {
          series: [
            { name: '恶意软件', value: 45 },
            { name: '钓鱼', value: 25 },
            { name: '漏洞利用', value: 20 },
            { name: '其他', value: 10 },
          ],
        },
      },
      {
        type: 'bar',
        title: '风险评分分布',
        data: {
          categories: ['高危', '中危', '低危', '信息'],
          series: [{ name: '数量', data: [12, 25, 18, 8] }],
        },
      },
    ],
    recommendations: [
      '立即隔离该文件，防止进一步传播',
      '检查网络中是否存在与该IOC相关的通信',
      '对受影响系统进行彻底扫描和清理',
      '更新安全策略和防护规则',
    ],
    createTime: new Date().toISOString(),
  };
};

/**
 * 生成Mock分析任务
 */
export const generateMockAnalyzeTask = (
  id: string,
  type: AnalyzeType = AT.MALWARE
): AnalyzeTask => {
  const statuses: AnalyzeStatus[] = [
    AS.PENDING,
    AS.RUNNING,
    AS.COMPLETED,
    AS.FAILED,
  ];
  const status = statuses[Math.floor(Math.random() * statuses.length)];
  
  let progress = 0;
  if (status === AS.RUNNING) {
    progress = Math.floor(Math.random() * 100);
  } else if (status === AS.COMPLETED) {
    progress = 100;
  }

  const now = new Date();
  const createTime = new Date(now.getTime() - Math.random() * 7 * 24 * 60 * 60 * 1000);

  return {
    id,
    fileId: `f${id}`,
    fileName: `analyze_sample_${id}.exe`,
    type,
    status,
    progress,
    createTime: createTime.toISOString(),
    updateTime: now.toISOString(),
    completeTime: status === AS.COMPLETED ? now.toISOString() : undefined,
  };
};

/**
 * 生成Mock分析任务列表
 */
export const generateMockAnalyzeTasks = (count: number = 20): AnalyzeTask[] => {
  const types = [AT.CONTENT, AT.MALWARE, AT.NETWORK, AT.CRYPTO, AT.METADATA, AT.IOC];
  
  return Array.from({ length: count }, (_, index) => {
    const type = types[Math.floor(Math.random() * types.length)];
    return generateMockAnalyzeTask(`t${(index + 1).toString().padStart(4, '0')}`, type);
  });
};

/**
 * Mock分析任务列表
 */
export const mockAnalyzeTasks: AnalyzeTask[] = generateMockAnalyzeTasks(50);

/**
 * Mock分析统计数据
 */
export const mockAnalyzeStatistics: AnalyzeStatistics = {
  totalTasks: 150,
  completedTasks: 120,
  runningTasks: 5,
  failedTasks: 25,
  avgCostTime: 45.5,
  typeDistribution: {
    [AT.CONTENT]: 30,
    [AT.MALWARE]: 45,
    [AT.NETWORK]: 25,
    [AT.CRYPTO]: 15,
    [AT.METADATA]: 20,
    [AT.IOC]: 15,
  },
  statusDistribution: {
    [AS.PENDING]: 10,
    [AS.RUNNING]: 5,
    [AS.COMPLETED]: 120,
    [AS.FAILED]: 15,
  },
};

/**
 * 获取Mock分析任务列表（分页）
 */
export const getMockAnalyzeTasks = (
  page: number = 1,
  pageSize: number = 10,
  status?: AnalyzeStatus,
  type?: AnalyzeType
): { list: AnalyzeTask[]; total: number } => {
  let filteredList = [...mockAnalyzeTasks];

  if (status) {
    filteredList = filteredList.filter((task) => task.status === status);
  }

  if (type) {
    filteredList = filteredList.filter((task) => task.type === type);
  }

  const total = filteredList.length;
  const start = (page - 1) * pageSize;
  const list = filteredList.slice(start, start + pageSize);

  return { list, total };
};

export default {
  mockAnalyzeTasks,
  mockAnalyzeStatistics,
  generateMockAnalyzeResult,
  generateMockAnalyzeTask,
  getMockAnalyzeTasks,
};
