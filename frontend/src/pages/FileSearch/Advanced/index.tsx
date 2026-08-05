/**
 * 高级检索页
 * - 多字段组合检索：关键词 / 类型 / 状态 / 敏感等级 / 标签 / 哈希 / 时间范围 / 大小范围 / 上传者 / 关联目标
 * - 支持保存检索条件为模板，便于复用
 * - 检索结果以表格形式呈现，支持跳转详情
 */
import React, { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Select,
  DatePicker,
  Button,
  Row,
  Col,
  Table,
  Tag,
  Space,
  Typography,
  message,
  Modal,
  InputNumber,
  Divider,
  Tooltip,
  Empty,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  ReloadOutlined,
  SaveOutlined,
  StarOutlined,
  EyeOutlined,
  DownloadOutlined,
  DeleteOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { Dayjs } from 'dayjs';
import { mockFileList } from '@/mock/file';
import { mockTargetList } from '@/mock/file';
import type { FileInfo } from '@/types';
import { FileType, FileStatus, SensitivityLevel, SensitivityLabel } from '@/types';
import { formatDateTime, formatFileSize } from '@/utils';
import { fileTypeLabel, fileTypeColor } from '@/utils/fileType';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

/** 已保存的检索条件 */
interface SavedQuery {
  id: string;
  name: string;
  params: Record<string, unknown>;
  createdAt: string;
}

/** 高级搜索表单值 */
interface AdvancedSearchParams {
  keyword?: string;
  type?: FileType;
  status?: FileStatus;
  sensitivity?: SensitivityLevel;
  tags?: string[];
  hash?: string;
  minSize?: number;
  maxSize?: number;
  uploader?: string;
  targetId?: string;
  dateRange?: [Dayjs, Dayjs];
}

/** Mock 已保存的查询 */
const mockSavedQueries: SavedQuery[] = [
  {
    id: 'sq_001',
    name: '近 30 天机密文件',
    params: { sensitivity: SensitivityLevel.L3, dateRange: '30d' },
    createdAt: '2026-07-15T10:00:00Z',
  },
  {
    id: 'sq_002',
    name: 'APT29 相关样本',
    params: { keyword: 'APT29', tags: ['APT'] },
    createdAt: '2026-07-20T11:00:00Z',
  },
  {
    id: 'sq_003',
    name: '失败的解析任务',
    params: { status: FileStatus.FAILED },
    createdAt: '2026-07-22T09:00:00Z',
  },
];

const AdvancedSearchPage: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<AdvancedSearchParams>();
  const [results, setResults] = useState<FileInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [savedQueries, setSavedQueries] = useState<SavedQuery[]>(mockSavedQueries);
  const [saveModalOpen, setSaveModalOpen] = useState(false);
  const [newQueryName, setNewQueryName] = useState('');

  /** 执行检索 */
  const handleSearch = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      // 模拟异步检索
      setTimeout(() => {
        let filtered = [...mockFileList];
        if (values.keyword) {
          const kw = values.keyword.toLowerCase();
          filtered = filtered.filter(
            (f) =>
              f.originalName.toLowerCase().includes(kw) ||
              f.tags.some((t) => t.includes(values.keyword ?? '')) ||
              (f.description ?? '').toLowerCase().includes(kw),
          );
        }
        if (values.type) filtered = filtered.filter((f) => f.type === values.type);
        if (values.status) filtered = filtered.filter((f) => f.status === values.status);
        if (values.sensitivity) filtered = filtered.filter((f) => f.sensitivity === values.sensitivity);
        if (values.tags?.length) {
          filtered = filtered.filter((f) => values.tags!.some((t) => f.tags.includes(t)));
        }
        if (values.hash) {
          const h = values.hash.toLowerCase();
          filtered = filtered.filter((f) => f.hash.toLowerCase().includes(h) || (f.sm3 ?? '').toLowerCase().includes(h));
        }
        if (values.uploader) {
          const u = values.uploader.toLowerCase();
          filtered = filtered.filter((f) => f.uploaderName.toLowerCase().includes(u));
        }
        if (values.targetId) filtered = filtered.filter((f) => f.targetId === values.targetId);
        if (values.minSize !== undefined && values.minSize !== null) {
          filtered = filtered.filter((f) => f.size >= values.minSize!);
        }
        if (values.maxSize !== undefined && values.maxSize !== null) {
          filtered = filtered.filter((f) => f.size <= values.maxSize!);
        }
        // 时间范围（Mock 简化）
        if (values.dateRange && Array.isArray(values.dateRange) && values.dateRange.length === 2) {
          // 真实场景需要转换日期；此处仅做演示
        }
        setResults(filtered);
        setHasSearched(true);
        setLoading(false);
        message.success(`检索完成，匹配 ${filtered.length} 条记录`);
      }, 400);
    } catch {
      message.error('表单校验失败');
    }
  };

  /** 重置 */
  const handleReset = () => {
    form.resetFields();
    setResults([]);
    setHasSearched(false);
  };

  /** 保存检索条件 */
  const handleSaveQuery = () => {
    if (!newQueryName.trim()) {
      message.warning('请输入查询名称');
      return;
    }
    const values = form.getFieldsValue();
    const newQuery: SavedQuery = {
      id: `sq_${Date.now()}`,
      name: newQueryName,
      params: values as unknown as Record<string, unknown>,
      createdAt: new Date().toISOString(),
    };
    setSavedQueries([newQuery, ...savedQueries]);
    setNewQueryName('');
    setSaveModalOpen(false);
    message.success('检索条件已保存');
  };

  /** 加载已保存的查询 */
  const handleLoadQuery = (query: SavedQuery) => {
    form.setFieldsValue(query.params as unknown as AdvancedSearchParams);
    message.success(`已加载查询：${query.name}`);
  };

  /** 删除已保存查询 */
  const handleDeleteQuery = (id: string) => {
    setSavedQueries(savedQueries.filter((q) => q.id !== id));
    message.success('已删除');
  };

  /** 结果表列定义 */
  const columns: ColumnsType<FileInfo> = [
    {
      title: '文件名',
      dataIndex: 'originalName',
      key: 'originalName',
      width: 280,
      ellipsis: true,
      render: (v: string, record) => (
        <a onClick={() => navigate(`/files/${record.id}`)}>{v}</a>
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
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (v: FileType) => <Tag color={fileTypeColor[v]}>{fileTypeLabel[v]}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (v: FileStatus) => {
        const m: Record<FileStatus, { color: string; text: string }> = {
          [FileStatus.PENDING]: { color: 'default', text: '待处理' },
          [FileStatus.PROCESSING]: { color: 'processing', text: '处理中' },
          [FileStatus.COMPLETED]: { color: 'success', text: '已完成' },
          [FileStatus.FAILED]: { color: 'error', text: '失败' },
        };
        return <Tag color={m[v].color}>{m[v].text}</Tag>;
      },
    },
    {
      title: '敏感等级',
      dataIndex: 'sensitivity',
      key: 'sensitivity',
      width: 110,
      render: (v?: SensitivityLevel) =>
        v ? <Tag color="volcano">{v} - {SensitivityLabel[v]}</Tag> : '-',
    },
    {
      title: '标签',
      dataIndex: 'tags',
      key: 'tags',
      width: 200,
      render: (tags: string[]) => (
        <Space wrap size={[4, 4]}>
          {tags.slice(0, 3).map((t) => <Tag key={t}>{t}</Tag>)}
          {tags.length > 3 && <Tooltip title={tags.slice(3).join(', ')}><Tag>+{tags.length - 3}</Tag></Tooltip>}
        </Space>
      ),
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
      width: 170,
      render: (v: string) => formatDateTime(v),
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/files/${record.id}`)}>
            详情
          </Button>
          <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => message.success('开始下载...')}>
            下载
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: spacing[4] }}>
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <SearchOutlined style={{ fontSize: 24, color: colors.primary[500] }} />
          <Title level={4} style={{ margin: 0 }}>高级检索</Title>
        </Space>
        <Text type="secondary">支持多字段组合检索与条件保存</Text>
      </div>

      <Row gutter={16}>
        {/* 检索表单 */}
        <Col xs={24} lg={18}>
          <Card title="检索条件" size="small">
            <Form form={form} layout="vertical">
              <Row gutter={16}>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="关键词" name="keyword" tooltip="支持文件名、描述、标签模糊匹配">
                    <Input placeholder="文件名 / 描述 / 标签" allowClear prefix={<SearchOutlined />} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="文件类型" name="type">
                    <Select placeholder="选择文件类型" allowClear options={Object.values(FileType).map((t) => ({ value: t, label: fileTypeLabel[t] }))} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="状态" name="status">
                    <Select
                      placeholder="选择状态"
                      allowClear
                      options={[
                        { value: FileStatus.PENDING, label: '待处理' },
                        { value: FileStatus.PROCESSING, label: '处理中' },
                        { value: FileStatus.COMPLETED, label: '已完成' },
                        { value: FileStatus.FAILED, label: '失败' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="敏感等级" name="sensitivity">
                    <Select
                      placeholder="选择敏感等级"
                      allowClear
                      options={Object.values(SensitivityLevel).map((s) => ({ value: s, label: `${s} - ${SensitivityLabel[s]}` }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="标签（任一匹配）" name="tags">
                    <Select mode="tags" placeholder="输入标签后回车" allowClear />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="哈希值（MD5/SM3 模糊）" name="hash">
                    <Input placeholder="支持部分哈希匹配" allowClear />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="上传者" name="uploader">
                    <Input placeholder="上传者姓名" allowClear />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="关联目标" name="targetId">
                    <Select placeholder="选择关联目标" allowClear options={mockTargetList} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="文件大小范围（字节）">
                    <Space>
                      <Form.Item name="minSize" noStyle><InputNumber placeholder="最小" style={{ width: 100 }} min={0} /></Form.Item>
                      <span>~</span>
                      <Form.Item name="maxSize" noStyle><InputNumber placeholder="最大" style={{ width: 100 }} min={0} /></Form.Item>
                    </Space>
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} lg={8}>
                  <Form.Item label="上传时间范围" name="dateRange">
                    <RangePicker showTime style={{ width: '100%' }} placeholder={['开始时间', '结束时间']} />
                  </Form.Item>
                </Col>
              </Row>
              <Divider style={{ margin: '8px 0' }} />
              <Space>
                <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={handleSearch}>
                  检索
                </Button>
                <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
                <Button icon={<SaveOutlined />} onClick={() => setSaveModalOpen(true)}>保存条件</Button>
              </Space>
            </Form>
          </Card>

          {/* 检索结果 */}
          <Card
            title={<Space><HistoryOutlined /> 检索结果 {!hasSearched ? '' : `(${results.length})`}</Space>}
            size="small"
            style={{ marginTop: spacing[4] }}
          >
            {!hasSearched ? (
              <Empty description="请输入检索条件后点击「检索」按钮" />
            ) : (
              <Table
                rowKey="id"
                columns={columns}
                dataSource={results}
                loading={loading}
                size="small"
                pagination={{
                  pageSize: 10,
                  showSizeChanger: true,
                  showTotal: (total) => `共 ${total} 条`,
                }}
                scroll={{ x: 1400 }}
              />
            )}
          </Card>
        </Col>

        {/* 已保存的查询 */}
        <Col xs={24} lg={6}>
          <Card title={<Space><StarOutlined /> 已保存的查询</Space>} size="small">
            {savedQueries.length === 0 ? (
              <Empty description="暂无保存的查询" />
            ) : (
              savedQueries.map((q) => (
                <Card
                  key={q.id}
                  size="small"
                  style={{ marginBottom: 8 }}
                  actions={[
                    <Tooltip title="加载查询" key="load">
                      <Button type="link" size="small" icon={<SearchOutlined />} onClick={() => handleLoadQuery(q)} />
                    </Tooltip>,
                    <Tooltip title="删除" key="del">
                      <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteQuery(q.id)} />
                    </Tooltip>,
                  ]}
                >
                  <Text strong>{q.name}</Text>
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>
                    {formatDateTime(q.createdAt)}
                  </div>
                  <div style={{ fontSize: 12, color: '#595959', marginTop: 4 }}>
                    {Object.entries(q.params).slice(0, 3).map(([k, v]) => (
                      <Tag key={k} style={{ marginBottom: 4 }}>{k}: {String(v).slice(0, 20)}</Tag>
                    ))}
                  </div>
                </Card>
              ))
            )}
          </Card>
        </Col>
      </Row>

      {/* 保存查询弹窗 */}
      <Modal
        title="保存检索条件"
        open={saveModalOpen}
        onCancel={() => setSaveModalOpen(false)}
        onOk={handleSaveQuery}
        okText="保存"
        cancelText="取消"
      >
        <Input
          placeholder="请输入查询名称（如：近 30 天机密文件）"
          value={newQueryName}
          onChange={(e) => setNewQueryName(e.target.value)}
          prefix={<SaveOutlined />}
        />
      </Modal>
    </div>
  );
};

export default AdvancedSearchPage;
