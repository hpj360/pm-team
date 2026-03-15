package com.redteam.common.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文件检索请求DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "文件检索请求")
public class FileSearchDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 搜索关键词
     */
    @Schema(description = "搜索关键词")
    private String keyword;

    /**
     * 文件名（模糊匹配）
     */
    @Schema(description = "文件名（模糊匹配）")
    private String filename;

    /**
     * 文件类型列表
     */
    @Schema(description = "文件类型列表")
    private List<String> fileTypes;

    /**
     * 文件大小最小值（字节）
     */
    @Schema(description = "文件大小最小值（字节）")
    private Long fileSizeMin;

    /**
     * 文件大小最大值（字节）
     */
    @Schema(description = "文件大小最大值（字节）")
    private Long fileSizeMax;

    /**
     * 来源类型列表
     */
    @Schema(description = "来源类型列表")
    private List<Integer> sourceTypes;

    /**
     * 目标ID列表
     */
    @Schema(description = "目标ID列表")
    private List<Long> targetIds;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表")
    private List<String> tags;

    /**
     * 敏感等级列表
     */
    @Schema(description = "敏感等级列表")
    private List<Integer> sensitiveLevels;

    /**
     * 解析状态列表
     */
    @Schema(description = "解析状态列表")
    private List<Integer> parseStatuses;

    /**
     * 索引状态列表
     */
    @Schema(description = "索引状态列表")
    private List<Integer> indexStatuses;

    /**
     * 创建时间开始
     */
    @Schema(description = "创建时间开始")
    private String createTimeStart;

    /**
     * 创建时间结束
     */
    @Schema(description = "创建时间结束")
    private String createTimeEnd;

    /**
     * 是否公开
     */
    @Schema(description = "是否公开")
    private Integer isPublic;

    /**
     * 是否使用语义搜索
     */
    @Schema(description = "是否使用语义搜索")
    private Boolean semanticSearch;

    /**
     * 向量相似度阈值（0-1）
     */
    @Schema(description = "向量相似度阈值（0-1）")
    private Double similarityThreshold;

    /**
     * 当前页码
     */
    @Schema(description = "当前页码", defaultValue = "1")
    private Integer current = 1;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", defaultValue = "10")
    private Integer size = 10;

    /**
     * 排序字段
     */
    @Schema(description = "排序字段")
    private String sortField;

    /**
     * 排序方向（asc/desc）
     */
    @Schema(description = "排序方向（asc/desc）")
    private String sortOrder;
}
