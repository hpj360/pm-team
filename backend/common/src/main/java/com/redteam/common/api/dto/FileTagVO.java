package com.redteam.common.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件标签 VO
 *
 * <p>文件标签关联查询的展示对象，融合了 file_tags 关联字段与 tag_dict_v2 字典字段，
 * 便于前端一次性渲染文件已打标签的完整信息。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "文件标签信息")
public class FileTagVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @Schema(description = "文件ID")
    private Long fileId;

    /**
     * 标签ID
     */
    @Schema(description = "标签ID")
    private Long tagId;

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
     * 标签来源：AUTO自动 / MANUAL手动
     */
    @Schema(description = "标签来源：AUTO自动/MANUAL手动")
    private String source;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
