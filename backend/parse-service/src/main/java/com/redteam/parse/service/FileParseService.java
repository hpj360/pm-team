package com.redteam.parse.service;

import com.redteam.common.result.PageResult;
import com.redteam.parse.dto.ParseQueryDTO;
import com.redteam.parse.dto.ParseResultDTO;

/**
 * 文件解析服务接口
 *
 * <p>提供同步/异步文件解析、解析结果查询与 YARA/NER 增强扫描能力。</p>
 *
 * @author 红方团队
 */
public interface FileParseService {

    /**
     * 解析文件（通过文件ID）
     *
     * @param fileId 文件ID
     * @return 解析结果
     */
    ParseResultDTO parseFile(Long fileId);

    /**
     * 解析文件（通过存储路径）
     *
     * @param storagePath 存储路径
     * @param filename    文件名
     * @param fileType    文件类型
     * @return 解析结果
     */
    ParseResultDTO parseFile(String storagePath, String filename, String fileType);

    /**
     * 异步解析文件
     *
     * @param fileId 文件ID
     */
    void parseFileAsync(Long fileId);

    /**
     * 异步解析文件（带元数据，由 Kafka 监听器调用）
     *
     * @param fileId      文件ID
     * @param storagePath 存储路径
     * @param fileName    文件名
     * @param fileType    文件类型
     * @param fileSize    文件大小
     */
    void parseFileAsync(Long fileId, String storagePath, String fileName, String fileType, Long fileSize);

    /**
     * 根据文件ID获取解析结果
     *
     * @param fileId 文件ID
     * @return 解析结果
     */
    ParseResultDTO getParseResult(Long fileId);

    /**
     * 分页查询解析结果
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ParseResultDTO> listParseResults(ParseQueryDTO query);
}
