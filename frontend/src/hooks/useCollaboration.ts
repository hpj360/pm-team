/**
 * 协同编辑 Hook（V4.7-P1-3 前端 WebSocket 客户端）
 *
 * 后端：search-service CollaborationController（端口 8083）WebSocket/STOMP
 * - WS 连接：ws://localhost:8083/ws（SockJS 兼容 http://localhost:8083/ws）
 * - 订阅：/topic/file/{fileId}/presence（在线用户变更）
 * - 订阅：/topic/file/{fileId}/tags（标签变更通知）
 * - 发送：/app/collab/{fileId}/join（加入文件）
 * - 发送：/app/collab/{fileId}/leave（离开文件）
 * - 发送：/app/collab/{fileId}/tag-update（标签变更通知）
 *
 * Hook 签名：useCollaboration(fileId)
 * 返回：{ onlineUsers, lastTagUpdate, isConnected, joinFile, leaveFile, notifyTagUpdate }
 *
 * 失败降级：WebSocket 连接失败时静默降级，不阻塞页面（isConnected 保持 false）。
 * 测试要求：测试中必须 mock @stomp/stompjs，避免真实连接。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/** 在线用户信息 */
export interface OnlineUser {
  /** 用户 ID */
  userId: string;
  /** 用户名 */
  name: string;
  /** 头像 URL（可选） */
  avatar?: string;
}

/** 标签更新事件载荷 */
export interface TagUpdatePayload {
  /** 操作人用户 ID */
  userId: string;
  /** 操作人用户名 */
  userName: string;
  /** 变更后的标签列表 */
  tags: string[];
  /** 变更时间 ISO 字符串 */
  updatedAt: string;
}

/** useCollaboration 返回值 */
export interface UseCollaborationResult {
  /** 当前在线用户列表 */
  onlineUsers: OnlineUser[];
  /** 最近一次标签更新事件（用于触发外部刷新） */
  lastTagUpdate: TagUpdatePayload | null;
  /** WebSocket 是否已连接 */
  isConnected: boolean;
  /** 加入文件协作（发送 join 通知） */
  joinFile: () => void;
  /** 离开文件协作（发送 leave 通知） */
  leaveFile: () => void;
  /** 通知其他端标签已变更 */
  notifyTagUpdate: (tags: string[]) => void;
}

/**
 * 协同编辑 Hook
 * @param fileId 当前文件 ID
 */
export function useCollaboration(fileId: string): UseCollaborationResult {
  const [onlineUsers, setOnlineUsers] = useState<OnlineUser[]>([]);
  const [lastTagUpdate, setLastTagUpdate] = useState<TagUpdatePayload | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  // STOMP Client 引用（使用 ref 避免重渲染）
  const clientRef = useRef<Client | null>(null);
  // 当前 fileId 引用（便于在 cleanup 时发送 leave）
  const fileIdRef = useRef<string>(fileId);
  fileIdRef.current = fileId;

  /**
   * 建立 STOMP 连接
   * - 使用 SockJS 作为 WebSocket 工厂
   * - 订阅 presence / tags 两个主题
   * - 连接成功后自动发送 join
   * - 失败时静默降级，不影响页面其他功能
   */
  useEffect(() => {
    if (!fileId) {
      // 无 fileId 时不建立连接
      return;
    }

    // SockJS 需要 http(s):// 协议；将 ws:// / wss:// 适配为 http(s)://
    const wsUrl = 'http://localhost:8083/ws';

    let disposed = false;

    const client = new Client({
      // SockJS 工厂：返回 SockJS 实例（兼容浏览器与 Node 测试环境）
      webSocketFactory: () => new SockJS(wsUrl) as unknown as WebSocket,
      // 调试日志仅在开发环境开启（生产环境静默）
      debug: () => {
        /* 静默，避免污染控制台 */
      },
      // 重连间隔
      reconnectDelay: 5000,
      // 心跳间隔
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    clientRef.current = client;

    // 连接成功回调：订阅主题 + 发送 join
    client.onConnect = () => {
      if (disposed) return;
      setIsConnected(true);

      // 订阅在线用户变更
      client.subscribe(`/topic/file/${fileId}/presence`, (message) => {
        try {
          const payload = JSON.parse(message.body) as OnlineUser[] | OnlineUser;
          // 后端可能返回数组或单个用户，统一规整为数组
          const users = Array.isArray(payload) ? payload : [payload];
          setOnlineUsers(users);
        } catch {
          // 解析失败静默忽略，避免阻塞页面
        }
      });

      // 订阅标签变更
      client.subscribe(`/topic/file/${fileId}/tags`, (message) => {
        try {
          const payload = JSON.parse(message.body) as TagUpdatePayload;
          setLastTagUpdate(payload);
        } catch {
          // 解析失败静默忽略
        }
      });

      // 连接建立后自动加入文件
      try {
        client.publish({
          destination: `/app/collab/${fileId}/join`,
          body: JSON.stringify({ fileId, joinedAt: new Date().toISOString() }),
        });
      } catch {
        // publish 失败静默忽略
      }
    };

    // 连接断开回调
    client.onDisconnect = () => {
      setIsConnected(false);
    };

    // STOMP 错误回调：静默降级
    client.onStompError = () => {
      setIsConnected(false);
    };

    // WebSocket 错误回调：静默降级
    client.onWebSocketError = () => {
      setIsConnected(false);
    };

    // 启动连接（异步，失败会被 onWebSocketError 捕获）
    try {
      client.activate();
    } catch {
      // 激活异常时静默降级
      setIsConnected(false);
    }

    // 卸载时清理：发送 leave + 断开连接
    return () => {
      disposed = true;
      const currentFileId = fileIdRef.current;
      const c = clientRef.current;
      if (c && c.connected) {
        // 尝试发送 leave 通知（失败不阻塞）
        try {
          c.publish({
            destination: `/app/collab/${currentFileId}/leave`,
            body: JSON.stringify({ fileId: currentFileId, leftAt: new Date().toISOString() }),
          });
        } catch {
          /* 静默 */
        }
        try {
          c.deactivate();
        } catch {
          /* 静默 */
        }
      }
      clientRef.current = null;
      setIsConnected(false);
    };
  }, [fileId]);

  /** 加入文件协作 */
  const joinFile = useCallback(() => {
    const c = clientRef.current;
    if (!c || !c.connected) return;
    try {
      c.publish({
        destination: `/app/collab/${fileId}/join`,
        body: JSON.stringify({ fileId, joinedAt: new Date().toISOString() }),
      });
    } catch {
      /* 静默 */
    }
  }, [fileId]);

  /** 离开文件协作 */
  const leaveFile = useCallback(() => {
    const c = clientRef.current;
    if (!c || !c.connected) return;
    try {
      c.publish({
        destination: `/app/collab/${fileId}/leave`,
        body: JSON.stringify({ fileId, leftAt: new Date().toISOString() }),
      });
    } catch {
      /* 静默 */
    }
  }, [fileId]);

  /** 通知其他端标签已变更 */
  const notifyTagUpdate = useCallback(
    (tags: string[]) => {
      const c = clientRef.current;
      if (!c || !c.connected) return;
      try {
        c.publish({
          destination: `/app/collab/${fileId}/tag-update`,
          body: JSON.stringify({
            fileId,
            tags,
            updatedAt: new Date().toISOString(),
          } as unknown as TagUpdatePayload),
        });
      } catch {
        /* 静默 */
      }
    },
    [fileId],
  );

  return {
    onlineUsers,
    lastTagUpdate,
    isConnected,
    joinFile,
    leaveFile,
    notifyTagUpdate,
  };
}

export default useCollaboration;
