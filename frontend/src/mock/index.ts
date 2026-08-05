/**
 * Mock 数据入口文件
 * 导出所有 Mock 数据
 */

export * from './file';
export * from './search';
export * from './tag';
export * from './analyze';
export * from './ioc';
export * from './dashboard';
export * from './yara';
export * from './ner';
export * from './monitor';
export * from './ai';
// V5.1 AI Agent 化模块 Mock 数据
export * from './agent';
// 文件评审 Mock 数据（评审实例 / 评审意见 / 审批进度时间轴）
export * from './fileReview';

// 红方模块
export * from './targetProfile';
export * from './threatIntel';
export * from './attackChain';
export * from './vulnerability';
export * from './arsenal';
export * from './collaboration';
export * from './relationGraph';
export * from './taskManage';

// 后台管理模块
export * from './adminUser';
export * from './adminRole';
export * from './adminPermission';
export * from './adminYara';
export * from './adminConfig';
export * from './adminAudit';
export * from './adminDataSource';
export * from './adminModel';
export * from './adminHealth';
export * from './adminReport';
export * from './adminNotification';
export * from './tag';
// 脱敏规则 Mock 数据
export * from './dataMasking';
// 工作流模块 Mock 数据
export * from './workflow';

// 应用运维模块（请直接从 '@/mock/ops' 导入，避免与 adminReport 模块的命名冲突 mockReports）

// V5.2 沙箱动态分析 Mock 数据（Cuckoo 沙箱任务/进程树/网络连接/ATT&CK 映射）
export * from './dynamic';
// V5.3 威胁狩猎 Mock 数据（ATT&CK 矩阵/狩猎假设/Sigma/YARA 规则）
export * from './hunting';
