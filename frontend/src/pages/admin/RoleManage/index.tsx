/**
 * 角色管理页面
 * 角色列表 + 权限树（Tree）
 */
import React, { useEffect, useState } from 'react';
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
  Row,
  Col,
  Tree,
  Empty,
  Spin,
} from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type { DataNode } from 'antd/es/tree';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SafetyOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { getAdminRoles, saveAdminRole, deleteAdminRole, getAdminPermissions } from '@/services';
import type { AdminRole, AdminPermission } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 角色表单值 */
interface RoleFormValues {
  name: string;
  code: string;
  description: string;
  permissionIds: string[];
}

/** 将扁平权限列表转换为树形 */
function buildPermissionTree(permissions: AdminPermission[]): DataNode[] {
  const map = new Map<string, DataNode & { children: DataNode[] }>();
  for (const p of permissions) {
    map.set(p.id, {
      key: p.id,
      title: (
        <Space size={6}>
          <Text strong>{p.name}</Text>
          <Tag>{p.code}</Tag>
        </Space>
      ),
      children: [],
    });
  }
  const roots: DataNode[] = [];
  for (const p of permissions) {
    const node = map.get(p.id)!;
    if (p.parentId && map.has(p.parentId)) {
      map.get(p.parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

const RoleManagePage: React.FC = () => {
  const [roles, setRoles] = useState<AdminRole[]>([]);
  const [permissions, setPermissions] = useState<AdminPermission[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AdminRole | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<RoleFormValues>();

  const load = () => {
    setLoading(true);
    Promise.all([getAdminRoles(), getAdminPermissions()])
      .then(([r, p]) => {
        setRoles(r.data);
        setPermissions(p.data);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  /** 打开新增 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: AdminRole) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      code: record.code,
      description: record.description,
      permissionIds: record.permissionIds,
    });
    setModalOpen(true);
  };

  /** 提交 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload: Partial<AdminRole> = {
        ...editing,
        ...values,
        id: editing?.id ?? `r_${Date.now()}`,
      };
      await saveAdminRole(payload);
      message.success(editing ? '角色已更新' : '角色已创建');
      setModalOpen(false);
      load();
    } catch {
      // 校验失败
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除 */
  const handleDelete = async (record: AdminRole) => {
    if (record.builtin) {
      message.warning('内置角色不可删除');
      return;
    }
    await deleteAdminRole(record.id);
    message.success('角色已删除');
    load();
  };

  /** 权限树 */
  const permissionTree = buildPermissionTree(permissions);

  /** 列定义 */
  const columns: ProColumns<AdminRole>[] = [
    {
      title: '角色名称',
      dataIndex: 'name',
      key: 'name',
      width: 140,
      render: (_, record) => (
        <Space>
          <SafetyOutlined style={{ color: colors.primary[500] }} />
          <Text strong>{record.name}</Text>
        </Space>
      ),
    },
    {
      title: '角色编码',
      dataIndex: 'code',
      key: 'code',
      width: 160,
      copyable: true,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '用户数',
      dataIndex: 'userCount',
      key: 'userCount',
      width: 80,
      render: (v: unknown) => <Tag color="blue">{v as number}</Tag>,
    },
    {
      title: '类型',
      dataIndex: 'builtin',
      key: 'builtin',
      width: 90,
      render: (v: unknown) =>
        v ? <Tag color="orange">内置</Tag> : <Tag>自定义</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (v: unknown) => formatDateTime(v as string),
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑权限
          </Button>
          <Popconfirm
            title="确认删除该角色？"
            onConfirm={() => handleDelete(record)}
            disabled={record.builtin}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />} disabled={record.builtin}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>角色管理</Title>
      <Card>
        <ProTable<AdminRole>
          columns={columns}
          dataSource={roles}
          rowKey="id"
          loading={loading}
          search={false}
          pagination={false}
          toolBarRender={() => [
            <Button key="reload" icon={<ReloadOutlined />} onClick={load}>
              刷新
            </Button>,
            <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建角色
            </Button>,
          ]}
          scroll={{ x: 900 }}
        />
      </Card>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24}>
          <Card title="权限树预览" size="small">
            <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 12 }}>
              以下展示系统全部权限节点的树形结构（菜单 / 接口 / 操作 / 数据）。在编辑角色时可在弹窗中勾选授权。
            </Paragraph>
            {permissions.length === 0 ? (
              <Spin>
                <Empty description="加载中" />
              </Spin>
            ) : (
              <Tree
                treeData={permissionTree}
                defaultExpandAll
                showLine
                checkable
                defaultCheckedKeys={roles[0]?.permissionIds ?? []}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* 新增/编辑弹窗 */}
      <Modal
        title={editing ? '编辑角色' : '新建角色'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input placeholder="如：红队队长" />
          </Form.Item>
          <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input placeholder="如：redteam_lead" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="角色说明" />
          </Form.Item>
          <Form.Item name="permissionIds" label="权限">
            <Tree
              treeData={permissionTree}
              checkable
              defaultExpandAll
              style={{ maxHeight: 280, overflow: 'auto', border: `1px solid ${colors.neutral[200]}`, padding: 8, borderRadius: 4 }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default RoleManagePage;
