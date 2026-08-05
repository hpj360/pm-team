/**
 * 路由配置
 * - /login: 登录页（无需鉴权）
 * - /: 主布局（需鉴权）
 *   - /dashboard: 仪表盘
 *   - /files: 文件列表 | /files/:id: 文件详情 | /files/upload: 上传 | /files/upload/batch: 批量上传
 *   - /search: 文件检索 | /search/advanced: 高级检索
 *   - /analyze: 文件分析 | /analyze/compare: 对比 | /analyze/report: 报告
 *   - /ioc: IOC 中心
 *   - /monitor: 监控看板 | /monitor/slo: SLO 监控 | /monitor/funnel: 漏斗分析
 *   - /settings: 系统设置 | /settings/profile: 个人中心
 *   - 红方作战模块：/redteam/* (含详情页 /:id 与子页 task-board/team)
 *   - 后台管理模块：/admin/* (含详情页 /:id 与报告预览 preview/:id)
 *     - /admin/data-masking: 脱敏规则管理（V4.7-P1-4）
 *   - 应用运维模块：/ops/* (D1空间台账 / D2一致性 / D3治愈 / D4生命周期 / D5配置 / D6安全 / D7报告 / 工单)
 * - *: 404
 */
import React, { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { Spin } from 'antd';
import { MainLayout } from '@/components/layout';

// 懒加载页面组件 - 原有
const Dashboard = lazy(() => import('@/pages/Dashboard'));
const FileList = lazy(() => import('@/pages/FileList'));
const FileUpload = lazy(() => import('@/pages/FileUpload'));
const FileSearch = lazy(() => import('@/pages/FileSearch'));
const FileAnalyze = lazy(() => import('@/pages/FileAnalyze'));
const IocCenter = lazy(() => import('@/pages/IocCenter'));
const Monitor = lazy(() => import('@/pages/Monitor'));
const Settings = lazy(() => import('@/pages/Settings'));
const Login = lazy(() => import('@/pages/Login'));
const NotFound = lazy(() => import('@/pages/NotFound'));

// 红方作战模块
const TargetProfile = lazy(() => import('@/pages/redteam/TargetProfile'));
const ThreatIntel = lazy(() => import('@/pages/redteam/ThreatIntel'));
const AttackChain = lazy(() => import('@/pages/redteam/AttackChain'));
const Vulnerability = lazy(() => import('@/pages/redteam/Vulnerability'));
const Arsenal = lazy(() => import('@/pages/redteam/Arsenal'));
const Collaboration = lazy(() => import('@/pages/redteam/Collaboration'));
const RelationGraph = lazy(() => import('@/pages/redteam/RelationGraph'));
const TaskManage = lazy(() => import('@/pages/redteam/TaskManage'));

// V5.3 威胁狩猎模块
const HuntingWorkbench = lazy(() => import('@/pages/hunting/Workbench'));
const HuntingRules = lazy(() => import('@/pages/hunting/Rules'));

// 监控深度页面
const MonitorSlo = lazy(() => import('@/pages/Monitor/Slo'));
const MonitorFunnel = lazy(() => import('@/pages/Monitor/Funnel'));

// 设置 - 个人中心
const SettingsProfile = lazy(() => import('@/pages/Settings/Profile'));

// 文件管理详情/子页
const FileListDetail = lazy(() => import('@/pages/FileList/Detail'));
const FileUploadBatch = lazy(() => import('@/pages/FileUpload/Batch'));
const FileSearchAdvanced = lazy(() => import('@/pages/FileSearch/Advanced'));
const FileAnalyzeCompare = lazy(() => import('@/pages/FileAnalyze/Compare'));
const FileAnalyzeReport = lazy(() => import('@/pages/FileAnalyze/Report'));

// 红方作战 - 详情/子页
const TargetProfileDetail = lazy(() => import('@/pages/redteam/TargetProfile/Detail'));
const ThreatIntelIocDetail = lazy(() => import('@/pages/redteam/ThreatIntel/IocDetail'));
const AttackChainDetail = lazy(() => import('@/pages/redteam/AttackChain/Detail'));
const VulnerabilityDetail = lazy(() => import('@/pages/redteam/Vulnerability/Detail'));
const ArsenalDetail = lazy(() => import('@/pages/redteam/Arsenal/Detail'));
const CollaborationTaskBoard = lazy(() => import('@/pages/redteam/Collaboration/TaskBoard'));
const CollaborationTeam = lazy(() => import('@/pages/redteam/Collaboration/Team'));

// 后台管理 - 详情/子页
const UserManageDetail = lazy(() => import('@/pages/admin/UserManage/Detail'));
const AuditLogDetail = lazy(() => import('@/pages/admin/AuditLog/Detail'));
const DataSourceDetail = lazy(() => import('@/pages/admin/DataSource/Detail'));
const ModelManageDetail = lazy(() => import('@/pages/admin/ModelManage/Detail'));
const ReportCenterPreview = lazy(() => import('@/pages/admin/ReportCenter/Preview'));

// 后台管理模块
const UserManage = lazy(() => import('@/pages/admin/UserManage'));
const RoleManage = lazy(() => import('@/pages/admin/RoleManage'));
const PermissionManage = lazy(() => import('@/pages/admin/PermissionManage'));
const YaraRuleManage = lazy(() => import('@/pages/admin/YaraRuleManage'));
const SystemConfig = lazy(() => import('@/pages/admin/SystemConfig'));
const AuditLog = lazy(() => import('@/pages/admin/AuditLog'));
const DataSource = lazy(() => import('@/pages/admin/DataSource'));
const ModelManage = lazy(() => import('@/pages/admin/ModelManage'));
const HealthCheck = lazy(() => import('@/pages/admin/HealthCheck'));
const ReportCenter = lazy(() => import('@/pages/admin/ReportCenter'));
const NotificationCenter = lazy(() => import('@/pages/admin/NotificationCenter'));
const TagManage = lazy(() => import('@/pages/admin/TagManage'));
const WorkflowDesigner = lazy(() => import('@/pages/admin/WorkflowDesigner'));
const WorkflowDesignerList = lazy(() => import('@/pages/admin/WorkflowDesigner/List'));
const DataMasking = lazy(() => import('@/pages/admin/DataMasking'));

// 应用运维模块（D1-D7）
const OpsSpaces = lazy(() => import('@/pages/ops/Spaces'));
const OpsSpaceDetail = lazy(() => import('@/pages/ops/Spaces/Detail'));
const OpsConsistency = lazy(() => import('@/pages/ops/Consistency'));
const OpsHeal = lazy(() => import('@/pages/ops/Heal'));
const OpsLifecycle = lazy(() => import('@/pages/ops/Lifecycle'));
const OpsConfig = lazy(() => import('@/pages/ops/Config'));
const OpsSecurity = lazy(() => import('@/pages/ops/Security'));
const OpsReports = lazy(() => import('@/pages/ops/Reports'));
const OpsTickets = lazy(() => import('@/pages/ops/Tickets'));

// V5.1 AI Agent 化模块
const AgentAnalysis = lazy(() => import('@/pages/ai/AgentAnalysis'));
const KnowledgeBase = lazy(() => import('@/pages/ai/KnowledgeBase'));

/** 加载中占位 */
const Loading: React.FC = () => (
  <div
    style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100%',
      minHeight: 200,
    }}
  >
    <Spin size="large" tip="加载中..." />
  </div>
);

