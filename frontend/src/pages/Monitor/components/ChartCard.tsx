/**
 * 通用图表卡片：统一标题、容器、空态
 */
import React from 'react';
import { Card, Empty, Spin } from 'antd';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';

interface ChartCardProps {
  title: string;
  option: EChartsOption;
  height?: number;
  loading?: boolean;
  extra?: React.ReactNode;
}

const ChartCard: React.FC<ChartCardProps> = ({ title, option, height = 320, loading, extra }) => {
  return (
    <Card
      title={title}
      size="small"
      extra={extra}
      bodyStyle={{ padding: 12 }}
      style={{ height: '100%' }}
    >
      {loading ? (
        <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Spin />
        </div>
      ) : option ? (
        <ReactECharts
          option={option}
          style={{ height, width: '100%' }}
          notMerge
          lazyUpdate
        />
      ) : (
        <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Empty />
        </div>
      )}
    </Card>
  );
};

export default ChartCard;
