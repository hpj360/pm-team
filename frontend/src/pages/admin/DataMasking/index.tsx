/**
 * 脱敏规则管理页面
 * - antd Table 展示规则列表：rule_name / pattern / replacement / classification_level / enabled
 * - 顶部操作：新增规则按钮
 * - 行操作：编辑 / 启用禁用 Switch / 删除（Popconfirm）
 * - 新增/编辑 Modal：rule_name / pattern / replacement / classification_level(Select) / enabled(Switch) / description
 * - 规则测试预览：Modal 底部「测试」按钮，输入样例文本 -> 调用 testRule -> 显示脱敏后结果
 *
 * 参考：src/pages/admin/TagManage/index.tsx（类似 CRUD 页面风格）
 */
import React, { useEffect, useMemo, useState, useCallback } from 'react';
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
  Row,
  Col,
  Table,
  Tooltip,
  Divider,
  Alert,
  Spin,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  ExperimentOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import {
  listRules,
  createRule,
  updateRule,
  deleteRule,
  toggleRule,
  testRule,
} from '@/services';
import type {
  DataMaskingRule,
  DataMaskingRulePayload,
  DataMaskingTestResult,
} from '@/types';
import {
  FileClassification,
  FileClassificationLabel,
  FileClassificationColor,
} from '@/types';
import { formatDateTime } from '@/utils';

const { Title, Text, Paragraph } = Typography;

/** 密级选项（用于 Select 与列渲染） */
const classificationOptions = Object.values(FileClassification).map((c) => ({
  label: `${c} - ${FileClassificationLabel[c]}`,
  value: c,
}));

/** 密级 Tag 渲染 */
function renderClassificationTag(level?: string) {
  if (!level) return <Text type="secondary">全部</Text>;
  const cls = level as FileClassification;
  const label = FileClassificationLabel[cls];
  const color = FileClassificationColor[cls];
  if (!label || !color) return <Text type="secondary">{level}</Text>;
  return <Tag color={color}>{label}</Tag>;
}

/** 表单值类型 */
interface RuleFormValues {
  ruleName: string;
  pattern: string;
  replacement: string;
  classificationLevel?: FileClassification;
  enabled: boolean;
  description?: string;
}

/** 把实体转为表单值 */
function toFormValues(rule: DataMaskingRule): RuleFormValues {
  return {
    ruleName: rule.ruleName,
    pattern: rule.pattern,
    replacement: rule.replacement,
    classificationLevel: rule.classificationLevel as FileClassification | undefined,
    enabled: rule.enabled,
    description: rule.description,
  };
}

/** 把表单值转为提交负载 */
function toPayload(values: RuleFormValues): DataMaskingRulePayload {
  return {
    ruleName: values.ruleName.trim(),
    pattern: values.pattern.trim(),
    replacement: values.replacement,
    classificationLevel: values.classificationLevel,
    enabled: values.enabled,
    description: values.description?.trim() || undefined,
  };
}

