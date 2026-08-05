package com.redteam.analyze.dynamic;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.analyze.config.CuckooProperties;
import com.redteam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态分析编排服务
 *
 * <p>负责动态分析任务的完整生命周期编排：</p>
 * <ol>
 *   <li>{@link #submitDynamicAnalysis(Long)}：生成业务 taskId，提交至 Cuckoo，状态置为 SUBMITTED</li>
 *   <li>{@link #pollTask(String)}：轮询 Cuckoo 状态，COMPLETED 后拉取报告</li>
 *   <li>{@link #parseReport(String)}：调用 {@link BehaviorIndicatorExtractor} 解析行为指标，状态置为 PARSED</li>
 *   <li>{@link #getTask(String)} / {@link #getReport(String)}：查询任务与报告</li>
 * </ol>
 *
 * <p>状态机：{@code PENDING → SUBMITTED → RUNNING → COMPLETED → PARSED}</p>
 *
 * <p>降级策略：Cuckoo 不可用时，taskId 标记 degraded，状态流转为 DEGRADED，
 * 仍返回基础结构（空指标），不阻塞主流程。</p>
 *
 * <p>静态 + 动态联合编排：{@link #parseReport} 在解析动态行为后，与静态 IOC 合并输出联合指标。
 * 当前版本静态 IOC 由调用方注入（{@link #attachStaticIocs}），实现解耦。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicAnalysisService {

    /**
     * ISO 时间格式化器
     */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 任务存储（内存，ConcurrentHashMap）
     */
    private final Map<String, DynamicAnalysisTask> taskStore = new ConcurrentHashMap<>();

    /**
     * 静态 IOC 注入存储（taskId -> 静态 IOC 列表）
     */
    private final Map<String, List<Map<String, Object>>> staticIocStore = new ConcurrentHashMap<>();

    private final CuckooClient cuckooClient;

    private final CuckooProperties cuckooProperties;

    private final BehaviorIndicatorExtractor behaviorIndicatorExtractor;

    /**
     * 提交动态分析任务
     *
     * @param fileId 文件ID
     * @return 平台侧动态分析任务ID
     */
    public String submitDynamicAnalysis(Long fileId) {
        if (fileId == null) {
            throw new BusinessException("文件ID不能为空");
        }
        String taskId = "dyn-" + IdUtil.fastSimpleUUID();
        DynamicAnalysisTask task = new DynamicAnalysisTask();
        task.setTaskId(taskId);
        task.setFileId(fileId);
        task.setStatus(DynamicAnalysisTask.STATUS_PENDING);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskStore.put(taskId, task);
        log.info("动态分析任务创建: taskId={}, fileId={}", taskId, fileId);

        // 提交到 Cuckoo
        String cuckooTaskId = cuckooClient.submitFile(fileId);
        task.setCuckooTaskId(cuckooTaskId);
        if (cuckooClient.isDegraded(cuckooTaskId)) {
            task.setDegraded(true);
            task.setStatus(DynamicAnalysisTask.STATUS_DEGRADED);
            task.setErrorMessage("Cuckoo 沙箱不可用，已降级");
            log.warn("动态分析降级: taskId={}, fileId={}", taskId, fileId);
        } else {
            task.setStatus(DynamicAnalysisTask.STATUS_SUBMITTED);
            // 降级场景下无需拉取报告，直接标记并解析空报告
        }
        task.setUpdateTime(LocalDateTime.now());
        return taskId;
    }

    /**
     * 轮询任务状态（单次轮询）
     *
     * <p>真实生产场景由定时任务或异步线程驱动；此处提供单步轮询入口便于测试与编排。
     * 状态为 COMPLETED 时自动拉取报告；DEGRADED 时跳过。</p>
     *
     * @param taskId 平台侧任务ID
     * @return 当前状态
     */
    public String pollTask(String taskId) {
        DynamicAnalysisTask task = getTaskInternal(taskId);
        if (DynamicAnalysisTask.STATUS_DEGRADED.equals(task.getStatus())
                || DynamicAnalysisTask.STATUS_PARSED.equals(task.getStatus())
                || DynamicAnalysisTask.STATUS_FAILED.equals(task.getStatus())
                || DynamicAnalysisTask.STATUS_COMPLETED.equals(task.getStatus())) {
            return task.getStatus();
        }
        String cuckooStatus = cuckooClient.getTaskStatus(task.getCuckooTaskId());
        if (CuckooClient.STATUS_DEGRADED.equals(cuckooStatus)) {
            task.setDegraded(true);
            task.setStatus(DynamicAnalysisTask.STATUS_DEGRADED);
            task.setErrorMessage("Cuckoo 状态查询降级");
        } else if (CuckooClient.STATUS_COMPLETED.equalsIgnoreCase(cuckooStatus)) {
            task.setStatus(DynamicAnalysisTask.STATUS_COMPLETED);
            // 拉取报告
            String report = cuckooClient.getReport(task.getCuckooTaskId());
            task.setRawReport(report);
            // 立即解析
            parseReport(taskId);
        } else if (CuckooClient.STATUS_RUNNING.equalsIgnoreCase(cuckooStatus)) {
            task.setStatus(DynamicAnalysisTask.STATUS_RUNNING);
        } else {
            // PENDING / SUBMITTED / 其他 → 保持 SUBMITTED
            task.setStatus(DynamicAnalysisTask.STATUS_SUBMITTED);
        }
        task.setUpdateTime(LocalDateTime.now());
        return task.getStatus();
    }

    /**
     * 解析报告（状态 COMPLETED → PARSED）
     *
     * @param taskId 平台侧任务ID
     * @return 解析后的任务
     */
    public DynamicAnalysisTask parseReport(String taskId) {
        DynamicAnalysisTask task = getTaskInternal(taskId);
        if (DynamicAnalysisTask.STATUS_DEGRADED.equals(task.getStatus())) {
            // 降级任务：填充空指标摘要
            return task;
        }
        if (StrUtil.isBlank(task.getRawReport())) {
            log.warn("报告为空，无法解析: taskId={}", taskId);
            task.setStatus(DynamicAnalysisTask.STATUS_FAILED);
            task.setErrorMessage("Cuckoo 报告为空");
            return task;
        }
        try {
            behaviorIndicatorExtractor.extract(task);
            // 合并静态 IOC（联合编排）
            List<Map<String, Object>> staticIocs = staticIocStore.getOrDefault(taskId, Collections.emptyList());
            if (!staticIocs.isEmpty()) {
                List<Map<String, Object>> merged = new ArrayList<>(task.getIocs());
                for (Map<String, Object> staticIoc : staticIocs) {
                    if (!merged.contains(staticIoc)) {
                        merged.add(staticIoc);
                    }
                }
                task.setIocs(merged);
            }
            task.setStatus(DynamicAnalysisTask.STATUS_PARSED);
            task.setParsedTime(LocalDateTime.now());
            log.info("动态分析报告解析完成: taskId={}, techniques={}, iocs={}",
                    taskId,
                    task.getAttackTechniques().size(),
                    task.getIocs().size());
        } catch (Exception e) {
            log.error("动态分析报告解析失败: taskId={}", taskId, e);
            task.setStatus(DynamicAnalysisTask.STATUS_FAILED);
            task.setErrorMessage("报告解析失败: " + e.getMessage());
        }
        task.setUpdateTime(LocalDateTime.now());
        return task;
    }

    /**
     * 注入静态分析 IOC（联合编排入口）
     *
     * @param taskId 平台侧任务ID
     * @param iocs   静态 IOC 列表
     */
    public void attachStaticIocs(String taskId, List<Map<String, Object>> iocs) {
        if (StrUtil.isBlank(taskId) || iocs == null) {
            return;
        }
        staticIocStore.put(taskId, new ArrayList<>(iocs));
    }

    /**
     * 获取任务（对外）
     *
     * @param taskId 平台侧任务ID
     * @return 任务对象
     */
    public DynamicAnalysisTask getTask(String taskId) {
        return getTaskInternal(taskId);
    }

    /**
     * 获取报告 VO
     *
     * @param taskId 平台侧任务ID
     * @return 报告 VO
     */
    public DynamicReportVO getReport(String taskId) {
        DynamicAnalysisTask task = getTaskInternal(taskId);
        return toVO(task);
    }

    /**
     * 列出全部动态分析任务
     *
     * @return 任务列表
     */
    public List<DynamicAnalysisTask> listTasks() {
        return new ArrayList<>(taskStore.values());
    }

    /**
     * 获取 Cuckoo 配置（测试可见）
     *
     * @return 配置
     */
    CuckooProperties getCuckooProperties() {
        return cuckooProperties;
    }

    // ==================== 内部方法 ====================

    /**
     * 获取任务（内部，不存在抛异常）
     */
    private DynamicAnalysisTask getTaskInternal(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException("任务ID不能为空");
        }
        DynamicAnalysisTask task = taskStore.get(taskId);
        if (task == null) {
            throw new BusinessException("动态分析任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 任务转 VO
     */
    private DynamicReportVO toVO(DynamicAnalysisTask task) {
        DynamicReportVO vo = new DynamicReportVO();
        vo.setTaskId(task.getTaskId());
        vo.setFileId(task.getFileId());
        vo.setCuckooTaskId(task.getCuckooTaskId());
        vo.setStatus(task.getStatus());
        vo.setDegraded(task.isDegraded());
        vo.setScore(extractScore(task));
        vo.setSummary(extractSummary(task));
        vo.setProcessTree(task.getProcessTree());
        vo.setNetworkConnections(task.getNetworkConnections());
        vo.setFileOperations(task.getFileOperations());
        vo.setAttackTechniques(task.getAttackTechniques());
        vo.setIocs(task.getIocs());
        vo.setStixObjects(behaviorIndicatorExtractor.buildStixObjects(task));
        vo.setErrorMessage(task.getErrorMessage());
        vo.setCreateTime(task.getCreateTime() != null ? task.getCreateTime().format(ISO_FORMATTER) : null);
        vo.setParsedTime(task.getParsedTime() != null ? task.getParsedTime().format(ISO_FORMATTER) : null);
        return vo;
    }

    /**
     * 从原始报告提取 score
     */
    private Double extractScore(DynamicAnalysisTask task) {
        if (StrUtil.isBlank(task.getRawReport())) {
            return task.isDegraded() ? 0.0 : null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(task.getRawReport());
            return json.getDouble("score", 0.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 从原始报告提取 summary
     */
    private String extractSummary(DynamicAnalysisTask task) {
        if (task.isDegraded()) {
            return "Cuckoo 沙箱不可用，已降级为基础静态分析结果";
        }
        if (StrUtil.isBlank(task.getRawReport())) {
            return null;
        }
        try {
            JSONObject json = JSONUtil.parseObj(task.getRawReport());
            return json.getStr("summary");
        } catch (Exception e) {
            return null;
        }
    }
}
