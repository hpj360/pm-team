/**
 * Mock 数据 - 报告中心
 */
import type {
  ReportItem,
  ReportTemplate,
  ReportType,
  ReportSchedule,
  ReportScheduleHistory,
} from '@/types';

/** 报告模板（8 个） */
export const mockReportTemplates: ReportTemplate[] = [
  {
    id: 'tpl_001',
    name: '标准渗透测试报告模板',
    type: 'penetration',
    description: '覆盖侦察 / 入侵 / 横向 / 持久化 / 数据外取全过程，适用于甲方验收交付。',
    fields: [
      { key: 'targetName', label: '目标名称', required: true },
      { key: 'timeRange', label: '作业时间范围', required: true },
      { key: 'attackSurface', label: '攻击面', required: false },
      { key: 'findings', label: '主要发现', required: true },
      { key: 'riskRating', label: '风险评级', required: true },
    ],
    defaultFormat: 'pdf',
    builtin: true,
    updateTime: '2026-06-01T00:00:00Z',
  },
  {
    id: 'tpl_002',
    name: '漏洞分析专项报告',
    type: 'vulnerability',
    description: '针对单漏洞或多漏洞的深度分析，含 CVE、CVSS、PoC、修复建议。',
    fields: [
      { key: 'cveList', label: 'CVE 列表', required: true },
      { key: 'affected', label: '受影响资产', required: true },
      { key: 'poc', label: 'PoC', required: false },
      { key: 'remediation', label: '修复建议', required: true },
    ],
    defaultFormat: 'pdf',
    builtin: true,
    updateTime: '2026-06-05T00:00:00Z',
  },
  {
    id: 'tpl_003',
    name: '威胁情报汇总报告',
    type: 'threat_intel',
    description: '汇总一段时间内的威胁情报订阅、IOC、APT 活动。',
    fields: [
      { key: 'timeRange', label: '时间范围', required: true },
      { key: 'iocCount', label: 'IOC 数量', required: true },
      { key: 'aptGroups', label: 'APT 组织', required: false },
    ],
    defaultFormat: 'html',
    builtin: true,
    updateTime: '2026-06-10T00:00:00Z',
  },
  {
    id: 'tpl_004',
    name: '攻击链路可视化报告',
    type: 'attack_chain',
    description: '含 kill chain 各阶段时间线、矩阵图。',
    fields: [
      { key: 'chainName', label: '攻击链名称', required: true },
      { key: 'stages', label: '阶段', required: true },
    ],
    defaultFormat: 'html',
    builtin: true,
    updateTime: '2026-06-15T00:00:00Z',
  },
  {
    id: 'tpl_005',
    name: '资产测绘清单',
    type: 'asset',
    description: '输出资产清单（CSV / Markdown），含 IP/端口/服务/标签。',
    fields: [
      { key: 'targetName', label: '目标名称', required: true },
      { key: 'assets', label: '资产清单', required: true },
    ],
    defaultFormat: 'markdown',
    builtin: true,
    updateTime: '2026-06-20T00:00:00Z',
  },
  {
    id: 'tpl_006',
    name: '审计合规报告',
    type: 'audit',
    description: '面向审计员的合规自查报告。',
    fields: [
      { key: 'timeRange', label: '审计时间范围', required: true },
      { key: 'complianceItems', label: '合规项', required: true },
    ],
    defaultFormat: 'pdf',
    builtin: true,
    updateTime: '2026-06-25T00:00:00Z',
  },
  {
    id: 'tpl_007',
    name: '红队季度复盘报告',
    type: 'penetration',
    description: '季度复盘模板，含 KPI、阶段成果、待改进项。',
    fields: [
      { key: 'quarter', label: '季度', required: true },
      { key: 'kpi', label: 'KPI', required: true },
    ],
    defaultFormat: 'pdf',
    builtin: false,
    updateTime: '2026-07-01T00:00:00Z',
  },
  {
    id: 'tpl_008',
    name: 'OT 工控安全专项',
    type: 'penetration',
    description: '面向 SCADA / PLC 的工控渗透报告。',
    fields: [
      { key: 'targetName', label: '目标名称', required: true },
      { key: 'protocols', label: '协议', required: true },
    ],
    defaultFormat: 'pdf',
    builtin: false,
    updateTime: '2026-07-10T00:00:00Z',
  },
];

