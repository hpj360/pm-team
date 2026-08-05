/**
 * Mock数据 - 监控看板
 * 基于团队空间、时间范围生成可交互的监控数据
 */

import {
  Stage,
  StageName,
  type TeamSpace,
  type KpiData,
  type StageMetricSeries,
  type StageMetricPoint,
  type FunnelStage,
  type FileTypeDist,
  type TopNItem,
  type SearchPercentilePoint,
  type SearchResultBucket,
  type SloStatus,
  type FileEvent,
  type QueueLagItem,
  type FailReasonItem,
  type TimeRange,
} from '@/types';

// ============ 基础数据 ============

/** 团队空间列表 */
export const mockTeamSpaces: TeamSpace[] = [
  { id: 1001, code: 'RED-A-01', name: 'APT追踪组', storageQuota: 500 * 1024 ** 3, fileQuota: 50000, storageUsed: 312 * 1024 ** 3, fileCount: 28400, status: 1 },
  { id: 1002, code: 'RED-A-02', name: '恶意软件分析组', storageQuota: 1024 * 1024 ** 3, fileQuota: 100000, storageUsed: 687 * 1024 ** 3, fileCount: 64200, status: 1 },
  { id: 1003, code: 'RED-B-01', name: '红蓝对抗组', storageQuota: 800 * 1024 ** 3, fileQuota: 80000, storageUsed: 156 * 1024 ** 3, fileCount: 12800, status: 1 },
  { id: 1004, code: 'RED-B-02', name: '钓鱼演练组', storageQuota: 300 * 1024 ** 3, fileQuota: 30000, storageUsed: 289 * 1024 ** 3, fileCount: 24100, status: 1 },
  { id: 1005, code: 'RED-C-01', name: '靶场运营组', storageQuota: 200 * 1024 ** 3, fileQuota: 20000, storageUsed: 48 * 1024 ** 3, fileCount: 4200, status: 1 },
  { id: 1006, code: 'RED-C-02', name: '情报汇聚组', storageQuota: 2048 * 1024 ** 3, fileQuota: 200000, storageUsed: 1480 * 1024 ** 3, fileCount: 156800, status: 1 },
];

/** 文件类型清单 */
const FILE_TYPES = ['pdf', 'docx', 'eml', 'exe', 'pcap', 'zip', 'png', 'log', 'py', 'bin'];

/** 错误码字典 */
const ERROR_CODES: Record<string, string> = {
  'UPLOAD.QUOTA.EXCEED': '团队空间配额超限',
  'UPLOAD.MIME.REJECT': 'MIME类型被拒',
  'UPLOAD.STORAGE.ERR': '对象存储异常',
  'INDEX.ES.REJECTED': 'ES写入拒绝',
  'INDEX.MAPPING.ERR': '映射错误',
  'INDEX.TIMEOUT': '索引超时',
  'PARSE.CORRUPT': '文件损坏',
  'PARSE.PASSWORD': '加密文件',
  'PARSE.OOM': '内存溢出',
  'PARSE.TIMEOUT': '解析超时',
  'PARSE.UNSUPPORTED': '类型不支持',
  'SEARCH.ES.TIMEOUT': '搜索超时',
  'SEARCH.QUERY.ERR': '查询语法错误',
};

/** 热门查询词池 */
const HOT_QUERIES = [
  'apt29', 'cobalt strike', 'powershell', 'mimikatz', 'lateral movement',
  'phishing email', 'ransomware', 'backdoor', 'c2 server', 'credential dump',
  'lpe exploit', 'amsi bypass', 'edr evasion', 'kernel driver', 'shellcode',
  'persistence', 'privilege escalation', 'data exfiltration', 'domain admin', 'kerberoasting',
];

/** 工具函数: 种子随机 */
const seedRandom = (seed: number) => {
  let s = seed;
  return () => {
    s = (s * 9301 + 49297) % 233280;
    return s / 233280;
  };
};

/** 工具函数: 区间随机 */
const rand = (min: number, max: number, rng: () => number = Math.random) =>
  Math.floor(rng() * (max - min + 1)) + min;

/** 工具函数: 浮点区间随机 */
const randFloat = (min: number, max: number, rng: () => number = Math.random) =>
  +(rng() * (max - min) + min).toFixed(2);

