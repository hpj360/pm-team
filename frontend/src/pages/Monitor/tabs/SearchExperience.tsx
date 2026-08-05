/**
 * 搜索体验看板
 */
import React, { useMemo } from 'react';
import { Row, Col } from 'antd';
import ChartCard from '../components/ChartCard';
import {
  searchPercentileOption,
  searchResultBucketOption,
  topNBarOption,
} from '../charts';
import {
  getMockSearchPercentile,
  getMockSearchResultBuckets,
  getMockHotQueries,
  getMockZeroHitQueries,
  getMockStageSeries,
} from '@/mock/monitor';
import { Stage, type MonitorFilter } from '@/types';

interface Props {
  filter: MonitorFilter;
}

const SearchExperience: React.FC<Props> = ({ filter }) => {
  const percentile = useMemo(
    () => getMockSearchPercentile(filter.timeRange, filter.teamSpaceId),
    [filter]
  );
  const buckets = useMemo(() => getMockSearchResultBuckets(filter.teamSpaceId), [filter]);
  const hotQueries = useMemo(() => getMockHotQueries(filter.teamSpaceId), [filter]);
  const zeroHitQueries = useMemo(() => getMockZeroHitQueries(filter.teamSpaceId), [filter]);
  const stageSeries = useMemo(
    () => getMockStageSeries(filter.timeRange, filter.teamSpaceId),
    [filter]
  );
  const searchSeries = stageSeries.find(s => s.stage === Stage.SEARCH)!;
  const zeroHitRate = searchSeries.points.map(() => +(Math.random() * 18 + 4).toFixed(2));

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <ChartCard title="搜索耗时分位数(P50/P95/P99)" option={searchPercentileOption(percentile)} height={300} />
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard title="搜索结果数分布" option={searchResultBucketOption(buckets)} height={300} />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard title="热门查询词 Top20" option={topNBarOption(hotQueries, '#1890ff')} height={360} />
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard title="零命中查询词 Top20" option={topNBarOption(zeroHitQueries, '#faad14')} height={360} />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard title="搜索 QPS 与零命中率趋势" option={searchPercentileQpsOption(searchSeries, zeroHitRate)} height={300} />
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard title="查询类型分布(环形)" option={queryTypePieOption()} height={300} />
        </Col>
      </Row>
    </div>
  );
};

// 搜索 QPS + 零命中率(双轴)
const searchPercentileQpsOption = (series: { points: Array<{ timestamp: string; count: number }> }, zeroHitRate: number[]) => {
  // 复用 import 后通过 echarts 内置类型推导
  return {
    tooltip: { trigger: 'axis' as const },
    legend: { data: ['搜索QPS', '零命中率'], top: 0 },
    grid: { left: 50, right: 50, top: 40, bottom: 40 },
    xAxis: {
      type: 'category' as const,
      boundaryGap: false,
      data: series.points.map(p => p.timestamp),
    },
    yAxis: [
      { type: 'value' as const, name: 'QPS' },
      { type: 'value' as const, name: '%', axisLabel: { formatter: '{value}%' } },
    ],
    series: [
      {
        name: '搜索QPS',
        type: 'line' as const,
        smooth: true,
        itemStyle: { color: '#722ed1' },
        data: series.points.map(p => p.count),
      },
      {
        name: '零命中率',
        type: 'line' as const,
        yAxisIndex: 1,
        smooth: true,
        itemStyle: { color: '#faad14' },
        data: zeroHitRate,
      },
    ],
  };
};

// 查询类型分布
const queryTypePieOption = () => ({
  tooltip: { trigger: 'item' as const, formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie' as const,
      radius: ['40%', '70%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{d}%' },
      data: [
        { name: '关键词', value: 5200, itemStyle: { color: '#1890ff' } },
        { name: '高级',   value: 1800, itemStyle: { color: '#52c41a' } },
        { name: '语义',   value: 900,  itemStyle: { color: '#722ed1' } },
      ],
    },
  ],
});

export default SearchExperience;
