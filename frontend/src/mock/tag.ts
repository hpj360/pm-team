/**
 * Mock 数据 - 标签字典 + 文件标签关联
 * - L1-L6 六层标签体系（含 L2 业务流程、L6 安全合规）
 * - 文件与标签的多对多关联（含 AUTO / MANUAL 来源）
 * - 标签层级树构造
 */
import type { TagDict, FileTagVO, TagTreeNode } from '@/types';

/** Mock 标签字典列表（覆盖 L1-L6，共 24 条） */
export const mockTagDictList: TagDict[] = [
  // L1 文件属性
  { id: 1, tagCode: 'L1.FILE.TYPE.PDF', tagName: 'PDF文档', layer: 'L1', category: 'FILE', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'ext==pdf', isMulti: 1, enabled: 1, description: 'PDF 格式文件', createdAt: '2026-06-01T10:00:00Z', updatedAt: '2026-06-01T10:00:00Z' },
  { id: 2, tagCode: 'L1.FILE.TYPE.EXE', tagName: '可执行文件', layer: 'L1', category: 'FILE', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'ext in (exe,dll,scr)', isMulti: 1, enabled: 1, description: 'Windows 可执行文件', createdAt: '2026-06-01T10:05:00Z', updatedAt: '2026-06-01T10:05:00Z' },
  { id: 3, tagCode: 'L1.FILE.TYPE.PCAP', tagName: 'PCAP', layer: 'L1', category: 'FILE', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'ext==pcap', isMulti: 1, enabled: 1, description: '网络抓包文件', createdAt: '2026-06-01T10:10:00Z', updatedAt: '2026-06-01T10:10:00Z' },
  { id: 4, tagCode: 'L1.FILE.TYPE.DOC', tagName: 'DOC文档', layer: 'L1', category: 'FILE', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'ext in (doc,docx)', isMulti: 1, enabled: 1, description: 'Word 文档', createdAt: '2026-06-01T10:15:00Z', updatedAt: '2026-06-01T10:15:00Z' },
  { id: 5, tagCode: 'L1.FILE.TYPE.IMAGE', tagName: '图片', layer: 'L1', category: 'FILE', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'ext in (jpg,png,gif,bmp)', isMulti: 1, enabled: 1, description: '图片文件', createdAt: '2026-06-01T10:20:00Z', updatedAt: '2026-06-01T10:20:00Z' },
  { id: 6, tagCode: 'L1.FILE.TYPE.ZIP', tagName: 'ZIP压缩包', layer: 'L1', category: 'FILE', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'ext in (zip,rar,7z)', isMulti: 1, enabled: 1, description: '压缩包文件', createdAt: '2026-06-01T10:25:00Z', updatedAt: '2026-06-01T10:25:00Z' },
  // L2 业务流程
  { id: 7, tagCode: 'L2.PROC.RECON', tagName: '侦察阶段', layer: 'L2', category: 'PROCESS', valueType: 'ENUM', applicableObject: 'TASK', identifyRule: 'phase==recon', isMulti: 0, parentCode: 'L2.PROC', enabled: 1, description: '红队侦察阶段文件', createdAt: '2026-06-02T09:00:00Z', updatedAt: '2026-06-02T09:00:00Z' },
  { id: 8, tagCode: 'L2.PROC.WEAPON', tagName: '武器化阶段', layer: 'L2', category: 'PROCESS', valueType: 'ENUM', applicableObject: 'TASK', identifyRule: 'phase==weapon', isMulti: 0, parentCode: 'L2.PROC', enabled: 1, description: '武器化生成阶段', createdAt: '2026-06-02T09:05:00Z', updatedAt: '2026-06-02T09:05:00Z' },
  { id: 9, tagCode: 'L2.PROC.C2', tagName: '命令控制阶段', layer: 'L2', category: 'PROCESS', valueType: 'ENUM', applicableObject: 'TASK', identifyRule: 'phase==c2', isMulti: 0, parentCode: 'L2.PROC', enabled: 1, description: 'C2 通信阶段', createdAt: '2026-06-02T09:10:00Z', updatedAt: '2026-06-02T09:10:00Z' },
  { id: 10, tagCode: 'L2.PROC.PERSIST', tagName: '持久化阶段', layer: 'L2', category: 'PROCESS', valueType: 'ENUM', applicableObject: 'TASK', identifyRule: 'phase==persist', isMulti: 0, parentCode: 'L2.PROC', enabled: 0, description: '持久化维持阶段（已禁用）', createdAt: '2026-06-02T09:15:00Z', updatedAt: '2026-06-10T08:00:00Z' },
  // L3 实体识别
  { id: 11, tagCode: 'L3.ENTITY.IP', tagName: 'IP地址', layer: 'L3', category: 'ENTITY', valueType: 'TEXT', applicableObject: 'ENTITY', identifyRule: 'regex:ipv4', isMulti: 1, enabled: 1, description: 'IPv4 地址提取', createdAt: '2026-06-03T10:00:00Z', updatedAt: '2026-06-03T10:00:00Z' },
  { id: 12, tagCode: 'L3.ENTITY.IOC', tagName: 'IOC', layer: 'L3', category: 'ENTITY', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'intel:ioc', isMulti: 1, enabled: 1, description: '威胁情报指标', createdAt: '2026-06-03T10:05:00Z', updatedAt: '2026-06-03T10:05:00Z' },
  { id: 13, tagCode: 'L3.ENTITY.DOMAIN', tagName: '域名', layer: 'L3', category: 'ENTITY', valueType: 'TEXT', applicableObject: 'ENTITY', identifyRule: 'regex:domain', isMulti: 1, enabled: 1, description: '域名实体', createdAt: '2026-06-03T10:10:00Z', updatedAt: '2026-06-03T10:10:00Z' },
  { id: 14, tagCode: 'L3.ENTITY.FACE', tagName: '人脸', layer: 'L3', category: 'ENTITY', valueType: 'ENUM', applicableObject: 'ENTITY', identifyRule: 'ner:face', isMulti: 1, enabled: 1, description: '人脸实体', createdAt: '2026-06-03T10:15:00Z', updatedAt: '2026-06-03T10:15:00Z' },
  // L4 业务场景
  { id: 15, tagCode: 'L4.SCENE.TARGET_PROFILE', tagName: '目标画像', layer: 'L4', category: 'SCENE', valueType: 'ENUM', applicableObject: 'TARGET', identifyRule: 'scene:target', isMulti: 1, enabled: 1, description: '目标人物/组织画像', createdAt: '2026-06-04T11:00:00Z', updatedAt: '2026-06-04T11:00:00Z' },
  { id: 16, tagCode: 'L4.SCENE.NETWORK_TOPO', tagName: '网络地形', layer: 'L4', category: 'SCENE', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'scene:topo', isMulti: 1, enabled: 1, description: '网络拓扑地形', createdAt: '2026-06-04T11:05:00Z', updatedAt: '2026-06-04T11:05:00Z' },
  { id: 17, tagCode: 'L4.SCENE.PHISH', tagName: '钓鱼场景', layer: 'L4', category: 'SCENE', valueType: 'BOOL', applicableObject: 'FILE', identifyRule: 'keyword:phishing', isMulti: 0, enabled: 1, description: '钓鱼邮件相关文件', createdAt: '2026-06-04T11:10:00Z', updatedAt: '2026-06-04T11:10:00Z' },
  // L5 情报关联
  { id: 18, tagCode: 'L5.INTEL.APT28', tagName: 'APT28', layer: 'L5', category: 'INTEL', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'intel:apt28', isMulti: 1, enabled: 1, description: 'APT28 组织关联', createdAt: '2026-06-05T14:00:00Z', updatedAt: '2026-06-05T14:00:00Z' },
  { id: 19, tagCode: 'L5.INTEL.APT29', tagName: 'APT29', layer: 'L5', category: 'INTEL', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'intel:apt29', isMulti: 1, enabled: 1, description: 'APT29 组织关联', createdAt: '2026-06-05T14:05:00Z', updatedAt: '2026-06-05T14:05:00Z' },
  { id: 20, tagCode: 'L5.INTEL.MALWARE', tagName: '恶意软件', layer: 'L5', category: 'INTEL', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'intel:family', isMulti: 1, enabled: 1, description: '恶意软件关联', createdAt: '2026-06-05T14:10:00Z', updatedAt: '2026-06-05T14:10:00Z' },
  { id: 21, tagCode: 'L5.INTEL.CVE', tagName: 'CVE编号', layer: 'L5', category: 'INTEL', valueType: 'TEXT', applicableObject: 'ALL', identifyRule: 'regex:CVE-\\d+', isMulti: 1, enabled: 1, description: 'CVE 漏洞编号关联', createdAt: '2026-06-05T14:15:00Z', updatedAt: '2026-06-05T14:15:00Z' },
  // L6 安全合规
  { id: 22, tagCode: 'L6.COMPLIANCE.GDPR', tagName: 'GDPR合规', layer: 'L6', category: 'COMPLIANCE', valueType: 'BOOL', applicableObject: 'ALL', identifyRule: 'policy:gdpr', isMulti: 0, enabled: 1, description: 'GDPR 数据合规标记', createdAt: '2026-06-06T15:00:00Z', updatedAt: '2026-06-06T15:00:00Z' },
  { id: 23, tagCode: 'L6.COMPLIANCE.CLASSIFIED', tagName: '涉密文件', layer: 'L6', category: 'COMPLIANCE', valueType: 'ENUM', applicableObject: 'FILE', identifyRule: 'policy:classified', isMulti: 0, enabled: 1, description: '涉密文件标记', createdAt: '2026-06-06T15:05:00Z', updatedAt: '2026-06-06T15:05:00Z' },
  { id: 24, tagCode: 'L6.COMPLIANCE.AUDIT', tagName: '审计留痕', layer: 'L6', category: 'COMPLIANCE', valueType: 'BOOL', applicableObject: 'ALL', identifyRule: 'policy:audit', isMulti: 0, enabled: 1, description: '需要审计留痕的资源', createdAt: '2026-06-06T15:10:00Z', updatedAt: '2026-06-06T15:10:00Z' },
];

