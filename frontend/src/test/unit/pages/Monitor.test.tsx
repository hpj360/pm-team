/**
 * 单元测试：监控看板页面 src/pages/Monitor/index.tsx
 * - 页面标题与全局筛选器渲染
 * - Tab 切换与各看板内容渲染（KPI 卡片 / 图表 / SLO 状态 / 漏斗）
 * - 时间范围切换交互
 * - 团队空间自动选择逻辑
 * - 团队空间列表为空时的提示态
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Monitor from '@/pages/Monitor';
import type { TeamSpace } from '@/types';

// 可变状态：控制 mockTeamSpaces（用于测试空列表场景）。
// 使用 vi.hoisted 保证在 vi.mock 工厂执行前已初始化。
const state = vi.hoisted(() => ({
  // 与 mock 模块共享的数组引用（in-place 修改以联动所有导入方）
  spaces: [] as TeamSpace[],
  backup: [] as TeamSpace[],
  ready: false,
}));

// Mock @/mock/monitor：保留真实函数实现，仅 mockTeamSpaces 可被外部覆盖。
// 函数内部仍引用原始 mockTeamSpaces，故 KPI/图表数据不受影响，
// 仅直接导入 mockTeamSpaces 的组件（Monitor 选择器、SpaceDetail）受控。
vi.mock('@/mock/monitor', async () => {
  const actual = await vi.importActual<typeof import('@/mock/monitor')>('@/mock/monitor');
  if (!state.ready) {
    state.backup.push(...actual.mockTeamSpaces);
    state.spaces.push(...actual.mockTeamSpaces);
    state.ready = true;
  }
  return {
    ...actual,
    mockTeamSpaces: state.spaces,
  };
});

// Mock echarts-for-react（覆盖 setup.ts 全局 mock，便于断言图表渲染）
vi.mock('echarts-for-react', () => ({
  default: () => <div data-testid="echarts-mock" />,
}));

const renderPage = () =>
  render(
    <MemoryRouter>
      <Monitor />
    </MemoryRouter>,
  );

describe('Monitor 监控看板页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 每个用例前恢复完整的团队空间列表
    state.spaces.length = 0;
    state.spaces.push(...state.backup);
  });

  it('页面正常渲染不崩溃', () => {
    renderPage();
    expect(document.querySelector('.ant-tabs')).toBeInTheDocument();
  });

  it('渲染页面标题"数据空间监控看板"', () => {
    renderPage();
    expect(screen.getByText('数据空间监控看板')).toBeInTheDocument();
  });

  it('渲染全局筛选标签', () => {
    renderPage();
    expect(screen.getByText('全局筛选')).toBeInTheDocument();
  });

  it('渲染时间范围 Segmented 选项', () => {
    renderPage();
    expect(screen.getByText('近1小时')).toBeInTheDocument();
    expect(screen.getByText('近6小时')).toBeInTheDocument();
    expect(screen.getByText('近24小时')).toBeInTheDocument();
    expect(screen.getByText('近7天')).toBeInTheDocument();
    expect(screen.getByText('近30天')).toBeInTheDocument();
  });

  it('渲染团队空间选择器占位符', () => {
    renderPage();
    expect(screen.getByText('选择团队空间')).toBeInTheDocument();
  });

  it('渲染全部 5 个 Tab 标签', () => {
    renderPage();
    // 使用 role=tab 定位，避免与 Overview 内同名 ChartCard 标题冲突
    expect(screen.getByRole('tab', { name: /业务总览/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /团队空间详情/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /业务链路漏斗/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /搜索体验/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /SLO 监控/ })).toBeInTheDocument();
  });

  it('默认渲染业务总览 KPI 指标卡片', () => {
    renderPage();
    expect(screen.getByText('上传文件数')).toBeInTheDocument();
    expect(screen.getByText('总存储量')).toBeInTheDocument();
    expect(screen.getByText('在线团队空间')).toBeInTheDocument();
    expect(screen.getByText('今日搜索数')).toBeInTheDocument();
  });

  it('业务总览渲染图表卡片区域', () => {
    renderPage();
    expect(screen.getByText('四阶段成功率趋势')).toBeInTheDocument();
    expect(screen.getByText('四阶段耗时 P95 趋势')).toBeInTheDocument();
    expect(screen.getByText('团队空间存储用量排行')).toBeInTheDocument();
    expect(screen.getByText('文件类型分布')).toBeInTheDocument();
    // echarts-for-react 被 mock 为带 testid 的 div，断言图表已挂载
    expect(screen.getAllByTestId('echarts-mock').length).toBeGreaterThan(0);
  });

  it('点击时间范围选项可切换选中状态', () => {
    renderPage();
    const option = screen.getByText('近1小时');
    fireEvent.click(option);
    // Segmented 选中项添加 ant-segmented-item-selected 类
    expect(option.closest('.ant-segmented-item')).toHaveClass('ant-segmented-item-selected');
  });

  it('切换到团队空间详情 Tab 自动选择首个空间并渲染详情', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: /团队空间详情/ }));
    // 未选空间时会显示 Alert；自动选择后应渲染详情卡片而非 Alert
    await waitFor(() => {
      expect(screen.getByText('文件数')).toBeInTheDocument();
      expect(screen.getByText('存储用量')).toBeInTheDocument();
      expect(screen.getByText('配额使用率仪表盘')).toBeInTheDocument();
    });
    expect(
      screen.queryByText('请先在顶部筛选器中选择一个具体的团队空间'),
    ).not.toBeInTheDocument();
  });

  it('切换到 SLO Tab 渲染服务健康状态指示器与明细表', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: /SLO 监控/ }));
    await waitFor(() => {
      expect(screen.getByText('SLO 状态明细')).toBeInTheDocument();
      // 每个 SLO 卡片均渲染"剩余错误预算"，存在多个
      expect(screen.getAllByText('剩余错误预算').length).toBeGreaterThan(0);
      expect(screen.getByText('SLO 燃烧率多窗口')).toBeInTheDocument();
    });
    // 至少渲染一个健康状态 Tag（达标 / 告警 / 违约）
    const statusTexts = ['达标', '告警', '违约'];
    const found = statusTexts.some((t) => screen.queryAllByText(t).length > 0);
    expect(found).toBe(true);
  });

  it('切换到业务链路漏斗 Tab 渲染漏斗图表与队列积压列表', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: /业务链路漏斗/ }));
    await waitFor(() => {
      expect(screen.getByText('上传 → 索引 → 解析 漏斗')).toBeInTheDocument();
      expect(screen.getByText('各阶段失败率对比')).toBeInTheDocument();
      expect(screen.getByText('解析队列积压 Top 空间')).toBeInTheDocument();
      expect(screen.getByText('链路追踪详情(最近事件)')).toBeInTheDocument();
    });
  });

  it('团队空间列表为空时切换详情 Tab 显示提示态', async () => {
    // 模拟无可用团队空间的异常态
    state.spaces.length = 0;
    renderPage();
    fireEvent.click(screen.getByRole('tab', { name: /团队空间详情/ }));
    await waitFor(() => {
      expect(
        screen.getByText('请先在顶部筛选器中选择一个具体的团队空间'),
      ).toBeInTheDocument();
    });
  });
});
