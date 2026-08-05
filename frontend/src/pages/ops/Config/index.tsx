/**
 * D5 应用配置
 * 1. 配置列表：按类型分组，支持全局/空间级配置
 * 2. 变更管理：草稿、审批中、灰度中、已生效、已回滚
 * 3. 灰度发布：选择灰度空间，验证后晋升或回滚
 */
import React, { useState } from 'react';
import {
  Card, Tabs, Typography, Space, Button, Tag, Modal, Form, Input, Select, message, Alert,
} from 'antd';
import type { TabsProps } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import {
  ReloadOutlined, PlusOutlined, SettingOutlined, ExperimentOutlined, ArrowUpOutlined, RollbackOutlined, EyeOutlined,
} from '@ant-design/icons';
import {
  useConfigList, useConfigChanges, useConfigImpact, useConfigCanary, useConfigPromote, useConfigRollback,
} from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import {
  ConfigItem, ConfigChange, ConfigType, ConfigTypeLabel, ALL_CONFIG_TYPES, ConfigChangeStatusTag,
} from '@/types/ops';
import { formatDateTime } from '@/utils';
import StatusTag from '@/components/ops/StatusTag';
import OpsTicketButton from '@/components/ops/OpsTicketButton';

const { Title, Text, Paragraph } = Typography;

