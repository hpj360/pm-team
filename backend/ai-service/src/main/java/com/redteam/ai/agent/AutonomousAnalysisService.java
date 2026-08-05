package com.redteam.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.entity.AgentTaskEntity;
import com.redteam.ai.mapper.AgentTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自主分析服务
 *
 * <p>用户提交分析请求后，Agent 自主完成检索、推理与结论生成。
 * 任务异步执行，状态流转：PENDING → RUNNING → COMPLETED / FAILED。</p>
 *
 * <p>任务结果同时持久化到数据库与内存缓存，支持后续查询与轨迹回放。</p>
 *
 * @author 红方团队
 */
@Service
@Slf4j
public class AutonomousAnalysisService {

    /**
     * 任务状态枚举
     */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 默认用户权限（V5.1 阶段暂开放全部工具权限）
     */
    private static final Set<String> DEFAULT_PERMISSIONS = Set.of("*");

    @Autowired
    private AgentExecutor agentExecutor;

    @Autowired
    private AgentTaskMapper agentTaskMapper;

    /**
     * 任务内存缓存（taskId -> entity），用于快速查询状态
     */
    private final Map<String, AgentTaskEntity> taskCache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 提交自主分析任务（异步执行）
     *
     * @param query  自然语言分析请求
     * @param userId 用户ID
     * @return 任务ID
     */
    public String submitAnalysis(String query, Long userId) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("分析请求不能为空");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");

        // 创建任务记录（PENDING 状态）
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setTaskId(taskId);
        entity.setQuery(query);
        entity.setUserId(userId);
        entity.setStatus(STATUS_PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setEvidenceChainJson("[]");
        entity.setReferencedFilesJson("[]");
        entity.setTracesJson("[]");
        entity.setConfidence(0.0);

        // 持久化
        try {
            agentTaskMapper.insert(entity);
        } catch (Exception e) {
            log.warn("任务落库失败（继续内存缓存）, taskId={}: {}", taskId, e.getMessage());
        }
        taskCache.put(taskId, entity);

        log.info("Agent 分析任务已提交, taskId={}, userId={}, query={}", taskId, userId, query);

        // 异步执行
        executeAsync(taskId, query, userId);

        return taskId;
    }

    /**
     * 异步执行 Agent 分析
     *
     * @param taskId 任务ID
     * @param query  分析请求
     * @param userId 用户ID
     */
    @Async("agentTaskExecutor")
    public void executeAsync(String taskId, String query, Long userId) {
        log.info("Agent 异步执行开始, taskId={}", taskId);
        AgentTaskEntity entity = taskCache.get(taskId);
        if (entity == null) {
            entity = new AgentTaskEntity();
            entity.setTaskId(taskId);
            entity.setQuery(query);
            entity.setUserId(userId);
        }

        try {
            // 更新为 RUNNING
            entity.setStatus(STATUS_RUNNING);
            updateTask(entity);

            // 执行 Agent
            AgentResult result = agentExecutor.execute(query, DEFAULT_PERMISSIONS);

            // 填充结果
            entity.setStatus(STATUS_COMPLETED);
            entity.setConclusion(result.getConclusion());
            entity.setEvidenceChainJson(toJson(result.getEvidenceChain()));
            entity.setReferencedFilesJson(toJson(result.getReferencedFiles()));
            entity.setConfidence(result.getConfidence());
            entity.setTracesJson(toJson(result.getTraces()));
            entity.setCompletedAt(LocalDateTime.now());
            if (result.isDegraded() && result.getErrorMessage() != null) {
                entity.setErrorMessage(result.getErrorMessage());
            }
            updateTask(entity);
            log.info("Agent 异步执行完成, taskId={}, confidence={}", taskId, result.getConfidence());

        } catch (Exception e) {
            log.error("Agent 异步执行异常, taskId={}", taskId, e);
            entity.setStatus(STATUS_FAILED);
            entity.setErrorMessage("执行异常: " + e.getMessage());
            entity.setCompletedAt(LocalDateTime.now());
            updateTask(entity);
        }
    }

    /**
     * 查询任务状态与结果
     *
     * @param taskId 任务ID
     * @return 任务实体，不存在返回 null
     */
    public AgentTaskEntity getTask(String taskId) {
        // 优先从内存缓存读取
        AgentTaskEntity entity = taskCache.get(taskId);
        if (entity != null) {
            return entity;
        }
        // 降级从数据库读取
        try {
            entity = agentTaskMapper.selectById(taskId);
            if (entity != null) {
                taskCache.put(taskId, entity);
            }
            return entity;
        } catch (Exception e) {
            log.warn("查询任务失败, taskId={}: {}", taskId, e.getMessage());
            return null;
        }
    }

    /**
     * 查询任务的推理轨迹
     *
     * @param taskId 任务ID
     * @return 推理轨迹列表
     */
    public List<AgentTrace> getTraces(String taskId) {
        AgentTaskEntity entity = getTask(taskId);
        if (entity == null || entity.getTracesJson() == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(entity.getTracesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AgentTrace.class));
        } catch (Exception e) {
            log.warn("解析推理轨迹失败, taskId={}: {}", taskId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询用户任务列表
     *
     * @param userId 用户ID
     * @param limit  返回条数上限
     * @return 任务列表
     */
    public List<AgentTaskEntity> listTasks(Long userId, int limit) {
        try {
            List<AgentTaskEntity> tasks = agentTaskMapper.selectByUserId(userId, limit <= 0 ? 20 : limit);
            // 同步到缓存
            for (AgentTaskEntity task : tasks) {
                taskCache.putIfAbsent(task.getTaskId(), task);
            }
            return tasks;
        } catch (Exception e) {
            log.warn("查询任务列表失败, userId={}: {}", userId, e.getMessage());
            // 降级：从缓存返回
            List<AgentTaskEntity> result = new ArrayList<>();
            for (AgentTaskEntity entity : taskCache.values()) {
                if (userId == null || userId.equals(entity.getUserId())) {
                    result.add(entity);
                }
            }
            result.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            int max = limit <= 0 ? 20 : limit;
            return result.subList(0, Math.min(max, result.size()));
        }
    }

    /**
     * 更新任务（缓存 + 数据库）
     *
     * @param entity 任务实体
     */
    private void updateTask(AgentTaskEntity entity) {
        taskCache.put(entity.getTaskId(), entity);
        try {
            agentTaskMapper.updateById(entity);
        } catch (Exception e) {
            log.warn("任务更新失败, taskId={}: {}", entity.getTaskId(), e.getMessage());
        }
    }

    /**
     * 对象转 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return "[]";
        }
    }
}
