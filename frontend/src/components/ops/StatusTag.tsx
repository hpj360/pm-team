/**
 * 运维状态标签
 * - 通用状态映射 Tag 组件
 * - 支持 type='badge' / 'tag' 两种展示
 * - 颜色继承自 ops.ts 中定义的 StatusTag 映射
 */
import React from 'react';
import { Tag, Badge } from 'antd';

export interface StatusTagProps {
  /** 颜色（antd Tag color，如 success/processing/error/warning/default/cyan 等） */
  color: string;
  /** 文本 */
  text: string;
  /** 展示形态，默认 'tag' */
  variant?: 'tag' | 'badge';
  /** 是否带边框（仅 tag 模式） */
  bordered?: boolean;
}

const StatusTag: React.FC<StatusTagProps> = ({ color, text, variant = 'tag', bordered }) => {
  if (variant === 'badge') {
    const statusMap: Record<string, 'success' | 'processing' | 'error' | 'warning' | 'default'> = {
      success: 'success',
      processing: 'processing',
      error: 'error',
      warning: 'warning',
      default: 'default',
    };
    const badgeStatus = statusMap[color] ?? 'default';
    return <Badge status={badgeStatus} text={text} />;
  }
  return <Tag color={color} bordered={bordered}>{text}</Tag>;
};

export default StatusTag;
