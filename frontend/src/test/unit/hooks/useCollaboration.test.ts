/**
 * 单元测试：useCollaboration Hook（src/hooks/useCollaboration.ts）
 * 覆盖：
 * - mock @stomp/stompjs 的 Client + sockjs-client，避免真实连接
 * - 连接建立：activate 后 onConnect 触发，isConnected 变为 true
 * - 消息接收：presence 主题消息更新 onlineUsers；tags 主题消息更新 lastTagUpdate
 * - joinFile / leaveFile / notifyTagUpdate 调用 client.publish
 * - 组件卸载时调用 deactivate 并发送 leave
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';

/** ===== Mock 状态容器：用于在测试中访问 Client 实例并触发回调 ===== */
interface MockClientInstance {
  /** Client 配置（含 onConnect / onDisconnect 等回调） */
  config: Record<string, unknown>;
  /** connected 标志 */
  connected: boolean;
  /** activate 调用次数 */
  activateCalls: number;
  /** deactivate 调用次数 */
  deactivateCalls: number;
  /** publish 调用记录 */
  publishCalls: Array<{ destination: string; body: string }>;
  /** subscribe 调用记录（destination → 回调） */
  subscriptions: Map<string, (message: { body: string }) => void>;
  /** 模拟 onConnect 触发 */
  triggerConnect: () => void;
  /** 模拟 onDisconnect 触发 */
  triggerDisconnect: () => void;
  /** 模拟收到消息 */
  triggerMessage: (destination: string, body: unknown) => void;
}

/** 当前测试中的 Mock Client 实例（每个用例重置） */
let mockClient: MockClientInstance | null = null;

/** 构造 Mock Client 实例 */
function createMockClient(): MockClientInstance {
  const instance: MockClientInstance = {
    config: {},
    connected: false,
    activateCalls: 0,
    deactivateCalls: 0,
    publishCalls: [],
    subscriptions: new Map(),
    triggerConnect: () => {
      instance.connected = true;
      const onConnect = instance.config.onConnect as (() => void) | undefined;
      if (onConnect) onConnect();
    },
    triggerDisconnect: () => {
      instance.connected = false;
      const onDisconnect = instance.config.onDisconnect as (() => void) | undefined;
      if (onDisconnect) onDisconnect();
    },
    triggerMessage: (destination: string, body: unknown) => {
      const cb = instance.subscriptions.get(destination);
      if (cb) cb({ body: JSON.stringify(body) });
    },
  };
  return instance;
}

/** Mock @stomp/stompjs 的 Client 类 */
vi.mock('@stomp/stompjs', () => {
  return {
    Client: class MockClient {
      connected = false;
      onConnect?: () => void;
      onDisconnect?: () => void;
      onStompError?: () => void;
      onWebSocketError?: () => void;
      webSocketFactory?: () => unknown;
      debug?: (...args: unknown[]) => void;
      reconnectDelay?: number;
      heartbeatIncoming?: number;
      heartbeatOutgoing?: number;

      constructor(config: Record<string, unknown>) {
        // 将配置同步到 mock 实例
        mockClient = createMockClient();
        mockClient.config = config;
        // 同步回调到 MockClient 属性
        this.onConnect = config.onConnect as (() => void) | undefined;
        this.onDisconnect = config.onDisconnect as (() => void) | undefined;
        this.onStompError = config.onStompError as (() => void) | undefined;
        this.onWebSocketError = config.onWebSocketError as (() => void) | undefined;
        this.webSocketFactory = config.webSocketFactory as (() => unknown) | undefined;
        this.debug = config.debug as ((...args: unknown[]) => void) | undefined;
        // 把 MockClient 实例的回调指向 this 的回调（保持同步）
        mockClient.triggerConnect = () => {
          this.connected = true;
          mockClient!.connected = true;
          this.onConnect?.();
        };
        mockClient.triggerDisconnect = () => {
          this.connected = false;
          mockClient!.connected = false;
          this.onDisconnect?.();
        };
      }

      activate() {
        mockClient!.activateCalls += 1;
      }

      deactivate() {
        mockClient!.deactivateCalls += 1;
        this.connected = false;
        mockClient!.connected = false;
      }

      subscribe(destination: string, callback: (message: { body: string }) => void) {
        mockClient!.subscriptions.set(destination, callback);
        return { unsubscribe: () => {} };
      }

      publish(params: { destination: string; body: string }) {
        mockClient!.publishCalls.push({ destination: params.destination, body: params.body });
      }
    },
  };
});

/** Mock sockjs-client */
vi.mock('sockjs-client', () => {
  return {
    default: class SockJSMock {
      url: string;
      constructor(url: string) {
        this.url = url;
      }
      close() {
        /* noop */
      }
    },
  };
});

import { useCollaboration } from '@/hooks/useCollaboration';

