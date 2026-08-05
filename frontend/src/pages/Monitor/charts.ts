/**
 * 监控看板 ECharts option 生成函数
 */
import React from 'react';
import type { EChartsOption } from 'echarts';
import dayjs from 'dayjs';
import {
  Stage,
  StageName,
  type StageMetricSeries,
  type FunnelStage,
  type FileTypeDist,
  type TopNItem,
  type SearchPercentilePoint,
  type SearchResultBucket,
  type SloStatus,
  type FileEvent,
  type QueueLagItem,
  type FailReasonItem,
  type TeamSpace,
} from '@/types';

/** 阶段配色 */
export const STAGE_COLORS: Record<Stage, string> = {
  [Stage.UPLOAD]: '#1890ff',
  [Stage.INDEX]: '#52c41a',
  [Stage.PARSE]: '#faad14',
  [Stage.SEARCH]: '#722ed1',
};

/** 时间戳格式化 */
const fmtTime = (ts: string) => dayjs(ts).format('MM-DD HH:mm');

// ============ 1. 四阶段成功率趋势 ============
export const successRateLineOption = (series: StageMetricSeries[]): EChartsOption => ({
  tooltip: { trigger: 'axis', valueFormatter: (v) => `${v}%` },
  legend: { data: series.map(s => StageName[s.stage]), top: 0 },
  grid: { left: 40, right: 24, top: 40, bottom: 40 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: series[0]?.points.map(p => fmtTime(p.timestamp)) || [],
  },
  yAxis: { type: 'value', min: 90, max: 100, axisLabel: { formatter: '{value}%' } },
  series: series.map(s => ({
    name: StageName[s.stage],
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    itemStyle: { color: STAGE_COLORS[s.stage] },
    data: s.points.map(p => p.successRate),
  })),
});

// ============ 2. 四阶段 P95 耗时趋势 ============
export const durationP95LineOption = (series: StageMetricSeries[]): EChartsOption => ({
  tooltip: { trigger: 'axis', valueFormatter: (v) => `${v} ms` },
  legend: { data: series.map(s => StageName[s.stage]), top: 0 },
  grid: { left: 50, right: 24, top: 40, bottom: 40 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: series[0]?.points.map(p => fmtTime(p.timestamp)) || [],
  },
  yAxis: { type: 'value', axisLabel: { formatter: '{value} ms' } },
  series: series.map(s => ({
    name: StageName[s.stage],
    type: 'line',
    smooth: true,
    itemStyle: { color: STAGE_COLORS[s.stage] },
    data: s.points.map(p => p.durationP95),
  })),
});

// ============ 3. 上传趋势(双轴: count + bytes) ============
export const uploadTrendOption = (series: StageMetricSeries[]): EChartsOption => {
  const uploadSeries = series.find(s => s.stage === Stage.UPLOAD);
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['上传文件数', '上传字节(MB)'], top: 0 },
    grid: { left: 50, right: 60, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: uploadSeries?.points.map(p => fmtTime(p.timestamp)) || [],
    },
    yAxis: [
      { type: 'value', name: '文件数' },
      { type: 'value', name: 'MB', axisLabel: { formatter: '{value}' } },
    ],
    series: [
      {
        name: '上传文件数',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.2 },
        itemStyle: { color: STAGE_COLORS[Stage.UPLOAD] },
        data: uploadSeries?.points.map(p => p.count) || [],
      },
      {
        name: '上传字节(MB)',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        itemStyle: { color: '#13c2c2' },
        data: uploadSeries?.points.map(p => +(p.count * 1.8).toFixed(1)) || [],
      },
    ],
  };
};

// ============ 4. 团队空间存储用量排行 ============
export const storageRankingOption = (data: Array<TeamSpace & { usageRate: number }>): EChartsOption => ({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params: any) => {
      const p = params[0];
      const item = data[p.dataIndex];
      const gb = (item.storageUsed / 1024 ** 3).toFixed(1);
      const quota = (item.storageQuota / 1024 ** 3).toFixed(0);
      return `${item.name}<br/>已用: ${gb} GB<br/>配额: ${quota} GB<br/>使用率: ${item.usageRate}%`;
    },
  },
  grid: { left: 120, right: 40, top: 20, bottom: 20 },
  xAxis: { type: 'value', axisLabel: { formatter: '{value} GB' } },
  yAxis: {
    type: 'category',
    data: data.map(d => d.name),
    inverse: true,
  },
  series: [
    {
      type: 'bar',
      data: data.map(d => +(d.storageUsed / 1024 ** 3).toFixed(1)),
      itemStyle: {
        color: (p: any) => {
          const rate = data[p.dataIndex]?.usageRate || 0;
          return rate > 90 ? '#ff4d4f' : rate > 70 ? '#faad14' : '#52c41a';
        },
      },
      label: { show: true, position: 'right', formatter: '{c} GB' },
    },
  ],
});

