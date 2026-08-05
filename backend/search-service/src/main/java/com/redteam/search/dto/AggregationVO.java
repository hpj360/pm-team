package com.redteam.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 聚合结果 VO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "聚合结果")
public class AggregationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 聚合名称
     */
    @Schema(description = "聚合名称")
    private String name;

    /**
     * 聚合桶列表
     */
    @Schema(description = "聚合桶列表")
    private List<Bucket> buckets = new ArrayList<>();

    /**
     * 聚合桶
     *
     * @author 红方团队
     */
    @Data
    public static class Bucket implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 桶 key
         */
        @Schema(description = "桶 key")
        private String key;

        /**
         * 桶中文档数量
         */
        @Schema(description = "桶中文档数量")
        private Long count;

        /**
         * 构造桶
         *
         * @param key   key
         * @param count 数量
         */
        public Bucket(String key, Long count) {
            this.key = key;
            this.count = count;
        }

        /**
         * 默认构造方法
         */
        public Bucket() {
        }
    }
}
