/**
 * API 服务入口文件
 * 导出所有 API 服务
 * 注意：parseFile / getIocList 在 file.ts / ioc.ts 与 analyze.ts 中均有定义，
 * 此处仅导出 file.ts 与 ioc.ts 的版本（功能更完整），analyze.ts 中的同名
 * 函数需通过 '@/services/analyze' 显式导入。
 */

export * from './auth';
export * from './file';
export * from './search';
export * from './tag';
export {
  createAnalyzeTask,
  getAnalyzeTasks,
  getAnalyzeTaskDetail,
  getAnalyzeResult,
  cancelAnalyzeTask,
  getAnalyzeStatistics,
  getAnalyzeTypes,
  exportAnalyzeReport,
  listYaraRules,
  scanFile,
  getNerResult,
} from './analyze';
export * from './ioc';
export * from './dashboard';
export * from './redteam';
export * from './admin';
export * from './tag';
// 文件评审服务（对应 workflow-service FileReviewService 端口 8094）
export * from './fileReview';
// 脱敏规则服务
export * from './dataMasking';
// 工作流设计器服务（对应 workflow-service WorkflowEngine 端口 8094）
export * from './workflow';
// V5.1 AI Agent 化模块服务（对应 ai-service AgentController 端口 8093）
export * from './agent';
// 应用运维服务请直接从 '@/services/ops' 导入，避免与 admin 模块的命名冲突 (getReports)

// V5.2 沙箱动态分析服务（对应 analyze-service DynamicAnalysisController 端口 8084）
export * from './dynamic';
// V5.3 威胁狩猎服务（对应 analyze-service HuntingController 端口 8084）
export * from './hunting';
