/**
 * Mock 数据 - 检索结果
 * 基于 mock/file.ts 的文件列表生成真实检索结果，附带聚合 facets 与多模式高亮片段
 */
import { SearchType } from '@/types';
import {
  AggregationField,
} from '@/types';
import type {
  SearchResultItem,
  SearchResult,
  SearchSuggestion,
  SearchHistory,
  SearchParams,
  AggregationResult,
  AggregationBucket,
  BooleanCondition,
  SearchTemplate,
} from '@/types';
import { mockFileList } from './file';
import { getMockFileTagsByNumericId, parseFileIdToNumber } from './tag';
import { fileTypeLabel } from '@/utils/fileType';
import type { FileInfo, FileTagVO } from '@/types';
import { FileType } from '@/types';

/** 字段中文映射（用于 matchedFields 展示） */
const FIELD_LABEL: Record<string, string> = {
  fileName: '文件名',
  tags: '标签',
  description: '描述',
  content: '内容',
  targetName: '关联目标',
};

/** 将关键词包装为 <em> 高亮片段 */
function em(keyword: string): string {
  return `<em>${keyword}</em>`;
}

/** 转义正则特殊字符（用于关键词字面量构建正则） */
function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** 计算 Levenshtein 编辑距离（用于模糊搜索评分） */
function levenshtein(a: string, b: string): number {
  const m = a.length;
  const n = b.length;
  if (m === 0) return n;
  if (n === 0) return m;
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost);
    }
  }
  return dp[m][n];
}

/** 在文本中查找关键词匹配并生成高亮片段（最多 3 段） */
function buildHighlightFragments(text: string, keywords: string[]): string[] {
  const fragments: string[] = [];
  const lowerText = text.toLowerCase();
  for (const kw of keywords) {
    if (!kw) continue;
    const lowerKw = kw.toLowerCase();
    let idx = lowerText.indexOf(lowerKw);
    let safety = 0;
    while (idx !== -1 && fragments.length < 3 && safety < 20) {
      const start = Math.max(0, idx - 20);
      const end = Math.min(text.length, idx + kw.length + 40);
      const prefix = start > 0 ? '…' : '';
      const suffix = end < text.length ? '…' : '';
      const rawFragment = text.slice(start, end);
      // 在片段内高亮关键词
      const highlighted = rawFragment.replace(
        new RegExp(escapeRegExp(kw), 'gi'),
        em(kw),
      );
      fragments.push(`${prefix}${highlighted}${suffix}`);
      idx = lowerText.indexOf(lowerKw, idx + kw.length);
      safety++;
    }
    if (fragments.length >= 3) break;
  }
  return fragments;
}

/** 从文件信息构造搜索结果项 */
function buildResultItem(
  file: FileInfo,
  keywords: string[],
  score: number,
  matchedFields: string[],
  snippetText: string,
): SearchResultItem {
  const highlights = matchedFields.map((field) => {
    let source = '';
    if (field === 'fileName') source = file.originalName;
    else if (field === 'tags') source = file.tags.join(' ');
    else if (field === 'description') source = file.description ?? '';
    else if (field === 'targetName') source = file.targetName ?? '';
    else source = snippetText;
    return {
      field,
      fragments: buildHighlightFragments(source, keywords),
    };
  }).filter((h) => h.fragments.length > 0);

  // 默认 snippet：优先用描述，否则用文件名
  const baseSnippet = file.description ?? file.originalName;
  const snippetFragments = buildHighlightFragments(baseSnippet, keywords);
  const snippet = snippetFragments[0] ?? baseSnippet;

  return {
    id: `sr-${file.id}`,
    fileId: file.id,
    fileName: file.originalName,
    score,
    highlights: highlights.length > 0 ? highlights : [
      { field: 'content', fragments: buildHighlightFragments(snippetText, keywords) },
    ],
    snippet,
    metadata: {
      fileType: file.type,
      size: file.size,
      uploadTime: file.createTime,
    },
    fileType: file.type,
    fileSize: file.size,
    mimeType: file.mimeType,
    sensitivity: file.sensitivity,
    tags: file.tags,
    fileTags: getMockFileTagsByNumericId(parseFileIdToNumber(file.id)),
    uploaderName: file.uploaderName,
    targetName: file.targetName,
    createTime: file.createTime,
    matchedFields,
  };
}

