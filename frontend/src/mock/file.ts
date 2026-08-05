/**
 * Mock 数据 - 文件列表（30 条固定 + 动态生成）
 */
import { FileType, FileStatus, SensitivityLevel } from '@/types';
import type { FileInfo } from '@/types';
import { getMockFileTags } from './tag';

const randomDate = (start: Date, end: Date): string => {
  const date = new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime()));
  return date.toISOString();
};

const randomFileSize = (): number => {
  const units = [1024, 1024 * 1024, 1024 * 1024 * 1024];
  const unit = units[Math.floor(Math.random() * units.length)];
  return Math.floor(Math.random() * 100 * unit);
};

const fileTypes: FileType[] = [
  FileType.DOCUMENT,
  FileType.IMAGE,
  FileType.VIDEO,
  FileType.AUDIO,
  FileType.ARCHIVE,
  FileType.CODE,
  FileType.OTHER,
];

const fileStatuses: FileStatus[] = [
  FileStatus.PENDING,
  FileStatus.PROCESSING,
  FileStatus.COMPLETED,
  FileStatus.COMPLETED,
  FileStatus.COMPLETED,
  FileStatus.FAILED,
];

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

const tagPool = [
  '恶意软件',
  '钓鱼',
  '漏洞利用',
  'APT',
  '勒索软件',
  '后门',
  '木马',
  '挖矿',
  'DDoS',
  '数据泄露',
  '红队',
  '蓝队',
  '靶场',
  '渗透测试',
  '社工',
];

const users = [
  { id: 'u1', name: '张三' },
  { id: 'u2', name: '李四' },
  { id: 'u3', name: '王五' },
  { id: 'u4', name: '赵六' },
];

const targets = [
  { id: 't1', name: '目标 A 集团' },
  { id: 't2', name: '目标 B 组织' },
  { id: 't3', name: '目标 C 公司' },
];

/** 目标列表（供上传元数据表单选择） */
export const mockTargetList = targets.map((t) => ({ value: t.id, label: t.name }));

/** 团队空间列表（供上传元数据表单选择） */
export const mockTeamSpaceOptions = [
  { value: 1001, label: 'APT追踪组' },
  { value: 1002, label: '恶意软件分析组' },
  { value: 1003, label: '红蓝对抗组' },
  { value: 1004, label: '钓鱼演练组' },
  { value: 1005, label: '靶场运营组' },
  { value: 1006, label: '情报汇聚组' },
];

const sensitivityLevels = [
  SensitivityLevel.L1,
  SensitivityLevel.L2,
  SensitivityLevel.L2,
  SensitivityLevel.L3,
  SensitivityLevel.L3,
  SensitivityLevel.L4,
  SensitivityLevel.L5,
];

const randomHash = (len: number): string =>
  Array.from({ length: len }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join('');

/**
 * 生成单个 Mock 文件
 */
export const generateMockFile = (id: string): FileInfo => {
  const type = fileTypes[Math.floor(Math.random() * fileTypes.length)];
  const status = fileStatuses[Math.floor(Math.random() * fileStatuses.length)];
  const uploader = users[Math.floor(Math.random() * users.length)];
  const target = targets[Math.floor(Math.random() * targets.length)];
  const sensitivity = sensitivityLevels[Math.floor(Math.random() * sensitivityLevels.length)];

  const tags: string[] = [];
  const tagCount = 2 + Math.floor(Math.random() * 3);
  const shuffledTags = [...tagPool].sort(() => Math.random() - 0.5);
  for (let i = 0; i < tagCount; i++) tags.push(shuffledTags[i]);

  const template = fileNameTemplates[Math.floor(Math.random() * fileNameTemplates.length)];
  const originalName = template.replace('{id}', id);
  const createTime = randomDate(new Date('2024-01-01'), new Date());

  return {
    id,
    name: `file_${id}`,
    originalName,
    size: randomFileSize(),
    type,
    mimeType: 'application/octet-stream',
    status,
    path: `/storage/files/${id}`,
    hash: randomHash(32),
    sm3: randomHash(64),
    tags,
    fileTags: getMockFileTags(id),
    description: `这是文件 ${id} 的描述信息，包含红方作业期间采集的关键证据材料。`,
    uploaderId: uploader.id,
    uploaderName: uploader.name,
    sensitivity,
    targetId: target.id,
    targetName: target.name,
    isPublic: sensitivity === SensitivityLevel.L1,
    parseStatus: status === FileStatus.COMPLETED ? FileStatus.COMPLETED : FileStatus.PROCESSING,
    parsedAt: status === FileStatus.COMPLETED ? randomDate(new Date(createTime), new Date()) : undefined,
    createTime,
    updateTime: randomDate(new Date(createTime), new Date()),
  };
};

/**
 * 生成 Mock 文件列表
 */
export const generateMockFileList = (count: number = 30): FileInfo[] => {
  return Array.from({ length: count }, (_, index) =>
    generateMockFile(`f${(index + 1).toString().padStart(4, '0')}`),
  );
};

/**
 * Mock 文件列表数据（30 条）
 */
export const mockFileList: FileInfo[] = generateMockFileList(30);

/**
 * 获取 Mock 文件列表（分页 + 过滤）
 */
export const getMockFileList = (
  page: number = 1,
  pageSize: number = 20,
  keyword?: string,
  type?: FileType,
  status?: FileStatus,
  sensitivity?: SensitivityLevel,
): { list: FileInfo[]; total: number } => {
  let filteredList = [...mockFileList];

  if (keyword) {
    const kw = keyword.toLowerCase();
    filteredList = filteredList.filter(
      (file) =>
        file.originalName.toLowerCase().includes(kw) ||
        file.tags.some((tag) => tag.includes(keyword)) ||
        (file.description ?? '').toLowerCase().includes(kw),
    );
  }
  if (type) filteredList = filteredList.filter((file) => file.type === type);
  if (status) filteredList = filteredList.filter((file) => file.status === status);
  if (sensitivity) filteredList = filteredList.filter((file) => file.sensitivity === sensitivity);

  const total = filteredList.length;
  const start = (page - 1) * pageSize;
  return { list: filteredList.slice(start, start + pageSize), total };
};

/**
 * 根据ID获取Mock文件
 */
export const getMockFileById = (id: string): FileInfo | undefined => {
  return mockFileList.find((file) => file.id === id);
};

export default mockFileList;
