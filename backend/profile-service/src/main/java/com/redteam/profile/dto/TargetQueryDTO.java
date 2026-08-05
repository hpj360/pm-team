package com.redteam.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 目标分页查询 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "目标分页查询DTO")
public class TargetQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码，默认 1
     */
    @Schema(description = "当前页码", example = "1")
    @Min(value = 1, message = "页码最小为1")
    private Long pageNum = 1L;

    /**
     * 每页大小，默认 10
     */
    @Schema(description = "每页大小", example = "10")
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 100, message = "每页大小最大为100")
    private Long pageSize = 10L;

    /**
     * 目标类型
     */
    @Schema(description = "目标类型")
    private Integer type;

    /**
     * 所属行业
     */
    @Schema(description = "所属行业")
    private String industry;

    /**
     * 风险等级
     */
    @Schema(description = "风险等级")
    private Integer riskLevel;

    /**
     * 是否关注（0-否，1-是）
     */
    @Schema(description = "是否关注")
    private Integer isFollowed;

    /**
     * 关键词（匹配名称、描述、标签）
     */
    @Schema(description = "关键词")
    private String keyword;
}