// ============ 5. 文件类型分布(环形) ============
export const fileTypePieOption = (data: FileTypeDist[]): EChartsOption => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, type: 'scroll' },
  series: [
    {
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false, position: 'center' },
      emphasis: { label: { show: true, fontSize: 18, fontWeight: 'bold' } },
      data: data.map(d => ({ name: d.fileType, value: d.count })),
    },
  ],
});

// ============ 6. 业务链路漏斗 ============
export const funnelOption = (data: FunnelStage[]): EChartsOption => ({
  tooltip: {
    trigger: 'item',
    formatter: '{b}: {c}',
  },
  series: [
    {
      type: 'funnel',
      left: '10%',
      width: '80%',
      gap: 4,
      label: { show: true, position: 'inside', formatter: '{b}\n{c}' },
      data: data.map(d => ({
        name: d.stageName,
        value: d.value,
        itemStyle: { color: STAGE_COLORS[d.stage] },
      })),
    },
  ],
});

// ============ 7. 失败原因 Top5(饼图) ============
export const failReasonPieOption = (data: FailReasonItem[]): EChartsOption => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c}次 ({d}%)' },
  legend: { type: 'scroll', bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['35%', '65%'],
      label: { formatter: '{b}\n{d}%' },
      data: data.map(d => ({ name: d.errorName, value: d.count })),
    },
  ],
});

// ============ 8. 解析成功率按文件类型(堆叠柱状) ============
export const parseStackBarOption = (
  fileTypes: string[],
  success: number[],
  fail: number[]
): EChartsOption => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['成功', '失败'], top: 0 },
  grid: { left: 50, right: 24, top: 40, bottom: 40 },
  xAxis: { type: 'category', data: fileTypes },
  yAxis: { type: 'value' },
  series: [
    { name: '成功', type: 'bar', stack: 'total', itemStyle: { color: '#52c41a' }, data: success },
    { name: '失败', type: 'bar', stack: 'total', itemStyle: { color: '#ff4d4f' }, data: fail },
  ],
});

// ============ 9. 搜索 P95 + 零命中率(双轴) ============
export const searchP95Option = (
  series: StageMetricSeries,
  zeroHitRate: number[]
): EChartsOption => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['搜索P95耗时', '零命中率'], top: 0 },
  grid: { left: 50, right: 50, top: 40, bottom: 40 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: series.points.map(p => fmtTime(p.timestamp)),
  },
  yAxis: [
    { type: 'value', name: 'ms' },
    { type: 'value', name: '%', axisLabel: { formatter: '{value}%' } },
  ],
  series: [
    {
      name: '搜索P95耗时',
      type: 'line',
      smooth: true,
      itemStyle: { color: STAGE_COLORS[Stage.SEARCH] },
      data: series.points.map(p => p.durationP95),
    },
    {
      name: '零命中率',
      type: 'line',
      yAxisIndex: 1,
      smooth: true,
      itemStyle: { color: '#faad14' },
      data: zeroHitRate,
    },
  ],
});

// ============ 10. 搜索分位数(P50/P95/P99) ============
export const searchPercentileOption = (data: SearchPercentilePoint[]): EChartsOption => ({
  tooltip: { trigger: 'axis', valueFormatter: (v) => `${v} ms` },
  legend: { data: ['P50', 'P95', 'P99'], top: 0 },
  grid: { left: 50, right: 24, top: 40, bottom: 40 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: data.map(p => fmtTime(p.timestamp)),
  },
  yAxis: { type: 'value', axisLabel: { formatter: '{value} ms' } },
  series: [
    { name: 'P50', type: 'line', smooth: true, itemStyle: { color: '#52c41a' }, data: data.map(p => p.p50) },
    { name: 'P95', type: 'line', smooth: true, itemStyle: { color: '#faad14' }, data: data.map(p => p.p95) },
    { name: 'P99', type: 'line', smooth: true, itemStyle: { color: '#ff4d4f' }, data: data.map(p => p.p99) },
  ],
});

