package com.redteam.task.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.task.dto.TaskDTO;
import com.redteam.task.dto.TaskQueryDTO;
import com.redteam.task.dto.TaskStatsDTO;
import com.redteam.task.dto.TaskVO;
import com.redteam.task.entity.TaskEntity;
import com.redteam.task.mapper.TaskMapper;
import com.redteam.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务管理服务实现
 *
 * <p>基于MyBatis Plus实现任务CRUD、状态流转、分配、进度更新及统计，
 * 通过Kafka发布任务生命周期事件供下游服务消费。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    /**
     * Kafka 任务事件主题
     */
    private static final String TASK_EVENT_TOPIC = "redteam.task.events";

    /**
     * 任务状态常量
     */
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_PAUSED = "PAUSED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 允许的状态枚举集合
     */
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            STATUS_PENDING, STATUS_RUNNING, STATUS_PAUSED, STATUS_COMPLETED, STATUS_CANCELLED);

    /**
     * 事件类型常量
     */
    private static final String EVENT_CREATED = "task.created";
    private static final String EVENT_UPDATED = "task.updated";
    private static final String EVENT_DELETED = "task.deleted";
    private static final String EVENT_STARTED = "task.started";
    private static final String EVENT_PAUSED = "task.paused";
    private static final String EVENT_COMPLETED = "task.completed";
    private static final String EVENT_CANCELLED = "task.cancelled";
    private static final String EVENT_ASSIGNED = "task.assigned";
    private static final String EVENT_STATUS_CHANGED = "task.status_changed";
    private static final String EVENT_PROGRESS_UPDATED = "task.progress_updated";

    private final TaskMapper taskMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 创建任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO createTask(TaskDTO dto) {
        log.info("创建任务: taskName={}, taskType={}", dto.getTaskName(), dto.getTaskType());

        TaskEntity entity = new TaskEntity();
        entity.setTaskId(IdUtil.fastSimpleUUID());
        entity.setTaskName(dto.getTaskName());
        entity.setTaskType(dto.getTaskType());
        entity.setStatus(STATUS_PENDING);
        entity.setPriority(dto.getPriority());
        entity.setTargetId(dto.getTargetId());
        entity.setFileIds(dto.getFileIds());
        entity.setOwnerId(dto.getOwnerId());
        entity.setDeadline(dto.getDeadline());
        entity.setProgress(0);
        entity.setDescription(dto.getDescription());

        taskMapper.insert(entity);
        log.info("任务创建成功: taskId={}", entity.getTaskId());

        sendTaskEvent(EVENT_CREATED, entity);

        return convertToVO(entity);
    }

    /**
     * 查询任务详情
     */
    @Override
    public TaskVO getTask(String taskId) {
        log.info("查询任务详情: taskId={}", taskId);
        TaskEntity entity = getTaskEntityByTaskId(taskId);
        return convertToVO(entity);
    }

    /**
     * 更新任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO updateTask(String taskId, TaskDTO dto) {
        log.info("更新任务: taskId={}", taskId);

        TaskEntity entity = getTaskEntityByTaskId(taskId);

        if (StrUtil.isNotBlank(dto.getTaskName())) {
            entity.setTaskName(dto.getTaskName());
        }
        if (StrUtil.isNotBlank(dto.getTaskType())) {
            entity.setTaskType(dto.getTaskType());
        }
        if (dto.getPriority() != null) {
            entity.setPriority(dto.getPriority());
        }
        if (dto.getTargetId() != null) {
            entity.setTargetId(dto.getTargetId());
        }
        if (dto.getFileIds() != null) {
            entity.setFileIds(dto.getFileIds());
        }
        if (dto.getOwnerId() != null) {
            entity.setOwnerId(dto.getOwnerId());
        }
        if (dto.getDeadline() != null) {
            entity.setDeadline(dto.getDeadline());
        }
        if (dto.getProgress() != null) {
            entity.setProgress(dto.getProgress());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }

        taskMapper.updateById(entity);
        log.info("任务更新成功: taskId={}", taskId);

        sendTaskEvent(EVENT_UPDATED, entity);

        return convertToVO(entity);
    }

    /**
     * 删除任务（逻辑删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(String taskId) {
        log.info("删除任务: taskId={}", taskId);
        TaskEntity entity = getTaskEntityByTaskId(taskId);
        taskMapper.deleteById(entity.getId());
        log.info("任务删除成功: taskId={}", taskId);
        sendTaskEvent(EVENT_DELETED, entity);
    }

    /**
     * 分页查询任务列表
     */
    @Override
    public PageResult<TaskVO> listTasks(TaskQueryDTO query) {
        log.info("分页查询任务: pageNum={}, pageSize={}", query.getPageNum(), query.getPageSize());

        Page<TaskEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<TaskEntity> wrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(query.getTaskType())) {
            wrapper.eq(TaskEntity::getTaskType, query.getTaskType());
        }
        if (StrUtil.isNotBlank(query.getStatus())) {
            wrapper.eq(TaskEntity::getStatus, query.getStatus());
        }
        if (query.getOwnerId() != null) {
            wrapper.eq(TaskEntity::getOwnerId, query.getOwnerId());
        }
        if (StrUtil.isNotBlank(query.getTargetId())) {
            wrapper.eq(TaskEntity::getTargetId, query.getTargetId());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(TaskEntity::getTaskName, query.getKeyword())
                    .or().like(TaskEntity::getDescription, query.getKeyword()));
        }
        wrapper.orderByDesc(TaskEntity::getPriority);
        wrapper.orderByDesc(TaskEntity::getCreateTime);

        Page<TaskEntity> result = taskMapper.selectPage(page, wrapper);
        List<TaskVO> voList = result.getRecords().stream().map(this::convertToVO).toList();

        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), voList);
    }

    /**
     * 分配任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO assignTask(String taskId, Long ownerId) {
        log.info("分配任务: taskId={}, ownerId={}", taskId, ownerId);
        if (ownerId == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "负责人ID不能为空");
        }
        TaskEntity entity = getTaskEntityByTaskId(taskId);
        entity.setOwnerId(ownerId);
        taskMapper.updateById(entity);
        log.info("任务分配成功: taskId={}, ownerId={}", taskId, ownerId);
        sendTaskEvent(EVENT_ASSIGNED, entity);
        return convertToVO(entity);
    }

    /**
     * 更新任务状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO updateStatus(String taskId, String status) {
        log.info("更新任务状态: taskId={}, status={}", taskId, status);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "非法任务状态: " + status);
        }

        TaskEntity entity = getTaskEntityByTaskId(taskId);
        String oldStatus = entity.getStatus();

        // 状态流转校验
        validateStatusTransition(oldStatus, status);

        entity.setStatus(status);
        // 状态为 RUNNING 时设置开始时间
        if (STATUS_RUNNING.equals(status) && entity.getStartTime() == null) {
            entity.setStartTime(LocalDateTime.now());
        }
        // 状态为 COMPLETED 时设置结束时间
        if (STATUS_COMPLETED.equals(status)) {
            entity.setEndTime(LocalDateTime.now());
            if (entity.getProgress() == null) {
                entity.setProgress(100);
            } else {
                entity.setProgress(100);
            }
        }
        taskMapper.updateById(entity);
        log.info("任务状态更新成功: taskId={}, {} -> {}", taskId, oldStatus, status);
        sendTaskEvent(EVENT_STATUS_CHANGED, entity);
        return convertToVO(entity);
    }

    /**
     * 更新任务进度
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskVO updateProgress(String taskId, Integer progress) {
        log.info("更新任务进度: taskId={}, progress={}", taskId, progress);
        if (progress == null || progress < 0 || progress > 100) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "进度必须在 0-100 之间");
        }
        TaskEntity entity = getTaskEntityByTaskId(taskId);
        entity.setProgress(progress);
        // 进度 100 且状态允许时自动转为 COMPLETED
        if (progress == 100 && (STATUS_RUNNING.equals(entity.getStatus())
                || STATUS_PAUSED.equals(entity.getStatus()))) {
            entity.setStatus(STATUS_COMPLETED);
            entity.setEndTime(LocalDateTime.now());
        }
        taskMapper.updateById(entity);
        log.info("任务进度更新成功: taskId={}, progress={}", taskId, progress);
        sendTaskEvent(EVENT_PROGRESS_UPDATED, entity);
        return convertToVO(entity);
    }

    /**
     * 任务统计
     */
    @Override
    public TaskStatsDTO getTaskStats() {
        log.info("获取任务统计");
        TaskStatsDTO stats = new TaskStatsDTO();

        Long total = taskMapper.selectCount(Wrappers.lambdaQuery());
        stats.setTotal(total == null ? 0L : total);

        // 按状态分组
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of(STATUS_PENDING, STATUS_RUNNING, STATUS_PAUSED, STATUS_COMPLETED, STATUS_CANCELLED)) {
            Long count = taskMapper.selectCount(Wrappers.<TaskEntity>lambdaQuery().eq(TaskEntity::getStatus, s));
            byStatus.put(s, count == null ? 0L : count);
        }
        stats.setByStatus(byStatus);
        stats.setPendingCount(byStatus.getOrDefault(STATUS_PENDING, 0L));
        stats.setRunningCount(byStatus.getOrDefault(STATUS_RUNNING, 0L));
        stats.setCompletedCount(byStatus.getOrDefault(STATUS_COMPLETED, 0L));

        // 完成率
        if (total != null && total > 0) {
            stats.setCompletionRate(stats.getCompletedCount() * 100.0 / total);
        } else {
            stats.setCompletionRate(0.0);
        }

        // 按优先级分组
        Map<String, Long> byPriority = new HashMap<>();
        for (int p = 1; p <= 5; p++) {
            Long count = taskMapper.selectCount(Wrappers.<TaskEntity>lambdaQuery().eq(TaskEntity::getPriority, p));
            byPriority.put(String.valueOf(p), count == null ? 0L : count);
        }
        stats.setByPriority(byPriority);

        // 按负责人分组（按 owner_id 聚合，本实现走内存分组）
        Map<String, Long> byOwner = new HashMap<>();
        List<TaskEntity> allTasks = taskMapper.selectList(null);
        for (TaskEntity t : allTasks) {
            if (t.getOwnerId() != null) {
                byOwner.merge(String.valueOf(t.getOwnerId()), 1L, Long::sum);
            }
        }
        stats.setByOwner(byOwner);

        return stats;
    }

    /**
     * 启动任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startTask(String taskId) {
        log.info("启动任务: taskId={}", taskId);
        TaskEntity entity = getTaskEntityByTaskId(taskId);

        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw BusinessException.of(ResultCode.FAIL,
                    StrUtil.format("任务当前状态[{}]不允许启动，仅待执行(PENDING)状态可启动", entity.getStatus()));
        }

        entity.setStatus(STATUS_RUNNING);
        entity.setStartTime(LocalDateTime.now());
        taskMapper.updateById(entity);
        log.info("任务启动成功: taskId={}", taskId);

        sendTaskEvent(EVENT_STARTED, entity);
    }

    /**
     * 暂停任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseTask(String taskId) {
        log.info("暂停任务: taskId={}", taskId);
        TaskEntity entity = getTaskEntityByTaskId(taskId);

        if (!STATUS_RUNNING.equals(entity.getStatus())) {
            throw BusinessException.of(ResultCode.FAIL,
                    StrUtil.format("任务当前状态[{}]不允许暂停，仅执行中(RUNNING)状态可暂停", entity.getStatus()));
        }

        entity.setStatus(STATUS_PAUSED);
        taskMapper.updateById(entity);
        log.info("任务暂停成功: taskId={}", taskId);

        sendTaskEvent(EVENT_PAUSED, entity);
    }

    /**
     * 完成任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId) {
        log.info("完成任务: taskId={}", taskId);
        TaskEntity entity = getTaskEntityByTaskId(taskId);

        String status = entity.getStatus();
        if (!STATUS_RUNNING.equals(status) && !STATUS_PAUSED.equals(status)) {
            throw BusinessException.of(ResultCode.FAIL,
                    StrUtil.format("任务当前状态[{}]不允许完成，仅执行中(RUNNING)/已暂停(PAUSED)状态可完成", status));
        }

        entity.setStatus(STATUS_COMPLETED);
        entity.setEndTime(LocalDateTime.now());
        entity.setProgress(100);
        taskMapper.updateById(entity);
        log.info("任务完成成功: taskId={}", taskId);

        sendTaskEvent(EVENT_COMPLETED, entity);
    }

    /**
     * 取消任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(String taskId) {
        log.info("取消任务: taskId={}", taskId);
        TaskEntity entity = getTaskEntityByTaskId(taskId);

        String status = entity.getStatus();
        if (STATUS_COMPLETED.equals(status) || STATUS_CANCELLED.equals(status)) {
            throw BusinessException.of(ResultCode.FAIL,
                    StrUtil.format("任务当前状态[{}]不允许取消", status));
        }

        entity.setStatus(STATUS_CANCELLED);
        taskMapper.updateById(entity);
        log.info("任务取消成功: taskId={}", taskId);

        sendTaskEvent(EVENT_CANCELLED, entity);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据任务ID查询任务实体
     *
     * @param taskId 任务ID
     * @return 任务实体
     * @throws BusinessException 任务不存在时抛出
     */
    private TaskEntity getTaskEntityByTaskId(String taskId) {
        LambdaQueryWrapper<TaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskEntity::getTaskId, taskId);
        TaskEntity entity = taskMapper.selectOne(wrapper);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "任务不存在: " + taskId);
        }
        return entity;
    }

    /**
     * 校验状态流转是否合法
     *
     * @param from 当前状态
     * @param to   目标状态
     */
    private void validateStatusTransition(String from, String to) {
        boolean valid = switch (from) {
            case STATUS_PENDING -> Set.of(STATUS_RUNNING, STATUS_CANCELLED).contains(to);
            case STATUS_RUNNING -> Set.of(STATUS_PAUSED, STATUS_COMPLETED, STATUS_CANCELLED).contains(to);
            case STATUS_PAUSED -> Set.of(STATUS_RUNNING, STATUS_COMPLETED, STATUS_CANCELLED).contains(to);
            case STATUS_COMPLETED, STATUS_CANCELLED -> false;
            default -> false;
        };
        if (!valid) {
            throw BusinessException.of(ResultCode.FAIL,
                    StrUtil.format("非法状态流转: {} -> {}", from, to));
        }
    }

    /**
     * 发送任务事件至 Kafka
     *
     * @param eventType 事件类型
     * @param task      任务实体
     */
    private void sendTaskEvent(String eventType, TaskEntity task) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", eventType);
            event.put("taskId", task.getTaskId());
            event.put("taskName", task.getTaskName());
            event.put("status", task.getStatus());
            event.put("progress", task.getProgress());
            event.put("ownerId", task.getOwnerId());
            event.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send(TASK_EVENT_TOPIC, task.getTaskId(), JSONUtil.toJsonStr(event));
            log.debug("任务事件已发送: eventType={}, taskId={}", eventType, task.getTaskId());
        } catch (Exception e) {
            log.error("任务事件发送失败: eventType={}, taskId={}", eventType, task.getTaskId(), e);
        }
    }

    /**
     * 实体转 VO
     *
     * @param entity 任务实体
     * @return 任务VO
     */
    private TaskVO convertToVO(TaskEntity entity) {
        TaskVO vo = new TaskVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setTaskName(entity.getTaskName());
        vo.setTaskType(entity.getTaskType());
        vo.setStatus(entity.getStatus());
        vo.setPriority(entity.getPriority());
        vo.setTargetId(entity.getTargetId());
        vo.setFileIds(entity.getFileIds());
        vo.setOwnerId(entity.getOwnerId());
        vo.setDeadline(entity.getDeadline());
        vo.setProgress(entity.getProgress());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setDescription(entity.getDescription());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setCreateBy(entity.getCreateBy());
        vo.setUpdateBy(entity.getUpdateBy());
        return vo;
    }
}