/** 关键词搜索：AND 关系，全部命中或部分命中 */
function keywordSearch(files: FileInfo[], keywords: string[]): { items: SearchResultItem[]; matched: FileInfo[] } {
  const items: SearchResultItem[] = [];
  const matched: FileInfo[] = [];
  for (const file of files) {
    const haystacks: Array<{ field: string; text: string }> = [
      { field: 'fileName', text: file.originalName },
      { field: 'tags', text: file.tags.join(' ') },
      { field: 'description', text: file.description ?? '' },
      { field: 'targetName', text: file.targetName ?? '' },
    ];
    const matchedFields: string[] = [];
    let hitCount = 0;
    for (const kw of keywords) {
      for (const h of haystacks) {
        if (h.text.toLowerCase().includes(kw.toLowerCase())) {
          if (!matchedFields.includes(h.field)) matchedFields.push(h.field);
          hitCount++;
          break;
        }
      }
    }
    if (hitCount > 0) {
      // 得分 = 命中关键词数 / 总关键词数 * 0.6 + 0.4 基础分
      const coverage = keywords.length > 0 ? hitCount / keywords.length : 0;
      const score = Math.min(1, 0.4 + coverage * 0.6);
      const snippetText = file.description ?? file.originalName;
      items.push(buildResultItem(file, keywords, score, matchedFields, snippetText));
      matched.push(file);
    }
  }
  // 按得分降序
  items.sort((a, b) => b.score - a.score);
  return { items, matched };
}

/** 模糊搜索：通配符 + 编辑距离 */
function fuzzySearch(files: FileInfo[], keyword: string): { items: SearchResultItem[]; matched: FileInfo[] } {
  // 将通配符转为正则
  let pattern: RegExp;
  try {
    const regexStr = escapeRegExp(keyword)
      .replace(/\\\*/g, '.*')
      .replace(/\\\?/g, '.');
    pattern = new RegExp(regexStr, 'i');
  } catch {
    pattern = new RegExp(escapeRegExp(keyword), 'i');
  }

  const items: SearchResultItem[] = [];
  const matched: FileInfo[] = [];
  const lowerKw = keyword.toLowerCase().replace(/[*?]/g, '');

  for (const file of files) {
    const haystacks: Array<{ field: string; text: string }> = [
      { field: 'fileName', text: file.originalName },
      { field: 'tags', text: file.tags.join(' ') },
      { field: 'description', text: file.description ?? '' },
    ];
    const matchedFields: string[] = [];
    let bestScore = 0;
    for (const h of haystacks) {
      const text = h.text;
      // 1. 通配符匹配
      if (pattern.test(text)) {
        if (!matchedFields.includes(h.field)) matchedFields.push(h.field);
        bestScore = Math.max(bestScore, 0.85);
      }
      // 2. 编辑距离匹配（针对无通配符的纯关键词）
      if (lowerKw.length >= 2) {
        const words = text.toLowerCase().split(/[\s._\-]+/);
        for (const word of words) {
          const dist = levenshtein(word, lowerKw);
          const maxLen = Math.max(word.length, lowerKw.length);
          if (maxLen > 0 && dist <= 2 && dist / maxLen < 0.4) {
            if (!matchedFields.includes(h.field)) matchedFields.push(h.field);
            const similarity = 1 - dist / maxLen;
            bestScore = Math.max(bestScore, 0.5 + similarity * 0.35);
          }
        }
      }
    }
    if (matchedFields.length > 0) {
      const snippetText = file.description ?? file.originalName;
      items.push(
        buildResultItem(file, [keyword], bestScore, matchedFields, snippetText),
      );
      matched.push(file);
    }
  }
  items.sort((a, b) => b.score - a.score);
  return { items, matched };
}

