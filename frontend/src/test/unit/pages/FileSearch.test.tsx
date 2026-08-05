/**
 * 单元测试：文件检索页面 src/pages/FileSearch/index.tsx
 * - 渲染搜索模式与输入框
 * - 无结果时显示空状态
 * - 切换搜索模式
 * - 有结果时渲染结果列表、聚合面板、历史记录
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import FileSearch from '@/pages/FileSearch';
import { SearchType } from '@/types';
import type { BooleanCondition, SearchTemplate, SearchHistoryItem, TagDict } from '@/types';

// 可配置的 mock 状态
let mockState: {
  keyword: string;
  searchType: SearchType;
  results: unknown[];
  total: number;
  loading: boolean;
  cost: number;
  aggregations: unknown[];
  activeFilters: unknown[];
  params: { page: number; pageSize: number };
  history: unknown[];
  booleanConditions: BooleanCondition[];
  booleanMode: boolean;
  refineQuery: string;
  lastResultIds: string[];
  isRefining: boolean;
  searchTemplates: SearchTemplate[];
  searchHistory: SearchHistoryItem[];
  saveTemplateModalVisible: boolean;
  selectedTagIds: number[];
  availableTags: TagDict[];
} = {
  keyword: '',
  searchType: SearchType.KEYWORD,
  results: [],
  total: 0,
  loading: false,
  cost: 0,
  aggregations: [],
  activeFilters: [],
  params: { page: 1, pageSize: 10 },
  history: [],
  booleanConditions: [],
  booleanMode: false,
  refineQuery: '',
  lastResultIds: [],
  isRefining: false,
  searchTemplates: [],
  searchHistory: [],
  saveTemplateModalVisible: false,
  selectedTagIds: [],
  availableTags: [],
};

// Mock useSearch hook
const mockSearch = vi.fn();
const mockSetKeyword = vi.fn();
const mockChangeSearchType = vi.fn();
const mockClearFilters = vi.fn();
const mockClearHistory = vi.fn();
const mockRemoveHistory = vi.fn();
const mockToggleFilter = vi.fn();
const mockChangePage = vi.fn();
// 布尔检索 mock
const mockToggleBooleanMode = vi.fn(() => {
  mockState.booleanMode = !mockState.booleanMode;
});
const mockAddCondition = vi.fn((cond: BooleanCondition) => {
  mockState.booleanConditions = [...mockState.booleanConditions, cond];
});
const mockUpdateCondition = vi.fn();
const mockRemoveCondition = vi.fn((id: string) => {
  mockState.booleanConditions = mockState.booleanConditions.filter(
    (c) => c.id !== id,
  );
});
const mockClearConditions = vi.fn(() => {
  mockState.booleanConditions = [];
});
// 二次检索 mock
const mockExecuteRefine = vi.fn((query: string) => {
  mockState.isRefining = true;
  mockState.refineQuery = query;
});
const mockExitRefine = vi.fn(() => {
  mockState.isRefining = false;
  mockState.refineQuery = '';
});
const mockSetRefineQuery = vi.fn();
// 搜索模板与历史 mock
const mockLoadTemplates = vi.fn().mockResolvedValue(undefined);
const mockSaveTemplate = vi.fn().mockResolvedValue(undefined);
const mockApplyTemplate = vi.fn().mockResolvedValue(undefined);
const mockDeleteTemplate = vi.fn().mockResolvedValue(undefined);
const mockShowSaveModal = vi.fn(() => {
  mockState.saveTemplateModalVisible = true;
});
const mockHideSaveModal = vi.fn(() => {
  mockState.saveTemplateModalVisible = false;
});
const mockClearSearchHistory = vi.fn(() => {
  mockState.searchHistory = [];
});
// 标签筛选 mock
const mockToggleTagFilter = vi.fn((tagId: number) => {
  mockState.selectedTagIds = mockState.selectedTagIds.includes(tagId)
    ? mockState.selectedTagIds.filter((id) => id !== tagId)
    : [...mockState.selectedTagIds, tagId];
});
const mockSetSelectedTagIds = vi.fn((ids: number[]) => {
  mockState.selectedTagIds = ids;
});
const mockLoadAvailableTags = vi.fn().mockResolvedValue(undefined);

vi.mock('@/hooks', () => ({
  useSearch: () => ({
    ...mockState,
    search: mockSearch,
    toggleFilter: mockToggleFilter,
    clearFilters: mockClearFilters,
    changePage: mockChangePage,
    changeSearchType: mockChangeSearchType,
    setKeyword: mockSetKeyword,
    clearHistory: mockClearHistory,
    removeHistory: mockRemoveHistory,
    fetchSuggestions: vi.fn(),
    clearResults: vi.fn(),
    setFilters: vi.fn(),
    // 布尔检索
    booleanConditions: mockState.booleanConditions,
    booleanMode: mockState.booleanMode,
    addCondition: mockAddCondition,
    updateCondition: mockUpdateCondition,
    removeCondition: mockRemoveCondition,
    toggleBooleanMode: mockToggleBooleanMode,
    clearConditions: mockClearConditions,
    // 二次检索
    refineQuery: mockState.refineQuery,
    lastResultIds: mockState.lastResultIds,
    isRefining: mockState.isRefining,
    executeRefine: mockExecuteRefine,
    exitRefine: mockExitRefine,
    setRefineQuery: mockSetRefineQuery,
    // 搜索模板与历史
    searchTemplates: mockState.searchTemplates,
    searchHistory: mockState.searchHistory,
    saveTemplateModalVisible: mockState.saveTemplateModalVisible,
    loadTemplates: mockLoadTemplates,
    saveTemplate: mockSaveTemplate,
    applyTemplate: mockApplyTemplate,
    deleteTemplate: mockDeleteTemplate,
    showSaveModal: mockShowSaveModal,
    hideSaveModal: mockHideSaveModal,
    clearSearchHistory: mockClearSearchHistory,
    // 标签筛选
    selectedTagIds: mockState.selectedTagIds,
    availableTags: mockState.availableTags,
    setSelectedTagIds: mockSetSelectedTagIds,
    toggleTagFilter: mockToggleTagFilter,
    loadAvailableTags: mockLoadAvailableTags,
  }),
}));

// Mock services
vi.mock('@/services', () => ({
  downloadFile: (id: string) => `/download/${id}`,
  getFileDetail: vi.fn().mockResolvedValue({
    code: 200,
    data: { id: 'f1', name: 'test.txt' },
  }),
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
  fileTypeLabel: { document: '文档', image: '图片', other: '其他' },
  fileTypeColor: { document: '#1890ff', image: '#52c41a', other: '#8c8c8c' },
}));

// Mock mock/search
vi.mock('@/mock/search', () => ({
  getAggregationBucketLabel: (field: string, key: string) => `${field}:${key}`,
}));

// Mock FileSearch.module.less
vi.mock('@/pages/FileSearch/FileSearch.module.less', () => ({
  default: new Proxy({}, { get: () => 'cls' }),
}));

const renderPage = () =>
  render(
    <MemoryRouter>
      <FileSearch />
    </MemoryRouter>,
  );

describe('FileSearch 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockState = {
      keyword: '',
      searchType: SearchType.KEYWORD,
      results: [],
      total: 0,
      loading: false,
      cost: 0,
      aggregations: [],
      activeFilters: [],
      params: { page: 1, pageSize: 10 },
      history: [],
      booleanConditions: [],
      booleanMode: false,
      refineQuery: '',
      lastResultIds: [],
      isRefining: false,
      searchTemplates: [],
      searchHistory: [],
      saveTemplateModalVisible: false,
      selectedTagIds: [],
      availableTags: [],
    };
  });

  it('渲染页面标题、模式选择与关键词输入框', () => {
    renderPage();
    expect(screen.getByText('文件检索')).toBeInTheDocument();
    // 关键词模式 placeholder
    expect(screen.getByPlaceholderText(/输入关键词/)).toBeInTheDocument();
    // Segmented 模式标签
    expect(screen.getByText('关键词搜索')).toBeInTheDocument();
  });

  it('无关键词无结果时显示 Empty 引导', () => {
    renderPage();
    expect(screen.getByText('请输入关键词或选择搜索模式进行检索')).toBeInTheDocument();
  });

  it('点击重置调用 setKeyword 与 clearFilters', async () => {
    renderPage();
    const resetBtn = screen.getByRole('button', { name: /重\s*置/ });
    fireEvent.click(resetBtn);
    await waitFor(() => {
      expect(mockSetKeyword).toHaveBeenCalledWith('');
      expect(mockClearFilters).toHaveBeenCalled();
    });
  });

  it('有搜索结果时渲染结果卡片与聚合面板', () => {
    mockState = {
      ...mockState,
      keyword: '测试',
      results: [
        {
          id: 'r1',
          fileId: 'f1',
          fileName: '测试文档.txt',
          score: 0.95,
          matchedFields: ['name', 'content'],
          highlights: [
            { field: 'name', fragments: ['<em>测试</em>文档'] },
          ],
          tags: ['重要', '红方'],
          fileType: 'document',
          fileSize: 1024,
          sensitivity: 'L2',
          uploaderName: 'admin',
          targetName: 'MetaTech',
          createTime: '2026-07-28T00:00:00Z',
        },
      ],
      total: 1,
      cost: 35,
      aggregations: [
        {
          field: 'fileType',
          buckets: [
            { key: 'document', count: 5 },
            { key: 'image', count: 3 },
          ],
        },
      ],
    };
    renderPage();
    // 结果文件名
    expect(screen.getByText('测试文档.txt')).toBeInTheDocument();
    // 命中字段标签
    expect(screen.getByText('name')).toBeInTheDocument();
    expect(screen.getByText('content')).toBeInTheDocument();
    // 聚合面板标题
    expect(screen.getByText('聚合筛选')).toBeInTheDocument();
    // 聚合 bucket
    expect(screen.getByText('fileType:document')).toBeInTheDocument();
  });

  it('有搜索历史时渲染历史记录列表', () => {
    mockState = {
      ...mockState,
      history: [
        {
          id: 'h1',
          keyword: '渗透报告',
          type: SearchType.KEYWORD,
          total: 12,
          timestamp: '2026-07-28T10:00:00Z',
        },
      ],
    };
    renderPage();
    expect(screen.getByText('渗透报告')).toBeInTheDocument();
  });

  it('加载中显示 Skeleton', () => {
    mockState = {
      ...mockState,
      keyword: '测试',
      loading: true,
    };
    renderPage();
    // 加载中时显示 Skeleton（antd 渲染为 .ant-skeleton）
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument();
  });

  // ===== 布尔检索（AND / OR / NOT）测试 =====

  it('test boolean mode toggle：点击布尔检索开关后显示条件构建器', () => {
    mockState = { ...mockState, booleanMode: false };
    const { rerender } = renderPage();
    // 初始：布尔模式关闭，「添加条件」按钮不应出现
    expect(screen.queryByRole('button', { name: /添加条件/ })).not.toBeInTheDocument();
    // 点击布尔检索开关（排除 NL 模式开关）
    const switchBtn = screen.getByTestId('boolean-mode-switch');
    fireEvent.click(switchBtn);
    expect(mockToggleBooleanMode).toHaveBeenCalled();
    // 模拟 store 状态更新后重渲染
    rerender(
      <MemoryRouter>
        <FileSearch />
      </MemoryRouter>,
    );
    // 布尔模式开启后显示「添加条件」按钮
    expect(screen.getByRole('button', { name: /添加条件/ })).toBeInTheDocument();
  });

  it('test add boolean condition：点击「添加条件」按钮新增一行', () => {
    mockState = { ...mockState, booleanMode: true, booleanConditions: [] };
    const { rerender } = renderPage();
    // 初始无条件行（无「输入关键词」placeholder 输入框）
    expect(screen.queryByPlaceholderText('输入关键词')).not.toBeInTheDocument();
    // 点击「添加条件」
    fireEvent.click(screen.getByRole('button', { name: /添加条件/ }));
    expect(mockAddCondition).toHaveBeenCalled();
    // 模拟 store 新增条件后重渲染
    rerender(
      <MemoryRouter>
        <FileSearch />
      </MemoryRouter>,
    );
    // 新增一行后出现条件输入框
    expect(screen.getByPlaceholderText('输入关键词')).toBeInTheDocument();
  });

  it('test remove boolean condition：删除条件行', () => {
    mockState = {
      ...mockState,
      booleanMode: true,
      booleanConditions: [
        { id: 'c1', logic: 'AND', field: 'fileName', value: 'malware' },
        { id: 'c2', logic: 'OR', field: 'tags', value: '钓鱼' },
      ],
    };
    const { container, rerender } = renderPage();
    // 初始有 2 个条件输入框
    expect(screen.getAllByPlaceholderText('输入关键词').length).toBe(2);
    // 点击第二行的删除按钮（.anticon-delete 的最近 button 父节点）
    const deleteIcons = container.querySelectorAll('.anticon-delete');
    expect(deleteIcons.length).toBeGreaterThan(0);
    const deleteBtn = deleteIcons[deleteIcons.length - 1].closest('button')!;
    fireEvent.click(deleteBtn);
    expect(mockRemoveCondition).toHaveBeenCalledWith('c2');
    // 模拟 store 删除后重渲染
    rerender(
      <MemoryRouter>
        <FileSearch />
      </MemoryRouter>,
    );
    // 删除后只剩 1 个条件输入框
    expect(screen.getAllByPlaceholderText('输入关键词').length).toBe(1);
  });

  // ===== 二次检索测试 =====

  it('test refine search input visible：有结果时显示「在结果中搜索」输入框', () => {
    mockState = {
      ...mockState,
      keyword: '测试',
      total: 2,
      results: [
        {
          id: 'r1',
          fileId: 'f1',
          fileName: '测试文档.txt',
          score: 0.9,
          highlights: [],
          tags: [],
          fileType: 'document',
        },
        {
          id: 'r2',
          fileId: 'f2',
          fileName: '其他文档.txt',
          score: 0.8,
          highlights: [],
          tags: [],
          fileType: 'document',
        },
      ],
    };
    renderPage();
    // 有结果时显示二次检索输入框
    expect(
      screen.getByPlaceholderText('在结果中搜索（二次检索）'),
    ).toBeInTheDocument();
  });

  it('test refine search execution：输入二次检索词后结果被过滤', () => {
    const fullResults = [
      {
        id: 'r1',
        fileId: 'f1',
        fileName: '测试文档.txt',
        score: 0.9,
        highlights: [],
        tags: [],
        fileType: 'document',
      },
      {
        id: 'r2',
        fileId: 'f2',
        fileName: '其他报告.txt',
        score: 0.8,
        highlights: [],
        tags: [],
        fileType: 'document',
      },
    ];
    mockState = {
      ...mockState,
      keyword: '测试',
      total: 2,
      results: fullResults,
      lastResultIds: ['f1', 'f2'],
    };
    const { rerender } = renderPage();
    // 输入二次检索词并触发搜索
    const refineInput = screen.getByPlaceholderText('在结果中搜索（二次检索）');
    fireEvent.change(refineInput, { target: { value: '测试' } });
    fireEvent.click(screen.getByRole('button', { name: '在结果中搜索' }));
    expect(mockExecuteRefine).toHaveBeenCalledWith('测试');
    // 模拟二次检索后状态：进入 refining，结果收窄到 1 条
    mockState = {
      ...mockState,
      isRefining: true,
      refineQuery: '测试',
      total: 1,
      results: [fullResults[0]],
    };
    rerender(
      <MemoryRouter>
        <FileSearch />
      </MemoryRouter>,
    );
    // 显示二次检索状态 Tag
    expect(screen.getByText(/当前在 2 条结果中搜索/)).toBeInTheDocument();
    // 「其他报告.txt」被过滤掉
    expect(screen.queryByText('其他报告.txt')).not.toBeInTheDocument();
    // 「测试文档.txt」仍在
    expect(screen.getByText('测试文档.txt')).toBeInTheDocument();
  });

  it('test exit refine：退出二次检索后恢复完整结果', () => {
    const fullResults = [
      {
        id: 'r1',
        fileId: 'f1',
        fileName: '测试文档.txt',
        score: 0.9,
        highlights: [],
        tags: [],
        fileType: 'document',
      },
      {
        id: 'r2',
        fileId: 'f2',
        fileName: '其他报告.txt',
        score: 0.8,
        highlights: [],
        tags: [],
        fileType: 'document',
      },
    ];
    mockState = {
      ...mockState,
      keyword: '测试',
      total: 1,
      results: [fullResults[0]],
      lastResultIds: ['f1', 'f2'],
      isRefining: true,
      refineQuery: '测试',
    };
    const { rerender } = renderPage();
    // 二次检索模式下显示「退出二次检索」按钮
    const exitBtn = screen.getByRole('button', { name: /退出二次检索/ });
    fireEvent.click(exitBtn);
    expect(mockExitRefine).toHaveBeenCalled();
    // 模拟退出后状态：恢复完整结果
    mockState = {
      ...mockState,
      isRefining: false,
      refineQuery: '',
      total: 2,
      results: fullResults,
    };
    rerender(
      <MemoryRouter>
        <FileSearch />
      </MemoryRouter>,
    );
    // 退出后恢复二次检索输入框（不再显示状态 Tag）
    expect(
      screen.getByPlaceholderText('在结果中搜索（二次检索）'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/当前在.*条结果中搜索/)).not.toBeInTheDocument();
    // 完整结果恢复
    expect(screen.getByText('其他报告.txt')).toBeInTheDocument();
    expect(screen.getByText('测试文档.txt')).toBeInTheDocument();
  });

  // ===== 搜索模板与历史测试 =====

  it('test save search button visible：搜索按钮区域显示"保存搜索"按钮', () => {
    renderPage();
    expect(
      screen.getByRole('button', { name: /保\s*存搜\s*索/ }),
    ).toBeInTheDocument();
  });

  it('test save template modal：点击保存按钮弹出 Modal', async () => {
    const { rerender } = renderPage();
    // 点击「保存搜索」按钮
    fireEvent.click(screen.getByRole('button', { name: /保\s*存搜\s*索/ }));
    expect(mockShowSaveModal).toHaveBeenCalled();
    // 模拟 store 状态更新后重渲染：Modal 可见
    mockState = { ...mockState, saveTemplateModalVisible: true };
    rerender(
      <MemoryRouter>
        <FileSearch />
      </MemoryRouter>,
    );
    // Modal 标题与输入框出现
    expect(screen.getByText('保存搜索模板')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('请输入模板名称')).toBeInTheDocument();
  });

  it('test search template dropdown：模板下拉选择渲染', () => {
    mockState = {
      ...mockState,
      searchTemplates: [
        {
          id: 100,
          name: '测试模板A',
          paramsJson: JSON.stringify({ keyword: 'malware', searchMode: 'keyword' }),
          createdAt: '2026-07-28 10:00:00',
          updatedAt: '2026-07-28 10:00:00',
        },
      ],
    };
    renderPage();
    // 下拉占位符存在
    expect(screen.getByText('选择搜索模板')).toBeInTheDocument();
  });

  it('test search history display：有历史时显示 Tag 列表', () => {
    mockState = {
      ...mockState,
      searchHistory: [
        {
          id: '1',
          keyword: '渗透测试',
          searchMode: 'keyword',
          timestamp: Date.now(),
          displayName: '渗透测试',
        },
        {
          id: '2',
          keyword: 'APT28',
          searchMode: 'semantic',
          timestamp: Date.now(),
          displayName: 'APT28',
        },
      ],
    };
    renderPage();
    // 历史项 Tag 展示 displayName
    expect(screen.getByText('渗透测试')).toBeInTheDocument();
    expect(screen.getByText('APT28')).toBeInTheDocument();
    // 「清空」按钮存在
    expect(screen.getByRole('button', { name: /清\s*空/ })).toBeInTheDocument();
  });

  it('test clear search history：点击清空按钮清空历史', async () => {
    mockState = {
      ...mockState,
      searchHistory: [
        {
          id: '1',
          keyword: '渗透测试',
          searchMode: 'keyword',
          timestamp: Date.now(),
          displayName: '渗透测试',
        },
      ],
    };
    const { rerender } = renderPage();
    // 点击「清空」按钮（在搜索历史区域，使用 Popconfirm）
    fireEvent.click(screen.getByRole('button', { name: /清\s*空/ }));
    // Popconfirm 出现确认按钮
    const confirmBtn = await screen.findByRole('button', { name: /^确定$|^OK$/i });
    fireEvent.click(confirmBtn);
    expect(mockClearSearchHistory).toHaveBeenCalled();
    // 模拟清空后状态重渲染
    mockState = { ...mockState, searchHistory: [] };
    rerender(
      <MemoryRouter>
        <FileSearch />
      </MemoryRouter>,
    );
    // 显示空提示文本
    expect(screen.getByText('暂无搜索历史')).toBeInTheDocument();
  });

  it('test search history click：点击历史 Tag 恢复搜索', () => {
    mockState = {
      ...mockState,
      searchHistory: [
        {
          id: '1',
          keyword: '钓鱼邮件',
          searchMode: 'keyword',
          timestamp: Date.now(),
          displayName: '钓鱼邮件',
        },
      ],
    };
    renderPage();
    // 点击历史 Tag（displayName 文本）
    fireEvent.click(screen.getByText('钓鱼邮件'));
    expect(mockSetKeyword).toHaveBeenCalledWith('钓鱼邮件');
    expect(mockChangeSearchType).toHaveBeenCalled();
    expect(mockSearch).toHaveBeenCalled();
  });

  // ===== 标签 facet 测试 =====

  it('test tag facet renders：标签 facet 区域渲染', () => {
    mockState = {
      ...mockState,
      availableTags: [
        { id: 1, tagCode: 'PDF', tagName: 'PDF文档', layer: 'L1', category: '文件格式', valueType: 'ENUM', applicableObject: 'FILE', isMulti: 1, enabled: 1 },
        { id: 7, tagCode: 'IP', tagName: 'IP', layer: 'L3', category: '实体识别', valueType: 'TEXT', applicableObject: 'ENTITY', isMulti: 1, enabled: 1 },
        { id: 11, tagCode: 'TARGET_PROFILE', tagName: '目标画像', layer: 'L4', category: '业务场景', valueType: 'ENUM', applicableObject: 'TARGET', isMulti: 1, enabled: 1 },
      ],
    };
    renderPage();
    // 标签 facet section 存在
    expect(screen.getByTestId('tag-facet-section')).toBeInTheDocument();
    // 标签名称显示
    expect(screen.getByText('PDF文档')).toBeInTheDocument();
    expect(screen.getByText('IP')).toBeInTheDocument();
    expect(screen.getByText('目标画像')).toBeInTheDocument();
    // "文件标签" 标题显示
    expect(screen.getByText('文件标签')).toBeInTheDocument();
  });

  it('test tag facet filter：点击标签筛选搜索结果', () => {
    mockState = {
      ...mockState,
      availableTags: [
        { id: 1, tagCode: 'PDF', tagName: 'PDF文档', layer: 'L1', category: '文件格式', valueType: 'ENUM', applicableObject: 'FILE', isMulti: 1, enabled: 1 },
        { id: 7, tagCode: 'IP', tagName: 'IP', layer: 'L3', category: '实体识别', valueType: 'TEXT', applicableObject: 'ENTITY', isMulti: 1, enabled: 1 },
      ],
    };
    renderPage();
    // 点击 "PDF文档" 标签
    const pdfTag = screen.getByTestId('tag-facet-1');
    fireEvent.click(pdfTag);
    // 验证 toggleTagFilter 被调用，传入 tagId=1
    expect(mockToggleTagFilter).toHaveBeenCalledWith(1);
  });

  it('test tag facet multi-select：多标签 AND 筛选', () => {
    mockState = {
      ...mockState,
      selectedTagIds: [1, 7],
      availableTags: [
        { id: 1, tagCode: 'PDF', tagName: 'PDF文档', layer: 'L1', category: '文件格式', valueType: 'ENUM', applicableObject: 'FILE', isMulti: 1, enabled: 1 },
        { id: 7, tagCode: 'IP', tagName: 'IP', layer: 'L3', category: '实体识别', valueType: 'TEXT', applicableObject: 'ENTITY', isMulti: 1, enabled: 1 },
        { id: 11, tagCode: 'TARGET_PROFILE', tagName: '目标画像', layer: 'L4', category: '业务场景', valueType: 'ENUM', applicableObject: 'TARGET', isMulti: 1, enabled: 1 },
      ],
    };
    renderPage();
    // 选中的标签应该有 color 属性（通过 antd Tag 的 class 判断是否高亮）
    const pdfTag = screen.getByTestId('tag-facet-1');
    const ipTag = screen.getByTestId('tag-facet-7');
    const targetTag = screen.getByTestId('tag-facet-11');
    // 选中标签（id=1, id=7）opacity=1，未选中标签（id=11）opacity=0.55
    expect(pdfTag).toHaveStyle({ opacity: '1' });
    expect(ipTag).toHaveStyle({ opacity: '1' });
    expect(targetTag).toHaveStyle({ opacity: '0.55' });
    // 显示已选数量
    expect(screen.getByText('已选 2')).toBeInTheDocument();
  });
});
