package com.redteam.analyze.dynamic;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.analyze.config.CuckooProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cuckoo Sandbox REST API 客户端
 *
 * <p>封装 Cuckoo 沙箱三类核心 API：</p>
 * <ul>
 *   <li>{@link #submitFile(Long)}：POST /tasks/create/file，提交文件并返回 taskId</li>
 *   <li>{@link #getTaskStatus(String)}：GET /tasks/view/{taskId}，查询任务状态</li>
 *   <li>{@link #getReport(String)}：GET /tasks/report/{taskId}/json，获取完整 JSON 报告</li>
 * </ul>
 *
 * <p>降级策略：配置禁用 / 网络异常 / 响应解析失败 → 返回 {@code degraded} 标识的状态对象，
 * 不抛异常，由上层 {@link DynamicAnalysisService} 决定后续编排。</p>
 *
 * <p>测试可见构造器允许注入自定义 {@link RestClient}，便于单测 mock。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class CuckooClient {

    /**
     * 降级任务ID前缀（与 SandboxServiceImpl 风格一致）
     */
    public static final String DEGRADED_PREFIX = "degraded-";

    /**
     * Cuckoo 状态：已完成
     */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * Cuckoo 状态：运行中
     */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * Cuckoo 状态：待处理
     */
    public static final String STATUS_PENDING = "PENDING";

    /**
     * Cuckoo 状态：已提交
     */
    public static final String STATUS_SUBMITTED = "SUBMITTED";

    /**
     * Cuckoo 状态：降级
     */
    public static final String STATUS_DEGRADED = "DEGRADED";

    private final CuckooProperties properties;

    /**
     * HTTP 客户端（懒加载，测试可注入）
     */
    private RestClient restClient;

    /**
     * 生产构造器：根据配置创建 RestClient
     *
     * @param properties Cuckoo 配置
     */
    public CuckooClient(CuckooProperties properties) {
        this.properties = properties;
    }

    /**
     * 测试构造器：注入自定义 RestClient（便于 mock）
     *
     * @param properties  Cuckoo 配置
     * @param restClient  自定义 RestClient
     */
    CuckooClient(CuckooProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * 初始化 HTTP 客户端（生产环境）
     */
    @PostConstruct
    public void init() {
        if (restClient != null) {
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getEndpoint())
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json");
        if (StrUtil.isNotBlank(properties.getApikey())) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApikey());
        }
        this.restClient = builder.build();
        log.info("CuckooClient 初始化: endpoint={}, enabled={}", properties.getEndpoint(), properties.isEnabled());
    }

    /**
     * 是否启用 Cuckoo
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 提交文件到 Cuckoo 沙箱
     *
     * @param fileId 文件ID
     * @return Cuckoo 任务ID（降级时返回 {@code degraded-} 前缀标识）
     */
    public String submitFile(Long fileId) {
        if (fileId == null) {
            return DEGRADED_PREFIX + "null";
        }
        if (!properties.isEnabled()) {
            log.warn("Cuckoo 未启用，返回降级 taskId: fileId={}", fileId);
            return DEGRADED_PREFIX + fileId;
        }
        try {
            return submitViaHttp(fileId);
        } catch (Exception e) {
            log.error("Cuckoo 提交失败，降级处理: fileId={}", fileId, e);
            return DEGRADED_PREFIX + fileId;
        }
    }

    /**
     * 获取任务状态
     *
     * @param taskId Cuckoo 任务ID
     * @return 状态字符串（PENDING/SUBMITTED/RUNNING/COMPLETED/DEGRADED）
     */
    public String getTaskStatus(String taskId) {
        if (isDegraded(taskId)) {
            return STATUS_DEGRADED;
        }
        if (!properties.isEnabled()) {
            return STATUS_DEGRADED;
        }
        try {
            return fetchStatusViaHttp(taskId);
        } catch (Exception e) {
            log.error("Cuckoo 获取状态失败，降级处理: taskId={}", taskId, e);
            return STATUS_DEGRADED;
        }
    }

    /**
     * 获取完整 JSON 报告
     *
     * @param taskId Cuckoo 任务ID
     * @return 报告 JSON 字符串（降级时返回包含 degraded 标记的 JSON）
     */
    public String getReport(String taskId) {
        if (isDegraded(taskId)) {
            return buildDegradedReportJson(taskId);
        }
        if (!properties.isEnabled()) {
            return buildDegradedReportJson(taskId);
        }
        try {
            return fetchReportViaHttp(taskId);
        } catch (Exception e) {
            log.error("Cuckoo 获取报告失败，降级处理: taskId={}", taskId, e);
            return buildDegradedReportJson(taskId);
        }
    }

    // ==================== HTTP 调用 ====================

    /**
     * 通过 HTTP 提交文件
     */
    private String submitViaHttp(Long fileId) {
        JSONObject body = new JSONObject();
        body.set("fileId", fileId);
        String resp = restClient.post()
                .uri("/tasks/create/file")
                .body(body.toString())
                .retrieve()
                .body(String.class);
        JSONObject json = JSONUtil.parseObj(resp);
        String taskId = json.getStr("taskId");
        if (StrUtil.isBlank(taskId)) {
            taskId = json.getStr("task_id");
        }
        if (StrUtil.isBlank(taskId)) {
            taskId = json.getStr("id");
        }
        if (StrUtil.isBlank(taskId)) {
            throw new IllegalStateException("Cuckoo 返回的 taskId 为空: " + resp);
        }
        log.info("Cuckoo 提交成功: fileId={}, taskId={}", fileId, taskId);
        return taskId;
    }

    /**
     * 通过 HTTP 获取状态
     */
    private String fetchStatusViaHttp(String taskId) {
        String resp = restClient.get()
                .uri("/tasks/view/" + taskId)
                .retrieve()
                .body(String.class);
        JSONObject json = JSONUtil.parseObj(resp);
        // 兼容多字段
        String status = json.getStr("status");
        if (StrUtil.isBlank(status)) {
            status = json.getByPath("task.status", String.class);
        }
        return StrUtil.blankToDefault(status, STATUS_PENDING);
    }

    /**
     * 通过 HTTP 获取报告
     */
    private String fetchReportViaHttp(String taskId) {
        return restClient.get()
                .uri("/tasks/report/" + taskId + "/json")
                .retrieve()
                .body(String.class);
    }

    // ==================== 降级 ====================

    /**
     * 判断是否为降级 taskId
     *
     * @param taskId 任务ID
     * @return 是否降级
     */
    public boolean isDegraded(String taskId) {
        return taskId != null && taskId.startsWith(DEGRADED_PREFIX);
    }

    /**
     * 构建降级报告 JSON
     *
     * @param taskId 任务ID
     * @return 降级报告 JSON 字符串
     */
    public String buildDegradedReportJson(String taskId) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("taskId", taskId);
        report.put("status", STATUS_DEGRADED);
        report.put("degraded", true);
        report.put("score", 0.0);
        report.put("summary", "Cuckoo 沙箱不可用，已降级为基础静态分析结果");
        report.put("error", "Cuckoo service degraded");
        return JSONUtil.toJsonStr(report);
    }
}
