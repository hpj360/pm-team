/**
 * 单元测试：标签管理页面 src/pages/admin/TagManage/index.tsx
 * - 渲染页面标题与工具栏
 * - 新增/编辑 Modal 打开与字段
 * - Switch 启停调用 API
 * - Popconfirm 删除调用 API
 * - 层级筛选切换列表
 * - 关键字搜索过滤
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import TagManagePage from '@/pages/admin/TagManage';
import type { TagDict } from '@/types';

// Mock services
const mockFetchTags = vi.fn();
const mockCreateTag = vi.fn();
const mockUpdateTag = vi.fn();
const mockToggleTag = vi.fn();
const mockDeleteTag = vi.fn();
vi.mock('@/services', () => ({
  fetchTags: (...args: unknown[]) => mockFetchTags(...args),
  createTag: (...args: unknown[]) => mockCreateTag(...args),
  updateTag: (...args: unknown[]) => mockUpdateTag(...args),
  toggleTag: (...args: unknown[]) => mockToggleTag(...args),
  deleteTag: (...args: unknown[]) => mockDeleteTag(...args),
}));

/** 构造一个标签 */
const buildTag = (id: number, overrides: Partial<TagDict> = {}): TagDict => ({
  id,
  tagCode: `L1.FILE.TYPE.${id}`,
  tagName: `标签${id}`,
  layer: 'L1',
  category: 'FILE',
  valueType: 'ENUM',
  applicableObject: 'FILE',
  identifyRule: 'ext==pdf',
  isMulti: 0,
  enabled: 1,
  description: '测试标签',
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
  ...overrides,
});

const sampleTags: TagDict[] = [
  buildTag(1, { tagCode: 'L1.FILE.TYPE.PDF', tagName: 'PDF文档', layer: 'L1' }),
  buildTag(2, { tagCode: 'L2.PROC.RECON', tagName: '侦察阶段', layer: 'L2' }),
  buildTag(3, { tagCode: 'L3.ENTITY.IP', tagName: 'IP地址', layer: 'L3' }),
];

const renderPage = () =>
  render(
    <MemoryRouter>
      <App>
        <TagManagePage />
      </App>
    </MemoryRouter>,
  );

describe('TagManage 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchTags.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: sampleTags,
    });
    mockCreateTag.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildTag(99, { tagCode: 'L1.FILE.TYPE.NEW' }),
    });
    mockUpdateTag.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: sampleTags[0],
    });
    mockToggleTag.mockResolvedValue({ code: 200, message: 'ok', data: undefined });
    mockDeleteTag.mockResolvedValue({ code: 200, message: 'ok', data: undefined });
  });

  it('渲染页面标题与工具栏按钮', async () => {
    renderPage();
    expect(screen.getByText('标签管理')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /刷\s*新/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /新增标签/ })).toBeInTheDocument();
  });

  it('点击新增标签按钮打开 Modal 并渲染表单字段', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /新增标签/ }));
    await waitFor(() => {
      // Modal 标题 + 按钮文本 -> 至少 2 个
      expect(screen.getAllByText('新增标签').length).toBeGreaterThanOrEqual(2);
    });
    // 表单字段标签
    expect(screen.getAllByText('标签编码').length).toBeGreaterThan(0);
    expect(screen.getAllByText('中文名').length).toBeGreaterThan(0);
    expect(screen.getAllByText('层级').length).toBeGreaterThan(0);
    expect(screen.getAllByText('分类').length).toBeGreaterThan(0);
    expect(screen.getAllByText('值类型').length).toBeGreaterThan(0);
    expect(screen.getAllByText('适用对象').length).toBeGreaterThan(0);
  });

  it('点击编辑按钮打开 Modal 并预填数据', async () => {
    renderPage();
    // 等待列表加载
    await waitFor(() => {
      expect(mockFetchTags).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByText('PDF文档')).toBeInTheDocument();
    });

    // 点击第一行的编辑按钮
    const editButtons = screen.getAllByRole('button', { name: /编\s*辑/ });
    expect(editButtons.length).toBeGreaterThan(0);
    fireEvent.click(editButtons[0]);

    await waitFor(() => {
      // Modal 标题文本"编辑标签"
      expect(screen.getAllByText('编辑标签').length).toBeGreaterThan(0);
    });

    // 标签编码字段值应已预填（Input 的 value）
    const codeInput = screen.getByDisplayValue('L1.FILE.TYPE.PDF') as HTMLInputElement;
    expect(codeInput).toBeInTheDocument();
    expect(codeInput.disabled).toBe(true); // 编辑时禁用 tagCode
  });

  it('点击 Switch 切换启用状态调用 toggleTag API', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('PDF文档')).toBeInTheDocument();
    });

    // 找到 Switch 按钮
    const switches = screen.getAllByRole('switch');
    expect(switches.length).toBeGreaterThan(0);
    fireEvent.click(switches[0]);

    await waitFor(() => {
      expect(mockToggleTag).toHaveBeenCalledTimes(1);
    });
  });

  it('点击删除按钮并确认后调用 deleteTag API', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('PDF文档')).toBeInTheDocument();
    });

    // 点击删除按钮
    const deleteButtons = screen.getAllByRole('button', { name: /删\s*除/ });
    expect(deleteButtons.length).toBeGreaterThan(0);
    fireEvent.click(deleteButtons[0]);

    // 出现 Popconfirm 确认框，点击确认
    await waitFor(() => {
      expect(screen.getByText('确认删除该标签？')).toBeInTheDocument();
    });
    const okBtn = screen.getByRole('button', { name: /^OK$|^确\s*定$/ }) as HTMLButtonElement;
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(mockDeleteTag).toHaveBeenCalledTimes(1);
    });
  });

  it('点击层级筛选 Radio 触发列表重新加载', async () => {
    renderPage();
    await waitFor(() => {
      expect(mockFetchTags).toHaveBeenCalledTimes(1);
    });

    // 点击 L2 层级
    const l2Radio = screen.getByRole('radio', { name: /L2 业务流程/ });
    fireEvent.click(l2Radio);

    await waitFor(() => {
      // 重新调用 fetchTags 时传入 layer=L2
      const lastCall = mockFetchTags.mock.calls[mockFetchTags.mock.calls.length - 1];
      expect(lastCall?.[0]).toMatchObject({ layer: 'L2' });
    });
  });

  it('搜索框输入关键字触发客户端过滤', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('PDF文档')).toBeInTheDocument();
    });

    // 找到搜索框（Input.Search placeholder）
    const searchInput = screen.getByPlaceholderText('按标签名 / 编码搜索') as HTMLInputElement;
    expect(searchInput).toBeInTheDocument();

    // 模拟输入并触发搜索（按下回车 -> onSearch）
    fireEvent.change(searchInput, { target: { value: 'PDF' } });
    fireEvent.keyDown(searchInput, { key: 'Enter', code: 'Enter', charCode: 13, keyCode: 13 });

    // 触发重新加载 -> 再次调用 fetchTags
    await waitFor(() => {
      expect(mockFetchTags.mock.calls.length).toBeGreaterThanOrEqual(2);
    });
  });
});
