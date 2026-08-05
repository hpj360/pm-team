/**
 * 文件上传页面（增强版）
 * - 拖拽/点击选择多文件
 * - 元数据表单：敏感等级 / 关联目标 / 公开性 / 标签 / 描述 / 团队空间
 * - 智能上传：
 *   - 自动计算文件哈希（MD5 + SM3）
 *   - 秒传检查：命中直接完成
 *   - 小文件（<5MB）直传
 *   - 大文件（>=5MB）分片上传，支持暂停 / 恢复 / 取消
 * - 任务列表实时进度（文件级 + 分片级）
 */

import React, { useMemo, useState } from 'react';
import {
  Card,
  Upload,
  Button,
  message,
  Input,
  Select,
  Typography,
  Space,
  Tag,
  Progress,
  Tooltip,
  Empty,
  Statistic,
  Row,
  Col,
  Switch,
  Form,
  Popconfirm,
  Divider,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  InboxOutlined,
  CloudUploadOutlined,
  PlusOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  ThunderboltOutlined,
  FileTextOutlined,
  SettingOutlined,
  ClearOutlined,
} from '@ant-design/icons';
import { useUpload } from '@/hooks';
import { mockTargetList } from '@/mock/file';
import { formatFileSize } from '@/utils';
import {
  SensitivityLevel,
  SensitivityLabel,
  MULTIPART_THRESHOLD,
  type FileUploadMetadata,
  type UploadTask,
} from '@/types';
import FileIcon from '@/components/common/FileIcon';
import { detectFileTypeFromName } from '@/utils/fileType';
import styles from './FileUpload.module.less';

const { Title, Text } = Typography;
const { Dragger } = Upload;
const { TextArea } = Input;

/** 敏感等级选项 */
const sensitivityOptions = Object.values(SensitivityLevel).map((level) => ({
  value: level,
  label: `${level} - ${SensitivityLabel[level]}`,
}));