/** 生成 HTML 预览内容（Mock） */
function buildHtmlPreview(title: string, type: ReportType, targetName?: string): string {
  return `<section style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; padding: 24px;">
  <h1 style="color: #f5222d; border-bottom: 2px solid #f5222d; padding-bottom: 8px;">${title}</h1>
  <p style="color: #595959;">报告类型：${type} ｜ 目标：${targetName ?? '-'}</p>
  <h2 style="margin-top: 24px;">1. 执行摘要</h2>
  <p>本次红队作业针对 <strong>${targetName ?? '目标系统'}</strong> 进行，发现多个高危风险：</p>
  <ul>
    <li>员工门户 SSRF（CVE-2021-26855，已利用）</li>
    <li>运维后台 actuator 端点未鉴权，泄露运维凭据</li>
    <li>NovaCloud API 越权，可获取全部租户数据</li>
    <li>SCADA Modbus 未授权，可下发控制指令</li>
  </ul>
  <h2>2. 风险评级</h2>
  <table style="border-collapse: collapse; width: 100%;">
    <thead><tr style="background: #fafafa;"><th style="border: 1px solid #d9d9d9; padding: 8px;">风险</th><th style="border: 1px solid #d9d9d9; padding: 8px;">等级</th><th style="border: 1px solid #d9d9d9; padding: 8px;">建议</th></tr></thead>
    <tbody>
      <tr><td style="border: 1px solid #d9d9d9; padding: 8px;">Exchange ProxyLogon</td><td style="border: 1px solid #d9d9d9; padding: 8px; color: #f5222d;">严重</td><td style="border: 1px solid #d9d9d9; padding: 8px;">立即打补丁</td></tr>
      <tr><td style="border: 1px solid #d9d9d9; padding: 8px;">actuator 暴露</td><td style="border: 1px solid #d9d9d9; padding: 8px; color: #fa541c;">高</td><td style="border: 1px solid #d9d9d9; padding: 8px;">禁用 actuator</td></tr>
      <tr><td style="border: 1px solid #d9d9d9; padding: 8px;">API 越权</td><td style="border: 1px solid #d9d9d9; padding: 8px; color: #f5222d;">严重</td><td style="border: 1px solid #d9d9d9; padding: 8px;">完善鉴权</td></tr>
    </tbody>
  </table>
  <h2>3. 详细攻击链</h2>
  <p>详见攻击链路模块，本次作业共形成 4 个 kill chain 阶段，详见时间线图。</p>
  <h2>4. 修复建议</h2>
  <ol>
    <li>立即打补丁：Exchange、Spring Boot、SCADA 上位机。</li>
    <li>完善 API 网关鉴权与租户隔离。</li>
    <li>启用 EDR 并对 C2 流量进行检测。</li>
    <li>加强员工安全意识培训，定期开展钓鱼演练。</li>
  </ol>
  <p style="margin-top: 32px; color: #8c8c8c; font-size: 12px; border-top: 1px solid #f0f0f0; padding-top: 8px;">本报告由红方文件汇聚平台自动生成 · 2026-07-27</p>
</section>`;
}

