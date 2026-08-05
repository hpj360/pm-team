package com.redteam.analyze.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 关键词视图对象
 *
 * @author 红方团队
 */
@Data
@Schema(description = "关键词信息")
public class KeywordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关键词
     */
    @Schema(description = "关键词")
    private String keyword;

    /**
     * 词频
     */
    @Schema(description = "词频")
    private Integer frequency;

    /**
     * 权重（0-1，归一化词频）
     */
    @Schema(description = "权重")
    private Double weight;

    /**
     * 默认构造方法
     */
    public KeywordVO() {
    }

    /**
     * 全参构造方法
     *
     * @param keyword   关键词
     * @param frequency 词频
     * @param weight    权重
     */
    public KeywordVO(String keyword, Integer frequency, Double weight) {
        this.keyword = keyword;
        this.frequency = frequency;
        this.weight = weight;
    }
}
