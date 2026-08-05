/**
 * E2E 测试环境初始化（轻量，不 mock ECharts）
 * - jest-dom 匹配器
 * - cleanup
 * - 基础浏览器 API mock（jsdom 缺失）
 */
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});

// Mock IntersectionObserver（jsdom 缺失）
class IntersectionObserverMock {
  readonly root: Element | null = null;
  readonly rootMargin: string = '';
  readonly thresholds: ReadonlyArray<number> = [];
  private callback: IntersectionObserverCallback;

  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback;
  }

  observe(target: Element): void {
    this.callback(
      [
        {
          target,
          isIntersecting: true,
          intersectionRatio: 1,
          intersectionRect: target.getBoundingClientRect(),
          boundingClientRect: target.getBoundingClientRect(),
          rootBounds: null,
          time: Date.now(),
        },
      ],
      this,
    );
  }

  unobserve(): void {
    /* noop */
  }

  disconnect(): void {
    /* noop */
  }

  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}

global.IntersectionObserver = IntersectionObserverMock as unknown as typeof IntersectionObserver;

// Mock ResizeObserver
class ResizeObserverMock {
  private callback: ResizeObserverCallback;

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
  }

  observe(target: Element): void {
    this.callback(
      [
        {
          target,
          contentRect: {
            x: 0,
            y: 0,
            top: 0,
            left: 0,
            bottom: 0,
            right: 0,
            width: 800,
            height: 600,
            toJSON: () => ({}),
          },
          borderBoxSize: [],
          contentBoxSize: [],
          devicePixelContentBoxSize: [],
        },
      ],
      this,
    );
  }

  unobserve(): void {
    /* noop */
  }

  disconnect(): void {
    /* noop */
  }
}

global.ResizeObserver = ResizeObserverMock as unknown as typeof ResizeObserver;

// Mock matchMedia（Antd 响应式栅格依赖）
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {
      /* noop */
    },
    removeListener: () => {
      /* noop */
    },
    addEventListener: () => {
      /* noop */
    },
    removeEventListener: () => {
      /* noop */
    },
    dispatchEvent: () => false,
  }),
});

// Mock window.scrollTo
window.scrollTo = window.scrollTo || (() => {
  /* noop */
});

// 静默 React 18 act 警告
const originalError = console.error;
console.error = (...args: unknown[]) => {
  const msg = String(args[0] ?? '');
  if (msg.includes('not wrapped in act')) return;
  originalError(...args);
};
