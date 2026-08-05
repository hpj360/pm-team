/**
 * 文件检索页面（增强版）
 * - 四种搜索模式：关键词 / 语义 / 模糊 / 正则
 * - 左侧聚合 facet 面板：文件类型 / 标签 / 敏感等级 / 上传者 / 关联目标 / 上传月份
 * - 结果高亮：安全渲染 <em> 标记，不使用 dangerouslySetInnerHTML
 * - 相关度评分、命中字段、搜索耗时、分页、历史记录
 */

import React, { useMemo, useState, useCallback, useEffect } from 'react';
import {
  Card,
  Input,
  Button,
  Segmented,
  Tag,
  Typography,
  Empty,
  Progress,
  Pagination,
  Skeleton,
  Space,
  Tooltip,
  Popconfirm,
  message,
  Divider,
  Switch,
  Select,
  Modal,
} from 'antd';
import type { SearchProps } from 'antd/es/input/Search';
import {
  SearchOutlined,
  HistoryOutlined,
  ClearOutlined,
  ReloadOutlined,
  EyeOutlined,
  DownloadOutlined,
  ExperimentOutlined,
  FilterOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  PlusOutlined,
  DeleteOutlined,
  SaveOutlined,
  StarOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useSearch } from '@/hooks';
import { useSearchStore } from '@/stores';
import { nlSearch } from '@/services/ai';
import {
  SearchType,
  SEARCH_MODES,
  AggregationFieldLabel,
  FileType,
  SensitivityLevel,
  SensitivityLabel,
  BooleanSearchFieldLabel,
  BooleanLogicLabel,
  LayerColors,
} from '@/types';
import type {
  SearchResultItem,
  AggregationResult,
  SearchModeMeta,
  SearchHistory,
  SearchTemplate,
  SearchHistoryItem,
  BooleanCondition,
  BooleanLogic,
  BooleanSearchField,
} from '@/types';
import { formatDateTime, formatFileSize } from '@/utils';
import { fileTypeLabel, fileTypeColor } from '@/utils/fileType';
import { getAggregationBucketLabel } from '@/mock/search';
import { downloadFile, getFileDetail } from '@/services';
import type { FileInfo } from '@/types';
import FileIcon from '@/components/common/FileIcon';
import FileDetailDrawer from '@/pages/FileList/components/FileDetailDrawer';
import styles from './FileSearch.module.less';

const { Title, Text } = Typography;
const { TextArea } = Input;

/** 敏感等级颜色映射 */
const SENSITIVITY_COLOR: Record<string, string> = {
  [SensitivityLevel.L1]: 'green',
  [SensitivityLevel.L2]: 'blue',
  [SensitivityLevel.L3]: 'orange',
  [SensitivityLevel.L4]: 'volcano',
  [SensitivityLevel.L5]: 'red',
};

/** 布尔条件唯一 ID 生成器（前端管理用） */
let booleanConditionSeq = 0;
function genConditionId(): string {
  booleanConditionSeq += 1;
  return `bc-${Date.now()}-${booleanConditionSeq}`;
}

/** 布尔搜索字段下拉选项 */
const BOOLEAN_FIELD_OPTIONS: Array<{ label: string; value: BooleanSearchField }> = [
  { label: BooleanSearchFieldLabel.fileName, value: 'fileName' },
  { label: BooleanSearchFieldLabel.textContent, value: 'textContent' },
  { label: BooleanSearchFieldLabel.tags, value: 'tags' },
  { label: BooleanSearchFieldLabel.fileType, value: 'fileType' },
];

/** 布尔逻辑操作符下拉选项（首行仅 AND，后续行支持 AND/OR/NOT） */
const BOOLEAN_LOGIC_OPTIONS_ALL: Array<{ label: string; value: BooleanLogic }> = [
  { label: BooleanLogicLabel.AND, value: 'AND' },
  { label: BooleanLogicLabel.OR, value: 'OR' },
  { label: BooleanLogicLabel.NOT, value: 'NOT' },
];

const BOOLEAN_LOGIC_OPTIONS_FIRST: Array<{ label: string; value: BooleanLogic }> = [
  { label: BooleanLogicLabel.AND, value: 'AND' },
];

/**
 * 高亮文本渲染组件
 * 安全解析 <em>...</em> 标记为 React 元素，避免 XSS
 */
