package com.redteam.analyze.service;

import com.redteam.common.api.dto.AnalyzeResultDTO;
import com.redteam.common.api.dto.FileAnalyzeDTO;

import java.util.List;

/**
 * 文件分析服务接口
 *
 * @author 红方团队
 */
public interface FileAnalyzeService {

    /**
     * 分析文件
     *
     * @param analyzeDTO 分析请求
     * @return 分析结果
     */
    AnalyzeResultDTO analyze(FileAnalyzeDTO analyzeDTO);

    /**
     * 异步分析文件
     *
     * @param analyzeDTO 分析请求
     * @return 任务ID
     */
    Long analyzeAsync(FileAnalyzeDTO analyzeDTO);

    /**
     * 获取分析结果
     *
     * @param taskId 任务ID
     * @return 分析结果
     */
    AnalyzeResultDTO getAnalyzeResult(Long taskId);

    /**
     * 提取敏感信息
     *
     * @param text 文本内容
     * @return 敏感信息列表
     */
    List<AnalyzeResultDTO.SensitiveInfo> extractSensitiveInfo(String text);

    /**
     * 提取关键词
     *
     * @param text 文本内容
     * @param topN 返回数量
     * @return 关键词列表
     */
    List<AnalyzeResultDTO.KeywordInfo> extractKeywords(String text, Integer topN);

    /**
     * 实体识别
     *
     * @param text 文本内容
     * @return 实体列表
     */
    List<AnalyzeResultDTO.EntityInfo> recognizeEntities(String text);

    /**
     * 情感分析
     *
     * @param text 文本内容
     * @return 情感分析结果
     */
    AnalyzeResultDTO.SentimentInfo analyzeSentiment(String text);

    /**
     * 生成文本摘要
     *
     * @param text   文本内容
     * @param length 摘要长度
     * @return 摘要
     */
    String generateSummary(String text, Integer length);

    /**
     * 生成向量嵌入
     *
     * @param text 文本内容
     * @return 向量ID
     */
    String generateEmbedding(String text);

    /**
     * 批量生成向量嵌入
     *
     * @param texts 文本列表
     * @return 向量ID列表
     */
    List<String> batchGenerateEmbedding(List<String> texts);
}
