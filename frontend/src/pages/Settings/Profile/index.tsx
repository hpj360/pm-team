/**
 * 个人中心页
 * - 顶部：用户信息卡片 + 头像 + 基本信息
 * - 主体：Tabs (个人资料 / 安全设置 / 偏好设置 / 登录历史 / 我的权限)
 * - 右侧：统计信息 + 快捷操作
 */
import React, { useMemo, useState } from 'react';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Row,
  Col,
  Statistic,
  Avatar,
  Tabs,
  Form,
  Input,
  DatePicker,
  Switch,
  Divider,
  Empty,
  Table,
  List,
  Badge,
  message,
  Upload,
  Progress,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  UserOutlined,
  MailOutlined,
  PhoneOutlined,
  LockOutlined,
  EnvironmentOutlined,
  SafetyCertificateOutlined,
  KeyOutlined,
  BellOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  CloudUploadOutlined,
  HistoryOutlined,
  SettingOutlined,
  EditOutlined,
  AimOutlined,
  FileTextOutlined,
  TeamOutlined,
  LoginOutlined,
  LogoutOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  SkinOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { useAuth } from '@/hooks';
import { useThemeStore } from '@/stores';
import { mockAuditLogs } from '@/mock/adminAudit';
import { mockAdminUsers } from '@/mock/adminUser';
import { mockAdminRoles } from '@/mock/adminRole';
import { mockAdminPermissions } from '@/mock/adminPermission';
import type { AuditLogItem } from '@/types';
import { AuditActionLabel } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 登录历史 Mock */
interface LoginHistoryItem {
  id: string;
  loginAt: string;
  ip: string;
  location: string;
  device: string;
  browser: string;
  status: 'success' | 'failed';
  mfaUsed: boolean;
}

const loginHistory: LoginHistoryItem[] = [
  { id: 'h1', loginAt: '2026-07-27T08:30:00Z', ip: '10.0.0.1', location: '北京-内网', device: 'Windows', browser: 'Chrome 119', status: 'success', mfaUsed: true },
  { id: 'h2', loginAt: '2026-07-26T18:45:00Z', ip: '10.0.0.1', location: '北京-内网', device: 'Windows', browser: 'Chrome 119', status: 'success', mfaUsed: true },
  { id: 'h3', loginAt: '2026-07-26T08:32:00Z', ip: '10.0.0.1', location: '北京-内网', device: 'Windows', browser: 'Chrome 119', status: 'success', mfaUsed: true },
  { id: 'h4', loginAt: '2026-07-25T22:10:00Z', ip: '114.114.114.114', location: '北京-公网', device: 'iPhone', browser: 'Safari', status: 'failed', mfaUsed: false },
  { id: 'h5', loginAt: '2026-07-25T08:28:00Z', ip: '10.0.0.1', location: '北京-内网', device: 'Windows', browser: 'Chrome 119', status: 'success', mfaUsed: true },
  { id: 'h6', loginAt: '2026-07-24T17:55:00Z', ip: '10.0.0.1', location: '北京-内网', device: 'Windows', browser: 'Chrome 119', status: 'success', mfaUsed: true },
  { id: 'h7', loginAt: '2026-07-24T09:05:00Z', ip: '10.0.0.1', location: '北京-内网', device: 'macOS', browser: 'Chrome 119', status: 'success', mfaUsed: true },
];

/** 偏好设置 Mock */
interface PreferenceItem {
  category: string;
  items: Array<{
    key: string;
    label: string;
    description: string;
    value: boolean;
  }>;
}

const preferences: PreferenceItem[] = [
  {
    category: '通知偏好',
    items: [
      { key: 'email_notify', label: '邮件通知', description: '重要事件通过邮件通知', value: true },
      { key: 'sms_notify', label: '短信通知', description: '紧急告警通过短信通知', value: true },
      { key: 'im_notify', label: '即时消息通知', description: '通过飞书/钉钉推送', value: true },
      { key: 'daily_report', label: '日报推送', description: '每天 18:00 推送日报', value: false },
    ],
  },
  {
    category: '安全偏好',
    items: [
      { key: 'auto_logout', label: '自动登出', description: '空闲 30 分钟后自动登出', value: true },
      { key: 'session_alert', label: '异地登录告警', description: '异地登录时发送告警', value: true },
      { key: 'audit_visible', label: '操作审计可见', description: '允许我查看自己的操作审计', value: true },
    ],
  },
  {
    category: '工作台偏好',
    items: [
      { key: 'compact_mode', label: '紧凑模式', description: '使用更紧凑的列表布局', value: false },
      { key: 'auto_refresh', label: '看板自动刷新', description: '看板每 30 秒自动刷新', value: true },
      { key: 'show_help', label: '显示帮助提示', description: '在界面显示新手帮助', value: true },
    ],
  },
];

