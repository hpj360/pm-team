/**
 * 漏斗分析深度页
 * - 顶部：阶段概览 + 转化率统计
 * - 主体：阶段详情列表 + 转化率趋势 + 失败原因 TopN
 * - 右侧：失败原因饼图 + 队列积压 + 链路追踪
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
  Progress,
  Tabs,
  Select,
  Input,
  Descriptions,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  LineChartOutlined,
  ThunderboltOutlined,
  FireOutlined,
  ClockCircleOutlined,
  SearchOutlined,
  DownloadOutlined,
  CloseCircleOutlined,
  FilterOutlined,
  RiseOutlined,
  FallOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import {
  getMockFunnel,
  getMockStageSeries,
  getMockQueueLag,
  getMockFileEvents,
  getMockFailReasons,
  getMockSearchPercentile,
  mockTeamSpaces,
} from '@/mock/monitor';
import {
  Stage,
  StageName,
  type FunnelStage,
  type QueueLagItem,
  type FileEvent,
  type FailReasonItem,
  type TimeRange,
} from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text } = Typography;

/** 阶段颜色 */
const stageColor: Record<Stage, string> = {
  [Stage.UPLOAD]: '#1890ff',
  [Stage.INDEX]: '#52c41a',
  [Stage.PARSE]: '#faad14',
  [Stage.SEARCH]: '#722ed1',
};

/** 阶段中文名 */
const stageText: Record<Stage, string> = {
  [Stage.UPLOAD]: '上传',
  [Stage.INDEX]: '索引',
  [Stage.PARSE]: '解析',
  [Stage.SEARCH]: '搜索',
};

