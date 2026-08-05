/**
 * 单元测试：通用工具函数 src/utils/index.ts
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  formatFileSize,
  formatDateTime,
  formatDate,
  sleep,
  generateId,
  debounce,
  throttle,
  deepClone,
  downloadFile,
  copyToClipboard,
  getFileExtension,
  isImageFile,
} from '@/utils';

describe('formatFileSize', () => {
  it('0 字节返回 "0 B"', () => {
    expect(formatFileSize(0)).toBe('0 B');
  });

  it('小于 1KB 返回 B 单位', () => {
    expect(formatFileSize(512)).toBe('512 B');
  });

  it('KB/MB/GB 单位正确换算', () => {
    expect(formatFileSize(1024)).toBe('1 KB');
    expect(formatFileSize(1024 * 1024)).toBe('1 MB');
    expect(formatFileSize(1024 * 1024 * 1024)).toBe('1 GB');
  });

  it('保留两位小数', () => {
    expect(formatFileSize(1536)).toBe('1.5 KB');
    expect(formatFileSize(1024 * 1.5)).toBe('1.5 KB');
  });
});

describe('formatDateTime', () => {
  it('使用字符串日期并按默认格式输出', () => {
    const result = formatDateTime('2026-03-15 09:30:45');
    expect(result).toMatch(/2026/);
    expect(result).toMatch(/03/);
    expect(result).toMatch(/15/);
  });

  it('使用 Date 对象作为入参', () => {
    const d = new Date(2026, 0, 5, 8, 7, 9);
    const result = formatDateTime(d);
    expect(result).toBe('2026-01-05 08:07:09');
  });

  it('支持自定义格式（仅日期）', () => {
    const d = new Date(2026, 5, 9);
    expect(formatDateTime(d, 'YYYY/MM/DD')).toBe('2026/06/09');
  });

  it('两位数补零', () => {
    const d = new Date(2026, 0, 1, 0, 0, 0);
    expect(formatDateTime(d)).toBe('2026-01-01 00:00:00');
  });
});

describe('formatDate', () => {
  it('仅返回日期部分', () => {
    const d = new Date(2026, 6, 4, 10, 30, 59);
    expect(formatDate(d)).toBe('2026-07-04');
  });

  it('接受字符串入参', () => {
    const result = formatDate('2026-12-25T10:00:00');
    expect(result).toBe('2026-12-25');
  });
});

describe('sleep', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('等待指定毫秒后 resolve', async () => {
    const spy = vi.fn();
    sleep(100).then(spy);
    expect(spy).not.toHaveBeenCalled();
    vi.advanceTimersByTime(100);
    await Promise.resolve();
    expect(spy).toHaveBeenCalled();
  });

  it('0 毫秒也生成 Promise', async () => {
    const spy = vi.fn();
    sleep(0).then(spy);
    vi.advanceTimersByTime(0);
    await Promise.resolve();
    await Promise.resolve();
    expect(spy).toHaveBeenCalled();
  });
});

describe('generateId', () => {
  it('返回非空字符串', () => {
    const id = generateId();
    expect(typeof id).toBe('string');
    expect(id.length).toBeGreaterThan(0);
  });

  it('多次调用返回不同值', () => {
    const ids = new Set(Array.from({ length: 20 }, () => generateId()));
    expect(ids.size).toBe(20);
  });
});

describe('debounce', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('延迟时间内不执行函数', () => {
    const fn = vi.fn();
    const debounced = debounce(fn, 200);
    debounced();
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(199);
    expect(fn).not.toHaveBeenCalled();
  });

  it('延迟到达后执行一次', () => {
    const fn = vi.fn();
    const debounced = debounce(fn, 200);
    debounced();
    vi.advanceTimersByTime(200);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('多次调用只执行最后一次', () => {
    const fn = vi.fn();
    const debounced = debounce(fn, 200);
    debounced('a');
    debounced('b');
    debounced('c');
    vi.advanceTimersByTime(200);
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith('c');
  });
});

describe('throttle', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('首次调用立即执行', () => {
    const fn = vi.fn();
    const throttled = throttle(fn, 200);
    throttled();
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('间隔内重复调用只执行一次', () => {
    const fn = vi.fn();
    const throttled = throttle(fn, 200);
    throttled();
    throttled();
    throttled();
    expect(fn).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(200);
    throttled();
    expect(fn).toHaveBeenCalledTimes(2);
  });
});

describe('deepClone', () => {
  it('原始类型直接返回', () => {
    expect(deepClone(42)).toBe(42);
    expect(deepClone('hello')).toBe('hello');
    expect(deepClone(null)).toBeNull();
  });

  it('数组深拷贝：修改副本不影响原数组', () => {
    const original = [{ a: 1 }, { a: 2 }];
    const copy = deepClone(original);
    expect(copy).toEqual(original);
    copy[0].a = 99;
    expect(original[0].a).toBe(1);
  });

  it('对象深拷贝：嵌套对象也独立', () => {
    const original = { a: { b: { c: 1 } } };
    const copy = deepClone(original);
    copy.a.b.c = 99;
    expect(original.a.b.c).toBe(1);
  });
});

describe('downloadFile', () => {
  it('创建 a 标签并触发点击', () => {
    const clickSpy = vi.fn();
    const appendSpy = vi.spyOn(document.body, 'appendChild').mockImplementation((node) => node);
    const removeSpy = vi.spyOn(document.body, 'removeChild').mockImplementation((node) => node);
    const createElementSpy = vi.spyOn(document, 'createElement');

    // 模拟 a 元素
    const fakeLink = {
      href: '',
      download: '',
      click: clickSpy,
    } as unknown as HTMLAnchorElement;
    createElementSpy.mockReturnValueOnce(fakeLink);

    downloadFile('http://example.com/file.pdf', 'file.pdf');

    expect(fakeLink.href).toBe('http://example.com/file.pdf');
    expect(fakeLink.download).toBe('file.pdf');
    expect(clickSpy).toHaveBeenCalled();
    expect(appendSpy).toHaveBeenCalled();
    expect(removeSpy).toHaveBeenCalled();

    appendSpy.mockRestore();
    removeSpy.mockRestore();
    createElementSpy.mockRestore();
  });
});

describe('copyToClipboard', () => {
  it('clipboard API 可用时返回 true', async () => {
    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: writeTextSpy },
    });
    const result = await copyToClipboard('hello');
    expect(writeTextSpy).toHaveBeenCalledWith('hello');
    expect(result).toBe(true);
  });

  it('clipboard API 失败时降级到 execCommand', async () => {
    // 让 clipboard.writeText 抛错
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: () => Promise.reject(new Error('denied')) },
    });
    // jsdom 中可能没有 execCommand，先注入
    document.execCommand = vi.fn().mockReturnValue(true);
    const result = await copyToClipboard('fallback-text');
    expect(document.execCommand).toHaveBeenCalledWith('copy');
    expect(result).toBe(true);
  });
});

describe('getFileExtension', () => {
  it('正常文件名返回小写扩展名', () => {
    expect(getFileExtension('file.PDF')).toBe('pdf');
    expect(getFileExtension('image.JPG')).toBe('jpg');
  });

  it('无扩展名返回空字符串', () => {
    expect(getFileExtension('README')).toBe('');
  });

  it('多个点取最后一个', () => {
    expect(getFileExtension('archive.tar.gz')).toBe('gz');
  });
});

describe('isImageFile', () => {
  it('通过扩展名识别图片', () => {
    expect(isImageFile('photo.png')).toBe(true);
    expect(isImageFile('photo.jpg')).toBe(true);
    expect(isImageFile('photo.gif')).toBe(true);
  });

  it('非图片扩展名返回 false', () => {
    expect(isImageFile('doc.pdf')).toBe(false);
    expect(isImageFile('code.js')).toBe(false);
  });

  it('通过 MIME 类型识别图片', () => {
    expect(isImageFile('image/png')).toBe(true);
    expect(isImageFile('image/jpeg')).toBe(true);
    expect(isImageFile('application/pdf')).toBe(false);
  });
});
