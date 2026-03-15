/**
 * 文件类型图标组件
 * 根据文件类型显示对应的图标
 */

import React from 'react';
import {
  FileTextOutlined,
  FileImageOutlined,
  FileZipOutlined,
  FileMarkdownOutlined,
  FilePdfOutlined,
  FileExcelOutlined,
  FileWordOutlined,
  FilePptOutlined,
  CodeOutlined,
  FileUnknownOutlined,
} from '@ant-design/icons';
import { FileType } from '@/types';

interface FileIconProps {
  type: FileType;
  mimeType?: string;
  size?: number;
  style?: React.CSSProperties;
}

const FileIcon: React.FC<FileIconProps> = ({ type, mimeType, size = 24, style }) => {
  const iconStyle = { fontSize: size, ...style };

  // 根据MIME类型判断
  if (mimeType) {
    if (mimeType.startsWith('image/')) {
      return <FileImageOutlined style={iconStyle} />;
    }
    if (mimeType === 'application/pdf') {
      return <FilePdfOutlined style={iconStyle} />;
    }
    if (mimeType.includes('word') || mimeType.includes('document')) {
      return <FileWordOutlined style={iconStyle} />;
    }
    if (mimeType.includes('excel') || mimeType.includes('spreadsheet')) {
      return <FileExcelOutlined style={iconStyle} />;
    }
    if (mimeType.includes('powerpoint') || mimeType.includes('presentation')) {
      return <FilePptOutlined style={iconStyle} />;
    }
    if (mimeType.includes('zip') || mimeType.includes('rar') || mimeType.includes('7z')) {
      return <FileZipOutlined style={iconStyle} />;
    }
  }

  // 根据文件类型判断
  switch (type) {
    case FileType.DOCUMENT:
      return <FileTextOutlined style={iconStyle} />;
    case FileType.IMAGE:
      return <FileImageOutlined style={iconStyle} />;
    case FileType.VIDEO:
      return <FileMarkdownOutlined style={iconStyle} />;
    case FileType.AUDIO:
      return <FileMarkdownOutlined style={iconStyle} />;
    case FileType.ARCHIVE:
      return <FileZipOutlined style={iconStyle} />;
    case FileType.CODE:
      return <CodeOutlined style={iconStyle} />;
    default:
      return <FileUnknownOutlined style={iconStyle} />;
  }
};

export default FileIcon;
