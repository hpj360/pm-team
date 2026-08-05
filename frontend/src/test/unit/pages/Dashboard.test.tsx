/**
 * 单元测试：工作台页面 src/pages/Dashboard/index.tsx
 * - 加载中状态
 * - 统计卡片与数据渲染
 * - 最近上传文件表格
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import Dashboard from '@/pages/Dashboard';
import { FileType, FileStatus } from '@/types';
import type { DashboardData } from '@/types';

// Mock services
const mockGetDashboardData = vi.fn();
vi.mock('@/services', () => ({
  getDashboardData: (...args: unknown[]) => mockGetDashboardData(...args),
}));

// Mock echarts-for-react（已在 setup.ts 中全局 mock，这里再确保 default 导出）
vi.mock('echarts-for-react', () => ({
  default: () => <div data-testid="echarts-mock" />,
}));

const buildDashboardData = (): DashboardData =>
  ({
    stats: {
      totalFiles: 128,
      totalSize: 1024 * 1024 * 512,
      parsedCount: 96,
      activeTasks: 7,
    },
    uploadTrend: [
      { date: '2026-07-22', count: 5 },
      { date: '2026-07-23', count: 8 },
      { date: '2026-07-24', count: 12 },
    ],
    typeDistribution: [
      { type: FileType.DOCUMENT, typeName: '文档', count: 60 },
      { type: FileType.IMAGE, typeName: '图片', count: 30 },
    ],
    taskProgress: [
      {
        id: 't1',
        name: '钓鱼演练',
        type: '红队任务',
        owner: 'alice',
        status: 'running',
        progress: 65,
        createTime: '2026-07-20T10:00:00Z',
      },
    ],
    systemStatus: {
      cpuUsage: 32,
      memoryUsage: 56,
      memoryUsed: 8 * 1024 * 1024 * 1024,
      memoryTotal: 16 * 1024 * 1024 * 1024,
      diskUsage: 70,
      diskUsed: 700 * 1024 * 1024 * 1024,
      diskTotal: 1024 * 1024 * 1024 * 1024,
    },
    recentFiles: [
      {
        id: 'f1',
        name: 'sample.pdf',
        size: 2048,
        type: FileType.DOCUMENT,
        uploader: 'admin',
        uploadTime: '2026-07-28T09:00:00Z',
        status: FileStatus.COMPLETED,
      },
    ],
  }) as unknown as DashboardData;

const renderDashboard = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

describe('Dashboard 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('加载中显示 Spin', () => {
    mockGetDashboardData.mockReturnValue(new Promise(() => {}));
    renderDashboard();
    // Ant Design 5.x Spin tip 在 jsdom 中可能不渲染文本，检查 Spin 容器
    expect(document.querySelector('.loading-container')).toBeInTheDocument();
    expect(document.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('加载完成后渲染统计卡片与最近文件', async () => {
    mockGetDashboardData.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildDashboardData(),
    });
    renderDashboard();

    await waitFor(() => {
      expect(screen.getByText('文件总数')).toBeInTheDocument();
    });
    expect(screen.getByText('128')).toBeInTheDocument();
    expect(screen.getByText('解析完成数')).toBeInTheDocument();
    expect(screen.getByText('sample.pdf')).toBeInTheDocument();
    expect(screen.getByText('红方任务进度')).toBeInTheDocument();
    expect(screen.getByText('钓鱼演练')).toBeInTheDocument();
  });

  it('调用 getDashboardData 一次以获取数据', async () => {
    mockGetDashboardData.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildDashboardData(),
    });
    renderDashboard();

    await waitFor(() => {
      expect(mockGetDashboardData).toHaveBeenCalledTimes(1);
    });
  });
});
