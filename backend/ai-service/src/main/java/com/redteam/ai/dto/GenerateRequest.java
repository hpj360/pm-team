package com.redteam.ai.dto;

import com.redteam.common.api.dto.NerEntityVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 威胁摘要生成请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "威胁摘要生成请求")
public class GenerateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件文本内容
     */
    @Schema(description = "文件文本内容（将截断到前 4000 字符）")
    private String textContent;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String fileName;

    /**
     * 文件类型
     */
    @Schema(description = "文件类型")
    private String fileType;

    /**
     * NER 实体列表
     */
    @Schema(description = "NER 实体列表")
    private List<NerEntityVO> nerEntities;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表")
    private List<String> tags;
}
