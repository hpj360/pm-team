/**
 * 单元测试：Agent 自主分析页面 src/pages/ai/AgentAnalysis/index.tsx
 * - 渲染标题与提交输入框
 * - 渲染任务列表表格
 * - 选中任务后渲染详情面板（结论、证据链、引用文件、置信度）
 * - 渲染推理轨迹 Timeline
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AgentAnalysis from '@/pages/ai/AgentAnalysis';
import type { AgentTask, AgentTrace } from '@/types';

// Mock services
const mockSubmitAnalysis = vi.fn();
const mockGetAgentTasks = vi.fn();
const mockGetAgentTask = vi.fn();
const mockGetAgentTraces = vi.fn();

vi.mock('@/services/agent', () => ({
  submitAnalysis: (...args: unknown[]) => mockSubmitAnalysis(...args),
  getAgentTasks: (...args: unknown[]) => mockGetAgentTasks(...args),
  getAgentTask: (...args: unknown[]) => mockGetAgentTask(...args),
  getAgentTraces: (...args: unknown[]) => mockGetAgentTraces(...args),
}));

// Mock antd message
vi.mock('antd', async () => {
  const actual = (await vi.importActual('antd')) as Record<string, unknown>;
  const actualMessage = actual.message as Record<string, unknown>;
  return {
    ...actual,
    message: {
      ...actualMessage,
      success: vi.fn(),
      warning: vi.fn(),
      error: vi.fn(),
    },
  };
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <AgentAnalysis />
    </MemoryRouter>,
  );

/** 构造 Mock 任务 */
function buildMockTask(overrides: Partial<AgentTask> = {}): AgentTask {
  return {
    taskId: 'task-001',
    query: '分析最近的钓鱼攻击',
    userId: 1001,
    status: 'COMPLETED',
    conclusion: '## 分析结论\n\n检测到 APT28 关联的钓鱼样本。',
    evidenceChain: ['步骤1 检索文件: 找到 3 份样本', '步骤2 查询情报: C2 关联 APT28'],
    referencedFiles: ['f-001', 'f-002', 'f-003'],
    confidence: 0.85,
    traces: [],
    errorMessage: null,
    createdAt: '2026-08-05T10:00:00Z',
    completedAt: '2026-08-05T10:05:00Z',
    ...overrides,
  };
}

/** 构造 Mock 轨迹 */
function buildMockTraces(): AgentTrace[] {
  return [
    {
      step: 1,
      thought: '先检索文件',
      action: 'search_files',
      actionInput: '{"query": "钓鱼"}',
      observation: '找到 3 份样本',
    },
    {
      step: 2,
      thought: '生成结论',
      action: 'FINAL_ANSWER',
      actionInput: '',
      observation: '检测到 APT28 关联样本',
    },
  ];
}

describe('AgentAnalysis 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 默认返回空列表
    mockGetAgentTasks.mockResolvedValue({ code: 200, data: [] });
    mockGetAgentTask.mockResolvedValue({ code: 200, data: null });
    mockGetAgentTraces.mockResolvedValue({ code: 200, data: [] });
  });

  it('渲染页面标题与提交输入框', async () => {
    renderPage();
    expect(screen.getByText('AI Agent 自主分析')).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/输入分析请求/),
    ).toBeInTheDocument();
  });

  it('渲染任务列表标题与刷新按钮', async () => {
    renderPage();
    expect(screen.getByText('分析任务')).toBeInTheDocument();
    expect(screen.getByText('刷新')).toBeInTheDocument();
  });

  it('有任务列表时渲染表格行', async () => {
    const mockTask = buildMockTask();
    mockGetAgentTasks.mockResolvedValue({ code: 200, data: [mockTask] });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('分析最近的钓鱼攻击')).toBeInTheDocument();
    });
  });

  it('点击查看详情应加载任务详情与轨迹', async () => {
    const mockTask = buildMockTask();
    mockGetAgentTasks.mockResolvedValue({ code: 200, data: [mockTask] });
    mockGetAgentTask.mockResolvedValue({ code: 200, data: mockTask });
    mockGetAgentTraces.mockResolvedValue({ code: 200, data: buildMockTraces() });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText('分析最近的钓鱼攻击')).toBeInTheDocument();
    });

    // 点击查看详情
    const detailBtn = screen.getByText('查看详情');
    fireEvent.click(detailBtn);

    await waitFor(() => {
      // 详情面板应渲染结论
      expect(screen.getByText('分析结论')).toBeInTheDocument();
    });
  });

  it('选中任务后渲染证据链与引用文件', async () => {
    const mockTask = buildMockTask();
    mockGetAgentTasks.mockResolvedValue({ code: 200, data: [mockTask] });
    mockGetAgentTask.mockResolvedValue({ code: 200, data: mockTask });
    mockGetAgentTraces.mockResolvedValue({ code: 200, data: [] });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText('分析最近的钓鱼攻击')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('查看详情'));

    await waitFor(() => {
      // 证据链
      expect(screen.getByText('证据链')).toBeInTheDocument();
      // 引用文件（Statistic title 与 Title 都是"引用文件"，用 getAllByText）
      expect(screen.getAllByText('引用文件').length).toBeGreaterThan(0);
      // 引用文件 Tag
      expect(screen.getByText('f-001')).toBeInTheDocument();
    });
  });

  it('切换到推理轨迹 Tab 应渲染 Timeline', async () => {
    const mockTask = buildMockTask();
    mockGetAgentTasks.mockResolvedValue({ code: 200, data: [mockTask] });
    mockGetAgentTask.mockResolvedValue({ code: 200, data: mockTask });
    mockGetAgentTraces.mockResolvedValue({ code: 200, data: buildMockTraces() });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText('分析最近的钓鱼攻击')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('查看详情'));

    await waitFor(() => {
      expect(screen.getByText('分析结论')).toBeInTheDocument();
    });

    // 切换到推理轨迹 Tab
    fireEvent.click(screen.getByText('推理轨迹'));

    await waitFor(() => {
      // Timeline 步骤
      expect(screen.getByText('步骤 1')).toBeInTheDocument();
      expect(screen.getByText('步骤 2')).toBeInTheDocument();
      // Thought 文本
      expect(screen.getByText('先检索文件')).toBeInTheDocument();
    });
  });

  it('未选中任务时显示 Empty 引导', async () => {
    mockGetAgentTasks.mockResolvedValue({ code: 200, data: [] });
    renderPage();
    await waitFor(() => {
      expect(
        screen.getByText(/请从左侧选择任务查看详情/),
      ).toBeInTheDocument();
    });
  });
});
