/**
 * 单元测试：脱敏规则管理页面 src/pages/admin/DataMasking/index.tsx
 * 覆盖：
 * - 列表渲染（标题 / 工具栏 / 规则数据）
 * - 新增 Modal 打开与字段渲染
 * - Switch 启用/禁用调用 toggleRule API
 * - Popconfirm 删除调用 deleteRule API
 * - 规则测试预览：输入样例文本 -> 调用 testRule -> 显示脱敏后结果
 * - 新增规则提交调用 createRule API
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import DataMaskingPage from '@/pages/admin/DataMasking';
import type { DataMaskingRule, DataMaskingTestResult } from '@/types';

// Mock services
const mockListRules = vi.fn();
const mockCreateRule = vi.fn();
const mockUpdateRule = vi.fn();
const mockDeleteRule = vi.fn();
const mockToggleRule = vi.fn();
const mockTestRule = vi.fn();
vi.mock('@/services', () => ({
  listRules: (...args: unknown[]) => mockListRules(...args),
  createRule: (...args: unknown[]) => mockCreateRule(...args),
  updateRule: (...args: unknown[]) => mockUpdateRule(...args),
  deleteRule: (...args: unknown[]) => mockDeleteRule(...args),
  toggleRule: (...args: unknown[]) => mockToggleRule(...args),
  testRule: (...args: unknown[]) => mockTestRule(...args),
}));

/** 构造一个脱敏规则 */
const buildRule = (id: number, overrides: Partial<DataMaskingRule> = {}): DataMaskingRule => ({
  id,
  ruleName: `规则${id}`,
  pattern: '(1[3-9])\\d{4}(\\d{4})',
  replacement: '$1****$2',
  classificationLevel: 'INTERNAL',
  enabled: true,
  description: '测试规则',
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
  ...overrides,
});

const sampleRules: DataMaskingRule[] = [
  buildRule(1, { ruleName: '手机号脱敏', classificationLevel: 'INTERNAL', enabled: true }),
  buildRule(2, { ruleName: '身份证号脱敏', classificationLevel: 'CONFIDENTIAL', enabled: false }),
  buildRule(3, { ruleName: '邮箱地址脱敏', classificationLevel: 'PUBLIC', enabled: true }),
];

const renderPage = () =>
  render(
    <MemoryRouter>
      <App>
        <DataMaskingPage />
      </App>
    </MemoryRouter>,
  );