/** 兼容别名（旧字段名） */
export const mockTags = mockTagDictList;

/** 按 ID 获取标签 */
export function getTagById(id: number): TagDict | undefined {
  return mockTagDictList.find((t) => t.id === id);
}

/** 按 tagCode 获取标签 */
export function getTagByCode(tagCode: string): TagDict | undefined {
  return mockTagDictList.find((t) => t.tagCode === tagCode);
}

/** 按层级/分类/启用状态/关键字过滤 */
export function filterMockTags(params?: {
  layer?: string;
  category?: string;
  enabled?: number;
  keyword?: string;
}): TagDict[] {
  let arr = [...mockTagDictList];
  if (params?.layer) arr = arr.filter((t) => t.layer === params.layer);
  if (params?.category) arr = arr.filter((t) => t.category === params.category);
  if (params?.enabled !== undefined) arr = arr.filter((t) => t.enabled === params.enabled);
  if (params?.keyword) {
    const kw = params.keyword.toLowerCase();
    arr = arr.filter(
      (t) =>
        t.tagName.toLowerCase().includes(kw) ||
        t.tagCode.toLowerCase().includes(kw),
    );
  }
  return arr;
}

/** 构造标签层级树 */
export function buildMockTagTree(): TagTreeNode[] {
  const layerGroups: Record<string, TagTreeNode[]> = {};
  for (const tag of mockTagDictList) {
    if (!layerGroups[tag.layer]) layerGroups[tag.layer] = [];
    layerGroups[tag.layer].push({ ...tag });
  }
  const layerOrder = ['L1', 'L2', 'L3', 'L4', 'L5', 'L6'];
  return layerOrder
    .filter((l) => layerGroups[l])
    .map((layer, idx) => ({
      id: -idx - 100,
      tagCode: layer,
      tagName: layer,
      layer,
      category: 'ROOT',
      valueType: 'ENUM',
      applicableObject: 'ALL',
      isMulti: 0,
      enabled: 1,
      children: layerGroups[layer],
    }));
}

