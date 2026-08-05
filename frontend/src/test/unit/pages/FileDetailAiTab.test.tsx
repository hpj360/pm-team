/**
 * 单元测试：文件详情页 AI 分析 Tab（src/pages/FileList/Detail/index.tsx）
 * 覆盖：
 * - AI 分析 Tab 标签渲染
 * - 切换到 AI Tab 时懒加载（调用 generateThreatSummary + inferAttackChain）
 * - 加载中显示 Skeleton
 * - 加载完成显示威胁摘要 / 关键发现 / 攻击链推理
 * - 错误降级：服务全部 reject 时显示 Result status="warning" + 重新分析按钮
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import FileDetailPage from '@/pages/FileList/Detail';
import { FileType, FileStatus } from '@/types';
import type { FileInfo, ThreatSummary, AiAttackChain } from '@/types';

// ===== Mock 服务 =====
const mockGenerateThreatSummary = vi.fn();
const mockInferAttackChain = vi.fn();
vi.mock('@/services/ai', () => ({
  generateThreatSummary: (...args: unknown[]) => mockGenerateThreatSummary(...args),
  inferAttackChain: (...args: unknown[]) => mockInferAttackChain(...args),
}));

// analyze 服务在页面顶部 import，需提供 mock 避免真实请求
vi.mock('@/services/analyze', () => ({
  scanFile: vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: {} }),
  getNerResult: vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: {} }),
}));

// ===== Mock 模块数据 =====
const mockFile: FileInfo = {
  id: 'f-test-001',
  name: 'storage_f_test_001',
  originalName: 'malware_sample_001.exe',
  size: 2048,
  type: FileType.OTHER,
  mimeType: 'application/octet-stream',
  status: FileStatus.COMPLETED,
  path: '/data/files/storage_f_test_001',
  hash: 'd41d8cd98f00b204e9800998ecf8427e',
  tags: ['恶意软件', 'APT'],
  uploaderId: 'u1',
  uploaderName: '张三',
  createTime: '2026-08-01T10:00:00Z',
  updateTime: '2026-08-02T10:00:00Z',
};

vi.mock('@/mock/file', () => ({
  getMockFileById: (id: string) => (id === mockFile.id ? mockFile : undefined),
}));

vi.mock('@/mock/yara', () => ({
  mockYaraRules: [],
}));

vi.mock('@/mock/threatIntel', () => ({
  mockThreatIntelItems: [],
}));

// ===== Mock 组件 =====
vi.mock('@/components/common/FileIcon', () => ({
  default: () => <span data-testid="file-icon" />,
}));

// ProDescriptions 在 meta tab 中使用，mock 为简单渲染避免复杂依赖
vi.mock('@ant-design/pro-components', () => ({
  ProDescriptions: ({ dataSource }: { dataSource?: Record<string, unknown> }) => (
    <div data-testid="pro-descriptions">{JSON.stringify(dataSource ?? {})}</div>
  ),
}));

vi.mock('@/utils/fileType', () => ({
  fileTypeLabel: { document: '文档', image: '图片', other: '其他' },
  fileTypeColor: { document: '#1890ff', image: '#52c41a', other: '#8c8c8c' },
}));

vi.mock('@/styles/tokens', () => ({
  colors: { info: '#1890ff', error: '#f5222d', warning: '#faad14' },
  spacing: { 4: 16 },
}));

const renderPage = (fileId: string = mockFile.id) =>
  render(
    <MemoryRouter initialEntries={[`/files/${fileId}`]}>
      <Routes>
        <Route path="/files/:id" element={<FileDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );

/** 构造可控的 mock 威胁摘要返回 */
function buildMockThreatSummary(fileId: string): ThreatSummary {
  return {
    fileId,
    summary: '该文件包含高危恶意代码，具备远程控制能力，建议立即隔离。',
    keyFindings: ['加壳 PE 文件', 'C2 通信特征', '注册表自启动持久化'],
    model: 'qwen2.5-14b-instruct',
    tokens: 1842,
    createdAt: '2026-08-05T10:00:00Z',
  };
}

/** 构造可控的 mock 攻击链返回 */
function buildMockAttackChain(fileId: string): AiAttackChain {
  return {
    attackPaths: [
      {
        name: '钓鱼邮件 → 宏代码执行 → C2 回连',
        description: '通过恶意宏文档建立 C2 通道',
        steps: ['投递钓鱼邮件', '宏代码执行', 'C2 心跳回连'],
      },
    ],
    confidence: 0.82,
    reasoning: `基于文件 ${fileId} 的综合推理，置信度 82%`,
  };
}

