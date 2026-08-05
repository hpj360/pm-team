/**
 * 可访问性辅助函数
 * - 统一生成 ARIA 标签
 * - 焦点管理（focus trap / restore）
 * - 键盘导航辅助
 *
 * 参考 WCAG 2.1 AA 与 WAI-ARIA Authoring Practices 1.2
 */

/**
 * 根据 key 与上下文生成统一格式的 aria-label
 * @param key 资源 key（如 'file.upload.button'）
 * @param context 上下文参数（可选，用于插值）
 * @returns 处理后的中文 aria-label
 *
 * @example
 * getAriaLabel('file.upload.button'); // '上传文件按钮'
 * getAriaLabel('menu.item', { label: '工作台' }); // '工作台 菜单项'
 */
export function getAriaLabel(
  key: string,
  context?: Record<string, string | number>,
): string {
  /** key → 中文标签映射表 */
  const LABEL_MAP: Record<string, string> = {
    // 通用
    'button.submit': '提交按钮',
    'button.cancel': '取消按钮',
    'button.close': '关闭按钮',
    'button.confirm': '确认按钮',
    'button.refresh': '刷新按钮',
    'button.export': '导出按钮',
    'button.search': '搜索按钮',
    'button.upload': '上传按钮',
    'button.download': '下载按钮',
    'button.delete': '删除按钮',
    'button.edit': '编辑按钮',
    'button.view': '查看按钮',
    'button.expand': '展开',
    'button.collapse': '收起',

    // 菜单与导航
    'menu.item': '菜单项',
    'menu.submenu': '子菜单',
    'menu.toggle': '折叠菜单按钮',
    'skip.to.content': '跳到主内容',

    // 主题与用户
    'button.theme.toggle': '切换主题',
    'button.user.menu': '用户菜单',
    'button.notification': '通知',

    // 表格
    'table.row.actions': '行操作',
    'table.selection': '行选择',

    // 图表
    'chart.container': '图表容器',

    // 文件
    'file.upload.button': '上传文件按钮',
    'file.upload.dragger': '点击或拖拽文件到此区域上传',

    // 抽屉与模态框
    'drawer.close': '关闭抽屉',
    'modal.close': '关闭对话框',

    // 图谱
    'graph.canvas': '关系图谱画布，可缩放与拖拽',
    'graph.node': '图谱节点',
  };

  let label = LABEL_MAP[key] ?? key;
  if (context) {
    for (const [k, v] of Object.entries(context)) {
      label = label.replace(`{${k}}`, String(v));
    }
    // 若上下文中带 label 且 key 未含占位符，则前置 label
    if (context.label !== undefined) {
      const ctxLabel = String(context.label);
      if (ctxLabel && !label.includes(ctxLabel)) {
        label = `${ctxLabel} ${label}`;
      }
    }
  }
  return label;
}

/**
 * 焦点管理工具
 * - 保存当前焦点，便于后续恢复
 * - 在抽屉 / 模态框打开时使用
 */
export const focusManagement = {
  /** 保存当前激活元素 */
  save(): HTMLElement | null {
    const active = document.activeElement;
    return active instanceof HTMLElement ? active : null;
  },

  /**
   * 恢复焦点到指定元素（或之前保存的元素）
   * @param element 目标元素；未传时恢复到 body
   */
  restore(element?: HTMLElement | null): void {
    const target = element ?? document.body;
    // 确保元素可聚焦
    if (target && typeof target.focus === 'function') {
      try {
        target.focus({ preventScroll: false });
      } catch {
        /* 部分浏览器不支持 options */
        target.focus();
      }
    }
  },

  /**
   * 将焦点移动到容器内第一个可聚焦元素
   * @param container 容器元素
   */
  focusFirst(container: HTMLElement): void {
    const focusable = queryFocusable(container);
    if (focusable.length > 0) {
      focusable[0].focus();
    }
  },

  /**
   * 将焦点移动到容器内最后一个可聚焦元素
   * @param container 容器元素
   */
  focusLast(container: HTMLElement): void {
    const focusable = queryFocusable(container);
    if (focusable.length > 0) {
      focusable[focusable.length - 1].focus();
    }
  },
};

/**
 * 查询容器内所有可聚焦元素
 * @param container 容器元素
 * @returns 可聚焦元素数组（按 DOM 顺序）
 */
export function queryFocusable(container: HTMLElement): HTMLElement[] {
  const selector = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
    '[contenteditable="true"]',
  ].join(',');

  return Array.from(
    container.querySelectorAll<HTMLElement>(selector),
  ).filter(
    (el) =>
      el.offsetParent !== null || // 可见
      el.getClientRects().length > 0,
  );
}

/**
 * 创建焦点陷阱（用于模态框 / 抽屉）
 * - Tab 在容器内循环
 * - 卸载时调用返回的 dispose 函数解绑
 *
 * @param container 容器元素
 * @returns dispose 函数（解绑监听）
 */
export function createFocusTrap(container: HTMLElement): () => void {
  const handleKeydown = (e: KeyboardEvent) => {
    if (e.key !== 'Tab') return;

    const focusable = queryFocusable(container);
    if (focusable.length === 0) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;

    if (e.shiftKey) {
      // Shift + Tab：从第一个跳到最后一个
      if (active === first || !container.contains(active)) {
        e.preventDefault();
        last.focus();
      }
    } else {
      // Tab：从最后一个跳到第一个
      if (active === last) {
        e.preventDefault();
        first.focus();
      }
    }
  };

  container.addEventListener('keydown', handleKeydown);
  return () => container.removeEventListener('keydown', handleKeydown);
}

/**
 * 屏幕阅读器实时通告
 * - 通过 aria-live 区域通告动态变化
 * - 自动创建并复用隐藏的 live region
 *
 * @param message 通告内容
 * @param level 通告级别（polite / assertive）
 */
export function announce(
  message: string,
  level: 'polite' | 'assertive' = 'polite',
): void {
  const LIVE_ID = 'app-aria-live-region';
  let region = document.getElementById(LIVE_ID);
  if (!region) {
    region = document.createElement('div');
    region.id = LIVE_ID;
    region.setAttribute('aria-live', level);
    region.setAttribute('aria-atomic', 'true');
    region.style.position = 'absolute';
    region.style.width = '1px';
    region.style.height = '1px';
    region.style.padding = '0';
    region.style.margin = '-1px';
    region.style.overflow = 'hidden';
    region.style.clip = 'rect(0,0,0,0)';
    region.style.whiteSpace = 'nowrap';
    region.style.border = '0';
    document.body.appendChild(region);
  } else {
    region.setAttribute('aria-live', level);
  }
  // 强制重新通告（清空再写入）
  region.textContent = '';
  window.setTimeout(() => {
    if (region) region.textContent = message;
  }, 50);
}

export default {
  getAriaLabel,
  focusManagement,
  queryFocusable,
  createFocusTrap,
  announce,
};
