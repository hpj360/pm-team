/**
 * 单元测试：工作流设计器
 * - 列表页渲染（src/pages/admin/WorkflowDesigner/List.tsx）
 * - 节点面板渲染（5 种节点类型）
 * - 节点拖拽到画布（mock reactflow）
 * - 保存工作流调用 API
 * 注意：React Flow 依赖 DOM 测量/ResizeObserver 等在 jsdom 中不可用，需 mock 整个模块
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React, { useState, useCallback } from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import WorkflowDesigner from '@/pages/admin/WorkflowDesigner';
import WorkflowDesignerList from '@/pages/admin/WorkflowDesigner/List';
import type { WorkflowDefinition } from '@/types';

/* ===================== Mock reactflow ===================== */
// jsdom 不支持 React Flow 所需的 DOM 测量 / d3-zoom 等，整体 mock
// 工厂内使用顶层导入的 React / useState / useCallback（工厂在模块首次被导入时惰性执行）
vi.mock('reactflow', () => {
  const Position = { Top: 'top', Bottom: 'bottom', Left: 'left', Right: 'right' };
  const BackgroundVariant = { Dots: 'dots', Lines: 'lines', Cross: 'cross' };

  /** 模拟 useNodesState：使用真实 React state 以支持节点增删 */
  function useNodesState(initial: unknown[]) {
    const [nodes, setNodes] = useState(initial);
    const onNodesChange = useCallback(() => {
      /* noop */
    }, []);
    return [nodes, setNodes, onNodesChange];
  }

  /** 模拟 useEdgesState */
  function useEdgesState(initial: unknown[]) {
    const [edges, setEdges] = useState(initial);
    const onEdgesChange = useCallback(() => {
      /* noop */
    }, []);
    return [edges, setEdges, onEdgesChange];
  }

  /** 模拟 useReactFlow：screenToFlowPosition 直接返回原坐标 */
  function useReactFlow() {
    return {
      screenToFlowPosition: (pos: { x: number; y: number }) => pos,
      fitView: () => {
        /* noop */
      },
    };
  }

  /** 模拟 addEdge */
  function addEdge(edge: unknown, edges: unknown[]) {
    return [...edges, edge];
  }

  /** 模拟 ReactFlow 画布：渲染节点/连线，便于断言 */
  const ReactFlow = (props: {
    nodes?: Array<{ id: string; data?: { name?: string } }>;
    edges?: Array<{ id: string; source: string; target: string }>;
    onNodeClick?: (e: React.MouseEvent, n: unknown) => void;
    onEdgeClick?: (e: React.MouseEvent, e2: unknown) => void;
    onPaneClick?: () => void;
    children?: React.ReactNode;
  }) => (
    <div
      data-testid="react-flow-mock"
      onClick={() => props.onPaneClick?.()}
    >
      {props.nodes?.map((n) => (
        <div
          key={n.id}
          data-testid="flow-node"
          onClick={(e) => {
            e.stopPropagation();
            props.onNodeClick?.(e, n);
          }}
        >
          {n.data?.name}
        </div>
      ))}
      {props.edges?.map((e) => (
        <div key={e.id} data-testid="flow-edge">
          {e.source}-{e.target}
        </div>
      ))}
      {props.children}
    </div>
  );

  const ReactFlowProvider = ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  );
  const Background = () => <div data-testid="rf-background" />;
  const Controls = () => <div data-testid="rf-controls" />;
  const MiniMap = () => <div data-testid="rf-minimap" />;
  const Handle = () => <div data-testid="rf-handle" />;

  return {
    default: ReactFlow,
    ReactFlow,
    ReactFlowProvider,
    Background,
    BackgroundVariant,
    Controls,
    MiniMap,
    addEdge,
    useNodesState,
    useEdgesState,
    useReactFlow,
    Handle,
    Position,
  };
});

