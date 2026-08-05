/**
 * ECharts 懒加载组件
 * - 通过 React.lazy 延迟加载 echarts-for-react，将 echarts 拆到独立 chunk
 * - 仅在首次渲染时拉取图表库，避免首屏负担
 * - props 与 echarts-for-react 默认导出完全一致，可平滑替换
 */
import React, { Suspense } from 'react';
import { Spin } from 'antd';

// 拿到 echarts-for-react 默认导出的 props 类型（type-only，不进入运行时 bundle）
type ReactEChartsComponent = typeof import('echarts-for-react')['default'];
type LazyEChartsProps = React.ComponentProps<ReactEChartsComponent>;

const ReactECharts = React.lazy(async () => {
  const mod = await import('echarts-for-react');
  return { default: mod.default };
});

const Fallback: React.FC = () => (
  <div
    style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      width: '100%',
      height: '100%',
      minHeight: 200,
    }}
  >
    <Spin size="large" />
  </div>
);

const LazyECharts: React.FC<LazyEChartsProps> = (props) => (
  <Suspense fallback={<Fallback />}>
    <ReactECharts {...props} />
  </Suspense>
);

export default LazyECharts;