describe('DataMasking 脱敏规则管理页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListRules.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: sampleRules,
    });
    mockCreateRule.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: buildRule(99, { ruleName: '新规则' }),
    });
    mockUpdateRule.mockResolvedValue({
      code: 200,
      message: 'ok',
      data: sampleRules[0],
    });
    mockToggleRule.mockResolvedValue({ code: 200, message: 'ok', data: sampleRules[0] });
    mockDeleteRule.mockResolvedValue({ code: 200, message: 'ok', data: undefined });
    const testResult: DataMaskingTestResult = {
      input: '电话 13812345678',
      output: '电话 138****5678',
      matchedRuleIds: [1],
      matchedRuleNames: ['手机号脱敏'],
      matchCount: 1,
      costMs: 1,
    };
    mockTestRule.mockResolvedValue({ code: 200, message: 'ok', data: testResult });
  });

  it('渲染页面标题、工具栏与规则列表', async () => {
    renderPage();
    expect(screen.getByText('脱敏规则管理')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /新增规则/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /刷\s*新/ })).toBeInTheDocument();

    // 等待列表加载，验证规则名称渲染
    await waitFor(() => {
      expect(mockListRules).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByText('手机号脱敏')).toBeInTheDocument();
      expect(screen.getByText('身份证号脱敏')).toBeInTheDocument();
      expect(screen.getByText('邮箱地址脱敏')).toBeInTheDocument();
    });
  });

  it('点击新增规则按钮打开 Modal 并渲染表单字段', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /新增规则/ }));
    await waitFor(() => {
      // Modal 标题"新增脱敏规则"出现（工具栏按钮文案为"新增规则"，与 Modal 标题不同）
      expect(screen.getAllByText('新增脱敏规则').length).toBeGreaterThanOrEqual(1);
    });
    // 表单字段标签
    expect(screen.getAllByText('规则名称').length).toBeGreaterThan(0);
    expect(screen.getAllByText('匹配模式').length).toBeGreaterThan(0);
    expect(screen.getAllByText('替换文本').length).toBeGreaterThan(0);
    expect(screen.getAllByText('适用密级').length).toBeGreaterThan(0);
    expect(screen.getAllByText('启用状态').length).toBeGreaterThan(0);
    expect(screen.getAllByText('描述').length).toBeGreaterThan(0);
    // 测试预览相关
    expect(screen.getByText('样例输入文本')).toBeInTheDocument();
    expect(screen.getByText('脱敏结果')).toBeInTheDocument();
    // 自定义 footer 按钮（按钮带图标，accessible name 不止"测试"二字，使用 testid 定位更稳定）
    expect(screen.getByTestId('rule-test-btn')).toBeInTheDocument();
  });

  it('点击 Switch 切换启用状态调用 toggleRule API', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('手机号脱敏')).toBeInTheDocument();
    });

    // 找到 Switch 按钮
    const switches = screen.getAllByRole('switch');
    expect(switches.length).toBeGreaterThan(0);
    fireEvent.click(switches[0]);

    await waitFor(() => {
      expect(mockToggleRule).toHaveBeenCalledTimes(1);
      // 传入的 id 应为第一条规则 id
      expect(mockToggleRule).toHaveBeenCalledWith(1);
    });
  });

  it('点击删除按钮并确认后调用 deleteRule API', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('手机号脱敏')).toBeInTheDocument();
    });

    // 点击删除按钮
    const deleteButtons = screen.getAllByRole('button', { name: /删\s*除/ });
    expect(deleteButtons.length).toBeGreaterThan(0);
    fireEvent.click(deleteButtons[0]);

    // 出现 Popconfirm 确认框
    await waitFor(() => {
      expect(screen.getByText('确认删除该脱敏规则？')).toBeInTheDocument();
    });
    const okBtn = screen.getByRole('button', { name: /^OK$|^确\s*定$/ }) as HTMLButtonElement;
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(mockDeleteRule).toHaveBeenCalledTimes(1);
      expect(mockDeleteRule).toHaveBeenCalledWith(1);
    });
  });

  it('规则测试预览：输入样例文本点击测试后显示脱敏结果', async () => {
    renderPage();
    // 打开新增 Modal
    fireEvent.click(screen.getByRole('button', { name: /新增规则/ }));
    await waitFor(() => {
      expect(screen.getAllByText('新增脱敏规则').length).toBeGreaterThanOrEqual(1);
    });

    // 输入样例文本
    const testInput = screen.getByTestId('rule-test-input') as HTMLTextAreaElement;
    expect(testInput).toBeInTheDocument();
    fireEvent.change(testInput, { target: { value: '电话 13812345678' } });

    // 点击测试按钮
    const testBtn = screen.getByTestId('rule-test-btn');
    fireEvent.click(testBtn);

    // 验证 testRule 被调用
    await waitFor(() => {
      expect(mockTestRule).toHaveBeenCalledWith('电话 13812345678', undefined);
    });

    // 验证脱敏结果显示
    await waitFor(() => {
      expect(screen.getByTestId('rule-test-result')).toBeInTheDocument();
    });
    expect(screen.getByTestId('rule-test-output').textContent).toBe('电话 138****5678');
  });

  it('新增规则：填写表单并点击确定调用 createRule API', async () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: /新增规则/ }));
    await waitFor(() => {
      expect(screen.getAllByText('新增脱敏规则').length).toBeGreaterThanOrEqual(1);
    });

    // 填写规则名称
    const nameInput = screen.getByPlaceholderText('如：手机号脱敏') as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: '新手机号脱敏' } });

    // 填写匹配模式
    const patternInputs = screen
      .getAllByRole('textbox') as HTMLTextAreaElement[];
    // 匹配模式 textarea（第一个多行输入框，placeholder 含 "1[3-9]"）
    const patternInput = patternInputs.find((el) =>
      el.getAttribute('placeholder')?.includes('1[3-9]'),
    ) as HTMLTextAreaElement;
    expect(patternInput).toBeTruthy();
    fireEvent.change(patternInput, { target: { value: '(1[3-9])\\d{4}(\\d{4})' } });

    // 填写替换文本
    const replacementInput = screen.getByPlaceholderText('$1****$2') as HTMLInputElement;
    fireEvent.change(replacementInput, { target: { value: '$1****$2' } });

    // 点击确定按钮（Modal footer 中的 type="primary" 按钮，文案"确定"）
    const okButtons = screen.getAllByRole('button', { name: /^确\s*定$/ });
    // 选择最后一个（footer 中的确定按钮）
    const okBtn = okButtons[okButtons.length - 1] as HTMLButtonElement;
    await act(async () => {
      fireEvent.click(okBtn);
    });

    await waitFor(() => {
      expect(mockCreateRule).toHaveBeenCalledTimes(1);
    });
    const payload = mockCreateRule.mock.calls[0][0];
    expect(payload).toMatchObject({
      ruleName: '新手机号脱敏',
      pattern: '(1[3-9])\\d{4}(\\d{4})',
      replacement: '$1****$2',
      enabled: true,
    });
  });

  it('刷新按钮触发列表重新加载', async () => {
    renderPage();
    await waitFor(() => {
      expect(mockListRules).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByRole('button', { name: /刷\s*新/ }));

    await waitFor(() => {
      expect(mockListRules).toHaveBeenCalledTimes(2);
    });
  });
});
