/**
 * 文件列表页面
 */

import React, { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Input,
  Tag,
  Popconfirm,
  Typography,
  message,
  Select,
  DatePicker,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  UploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  SearchOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useFile } from '@/hooks';
import { downloadFile } from '@/services';
import type { FileInfo } from '@/types';
import { formatDateTime, formatFileSize } from '@/utils';
import { FileStatus, FileType } from '@/types';

const { Title } = Typography;
const { RangePicker } = DatePicker;

const FileList: React.FC = () => {
  const {
    files,
    total,
    loading,
    params,
    selectedFileIds,
    fetchFiles,
    deleteFile,
    deleteFiles,
    toggleFileSelection,
    toggleSelectAll,
    setSelectedFileIds,
    setParams,
  } = useFile();

  const [keyword, setKeyword] = useState('');

  // 初始化加载数据
  useEffect(() => {
    fetchFiles();
  }, []);

  // 表格列定义
  const columns: ColumnsType<FileInfo> = [
    {
      title: '文件名',
      dataIndex: 'originalName',
      key: 'originalName',
      ellipsis: true,
      width: 250,
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (size: number) => formatFileSize(size),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type: FileType) => {
        const typeMap: Record<FileType, { color: string; text: string }> = {
          [FileType.DOCUMENT]: { color: 'blue', text: '文档' },
          [FileType.IMAGE]: { color: 'green', text: '图片' },
          [FileType.VIDEO]: { color: 'purple', text: '视频' },
          [FileType.AUDIO]: { color: 'cyan', text: '音频' },
          [FileType.ARCHIVE]: { color: 'orange', text: '压缩包' },
          [FileType.CODE]: { color: 'geekblue', text: '代码' },
          [FileType.OTHER]: { color: 'default', text: '其他' },
        };
        const config = typeMap[type] || typeMap[FileType.OTHER];
        return <Tag color={config.color}>{config.text}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: FileStatus) => {
        const statusMap: Record<FileStatus, { color: string; text: string }> = {
          [FileStatus.PENDING]: { color: 'default', text: '待处理' },
          [FileStatus.PROCESSING]: { color: 'processing', text: '处理中' },
          [FileStatus.COMPLETED]: { color: 'success', text: '已完成' },
          [FileStatus.FAILED]: { color: 'error', text: '失败' },
        };
        const config = statusMap[status];
        return <Tag color={config.color}>{config.text}</Tag>;
      },
    },
    {
      title: '标签',
      dataIndex: 'tags',
      key: 'tags',
      width: 150,
      render: (tags: string[]) =>
        tags?.slice(0, 2).map((tag) => <Tag key={tag}>{tag}</Tag>),
    },
    {
      title: '上传者',
      dataIndex: 'uploaderName',
      key: 'uploaderName',
      width: 100,
    },
    {
      title: '上传时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (time: string) => formatDateTime(time),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => {
              window.open(downloadFile(record.id), '_blank');
            }}
          >
            下载
          </Button>
          <Popconfirm
            title="确定删除此文件吗？"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // 删除单个文件
  const handleDelete = async (id: string) => {
    await deleteFile(id);
  };

  // 批量删除
  const handleBatchDelete = async () => {
    if (selectedFileIds.length === 0) {
      message.warning('请选择要删除的文件');
      return;
    }
    await deleteFiles(selectedFileIds);
  };

  // 搜索
  const handleSearch = () => {
    fetchFiles({ keyword, page: 1 });
  };

  // 表格选择配置
  const rowSelection = {
    selectedRowKeys: selectedFileIds,
    onChange: (keys: React.Key[]) => setSelectedFileIds(keys as string[]),
  };

  return (
    <div>
      <Title level={4}>文件管理</Title>

      <Card>
        {/* 搜索栏 */}
        <Space wrap style={{ marginBottom: 16 }}>
          <Input
            placeholder="搜索文件名"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={handleSearch}
            style={{ width: 200 }}
            prefix={<SearchOutlined />}
          />
          <Select
            placeholder="文件类型"
            allowClear
            style={{ width: 120 }}
            onChange={(value) => setParams({ type: value })}
          >
            <Select.Option value={FileType.DOCUMENT}>文档</Select.Option>
            <Select.Option value={FileType.IMAGE}>图片</Select.Option>
            <Select.Option value={FileType.VIDEO}>视频</Select.Option>
            <Select.Option value={FileType.AUDIO}>音频</Select.Option>
            <Select.Option value={FileType.ARCHIVE}>压缩包</Select.Option>
            <Select.Option value={FileType.CODE}>代码</Select.Option>
          </Select>
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 120 }}
            onChange={(value) => setParams({ status: value })}
          >
            <Select.Option value={FileStatus.PENDING}>待处理</Select.Option>
            <Select.Option value={FileStatus.PROCESSING}>处理中</Select.Option>
            <Select.Option value={FileStatus.COMPLETED}>已完成</Select.Option>
            <Select.Option value={FileStatus.FAILED}>失败</Select.Option>
          </Select>
          <RangePicker onChange={(dates) => {
            if (dates && dates[0] && dates[1]) {
              setParams({
                startTime: dates[0].toISOString(),
                endTime: dates[1].toISOString(),
              });
            }
          }} />
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            搜索
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => fetchFiles()}>
            刷新
          </Button>
        </Space>

        {/* 操作栏 */}
        <Space style={{ marginBottom: 16 }}>
          <Button type="primary" icon={<UploadOutlined />}>
            上传文件
          </Button>
          <Popconfirm
            title={`确定删除选中的 ${selectedFileIds.length} 个文件吗？`}
            onConfirm={handleBatchDelete}
            disabled={selectedFileIds.length === 0}
          >
            <Button
              danger
              icon={<DeleteOutlined />}
              disabled={selectedFileIds.length === 0}
            >
              批量删除
            </Button>
          </Popconfirm>
        </Space>

        {/* 文件列表 */}
        <Table
          columns={columns}
          dataSource={files}
          rowKey="id"
          loading={loading}
          rowSelection={rowSelection}
          pagination={{
            current: params.page,
            pageSize: params.pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              fetchFiles({ page, pageSize });
            },
          }}
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  );
};

export default FileList;