/** 语义搜索：基于关键词语义相似度模拟（Mock 用关键词重叠 + 标签相关性） */
function semanticSearch(files: FileInfo[], query: string): { items: SearchResultItem[]; matched: FileInfo[] } {
  // 提取查询词（分词，去停用词）
  const stopWords = new Set(['的', '了', '和', '与', '在', '是', '我', '要', '找', '关于', '一个']);
  const queryTerms = query
    .toLowerCase()
    .split(/[\s,，。、；;]+/)
    .map((t) => t.trim())
    .filter((t) => t.length > 0 && !stopWords.has(t));

  const items: SearchResultItem[] = [];
  const matched: FileInfo[] = [];

  // 语义关联词表（Mock 模拟向量相似度）
  const semanticRelated: Record<string, string[]> = {
    malware: ['恶意软件', '木马', '后门', '病毒'],
    phishing: ['钓鱼', '钓鱼邮件', '社工'],
    apt: ['apt', '高级威胁', '组织'],
    ransomware: ['勒索', '勒索软件', '加密'],
    c2: ['c2', '命令控制', '远控'],
    exploit: ['漏洞利用', 'exp', 'poc'],
    钓鱼: ['phishing', '社工', '邮件'],
    恶意软件: ['malware', '木马', '后门'],
    勒索: ['ransomware', '加密', '锁定'],
    漏洞: ['exploit', 'cve', 'poc'],
  };

  for (const file of files) {
    const corpus = (
      file.originalName +
      ' ' +
      file.tags.join(' ') +
      ' ' +
      (file.description ?? '') +
      ' ' +
      (file.targetName ?? '')
    ).toLowerCase();

    let score = 0;
    const matchedFields: string[] = [];
    for (const term of queryTerms) {
      // 直接命中
      if (corpus.includes(term)) {
        score += 0.3;
        if (file.originalName.toLowerCase().includes(term) && !matchedFields.includes('fileName')) {
          matchedFields.push('fileName');
        }
        if (file.tags.some((t) => t.toLowerCase().includes(term)) && !matchedFields.includes('tags')) {
          matchedFields.push('tags');
        }
        if ((file.description ?? '').toLowerCase().includes(term) && !matchedFields.includes('description')) {
          matchedFields.push('description');
        }
      }
      // 语义关联命中
      const related = semanticRelated[term];
      if (related) {
        for (const rel of related) {
          if (corpus.includes(rel.toLowerCase())) {
            score += 0.2;
            if (!matchedFields.includes('content')) matchedFields.push('content');
          }
        }
      }
    }
    // 归一化得分（最多 1.0）
    score = Math.min(1, score / Math.max(1, queryTerms.length * 0.4));
    if (score >= 0.35) {
      const snippetText = file.description ?? file.originalName;
      items.push(
        buildResultItem(file, queryTerms.length > 0 ? queryTerms : [query], score, matchedFields, snippetText),
      );
      matched.push(file);
    }
  }
  items.sort((a, b) => b.score - a.score);
  return { items, matched };
}

/** 正则搜索 */
function regexSearch(files: FileInfo[], pattern: string): { items: SearchResultItem[]; matched: FileInfo[] } {
  let regex: RegExp;
  try {
    regex = new RegExp(pattern, 'i');
  } catch {
    return { items: [], matched: [] };
  }

  const items: SearchResultItem[] = [];
  const matched: FileInfo[] = [];
  for (const file of files) {
    const haystacks: Array<{ field: string; text: string }> = [
      { field: 'fileName', text: file.originalName },
      { field: 'tags', text: file.tags.join(' ') },
      { field: 'description', text: file.description ?? '' },
      { field: 'targetName', text: file.targetName ?? '' },
    ];
    const matchedFields: string[] = [];
    for (const h of haystacks) {
      if (regex.test(h.text)) {
        if (!matchedFields.includes(h.field)) matchedFields.push(h.field);
      }
    }
    if (matchedFields.length > 0) {
      const snippetText = file.description ?? file.originalName;
      // 正则高亮：用匹配到的子串作为关键词
      const match = snippetText.match(regex);
      const highlightKw = match && match[0] ? match[0] : pattern;
      items.push(
        buildResultItem(file, [highlightKw], 0.9, matchedFields, snippetText),
      );
      matched.push(file);
    }
  }
  items.sort((a, b) => b.score - a.score);
  return { items, matched };
}

