/**
 * 单元测试：攻击链路页面 src/pages/redteam/AttackChain/index.tsx
 * - 渲染标题与选择器
 * - 加载攻击链列表后填充 Select
 * - 选中后渲染阶段与详情
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AttackChainPage from '@/pages/redteam/AttackChain';
import type { AttackChain } from '@/types';

// Mock services
const mockGetAttackChains = vi.fn();
const mockGetAttackChainDetail = vi.fn();
vi.mock('@/services', () => ({
  getAttackChains: (...args: unknown[]) => mockGetAttackChains(...args),
  getAttackChainDetail: (...args: unknown[]) => mockGetAttackChainDetail(...args),
}));

// Mock echarts-for-react
vi.mock('echarts-for-react', () => ({
  default: () => <div data-testid="echarts-mock" />,
}));

const buildChain = (id: string, name: string): AttackChain =>
  ({
    id,
    name,
    target: 'MetaTech',
    status: 'active',
    objective: '获取内网立足点',
    startTime: '2026-07-01T00:00:00Z',
    endTime: null,
    stages: [
      {
        phase: 1,
        name: '侦察',
        tactic: 'TA0043',
        technique: 'T1595',
        description: '主动扫描',
        status: 'completed',
        operator: 'alice',
        startTime: '2026-07-01T00:00:00Z',
        endTime: '2026-07-02T00:00:00Z',
      },
      {
        phase: 2,
        name: '初始访问',
        tactic: 'TA0001',
        technique: 'T1566',
        description: '鱼叉钓鱼',
        status: 'in-progress',
      },
    ],
    flow: [
      { from: '侦察', to: '初始访问', value: 1 },
    ],
  }) as unknown as AttackChain;

const renderPage = () =>
  render(
    <MemoryRouter>
      <AttackChainPage />
    </MemoryRouter>,
  );

describe('AttackChain 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染页面标题与攻击链选择器', async () => {
    mockGetAttackChains.mockResolvedValue({ code: 200, message: 'ok', data: [] });
    renderPage();
    expect(screen.getByText('攻击链路')).toBeInTheDocument();
    expect(screen.getByText('选择攻击链：')).toBeInTheDocument();
    expect(screen.getByText('请选择攻击链')).toBeInTheDocument();
  });

  it('加载攻击链列表后渲染选项并加载默认详情', async () => {
    mockGetAttackChains.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [buildChain('c1', 'RedStrike'), buildChain('c2', 'BlueDefend')],
    });
    mockGetAttackChainDetail.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildChain('c1', 'RedStrike'),
    });
    renderPage();

    await waitFor(() => {
      expect(mockGetAttackChainDetail).toHaveBeenCalledWith('c1');
    });
    await waitFor(() => {
      expect(screen.getByText('攻击链概览')).toBeInTheDocument();
      expect(screen.getByText('获取内网立足点')).toBeInTheDocument();
    });
    // 阶段卡片
    expect(screen.getByText('Kill Chain 攻击阶段')).toBeInTheDocument();
  });

  it('空列表显示 Empty 提示', async () => {
    mockGetAttackChains.mockResolvedValue({ code: 200, message: 'ok', data: [] });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('暂无数据')).toBeInTheDocument();
    });
  });
});
