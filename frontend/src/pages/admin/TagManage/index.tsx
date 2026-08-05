/**
 * 标签管理页面
 * - 左侧层级筛选面板（L1-L6 + 全部）
 * - 右侧 ProTable：tagCode / tagName / layer / category / valueType / enabled / 操作
 * - 新增/编辑 Modal：tagCode / tagName / layer / category / valueType / applicableObject / identifyRule / isMulti / parentCode / enabled / description
 * - 启用/禁用：Switch 调用 toggleTag
 * - 删除：Popconfirm 调用 deleteTag
 * - 层级颜色：L1 blue / L2 green / L3 orange / L4 purple / L5 red / L6 cyan
 */
import React, { useMemo, useRef, useState, useCallback } from 'react';
import {
  Card,
  Typography,
  Button,
  Space,
  Tag,
  Popconfirm,
  message,
  Modal,
  Form,
  Input,
  Select,
  Switch,
  Radio,
  Row,
  Col,
} from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import {
  fetchTags,
  createTag,
  updateTag,
  toggleTag,
  deleteTag,
} from '@/services';
import type { TagDict, TagDictPayload } from '@/types';
import {
  LayerLabels,
  LayerColors,
  ValueTypeLabels,
  ApplicableObjectLabels,
} from '@/types';
import { formatDateTime } from '@/utils';

const { Title, Text } = Typography;

/** 全部层级标识 */
const ALL_LAYERS = 'ALL';

/** 表单值类型 */
interface TagFormValues {
  tagCode: string;
  tagName: string;
  layer: string;
  category: string;
  valueType: string;
  applicableObject: string;
  identifyRule?: string;
  isMulti: boolean;
  parentCode?: string;
  enabled: boolean;
  description?: string;
}

/** 把实体转为表单值 */
function toFormValues(tag: TagDict): TagFormValues {
  return {
    tagCode: tag.tagCode,
    tagName: tag.tagName,
    layer: tag.layer,
    category: tag.category,
    valueType: tag.valueType,
    applicableObject: tag.applicableObject,
    identifyRule: tag.identifyRule,
    isMulti: tag.isMulti === 1,
    parentCode: tag.parentCode,
    enabled: tag.enabled === 1,
    description: tag.description,
  };
}

/** 把表单值转为提交负载 */
function toPayload(values: TagFormValues): TagDictPayload {
  return {
    tagCode: values.tagCode.trim(),
    tagName: values.tagName.trim(),
    layer: values.layer,
    category: values.category,
    valueType: values.valueType,
    applicableObject: values.applicableObject,
    identifyRule: values.identifyRule?.trim() || undefined,
    isMulti: values.isMulti ? 1 : 0,
    parentCode: values.parentCode?.trim() || undefined,
    enabled: values.enabled ? 1 : 0,
    description: values.description?.trim() || undefined,
  };
}

