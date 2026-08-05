/**
 * ECharts 按需引入
 * - 统一注册项目用到的图表与组件
 * - 减小打包体积（仅打包用到的部分）
 *
 * 使用方式：
 * ```ts
 * import ReactECharts from 'echarts-for-react';
 * import type { EChartsOption } from '@/utils/echarts';
 * // 不要直接 import 'echarts'，所有依赖通过本文件统一注册
 * ```
 */
import * as echarts from 'echarts/core';

// 图表类型（按需）
import { GraphChart } from 'echarts/charts';
import { LineChart } from 'echarts/charts';
import { BarChart } from 'echarts/charts';
import { PieChart } from 'echarts/charts';
import { FunnelChart } from 'echarts/charts';
import { GaugeChart } from 'echarts/charts';
import { ScatterChart } from 'echarts/charts';

// 组件（按需）
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DataZoomComponent,
  VisualMapComponent,
  ToolboxComponent,
  MarkLineComponent,
  MarkPointComponent,
  GraphicComponent,
  AriaComponent,
} from 'echarts/components';

// 渲染器
import { CanvasRenderer } from 'echarts/renderers';

// 一次性注册所有用到的图表与组件
echarts.use([
  // 图表
  GraphChart,
  LineChart,
  BarChart,
  PieChart,
  FunnelChart,
  GaugeChart,
  ScatterChart,
  // 组件
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DataZoomComponent,
  VisualMapComponent,
  ToolboxComponent,
  MarkLineComponent,
  MarkPointComponent,
  GraphicComponent,
  AriaComponent,
  // 渲染器
  CanvasRenderer,
]);

// 重新导出常用类型与工具
export type { EChartsOption, EChartsType } from 'echarts';
export { echarts };
export default echarts;
