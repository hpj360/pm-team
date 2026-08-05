package com.redteam.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索服务统一配置属性
 *
 * <p>对应配置前缀 {@code redteam.search}，聚合 ES / Milvus / 向量化 / 混合检索 / 缓存 五大子配置。</p>
 *
 * @author 红方团队
 */
@Data
@Component
@ConfigurationProperties(prefix = "redteam.search")
public class SearchProperties {

    /**
     * Elasticsearch 相关配置
     */
    private Es es = new Es();

    /**
     * Milvus 向量库相关配置
     */
    private Milvus milvus = new Milvus();

    /**
     * 向量化服务相关配置
     */
    private Embedding embedding = new Embedding();

    /**
     * 混合检索相关配置
     */
    private Hybrid hybrid = new Hybrid();

    /**
     * 缓存相关配置
     */
    private Cache cache = new Cache();

    /**
     * Elasticsearch 子配置
     *
     * @author 红方团队
     */
    @Data
    public static class Es {
        /**
         * 索引名称
         */
        private String indexName = "redhead-files";

        /**
         * 分片数
         */
        private Integer shards = 3;

        /**
         * 副本数
         */
        private Integer replicas = 1;
    }

    /**
     * Milvus 子配置
     *
     * @author 红方团队
     */
    @Data
    public static class Milvus {
        /**
         * Milvus 主机地址
         */
        private String host = "localhost";

        /**
         * Milvus 端口
         */
        private Integer port = 19530;

        /**
         * Collection 名称
         */
        private String collectionName = "file_vectors";

        /**
         * 向量维度
         */
        private Integer vectorDim = 768;

        /**
         * 索引类型（IVF_FLAT / IVF_SQ8 / HNSW）
         */
        private String indexType = "IVF_FLAT";

        /**
         * 度量类型（COSINE / L2 / IP）
         */
        private String metricType = "COSINE";

        /**
         * IVF nlist 参数
         */
        private Integer nlist = 1024;

        /**
         * 检索 nprobe 参数
         */
        private Integer nprobe = 16;
    }

    /**
     * 向量化子配置
     *
     * @author 红方团队
     */
    @Data
    public static class Embedding {
        /**
         * 外部向量化服务 API 地址
         */
        private String apiUrl = "http://localhost:8081/embed";

        /**
         * 调用超时时间（秒）
         */
        private Integer timeoutSeconds = 10;

        /**
         * 是否启用向量缓存
         */
        private Boolean cacheEnabled = true;
    }

    /**
     * 混合检索子配置
     *
     * @author 红方团队
     */
    @Data
    public static class Hybrid {
        /**
         * RRF 融合参数 k
         */
        private Integer rrfK = 60;

        /**
         * 每路检索取 top K
         */
        private Integer topKPerSource = 50;
    }

    /**
     * 缓存子配置
     *
     * @author 红方团队
     */
    @Data
    public static class Cache {
        /**
         * 缓存过期时间（秒）
         */
        private Integer ttlSeconds = 300;
    }
}