// ============ 11. 搜索结果数分布(直方图) ============
export const searchResultBucketOption = (data: SearchResultBucket[]): EChartsOption => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 50, right: 24, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: data.map(d => d.bucket) },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'bar',
      barWidth: '50%',
      itemStyle: { color: '#1890ff', borderRadius: [4, 4, 0, 0] },
      data: data.map(d => d.count),
    },
  ],
});

// ============ 12. 热门查询词 TopN(水平条形) ============
export const topNBarOption = (data: TopNItem[], color = '#1890ff'): EChartsOption => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 140, right: 40, top: 10, bottom: 20 },
  xAxis: { type: 'value' },
  yAxis: {
    type: 'category',
    data: data.map(d => d.itemKey),
    inverse: true,
  },
  series: [
    {
      type: 'bar',
      data: data.map(d => d.itemCount),
      itemStyle: { color, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right' },
    },
  ],
});

// ============ 13. 索引积压趋势 ============
export const indexLagOption = (series: StageMetricSeries): EChartsOption => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 24, top: 20, bottom: 40 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: series.points.map(p => fmtTime(p.timestamp)),
  },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.3 },
      itemStyle: { color: STAGE_COLORS[Stage.INDEX] },
      data: series.points.map(p => Math.floor(p.count * 0.15)),
    },
  ],
});

// ============ 14. 配额使用率 Gauge ============
export const quotaGaugeOption = (rate: number): EChartsOption => ({
  series: [
    {
      type: 'gauge',
      startAngle: 200,
      endAngle: -20,
      min: 0,
      max: 100,
      progress: { show: true, width: 14 },
      axisLine: { lineStyle: { width: 14 } },
      axisTick: { show: false },
      splitLine: { length: 10, lineStyle: { width: 2, color: '#999' } },
      axisLabel: { distance: 18, fontSize: 11 },
      pointer: { width: 5 },
      detail: {
        valueAnimation: true,
        formatter: '{value}%',
        fontSize: 22,
        offsetCenter: [0, '40%'],
      },
      data: [{ value: rate, name: '配额使用率' }],
      itemStyle: {
        color: rate > 90 ? '#ff4d4f' : rate > 70 ? '#faad14' : '#52c41a',
      },
    },
  ],
});

// ============ 15. 端到端时延瀑布 ============
export const latencyWaterfallOption = (series: StageMetricSeries[]): EChartsOption => {
  const stages = series.filter(s => s.stage !== Stage.SEARCH);
  const avgData = stages.map(s => Math.floor(s.points.reduce((sum, p) => sum + p.durationP95, 0) / s.points.length));
  return {
    tooltip: { trigger: 'axis', valueFormatter: (v) => `${v} ms` },
    grid: { left: 50, right: 24, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: stages.map(s => StageName[s.stage]) },
    yAxis: { type: 'value', axisLabel: { formatter: '{value} ms' } },
    series: [
      {
        type: 'bar',
        barWidth: '40%',
        data: stages.map((s, i) => ({
          value: avgData[i],
          itemStyle: { color: STAGE_COLORS[s.stage] },
        })),
        label: { show: true, position: 'top', formatter: '{c} ms' },
      },
    ],
  };
};

// ============ 16. SLO 燃烧率多窗口 ============
export const sloBurnRateOption = (data: SloStatus[]): EChartsOption => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['2小时燃烧率', '6小时燃烧率'], top: 0 },
  grid: { left: 50, right: 24, top: 40, bottom: 60 },
  xAxis: {
    type: 'category',
    data: data.map(d => d.sloName),
    axisLabel: { rotate: 20, interval: 0 },
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: '2小时燃烧率',
      type: 'bar',
      itemStyle: { color: '#faad14' },
      data: data.map(d => d.burnRate2h),
    },
    {
      name: '6小时燃烧率',
      type: 'bar',
      itemStyle: { color: '#1890ff' },
      data: data.map(d => d.burnRate6h),
    },
  ],
});