/** 报告列表（14 条） */
export const mockReports: ReportItem[] = [
  {
    id: 'rpt_001',
    title: 'MetaTech 2026 Q2 渗透测试报告',
    type: 'penetration',
    status: 'completed',
    templateId: 'tpl_001',
    templateName: '标准渗透测试报告模板',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    fileIds: ['file_001'],
    fileNames: ['metatech_q2_pentest.pdf'],
    creator: '王浩然',
    generatedAt: '2026-07-15T18:00:00Z',
    fileSize: 2_400_000,
    format: 'pdf',
    downloadUrl: '/downloads/rpt_001.pdf',
    htmlContent: buildHtmlPreview('MetaTech 2026 Q2 渗透测试报告', 'penetration', 'MetaTech 集团'),
    summary: '本次作业共发现严重风险 3 项、高危 5 项，已取得 DB 与 SCADA 控制权限。',
    tags: ['MetaTech', 'Q2', '渗透'],
    createTime: '2026-07-14T10:00:00Z',
    updateTime: '2026-07-15T18:00:00Z',
  },
  {
    id: 'rpt_002',
    title: 'NovaCloud API 越权漏洞专项',
    type: 'vulnerability',
    status: 'completed',
    templateId: 'tpl_002',
    templateName: '漏洞分析专项报告',
    targetId: 'tgt_003',
    targetName: 'NovaCloud',
    creator: '陈思齐',
    generatedAt: '2026-07-20T16:00:00Z',
    fileSize: 1_200_000,
    format: 'pdf',
    downloadUrl: '/downloads/rpt_002.pdf',
    htmlContent: buildHtmlPreview('NovaCloud API 越权漏洞专项', 'vulnerability', 'NovaCloud'),
    summary: 'API 越权可获取全部租户数据，CVSS 9.1，需立即修复。',
    tags: ['NovaCloud', 'API', '越权'],
    createTime: '2026-07-19T10:00:00Z',
    updateTime: '2026-07-20T16:00:00Z',
  },
  {
    id: 'rpt_003',
    title: 'SkyNet OT 工控安全测试报告',
    type: 'penetration',
    status: 'completed',
    templateId: 'tpl_008',
    templateName: 'OT 工控安全专项',
    targetId: 'tgt_002',
    targetName: 'SkyNet 工控',
    creator: '孙磊',
    generatedAt: '2026-07-22T17:00:00Z',
    fileSize: 3_100_000,
    format: 'pdf',
    downloadUrl: '/downloads/rpt_003.pdf',
    htmlContent: buildHtmlPreview('SkyNet OT 工控安全测试报告', 'penetration', 'SkyNet 工控'),
    summary: 'Modbus 未授权 + MS17-010，可下发控制指令并实现持久化。',
    tags: ['SkyNet', 'OT', 'MS17-010'],
    createTime: '2026-07-21T10:00:00Z',
    updateTime: '2026-07-22T17:00:00Z',
  },
  {
    id: 'rpt_004',
    title: '2026 H1 威胁情报汇总',
    type: 'threat_intel',
    status: 'completed',
    templateId: 'tpl_003',
    templateName: '威胁情报汇总报告',
    creator: '林浩',
    generatedAt: '2026-07-10T15:00:00Z',
    fileSize: 1_800_000,
    format: 'html',
    downloadUrl: '/downloads/rpt_004.html',
    htmlContent: buildHtmlPreview('2026 H1 威胁情报汇总', 'threat_intel'),
    summary: 'H1 共订阅 12 个情报源，新增 IOC 1240 条，识别 6 个 APT 组织。',
    tags: ['H1', '威胁情报'],
    createTime: '2026-07-05T10:00:00Z',
    updateTime: '2026-07-10T15:00:00Z',
  },
  {
    id: 'rpt_005',
    title: 'MetaTech 攻击链路可视化',
    type: 'attack_chain',
    status: 'completed',
    templateId: 'tpl_004',
    templateName: '攻击链路可视化报告',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    creator: '王浩然',
    generatedAt: '2026-07-12T14:00:00Z',
    fileSize: 980_000,
    format: 'html',
    downloadUrl: '/downloads/rpt_005.html',
    htmlContent: buildHtmlPreview('MetaTech 攻击链路可视化', 'attack_chain', 'MetaTech 集团'),
    summary: '4 阶段 kill chain 全程时间线，含每个阶段的 IoC 与攻击工具。',
    tags: ['MetaTech', 'KillChain'],
    createTime: '2026-07-11T10:00:00Z',
    updateTime: '2026-07-12T14:00:00Z',
  },
  {
    id: 'rpt_006',
    title: 'MetaTech 资产测绘清单',
    type: 'asset',
    status: 'completed',
    templateId: 'tpl_005',
    templateName: '资产测绘清单',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    creator: '周宇翔',
    generatedAt: '2026-05-15T11:00:00Z',
    fileSize: 320_000,
    format: 'markdown',
    downloadUrl: '/downloads/rpt_006.md',
    summary: '资产清单：4 组织、6 资产、4 域名、4 IP。',
    tags: ['MetaTech', '资产'],
    createTime: '2026-05-14T10:00:00Z',
    updateTime: '2026-05-15T11:00:00Z',
  },
  {
    id: 'rpt_007',
    title: '2026 Q2 审计合规自查报告',
    type: 'audit',
    status: 'completed',
    templateId: 'tpl_006',
    templateName: '审计合规报告',
    creator: 'admin',
    generatedAt: '2026-07-01T10:00:00Z',
    fileSize: 540_000,
    format: 'pdf',
    downloadUrl: '/downloads/rpt_007.pdf',
    summary: 'Q2 合规自查 32 项，全部通过。',
    tags: ['Q2', '审计'],
    createTime: '2026-06-28T10:00:00Z',
    updateTime: '2026-07-01T10:00:00Z',
  },
  {
    id: 'rpt_008',
    title: 'MetaTech 2026 Q3 渗透报告（生成中）',
    type: 'penetration',
    status: 'generating',
    templateId: 'tpl_001',
    templateName: '标准渗透测试报告模板',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    creator: '王浩然',
    format: 'pdf',
    summary: '正在汇总 Q3 阶段成果...',
    tags: ['MetaTech', 'Q3'],
    createTime: '2026-07-25T10:00:00Z',
    updateTime: '2026-07-27T09:00:00Z',
  },
  {
    id: 'rpt_009',
    title: 'GovCity 政务云初步侦察报告',
    type: 'asset',
    status: 'draft',
    templateId: 'tpl_005',
    templateName: '资产测绘清单',
    targetId: 'tgt_004',
    targetName: 'GovCity 政务云',
    creator: '周宇翔',
    format: 'markdown',
    summary: '资产测绘 60% 完成，待补充子域枚举结果。',
    tags: ['GovCity', '政务'],
    createTime: '2026-07-26T10:00:00Z',
    updateTime: '2026-07-27T09:00:00Z',
  },
  {
    id: 'rpt_010',
    title: 'C2 隐蔽性测试报告（失败）',
    type: 'penetration',
    status: 'failed',
    templateId: 'tpl_001',
    templateName: '标准渗透测试报告模板',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    creator: '刘晓东',
    format: 'pdf',
    summary: 'EDR 多次告警，C2 隐蔽性测试未通过，需调整方案。',
    tags: ['MetaTech', 'C2'],
    createTime: '2026-07-22T14:00:00Z',
    updateTime: '2026-07-23T10:00:00Z',
  },
  {
    id: 'rpt_011',
    title: '2025 年度渗透报告（归档）',
    type: 'penetration',
    status: 'archived',
    templateId: 'tpl_001',
    templateName: '标准渗透测试报告模板',
    creator: '林浩',
    generatedAt: '2026-01-10T10:00:00Z',
    fileSize: 4_200_000,
    format: 'pdf',
    downloadUrl: '/downloads/rpt_011.pdf',
    summary: '2025 年度红队作业汇总，共 18 个项目。',
    tags: ['2025', '年度', '归档'],
    createTime: '2026-01-05T10:00:00Z',
    updateTime: '2026-01-10T10:00:00Z',
  },
  {
    id: 'rpt_012',
    title: '钓鱼演练结果报告',
    type: 'penetration',
    status: 'completed',
    templateId: 'tpl_007',
    templateName: '红队季度复盘报告',
    targetId: 'tgt_001',
    targetName: 'MetaTech 集团',
    creator: '赵敏',
    generatedAt: '2026-07-18T14:00:00Z',
    fileSize: 760_000,
    format: 'pdf',
    downloadUrl: '/downloads/rpt_012.pdf',
    htmlContent: buildHtmlPreview('钓鱼演练结果报告', 'penetration', 'MetaTech 集团'),
    summary: '本次演练共发送 200 封钓鱼邮件，点击率 23%，符合行业平均。',
    tags: ['钓鱼', '演练'],
    createTime: '2026-07-17T10:00:00Z',
    updateTime: '2026-07-18T14:00:00Z',
  },
  {
    id: 'rpt_013',
    title: 'CVE-2021-26855 漏洞专项',
    type: 'vulnerability',
    status: 'completed',
    templateId: 'tpl_002',
    templateName: '漏洞分析专项报告',
    creator: '陈思齐',
    generatedAt: '2026-06-29T15:00:00Z',
    fileSize: 950_000,
    format: 'pdf',
    downloadUrl: '/downloads/rpt_013.pdf',
    htmlContent: buildHtmlPreview('CVE-2021-26855 漏洞专项', 'vulnerability'),
    summary: 'Exchange ProxyLogon，CVSS 9.8，已实际利用获取 webshell。',
    tags: ['CVE-2021-26855', 'Exchange'],
    createTime: '2026-06-28T10:00:00Z',
    updateTime: '2026-06-29T15:00:00Z',
  },
  {
    id: 'rpt_014',
    title: '红队季度复盘报告 2026 Q3',
    type: 'penetration',
    status: 'generating',
    templateId: 'tpl_007',
    templateName: '红队季度复盘报告',
    creator: '王浩然',
    format: 'pdf',
    summary: 'Q3 项目复盘与 KPI 统计，生成中...',
    tags: ['Q3', '复盘'],
    createTime: '2026-07-26T18:00:00Z',
    updateTime: '2026-07-27T09:30:00Z',
  },
];

