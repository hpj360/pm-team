/**
 * 健康分仪表盘组件
 * 用于空间健康分、配置健康分等场景，基于 ECharts gauge 实现
 * 支持尺寸自适应、颜色分级、维度明细展示
 */
import React, { useMemo } from 'react';
import { Tooltip, Typography, Space } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import LazyECharts from '@/components/common/LazyECharts';
import type { SpaceHealthDimension } from '@/types/ops';

const { Text } = Typography;

export interface HealthScoreGaugeProps {
  /** 健康分 0-100 */
  value: number;
  /** 标题，默认 "健康分" */
  title?: string;
  /** 宽度，默认 220 */
  width?: number;
  /** 高度，默认 180 */
  height?: number;
  /** 维度明细（hover 显示） */
  dimensions?: SpaceHealthDimension[];
}

/** 根据分值返回颜色（>=90 绿 / 70-89 蓝 / 50-69 橙 / <50 红） */
function colorOf(score: number): string {
  if (score >= 90) return '#52c41a';
  if (score >= 70) return '#1677ff';
  if (score >= 50) return '#faad14';
  return '#ff4d4f';
}

/** 等级文本 */
function levelOf(score: number): string {
  if (score >= 90) return '优秀';
  if (score >= 70) return '良好';
  if (score >= 50) return '一般';
  return '风险';
}

const HealthScoreGauge: React.FC<HealthScoreGaugeProps> = ({
  value,
  title = '健康分',
  width = 220,
  height = 180,
  dimensions = [],
}) => {
  const safeValue = Math.max(0, Math.min(100, value));
  const color = colorOf(safeValue);

  const option = useMemo(
    () => ({
      series: [
        {
          type: 'gauge',
          startAngle: 200,
          endAngle: -20,
          min: 0,
          max: 100,
          radius: '95%',
          progress: {
            show: true,
            width: 14,
            roundCap: true,
            itemStyle: { color },
          },
          axisLine: {
            lineStyle: { width: 14, color: [[1, '#f0f0f0']] },
          },
          pointer: { show: false },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { show: false },
          anchor: { show: false },
          detail: {
            valueAnimation: true,
            offsetCenter: [0, '10%'],
            fontSize: 28,
            fontWeight: 'bold' as const,
            color,
            formatter: '{value}',
          },
          title: {
            offsetCenter: [0, '45%'],
            fontSize: 12,
            color: '#8c8c8c',
          },
          data: [{ value: safeValue, name: levelOf(safeValue) }],
        },
      ],
    }),
    [safeValue, color],
  );

  const hasDimensions = dimensions.length > 0;

  return (
    <div style={{ width, textAlign: 'center', display: 'inline-block' }}>
      <div style={{ marginBottom: 4 }}>
        <Space size={4}>
          <Text strong>{title}</Text>
          {hasDimensions && (
            <Tooltip
              title={
                <div>
                  {dimensions.map((d) => (
                    <div key={d.name} style={{ marginBottom: 4 }}>
                      <strong>{d.name}</strong>: {d.score}/{d.full}
                      <div style={{ color: '#bbb', fontSize: 11 }}>{d.reason}</div>
                    </div>
                  ))}
                </div>
              }
            >
              <InfoCircleOutlined style={{ color: '#8c8c8c', cursor: 'help' }} />
            </Tooltip>
          )}
        </Space>
      </div>
      <LazyECharts option={option} style={{ height, width: '100%' }} />
    </div>
  );
};

export default HealthScoreGauge;
