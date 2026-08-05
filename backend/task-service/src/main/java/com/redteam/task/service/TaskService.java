package com.redteam.task.service;

import com.redteam.common.result.PageResult;
import com.redteam.task.dto.TaskDTO;
import com.redteam.task.dto.TaskQueryDTO;
import com.redteam.task.dto.TaskStatsDTO;
import com.redteam.task.dto.TaskVO;

/**
 * 任务管理服务接口
 *
 * <p>提供任务的创建、查询、更新、删除、状态流转、分配、进度更新及统计等能力。</p>
 *
 * @author 红方团队
 */
public interface TaskService {

    /**
     * 创建任务
     *
     * <p>自动生成 UUID 作为任务业务主键，初始状态为 PENDING，
     * 创建成功后发送 task.created 事件至 Kafka。</p>
     *
     * @param dto 任务创建DTO
     * @return 任务VO
     */
    TaskVO createTask(TaskDTO dto);

    /**
     * 根据任务ID查询任务详情
     *
     * @param taskId 任务ID（UUID）
     * @return 任务VO
     */
    TaskVO getTask(String taskId);

    /**
     * 更新任务信息
     *
     * <p>仅允许更新任务名称、类型、优先级、目标、负责人、描述等业务字段，
     * 不允许通过本接口变更任务状态。更新成功后发送 task.updated 事件。</p>
     *
     * @param taskId 任务ID
     * @param dto    任务更新DTO
     * @return 任务VO
     */
    TaskVO updateTask(String taskId, TaskDTO dto);

    /**
     * 删除任务（逻辑删除）
     *
     * <p>删除成功后发送 task.deleted 事件。</p>
     *
     * @param taskId 任务ID
     */
    void deleteTask(String taskId);

    /**
     * 分页查询任务列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<TaskVO> listTasks(TaskQueryDTO query);

    /**
     * 分配任务给指定用户
     *
     * @param taskId  任务ID
     * @param ownerId 负责人ID
     * @return 更新后的任务VO
     */
    TaskVO assignTask(String taskId, Long ownerId);

    /**
     * 更新任务状态（PENDING→RUNNING→COMPLETED）
     *
     * @param taskId 任务ID
     * @param status 新状态
     * @return 更新后的任务VO
     */
    TaskVO updateStatus(String taskId, String status);

    /**
     * 更新任务进度
     *
     * @param taskId   任务ID
     * @param progress 进度（0-100）
     * @return 更新后的任务VO
     */
    TaskVO updateProgress(String taskId, Integer progress);

    /**
     * 任务统计（按状态、优先级、负责人分组）
     *
     * @return 统计结果
     */
    TaskStatsDTO getTaskStats();

    /**
     * 启动任务
     *
     * @param taskId 任务ID
     */
    void startTask(String taskId);

    /**
     * 暂停任务
     *
     * @param taskId 任务ID
     */
    void pauseTask(String taskId);

    /**
     * 完成任务
     *
     * @param taskId 任务ID
     */
    void completeTask(String taskId);

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     */
    void cancelTask(String taskId);
}