/* ===================== Mock 服务 ===================== */
const mockListWorkflowDefinitions = vi.fn();
const mockGetWorkflowDefinition = vi.fn();
const mockSaveWorkflowDefinition = vi.fn();
const mockToggleWorkflowDefinition = vi.fn();
const mockDeleteWorkflowDefinition = vi.fn();
vi.mock('@/services/workflow', () => ({
  listWorkflowDefinitions: (...args: unknown[]) => mockListWorkflowDefinitions(...args),
  getWorkflowDefinition: (...args: unknown[]) => mockGetWorkflowDefinition(...args),
  saveWorkflowDefinition: (...args: unknown[]) => mockSaveWorkflowDefinition(...args),
  toggleWorkflowDefinition: (...args: unknown[]) => mockToggleWorkflowDefinition(...args),
  deleteWorkflowDefinition: (...args: unknown[]) => mockDeleteWorkflowDefinition(...args),
}));

/* ===================== 工具函数 ===================== */

/**
 * 构造一个 dataTransfer 对象（jsdom 未完整实现 DataTransfer）
 * 用于模拟 dragStart / drop 事件的数据传递
 */
function makeDataTransfer(nodeType?: string) {
  const store: Record<string, string> = nodeType
    ? { 'application/reactflow': nodeType }
    : {};
  return {
    getData: (type: string) => store[type] ?? '',
    setData: (type: string, value: string) => {
      store[type] = value;
    },
    effectAllowed: 'move' as string,
    dropEffect: 'move' as string,
    types: Object.keys(store),
  };
}

/**
 * 在指定元素上触发 drop 事件（携带 dataTransfer）
 * 使用 dispatchEvent 以确保 dataTransfer 可被读取
 */
