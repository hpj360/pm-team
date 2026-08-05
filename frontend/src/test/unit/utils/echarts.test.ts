/**
 * 单元测试：ECharts 按需引入模块 src/utils/echarts.ts
 * 注：echarts/core 已在 setup.ts 中被 mock，此处仅验证导出
 */
import { describe, it, expect } from 'vitest';
import * as echartsModule from '@/utils/echarts';
import echartsDefault from '@/utils/echarts';

describe('echarts 模块导出', () => {
  it('命名导出 echarts 对象存在', () => {
    expect(echartsModule.echarts).toBeDefined();
    expect(typeof echartsModule.echarts).toBe('object');
  });

  it('默认导出与命名导出一致', () => {
    expect(echartsDefault).toBe(echartsModule.echarts);
  });

  it('包含 use 方法（用于注册组件）', () => {
    expect(typeof echartsModule.echarts.use).toBe('function');
  });

  it('调用 use 不抛错', () => {
    expect(() => echartsModule.echarts.use([])).not.toThrow();
  });
});
