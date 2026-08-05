/**
 * 单元测试：主题 Store（src/stores/theme.ts）
 * 覆盖：
 * - 默认初始主题为亮色模式
 * - 从 localStorage 读取已保存主题
 * - toggleTheme 切换主题
 * - setTheme 手动设置主题
 * - localStorage 持久化
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('useThemeStore', () => {
  beforeEach(() => {
    localStorage.clear();
    // 重置模块注册表，使每个用例都能拿到重新初始化的 store
    vi.resetModules();
  });

  it('初始主题默认为亮色模式（localStorage 为空时）', async () => {
    const { useThemeStore } = await import('@/stores/theme');
    expect(useThemeStore.getState().mode).toBe('light');
  });

  it('localStorage 存在 dark 时，初始主题读取为暗色', async () => {
    localStorage.setItem('app-theme', 'dark');
    const { useThemeStore } = await import('@/stores/theme');
    expect(useThemeStore.getState().mode).toBe('dark');
  });

  it('localStorage 存在非法值时，回退为亮色', async () => {
    localStorage.setItem('app-theme', 'pink');
    const { useThemeStore } = await import('@/stores/theme');
    expect(useThemeStore.getState().mode).toBe('light');
  });

  it('toggleTheme 从亮色切换到暗色', async () => {
    const { useThemeStore } = await import('@/stores/theme');
    useThemeStore.getState().toggleTheme();
    expect(useThemeStore.getState().mode).toBe('dark');
  });

  it('toggleTheme 来回切换主题稳定', async () => {
    const { useThemeStore } = await import('@/stores/theme');
    useThemeStore.getState().toggleTheme();
    expect(useThemeStore.getState().mode).toBe('dark');
    useThemeStore.getState().toggleTheme();
    expect(useThemeStore.getState().mode).toBe('light');
  });

  it('setTheme 手动设置主题', async () => {
    const { useThemeStore } = await import('@/stores/theme');
    useThemeStore.getState().setTheme('dark');
    expect(useThemeStore.getState().mode).toBe('dark');
    useThemeStore.getState().setTheme('light');
    expect(useThemeStore.getState().mode).toBe('light');
  });

  it('setTheme 持久化到 localStorage', async () => {
    const { useThemeStore } = await import('@/stores/theme');
    useThemeStore.getState().setTheme('dark');
    expect(localStorage.getItem('app-theme')).toBe('dark');
    useThemeStore.getState().setTheme('light');
    expect(localStorage.getItem('app-theme')).toBe('light');
  });

  it('toggleTheme 持久化到 localStorage', async () => {
    const { useThemeStore } = await import('@/stores/theme');
    useThemeStore.getState().toggleTheme();
    expect(localStorage.getItem('app-theme')).toBe('dark');
  });
});
