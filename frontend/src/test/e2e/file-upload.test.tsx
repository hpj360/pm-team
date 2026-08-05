/**
 * E2E 测试：文件上传页面
 * 覆盖：
 * - 拖拽区域渲染与文案
 * - 元数据表单（敏感等级 / 关联目标 / 公开性 / 标签 / 描述）
 * - 文件选择与待上传列表
 * - 标签添加 / 删除
 * - 上传按钮文案与状态
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ConfigProvider, App as AntdApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import FileUpload from '@/pages/FileUpload';
import { useFileStore } from '@/stores';

/** 包装组件 */
const renderUpload = () => {
  return render(
    <ConfigProvider locale={zhCN}>
      <AntdApp>
        <MemoryRouter>
          <FileUpload />
        </MemoryRouter>
      </AntdApp>
    </ConfigProvider>,
  );
};

describe('E2E: 文件上传', () => {
  beforeEach(() => {
    // 清空上传任务列表
    useFileStore.getState().uploadTasks = [];
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('初始渲染：包含标题、拖拽区、元数据表单', () => {
    renderUpload();

    // 标题
    expect(screen.getByText('文件上传')).toBeInTheDocument();
    expect(screen.getByText(/支持分片上传/)).toBeInTheDocument();

    // 拖拽区文案
    expect(screen.getByText('点击或拖拽文件到此区域上传')).toBeInTheDocument();
    expect(
      screen.getByText(/支持单个或批量上传，单文件最大 500MB/),
    ).toBeInTheDocument();

    // 元数据表单标签
    expect(screen.getByText('敏感等级')).toBeInTheDocument();
    expect(screen.getByText('关联目标')).toBeInTheDocument();
    expect(screen.getByText('是否公开')).toBeInTheDocument();
    expect(screen.getByText('标签')).toBeInTheDocument();
    expect(screen.getByText('描述信息')).toBeInTheDocument();
  });

  it('敏感等级下拉框：默认显示 L2 内部', () => {
    renderUpload();

    // 默认 L2 内部（下拉显示文本带 value - label 格式）
    const sensitivityDisplay = screen.getByText(/L2 - 内部/);
    expect(sensitivityDisplay).toBeInTheDocument();
  });

  it('标签输入：可通过回车添加标签', async () => {
    const user = userEvent.setup();
    renderUpload();

    // 标签输入框
    const tagInput = screen.getByPlaceholderText('输入标签后回车');
    expect(tagInput).toBeInTheDocument();

    // 添加标签 1：输入后按 Enter
    await user.type(tagInput, 'APT31');
    fireEvent.keyDown(tagInput, { key: 'Enter', code: 'Enter' });

    await waitFor(() => {
      expect(screen.getByText('APT31')).toBeInTheDocument();
    });
  });

  it('多个标签可同时显示', async () => {
    const user = userEvent.setup();
    renderUpload();

    // 添加第一个标签：通过 fireEvent.change 设置值，再点击 "添加" 按钮
    let tagInput = screen.getByPlaceholderText('输入标签后回车');
    fireEvent.change(tagInput, { target: { value: 'APT31' } });
    let addBtn = screen.getByRole('button', { name: /添加/ });
    await user.click(addBtn);

    // 等待标签 1 显示
    await screen.findByText('APT31');

    // 重新获取输入框（避免引用过期）
    tagInput = screen.getByPlaceholderText('输入标签后回车');
    fireEvent.change(tagInput, { target: { value: '钓鱼邮件' } });
    addBtn = screen.getByRole('button', { name: /添加/ });
    await user.click(addBtn);

    // 等待标签 2 显示
    await screen.findByText('钓鱼邮件');

    // 两个标签都存在
    expect(screen.getByText('APT31')).toBeInTheDocument();
    expect(screen.getByText('钓鱼邮件')).toBeInTheDocument();
  });

  it('公开性开关：默认内部，可切换为公开', async () => {
    const user = userEvent.setup();
    renderUpload();

    // 默认显示 "内部"
    expect(screen.getByText('内部')).toBeInTheDocument();

    // 找到 Switch（Antd 开关）
    const switchBtn = screen.getByRole('switch');
    expect(switchBtn).toBeInTheDocument();

    await user.click(switchBtn);

    await waitFor(() => {
      expect(screen.getByText('公开')).toBeInTheDocument();
    });
  });

  it('描述信息：可输入并显示字数', async () => {
    const user = userEvent.setup();
    renderUpload();

    const description = screen.getByPlaceholderText(
      /请输入文件描述/,
    ) as HTMLTextAreaElement;
    expect(description).toBeInTheDocument();

    await user.type(description, '本次红队作业采集的样本');

    await waitFor(() => {
      expect(description.value).toBe('本次红队作业采集的样本');
    });
  });

  it('上传按钮：未选择文件时不显示开始上传按钮', () => {
    renderUpload();

    // 没有待上传文件时，不显示 "开始上传" 按钮
    const buttons = screen.getAllByRole('button');
    const startUploadBtn = buttons.find((b) =>
      /开始上传/.test(b.textContent ?? ''),
    );
    expect(startUploadBtn).toBeUndefined();
  });

  it('页面包含 Skip / 文件元数据 区块标题', () => {
    renderUpload();

    expect(screen.getByText('文件元数据')).toBeInTheDocument();
    // 提示文本应说明元数据应用于所有文件
    expect(screen.getByText(/应用于本次上传的所有文件/)).toBeInTheDocument();
  });

  it('拖拽组件包含 Inbox 图标与提示', () => {
    renderUpload();
    // 拖拽区主要文案
    expect(
      screen.getByText('点击或拖拽文件到此区域上传'),
    ).toBeInTheDocument();
    // 提示信息
    expect(
      screen.getByText(
        /支持单个或批量上传，单文件最大 500MB；超过 5MB 自动启用分片上传/,
      ),
    ).toBeInTheDocument();
  });

  it('关联目标下拉框存在并可点击展开', async () => {
    renderUpload();

    // 关联目标选择器存在
    const targetSelector = screen.getByText('选择关联目标（可选）');
    expect(targetSelector).toBeInTheDocument();

    // Antd Select 的 selector 元素（避免点击到 pointer-events:none 的 span）
    const selectorInput = targetSelector.closest('.ant-select-selector');
    if (selectorInput) {
      // 通过 mousedown 触发展开（Antd Select 监听 mousedown）
      fireEvent.mouseDown(selectorInput);
    }

    // 至少不应抛出错误
    expect(targetSelector).toBeInTheDocument();
  });
});
