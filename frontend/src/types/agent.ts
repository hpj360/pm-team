/**
 * AI Agent 化模块类型定义（V5.1）
 * 对应后端 ai-service AgentController 的 7 个端点：
 * - Agent 自主分析任务（提交 / 查询 / 列表 / 轨迹）
 * - 知识库管理（索引 / 检索 / 列表）
 */

/**
 * Agent 任务状态
 */
export type AgentTaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

/**
 * Agent 任务状态标签映射
 */
export const AgentTaskStatusLabel: Record<AgentTaskStatus, string> = {
  PENDING: '排队中',
  RUNNING: '执行中',
  COMPLETED: '已完成',
  FAILED: '失败',
};

/** Agent 任务状态颜色（antd Tag 内置色板） */
export const AgentTaskStatusColor: Record<AgentTaskStatus, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  COMPLETED: 'success',
  FAILED: 'error',
};

/**
 * Agent 推理轨迹单步记录（对应后端 AgentTrace）
 */
export interface AgentTrace {
  /** 步骤序号（从 1 开始） */
  step: number;
  /** 思考过程（Thought） */
  thought: string;
  /** 动作（工具名称或 FINAL_ANSWER） */
  action: string;
  /** 动作输入（工具参数 JSON 字符串） */
  actionInput: string;
  /** 观察结果（工具执行返回内容） */
  observation: string;
}

/**
 * Agent 自主分析任务（对应后端 AgentTaskEntity）
 */
export interface AgentTask {
  /** 任务ID（UUID） */
  taskId: string;
  /** 用户分析请求 */
  query: string;
  /** 提交用户ID */
  userId: number | null;
  /** 任务状态 */
  status: AgentTaskStatus;
  /** 最终结论 */
  conclusion: string | null;
  /** 证据链 JSON 数组 */
  evidenceChain: string[];
  /** 引用文件 JSON 数组 */
  referencedFiles: string[];
  /** 置信度（0.0~1.0） */
  confidence: number | null;
  /** 推理轨迹 JSON 数组 */
  traces: AgentTrace[];
  /** 错误信息 */
  errorMessage: string | null;
  /** 创建时间（ISO 字符串） */
  createdAt: string;
  /** 完成时间（ISO 字符串） */
  completedAt: string | null;
}

/**
 * 知识库文档（对应后端 KnowledgeEntity）
 */
export interface Knowledge {
  /** 知识ID */
  knowledgeId: string;
  /** 文档标题 */
  title: string | null;
  /** 文档内容 */
  content: string | null;
  /** 来源（ATT&CK / CVE / APT / REPORT） */
  source: string | null;
  /** 元数据 JSON */
  metadata: Record<string, unknown> | null;
  /** 创建时间（ISO 字符串） */
  createdAt: string;
}

/**
 * 知识库检索结果片段
 */
export interface KnowledgeSearchResult {
  /** 知识ID */
  knowledgeId: string;
  /** 标题 */
  title: string | null;
  /** 内容片段 */
  content: string;
  /** 来源 */
  source: string | null;
  /** 匹配分数 */
  score: number;
}

/**
 * 提交 Agent 分析任务请求
 */
export interface SubmitAnalysisPayload {
  /** 自然语言分析请求 */
  query: string;
  /** 用户ID */
  userId?: number;
}

/**
 * 索引知识库请求
 */
export interface IndexKnowledgePayload {
  /** 文档标题 */
  title: string;
  /** 文档内容 */
  content: string;
  /** 来源 */
  source?: string;
  /** 元数据 */
  metadata?: Record<string, unknown>;
}