/** 时间范围 → 点数与步长(分钟) */
const timeRangeConfig = (range: TimeRange): { points: number; stepMin: number } => {
  switch (range) {
    case '1h':  return { points: 12, stepMin: 5 };
    case '6h':  return { points: 12, stepMin: 30 };
    case '24h': return { points: 24, stepMin: 60 };
    case '7d':  return { points: 28, stepMin: 360 };   // 6h粒度
    case '30d': return { points: 30, stepMin: 1440 };  // 1天粒度
  }
};

/** 生成时间戳序列(从过去到现在) */
const genTimestamps = (range: TimeRange): string[] => {
  const { points, stepMin } = timeRangeConfig(range);
  const now = Date.now();
  const ts: string[] = [];
  for (let i = points - 1; i >= 0; i--) {
    ts.push(new Date(now - i * stepMin * 60 * 1000).toISOString());
  }
  return ts;
};

// ============ KPI ============

/** 获取 KPI 数据 */
export const getMockKpi = (range: TimeRange, teamSpaceId?: number): KpiData => {
  const rng = seedRandom((teamSpaceId || 0) + range.length * 7);
  const space = teamSpaceId ? mockTeamSpaces.find(s => s.id === teamSpaceId)! : undefined;
  const spaces = space ? [space] : mockTeamSpaces;

  return {
    uploadCount: rand(800, 5000, rng) * (range === '1h' ? 1 : range === '30d' ? 30 : 5),
    totalStorage: spaces.reduce((s, x) => s + x.storageUsed, 0),
    spaceCount: spaces.filter(s => s.status === 1).length,
    searchCountToday: rand(2000, 15000, rng),
  };
};

// ============ 阶段指标序列 ============

/** 各阶段基准成功率与耗时(ms) */
const stageBaselines: Record<Stage, { successRate: [number, number]; durationP95: [number, number]; count: [number, number] }> = {
  [Stage.UPLOAD]: { successRate: [99.2, 99.95], durationP95: [800, 1500],  count: [200, 500] },
  [Stage.INDEX]:  { successRate: [98.0, 99.5],  durationP95: [200, 600],   count: [180, 480] },
  [Stage.PARSE]:  { successRate: [93.0, 97.5],  durationP95: [2000, 8000], count: [150, 420] },
  [Stage.SEARCH]: { successRate: [99.0, 99.8],  durationP95: [200, 500],   count: [500, 1500] },
};

/** 获取四阶段指标序列(成功率+P95+count) */
export const getMockStageSeries = (range: TimeRange, teamSpaceId?: number): StageMetricSeries[] => {
  const ts = genTimestamps(range);
  const seed = (teamSpaceId || 0) + range.length * 13;
  return (Object.values(Stage) as Stage[]).map((stage, idx) => {
    const rng = seedRandom(seed + idx * 31);
    const base = stageBaselines[stage];
    const points: StageMetricPoint[] = ts.map(timestamp => ({
      timestamp,
      successRate: randFloat(base.successRate[0], base.successRate[1], rng),
      durationP95: rand(base.durationP95[0], base.durationP95[1], rng),
      count: rand(base.count[0], base.count[1], rng),
    }));
    return { stage, points };
  });
};

// ============ 漏斗 ============

/** 获取上传→索引→解析 漏斗数据 */
export const getMockFunnel = (teamSpaceId?: number): FunnelStage[] => {
  const rng = seedRandom((teamSpaceId || 0) + 99);
  const upload = rand(4000, 6000, rng);
  const indexRate = randFloat(0.92, 0.98, rng);
  const parseRate = randFloat(0.88, 0.96, rng);
  return [
    { stage: Stage.UPLOAD, stageName: StageName[Stage.UPLOAD], value: upload },
    { stage: Stage.INDEX,  stageName: StageName[Stage.INDEX],  value: Math.floor(upload * indexRate) },
    { stage: Stage.PARSE,  stageName: StageName[Stage.PARSE],  value: Math.floor(upload * indexRate * parseRate) },
  ];
};

// ============ 文件类型分布 ============

/** 获取文件类型分布 */
export const getMockFileTypeDist = (teamSpaceId?: number): FileTypeDist[] => {
  const rng = seedRandom((teamSpaceId || 0) + 7);
  return FILE_TYPES.map(t => ({ fileType: t, count: rand(500, 8000, rng) }))
    .sort((a, b) => b.count - a.count);
};

// ============ TopN ============

