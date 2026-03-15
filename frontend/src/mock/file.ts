/**
 * Mock数据 - 文件列表
 */

import type { FileInfo, FileStatus, FileType } from '@/types';

// 生成随机日期
const randomDate = (start: Date, end: Date): string => {
  const date = new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime()));
  return date.toISOString();
};

// 生成随机文件大小
const randomFileSize = (): number => {
  const units = [1024, 1024 * 1024, 1024 * 1024 * 1024];
  const unit = units[Math.floor(Math.random() * units.length)];
  return Math.floor(Math.random() * 100 * unit);
};

// 文件类型列表
const fileTypes: FileType[] = [
  FileType.DOCUMENT,
  FileType.IMAGE,
  FileType.VIDEO,
  FileType.AUDIO,
  FileType.ARCHIVE,
  FileType.CODE,
  FileType.OTHER,
];

// 文件状态列表
const fileStatuses: FileStatus[] = [
  FileStatus.PENDING,
  FileStatus.PROCESSING,
  FileStatus.COMPLETED,
  FileStatus.FAILED,
];

// 文件名模板
const fileNameTemplates = [
  'malware_sample_{id}.exe',
  'network_traffic_{id}.pcap',
  'attack_report_{id}.pdf',
  'target_profile_{id}.docx',
  'exploit_code_{id}.py',
  'phishing_email_{id}.eml',
  'screenshot_{id}.png',
  'system_log_{id}.log',
  'config_backup_{id}.zip',
  'payload_{id}.bin',
];

// 标签列表
const tagPool = [
  '恶意软件', '钓鱼', '漏洞利用', 'APT', '勒索软件',
  '后门', '木马', '挖矿', 'DDoS', '数据泄露',
  '红队', '蓝队', '靶场', '渗透测试', '社工',
];

// 用户列表
const users = [
  { id: 'u1', name: '张三' },
  { id: 'u2', name: '李四' },
  { id: 'u3', name: '王五' },
  { id: 'u4', name: '赵六' },
];

/**
 * 生成单个Mock文件
 */
export const generateMockFile = (id: string): FileInfo => {
  const type = fileTypes[Math.floor(Math.random() * fileTypes.length)];
  const status = fileStatuses[Math.floor(Math.random() * fileStatuses.length)];
  const uploader = users[Math.floor(Math.random() * users.length)];
  const tags: string[] = [];
  
  // 随机选择2-4个标签
  const tagCount = 2 + Math.floor(Math.random() * 3);
  const shuffledTags = [...tagPool].sort(() => Math.random() - 0.5);
  for (let i = 0; i < tagCount; i++) {
    tags.push(shuffledTags[i]);
  }

  const template = fileNameTemplates[Math.floor(Math.random() * fileNameTemplates.length)];
  const originalName = template.replace('{id}', id);

  return {
    id,
    name: `file_${id}`,
    originalName,
    size: randomFileSize(),
    type,
    mimeType: 'application/octet-stream',
    status,
    path: `/storage/files/${id}`,
    hash: Array.from({ length: 64 }, () => 
      '0123456789abcdef'[Math.floor(Math.random() * 16)]
    ).join(''),
    tags,
    description: `这是文件 ${id} 的描述信息`,
    uploaderId: uploader.id,
    uploaderName: uploader.name,
    createTime: randomDate(new Date('2024-01-01'), new Date()),
    updateTime: randomDate(new Date('2024-01-01'), new Date()),
  };
};

/**
 * 生成Mock文件列表
 */
export const generateMockFileList = (count: number = 50): FileInfo[] => {
  return Array.from({ length: count }, (_, index) => 
    generateMockFile(`f${(index + 1).toString().padStart(4, '0')}`)
  );
};

/**
 * Mock文件列表数据
 */
export const mockFileList: FileInfo[] = generateMockFileList(100);

/**
 * 获取Mock文件列表（分页）
 */
export const getMockFileList = (
  page: number = 1,
  pageSize: number = 20,
  keyword?: string,
  type?: FileType,
  status?: FileStatus
): { list: FileInfo[]; total: number } => {
  let filteredList = [...mockFileList];

  // 关键词过滤
  if (keyword) {
    filteredList = filteredList.filter(
      (file) =>
        file.originalName.toLowerCase().includes(keyword.toLowerCase()) ||
        file.tags.some((tag) => tag.includes(keyword))
    );
  }

  // 类型过滤
  if (type) {
    filteredList = filteredList.filter((file) => file.type === type);
  }

  // 状态过滤
  if (status) {
    filteredList = filteredList.filter((file) => file.status === status);
  }

  const total = filteredList.length;
  const start = (page - 1) * pageSize;
  const list = filteredList.slice(start, start + pageSize);

  return { list, total };
};

/**
 * 根据ID获取Mock文件
 */
export const getMockFileById = (id: string): FileInfo | undefined => {
  return mockFileList.find((file) => file.id === id);
};

export default mockFileList;