/** 标签层级树（Mock） */
export const mockTagTree: TagTreeNode[] = buildMockTagTree();

/** 根据 tagId 获取标签字典 */
const tagDictMap: Map<number, TagDict> = new Map(
  mockTagDictList.map((t) => [t.id, t]),
);

/** 构造 FileTagVO */
function makeFileTag(fileId: number, tagId: number, source: 'AUTO' | 'MANUAL'): FileTagVO {
  const tag = tagDictMap.get(tagId);
  return {
    fileId,
    tagId,
    tagCode: tag?.tagCode ?? '',
    tagName: tag?.tagName ?? '',
    layer: tag?.layer ?? '',
    source,
    createdAt: '2026-07-01T00:00:00.000Z',
  };
}

/**
 * Mock 文件标签关联列表（至少 5 个文件）
 * - 文件 1: PDF文档(L1)、IP(L3)、目标画像(L4)
 * - 文件 2: EXE(L1)、APT28(L5)、IOC(L3)
 * - 文件 3: PCAP(L1)、网络地形(L4)
 * - 文件 4: DOC文档(L1)、APT29(L5)、域名(L3)
 * - 文件 5: 图片(L1)、人脸(L3)、目标画像(L4)
 * - 文件 6: ZIP压缩包(L1)、恶意软件(L5)、IOC(L3)
 */