const TagManagePage: React.FC = () => {
  const actionRef = useRef<ActionType>(null);
  const [layerFilter, setLayerFilter] = useState<string>(ALL_LAYERS);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<TagDict | null>(null);
  const [submitting, setSubmitting] = useState(false);
  /** 搜索关键字（在 ProTable request 中即时读取） */
  const [keyword, setKeyword] = useState<string>('');
  /** 强制刷新 token：用于让 ProTable 在搜索/筛选变化时重新拉取 */
  const [reloadToken, setReloadToken] = useState<number>(0);
  const [form] = Form.useForm<TagFormValues>();

  /** 层级选项（L1-L6 + 全部） */
  const layerOptions = useMemo(() => {
    return [
      { value: ALL_LAYERS, label: '全部' },
      ...Object.keys(LayerLabels).map((layer) => ({
        value: layer,
        label: LayerLabels[layer],
      })),
    ];
  }, []);

  /** 值类型选项 */
  const valueTypeOptions = useMemo(
    () =>
      Object.keys(ValueTypeLabels).map((v) => ({
        label: ValueTypeLabels[v],
        value: v,
      })),
    [],
  );

  /** 适用对象选项 */
  const applicableObjectOptions = useMemo(
    () =>
      Object.keys(ApplicableObjectLabels).map((o) => ({
        label: ApplicableObjectLabels[o],
        value: o,
      })),
    [],
  );

  /** 分类选项 */
  const categoryOptions = useMemo(
    () => [
      { label: '文件', value: 'FILE' },
      { label: '业务流程', value: 'PROCESS' },
      { label: '实体', value: 'ENTITY' },
      { label: '场景', value: 'SCENE' },
      { label: '情报', value: 'INTEL' },
      { label: '合规', value: 'COMPLIANCE' },
      { label: '其他', value: 'OTHER' },
    ],
    [],
  );

  /** 触发表格重新拉取：通过 params 变化让 ProTable 自动 refetch */
  const triggerReload = useCallback(() => {
    setReloadToken((n) => n + 1);
  }, []);

  /** 打开新增 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      layer: layerFilter !== ALL_LAYERS ? layerFilter : 'L1',
      valueType: 'ENUM',
      applicableObject: 'FILE',
      isMulti: false,
      enabled: true,
      category: 'FILE',
    });
    setModalOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: TagDict) => {
    setEditing(record);
    form.setFieldsValue(toFormValues(record));
    setModalOpen(true);
  };

  /** 提交表单 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload = toPayload(values);
      if (editing) {
        await updateTag(editing.id, payload);
        message.success('标签已更新');
      } else {
        await createTag(payload);
        message.success('标签已创建');
      }
      setModalOpen(false);
      triggerReload();
    } catch {
      // 校验失败
    } finally {
      setSubmitting(false);
    }
  };

  /** 切换启用状态 */
  const handleToggle = async (record: TagDict) => {
    try {
      await toggleTag(record.id);
      message.success(`标签已${record.enabled === 1 ? '禁用' : '启用'}`);
      triggerReload();
    } catch {
      message.error('切换状态失败');
    }
  };

  /** 删除 */
  const handleDelete = async (record: TagDict) => {
    try {
      await deleteTag(record.id);
      message.success('标签已删除');
      triggerReload();
    } catch {
      message.error('删除失败');
    }
  };

  /** 列定义 */
  const columns = useMemo<ProColumns<TagDict>[]>(
    () => [
      {
        title: '标签编码',
        dataIndex: 'tagCode',
        key: 'tagCode',
        width: 220,
        copyable: true,
        render: (_, record) => <Text code style={{ fontSize: 12 }}>{record.tagCode}</Text>,
      },
      {
        title: '中文名',
        dataIndex: 'tagName',
        key: 'tagName',
        width: 160,
        render: (_, record) => <Text strong>{record.tagName}</Text>,
      },
      {
        title: '层级',
        dataIndex: 'layer',
        key: 'layer',
        width: 130,
        hideInSearch: true,
        render: (_, record) => (
          <Tag color={LayerColors[record.layer] ?? 'default'}>
            {LayerLabels[record.layer] ?? record.layer}
          </Tag>
        ),
      },
      {
        title: '分类',
        dataIndex: 'category',
        key: 'category',
        width: 110,
        hideInSearch: true,
        render: (_, record) => <Tag>{record.category}</Tag>,
      },
      {
        title: '值类型',
        dataIndex: 'valueType',
        key: 'valueType',
        width: 100,
        hideInSearch: true,
        render: (_, record) => (
          <Tag color="geekblue">{ValueTypeLabels[record.valueType] ?? record.valueType}</Tag>
        ),
      },
      {
        title: '适用对象',
        dataIndex: 'applicableObject',
        key: 'applicableObject',
        width: 100,
        hideInSearch: true,
        render: (_, record) => (
          <Text style={{ fontSize: 12 }}>
            {ApplicableObjectLabels[record.applicableObject] ?? record.applicableObject}
          </Text>
        ),
      },
      {
        title: '多值',
        dataIndex: 'isMulti',
        key: 'isMulti',
        width: 70,
        hideInSearch: true,
        render: (_, record) => (record.isMulti === 1 ? <Tag color="orange">多值</Tag> : <Text type="secondary">单值</Text>),
      },
      {
        title: '启用',
        dataIndex: 'enabled',
        key: 'enabled',
        width: 80,
        hideInSearch: true,
        render: (_, record) => (
          <Switch
            checked={record.enabled === 1}
            onChange={() => handleToggle(record)}
            size="small"
            aria-label={`切换 ${record.tagName} 启用状态`}
          />
        ),
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        key: 'updatedAt',
        width: 160,
        hideInSearch: true,
        render: (v: unknown) =>
          v ? <Text style={{ fontSize: 12 }}>{formatDateTime(v as string)}</Text> : <Text type="secondary">-</Text>,
      },
      {
        title: '操作',
        key: 'action',
        width: 160,
        fixed: 'right',
        hideInSearch: true,
        render: (_, record) => (
          <Space size={4}>
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Popconfirm title="确认删除该标签？" onConfirm={() => handleDelete(record)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  return (
    <div>
      <Title level={4}>标签管理</Title>

      <Row gutter={16}>
        {/* 左侧层级筛选 */}
        <Col xs={24} sm={6} md={5} lg={4} xl={3}>
          <Card size="small" title={<Space><TagsOutlined />层级筛选</Space>} style={{ marginBottom: 16 }}>
            <Radio.Group
              value={layerFilter}
              onChange={(e) => {
                setLayerFilter(e.target.value);
                triggerReload();
              }}
              style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
            >
              {layerOptions.map((opt) => (
                <Radio key={opt.value} value={opt.value} style={{ margin: 0 }}>
                  {opt.value !== ALL_LAYERS ? (
                    <Tag
                      color={LayerColors[opt.value] ?? 'default'}
                      style={{ marginLeft: 4 }}
                    >
                      {opt.label}
                    </Tag>
                  ) : (
                    opt.label
                  )}
                </Radio>
              ))}
            </Radio.Group>
          </Card>
        </Col>

        {/* 右侧标签列表 */}
        <Col xs={24} sm={18} md={19} lg={20} xl={21}>
          <Card>
            <ProTable<TagDict>
              actionRef={actionRef}
              columns={columns}
              rowKey="id"
              search={false}
              params={{ reloadToken, layerFilter, keyword }}
              request={async (params) => {
                const page = params.current ?? 1;
                const pageSize = params.pageSize ?? 10;
                const res = await fetchTags({
                  layer: layerFilter !== ALL_LAYERS ? layerFilter : undefined,
                });
                let list: TagDict[] = res.data ?? [];
                if (keyword.trim()) {
                  const kw = keyword.trim().toLowerCase();
                  list = list.filter(
                    (t: TagDict) =>
                      t.tagName.toLowerCase().includes(kw) ||
                      t.tagCode.toLowerCase().includes(kw),
                  );
                }
                const total = list.length;
                const start = (page - 1) * pageSize;
                const paged = list.slice(start, start + pageSize);
                return {
                  data: paged,
                  total,
                  success: true,
                };
              }}
              pagination={{ pageSize: 10, showSizeChanger: true }}
              toolBarRender={() => [
                <Input.Search
                  key="search"
                  placeholder="按标签名 / 编码搜索"
                  allowClear
                  onSearch={(v) => {
                    setKeyword(v);
                    triggerReload();
                  }}
                  style={{ width: 240 }}
                />,
                <Button
                  key="reload"
                  icon={<ReloadOutlined />}
                  onClick={() => triggerReload()}
                >
                  刷新
                </Button>,
                <Button
                  key="create"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={openCreate}
                >
                  新增标签
                </Button>,
              ]}
              scroll={{ x: 1400 }}
              options={{ search: false }}
            />
          </Card>
        </Col>
      </Row>

      {/* 新增/编辑 Modal */}
      <Modal
        title={editing ? '编辑标签' : '新增标签'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="tagCode"
                label="标签编码"
                rules={[
                  { required: true, message: '请输入标签编码' },
                  {
                    pattern: /^[A-Za-z0-9_.-]+$/,
                    message: '仅支持字母、数字、点号、下划线、连字符',
                  },
                ]}
                extra="格式建议：层级.分类.名称.值（如 L1.FILE.TYPE.PDF）"
              >
                <Input placeholder="L1.FILE.TYPE.PDF" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="tagName"
                label="中文名"
                rules={[{ required: true, message: '请输入中文名' }]}
              >
                <Input placeholder="如：PDF文档" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="layer"
                label="层级"
                rules={[{ required: true, message: '请选择层级' }]}
              >
                <Select
                  placeholder="选择层级"
                  options={Object.keys(LayerLabels).map((l) => ({
                    label: LayerLabels[l],
                    value: l,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="category"
                label="分类"
                rules={[{ required: true, message: '请选择分类' }]}
              >
                <Select placeholder="选择分类" options={categoryOptions} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="valueType"
                label="值类型"
                rules={[{ required: true, message: '请选择值类型' }]}
              >
                <Select placeholder="选择值类型" options={valueTypeOptions} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="applicableObject"
                label="适用对象"
                rules={[{ required: true, message: '请选择适用对象' }]}
              >
                <Select placeholder="选择适用对象" options={applicableObjectOptions} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="identifyRule" label="识别规则" extra="可选，如 ext==pdf 或 regex:CVE-\\d+">
            <Input.TextArea rows={2} placeholder="例如：ext==pdf" />
          </Form.Item>

          <Form.Item name="parentCode" label="父标签编码" extra="可选，用于构造层级关系">
            <Input placeholder="如 L2.PROC" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="isMulti" label="是否多值" valuePropName="checked">
                <Switch checkedChildren="多值" unCheckedChildren="单值" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="enabled" label="启用状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="标签用途说明" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TagManagePage;
