/**
 * Mock 数据 - 后台 YARA 规则
 */
import type { AdminYaraRule, YaraTestResult } from '@/types';

export const mockAdminYaraRules: AdminYaraRule[] = [
  {
    id: 'ayr001',
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
    isCustom: true,
    createTime: '2026-05-10T08:00:00Z',
    updateTime: '2026-07-15T10:00:00Z',
  },
  {
    id: 'ayr002',
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
    isCustom: true,
    createTime: '2026-04-22T08:00:00Z',
    updateTime: '2026-07-20T10:00:00Z',
  },
  {
    id: 'ayr003',
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
    isCustom: true,
    createTime: '2026-06-01T08:00:00Z',
    updateTime: '2026-07-25T10:00:00Z',
  },
  {
    id: 'ayr004',
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
    isCustom: false,
    createTime: '2026-03-15T08:00:00Z',
    updateTime: '2026-07-22T10:00:00Z',
  },
  {
    id: 'ayr005',
    name: 'Mimikatz_Credential_Dump',
    description: 'Mimikatz 凭证转储工具特征',
    author: 'redteam-analyst',
    severity: 'high',
    tags: ['mimikatz', 'credential'],
    source: `rule Mimikatz_Credential_Dump {
  strings:
    $a = "sekurlsa" ascii nocase
    $b = "kerberos" ascii nocase
    $c = "logonpasswords" ascii nocase
  condition:
    2 of them
}`,
    enabled: false,
    matchCount: 89,
    isCustom: true,
    createTime: '2026-05-28T08:00:00Z',
    updateTime: '2026-07-24T10:00:00Z',
  },
  {
    id: 'ayr006',
    name: 'Empire_PowerShell_Launcher',
    description: 'Empire PowerShell 启动器特征',
    author: 'redteam-analyst',
    severity: 'high',
    tags: ['Empire', 'PowerShell'],
    source: `rule Empire_PowerShell_Launcher {
  strings:
    $a = "Invoke-Empire" ascii
    $b = { 45 6D 70 69 72 65 }
  condition:
    $a or $b
}`,
    enabled: true,
    matchCount: 18,
    isCustom: true,
    createTime: '2026-06-15T08:00:00Z',
    updateTime: '2026-07-23T10:00:00Z',
  },
];

/** 测试 YARA 规则（Mock） */
export function testYaraRule(source: string): YaraTestResult {
  const hasMatch = source.includes('rule') && source.length > 30;
  return {
    matched: hasMatch,
    matchedRules: hasMatch ? ['test_rule'] : [],
    costMs: 20 + Math.floor(Math.random() * 50),
    output: hasMatch
      ? '[+] Scan completed. 1 rule matched.\n[*] test_rule matched at offset 0x400'
      : '[*] Scan completed. 0 rules matched.',
  };
}

export default { mockAdminYaraRules, testYaraRule };
