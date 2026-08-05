/**
 * 单元测试：威胁情报页面 src/pages/redteam/ThreatIntel/index.tsx
 * - 渲染标题与 Tabs
 * - IOC 列表加载后渲染
 * - 同步按钮触发 syncIntelFeed
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import ThreatIntelPage from '@/pages/redteam/ThreatIntel';
import type { ThreatIntelItem, ThreatActor, IntelFeed } from '@/types';

// Mock services
const mockGetThreatIntelList = vi.fn();
const mockGetThreatIntelDetail = vi.fn();
const mockGetThreatActors = vi.fn();
const mockGetIntelFeeds = vi.fn();
const mockSyncIntelFeed = vi.fn();
vi.mock('@/services', () => ({
  getThreatIntelList: (...args: unknown[]) => mockGetThreatIntelList(...args),
  getThreatIntelDetail: (...args: unknown[]) => mockGetThreatIntelDetail(...args),
  getThreatActors: (...args: unknown[]) => mockGetThreatActors(...args),
  getIntelFeeds: (...args: unknown[]) => mockGetIntelFeeds(...args),
  syncIntelFeed: (...args: unknown[]) => mockSyncIntelFeed(...args),
}));

const buildIntel = (): ThreatIntelItem[] => [
  {
    id: 'i1',
    type: 'ip',
    value: '1.2.3.4',
    confidence: 0.95,
    threatActors: ['APT28'],
    occurrences: 12,
    firstSeen: '2026-01-01T00:00:00Z',
    lastSeen: '2026-07-28T00:00:00Z',
    tags: ['C2'],
    sources: ['OSINT'],
    relatedCves: [],
    relatedFiles: [],
  } as unknown as ThreatIntelItem,
];

const buildActors = (): ThreatActor[] => [];
const buildFeeds = (): IntelFeed[] => [
  {
    id: 'f1',
    name: 'AlienVault OTX',
    type: 'stix',
    status: 'active',
    url: 'https://example.com',
    reliability: 'A',
    indicators: 1024,
    lastSync: '2026-07-28T00:00:00Z',
  } as unknown as IntelFeed,
];

const renderPage = () =>
  render(
    <MemoryRouter>
      <App>
        <ThreatIntelPage />
      </App>
    </MemoryRouter>,
  );

describe('ThreatIntel 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetThreatIntelList.mockResolvedValue({ code: 200, message: 'ok', data: buildIntel() });
    mockGetThreatActors.mockResolvedValue({ code: 200, message: 'ok', data: buildActors() });
    mockGetIntelFeeds.mockResolvedValue({ code: 200, message: 'ok', data: buildFeeds() });
    mockGetThreatIntelDetail.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildIntel()[0],
    });
    mockSyncIntelFeed.mockResolvedValue({ code: 200, message: 'ok', data: undefined });
  });

  it('渲染页面标题与 3 个 Tab', async () => {
    renderPage();
    expect(screen.getByText('威胁情报')).toBeInTheDocument();
    expect(screen.getByText(/IOC 列表/)).toBeInTheDocument();
    expect(screen.getByText(/威胁行为者/)).toBeInTheDocument();
    expect(screen.getByText(/情报订阅源/)).toBeInTheDocument();
  });

  it('IOC 列表加载后渲染数据行', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('1.2.3.4')).toBeInTheDocument();
    });
    // 类型 Tag IP
    expect(screen.getByText('IP')).toBeInTheDocument();
  });

  it('点击同步按钮触发 syncIntelFeed', async () => {
    renderPage();
    // 切到情报订阅源 Tab
    const feedsTab = screen.getByText(/情报订阅源/);
    fireEvent.click(feedsTab);

    await waitFor(() => {
      expect(screen.getByText('AlienVault OTX')).toBeInTheDocument();
    });
    const syncBtn = screen.getByRole('button', { name: /同\s*步/ });
    fireEvent.click(syncBtn);

    await waitFor(() => {
      expect(mockSyncIntelFeed).toHaveBeenCalledWith('f1');
    });
  });
});
