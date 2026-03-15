package com.redteam.search.service;

import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.api.dto.FileSearchDTO;
import com.redteam.common.result.PageResult;

import java.util.List;

/**
 * 文件检索服务接口
 *
 * @author 红方团队
 */
public interface FileSearchService {

    /**
     * 全文检索
     *
     * @param searchDTO 检索条件
     * @return 检索结果
     */
    PageResult<FileInfoDTO> search(FileSearchDTO searchDTO);

    /**
     * 语义搜索（向量检索）
     *
     * @param query              查询文本
     * @param similarityThreshold 相似度阈值
     * @param size               返回数量
     * @return 检索结果
     */
    List<FileInfoDTO> semanticSearch(String query, Double similarityThreshold, Integer size);

    /**
     * 高亮检索
     *
     * @param keyword   关键词
     * @param current   当前页
     * @param size      每页大小
     * @return 检索结果
     */
    PageResult<FileInfoDTO> searchWithHighlight(String keyword, Integer current, Integer size);

    /**
     * 索引文件
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    boolean indexFile(Long fileId);

    /**
     * 批量索引文件
     *
     * @param fileIds 文件ID列表
     * @return 是否成功
     */
    boolean batchIndexFiles(List<Long> fileIds);

    /**
     * 删除索引
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    boolean deleteIndex(Long fileId);

    /**
     * 更新索引
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    boolean updateIndex(Long fileId);

    /**
     * 获取搜索建议
     *
     * @param prefix 前缀
     * @param size   返回数量
     * @return 建议列表
     */
    List<String> getSuggestions(String prefix, Integer size);

    /**
     * 聚合统计
     *
     * @param field 聚合字段
     * @return 聚合结果
     */
    Object aggregate(String field);
}