const HighlightText: React.FC<{ text: string; className?: string }> = ({
  text,
  className,
}) => {
  const parts = useMemo(() => {
    const result: React.ReactNode[] = [];
    const regex = /<em>([\s\S]*?)<\/em>/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    let key = 0;
    while ((match = regex.exec(text)) !== null) {
      if (match.index > lastIndex) {
        result.push(text.slice(lastIndex, match.index));
      }
      result.push(<em key={key++}>{match[1]}</em>);
      lastIndex = regex.lastIndex;
    }
    if (lastIndex < text.length) {
      result.push(text.slice(lastIndex));
    }
    return result;
  }, [text]);

  return <span className={className}>{parts}</span>;
};

/** 文件检索主页面 */
const FileSearch: React.FC = () => {
  const navigate = useNavigate();
  const {
    keyword,
    searchType,
    results,
    total,
    loading,
    cost,
    aggregations,
    activeFilters,
    params,
    history,
    // 布尔检索
    booleanConditions,
    booleanMode,
    addCondition,
    updateCondition,
    removeCondition,
    toggleBooleanMode,
    clearConditions,
    // 二次检索
    refineQuery,
    lastResultIds,
    isRefining,
    executeRefine,
    exitRefine,
    // 基础操作
    search,
    toggleFilter,
    clearFilters,
    changePage,
    changeSearchType,
    setKeyword,
    clearHistory,
    removeHistory,
    // 搜索模板与历史
    searchTemplates,
    searchHistory,
    saveTemplateModalVisible,
    loadTemplates,
    saveTemplate,
    applyTemplate,
    deleteTemplate,
    showSaveModal,
    hideSaveModal,
    clearSearchHistory,
    // 标签筛选
    selectedTagIds,
    availableTags,
    toggleTagFilter,
    loadAvailableTags,
  } = useSearch();

  const [inputValue, setInputValue] = useState(keyword);
  const [regexError, setRegexError] = useState<string>('');
  // 二次检索输入框临时值
  const [refineInput, setRefineInput] = useState(refineQuery);

  // 文件详情抽屉
  const [detailOpen, setDetailOpen] = useState(false);
  const [currentFile, setCurrentFile] = useState<FileInfo | null>(null);

  // 搜索模板相关本地状态
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | undefined>();
  const [templateName, setTemplateName] = useState<string>('');

  // 自然语言搜索模式（AI 驱动，调用 /api/ai/nlsearch）
  const [nlMode, setNlMode] = useState(false);
  const [nlInput, setNlInput] = useState('');
  const [nlLoading, setNlLoading] = useState(false);

  // 页面挂载时加载搜索模板列表
  useEffect(() => {
    loadTemplates();
  }, [loadTemplates]);

  // 页面挂载时加载可用标签列表
  useEffect(() => {
    loadAvailableTags();
  }, [loadAvailableTags]);

  /** 当前模式元信息 */
  const currentMode: SearchModeMeta = useMemo(
    () => SEARCH_MODES.find((m) => m.type === searchType) ?? SEARCH_MODES[0],
    [searchType],
  );

  /** 校验正则表达式 */
  const validateRegex = useCallback((pattern: string): boolean => {
    if (!pattern) {
      setRegexError('');
      return true;
    }
    try {
      // eslint-disable-next-line no-new
      new RegExp(pattern);
      setRegexError('');
      return true;
    } catch (e) {
      setRegexError(e instanceof Error ? e.message : '正则表达式无效');
      return false;
    }
  }, []);

  /** 输入变化处理 */
  const handleInputChange = (value: string) => {
    setInputValue(value);
    if (searchType === SearchType.REGEX) {
      validateRegex(value);
    }
  };

  /** 执行搜索 */
  const handleSearch = () => {
    if (searchType === SearchType.REGEX && inputValue && !validateRegex(inputValue)) {
      message.error('请输入有效的正则表达式');
      return;
    }
    setKeyword(inputValue);
    search({ keyword: inputValue, type: searchType, page: 1 });
  };

  /**
   * 执行自然语言搜索（AI 驱动）
   * 调用 /api/ai/nlsearch，将返回的结构化结果直接填充到现有结果列表
   * 失败时由服务层降级返回 Mock 数据，不阻塞页面
   */
  const handleNlSearch = async () => {
    const query = nlInput.trim();
    if (!query) {
      message.warning('请输入自然语言查询语句');
      return;
    }
    setNlLoading(true);
    const store = useSearchStore.getState();
    store.setLoading(true);
    try {
      const res = await nlSearch(query);
      if (res.code === 200 || res.code === 0) {
        const items = res.data.searchResults ?? [];
        store.setResults(items);
        store.setTotal(items.length);
        store.setCost(0);
        store.setAggregations([]);
        store.setKeyword(query);
        store.setLastResultIds(items.map((it) => it.fileId));
        message.success(`AI 检索完成：${res.data.translatedQuery}`);
      } else {
        message.error(res.message || 'AI 检索失败');
      }
    } catch {
      message.error('AI 检索失败，请稍后重试');
    } finally {
      setNlLoading(false);
      store.setLoading(false);
    }
  };

  /** 切换搜索模式 */
  const handleModeChange = (value: string | number) => {
    const newType = value as SearchType;
    changeSearchType(newType);
    setRegexError('');
    // 切换模式后若已有输入则自动重搜
    if (inputValue.trim()) {
      setKeyword(inputValue);
      search({ keyword: inputValue, type: newType, page: 1 }, true);
    }
  };

  /** 点击历史记录快速搜索 */
  const handleHistoryClick = (h: SearchHistory) => {
    setInputValue(h.keyword);
    changeSearchType(h.type);
    setKeyword(h.keyword);
    search({ keyword: h.keyword, type: h.type, page: 1 });
  };

  /** 选择搜索模板并应用 */
  const handleTemplateSelect = (template: SearchTemplate) => {
    setSelectedTemplateId(template.id);
    // 同步输入框为模板中的关键词
    try {
      const tplParams = JSON.parse(template.paramsJson) as { keyword?: string };
      setInputValue(tplParams.keyword ?? '');
    } catch {
      // 解析失败时保持输入框不变
    }
    applyTemplate(template);
  };

  /** 删除搜索模板 */
  const handleDeleteTemplate = (id: number) => {
    deleteTemplate(id);
    if (selectedTemplateId === id) {
      setSelectedTemplateId(undefined);
    }
    message.success('模板已删除');
  };

  /** 保存搜索模板（Modal 确认） */
  const handleSaveTemplate = async () => {
    const name = templateName.trim();
    if (!name) {
      message.warning('请输入模板名称');
      return;
    }
    // 保存当前搜索条件（以 store 中的 keyword 为准，若输入框有值则先同步）
    if (inputValue.trim()) {
      setKeyword(inputValue);
    }
    await saveTemplate(name);
    setTemplateName('');
    hideSaveModal();
    message.success('保存成功');
  };

  /** 取消保存模板 */
  const handleCancelSaveTemplate = () => {
    setTemplateName('');
    hideSaveModal();
  };

  /** 点击搜索历史项（SearchHistoryItem）恢复搜索 */
  const handleSearchHistoryClick = (item: SearchHistoryItem) => {
    const mode = item.searchMode as SearchType;
    setInputValue(item.keyword);
    changeSearchType(mode);
    setKeyword(item.keyword);
    search({ keyword: item.keyword, type: mode, page: 1 });
  };

  /** 重置搜索 */
  const handleReset = () => {
    setInputValue('');
    setKeyword('');
    setRegexError('');
    clearFilters();
    // 退出二次检索并清空布尔条件（保持与重置语义一致）
    if (isRefining) {
      setRefineInput('');
      exitRefine();
    }
    if (booleanMode) {
      clearConditions();
    }
  };

  /** 切换布尔检索模式 */
  const handleToggleBooleanMode = (checked: boolean) => {
    toggleBooleanMode();
    if (!checked) {
      // 关闭时清空条件（store 中 setBooleanMode(false) 已清空）
      // 这里无需额外操作
    }
  };

  /** 添加布尔条件（默认 AND 逻辑） */
  const handleAddCondition = () => {
    const newCondition: BooleanCondition = {
      id: genConditionId(),
      logic: 'AND',
      field: 'fileName',
      value: '',
    };
    addCondition(newCondition);
  };

  /** 更新条件逻辑操作符 */
  const handleConditionLogicChange = (id: string, logic: BooleanLogic) => {
    updateCondition(id, { logic });
  };

  /** 更新条件字段 */
  const handleConditionFieldChange = (id: string, field: BooleanSearchField) => {
    updateCondition(id, { field });
  };

  /** 更新条件值 */
  const handleConditionValueChange = (id: string, value: string) => {
    updateCondition(id, { value });
  };

  /** 删除条件 */
  const handleRemoveCondition = (id: string) => {
    removeCondition(id);
  };

  /** 执行二次检索 */
  const handleRefineSearch = (value: string) => {
    const trimmed = (value ?? '').trim();
    if (!trimmed) {
      message.warning('请输入二次检索关键词');
      return;
    }
    executeRefine(trimmed);
  };

  /** 退出二次检索 */
  const handleExitRefine = () => {
    setRefineInput('');
    exitRefine();
  };

  /** 打开文件详情 */
  const handleViewDetail = async (item: SearchResultItem) => {
    if (!item.fileId) return;
    const hide = message.loading('加载文件详情...', 0);
    try {
      const res = await getFileDetail(item.fileId);
      hide();
      if (res.code === 200 || res.code === 0) {
        setCurrentFile(res.data);
        setDetailOpen(true);
      } else {
        message.error(res.message || '获取文件详情失败');
      }
    } catch {
      hide();
      message.error('获取文件详情失败');
    }
  };

  /** 下载文件 */
  const handleDownload = (item: SearchResultItem) => {
    if (!item.fileId) return;
    window.open(downloadFile(item.fileId), '_blank');
  };

  /** 跳转分析 */
  const handleAnalyze = (item: SearchResultItem) => {
    navigate('/analyze', { state: { fileId: item.fileId, fileName: item.fileName } });
  };

  /** 判断 facet bucket 是否激活 */
  const isBucketActive = (field: string, key: string): boolean => {
    return activeFilters.some((f) => {
      if (f.field !== field) return false;
      if (Array.isArray(f.value)) {
        // tags 多选场景：value 为 string[]，检查是否包含 key
        return (f.value as string[]).includes(key);
      }
      return f.value === key;
    });
  };

  /** 渲染搜索输入区 */
  const renderSearchInput = () => {
    const onSearch: SearchProps['onSearch'] = handleSearch;
    if (currentMode.multiline) {
      return (
        <div className={styles.searchInput}>
          <TextArea
            className={styles.searchTextarea}
            value={inputValue}
            onChange={(e) => handleInputChange(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                handleSearch();
              }
            }}
            placeholder={currentMode.placeholder}
            autoSize={{ minRows: 2, maxRows: 6 }}
            size="large"
          />
          <Button
            type="primary"
            size="large"
            icon={<SearchOutlined />}
            onClick={handleSearch}
            loading={loading}
          >
            搜索
          </Button>
        </div>
      );
    }
    return (
      <Input.Search
        value={inputValue}
        onChange={(e) => handleInputChange(e.target.value)}
        onPressEnter={handleSearch}
        onSearch={onSearch}
        placeholder={currentMode.placeholder}
        size="large"
        loading={loading}
        enterButton="搜索"
      />
    );
  };

  /** 渲染布尔查询构建器 */
  const renderBooleanBuilder = () => {
    return (
      <div className={styles.booleanBuilder}>
        <div className={styles.booleanHeader}>
          <Space>
            <Switch
              checked={booleanMode}
              onChange={handleToggleBooleanMode}
              size="small"
              data-testid="boolean-mode-switch"
            />
            <Text type="secondary" style={{ fontSize: 13 }}>
              布尔检索（AND / OR / NOT 组合查询）
            </Text>
          </Space>
          {booleanMode && booleanConditions.length > 0 && (
            <Button
              type="link"
              size="small"
              icon={<ClearOutlined />}
              onClick={clearConditions}
            >
              清空条件
            </Button>
          )}
        </div>

        {booleanMode && (
          <div className={styles.booleanConditions}>
            {booleanConditions.length === 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                点击下方「添加条件」创建第一个布尔条件
              </Text>
            )}
            {booleanConditions.map((cond, idx) => {
              const isFirst = idx === 0;
              const logicOptions = isFirst
                ? BOOLEAN_LOGIC_OPTIONS_FIRST
                : BOOLEAN_LOGIC_OPTIONS_ALL;
              return (
                <div key={cond.id} className={styles.booleanRow}>
                  <Select
                    className={styles.booleanLogicSelect}
                    value={cond.logic}
                    disabled={isFirst}
                    options={logicOptions}
                    onChange={(v: BooleanLogic) => handleConditionLogicChange(cond.id, v)}
                  />
                  <Select
                    className={styles.booleanFieldSelect}
                    value={cond.field}
                    options={BOOLEAN_FIELD_OPTIONS}
                    onChange={(v: BooleanSearchField) =>
                      handleConditionFieldChange(cond.id, v)
                    }
                  />
                  <Input
                    className={styles.booleanValueInput}
                    value={cond.value}
                    placeholder="输入关键词"
                    onChange={(e) => handleConditionValueChange(cond.id, e.target.value)}
                    onPressEnter={handleSearch}
                  />
                  <Tooltip title={isFirst ? '第一行不可删除' : '删除该条件'}>
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      disabled={isFirst && booleanConditions.length === 1}
                      onClick={() => handleRemoveCondition(cond.id)}
                    />
                  </Tooltip>
                </div>
              );
            })}
            <Button
              type="dashed"
              size="small"
              icon={<PlusOutlined />}
              onClick={handleAddCondition}
            >
              添加条件
            </Button>
          </div>
        )}
      </div>
    );
  };

  /** 渲染二次检索栏 */
  const renderRefineBar = () => {
    if (total === 0 && !isRefining) return null;
    return (
      <div className={styles.refineBar}>
        {isRefining ? (
          <Space wrap>
            <Tag color="processing" closable onClose={handleExitRefine}>
              当前在 {lastResultIds.length} 条结果中搜索：{refineQuery}
            </Tag>
            <Button
              type="link"
              size="small"
              icon={<ClearOutlined />}
              onClick={handleExitRefine}
            >
              退出二次检索
            </Button>
          </Space>
        ) : (
          <Input.Search
            className={styles.refineInput}
            value={refineInput}
            onChange={(e) => setRefineInput(e.target.value)}
            onSearch={handleRefineSearch}
            placeholder="在结果中搜索（二次检索）"
            size="small"
            allowClear
            enterButton="在结果中搜索"
          />
        )}
      </div>
    );
  };

  /** 渲染标签 facet（结构化标签字典，支持 AND 多选） */
  const renderTagFacet = () => {
    if (!availableTags || availableTags.length === 0) return null;
    const topTags = availableTags.slice(0, 10);
    return (
      <div className={styles.facetSection} data-testid="tag-facet-section">
        <div className={styles.facetTitle}>
          <span>文件标签</span>
          <span className={styles.facetCount}>
            {selectedTagIds.length > 0
              ? `已选 ${selectedTagIds.length}`
              : `${topTags.length} 项`}
          </span>
        </div>
        <div className={styles.facetBucketList} style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 4 }}>
          {topTags.map((tag) => {
            const active = selectedTagIds.includes(tag.id);
            const color = LayerColors[tag.layer] ?? 'default';
            return (
              <Tag
                key={tag.id}
                color={active ? color : undefined}
                style={{
                  cursor: 'pointer',
                  opacity: active ? 1 : 0.55,
                  borderWidth: active ? 2 : 1,
                  borderColor: active ? undefined : 'transparent',
                  margin: 0,
                }}
                data-testid={`tag-facet-${tag.id}`}
                onClick={() => toggleTagFilter(tag.id)}
              >
                {tag.tagName}
              </Tag>
            );
          })}
        </div>
      </div>
    );
  };

  /** 渲染聚合 facet 面板 */
  const renderFacetPanel = () => {
    const hasTagFacet = availableTags && availableTags.length > 0;
    const hasAggregations = aggregations.length > 0;
    const hasAnyFilter = activeFilters.length > 0 || selectedTagIds.length > 0;

    if (!hasAggregations && !hasTagFacet) {
      return (
        <Card className={styles.facetPanel} size="small" title="聚合筛选">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="执行搜索后显示聚合统计"
          />
        </Card>
      );
    }

    return (
      <Card
        className={styles.facetPanel}
        size="small"
        title={
          <Space>
            <FilterOutlined />
            <span>聚合筛选</span>
          </Space>
        }
        extra={
          hasAnyFilter ? (
            <Button
              type="link"
              size="small"
              icon={<ClearOutlined />}
              onClick={clearFilters}
            >
              清空
            </Button>
          ) : null
        }
      >
        {/* 标签 facet（结构化标签字典，AND 多选） */}
        {renderTagFacet()}
        {aggregations.map((agg: AggregationResult) => {
          if (!agg.buckets || agg.buckets.length === 0) return null;
          const fieldLabel =
            AggregationFieldLabel[agg.field as keyof typeof AggregationFieldLabel] ??
            agg.field;
          return (
            <div key={agg.field} className={styles.facetSection}>
              <div className={styles.facetTitle}>
                <span>{fieldLabel}</span>
                <span className={styles.facetCount}>{agg.buckets.length} 项</span>
              </div>
              <div className={styles.facetBucketList}>
                {agg.buckets.map((bucket) => {
                  const active = isBucketActive(agg.field, bucket.key);
                  const label = getAggregationBucketLabel(agg.field, bucket.key);
                  return (
                    <div
                      key={bucket.key}
                      className={`${styles.facetBucket} ${
                        active ? styles.facetBucketActive : ''
                      }`}
                      onClick={() => toggleFilter(agg.field, bucket.key)}
                    >
                      <span className={styles.facetBucketLabel}>{label}</span>
                      <span className={styles.facetBucketCount}>{bucket.count}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
        {hasAnyFilter && (
          <Button
            className={styles.facetClearAll}
            block
            size="small"
            icon={<ClearOutlined />}
            onClick={clearFilters}
          >
            清空所有筛选（{activeFilters.length + selectedTagIds.length}）
          </Button>
        )}
      </Card>
    );
  };

  /** 渲染单条结果卡片 */
  const renderResultCard = (item: SearchResultItem, isLast: boolean) => {
    const scorePercent = Math.round(item.score * 100);
    const scoreColor =
      scorePercent >= 80 ? '#52c41a' : scorePercent >= 50 ? '#1890ff' : '#faad14';
    const fileType = (item.fileType as FileType) ?? FileType.OTHER;

    return (
      <Card
        key={item.id}
        size="small"
        className={`${styles.resultCard} ${isLast ? styles.resultCardLast : ''}`}
      >
        {/* 头部：图标 + 文件名 + 评分 */}
        <div className={styles.resultHeader}>
          <FileIcon type={fileType} size={18} />
          <Tooltip title={item.fileName}>
            <span className={styles.resultTitle}>
              <a
                className={styles.resultTitleLink}
                onClick={() => handleViewDetail(item)}
              >
                {item.fileName}
              </a>
            </span>
          </Tooltip>
          <div className={styles.scoreBar}>
            <Progress
              percent={scorePercent}
              size="small"
              strokeColor={scoreColor}
              showInfo={false}
              style={{ width: 80, margin: 0 }}
            />
            <span className={styles.scoreValue} style={{ color: scoreColor }}>
              {scorePercent}%
            </span>
          </div>
        </div>

        {/* 命中字段标签 */}
        {item.matchedFields && item.matchedFields.length > 0 && (
          <div className={styles.matchedFields}>
            <Text type="secondary" style={{ fontSize: 11 }}>
              命中：
            </Text>
            {item.matchedFields.map((field) => (
              <Tag key={field} className={styles.matchedFieldTag} color="processing">
                {field}
              </Tag>
            ))}
          </div>
        )}

        {/* 高亮片段 */}
        {item.highlights && item.highlights.length > 0 && (
          <div className={`${styles.snippetArea} search-highlight`}>
            {item.highlights.slice(0, 3).map((hl, idx) => (
              <span key={`${hl.field}-${idx}`} className={styles.snippetLine}>
                <span className={styles.snippetField}>[{hl.field}]</span>
                <HighlightText text={hl.fragments[0] || ''} />
              </span>
            ))}
          </div>
        )}

        {/* 标签 */}
        {item.tags && item.tags.length > 0 && (
          <div className={styles.resultTags}>
            {item.tags.slice(0, 6).map((tag) => (
              <Tag key={tag}>{tag}</Tag>
            ))}
            {item.tags.length > 6 && (
              <Tooltip title={item.tags.slice(6).join(', ')}>
                <Tag>+{item.tags.length - 6}</Tag>
              </Tooltip>
            )}
          </div>
        )}

        {/* 元信息 */}
        <div className={styles.resultMeta}>
          <span className={styles.metaItem}>
            <Tag color={fileTypeColor[fileType]} style={{ margin: 0 }}>
              {fileTypeLabel[fileType]}
            </Tag>
          </span>
          {item.fileSize !== undefined && (
            <span className={styles.metaItem}>{formatFileSize(item.fileSize)}</span>
          )}
          {item.sensitivity && (
            <span className={styles.metaItem}>
              <Tag
                color={SENSITIVITY_COLOR[item.sensitivity] ?? 'default'}
                style={{ margin: 0 }}
              >
                {item.sensitivity} - {SensitivityLabel[item.sensitivity as SensitivityLevel] ?? item.sensitivity}
              </Tag>
            </span>
          )}
          {item.uploaderName && (
            <span className={styles.metaItem}>
              <Text type="secondary">{item.uploaderName}</Text>
            </span>
          )}
          {item.targetName && (
            <span className={styles.metaItem}>
              <Text type="secondary">目标：{item.targetName}</Text>
            </span>
          )}
          {item.createTime && (
            <span className={styles.metaItem}>
              <ClockCircleOutlined />
              {formatDateTime(item.createTime)}
            </span>
          )}
        </div>

        {/* 操作 */}
        <div className={styles.resultActions}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(item)}
          >
            详情
          </Button>
          <Button
            type="link"
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => handleDownload(item)}
          >
            下载
          </Button>
          <Button
            type="link"
            size="small"
            icon={<ExperimentOutlined />}
            onClick={() => handleAnalyze(item)}
          >
            分析
          </Button>
        </div>
      </Card>
    );
  };

  /** 渲染结果区 */
  const renderResults = () => {
    if (loading) {
      return (
        <div className={styles.loadingState}>
          {[1, 2, 3].map((i) => (
            <Skeleton
              key={i}
              className={styles.skeletonItem}
              active
              avatar={{ shape: 'square', size: 'small' }}
              paragraph={{ rows: 2 }}
            />
          ))}
        </div>
      );
    }

    if (results.length === 0) {
      return (
        <Card>
          <div className={styles.emptyState}>
            <Empty
              description={
                keyword || activeFilters.length > 0
                  ? '没有找到匹配的文件'
                  : '请输入关键词或选择搜索模式进行检索'
              }
            >
              {keyword && (
                <Button
                  type="primary"
                  icon={<ReloadOutlined />}
                  onClick={handleReset}
                >
                  重置搜索
                </Button>
              )}
            </Empty>
          </div>
        </Card>
      );
    }

    return (
      <>
        {results.map((item, idx) =>
          renderResultCard(item, idx === results.length - 1),
        )}
        <div className={styles.paginationWrap}>
          <Pagination
            current={params.page}
            pageSize={params.pageSize}
            total={total}
            showSizeChanger
            showQuickJumper
            showTotal={(t) => `共 ${t} 条`}
            onChange={(page, pageSize) => changePage(page, pageSize)}
          />
        </div>
      </>
    );
  };

  return (
    <div>
      {/* 页面标题 */}
      <div className={styles.pageHeader}>
        <Title level={4} style={{ margin: 0 }}>
          文件检索
        </Title>
        <Text type="secondary">
          关键词 / 语义 / 模糊 / 正则 四种模式 · 聚合筛选 · 结果高亮
        </Text>
      </div>

      {/* 搜索卡片 */}
      <Card className={styles.searchCard}>
        {/* 自然语言模式开关（AI 驱动） */}
        <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center' }}>
          <Space>
            <Switch
              checked={nlMode}
              onChange={(checked) => setNlMode(checked)}
              size="small"
              data-testid="nl-mode-switch"
            />
            <RobotOutlined style={{ color: '#722ed1' }} />
            <Text type="secondary">
              自然语言模式（AI 驱动，将查询语义转换为关键词检索）
            </Text>
          </Space>
        </div>

        {nlMode ? (
          /* 自然语言搜索输入 */
          <div className={styles.searchInput}>
            <Input.Search
              value={nlInput}
              onChange={(e) => setNlInput(e.target.value)}
              onSearch={handleNlSearch}
              onPressEnter={handleNlSearch}
              placeholder="用自然语言描述，如：查找所有包含 APT28 相关 IP 的 PDF 文件"
              size="large"
              loading={nlLoading}
              enterButton="AI 检索"
              data-testid="nl-search-input"
            />
          </div>
        ) : (
          <>
            {/* 模式选择 */}
            <div className={styles.modeBar}>
              <Segmented
                value={searchType}
                onChange={handleModeChange}
                options={SEARCH_MODES.map((m) => ({
                  label: m.label,
                  value: m.type,
                }))}
              />
            </div>
            <div className={styles.modeDesc}>{currentMode.description}</div>

            {/* 搜索输入 */}
            {renderSearchInput()}

            {/* 正则错误提示 */}
            {searchType === SearchType.REGEX && regexError && (
              <div className={styles.regexError}>正则错误：{regexError}</div>
            )}

            {/* 布尔查询构建器 */}
            {renderBooleanBuilder()}
          </>
        )}

        {/* 操作栏 */}
        <div className={styles.searchActions}>
          <Button icon={<ReloadOutlined />} onClick={handleReset}>
            重置
          </Button>
          {activeFilters.length > 0 && (
            <Button
              type="link"
              icon={<ClearOutlined />}
              onClick={clearFilters}
            >
              清空筛选（{activeFilters.length}）
            </Button>
          )}
          <span className={styles.modeHint}>
            <ThunderboltOutlined /> {currentMode.hint}
          </span>
        </div>

        {/* 搜索模板栏 */}
        <div className={styles.templateBar}>
          <Space wrap>
            <Select
              className={styles.templateSelect}
              placeholder="选择搜索模板"
              value={selectedTemplateId}
              allowClear
              suffixIcon={<StarOutlined />}
              onChange={(value: number | undefined) => {
                if (value == null) {
                  setSelectedTemplateId(undefined);
                  return;
                }
                const tpl = searchTemplates.find((t) => t.id === value);
                if (tpl) handleTemplateSelect(tpl);
              }}
              options={searchTemplates.map((t) => ({
                label: t.name,
                value: t.id,
              }))}
            />
            {selectedTemplateId != null && (
              <Popconfirm
                title="确定删除该搜索模板吗？"
                onConfirm={() => handleDeleteTemplate(selectedTemplateId)}
              >
                <Button danger icon={<DeleteOutlined />} />
              </Popconfirm>
            )}
            <Button icon={<SaveOutlined />} onClick={showSaveModal}>
              保存搜索
            </Button>
          </Space>
        </div>

        {/* 搜索历史 */}
        {history.length > 0 && (
          <>
            <Divider style={{ margin: '12px 0 8px' }} />
            <div className={styles.historyRow}>
              <span className={styles.historyLabel}>
                <HistoryOutlined /> 搜索历史：
              </span>
              {history.slice(0, 8).map((h) => (
                <Tag
                  key={h.id}
                  className={styles.historyTag}
                  closable
                  onClose={(e) => {
                    e.preventDefault();
                    removeHistory(h.id);
                  }}
                  onClick={() => handleHistoryClick(h)}
                >
                  {h.keyword}
                  <span className={styles.facetCount} style={{ marginLeft: 4 }}>
                    ({h.resultCount})
                  </span>
                </Tag>
              ))}
              {history.length > 0 && (
                <Popconfirm
                  title="确定清空所有搜索历史吗？"
                  onConfirm={clearHistory}
                >
                  <Button
                    type="link"
                    size="small"
                    icon={<ClearOutlined />}
                  >
                    清空历史
                  </Button>
                </Popconfirm>
              )}
            </div>
          </>
        )}

        {/* 搜索历史（localStorage 持久化） */}
        <Divider style={{ margin: '12px 0 8px' }} />
        <div className={styles.searchHistorySection}>
          <div className={styles.searchHistoryHeader}>
            <span className={styles.searchHistoryTitle}>
              <HistoryOutlined /> 搜索历史
            </span>
            {searchHistory.length > 0 && (
              <Popconfirm
                title="确定清空所有搜索历史吗？"
                onConfirm={clearSearchHistory}
              >
                <Button type="link" size="small" icon={<ClearOutlined />}>
                  清空
                </Button>
              </Popconfirm>
            )}
          </div>
          {searchHistory.length > 0 ? (
            <div className={styles.searchHistoryTags}>
              {searchHistory.slice(0, 20).map((item) => (
                <Tag
                  key={item.id}
                  className={styles.historyTag}
                  onClick={() => handleSearchHistoryClick(item)}
                >
                  {item.displayName}
                </Tag>
              ))}
            </div>
          ) : (
            <Text type="secondary" style={{ fontSize: 12 }}>
              暂无搜索历史
            </Text>
          )}
        </div>
      </Card>

      {/* 统计栏 */}
      {(total > 0 || loading) && (
        <div className={styles.statsBar}>
          <div className={styles.statsInfo}>
            <span className={styles.statItem}>
              找到 <span className={styles.statValue}>{total}</span> 个结果
            </span>
            {cost > 0 && (
              <span className={styles.statItem}>
                耗时 <span className={`${styles.statValue} ${styles.statCost}`}>{cost}ms</span>
              </span>
            )}
            <span className={styles.statItem}>
              模式 <span className={styles.statValue}>{currentMode.label}</span>
            </span>
            {activeFilters.length > 0 && (
              <span className={styles.statItem}>
                筛选 <span className={styles.statValue}>{activeFilters.length}</span> 项
              </span>
            )}
            {selectedTagIds.length > 0 && (
              <span className={styles.statItem}>
                标签 <span className={styles.statValue}>{selectedTagIds.length}</span> 项
              </span>
            )}
            {booleanMode && booleanConditions.length > 0 && (
              <span className={styles.statItem}>
                布尔条件 <span className={styles.statValue}>{booleanConditions.length}</span> 项
              </span>
            )}
          </div>
          {/* 二次检索栏 */}
          {renderRefineBar()}
        </div>
      )}

      {/* 主体：双栏布局 */}
      <div className={styles.body}>
        {renderFacetPanel()}
        <div className={styles.resultsArea}>{renderResults()}</div>
      </div>

      {/* 文件详情抽屉（复用 FileList 的抽屉组件） */}
      <FileDetailDrawer
        open={detailOpen}
        file={currentFile}
        onClose={() => {
          setDetailOpen(false);
          setCurrentFile(null);
        }}
      />

      {/* 保存搜索模板 Modal */}
      <Modal
        title="保存搜索模板"
        open={saveTemplateModalVisible}
        onOk={handleSaveTemplate}
        onCancel={handleCancelSaveTemplate}
        okText="保存"
        cancelText="取消"
      >
        <Input
          placeholder="请输入模板名称"
          value={templateName}
          onChange={(e) => setTemplateName(e.target.value)}
          onPressEnter={handleSaveTemplate}
          maxLength={50}
        />
      </Modal>
    </div>
  );
};

export default FileSearch;
