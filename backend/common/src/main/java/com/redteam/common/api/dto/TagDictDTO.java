package com.redteam.common.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 标签字典创建/更新 DTO
 *
 * <p>字段与 {@code TagDictEntity} 对应，但不含 id/createdAt/updatedAt，
 * 创建与更新时由后端统一处理时间戳与主键。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "标签字典创建/更新请求")
public class TagDictDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签编码，格式：层级.分类.名称.值
     */
    @Schema(description = "标签编码，格式：层级.分类.名称.值，如 L1.FILE.TYPE.PDF")
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
    @Schema(description = "分类：FILE/BUSINESS/ENTITY/SCENE/INTEL/COMPLIANCE")
    private String category;

    /**
     * 值类型：ENUM/TEXT/NUMBER/BOOL/DATE
     */
    @Schema(description = "值类型：ENUM/TEXT/NUMBER/BOOL/DATE")
    private String valueType;

    /**
     * 适用对象：FILE/ENTITY/TARGET/TASK/ALL
     */
    @Schema(description = "适用对象：FILE/ENTITY/TARGET/TASK/ALL")
    private String applicableObject;

    /**
     * 识别规则描述（正则/字典/模型/关联）
     */
    @Schema(description = "识别规则描述（正则/字典/模型/关联）")
    private String identifyRule;

    /**
     * 是否多选：0单选 1多选
     */
    @Schema(description = "是否多选：0单选 1多选")
    private Integer isMulti;

    /**
     * 父标签编码
     */
    @Schema(description = "父标签编码")
    private String parentCode;

    /**
     * 启用：0禁用 1启用
     */
    @Schema(description = "启用：0禁用 1启用")
    private Integer enabled;

    /**
     * 口径定义
     */
    @Schema(description = "口径定义")
    private String description;
}
