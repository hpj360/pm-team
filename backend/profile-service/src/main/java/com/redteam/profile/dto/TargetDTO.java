package com.redteam.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 目标创建/更新 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "目标创建/更新DTO")
public class TargetDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标名称
     */
    @Schema(description = "目标名称", example = "目标A")
    @NotBlank(message = "目标名称不能为空")
    @Size(max = 128, message = "目标名称长度不能超过128")
    private String name;

    /**
     * 目标类型（1-个人，2-组织，3-网站，4-IP，5-域名，6-其他）
     */
    @Schema(description = "目标类型", example = "2")
    @NotNull(message = "目标类型不能为空")
    @Min(value = 1, message = "目标类型最小为1")
    @Max(value = 6, message = "目标类型最大为6")
    private Integer type;

    /**
     * 所属行业
     */
    @Schema(description = "所属行业", example = "互联网")
    @Size(max = 64, message = "行业长度不能超过64")
    private String industry;

    /**
     * 目标描述
     */
    @Schema(description = "目标描述")
    @Size(max = 1024, message = "目标描述长度不能超过1024")
    private String description;

    /**
     * 攻击面 JSON
     */
    @Schema(description = "攻击面JSON")
    private String attackSurface;

    /**
     * 技术资产 JSON
     */
    @Schema(description = "技术资产JSON")
    private String techAssets;

    /**
     * 组织架构 JSON
     */
    @Schema(description = "组织架构JSON")
    private String orgStructure;

    /**
     * 标签列表（逗号分隔）
     */
    @Schema(description = "标签列表")
    @Size(max = 256, message = "标签长度不能超过256")
    private String tags;

    /**
     * 风险等级（1-低，2-中，3-高）
     */
    @Schema(description = "风险等级", example = "1")
    @Min(value = 1, message = "风险等级最小为1")
    @Max(value = 3, message = "风险等级最大为3")
    private Integer riskLevel;
}
