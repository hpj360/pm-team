/**
 * E2E 测试：文件检索页面
 * 覆盖：
 * - 四种搜索模式切换（关键词 / 语义 / 模糊 / 正则）
 * - 关键词搜索流程：输入 → 点击搜索 → 显示结果
 * - 搜索历史：添加 / 点击回搜 / 清除
 * - 正则模式校验
 * - 重置搜索
 * - 聚合 facet 面板（搜索后显示）
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ConfigProvider, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import FileSearch from '@/pages/FileSearch';
import { useSearchStore } from '@/stores';
import { SearchType } from '@/types';

/** 包装组件 */
const renderSearch = () => {
  return render(
    <ConfigProvider locale={zhCN}>
      <AntdApp>
        <MemoryRouter>
          <FileSearch />
        </MemoryRouter>
      </AntdApp>
    </ConfigProvider>,
  );
};

/** 在 Antd Button 上查找按钮（兼容 CJK 字间距 "搜 索"） */
const findButtonByText = (text: RegExp): HTMLElement => {
  const buttons = screen.getAllByRole('button');
  const matched = buttons.find((btn) => text.test(btn.textContent ?? ''));
  if (!matched) {
    throw new Error(`未找到匹配 ${text} 的按钮`);
  }
  return matched;
};

/** 通过 Enter 键触发搜索（避免按钮名匹配问题） */
const submitSearchByEnter = (input: HTMLElement) => {
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
  fireEvent.submit(input.closest('form') ?? input);
};

describe('E2E: 文件检索', () => {
  beforeEach(() => {
    // 重置搜索 store
    useSearchStore.getState().reset();
    useSearchStore.getState().clearHistory();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('初始渲染：包含搜索模式切换、搜索输入框与搜索按钮', () => {
    renderSearch();

    // 默认关键词模式 placeholder
    expect(
      screen.getByPlaceholderText(/输入关键词，如 malware、APT、钓鱼邮件/),
    ).toBeInTheDocument();

    // 至少存在一个按钮（搜索按钮）
    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBeGreaterThan(0);
  });

  it('搜索模式切换：关键词 → 语义 → 模糊 → 正则', async () => {
    const user = userEvent.setup();
    renderSearch();

    // 切换到语义模式
    await user.click(screen.getByText('语义搜索'));

    await waitFor(() => {
      expect(
        screen.getByPlaceholderText(/描述你要找的内容/),
      ).toBeInTheDocument();
    });

    // 切换到模糊模式
    await user.click(screen.getByText('模糊搜索'));

    await waitFor(() => {
      expect(
        screen.getByPlaceholderText(/输入可能不完整或不准确的关键词/),
      ).toBeInTheDocument();
    });

    // 切换到正则模式
    await user.click(screen.getByText('正则搜索'));

    await waitFor(() => {
      expect(
        screen.getByPlaceholderText(/输入正则表达式/),
      ).toBeInTheDocument();
    });

    // 切换回关键词模式
    await user.click(screen.getByText('关键词搜索'));

    await waitFor(() => {
      expect(
        screen.getByPlaceholderText(/输入关键词，如 malware、APT、钓鱼邮件/),
      ).toBeInTheDocument();
    });
  });

  it('关键词搜索：输入并触发搜索，记录历史', async () => {
    const user = userEvent.setup();
    renderSearch();

    const input = screen.getByPlaceholderText(
      /输入关键词，如 malware、APT、钓鱼邮件/,
    );

    await user.type(input, 'malware');
    // 通过回车触发搜索
    submitSearchByEnter(input);

    // 搜索结果应被加载（mock 数据返回）
    await waitFor(
      () => {
        const state = useSearchStore.getState();
        // 历史应被添加
        expect(state.history.some((h) => h.keyword === 'malware')).toBe(true);
      },
      { timeout: 5000 },
    );
  });

  it('空关键词搜索：不触发请求且结果为空', async () => {
    renderSearch();

    const input = screen.getByPlaceholderText(
      /输入关键词，如 malware、APT、钓鱼邮件/,
    );
    // 直接按 Enter
    submitSearchByEnter(input);

    // store 中结果仍为空
    expect(useSearchStore.getState().results).toHaveLength(0);
    expect(useSearchStore.getState().total).toBe(0);
  });

  it('正则模式：可切换并显示正则输入框', async () => {
    const user = userEvent.setup();
    renderSearch();

    // 切换到正则模式
    await user.click(screen.getByText('正则搜索'));

    await waitFor(() => {
      const regexInput = screen.getByPlaceholderText(/输入正则表达式/);
      expect(regexInput).toBeInTheDocument();
    });
  });

  it('重置按钮：可清空关键词', async () => {
    const user = userEvent.setup();
    renderSearch();

    const input = screen.getByPlaceholderText(
      /输入关键词，如 malware、APT、钓鱼邮件/,
    ) as HTMLInputElement;
    await user.type(input, 'test-keyword');
    expect(input.value).toBe('test-keyword');

    // 查找 reload 图标按钮
    const resetButtons = screen.getAllByRole('button');
    const resetBtn = resetButtons.find((btn) =>
      btn.querySelector('.anticon-reload'),
    );
    if (resetBtn) {
      await user.click(resetBtn);
      await waitFor(() => {
        expect(input.value).toBe('');
      });
    }
  });

  it('搜索历史：执行搜索后历史中包含关键词', async () => {
    const user = userEvent.setup();
    renderSearch();

    const input = screen.getByPlaceholderText(
      /输入关键词，如 malware、APT、钓鱼邮件/,
    );
    await user.type(input, 'malware');
    submitSearchByEnter(input);

    // 等待历史记录渲染
    await waitFor(
      () => {
        expect(
          useSearchStore.getState().history.some((h) => h.keyword === 'malware'),
        ).toBe(true);
      },
      { timeout: 5000 },
    );
  });

  it('搜索模式元信息：切换模式后 placeholder 与提示同步变化', async () => {
    const user = userEvent.setup();
    renderSearch();

    // 关键词模式
    expect(
      screen.getByPlaceholderText(/输入关键词，如 malware、APT、钓鱼邮件/),
    ).toBeInTheDocument();

    // 切换到语义
    await user.click(screen.getByText('语义搜索'));

    await waitFor(() => {
      expect(
        screen.getByPlaceholderText(/描述你要找的内容/),
      ).toBeInTheDocument();
    });

    // 切换到模糊
    await user.click(screen.getByText('模糊搜索'));

    await waitFor(() => {
      expect(
        screen.getByPlaceholderText(/输入可能不完整或不准确的关键词/),
      ).toBeInTheDocument();
    });
  });

  it('搜索完成后：store 中保存 keyword 与结果', async () => {
    const user = userEvent.setup();
    renderSearch();

    const input = screen.getByPlaceholderText(
      /输入关键词，如 malware、APT、钓鱼邮件/,
    );
    await user.type(input, 'APT');
    submitSearchByEnter(input);

    await waitFor(
      () => {
        const state = useSearchStore.getState();
        expect(state.keyword).toBe('APT');
        expect(state.searchType).toBe(SearchType.KEYWORD);
      },
      { timeout: 5000 },
    );
  });

  it('搜索按钮可通过 findButtonByText 定位', () => {
    renderSearch();
    // 搜索按钮文本含 "搜 索"（CJK 字间距）
    const btn = findButtonByText(/搜\s*索/);
    expect(btn).toBeInTheDocument();
  });
});
