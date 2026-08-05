/**
 * 批量上传页
 * - 支持多文件队列管理（增/删/排序）
 * - 批量元数据设置：敏感等级 / 关联目标 / 团队空间 / 标签
 * - 单文件元数据覆盖
 * - 批量上传进度
 */
import React, { useState, useMemo } from 'react';
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
  Table,
  Form,
  Popconfirm,
  Divider,
  Modal,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  InboxOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  EditOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  ClearOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  CheckCircleOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { mockTargetList, mockTeamSpaceOptions } from '@/mock/file';
import { formatFileSize } from '@/utils';
import {
  SensitivityLevel,
  SensitivityLabel,
  type FileUploadMetadata,
} from '@/types';
import FileIcon from '@/components/common/FileIcon';
import { detectFileTypeFromName } from '@/utils/fileType';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text } = Typography;
const { Dragger } = Upload;

/** 批量上传扩展元数据（含团队空间） */
interface BatchMetadata extends FileUploadMetadata {
  teamSpaceId?: number;
}

/** 批量上传队列项 */
interface BatchItem {
  uid: string;
  name: string;
  size: number;
  type: string;
  status: 'pending' | 'uploading' | 'completed' | 'failed' | 'paused';
  progress: number;
  speed: number; // KB/s
  metadata: BatchMetadata;
  error?: string;
}

/** 批量元数据默认值 */
const defaultBatchMeta: BatchMetadata = {
  sensitivity: SensitivityLevel.L2,
  isPublic: false,
  tags: [],
  description: '',
  targetId: undefined,
  teamSpaceId: undefined,
};

