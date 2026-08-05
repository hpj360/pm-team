/**
 * 单元测试：通知中心页面 src/pages/admin/NotificationCenter/index.tsx
 * - 渲染标题与统计卡片
 * - 通知列表加载后渲染
 * - 已读/未读筛选切换
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import NotificationCenterPage from '@/pages/admin/NotificationCenter';
import type { NotificationItem } from '@/types';

// Mock services
const mockGetNotifications = vi.fn();
const mockGetNotificationDetail = vi.fn();
const mockMarkNotificationRead = vi.fn();
const mockMarkAllNotificationsRead = vi.fn();
const mockDeleteNotification = vi.fn();
vi.mock('@/services', () => ({
  getNotifications: (...args: unknown[]) => mockGetNotifications(...args),
  getNotificationDetail: (...args: unknown[]) => mockGetNotificationDetail(...args),
  markNotificationRead: (...args: unknown[]) => mockMarkNotificationRead(...args),
  markAllNotificationsRead: (...args: unknown[]) => mockMarkAllNotificationsRead(...args),
  deleteNotification: (...args: unknown[]) => mockDeleteNotification(...args),
}));

const buildNotification = (
  id: string,
  title: string,
  read: boolean = false,
): NotificationItem =>
  ({
    id,
    title,
    content: '通知内容',
    type: 'system',
    priority: 'normal',
    read,
    sender: '系统',
    createTime: '2026-07-28T10:00:00Z',
    readTime: read ? '2026-07-28T11:00:00Z' : null,
    resourceType: '',
    resourceId: '',
    link: '',
  }) as unknown as NotificationItem;

const renderPage = () =>
  render(
    <MemoryRouter>
      <App>
        <NotificationCenterPage />
      </App>
    </MemoryRouter>,
  );

describe('NotificationCenter 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetNotifications.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [
        buildNotification('n1', '系统升级通知', false),
        buildNotification('n2', '任务完成通知', true),
      ],
    });
    mockGetNotificationDetail.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildNotification('n1', '系统升级通知', false),
    });
    mockMarkNotificationRead.mockResolvedValue({ code: 200, message: 'ok', data: undefined });
    mockMarkAllNotificationsRead.mockResolvedValue({ code: 200, message: 'ok', data: undefined });
    mockDeleteNotification.mockResolvedValue({ code: 200, message: 'ok', data: undefined });
  });

  it('渲染页面标题与统计卡片', async () => {
    renderPage();
    expect(screen.getByText('通知中心')).toBeInTheDocument();
    expect(screen.getByText('通知总数')).toBeInTheDocument();
    expect(screen.getByText('未读通知')).toBeInTheDocument();
    expect(screen.getByText('紧急未读')).toBeInTheDocument();
  });

  it('通知列表加载后渲染通知项', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('系统升级通知')).toBeInTheDocument();
      expect(screen.getByText('任务完成通知')).toBeInTheDocument();
    });
  });

  it('点击未读筛选仅显示未读通知', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('系统升级通知')).toBeInTheDocument();
    });

    // 切换到"未读"筛选（Segmented 选项，与列表中的"未读"Tag 文本重复）
    const unreadElements = screen.getAllByText('未读');
    // Segmented 标签在工具栏中，先于列表项渲染
    fireEvent.click(unreadElements[0]);

    await waitFor(() => {
      expect(screen.getByText('系统升级通知')).toBeInTheDocument();
      expect(screen.queryByText('任务完成通知')).not.toBeInTheDocument();
    });
  });

  it('点击全部已读按钮调用 markAllNotificationsRead', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('系统升级通知')).toBeInTheDocument();
    });

    // 按钮 aria-label="全部标记为已读" 覆盖了文本内容
    const markAllBtn = screen.getByRole('button', { name: /全部标记为已读/ });
    fireEvent.click(markAllBtn);

    await waitFor(() => {
      expect(mockMarkAllNotificationsRead).toHaveBeenCalled();
    });
  });
});
