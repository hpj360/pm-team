package com.redteam.parse.service;

import com.redteam.parse.dto.ParseResultDTO;

/**
 * 文件解析服务接口
 *
 * @author 红方团队
 */
public interface FileParseService {

    /**
     * 解析文件
     *
     * @param fileId   文件ID
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
}
