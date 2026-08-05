package com.redteam.report.service;

import com.redteam.common.result.PageResult;
import com.redteam.report.dto.ReportGenerateDTO;
import com.redteam.report.dto.ReportQueryDTO;
import com.redteam.report.dto.ReportShareDTO;
import com.redteam.report.dto.ReportStatsDTO;
import com.redteam.report.dto.ReportTemplateVO;
import com.redteam.report.dto.ReportVO;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * 报告生成服务接口
 *
 * <p>提供报告的异步生成、查询、删除、下载、共享、版本管理、重试、统计及模板列表能力。
 * 生成流程：先入库为 PENDING → 通过 {@code @Async} 线程池异步渲染 → 完成后更新为 COMPLETED/FAILED。</p>
 *
 * @author 红方团队
 */
public interface ReportService {

    /**
     * 异步生成报告
     *
     * <p>立即返回 {@link ReportVO}（状态为 PENDING），实际生成在 {@code reportTaskExecutor} 线程池中异步执行。
     * 调用方可通过轮询 {@link #getReport(String)} 获取最终状态。</p>
     *
     * @param dto 报告生成请求
     * @return 报告基础信息（含 reportId，状态为 PENDING）
     */
    ReportVO generateReport(ReportGenerateDTO dto);

    /**
     * 根据报告ID获取报告详情
     *
     * @param reportId 报告ID
     * @return 报告信息
     */
    ReportVO getReport(String reportId);

    /**
     * 分页查询报告列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ReportVO> listReports(ReportQueryDTO query);

    /**
     * 删除报告（逻辑删除 + 物理文件清理）
     *
     * @param reportId 报告ID
     */
    void deleteReport(String reportId);

    /**
     * 下载报告文件
     *
     * @param reportId 报告ID
     * @return 可下载的文件资源
     */
    Resource downloadReport(String reportId);

    /**
     * 获取所有可用报告模板列表
     *
     * @return 模板列表
     */
    List<ReportTemplateVO> listTemplates();

    /**
     * 共享报告给指定用户列表
     *
     * @param reportId 报告ID
     * @param dto      共享请求（含目标用户ID列表）
     * @return 更新后的报告信息
     */
    ReportVO shareReport(String reportId, ReportShareDTO dto);

    /**
     * 取消报告共享
     *
     * @param reportId 报告ID
     * @return 更新后的报告信息
     */
    ReportVO unshareReport(String reportId);

    /**
     * 重新生成报告（版本号递增）
     *
     * <p>仅允许对已存在报告进行重新生成；新版本的报告会覆盖原文件，版本号自增。</p>
     *
     * @param reportId 报告ID
     * @return 报告基础信息（状态为 PENDING）
     */
    ReportVO regenerateReport(String reportId);

    /**
     * 重试生成失败的报告
     *
     * <p>仅当报告状态为 FAILED 时允许重试，重置重试次数与状态。</p>
     *
     * @param reportId 报告ID
     * @return 报告基础信息（状态为 PENDING）
     */
    ReportVO retryFailed(String reportId);

    /**
     * 报告统计（按状态/类型/格式分组）
     *
     * @return 统计结果
     */
    ReportStatsDTO getReportStats();
}
