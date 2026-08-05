/**
 * Mock 数据 - NER 实体识别结果
 */
import { NerEntityType } from '@/types';
import type { NerResult, NerEntity } from '@/types';

/** 实体类型颜色（用于 UI 高亮渲染） */
export const nerEntityTypeColor: Record<NerEntityType, string> = {
  [NerEntityType.IP]: '#1890ff',
  [NerEntityType.DOMAIN]: '#52c41a',
  [NerEntityType.URL]: '#722ed1',
  [NerEntityType.EMAIL]: '#13c2c2',
  [NerEntityType.HASH]: '#fa8c16',
  [NerEntityType.CVE]: '#f5222d',
  [NerEntityType.FILE_PATH]: '#fa541c',
  [NerEntityType.REGISTRY]: '#eb2f96',
  [NerEntityType.PERSON]: '#2f54eb',
  [NerEntityType.ORGANIZATION]: '#a0d911',
  [NerEntityType.LOCATION]: '#fadb14',
  [NerEntityType.MONEY]: '#08979c',
  [NerEntityType.DATE]: '#5b8c00',
  [NerEntityType.PHONE]: '#c41d7f',
  [NerEntityType.BITCOIN]: '#faad14',
};

/**
 * 为指定文件生成 Mock NER 结果
 */
export function generateMockNerResult(fileId: string, fileName: string): NerResult {
  const entities: NerEntity[] = [
    {
      id: 'ne1',
      type: NerEntityType.IP,
      value: '192.168.1.100',
      start: 128,
      end: 141,
      confidence: 0.98,
      normalized: '192.168.1.100',
    },
    {
      id: 'ne2',
      type: NerEntityType.IP,
      value: '10.0.0.5',
      start: 256,
      end: 265,
      confidence: 0.95,
      normalized: '10.0.0.5',
    },
    {
      id: 'ne3',
      type: NerEntityType.DOMAIN,
      value: 'malicious.example.com',
      start: 320,
      end: 341,
      confidence: 0.92,
      normalized: 'malicious.example.com',
    },
    {
      id: 'ne4',
      type: NerEntityType.URL,
      value: 'http://malicious.example.com/payload.exe',
      start: 410,
      end: 452,
      confidence: 0.99,
    },
    {
      id: 'ne5',
      type: NerEntityType.EMAIL,
      value: 'attacker@evil-mail.com',
      start: 540,
      end: 562,
      confidence: 0.96,
    },
    {
      id: 'ne6',
      type: NerEntityType.HASH,
      value: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      start: 678,
      end: 742,
      confidence: 0.99,
    },
    {
      id: 'ne7',
      type: NerEntityType.CVE,
      value: 'CVE-2024-1234',
      start: 820,
      end: 834,
      confidence: 0.94,
    },
    {
      id: 'ne8',
      type: NerEntityType.FILE_PATH,
      value: 'C:\\Windows\\System32\\malware.dll',
      start: 905,
      end: 938,
      confidence: 0.91,
    },
    {
      id: 'ne9',
      type: NerEntityType.REGISTRY,
      value: 'HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run',
      start: 1010,
      end: 1062,
      confidence: 0.89,
    },
    {
      id: 'ne10',
      type: NerEntityType.PERSON,
      value: '张伟',
      start: 1120,
      end: 1122,
      confidence: 0.78,
    },
    {
      id: 'ne11',
      type: NerEntityType.ORGANIZATION,
      value: 'APT41',
      start: 1180,
      end: 1185,
      confidence: 0.88,
    },
    {
      id: 'ne12',
      type: NerEntityType.BITCOIN,
      value: 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh',
      start: 1240,
      end: 1282,
      confidence: 0.93,
    },
  ];

  const typeMap = new Map<NerEntityType, number>();
  for (const e of entities) {
    typeMap.set(e.type, (typeMap.get(e.type) ?? 0) + 1);
  }

  return {
    fileId,
    fileName,
    textLength: 2048,
    totalEntities: entities.length,
    typeDistribution: Array.from(typeMap.entries()).map(([type, count]) => ({ type, count })),
    entities,
    costMs: 95 + Math.floor(Math.random() * 80),
    processedAt: new Date().toISOString(),
  };
}

export default {
  nerEntityTypeColor,
  generateMockNerResult,
};
