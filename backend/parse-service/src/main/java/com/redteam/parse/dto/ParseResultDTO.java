package com.redteam.parse.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 文件解析结果DTO
 *
 * @author 红方团队
 */
@Data
public class ParseResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否解析成功
     */
    private Boolean success;

    /**
     * 提取的文本内容
     */
    private String textContent;

    /**
     * 文本内容长度
     */
    private Integer textLength;

    /**
     * 页数（适用于PDF、Word等）
     */
    private Integer pageCount;

    /**
     * 标题
     */
    private String title;

    /**
     * 作者
     */
    private String author;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 关键词列表
     */
    private List<String> keywords;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 图片列表（图片路径或Base64）
     */
    private List<String> images;

    /**
     * 表格数据
     */
    private List<List<List<String>>> tables;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 解析耗时（毫秒）
     */
    private Long duration;

    /**
     * 创建成功结果
     *
     * @param textContent 文本内容
     * @return 解析结果
     */
    public static ParseResultDTO success(String textContent) {
        ParseResultDTO result = new ParseResultDTO();
        result.setSuccess(true);
        result.setTextContent(textContent);
        if (textContent != null) {
            result.setTextLength(textContent.length());
        }
        return result;
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误信息
     * @return 解析结果
     */
    public static ParseResultDTO fail(String errorMessage) {
        ParseResultDTO result = new ParseResultDTO();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
