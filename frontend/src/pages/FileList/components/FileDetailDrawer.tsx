/**
 * 文件详情抽屉
 * - 基本信息：元数据 + 哈希 + 标签
 * - YARA 扫描：触发扫描 + 查看命中规则与匹配字符串
 * - NER 实体：命名实体识别结果列表
 * - 关联 IOC：从文件中提取的威胁情报
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  Drawer,
  Tabs,
  Descriptions,
  Tag,
  Space,
  Button,
  Table,
  Typography,
  Spin,
  Empty,
  Badge,
  Statistic,
  Row,
  Col,
  Select,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ScanOutlined,
  ReloadOutlined,
  CopyOutlined,
  WarningOutlined,
  FileSearchOutlined,
} from '@ant-design/icons';
import type {
  FileInfo,
  YaraScanResult,
  YaraMatchResult,
  YaraMatchString,
  NerResult,
  NerEntity,
  FileTagVO,
  TagDict,
} from '@/types';
import { NerEntityType, NerEntityTypeLabel, LayerColors } from '@/types';
import {
  scanFile,
  getNerResult,
} from '@/services/analyze';
import {
  fetchFileTagsByStringId,
  addFileTags as addFileTagsService,
  removeFileTag as removeFileTagService,
  fetchEnabledTags,
} from '@/services/tag';
import { parseFileIdToNumber } from '@/mock/tag';
import {
  yaraSeverityColor,
  yaraSeverityText,
} from '@/mock/yara';
import { nerEntityTypeColor } from '@/mock/ner';
import {
  formatDateTime,
  formatFileSize,
  copyToClipboard,
} from '@/utils';
import { FileStatus, SensitivityLabel } from '@/types';
import FileIcon from '@/components/common/FileIcon';
import { fileTypeLabel, fileTypeColor } from '@/utils/fileType';
import styles from '../FileList.module.less';

const { Text, Paragraph } = Typography;

interface FileDetailDrawerProps {
  open: boolean;
  file: FileInfo | null;
  onClose: () => void;
}

/** 状态标签映射 */
const statusMap: Record<FileStatus, { color: string; text: string }> = {
  [FileStatus.PENDING]: { color: 'default', text: '待处理' },
  [FileStatus.PROCESSING]: { color: 'processing', text: '处理中' },
  [FileStatus.COMPLETED]: { color: 'success', text: '已完成' },
  [FileStatus.FAILED]: { color: 'error', text: '失败' },
};