const FunnelAnalysisPage: React.FC = () => {
  const navigate = useNavigate();
  const [timeRange, setTimeRange] = useState<TimeRange>('24h');
  const [teamSpaceId, setTeamSpaceId] = useState<number | undefined>(undefined);
  const [activeStage, setActiveStage] = useState<Stage>(Stage.PARSE);
  const [traceSearch, setTraceSearch] = useState('');

  const funnel = useMemo(() => getMockFunnel(teamSpaceId), [teamSpaceId]);
  const stageSeries = useMemo(() => getMockStageSeries(timeRange, teamSpaceId), [timeRange, teamSpaceId]);
  const queueLag = useMemo(() => getMockQueueLag(), []);
  const events = useMemo(() => getMockFileEvents(teamSpaceId, 80), [teamSpaceId]);
  const failReasons = useMemo(() => getMockFailReasons(activeStage, teamSpaceId), [activeStage, teamSpaceId]);
  const searchPercentile = useMemo(() => getMockSearchPercentile(timeRange, teamSpaceId), [timeRange, teamSpaceId]);

  /** 漏斗转化统计 */
  const funnelStats = useMemo(() => {
    if (funnel.length < 2) return { uploadToIndex: 0, indexToParse: 0, overall: 0 };
    const upload = funnel[0].value;
    const index = funnel[1].value;
    const parse = funnel[2].value;
    return {
      uploadToIndex: +((index / upload) * 100).toFixed(2),
      indexToParse: +((parse / index) * 100).toFixed(2),
      overall: +((parse / upload) * 100).toFixed(2),
    };
  }, [funnel]);

  /** 当前阶段时间序列 */
  const activeStageSeries = stageSeries.find((s) => s.stage === activeStage);
  const avgP95 = activeStageSeries
    ? Math.floor(activeStageSeries.points.reduce((sum, p) => sum + p.durationP95, 0) / activeStageSeries.points.length)
    : 0;
  const avgSuccess = activeStageSeries
    ? +(activeStageSeries.points.reduce((sum, p) => sum + p.successRate, 0) / activeStageSeries.points.length).toFixed(2)
    : 0;
  const totalCnt = activeStageSeries?.points.reduce((sum, p) => sum + p.count, 0) ?? 0;

  /** 漏斗图 */
  const funnelChartOption: EChartsOption = useMemo(() => ({
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    series: [
      {
        type: 'funnel',
        left: '10%',
        width: '80%',
        gap: 4,
        label: { show: true, position: 'inside', formatter: '{b}\n{c}' },
        data: funnel.map((d) => ({
          name: d.stageName,
          value: d.value,
          itemStyle: { color: stageColor[d.stage] },
        })),
      },
    ],
  }), [funnel]);

  /** 转化率趋势 */
  const conversionTrendOption: EChartsOption = useMemo(() => {
    const uploadSeries = stageSeries.find((s) => s.stage === Stage.UPLOAD);
    const indexSeries = stageSeries.find((s) => s.stage === Stage.INDEX);
    const parseSeries = stageSeries.find((s) => s.stage === Stage.PARSE);
    if (!uploadSeries || !indexSeries || !parseSeries) return {};
    return {
      tooltip: { trigger: 'axis', valueFormatter: (v) => `${v}%` },
      legend: { data: ['上传→索引', '索引→解析', '端到端'], top: 0 },
      grid: { left: 50, right: 24, top: 40, bottom: 40 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: uploadSeries.points.map((_, i) => `T-${uploadSeries.points.length - i}`),
      },
      yAxis: { type: 'value', min: 80, max: 100, axisLabel: { formatter: '{value}%' } },
      series: [
        {
          name: '上传→索引',
          type: 'line',
          smooth: true,
          itemStyle: { color: stageColor[Stage.UPLOAD] },
          data: uploadSeries.points.map((p, i) => +((indexSeries.points[i].count / p.count) * 100).toFixed(2)),
        },
        {
          name: '索引→解析',
          type: 'line',
          smooth: true,
          itemStyle: { color: stageColor[Stage.INDEX] },
          data: indexSeries.points.map((p, i) => +((parseSeries.points[i].count / p.count) * 100).toFixed(2)),
        },
        {
          name: '端到端',
          type: 'line',
          smooth: true,
          itemStyle: { color: stageColor[Stage.PARSE] },
          data: uploadSeries.points.map((p, i) => +((parseSeries.points[i].count / p.count) * 100).toFixed(2)),
        },
      ],
    };
  }, [stageSeries]);

  /** 阶段 P95 时延瀑布 */
  const waterfallOption: EChartsOption = useMemo(() => {
    const stages = stageSeries.filter((s) => s.stage !== Stage.SEARCH);
    const avgData = stages.map((s) => Math.floor(s.points.reduce((sum, p) => sum + p.durationP95, 0) / s.points.length));
    return {
      tooltip: { trigger: 'axis', valueFormatter: (v) => `${v} ms` },
      grid: { left: 50, right: 24, top: 20, bottom: 40 },
      xAxis: { type: 'category', data: stages.map((s) => StageName[s.stage]) },
      yAxis: { type: 'value', axisLabel: { formatter: '{value} ms' } },
      series: [
        {
          type: 'bar',
          barWidth: '40%',
          data: stages.map((s, i) => ({ value: avgData[i], itemStyle: { color: stageColor[s.stage] } })),
          label: { show: true, position: 'top', formatter: '{c} ms' },
        },
      ],
    };
  }, [stageSeries]);

  /** 当前阶段成功率/耗时趋势 */
  const stageTrendOption: EChartsOption = useMemo(() => {
    if (!activeStageSeries) return {};
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['成功率', 'P95耗时', '处理量'], top: 0 },
      grid: { left: 50, right: 50, top: 40, bottom: 40 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: activeStageSeries.points.map((_, i) => `T-${activeStageSeries.points.length - i}`),
      },
      yAxis: [
        { type: 'value', name: '%', min: 80, max: 100 },
        { type: 'value', name: 'ms / cnt' },
      ],
      series: [
        {
          name: '成功率',
          type: 'line',
          smooth: true,
          itemStyle: { color: colors.success },
          data: activeStageSeries.points.map((p) => p.successRate),
        },
        {
          name: 'P95耗时',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          itemStyle: { color: colors.warning },
          data: activeStageSeries.points.map((p) => p.durationP95),
        },
        {
          name: '处理量',
          type: 'bar',
          yAxisIndex: 1,
          itemStyle: { color: stageColor[activeStage], opacity: 0.6 },
          data: activeStageSeries.points.map((p) => p.count),
        },
      ],
    };
  }, [activeStageSeries, activeStage]);

  /** 失败原因饼图 */
  const failReasonOption: EChartsOption = useMemo(() => ({
    tooltip: { trigger: 'item', formatter: '{b}: {c}次 ({d}%)' },
    legend: { type: 'scroll', bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['35%', '65%'],
        label: { formatter: '{b}\n{d}%' },
        data: failReasons.map((d) => ({ name: d.errorName, value: d.count })),
      },
    ],
  }), [failReasons]);

  /** 搜索分位数 P50/P95/P99 */
  const percentileOption: EChartsOption = useMemo(() => ({
    tooltip: { trigger: 'axis', valueFormatter: (v) => `${v} ms` },
    legend: { data: ['P50', 'P95', 'P99'], top: 0 },
    grid: { left: 50, right: 24, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: searchPercentile.map((_, i) => `T-${searchPercentile.length - i}`),
    },
    yAxis: { type: 'value', axisLabel: { formatter: '{value} ms' } },
    series: [
      { name: 'P50', type: 'line', smooth: true, itemStyle: { color: colors.success }, data: searchPercentile.map((p) => p.p50) },
      { name: 'P95', type: 'line', smooth: true, itemStyle: { color: colors.warning }, data: searchPercentile.map((p) => p.p95) },
      { name: 'P99', type: 'line', smooth: true, itemStyle: { color: colors.error }, data: searchPercentile.map((p) => p.p99) },
    ],
  }), [searchPercentile]);

  /** 队列积压条形图 */
  const queueLagOption: EChartsOption = useMemo(() => ({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 120, right: 40, top: 10, bottom: 20 },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: queueLag.map((d) => d.teamSpaceName),
      inverse: true,
    },
    series: [
      {
        type: 'bar',
        data: queueLag.map((d) => ({
          value: d.lag,
          itemStyle: { color: d.lag > 100 ? colors.error : d.lag > 50 ? colors.warning : colors.success },
        })),
        label: { show: true, position: 'right' },
      },
    ],
  }), [queueLag]);

  /** 阶段详情列 */
  const stageColumns: ColumnsType<FunnelStage> = [
    {
      title: '阶段',
      dataIndex: 'stage',
      width: 120,
      render: (v: Stage) => (
        <Space>
          <Tag color={stageColor[v]}>●</Tag>
          <Text strong>{stageText[v]}</Text>
        </Space>
      ),
    },
    { title: '阶段名称', dataIndex: 'stageName', width: 120 },
    {
      title: '处理量',
      dataIndex: 'value',
      width: 120,
      sorter: (a, b) => a.value - b.value,
      render: (v: number) => <Text strong>{v.toLocaleString()}</Text>,
    },
    {
      title: '占比',
      key: 'rate',
      width: 200,
      render: (_, record) => {
        const total = funnel[0]?.value || 1;
        const rate = +((record.value / total) * 100).toFixed(2);
        return (
          <Progress
            percent={rate}
            size="small"
            strokeColor={stageColor[record.stage]}
            format={(p) => `${p}%`}
          />
        );
      },
    },
    {
      title: '环比',
      key: 'delta',
      width: 100,
      render: (record, _prev, idx) => {
        if (idx === 0) return <Tag color="default">起点</Tag>;
        const prev = funnel[idx - 1].value;
        const curr = record.value;
        const delta = +(((curr - prev) / prev) * 100).toFixed(2);
        return delta >= 0
          ? <Tag icon={<RiseOutlined />} color="red">+{delta}%</Tag>
          : <Tag icon={<FallOutlined />} color="green">{delta}%</Tag>;
      },
    },
  ];

  /** 链路追踪列 */
  const traceColumns: ColumnsType<FileEvent> = [
    { title: '时间', dataIndex: 'createdAt', width: 160, render: (v: string) => formatDateTime(v) },
    { title: 'Trace ID', dataIndex: 'traceId', width: 180, ellipsis: true, render: (v: string) => <code style={{ fontSize: 12 }}>{v}</code> },
    { title: '空间', dataIndex: 'teamSpaceId', width: 90 },
    {
      title: '阶段',
      dataIndex: 'stage',
      width: 80,
      render: (v: Stage) => <Tag color={stageColor[v]}>{stageText[v]}</Tag>,
    },
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
    { title: '耗时', dataIndex: 'durationMs', width: 100, sorter: (a, b) => a.durationMs - b.durationMs, render: (v: number) => `${v} ms` },
    { title: '文件类型', dataIndex: 'fileType', width: 100, render: (v: string) => <Tag>{v}</Tag> },
    { title: '错误码', dataIndex: 'errorCode', ellipsis: true, render: (v?: string) => v ? <Tag color="red">{v}</Tag> : '-' },
  ];

  /** 失败原因列 */
  const failReasonColumns: ColumnsType<FailReasonItem> = [
    { title: '错误码', dataIndex: 'errorCode', width: 180, render: (v: string) => <code>{v}</code> },
    { title: '错误名称', dataIndex: 'errorName' },
    {
      title: '出现次数',
      dataIndex: 'count',
      width: 120,
      sorter: (a, b) => a.count - b.count,
      render: (v: number) => <Text strong style={{ color: v > 200 ? colors.error : v > 100 ? colors.warning : colors.info }}>{v}</Text>,
    },
    {
      title: '占比',
      key: 'rate',
      width: 150,
      render: (_, record) => {
        const total = failReasons.reduce((s, x) => s + x.count, 0) || 1;
        const rate = +((record.count / total) * 100).toFixed(2);
        return <Progress percent={rate} size="small" strokeColor={colors.error} format={(p) => `${p}%`} />;
      },
    },
  ];

  /** 队列积压列 */
  const queueColumns: ColumnsType<QueueLagItem> = [
    { title: '排名', key: 'rank', width: 60, render: (_, __, i) => i + 1 },
    { title: '团队空间', dataIndex: 'teamSpaceName' },
    {
      title: '积压数',
      dataIndex: 'lag',
      width: 120,
      sorter: (a, b) => b.lag - a.lag,
      render: (v: number) => (
        <Tag color={v > 100 ? 'error' : v > 50 ? 'warning' : 'success'} style={{ fontSize: 13 }}>
          {v}
        </Tag>
      ),
    },
    {
      title: '积压等级',
      key: 'level',
      width: 120,
      render: (_, record) => (
        <Tag color={record.lag > 100 ? 'red' : record.lag > 50 ? 'orange' : 'green'}>
          {record.lag > 100 ? '严重' : record.lag > 50 ? '中等' : '正常'}
        </Tag>
      ),
    },
  ];

  /** 过滤事件 */
  const filteredEvents = useMemo(() => {
    if (!traceSearch) return events;
    const kw = traceSearch.toLowerCase();
    return events.filter((e) => e.traceId.toLowerCase().includes(kw) || String(e.fileId).includes(kw));
  }, [events, traceSearch]);

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/monitor')}>返回看板</Button>
          <LineChartOutlined style={{ fontSize: 22, color: colors.primary[500] }} />
          <Title level={4} style={{ margin: 0 }}>业务链路漏斗分析</Title>
          <Tag icon={<FilterOutlined />} color="blue">深度分析</Tag>
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
          <Button icon={<DownloadOutlined />}>导出报告</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="上传总量"
              value={funnel[0]?.value ?? 0}
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: stageColor[Stage.UPLOAD] }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="上传→索引 转化"
              value={funnelStats.uploadToIndex}
              suffix="%"
              prefix={<RiseOutlined />}
              valueStyle={{ color: stageColor[Stage.INDEX] }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="索引→解析 转化"
              value={funnelStats.indexToParse}
              suffix="%"
              prefix={<RiseOutlined />}
              valueStyle={{ color: stageColor[Stage.PARSE] }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="端到端转化率"
              value={funnelStats.overall}
              suffix="%"
              prefix={<FireOutlined />}
              valueStyle={{ color: funnelStats.overall > 85 ? colors.success : colors.warning }}
            />
          </Card>
        </Col>
      </Row>

      {/* 上半部分：漏斗 + 转化趋势 + 时延瀑布 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={24} lg={10}>
          <Card size="small" title={<Space><FilterOutlined /> 上传→索引→解析 漏斗</Space>} style={{ height: '100%' }}>
            <ReactECharts option={funnelChartOption} style={{ height: 320, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card size="small" title={<Space><RiseOutlined /> 转化率趋势</Space>} style={{ height: '100%' }}>
            <ReactECharts option={conversionTrendOption} style={{ height: 320, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
        <Col xs={24} lg={6}>
          <Card size="small" title={<Space><ClockCircleOutlined /> P95 时延瀑布</Space>} style={{ height: '100%' }}>
            <ReactECharts option={waterfallOption} style={{ height: 320, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
      </Row>

      {/* 阶段详情表格 */}
      <Card
        size="small"
        title={<Space><ThunderboltOutlined /> 阶段详情</Space>}
        style={{ marginBottom: spacing[4] }}
      >
        <Table
          size="small"
          rowKey="stage"
          columns={stageColumns}
          dataSource={funnel}
          pagination={false}
          scroll={{ x: 700 }}
        />
      </Card>

      {/* 中部：阶段深度分析 */}
      <Card
        size="small"
        title={
          <Space>
            <LineChartOutlined />
            阶段深度分析
            <Select
              size="small"
              value={activeStage}
              onChange={(v) => setActiveStage(v)}
              style={{ width: 120, marginLeft: 8 }}
              options={(Object.values(Stage) as Stage[]).map((s) => ({ label: stageText[s], value: s }))}
            />
          </Space>
        }
        style={{ marginBottom: spacing[4] }}
      >
        <Tabs
          items={[
            {
              key: 'trend',
              label: <span><LineChartOutlined /> 趋势</span>,
              children: (
                <Row gutter={16}>
                  <Col xs={24} lg={16}>
                    <ReactECharts option={stageTrendOption} style={{ height: 320, width: '100%' }} notMerge lazyUpdate />
                  </Col>
                  <Col xs={24} lg={8}>
                    <Descriptions column={1} size="small" bordered>
                      <Descriptions.Item label="阶段">{stageText[activeStage]}</Descriptions.Item>
                      <Descriptions.Item label="平均 P95">{avgP95} ms</Descriptions.Item>
                      <Descriptions.Item label="平均成功率">{avgSuccess} %</Descriptions.Item>
                      <Descriptions.Item label="处理总量">{totalCnt.toLocaleString()}</Descriptions.Item>
                      <Descriptions.Item label="状态">
                        <Tag color={avgSuccess >= 98 ? 'success' : avgSuccess >= 95 ? 'warning' : 'error'}>
                          {avgSuccess >= 98 ? '健康' : avgSuccess >= 95 ? '需关注' : '异常'}
                        </Tag>
                      </Descriptions.Item>
                    </Descriptions>
                  </Col>
                </Row>
              ),
            },
            {
              key: 'fail',
              label: <span><CloseCircleOutlined /> 失败原因 ({failReasons.length})</span>,
              children: (
                <Row gutter={16}>
                  <Col xs={24} lg={14}>
                    <Table
                      size="small"
                      rowKey="errorCode"
                      columns={failReasonColumns}
                      dataSource={failReasons}
                      pagination={false}
                      scroll={{ y: 280 }}
                    />
                  </Col>
                  <Col xs={24} lg={10}>
                    <ReactECharts option={failReasonOption} style={{ height: 280, width: '100%' }} notMerge lazyUpdate />
                  </Col>
                </Row>
              ),
            },
          ]}
        />
      </Card>

      {/* 下部：搜索分位数 + 队列积压 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={24} lg={14}>
          <Card size="small" title={<Space><LineChartOutlined /> 搜索分位数 P50/P95/P99</Space>} style={{ height: '100%' }}>
            <ReactECharts option={percentileOption} style={{ height: 300, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card size="small" title={<Space><ThunderboltOutlined /> 解析队列积压 Top 空间</Space>} style={{ height: '100%' }}>
            <ReactECharts option={queueLagOption} style={{ height: 300, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
      </Row>

      {/* 队列积压表格 */}
      <Card
        size="small"
        title={<Space><ThunderboltOutlined /> 队列积压明细 ({queueLag.length})</Space>}
        style={{ marginBottom: spacing[4] }}
      >
        <Table
          size="small"
          rowKey="teamSpaceId"
          columns={queueColumns}
          dataSource={queueLag}
          pagination={false}
          scroll={{ x: 500 }}
        />
      </Card>

      {/* 链路追踪 */}
      <Card
        size="small"
        title={
          <Space style={{ justifyContent: 'space-between', width: '100%' }}>
            <Space><FilterOutlined /> 链路追踪详情 ({filteredEvents.length})</Space>
            <Input
              size="small"
              placeholder="搜索 Trace ID / 文件 ID"
              prefix={<SearchOutlined />}
              value={traceSearch}
              onChange={(e) => setTraceSearch(e.target.value)}
              style={{ width: 240 }}
              allowClear
            />
          </Space>
        }
      >
        <Table
          size="small"
          rowKey="id"
          columns={traceColumns}
          dataSource={filteredEvents}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          scroll={{ x: 1000 }}
          locale={{ emptyText: <Empty description="无匹配事件" /> }}
        />
      </Card>
    </div>
  );
};

export default FunnelAnalysisPage;
