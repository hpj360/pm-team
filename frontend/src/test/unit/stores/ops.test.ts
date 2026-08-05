/**
 * 单元测试：应用运维 Store（src/stores/ops.ts）
 * - 当前空间 ID 状态
 * - 治愈操作类型状态
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { useOpsStore } from '@/stores/ops';

describe('useOpsStore', () => {
  beforeEach(() => {
    useOpsStore.getState().setCurrentSpaceId(null);
    useOpsStore.getState().setHealJobType(null);
  });

  it('初始状态：currentSpaceId 为 null', () => {
    expect(useOpsStore.getState().currentSpaceId).toBeNull();
  });

  it('初始状态：healJobType 为 null', () => {
    expect(useOpsStore.getState().healJobType).toBeNull();
  });

  it('setCurrentSpaceId 更新当前空间 ID', () => {
    useOpsStore.getState().setCurrentSpaceId(1);
    expect(useOpsStore.getState().currentSpaceId).toBe(1);
  });

  it('setCurrentSpaceId 可清空为 null', () => {
    useOpsStore.getState().setCurrentSpaceId(2);
    useOpsStore.getState().setCurrentSpaceId(null);
    expect(useOpsStore.getState().currentSpaceId).toBeNull();
  });

  it('setHealJobType 更新治愈操作类型', () => {
    useOpsStore.getState().setHealJobType('RETRY_INDEX');
    expect(useOpsStore.getState().healJobType).toBe('RETRY_INDEX');
  });

  it('setHealJobType 可清空为 null', () => {
    useOpsStore.getState().setHealJobType('REBUILD_GRAPH');
    useOpsStore.getState().setHealJobType(null);
    expect(useOpsStore.getState().healJobType).toBeNull();
  });

  it('多次切换状态保持稳定', () => {
    useOpsStore.getState().setCurrentSpaceId(1);
    useOpsStore.getState().setHealJobType('RETRY_PARSE');
    expect(useOpsStore.getState().currentSpaceId).toBe(1);
    expect(useOpsStore.getState().healJobType).toBe('RETRY_PARSE');

    useOpsStore.getState().setCurrentSpaceId(3);
    expect(useOpsStore.getState().currentSpaceId).toBe(3);
    expect(useOpsStore.getState().healJobType).toBe('RETRY_PARSE');
  });
});
