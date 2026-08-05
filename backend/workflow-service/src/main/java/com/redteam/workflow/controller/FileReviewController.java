package com.redteam.workflow.controller;

import com.redteam.common.entity.WorkflowInstanceEntity;
import com.redteam.common.result.Result;
import com.redteam.common.util.UserContext;
import com.redteam.workflow.dto.SubmitFileReviewDTO;
import com.redteam.workflow.dto.WorkflowInstanceVO;
import com.redteam.workflow.dto.ReviewDecisionDTO;
import com.redteam.workflow.service.FileReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件评审 REST 接口
 *
 * <p>提供文件评审的提交、评审决定、状态查询、待评审列表等接口，
 * 统一前缀 {@code /api/workflow/file-review}。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/file-review")
@RequiredArgsConstructor
@Tag(name = "文件评审", description = "文件评审流程接口：提交/决定/查询")
public class FileReviewController {

    private final FileReviewService fileReviewService;

    /**
     * 提交文件评审
     *
     * <p>若 DTO 中未指定提交人，则从 {@link UserContext} 获取当前登录用户作为提交人。</p>
     *
     * @param dto 提交文件评审请求
     * @return 已创建的工作流实例
     */
    @PostMapping("/submit")
    @Operation(summary = "提交文件评审")
    public Result<WorkflowInstanceEntity> submit(@RequestBody SubmitFileReviewDTO dto) {
        log.info("提交文件评审: fileId={}, submitterId={}", dto.getFileId(), dto.getSubmitterId());
        Long submitterId = dto.getSubmitterId() != null ? dto.getSubmitterId() : UserContext.getUserId();
        String submitterName = dto.getSubmitterName() != null ? dto.getSubmitterName() : UserContext.getUsername();
        WorkflowInstanceEntity instance = fileReviewService.submitReview(
                dto.getFileId(), submitterId, submitterName, dto.getComment());
        return Result.success(instance);
    }

    /**
     * 评审决定
     *
     * <p>审批人信息从 {@link UserContext} 获取当前登录用户。</p>
     *
     * @param instanceId 实例ID
     * @param dto        评审决定请求
     * @return 更新后的工作流实例
     */
    @PostMapping("/{instanceId}/decide")
    @Operation(summary = "评审决定")
    public Result<WorkflowInstanceEntity> decide(
            @Parameter(description = "实例ID", required = true) @PathVariable Long instanceId,
            @RequestBody ReviewDecisionDTO dto) {
        log.info("评审决定: instanceId={}, decision={}", instanceId, dto.getDecision());
        Long reviewerId = UserContext.getUserId();
        String reviewerName = UserContext.getUsername();
        WorkflowInstanceEntity instance = fileReviewService.processReview(
                instanceId, reviewerId, reviewerName, dto.getDecision(), dto.getComment());
        return Result.success(instance);
    }

    /**
     * 获取文件评审状态
     *
     * @param fileId 文件ID
     * @return 评审实例详情 VO
     */
    @GetMapping("/files/{fileId}")
    @Operation(summary = "获取文件评审状态")
    public Result<WorkflowInstanceVO> getStatus(
            @Parameter(description = "文件ID", required = true) @PathVariable Long fileId) {
        log.info("获取文件评审状态: fileId={}", fileId);
        WorkflowInstanceVO vo = fileReviewService.getFileReviewStatus(fileId);
        return Result.success(vo);
    }

    /**
     * 待评审列表
     *
     * @param reviewerId 审批人ID
     * @return 待评审实例列表
     */
    @GetMapping("/pending")
    @Operation(summary = "待评审列表")
    public Result<List<WorkflowInstanceVO>> getPending(
            @Parameter(description = "审批人ID", required = true) @RequestParam Long reviewerId) {
        log.info("查询待评审列表: reviewerId={}", reviewerId);
        List<WorkflowInstanceVO> list = fileReviewService.getPendingReviews(reviewerId);
        return Result.success(list);
    }
}
