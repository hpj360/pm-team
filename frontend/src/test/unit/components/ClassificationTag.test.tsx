/**
 * 单元测试：ClassificationTag 组件（src/components/common/ClassificationTag.tsx）
 * 覆盖：
 * - PUBLIC 蓝色 "公开"
 * - INTERNAL 绿色 "内部"
 * - CONFIDENTIAL 橙色 "秘密"
 * - SECRET 红色 "机密"
 * - 缺省（undefined / null / 空字符串 / 非法值）显示灰色 "未分级"
 * - showCode 选项展示密级编码
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ClassificationTag from '@/components/common/ClassificationTag';
import { FileClassification } from '@/types';

describe('ClassificationTag 组件', () => {
  it('PUBLIC 渲染为蓝色 "公开" Tag', () => {
    const { container } = render(<ClassificationTag classification={FileClassification.PUBLIC} />);
    expect(screen.getByText('公开')).toBeInTheDocument();
    const tag = container.querySelector('.ant-tag');
    expect(tag).not.toBeNull();
    expect(tag?.className).toContain('ant-tag-blue');
  });

  it('INTERNAL 渲染为绿色 "内部" Tag', () => {
    const { container } = render(<ClassificationTag classification={FileClassification.INTERNAL} />);
    expect(screen.getByText('内部')).toBeInTheDocument();
    const tag = container.querySelector('.ant-tag');
    expect(tag?.className).toContain('ant-tag-green');
  });

  it('CONFIDENTIAL 渲染为橙色 "秘密" Tag', () => {
    const { container } = render(<ClassificationTag classification={FileClassification.CONFIDENTIAL} />);
    expect(screen.getByText('秘密')).toBeInTheDocument();
    const tag = container.querySelector('.ant-tag');
    expect(tag?.className).toContain('ant-tag-orange');
  });

  it('SECRET 渲染为红色 "机密" Tag', () => {
    const { container } = render(<ClassificationTag classification={FileClassification.SECRET} />);
    expect(screen.getByText('机密')).toBeInTheDocument();
    const tag = container.querySelector('.ant-tag');
    expect(tag?.className).toContain('ant-tag-red');
  });

  it('classification 为 undefined 时缺省显示灰色 "未分级"', () => {
    const { container } = render(<ClassificationTag classification={undefined} />);
    expect(screen.getByText('未分级')).toBeInTheDocument();
    const tag = container.querySelector('.ant-tag');
    expect(tag?.className).toContain('ant-tag-default');
  });

  it('classification 为 null 时缺省显示灰色 "未分级"', () => {
    const { container } = render(<ClassificationTag classification={null} />);
    expect(screen.getByText('未分级')).toBeInTheDocument();
    const tag = container.querySelector('.ant-tag');
    expect(tag?.className).toContain('ant-tag-default');
  });

  it('classification 为非法值时降级显示灰色 "未分级"', () => {
    const { container } = render(<ClassificationTag classification="TOP_SECRET" />);
    expect(screen.getByText('未分级')).toBeInTheDocument();
    const tag = container.querySelector('.ant-tag');
    expect(tag?.className).toContain('ant-tag-default');
  });

  it('showCode=true 时显示密级编码前缀（如 "SECRET 机密"）', () => {
    render(<ClassificationTag classification={FileClassification.SECRET} showCode />);
    expect(screen.getByText('SECRET 机密')).toBeInTheDocument();
  });

  it('字符串字面量 "PUBLIC" 也能正确渲染（兼容后端返回字符串）', () => {
    const { container } = render(<ClassificationTag classification="PUBLIC" />);
    expect(screen.getByText('公开')).toBeInTheDocument();
    const tag = container.querySelector('.ant-tag');
    expect(tag?.className).toContain('ant-tag-blue');
  });
});
