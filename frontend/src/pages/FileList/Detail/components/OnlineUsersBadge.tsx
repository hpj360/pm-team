/**
 * 在线用户 Badge 组件
 * 用于文件详情页右上角，展示当前文件的在线协作用户。
 *
 * - antd Badge + Avatar.Group 显示在线用户头像
 * - 最多展示 3 个头像，超出显示 "+N"
 * - Tooltip 显示用户名
 * - WebSocket 断开时显示灰色圆点
 */
import React from 'react';
import { Avatar, Badge, Tooltip, Typography } from 'antd';
import { TeamOutlined, UserOutlined } from '@ant-design/icons';
import type { OnlineUser } from '@/hooks/useCollaboration';

const { Text } = Typography;

/** OnlineUsersBadge Props */
export interface OnlineUsersBadgeProps {
  /** 在线用户列表 */
  onlineUsers: OnlineUser[];
  /** 是否已连接 WebSocket */
  isConnected: boolean;
  /** 最大显示头像数（超出显示 +N） */
  max?: number;
}

/**
 * 在线用户 Badge 组件
 */
const OnlineUsersBadge: React.FC<OnlineUsersBadgeProps> = ({
  onlineUsers,
  isConnected,
  max = 3,
}) => {
  const displayUsers = onlineUsers.slice(0, max);
  const overflow = Math.max(0, onlineUsers.length - max);

  return (
    <Badge
      dot
      status={isConnected ? 'success' : 'default'}
      offset={[-4, 4]}
      data-testid="online-users-badge"
    >
      <Avatar.Group
        maxCount={max}
        maxStyle={{
          color: '#fff',
          backgroundColor: '#8c8c8c',
        }}
      >
        {displayUsers.map((user) => (
          <Tooltip
            key={user.userId}
            title={user.name}
            placement="bottom"
          >
            <Avatar
              src={user.avatar}
              icon={!user.avatar ? <UserOutlined /> : undefined}
              size="small"
              style={{ backgroundColor: user.avatar ? undefined : '#1677ff' }}
              data-testid={`online-avatar-${user.userId}`}
            >
              {user.name?.slice(0, 1) ?? ''}
            </Avatar>
          </Tooltip>
        ))}
        {overflow > 0 && (
          <Avatar
            size="small"
            style={{ backgroundColor: '#8c8c8c' }}
            data-testid="online-avatar-overflow"
          >
            +{overflow}
          </Avatar>
        )}
        {onlineUsers.length === 0 && (
          <Avatar
            size="small"
            icon={<TeamOutlined />}
            style={{ backgroundColor: '#bfbfbf' }}
            data-testid="online-avatar-empty"
          />
        )}
      </Avatar.Group>
      <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
        {onlineUsers.length > 0 ? `${onlineUsers.length} 人在线` : isConnected ? '当前无其他用户' : '离线'}
      </Text>
    </Badge>
  );
};

export default OnlineUsersBadge;
