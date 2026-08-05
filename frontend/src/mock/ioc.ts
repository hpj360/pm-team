/**
 * Mock 数据 - IOC 列表（50 条）
 */
import { IocType } from '@/types';
import type { IocItem, IocAggregation } from '@/types';

/** 目标文件池 */
const sourceFiles = [
  { id: 'f0001', name: 'malware_sample_001.exe' },
  { id: 'f0005', name: 'network_traffic_005.pcap' },
  { id: 'f0012', name: 'phishing_email_012.eml' },
  { id: 'f0018', name: 'attack_report_018.pdf' },
  { id: 'f0023', name: 'exploit_code_023.py' },
  { id: 'f0031', name: 'config_backup_031.zip' },
  { id: 'f0042', name: 'payload_042.bin' },
  { id: 'f0056', name: 'system_log_056.log' },
];

/** 威胁分类 */
const threatCategories = ['C2', 'Phishing', 'Malware', 'Reconnaissance', 'Lateral Movement', 'Exfiltration'];

/** 情报源 */
const intelligenceSources = ['AlienVault OTX', 'VirusTotal', 'URLhaus', 'Hybrid Analysis', ' AbuseIPDB', 'ThreatFox'];

/** 标签池 */
const tagPool = ['恶意', '可疑', 'APT29', 'APT41', 'Cobalt Strike', 'Mimikatz', 'Ransomware', 'Trojan'];

const randomDate = (start: Date, end: Date): string => {
  return new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime())).toISOString();
};

const randomInt = (min: number, max: number): number =>
  Math.floor(Math.random() * (max - min + 1)) + min;

const pick = <T>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)];

const pickMany = <T>(arr: T[], count: number): T[] => {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, count);
};

/** IOC 值生成器 */
function generateIocValue(type: IocType, index: number): string {
  switch (type) {
    case IocType.IP:
      return `${randomInt(10, 220)}.${randomInt(0, 255)}.${randomInt(0, 255)}.${randomInt(1, 254)}`;
    case IocType.DOMAIN:
      return `malicious-${index}.example-evil.com`;
    case IocType.URL:
      return `http://malicious-${index}.example-evil.com/payload?id=${index}`;
    case IocType.EMAIL:
      return `attacker${index}@evil-mail.com`;
    case IocType.MD5:
      return Array.from({ length: 32 }, () => '0123456789abcdef'[randomInt(0, 15)]).join('');
    case IocType.SHA1:
      return Array.from({ length: 40 }, () => '0123456789abcdef'[randomInt(0, 15)]).join('');
    case IocType.SHA256:
      return Array.from({ length: 64 }, () => '0123456789abcdef'[randomInt(0, 15)]).join('');
    case IocType.CVE:
      return `CVE-2024-${randomInt(1000, 9999)}`;
    case IocType.BTC:
      return `bc1q${Array.from({ length: 38 }, () => '0123456789abcdefghijklmnopqrstuvwxyz'[randomInt(0, 31)]).join('')}`;
    default:
      return `unknown-${index}`;
  }
}

/** IOC 类型权重（IP/Domain/URL 较多） */
const iocTypeWeights: Array<[IocType, number]> = [
  [IocType.IP, 15],
  [IocType.DOMAIN, 12],
  [IocType.URL, 8],
  [IocType.EMAIL, 5],
  [IocType.MD5, 4],
  [IocType.SHA256, 3],
  [IocType.CVE, 2],
  [IocType.BTC, 1],
];

/** 加权随机选择 IOC 类型 */
function weightedPickType(): IocType {
  const total = iocTypeWeights.reduce((sum, [, w]) => sum + w, 0);
  let r = Math.random() * total;
  for (const [type, w] of iocTypeWeights) {
    r -= w;
    if (r <= 0) return type;
  }
  return IocType.IP;
}

/** 生成单条 IOC */
function generateIoc(index: number): IocItem {
  const type = weightedPickType();
  const source = pick(sourceFiles);
  const malicious = Math.random() > 0.2;
  const firstSeen = randomDate(new Date('2024-06-01'), new Date('2025-06-01'));
  const lastSeen = randomDate(new Date(firstSeen), new Date());
  const occurrences = randomInt(1, 50);

  return {
    id: `ioc_${index.toString().padStart(4, '0')}`,
    type,
    value: generateIocValue(type, index),
    confidence: Math.round((0.5 + Math.random() * 0.5) * 100) / 100,
    sourceFileId: source.id,
    sourceFileName: source.name,
    tags: pickMany(tagPool, randomInt(1, 3)),
    firstSeen,
    lastSeen,
    occurrences,
    malicious,
    threatCategory: malicious ? pick(threatCategories) : undefined,
    intelligenceSources: pickMany(intelligenceSources, randomInt(1, 2)),
  };
}

/** Mock IOC 列表（50 条） */
export const mockIocList: IocItem[] = Array.from({ length: 50 }, (_, i) => generateIoc(i + 1));

/** Mock IOC 聚合统计 */
export const mockIocAggregation: IocAggregation = (() => {
  const typeMap = new Map<IocType, number>();
  const categoryMap = new Map<string, number>();
  let highConfidenceCount = 0;
  let maliciousCount = 0;

  for (const ioc of mockIocList) {
    typeMap.set(ioc.type, (typeMap.get(ioc.type) ?? 0) + 1);
    if (ioc.threatCategory) {
      categoryMap.set(ioc.threatCategory, (categoryMap.get(ioc.threatCategory) ?? 0) + 1);
    }
    if (ioc.confidence >= 0.8) highConfidenceCount++;
    if (ioc.malicious) maliciousCount++;
  }

  return {
    typeDistribution: Array.from(typeMap.entries()).map(([type, count]) => ({ type, count })),
    categoryDistribution: Array.from(categoryMap.entries()).map(([category, count]) => ({ category, count })),
    highConfidenceCount,
    maliciousCount,
    total: mockIocList.length,
  };
})();

/** 分页查询 */
export function getMockIocList(params: {
  keyword?: string;
  type?: IocType;
  malicious?: boolean;
  threatCategory?: string;
  page: number;
  pageSize: number;
  sortBy?: 'occurrences' | 'firstSeen' | 'lastSeen' | 'confidence';
  order?: 'asc' | 'desc';
}): { list: IocItem[]; total: number } {
  let list = [...mockIocList];

  if (params.keyword) {
    const kw = params.keyword.toLowerCase();
    list = list.filter(
      (i) =>
        i.value.toLowerCase().includes(kw) ||
        i.sourceFileName.toLowerCase().includes(kw) ||
        i.tags.some((t) => t.toLowerCase().includes(kw)),
    );
  }
  if (params.type) list = list.filter((i) => i.type === params.type);
  if (params.malicious !== undefined) list = list.filter((i) => i.malicious === params.malicious);
  if (params.threatCategory) list = list.filter((i) => i.threatCategory === params.threatCategory);

  const sortBy = params.sortBy ?? 'occurrences';
  const order = params.order ?? 'desc';
  list.sort((a, b) => {
    const av = a[sortBy];
    const bv = b[sortBy];
    if (av === bv) return 0;
    const cmp = av < bv ? -1 : 1;
    return order === 'asc' ? cmp : -cmp;
  });

  const total = list.length;
  const start = (params.page - 1) * params.pageSize;
  return { list: list.slice(start, start + params.pageSize), total };
}

export default {
  mockIocList,
  mockIocAggregation,
  getMockIocList,
};
