/**
 * Mock 数据 - AI 分析模块
 * - 威胁摘要
 * - 攻击链推理
 * - 自然语言搜索
 * - 报告草稿
 * 当后端 ai-service（端口 8093）不可达时，由 services/ai.ts 降级调用本文件
 */
import type {
  ThreatSummary,
  AiAttackChain,
  NlSearchResult,
  ReportDraft,
  SearchResultItem,
} from '@/types';

/** 威胁摘要候选模板（按 fileId 哈希选择，保证同一文件返回稳定结果） */
const THREAT_SUMMARY_TEMPLATES: Array<Omit<ThreatSummary, 'fileId' | 'createdAt'>> = [
  {
    summary:
      '该文件包含高危恶意代码，具备远程控制与横向移动能力。样本通过混淆 loader 加载核心载荷，运行后向外部 C2 节点回连并周期性上报主机指纹，建议立即隔离并启动应急响应。',
    keyFindings: [
      '检测到加壳 PE 文件，入口点位于非标准节区，疑似使用自定义 loader',
      '网络行为中出现与已知 APT28 基础设施匹配的 C2 通信特征',
      '注册表自启动项被写入，实现持久化驻留',
      '释放并加载第二个载荷文件到内存执行，无落盘行为',
    ],
    model: 'qwen2.5-14b-instruct',
    tokens: 1842,
  },
  {
    summary:
      '文件经静态与动态联合分析后判定为钓鱼诱饵文档，携带宏代码与外部模板注入链。打开后将向攻击者控制的域名下载下一阶段载荷，存在凭据窃取与内网渗透前置行为。',
    keyFindings: [
      'Office 文档内嵌 VBA 宏，启用后通过 PowerShell 解密并执行下一阶段',
      '外链域名注册时间近期、隐私保护开启，符合钓鱼基础设施特征',
      '文档元数据中的最后修改者与已知攻击组织存在关联',
      '执行链中出现 regsvr32 / sct 远程脚本加载，绕过应用白名单',
    ],
    model: 'qwen2.5-14b-instruct',
    tokens: 1536,
  },
  {
    summary:
      '该样本为 Linux 平台挖矿木马，集成 SSH 横向移动与竞品清理模块。运行后会清理既有挖矿进程、建立计划任务持久化，并向矿池地址发起长连接，占用大量 CPU 资源。',
    keyFindings: [
      '样本包含 x86 与 ARM 双架构 ELF 载荷，适配多种服务器环境',
      '内置 SSH 弱口令字典，尝试向同网段主机横向扩散',
      '清理其他挖矿进程并修改 /etc/cron* 实现持久化',
      'C2 通信使用加密协议，矿池地址经 DGA 域名中转',
    ],
    model: 'qwen2.5-14b-instruct',
    tokens: 1280,
  },
];

/** 攻击链候选模板 */
const ATTACK_CHAIN_TEMPLATES: Array<Omit<AiAttackChain, 'reasoning'>> = [
  {
    attackPaths: [
      {
        name: '钓鱼邮件 → 宏代码执行 → C2 回连',
        description:
          '攻击者通过携带恶意宏的诱饵文档诱导用户启用宏，宏代码解密并执行下一阶段载荷，建立 C2 通道。',
        steps: [
          '1. 投递伪装成财务报销的钓鱼邮件，附件为 .docm 文件',
          '2. 用户启用宏后，VBA 通过 PowerShell 解密 base64 载荷',
          '3. 载荷注入 explorer.exe，向 C2 域名发起 HTTPS 心跳',
          '4. C2 下发指令执行凭据窃取与横向探测',
        ],
      },
      {
        name: '漏洞利用 → 提权 → 持久化',
        description:
          '利用应用漏洞获取初始执行权限，随后通过内核提权漏洞获取 root，写入计划任务持久化。',
        steps: [
          '1. 利用 Web 服务反序列化漏洞获取初始 shell',
          '2. 触发 CVE-2024-1234 内核提权获取 root 权限',
          '3. 写入 /etc/cron.d 与 systemd service 实现持久化',
          '4. 清理日志并植入 rootkit 隐藏进程',
        ],
      },
    ],
    confidence: 0.82,
  },
  {
    attackPaths: [
      {
        name: '供应链感染 → 内网横向 → 数据外泄',
        description:
          '通过被污染的第三方依赖进入内网，横向移动至数据库服务器后外泄敏感数据。',
        steps: [
          '1. 受感染的开源库随构建产物进入内部应用',
          '2. 恶意代码在应用启动时激活，扫描内网存活主机',
          '3. 利用 SMB 弱配置横向移动至数据库服务器',
          '4. 打包敏感数据并通过 DNS 隧道外泄',
        ],
      },
    ],
    confidence: 0.71,
  },
];

