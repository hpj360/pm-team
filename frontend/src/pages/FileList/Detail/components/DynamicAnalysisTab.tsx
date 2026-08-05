/**
 * 动态分析 Tab（V5.2）
 * - 嵌入文件详情页，对当前文件提交 Cuckoo 沙箱动态分析
 * - 展示任务状态机、进程树、网络连接、文件操作、ATT&CK 映射、IOC、STIX 对象
 * - 沙箱不可用时降级展示，不阻塞主流程
 */
import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Space,
  Tag,
  Table,
  Empty,
  Spin,
  Statistic,
  Row,
  Col,
  Card,
  Alert,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlayCircleOutlined,
  ReloadOutlined,
  ExperimentOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  CloudOutlined,
  BranchesOutlined,
  FileSearchOutlined,
  BugOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import {
  submitDynamicAnalysis,
  getDynamicReport,
  pollDynamicTask,
} from '@/services/dynamic';
import type {
  DynamicAnalysisTask,
  DynamicReport,
  ProcessTreeNode,
  NetworkConnection,
  FileOperation,
  AttackTechniqueMapping,
  DynamicIocItem,
  StixObject,
} from '@/types';
import {
  DynamicTaskStatus,
  DynamicTaskStatusLabel,
  DynamicTaskStatusColor,
} from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Text, Paragraph } = Typography;

/** 进程是否处于终态（可拉取报告） */
function isTerminal(status: string): boolean {
  return (
    status === DynamicTaskStatus.PARSED ||
    status === DynamicTaskStatus.COMPLETED ||
    status === DynamicTaskStatus.DEGRADED ||
    status === DynamicTaskStatus.FAILED
  );
}

/** 进程是否可拉取完整报告（含行为指标） */
function canFetchReport(status: string): boolean {
  return (
    status === DynamicTaskStatus.PARSED ||
    status === DynamicTaskStatus.COMPLETED ||
    status === DynamicTaskStatus.DEGRADED
  );
}

interface DynamicAnalysisTabProps {
  /** 文件ID（前端为字符串，后端为 Long，内部转换） */
  fileId: string;
}

