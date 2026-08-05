/**
 * YARA 规则管理页面
 * - YARA 规则列表
 * - 在线编辑器（textarea）
 * - 规则测试、启用/禁用
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Typography,
  Button,
  Space,
  Tag,
  Popconfirm,
  message,
  Modal,
  Input,
  Row,
  Col,
  Tooltip,
} from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  CheckOutlined,
  StopOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import {
  getAdminYaraRules,
  saveAdminYaraRule,
  toggleYaraRuleStatus,
  deleteAdminYaraRule,
  testYaraRuleSource,
} from '@/services';
import type { AdminYaraRule, YaraTestResult } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

const { TextArea } = Input;

/** 严重程度颜色 */
const severityColor: Record<AdminYaraRule['severity'], string> = {
  info: 'default',
  low: 'success',
  medium: 'processing',
  high: 'warning',
  critical: 'error',
};

const severityText: Record<AdminYaraRule['severity'], string> = {
  info: '信息',
  low: '低危',
  medium: '中危',
  high: '高危',
  critical: '严重',
};

/** 规则表单值 */
interface RuleFormValues {
  name: string;
  description: string;
  severity: AdminYaraRule['severity'];
  tags: string;
  source: string;
  enabled: boolean;
  isCustom: boolean;
}

const YaraRuleManagePage: React.FC = () => {
  const [list, setList] = useState<AdminYaraRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AdminYaraRule | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [source, setSource] = useState('');
  const [testOpen, setTestOpen] = useState(false);
  const [testResult, setTestResult] = useState<YaraTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [formValues, setFormValues] = useState<RuleFormValues>({
    name: '',
    description: '',
    severity: 'medium',
    tags: '',
    source: '',
    enabled: true,
    isCustom: true,
  });

  const load = () => {
    setLoading(true);
    getAdminYaraRules()
      .then((res) => setList(res.data))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  /** 打开新增 */
  const openCreate = () => {
    setEditing(null);
    setFormValues({
      name: '',
      description: '',
      severity: 'medium',
      tags: '',
      source: `rule New_Rule {\n  strings:\n    $a = \"sample\" ascii\n  condition:\n    $a\n}`,
      enabled: true,
      isCustom: true,
    });
    setModalOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: AdminYaraRule) => {
    setEditing(record);
    setFormValues({
      name: record.name,
      description: record.description,
      severity: record.severity,
      tags: record.tags.join(', '),
      source: record.source,
      enabled: record.enabled,
      isCustom: record.isCustom,
    });
    setModalOpen(true);
  };

  /** 提交保存 */
  const handleSubmit = async () => {
    if (!formValues.name.trim()) {
      message.warning('请输入规则名称');
      return;
    }
    if (!formValues.source.trim()) {
      message.warning('请输入规则源代码');
      return;
    }
    setSubmitting(true);
    const payload: Partial<AdminYaraRule> = {
      ...editing,
      name: formValues.name,
      description: formValues.description,
      severity: formValues.severity,
      tags: formValues.tags.split(',').map((t) => t.trim()).filter(Boolean),
      source: formValues.source,
      enabled: formValues.enabled,
      isCustom: formValues.isCustom,
      author: editing?.author ?? 'admin',
      matchCount: editing?.matchCount ?? 0,
      id: editing?.id ?? `ayr_${Date.now()}`,
    };
    await saveAdminYaraRule(payload);
    message.success(editing ? '规则已更新' : '规则已创建');
    setModalOpen(false);
    load();
    setSubmitting(false);
  };

  /** 切换启用状态 */
  const handleToggle = async (record: AdminYaraRule) => {
    await toggleYaraRuleStatus(record.id, !record.enabled);
    message.success(`规则已${record.enabled ? '禁用' : '启用'}`);
    load();
  };

  /** 删除 */
  const handleDelete = async (record: AdminYaraRule) => {
    await deleteAdminYaraRule(record.id);
    message.success('规则已删除');
    load();
  };

  /** 测试规则 */
  const handleTest = async () => {
    setTesting(true);
    try {
      const res = await testYaraRuleSource(source);
      setTestResult(res.data);
      setTestOpen(true);
    } finally {
      setTesting(false);
    }
  };

  /** 列定义 */
  const columns: ProColumns<AdminYaraRule>[] = useMemo(
    () => [
      {
        title: '规则名称',
        dataIndex: 'name',
        key: 'name',
        width: 200,
        render: (_, record) => (
          <Space>
            <CodeOutlined style={{ color: colors.primary[500] }} />
            <Text strong style={{ fontFamily: 'monospace' }}>{record.name}</Text>
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
        title: '严重程度',
        dataIndex: 'severity',
        key: 'severity',
        width: 100,
        filters: [
          { text: '严重', value: 'critical' },
          { text: '高危', value: 'high' },
          { text: '中危', value: 'medium' },
          { text: '低危', value: 'low' },
          { text: '信息', value: 'info' },
        ],
        onFilter: (val, record) => record.severity === val,
        render: (s: unknown) => (
          <Tag color={severityColor[s as AdminYaraRule['severity']]}>
            {severityText[s as AdminYaraRule['severity']]}
          </Tag>
        ),
      },
      {
        title: '标签',
        dataIndex: 'tags',
        key: 'tags',
        width: 200,
        render: (tags: unknown) => (
          <Space wrap size={4}>
            {(tags as string[]).map((t) => (
              <Tag key={t} style={{ fontSize: 11 }}>{t}</Tag>
            ))}
          </Space>
        ),
      },
      {
        title: '命中次数',
        dataIndex: 'matchCount',
        key: 'matchCount',
        width: 100,
        sorter: (a, b) => a.matchCount - b.matchCount,
        render: (v: unknown) => <Tag color="red">{v as number}</Tag>,
      },
      {
        title: '来源',
        dataIndex: 'isCustom',
        key: 'isCustom',
        width: 80,
        render: (v: unknown) =>
          v ? <Tag color="blue">自定义</Tag> : <Tag>内置</Tag>,
      },
      {
        title: '状态',
        dataIndex: 'enabled',
        key: 'enabled',
        width: 80,
        render: (v: unknown) =>
          v ? <Tag color="success">启用</Tag> : <Tag>已禁用</Tag>,
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        key: 'updateTime',
        width: 160,
        render: (v: unknown) => formatDateTime(v as string),
      },
      {
        title: '操作',
        key: 'action',
        width: 220,
        fixed: 'right',
        render: (_, record) => (
          <Space>
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => { setSource(record.source); handleTest(); }}>
              测试
            </Button>
            <Button type="link" size="small" icon={record.enabled ? <StopOutlined /> : <CheckOutlined />} onClick={() => handleToggle(record)}>
              {record.enabled ? '禁用' : '启用'}
            </Button>
            <Popconfirm title="确认删除该规则？" onConfirm={() => handleDelete(record)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [],
  );

  return (
    <div>
      <Title level={4}>YARA 规则管理</Title>
      <Card>
        <ProTable<AdminYaraRule>
          columns={columns}
          dataSource={list}
          rowKey="id"
          loading={loading}
          search={false}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          toolBarRender={() => [
            <Button key="reload" icon={<ReloadOutlined />} onClick={load}>
              刷新
            </Button>,
            <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建规则
            </Button>,
          ]}
          scroll={{ x: 1400 }}
        />
      </Card>

      {/* 规则编辑弹窗 */}
      <Modal
        title={editing ? '编辑 YARA 规则' : '新建 YARA 规则'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        width={820}
        destroyOnClose
        footer={[
          <Button key="cancel" onClick={() => setModalOpen(false)}>取消</Button>,
          <Button key="test" icon={<PlayCircleOutlined />} loading={testing} onClick={async () => {
            setTesting(true);
            try {
              const res = await testYaraRuleSource(formValues.source);
              setTestResult(res.data);
              setTestOpen(true);
            } finally { setTesting(false); }
          }}>
            测试规则
          </Button>,
          <Button key="ok" type="primary" loading={submitting} onClick={handleSubmit}>保存</Button>,
        ]}
      >
        <Row gutter={[12, 12]}>
          <Col xs={24} md={8}>
            <label style={{ fontSize: 12, color: colors.neutral[600] }}>规则名称</label>
            <Input
              value={formValues.name}
              onChange={(e) => setFormValues({ ...formValues, name: e.target.value })}
              placeholder="如：CobaltStrike_Beacon"
            />
          </Col>
          <Col xs={24} md={8}>
            <label style={{ fontSize: 12, color: colors.neutral[600] }}>严重程度</label>
            <select
              value={formValues.severity}
              onChange={(e) => setFormValues({ ...formValues, severity: e.target.value as AdminYaraRule['severity'] })}
              style={{ width: '100%', padding: 6, borderRadius: 4, border: `1px solid ${colors.neutral[300]}` }}
            >
              <option value="info">信息</option>
              <option value="low">低危</option>
              <option value="medium">中危</option>
              <option value="high">高危</option>
              <option value="critical">严重</option>
            </select>
          </Col>
          <Col xs={24} md={8}>
            <label style={{ fontSize: 12, color: colors.neutral[600] }}>标签（逗号分隔）</label>
            <Input
              value={formValues.tags}
              onChange={(e) => setFormValues({ ...formValues, tags: e.target.value })}
              placeholder="malware, downloader"
            />
          </Col>
          <Col xs={24}>
            <label style={{ fontSize: 12, color: colors.neutral[600] }}>描述</label>
            <Input
              value={formValues.description}
              onChange={(e) => setFormValues({ ...formValues, description: e.target.value })}
              placeholder="规则描述"
            />
          </Col>
          <Col xs={24}>
            <label style={{ fontSize: 12, color: colors.neutral[600] }}>规则源代码</label>
            <TextArea
              value={formValues.source}
              onChange={(e) => setFormValues({ ...formValues, source: e.target.value })}
              autoSize={{ minRows: 10, maxRows: 20 }}
              style={{ fontFamily: 'monospace', fontSize: 12 }}
            />
          </Col>
        </Row>
      </Modal>

      {/* 测试结果弹窗 */}
      <Modal
        title="YARA 规则测试结果"
        open={testOpen}
        onCancel={() => setTestOpen(false)}
        footer={<Button type="primary" onClick={() => setTestOpen(false)}>关闭</Button>}
        width={640}
      >
        {testResult && (
          <div>
            <Paragraph>
              匹配结果：
              <Tag color={testResult.matched ? 'success' : 'default'}>
                {testResult.matched ? '命中' : '未命中'}
              </Tag>
              <Tag>耗时 {testResult.costMs} ms</Tag>
            </Paragraph>
            <Paragraph type="secondary">匹配规则数：{testResult.matchedRules.length}</Paragraph>
            {testResult.matchedRules.length > 0 && (
              <Space wrap style={{ marginBottom: 8 }}>
                {testResult.matchedRules.map((r) => (
                  <Tag key={r} color="red">{r}</Tag>
                ))}
              </Space>
            )}
            <pre
              style={{
                background: colors.dark.surface,
                color: colors.dark.text,
                padding: 12,
                borderRadius: 4,
                fontSize: 12,
                whiteSpace: 'pre-wrap',
              }}
            >
              {testResult.output}
            </pre>
          </div>
        )}
      </Modal>

      {/* 隐藏的 Tooltip 占位（保留 import） */}
      <Tooltip title="" open={false}>
        <span style={{ display: 'none' }} />
      </Tooltip>
    </div>
  );
};

export default YaraRuleManagePage;
