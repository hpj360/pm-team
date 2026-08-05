/**
 * Vitest 测试环境初始化
 */
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  cleanup();
});

// Mock IntersectionObserver（ECharts、Infinite Scroll 等依赖）
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

// Mock getComputedStyle（部分场景需要）
const originalGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = (elt) => originalGetComputedStyle(elt);

// 全局 mock localStorage（已有实现，仅做兜底）
if (!global.localStorage) {
  const store: Record<string, string> = {};
  global.localStorage = {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = String(value);
    },
    removeItem: (key: string) => {
      delete store[key];
    },
    clear: () => {
      Object.keys(store).forEach((key) => delete store[key]);
    },
    key: (index: number) => Object.keys(store)[index] ?? null,
    get length() {
      return Object.keys(store).length;
    },
  } as Storage;
}

// 静默控制台错误（测试中预期会打印的告警）
const originalError = console.error;
console.error = (...args: unknown[]) => {
  const msg = String(args[0] ?? '');
  // 过滤掉 React 18 act 警告等无关紧要的告警
  if (msg.includes('not wrapped in act')) return;
  originalError(...args);
};

// Mock ECharts（避免在 jsdom 中初始化 Canvas）
vi.mock('echarts', () => ({
  init: () => ({
    setOption: () => {},
    resize: () => {},
    dispose: () => {},
    on: () => {},
    off: () => {},
    getInstanceByDom: () => null,
  }),
  getInstanceByDom: () => null,
  use: () => {},
  registerTheme: () => {},
  default: {
    init: () => ({
      setOption: () => {},
      resize: () => {},
      dispose: () => {},
      on: () => {},
      off: () => {},
    }),
    use: () => {},
  },
}));

vi.mock('echarts/core', () => ({
  init: () => ({
    setOption: () => {},
    resize: () => {},
    dispose: () => {},
    on: () => {},
    off: () => {},
  }),
  use: () => {},
  registerTheme: () => {},
  default: {
    init: () => ({
      setOption: () => {},
      resize: () => {},
      dispose: () => {},
      on: () => {},
      off: () => {},
    }),
    use: () => {},
  },
}));

vi.mock('echarts-for-react', () => ({
  default: () => null,
}));