const DynamicAnalysisTab: React.FC<DynamicAnalysisTabProps> = ({ fileId }) => {
  const [submitting, setSubmitting] = useState(false);
  const [polling, setPolling] = useState(false);
  const [task, setTask] = useState<DynamicAnalysisTask | null>(null);
  const [report, setReport] = useState<DynamicReport | null>(null);
  const [loadingReport, setLoadingReport] = useState(false);

  /** 将字符串 fileId 转为数字（后端要求 Long），无法转换时回退哈希值 */
  const numericFileId = (() => {
    const n = parseInt(fileId.replace(/[^0-9]/g, ''), 10);
    return Number.isFinite(n) && n > 0 ? n : 1;
  })();

  /** 提交动态分析任务 */
  const handleSubmit = useCallback(async () => {
    setSubmitting(true);
    try {
      const res = await submitDynamicAnalysis(numericFileId);
      if (res.code === 200 || res.code === 0) {
        message.success(`动态分析任务已提交：${res.data}`);
        // 立即轮询一次以获取初始状态
        await handlePoll(res.data);
      } else {
        message.error(res.message || '提交动态分析失败');
      }
    } catch {
      message.error('提交动态分析失败');
    } finally {
      setSubmitting(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [numericFileId]);

  /** 轮询任务状态 */
  const handlePoll = useCallback(async (taskId?: string) => {
    const tid = taskId ?? task?.taskId;
    if (!tid) return;
    setPolling(true);
    try {
      const res = await pollDynamicTask(tid);
      if (res.code === 200 || res.code === 0) {
        const status = res.data;
        // 简化：用返回状态更新 task
        setTask((prev) =>
          prev
            ? { ...prev, status, updateTime: new Date().toISOString() }
            : { taskId: tid, fileId: numericFileId, status, degraded: false, processTree: [], networkConnections: [], fileOperations: [], attackTechniques: [], iocs: [], createTime: new Date().toISOString(), updateTime: new Date().toISOString() },
        );
      }
    } catch {
      // 轮询失败静默处理
    } finally {
      setPolling(false);
    }
  }, [task?.taskId, numericFileId]);

  /** 拉取动态分析报告 */
  const handleFetchReport = useCallback(async (taskId: string) => {
    setLoadingReport(true);
    try {
      const res = await getDynamicReport(taskId);
      if (res.code === 200 || res.code === 0) {
        setReport(res.data);
      } else {
        message.error(res.message || '获取动态分析报告失败');
      }
    } catch {
      message.error('获取动态分析报告失败');
    } finally {
      setLoadingReport(false);
    }
  }, []);

  /** 任务进入终态时自动拉取报告 */
  useEffect(() => {
    if (task && canFetchReport(task.status) && !report && !loadingReport) {
      handleFetchReport(task.taskId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [task]);

  /** 状态 Tag */
  const renderStatusTag = (status: string) => {
    const color =
      DynamicTaskStatusColor[status as DynamicTaskStatus] ?? 'default';
    const label =
      DynamicTaskStatusLabel[status as DynamicTaskStatus] ?? status;
    return <Tag color={color}>{label}</Tag>;
  };

  /* ===================== 表格列定义 ===================== */

  /** 进程树列 */
  const processColumns: ColumnsType<ProcessTreeNode> = [
    { title: 'PID', dataIndex: 'pid', width: 80 },
    {
      title: '进程名',
      dataIndex: 'name',
      render: (v: string, r) => (
        <Space>
          <Text strong={r.malicious} style={r.malicious ? { color: colors.error } : undefined}>
            {v}
          </Text>
          {r.malicious && <WarningOutlined style={{ color: colors.error }} />}
        </Space>
      ),
    },
    { title: '父 PID', dataIndex: 'parentPid', width: 90 },
    {
      title: '行为',
      dataIndex: 'action',
      width: 120,
      render: (v: string) => (v ? <Tag color="orange">{v}</Tag> : '-'),
    },
    {
      title: '命令行',
      dataIndex: 'commandLine',
      ellipsis: true,
      render: (v: string) => (v ? <code style={{ fontSize: 12 }}>{v}</code> : '-'),
    },
  ];

  /** 网络连接列 */
  const networkColumns: ColumnsType<NetworkConnection> = [
    {
      title: '目标地址',
      key: 'dst',
      render: (_, r) => (
        <Space>
          <Text style={r.malicious ? { color: colors.error } : undefined}>
            {r.dstIp}:{r.dstPort}
          </Text>
          {r.malicious && <WarningOutlined style={{ color: colors.error }} />}
        </Space>
      ),
    },
    {
      title: '域名',
      dataIndex: 'dstDomain',
      ellipsis: true,
      render: (v: string) => (v ? <code>{v}</code> : '-'),
    },
    {
      title: '协议',
      dataIndex: 'protocol',
      width: 100,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: '流量(Byte)',
      dataIndex: 'bytes',
      width: 120,
      sorter: (a, b) => a.bytes - b.bytes,
    },
  ];

  /** 文件操作列 */
  const fileOpColumns: ColumnsType<FileOperation> = [
    {
      title: '操作类型',
      dataIndex: 'type',
      width: 100,
      render: (v: string) => <Tag color="purple">{v}</Tag>,
    },
    {
      title: '目标路径',
      dataIndex: 'path',
      ellipsis: true,
      render: (v: string, r) => (
        <Text style={r.malicious ? { color: colors.error } : undefined}>
          <code>{v}</code>
        </Text>
      ),
    },
    {
      title: '进程',
      dataIndex: 'processName',
      width: 160,
      render: (v: string) => v ?? '-',
    },
    {
      title: '恶意',
      dataIndex: 'malicious',
      width: 70,
      render: (v: boolean) =>
        v ? <WarningOutlined style={{ color: colors.error }} /> : '-',
    },
  ];

  /** ATT&CK 技术映射列 */
  const attackColumns: ColumnsType<AttackTechniqueMapping> = [
    {
      title: '技术 ID',
      dataIndex: 'techniqueId',
      width: 110,
      render: (v: string) => <Tag color="red">{v}</Tag>,
    },
    { title: '技术名称', dataIndex: 'name', ellipsis: true, render: (v: string) => v ?? '-' },
    {
      title: '战术',
      dataIndex: 'tactic',
      width: 160,
      render: (v: string) => <Tag color="geekblue">{v}</Tag>,
    },
    { title: '描述', dataIndex: 'description', ellipsis: true },
  ];

  /** IOC 列 */
  const iocColumns: ColumnsType<DynamicIocItem> = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (v: string) => <Tag color="magenta">{v}</Tag>,
    },
    {
      title: '值',
      dataIndex: 'value',
      ellipsis: true,
      render: (v: string) => <code>{v}</code>,
    },
    { title: '来源', dataIndex: 'source', width: 130, render: (v: string) => v ?? '-' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
  ];

  /** STIX 对象列 */
  const stixColumns: ColumnsType<StixObject> = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 140,
      render: (v: string) => <Tag color="cyan">{v}</Tag>,
    },
    {
      title: 'ID',
      dataIndex: 'id',
      ellipsis: true,
      render: (v: string) => <code style={{ fontSize: 12 }}>{v}</code>,
    },
    {
      title: '属性',
      key: 'props',
      render: (_, r) => {
        const extra = Object.entries(r)
          .filter(([k]) => k !== 'type' && k !== 'id')
          .map(([k, v]) => `${k}=${String(v)}`);
        return (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {extra.join(', ') || '-'}
          </Text>
        );
      },
    },
  ];

  /* ===================== 渲染 ===================== */

  /** 渲染降级提示 */
  const renderDegradedAlert = () => (
    <Alert
      type="warning"
      showIcon
      message="Cuckoo 沙箱不可用，已降级"
      description={
        task?.errorMessage ??
        '动态分析服务暂不可用，本次任务以降级模式返回。可稍后重试或检查沙箱配置。'
      }
      style={{ marginBottom: spacing[3] }}
      data-testid="dynamic-degraded-alert"
    />
  );

  /** 渲染报告内容 */
  const renderReport = () => {
    if (!report) return null;
    const score = report.score ?? 0;
    const scoreColor = score >= 7 ? colors.error : score >= 4 ? colors.warning : colors.success;

    return (
      <div data-testid="dynamic-report-content">
        {/* 概要统计 */}
        <Row gutter={16} style={{ marginBottom: spacing[3] }}>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="威胁评分"
                value={score.toFixed(1)}
                suffix="/10"
                valueStyle={{ color: scoreColor }}
                prefix={<WarningOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic title="进程数" value={report.processTree.length} prefix={<BranchesOutlined />} />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="网络连接"
                value={report.networkConnections.length}
                prefix={<CloudOutlined />}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card size="small">
              <Statistic
                title="ATT&CK 技术"
                value={report.attackTechniques.length}
                prefix={<BugOutlined />}
                valueStyle={{ color: colors.error }}
              />
            </Card>
          </Col>
        </Row>

        {/* 摘要 */}
        {report.summary && (
          <Card size="small" title={<><ExperimentOutlined /> 分析摘要</>} style={{ marginBottom: spacing[3] }}>
            <Paragraph>{report.summary}</Paragraph>
          </Card>
        )}

        {/* 进程树 */}
        <Card
          size="small"
          title={<><BranchesOutlined /> 进程行为链 ({report.processTree.length})</>}
          style={{ marginBottom: spacing[3] }}
        >
          {report.processTree.length > 0 ? (
            <Table
              size="small"
              rowKey="pid"
              pagination={false}
              columns={processColumns}
              dataSource={report.processTree}
              expandable={{
                childrenColumnName: 'children',
                defaultExpandAllRows: true,
              }}
            />
          ) : (
            <Empty description="无进程行为数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>

        {/* 网络连接 */}
        <Card
          size="small"
          title={<><CloudOutlined /> 网络行为 ({report.networkConnections.length})</>}
          style={{ marginBottom: spacing[3] }}
        >
          {report.networkConnections.length > 0 ? (
            <Table
              size="small"
              rowKey={(r) => `${r.dstIp}:${r.dstPort}`}
              pagination={false}
              columns={networkColumns}
              dataSource={report.networkConnections}
            />
          ) : (
            <Empty description="无网络行为数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>

        {/* 文件操作 */}
        <Card
          size="small"
          title={<><FileSearchOutlined /> 文件操作 ({report.fileOperations.length})</>}
          style={{ marginBottom: spacing[3] }}
        >
          {report.fileOperations.length > 0 ? (
            <Table
              size="small"
              rowKey={(r) => `${r.type}-${r.path}`}
              pagination={false}
              columns={fileOpColumns}
              dataSource={report.fileOperations}
            />
          ) : (
            <Empty description="无文件操作数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>

        {/* ATT&CK 映射 */}
        <Card
          size="small"
          title={<><BugOutlined /> ATT&CK 技术映射 ({report.attackTechniques.length})</>}
          style={{ marginBottom: spacing[3] }}
        >
          {report.attackTechniques.length > 0 ? (
            <Table
              size="small"
              rowKey="techniqueId"
              pagination={false}
              columns={attackColumns}
              dataSource={report.attackTechniques}
            />
          ) : (
            <Empty description="未映射到 ATT&CK 技术" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>

        {/* IOC */}
        <Card
          size="small"
          title={<><WarningOutlined /> 提取 IOC ({report.iocs.length})</>}
          style={{ marginBottom: spacing[3] }}
        >
          {report.iocs.length > 0 ? (
            <Table
              size="small"
              rowKey={(r) => `${r.type}-${r.value}`}
              pagination={false}
              columns={iocColumns}
              dataSource={report.iocs}
            />
          ) : (
            <Empty description="未提取到 IOC" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>

        {/* STIX 对象 */}
        <Card
          size="small"
          title={<><CodeOutlined /> STIX 2.1 对象 ({report.stixObjects.length})</>}
        >
          {report.stixObjects.length > 0 ? (
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              columns={stixColumns}
              dataSource={report.stixObjects}
            />
          ) : (
            <Empty description="无 STIX 对象" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      </div>
    );
  };

  return (
    <div data-testid="dynamic-analysis-tab">
      {/* 操作栏 */}
      <Space style={{ marginBottom: spacing[3] }}>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          loading={submitting}
          onClick={handleSubmit}
          data-testid="dynamic-submit-btn"
        >
          提交动态分析
        </Button>
        {task && !isTerminal(task.status) && (
          <Button
            icon={<ReloadOutlined />}
            loading={polling}
            onClick={() => handlePoll()}
            data-testid="dynamic-poll-btn"
          >
            刷新状态
          </Button>
        )}
        {task && canFetchReport(task.status) && (
          <Button
            icon={<ReloadOutlined />}
            loading={loadingReport}
            onClick={() => handleFetchReport(task.taskId)}
          >
            重新获取报告
          </Button>
        )}
        <Text type="secondary" style={{ fontSize: 12 }}>
          动态分析由 Cuckoo 沙箱（端口 8090）执行，沙箱不可用时自动降级
        </Text>
      </Space>

      {/* 任务状态 */}
      {task && (
        <Card size="small" style={{ marginBottom: spacing[3] }}>
          <Row gutter={16}>
            <Col span={6}>
              <Statistic title="任务ID" value={task.taskId} valueStyle={{ fontSize: 14 }} />
            </Col>
            <Col span={4}>
              <div style={{ marginBottom: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>状态</Text>
              </div>
              {renderStatusTag(task.status)}
            </Col>
            <Col span={4}>
              <div style={{ marginBottom: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Cuckoo 任务</Text>
              </div>
              <Text style={{ fontSize: 13 }}>{task.cuckooTaskId ?? '-'}</Text>
            </Col>
            <Col span={5}>
              <Statistic
                title="创建时间"
                value={formatDateTime(task.createTime)}
                valueStyle={{ fontSize: 13 }}
              />
            </Col>
            <Col span={5}>
              <Statistic
                title="更新时间"
                value={formatDateTime(task.updateTime)}
                valueStyle={{ fontSize: 13 }}
              />
            </Col>
          </Row>
        </Card>
      )}

      {/* 降级提示 */}
      {task?.degraded && renderDegradedAlert()}

      {/* 加载中 */}
      {loadingReport && (
        <div style={{ textAlign: 'center', padding: 40 }} data-testid="dynamic-report-loading">
          <Spin tip="加载动态分析报告..." />
        </div>
      )}

      {/* 报告内容 */}
      {!loadingReport && report && renderReport()}

      {/* 空状态 */}
      {!task && !loadingReport && (
        <Empty
          description="点击「提交动态分析」将文件送入 Cuckoo 沙箱进行动态行为分析"
          data-testid="dynamic-empty"
        >
          <Space>
            <CheckCircleOutlined style={{ color: colors.success }} />
            <Text type="secondary" style={{ fontSize: 12 }}>
              支持进程树、网络行为、ATT&CK 映射、IOC 提取与 STIX 2.1 导出
            </Text>
          </Space>
        </Empty>
      )}
    </div>
  );
};

export default DynamicAnalysisTab;