describe('useCollaboration Hook', () => {
  beforeEach(() => {
    mockClient = null;
    vi.clearAllMocks();
  });

  it('传入 fileId 后创建 Client 并调用 activate（连接建立）', async () => {
    const { result } = renderHook(() => useCollaboration('file_test_001'));

    // 等待 useEffect 执行
    await act(async () => {
      await Promise.resolve();
    });

    expect(mockClient).not.toBeNull();
    expect(mockClient!.activateCalls).toBe(1);
    // 初始未连接
    expect(result.current.isConnected).toBe(false);

    // 触发 onConnect
    act(() => {
      mockClient!.triggerConnect();
    });

    expect(result.current.isConnected).toBe(true);
  });

  it('presence 主题消息更新 onlineUsers；tags 主题消息更新 lastTagUpdate', async () => {
    const { result } = renderHook(() => useCollaboration('file_test_002'));

    await act(async () => {
      await Promise.resolve();
    });

    // 触发连接，订阅会被建立
    act(() => {
      mockClient!.triggerConnect();
    });

    // 初始为空数组
    expect(result.current.onlineUsers).toEqual([]);
    expect(result.current.lastTagUpdate).toBeNull();

    // 模拟收到 presence 消息（数组形式）
    act(() => {
      mockClient!.triggerMessage('/topic/file/file_test_002/presence', [
        { userId: 'u1', name: '张三' },
        { userId: 'u2', name: '李四' },
      ]);
    });

    expect(result.current.onlineUsers).toHaveLength(2);
    expect(result.current.onlineUsers[0].name).toBe('张三');

    // 模拟收到 presence 消息（单个用户对象）
    act(() => {
      mockClient!.triggerMessage('/topic/file/file_test_002/presence', {
        userId: 'u3',
        name: '王五',
      });
    });

    expect(result.current.onlineUsers).toHaveLength(1);
    expect(result.current.onlineUsers[0].userId).toBe('u3');

    // 模拟收到 tags 消息
    act(() => {
      mockClient!.triggerMessage('/topic/file/file_test_002/tags', {
        userId: 'u1',
        userName: '张三',
        tags: ['恶意软件', 'APT28'],
        updatedAt: '2026-08-05T10:00:00Z',
      });
    });

    expect(result.current.lastTagUpdate).not.toBeNull();
    expect(result.current.lastTagUpdate!.userName).toBe('张三');
    expect(result.current.lastTagUpdate!.tags).toEqual(['恶意软件', 'APT28']);
  });

  it('joinFile / notifyTagUpdate 调用 client.publish 到正确的 destination', async () => {
    const { result } = renderHook(() => useCollaboration('file_test_003'));

    await act(async () => {
      await Promise.resolve();
    });

    // 连接建立
    act(() => {
      mockClient!.triggerConnect();
    });

    // 连接建立后会自动发送一次 join
    const joinCallAfterConnect = mockClient!.publishCalls.filter(
      (c) => c.destination === '/app/collab/file_test_003/join',
    );
    expect(joinCallAfterConnect.length).toBeGreaterThanOrEqual(1);

    // 调用 joinFile
    act(() => {
      result.current.joinFile();
    });
    expect(
      mockClient!.publishCalls.some(
        (c) => c.destination === '/app/collab/file_test_003/join',
      ),
    ).toBe(true);

    // 调用 notifyTagUpdate
    act(() => {
      result.current.notifyTagUpdate(['标签A', '标签B']);
    });
    expect(
      mockClient!.publishCalls.some(
        (c) => c.destination === '/app/collab/file_test_003/tag-update',
      ),
    ).toBe(true);
    // 验证 body 包含标签内容
    const tagUpdateCall = mockClient!.publishCalls.find(
      (c) => c.destination === '/app/collab/file_test_003/tag-update',
    );
    expect(tagUpdateCall).toBeDefined();
    expect(tagUpdateCall!.body).toContain('标签A');
    expect(tagUpdateCall!.body).toContain('标签B');
  });

  it('组件卸载时发送 leave 并调用 deactivate', async () => {
    const { unmount } = renderHook(() => useCollaboration('file_test_004'));

    await act(async () => {
      await Promise.resolve();
    });

    // 连接建立
    act(() => {
      mockClient!.triggerConnect();
    });

    // 卸载前 leave 调用为 0
    const leaveBeforeUnmount = mockClient!.publishCalls.filter(
      (c) => c.destination === '/app/collab/file_test_004/leave',
    );
    expect(leaveBeforeUnmount.length).toBe(0);

    // 卸载
    unmount();

    // 卸载后应发送 leave
    expect(
      mockClient!.publishCalls.some(
        (c) => c.destination === '/app/collab/file_test_004/leave',
      ),
    ).toBe(true);
    // 应调用 deactivate
    expect(mockClient!.deactivateCalls).toBeGreaterThanOrEqual(1);
  });

  it('未传入 fileId 时不建立连接（边界场景）', async () => {
    const { result } = renderHook(() => useCollaboration(''));

    await act(async () => {
      await Promise.resolve();
    });

    // 无 fileId 时不会创建 Client
    expect(mockClient).toBeNull();
    expect(result.current.isConnected).toBe(false);
    expect(result.current.onlineUsers).toEqual([]);
  });
});
