/**
 * 用户管理页面
 * ProTable + 新增/编辑用户（Modal + Form）+ 角色分配 + 状态启用/禁用
 */
import React, { useEffect, useRef, useState } from 'react';
import {
  Card,
  Typography,
  Button,
  Space,
  Tag,
  Popconfirm,
  message,
  Avatar,
  Modal,
  Form,
  Input,
  Select,
} from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  UserOutlined,
  ReloadOutlined,
  KeyOutlined,
} from '@ant-design/icons';
import { getAdminUsers, saveAdminUser, toggleUserStatus, deleteAdminUser, getAdminRoles } from '@/services';
import { UserStatusLabel } from '@/types';
import type { AdminUser, AdminRole, UserStatus } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title } = Typography;

/** 状态颜色 */
const statusColor: Record<UserStatus, string> = {
  active: 'success',
  disabled: 'default',
  locked: 'error',
};

/** 表单值类型 */
interface UserFormValues {
  username: string;
  nickname: string;
  email: string;
  phone?: string;
  password?: string;
  dept: string;
  roleIds: string[];
  status: UserStatus;
}

const UserManagePage: React.FC = () => {
  const actionRef = useRef<ActionType>(null);
  const [roles, setRoles] = useState<AdminRole[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AdminUser | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<UserFormValues>();

  useEffect(() => {
    getAdminRoles().then((res) => setRoles(res.data));
  }, []);

  /** 打开新增 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 'active' as UserStatus });
    setModalOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: AdminUser) => {
    setEditing(record);
    form.setFieldsValue({
      username: record.username,
      nickname: record.nickname,
      email: record.email,
      phone: record.phone,
      password: '',
      dept: record.dept,
      roleIds: record.roleIds,
      status: record.status,
    });
    setModalOpen(true);
  };

  /** 提交表单 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload: Partial<AdminUser> = {
        ...editing,
        ...values,
        id: editing?.id ?? `u_${Date.now()}`,
      };
      await saveAdminUser(payload);
      message.success(editing ? '用户已更新' : '用户已创建');
      setModalOpen(false);
      actionRef.current?.reload();
    } catch {
      // 校验失败
    } finally {
      setSubmitting(false);
    }
  };

  /** 切换状态 */
  const handleToggle = async (record: AdminUser) => {
    const next: UserStatus = record.status === 'active' ? 'disabled' : 'active';
    await toggleUserStatus(record.id, next);
    message.success(`用户状态已切换为：${UserStatusLabel[next]}`);
    actionRef.current?.reload();
  };

  /** 删除 */
  const handleDelete = async (record: AdminUser) => {
    await deleteAdminUser(record.id);
    message.success('用户已删除');
    actionRef.current?.reload();
  };

  /** 列定义 */
  const columns: ProColumns<AdminUser>[] = [
    {
      title: '头像',
      dataIndex: 'avatar',
      key: 'avatar',
      width: 60,
      hideInSearch: true,
      render: (_, record) => (
        <Avatar src={record.avatar} icon={<UserOutlined />} style={{ background: colors.primary[500] }} />
      ),
    },
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 140,
      copyable: true,
    },
    {
      title: '昵称',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 120,
      hideInSearch: true,
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      width: 200,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '部门',
      dataIndex: 'dept',
      key: 'dept',
      width: 100,
      hideInSearch: true,
    },
    {
      title: '角色',
      dataIndex: 'roleIds',
      key: 'roleIds',
      hideInSearch: true,
      width: 180,
      render: (_, record) => (
        <Space wrap size={4}>
          {record.roleIds.map((rid) => {
            const role = roles.find((r) => r.id === rid);
            return role ? (
              <Tag key={rid} color="blue">
                {role.name}
              </Tag>
            ) : null;
          })}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      valueType: 'select',
      valueEnum: {
        active: { text: '启用', status: 'Success' },
        disabled: { text: '禁用', status: 'Default' },
        locked: { text: '锁定', status: 'Error' },
      },
      render: (_, record) => (
        <Tag color={statusColor[record.status]}>{UserStatusLabel[record.status]}</Tag>
      ),
    },
    {
      title: '最近登录',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      hideInSearch: true,
      width: 160,
      render: (v: unknown) => (v ? formatDateTime(v as string) : <Tag>从未登录</Tag>),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      hideInSearch: true,
      width: 160,
      render: (v: unknown) => formatDateTime(v as string),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right',
      hideInSearch: true,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" icon={<KeyOutlined />} onClick={() => handleToggle(record)}>
            {record.status === 'active' ? '禁用' : '启用'}
          </Button>
          <Popconfirm title="确认删除该用户？" onConfirm={() => handleDelete(record)}>
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
      <Title level={4}>用户管理</Title>
      <Card>
        <ProTable<AdminUser>
          actionRef={actionRef}
          columns={columns}
          rowKey="id"
          search={{ labelWidth: 80 }}
          request={async (params) => {
            const page = params.current ?? 1;
            const pageSize = params.pageSize ?? 10;
            const res = await getAdminUsers({
              keyword: params.username,
              status: params.status as UserStatus | undefined,
              page,
              pageSize,
            });
            return {
              data: res.data.list,
              total: res.data.total,
              success: true,
            };
          }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          toolBarRender={() => [
            <Button
              key="reload"
              icon={<ReloadOutlined />}
              onClick={() => actionRef.current?.reload()}
            >
              刷新
            </Button>,
            <Button
              key="create"
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
            >
              新建用户
            </Button>,
          ]}
          scroll={{ x: 1400 }}
        />
      </Card>

      {/* 新增/编辑弹窗 */}
      <Modal
        title={editing ? '编辑用户' : '新建用户'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="登录用户名" disabled={!!editing} />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input placeholder="用户昵称" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '邮箱格式不正确' },
            ]}
          >
            <Input placeholder="user@redteam.local" />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input placeholder="13800138000" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: !editing, message: '请输入密码' }]}
          >
            <Input.Password placeholder={editing ? '留空则不修改' : '请输入密码'} />
          </Form.Item>
          <Form.Item
            name="dept"
            label="部门"
            rules={[{ required: true, message: '请输入部门' }]}
          >
            <Input placeholder="所属部门" />
          </Form.Item>
          <Form.Item
            name="roleIds"
            label="角色"
            rules={[{ required: true, message: '请选择角色' }]}
          >
            <Select
              mode="multiple"
              placeholder="请选择角色"
              options={roles.map((r) => ({ label: r.name, value: r.id }))}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '启用', value: 'active' },
                { label: '禁用', value: 'disabled' },
                { label: '锁定', value: 'locked' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagePage;
