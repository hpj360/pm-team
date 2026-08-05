package com.redteam.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 自然语言搜索请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "自然语言搜索请求")
public class NlSearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 自然语言查询语句
     */
    @Schema(description = "自然语言查询语句", example = "查找所有包含 APT28 相关 IP 的 PDF 文件")
    private String query;
}
