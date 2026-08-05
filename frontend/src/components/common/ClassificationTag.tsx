/**
 * 文件密级标识组件
 * - 根据 classification 渲染对应颜色的 antd Tag
 * - PUBLIC: 蓝色 "公开"
 * - INTERNAL: 绿色 "内部"
 * - CONFIDENTIAL: 橙色 "秘密"
 * - SECRET: 红色 "机密"
 * - 字段为空或非法值时，缺省显示灰色 "未分级"
 *
 * 复用场景：FileList 表格列、FileDetail 顶部信息、其他需要展示密级的位置
 */
import React from 'react';
import { Tag } from 'antd';
import type { TagProps } from 'antd';
import {
  FileClassification,
  FileClassificationLabel,
  FileClassificationColor,
} from '@/types';

export interface ClassificationTagProps extends Omit<TagProps, 'color'> {
  /** 文件密级，缺省或非法值时显示"未分级" */
  classification?: FileClassification | string | null;
  /** 是否显示密级编码前缀（如 "SECRET 机密"），默认 false */
  showCode?: boolean;
}

const ClassificationTag: React.FC<ClassificationTagProps> = ({
  classification,
  showCode = false,
  ...rest
}) => {
  // 缺省或空值 -> 未分级（灰色）
  if (!classification) {
    return (
      <Tag color="default" {...rest}>
        未分级
      </Tag>
    );
  }

  const cls = classification as FileClassification;
  const label = FileClassificationLabel[cls];
  const color = FileClassificationColor[cls];

  // 非法值（不在枚举中） -> 未分级
  if (!label || !color) {
    return (
      <Tag color="default" {...rest}>
        未分级
      </Tag>
    );
  }

  return (
    <Tag color={color} {...rest}>
      {showCode ? `${cls} ${label}` : label}
    </Tag>
  );
};

export default ClassificationTag;