/** 应用过滤条件（基于 active filters） */
function applyFilters(files: FileInfo[], filters?: SearchParams['filters']): FileInfo[] {
  if (!filters || filters.length === 0) return files;
  return files.filter((file) => {
    return filters.every((f) => {
      const val = f.value;
      switch (f.field) {
        case AggregationField.FILE_TYPE:
          return f.operator === 'eq' ? file.type === val : true;
        case AggregationField.SENSITIVITY:
          return f.operator === 'eq' ? file.sensitivity === val : true;
        case AggregationField.UPLOADER:
          return f.operator === 'eq' ? file.uploaderName === val : true;
        case AggregationField.TARGET:
          return f.operator === 'eq' ? file.targetName === val : true;
        case AggregationField.TAGS:
          return f.operator === 'in'
            ? Array.isArray(val) && val.some((v) => file.tags.includes(String(v)))
            : file.tags.includes(String(val));
        default:
          return true;
      }
    });
  });
}

/**
 * 判断单个文件是否在指定字段中包含条件值（大小写不敏感）
 * - fileName: 文件名 originalName
 * - textContent: 文件内容（Mock 用 description 近似）
 * - tags: 标签数组任一包含
 * - fileType: 文件类型（type 或中文标签）包含
 */
function matchBooleanCondition(file: FileInfo, condition: BooleanCondition): boolean {
  const value = condition.value.trim().toLowerCase();
  if (!value) return true;
  switch (condition.field) {
    case 'fileName':
      return file.originalName.toLowerCase().includes(value);
    case 'textContent':
      return (file.description ?? '').toLowerCase().includes(value);
    case 'tags':
      return file.tags.some((t) => t.toLowerCase().includes(value));
    case 'fileType':
      return (
        file.type.toLowerCase().includes(value) ||
        (fileTypeLabel[file.type as FileType] ?? '').toLowerCase().includes(value)
      );
    default:
      return false;
  }
}

/**
 * 应用布尔组合条件
 * 逻辑：先处理 NOT（排除），再处理 AND（交集），再处理 OR（并集）
 * - NOT：文件不能包含任一 NOT 条件的关键词
 * - AND：文件必须满足所有 AND 条件
 * - OR：文件满足任一 OR 条件即可（与 AND 结果取并集）
 * @param files 已经过 facet 过滤的文件列表
 * @param conditions 布尔条件列表
 */
function applyBooleanConditions(
  files: FileInfo[],
  conditions?: BooleanCondition[],
): FileInfo[] {
  if (!conditions || conditions.length === 0) return files;

  const notConds = conditions.filter((c) => c.logic === 'NOT');
  const andConds = conditions.filter((c) => c.logic === 'AND');
  const orConds = conditions.filter((c) => c.logic === 'OR');

  // Step 1: NOT 排除 —— 移除匹配任一 NOT 条件的文件
  let working = files.filter((f) => !notConds.some((c) => matchBooleanCondition(f, c)));

  // Step 2 & 3: AND 交集 + OR 并集
  if (andConds.length > 0 && orConds.length > 0) {
    // AND 匹配集
    const andMatches = working.filter((f) => andConds.every((c) => matchBooleanCondition(f, c)));
    // OR 匹配集
    const orMatches = working.filter((f) => orConds.some((c) => matchBooleanCondition(f, c)));
    // 并集（保留 AND 优先顺序）
    const andIds = new Set(andMatches.map((f) => f.id));
    working = [...andMatches, ...orMatches.filter((f) => !andIds.has(f.id))];
  } else if (andConds.length > 0) {
    // 仅 AND：交集
    working = working.filter((f) => andConds.every((c) => matchBooleanCondition(f, c)));
  } else if (orConds.length > 0) {
    // 仅 OR：满足任一 OR 条件
    working = working.filter((f) => orConds.some((c) => matchBooleanCondition(f, c)));
  }

  return working;
}

