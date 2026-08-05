package com.redteam.search.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 向量检索结果（内部使用）
 *
 * <p>Milvus 检索返回的原始结果，包含 fileId、相似度分数与元数据。
 * 供 {@code HybridSearchService} 进行 RRF 融合时使用。</p>
 *
 * @author 红方团队
 */
@Data
public class VectorSearchResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件 ID
     */
    private Long fileId;

    /**
     * 相似度分数（0-1，越高越相似）
     */
    private Float score;

    /**
     * 元数据（file_name / file_sm3 / target_id / upload_time）
     */
    private Map<String, Object> metadata;

    /**
     * 在结果列表中的排名（从 1 开始，RRF 融合用）
     */
    private int rank;

    /**
     * 构造向量检索结果
     *
     * @param fileId   文件 ID
     * @param score    相似度分数
     * @param metadata 元数据
     */
    public VectorSearchResultDTO(Long fileId, Float score, Map<String, Object> metadata) {
        this.fileId = fileId;
        this.score = score;
        this.metadata = metadata;
    }

    /**
     * 默认构造方法
     */
    public VectorSearchResultDTO() {
    }

    /**
     * 空结果列表
     *
     * @return 空列表
     */
    public static List<VectorSearchResultDTO> emptyList() {
        return List.of();
    }
}