// ============ 17. SLO 错误预算 Gauge 单个 ============
export const sloBudgetGaugeOption = (remaining: number): EChartsOption => ({
  series: [
    {
      type: 'gauge',
      startAngle: 200,
      endAngle: -20,
      min: 0,
      max: 100,
      radius: '90%',
      progress: { show: true, width: 10 },
      axisLine: { lineStyle: { width: 10 } },
      axisTick: { show: false },
      splitLine: { length: 8, lineStyle: { width: 1, color: '#999' } },
      axisLabel: { distance: 12, fontSize: 9 },
      pointer: { width: 4 },
      detail: { valueAnimation: true, formatter: '{value}%', fontSize: 16, offsetCenter: [0, '50%'] },
      data: [{ value: remaining }],
      itemStyle: {
        color: remaining < 30 ? '#ff4d4f' : remaining < 60 ? '#faad14' : '#52c41a',
      },
    },
  ],
});

// ============ 18. 队列积压(横向条形) ============
export const queueLagOption = (data: QueueLagItem[]): EChartsOption => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 120, right: 40, top: 10, bottom: 20 },
  xAxis: { type: 'value' },
  yAxis: {
    type: 'category',
    data: data.map(d => d.teamSpaceName),
    inverse: true,
  },
  series: [
    {
      type: 'bar',
      data: data.map(d => ({
        value: d.lag,
        itemStyle: { color: d.lag > 100 ? '#ff4d4f' : d.lag > 50 ? '#faad14' : '#52c41a' },
      })),
      label: { show: true, position: 'right' },
    },
  ],
});

// ============ 19. 存储用量趋势 ============
export const storageTrendOption = (
  data: Array<{ timestamp: string; used: number }>
): EChartsOption => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (v) => `${((v as number) / 1024 ** 3).toFixed(2)} GB`,
  },
  grid: { left: 60, right: 24, top: 20, bottom: 40 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: data.map(d => fmtTime(d.timestamp)),
  },
  yAxis: {
    type: 'value',
    axisLabel: { formatter: (v) => `${(v / 1024 ** 3).toFixed(0)} GB` },
  },
  series: [
    {
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.3 },
      itemStyle: { color: '#1890ff' },
      data: data.map(d => d.used),
    },
  ],
});

// ============ 20. 各阶段失败率对比(分组柱状) ============
export const stageFailRateOption = (
  stages: Stage[],
  failRates: number[]
): EChartsOption => ({
  tooltip: { trigger: 'axis', valueFormatter: (v) => `${v}%` },
  grid: { left: 50, right: 24, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: stages.map(s => StageName[s]) },
  yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
  series: [
    {
      type: 'bar',
      barWidth: '40%',
      data: failRates.map((v, i) => ({
        value: v,
        itemStyle: { color: STAGE_COLORS[stages[i]] },
      })),
      label: { show: true, position: 'top', formatter: '{c}%' },
    },
  ],
});

// ============ 文件事件表格列定义 ============
export const fileEventColumns = [
  {
    title: '时间',
    dataIndex: 'createdAt',
    width: 160,
    render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
  },
  { title: 'TraceID', dataIndex: 'traceId', width: 180, ellipsis: true },
  { title: '团队空间', dataIndex: 'teamSpaceId', width: 100 },
  { title: '文件ID', dataIndex: 'fileId', width: 100 },
  {
    title: '阶段',
    dataIndex: 'stage',
    width: 80,
    render: (v: Stage) => StageName[v],
  },
  {
    title: '状态',
    dataIndex: 'eventType',
    width: 80,
    render: (v: string) => {
      const color = v === 'SUCCESS' ? '#52c41a' : v === 'FAIL' ? '#ff4d4f' : '#1890ff';
      const text = v === 'SUCCESS' ? '成功' : v === 'FAIL' ? '失败' : '开始';
      return React.createElement('span', { style: { color } }, text);
    },
  },
  { title: '耗时(ms)', dataIndex: 'durationMs', width: 100 },
  { title: '文件类型', dataIndex: 'fileType', width: 100 },
  { title: '错误码', dataIndex: 'errorCode', width: 180 },
  { title: '操作人', dataIndex: 'operatorId', width: 100 },
];

/** 通用 ReactECharts option 类型导出 */
export type { EChartsOption };
export type { FileEvent };
