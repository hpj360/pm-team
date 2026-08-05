/**
 * 报告中心页面
 * - ProTable：报告列表，支持关键字、类型、状态筛选
 * - 生成报告：模态框，可选模板、目标、格式
 * - 预览：模态框内 iframe 渲染 HTML 报告
 * - 导出：PDF / HTML / Markdown 三种格式
 * - 详情抽屉：报告元信息 + 摘要 + 预览
 * - 定时报告 Tab：管理定时报告配置（增删改查、启停、查看历史）
 * - 可访问性：WCAG AA
 */
import React, { useMemo, useRef, useState } from 'react';
import {
  Card,
  Typography,
  Button,
  Space,
  Tag,
  Drawer,
  Modal,
  Form,
  Input,
  Select,
  Popconfirm,
  message,
  Tooltip,
  Statistic,
  Row,
  Col,
  Tabs,
  InputNumber,
  Spin,
  Empty,
} from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import {
  PlusOutlined,
  ReloadOutlined,
  EyeOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FileTextOutlined,
  FilePdfOutlined,
  FileMarkdownOutlined,
  InboxOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  HistoryOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
} from '@ant-design/icons';
import {
  getReports,
  getReportDetail,
  getReportTemplates,
  generateReport,
  exportReport,
  deleteReport,
  archiveReport,
  getReportSchedules,
  createReportSchedule,
  toggleReportSchedule,
  deleteReportSchedule,
  getReportScheduleHistory,
} from '@/services';
import {
  ReportTypeLabel,
  ReportStatusLabel,
  ReportFormatLabel,
  ScheduleReportTypeLabel,
  ScheduleStatusLabel,
  ScheduleRunStatusLabel,
} from '@/types';
import type {
  ReportItem,
  ReportType,
  ReportFormat,
  ReportTemplate,
  ReportSchedule,
  ScheduleReportType,
  ScheduleStatus,
  ScheduleRunStatus,
  ReportScheduleHistory,
} from '@/types';
import { formatDateTime, formatFileSize } from '@/utils';
import { getAriaLabel } from '@/utils/accessibility';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 报告状态颜色 */
const statusColor: Record<ReportItem['status'], string> = {
  draft: 'default',
  generating: 'processing',
  completed: 'success',
  failed: 'error',
  archived: 'warning',
};

/** 报告状态图标 */
const statusIcon: Record<ReportItem['status'], React.ReactNode> = {
  draft: <FileTextOutlined />,
  generating: <ClockCircleOutlined />,
  completed: <CheckCircleOutlined />,
  failed: <ExclamationCircleOutlined />,
  archived: <InboxOutlined />,
};

/** 报告类型颜色 */
const typeColor: Record<ReportType, string> = {
  penetration: 'red',
  vulnerability: 'orange',
  threat_intel: 'purple',
  attack_chain: 'magenta',
  asset: 'blue',
  audit: 'cyan',
};

/** 格式图标 */
const formatIcon: Record<ReportFormat, React.ReactNode> = {
  pdf: <FilePdfOutlined style={{ color: '#f5222d' }} />,
  html: <FileTextOutlined style={{ color: '#1890ff' }} />,
  markdown: <FileMarkdownOutlined style={{ color: '#595959' }} />,
};

/** 生成报告表单值 */
interface GenerateFormValues {
  title: string;
  templateId: string;
  targetId?: string;
  format: ReportFormat;
}

/** 定时报告表单值 */
interface ScheduleFormValues {
  reportName: string;
  reportType: ScheduleReportType;
  cronExpression: string;
  recipients: string;
  templateName?: string;
  targetId?: number;
}

/** 定时报告状态颜色 */
const scheduleStatusColor: Record<ScheduleStatus, string> = {
  ACTIVE: 'success',
  INACTIVE: 'default',
};

/** 定时报告执行状态颜色 */
const scheduleRunStatusColor: Record<ScheduleRunStatus, string> = {
  SUCCESS: 'success',
  FAILED: 'error',
  RUNNING: 'processing',
  PENDING: 'default',
};

/** 定时报告类型颜色 */
const scheduleTypeColor: Record<ScheduleReportType, string> = {
  'target-profile': 'blue',
  'penetration-test': 'red',
  'vulnerability-scan': 'orange',
  'attack-chain': 'magenta',
  'task-summary': 'cyan',
};

