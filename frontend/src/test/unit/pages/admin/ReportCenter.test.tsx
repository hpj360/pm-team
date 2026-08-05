/**
 * 单元测试：报告中心页面 src/pages/admin/ReportCenter/index.tsx
 * - 渲染标题与统计卡片
 * - 生成报告按钮打开模态框
 * - 报告列表加载后渲染
 * - 定时报告 Tab 切换 + 列表渲染
 * - 新建定时报告按钮打开模态框
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import ReportCenterPage from '@/pages/admin/ReportCenter';
import type { ReportItem, ReportTemplate, ReportSchedule } from '@/types';

// Mock services
const mockGetReports = vi.fn();
const mockGetReportDetail = vi.fn();
const mockGetReportTemplates = vi.fn();
const mockGenerateReport = vi.fn();
const mockExportReport = vi.fn();
const mockDeleteReport = vi.fn();
const mockArchiveReport = vi.fn();
const mockGetReportSchedules = vi.fn();
const mockCreateReportSchedule = vi.fn();
const mockToggleReportSchedule = vi.fn();
const mockDeleteReportSchedule = vi.fn();
const mockGetReportScheduleHistory = vi.fn();
vi.mock('@/services', () => ({
  getReports: (...args: unknown[]) => mockGetReports(...args),
  getReportDetail: (...args: unknown[]) => mockGetReportDetail(...args),
  getReportTemplates: (...args: unknown[]) => mockGetReportTemplates(...args),
  generateReport: (...args: unknown[]) => mockGenerateReport(...args),
  exportReport: (...args: unknown[]) => mockExportReport(...args),
  deleteReport: (...args: unknown[]) => mockDeleteReport(...args),
  archiveReport: (...args: unknown[]) => mockArchiveReport(...args),
  getReportSchedules: (...args: unknown[]) => mockGetReportSchedules(...args),
  createReportSchedule: (...args: unknown[]) => mockCreateReportSchedule(...args),
  toggleReportSchedule: (...args: unknown[]) => mockToggleReportSchedule(...args),
  deleteReportSchedule: (...args: unknown[]) => mockDeleteReportSchedule(...args),
  getReportScheduleHistory: (...args: unknown[]) => mockGetReportScheduleHistory(...args),
}));

const buildTemplate = (): ReportTemplate =>
  ({
    id: 'tpl1',
    name: '渗透测试模板',
    type: 'penetration',
    defaultFormat: 'pdf',
    description: '',
  }) as unknown as ReportTemplate;

const buildReport = (id: string, title: string): ReportItem =>
  ({
    id,
    title,
    type: 'penetration',
    status: 'completed',
    templateName: '渗透测试模板',
    targetName: 'MetaTech',
    format: 'pdf',
    creator: 'admin',
    generatedAt: '2026-07-28T00:00:00Z',
    fileSize: 1024 * 512,
    summary: '本次测试摘要',
    htmlContent: '<html></html>',
    fileNames: [],
    tags: [],
    createTime: '2026-07-28T00:00:00Z',
    updateTime: '2026-07-28T00:00:00Z',
  }) as unknown as ReportItem;

const buildSchedule = (id: number, reportName: string): ReportSchedule =>
  ({
    id,
    reportName,
    reportType: 'target-profile',
    cronExpression: '0 0 8 * * ?',
    recipients: 'admin@redteam.com',
    status: 'ACTIVE',
    lastRunTime: '2026-08-16 08:00:00',
    lastRunStatus: 'SUCCESS',
  }) as unknown as ReportSchedule;

const renderPage = () =>
  render(
    <MemoryRouter>
      <App>
        <ReportCenterPage />
      </App>
    </MemoryRouter>,
  );

describe('ReportCenter 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetReportTemplates.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [buildTemplate()],
    });
    mockGetReports.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [buildReport('r1', 'Q3 渗透报告')],
    });
    mockGetReportDetail.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildReport('r1', 'Q3 渗透报告'),
    });
    mockGetReportSchedules.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { list: [buildSchedule(1, '每日目标画像报告')], total: 1 },
    });
    mockCreateReportSchedule.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { id: 99 },
    });
    mockGetReportScheduleHistory.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [],
    });
  });

  it('渲染页面标题与统计卡片', async () => {
    renderPage();
    expect(screen.getByText('报告中心')).toBeInTheDocument();
    expect(screen.getByText('报告总数')).toBeInTheDocument();
    expect(screen.getByText('已完成')).toBeInTheDocument();
    expect(screen.getByText('生成中')).toBeInTheDocument();
    expect(screen.getByText('失败')).toBeInTheDocument();
  });

  it('渲染 Tabs 包含"报告列表"与"定时报告"', async () => {
    renderPage();
    expect(screen.getByRole('tab', { name: /报告列表/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /定时报告/ })).toBeInTheDocument();
  });

  it('点击生成报告按钮打开模态框', async () => {
    renderPage();
    // 按钮 aria-label="提交按钮"（getAriaLabel('button.submit')）覆盖了文本内容
    const generateBtn = screen.getByRole('button', { name: /提交按钮/ });
    fireEvent.click(generateBtn);

    // 模态框打开后"生成报告"文本存在多个（按钮文本 + 模态框标题）
    await waitFor(() => {
      expect(screen.getAllByText('生成报告').length).toBeGreaterThanOrEqual(2);
    });
    // 表单字段标签与 ProTable 列头可能重复，使用 getAllByText
    expect(screen.getAllByText('报告标题').length).toBeGreaterThan(0);
    expect(screen.getAllByText('报告模板').length).toBeGreaterThan(0);
    expect(screen.getAllByText('关联目标').length).toBeGreaterThan(0);
    expect(screen.getAllByText('导出格式').length).toBeGreaterThan(0);
  });

  it('报告列表加载后渲染报告标题', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('Q3 渗透报告')).toBeInTheDocument();
    });
  });

  it('切换到定时报告 Tab 后加载定时报告列表', async () => {
    renderPage();
    // 等待默认 Tab 加载完成
    await waitFor(() => {
      expect(mockGetReports).toHaveBeenCalled();
    });

    // 切换到定时报告 Tab
    const scheduleTab = screen.getByRole('tab', { name: /定时报告/ });
    fireEvent.click(scheduleTab);

    await waitFor(() => {
      expect(mockGetReportSchedules).toHaveBeenCalled();
    });
  });

  it('点击"新建定时报告"按钮打开模态框', async () => {
    renderPage();
    // 切换到定时报告 Tab
    const scheduleTab = screen.getByRole('tab', { name: /定时报告/ });
    fireEvent.click(scheduleTab);

    await waitFor(() => {
      expect(mockGetReportSchedules).toHaveBeenCalled();
    });

    // 点击"新建定时报告"按钮（aria-label="提交按钮"覆盖了文本，故通过文本查找 button）
    const createBtn = await screen.findByText('新建定时报告');
    fireEvent.click(createBtn.closest('button')!);

    await waitFor(() => {
      // 模态框标题与按钮文本重复，使用 getAllByText
      expect(screen.getAllByText('新建定时报告').length).toBeGreaterThanOrEqual(2);
      // 表单字段
      expect(screen.getAllByText('报告名称').length).toBeGreaterThan(0);
      expect(screen.getAllByText('报告类型').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Cron 表达式').length).toBeGreaterThan(0);
      expect(screen.getAllByText('收件人').length).toBeGreaterThan(0);
    });
  });
});