const ConfigPage: React.FC = () => {
  const { can } = useOpsPermission();
  const [activeTab, setActiveTab] = useState('list');
  const [configType, setConfigType] = useState<ConfigType | 'all'>('all');

  // 配置列表
  const [listParams, setListParams] = useState<{ page: number; pageSize: number }>({ page: 1, pageSize: 10 });
  const listQ = useConfigList(configType, listParams);
  const items = listQ.data?.data?.list ?? [];
  const listTotal = listQ.data?.data?.total ?? 0;

  // 变更管理
  const [changeParams, setChangeParams] = useState<{ page: number; pageSize: number; config_type?: ConfigType }>({ page: 1, pageSize: 10 });
  const changesQ = useConfigChanges(changeParams);
  const changes = changesQ.data?.data?.list ?? [];
  const changesTotal = changesQ.data?.data?.total ?? 0;

  // 灰度发布
  const [canaryChangeId, setCanaryChangeId] = useState<number | undefined>(undefined);
  const [canaryOpen, setCanaryOpen] = useState(false);
  const [canaryForm] = Form.useForm();
  const canaryM = useConfigCanary();
  const promoteM = useConfigPromote();
  const rollbackM = useConfigRollback();
  const impactM = useConfigImpact();
  const [impactData, setImpactData] = useState<{ affected_files: number; affected_spaces: number } | null>(null);

  /** 加载影响预览 */
  const handleLoadImpact = async (changeId: number) => {
    try {
      const res = await impactM.mutateAsync(changeId);
      setImpactData(res.data);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '加载影响失败');
    }
  };

  /** 提交灰度 */
  const handleCanary = async () => {
    try {
      const values = await canaryForm.validateFields();
      await canaryM.mutateAsync({
        changeId: canaryChangeId!,
        canary_space_ids: values.canary_space_ids,
        validation_rule: values.validation_rule,
      });
      message.success('灰度发布已启动');
      setCanaryOpen(false);
      canaryForm.resetFields();
      changesQ.refetch();
    } catch (err) {
      if (err instanceof Error) message.error(err.message);
    }
  };

  /** 晋升全量 */
  const handlePromote = async (changeId: number) => {
    Modal.confirm({
      title: '确认晋升全量？',
      content: '灰度验证通过后，将配置应用到所有目标空间。',
      onOk: async () => {
        try {
          await promoteM.mutateAsync(changeId);
          message.success('已晋升全量');
          changesQ.refetch();
        } catch (err) {
          message.error(err instanceof Error ? err.message : '操作失败');
        }
      },
    });
  };

  /** 回滚 */
  const handleRollback = async (changeId: number) => {
    Modal.confirm({
      title: '确认回滚？',
      content: '将配置回滚到上一版本。',
      type: 'error',
      onOk: async () => {
        try {
          await rollbackM.mutateAsync(changeId);
          message.success('已回滚');
          changesQ.refetch();
        } catch (err) {
          message.error(err instanceof Error ? err.message : '操作失败');
        }
      },
    });
  };

  /** 配置列表 Tab */
  const listColumns: ProColumns<ConfigItem>[] = [
    { title: '配置 ID', dataIndex: 'id', width: 80 },
    {
      title: '类型', dataIndex: 'config_type', width: 110,
      render: (_, r) => <Tag color="blue">{ConfigTypeLabel[r.config_type]}</Tag>,
    },
    { title: '配置 Key', dataIndex: 'config_key', width: 220, render: (v) => <Text code>{v as string}</Text> },
    { title: '当前值', dataIndex: 'value', width: 220, ellipsis: true },
    { title: '版本', dataIndex: 'version', width: 80, render: (v) => `v${v}` },
    {
      title: '作用域', dataIndex: 'scope_type', width: 160,
      render: (_, r) => (
        <Space>
          <Tag color={r.scope_type === 'GLOBAL' ? 'purple' : 'cyan'}>
            {r.scope_type === 'GLOBAL' ? '全局' : '空间级'}
          </Tag>
          {r.scope_type === 'TEAM_SPACE' && r.scope_space_ids.length > 0 && (
            <Text type="secondary" style={{ fontSize: 12 }}>#{r.scope_space_ids.join(',#')}</Text>
          )}
        </Space>
      ),
    },
    { title: '生效时间', dataIndex: 'effective_at', width: 160, render: (v) => formatDateTime(v as string) },
  ];
  const listTab: NonNullable<TabsProps['items']>[number] = {
    key: 'list',
    label: <Space><SettingOutlined /><span>配置列表</span></Space>,
    children: (
      <ProTable<ConfigItem>
        rowKey="id"
        columns={listColumns}
        dataSource={items}
        loading={listQ.isLoading}
        search={false}
        scroll={{ x: 1200 }}
        pagination={{
          current: listParams.page, pageSize: listParams.pageSize, total: listTotal,
          onChange: (page, pageSize) => setListParams({ page, pageSize }),
        }}
        headerTitle={<Title level={5} style={{ margin: 0 }}>应用配置</Title>}
        toolBarRender={() => [
          <Select
            key="type"
            placeholder="配置类型"
            allowClear
            style={{ width: 140 }}
            value={configType}
            onChange={(v) => { setConfigType(v ?? 'all'); setListParams({ ...listParams, page: 1 }); }}
            options={[{ value: 'all', label: '全部' }, ...ALL_CONFIG_TYPES.map((t) => ({ value: t, label: ConfigTypeLabel[t] }))]}
          />,
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => listQ.refetch()}>刷新</Button>,
          can('config:view') && (
            <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => message.info('新建配置变更请通过变更管理 Tab 发起')}>
              新建配置
            </Button>
          ),
        ]}
      />
    ),
  };

  /** 变更管理 Tab */
  const changeColumns: ProColumns<ConfigChange>[] = [
    { title: '变更 ID', dataIndex: 'id', width: 90 },
    {
      title: '类型', dataIndex: 'config_type', width: 100,
      render: (_, r) => <Tag color="blue">{ConfigTypeLabel[r.config_type]}</Tag>,
    },
    { title: '配置 Key', dataIndex: 'config_key', width: 200, render: (v) => <Text code>{v as string}</Text> },
    { title: '版本', dataIndex: 'version', width: 80, render: (v) => `v${v}` },
    { title: '旧值', dataIndex: 'old_value', width: 160, ellipsis: true },
    { title: '新值', dataIndex: 'new_value', width: 160, ellipsis: true },
    { title: '操作人', dataIndex: 'operator_name', width: 100 },
    { title: '原因', dataIndex: 'reason', width: 200, ellipsis: true },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (_, r) => {
        const tag = ConfigChangeStatusTag[r.status];
        return <StatusTag color={tag.color} text={tag.text} />;
      },
    },
    { title: '生效时间', dataIndex: 'effective_at', width: 160, render: (v) => v ? formatDateTime(v as string) : '-' },
    {
      title: '操作', width: 220, fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleLoadImpact(r.id)}
          >
            影响
          </Button>
          {r.status === 1 && can('config:view') && (
            <Button
              type="link"
              size="small"
              icon={<ExperimentOutlined />}
              onClick={() => { setCanaryChangeId(r.id); setCanaryOpen(true); }}
            >
              灰度
            </Button>
          )}
          {r.status === 2 && can('config:view') && (
            <Button type="link" size="small" icon={<ArrowUpOutlined />} onClick={() => handlePromote(r.id)}>
              晋升
            </Button>
          )}
          {(r.status === 2 || r.status === 3) && can('config:view') && (
            <Button type="link" size="small" danger icon={<RollbackOutlined />} onClick={() => handleRollback(r.id)}>
              回滚
            </Button>
          )}
        </Space>
      ),
    },
  ];
  const changeTab: NonNullable<TabsProps['items']>[number] = {
    key: 'changes',
    label: <Space><ExperimentOutlined /><span>变更管理</span></Space>,
    children: (
      <div>
        {impactData && (
          <Alert
            type="info"
            showIcon
            message={`影响预览：受影响文件 ${impactData.affected_files.toLocaleString()} 个，受影响空间 ${impactData.affected_spaces} 个`}
            style={{ marginBottom: 12 }}
            closable
            onClose={() => setImpactData(null)}
          />
        )}
        <ProTable<ConfigChange>
          rowKey="id"
          columns={changeColumns}
          dataSource={changes}
          loading={changesQ.isLoading}
          search={false}
          scroll={{ x: 1600 }}
          pagination={{
            current: changeParams.page, pageSize: changeParams.pageSize, total: changesTotal,
            onChange: (page, pageSize) => setChangeParams({ ...changeParams, page, pageSize }),
          }}
          headerTitle={<Title level={5} style={{ margin: 0 }}>配置变更管理</Title>}
          toolBarRender={() => [
            <Select
              key="type"
              placeholder="配置类型"
              allowClear
              style={{ width: 140 }}
              onChange={(v) => setChangeParams({ ...changeParams, config_type: v, page: 1 })}
              options={ALL_CONFIG_TYPES.map((t) => ({ value: t, label: ConfigTypeLabel[t] }))}
            />,
            <Button key="refresh" icon={<ReloadOutlined />} onClick={() => changesQ.refetch()}>刷新</Button>,
            can('config:view') && (
              <OpsTicketButton
                ticketType="CONFIG"
                targetRef="config:new"
                impactPreview={{}}
                buttonText="发起变更"
              />
            ),
          ]}
        />
      </div>
    ),
  };

  /** 灰度发布 Tab（说明 + 入口） */
  const canaryTab: NonNullable<TabsProps['items']>[number] = {
    key: 'canary',
    label: <Space><ExperimentOutlined /><span>灰度发布</span></Space>,
    children: (
      <Card>
        <Paragraph>
          灰度发布流程：
        </Paragraph>
        <ol style={{ paddingLeft: 20, lineHeight: 2 }}>
          <li>在"变更管理"中选择"审批中"状态的变更，点击"灰度"</li>
          <li>选择灰度目标空间（建议 1-2 个）</li>
          <li>配置验证规则（如 NER 准确率 &gt;= 95%）</li>
          <li>启动灰度发布，系统自动监控指标</li>
          <li>验证通过后点击"晋升"全量生效；异常则点击"回滚"</li>
        </ol>
        <Alert
          type="warning"
          showIcon
          message="灰度发布需通过工单审批，并要求 MFA 二次验证"
          style={{ marginTop: 12 }}
        />
        <Button
          type="primary"
          icon={<EyeOutlined />}
          style={{ marginTop: 12 }}
          onClick={() => setActiveTab('changes')}
        >
          前往变更管理
        </Button>
      </Card>
    ),
  };

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Space>
          <SettingOutlined style={{ fontSize: 20, color: '#1677ff' }} />
          <Title level={5} style={{ margin: 0 }}>应用配置</Title>
          <Text type="secondary">D5 · 解析器/模型/规则/上传/索引/重试策略配置与灰度发布</Text>
        </Space>
      </Card>

      <Card bordered={false}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[listTab, changeTab, canaryTab]}
        />
      </Card>

      <Modal
        open={canaryOpen}
        title="灰度发布"
        onCancel={() => setCanaryOpen(false)}
        onOk={handleCanary}
        confirmLoading={canaryM.isPending}
        destroyOnClose
        width={560}
      >
        <Paragraph type="secondary">
          选择灰度空间与验证规则后启动。系统会在灰度空间内应用新配置并监控关键指标。
        </Paragraph>
        <Form form={canaryForm} layout="vertical" preserve={false}>
          <Form.Item
            name="canary_space_ids"
            label="灰度空间 ID（多个用逗号分隔）"
            rules={[{ required: true, message: '请输入灰度空间 ID' }]}
          >
            <Select
              mode="tags"
              placeholder="输入空间 ID 后回车"
              tokenSeparators={[',']}
            />
          </Form.Item>
          <Form.Item name="validation_rule" label="验证规则（可选）">
            <Input placeholder="如 ner_accuracy >= 0.95" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ConfigPage;
