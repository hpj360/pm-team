/**
 * 类型定义入口文件
 * 导出所有类型定义
 */

export * from './common';
export * from './file';
export * from './search';
export * from './analyze';
export * from './monitor';
export * from './ioc';
export * from './yara';
export * from './ner';
export * from './upload';
export * from './dashboard';
export * from './redteam';
export * from './admin';
// AI 分析模块类型（对应 ai-service 端口 8093）
// 注意：ai.ts 中的 AttackChain 与 redteam.ts 的 AttackChain 同名，
// 此处显式重命名为 AiAttackChain 以避免 re-export 冲突；
// 其余 AI 类型无冲突，直接 re-export。
export type {
  ThreatSummary,
  AttackPath,
  AttackChain as AiAttackChain,
  NlSearchResult,
  ReportDraft,
  GenerateReportDraftPayload,
} from './ai';
// V5.1 AI Agent 化模块类型（对应 ai-service AgentController）
export * from './agent';
// 标签字典类型请直接从 '@/types/tag' 导入，避免与 admin 模块潜在的命名冲突
export * from './tag';
// 文件评审模块类型（对应 workflow-service FileReviewService 端口 8094）
export * from './fileReview';
// 脱敏规则类型
export * from './dataMasking';
// 工作流模块类型请直接从 '@/types/workflow' 导入，避免与 admin 模块潜在的命名冲突
// (WorkflowNode / WorkflowEdge 可能与其他图谱模块同名)
export * from './workflow';
// 应用运维模块类型请直接从 '@/types/ops' 导入，避免与 admin/monitor 模块的命名冲突
// (ReportType, ReportTypeLabel, TeamSpace)
// V5.2 沙箱动态分析模块类型（对应 analyze-service DynamicAnalysisController）
export * from './dynamic';
// V5.3 威胁狩猎模块类型（对应 analyze-service HuntingController）
export * from './hunting';
