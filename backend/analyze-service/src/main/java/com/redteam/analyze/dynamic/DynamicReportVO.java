package com.redteam.analyze.dynamic;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 动态分析报告 VO（对外返回）
 *
 * <p>聚合 Cuckoo 原始报告摘要 + 行为指标解析结果 + ATT&CK 映射 + IOC 提取。</p>
 *
 * @author 红方团队
 */
@Data
public class DynamicReportVO {

    /**
     * 平台侧动态分析任务ID
     */
    private String taskId;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * Cuckoo 任务ID
     */
    private String cuckooTaskId;

    /**
     * 当前状态
     */
    private String status;

    /**
     * 是否降级
     */
    private boolean degraded;

    /**
     * 威胁评分（0-10，Cuckoo score 转换）
     */
    private Double score;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 进程树节点
     */
    private List<Map<String, Object>> processTree;

    /**
     * 网络连接列表
     */
    private List<Map<String, Object>> networkConnections;

    /**
     * 文件操作列表
     */
    private List<Map<String, Object>> fileOperations;

    /**
     * ATT&CK 技术映射列表
     */
    private List<Map<String, Object>> attackTechniques;

    /**
     * 提取的 IOC 列表（联合静态分析）
     */
    private List<Map<String, Object>> iocs;

    /**
     * STIX 2.1 对象列表（进程 / 网络流量）
     */
    private List<Map<String, Object>> stixObjects;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间（ISO 字符串）
     */
    private String createTime;

    /**
     * 解析完成时间（ISO 字符串）
     */
    private String parsedTime;
}