/** 按 ID 获取报告 */
export function getReportById(id: string): ReportItem | undefined {
  return mockReports.find((r) => r.id === id);
}

/** 按 ID 获取模板 */
export function getReportTemplateById(id: string): ReportTemplate | undefined {
  return mockReportTemplates.find((t) => t.id === id);
}

/** 按类型获取模板 */
export function getTemplatesByType(type: ReportType): ReportTemplate[] {
  return mockReportTemplates.filter((t) => t.type === type);
}

/* ===================== 定时报告 Mock 数据 ===================== */

/** 定时报告 Mock 列表 */
export const mockReportSchedules: ReportSchedule[] = [
  {
    id: 1,
    reportName: '每日目标画像报告',
    reportType: 'target-profile',
    cronExpression: '0 0 8 * * ?',
    recipients: 'admin@redteam.com,analyst@redteam.com',
    templateName: '目标画像模板',
    targetId: 1,
    status: 'ACTIVE',
    lastRunTime: '2026-08-16 08:00:00',
    lastRunStatus: 'SUCCESS',
    creator: 'admin',
    createTime: '2026-08-01 10:00:00',
    updateTime: '2026-08-16 08:00:05',
  },
  {
    id: 2,
    reportName: '每周渗透测试报告',
    reportType: 'penetration-test',
    cronExpression: '0 0 9 ? * MON',
    recipients: 'team@redteam.com',
    templateName: '标准渗透测试报告模板',
    targetId: 2,
    status: 'ACTIVE',
    lastRunTime: '2026-08-12 09:00:00',
    lastRunStatus: 'SUCCESS',
    creator: '王浩然',
    createTime: '2026-07-15 14:30:00',
    updateTime: '2026-08-12 09:00:10',
  },
  {
    id: 3,
    reportName: '每日漏洞扫描汇总',
    reportType: 'vulnerability-scan',
    cronExpression: '0 30 7 * * ?',
    recipients: 'vuln@redteam.com,ops@redteam.com',
    templateName: '漏洞分析专项报告',
    targetId: 3,
    status: 'INACTIVE',
    lastRunTime: '2026-08-10 07:30:00',
    lastRunStatus: 'FAILED',
    lastRunError: '数据库连接超时',
    creator: '陈思齐',
    createTime: '2026-06-20 09:00:00',
    updateTime: '2026-08-10 07:30:15',
  },
  {
    id: 4,
    reportName: '每月攻击链路复盘',
    reportType: 'attack-chain',
    cronExpression: '0 0 18 L * ?',
    recipients: 'commander@redteam.com,lead@redteam.com',
    templateName: '攻击链路可视化报告',
    status: 'ACTIVE',
    lastRunTime: '2026-07-31 18:00:00',
    lastRunStatus: 'SUCCESS',
    creator: '林浩',
    createTime: '2026-05-01 11:00:00',
    updateTime: '2026-07-31 18:00:30',
  },
  {
    id: 5,
    reportName: '每周任务汇总报告',
    reportType: 'task-summary',
    cronExpression: '0 0 17 ? * FRI',
    recipients: 'pm@redteam.com,team@redteam.com',
    templateName: '任务汇总模板',
    status: 'ACTIVE',
    lastRunTime: '2026-08-09 17:00:00',
    lastRunStatus: 'SUCCESS',
    creator: '周宇翔',
    createTime: '2026-06-01 16:00:00',
    updateTime: '2026-08-09 17:00:20',
  },
];