/**
 * 报告中心主组件
 */
const ReportCenterPage: React.FC = () => {
  const actionRef = useRef<ActionType>(null);
  const [detail, setDetail] = useState<ReportItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewHtml, setPreviewHtml] = useState<string>('');
  const [previewTitle, setPreviewTitle] = useState<string>('');
  const [submitting, setSubmitting] = useState(false);
  const [templates, setTemplates] = useState<ReportTemplate[]>([]);
  const [form] = Form.useForm<GenerateFormValues>();
  const [stats, setStats] = useState({ total: 0, completed: 0, generating: 0, failed: 0 });

  // 定时报告相关状态
  const scheduleActionRef = useRef<ActionType>(null);
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);
  const [scheduleSubmitting, setScheduleSubmitting] = useState(false);
  const [scheduleHistoryOpen, setScheduleHistoryOpen] = useState(false);
  const [scheduleHistoryLoading, setScheduleHistoryLoading] = useState(false);
  const [scheduleHistory, setScheduleHistory] = useState<ReportScheduleHistory[]>([]);
  const [scheduleHistoryRecord, setScheduleHistoryRecord] = useState<ReportSchedule | null>(null);
  const [scheduleForm] = Form.useForm<ScheduleFormValues>();
  const [activeTab, setActiveTab] = useState<string>('reports');

  /** 加载模板列表 */
  React.useEffect(() => {
    getReportTemplates().then((res) => setTemplates(res.data));
  }, []);

  /** 加载统计 */
  const fetchStats = async () => {
    const res = await getReports();
    if (res.code === 200 || res.code === 0) {
      const list = res.data;
      setStats({
        total: list.length,
        completed: list.filter((r) => r.status === 'completed').length,
        generating: list.filter((r) => r.status === 'generating').length,
        failed: list.filter((r) => r.status === 'failed').length,
      });
    }
  };

  React.useEffect(() => {
    fetchStats();
  }, []);

  /** 打开详情抽屉 */
  const openDetail = (record: ReportItem) => {
    getReportDetail(record.id).then((res) => {
      setDetail(res.data);
      setDrawerOpen(true);
    });
  };

  /** 打开生成报告模态框 */
  const openGenerate = () => {
    form.resetFields();
    if (templates.length > 0) {
      form.setFieldsValue({
        templateId: templates[0].id,
        format: templates[0].defaultFormat,
      });
    }
    setModalOpen(true);
  };

  /** 提交生成报告 */
  const handleSubmitGenerate = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await generateReport({
        templateId: values.templateId,
        title: values.title,
        targetId: values.targetId,
        format: values.format,
      });
      message.success('报告生成请求已提交，请稍后刷新查看');
      setModalOpen(false);
      actionRef.current?.reload();
      fetchStats();
    } catch {
      // 校验失败
    } finally {
      setSubmitting(false);
    }
  };

  /** 预览报告 */
  const handlePreview = (record: ReportItem) => {
    getReportDetail(record.id).then((res) => {
      const r = res.data;
      if (r.htmlContent) {
        setPreviewHtml(r.htmlContent);
        setPreviewTitle(r.title);
        setPreviewOpen(true);
      } else {
        message.warning('该报告暂不支持在线预览');
      }
    });
  };

  /** 导出报告 */
  const handleExport = async (record: ReportItem, format: ReportFormat) => {
    try {
      const res = await exportReport(record.id, format);
      if (res.data?.url) {
        message.success(`已生成 ${ReportFormatLabel[format]} 下载链接`);
        // 模拟下载
        window.open(res.data.url, '_blank');
      }
    } catch {
      message.error('导出失败');
    }
  };

  /** 归档 */
  const handleArchive = async (id: string) => {
    await archiveReport(id);
    message.success('报告已归档');
    actionRef.current?.reload();
    fetchStats();
  };

  /** 删除 */
  const handleDelete = async (id: string) => {
    await deleteReport(id);
    message.success('报告已删除');
    actionRef.current?.reload();
    fetchStats();
  };

  /* ============ 定时报告相关处理函数 ============ */

  /** 打开新建定时报告模态框 */
  const openCreateSchedule = () => {
    scheduleForm.resetFields();
    scheduleForm.setFieldsValue({
      reportType: 'target-profile',
      cronExpression: '0 0 8 * * ?',
    });
    setScheduleModalOpen(true);
  };

  /** 提交新建定时报告 */
  const handleSubmitCreateSchedule = async () => {
    try {
      const values = await scheduleForm.validateFields();
      setScheduleSubmitting(true);
      await createReportSchedule({
        reportName: values.reportName,
        reportType: values.reportType,
        cronExpression: values.cronExpression,
        recipients: values.recipients,
        templateName: values.templateName,
        targetId: values.targetId,
      });
      message.success('定时报告创建成功');
      setScheduleModalOpen(false);
      scheduleActionRef.current?.reload();
    } catch {
      // 校验失败
    } finally {
      setScheduleSubmitting(false);
    }
  };

  /** 启停切换 */
  const handleToggleSchedule = async (record: ReportSchedule) => {
    try {
      await toggleReportSchedule(record.id);
      message.success(`定时报告已${record.status === 'ACTIVE' ? '停用' : '启用'}`);
      scheduleActionRef.current?.reload();
    } catch {
      message.error('启停切换失败');
    }
  };

  /** 删除定时报告 */
  const handleDeleteSchedule = async (id: number | string) => {
    try {
      await deleteReportSchedule(id);
      message.success('定时报告已删除');
      scheduleActionRef.current?.reload();
    } catch {
      message.error('删除失败');
    }
  };

  /** 查看执行历史 */
  const handleViewHistory = async (record: ReportSchedule) => {
    setScheduleHistoryRecord(record);
    setScheduleHistoryOpen(true);
    setScheduleHistoryLoading(true);
    try {
      const res = await getReportScheduleHistory(record.id);
      if (res.code === 200 || res.code === 0) {
        setScheduleHistory(res.data);
      } else {
        setScheduleHistory([]);
      }
    } catch {
      setScheduleHistory([]);
    } finally {
      setScheduleHistoryLoading(false);
    }
  };

  /** 定时报告列定义 */
  const scheduleColumns: ProColumns<ReportSchedule>[] = useMemo(
    () => [
      {
        title: '报告名称',
        dataIndex: 'reportName',
        key: 'reportName',
        width: 200,
        render: (_, record) => <Text strong>{record.reportName}</Text>,
      },
      {
        title: '报告类型',
        dataIndex: 'reportType',
        key: 'reportType',
        width: 130,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          (Object.keys(ScheduleReportTypeLabel) as ScheduleReportType[]).map((t) => [
            t,
            { text: ScheduleReportTypeLabel[t] },
          ]),
        ),
        render: (_, record) => (
          <Tag color={scheduleTypeColor[record.reportType]}>
            {ScheduleReportTypeLabel[record.reportType]}
          </Tag>
        ),
      },
      {
        title: 'Cron 表达式',
        dataIndex: 'cronExpression',
        key: 'cronExpression',
        width: 160,
        hideInSearch: true,
        render: (_, record) => <Text code style={{ fontSize: 12 }}>{record.cronExpression}</Text>,
      },
      {
        title: '收件人',
        dataIndex: 'recipients',
        key: 'recipients',
        width: 220,
        hideInSearch: true,
        ellipsis: true,
        render: (_, record) => (
          <Tooltip title={record.recipients}>
            <Text style={{ fontSize: 12 }}>{record.recipients}</Text>
          </Tooltip>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 100,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          (Object.keys(ScheduleStatusLabel) as ScheduleStatus[]).map((s) => [
            s,
            { text: ScheduleStatusLabel[s] },
          ]),
        ),
        render: (_, record) => (
          <Tag color={scheduleStatusColor[record.status]}>
            {ScheduleStatusLabel[record.status]}
          </Tag>
        ),
      },
      {
        title: '上次执行时间',
        dataIndex: 'lastRunTime',
        key: 'lastRunTime',
        width: 170,
        hideInSearch: true,
        sorter: (a, b) => ((a.lastRunTime ?? '') < (b.lastRunTime ?? '') ? 1 : -1),
        render: (v: unknown) =>
          v ? <Text style={{ fontSize: 12 }}>{formatDateTime(v as string)}</Text> : <Text type="secondary">-</Text>,
      },
      {
        title: '上次执行状态',
        dataIndex: 'lastRunStatus',
        key: 'lastRunStatus',
        width: 120,
        hideInSearch: true,
        render: (_, record) =>
          record.lastRunStatus ? (
            <Tag color={scheduleRunStatusColor[record.lastRunStatus]}>
              {ScheduleRunStatusLabel[record.lastRunStatus]}
            </Tag>
          ) : (
            <Text type="secondary">-</Text>
          ),
      },
      {
        title: '操作',
        key: 'action',
        width: 220,
        fixed: 'right',
        hideInSearch: true,
        render: (_, record) => (
          <Space size={4} wrap>
            <Button
              type="link"
              size="small"
              icon={record.status === 'ACTIVE' ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
              onClick={() => handleToggleSchedule(record)}
            >
              {record.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
            <Button
              type="link"
              size="small"
              icon={<HistoryOutlined />}
              onClick={() => handleViewHistory(record)}
            >
              历史
            </Button>
            <Popconfirm
              title="确认删除该定时报告？"
              onConfirm={() => handleDeleteSchedule(record.id)}
            >
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

  /** 定时报告类型选项 */
  const scheduleTypeOptions = (Object.keys(ScheduleReportTypeLabel) as ScheduleReportType[]).map(
    (t) => ({
      label: ScheduleReportTypeLabel[t],
      value: t,
    }),
  );

  /** 模板名称选项（从已有模板中提取名称） */
  const templateNameOptions = templates.map((t) => ({
    label: t.name,
    value: t.name,
  }));

  /** 列定义 */
  const columns: ProColumns<ReportItem>[] = useMemo(
    () => [
      {
        title: '报告标题',
        dataIndex: 'title',
        key: 'title',
        width: 280,
        render: (_, record) => (
          <Button
            type="link"
            size="small"
            style={{ padding: 0, height: 'auto' }}
            onClick={() => openDetail(record)}
          >
            <Text strong>{record.title}</Text>
          </Button>
        ),
      },
      {
        title: '类型',
        dataIndex: 'type',
        key: 'type',
        width: 130,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          (Object.keys(ReportTypeLabel) as ReportType[]).map((t) => [
            t,
            { text: ReportTypeLabel[t] },
          ]),
        ),
        render: (_, record) => (
          <Tag color={typeColor[record.type]}>{ReportTypeLabel[record.type]}</Tag>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 110,
        valueType: 'select',
        valueEnum: Object.fromEntries(
          (Object.keys(ReportStatusLabel) as ReportItem['status'][]).map((s) => [
            s,
            {
              text: ReportStatusLabel[s],
              status:
                s === 'completed'
                  ? 'Success'
                  : s === 'generating'
                    ? 'Processing'
                    : s === 'failed'
                      ? 'Error'
                      : s === 'archived'
                        ? 'Warning'
                        : 'Default',
            },
          ]),
        ),
        render: (_, record) => (
          <Tag color={statusColor[record.status]} icon={statusIcon[record.status]}>
            {ReportStatusLabel[record.status]}
          </Tag>
        ),
      },
      {
        title: '模板',
        dataIndex: 'templateName',
        key: 'templateName',
        width: 200,
        ellipsis: true,
        hideInSearch: true,
      },
      {
        title: '目标',
        dataIndex: 'targetName',
        key: 'targetName',
        width: 130,
        ellipsis: true,
        hideInSearch: true,
        render: (v: unknown) =>
          v ? <Tag color="blue">{v as string}</Tag> : <Text type="secondary">-</Text>,
      },
      {
        title: '格式',
        dataIndex: 'format',
        key: 'format',
        width: 90,
        hideInSearch: true,
        render: (_, record) => (
          <Space size={4}>
            {formatIcon[record.format]}
            <Text style={{ fontSize: 12 }}>{ReportFormatLabel[record.format]}</Text>
          </Space>
        ),
      },
      {
        title: '生成者',
        dataIndex: 'creator',
        key: 'creator',
        width: 110,
        hideInSearch: true,
      },
      {
        title: '生成时间',
        dataIndex: 'generatedAt',
        key: 'generatedAt',
        width: 160,
        hideInSearch: true,
        sorter: (a, b) => ((a.generatedAt ?? '') < (b.generatedAt ?? '') ? 1 : -1),
        render: (v: unknown) =>
          v ? <Text style={{ fontSize: 12 }}>{formatDateTime(v as string)}</Text> : <Text type="secondary">-</Text>,
      },
      {
        title: '大小',
        dataIndex: 'fileSize',
        key: 'fileSize',
        width: 100,
        hideInSearch: true,
        render: (v: unknown) =>
          v ? <Text style={{ fontSize: 12 }}>{formatFileSize(v as number)}</Text> : <Text type="secondary">-</Text>,
      },
      {
        title: '操作',
        key: 'action',
        width: 260,
        fixed: 'right',
        hideInSearch: true,
        render: (_, record) => (
          <Space size={4} wrap>
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => openDetail(record)}
              aria-label={getAriaLabel('button.view', { label: record.title })}
            >
              详情
            </Button>
            {record.htmlContent && (
              <Button
                type="link"
                size="small"
                icon={<FileTextOutlined />}
                onClick={() => handlePreview(record)}
              >
                预览
              </Button>
            )}
            {record.status === 'completed' && (
              <Tooltip title="导出 PDF">
                <Button
                  type="link"
                  size="small"
                  icon={<DownloadOutlined />}
                  onClick={() => handleExport(record, 'pdf')}
                />
              </Tooltip>
            )}
            {record.status === 'completed' && (
              <Popconfirm
                title="确认归档该报告？"
                onConfirm={() => handleArchive(record.id)}
              >
                <Button type="link" size="small" icon={<InboxOutlined />}>
                  归档
                </Button>
              </Popconfirm>
            )}
            <Popconfirm
              title="确认删除该报告？"
              onConfirm={() => handleDelete(record.id)}
              aria-label={getAriaLabel('button.delete', { label: record.title })}
            >
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

  /** 格式选项 */
  const formatOptions = (Object.keys(ReportFormatLabel) as ReportFormat[]).map((f) => ({
    label: ReportFormatLabel[f],
    value: f,
  }));

  return (
    <div>
      <Title level={4}>报告中心</Title>
      <Paragraph type="secondary" style={{ marginTop: -4, marginBottom: 16 }}>
        红方作业成果汇总：支持按模板生成报告，多格式导出（PDF / HTML / Markdown），可在线预览、归档与删除。
      </Paragraph>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'reports',
            label: (
              <span>
                <FileTextOutlined /> 报告列表
              </span>
            ),
            children: (
              <>
                {/* 统计卡片 */}
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col xs={12} md={6}>
                    <Card size="small">
                      <Statistic title="报告总数" value={stats.total} prefix={<FileTextOutlined />} />
                    </Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small">
                      <Statistic
                        title="已完成"
                        value={stats.completed}
                        valueStyle={{ color: colors.success }}
                        prefix={<CheckCircleOutlined />}
                      />
                    </Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small">
                      <Statistic
                        title="生成中"
                        value={stats.generating}
                        valueStyle={{ color: colors.severity.info }}
                        prefix={<ClockCircleOutlined />}
                      />
                    </Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small">
                      <Statistic
                        title="失败"
                        value={stats.failed}
                        valueStyle={{ color: colors.severity.critical }}
                        prefix={<ExclamationCircleOutlined />}
                      />
                    </Card>
                  </Col>
                </Row>

                <Card>
                  <ProTable<ReportItem>
                    actionRef={actionRef}
                    columns={columns}
                    rowKey="id"
                    search={{ labelWidth: 80 }}
                    request={async (params) => {
                      const res = await getReports({
                        keyword: params.title,
                        type: params.type as ReportType | undefined,
                        status: params.status as ReportItem['status'] | undefined,
                      });
                      return {
                        data: res.data,
                        total: res.data.length,
                        success: true,
                      };
                    }}
                    pagination={{ pageSize: 10, showSizeChanger: true }}
                    scroll={{ x: 1500 }}
                    toolBarRender={() => [
                      <Button
                        key="reload"
                        icon={<ReloadOutlined />}
                        onClick={() => {
                          actionRef.current?.reload();
                          fetchStats();
                        }}
                        aria-label={getAriaLabel('button.refresh')}
                      >
                        刷新
                      </Button>,
                      <Button
                        key="generate"
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={openGenerate}
                        aria-label={getAriaLabel('button.submit')}
                      >
                        生成报告
                      </Button>,
                    ]}
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'schedules',
            label: (
              <span>
                <ClockCircleOutlined /> 定时报告
              </span>
            ),
            children: (
              <Card>
                <ProTable<ReportSchedule>
                  actionRef={scheduleActionRef}
                  columns={scheduleColumns}
                  rowKey="id"
                  search={{ labelWidth: 80 }}
                  request={async (params) => {
                    const res = await getReportSchedules({
                      page: params.current ?? 1,
                      size: params.pageSize ?? 10,
                      keyword: params.reportName,
                      reportType: params.reportType as ScheduleReportType | undefined,
                      status: params.status as ScheduleStatus | undefined,
                    });
                    return {
                      data: res.data.list,
                      total: res.data.total,
                      success: true,
                    };
                  }}
                  pagination={{ pageSize: 10, showSizeChanger: true }}
                  scroll={{ x: 1300 }}
                  toolBarRender={() => [
                    <Button
                      key="reload-schedule"
                      icon={<ReloadOutlined />}
                      onClick={() => scheduleActionRef.current?.reload()}
                      aria-label={getAriaLabel('button.refresh')}
                    >
                      刷新
                    </Button>,
                    <Button
                      key="create-schedule"
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={openCreateSchedule}
                      aria-label={getAriaLabel('button.submit')}
                    >
                      新建定时报告
                    </Button>,
                  ]}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 详情抽屉 */}
      <Drawer
        title="报告详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={640}
        destroyOnClose
      >
        {detail && (
          <>
            <div style={{ marginBottom: 12 }}>
              <Title level={5} style={{ marginBottom: 4 }}>
                {detail.title}
              </Title>
              <Space size={6} wrap>
                <Tag color={typeColor[detail.type]}>{ReportTypeLabel[detail.type]}</Tag>
                <Tag color={statusColor[detail.status]} icon={statusIcon[detail.status]}>
                  {ReportStatusLabel[detail.status]}
                </Tag>
                <Tag color="blue">{detail.templateName}</Tag>
                {detail.targetName && <Tag color="geekblue">{detail.targetName}</Tag>}
              </Space>
            </div>

            {detail.summary && (
              <Paragraph type="secondary" style={{ background: colors.neutral[50], padding: 12, borderRadius: 4 }}>
                {detail.summary}
              </Paragraph>
            )}

            <div style={{ margin: '16px 0' }}>
              <Text strong>基本信息</Text>
              <div style={{ marginTop: 8, fontSize: 13, lineHeight: 2 }}>
                <div>
                  <Text type="secondary">报告 ID：</Text>
                  <Text code>{detail.id}</Text>
                </div>
                <div>
                  <Text type="secondary">生成者：</Text>
                  <Text>{detail.creator}</Text>
                </div>
                {detail.generatedAt && (
                  <div>
                    <Text type="secondary">生成时间：</Text>
                    <Text>{formatDateTime(detail.generatedAt)}</Text>
                  </div>
                )}
                <div>
                  <Text type="secondary">格式：</Text>
                  <Space size={4}>
                    {formatIcon[detail.format]}
                    <Text>{ReportFormatLabel[detail.format]}</Text>
                  </Space>
                </div>
                {detail.fileSize && (
                  <div>
                    <Text type="secondary">文件大小：</Text>
                    <Text>{formatFileSize(detail.fileSize)}</Text>
                  </div>
                )}
                <div>
                  <Text type="secondary">创建时间：</Text>
                  <Text>{formatDateTime(detail.createTime)}</Text>
                </div>
                <div>
                  <Text type="secondary">更新时间：</Text>
                  <Text>{formatDateTime(detail.updateTime)}</Text>
                </div>
              </div>
            </div>

            {detail.fileNames && detail.fileNames.length > 0 && (
              <div style={{ margin: '16px 0' }}>
                <Text strong>关联文件</Text>
                <div style={{ marginTop: 8 }}>
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    {detail.fileNames.map((f) => (
                      <div
                        key={f}
                        style={{
                          padding: '6px 10px',
                          background: colors.neutral[50],
                          borderRadius: 4,
                          fontSize: 12,
                        }}
                      >
                        <FileTextOutlined style={{ marginRight: 6 }} />
                        {f}
                      </div>
                    ))}
                  </Space>
                </div>
              </div>
            )}

            {detail.tags && detail.tags.length > 0 && (
              <div style={{ margin: '16px 0' }}>
                <Text strong>标签</Text>
                <div style={{ marginTop: 8 }}>
                  <Space wrap size={[4, 4]}>
                    {detail.tags.map((t) => (
                      <Tag key={t}>{t}</Tag>
                    ))}
                  </Space>
                </div>
              </div>
            )}

            <div style={{ marginTop: 24, borderTop: `1px solid ${colors.neutral[200]}`, paddingTop: 12 }}>
              <Space wrap>
                {detail.htmlContent && (
                  <Button
                    type="primary"
                    icon={<EyeOutlined />}
                    onClick={() => handlePreview(detail)}
                  >
                    在线预览
                  </Button>
                )}
                {detail.status === 'completed' && (
                  <>
                    <Button icon={<DownloadOutlined />} onClick={() => handleExport(detail, 'pdf')}>
                      导出 PDF
                    </Button>
                    <Button icon={<DownloadOutlined />} onClick={() => handleExport(detail, 'html')}>
                      导出 HTML
                    </Button>
                    <Button icon={<DownloadOutlined />} onClick={() => handleExport(detail, 'markdown')}>
                      导出 Markdown
                    </Button>
                  </>
                )}
                <Popconfirm title="确认删除该报告？" onConfirm={() => {
                  handleDelete(detail.id);
                  setDrawerOpen(false);
                }}>
                  <Button danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            </div>
          </>
        )}
      </Drawer>

      {/* 生成报告模态框 */}
      <Modal
        title="生成报告"
        open={modalOpen}
        onOk={handleSubmitGenerate}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="title"
            label="报告标题"
            rules={[{ required: true, message: '请输入报告标题' }]}
          >
            <Input placeholder="例如：MetaTech 2026 Q3 渗透测试报告" />
          </Form.Item>
          <Form.Item
            name="templateId"
            label="报告模板"
            rules={[{ required: true, message: '请选择模板' }]}
          >
            <Select
              placeholder="选择模板"
              options={templates.map((t) => ({
                label: `${t.name}（${ReportTypeLabel[t.type]}）`,
                value: t.id,
              }))}
            />
          </Form.Item>
          <Form.Item name="targetId" label="关联目标">
            <Input placeholder="目标 ID（可选）" />
          </Form.Item>
          <Form.Item name="format" label="导出格式" rules={[{ required: true }]}>
            <Select options={formatOptions} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 预览模态框 */}
      <Modal
        title={previewTitle}
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={null}
        width={960}
        destroyOnClose
      >
        <div
          style={{
            border: `1px solid ${colors.neutral[200]}`,
            borderRadius: 4,
            maxHeight: '70vh',
            overflow: 'auto',
          }}
        >
          <iframe
            title={previewTitle}
            srcDoc={previewHtml}
            style={{ width: '100%', height: '70vh', border: 'none' }}
          />
        </div>
      </Modal>

      {/* 新建定时报告模态框 */}
      <Modal
        title="新建定时报告"
        open={scheduleModalOpen}
        onOk={handleSubmitCreateSchedule}
        onCancel={() => setScheduleModalOpen(false)}
        confirmLoading={scheduleSubmitting}
        width={600}
        destroyOnClose
      >
        <Form form={scheduleForm} layout="vertical" preserve={false}>
          <Form.Item
            name="reportName"
            label="报告名称"
            rules={[{ required: true, message: '请输入报告名称' }]}
          >
            <Input placeholder="例如：每日目标画像报告" />
          </Form.Item>
          <Form.Item
            name="reportType"
            label="报告类型"
            rules={[{ required: true, message: '请选择报告类型' }]}
          >
            <Select options={scheduleTypeOptions} placeholder="选择报告类型" />
          </Form.Item>
          <Form.Item
            name="cronExpression"
            label="Cron 表达式"
            rules={[{ required: true, message: '请输入 Cron 表达式' }]}
            extra={
              <Text type="secondary" style={{ fontSize: 12 }}>
                示例：<Text code>0 0 8 * * ?</Text>（每天 8 点）、
                <Text code>0 0 9 ? * MON</Text>（每周一 9 点）、
                <Text code>0 30 7 * * ?</Text>（每天 7:30）
              </Text>
            }
          >
            <Input placeholder="0 0 8 * * ?" />
          </Form.Item>
          <Form.Item
            name="recipients"
            label="收件人"
            rules={[
              { required: true, message: '请输入收件人邮箱' },
              {
                validator: (_, value: string) => {
                  if (!value) return Promise.resolve();
                  const emails = value.split(',').map((s) => s.trim()).filter(Boolean);
                  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                  const invalid = emails.find((e) => !emailRegex.test(e));
                  if (invalid) {
                    return Promise.reject(new Error(`邮箱格式不正确：${invalid}`));
                  }
                  return Promise.resolve();
                },
              },
            ]}
            extra={
              <Text type="secondary" style={{ fontSize: 12 }}>
                多个邮箱用英文逗号分隔，例如：admin@redteam.com,analyst@redteam.com
              </Text>
            }
          >
            <Input.TextArea
              rows={2}
              placeholder="admin@redteam.com,analyst@redteam.com"
            />
          </Form.Item>
          <Form.Item name="templateName" label="模板（可选）">
            <Select
              options={templateNameOptions}
              placeholder="选择模板"
              allowClear
            />
          </Form.Item>
          <Form.Item name="targetId" label="目标 ID（可选）">
            <InputNumber
              placeholder="例如：1"
              min={1}
              style={{ width: '100%' }}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 定时报告执行历史抽屉 */}
      <Drawer
        title={
          scheduleHistoryRecord
            ? `执行历史 - ${scheduleHistoryRecord.reportName}`
            : '执行历史'
        }
        open={scheduleHistoryOpen}
        onClose={() => {
          setScheduleHistoryOpen(false);
          setScheduleHistory([]);
          setScheduleHistoryRecord(null);
        }}
        width={640}
        destroyOnClose
      >
        {scheduleHistoryLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin tip="加载历史记录..." />
          </div>
        ) : scheduleHistory.length === 0 ? (
          <Empty description="暂无执行历史" />
        ) : (
          <>
            {scheduleHistoryRecord && (
              <div style={{ marginBottom: 16 }}>
                <Space size={6} wrap>
                  <Tag color={scheduleTypeColor[scheduleHistoryRecord.reportType]}>
                    {ScheduleReportTypeLabel[scheduleHistoryRecord.reportType]}
                  </Tag>
                  <Tag color={scheduleStatusColor[scheduleHistoryRecord.status]}>
                    {ScheduleStatusLabel[scheduleHistoryRecord.status]}
                  </Tag>
                  <Text code style={{ fontSize: 12 }}>
                    {scheduleHistoryRecord.cronExpression}
                  </Text>
                </Space>
              </div>
            )}
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              {scheduleHistory.map((item) => (
                <Card
                  key={item.id}
                  size="small"
                  style={{
                    borderLeft: `3px solid ${
                      item.status === 'SUCCESS'
                        ? colors.success
                        : item.status === 'FAILED'
                          ? colors.severity.critical
                          : colors.severity.info
                    }`,
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Space size={6}>
                      <Tag color={scheduleRunStatusColor[item.status]}>
                        {ScheduleRunStatusLabel[item.status]}
                      </Tag>
                      {item.trigger && (
                        <Tag>{item.trigger === 'cron' ? '定时触发' : '手动触发'}</Tag>
                      )}
                    </Space>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {formatDateTime(item.runTime)}
                    </Text>
                  </div>
                  <div style={{ marginTop: 8, fontSize: 12, color: colors.neutral[600] }}>
                    {item.costMs !== undefined && (
                      <span>耗时：{(item.costMs / 1000).toFixed(2)}s</span>
                    )}
                    {item.reportId && (
                      <span style={{ marginLeft: 12 }}>
                        报告 ID：<Text code>{item.reportId}</Text>
                      </span>
                    )}
                  </div>
                  {item.errorMessage && (
                    <div style={{ marginTop: 8, padding: 8, background: colors.neutral[50], borderRadius: 4, fontSize: 12, color: colors.severity.critical }}>
                      <ExclamationCircleOutlined style={{ marginRight: 6 }} />
                      {item.errorMessage}
                    </div>
                  )}
                </Card>
              ))}
            </Space>
          </>
        )}
      </Drawer>
    </div>
  );
};

export default ReportCenterPage;