/** 简单字符串哈希（用于根据 fileId 选择稳定模板） */
function hashString(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0; // 转为 32 位整数
  }
  return Math.abs(hash);
}

/**
 * 生成 Mock 威胁摘要
 * @param fileId 文件 ID
 */
export function generateMockThreatSummary(fileId: string): ThreatSummary {
  const idx = hashString(fileId) % THREAT_SUMMARY_TEMPLATES.length;
  const template = THREAT_SUMMARY_TEMPLATES[idx];
  return {
    ...template,
    fileId,
    createdAt: new Date().toISOString(),
  };
}

/**
 * 生成 Mock 攻击链推理
 * @param fileId 文件 ID
 */
export function generateMockAttackChain(fileId: string): AiAttackChain {
  const idx = hashString(fileId) % ATTACK_CHAIN_TEMPLATES.length;
  const template = ATTACK_CHAIN_TEMPLATES[idx];
  const usedPaths = template.attackPaths.length;
  return {
    ...template,
    reasoning: `基于文件 ${fileId} 的静态特征、动态行为与关联威胁情报综合推理，共识别 ${usedPaths} 条潜在攻击路径。结合 IOC 命中与行为序列相似度，整体置信度为 ${(template.confidence * 100).toFixed(0)}%。建议结合上下文进一步验证。`,
  };
}

/**
 * 生成 Mock 自然语言搜索结果
 * @param query 用户输入的自然语言查询
 */
export function generateMockNlSearchResult(query: string): NlSearchResult {
  // 基于 query 生成 3~5 条模拟搜索结果
  const keywords = query.trim().split(/\s+/).filter(Boolean);
  const primary = keywords[0] ?? '威胁';

  const items: SearchResultItem[] = Array.from({ length: 4 }, (_, i) => {
    const id = `nl-${Date.now()}-${i}`;
    return {
      id,
      fileId: `f-nl-${i + 1}`,
      fileName: `${primary}_相关样本_${i + 1}.pdf`,
      score: Math.max(0.6, 0.95 - i * 0.08),
      highlights: [
        {
          field: 'content',
          fragments: [`该文件涉及 <em>${primary}</em> 相关的恶意行为与 IOC 命中`],
        },
      ],
      snippet: `与「${query}」语义相关的样本，包含 ${primary} 相关特征。`,
      metadata: {},
      matchedFields: ['fileName', 'content'],
      fileType: 'document',
      fileSize: 256 * 1024 + i * 1024,
      tags: [primary, 'AI检索'],
      uploaderName: 'ai-service',
      createTime: new Date().toISOString(),
    };
  });

  return {
    translatedQuery: `查找与「${query}」语义相关的文件（已转换为关键词：${keywords.join(' / ') || primary}）`,
    searchResults: items,
  };
}

/**
 * 生成 Mock 报告草稿
 * @param reportId 报告 ID
 */
export function generateMockReportDraft(reportId: string): ReportDraft {
  return {
    reportId,
    conclusion:
      `## 分析结论\n\n` +
      `本报告（${reportId}）综合静态分析、动态行为与威胁情报关联，识别出**高危恶意行为**。` +
      `样本具备持久化驻留、C2 通信与横向移动能力，与已知 APT 组织 TTP 高度重合，建议按重大安全事件处置。\n\n` +
      `## 关键证据\n\n` +
      `- 文件哈希与威胁情报库命中 3 条记录\n` +
      `- 动态行为中出现加密 C2 心跳与凭据窃取\n` +
      `- 检测到内核提权漏洞利用链\n`,
    recommendations: [
      '立即隔离受影响主机，阻断 C2 通信并保留内存取证镜像',
      '在全网范围检索同哈希、同 C2 域名的关联文件与主机',
      '更新 EDR / YARA 规则覆盖本次发现的 IOC 与行为特征',
      '对受影响账号强制重置口令并审计近 30 天登录行为',
      '完善钓鱼邮件过滤策略，拦截同类诱饵文档',
    ],
    createdAt: new Date().toISOString(),
  };
}

export default {
  generateMockThreatSummary,
  generateMockAttackChain,
  generateMockNlSearchResult,
  generateMockReportDraft,
};