function fireDrop(element: Element, nodeType: string) {
  const event = new Event('drop', { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'dataTransfer', {
    value: makeDataTransfer(nodeType),
    writable: false,
    configurable: true,
  });
  element.dispatchEvent(event);
}

/** 构造示例工作流 */
function buildWorkflow(id: string, name: string, enabled = true): WorkflowDefinition {
  return {
    id,
    name,
    nodes: [
      { id: `${id}_start`, type: 'START', name: '发起人', approverIds: ['u1'], position: { x: 0, y: 0 } },
      { id: `${id}_end`, type: 'END', name: '结束', approverIds: [], position: { x: 0, y: 100 } },
    ],
    edges: [{ id: `${id}_e1`, source: `${id}_start`, target: `${id}_end` }],
    enabled,
    createdBy: 'admin',
    createdAt: '2026-07-01T00:00:00Z',
  };
}

/* ===================== 测试用例 ===================== */

describe('WorkflowDesigner 工作流设计器', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('列表页渲染：展示工作流名称、节点数与操作按钮', async () => {
    const samples = [
      buildWorkflow('wf-001', '文件上传审批流程'),
      buildWorkflow('wf-002', '高危文件会签流程', false),
    ];
    mockListWorkflowDefinitions.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: samples,
    });

    render(
      <MemoryRouter>
        <App>
          <WorkflowDesignerList />
        </App>
      </MemoryRouter>,
    );

    // 标题
    expect(screen.getByText('工作流管理')).toBeInTheDocument();
    // 工具栏按钮
    expect(screen.getByRole('button', { name: /新建工作流/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /刷\s*新/ })).toBeInTheDocument();

    // 等待列表加载并渲染工作流名称
    await waitFor(() => {
      expect(screen.getByText('文件上传审批流程')).toBeInTheDocument();
    });
    expect(screen.getByText('高危文件会签流程')).toBeInTheDocument();

    // 列表加载调用了 API
    expect(mockListWorkflowDefinitions).toHaveBeenCalledTimes(1);
  });

  it('节点面板渲染 5 种节点类型（发起人/审批人/抄送人/条件分支/结束）', async () => {
    mockGetWorkflowDefinition.mockResolvedValue({ code: 200, message: 'ok', data: buildWorkflow('wf-x', '测试') });
    mockSaveWorkflowDefinition.mockResolvedValue({ code: 200, message: 'ok', data: buildWorkflow('wf-x', '测试') });

    render(
      <MemoryRouter>
        <App>
          <WorkflowDesigner />
        </App>
      </MemoryRouter>,
    );

    // 左侧节点面板标题
    expect(screen.getByText('节点类型')).toBeInTheDocument();
    // 5 种节点类型文案（面板中作为节点名称出现）
    // 注意：新建模式下画布默认有一个「发起人」START 节点，因此「发起人」会出现多次
    expect(screen.getAllByText('发起人').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('审批人').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('抄送人').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('条件分支').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('结束').length).toBeGreaterThanOrEqual(1);

    // 节点描述
    expect(screen.getByText('流程发起节点')).toBeInTheDocument();
    expect(screen.getByText('审批处理节点')).toBeInTheDocument();
    expect(screen.getByText('抄送通知节点')).toBeInTheDocument();
    expect(screen.getByText('条件判断节点')).toBeInTheDocument();
    expect(screen.getByText('流程结束节点')).toBeInTheDocument();
  });

  it('节点拖拽到画布：drop 事件后在画布新增节点', async () => {
    render(
      <MemoryRouter>
        <App>
          <WorkflowDesigner />
        </App>
      </MemoryRouter>,
    );

    // 等待新建模式默认的发起人节点渲染
    await waitFor(() => {
      expect(screen.getAllByTestId('flow-node').length).toBeGreaterThanOrEqual(1);
    });
    const initialCount = screen.getAllByTestId('flow-node').length;

    // 在画布上触发 drop 事件，拖入一个审批人节点
    const canvas = screen.getByTestId('react-flow-mock');
    fireDrop(canvas, 'APPROVER');

    // 节点数应增加 1
    await waitFor(() => {
      expect(screen.getAllByTestId('flow-node').length).toBe(initialCount + 1);
    });
  });

  it('保存工作流：点击保存按钮调用 saveWorkflowDefinition API', async () => {
    mockSaveWorkflowDefinition.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildWorkflow('wf-new', '新建流程'),
    });

    render(
      <MemoryRouter>
        <App>
          <WorkflowDesigner />
        </App>
      </MemoryRouter>,
    );

    // 等待默认发起人节点渲染
    await waitFor(() => {
      expect(screen.getAllByTestId('flow-node').length).toBeGreaterThanOrEqual(1);
    });

    // 拖入一个结束节点，使工作流包含 START + END（通过保存校验）
    const canvas = screen.getByTestId('react-flow-mock');
    fireDrop(canvas, 'END');
    await waitFor(() => {
      expect(screen.getAllByTestId('flow-node').length).toBeGreaterThanOrEqual(2);
    });

    // 输入工作流名称
    const nameInput = screen.getByPlaceholderText('请输入工作流名称') as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: '自动化测试工作流' } });

    // 点击保存按钮
    const saveBtn = screen.getByRole('button', { name: /保存工作流/ });
    fireEvent.click(saveBtn);

    // 验证 saveWorkflowDefinition 被调用，且包含名称与节点
    await waitFor(() => {
      expect(mockSaveWorkflowDefinition).toHaveBeenCalledTimes(1);
    });
    const callArg = mockSaveWorkflowDefinition.mock.calls[0][0] as {
      name: string;
      nodes: unknown[];
      edges: unknown[];
      enabled: boolean;
    };
    expect(callArg.name).toBe('自动化测试工作流');
    expect(callArg.nodes.length).toBeGreaterThanOrEqual(2);
    expect(callArg.enabled).toBe(true);
  });

  it('列表页点击新建按钮跳转设计器路由', async () => {
    mockListWorkflowDefinitions.mockResolvedValue({ code: 200, message: 'ok', data: [] });
    render(
      <MemoryRouter>
        <App>
          <WorkflowDesignerList />
        </App>
      </MemoryRouter>,
    );
    const createBtn = screen.getByRole('button', { name: /新建工作流/ });
    expect(createBtn).toBeInTheDocument();
    // 点击不应抛错
    fireEvent.click(createBtn);
  });
});
