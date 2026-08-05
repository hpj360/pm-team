/**
 * 工作流列表页面
 * - antd Table 展示工作流列表：名称 / 节点数 / 状态 / 创建时间 / 操作
 * - 操作：编辑（跳转设计器）/ 启用禁用 / 删除
 * - 顶部「新建工作流」按钮
 */
import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Button,
  Space,
  Table,
  Tag,
  Popconfirm,
  message,
  Switch,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  ApartmentOutlined,
} from '@ant-design/icons';
import {
  listWorkflowDefinitions,
  toggleWorkflowDefinition,
  deleteWorkflowDefinition,
} from '@/services/workflow';
import type { WorkflowDefinition } from '@/types';
import { formatDateTime } from '@/utils';

const { Title, Text } = Typography;

const WorkflowDesignerList: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState<boolean>(false);
  const [list, setList] = useState<WorkflowDefinition[]>([]);
  /** 刷新令牌：用于强制重新拉取 */
  const [reloadToken, setReloadToken] = useState<number>(0);

  /** 加载工作流列表 */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listWorkflowDefinitions();
      if (res.code === 200) {
        setList(res.data ?? []);
      } else {
        message.error(res.message || '加载失败');
      }
    } catch {
      message.error('加载工作流列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchList();
  }, [fetchList, reloadToken]);

  /** 触发刷新 */
  const triggerReload = useCallback(() => {
    setReloadToken((n) => n + 1);
  }, []);

  /** 跳转新建 */
  const handleCreate = () => {
    navigate('/admin/workflows/new');
  };

  /** 跳转编辑 */
  const handleEdit = (record: WorkflowDefinition) => {
    navigate(`/admin/workflows/${record.id}`);
  };

  /** 切换启用状态 */
  const handleToggle = async (record: WorkflowDefinition) => {
    if (!record.id) return;
    try {
      await toggleWorkflowDefinition(record.id);
      message.success(`工作流已${record.enabled ? '禁用' : '启用'}`);
      triggerReload();
    } catch {
      message.error('切换状态失败');
    }
  };

  /** 删除 */
  const handleDelete = async (record: WorkflowDefinition) => {
    if (!record.id) return;
    try {
      await deleteWorkflowDefinition(record.id);
      message.success('工作流已删除');
      triggerReload();
    } catch {
      message.error('删除失败');
    }
  };

  /** 列定义 */
  const columns: ColumnsType<WorkflowDefinition> = [
    {
      title: '工作流名称',
      dataIndex: 'name',
      key: 'name',
      width: 240,
      render: (_, record) => <Text strong>{record.name}</Text>,
    },
    {
      title: '工作流 ID',
      dataIndex: 'id',
      key: 'id',
      width: 140,
      render: (v: unknown) =>
        v ? <Text code style={{ fontSize: 12 }}>{String(v)}</Text> : <Text type="secondary">-</Text>,
    },
    {
      title: '节点数',
      key: 'nodeCount',
      width: 90,
      align: 'center',
      render: (_, record) => (
        <Tag color="blue">{record.nodes?.length ?? 0}</Tag>
      ),
    },
    {
      title: '连线数',
      key: 'edgeCount',
      width: 90,
      align: 'center',
      render: (_, record) => (
        <Tag color="geekblue">{record.edges?.length ?? 0}</Tag>
      ),
    },
    {
      title: '创建人',
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 140,
      render: (v: unknown) => (v ? <Text>{String(v)}</Text> : <Text type="secondary">-</Text>),
    },
    {
      title: '状态',
      key: 'enabled',
      width: 110,
      render: (_, record) => (
        <Switch
          checked={record.enabled}
          onChange={() => handleToggle(record)}
          checkedChildren="启用"
          unCheckedChildren="禁用"
          size="small"
          aria-label={`切换 ${record.name} 启用状态`}
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (v: unknown) =>
        v ? (
          <Text style={{ fontSize: 12 }}>{formatDateTime(v as string)}</Text>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除该工作流？"
            description="删除后无法恢复"
            onConfirm={() => handleDelete(record)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>工作流管理</Title>
      <Card>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <ApartmentOutlined />
            <Text type="secondary">管理审批工作流定义，支持线性 / 会签 / 或签流程编排</Text>
          </Space>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={triggerReload}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              新建工作流
            </Button>
          </Space>
        </div>
        <Table<WorkflowDefinition>
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          scroll={{ x: 1100 }}
          size="middle"
        />
      </Card>

      {/* 提示信息：无数据时的引导 */}
      {list.length === 0 && !loading && (
        <Card style={{ marginTop: 12 }}>
          <div style={{ textAlign: 'center', padding: 20 }}>
            <Text type="secondary">
              暂无工作流定义，点击右上角「新建工作流」开始编排审批流程
            </Text>
          </div>
        </Card>
      )}
    </div>
  );
};

export default WorkflowDesignerList;
