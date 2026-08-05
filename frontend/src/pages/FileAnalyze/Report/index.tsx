/**
 * 分析报告页
 * - 顶部：报告基本信息（任务、文件、状态、生成时间）
 * - 中部：分析摘要 + 风险概览 + IOC 列表
 * - 底部：沙箱报告（进程行为链 + 网络行为 + 文件操作）
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Table,
  Empty,
  Spin,
  Row,
  Col,
  Statistic,
  List,
  Divider,
  Timeline,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  DownloadOutlined,
  PrinterOutlined,
  ShareAltOutlined,
  WarningOutlined,
  BugOutlined,
  FileTextOutlined,
  GlobalOutlined,
  CodeOutlined,
  ApiOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import type { AnalyzeTask, AnalyzeResult, AnalyzeDetail, IocInfo, RiskInfo } from '@/types';
import { AnalyzeStatus, AnalyzeType, IocType } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** Mock 任务 */
const mockTask: AnalyzeTask = {
  id: 'task_001',
  fileId: 'f0001',
  fileName: 'malware_sample_0001.exe',
  type: AnalyzeType.MALWARE,
  status: AnalyzeStatus.COMPLETED,
  progress: 100,
  createTime: '2026-07-25T10:00:00Z',
  updateTime: '2026-07-25T10:30:00Z',
  completeTime: '2026-07-25T10:30:00Z',
};

/** Mock 分析结果 */
const mockResult: AnalyzeResult = {
  taskId: 'task_001',
  fileId: 'f0001',
  type: AnalyzeType.MALWARE,
  summary: '该样本为 Cobalt Strike Beacon 变种，包含持久化、横向移动、键盘记录等功能。运行后会连接 C2 服务器 45.155.205.233 并下载第二阶段载荷。',
  details: [
    {
      category: '静态特征',
      title: 'PDB 路径暴露',
      description: '可执行文件包含 PDB 路径 D:\\workspace\\beacon\\Release\\beacon.pdb',
      severity: 'info',
      evidence: ['字符串：D:\\workspace\\beacon\\Release\\beacon.pdb'],
    },
    {
      category: '静态特征',
      title: '导出函数可疑',
      description: '导出 ServiceMain 与 svchost 入口点，疑似服务型后门',
      severity: 'warning',
      evidence: ['Export: ServiceMain', 'Export: DllRegisterServer'],
    },
    {
      category: '动态行为',
      title: '进程注入',
      description: '在 explorer.exe 中注入代码',
      severity: 'critical',
      evidence: ['OpenProcess(target=explorer.exe)', 'VirtualAllocEx', 'WriteProcessMemory'],
    },
    {
      category: '网络行为',
      title: 'C2 通信',
      description: '通过 HTTPS 与 45.155.205.233 进行心跳通信',
      severity: 'critical',
      evidence: ['GET /api/beacon HTTP/1.1', 'Host: 45.155.205.233'],
    },
  ],
  iocs: [
    { type: IocType.IP, value: '45.155.205.233', confidence: 0.95, tags: ['C2', 'APT41'], source: '沙箱', firstSeen: '2026-07-25T10:00:00Z', lastSeen: '2026-07-25T10:30:00Z' },
    { type: IocType.DOMAIN, value: 'malicious-update.example-evil.com', confidence: 0.88, tags: ['C2', 'APT41'], source: '沙箱' },
    { type: IocType.URL, value: 'http://cdn-update-x.com/payload.bin', confidence: 0.92, tags: ['Loader'], source: '沙箱' },
    { type: IocType.MD5, value: 'a3f5b8c9d2e1f0a1b2c3d4e5f6a7b8c9', confidence: 0.99, tags: ['Sample'], source: '哈希' },
    { type: IocType.EMAIL, value: 'recruitment@evil-corp.cn', confidence: 0.78, tags: ['Phishing'], source: '沙箱' },
  ],
  risks: [
    { level: 'critical', category: 'C2 通信', description: '与已知 APT41 C2 服务器通信', score: 9.8, vector: '网络' },
    { level: 'high', category: '进程注入', description: '注入 explorer.exe', score: 8.5, vector: '主机' },
    { level: 'high', category: '持久化', description: '创建注册表 Run 键', score: 8.2, vector: '主机' },
    { level: 'medium', category: '信息收集', description: '读取系统信息与用户凭据', score: 6.5, vector: '主机' },
  ],
  recommendations: [
    '立即隔离该样本文件并在所有终端查杀相关 IOC',
    '阻断 45.155.205.233 与 malicious-update.example-evil.com 的访问',
    '排查 explorer.exe 异常子进程',
    '检查注册表 HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run 项',
  ],
  createTime: '2026-07-25T10:30:00Z',
};

