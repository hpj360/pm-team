package com.redteam.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 报告共享请求 DTO
 *
 * <p>用于 {@code POST /api/v1/reports/{reportId}/share} 接口的请求体，
 * 将指定报告共享给一组用户。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "报告共享请求")
public class ReportShareDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 共享给的用户ID列表
     */
    @NotEmpty(message = "共享用户列表不能为空")
    @Schema(description = "共享给的用户ID列表", example = "[1001, 1002]")
    private List<Long> userIds;
}
