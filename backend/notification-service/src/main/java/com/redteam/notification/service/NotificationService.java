package com.redteam.notification.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.redteam.common.result.PageResult;
import com.redteam.notification.dto.BroadcastNotificationDTO;
import com.redteam.notification.dto.NotificationDTO;
import com.redteam.notification.dto.NotificationQueryDTO;
import com.redteam.notification.dto.NotificationStatsDTO;
import com.redteam.notification.dto.NotificationVO;
import com.redteam.notification.entity.NotificationEntity;

import java.util.List;

/**
 * 通知服务接口
 *
 * <p>提供通知的发送、广播、查询、已读标记、删除、未读统计、过期清理及重试等能力。</p>
 *
 * @author 红方团队
 */
public interface NotificationService extends IService<NotificationEntity> {

    /**
     * 发送通知
     *
     * <p>根据通知通道（IN_APP / EMAIL / IM / ALL）分发到对应通道。</p>
     *
     * @param dto 通知请求
     * @return 通知返回对象
     */
    NotificationVO sendNotification(NotificationDTO dto);

    /**
     * 广播通知：向多个用户批量发送相同通知
     *
     * @param dto 广播请求
     * @return 已发送通知列表
     */
    List<NotificationVO> broadcastNotification(BroadcastNotificationDTO dto);

    /**
     * 根据通知业务ID查询通知详情
     *
     * @param notificationId 通知业务ID
     * @return 通知返回对象
     */
    NotificationVO getNotification(String notificationId);

    /**
     * 分页查询用户通知列表
     *
     * @param userId 用户ID
     * @param query  查询条件
     * @return 分页结果
     */
    PageResult<NotificationVO> listUserNotifications(Long userId, NotificationQueryDTO query);

    /**
     * 标记单条通知为已读
     *
     * @param notificationId 通知业务ID
     */
    void markAsRead(String notificationId);

    /**
     * 标记指定用户的全部通知为已读
     *
     * @param userId 用户ID
     */
    void markAllAsRead(Long userId);

    /**
     * 删除通知（逻辑删除）
     *
     * @param notificationId 通知业务ID
     */
    void deleteNotification(String notificationId);

    /**
     * 获取用户未读通知数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    Integer getUnreadCount(Long userId);

    /**
     * 通知统计（按类型/级别/通道/发送状态分组）
     *
     * @param userId 用户ID，null 表示全局统计
     * @return 统计结果
     */
    NotificationStatsDTO getNotificationStats(Long userId);

    /**
     * 清理过期通知（物理删除已过期且未读的通知）
     *
     * @return 清理数量
     */
    Integer cleanupExpired();

    /**
     * 重试发送失败的通知
     *
     * @param notificationId 通知业务ID
     * @return 重试后的通知
     */
    NotificationVO retryFailed(String notificationId);
}