const ProfilePage: React.FC = () => {
  const { user } = useAuth();
  const themeMode = useThemeStore((s) => s.mode);
  const toggleTheme = useThemeStore((s) => s.toggleTheme);
  const [activeTab, setActiveTab] = useState('profile');

  /** 关联的 AdminUser 数据（用于补全信息） */
  const adminUser = useMemo(() => {
    if (!user) return mockAdminUsers[0];
    return mockAdminUsers.find((u) => u.username === user.username) ?? mockAdminUsers[0];
  }, [user]);

  /** 用户角色 */
  const userRoles = useMemo(() => {
    return mockAdminRoles.filter((r) => adminUser.roleIds.includes(r.id));
  }, [adminUser]);

  /** 用户权限 */
  const userPermissions = useMemo(() => {
    const permIds = new Set<string>();
    userRoles.forEach((r) => r.permissionIds.forEach((p) => permIds.add(p)));
    return mockAdminPermissions.filter((p) => permIds.has(p.id));
  }, [userRoles]);

  /** 用户审计日志 */
  const userAuditLogs = useMemo(() => {
    return mockAuditLogs.filter((l) => l.username === adminUser.username).slice(0, 10);
  }, [adminUser]);

  /** 统计信息 */
  const stats = useMemo(() => {
    const totalLogins = loginHistory.filter((h) => h.status === 'success').length;
    const failedLogins = loginHistory.filter((h) => h.status === 'failed').length;
    const auditCount = mockAuditLogs.filter((l) => l.username === adminUser.username).length;
    const mfaEnabled = user?.mfaEnabled ?? true;
    return { totalLogins, failedLogins, auditCount, mfaEnabled };
  }, [adminUser, user]);

  /** 登录活跃度趋势（最近 7 天） */
  const loginTrendOption: EChartsOption = useMemo(() => ({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['7天前', '6天前', '5天前', '4天前', '3天前', '2天前', '今日'],
    },
    yAxis: { type: 'value', min: 0, max: 10 },
    series: [
      {
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: colors.primary[500] },
        data: [3, 5, 4, 7, 6, 8, 9],
      },
    ],
  }), []);

  /** 登录历史列 */
  const loginColumns: ColumnsType<LoginHistoryItem> = [
    {
      title: '登录时间',
      dataIndex: 'loginAt',
      width: 160,
      render: (v: string) => formatDateTime(v),
    },
    { title: 'IP', dataIndex: 'ip', width: 130, render: (v: string) => <code>{v}</code> },
    { title: '位置', dataIndex: 'location', width: 140, render: (v: string) => <Space><EnvironmentOutlined />{v}</Space> },
    { title: '设备', dataIndex: 'device', width: 100 },
    { title: '浏览器', dataIndex: 'browser', width: 130 },
    {
      title: 'MFA',
      dataIndex: 'mfaUsed',
      width: 80,
      render: (v: boolean) => v ? <Tag color="success">已用</Tag> : <Tag>未用</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => (
        <Tag color={v === 'success' ? 'success' : 'error'} icon={v === 'success' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}>
          {v === 'success' ? '成功' : '失败'}
        </Tag>
      ),
    },
  ];

  /** 审计日志列 */
  const auditColumns: ColumnsType<AuditLogItem> = [
    { title: '时间', dataIndex: 'createdAt', width: 160, render: (v: string) => formatDateTime(v) },
    {
      title: '操作',
      dataIndex: 'action',
      width: 90,
      render: (v: string) => <Tag color="blue">{AuditActionLabel[v as keyof typeof AuditActionLabel] ?? v}</Tag>,
    },
    { title: '资源', dataIndex: 'resource', width: 200, ellipsis: true, render: (v: string) => <code style={{ fontSize: 12 }}>{v}</code> },
    { title: '详情', dataIndex: 'detail', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => <Tag color={v === 'success' ? 'success' : 'error'}>{v === 'success' ? '成功' : '失败'}</Tag>,
    },
    { title: 'IP', dataIndex: 'ip', width: 130, render: (v: string) => <code>{v}</code> },
  ];

  /** 权限类型分组统计 */
  const permStats = useMemo(() => {
    const grouped: Record<string, number> = { menu: 0, api: 0, action: 0, data: 0 };
    userPermissions.forEach((p) => { grouped[p.type] = (grouped[p.type] || 0) + 1; });
    return grouped;
  }, [userPermissions]);

  /** 上传头像 */
  const handleAvatarUpload = (file: UploadFile) => {
    message.success(`头像 ${file.name} 上传成功`);
  };

  /** 表单保存 */
  const handleProfileSave = () => {
    message.success('个人资料已保存');
  };

  /** 偏好开关 */
  const handlePrefChange = (key: string, checked: boolean) => {
    message.success(`${key} 已 ${checked ? '开启' : '关闭'}`);
  };

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部：用户信息卡片 */}
      <Card style={{ marginBottom: spacing[4] }} styles={{ body: { padding: spacing[6] } }}>
        <Row gutter={24} align="middle">
          <Col xs={24} md={6} style={{ textAlign: 'center' }}>
            <Avatar size={120} src={user?.avatar} icon={<UserOutlined />} style={{ border: `4px solid ${colors.primary[100]}` }} />
            <div style={{ marginTop: 12 }}>
              <Upload
                showUploadList={false}
                accept="image/*"
                beforeUpload={(file) => {
                  handleAvatarUpload(file as unknown as UploadFile);
                  return false;
                }}
              >
                <Button size="small" icon={<CloudUploadOutlined />}>更换头像</Button>
              </Upload>
            </div>
          </Col>
          <Col xs={24} md={12}>
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <Space size={12}>
                <Title level={3} style={{ margin: 0 }}>{user?.nickname || user?.username || '系统管理员'}</Title>
                <Tag icon={<SafetyCertificateOutlined />} color="gold">超级管理员</Tag>
                <Tag icon={<CheckCircleOutlined />} color="success">在线</Tag>
              </Space>
              <Space size={16} wrap>
                <Text type="secondary"><UserOutlined /> {user?.username}</Text>
                <Text type="secondary"><MailOutlined /> {user?.email}</Text>
                <Text type="secondary"><TeamOutlined /> {adminUser.dept}</Text>
              </Space>
              <Space size={16} wrap>
                <Tag color="blue">用户 ID: {user?.id || '-'}</Tag>
                <Tag color="purple">注册时间: {formatDateTime(user?.createTime || adminUser.createTime)}</Tag>
                <Tag color="cyan">MFA: {stats.mfaEnabled ? '已启用' : '未启用'}</Tag>
              </Space>
              <Paragraph type="secondary" style={{ marginBottom: 0, marginTop: 4 }}>
                红方文件聚合平台 - 负责整体系统管理与运维，关注平台稳定性、安全合规与数据治理。
              </Paragraph>
            </Space>
          </Col>
          <Col xs={24} md={6}>
            <Row gutter={8}>
              <Col span={12}>
                <Statistic title="本月登录" value={stats.totalLogins} prefix={<LoginOutlined />} valueStyle={{ color: colors.info }} />
              </Col>
              <Col span={12}>
                <Statistic title="失败次数" value={stats.failedLogins} prefix={<LogoutOutlined />} valueStyle={{ color: stats.failedLogins > 0 ? colors.error : colors.success }} />
              </Col>
              <Col span={12} style={{ marginTop: 12 }}>
                <Statistic title="操作审计" value={stats.auditCount} prefix={<HistoryOutlined />} valueStyle={{ color: colors.warning }} />
              </Col>
              <Col span={12} style={{ marginTop: 12 }}>
                <Statistic title="权限数" value={userPermissions.length} prefix={<KeyOutlined />} valueStyle={{ color: colors.primary[500] }} />
              </Col>
            </Row>
          </Col>
        </Row>
      </Card>

      <Row gutter={16}>
        {/* 左侧：主体内容（Tabs） */}
        <Col xs={24} lg={17}>
          <Card>
            <Tabs
              activeKey={activeTab}
              onChange={setActiveTab}
              items={[
                {
                  key: 'profile',
                  label: <span><UserOutlined /> 个人资料</span>,
                  children: (
                    <div>
                      <Form layout="vertical" onFinish={handleProfileSave}>
                        <Row gutter={16}>
                          <Col xs={24} md={12}>
                            <Form.Item label="用户名" name="username" initialValue={user?.username}>
                              <Input disabled prefix={<UserOutlined />} />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={12}>
                            <Form.Item label="昵称" name="nickname" initialValue={user?.nickname}>
                              <Input prefix={<EditOutlined />} placeholder="请输入昵称" />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={12}>
                            <Form.Item label="邮箱" name="email" initialValue={user?.email} rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
                              <Input prefix={<MailOutlined />} placeholder="user@example.com" />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={12}>
                            <Form.Item label="手机号" name="phone" initialValue={adminUser.phone}>
                              <Input prefix={<PhoneOutlined />} placeholder="请输入手机号" />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={12}>
                            <Form.Item label="部门" name="dept" initialValue={adminUser.dept}>
                              <Input prefix={<TeamOutlined />} disabled />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={12}>
                            <Form.Item label="地区" name="region" initialValue="北京">
                              <Input prefix={<EnvironmentOutlined />} placeholder="请输入地区" />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={12}>
                            <Form.Item label="职位" name="position" initialValue="系统管理员">
                              <Input prefix={<AimOutlined />} placeholder="请输入职位" />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={12}>
                            <Form.Item label="入职日期" name="entryDate">
                              <DatePicker style={{ width: '100%' }} />
                            </Form.Item>
                          </Col>
                          <Col xs={24}>
                            <Form.Item label="个人简介" name="bio">
                              <Input.TextArea rows={3} placeholder="请输入个人简介" defaultValue="红方文件聚合平台 - 系统管理员" />
                            </Form.Item>
                          </Col>
                        </Row>
                        <Space>
                          <Button type="primary" htmlType="submit" icon={<CheckCircleOutlined />}>保存修改</Button>
                          <Button icon={<CloseCircleOutlined />}>重置</Button>
                        </Space>
                      </Form>
                    </div>
                  ),
                },
                {
                  key: 'security',
                  label: <span><LockOutlined /> 安全设置</span>,
                  children: (
                    <div>
                      <Card size="small" title={<Space><KeyOutlined /> 修改密码</Space>} style={{ marginBottom: 16 }}>
                        <Form layout="vertical">
                          <Row gutter={16}>
                            <Col xs={24}>
                              <Form.Item label="当前密码" name="currentPassword" rules={[{ required: true, message: '请输入当前密码' }]}>
                                <Input.Password placeholder="请输入当前密码" prefix={<LockOutlined />} iconRender={(visible) => visible ? <EyeOutlined /> : <EyeInvisibleOutlined />} />
                              </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                              <Form.Item label="新密码" name="newPassword" rules={[{ required: true, message: '请输入新密码' }, { min: 8, message: '密码至少 8 位' }]}>
                                <Input.Password placeholder="至少 8 位，包含大小写、数字、符号" prefix={<LockOutlined />} />
                              </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                              <Form.Item label="确认新密码" name="confirmPassword" rules={[{ required: true, message: '请确认新密码' }]}>
                                <Input.Password placeholder="请再次输入新密码" prefix={<LockOutlined />} />
                              </Form.Item>
                            </Col>
                          </Row>
                          <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => message.success('密码修改成功')}>修改密码</Button>
                        </Form>
                      </Card>

                      <Card size="small" title={<Space><SafetyCertificateOutlined /> MFA 两步验证</Space>} style={{ marginBottom: 16 }}>
                        <ProDescriptions
                          column={2}
                          bordered
                          size="small"
                          dataSource={{
                            mfaStatus: stats.mfaEnabled ? '已启用' : '未启用',
                            mfaType: 'TOTP (Google Authenticator)',
                            bindTime: stats.mfaEnabled ? '2026-01-15 10:30:00' : '-',
                            lastVerify: userAuditLogs[0]?.createdAt ? formatDateTime(userAuditLogs[0].createdAt) : '-',
                          }}
                          columns={[
                            { title: 'MFA 状态', dataIndex: 'mfaStatus', key: 'mfaStatus' },
                            { title: '验证方式', dataIndex: 'mfaType', key: 'mfaType' },
                            { title: '绑定时间', dataIndex: 'bindTime', key: 'bindTime' },
                            { title: '最近验证', dataIndex: 'lastVerify', key: 'lastVerify' },
                          ]}
                        />
                        <Space style={{ marginTop: 12 }}>
                          <Button type={stats.mfaEnabled ? 'default' : 'primary'} icon={<KeyOutlined />} onClick={() => message.info('请使用 Google Authenticator 扫描二维码')}>
                            {stats.mfaEnabled ? '重新绑定' : '立即绑定'}
                          </Button>
                          {stats.mfaEnabled && (
                            <Button danger icon={<CloseCircleOutlined />} onClick={() => message.warning('已发送解绑申请')}>解绑</Button>
                          )}
                        </Space>
                      </Card>

                      <Card size="small" title={<Space><BellOutlined /> 异常登录告警</Space>}>
                        <List
                          size="small"
                          dataSource={[
                            { id: 1, level: 'warning', content: '检测到异地登录尝试 (IP: 114.114.114.114)', time: '2026-07-25 22:10' },
                            { id: 2, level: 'info', content: '登录成功 - 北京内网', time: '2026-07-27 08:30' },
                            { id: 3, level: 'success', content: 'MFA 验证通过', time: '2026-07-27 08:30' },
                          ]}
                          renderItem={(item) => (
                            <List.Item>
                              <Space>
                                {item.level === 'warning' && <ExclamationCircleOutlined style={{ color: colors.warning }} />}
                                {item.level === 'success' && <CheckCircleOutlined style={{ color: colors.success }} />}
                                {item.level === 'info' && <BellOutlined style={{ color: colors.info }} />}
                                <Text>{item.content}</Text>
                                <Text type="secondary">{item.time}</Text>
                              </Space>
                            </List.Item>
                          )}
                        />
                      </Card>
                    </div>
                  ),
                },
                {
                  key: 'preferences',
                  label: <span><SettingOutlined /> 偏好设置</span>,
                  children: (
                    <div>
                      <Card size="small" style={{ marginBottom: 16 }}>
                        <Space>
                          <SkinOutlined style={{ fontSize: 18 }} />
                          <Text strong>主题模式</Text>
                          <Switch
                            checked={themeMode === 'dark'}
                            onChange={toggleTheme}
                            checkedChildren="暗黑"
                            unCheckedChildren="浅色"
                          />
                          <Text type="secondary">切换主题模式 (当前: {themeMode === 'dark' ? '暗黑' : '浅色'})</Text>
                        </Space>
                      </Card>
                      {preferences.map((group) => (
                        <Card key={group.category} size="small" title={<Space><BulbOutlined /> {group.category}</Space>} style={{ marginBottom: 16 }}>
                          {group.items.map((item) => (
                            <div key={item.key} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0' }}>
                              <div>
                                <Text strong>{item.label}</Text>
                                <div style={{ fontSize: 12, color: '#8c8c8c' }}>{item.description}</div>
                              </div>
                              <Switch defaultChecked={item.value} onChange={(checked) => handlePrefChange(item.key, checked)} />
                            </div>
                          ))}
                        </Card>
                      ))}
                    </div>
                  ),
                },
                {
                  key: 'history',
                  label: <span><HistoryOutlined /> 登录历史 ({loginHistory.length})</span>,
                  children: (
                    <div>
                      <Card size="small" style={{ marginBottom: 16 }}>
                        <ReactECharts option={loginTrendOption} style={{ height: 220, width: '100%' }} notMerge lazyUpdate />
                      </Card>
                      <Table
                        size="small"
                        rowKey="id"
                        columns={loginColumns}
                        dataSource={loginHistory}
                        pagination={{ pageSize: 8, showSizeChanger: true }}
                        scroll={{ x: 900 }}
                      />
                    </div>
                  ),
                },
                {
                  key: 'audit',
                  label: <span><HistoryOutlined /> 我的操作 ({userAuditLogs.length})</span>,
                  children: (
                    <Table
                      size="small"
                      rowKey="id"
                      columns={auditColumns}
                      dataSource={userAuditLogs}
                      pagination={{ pageSize: 8 }}
                      scroll={{ x: 900 }}
                    />
                  ),
                },
              ]}
            />
          </Card>
        </Col>

        {/* 右侧：统计信息 + 快捷操作 */}
        <Col xs={24} lg={7}>
          {/* 权限分布 */}
          <Card size="small" title={<Space><KeyOutlined /> 权限分布</Space>} style={{ marginBottom: 16 }}>
            <Row gutter={8}>
              <Col span={6}>
                <Statistic title="菜单" value={permStats.menu} valueStyle={{ fontSize: 18, color: colors.info }} />
              </Col>
              <Col span={6}>
                <Statistic title="接口" value={permStats.api} valueStyle={{ fontSize: 18, color: colors.success }} />
              </Col>
              <Col span={6}>
                <Statistic title="操作" value={permStats.action} valueStyle={{ fontSize: 18, color: colors.warning }} />
              </Col>
              <Col span={6}>
                <Statistic title="数据" value={permStats.data} valueStyle={{ fontSize: 18, color: colors.primary[500] }} />
              </Col>
            </Row>
            <Divider style={{ margin: '12px 0' }} />
            <Text type="secondary" style={{ fontSize: 12 }}>权限使用率</Text>
            <Progress percent={Math.min(100, userPermissions.length)} size="small" strokeColor={colors.primary[500]} style={{ marginTop: 4 }} />
          </Card>

          {/* 角色信息 */}
          <Card size="small" title={<Space><SafetyCertificateOutlined /> 我的角色 ({userRoles.length})</Space>} style={{ marginBottom: 16 }}>
            <List
              size="small"
              dataSource={userRoles}
              renderItem={(role) => (
                <List.Item>
                  <div style={{ width: '100%' }}>
                    <Space>
                      <Tag color={role.builtin ? 'gold' : 'blue'}>{role.builtin ? '内置' : '自定义'}</Tag>
                      <Text strong>{role.name}</Text>
                    </Space>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>{role.description}</div>
                    <div style={{ marginTop: 4 }}>
                      <Tag color="purple">权限 {role.permissionIds.length}</Tag>
                      <Tag color="cyan">用户 {role.userCount}</Tag>
                      <Tag color="default">{role.code}</Tag>
                    </div>
                  </div>
                </List.Item>
              )}
              locale={{ emptyText: <Empty description="暂无角色" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
            />
          </Card>

          {/* 快捷操作 */}
          <Card size="small" title={<Space><ThunderboltOutlined /> 快捷操作</Space>} style={{ marginBottom: 16 }}>
            <Row gutter={[8, 8]}>
              <Col xs={12}>
                <Button block icon={<FileTextOutlined />} onClick={() => message.info('跳转到我的文件')}>我的文件</Button>
              </Col>
              <Col xs={12}>
                <Button block icon={<HistoryOutlined />} onClick={() => setActiveTab('audit')}>操作审计</Button>
              </Col>
              <Col xs={12}>
                <Button block icon={<LockOutlined />} onClick={() => setActiveTab('security')}>安全设置</Button>
              </Col>
              <Col xs={12}>
                <Button block icon={<SettingOutlined />} onClick={() => setActiveTab('preferences')}>偏好设置</Button>
              </Col>
            </Row>
          </Card>

          {/* 账户安全等级 */}
          <Card size="small" title={<Space><SafetyCertificateOutlined /> 账户安全等级</Space>}>
            <div style={{ textAlign: 'center', padding: '8px 0' }}>
              <Progress
                type="dashboard"
                percent={85}
                strokeColor={{ '0%': colors.warning, '100%': colors.success }}
                format={(p) => `${p}`}
              />
              <Title level={4} style={{ marginTop: 8, marginBottom: 0 }}>安全等级 A</Title>
              <Text type="secondary" style={{ fontSize: 12 }}>账户安全性良好</Text>
            </div>
            <Divider style={{ margin: '12px 0' }} />
            <Space direction="vertical" size={4} style={{ width: '100%' }}>
              <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                <Text style={{ fontSize: 12 }}><CheckCircleOutlined style={{ color: colors.success }} /> 已启用 MFA</Text>
                <Badge status="success" />
              </Space>
              <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                <Text style={{ fontSize: 12 }}><CheckCircleOutlined style={{ color: colors.success }} /> 强密码策略</Text>
                <Badge status="success" />
              </Space>
              <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                <Text style={{ fontSize: 12 }}><CheckCircleOutlined style={{ color: colors.success }} /> 异地登录告警</Text>
                <Badge status="success" />
              </Space>
              <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                <Text style={{ fontSize: 12 }}><ExclamationCircleOutlined style={{ color: colors.warning }} /> 建议定期更换密码</Text>
                <Badge status="warning" />
              </Space>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default ProfilePage;