/** 获取热门查询 Top20 */
export const getMockHotQueries = (teamSpaceId?: number): TopNItem[] => {
  const rng = seedRandom((teamSpaceId || 0) + 11);
  return [...HOT_QUERIES]
    .map((q, i) => ({ rankNo: i + 1, itemKey: q, itemCount: rand(50, 800, rng) }))
    .sort((a, b) => b.itemCount - a.itemCount)
    .map((item, i) => ({ ...item, rankNo: i + 1 }))
    .slice(0, 20);
};

/** 获取零命中查询 Top20 */
export const getMockZeroHitQueries = (teamSpaceId?: number): TopNItem[] => {
  const rng = seedRandom((teamSpaceId || 0) + 17);
  return [...HOT_QUERIES].reverse()
    .map((q, i) => ({ rankNo: i + 1, itemKey: `${q} v2`, itemCount: rand(10, 200, rng) }))
    .sort((a, b) => b.itemCount - a.itemCount)
    .map((item, i) => ({ ...item, rankNo: i + 1 }))
    .slice(0, 20);
};

// ============ 搜索分位数 ============

/** 获取搜索分位数序列 */
export const getMockSearchPercentile = (range: TimeRange, teamSpaceId?: number): SearchPercentilePoint[] => {
  const ts = genTimestamps(range);
  const rng = seedRandom((teamSpaceId || 0) + range.length * 19);
  return ts.map(timestamp => ({
    timestamp,
    p50: rand(80, 180, rng),
    p95: rand(280, 520, rng),
    p99: rand(600, 1200, rng),
  }));
};

/** 获取搜索结果数分布 */
export const getMockSearchResultBuckets = (teamSpaceId?: number): SearchResultBucket[] => {
  const rng = seedRandom((teamSpaceId || 0) + 23);
  return [
    { bucket: '0条',     count: rand(200, 800, rng) },
    { bucket: '1-10条',  count: rand(1500, 3500, rng) },
    { bucket: '11-50条', count: rand(800, 2000, rng) },
    { bucket: '50+条',   count: rand(200, 600, rng) },
  ];
};

// ============ SLO ============

/** 获取 SLO 状态列表 */
export const getMockSloStatus = (teamSpaceId?: number): SloStatus[] => {
  const rng = seedRandom((teamSpaceId || 0) + 29);
  const data: Array<Omit<SloStatus, 'status'>> = [
    { sloCode: 'slo.upload.availability',     sloName: '上传可用性',        stage: Stage.UPLOAD, targetValue: 99.9,  targetUnit: '%',  actualValue: randFloat(99.75, 99.99, rng), errorBudgetRemaining: randFloat(40, 95, rng), burnRate2h: randFloat(0.1, 1.5, rng), burnRate6h: randFloat(0.1, 1.2, rng) },
    { sloCode: 'slo.index.freshness.p95',     sloName: '索引可搜时延 P95',  stage: Stage.INDEX,  targetValue: 60,    targetUnit: 's',  actualValue: randFloat(35, 75, rng),       errorBudgetRemaining: randFloat(30, 90, rng), burnRate2h: randFloat(0.2, 2.0, rng), burnRate6h: randFloat(0.2, 1.6, rng) },
    { sloCode: 'slo.parse.success.rate',      sloName: '解析成功率',        stage: Stage.PARSE,  targetValue: 95,    targetUnit: '%',  actualValue: randFloat(92, 97.5, rng),     errorBudgetRemaining: randFloat(20, 85, rng), burnRate2h: randFloat(0.5, 3.0, rng), burnRate6h: randFloat(0.4, 2.5, rng) },
    { sloCode: 'slo.search.latency.p95',      sloName: '搜索 P95 时延',     stage: Stage.SEARCH, targetValue: 500,   targetUnit: 'ms', actualValue: randFloat(380, 620, rng),     errorBudgetRemaining: randFloat(35, 92, rng), burnRate2h: randFloat(0.1, 1.8, rng), burnRate6h: randFloat(0.1, 1.4, rng) },
    { sloCode: 'slo.search.availability',     sloName: '搜索可用性',        stage: Stage.SEARCH, targetValue: 99.5,  targetUnit: '%',  actualValue: randFloat(99.3, 99.95, rng),  errorBudgetRemaining: randFloat(50, 95, rng), burnRate2h: randFloat(0.1, 1.0, rng), burnRate6h: randFloat(0.1, 0.8, rng) },
  ];
  return data.map(d => ({
    ...d,
    status: d.actualValue >= d.targetValue ? 0 : d.errorBudgetRemaining < 30 ? 2 : 1,
  }));
};