export const mockFileTagList: FileTagVO[] = [
  // 文件 1
  makeFileTag(1, 1, 'AUTO'),
  makeFileTag(1, 7, 'AUTO'),
  makeFileTag(1, 11, 'MANUAL'),
  // 文件 2
  makeFileTag(2, 2, 'AUTO'),
  makeFileTag(2, 13, 'MANUAL'),
  makeFileTag(2, 8, 'AUTO'),
  // 文件 3
  makeFileTag(3, 3, 'AUTO'),
  makeFileTag(3, 12, 'MANUAL'),
  // 文件 4
  makeFileTag(4, 4, 'AUTO'),
  makeFileTag(4, 14, 'MANUAL'),
  makeFileTag(4, 9, 'AUTO'),
  // 文件 5
  makeFileTag(5, 5, 'AUTO'),
  makeFileTag(5, 10, 'AUTO'),
  makeFileTag(5, 11, 'MANUAL'),
  // 文件 6
  makeFileTag(6, 6, 'AUTO'),
  makeFileTag(6, 15, 'MANUAL'),
  makeFileTag(6, 8, 'AUTO'),
];

/** 内存中的可变文件标签列表（供打标/取消打标操作使用） */
let inMemoryFileTags: FileTagVO[] = [...mockFileTagList];

/**
 * 从文件字符串 ID 中提取数字 ID
 * 例如 "f0001" -> 1, "f0012" -> 12
 */
export function parseFileIdToNumber(fileId: string): number {
  const num = parseInt(fileId.replace(/\D/g, ''), 10);
  return Number.isNaN(num) ? 0 : num;
}

/**
 * 根据文件 ID（字符串）获取文件标签
 */
export function getMockFileTags(fileId: string): FileTagVO[] {
  const numId = parseFileIdToNumber(fileId);
  return inMemoryFileTags
    .filter((ft) => ft.fileId === numId)
    .map((ft) => ({ ...ft }));
}

/**
 * 根据文件数字 ID 获取文件标签
 */
export function getMockFileTagsByNumericId(fileId: number): FileTagVO[] {
  return inMemoryFileTags
    .filter((ft) => ft.fileId === fileId)
    .map((ft) => ({ ...ft }));
}

/**
 * 根据标签 ID 获取文件 ID 列表
 */
export function getMockFilesByTag(tagId: number): number[] {
  return inMemoryFileTags
    .filter((ft) => ft.tagId === tagId)
    .map((ft) => ft.fileId);
}

/**
 * 为文件添加标签（Mock，写入内存）
 */
export function mockAddFileTags(fileId: number, tagIds: number[]): void {
  const now = new Date().toISOString();
  for (const tagId of tagIds) {
    const exists = inMemoryFileTags.some(
      (ft) => ft.fileId === fileId && ft.tagId === tagId,
    );
    if (!exists) {
      const tag = tagDictMap.get(tagId);
      inMemoryFileTags.push({
        fileId,
        tagId,
        tagCode: tag?.tagCode ?? '',
        tagName: tag?.tagName ?? '',
        layer: tag?.layer ?? '',
        source: 'MANUAL',
        createdAt: now,
      });
    }
  }
}

/**
 * 取消文件标签（Mock，从内存移除）
 */
export function mockRemoveFileTag(fileId: number, tagId: number): void {
  inMemoryFileTags = inMemoryFileTags.filter(
    (ft) => !(ft.fileId === fileId && ft.tagId === tagId),
  );
}

/** 别名：与 mockRemoveFileTag 等价（兼容 services 调用名） */
export const mockRemoveFileTags = mockRemoveFileTag;

/** 重置内存标签数据为初始状态（测试用） */
export function resetMockFileTags(): void {
  inMemoryFileTags = [...mockFileTagList];
}

export default {
  mockTagDictList,
  mockTags,
  mockFileTagList,
  mockTagTree,
  getTagById,
  getTagByCode,
  filterMockTags,
  buildMockTagTree,
  getMockFileTags,
  getMockFileTagsByNumericId,
  getMockFilesByTag,
  mockAddFileTags,
  mockRemoveFileTag,
  mockRemoveFileTags,
  parseFileIdToNumber,
  resetMockFileTags,
};
