package com.redteam.analyze.dynamic;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 动态分析任务（内存模型）
 *
 * <p>状态机：{@code PENDING → SUBMITTED → RUNNING → COMPLETED → PARSED}</p>
 *
 * <p>当前版本采用内存 ConcurrentHashMap 存储（与 V2.5 SandboxService 简化风格一致），
 * 后续可平滑替换为基于 t_dynamic_analysis_task 的持久化实现。</p>
 *
 * @author 红方团队
 */
@Data
public class DynamicAnalysisTask {

    /**
     * 任务状态：待处理
     */
    public static final String STATUS_PENDING = "PENDING";

    /**
     * 任务状态：已提交到 Cuckoo
     */
    public static final String STATUS_SUBMITTED = "SUBMITTED";

    /**
     * 任务状态：沙箱运行中
     */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * 任务状态：沙箱已完成
     */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * 任务状态：行为指标已解析
     */
    public static final String STATUS_PARSED = "PARSED";

    /**
     * 任务状态：失败
     */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 任务状态：降级
     */
    public static final String STATUS_DEGRADED = "DEGRADED";

    /**
     * 平台侧动态分析任务ID（业务主键）
     */
    private String taskId;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * Cuckoo 沙箱返回的任务ID（降级时为 {@code degraded-} 前缀）
     */
    private String cuckooTaskId;

    /**
     * 当前状态
     */
    private String status;

    /**
     * Cuckoo 报告原始 JSON
     */
    private String rawReport;

    /**
     * 行为指标解析结果（由 BehaviorIndicatorExtractor 产出）
     */
    private Map<String, Object> indicators;

    /**
     * 进程树节点（解析后填充）
     */
    private List<Map<String, Object>> processTree = new ArrayList<>();

    /**
     * 网络连接列表（解析后填充）
     */
    private List<Map<String, Object>> networkConnections = new ArrayList<>();

    /**
     * 文件操作列表（解析后填充）
     */
    private List<Map<String, Object>> fileOperations = new ArrayList<>();

    /**
     * ATT&CK 技术映射列表（解析后填充，元素形如 {techniqueId, tactic, description}）
     */
    private List<Map<String, Object>> attackTechniques = new ArrayList<>();

    /**
     * 提取的 IOC 列表（联合静态分析）
     */
    private List<Map<String, Object>> iocs = new ArrayList<>();

    /**
     * 是否降级
     */
    private boolean degraded;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 解析完成时间
     */
    private LocalDateTime parsedTime;
}