// ============ 队列积压 ============

/** 获取各团队空间解析队列积压 Top */
export const getMockQueueLag = (): QueueLagItem[] => {
  return mockTeamSpaces.map(s => ({
    teamSpaceId: s.id,
    teamSpaceName: s.name,
    lag: Math.floor(Math.random() * 200),
  })).sort((a, b) => b.lag - a.lag);
};

// ============ 失败原因 TopN ============

/** 获取失败原因 TopN */
export const getMockFailReasons = (stage: Stage, teamSpaceId?: number): FailReasonItem[] => {
  const rng = seedRandom((teamSpaceId || 0) + stage.length * 41);
  const codes = Object.entries(ERROR_CODES).filter(([code]) => code.startsWith(stage));
  return codes.map(([code, name]) => ({
    errorCode: code,
    errorName: name,
    count: rand(20, 400, rng),
  })).sort((a, b) => b.count - a.count).slice(0, 5);
};

// ============ 文件事件流 ============

const OPERATORS = [20011, 20012, 20013, 20014, 20015];
const TRACE_PREFIX = 'trace-';

/** 获取最近事件流 */
export const getMockFileEvents = (teamSpaceId: number | undefined, limit = 20): FileEvent[] => {
  const rng = seedRandom((teamSpaceId || 0) + 53);
  const spaces = teamSpaceId ? mockTeamSpaces.filter(s => s.id === teamSpaceId) : mockTeamSpaces;
  const events: FileEvent[] = [];
  const now = Date.now();

  for (let i = 0; i < limit; i++) {
    const space = spaces[Math.floor(rng() * spaces.length)];
    const stage = (Object.values(Stage) as Stage[])[Math.floor(rng() * 4)];
    const isFail = rng() < 0.18;
    const duration = stage === Stage.PARSE ? rand(1500, 9000, rng) : rand(100, 2000, rng);
    const traceId = TRACE_PREFIX + Math.floor(rng() * 1e9).toString(16);

    events.push({
      id: i + 1,
      traceId,
      teamSpaceId: space.id,
      fileId: rand(100000, 999999, rng),
      stage,
      eventType: isFail ? 'FAIL' : 'SUCCESS',
      durationMs: duration,
      fileType: FILE_TYPES[Math.floor(rng() * FILE_TYPES.length)],
      operatorId: OPERATORS[Math.floor(rng() * OPERATORS.length)],
      errorCode: isFail ? Object.keys(ERROR_CODES).find(c => c.startsWith(stage)) : undefined,
      createdAt: new Date(now - i * rand(30, 600, rng) * 1000).toISOString(),
    });
  }
  return events.sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt));
};

// ============ 配额使用率 ============

/** 获取团队空间配额使用率排行 */
export const getMockStorageRanking = (limit = 10): Array<TeamSpace & { usageRate: number }> => {
  return mockTeamSpaces
    .map(s => ({ ...s, usageRate: +((s.storageUsed / s.storageQuota) * 100).toFixed(2) }))
    .sort((a, b) => b.storageUsed - a.storageUsed)
    .slice(0, limit);
};

// ============ 容量增长 ============

/** 获取存储用量趋势 */
export const getMockStorageTrend = (range: TimeRange, teamSpaceId?: number): Array<{ timestamp: string; used: number }> => {
  const ts = genTimestamps(range);
  const rng = seedRandom((teamSpaceId || 0) + range.length * 61);
  const base = (teamSpaceId ? mockTeamSpaces.find(s => s.id === teamSpaceId)?.storageUsed : 4000 * 1024 ** 3) || 1000 * 1024 ** 3;
  let growth = base * 0.9;
  return ts.map(timestamp => {
    growth = growth * (1 + randFloat(0.001, 0.01, rng) / 100);
    return { timestamp, used: Math.floor(growth) };
  });
};

export default {
  mockTeamSpaces,
  getMockKpi,
  getMockStageSeries,
  getMockFunnel,
  getMockFileTypeDist,
  getMockHotQueries,
  getMockZeroHitQueries,
  getMockSearchPercentile,
  getMockSearchResultBuckets,
  getMockSloStatus,
  getMockQueueLag,
  getMockFailReasons,
  getMockFileEvents,
  getMockStorageRanking,
  getMockStorageTrend,
};
