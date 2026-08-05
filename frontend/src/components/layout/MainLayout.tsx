/**
 * 主布局组件
 * - 侧边栏：业务菜单（含文件管理子菜单）
 * - 顶部：折叠按钮 + 主题切换 + 通知 + 用户菜单
 * - 内容区：Outlet
 */
import React, { useMemo, useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Dropdown, Avatar, Button, Badge, theme, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import {
  SearchOutlined,
  FileTextOutlined,
  BarChartOutlined,
  SettingOutlined,
  UserOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  DashboardOutlined,
  SafetyCertificateOutlined,
  AreaChartOutlined,
  LineChartOutlined,
  BellOutlined,
  SunOutlined,
  MoonOutlined,
  CloudUploadOutlined,
  BugOutlined,
  AimOutlined,
  RadarChartOutlined,
  NodeIndexOutlined,
  FireOutlined,
  ToolOutlined,
  TeamOutlined,
  KeyOutlined,
  CodeOutlined,
  AuditOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  HeartOutlined,
  ControlOutlined,
  ShareAltOutlined,
  ScheduleOutlined,
  FileDoneOutlined,
  NotificationOutlined,
  AppstoreOutlined,
  ClusterOutlined,
  ApiOutlined,
  HddOutlined,
  ProfileOutlined,
  SolutionOutlined,
  TagsOutlined,
  RobotOutlined,
  BookOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/hooks';
import { useThemeStore } from '@/stores';
import styles from './MainLayout.module.less';

const { Header, Sider, Content } = Layout;

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();
  const themeMode = useThemeStore((s) => s.mode);
  const toggleTheme = useThemeStore((s) => s.toggleTheme);
  const { token: themeToken } = theme.useToken();

  /** 侧边栏菜单 */
  const menuItems = useMemo<MenuProps['items']>(
    () => [
      { key: '/dashboard', icon: <DashboardOutlined />, label: '工作台' },
      {
        key: '/files-group',
        icon: <FileTextOutlined />,
        label: '文件管理',
        children: [
          { key: '/files', icon: <FileTextOutlined />, label: '文件列表' },
          { key: '/files/upload', icon: <CloudUploadOutlined />, label: '文件上传' },
        ],
      },
      { key: '/search', icon: <SearchOutlined />, label: '文件检索' },
      { key: '/analyze', icon: <BarChartOutlined />, label: '文件分析' },
      { key: '/ioc', icon: <BugOutlined />, label: '威胁情报' },
      // V5.1 AI Agent 化模块
      {
        key: '/ai-group',
        icon: <RobotOutlined />,
        label: 'AI Agent',
        children: [
          { key: '/ai/agent', icon: <RobotOutlined />, label: '自主分析' },
          { key: '/ai/knowledge', icon: <BookOutlined />, label: '知识库管理' },
        ],
      },
      {
        key: '/monitor-group',
        icon: <AreaChartOutlined />,
        label: '监控看板',
        children: [
          { key: '/monitor', icon: <DashboardOutlined />, label: '总览看板' },
          { key: '/monitor/slo', icon: <DashboardOutlined />, label: 'SLO 监控' },
          { key: '/monitor/funnel', icon: <LineChartOutlined />, label: '漏斗分析' },
        ],
      },
      // 红方作战分组
      {
        key: '/redteam-group',
        icon: <FireOutlined />,
        label: '红方作战',
        children: [
          { key: '/redteam/target-profile', icon: <AimOutlined />, label: '目标画像' },
          { key: '/redteam/threat-intel', icon: <RadarChartOutlined />, label: '威胁情报' },
          { key: '/redteam/attack-chain', icon: <NodeIndexOutlined />, label: '攻击链路' },
          { key: '/redteam/vulnerability', icon: <BugOutlined />, label: '漏洞利用' },
          { key: '/redteam/arsenal', icon: <ToolOutlined />, label: '武器库' },
          { key: '/redteam/collaboration', icon: <TeamOutlined />, label: '协同作战' },
          { key: '/redteam/collaboration/task-board', icon: <ScheduleOutlined />, label: '任务看板' },
          { key: '/redteam/collaboration/team', icon: <TeamOutlined />, label: '团队成员' },
          { key: '/redteam/relation-graph', icon: <ShareAltOutlined />, label: '关系图谱' },
          { key: '/redteam/tasks', icon: <ScheduleOutlined />, label: '任务管理' },
        ],
      },
      // V5.3 威胁狩猎分组
      {
        key: '/hunting-group',
        icon: <RadarChartOutlined />,
        label: '威胁狩猎',
        children: [
          { key: '/hunting/workbench', icon: <AimOutlined />, label: '狩猎工作台' },
          { key: '/hunting/rules', icon: <CodeOutlined />, label: '狩猎规则' },
        ],
      },
      // 后台管理分组
      {
        key: '/admin-group',
        icon: <ControlOutlined />,
        label: '后台管理',
        children: [
          { key: '/admin/users', icon: <UserOutlined />, label: '用户管理' },
          { key: '/admin/roles', icon: <KeyOutlined />, label: '角色管理' },
          { key: '/admin/permissions', icon: <SafetyCertificateOutlined />, label: '权限管理' },
          { key: '/admin/yara-rules', icon: <CodeOutlined />, label: 'YARA规则' },
          { key: '/admin/config', icon: <SettingOutlined />, label: '系统配置' },
          { key: '/admin/audit-log', icon: <AuditOutlined />, label: '审计日志' },
          { key: '/admin/data-sources', icon: <DatabaseOutlined />, label: '数据源' },
          { key: '/admin/models', icon: <ExperimentOutlined />, label: '模型管理' },
          { key: '/admin/health', icon: <HeartOutlined />, label: '健康检查' },
          { key: '/admin/reports', icon: <FileDoneOutlined />, label: '报告中心' },
          { key: '/admin/notifications', icon: <NotificationOutlined />, label: '通知中心' },
          { key: '/admin/tags', icon: <TagsOutlined />, label: '标签管理' },
          { key: '/admin/data-masking', icon: <SafetyCertificateOutlined />, label: '脱敏规则' },
        ],
      },
      // 应用运维分组（D1-D7 + 工单）
      {
        key: '/ops-group',
        icon: <AppstoreOutlined />,
        label: '应用运维',
        children: [
          { key: '/ops/spaces', icon: <ClusterOutlined />, label: '空间台账' },
          { key: '/ops/consistency', icon: <ApiOutlined />, label: '一致性对账' },
          { key: '/ops/heal', icon: <ToolOutlined />, label: '链路治愈' },
          { key: '/ops/lifecycle', icon: <HddOutlined />, label: '生命周期' },
          { key: '/ops/config', icon: <SettingOutlined />, label: '应用配置' },
          { key: '/ops/security', icon: <SafetyCertificateOutlined />, label: '数据安全' },
          { key: '/ops/reports', icon: <ProfileOutlined />, label: '空间报告' },
          { key: '/ops/tickets', icon: <SolutionOutlined />, label: '运维工单' },
        ],
      },
      { key: '/settings/profile', icon: <UserOutlined />, label: '个人中心' },
      { key: '/settings', icon: <SettingOutlined />, label: '系统设置' },
    ],
    [],
  );

  /** 当前展开的子菜单与选中项 */
  const { selectedKeys, openKeys } = useMemo(() => {
    const path = location.pathname;
    let open: string[] = [];
    if (path.startsWith('/files')) {
      open = ['/files-group'];
    } else if (path.startsWith('/monitor')) {
      open = ['/monitor-group'];
    } else if (path.startsWith('/redteam')) {
      open = ['/redteam-group'];
    } else if (path.startsWith('/hunting')) {
      open = ['/hunting-group'];
    } else if (path.startsWith('/admin')) {
      open = ['/admin-group'];
    } else if (path.startsWith('/ops')) {
      open = ['/ops-group'];
    } else if (path.startsWith('/ai')) {
      open = ['/ai-group'];
    }
    return { selectedKeys: [path], openKeys: open };
  }, [location.pathname]);

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key);
  };

  /** 用户下拉菜单 */
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
      onClick: () => navigate('/settings/profile'),
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '账户设置',
      onClick: () => navigate('/settings'),
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: logout,
    },
  ];

  /** 通知下拉（Mock） */
  const notificationItems: MenuProps['items'] = [
    {
      key: 'n1',
      label: '文件解析完成：malware_sample_0001.exe',
    },
    {
      key: 'n2',
      label: 'YARA 规则命中：CobaltStrike_Beacon',
    },
    {
      key: 'n3',
      label: '检测到新增 IOC 12 条',
    },
  ];

  return (
    <Layout className={styles.layout}>
      {/* Skip to content 链接，WCAG AA 2.4.1 */}
      <a
        href="#main-content"
        className={styles.skipLink}
        aria-label="跳到主内容"
      >
        跳到主内容
      </a>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        className={styles.sider}
        width={220}
        theme="dark"
        aria-label="主导航"
      >
        <div className={styles.logo}>
          <SafetyCertificateOutlined className={styles.logoIcon} />
          {!collapsed && <span className={styles.logoText}>红方文件平台</span>}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKeys}
          defaultOpenKeys={openKeys}
          items={menuItems}
          onClick={handleMenuClick}
          aria-label="主菜单"
        />
      </Sider>

      <Layout>
        <Header
          className={styles.header}
          style={{ background: themeToken.colorBgContainer }}
          role="banner"
        >
          <div className={styles.headerLeft}>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              className={styles.trigger}
              aria-label={collapsed ? '展开侧边栏' : '收起侧边栏'}
              aria-expanded={!collapsed}
            />
          </div>

          <div className={styles.headerRight}>
            {/* 主题切换 */}
            <Tooltip title={themeMode === 'light' ? '切换到暗黑主题' : '切换到浅色主题'}>
              <Button
                type="text"
                icon={themeMode === 'light' ? <MoonOutlined /> : <SunOutlined />}
                onClick={toggleTheme}
                className={styles.iconBtn}
                aria-label="切换主题"
              />
            </Tooltip>

            {/* 通知 */}
            <Dropdown menu={{ items: notificationItems }} placement="bottomRight">
              <Badge count={3} size="small">
                <Button
                  type="text"
                  icon={<BellOutlined />}
                  className={styles.iconBtn}
                  aria-label="通知中心"
                  onClick={() => navigate('/admin/notifications')}
                />
              </Badge>
            </Dropdown>

            {/* 用户 */}
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <div className={styles.userInfo} role="button" tabIndex={0} aria-label="用户菜单">
                <Avatar
                  size="small"
                  icon={<UserOutlined />}
                  src={user?.avatar}
                  className={styles.avatar}
                />
                <span className={styles.username}>
                  {user?.nickname || user?.username || '用户'}
                </span>
              </div>
            </Dropdown>
          </div>
        </Header>

        <Content
          id="main-content"
          className={styles.content}
          style={{
            background: themeToken.colorBgContainer,
            borderRadius: themeToken.borderRadiusLG,
          }}
          role="main"
          tabIndex={-1}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
