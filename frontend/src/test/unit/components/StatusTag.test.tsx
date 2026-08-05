/**
 * 单元测试：StatusTag 组件（src/components/ops/StatusTag.tsx）
 * - Tag 模式渲染
 * - Badge 模式渲染
 * - 各状态颜色映射
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StatusTag from '@/components/ops/StatusTag';

describe('StatusTag', () => {
  it('tag 模式：渲染文本', () => {
    render(<StatusTag color="success" text="活跃" />);
    expect(screen.getByText('活跃')).toBeInTheDocument();
  });

  it('tag 模式：默认 variant 为 tag', () => {
    const { container } = render(<StatusTag color="error" text="异常" />);
    // antd Tag 渲染为 span.ant-tag
    const tag = container.querySelector('.ant-tag');
    expect(tag).not.toBeNull();
    expect(tag?.textContent).toContain('异常');
  });

  it('badge 模式：渲染 Badge + 文本', () => {
    const { container } = render(<StatusTag color="success" text="已完成" variant="badge" />);
    const badge = container.querySelector('.ant-badge');
    expect(badge).not.toBeNull();
    expect(screen.getByText('已完成')).toBeInTheDocument();
  });

  it('processing 颜色映射到 badge status', () => {
    const { container } = render(<StatusTag color="processing" text="运行中" variant="badge" />);
    const badgeStatus = container.querySelector('.ant-badge-status-processing');
    expect(badgeStatus).not.toBeNull();
  });

  it('warning 颜色映射到 badge status', () => {
    const { container } = render(<StatusTag color="warning" text="冻结" variant="badge" />);
    const badgeStatus = container.querySelector('.ant-badge-status-warning');
    expect(badgeStatus).not.toBeNull();
  });

  it('error 颜色映射到 badge status', () => {
    const { container } = render(<StatusTag color="error" text="失败" variant="badge" />);
    const badgeStatus = container.querySelector('.ant-badge-status-error');
    expect(badgeStatus).not.toBeNull();
  });

  it('default 颜色映射到 badge status', () => {
    const { container } = render(<StatusTag color="default" text="默认" variant="badge" />);
    const badgeStatus = container.querySelector('.ant-badge-status-default');
    expect(badgeStatus).not.toBeNull();
  });

  it('未知颜色降级为 default badge status', () => {
    const { container } = render(<StatusTag color="cyan" text="自定义" variant="badge" />);
    const badgeStatus = container.querySelector('.ant-badge-status-default');
    expect(badgeStatus).not.toBeNull();
  });

  it('tag 模式支持任意 antd 颜色值（如 cyan）', () => {
    const { container } = render(<StatusTag color="cyan" text="已归档" />);
    const tag = container.querySelector('.ant-tag');
    expect(tag).not.toBeNull();
    expect(tag?.className).toContain('ant-tag-cyan');
  });
});