/** 按 ID 获取定时报告 */
export function getReportScheduleById(id: number | string): ReportSchedule | undefined {
  const numId = typeof id === 'string' ? Number(id) : id;
  return mockReportSchedules.find((s) => s.id === numId);
}

/** 定时报告执行历史 Mock（按 scheduleId 索引） */
export const mockReportScheduleHistory: Record<number, ReportScheduleHistory[]> = {
  1: [
    {
      id: 'h_1_1',
      scheduleId: 1,
      runTime: '2026-08-16 08:00:00',
      status: 'SUCCESS',
      costMs: 4250,
      reportId: 'rpt_sched_1_20260816',
      trigger: 'cron',
    },
    {
      id: 'h_1_2',
      scheduleId: 1,
      runTime: '2026-08-15 08:00:00',
      status: 'SUCCESS',
      costMs: 3980,
      reportId: 'rpt_sched_1_20260815',
      trigger: 'cron',
    },
    {
      id: 'h_1_3',
      scheduleId: 1,
      runTime: '2026-08-14 08:00:00',
      status: 'FAILED',
      costMs: 1200,
      errorMessage: '邮件服务连接超时',
      trigger: 'cron',
    },
  ],
  2: [
    {
      id: 'h_2_1',
      scheduleId: 2,
      runTime: '2026-08-12 09:00:00',
      status: 'SUCCESS',
      costMs: 8900,
      reportId: 'rpt_sched_2_20260812',
      trigger: 'cron',
    },
    {
      id: 'h_2_2',
      scheduleId: 2,
      runTime: '2026-08-05 09:00:00',
      status: 'SUCCESS',
      costMs: 7650,
      reportId: 'rpt_sched_2_20260805',
      trigger: 'cron',
    },
  ],
  3: [
    {
      id: 'h_3_1',
      scheduleId: 3,
      runTime: '2026-08-10 07:30:00',
      status: 'FAILED',
      costMs: 1100,
      errorMessage: '数据库连接超时',
      trigger: 'cron',
    },
  ],
  4: [
    {
      id: 'h_4_1',
      scheduleId: 4,
      runTime: '2026-07-31 18:00:00',
      status: 'SUCCESS',
      costMs: 12300,
      reportId: 'rpt_sched_4_20260731',
      trigger: 'cron',
    },
  ],
  5: [
    {
      id: 'h_5_1',
      scheduleId: 5,
      runTime: '2026-08-09 17:00:00',
      status: 'SUCCESS',
      costMs: 5400,
      reportId: 'rpt_sched_5_20260809',
      trigger: 'cron',
    },
  ],
};

