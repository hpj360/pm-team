package com.redteam.analyze.service;

import com.redteam.analyze.dto.SandboxReportVO;

/**
 * 沙箱分析服务接口
 *
 * <p>封装文件提交沙箱、获取报告与状态的能力，沙箱不可用时自动降级到基础静态分析。</p>
 *
 * @author 红方团队
 */
public interface SandboxService {

    /**
     * 提交文件到沙箱进行分析
     *
     * <p>沙箱不可用时返回降级结果（基于文件元信息的基础静态分析）。</p>
     *
     * @param fileId 文件ID
     * @return 沙箱任务ID（降级时返回降级标识）
     */
    String submitToSandbox(Long fileId);

    /**
     * 获取沙箱分析报告
     *
     * @param taskId 沙箱任务ID
     * @return 沙箱报告 VO
     */
    SandboxReportVO getSandboxReport(String taskId);

    /**
     * 获取沙箱分析状态
     *
     * @param taskId 沙箱任务ID
     * @return 状态字符串（PENDING/RUNNING/COMPLETED/FAILED/DEGRADED）
     */
    String getSandboxStatus(String taskId);
}