const DataMaskingPage: React.FC = () => {
  const [rules, setRules] = useState<DataMaskingRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [reloadToken, setReloadToken] = useState(0);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DataMaskingRule | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<RuleFormValues>();

  // 规则测试预览状态
  const [testInput, setTestInput] = useState('');
  const [testResult, setTestResult] = useState<DataMaskingTestResult | null>(null);
  const [testLoading, setTestLoading] = useState(false);

  /** 触发表格重新拉取 */
  const triggerReload = useCallback(() => {
    setReloadToken((n) => n + 1);
  }, []);

  /** 加载规则列表 */
  const loadRules = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listRules();
      if (res.code === 200 || res.code === 0) {
        setRules(res.data ?? []);
      } else {
        message.error(res.message || '加载脱敏规则失败');
        setRules([]);
      }
    } catch {
      message.error('加载脱敏规则失败');
      setRules([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRules();
  }, [loadRules, reloadToken]);

  /** 客户端关键字过滤 */
  const filteredRules = useMemo(() => {
    if (!keyword.trim()) return rules;
    const kw = keyword.trim().toLowerCase();
    return rules.filter(
      (r) =>
        r.ruleName.toLowerCase().includes(kw) ||
        r.pattern.toLowerCase().includes(kw) ||
        (r.description ?? '').toLowerCase().includes(kw),
    );
  }, [rules, keyword]);

  /** 打开新增 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      classificationLevel: FileClassification.INTERNAL,
      enabled: true,
    });
    setTestInput('');
    setTestResult(null);
    setModalOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: DataMaskingRule) => {
    setEditing(record);
    form.setFieldsValue(toFormValues(record));
    setTestInput('');
    setTestResult(null);
    setModalOpen(true);
  };

  /** 提交表单 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload = toPayload(values);
      if (editing) {
        await updateRule(editing.id, payload);
        message.success('脱敏规则已更新');
      } else {
        await createRule(payload);
        message.success('脱敏规则已创建');
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
  const handleToggle = async (record: DataMaskingRule) => {
    try {
      await toggleRule(record.id);
      message.success(`规则已${record.enabled ? '禁用' : '启用'}`);
      triggerReload();
    } catch {
      message.error('切换状态失败');
    }
  };

  /** 删除 */
  const handleDelete = async (record: DataMaskingRule) => {
    try {
      await deleteRule(record.id);
      message.success('规则已删除');
      triggerReload();
    } catch {
      message.error('删除失败');
    }
  };

  /** 规则测试预览 */
  const handleTest = async () => {
    if (!testInput.trim()) {
      message.warning('请输入样例文本');
      return;
    }
    setTestLoading(true);
    try {
      const res = await testRule(testInput, editing?.id);
      if (res.code === 200 || res.code === 0) {
        setTestResult(res.data);
      } else {
        message.error(res.message || '规则测试失败');
      }
    } catch {
      message.error('规则测试失败');
    } finally {
      setTestLoading(false);
    }
  };

  /** 列定义 */
  const columns = useMemo<ColumnsType<DataMaskingRule>>(
    () => [
      {
        title: '规则名称',
        dataIndex: 'ruleName',
        key: 'ruleName',
        width: 180,
        render: (v: string) => <Text strong>{v}</Text>,
      },
      {
        title: '匹配模式',
        dataIndex: 'pattern',
        key: 'pattern',
        ellipsis: true,
        render: (v: string) => (
          <Tooltip title={v}>
            <code style={{ fontSize: 12 }}>{v}</code>
          </Tooltip>
        ),
      },
      {
        title: '替换文本',
        dataIndex: 'replacement',
        key: 'replacement',
        width: 160,
        render: (v: string) => <code style={{ fontSize: 12 }}>{v}</code>,
      },
      {
        title: '适用密级',
        dataIndex: 'classificationLevel',
        key: 'classificationLevel',
        width: 110,
        render: (level?: string) => renderClassificationTag(level),
      },
      {
        title: '启用',
        dataIndex: 'enabled',
        key: 'enabled',
        width: 80,
        render: (_, record) => (
          <Switch
            checked={record.enabled}
            onChange={() => handleToggle(record)}
            size="small"
            aria-label={`切换 ${record.ruleName} 启用状态`}
          />
        ),
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        key: 'updatedAt',
        width: 170,
        render: (v?: string) =>
          v ? (
            <Text style={{ fontSize: 12 }}>{formatDateTime(v)}</Text>
          ) : (
            <Text type="secondary">-</Text>
          ),
      },
      {
        title: '操作',
        key: 'action',
        width: 160,
        fixed: 'right',
        render: (_, record) => (
          <Space size={4}>
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => openEdit(record)}
            >
              编辑
            </Button>
            <Popconfirm
              title="确认删除该脱敏规则？"
              onConfirm={() => handleDelete(record)}
            >
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
      <Title level={4}>脱敏规则管理</Title>
      <Paragraph type="secondary" style={{ marginBottom: 16 }}>
        配置敏感数据脱敏规则，按密级生效。规则基于正则表达式匹配，支持捕获组引用（$1 / $2 等）。
      </Paragraph>

      <Card>
        <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
          <Input.Search
            placeholder="按规则名称 / 模式 / 描述搜索"
            allowClear
            onSearch={(v) => setKeyword(v)}
            style={{ width: 320 }}
            data-testid="rule-search-input"
          />
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => triggerReload()}
              data-testid="rule-reload-btn"
            >
              刷新
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
              data-testid="rule-create-btn"
            >
              新增规则
            </Button>
          </Space>
        </Space>

        <Table<DataMaskingRule>
          columns={columns}
          dataSource={filteredRules}
          rowKey="id"
          loading={loading}
          size="small"
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
          scroll={{ x: 1100 }}
          data-testid="rule-table"
        />
      </Card>

      {/* 新增/编辑 Modal */}
      <Modal
        title={editing ? '编辑脱敏规则' : '新增脱敏规则'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        width={720}
        destroyOnClose
        footer={[
          <Button key="cancel" onClick={() => setModalOpen(false)}>
            取消
          </Button>,
          <Button
            key="test"
            icon={<ExperimentOutlined />}
            onClick={handleTest}
            loading={testLoading}
            data-testid="rule-test-btn"
          >
            测试
          </Button>,
          <Button key="ok" type="primary" loading={submitting} onClick={handleSubmit}>
            确定
          </Button>,
        ]}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="ruleName"
                label="规则名称"
                rules={[{ required: true, message: '请输入规则名称' }]}
              >
                <Input placeholder="如：手机号脱敏" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="classificationLevel"
                label="适用密级"
                extra="仅对不低于该密级的文件生效"
              >
                <Select
                  placeholder="选择适用密级"
                  options={classificationOptions}
                  allowClear
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="pattern"
            label="匹配模式（正则表达式）"
            rules={[{ required: true, message: '请输入正则匹配模式' }]}
            extra="使用 JavaScript 正则语法，支持捕获组。如：(1[3-9])\d{4}(\d{4})"
          >
            <Input.TextArea rows={2} placeholder="(1[3-9])\d{4}(\d{4})" />
          </Form.Item>

          <Form.Item
            name="replacement"
            label="替换文本"
            rules={[{ required: true, message: '请输入替换文本' }]}
            extra="支持 $1 / $2 等捕获组引用，如：$1****$2"
          >
            <Input placeholder="$1****$2" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="enabled" label="启用状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="规则用途说明" />
          </Form.Item>

          {/* 规则测试预览区 */}
          <Divider>
            <Space>
              <SafetyCertificateOutlined />
              <span>规则测试预览</span>
            </Space>
          </Divider>
          <Form.Item label="样例输入文本">
            <Input.TextArea
              rows={2}
              placeholder="输入需要测试的文本，如：联系电话 13812345678，邮箱 test@example.com"
              value={testInput}
              onChange={(e) => setTestInput(e.target.value)}
              data-testid="rule-test-input"
            />
          </Form.Item>
          <Form.Item label="脱敏结果">
            {testLoading ? (
              <Spin tip="测试中..." />
            ) : testResult ? (
              <div data-testid="rule-test-result">
                <Alert
                  type={testResult.matchCount > 0 ? 'success' : 'info'}
                  message={`命中 ${testResult.matchCount} 处，命中规则：${
                    testResult.matchedRuleNames.length > 0
                      ? testResult.matchedRuleNames.join('、')
                      : '无'
                  }`}
                  style={{ marginBottom: 8 }}
                />
                <Paragraph style={{ margin: 0 }}>
                  <Text type="secondary">输出：</Text>
                  <code data-testid="rule-test-output">{testResult.output}</code>
                </Paragraph>
              </div>
            ) : (
              <Text type="secondary">点击「测试」按钮查看脱敏结果</Text>
            )}
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DataMaskingPage;
