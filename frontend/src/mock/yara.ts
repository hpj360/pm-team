/**
 * Mock 数据 - YARA 规则与扫描结果
 */
import type { YaraRule, YaraScanResult, YaraMatchResult, YaraSeverity } from '@/types';

/** Mock YARA 规则列表 */
export const mockYaraRules: YaraRule[] = [
  {
    id: 'yr0001',
    name: 'Generic_Malware_Downloader',
    description: '通用恶意软件下载器特征',
    author: 'redteam-analyst',
    severity: 'high',
    tags: ['malware', 'downloader'],
    source: `rule Generic_Malware_Downloader {
  strings:
    $a = "WinExec" wide ascii
    $b = "URLDownloadToFile" wide ascii
    $c = { 6A 30 6A 30 6A 30 }
  condition:
    2 of them
}`,
    enabled: true,
    matchCount: 134,
    createTime: '2026-05-10T08:00:00Z',
    updateTime: '2026-07-15T10:00:00Z',
  },
  {
    id: 'yr0002',
    name: 'CobaltStrike_Beacon',
    description: 'Cobalt Strike Beacon 通信特征',
    author: 'redteam-analyst',
    severity: 'critical',
    tags: ['CobaltStrike', 'C2', 'APT'],
    source: `rule CobaltStrike_Beacon {
  strings:
    $a = "%%IMPORT%%" ascii
    $b = { 00 01 00 00 00 00 00 00 }
  condition:
    $a or $b
}`,
    enabled: true,
    matchCount: 67,
    createTime: '2026-04-22T08:00:00Z',
    updateTime: '2026-07-20T10:00:00Z',
  },
  {
    id: 'yr0003',
    name: 'Ransomware_Lock_Extension',
    description: '勒索软件加密后缀行为特征',
    author: 'redteam-analyst',
    severity: 'critical',
    tags: ['ransomware', 'encryption'],
    source: `rule Ransomware_Lock_Extension {
  strings:
    $ext = ".locked" ascii
    $note = "HOW_TO_DECRYPT.txt" wide ascii
  condition:
    $ext and $note
}`,
    enabled: true,
    matchCount: 23,
    createTime: '2026-06-01T08:00:00Z',
    updateTime: '2026-07-25T10:00:00Z',
  },
  {
    id: 'yr0004',
    name: 'Phishing_Suspicious_Link',
    description: '钓鱼邮件可疑链接特征',
    author: 'redteam-analyst',
    severity: 'medium',
    tags: ['phishing', 'email'],
    source: `rule Phishing_Suspicious_Link {
  strings:
    $a = "http://" ascii
    $b = "click here" wide ascii nocase
    $c = "verify your account" wide ascii nocase
  condition:
    $a and any of ($b, $c)
}`,
    enabled: true,
    matchCount: 412,
    createTime: '2026-03-15T08:00:00Z',
    updateTime: '2026-07-22T10:00:00Z',
  },
  {
    id: 'yr0005',
    name: 'Mimikatz_Credential_Dump',
    description: 'Mimikatz 凭证转储工具特征',
    author: 'redteam-analyst',
    severity: 'high',
    tags: ['mimikatz', 'credential', 'lateral'],
    source: `rule Mimikatz_Credential_Dump {
  strings:
    $a = "sekurlsa" ascii nocase
    $b = "kerberos" ascii nocase
    $c = "logonpasswords" ascii nocase
  condition:
    2 of them
}`,
    enabled: true,
    matchCount: 89,
    createTime: '2026-05-28T08:00:00Z',
    updateTime: '2026-07-24T10:00:00Z',
  },
];

/** 严重程度颜色映射（用于 UI 渲染） */
export const yaraSeverityColor: Record<YaraSeverity, string> = {
  info: '#1890ff',
  low: '#52c41a',
  medium: '#faad14',
  high: '#fa541c',
  critical: '#f5222d',
};

export const yaraSeverityText: Record<YaraSeverity, string> = {
  info: '信息',
  low: '低危',
  medium: '中危',
  high: '高危',
  critical: '严重',
};

/**
 * 为指定文件生成 Mock 扫描结果
 */
export function generateMockYaraScanResult(
  fileId: string,
  fileName: string,
  matchedRuleIds: string[] = ['yr0001', 'yr0004'],
): YaraScanResult {
  const matches: YaraMatchResult[] = matchedRuleIds
    .map((ruleId) => mockYaraRules.find((r) => r.id === ruleId))
    .filter((r): r is YaraRule => !!r)
    .map((rule) => ({
      ruleId: rule.id,
      ruleName: rule.name,
      severity: rule.severity,
      description: rule.description,
      tags: rule.tags,
      matchedStrings: [
        {
          value: rule.tags[0] ?? 'suspicious_string',
          offset: Math.floor(Math.random() * 4096),
          length: 16,
          identifier: '$a',
        },
      ],
      matchedAt: new Date().toISOString(),
    }));

  return {
    fileId,
    fileName,
    totalRules: mockYaraRules.length,
    matchedRules: matches.length,
    matches,
    costMs: 128 + Math.floor(Math.random() * 200),
    scannedAt: new Date().toISOString(),
  };
}

export default {
  mockYaraRules,
  yaraSeverityColor,
  yaraSeverityText,
  generateMockYaraScanResult,
};
