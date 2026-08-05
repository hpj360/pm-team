/**
 * 文件类型识别工具
 * - 根据文件名扩展名推断 FileType
 * - 与 services/file.ts 中的 detectFileType 保持一致
 */
import { FileType } from '@/types';

/** 扩展名到 FileType 的映射 */
const EXT_TYPE_MAP: Record<string, FileType> = {
  // 文档
  pdf: FileType.DOCUMENT,
  doc: FileType.DOCUMENT,
  docx: FileType.DOCUMENT,
  txt: FileType.DOCUMENT,
  md: FileType.DOCUMENT,
  markdown: FileType.DOCUMENT,
  eml: FileType.DOCUMENT,
  msg: FileType.DOCUMENT,
  rtf: FileType.DOCUMENT,
  xls: FileType.DOCUMENT,
  xlsx: FileType.DOCUMENT,
  ppt: FileType.DOCUMENT,
  pptx: FileType.DOCUMENT,
  // 图片
  png: FileType.IMAGE,
  jpg: FileType.IMAGE,
  jpeg: FileType.IMAGE,
  gif: FileType.IMAGE,
  bmp: FileType.IMAGE,
  webp: FileType.IMAGE,
  svg: FileType.IMAGE,
  ico: FileType.IMAGE,
  tiff: FileType.IMAGE,
  // 视频
  mp4: FileType.VIDEO,
  avi: FileType.VIDEO,
  mov: FileType.VIDEO,
  mkv: FileType.VIDEO,
  flv: FileType.VIDEO,
  wmv: FileType.VIDEO,
  // 音频
  mp3: FileType.AUDIO,
  wav: FileType.AUDIO,
  flac: FileType.AUDIO,
  aac: FileType.AUDIO,
  ogg: FileType.AUDIO,
  // 压缩包
  zip: FileType.ARCHIVE,
  rar: FileType.ARCHIVE,
  '7z': FileType.ARCHIVE,
  tar: FileType.ARCHIVE,
  gz: FileType.ARCHIVE,
  bz2: FileType.ARCHIVE,
  xz: FileType.ARCHIVE,
  // 代码
  py: FileType.CODE,
  js: FileType.CODE,
  ts: FileType.CODE,
  jsx: FileType.CODE,
  tsx: FileType.CODE,
  java: FileType.CODE,
  c: FileType.CODE,
  cpp: FileType.CODE,
  cc: FileType.CODE,
  h: FileType.CODE,
  go: FileType.CODE,
  rs: FileType.CODE,
  rb: FileType.CODE,
  php: FileType.CODE,
  sh: FileType.CODE,
  bat: FileType.CODE,
  ps1: FileType.CODE,
  // 取证/流量
  pcap: FileType.OTHER,
  pcapng: FileType.OTHER,
  bin: FileType.OTHER,
  dat: FileType.OTHER,
  log: FileType.OTHER,
};

/**
 * 根据文件名推断 FileType
 * @param fileName 文件名
 * @returns FileType
 */
export function detectFileTypeFromName(fileName: string): FileType {
  const lastDot = fileName.lastIndexOf('.');
  const ext = lastDot !== -1 ? fileName.slice(lastDot + 1).toLowerCase() : '';
  return EXT_TYPE_MAP[ext] ?? FileType.OTHER;
}

/** 文件类型中文标签 */
export const fileTypeLabel: Record<FileType, string> = {
  [FileType.DOCUMENT]: '文档',
  [FileType.IMAGE]: '图片',
  [FileType.VIDEO]: '视频',
  [FileType.AUDIO]: '音频',
  [FileType.ARCHIVE]: '压缩包',
  [FileType.CODE]: '代码',
  [FileType.OTHER]: '其他',
};

/** 文件类型对应的标签颜色（Ant Design Tag 颜色） */
export const fileTypeColor: Record<FileType, string> = {
  [FileType.DOCUMENT]: 'blue',
  [FileType.IMAGE]: 'green',
  [FileType.VIDEO]: 'purple',
  [FileType.AUDIO]: 'cyan',
  [FileType.ARCHIVE]: 'orange',
  [FileType.CODE]: 'geekblue',
  [FileType.OTHER]: 'default',
};

export default {
  detectFileTypeFromName,
  fileTypeLabel,
  fileTypeColor,
};