/**
 * 应用二次检索：在已有结果文件ID范围内，按额外关键词过滤
 * @param files 当前文件列表
 * @param refineQuery 二次检索关键词
 * @param refineFileIds 限定文件ID范围（若提供则先收窄到此范围）
 */
function applyRefine(
  files: FileInfo[],
  refineQuery?: string,
  refineFileIds?: string[],
): FileInfo[] {
  let result = files;
  // 限定文件 ID 范围
  if (refineFileIds && refineFileIds.length > 0) {
    const idSet = new Set(refineFileIds);
    result = result.filter((f) => idSet.has(f.id));
  }
  // 额外关键词过滤（在文件名 / 标签 / 描述中匹配）
  const rq = (refineQuery ?? '').trim().toLowerCase();
  if (rq) {
    result = result.filter(
      (f) =>
        f.originalName.toLowerCase().includes(rq) ||
        f.tags.some((t) => t.toLowerCase().includes(rq)) ||
        (f.description ?? '').toLowerCase().includes(rq),
    );
  }
  return result;
}

/** 计算聚合 facets */
function buildAggregations(files: FileInfo[]): AggregationResult[] {
  const typeBuckets = new Map<string, number>();
  const tagBuckets = new Map<string, number>();
  const sensitivityBuckets = new Map<string, number>();
  const uploaderBuckets = new Map<string, number>();
  const targetBuckets = new Map<string, number>();
  const monthBuckets = new Map<string, number>();

  for (const file of files) {
    typeBuckets.set(file.type, (typeBuckets.get(file.type) ?? 0) + 1);
    for (const tag of file.tags) {
      tagBuckets.set(tag, (tagBuckets.get(tag) ?? 0) + 1);
    }
    if (file.sensitivity) {
      sensitivityBuckets.set(file.sensitivity, (sensitivityBuckets.get(file.sensitivity) ?? 0) + 1);
    }
    uploaderBuckets.set(file.uploaderName, (uploaderBuckets.get(file.uploaderName) ?? 0) + 1);
    if (file.targetName) {
      targetBuckets.set(file.targetName, (targetBuckets.get(file.targetName) ?? 0) + 1);
    }
    const month = (file.createTime || '').slice(0, 7); // YYYY-MM
    if (month) {
      monthBuckets.set(month, (monthBuckets.get(month) ?? 0) + 1);
    }
  }

  const toBuckets = (m: Map<string, number>, sortFn?: (a: AggregationBucket, b: AggregationBucket) => number): AggregationBucket[] => {
    const arr = Array.from(m.entries()).map(([key, count]) => ({ key, count }));
    arr.sort(sortFn ?? ((a, b) => b.count - a.count));
    return arr;
  };

  return [
    { field: AggregationField.FILE_TYPE, buckets: toBuckets(typeBuckets) },
    {
      field: AggregationField.TAGS,
      buckets: toBuckets(tagBuckets).slice(0, 12),
    },
    {
      field: AggregationField.SENSITIVITY,
      buckets: toBuckets(sensitivityBuckets, (a, b) => a.key.localeCompare(b.key)),
    },
    { field: AggregationField.UPLOADER, buckets: toBuckets(uploaderBuckets) },
    { field: AggregationField.TARGET, buckets: toBuckets(targetBuckets) },
    {
      field: AggregationField.UPLOAD_MONTH,
      buckets: toBuckets(monthBuckets, (a, b) => b.key.localeCompare(a.key)).slice(0, 6),
    },
  ];
}

/**
 * 应用标签筛选（AND 逻辑：文件必须包含所有选中标签）
 */