/** 文件上传页面组件 */
const FileUpload: React.FC = () => {
  const {
    uploadTasks,
    startUpload,
    pauseTask,
    resumeTask,
    cancelTask,
    removeTask,
    clearCompleted,
  } = useUpload();

  // 待上传文件列表（尚未进入上传队列）
  const [pendingFiles, setPendingFiles] = useState<UploadFile[]>([]);

  // 元数据表单
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [description, setDescription] = useState('');
  const [sensitivity, setSensitivity] = useState<SensitivityLevel>(
    SensitivityLevel.L2,
  );
  const [targetId, setTargetId] = useState<string | undefined>(undefined);
  const [isPublic, setIsPublic] = useState(false);
  const [uploading, setUploading] = useState(false);

  /** 拖拽上传配置 */
  const uploadProps = {
    name: 'file',
    multiple: true,
    fileList: pendingFiles,
    beforeUpload: (file: File) => {
      // 限制单个文件 500MB
      const isLt500M = file.size / 1024 / 1024 < 500;
      if (!isLt500M) {
        message.error(`「${file.name}」超过 500MB，请拆分后再上传`);
        return false;
      }
      setPendingFiles((prev) => [
        ...prev,
        {
          uid: `${file.name}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          name: file.name,
          status: 'done',
          size: file.size,
          type: file.type,
          originFileObj: file as UploadFile['originFileObj'],
        } as UploadFile,
      ]);
      return false; // 阻止自动上传
    },
    onRemove: (file: UploadFile) => {
      setPendingFiles((prev) => prev.filter((f) => f.uid !== file.uid));
    },
  };

  /** 添加标签 */
  const handleAddTag = () => {
    const trimmed = tagInput.trim();
    if (trimmed && !tags.includes(trimmed)) {
      setTags([...tags, trimmed]);
      setTagInput('');
    }
  };

  /** 构造元数据 */
  const buildMetadata = (): FileUploadMetadata => ({
    tags,
    description: description.trim() || undefined,
    sensitivity,
    targetId,
    isPublic,
  });

  /** 开始上传 */
  const handleStartUpload = async () => {
    if (pendingFiles.length === 0) {
      message.warning('请选择要上传的文件');
      return;
    }
    const files = pendingFiles
      .map((f) => f.originFileObj)
      .filter(
        (f): f is NonNullable<UploadFile['originFileObj']> => !!f,
      );
    if (files.length === 0) {
      message.warning('未获取到有效的文件对象');
      return;
    }
    setUploading(true);
    try {
      await startUpload(files, buildMetadata());
      setPendingFiles([]);
    } finally {
      setUploading(false);
    }
  };

  /** 清空已完成任务 */
  const handleClearCompleted = () => {
    clearCompleted();
    message.success('已清空已完成的任务');
  };

  /** 任务统计 */
  const stats = useMemo(() => {
    const total = uploadTasks.length;
    const completed = uploadTasks.filter(
      (t) => t.status === 'completed' || t.status === 'instant',
    ).length;
    const instant = uploadTasks.filter((t) => t.instantHit).length;
    const failed = uploadTasks.filter((t) => t.status === 'failed').length;
    const totalBytes = uploadTasks.reduce((sum, t) => sum + t.fileSize, 0);
    return { total, completed, instant, failed, totalBytes };
  }, [uploadTasks]);

  /** 渲染任务状态标签 */
  const renderStatusTag = (task: UploadTask) => {
    switch (task.status) {
      case 'pending':
        return <Tag>等待中</Tag>;
      case 'uploading':
        return (
          <Tag color="processing" icon={<CloudUploadOutlined spin />}>
            上传中
          </Tag>
        );
      case 'paused':
        return <Tag color="warning">已暂停</Tag>;
      case 'completed':
        return (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            已完成
          </Tag>
        );
      case 'instant':
        return (
          <Tag color="purple" icon={<ThunderboltOutlined />}>
            秒传
          </Tag>
        );
      case 'failed':
        return <Tag color="error">失败</Tag>;
      default:
        return <Tag>未知</Tag>;
    }
  };

  /** 渲染任务操作按钮 */
  const renderTaskActions = (task: UploadTask) => {
    const actions: React.ReactNode[] = [];

    if (task.isMultipart && (task.status === 'uploading' || task.status === 'paused')) {
      actions.push(
        task.status === 'uploading' ? (
          <Tooltip key="pause" title="暂停">
            <Button
              type="text"
              size="small"
              icon={<PauseCircleOutlined />}
              onClick={() => pauseTask(task.uid)}
            />
          </Tooltip>
        ) : (
          <Tooltip key="resume" title="恢复">
            <Button
              type="text"
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => resumeTask(task.uid)}
            />
          </Tooltip>
        ),
        <Popconfirm
          key="cancel"
          title="确定取消该上传任务吗？"
          onConfirm={() => cancelTask(task.uid)}
        >
          <Tooltip title="取消">
            <Button type="text" size="small" danger icon={<CloseCircleOutlined />} />
          </Tooltip>
        </Popconfirm>,
      );
    }

    if (task.status === 'completed' || task.status === 'instant' || task.status === 'failed') {
      actions.push(
        <Tooltip key="remove" title="移除">
          <Button
            type="text"
            size="small"
            icon={<DeleteOutlined />}
            onClick={() => removeTask(task.uid)}
          />
        </Tooltip>,
      );
    }

    return <Space size={4}>{actions}</Space>;
  };

  /** 渲染单个上传任务 */
  const renderTask = (task: UploadTask) => {
    const fileType = detectFileTypeFromName(task.fileName);
    const progressStatus: 'success' | 'exception' | 'active' | 'normal' =
      task.status === 'failed'
        ? 'exception'
        : task.status === 'completed' || task.status === 'instant'
          ? 'success'
          : 'active';

    return (
      <div key={task.uid} className={styles.taskItem}>
        <FileIcon type={fileType} size={32} />
        <div className={styles.taskInfo}>
          <div className={styles.taskHeader}>
            <Tooltip title={task.fileName}>
              <span className={styles.taskName}>{task.fileName}</span>
            </Tooltip>
            <Space size={8}>
              {renderStatusTag(task)}
              {task.isMultipart && (
                <Tag color="blue" className={styles.fileBadge}>
                  分片 {task.completedChunks}/{task.chunkCount}
                </Tag>
              )}
              {!task.isMultipart && task.fileSize < MULTIPART_THRESHOLD && (
                <Tag className={styles.fileBadge}>直传</Tag>
              )}
              {renderTaskActions(task)}
            </Space>
          </div>
          <div className={styles.taskMeta}>
            <span>{formatFileSize(task.fileSize)}</span>
            {task.md5 && (
              <Tooltip title={`MD5: ${task.md5}`}>
                <span>哈希已计算</span>
              </Tooltip>
            )}
            {task.error && <Text type="danger">{task.error}</Text>}
          </div>
          <Progress
            percent={task.percent}
            status={progressStatus}
            size="small"
            strokeColor={
              task.status === 'instant'
                ? '#722ed1'
                : task.status === 'completed'
                  ? '#52c41a'
                  : undefined
            }
          />
        </div>
      </div>
    );
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <Title level={4} style={{ margin: 0 }}>
          文件上传
        </Title>
        <Text type="secondary">
          支持分片上传 / 秒传 / 元数据标注
        </Text>
      </div>

      {/* 拖拽上传区 */}
      <Card className={styles.metaCard}>
        <Dragger {...uploadProps} className={styles.dragger} showUploadList={false}>
          <p className="ant-upload-drag-icon">
            <InboxOutlined className={styles.dragIcon} />
          </p>
          <p className={`ant-upload-text ${styles.dragText}`}>
            点击或拖拽文件到此区域上传
          </p>
          <p className={`ant-upload-hint ${styles.dragHint}`}>
            支持单个或批量上传，单文件最大 500MB；超过 5MB 自动启用分片上传
          </p>
        </Dragger>
      </Card>

      {/* 元数据表单 */}
      <Card
        className={styles.metaCard}
        title={
          <Space>
            <SettingOutlined />
            <span>文件元数据</span>
            <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              （应用于本次上传的所有文件）
            </Text>
          </Space>
        }
      >
        <Form layout="vertical">
          <Row gutter={16}>
            <Col xs={24} sm={12} lg={8}>
              <Form.Item label="敏感等级" required>
                <Select
                  value={sensitivity}
                  onChange={(v) => setSensitivity(v)}
                  options={sensitivityOptions}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={8}>
              <Form.Item label="关联目标">
                <Select
                  value={targetId}
                  onChange={(v) => setTargetId(v)}
                  placeholder="选择关联目标（可选）"
                  allowClear
                  options={mockTargetList}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12} lg={8}>
              <Form.Item label="是否公开">
                <Space>
                  <Switch
                    checked={isPublic}
                    onChange={(v) => setIsPublic(v)}
                    checkedChildren="公开"
                    unCheckedChildren="内部"
                  />
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    公开文件可被其他成员检索
                  </Text>
                </Space>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="标签">
            <Space wrap>
              {tags.map((tag) => (
                <Tag
                  key={tag}
                  closable
                  onClose={() => setTags(tags.filter((t) => t !== tag))}
                >
                  {tag}
                </Tag>
              ))}
              <Input
                placeholder="输入标签后回车"
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onPressEnter={handleAddTag}
                style={{ width: 160 }}
                suffix={
                  <Button
                    type="link"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={handleAddTag}
                  >
                    添加
                  </Button>
                }
              />
            </Space>
          </Form.Item>

          <Form.Item label="描述信息">
            <TextArea
              placeholder="请输入文件描述（可选），如：本次红队作业采集的钓鱼邮件样本"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={2}
              maxLength={500}
              showCount
            />
          </Form.Item>
        </Form>
      </Card>

      {/* 待上传文件列表 */}
      {pendingFiles.length > 0 && (
        <Card
          title={
            <Space>
              <FileTextOutlined />
              <span>待上传文件（{pendingFiles.length}）</span>
            </Space>
          }
          extra={
            <Space>
              <Button
                type="primary"
                size="large"
                icon={<CloudUploadOutlined />}
                loading={uploading}
                onClick={handleStartUpload}
              >
                开始上传 ({pendingFiles.length} 个文件)
              </Button>
              <Button
                icon={<ClearOutlined />}
                onClick={() => setPendingFiles([])}
                disabled={uploading}
              >
                清空
              </Button>
            </Space>
          }
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            {pendingFiles.map((file) => {
              const fileType = detectFileTypeFromName(file.name);
              return (
                <div
                  key={file.uid}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '8px 0',
                    borderBottom: '1px solid rgba(0,0,0,0.06)',
                  }}
                >
                  <FileIcon type={fileType} size={28} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div
                      style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {file.name}
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {formatFileSize(file.size || 0)}
                      {file.size && file.size >= MULTIPART_THRESHOLD && (
                        <Tag color="blue" style={{ marginLeft: 8 }}>
                          将分片上传
                        </Tag>
                      )}
                    </Text>
                  </div>
                  <Button
                    type="text"
                    size="small"
                    icon={<DeleteOutlined />}
                    onClick={() =>
                      setPendingFiles((prev) =>
                        prev.filter((f) => f.uid !== file.uid),
                      )
                    }
                  />
                </div>
              );
            })}
          </Space>
        </Card>
      )}

      {/* 上传任务列表 */}
      <Card
        className={styles.taskCard}
        title={
          <Space>
            <CloudUploadOutlined />
            <span>上传任务（{uploadTasks.length}）</span>
          </Space>
        }
        extra={
          uploadTasks.length > 0 && (
            <Button
              size="small"
              icon={<ClearOutlined />}
              onClick={handleClearCompleted}
            >
              清空已完成
            </Button>
          )
        }
      >
        {/* 任务统计 */}
        {uploadTasks.length > 0 && (
          <>
            <Row gutter={16} style={{ padding: '12px 0' }}>
              <Col xs={12} sm={6}>
                <Statistic
                  title="任务总数"
                  value={stats.total}
                  prefix={<FileTextOutlined />}
                />
              </Col>
              <Col xs={12} sm={6}>
                <Statistic
                  title="已完成"
                  value={stats.completed}
                  valueStyle={{ color: '#52c41a' }}
                  prefix={<CheckCircleOutlined />}
                />
              </Col>
              <Col xs={12} sm={6}>
                <Statistic
                  title="秒传命中"
                  value={stats.instant}
                  valueStyle={{ color: '#722ed1' }}
                  prefix={<ThunderboltOutlined />}
                />
              </Col>
              <Col xs={12} sm={6}>
                <Statistic
                  title="失败"
                  value={stats.failed}
                  valueStyle={{ color: stats.failed > 0 ? '#f5222d' : undefined }}
                />
              </Col>
            </Row>
            <Divider style={{ margin: '8px 0' }} />
          </>
        )}

        {uploadTasks.length === 0 ? (
          <div className={styles.empty}>
            <Empty description="暂无上传任务，请选择文件后开始上传" />
          </div>
        ) : (
          <div>{uploadTasks.map(renderTask)}</div>
        )}
      </Card>
    </div>
  );
};

export default FileUpload;
