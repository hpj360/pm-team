/**
 * 文件列表页面（重写版）
 * - 基于 ProTable 实现高级搜索、列配置、分页
 * - 支持关键词、类型、状态、敏感等级、标签、日期范围多维筛选
 * - 行点击打开详情抽屉（含 YARA 扫描 / NER 实体）
 * - 批量删除、下载、刷新
 */

import React, { useRef, useState } from 'react';
import {
  Button,
  Space,
  Tag,
  Popconfirm,
  Typography,
  message,
  Card,
  Tooltip,
  Input,
  Select,
  DatePicker,
  Form,
  Row,
  Col,
} from 'antd';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import {
  UploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  ReloadOutlined,
  EyeOutlined,
  SearchOutlined,
  DownOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { getFileList, downloadFile } from '@/services';
import type { FileInfo, FileListParams } from '@/types';
import {
  FileType,
  FileStatus,
  SensitivityLevel,
  SensitivityLabel,
  LayerColors,
} from '@/types';
import { formatDateTime, formatFileSize } from '@/utils';
import { fileTypeLabel, fileTypeColor } from '@/utils/fileType';
import { useFile } from '@/hooks';
import FileIcon from '@/components/common/FileIcon';
import ClassificationTag from '@/components/common/ClassificationTag';
import FileDetailDrawer from './components/FileDetailDrawer';
import styles from './FileList.module.less';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

/** 文件状态映射 */
const statusMap: Record<FileStatus, { color: string; text: string }> = {
  [FileStatus.PENDING]: { color: 'default', text: '待处理' },
  [FileStatus.PROCESSING]: { color: 'processing', text: '处理中' },
  [FileStatus.COMPLETED]: { color: 'success', text: '已完成' },
  [FileStatus.FAILED]: { color: 'error', text: '失败' },
};

/** 文件列表页面 */
const FileList: React.FC = () => {
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>();
  const { deleteFile, deleteFiles } = useFile();

  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [detailOpen, setDetailOpen] = useState(false);
  const [currentFile, setCurrentFile] = useState<FileInfo | null>(null);
  const [advancedSearchOpen, setAdvancedSearchOpen] = useState(false);
  const [searchForm] = Form.useForm();

  /** 打开详情抽屉 */
  const openDetail = (file: FileInfo) => {
    setCurrentFile(file);
    setDetailOpen(true);
  };

  /** 处理单个删除 */
  const handleDelete = async (id: string) => {
    const ok = await deleteFile(id);
    if (ok) {
      actionRef.current?.reload();
    }
  };

  /** 处理批量删除 */
  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请选择要删除的文件');
      return;
    }
    const ok = await deleteFiles(selectedRowKeys as string[]);
    if (ok) {
      setSelectedRowKeys([]);
      actionRef.current?.reload();
    }
  };

  /** 处理搜索 */
  const handleSearch = () => {
    // request 函数内部会通过 searchForm.getFieldsValue() 读取最新筛选条件
    actionRef.current?.reload();
  };

  /** 重置搜索 */
  const handleReset = () => {
    searchForm.resetFields();
    actionRef.current?.reload();
  };

  /** ProTable 列定义 */
  const columns: ProColumns<FileInfo>[] = [
    {
      title: '文件名',
      dataIndex: 'originalName',
      key: 'originalName',
      ellipsis: true,
      width: 280,
      render: (_, record) => (
        <Space>
          <FileIcon type={record.type} size={18} />
          <a
            onClick={(e) => {
              e.stopPropagation();
              openDetail(record);
            }}
          >
            {record.originalName}
          </a>
        </Space>
      ),
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      valueType: 'digit',
      search: false,
      render: (_, record) => formatFileSize(record.size),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      valueType: 'select',
      valueEnum: {
        [FileType.DOCUMENT]: { text: '文档' },
        [FileType.IMAGE]: { text: '图片' },
        [FileType.VIDEO]: { text: '视频' },
        [FileType.AUDIO]: { text: '音频' },
        [FileType.ARCHIVE]: { text: '压缩包' },
        [FileType.CODE]: { text: '代码' },
        [FileType.OTHER]: { text: '其他' },
      },
      render: (_, record) => (
        <Tag color={fileTypeColor[record.type]}>
          {fileTypeLabel[record.type]}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: {
        [FileStatus.PENDING]: { text: '待处理' },
        [FileStatus.PROCESSING]: { text: '处理中' },
        [FileStatus.COMPLETED]: { text: '已完成' },
        [FileStatus.FAILED]: { text: '失败' },
      },
      render: (_, record) => (
        <Tag color={statusMap[record.status].color}>
          {statusMap[record.status].text}
        </Tag>
      ),
    },
    {
      title: '敏感等级',
      dataIndex: 'sensitivity',
      key: 'sensitivity',
      width: 110,
      valueType: 'select',
      valueEnum: {
        [SensitivityLevel.L1]: { text: 'L1 - 公开' },
        [SensitivityLevel.L2]: { text: 'L2 - 内部' },
        [SensitivityLevel.L3]: { text: 'L3 - 机密' },
        [SensitivityLevel.L4]: { text: 'L4 - 秘密' },
        [SensitivityLevel.L5]: { text: 'L5 - 绝密' },
      },
      render: (_, record) =>
        record.sensitivity ? (
          <Tag color="volcano">
            {record.sensitivity} - {SensitivityLabel[record.sensitivity]}
          </Tag>
        ) : (
          '-'
        ),
    },
    {
      title: '密级',
      dataIndex: 'classification',
      key: 'classification',
      width: 90,
      search: false,
      render: (_, record) => (
        <ClassificationTag
          classification={record.classification}
          // data-testid 便于单元测试定位
          data-testid={`classification-tag-${record.id}`}
        />
      ),
    },
    {
      title: '标签',
      dataIndex: 'tags',
      key: 'tags',
      width: 180,
      search: false,
      render: (_, record) =>
        record.tags?.length > 0 ? (
          <Space wrap size={[4, 4]}>
            {record.tags.slice(0, 3).map((tag) => (
              <Tag key={tag}>{tag}</Tag>
            ))}
            {record.tags.length > 3 && (
              <Tooltip title={record.tags.slice(3).join(', ')}>
                <Tag>+{record.tags.length - 3}</Tag>
              </Tooltip>
            )}
          </Space>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '文件标签',
      dataIndex: 'fileTags',
      key: 'fileTags',
      width: 200,
      search: false,
      render: (_, record) => {
        const fileTags = record.fileTags ?? [];
        if (fileTags.length === 0) return <Text type="secondary">-</Text>;
        const visible = fileTags.slice(0, 3);
        const overflow = fileTags.slice(3);
        return (
          <Space wrap size={[4, 4]} data-testid="file-tags-cell">
            {visible.map((ft) => (
              <Tag
                key={`${ft.tagId}`}
                color={LayerColors[ft.layer] ?? 'default'}
                style={
                  ft.source === 'AUTO'
                    ? { borderStyle: 'dashed', margin: 0 }
                    : { margin: 0 }
                }
              >
                {ft.tagName}
              </Tag>
            ))}
            {overflow.length > 0 && (
              <Tooltip
                title={overflow.map((ft) => ft.tagName).join(', ')}
              >
                <Tag data-testid="file-tags-overflow">+{overflow.length}</Tag>
              </Tooltip>
            )}
          </Space>
        );
      },
    },
    {
      title: '上传者',
      dataIndex: 'uploaderName',
      key: 'uploaderName',
      width: 100,
      search: false,
    },
    {
      title: '关联目标',
      dataIndex: 'targetName',
      key: 'targetName',
      width: 140,
      search: false,
      ellipsis: true,
      render: (_, record) => record.targetName ?? '-',
    },
    {
      title: '上传时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
      render: (_, record) => formatDateTime(record.createTime),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      search: false,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={(e) => {
              e.stopPropagation();
              openDetail(record);
            }}
          >
            详情
          </Button>
          <Button
            type="link"
            size="small"
            icon={<DownloadOutlined />}
            onClick={(e) => {
              e.stopPropagation();
              window.open(downloadFile(record.id), '_blank');
            }}
          >
            下载
          </Button>
          <Popconfirm
            title="确定删除此文件吗？"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={(e) => e.stopPropagation()}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div className={styles.pageHeader}>
        <Title level={4} style={{ margin: 0 }}>
          文件管理
        </Title>
        <Text type="secondary">
          支持多维筛选、批量操作、详情查看（含 YARA / NER）
        </Text>
      </div>

      <ProTable<FileInfo>
        actionRef={actionRef}
        columns={columns}
        rowKey="id"
        search={false}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys),
        }}
        tableAlertRender={({ selectedRowKeys: keys }) => (
          <Space>
            <span>已选择 {keys.length} 项</span>
          </Space>
        )}
        tableAlertOptionRender={() => (
          <Space>
            <Popconfirm
              title={`确定删除选中的 ${selectedRowKeys.length} 个文件吗？`}
              onConfirm={handleBatchDelete}
            >
              <Button danger size="small" icon={<DeleteOutlined />}>
                批量删除
              </Button>
            </Popconfirm>
            <Button
              size="small"
              onClick={() => setSelectedRowKeys([])}
            >
              取消选择
            </Button>
          </Space>
        )}
        request={async (params) => {
          const searchParams: FileListParams = {
            keyword: (params.keyword as string) || undefined,
            type: params.type as FileType,
            status: params.status as FileStatus,
            sensitivity: params.sensitivity as SensitivityLevel,
            page: params.current || 1,
            pageSize: params.pageSize || 20,
          };
          // 合并高级搜索表单的值
          const formValues = searchForm.getFieldsValue();
          if (formValues.tags) searchParams.tags = formValues.tags;
          if (formValues.dateRange && formValues.dateRange.length === 2) {
            searchParams.startTime = formValues.dateRange[0].toISOString();
            searchParams.endTime = formValues.dateRange[1].toISOString();
          }
          try {
            const res = await getFileList(searchParams);
            if (res.code === 200 || res.code === 0) {
              return {
                data: res.data.list,
                success: true,
                total: res.data.total,
              };
            }
            return { data: [], success: false, total: 0 };
          } catch {
            return { data: [], success: false, total: 0 };
          }
        }}
        headerTitle={
          <Space>
            <SearchOutlined />
            <span>文件列表</span>
          </Space>
        }
        toolBarRender={() => [
          <Button
            key="advanced"
            type={advancedSearchOpen ? 'primary' : 'default'}
            icon={advancedSearchOpen ? <UpOutlined /> : <DownOutlined />}
            onClick={() => setAdvancedSearchOpen(!advancedSearchOpen)}
          >
            高级搜索
          </Button>,
          <Button
            key="upload"
            type="primary"
            icon={<UploadOutlined />}
            onClick={() => navigate('/files/upload')}
          >
            上传文件
          </Button>,
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={() => actionRef.current?.reload()}
          >
            刷新
          </Button>,
        ]}
        onRow={(record) => ({
          onClick: () => openDetail(record),
          style: { cursor: 'pointer' },
        })}
        scroll={{ x: 1700 }}
      />

      {/* 高级搜索面板 */}
      {advancedSearchOpen && (
        <Card
          size="small"
          style={{ marginTop: 12 }}
          title="高级搜索"
          extra={
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                搜索
              </Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          }
        >
          <Form form={searchForm} layout="vertical">
            <Row gutter={16}>
              <Col xs={24} sm={12} lg={8}>
                <Form.Item label="关键词" name="keyword">
                  <Input
                    placeholder="文件名 / 标签 / 描述"
                    allowClear
                    prefix={<SearchOutlined />}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12} lg={8}>
                <Form.Item label="文件类型" name="type">
                  <Select
                    placeholder="选择文件类型"
                    allowClear
                    options={Object.values(FileType).map((t) => ({
                      value: t,
                      label: fileTypeLabel[t],
                    }))}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12} lg={8}>
                <Form.Item label="状态" name="status">
                  <Select
                    placeholder="选择状态"
                    allowClear
                    options={Object.values(FileStatus).map((s) => ({
                      value: s,
                      label: statusMap[s].text,
                    }))}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12} lg={8}>
                <Form.Item label="敏感等级" name="sensitivity">
                  <Select
                    placeholder="选择敏感等级"
                    allowClear
                    options={Object.values(SensitivityLevel).map((s) => ({
                      value: s,
                      label: `${s} - ${SensitivityLabel[s]}`,
                    }))}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12} lg={8}>
                <Form.Item label="上传时间" name="dateRange">
                  <RangePicker
                    showTime
                    style={{ width: '100%' }}
                    placeholder={['开始时间', '结束时间']}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12} lg={8}>
                <Form.Item label="标签" name="tags">
                  <Select
                    mode="tags"
                    placeholder="输入标签后回车"
                    allowClear
                  />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        </Card>
      )}

      {/* 文件详情抽屉 */}
      <FileDetailDrawer
        open={detailOpen}
        file={currentFile}
        onClose={() => setDetailOpen(false)}
      />
    </div>
  );
};

export default FileList;