/** 按 scheduleId 获取执行历史（无记录时返回空数组） */
export function getReportScheduleHistoryByScheduleId(
  scheduleId: number | string,
): ReportScheduleHistory[] {
  const numId = typeof scheduleId === 'string' ? Number(scheduleId) : scheduleId;
  return mockReportScheduleHistory[numId] ?? [];
}

/** 模拟分页查询定时报告 */
export function getMockReportSchedules(params: {
  page?: number;
  size?: number;
  keyword?: string;
  reportType?: ReportSchedule['reportType'];
  status?: ReportSchedule['status'];
}): { list: ReportSchedule[]; total: number } {
  let arr = [...mockReportSchedules];
  if (params?.keyword) {
    const kw = params.keyword.toLowerCase();
    arr = arr.filter(
      (s) =>
        s.reportName.toLowerCase().includes(kw) ||
        (s.recipients ?? '').toLowerCase().includes(kw),
    );
  }
  if (params?.reportType) arr = arr.filter((s) => s.reportType === params.reportType);
  if (params?.status) arr = arr.filter((s) => s.status === params.status);

  const total = arr.length;
  const page = params?.page ?? 1;
  const size = params?.size ?? 10;
  const start = (page - 1) * size;
  const list = arr.slice(start, start + size);
  return { list, total };
}

export default {
  mockReports,
  mockReportTemplates,
  getReportById,
  getReportTemplateById,
  getTemplatesByType,
  mockReportSchedules,
  getReportScheduleById,
  mockReportScheduleHistory,
  getReportScheduleHistoryByScheduleId,
  getMockReportSchedules,
};
