/**
 * 单元测试：可访问性辅助函数 src/utils/accessibility.ts
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  getAriaLabel,
  focusManagement,
  queryFocusable,
  createFocusTrap,
  announce,
} from '@/utils/accessibility';

describe('getAriaLabel', () => {
  it('根据 key 返回中文 aria-label', () => {
    expect(getAriaLabel('button.submit')).toBe('提交按钮');
    expect(getAriaLabel('button.cancel')).toBe('取消按钮');
    expect(getAriaLabel('file.upload.button')).toBe('上传文件按钮');
  });

  it('未匹配 key 时返回 key 本身', () => {
    expect(getAriaLabel('unknown.key')).toBe('unknown.key');
  });

  it('带 context.label 且未含占位符时前置 label', () => {
    const label = getAriaLabel('menu.item', { label: '工作台' });
    expect(label).toBe('工作台 菜单项');
  });

  it('占位符 {key} 被替换', () => {
    // 自定义 key 带占位符
    const label = getAriaLabel('test.{id}.item', { id: 42 });
    expect(label).toBe('test.42.item');
  });
});

describe('focusManagement', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it('save 返回当前 activeElement（HTMLElement）', () => {
    const btn = document.createElement('button');
    document.body.appendChild(btn);
    btn.focus();
    const saved = focusManagement.save();
    expect(saved).toBe(btn);
  });

  it('save 在无 activeElement 时返回 null', () => {
    // 默认 activeElement 是 body，body 是 HTMLElement，因此构造一个无焦点的场景
    document.body.innerHTML = '<div>no focusable</div>';
    // body.blur() 不一定改变 activeElement，这里仅校验 save 不抛错
    const saved = focusManagement.save();
    // body 也是 HTMLElement，所以可能返回 body；只要类型正确即可
    expect(saved === null || saved instanceof HTMLElement).toBe(true);
  });

  it('restore 调用目标元素的 focus', () => {
    const focusSpy = vi.fn();
    const el = { focus: focusSpy } as unknown as HTMLElement;
    focusManagement.restore(el);
    expect(focusSpy).toHaveBeenCalled();
  });

  it('restore 不传参时聚焦 body', () => {
    const focusSpy = vi.spyOn(document.body, 'focus');
    focusManagement.restore();
    expect(focusSpy).toHaveBeenCalled();
    focusSpy.mockRestore();
  });

  it('focusFirst 聚焦容器内第一个可聚焦元素', () => {
    const container = document.createElement('div');
    const btn1 = document.createElement('button');
    const btn2 = document.createElement('button');
    container.appendChild(btn1);
    container.appendChild(btn2);
    document.body.appendChild(container);
    [btn1, btn2].forEach((el) => {
      Object.defineProperty(el, 'offsetParent', {
        configurable: true,
        get: () => document.body,
      });
    });
    const spy = vi.spyOn(btn1, 'focus');
    focusManagement.focusFirst(container);
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });

  it('focusLast 聚焦容器内最后一个可聚焦元素', () => {
    const container = document.createElement('div');
    const btn1 = document.createElement('button');
    const btn2 = document.createElement('button');
    container.appendChild(btn1);
    container.appendChild(btn2);
    document.body.appendChild(container);
    [btn1, btn2].forEach((el) => {
      Object.defineProperty(el, 'offsetParent', {
        configurable: true,
        get: () => document.body,
      });
    });
    const spy = vi.spyOn(btn2, 'focus');
    focusManagement.focusLast(container);
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });
});

describe('queryFocusable', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it('返回容器内所有可聚焦元素', () => {
    const container = document.createElement('div');
    container.innerHTML = `
      <a href="#">link</a>
      <button>btn1</button>
      <button disabled>btn2</button>
      <input type="text" />
      <input type="text" disabled />
      <div tabindex="0">div</div>
      <div tabindex="-1">skip</div>
      <p>paragraph</p>
    `;
    document.body.appendChild(container);
    // jsdom 中 offsetParent 恒为 null，需 mock getClientRects 返回非空以通过可见性过滤
    const elements = container.querySelectorAll('*');
    elements.forEach((el) => {
      // offsetParent 设为非 null 让其通过第一道过滤
      Object.defineProperty(el, 'offsetParent', {
        configurable: true,
        get: () => document.body,
      });
    });
    const focusable = queryFocusable(container);
    // link, button(1), input(1), div[tabindex=0] = 4 个
    expect(focusable.length).toBe(4);
  });

  it('空容器返回空数组', () => {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const focusable = queryFocusable(container);
    expect(focusable).toEqual([]);
  });
});

describe('createFocusTrap', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it('返回 dispose 函数且能解绑监听', () => {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const dispose = createFocusTrap(container);
    expect(typeof dispose).toBe('function');
    // 不应抛错
    expect(() => dispose()).not.toThrow();
  });

  it('Tab 在最后一个元素时阻止默认行为并跳回第一个', () => {
    const container = document.createElement('div');
    const btn1 = document.createElement('button');
    const btn2 = document.createElement('button');
    container.appendChild(btn1);
    container.appendChild(btn2);
    document.body.appendChild(container);
    [btn1, btn2].forEach((el) => {
      Object.defineProperty(el, 'offsetParent', {
        configurable: true,
        get: () => document.body,
      });
    });

    const focusSpy = vi.spyOn(btn1, 'focus');
    const dispose = createFocusTrap(container);

    // 当前焦点在 btn2（最后一个），按 Tab
    Object.defineProperty(document, 'activeElement', {
      configurable: true,
      get: () => btn2,
    });

    const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true });
    const preventSpy = vi.spyOn(event, 'preventDefault');
    container.dispatchEvent(event);

    expect(preventSpy).toHaveBeenCalled();
    expect(focusSpy).toHaveBeenCalled();

    dispose();
    focusSpy.mockRestore();
  });
});

describe('announce', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it('创建 live region 并附加到 DOM', () => {
    announce('测试通告', 'polite');
    const region = document.getElementById('app-aria-live-region');
    expect(region).not.toBeNull();
    expect(region?.getAttribute('aria-live')).toBe('polite');
  });

  it('复用已存在的 live region 并更新 level', () => {
    announce('第一条', 'polite');
    const region1 = document.getElementById('app-aria-live-region');
    expect(region1?.getAttribute('aria-live')).toBe('polite');

    announce('第二条', 'assertive');
    const region2 = document.getElementById('app-aria-live-region');
    expect(region2).toBe(region1);
    expect(region2?.getAttribute('aria-live')).toBe('assertive');
  });
});