function applyTagFilters(files: FileInfo[], tagIds?: number[]): FileInfo[] {
  if (!tagIds || tagIds.length === 0) return files;
  return files.filter((file) => {
    const numId = parseFileIdToNumber(file.id);
    const fileTagIds = new Set(
      getMockFileTagsByNumericId(numId).map((ft: FileTagVO) => ft.tagId),
    );
    return tagIds.every((id) => fileTagIds.has(id));
  });
}

/**
 * 执行 Mock 搜索（统一入口）
 * @param params 搜索参数
 * @returns 完整搜索结果（含聚合）
 */
export function mockSearch(params: SearchParams): SearchResult {
  const keyword = (params.keyword ?? '').trim();
  // 先应用 facet 过滤条件
  let filteredFiles = applyFilters(mockFileList, params.filters);
  // 再应用布尔组合条件（AND / OR / NOT）
  filteredFiles = applyBooleanConditions(filteredFiles, params.booleanConditions);
  // 应用标签筛选（AND 逻辑）
  filteredFiles = applyTagFilters(filteredFiles, params.tagIds);

  let items: SearchResultItem[] = [];
  let matchedFiles: FileInfo[] = [];

  if (!keyword) {
    // 无关键词：返回过滤后的全部文件（按时间倒序）
    matchedFiles = [...filteredFiles].sort(
      (a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime(),
    );
    items = matchedFiles.map((file) =>
      buildResultItem(file, [], 0.5, [], file.description ?? file.originalName),
    );
  } else {
    switch (params.type) {
      case SearchType.SEMANTIC: {
        const r = semanticSearch(filteredFiles, keyword);
        items = r.items;
        matchedFiles = r.matched;
        break;
      }
      case SearchType.FUZZY: {
        const r = fuzzySearch(filteredFiles, keyword);
        items = r.items;
        matchedFiles = r.matched;
        break;
      }
      case SearchType.REGEX: {
        const r = regexSearch(filteredFiles, keyword);
        items = r.items;
        matchedFiles = r.matched;
        break;
      }
      case SearchType.KEYWORD:
      default: {
        const keywords = keyword.split(/\s+/).filter(Boolean);
        const r = keywordSearch(filteredFiles, keywords);
        items = r.items;
        matchedFiles = r.matched;
        break;
      }
    }
  }

  // 语义搜索阈值过滤
  if (params.type === SearchType.SEMANTIC && params.threshold !== undefined) {
    items = items.filter((it) => it.score >= params.threshold!);
  }
  // topK 截断
  if (params.type === SearchType.SEMANTIC && params.topK !== undefined) {
    items = items.slice(0, params.topK);
  }

  // 二次检索：在已有结果中搜索（限定文件ID范围 + 额外关键词过滤）
  const hasRefine =
    (params.refineFileIds && params.refineFileIds.length > 0) ||
    (params.refineQuery && params.refineQuery.trim().length > 0);
  if (hasRefine) {
    const refineIdSet = new Set(params.refineFileIds ?? []);
    const rq = (params.refineQuery ?? '').trim().toLowerCase();

    // 收窄 matchedFiles（驱动聚合）
    matchedFiles = applyRefine(matchedFiles, params.refineQuery, params.refineFileIds);

    // 收窄 items（保持与 matchedFiles 一致）
    items = items.filter((it) => {
      // 文件ID范围过滤
      if (refineIdSet.size > 0 && !refineIdSet.has(it.fileId)) return false;
      // 额外关键词过滤（文件名 / 标签 / 片段）
      if (rq) {
        const inName = it.fileName.toLowerCase().includes(rq);
        const inTags = (it.tags ?? []).some((t) => t.toLowerCase().includes(rq));
        const inSnippet = (it.snippet ?? '').toLowerCase().includes(rq);
        if (!inName && !inTags && !inSnippet) return false;
      }
      return true;
    });
  }

  const total = items.length;
  const page = params.page || 1;
  const pageSize = params.pageSize || 20;
  const start = (page - 1) * pageSize;
  const pageItems = items.slice(start, start + pageSize);

  // 聚合基于「命中文件全集」而非当前页
  const aggregations = buildAggregations(matchedFiles);

  // 模拟耗时：与结果数 + 类型相关
  const baseCost =
    params.type === SearchType.SEMANTIC ? 180 : params.type === SearchType.REGEX ? 120 : 60;
  const cost = baseCost + Math.floor(Math.random() * 80) + Math.min(200, total);

  return {
    items: pageItems,
    total,
    page,
    pageSize,
    cost,
    aggregations,
  };
}

/**
 * Mock 搜索建议
 */
export const mockSearchSuggestions: SearchSuggestion[] = [
  { text: 'malware', score: 0.95, type: 'keyword' },
  { text: 'malware analysis', score: 0.88, type: 'keyword' },
  { text: 'malware sample', score: 0.82, type: 'keyword' },
  { text: 'APT 攻击', score: 0.78, type: 'keyword' },
  { text: '钓鱼邮件', score: 0.72, type: 'keyword' },
  { text: '勒索软件', score: 0.68, type: 'keyword' },
  { text: 'C2 服务器', score: 0.65, type: 'keyword' },
];

/**
 * 获取 Mock 搜索建议
 */
export function getMockSearchSuggestions(keyword: string): SearchSuggestion[] {
  if (!keyword) return [];
  return mockSearchSuggestions
    .filter((s) => s.text.toLowerCase().includes(keyword.toLowerCase()))
    .slice(0, 5);
}

/**
 * Mock 搜索历史
 */
export const mockSearchHistory: SearchHistory[] = [
  {
    id: 'h1',
    keyword: 'malware',
    type: SearchType.KEYWORD,
    resultCount: 18,
    searchTime: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
  },
  {
    id: 'h2',
    keyword: 'APT 攻击',
    type: SearchType.SEMANTIC,
    resultCount: 12,
    searchTime: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
  },
  {
    id: 'h3',
    keyword: '钓鱼邮件',
    type: SearchType.KEYWORD,
    resultCount: 24,
    searchTime: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
  },
  {
    id: 'h4',
    keyword: '勒索软件',
    type: SearchType.FUZZY,
    resultCount: 9,
    searchTime: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
  },
  {
    id: 'h5',
    keyword: 'C2 服务器',
    type: SearchType.KEYWORD,
    resultCount: 7,
    searchTime: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(),
  },
];

/**
 * Mock 搜索模板
 */
export const mockSearchTemplates: SearchTemplate[] = [
  {
    id: 1,
    name: '高危漏洞文件搜索',
    paramsJson: JSON.stringify({ keyword: 'CVE', searchMode: 'keyword' }),
    createdAt: '2026-07-20 10:00:00',
    updatedAt: '2026-07-20 10:00:00',
  },
  {
    id: 2,
    name: 'APT 组织相关文件',
    paramsJson: JSON.stringify({ keyword: 'APT28 OR APT29', searchMode: 'keyword' }),
    createdAt: '2026-07-21 14:00:00',
    updatedAt: '2026-07-21 14:00:00',
  },
  {
    id: 3,
    name: '网络流量包',
    paramsJson: JSON.stringify({ keyword: '', searchMode: 'keyword', fileType: 'pcap' }),
    createdAt: '2026-07-22 09:00:00',
    updatedAt: '2026-07-22 09:00:00',
  },
];

/** 文件类型中文标签（Mock 聚合展示用） */
export function getAggregationBucketLabel(field: string, key: string): string {
  if (field === AggregationField.FILE_TYPE) {
    return fileTypeLabel[key as FileType] ?? key;
  }
  return key;
}

/** 字段中文标签 */
export function getFieldLabel(field: string): string {
  return FIELD_LABEL[field] ?? field;
}

export default {
  mockSearch,
  getMockSearchSuggestions,
  mockSearchHistory,
  mockSearchTemplates,
  getAggregationBucketLabel,
  getFieldLabel,
};
