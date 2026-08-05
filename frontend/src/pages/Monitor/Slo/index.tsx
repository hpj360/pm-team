/**
 * SLO 监控深度分析页
 * - 顶部：SLO 概要统计 + 状态徽章
 * - 主体：SLO 列表 + 错误预算历史 + 燃烧率多窗口对比
 * - 右侧：告警规则 + 最近事件
 */
import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Row,
  Col,
  Statistic,
  Table,
  Empty,
  Segmented,
  Tooltip,
  Progress,
  Timeline,
  Badge,
  Switch,
  Input,
  Select,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  DashboardOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  ThunderboltOutlined,
  FireOutlined,
  ClockCircleOutlined,
  BellOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import {
  getMockSloStatus,
  getMockFileEvents,
  mockTeamSpaces,
} from '@/mock/monitor';
import {
  Stage,
  StageName,
  type SloStatus,
  type TimeRange,
} from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text } = Typography;

/** SLO 状态元数据 */
const statusMeta: Record<number, { color: string; text: string; badge: 'success' | 'warning' | 'error'; icon: React.ReactNode }> = {
  0: { color: colors.success, text: '达标', badge: 'success', icon: <CheckCircleOutlined /> },
  1: { color: colors.warning, text: '告警', badge: 'warning', icon: <WarningOutlined /> },
  2: { color: colors.error, text: '违约', badge: 'error', icon: <CloseCircleOutlined /> },
};

/** 阶段颜色 */
const stageColor: Record<Stage, string> = {
  [Stage.UPLOAD]: '#1890ff',
  [Stage.INDEX]: '#52c41a',
  [Stage.PARSE]: '#faad14',
  [Stage.SEARCH]: '#722ed1',
};

/** 告警规则 Mock */
interface AlertRule {
  id: string;
  name: string;
  sloCode: string;
  condition: string;
  windowMin: number;
  threshold: number;
  enabled: boolean;
  triggeredCount: number;
}

const alertRules: AlertRule[] = [
  { id: 'a1', name: '上传可用性下降', sloCode: 'slo.upload.availability', condition: '可用性 < 99.9%', windowMin: 5, threshold: 99.9, enabled: true, triggeredCount: 3 },
  { id: 'a2', name: '索引可搜时延超限', sloCode: 'slo.index.freshness.p95', condition: 'P95 > 60s', windowMin: 10, threshold: 60, enabled: true, triggeredCount: 7 },
  { id: 'a3', name: '解析成功率告警', sloCode: 'slo.parse.success.rate', condition: '成功率 < 95%', windowMin: 30, threshold: 95, enabled: true, triggeredCount: 12 },
  { id: 'a4', name: '搜索 P95 时延', sloCode: 'slo.search.latency.p95', condition: 'P95 > 500ms', windowMin: 5, threshold: 500, enabled: false, triggeredCount: 0 },
  { id: 'a5', name: '搜索可用性告警', sloCode: 'slo.search.availability', condition: '可用性 < 99.5%', windowMin: 5, threshold: 99.5, enabled: true, triggeredCount: 1 },
];

/** SLO 事件 Mock */
interface SloEvent {
  id: string;
  sloCode: string;
  sloName: string;
  type: 'violation' | 'warning' | 'recovered' | 'burn-rate';
  message: string;
  occurredAt: string;
  duration?: string;
}

const sloEvents: SloEvent[] = [
  { id: 'e1', sloCode: 'slo.parse.success.rate', sloName: '解析成功率', type: 'violation', message: '解析成功率跌至 94.2%，已违反 SLO 目标', occurredAt: '2026-07-27T08:32:00Z', duration: '12分钟' },
  { id: 'e2', sloCode: 'slo.index.freshness.p95', sloName: '索引可搜时延 P95', type: 'warning', message: '索引时延 P95 达 72s，接近阈值', occurredAt: '2026-07-27T07:15:00Z', duration: '6分钟' },
  { id: 'e3', sloCode: 'slo.upload.availability', sloName: '上传可用性', type: 'burn-rate', message: '2h 燃烧率达 1.4，超过预算消耗速率', occurredAt: '2026-07-27T06:48:00Z' },
  { id: 'e4', sloCode: 'slo.search.latency.p95', sloName: '搜索 P95 时延', type: 'recovered', message: '搜索时延恢复正常', occurredAt: '2026-07-26T22:10:00Z', duration: '34分钟' },
  { id: 'e5', sloCode: 'slo.parse.success.rate', sloName: '解析成功率', type: 'warning', message: '解析成功率出现周期性波动', occurredAt: '2026-07-26T18:55:00Z', duration: '8分钟' },
];

const eventColor: Record<SloEvent['type'], string> = {
  violation: 'red',
  warning: 'orange',
  recovered: 'green',
  'burn-rate': 'magenta',
};