const BatchUploadPage: React.FC = () => {
  const [batchItems, setBatchItems] = useState<BatchItem[]>([]);
  const [batchMeta, setBatchMeta] = useState<BatchMetadata>(defaultBatchMeta);
  const [batchModalOpen, setBatchModalOpen] = useState(false);
  const [editingUid, setEditingUid] = useState<string | null>(null);
  const [editForm] = Form.useForm<BatchMetadata>();
  const [overallUploading, setOverallUploading] = useState(false);

  /** 敏感等级选项 */
  const sensitivityOptions = Object.values(SensitivityLevel).map((level) => ({
    value: level,
    label: `${level} - ${SensitivityLabel[level]}`,
  }));

  /** 添加文件到队列 */
  const handleAddFiles = (files: File[]) => {
    const items: BatchItem[] = files.map((file) => {
      const uid = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
      const detectedType = detectFileTypeFromName(file.name);
      return {
        uid,
        name: file.name,
        size: file.size,
        type: detectedType,
        status: 'pending',
        progress: 0,
        speed: 0,
        metadata: { ...batchMeta },
      };
    });
    setBatchItems([...batchItems, ...items]);
    message.success(`已添加 ${items.length} 个文件到队列`);
  };

  /** 移除文件 */
  const handleRemove = (uid: string) => {
    setBatchItems(batchItems.filter((i) => i.uid !== uid));
  };

  /** 清空队列 */
  const handleClear = () => {
    setBatchItems([]);
    message.success('队列已清空');
  };

  /** 上移 */
  const handleMoveUp = (uid: string) => {
    const idx = batchItems.findIndex((i) => i.uid === uid);
    if (idx <= 0) return;
    const next = [...batchItems];
    [next[idx - 1], next[idx]] = [next[idx], next[idx - 1]];
    setBatchItems(next);
  };

  /** 下移 */
  const handleMoveDown = (uid: string) => {
    const idx = batchItems.findIndex((i) => i.uid === uid);
    if (idx < 0 || idx >= batchItems.length - 1) return;
    const next = [...batchItems];
    [next[idx + 1], next[idx]] = [next[idx], next[idx + 1]];
    setBatchItems(next);
  };

  /** 应用批量元数据到全部 */
  const handleApplyBatchMeta = () => {
    setBatchItems(
      batchItems.map((item) => ({
        ...item,
        metadata: { ...batchMeta },
      })),
    );
    message.success('批量元数据已应用到全部待上传文件');
  };

  /** 编辑单文件元数据 */
  const handleEditMeta = (uid: string) => {
    const item = batchItems.find((i) => i.uid === uid);
    if (!item) return;
    setEditingUid(uid);
    editForm.setFieldsValue(item.metadata);
    setBatchModalOpen(true);
  };

  /** 保存单文件元数据 */
  const handleSaveEdit = async () => {
    try {
      const values = await editForm.validateFields();
      setBatchItems(
        batchItems.map((item) =>
          item.uid === editingUid ? { ...item, metadata: { ...values } } : item,
        ),
      );
      setBatchModalOpen(false);
      message.success('元数据已更新');
    } catch {
      // 校验失败
    }
  };

  /** 启动批量上传 */
  const handleStartUpload = async () => {
    if (batchItems.length === 0) {
      message.warning('队列为空');
      return;
    }
    setOverallUploading(true);
    const pendingItems = batchItems.filter((i) => i.status === 'pending' || i.status === 'paused');
    if (pendingItems.length === 0) {
      message.info('没有待上传文件');
      setOverallUploading(false);
      return;
    }
    message.info(`开始上传 ${pendingItems.length} 个文件`);

    // Mock 顺序上传
    for (const item of pendingItems) {
      setBatchItems((prev) =>
        prev.map((p) => (p.uid === item.uid ? { ...p, status: 'uploading', speed: 0 } : p)),
      );
      // 模拟分批进度
      for (let progress = 0; progress <= 100; progress += 10) {
        await new Promise((resolve) => setTimeout(resolve, 80));
        setBatchItems((prev) =>
          prev.map((p) =>
            p.uid === item.uid
              ? { ...p, progress, speed: Math.floor(Math.random() * 1024 * 4) + 512 }
              : p,
          ),
        );
      }
      setBatchItems((prev) =>
        prev.map((p) =>
          p.uid === item.uid ? { ...p, status: 'completed', progress: 100, speed: 0 } : p,
        ),
      );
    }
    setOverallUploading(false);
    message.success('全部文件上传完成');
  };

  /** 暂停单个 */
  const handlePause = (uid: string) => {
    setBatchItems(batchItems.map((i) => (i.uid === uid ? { ...i, status: 'paused' } : i)));
  };

  /** 恢复单个 */
  const handleResume = (uid: string) => {
    setBatchItems(batchItems.map((i) => (i.uid === uid ? { ...i, status: 'pending' } : i)));
  };

  /** 统计信息 */
  const stats = useMemo(() => {
    const total = batchItems.length;
    const completed = batchItems.filter((i) => i.status === 'completed').length;
    const uploading = batchItems.filter((i) => i.status === 'uploading').length;
    const failed = batchItems.filter((i) => i.status === 'failed').length;
    const totalSize = batchItems.reduce((sum, i) => sum + i.size, 0);
    const uploadedSize = batchItems.reduce((sum, i) => sum + (i.size * i.progress) / 100, 0);
    return { total, completed, uploading, failed, totalSize, uploadedSize };
  }, [batchItems]);

  /** 状态标签 */
  const statusTagMap: Record<BatchItem['status'], { color: string; text: string }> = {
    pending: { color: 'default', text: '待上传' },
    uploading: { color: 'processing', text: '上传中' },
    completed: { color: 'success', text: '已完成' },
    failed: { color: 'error', text: '失败' },
    paused: { color: 'warning', text: '已暂停' },
  };

  /** 表列定义 */
  const columns: ColumnsType<BatchItem> = [
    {
      title: '文件名',
      dataIndex: 'name',
      key: 'name',
      width: 280,
      ellipsis: true,
      render: (v: string, record) => (
        <Space>
          <FileIcon type={record.type as never} size={18} />
          <Text>{v}</Text>
        </Space>
      ),
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (v: number) => formatFileSize(v),
    },
    {
      title: '敏感等级',
      key: 'sensitivity',
      width: 110,
      render: (_, record) => (
        <Tag color="volcano">
          {record.metadata.sensitivity} - {SensitivityLabel[record.metadata.sensitivity ?? SensitivityLevel.L2]}
        </Tag>
      ),
    },
    {
      title: '关联目标',
      key: 'target',
      width: 140,
      render: (_, record) => {
        const t = mockTargetList.find((x) => x.value === record.metadata.targetId);
        return t ? t.label : '-';
      },
    },
    {
      title: '团队空间',
      key: 'teamSpace',
      width: 140,
      render: (_, record) => {
        const t = mockTeamSpaceOptions.find((x) => x.value === record.metadata.teamSpaceId);
        return t ? t.label : '-';
      },
    },
    {
      title: '进度',
      key: 'progress',
      width: 200,
      render: (_, record) => (
        <div>
          <Progress percent={record.progress} size="small" status={record.status === 'failed' ? 'exception' : record.status === 'completed' ? 'success' : 'active'} />
          {record.status === 'uploading' && (
            <Text type="secondary" style={{ fontSize: 11 }}>{record.speed} KB/s</Text>
          )}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (v: BatchItem['status']) => <Tag color={statusTagMap[v].color}>{statusTagMap[v].text}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="上移">
            <Button type="text" size="small" icon={<ArrowUpOutlined />} onClick={() => handleMoveUp(record.uid)} />
          </Tooltip>
          <Tooltip title="下移">
            <Button type="text" size="small" icon={<ArrowDownOutlined />} onClick={() => handleMoveDown(record.uid)} />
          </Tooltip>
          <Tooltip title="编辑元数据">
            <Button type="text" size="small" icon={<EditOutlined />} onClick={() => handleEditMeta(record.uid)} />
          </Tooltip>
          {record.status === 'uploading' && (
            <Tooltip title="暂停">
              <Button type="text" size="small" icon={<PauseCircleOutlined />} onClick={() => handlePause(record.uid)} />
            </Tooltip>
          )}
          {record.status === 'paused' && (
            <Tooltip title="恢复">
              <Button type="text" size="small" icon={<PlayCircleOutlined />} onClick={() => handleResume(record.uid)} />
            </Tooltip>
          )}
          <Popconfirm title="确定移除？" onConfirm={() => handleRemove(record.uid)}>
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <CloudUploadOutlined style={{ fontSize: 24, color: colors.primary[500] }} />
          <Title level={4} style={{ margin: 0 }}>批量上传</Title>
        </Space>
        <Space>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={handleStartUpload}
            loading={overallUploading}
            disabled={batchItems.length === 0}
          >
            开始上传
          </Button>
          <Popconfirm title="确定清空队列？" onConfirm={handleClear} disabled={batchItems.length === 0}>
            <Button icon={<ClearOutlined />} disabled={batchItems.length === 0}>清空</Button>
          </Popconfirm>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="文件总数" value={stats.total} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="已完成" value={stats.completed} prefix={<CheckCircleOutlined />} valueStyle={{ color: colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="上传中" value={stats.uploading} prefix={<CloudUploadOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="总大小"
              value={formatFileSize(stats.totalSize)}
              valueStyle={{ fontSize: 16 }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        {/* 左侧：上传区 + 批量元数据 */}
        <Col xs={24} lg={8}>
          <Card size="small" title={<Space><InboxOutlined /> 添加文件</Space>} style={{ marginBottom: spacing[4] }}>
            <Dragger
              multiple
              showUploadList={false}
              beforeUpload={(_file, fileList) => {
                handleAddFiles(fileList as unknown as File[]);
                return false;
              }}
            >
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p className="ant-upload-text">点击或拖拽文件到此处添加到上传队列</p>
              <p className="ant-upload-hint">支持单个或批量文件上传</p>
            </Dragger>
          </Card>

          <Card
            size="small"
            title={<Space><SettingOutlined /> 批量元数据</Space>}
            extra={<Button type="link" size="small" onClick={handleApplyBatchMeta}>应用到全部</Button>}
          >
            <Form layout="vertical">
              <Form.Item label="敏感等级">
                <Select
                  value={batchMeta.sensitivity}
                  options={sensitivityOptions}
                  onChange={(v) => setBatchMeta({ ...batchMeta, sensitivity: v })}
                />
              </Form.Item>
              <Form.Item label="关联目标">
                <Select
                  placeholder="选择关联目标"
                  allowClear
                  options={mockTargetList}
                  value={batchMeta.targetId}
                  onChange={(v) => setBatchMeta({ ...batchMeta, targetId: v })}
                />
              </Form.Item>
              <Form.Item label="团队空间">
                <Select
                  placeholder="选择团队空间"
                  allowClear
                  options={mockTeamSpaceOptions}
                  value={batchMeta.teamSpaceId}
                  onChange={(v) => setBatchMeta({ ...batchMeta, teamSpaceId: v })}
                />
              </Form.Item>
              <Form.Item label="是否公开">
                <Select
                  value={batchMeta.isPublic ? 'public' : 'private'}
                  onChange={(v) => setBatchMeta({ ...batchMeta, isPublic: v === 'public' })}
                  options={[
                    { value: 'private', label: '私有' },
                    { value: 'public', label: '公开' },
                  ]}
                />
              </Form.Item>
              <Form.Item label="批量标签">
                <Select
                  mode="tags"
                  placeholder="输入标签回车"
                  value={batchMeta.tags ?? []}
                  onChange={(v) => setBatchMeta({ ...batchMeta, tags: v })}
                />
              </Form.Item>
              <Form.Item label="批量描述">
                <Input.TextArea
                  rows={2}
                  placeholder="批量文件描述"
                  value={batchMeta.description ?? ''}
                  onChange={(e) => setBatchMeta({ ...batchMeta, description: e.target.value })}
                />
              </Form.Item>
            </Form>
          </Card>
        </Col>

        {/* 右侧：上传队列 */}
        <Col xs={24} lg={16}>
          <Card
            size="small"
            title={<Space><CloudUploadOutlined /> 上传队列 ({batchItems.length})</Space>}
          >
            {batchItems.length === 0 ? (
              <Empty description="队列为空，请从左侧添加文件" />
            ) : (
              <Table
                rowKey="uid"
                columns={columns}
                dataSource={batchItems}
                size="small"
                pagination={false}
                scroll={{ x: 1400 }}
              />
            )}
            <Divider />
            <Space>
              <Text type="secondary">总进度：</Text>
              <Progress
                type="circle"
                percent={stats.total === 0 ? 0 : Math.round((stats.completed / stats.total) * 100)}
                width={50}
              />
              <Text>
                {stats.completed} / {stats.total} 完成 ·
                已上传 {formatFileSize(stats.uploadedSize)} / {formatFileSize(stats.totalSize)}
              </Text>
            </Space>
          </Card>
        </Col>
      </Row>

      {/* 编辑元数据弹窗 */}
      <Modal
        title="编辑文件元数据"
        open={batchModalOpen}
        onCancel={() => setBatchModalOpen(false)}
        onOk={handleSaveEdit}
        okText="保存"
        cancelText="取消"
      >
        <Form form={editForm} layout="vertical">
          <Form.Item label="敏感等级" name="sensitivity">
            <Select options={sensitivityOptions} />
          </Form.Item>
          <Form.Item label="关联目标" name="targetId">
            <Select allowClear options={mockTargetList} />
          </Form.Item>
          <Form.Item label="团队空间" name="teamSpaceId">
            <Select allowClear options={mockTeamSpaceOptions} />
          </Form.Item>
          <Form.Item label="是否公开" name="isPublic">
            <Select
              options={[
                { value: false, label: '私有' },
                { value: true, label: '公开' },
              ]}
            />
          </Form.Item>
          <Form.Item label="标签" name="tags">
            <Select mode="tags" placeholder="输入标签回车" />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} placeholder="文件描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default BatchUploadPage;
