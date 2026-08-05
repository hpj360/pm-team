/**
 * 单元测试：文件列表页面 src/pages/FileList/index.tsx
 * - 渲染页面标题与 ProTable
 * - 高级搜索面板展开/收起
 * - 上传文件按钮跳转
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import FileList from '@/pages/FileList';
import { getFileList } from '@/services';
import { FileType, FileStatus } from '@/types';

// Mock react-router-dom
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock useFile hook
vi.mock('@/hooks', () => ({
  useFile: () => ({
    deleteFile: vi.fn().mockResolvedValue(true),
    deleteFiles: vi.fn().mockResolvedValue(true),
  }),
}));

// Mock services
vi.mock('@/services', () => ({
  getFileList: vi.fn().mockResolvedValue({
    code: 200,
    message: 'ok',
    data: {
      list: [],
      total: 0,
      page: 1,
      pageSize: 20,
    },
  }),
  downloadFile: (id: string) => `/download/${id}`,
}));

// Mock FileIcon
vi.mock('@/components/common/FileIcon', () => ({
  default: () => <span data-testid="file-icon" />,
}));

// Mock FileDetailDrawer
vi.mock('@/pages/FileList/components/FileDetailDrawer', () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div data-testid="detail-drawer" /> : null,
}));

// Mock utils/fileType
vi.mock('@/utils/fileType', () => ({
  fileTypeLabel: { document: '文档', image: '图片' },
  fileTypeColor: { document: '#1890ff', image: '#52c41a' },
}));

// Mock FileList.module.less
vi.mock('@/pages/FileList/FileList.module.less', () => ({
  default: new Proxy({}, { get: () => 'cls' }),
}));

const renderPage = () =>
  render(
    <MemoryRouter>
      <FileList />
    </MemoryRouter>,
  );

describe('FileList 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染页面标题与工具栏按钮', async () => {
    renderPage();
    expect(screen.getByText('文件管理')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /高级搜索/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /上传文件/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /刷\s*新/ })).toBeInTheDocument();
  });

  it('点击高级搜索按钮展开/收起面板', async () => {
    renderPage();
    const advancedBtn = screen.getByRole('button', { name: /高级搜索/ });
    // 初始不显示高级搜索面板
    expect(screen.queryByPlaceholderText('文件名 / 标签 / 描述')).toBeNull();
    // 点击展开
    fireEvent.click(advancedBtn);
    await waitFor(() => {
      expect(screen.getByPlaceholderText('文件名 / 标签 / 描述')).toBeInTheDocument();
    });
  });

  it('点击上传文件按钮调用 navigate 跳转', async () => {
    renderPage();
    const uploadBtn = screen.getByRole('button', { name: /上传文件/ });
    fireEvent.click(uploadBtn);
    expect(mockNavigate).toHaveBeenCalledWith('/files/upload');
  });

  // ===== 文件标签列测试 =====

  it('test tag column renders：标签列渲染', async () => {
    vi.mocked(getFileList).mockResolvedValueOnce({
      code: 200,
      message: 'ok',
      data: {
        list: [
          {
            id: 'f0001',
            name: 'file_f0001',
            originalName: 'malware_sample.pdf',
            size: 1024,
            type: FileType.DOCUMENT,
            mimeType: 'application/pdf',
            status: FileStatus.COMPLETED,
            path: '/storage/files/f0001',
            hash: 'abc123',
            tags: ['恶意软件'],
            fileTags: [
              { fileId: 1, tagId: 1, tagCode: 'PDF', tagName: 'PDF文档', layer: 'L1', source: 'AUTO', createdAt: '2026-07-01T00:00:00.000Z' },
              { fileId: 1, tagId: 7, tagCode: 'IP', tagName: 'IP', layer: 'L3', source: 'AUTO', createdAt: '2026-07-01T00:00:00.000Z' },
            ],
            uploaderId: 'u1',
            uploaderName: '张三',
            createTime: '2026-07-01T00:00:00.000Z',
            updateTime: '2026-07-01T00:00:00.000Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 20,
      },
    });
    renderPage();
    // 等待表格渲染，验证文件标签列显示标签名称
    await waitFor(() => {
      expect(screen.getByText('PDF文档')).toBeInTheDocument();
      expect(screen.getByText('IP')).toBeInTheDocument();
    });
  });

  it('test tag column overflow：超过 3 个标签显示 "+N"', async () => {
    vi.mocked(getFileList).mockResolvedValueOnce({
      code: 200,
      message: 'ok',
      data: {
        list: [
          {
            id: 'f0002',
            name: 'file_f0002',
            originalName: 'attack_report.pdf',
            size: 2048,
            type: FileType.DOCUMENT,
            mimeType: 'application/pdf',
            status: FileStatus.COMPLETED,
            path: '/storage/files/f0002',
            hash: 'def456',
            tags: [],
            fileTags: [
              { fileId: 2, tagId: 1, tagCode: 'PDF', tagName: 'PDF文档', layer: 'L1', source: 'AUTO', createdAt: '2026-07-01T00:00:00.000Z' },
              { fileId: 2, tagId: 7, tagCode: 'IP', tagName: 'IP', layer: 'L3', source: 'AUTO', createdAt: '2026-07-01T00:00:00.000Z' },
              { fileId: 2, tagId: 11, tagCode: 'TARGET_PROFILE', tagName: '目标画像', layer: 'L4', source: 'MANUAL', createdAt: '2026-07-01T00:00:00.000Z' },
              { fileId: 2, tagId: 13, tagCode: 'APT28', tagName: 'APT28', layer: 'L5', source: 'MANUAL', createdAt: '2026-07-01T00:00:00.000Z' },
              { fileId: 2, tagId: 8, tagCode: 'IOC', tagName: 'IOC', layer: 'L3', source: 'AUTO', createdAt: '2026-07-01T00:00:00.000Z' },
            ],
            uploaderId: 'u1',
            uploaderName: '李四',
            createTime: '2026-07-01T00:00:00.000Z',
            updateTime: '2026-07-01T00:00:00.000Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 20,
      },
    });
    renderPage();
    // 5 个标签，前 3 个显示，后 2 个显示 "+2"
    await waitFor(() => {
      expect(screen.getByText('+2')).toBeInTheDocument();
    });
    // 前三个标签名称显示
    expect(screen.getByText('PDF文档')).toBeInTheDocument();
    expect(screen.getByText('IP')).toBeInTheDocument();
    expect(screen.getByText('目标画像')).toBeInTheDocument();
  });

  it('test tag column source style：AUTO 标签虚线边框 / MANUAL 实线边框', async () => {
    vi.mocked(getFileList).mockResolvedValueOnce({
      code: 200,
      message: 'ok',
      data: {
        list: [
          {
            id: 'f0003',
            name: 'file_f0003',
            originalName: 'sample.exe',
            size: 512,
            type: FileType.DOCUMENT,
            mimeType: 'application/octet-stream',
            status: FileStatus.COMPLETED,
            path: '/storage/files/f0003',
            hash: 'ghi789',
            tags: [],
            fileTags: [
              { fileId: 3, tagId: 2, tagCode: 'EXE', tagName: '可执行文件', layer: 'L1', source: 'AUTO', createdAt: '2026-07-01T00:00:00.000Z' },
              { fileId: 3, tagId: 18, tagCode: 'APT28', tagName: 'APT28', layer: 'L5', source: 'MANUAL', createdAt: '2026-07-01T00:00:00.000Z' },
            ],
            uploaderId: 'u1',
            uploaderName: '王五',
            createTime: '2026-07-01T00:00:00.000Z',
            updateTime: '2026-07-01T00:00:00.000Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 20,
      },
    });
    renderPage();
    // 等待标签渲染
    await waitFor(() => {
      expect(screen.getByText('可执行文件')).toBeInTheDocument();
      expect(screen.getByText('APT28')).toBeInTheDocument();
    });
    // 验证 AUTO 标签存在 dashed 边框样式，MANUAL 标签为实线
    const autoTag = screen.getByText('可执行文件').closest('.ant-tag') as HTMLElement | null;
    const manualTag = screen.getByText('APT28').closest('.ant-tag') as HTMLElement | null;
    expect(autoTag).not.toBeNull();
    expect(manualTag).not.toBeNull();
    // AUTO 标签 borderStyle 为 dashed
    expect(autoTag?.style.borderStyle).toBe('dashed');
    // MANUAL 标签 borderStyle 不是 dashed（默认实线）
    expect(manualTag?.style.borderStyle).not.toBe('dashed');
  });
});