const eventText: Record<SloEvent['type'], string> = {
  violation: '违约',
  warning: '告警',
  recovered: '恢复',
  'burn-rate': '燃烧率',
};

const SloMonitorPage: React.FC = () => {
  const navigate = useNavigate();
  const [timeRange, setTimeRange] = useState<TimeRange>('24h');
  const [teamSpaceId, setTeamSpaceId] = useState<number | undefined>(undefined);
  const [activeSloCode, setActiveSloCode] = useState<string>('slo.parse.success.rate');
  const [alertSearch, setAlertSearch] = useState('');

  const sloList = useMemo(() => getMockSloStatus(teamSpaceId), [teamSpaceId]);
  const recentEvents = useMemo(() => getMockFileEvents(teamSpaceId, 30), [teamSpaceId]);

  /** 统计 */
  const stats = useMemo(() => {
    const total = sloList.length;
    const ok = sloList.filter((s) => s.status === 0).length;
    const warn = sloList.filter((s) => s.status === 1).length;
    const violated = sloList.filter((s) => s.status === 2).length;
    const avgBudget = +(sloList.reduce((sum, s) => sum + s.errorBudgetRemaining, 0) / total).toFixed(1);
    return { total, ok, warn, violated, avgBudget };
  }, [sloList]);

  /** 当前选中的 SLO */
  const activeSlo = sloList.find((s) => s.sloCode === activeSloCode) ?? sloList[0];

  /** 错误预算消耗趋势（生成 24h 序列） */
  const budgetHistoryOption: EChartsOption = useMemo(() => {
    if (!activeSlo) return {};
    const points = 24;
    const now = Date.now();
    const xs: string[] = [];
    const budget: number[] = [];
    const target = activeSlo.errorBudgetRemaining;
    let v = 100;
    for (let i = points - 1; i >= 0; i--) {
      const t = new Date(now - i * 60 * 60 * 1000);
      xs.push(`${String(t.getHours()).padStart(2, '0')}:00`);
      // 模拟错误预算逐渐消耗
      const consumption = (100 - target) / points + Math.random() * 1.5;
      v = Math.max(target - 10, v - consumption);
      budget.push(+v.toFixed(2));
    }
    budget[budget.length - 1] = target;
    return {
      tooltip: { trigger: 'axis', valueFormatter: (val) => `${val}%` },
      grid: { left: 50, right: 24, top: 30, bottom: 30 },
      xAxis: { type: 'category', boundaryGap: false, data: xs },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      series: [
        {
          type: 'line',
          smooth: true,
          areaStyle: { opacity: 0.25 },
          itemStyle: { color: stageColor[activeSlo.stage] },
          markLine: {
            silent: true,
            data: [
              { yAxis: 30, lineStyle: { color: colors.error, type: 'dashed' }, label: { formatter: '告警线 30%' } },
              { yAxis: 60, lineStyle: { color: colors.warning, type: 'dashed' }, label: { formatter: '警戒线 60%' } },
            ],
          },
          data: budget,
        },
      ],
    };
  }, [activeSlo]);

  /** 燃烧率多窗口对比 */
  const burnRateCompareOption: EChartsOption = useMemo(() => ({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['2h 燃烧率', '6h 燃烧率'], top: 0 },
    grid: { left: 50, right: 24, top: 40, bottom: 60 },
    xAxis: {
      type: 'category',
      data: sloList.map((s) => s.sloName),
      axisLabel: { rotate: 20, interval: 0 },
    },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}x' } },
    series: [
      {
        name: '2h 燃烧率',
        type: 'bar',
        itemStyle: { color: colors.warning },
        data: sloList.map((s) => s.burnRate2h),
      },
      {
        name: '6h 燃烧率',
        type: 'bar',
        itemStyle: { color: colors.info },
        data: sloList.map((s) => s.burnRate6h),
      },
    ],
  }), [sloList]);

  /** SLO 状态雷达图 */
  const radarOption: EChartsOption = useMemo(() => ({
    tooltip: {},
    radar: {
      indicator: sloList.map((s) => ({ name: s.sloName, max: 100 })),
      shape: 'polygon',
      radius: '65%',
      splitArea: { areaStyle: { color: ['rgba(245, 34, 45, 0.05)', 'rgba(250, 173, 20, 0.05)'] } },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: sloList.map((s) => Math.min(100, s.actualValue / s.targetValue * 100)),
            name: 'SLO 达成度',
            itemStyle: { color: colors.primary[500] },
            areaStyle: { opacity: 0.2 },
          },
        ],
      },
    ],
  }), [sloList]);

  /** SLO 表格列 */
  const sloColumns: ColumnsType<SloStatus> = [
    {
      title: 'SLO 名称',
      dataIndex: 'sloName',
      width: 180,
      render: (text: string, record) => (
        <a onClick={() => setActiveSloCode(record.sloCode)}>
          <Space>
            <DashboardOutlined style={{ color: stageColor[record.stage] }} />
            <Text strong>{text}</Text>
          </Space>
        </a>
      ),
    },
    {
      title: '阶段',
      dataIndex: 'stage',
      width: 90,
      render: (v: Stage) => <Tag color="blue">{StageName[v]}</Tag>,
    },
    {
      title: '目标 / 实际',
      key: 'target',
      width: 160,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text type="secondary">目标 {record.targetValue} {record.targetUnit}</Text>
          <Text style={{ color: record.actualValue >= record.targetValue ? colors.success : colors.error }}>
            实际 {record.actualValue} {record.targetUnit}
          </Text>
        </Space>
      ),
    },
    {
      title: '错误预算',
      dataIndex: 'errorBudgetRemaining',
      width: 200,
      sorter: (a, b) => a.errorBudgetRemaining - b.errorBudgetRemaining,
      render: (v: number) => (
        <Tooltip title={`剩余 ${v}%`}>
          <Progress
            percent={v}
            size="small"
            strokeColor={v < 30 ? colors.error : v < 60 ? colors.warning : colors.success}
            format={(p) => `${p}%`}
          />
        </Tooltip>
      ),
    },
    {
      title: '燃烧率',
      key: 'burn',
      width: 140,
      render: (_, record) => (
        <Space>
          <Tag color={record.burnRate2h > 2 ? 'error' : record.burnRate2h > 1 ? 'warning' : 'default'}>2h: {record.burnRate2h}x</Tag>
          <Tag color={record.burnRate6h > 2 ? 'error' : record.burnRate6h > 1 ? 'warning' : 'default'}>6h: {record.burnRate6h}x</Tag>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      filters: [
        { text: '达标', value: 0 },
        { text: '告警', value: 1 },
        { text: '违约', value: 2 },
      ],
      onFilter: (val, record) => record.status === val,
      render: (v: number) => {
        const meta = statusMeta[v];
        return <Badge status={meta.badge} text={meta.text} />;
      },
    },
  ];

  /** 过滤告警规则 */
  const filteredAlertRules = useMemo(() => {
    if (!alertSearch) return alertRules;
    const kw = alertSearch.toLowerCase();
    return alertRules.filter((r) => r.name.toLowerCase().includes(kw) || r.sloCode.toLowerCase().includes(kw));
  }, [alertSearch]);

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/monitor')}>返回看板</Button>
          <DashboardOutlined style={{ fontSize: 22, color: colors.primary[500] }} />
          <Title level={4} style={{ margin: 0 }}>SLO 监控中心</Title>
          <Tag icon={<FireOutlined />} color="red">W9 联调</Tag>
        </Space>
        <Space size={12} wrap>
          <Segmented
            options={[
              { label: '近1小时', value: '1h' },
              { label: '近6小时', value: '6h' },
              { label: '近24小时', value: '24h' },
              { label: '近7天', value: '7d' },
              { label: '近30天', value: '30d' },
            ]}
            value={timeRange}
            onChange={(v) => setTimeRange(v as TimeRange)}
          />
          <Select
            style={{ width: 200 }}
            placeholder="选择团队空间"
            allowClear
            value={teamSpaceId}
            onChange={(v) => setTeamSpaceId(v)}
            options={mockTeamSpaces.map((s) => ({ label: `${s.name} (${s.code})`, value: s.id }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => setTeamSpaceId(undefined)}>重置</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="SLO 总数"
              value={stats.total}
              prefix={<DashboardOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="达标"
              value={stats.ok}
              valueStyle={{ color: colors.success }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="告警"
              value={stats.warn}
              valueStyle={{ color: colors.warning }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="违约"
              value={stats.violated}
              valueStyle={{ color: colors.error }}
              prefix={<CloseCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        {/* 左侧：SLO 表格 */}
        <Col xs={24} lg={16}>
          <Card
            size="small"
            title={<Space><DashboardOutlined /> SLO 状态列表 ({sloList.length})</Space>}
            style={{ marginBottom: spacing[4] }}
          >
            <Table
              size="small"
              rowKey="sloCode"
              columns={sloColumns}
              dataSource={sloList}
              pagination={false}
              scroll={{ x: 1000 }}
              locale={{ emptyText: <Empty description="暂无 SLO 数据" /> }}
              rowClassName={(record) => record.sloCode === activeSloCode ? 'ant-table-row-selected' : ''}
            />
          </Card>

          {/* 错误预算消耗趋势 */}
          <Card
            size="small"
            title={
              <Space>
                <ThunderboltOutlined />
                错误预算消耗趋势 - {activeSlo?.sloName}
              </Space>
            }
            style={{ marginBottom: spacing[4] }}
          >
            <ReactECharts option={budgetHistoryOption} style={{ height: 280, width: '100%' }} notMerge lazyUpdate />
          </Card>

          {/* 燃烧率多窗口对比 */}
          <Card size="small" title={<Space><FireOutlined /> 燃烧率多窗口对比</Space>}>
            <ReactECharts option={burnRateCompareOption} style={{ height: 300, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>

        {/* 右侧：SLO 雷达图 + 告警规则 + 事件流 */}
        <Col xs={24} lg={8}>
          <Card size="small" title={<Space><DashboardOutlined /> SLO 达成度雷达</Space>} style={{ marginBottom: spacing[4] }}>
            <ReactECharts option={radarOption} style={{ height: 260, width: '100%' }} notMerge lazyUpdate />
          </Card>

          {/* 告警规则 */}
          <Card
            size="small"
            title={
              <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                <Space><BellOutlined /> 告警规则</Space>
                <Input
                  size="small"
                  placeholder="搜索告警"
                  prefix={<SearchOutlined />}
                  value={alertSearch}
                  onChange={(e) => setAlertSearch(e.target.value)}
                  style={{ width: 140 }}
                  allowClear
                />
              </Space>
            }
            style={{ marginBottom: spacing[4] }}
          >
            <Table
              size="small"
              rowKey="id"
              columns={[
                { title: '规则', dataIndex: 'name', width: 140, render: (v: string) => <Text strong>{v}</Text> },
                { title: '条件', dataIndex: 'condition', width: 120, render: (v: string) => <Tag color="blue">{v}</Tag> },
                {
                  title: '触发',
                  dataIndex: 'triggeredCount',
                  width: 70,
                  render: (v: number) => <Tag color={v > 5 ? 'red' : v > 0 ? 'orange' : 'default'}>{v}</Tag>,
                },
                {
                  title: '启用',
                  dataIndex: 'enabled',
                  width: 70,
                  render: (v: boolean) => <Switch size="small" defaultChecked={v} />,
                },
              ]}
              dataSource={filteredAlertRules}
              pagination={false}
              scroll={{ y: 220 }}
              locale={{ emptyText: <Empty description="无匹配告警规则" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
            />
          </Card>

          {/* SLO 事件时间线 */}
          <Card size="small" title={<Space><ClockCircleOutlined /> SLO 事件流</Space>}>
            <Timeline
              items={sloEvents.map((e) => ({
                color: eventColor[e.type],
                children: (
                  <div>
                    <Space>
                      <Tag color={eventColor[e.type]}>{eventText[e.type]}</Tag>
                      <Text strong>{e.sloName}</Text>
                    </Space>
                    <div style={{ fontSize: 12, color: '#595959', marginTop: 4 }}>{e.message}</div>
                    <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 4 }}>
                      <ClockCircleOutlined /> {formatDateTime(e.occurredAt)}
                      {e.duration ? <Tag style={{ marginLeft: 8 }}>持续 {e.duration}</Tag> : null}
                    </div>
                  </div>
                ),
              }))}
            />
          </Card>
        </Col>
      </Row>

      {/* 底部：最近事件流 */}
      <Card
        size="small"
        title={<Space><ThunderboltOutlined /> 最近 SLO 相关事件 ({recentEvents.length})</Space>}
        style={{ marginTop: spacing[4] }}
      >
        <Table
          size="small"
          rowKey="id"
          pagination={{ pageSize: 8, size: 'small' }}
          dataSource={recentEvents}
          scroll={{ x: 900 }}
          columns={[
            { title: '时间', dataIndex: 'createdAt', width: 160, render: (v: string) => formatDateTime(v) },
            { title: 'Trace ID', dataIndex: 'traceId', width: 180, ellipsis: true, render: (v: string) => <code>{v}</code> },
            { title: '阶段', dataIndex: 'stage', width: 80, render: (v: Stage) => <Tag color="blue">{StageName[v]}</Tag> },
            {
              title: '状态',
              dataIndex: 'eventType',
              width: 90,
              render: (v: string) => (
                <Tag color={v === 'SUCCESS' ? 'success' : v === 'FAIL' ? 'error' : 'processing'}>
                  {v === 'SUCCESS' ? '成功' : v === 'FAIL' ? '失败' : '开始'}
                </Tag>
              ),
            },
            { title: '耗时', dataIndex: 'durationMs', width: 100, render: (v: number) => `${v} ms` },
            { title: '文件类型', dataIndex: 'fileType', width: 100, render: (v: string) => <Tag>{v}</Tag> },
            { title: '错误码', dataIndex: 'errorCode', ellipsis: true, render: (v?: string) => v ? <Tag color="red">{v}</Tag> : '-' },
          ]}
        />
      </Card>
    </div>
  );
};

export default SloMonitorPage;
