package com.redteam.parse.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件解析结果DTO
 *
 * <p>保留 v2.1 既有字段（success/textContent/textLength/pageCount/title/author/summary/keywords/
 * metadata/images/tables/errorMessage/duration），新增 YARA 扫描与 NER 实体识别相关字段，
 * 用于红方文件汇聚平台的增强解析能力。</p>
 *
 * @author 红方团队
 */
@Data
public class ParseResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== v2.1 既有字段（保留） ====================

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

    // ==================== v2.3 新增字段（YARA + NER） ====================

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文本内容 SM3 哈希
     */
    private String textHash;

    /**
     * 检测到的语言
     */
    private String language;

    /**
     * 文件编码
     */
    private String encoding;

    /**
     * 解析状态：PENDING/SUCCESS/FAILED
     */
    private String parseStatus;

    /**
     * 解析耗时（毫秒，与 duration 同义，用于持久化字段对齐）
     */
    private Long parseDurationMs;

    /**
     * YARA 匹配结果
     */
    private List<YaraMatchVO> yaraMatches;

    /**
     * NER 实体识别结果
     */
    private List<NerEntityVO> nerEntities;

    /**
     * 解析错误信息（持久化字段，与 errorMessage 同义）
     */
    private String parseError;

    // ==================== 工厂方法 ====================

    /**
     * 创建成功结果
     *
     * @param textContent 文本内容
     * @return 解析结果
     */
    public static ParseResultDTO success(String textContent) {
        ParseResultDTO result = new ParseResultDTO();
        result.setSuccess(true);
        result.setParseStatus("SUCCESS");
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
        result.setParseStatus("FAILED");
        result.setErrorMessage(errorMessage);
        result.setParseError(errorMessage);
        return result;
    }

    /**
     * 添加 YARA 匹配结果（空安全）
     *
     * @param match YARA 匹配
     */
    public void addYaraMatch(YaraMatchVO match) {
        if (match == null) {
            return;
        }
        if (this.yaraMatches == null) {
            this.yaraMatches = new ArrayList<>();
        }
        this.yaraMatches.add(match);
    }

    /**
     * 添加 NER 实体（空安全）
     *
     * @param entity NER 实体
     */
    public void addNerEntity(NerEntityVO entity) {
        if (entity == null) {
            return;
        }
        if (this.nerEntities == null) {
            this.nerEntities = new ArrayList<>();
        }
        this.nerEntities.add(entity);
    }
}