/** 文件详情抽屉组件 */
const FileDetailDrawer: React.FC<FileDetailDrawerProps> = ({
  open,
  file,
  onClose,
}) => {
  const [activeTab, setActiveTab] = useState('basic');

  // YARA 扫描状态
  const [yaraLoading, setYaraLoading] = useState(false);
  const [yaraResult, setYaraResult] = useState<YaraScanResult | null>(null);

  // NER 状态
  const [nerLoading, setNerLoading] = useState(false);
  const [nerResult, setNerResult] = useState<NerResult | null>(null);

  // 文件标签状态
  const [fileTags, setFileTags] = useState<FileTagVO[]>([]);
  const [tagLoading, setTagLoading] = useState(false);
  const [availableTags, setAvailableTags] = useState<TagDict[]>([]);
  const [selectedNewTagIds, setSelectedNewTagIds] = useState<number[]>([]);

  /** 加载文件标签 */
  const loadFileTags = useCallback(async (fileId: string) => {
    setTagLoading(true);
    try {
      const tags = await fetchFileTagsByStringId(fileId);
      setFileTags(tags);
    } catch {
      // 忽略加载失败
    } finally {
      setTagLoading(false);
    }
  }, []);

  /** 加载可用标签字典 */
  const loadAvailableTags = useCallback(async () => {
    try {
      const tags = await fetchEnabledTags();
      setAvailableTags(tags);
    } catch {
      // 忽略加载失败
    }
  }, []);

  /** 文件变化时重置状态 */
  useEffect(() => {
    if (file) {
      setYaraResult(null);
      setNerResult(null);
      setActiveTab('basic');
      setSelectedNewTagIds([]);
      loadFileTags(file.id);
    } else {
      setFileTags([]);
      setSelectedNewTagIds([]);
    }
  }, [file?.id, loadFileTags]);

  /** 首次打开时加载可用标签 */
  useEffect(() => {
    if (open && availableTags.length === 0) {
      loadAvailableTags();
    }
  }, [open, availableTags.length, loadAvailableTags]);

  /** 添加标签（手动打标） */
  const handleAddTags = useCallback(async () => {
    if (!file || selectedNewTagIds.length === 0) return;
    const numId = parseFileIdToNumber(file.id);
    try {
      await addFileTagsService(numId, selectedNewTagIds);
      message.success(`已添加 ${selectedNewTagIds.length} 个标签`);
      setSelectedNewTagIds([]);
      await loadFileTags(file.id);
    } catch {
      message.error('打标失败');
    }
  }, [file, selectedNewTagIds, loadFileTags]);

  /** 取消标签 */
  const handleRemoveTag = useCallback(
    async (tagId: number) => {
      if (!file) return;
      const numId = parseFileIdToNumber(file.id);
      try {
        await removeFileTagService(numId, tagId);
        message.success('已取消标签');
        await loadFileTags(file.id);
      } catch {
        message.error('取消标签失败');
      }
    },
    [file, loadFileTags],
  );

  /** 触发 YARA 扫描 */
  const handleYaraScan = async () => {
    if (!file) return;
    setYaraLoading(true);
    try {
      const res = await scanFile(file.id, file.originalName);
      if (res.code === 200 || res.code === 0) {
        setYaraResult(res.data);
        message.success(
          `扫描完成：命中 ${res.data.matchedRules} / ${res.data.totalRules} 条规则`,
        );
      } else {
        message.error(res.message || 'YARA 扫描失败');
      }
    } catch {
      message.error('YARA 扫描失败');
    } finally {
      setYaraLoading(false);
    }
  };

  /** 获取 NER 结果 */
  const handleFetchNer = async () => {
    if (!file) return;
    setNerLoading(true);
    try {
      const res = await getNerResult(file.id, file.originalName);
      if (res.code === 200 || res.code === 0) {
        setNerResult(res.data);
      } else {
        message.error(res.message || 'NER 识别失败');
      }
    } catch {
      message.error('NER 识别失败');
    } finally {
      setNerLoading(false);
    }
  };

  /** 复制文本 */
  const handleCopy = async (text: string, label: string) => {
    const ok = await copyToClipboard(text);
    if (ok) message.success(`已复制${label}`);
    else message.error('复制失败');
  };

  /** YARA 匹配字符串表格列 */
  const matchStringColumns: ColumnsType<YaraMatchString> = [
    {
      title: '标识',
      dataIndex: 'identifier',
      key: 'identifier',
      width: 80,
      render: (v: string) => (
        <code className={styles.matchedString}>{v || '-'}</code>
      ),
    },
    {
      title: '匹配字符串',
      dataIndex: 'value',
      key: 'value',
      ellipsis: true,
      render: (v: string) => (
        <code className={styles.matchedString}>{v}</code>
      ),
    },
    {
      title: '偏移',
      dataIndex: 'offset',
      key: 'offset',
      width: 100,
      render: (v: number) => `0x${v.toString(16).toUpperCase()}`,
    },
    {
      title: '长度',
      dataIndex: 'length',
      key: 'length',
      width: 80,
    },
  ];

  /** YARA 匹配规则表格列 */
  const yaraMatchColumns: ColumnsType<YaraMatchResult> = [
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      key: 'ruleName',
      width: 200,
      render: (v: string, record) => (
        <Space>
          <Tag color={yaraSeverityColor[record.severity]}>
            {yaraSeverityText[record.severity]}
          </Tag>
          <Text strong>{v}</Text>
        </Space>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '标签',
      dataIndex: 'tags',
      key: 'tags',
      width: 200,
      render: (tags: string[]) =>
        tags?.map((t) => <Tag key={t}>{t}</Tag>),
    },
    {
      title: '匹配数',
      dataIndex: 'matchedStrings',
      key: 'matchedStrings',
      width: 80,
      render: (arr: YaraMatchString[]) => arr?.length ?? 0,
    },
  ];

  /** NER 实体表格列 */
  const nerEntityColumns: ColumnsType<NerEntity> = [
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: NerEntityType) => (
        <Tag color={nerEntityTypeColor[type]}>
          {NerEntityTypeLabel[type]}
        </Tag>
      ),
    },
    {
      title: '值',
      dataIndex: 'value',
      key: 'value',
      ellipsis: true,
      render: (v: string, record) => (
        <Space>
          <code className={styles.nerEntityValue}>{v}</code>
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined />}
            onClick={() => handleCopy(v, '实体值')}
          />
          {record.normalized && record.normalized !== v && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              → {record.normalized}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      key: 'confidence',
      width: 120,
      render: (c: number) => (
        <Badge
          status={
            c >= 0.9 ? 'success' : c >= 0.7 ? 'processing' : 'warning'
          }
          text={`${(c * 100).toFixed(0)}%`}
        />
      ),
    },
    {
      title: '位置',
      key: 'position',
      width: 120,
      render: (_, record) => `${record.start} - ${record.end}`,
    },
  ];

  /** 渲染基本信息 Tab */
  const renderBasicTab = () => {
    if (!file) return null;
    return (
      <div>
        <div className={styles.metaGrid}>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>文件 ID</span>
            <span className={styles.metaValue}>{file.id}</span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>原始文件名</span>
            <span className={styles.metaValue}>{file.originalName}</span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>存储名称</span>
            <span className={styles.metaValue}>{file.name}</span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>文件大小</span>
            <span className={styles.metaValue}>
              {formatFileSize(file.size)}
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>文件类型</span>
            <span className={styles.metaValue}>
              <Tag color={fileTypeColor[file.type]}>
                {fileTypeLabel[file.type]}
              </Tag>
              <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                {file.mimeType}
              </Text>
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>状态</span>
            <span className={styles.metaValue}>
              <Tag color={statusMap[file.status].color}>
                {statusMap[file.status].text}
              </Tag>
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>敏感等级</span>
            <span className={styles.metaValue}>
              <Tag color="volcano">
                {file.sensitivity
                  ? `${file.sensitivity} - ${SensitivityLabel[file.sensitivity]}`
                  : '-'}
              </Tag>
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>上传者</span>
            <span className={styles.metaValue}>{file.uploaderName}</span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>关联目标</span>
            <span className={styles.metaValue}>
              {file.targetName ?? '-'}
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>是否公开</span>
            <span className={styles.metaValue}>
              {file.isPublic ? '是' : '否'}
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>上传时间</span>
            <span className={styles.metaValue}>
              {formatDateTime(file.createTime)}
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>更新时间</span>
            <span className={styles.metaValue}>
              {formatDateTime(file.updateTime)}
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>解析状态</span>
            <span className={styles.metaValue}>
              {file.parseStatus && (
                <Tag color={statusMap[file.parseStatus]?.color}>
                  {statusMap[file.parseStatus]?.text ?? '-'}
                </Tag>
              )}
            </span>
          </div>
          <div className={styles.metaItem}>
            <span className={styles.metaLabel}>解析完成时间</span>
            <span className={styles.metaValue}>
              {file.parsedAt ? formatDateTime(file.parsedAt) : '-'}
            </span>
          </div>
        </div>

        {/* 哈希信息 */}
        <Descriptions
          title="哈希信息"
          bordered
          column={1}
          size="small"
          style={{ marginBottom: 16 }}
        >
          <Descriptions.Item label="MD5">
            <Space>
              <code className={styles.hashValue}>{file.hash}</code>
              <Button
                type="text"
                size="small"
                icon={<CopyOutlined />}
                onClick={() => handleCopy(file.hash, 'MD5')}
              />
            </Space>
          </Descriptions.Item>
          {file.sm3 && (
            <Descriptions.Item label="SM3（国密）">
              <Space>
                <code className={styles.hashValue}>{file.sm3}</code>
                <Button
                  type="text"
                  size="small"
                  icon={<CopyOutlined />}
                  onClick={() => handleCopy(file.sm3 as string, 'SM3')}
                />
              </Space>
            </Descriptions.Item>
          )}
          <Descriptions.Item label="存储路径">
            <code className={styles.hashValue}>{file.path}</code>
          </Descriptions.Item>
        </Descriptions>

        {/* 标签与描述 */}
        <Descriptions title="标签与描述" bordered column={1} size="small">
          <Descriptions.Item label="标签">
            {file.tags?.length > 0 ? (
              <Space wrap>
                {file.tags.map((tag) => (
                  <Tag key={tag} color="blue">
                    {tag}
                  </Tag>
                ))}
              </Space>
            ) : (
              <Text type="secondary">无</Text>
            )}
          </Descriptions.Item>
          {file.description && (
            <Descriptions.Item label="描述">
              <Paragraph style={{ margin: 0 }}>{file.description}</Paragraph>
            </Descriptions.Item>
          )}
        </Descriptions>

        {/* 文件标签管理（结构化标签字典） */}
        <Descriptions
          title="文件标签"
          bordered
          column={1}
          size="small"
          style={{ marginTop: 16 }}
        >
          <Descriptions.Item label="已有标签">
            {tagLoading ? (
              <Spin size="small" />
            ) : fileTags.length > 0 ? (
              <Space wrap data-testid="detail-file-tags">
                {fileTags.map((ft) => (
                  <Tag
                    key={`${ft.tagId}`}
                    color={LayerColors[ft.layer] ?? 'default'}
                    closable={ft.source === 'MANUAL'}
                    onClose={(e) => {
                      e.preventDefault();
                      handleRemoveTag(ft.tagId);
                    }}
                    style={
                      ft.source === 'AUTO'
                        ? { borderStyle: 'dashed', margin: 0 }
                        : { margin: 0 }
                    }
                  >
                    {ft.tagName}
                    <Text
                      type="secondary"
                      style={{ fontSize: 10, marginLeft: 2 }}
                    >
                      {ft.source === 'AUTO' ? '自动' : '手动'}
                    </Text>
                  </Tag>
                ))}
              </Space>
            ) : (
              <Text type="secondary">暂无标签</Text>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="添加标签">
            <Space direction="vertical" style={{ width: '100%' }} data-testid="detail-add-tags">
              <Select
                mode="multiple"
                placeholder="选择要添加的标签"
                value={selectedNewTagIds}
                onChange={setSelectedNewTagIds}
                style={{ width: '100%' }}
                options={availableTags
                  .filter(
                    (t) => !fileTags.some((ft) => ft.tagId === t.id),
                  )
                  .map((t) => ({
                    value: t.id,
                    label: `${t.tagName} (${t.layer})`,
                  }))}
              />
              <Button
                type="primary"
                size="small"
                disabled={selectedNewTagIds.length === 0}
                onClick={handleAddTags}
              >
                确认打标
              </Button>
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </div>
    );
  };

  /** 渲染 YARA 扫描 Tab */
  const renderYaraTab = () => (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          icon={<ScanOutlined />}
          loading={yaraLoading}
          onClick={handleYaraScan}
        >
          触发 YARA 扫描
        </Button>
        {yaraResult && (
          <Button icon={<ReloadOutlined />} onClick={handleYaraScan}>
            重新扫描
          </Button>
        )}
      </Space>

      {yaraLoading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin tip="YARA 扫描中..." />
        </div>
      ) : yaraResult ? (
        <div className={styles.scanResult}>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={6}>
              <Statistic title="规则总数" value={yaraResult.totalRules} />
            </Col>
            <Col span={6}>
              <Statistic
                title="命中规则"
                value={yaraResult.matchedRules}
                valueStyle={{ color: '#f5222d' }}
                prefix={<WarningOutlined />}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="扫描耗时"
                value={yaraResult.costMs}
                suffix="ms"
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="扫描时间"
                value={formatDateTime(yaraResult.scannedAt)}
                valueStyle={{ fontSize: 13 }}
              />
            </Col>
          </Row>

          {yaraResult.matches.length > 0 ? (
            <Table
              title={() => (
                <Space>
                  <FileSearchOutlined />
                  <span>命中规则详情（{yaraResult.matches.length}）</span>
                </Space>
              )}
              columns={yaraMatchColumns}
              dataSource={yaraResult.matches}
              rowKey="ruleId"
              size="small"
              pagination={false}
              expandable={{
                expandedRowRender: (record) => (
                  <Table
                    columns={matchStringColumns}
                    dataSource={record.matchedStrings}
                    rowKey={(r) => `${r.identifier}-${r.offset}`}
                    size="small"
                    pagination={false}
                  />
                ),
              }}
            />
          ) : (
            <Empty description="未命中任何 YARA 规则" />
          )}
        </div>
      ) : (
        <div className={styles.emptyTab}>
          <Empty description="点击「触发 YARA 扫描」开始检测恶意特征" />
        </div>
      )}
    </div>
  );

  /** 渲染 NER Tab */
  const renderNerTab = () => (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          icon={<ScanOutlined />}
          loading={nerLoading}
          onClick={handleFetchNer}
        >
          获取 NER 实体
        </Button>
        {nerResult && (
          <Button icon={<ReloadOutlined />} onClick={handleFetchNer}>
            重新识别
          </Button>
        )}
      </Space>

      {nerLoading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin tip="NER 实体识别中..." />
        </div>
      ) : nerResult ? (
        <div className={styles.scanResult}>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={6}>
              <Statistic
                title="实体总数"
                value={nerResult.totalEntities}
                valueStyle={{ color: '#1890ff' }}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="类型数"
                value={nerResult.typeDistribution.length}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="文本长度"
                value={nerResult.textLength}
                suffix="字符"
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="处理耗时"
                value={nerResult.costMs}
                suffix="ms"
              />
            </Col>
          </Row>

          {/* 类型分布 */}
          <Space wrap style={{ marginBottom: 16 }}>
            {nerResult.typeDistribution.map((d) => (
              <Tag
                key={d.type}
                color={nerEntityTypeColor[d.type]}
              >
                {NerEntityTypeLabel[d.type]}: {d.count}
              </Tag>
            ))}
          </Space>

          <Table
            columns={nerEntityColumns}
            dataSource={nerResult.entities}
            rowKey="id"
            size="small"
            pagination={{ pageSize: 10, showSizeChanger: false }}
          />
        </div>
      ) : (
        <div className={styles.emptyTab}>
          <Empty description="点击「获取 NER 实体」提取文件中的命名实体" />
        </div>
      )}
    </div>
  );

  return (
    <Drawer
      title={
        file && (
          <Space>
            <FileIcon type={file.type} size={20} />
            <span>{file.originalName}</span>
          </Space>
        )
      }
      open={open}
      onClose={onClose}
      width={900}
      destroyOnClose
      footer={
        <div className={styles.drawerFooter}>
          <Button onClick={onClose}>关闭</Button>
        </div>
      }
    >
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'basic',
            label: '基本信息',
            children: renderBasicTab(),
          },
          {
            key: 'yara',
            label: (
              <span>
                <ScanOutlined /> YARA 扫描
              </span>
            ),
            children: renderYaraTab(),
          },
          {
            key: 'ner',
            label: (
              <span>
                <FileSearchOutlined /> NER 实体
              </span>
            ),
            children: renderNerTab(),
          },
        ]}
      />
    </Drawer>
  );
};

export default FileDetailDrawer;
