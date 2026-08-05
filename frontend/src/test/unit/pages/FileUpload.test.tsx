/**
 * 单元测试：文件上传页面 src/pages/FileUpload/index.tsx
 * - 渲染拖拽区与元数据表单
 * - 上传任务列表为空时显示 Empty
 * - 任务统计渲染
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import FileUpload from '@/pages/FileUpload';
import type { UploadTask } from '@/types';

// Mock useUpload hook
const mockUploadTasks: UploadTask[] = [];
vi.mock('@/hooks', () => ({
  useUpload: () => ({
    uploadTasks: mockUploadTasks,
    startUpload: vi.fn(),
    pauseTask: vi.fn(),
    resumeTask: vi.fn(),
    cancelTask: vi.fn(),
    removeTask: vi.fn(),
    clearCompleted: vi.fn(),
  }),
}));

// Mock mockTargetList
vi.mock('@/mock/file', () => ({
  mockTargetList: [
    { value: 't1', label: '目标 A' },
    { value: 't2', label: '目标 B' },
  ],
}));

// Mock FileIcon
vi.mock('@/components/common/FileIcon', () => ({
  default: () => <span data-testid="file-icon" />,
}));

// Mock utils/fileType
vi.mock('@/utils/fileType', () => ({
  detectFileTypeFromName: () => 'document',
  fileTypeLabel: { document: '文档' },
  fileTypeColor: { document: '#1890ff' },
}));

// Mock FileUpload.module.less
vi.mock('@/pages/FileUpload/FileUpload.module.less', () => ({
  default: new Proxy({}, { get: () => 'cls' }),
}));

const renderPage = () =>
  render(
    <MemoryRouter>
      <FileUpload />
    </MemoryRouter>,
  );

describe('FileUpload 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUploadTasks.length = 0;
  });

  it('渲染页面标题、拖拽区与元数据表单', () => {
    renderPage();
    expect(screen.getByText('文件上传')).toBeInTheDocument();
    expect(screen.getByText('支持分片上传 / 秒传 / 元数据标注')).toBeInTheDocument();
    expect(screen.getByText('点击或拖拽文件到此区域上传')).toBeInTheDocument();
    expect(screen.getByText('文件元数据')).toBeInTheDocument();
    expect(screen.getByText('敏感等级')).toBeInTheDocument();
    // 默认 L2 内部
    expect(screen.getByText(`L2 - 内部`)).toBeInTheDocument();
  });

  it('无上传任务时显示 Empty 提示', () => {
    renderPage();
    expect(screen.getByText('暂无上传任务，请选择文件后开始上传')).toBeInTheDocument();
  });

  it('存在上传任务时渲染任务列表与统计', () => {
    mockUploadTasks.push(
      {
        uid: 't1',
        file: new File(['x'], 'a.txt'),
        fileName: 'a.txt',
        fileSize: 1024,
        isMultipart: false,
        chunkSize: 5 * 1024 * 1024,
        chunkCount: 1,
        completedChunks: 0,
        percent: 100,
        status: 'completed',
        instantHit: false,
        metadata: {},
        partPercents: [],
      },
      {
        uid: 't2',
        file: new File(['y'], 'b.txt'),
        fileName: 'b.txt',
        fileSize: 1024,
        isMultipart: false,
        chunkSize: 5 * 1024 * 1024,
        chunkCount: 1,
        completedChunks: 0,
        percent: 0,
        status: 'failed',
        instantHit: false,
        metadata: {},
        partPercents: [],
      },
    );

    renderPage();
    expect(screen.getByText('a.txt')).toBeInTheDocument();
    expect(screen.getByText('b.txt')).toBeInTheDocument();
    // Statistic 标题与 Tag 文本可能重复，使用 getAllByText
    expect(screen.getAllByText('任务总数').length).toBeGreaterThan(0);
    expect(screen.getAllByText('已完成').length).toBeGreaterThan(0);
    expect(screen.getAllByText('失败').length).toBeGreaterThan(0);
  });
});