describe('文件详情页 AI 分析 Tab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染页面并展示 AI 分析 Tab 标签', async () => {
    renderPage();
    // 页面加载完成后，AI 分析 Tab 标签存在
    expect(await screen.findByRole('tab', { name: /AI 分析/ })).toBeInTheDocument();
  });

  it('切换到 AI Tab 时调用 generateThreatSummary + inferAttackChain', async () => {
    // 用 pending promise 锁定加载状态以便观察调用
    let resolveSummary!: (v: { code: number; message: string; data: ThreatSummary }) => void;
    let resolveChain!: (v: { code: number; message: string; data: AiAttackChain }) => void;
    mockGenerateThreatSummary.mockReturnValueOnce(
      new Promise((r) => {
        resolveSummary = r;
      }),
    );
    mockInferAttackChain.mockReturnValueOnce(
      new Promise((r) => {
        resolveChain = r;
      }),
    );

    renderPage();
    const aiTab = await screen.findByRole('tab', { name: /AI 分析/ });
    fireEvent.click(aiTab);

    await waitFor(() => {
      expect(mockGenerateThreatSummary).toHaveBeenCalledWith('f-test-001');
      expect(mockInferAttackChain).toHaveBeenCalledWith('f-test-001');
    });

    // 释放 promise 避免悬挂
    resolveSummary({ code: 200, message: 'ok', data: buildMockThreatSummary('f-test-001') });
    resolveChain({ code: 200, message: 'ok', data: buildMockAttackChain('f-test-001') });
  });

  it('加载中显示 Skeleton（ai-tab-loading）', async () => {
    // pending promise 使加载状态持续
    mockGenerateThreatSummary.mockReturnValueOnce(new Promise(() => {}));
    mockInferAttackChain.mockReturnValueOnce(new Promise(() => {}));

    renderPage();
    const aiTab = await screen.findByRole('tab', { name: /AI 分析/ });
    fireEvent.click(aiTab);

    await waitFor(() => {
      expect(screen.getByTestId('ai-tab-loading')).toBeInTheDocument();
    });
  });

  it('加载完成显示威胁摘要 / 关键发现 / 攻击链推理', async () => {
    const summary = buildMockThreatSummary('f-test-001');
    const chain = buildMockAttackChain('f-test-001');
    mockGenerateThreatSummary.mockResolvedValueOnce({ code: 200, message: 'ok', data: summary });
    mockInferAttackChain.mockResolvedValueOnce({ code: 200, message: 'ok', data: chain });

    renderPage();
    const aiTab = await screen.findByRole('tab', { name: /AI 分析/ });
    fireEvent.click(aiTab);

    // AI 内容容器出现
    await waitFor(() => {
      expect(screen.getByTestId('ai-tab-content')).toBeInTheDocument();
    });
    // 威胁摘要正文
    expect(screen.getByText(summary.summary)).toBeInTheDocument();
    // 关键发现项
    expect(screen.getByText('加壳 PE 文件')).toBeInTheDocument();
    // 攻击路径名称
    expect(screen.getByText('钓鱼邮件 → 宏代码执行 → C2 回连')).toBeInTheDocument();
    // 模型名称（Card 标题 Tag + Descriptions 详情项各出现一次）
    expect(screen.getAllByText('qwen2.5-14b-instruct').length).toBeGreaterThanOrEqual(1);
  });

  it('错误降级：服务全部 reject 时显示 Result warning 与重新分析按钮', async () => {
    mockGenerateThreatSummary.mockRejectedValueOnce(new Error('ai-service down'));
    mockInferAttackChain.mockRejectedValueOnce(new Error('ai-service down'));

    renderPage();
    const aiTab = await screen.findByRole('tab', { name: /AI 分析/ });
    fireEvent.click(aiTab);

    // 显示降级 Result
    await waitFor(() => {
      expect(screen.getByText('AI 分析结果加载失败')).toBeInTheDocument();
    });
    // 重新分析按钮存在
    expect(screen.getByRole('button', { name: /重新分析/ })).toBeInTheDocument();
  });

  it('一键重新生成：点击「重新生成 AI 分析」再次触发服务调用', async () => {
    const summary = buildMockThreatSummary('f-test-001');
    const chain = buildMockAttackChain('f-test-001');
    mockGenerateThreatSummary.mockResolvedValueOnce({ code: 200, message: 'ok', data: summary });
    mockInferAttackChain.mockResolvedValueOnce({ code: 200, message: 'ok', data: chain });

    renderPage();
    const aiTab = await screen.findByRole('tab', { name: /AI 分析/ });
    fireEvent.click(aiTab);

    // 等待首次加载完成
    await waitFor(() => {
      expect(screen.getByTestId('ai-tab-content')).toBeInTheDocument();
    });
    expect(mockGenerateThreatSummary).toHaveBeenCalledTimes(1);

    // 为重新生成准备新的 mock 返回
    mockGenerateThreatSummary.mockResolvedValueOnce({ code: 200, message: 'ok', data: summary });
    mockInferAttackChain.mockResolvedValueOnce({ code: 200, message: 'ok', data: chain });

    // 点击「重新生成 AI 分析」按钮
    const regenBtn = screen.getByRole('button', { name: /重新生成 AI 分析/ });
    fireEvent.click(regenBtn);

    await waitFor(() => {
      expect(mockGenerateThreatSummary).toHaveBeenCalledTimes(2);
      expect(mockInferAttackChain).toHaveBeenCalledTimes(2);
    });
  });
});
