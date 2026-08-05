/**
 * 单元测试：目标画像页面 src/pages/redteam/TargetProfile/index.tsx
 * - 渲染标题与搜索框
 * - 加载目标列表后渲染
 * - 选中目标后加载详情
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import TargetProfilePage from '@/pages/redteam/TargetProfile';
import type { TargetProfile } from '@/types';

// Mock services
const mockGetTargetProfiles = vi.fn();
const mockGetTargetProfileDetail = vi.fn();
vi.mock('@/services', () => ({
  getTargetProfiles: (...args: unknown[]) => mockGetTargetProfiles(...args),
  getTargetProfileDetail: (...args: unknown[]) => mockGetTargetProfileDetail(...args),
}));

const buildProfile = (id: string, name: string): TargetProfile =>
  ({
    id,
    name,
    type: 'organization',
    industry: '金融',
    region: '北京',
    riskLevel: 'medium',
    tags: ['APT28', '金融'],
    description: '测试目标描述',
    organization: [],
    techAssets: [],
    attackSurfaces: [],
    timeline: [],
    updateTime: '2026-07-28T10:00:00Z',
    createTime: '2026-01-01T00:00:00Z',
  }) as unknown as TargetProfile;

const renderPage = () =>
  render(
    <MemoryRouter>
      <TargetProfilePage />
    </MemoryRouter>,
  );

describe('TargetProfile 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染页面标题、搜索框与目标列表卡片', async () => {
    mockGetTargetProfiles.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [],
    });
    renderPage();
    expect(screen.getByText('目标画像')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('搜索目标名称 / 行业 / 标签')).toBeInTheDocument();
    expect(screen.getByText('目标列表')).toBeInTheDocument();
  });

  it('加载目标列表后渲染列表项', async () => {
    mockGetTargetProfiles.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [buildProfile('t1', 'MetaTech'), buildProfile('t2', 'AcmeCorp')],
    });
    mockGetTargetProfileDetail.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildProfile('t1', 'MetaTech'),
    });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('MetaTech')).toBeInTheDocument();
      expect(screen.getByText('AcmeCorp')).toBeInTheDocument();
    });
    // 选中第一个目标时加载详情
    await waitFor(() => {
      expect(mockGetTargetProfileDetail).toHaveBeenCalledWith('t1');
    });
  });

  it('空列表显示 Empty 提示', async () => {
    mockGetTargetProfiles.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [],
    });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('未找到匹配目标')).toBeInTheDocument();
    });
  });
});
