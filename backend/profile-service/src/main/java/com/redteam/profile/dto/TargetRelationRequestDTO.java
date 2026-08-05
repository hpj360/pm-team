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
 * 目标关系创建请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "目标关系创建请求")
public class TargetRelationRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 源目标ID
     */
    @Schema(description = "源目标ID", example = "1001")
    @NotNull(message = "源目标ID不能为空")
    private Long sourceId;

    /**
     * 目标目标ID
     */
    @Schema(description = "目标目标ID", example = "1002")
    @NotNull(message = "目标目标ID不能为空")
    private Long targetId;

    /**
     * 关系类型（AFFILIATED/SUBDOMAIN/RESOLVES_TO/RELATED/OWNS 等）
     */
    @Schema(description = "关系类型", example = "RELATED")
    @NotBlank(message = "关系类型不能为空")
    @Size(max = 32, message = "关系类型长度不能超过32")
    private String relationType;

    /**
     * 关系权重（0.0-1.0）
     */
    @Schema(description = "关系权重", example = "0.8")
    @Min(value = 0, message = "权重不能小于0")
    @Max(value = 1, message = "权重不能大于1")
    private Double weight;

    /**
     * 关系描述
     */
    @Schema(description = "关系描述")
    @Size(max = 256, message = "关系描述长度不能超过256")
    private String description;
}
