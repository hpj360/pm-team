package com.redteam.search.service;

import java.util.List;

/**
 * 向量化服务接口
 *
 * <p>将文本转换为 768 维浮点向量，供 Milvus 向量检索使用。</p>
 *
 * @author 红方团队
 */
public interface VectorEmbeddingService {

    /**
     * 文本向量化
     *
     * @param text 原始文本
     * @return 768 维浮点向量
     */
    List<Float> embed(String text);

    /**
     * 批量文本向量化
     *
     * @param texts 文本列表
     * @return 向量列表（顺序与入参一致）
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 向量维度
     *
     * @return 维度
     */
    int dimension();
}