/** Mock 沙箱报告 */
const sandboxReport = {
  environment: 'Windows 10 1909 x64 / Office 2019',
  duration: 120,
  score: 87,
  processes: [
    { pid: 1234, name: 'malware_sample.exe', parent: 0, action: '创建进程', time: '00:00:01' },
    { pid: 1456, name: 'cmd.exe', parent: 1234, action: '执行命令 /c whoami', time: '00:00:05' },
    { pid: 1789, name: 'powershell.exe', parent: 1456, action: '下载脚本 (iex(New-Object Net.WebClient).DownloadString)', time: '00:00:12' },
    { pid: 1820, name: 'rundll32.exe', parent: 1234, action: '加载 a.dll,Start', time: '00:00:25' },
    { pid: 1900, name: 'explorer.exe', parent: 1820, action: '被注入异常子进程', time: '00:00:40' },
  ],
  network: [
    { dst: '45.155.205.233:443', proto: 'HTTPS', bytes: 8420, time: '00:00:15' },
    { dst: 'malicious-update.example-evil.com:443', proto: 'HTTPS', bytes: 15200, time: '00:00:18' },
    { dst: 'cdn-update-x.com:80', proto: 'HTTP', bytes: 22000, time: '00:00:20' },
  ],
  fileOps: [
    { op: '创建', path: 'C:\\Users\\Public\\beacon.dll', time: '00:00:08' },
    { op: '写入', path: 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\\Update', time: '00:00:30' },
    { op: '删除', path: 'C:\\Users\\Public\\temp.log', time: '00:01:00' },
  ],
};

/** 类型映射 */
const typeMap: Record<AnalyzeType, { color: string; text: string }> = {
  [AnalyzeType.CONTENT]: { color: 'blue', text: '内容分析' },
  [AnalyzeType.MALWARE]: { color: 'red', text: '恶意软件分析' },
  [AnalyzeType.NETWORK]: { color: 'green', text: '网络行为分析' },
  [AnalyzeType.CRYPTO]: { color: 'purple', text: '加密分析' },
  [AnalyzeType.METADATA]: { color: 'orange', text: '元数据分析' },
  [AnalyzeType.IOC]: { color: 'cyan', text: 'IOC 提取' },
};

/** 状态映射 */
const statusMap: Record<AnalyzeStatus, { color: string; text: string }> = {
  [AnalyzeStatus.PENDING]: { color: 'default', text: '待处理' },
  [AnalyzeStatus.RUNNING]: { color: 'processing', text: '分析中' },
  [AnalyzeStatus.COMPLETED]: { color: 'success', text: '已完成' },
  [AnalyzeStatus.FAILED]: { color: 'error', text: '失败' },
};

/** 风险等级颜色 */
const riskColor: Record<RiskInfo['level'], string> = {
  low: colors.success,
  medium: colors.warning,
  high: colors.severity.high,
  critical: colors.severity.critical,
};

const riskText: Record<RiskInfo['level'], string> = {
  low: '低危',
  medium: '中危',
  high: '高危',
  critical: '严重',
};

/** IOC 类型映射 */
const iocTypeText: Record<IocType, string> = {
  [IocType.IP]: 'IP',
  [IocType.DOMAIN]: '域名',
  [IocType.URL]: 'URL',
  [IocType.MD5]: 'MD5',
  [IocType.SHA1]: 'SHA1',
  [IocType.SHA256]: 'SHA256',
  [IocType.EMAIL]: '邮箱',
  [IocType.CVE]: 'CVE',
  [IocType.BTC]: 'BTC 地址',
};

const AnalyzeReportPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [task, setTask] = useState<AnalyzeTask | null>(null);
  const [result, setResult] = useState<AnalyzeResult | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      setTask({ ...mockTask, id: id || mockTask.id });
      setResult(mockResult);
      setLoading(false);
    }, 200);
  }, [id]);

  /** 详情严重程度颜色 */
  const detailSeverityColor: Record<AnalyzeDetail['severity'], string> = {
    info: 'default',
    warning: 'warning',
    critical: 'error',
  };

  /** IOC 列 */
  const iocColumns: ColumnsType<IocInfo> = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      render: (v: IocType) => <Tag color="red">{iocTypeText[v]}</Tag>,
    },
    {
      title: '值',
      dataIndex: 'value',
      ellipsis: true,
      render: (v: string) => <code>{v}</code>,
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 100,
      render: (v: number) => <Tag color={v >= 0.9 ? 'red' : v >= 0.8 ? 'orange' : 'default'}>{(v * 100).toFixed(0)}%</Tag>,
    },
    {
      title: '标签',
      dataIndex: 'tags',
      width: 200,
      render: (tags: string[]) => tags?.map((t) => <Tag key={t}>{t}</Tag>),
    },
    { title: '来源', dataIndex: 'source', width: 100 },
  ];

  /** 风险评分图 */
  const riskRadarOption: EChartsOption = {
    tooltip: {},
    radar: {
      indicator: result?.risks.map((r) => ({ name: r.category, max: 10 })) ?? [],
      shape: 'polygon',
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: result?.risks.map((r) => r.score) ?? [],
            name: '风险评分',
            areaStyle: { color: 'rgba(245, 34, 45, 0.3)' },
            lineStyle: { color: colors.error },
          },
        ],
      },
    ],
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载分析报告..." /></div>;
  }

  if (!task || !result) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到分析任务">
          <Button type="primary" onClick={() => navigate('/analyze')}>返回分析列表</Button>
        </Empty>
      </div>
    );
  }

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/analyze')}>返回</Button>
          <Title level={4} style={{ margin: 0 }}>分析报告 · {task.fileName}</Title>
          <Tag color={typeMap[task.type].color}>{typeMap[task.type].text}</Tag>
          <Tag color={statusMap[task.status].color}>{statusMap[task.status].text}</Tag>
        </Space>
        <Space>
          <Button icon={<PrinterOutlined />} onClick={() => window.print()}>打印</Button>
          <Button icon={<ShareAltOutlined />} onClick={() => message.success('分享链接已复制')}>分享</Button>
          <Button type="primary" icon={<DownloadOutlined />} onClick={() => message.success('开始下载 PDF')}>下载 PDF</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="分析类型" value={typeMap[task.type].text} valueStyle={{ fontSize: 16 }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="风险数" value={result.risks.length} prefix={<WarningOutlined />} valueStyle={{ color: colors.error }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="IOC 数" value={result.iocs.length} prefix={<BugOutlined />} valueStyle={{ color: colors.info }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="耗时" value={Math.round((new Date(task.completeTime ?? task.updateTime).getTime() - new Date(task.createTime).getTime()) / 1000)} suffix="秒" />
          </Card>
        </Col>
      </Row>

      {/* 基本信息 */}
      <Card size="small" title={<Space><FileTextOutlined /> 任务基本信息</Space>} style={{ marginBottom: spacing[4] }}>
        <ProDescriptions
          column={3}
          size="small"
          bordered
          dataSource={{
            taskId: task.id,
            fileId: task.fileId,
            fileName: task.fileName,
            type: typeMap[task.type].text,
            status: statusMap[task.status].text,
            progress: `${task.progress}%`,
            createTime: formatDateTime(task.createTime),
            updateTime: formatDateTime(task.updateTime),
            completeTime: task.completeTime ? formatDateTime(task.completeTime) : '-',
          }}
          columns={[
            { title: '任务 ID', dataIndex: 'taskId', key: 'taskId' },
            { title: '文件 ID', dataIndex: 'fileId', key: 'fileId' },
            { title: '文件名', dataIndex: 'fileName', key: 'fileName' },
            { title: '类型', dataIndex: 'type', key: 'type' },
            { title: '状态', dataIndex: 'status', key: 'status' },
            { title: '进度', dataIndex: 'progress', key: 'progress' },
            { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
            { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
            { title: '完成时间', dataIndex: 'completeTime', key: 'completeTime' },
          ]}
        />
      </Card>

      {/* 摘要 */}
      <Card size="small" title={<Space><FileTextOutlined /> 分析摘要</Space>} style={{ marginBottom: spacing[4] }}>
        <Paragraph>{result.summary}</Paragraph>
      </Card>

      {/* 风险概览 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={24} lg={12}>
          <Card size="small" title={<Space><WarningOutlined /> 风险评分雷达</Space>}>
            <ReactECharts option={riskRadarOption} style={{ height: 280, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card size="small" title={<Space><WarningOutlined /> 风险列表</Space>}>
            <Table
              size="small"
              rowKey={(r) => r.category}
              pagination={false}
              dataSource={result.risks}
              columns={[
                {
                  title: '等级',
                  dataIndex: 'level',
                  width: 80,
                  render: (v: RiskInfo['level']) => <Tag color={riskColor[v]}>{riskText[v]}</Tag>,
                },
                { title: '类别', dataIndex: 'category', width: 120 },
                { title: '描述', dataIndex: 'description', ellipsis: true },
                { title: '分数', dataIndex: 'score', width: 80, render: (v: number) => v.toFixed(1) },
                { title: '向量', dataIndex: 'vector', width: 100 },
              ]}
            />
          </Card>
        </Col>
      </Row>

      {/* IOC 列表 */}
      <Card size="small" title={<Space><BugOutlined /> 提取的威胁情报 (IOC)</Space>} style={{ marginBottom: spacing[4] }}>
        <Table
          size="small"
          rowKey={(r) => r.value}
          pagination={false}
          columns={iocColumns}
          dataSource={result.iocs}
        />
      </Card>

      {/* 分析详情 */}
      <Card size="small" title={<Space><CodeOutlined /> 分析详情项</Space>} style={{ marginBottom: spacing[4] }}>
        <List
          dataSource={result.details}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                avatar={<Tag color={detailSeverityColor[item.severity]}>{item.severity}</Tag>}
                title={<Space><Text strong>{item.title}</Text><Tag>{item.category}</Tag></Space>}
                description={
                  <div>
                    <Paragraph style={{ margin: 0 }}>{item.description}</Paragraph>
                    {item.evidence.length > 0 && (
                      <div style={{ marginTop: 8 }}>
                        <Text type="secondary" style={{ fontSize: 12 }}>证据：</Text>
                        {item.evidence.map((e, i) => (
                          <div key={i}><code style={{ fontSize: 12 }}>{e}</code></div>
                        ))}
                      </div>
                    )}
                  </div>
                }
              />
            </List.Item>
          )}
        />
      </Card>

      {/* 沙箱报告 */}
      <Card size="small" title={<Space><ApiOutlined /> 沙箱报告</Space>} style={{ marginBottom: spacing[4] }}>
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={6}><Statistic title="运行环境" value={sandboxReport.environment} valueStyle={{ fontSize: 13 }} /></Col>
          <Col span={6}><Statistic title="运行时长" value={sandboxReport.duration} suffix="s" /></Col>
          <Col span={6}>
            <Statistic
              title="威胁评分"
              value={sandboxReport.score}
              suffix="/100"
              valueStyle={{ color: sandboxReport.score >= 70 ? colors.error : colors.warning }}
            />
          </Col>
          <Col span={6}><Statistic title="进程数" value={sandboxReport.processes.length} /></Col>
        </Row>

        <Divider orientation="left">进程行为链</Divider>
        <Timeline
          items={sandboxReport.processes.map((p) => ({
            color: p.action.includes('注入') ? 'red' : p.action.includes('下载') ? 'orange' : 'blue',
            children: (
              <div>
                <Text strong>{p.name}</Text> <Text type="secondary">(PID: {p.pid}, 父 PID: {p.parent})</Text>
                <div style={{ fontSize: 12 }}>{p.action}</div>
                <div style={{ fontSize: 11, color: '#8c8c8c' }}>{p.time}</div>
              </div>
            ),
          }))}
        />

        <Divider orientation="left">网络行为</Divider>
        <Table
          size="small"
          rowKey="dst"
          pagination={false}
          dataSource={sandboxReport.network}
          columns={[
            { title: '目标地址', dataIndex: 'dst', render: (v: string) => <code>{v}</code> },
            { title: '协议', dataIndex: 'proto', render: (v: string) => <Tag color="blue">{v}</Tag> },
            { title: '流量(Byte)', dataIndex: 'bytes' },
            { title: '时间', dataIndex: 'time', width: 100 },
          ]}
        />

        <Divider orientation="left">文件 / 注册表操作</Divider>
        <Table
          size="small"
          rowKey={(r) => r.path}
          pagination={false}
          dataSource={sandboxReport.fileOps}
          columns={[
            {
              title: '操作',
              dataIndex: 'op',
              width: 80,
              render: (v: string) => <Tag color={v === '删除' ? 'red' : v === '写入' ? 'orange' : 'blue'}>{v}</Tag>,
            },
            { title: '路径', dataIndex: 'path', render: (v: string) => <code>{v}</code> },
            { title: '时间', dataIndex: 'time', width: 100 },
          ]}
        />
      </Card>

      {/* 修复建议 */}
      <Card size="small" title={<Space><GlobalOutlined /> 修复建议</Space>}>
        <ol>
          {result.recommendations.map((r, i) => (
            <li key={i} style={{ marginBottom: 8 }}>
              <Text>{r}</Text>
            </li>
          ))}
        </ol>
      </Card>
    </div>
  );
};

export default AnalyzeReportPage;
