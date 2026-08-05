package com.redteam.common.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标签树形结构 VO
 *
 * <p>用于按父子层级（parent_code）组装的标签树展示，
 * children 为子标签列表，递归构建。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "标签树形结构")
public class TagTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    @Schema(description = "标签ID")
    private Long id;

    /**
     * 标签编码
     */
    @Schema(description = "标签编码")
    private String tagCode;

    /**
     * 标签中文名
     */
    @Schema(description = "标签中文名")
    private String tagName;

    /**
     * 层级：L1-L6
     */
    @Schema(description = "层级：L1-L6")
    private String layer;

    /**
     * 分类：FILE/BUSINESS/ENTITY/SCENE/INTEL/COMPLIANCE
     */
    @Schema(description = "分类")
    private String category;

    /**
     * 启用：0禁用 1启用
     */
    @Schema(description = "启用：0禁用 1启用")
    private Integer enabled;

    /**
     * 子标签列表
     */
    @Schema(description = "子标签列表")
    private List<TagTreeVO> children;
}
