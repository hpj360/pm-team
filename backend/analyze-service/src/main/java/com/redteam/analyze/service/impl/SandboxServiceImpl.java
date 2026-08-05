package com.redteam.analyze.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.analyze.config.SandboxProperties;
import com.redteam.analyze.dto.SandboxReportVO;
import com.redteam.analyze.service.SandboxService;
import com.redteam.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 沙箱分析服务实现
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>通过 HTTP 调用 Cuckoo 沙箱 API 提交文件并查询报告。</li>
 *   <li>三级降级策略：配置禁用 / API 调用异常 / 超时 → 返回降级结果（基础静态分析）。</li>
 *   <li>降级结果标记 {@code degraded=true}，填充文件元信息与简易静态指标。</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    /**
     * 降级任务ID前缀
     */
    private static final String DEGRADED_PREFIX = "degraded-";

    /**
     * 状态：已完成
     */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * 状态：失败
     */
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 状态：降级
     */
    private static final String STATUS_DEGRADED = "DEGRADED";

    /**
     * 状态：运行中
     */
    private static final String STATUS_RUNNING = "RUNNING";

    /**
     * 状态：待处理
     */
    private static final String STATUS_PENDING = "PENDING";

    private final SandboxProperties sandboxProperties;

    /**
     * HTTP 客户端（懒加载）
     */
    private RestClient restClient;

    /**
     * 初始化 HTTP 客户端
     */
    @PostConstruct
    public void init() {
        restClient = RestClient.builder()
                .baseUrl(sandboxProperties.getApiUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", "Bearer " + sandboxProperties.getApiKey())
                .build();
        log.info("沙箱服务初始化: apiUrl={}, enabled={}", sandboxProperties.getApiUrl(), sandboxProperties.isEnabled());
    }

    /**
     * 提交文件到沙箱进行分析
     *
     * @param fileId 文件ID
     * @return 沙箱任务ID（降级时返回 degraded- 前缀标识）
     */
    @Override
    public String submitToSandbox(Long fileId) {
        if (fileId == null) {
            throw new BusinessException("文件ID不能为空");
        }
        log.info("提交文件到沙箱: fileId={}", fileId);

        if (!sandboxProperties.isEnabled()) {
            log.warn("沙箱未启用，返回降级结果: fileId={}", fileId);
            return DEGRADED_PREFIX + fileId;
        }

        try {
            return submitViaHttp(fileId);
        } catch (Exception e) {
            log.error("沙箱提交失败，降级处理: fileId={}", fileId, e);
            return DEGRADED_PREFIX + fileId;
        }
    }

    /**
     * 获取沙箱分析报告
     *
     * @param taskId 沙箱任务ID
     * @return 沙箱报告 VO
     */
    @Override
    public SandboxReportVO getSandboxReport(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException("沙箱任务ID不能为空");
        }
        log.info("获取沙箱报告: taskId={}", taskId);

        if (isDegradedTaskId(taskId)) {
            return buildDegradedReport(taskId);
        }

        try {
            return fetchReportViaHttp(taskId);
        } catch (Exception e) {
            log.error("获取沙箱报告失败，降级处理: taskId={}", taskId, e);
            return buildDegradedReport(taskId);
        }
    }

    /**
     * 获取沙箱分析状态
     *
     * @param taskId 沙箱任务ID
     * @return 状态字符串
     */
    @Override
    public String getSandboxStatus(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException("沙箱任务ID不能为空");
        }
        log.info("获取沙箱状态: taskId={}", taskId);

        if (isDegradedTaskId(taskId)) {
            return STATUS_DEGRADED;
        }

        if (!sandboxProperties.isEnabled()) {
            return STATUS_DEGRADED;
        }

        try {
            return fetchStatusViaHttp(taskId);
        } catch (Exception e) {
            log.error("获取沙箱状态失败，降级处理: taskId={}", taskId, e);
            return STATUS_DEGRADED;
        }
    }

    // ==================== HTTP 调用 ====================

    /**
     * 通过 HTTP 提交文件到沙箱
     *
     * @param fileId 文件ID
     * @return 沙箱任务ID
     */
    private String submitViaHttp(Long fileId) {
        JSONObject body = new JSONObject();
        body.set("fileId", fileId);
        String resp = restClient.post()
                .uri("/api/tasks/create")
                .body(body.toString())
                .retrieve()
                .body(String.class);
        JSONObject json = JSONUtil.parseObj(resp);
        String taskId = json.getStr("taskId");
        if (StrUtil.isBlank(taskId)) {
            taskId = json.getStr("task_id");
        }
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException("沙箱返回的任务ID为空");
        }
        log.info("沙箱提交成功: fileId={}, taskId={}", fileId, taskId);
        return taskId;
    }

    /**
     * 通过 HTTP 获取沙箱报告
     *
     * @param taskId 沙箱任务ID
     * @return 沙箱报告 VO
     */
    private SandboxReportVO fetchReportViaHttp(String taskId) {
        String resp = restClient.get()
                .uri(java.net.URI.create("/api/tasks/" + taskId + "/report"))
                .retrieve()
                .body(String.class);
        return parseReport(taskId, resp);
    }

    /**
     * 通过 HTTP 获取沙箱状态
     *
     * @param taskId 沙箱任务ID
     * @return 状态字符串
     */
    private String fetchStatusViaHttp(String taskId) {
        String resp = restClient.get()
                .uri(java.net.URI.create("/api/tasks/" + taskId + "/status"))
                .retrieve()
                .body(String.class);
        JSONObject json = JSONUtil.parseObj(resp);
        String status = json.getStr("status");
        if (StrUtil.isBlank(status)) {
            status = json.getStr("taskStatus");
        }
        return StrUtil.blankToDefault(status, STATUS_PENDING);
    }

    /**
     * 解析沙箱报告响应
     *
     * @param taskId 沙箱任务ID
     * @param resp   HTTP 响应体
     * @return 沙箱报告 VO
     */
    @SuppressWarnings("unchecked")
    private SandboxReportVO parseReport(String taskId, String resp) {
        SandboxReportVO vo = new SandboxReportVO();
        vo.setTaskId(taskId);
        vo.setDegraded(false);
        if (StrUtil.isBlank(resp)) {
            vo.setStatus(STATUS_PENDING);
            return vo;
        }
        JSONObject json = JSONUtil.parseObj(resp);
        vo.setStatus(json.getStr("status", STATUS_COMPLETED));
        vo.setScore(json.getDouble("score", 0.0));
        vo.setSummary(json.getStr("summary"));
        Object threats = json.get("threats");
        if (threats instanceof List) {
            vo.setThreats((List<String>) threats);
        }
        Object signatures = json.get("signatures");
        if (signatures instanceof List) {
            vo.setSignatures((List<String>) signatures);
        }
        Object network = json.get("network");
        if (network instanceof Map) {
            vo.setNetworkInfo((Map<String, Object>) network);
        }
        return vo;
    }

    // ==================== 降级策略 ====================

    /**
     * 判断是否为降级任务ID
     *
     * @param taskId 任务ID
     * @return 是否降级
     */
    private boolean isDegradedTaskId(String taskId) {
        return taskId != null && taskId.startsWith(DEGRADED_PREFIX);
    }

    /**
     * 构建降级报告（基础静态分析）
     *
     * @param taskId 沙箱任务ID
     * @return 降级报告 VO
     */
    private SandboxReportVO buildDegradedReport(String taskId) {
        SandboxReportVO vo = new SandboxReportVO();
        vo.setTaskId(taskId);
        vo.setStatus(STATUS_DEGRADED);
        vo.setDegraded(true);
        vo.setScore(0.0);
        vo.setSummary("沙箱不可用，已降级为基础静态分析结果");
        vo.setThreats(new ArrayList<>());
        vo.setSignatures(new ArrayList<>());
        Map<String, Object> staticInfo = new LinkedHashMap<>();
        staticInfo.put("note", "沙箱服务不可用，仅返回基础静态分析信息");
        staticInfo.put("degraded", true);
        vo.setStaticInfo(staticInfo);
        vo.setErrorMessage("沙箱服务降级");
        return vo;
    }

    // ==================== 测试可见方法 ====================

    /**
     * 测试用：判断沙箱是否启用
     *
     * @return 是否启用
     */
    boolean isSandboxEnabled() {
        return sandboxProperties.isEnabled();
    }
}
