package com.redteam.parse.service;

import com.redteam.parse.dto.ParseResultDTO;

import java.io.InputStream;

/**
 * 文件解析器接口
 *
 * @author 红方团队
 */
public interface FileParser {

    /**
     * 获取支持的文件类型
     *
     * @return 文件类型列表
     */
    String[] getSupportedTypes();

    /**
     * 解析文件
     *
     * @param inputStream 文件输入流
     * @param filename    文件名
     * @return 解析结果
     */
    ParseResultDTO parse(InputStream inputStream, String filename);

    /**
     * 判断是否支持该文件类型
     *
     * @param fileType 文件类型
     * @return 是否支持
     */
    default boolean supports(String fileType) {
        if (fileType == null) {
            return false;
        }
        String[] supportedTypes = getSupportedTypes();
        for (String type : supportedTypes) {
            if (type.equalsIgnoreCase(fileType)) {
                return true;
            }
        }
        return false;
    }
}
