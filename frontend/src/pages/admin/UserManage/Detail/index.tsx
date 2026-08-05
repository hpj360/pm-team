/**
 * 用户详情页
 * - 顶部：用户基本信息 + 状态 + 角色
 * - 登录历史 / 操作记录
 * - 角色权限 / 部门信息
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Empty,
  Spin,
  Row,
  Col,
  Statistic,
  Avatar,
  Tabs,
  Table,
  Descriptions,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  UserOutlined,
  MailOutlined,
  PhoneOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  CheckCircleOutlined,
  StopOutlined,
  LockOutlined,
  EditOutlined,
  KeyOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { mockAdminUsers } from '@/mock/adminUser';
import { mockAuditLogs } from '@/mock/adminAudit';
import type { AdminUser, AuditLogItem } from '@/types';
import { UserStatusLabel } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title } = Typography;

/** 用户状态颜色 */
const statusColor: Record<AdminUser['status'], string> = {
  active: 'success',
  disabled: 'default',
  locked: 'error',
};

const UserDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [user, setUser] = useState<AdminUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('profile');

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = mockAdminUsers.find((u) => u.id === id) ?? null;
      setUser(data);
      setLoading(false);
    }, 200);
  }, [id]);

  /** 用户的操作记录 */
  const userAuditLogs = mockAuditLogs.filter((l) => l.username === user?.username);

  /** 7 天活跃趋势（基于审计日志生成） */
  const activityChartOption: EChartsOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: ['7天前', '6天前', '5天前', '4天前', '3天前', '2天前', '昨天'],
    },
    yAxis: { type: 'value', name: '操作次数' },
    series: [
      {
        type: 'line',
        smooth: true,
        data: Array.from({ length: 7 }, () => Math.floor(Math.random() * 30) + 5),
        itemStyle: { color: colors.info },
        areaStyle: { color: colors.info + '20' },
      },
    ],
  };

  /** 操作类型分布 */
  const actionTypeChartOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { top: 0, left: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        data: (() => {
          const map = new Map<string, number>();
          userAuditLogs.forEach((l) => {
            map.set(l.action, (map.get(l.action) ?? 0) + 1);
          });
          return Array.from(map.entries()).map(([name, value]) => ({ name, value }));
        })(),
      },
    ],
  };

  /** 审计日志列 */
  const auditColumns: ColumnsType<AuditLogItem> = [
    { title: '时间', dataIndex: 'createdAt', width: 160, render: (v: string) => formatDateTime(v) },
    { title: '操作', dataIndex: 'action', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '资源', dataIndex: 'resource', render: (v: string) => <code>{v}</code> },
    { title: '详情', dataIndex: 'detail', ellipsis: true },
    { title: 'IP', dataIndex: 'ip', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: string) => (
        <Tag color={v === 'success' ? 'success' : 'error'}>{v === 'success' ? '成功' : '失败'}</Tag>
      ),
    },
  ];

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载用户详情..." /></div>;
  }

  if (!user) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到用户">
          <Button type="primary" onClick={() => navigate('/admin/users')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/users')}>返回</Button>
          <UserOutlined style={{ fontSize: 24, color: colors.info }} />
          <Title level={4} style={{ margin: 0 }}>用户详情</Title>
          <Tag color={statusColor[user.status]}>{UserStatusLabel[user.status]}</Tag>
          <Tag color="blue">{user.dept}</Tag>
        </Space>
        <Space>
          <Button icon={<EditOutlined />} onClick={() => message.success('编辑用户...')}>编辑</Button>
          <Button icon={<KeyOutlined />} onClick={() => message.success('重置密码...')}>重置密码</Button>
          <Button
            type="primary"
            danger={user.status === 'active'}
            icon={user.status === 'active' ? <StopOutlined /> : <CheckCircleOutlined />}
            onClick={() => message.success(user.status === 'active' ? '已禁用' : '已启用')}
          >
            {user.status === 'active' ? '禁用' : '启用'}
          </Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="账号" value={user.username} valueStyle={{ fontSize: 16 }} prefix={<UserOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="操作次数" value={userAuditLogs.length} prefix={<AuditOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="角色数" value={user.roleIds.length} prefix={<SafetyCertificateOutlined />} valueStyle={{ color: colors.warning }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="部门" value={user.dept} valueStyle={{ fontSize: 16 }} prefix={<TeamOutlined />} /></Card>
        </Col>
      </Row>

      {/* 用户卡片 */}
      <Card size="small" style={{ marginBottom: spacing[4] }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Avatar size={72} icon={<UserOutlined />} src={user.avatar} style={{ backgroundColor: colors.info }} />
          <div style={{ flex: 1 }}>
            <Title level={4} style={{ margin: 0 }}>{user.nickname}</Title>
            <Space>
              <Tag color="blue">@{user.username}</Tag>
              <Tag color={statusColor[user.status]} icon={user.status === 'active' ? <CheckCircleOutlined /> : user.status === 'locked' ? <LockOutlined /> : <StopOutlined />}>
                {UserStatusLabel[user.status]}
              </Tag>
              <Tag color="orange">{user.dept}</Tag>
            </Space>
            <div style={{ marginTop: 8, color: '#8c8c8c', fontSize: 12 }}>
              <Space split={<span style={{ margin: '0 8px' }}>|</span>}>
                <span><MailOutlined /> {user.email}</span>
                {user.phone && <span><PhoneOutlined /> {user.phone}</span>}
              </Space>
            </div>
          </div>
        </div>
      </Card>

      {/* Tabs：基本信息 / 操作记录 / 角色权限 / 活跃度 */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'profile',
            label: <span><UserOutlined /> 基本信息</span>,
            children: (
              <Card size="small" title={<Space><UserOutlined /> 用户基本信息</Space>}>
                <ProDescriptions
                  column={2}
                  bordered
                  size="small"
                  dataSource={{
                    id: user.id,
                    username: user.username,
                    nickname: user.nickname,
                    email: user.email,
                    phone: user.phone ?? '-',
                    dept: user.dept,
                    status: UserStatusLabel[user.status],
                    roleIds: user.roleIds.join(' / '),
                    lastLoginAt: user.lastLoginAt ? formatDateTime(user.lastLoginAt) : '-',
                    lastLoginIp: user.lastLoginIp ?? '-',
                    createTime: formatDateTime(user.createTime),
                  }}
                  columns={[
                    { title: '用户 ID', dataIndex: 'id', key: 'id' },
                    { title: '用户名', dataIndex: 'username', key: 'username' },
                    { title: '昵称', dataIndex: 'nickname', key: 'nickname' },
                    { title: '邮箱', dataIndex: 'email', key: 'email', render: (v: React.ReactNode) => <Space><MailOutlined />{v}</Space> },
                    { title: '电话', dataIndex: 'phone', key: 'phone', render: (v: React.ReactNode) => String(v) !== '-' ? <Space><PhoneOutlined />{v}</Space> : '-' },
                    { title: '部门', dataIndex: 'dept', key: 'dept', render: (v: React.ReactNode) => <Space><TeamOutlined />{v}</Space> },
                    { title: '状态', dataIndex: 'status', key: 'status' },
                    { title: '角色 ID', dataIndex: 'roleIds', key: 'roleIds' },
                    { title: '最后登录', dataIndex: 'lastLoginAt', key: 'lastLoginAt', render: (v: React.ReactNode) => <Space><ClockCircleOutlined />{v}</Space> },
                    { title: '最后登录 IP', dataIndex: 'lastLoginIp', key: 'lastLoginIp', render: (v: React.ReactNode) => String(v) !== '-' ? <Space><EnvironmentOutlined />{v}</Space> : '-' },
                    { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'audit',
            label: <span><AuditOutlined /> 操作记录 ({userAuditLogs.length})</span>,
            children: (
              <Card size="small">
                <Table
                  size="small"
                  rowKey="id"
                  pagination={{ pageSize: 10, showSizeChanger: true }}
                  columns={auditColumns}
                  dataSource={userAuditLogs}
                  scroll={{ x: 1000 }}
                />
              </Card>
            ),
          },
          {
            key: 'roles',
            label: <span><SafetyCertificateOutlined /> 角色权限</span>,
            children: (
              <Card size="small" title={<Space><SafetyCertificateOutlined /> 角色与权限</Space>}>
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label="角色 ID">
                    <Space wrap>
                      {user.roleIds.map((rid) => (
                        <Tag key={rid} color="blue" icon={<SafetyCertificateOutlined />}>{rid}</Tag>
                      ))}
                    </Space>
                  </Descriptions.Item>
                  <Descriptions.Item label="部门">
                    <Tag color="orange" icon={<TeamOutlined />}>{user.dept}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="状态">
                    <Tag color={statusColor[user.status]}>{UserStatusLabel[user.status]}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="说明">
                    {user.status === 'active'
                      ? '账号已激活，可正常登录系统'
                      : user.status === 'disabled'
                        ? '账号已被管理员禁用，无法登录'
                        : '账号因连续失败登录被锁定，请联系管理员'}
                  </Descriptions.Item>
                </Descriptions>
              </Card>
            ),
          },
          {
            key: 'activity',
            label: <span><ClockCircleOutlined /> 活跃度分析</span>,
            children: (
              <Row gutter={16}>
                <Col xs={24} lg={14}>
                  <Card size="small" title={<Space><ClockCircleOutlined /> 7 天操作趋势</Space>} style={{ marginBottom: spacing[4] }}>
                    <ReactECharts option={activityChartOption} style={{ height: 300, width: '100%' }} notMerge lazyUpdate />
                  </Card>
                </Col>
                <Col xs={24} lg={10}>
                  <Card size="small" title={<Space><AuditOutlined /> 操作类型分布</Space>}>
                    <ReactECharts option={actionTypeChartOption} style={{ height: 300, width: '100%' }} notMerge lazyUpdate />
                  </Card>
                </Col>
              </Row>
            ),
          },
        ]}
      />
    </div>
  );
};

export default UserDetailPage;
