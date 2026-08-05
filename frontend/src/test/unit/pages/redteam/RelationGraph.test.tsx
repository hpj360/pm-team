/**
 * 单元测试：关系图谱页面 src/pages/redteam/RelationGraph/index.tsx
 * - 渲染标题与筛选面板
 * - 节点类型与关系类型 Checkbox
 * - 重置按钮可点击
 * - 数据源切换：Mock / Neo4j 实时
 * - Neo4j 失败时降级回 Mock 数据并显示 Toast
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from 'antd';
import RelationGraphPage from '@/pages/redteam/RelationGraph';
import type { RelationGraphData } from '@/types';

// Mock services
const mockGetRelationGraph = vi.fn();
const mockGetRelationGraphFromNeo4j = vi.fn();
const mockGetRelationGraphMockFallback = vi.fn();
vi.mock('@/services', () => ({
  getRelationGraph: (...args: unknown[]) => mockGetRelationGraph(...args),
  getRelationGraphFromNeo4j: (...args: unknown[]) => mockGetRelationGraphFromNeo4j(...args),
  getRelationGraphMockFallback: (...args: unknown[]) => mockGetRelationGraphMockFallback(...args),
}));

// Mock LazyECharts（含 React.lazy 加载）
vi.mock('@/components/common', () => ({
  LazyECharts: () => <div data-testid="echarts-mock" />,
}));

const buildGraphData = (): RelationGraphData =>
  ({
    nodes: [
      { id: 'n1', name: 'OrgA', type: 'organization', value: 60 },
      { id: 'n2', name: 'IP1', type: 'ip', value: 30 },
    ],
    edges: [
      { id: 'e1', source: 'n1', target: 'n2', relation: 'connect', weight: 2 },
    ],
    stats: { nodeCount: 2, edgeCount: 1, typeDistribution: { organization: 1, ip: 1 } },
  }) as unknown as RelationGraphData;

const buildNeo4jGraphData = (): RelationGraphData =>
  ({
    nodes: [
      { id: '1', name: 'Target A', type: 'organization', value: 60 },
      { id: '2', name: 'File B', type: 'asset', value: 30 },
    ],
    edges: [
      { id: 'neo4j_e_0', source: '1', target: '2', relation: 'own', weight: 2 },
    ],
    stats: { nodeCount: 2, edgeCount: 1, typeDistribution: { organization: 1, asset: 1 } },
  }) as unknown as RelationGraphData;

const renderPage = (initialPath = '/redteam/relation-graph') =>
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <App>
        <RelationGraphPage />
      </App>
    </MemoryRouter>,
  );

describe('RelationGraph 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetRelationGraph.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildGraphData(),
    });
    mockGetRelationGraphMockFallback.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildGraphData(),
    });
  });

  it('渲染标题、说明与筛选面板', async () => {
    renderPage();
    expect(screen.getByText('关系图谱')).toBeInTheDocument();
    expect(screen.getByText(/目标关系网络可视化/)).toBeInTheDocument();
    expect(screen.getByText('筛选')).toBeInTheDocument();
    expect(screen.getByText('节点类型')).toBeInTheDocument();
    expect(screen.getByText('关系类型')).toBeInTheDocument();
  });

  it('渲染数据源切换控件（默认 Mock）', async () => {
    renderPage();
    expect(screen.getByText('数据源：')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /Mock 数据/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /Neo4j 实时/ })).toBeInTheDocument();
  });

  it('加载完成后渲染统计信息', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('节点总数：')).toBeInTheDocument();
      expect(screen.getByText('关系总数：')).toBeInTheDocument();
    });
    // 节点数 2
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('点击重置按钮无报错', async () => {
    renderPage();
    const resetBtn = screen.getByRole('button', { name: /重\s*置/ });
    fireEvent.click(resetBtn);
    // 重置后应再次调用 getRelationGraph
    await waitFor(() => {
      expect(mockGetRelationGraph.mock.calls.length).toBeGreaterThanOrEqual(2);
    });
  });

  it('切换到 Neo4j 数据源时调用 getRelationGraphFromNeo4j', async () => {
    mockGetRelationGraphFromNeo4j.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildNeo4jGraphData(),
    });
    renderPage();
    // 等待初始 Mock 加载完成
    await waitFor(() => {
      expect(mockGetRelationGraph).toHaveBeenCalled();
    });

    // 切换到 Neo4j
    const neo4jRadio = screen.getByRole('radio', { name: /Neo4j 实时/ });
    fireEvent.click(neo4jRadio);

    await waitFor(() => {
      expect(mockGetRelationGraphFromNeo4j).toHaveBeenCalledWith(1, 3);
    });
  });

  it('Neo4j 接口失败时降级回 Mock 数据', async () => {
    mockGetRelationGraphFromNeo4j.mockRejectedValue(new Error('Network error'));
    renderPage();
    // 切换到 Neo4j
    const neo4jRadio = screen.getByRole('radio', { name: /Neo4j 实时/ });
    fireEvent.click(neo4jRadio);

    await waitFor(() => {
      expect(mockGetRelationGraphFromNeo4j).toHaveBeenCalled();
      expect(mockGetRelationGraphMockFallback).toHaveBeenCalled();
    });
  });

  it('从 URL params 读取 targetId', async () => {
    mockGetRelationGraphFromNeo4j.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildNeo4jGraphData(),
    });
    renderPage('/redteam/relation-graph?targetId=42');
    // 切换到 Neo4j
    const neo4jRadio = screen.getByRole('radio', { name: /Neo4j 实时/ });
    fireEvent.click(neo4jRadio);

    await waitFor(() => {
      expect(mockGetRelationGraphFromNeo4j).toHaveBeenCalledWith(42, 3);
    });
  });
});
