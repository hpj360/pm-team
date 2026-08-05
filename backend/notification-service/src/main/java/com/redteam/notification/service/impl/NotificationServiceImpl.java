package com.redteam.notification.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.notification.dto.BroadcastNotificationDTO;
import com.redteam.notification.dto.NotificationDTO;
import com.redteam.notification.dto.NotificationQueryDTO;
import com.redteam.notification.dto.NotificationStatsDTO;
import com.redteam.notification.dto.NotificationVO;
import com.redteam.notification.entity.NotificationEntity;
import com.redteam.notification.mapper.NotificationMapper;
import com.redteam.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通知服务实现
 *
 * <p>支持站内信、邮件、IM 三通道分发。站内信持久化到 PostgreSQL；邮件通过 SMTP 发送；
 * IM 通过 Kafka 投递到下游 IM 网关（飞书/钉钉等）。
 * 增强能力包含：广播通知、通知统计、过期清理、失败重试。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, NotificationEntity>
        implements NotificationService {

    /**
     * 通知 Mapper
     */
    private final NotificationMapper notificationMapper;

    /**
     * 邮件发送器
     */
    private final JavaMailSender mailSender;

    /**
     * Kafka 模板（用于 IM 通道投递）
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * IM 投递主题
     */
    @Value("${spring.kafka.topics.im-dispatch:redteam.notification.im}")
    private String imDispatchTopic;

    /**
     * 邮件发送方
     */
    @Value("${notification.mail.from:noreply@redteam.example.com}")
    private String mailFrom;

    /**
     * 邮件通道开关
     */
    @Value("${notification.mail.enabled:true}")
    private boolean mailEnabled;

    /**
     * IM 通道开关
     */
    @Value("${notification.im.enabled:true}")
    private boolean imEnabled;

    /**
     * 通道常量
     */
    private static final String CHANNEL_IN_APP = "IN_APP";
    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_IM = "IM";
    private static final String CHANNEL_ALL = "ALL";

    /**
     * 发送状态常量
     */
    private static final String SEND_STATUS_PENDING = "PENDING";
    private static final String SEND_STATUS_SENT = "SENT";
    private static final String SEND_STATUS_FAILED = "FAILED";

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO sendNotification(NotificationDTO dto) {
        log.info("发送通知: userId={}, type={}, level={}, channel={}",
                dto.getUserId(), dto.getType(), dto.getLevel(), dto.getChannel());

        NotificationEntity entity = buildEntity(dto);
        String channel = entity.getChannel();

        boolean emailOk = true;
        boolean imOk = true;

        switch (channel) {
            case CHANNEL_IN_APP:
                sendInApp(entity);
                entity.setSendStatus(SEND_STATUS_SENT);
                break;
            case CHANNEL_EMAIL:
                sendInApp(entity);
                emailOk = sendEmail(entity);
                entity.setSendStatus(emailOk ? SEND_STATUS_SENT : SEND_STATUS_FAILED);
                break;
            case CHANNEL_IM:
                sendInApp(entity);
                imOk = sendIM(entity);
                entity.setSendStatus(imOk ? SEND_STATUS_SENT : SEND_STATUS_FAILED);
                break;
            case CHANNEL_ALL:
                sendInApp(entity);
                emailOk = sendEmail(entity);
                imOk = sendIM(entity);
                entity.setSendStatus((emailOk && imOk) ? SEND_STATUS_SENT : SEND_STATUS_FAILED);
                break;
            default:
                log.warn("未知通知通道 {}，默认走站内信", channel);
                sendInApp(entity);
                entity.setSendStatus(SEND_STATUS_SENT);
        }

        // 更新发送状态
        if (entity.getId() != null) {
            notificationMapper.updateById(entity);
        }

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<NotificationVO> broadcastNotification(BroadcastNotificationDTO dto) {
        log.info("广播通知: userCount={}, type={}, channel={}",
                dto.getUserIds().size(), dto.getType(), dto.getChannel());

        List<NotificationVO> results = new ArrayList<>();
        for (Long userId : dto.getUserIds()) {
            try {
                NotificationDTO single = new NotificationDTO();
                single.setUserId(userId);
                single.setSenderId(dto.getSenderId());
                single.setTitle(dto.getTitle());
                single.setContent(dto.getContent());
                single.setType(dto.getType());
                single.setLevel(dto.getLevel());
                single.setChannel(dto.getChannel());
                single.setRelatedId(dto.getRelatedId());
                single.setRelatedType(dto.getRelatedType());
                single.setExpiredTime(dto.getExpiredTime());
                single.setMetadata(dto.getMetadata());
                results.add(sendNotification(single));
            } catch (Exception e) {
                log.error("广播通知发送失败: userId={}", userId, e);
            }
        }
        log.info("广播通知完成: 总数={}, 成功={}", dto.getUserIds().size(), results.size());
        return results;
    }

    @Override
    public NotificationVO getNotification(String notificationId) {
        if (StrUtil.isBlank(notificationId)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "通知ID不能为空");
        }
        NotificationEntity entity = findByNotificationId(notificationId);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "通知不存在: " + notificationId);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<NotificationVO> listUserNotifications(Long userId, NotificationQueryDTO query) {
        if (userId == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        if (query == null) {
            query = new NotificationQueryDTO();
        }
        long pageNum = query.getPageNum() == null ? 1L : Math.max(1L, query.getPageNum());
        long pageSize = query.getPageSize() == null ? 10L : Math.min(100L, Math.max(1L, query.getPageSize()));

        Page<NotificationEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationEntity::getUserId, userId);
        if (StrUtil.isNotBlank(query.getType())) {
            wrapper.eq(NotificationEntity::getType, query.getType());
        }
        if (StrUtil.isNotBlank(query.getLevel())) {
            wrapper.eq(NotificationEntity::getLevel, query.getLevel());
        }
        if (query.getIsRead() != null) {
            wrapper.eq(NotificationEntity::getIsRead, query.getIsRead());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(NotificationEntity::getCreateTime, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(NotificationEntity::getCreateTime, query.getEndTime());
        }
        wrapper.orderByDesc(NotificationEntity::getCreateTime);

        IPage<NotificationEntity> result = notificationMapper.selectPage(page, wrapper);
        List<NotificationVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(pageNum, pageSize, result.getTotal(), records);
    }

    @Override
    public void markAsRead(String notificationId) {
        if (StrUtil.isBlank(notificationId)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "通知ID不能为空");
        }
        NotificationEntity entity = findByNotificationId(notificationId);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "通知不存在: " + notificationId);
        }
        if (entity.getIsRead() != null && entity.getIsRead() == 1) {
            log.info("通知已读，跳过: notificationId={}", notificationId);
            return;
        }
        LambdaUpdateWrapper<NotificationEntity> update = new LambdaUpdateWrapper<>();
        update.eq(NotificationEntity::getNotificationId, notificationId)
                .set(NotificationEntity::getIsRead, 1)
                .set(NotificationEntity::getReadTime, LocalDateTime.now());
        notificationMapper.update(null, update);
        log.info("通知已标记已读: notificationId={}", notificationId);
    }

    @Override
    public void markAllAsRead(Long userId) {
        if (userId == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        LambdaUpdateWrapper<NotificationEntity> update = new LambdaUpdateWrapper<>();
        update.eq(NotificationEntity::getUserId, userId)
                .eq(NotificationEntity::getIsRead, 0)
                .set(NotificationEntity::getIsRead, 1)
                .set(NotificationEntity::getReadTime, LocalDateTime.now());
        int rows = notificationMapper.update(null, update);
        log.info("用户 {} 全部通知已标记已读，影响行数={}", userId, rows);
    }

    @Override
    public void deleteNotification(String notificationId) {
        if (StrUtil.isBlank(notificationId)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "通知ID不能为空");
        }
        NotificationEntity entity = findByNotificationId(notificationId);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "通知不存在: " + notificationId);
        }
        notificationMapper.deleteById(entity.getId());
        log.info("通知已删除: notificationId={}", notificationId);
    }

    @Override
    public Integer getUnreadCount(Long userId) {
        if (userId == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationEntity::getUserId, userId)
                .eq(NotificationEntity::getIsRead, 0);
        Long count = notificationMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    @Override
    public NotificationStatsDTO getNotificationStats(Long userId) {
        log.info("获取通知统计: userId={}", userId);
        NotificationStatsDTO stats = new NotificationStatsDTO();

        // 总数
        LambdaQueryWrapper<NotificationEntity> baseWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            baseWrapper.eq(NotificationEntity::getUserId, userId);
        }
        Long total = notificationMapper.selectCount(baseWrapper);
        stats.setTotal(total == null ? 0L : total);

        // 未读数
        LambdaQueryWrapper<NotificationEntity> unreadWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            unreadWrapper.eq(NotificationEntity::getUserId, userId);
        }
        unreadWrapper.eq(NotificationEntity::getIsRead, 0);
        Long unread = notificationMapper.selectCount(unreadWrapper);
        stats.setUnreadCount(unread == null ? 0L : unread);

        // 已读数
        LambdaQueryWrapper<NotificationEntity> readWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            readWrapper.eq(NotificationEntity::getUserId, userId);
        }
        readWrapper.eq(NotificationEntity::getIsRead, 1);
        Long read = notificationMapper.selectCount(readWrapper);
        stats.setReadCount(read == null ? 0L : read);

        // 已读率
        if (total != null && total > 0) {
            stats.setReadRate(stats.getReadCount() * 100.0 / total);
        } else {
            stats.setReadRate(0.0);
        }

        // 按类型分组
        stats.setByType(buildGroupStatsByType(userId));

        // 按级别分组
        stats.setByLevel(buildGroupStatsByLevel(userId));

        // 按通道分组
        stats.setByChannel(buildGroupStatsByChannel(userId));

        // 按发送状态分组
        stats.setBySendStatus(buildGroupStatsBySendStatus(userId));

        return stats;
    }

    /**
     * 按类型分组统计
     */
    private Map<String, Long> buildGroupStatsByType(Long userId) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : List.of("TASK", "FILE", "SYSTEM", "SECURITY", "ALERT")) {
            LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
            if (userId != null) {
                wrapper.eq(NotificationEntity::getUserId, userId);
            }
            wrapper.eq(NotificationEntity::getType, key);
            Long count = notificationMapper.selectCount(wrapper);
            result.put(key, count == null ? 0L : count);
        }
        return result;
    }

    /**
     * 按级别分组统计
     */
    private Map<String, Long> buildGroupStatsByLevel(Long userId) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : List.of("INFO", "WARN", "ERROR", "CRITICAL")) {
            LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
            if (userId != null) {
                wrapper.eq(NotificationEntity::getUserId, userId);
            }
            wrapper.eq(NotificationEntity::getLevel, key);
            Long count = notificationMapper.selectCount(wrapper);
            result.put(key, count == null ? 0L : count);
        }
        return result;
    }

    /**
     * 按通道分组统计
     */
    private Map<String, Long> buildGroupStatsByChannel(Long userId) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : List.of(CHANNEL_IN_APP, CHANNEL_EMAIL, CHANNEL_IM, CHANNEL_ALL)) {
            LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
            if (userId != null) {
                wrapper.eq(NotificationEntity::getUserId, userId);
            }
            wrapper.eq(NotificationEntity::getChannel, key);
            Long count = notificationMapper.selectCount(wrapper);
            result.put(key, count == null ? 0L : count);
        }
        return result;
    }

    /**
     * 按发送状态分组统计
     */
    private Map<String, Long> buildGroupStatsBySendStatus(Long userId) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : List.of(SEND_STATUS_PENDING, SEND_STATUS_SENT, SEND_STATUS_FAILED)) {
            LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
            if (userId != null) {
                wrapper.eq(NotificationEntity::getUserId, userId);
            }
            wrapper.eq(NotificationEntity::getSendStatus, key);
            Long count = notificationMapper.selectCount(wrapper);
            result.put(key, count == null ? 0L : count);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer cleanupExpired() {
        log.info("清理过期通知");
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(NotificationEntity::getExpiredTime, LocalDateTime.now())
                .eq(NotificationEntity::getIsRead, 0);
        List<NotificationEntity> expired = notificationMapper.selectList(wrapper);
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(NotificationEntity::getId).toList();
        int rows = notificationMapper.deleteBatchIds(ids);
        log.info("过期通知清理完成: 清理数量={}", rows);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO retryFailed(String notificationId) {
        log.info("重试失败通知: notificationId={}", notificationId);
        if (StrUtil.isBlank(notificationId)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "通知ID不能为空");
        }
        NotificationEntity entity = findByNotificationId(notificationId);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "通知不存在: " + notificationId);
        }
        if (!SEND_STATUS_FAILED.equals(entity.getSendStatus())) {
            throw BusinessException.of(ResultCode.FAIL, "仅发送失败的通知可重试");
        }
        if (entity.getRetryCount() != null && entity.getRetryCount() >= MAX_RETRY_COUNT) {
            throw BusinessException.of(ResultCode.FAIL,
                    "重试次数已达上限: " + MAX_RETRY_COUNT);
        }

        boolean emailOk = true;
        boolean imOk = true;
        String channel = entity.getChannel();
        switch (channel) {
            case CHANNEL_EMAIL:
                emailOk = sendEmail(entity);
                break;
            case CHANNEL_IM:
                imOk = sendIM(entity);
                break;
            case CHANNEL_ALL:
                emailOk = sendEmail(entity);
                imOk = sendIM(entity);
                break;
            default:
                // IN_APP 不需要重试外部通道
                entity.setSendStatus(SEND_STATUS_SENT);
                entity.setRetryCount((entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1);
                notificationMapper.updateById(entity);
                return toVO(entity);
        }

        boolean allOk = emailOk && imOk;
        entity.setSendStatus(allOk ? SEND_STATUS_SENT : SEND_STATUS_FAILED);
        entity.setRetryCount((entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1);
        notificationMapper.updateById(entity);
        log.info("重试完成: notificationId={}, status={}", notificationId, entity.getSendStatus());
        return toVO(entity);
    }

    // ==================== 私有方法：通道分发 ====================

    /**
     * 站内信通道：持久化通知记录到数据库
     *
     * @param entity 通知实体
     */
    private void sendInApp(NotificationEntity entity) {
        notificationMapper.insert(entity);
        log.info("站内信已落库: notificationId={}, userId={}",
                entity.getNotificationId(), entity.getUserId());
    }

    /**
     * 邮件通道：通过 SMTP 发送邮件
     *
     * @param entity 通知实体
     * @return 发送结果 true=成功 false=失败
     */
    private boolean sendEmail(NotificationEntity entity) {
        if (!mailEnabled) {
            log.warn("邮件通道已关闭，跳过发送: notificationId={}", entity.getNotificationId());
            return true;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            // TODO: 通过 auth-service 解析用户邮箱，此处暂用 userId 占位以避免阻塞主流程
            message.setTo("user-" + entity.getUserId() + "@redteam.example.com");
            message.setSubject(entity.getTitle());
            message.setText(entity.getContent());
            mailSender.send(message);
            log.info("邮件已发送: notificationId={}, userId={}",
                    entity.getNotificationId(), entity.getUserId());
            return true;
        } catch (Exception e) {
            // 邮件发送失败不应影响其他通道与主流程
            log.error("邮件发送失败: notificationId={}", entity.getNotificationId(), e);
            return false;
        }
    }

    /**
     * IM 通道：通过 Kafka 投递到 IM 网关
     *
     * @param entity 通知实体
     * @return 投递结果 true=成功 false=失败
     */
    private boolean sendIM(NotificationEntity entity) {
        if (!imEnabled) {
            log.warn("IM通道已关闭，跳过发送: notificationId={}", entity.getNotificationId());
            return true;
        }
        try {
            String payload = JSONUtil.toJsonStr(entity);
            kafkaTemplate.send(imDispatchTopic, entity.getNotificationId(), payload);
            log.info("IM消息已投递: topic={}, notificationId={}",
                    imDispatchTopic, entity.getNotificationId());
            return true;
        } catch (Exception e) {
            log.error("IM投递失败: notificationId={}", entity.getNotificationId(), e);
            return false;
        }
    }

    // ==================== 私有工具方法 ====================

    /**
     * 根据通知业务ID查询实体
     *
     * @param notificationId 通知业务ID
     * @return 通知实体，不存在返回 null
     */
    private NotificationEntity findByNotificationId(String notificationId) {
        LambdaQueryWrapper<NotificationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationEntity::getNotificationId, notificationId);
        return notificationMapper.selectOne(wrapper);
    }

    /**
     * 由 DTO 构建通知实体（不含 id、createTime 等数据库自动生成字段）
     *
     * @param dto 通知 DTO
     * @return 通知实体
     */
    private NotificationEntity buildEntity(NotificationDTO dto) {
        NotificationEntity entity = new NotificationEntity();
        entity.setNotificationId(UUID.randomUUID().toString());
        entity.setUserId(dto.getUserId());
        entity.setSenderId(dto.getSenderId());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setType(dto.getType());
        entity.setLevel(dto.getLevel());
        entity.setChannel(dto.getChannel());
        entity.setIsRead(0);
        entity.setRelatedId(dto.getRelatedId());
        entity.setRelatedType(dto.getRelatedType());
        entity.setSendStatus(SEND_STATUS_PENDING);
        entity.setRetryCount(0);
        entity.setExpiredTime(dto.getExpiredTime());
        entity.setMetadata(dto.getMetadata());
        return entity;
    }

    /**
     * 实体转 VO
     *
     * @param entity 通知实体
     * @return 通知 VO
     */
    private NotificationVO toVO(NotificationEntity entity) {
        NotificationVO vo = new NotificationVO();
        vo.setNotificationId(entity.getNotificationId());
        vo.setUserId(entity.getUserId());
        vo.setSenderId(entity.getSenderId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setType(entity.getType());
        vo.setLevel(entity.getLevel());
        vo.setChannel(entity.getChannel());
        vo.setIsRead(entity.getIsRead());
        vo.setReadTime(entity.getReadTime());
        vo.setRelatedId(entity.getRelatedId());
        vo.setRelatedType(entity.getRelatedType());
        vo.setSendStatus(entity.getSendStatus());
        vo.setRetryCount(entity.getRetryCount());
        vo.setExpiredTime(entity.getExpiredTime());
        vo.setMetadata(entity.getMetadata());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