/** 懒加载包装器 */
const LazyLoad = (Component: React.LazyExoticComponent<React.FC>) => (
  <Suspense fallback={<Loading />}>
    <Component />
  </Suspense>
);

/** 路由守卫 - 检查登录状态 */
const AuthGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

/** 路由配置 */
export const router = createBrowserRouter([
  {
    path: '/login',
    element: LazyLoad(Login),
  },
  {
    path: '/',
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: 'dashboard',
        element: LazyLoad(Dashboard),
      },
      {
        path: 'files',
        element: LazyLoad(FileList),
      },
      {
        path: 'files/:id',
        element: LazyLoad(FileListDetail),
      },
      {
        path: 'files/upload',
        element: LazyLoad(FileUpload),
      },
      {
        path: 'files/upload/batch',
        element: LazyLoad(FileUploadBatch),
      },
      {
        path: 'search',
        element: LazyLoad(FileSearch),
      },
      {
        path: 'search/advanced',
        element: LazyLoad(FileSearchAdvanced),
      },
      {
        path: 'analyze',
        element: LazyLoad(FileAnalyze),
      },
      {
        path: 'analyze/compare',
        element: LazyLoad(FileAnalyzeCompare),
      },
      {
        path: 'analyze/report',
        element: LazyLoad(FileAnalyzeReport),
      },
      {
        path: 'ioc',
        element: LazyLoad(IocCenter),
      },
      {
        path: 'monitor',
        element: LazyLoad(Monitor),
      },
      {
        path: 'monitor/slo',
        element: LazyLoad(MonitorSlo),
      },
      {
        path: 'monitor/funnel',
        element: LazyLoad(MonitorFunnel),
      },
      {
        path: 'settings',
        element: LazyLoad(Settings),
      },
      {
        path: 'settings/profile',
        element: LazyLoad(SettingsProfile),
      },
      // 红方作战模块
      {
        path: 'redteam/target-profile',
        element: LazyLoad(TargetProfile),
      },
      {
        path: 'redteam/target-profile/:id',
        element: LazyLoad(TargetProfileDetail),
      },
      {
        path: 'redteam/threat-intel',
        element: LazyLoad(ThreatIntel),
      },
      {
        path: 'redteam/threat-intel/ioc/:id',
        element: LazyLoad(ThreatIntelIocDetail),
      },
      {
        path: 'redteam/attack-chain',
        element: LazyLoad(AttackChain),
      },
      {
        path: 'redteam/attack-chain/:id',
        element: LazyLoad(AttackChainDetail),
      },
      {
        path: 'redteam/vulnerability',
        element: LazyLoad(Vulnerability),
      },
      {
        path: 'redteam/vulnerability/:id',
        element: LazyLoad(VulnerabilityDetail),
      },
      {
        path: 'redteam/arsenal',
        element: LazyLoad(Arsenal),
      },
      {
        path: 'redteam/arsenal/:id',
        element: LazyLoad(ArsenalDetail),
      },
      {
        path: 'redteam/collaboration',
        element: LazyLoad(Collaboration),
      },
      {
        path: 'redteam/collaboration/task-board',
        element: LazyLoad(CollaborationTaskBoard),
      },
      {
        path: 'redteam/collaboration/team',
        element: LazyLoad(CollaborationTeam),
      },
      {
        path: 'redteam/relation-graph',
        element: LazyLoad(RelationGraph),
      },
      {
        path: 'redteam/tasks',
        element: LazyLoad(TaskManage),
      },
      // V5.3 威胁狩猎模块
      {
        path: 'hunting/workbench',
        element: LazyLoad(HuntingWorkbench),
      },
      {
        path: 'hunting/rules',
        element: LazyLoad(HuntingRules),
      },
      // 后台管理模块
      {
        path: 'admin/users',
        element: LazyLoad(UserManage),
      },
      {
        path: 'admin/users/:id',
        element: LazyLoad(UserManageDetail),
      },
      {
        path: 'admin/roles',
        element: LazyLoad(RoleManage),
      },
      {
        path: 'admin/permissions',
        element: LazyLoad(PermissionManage),
      },
      {
        path: 'admin/yara-rules',
        element: LazyLoad(YaraRuleManage),
      },
      {
        path: 'admin/config',
        element: LazyLoad(SystemConfig),
      },
      {
        path: 'admin/audit-log',
        element: LazyLoad(AuditLog),
      },
      {
        path: 'admin/audit-log/:id',
        element: LazyLoad(AuditLogDetail),
      },
      {
        path: 'admin/data-sources',
        element: LazyLoad(DataSource),
      },
      {
        path: 'admin/data-sources/:id',
        element: LazyLoad(DataSourceDetail),
      },
      {
        path: 'admin/models',
        element: LazyLoad(ModelManage),
      },
      {
        path: 'admin/models/:id',
        element: LazyLoad(ModelManageDetail),
      },
      {
        path: 'admin/health',
        element: LazyLoad(HealthCheck),
      },
      {
        path: 'admin/reports',
        element: LazyLoad(ReportCenter),
      },
      {
        path: 'admin/reports/preview/:id',
        element: LazyLoad(ReportCenterPreview),
      },
      {
        path: 'admin/notifications',
        element: LazyLoad(NotificationCenter),
      },
      {
        path: 'admin/tags',
        element: LazyLoad(TagManage),
      },
      // 工作流设计器模块
      {
        path: 'admin/workflows',
        element: LazyLoad(WorkflowDesignerList),
      },
      {
        path: 'admin/workflows/new',
        element: LazyLoad(WorkflowDesigner),
      },
      {
        path: 'admin/workflows/:id',
        element: LazyLoad(WorkflowDesigner),
      },
      {
        path: 'admin/data-masking',
        element: LazyLoad(DataMasking),
      },
      // 应用运维模块（D1-D7）
      {
        path: 'ops/spaces',
        element: LazyLoad(OpsSpaces),
      },
      {
        path: 'ops/spaces/:id',
        element: LazyLoad(OpsSpaceDetail),
      },
      {
        path: 'ops/consistency',
        element: LazyLoad(OpsConsistency),
      },
      {
        path: 'ops/heal',
        element: LazyLoad(OpsHeal),
      },
      {
        path: 'ops/lifecycle',
        element: LazyLoad(OpsLifecycle),
      },
      {
        path: 'ops/config',
        element: LazyLoad(OpsConfig),
      },
      {
        path: 'ops/security',
        element: LazyLoad(OpsSecurity),
      },
      {
        path: 'ops/reports',
        element: LazyLoad(OpsReports),
      },
      {
        path: 'ops/tickets',
        element: LazyLoad(OpsTickets),
      },
      // V5.1 AI Agent 化模块
      {
        path: 'ai/agent',
        element: LazyLoad(AgentAnalysis),
      },
      {
        path: 'ai/knowledge',
        element: LazyLoad(KnowledgeBase),
      },
    ],
  },
  {
    path: '*',
    element: LazyLoad(NotFound),
  },
]);

export default router;
