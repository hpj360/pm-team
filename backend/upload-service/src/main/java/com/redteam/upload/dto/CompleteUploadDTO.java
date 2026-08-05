package com.redteam.upload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 完成分片上传请求 DTO
 *
 * <p>由前端在调用 /file/multipart/complete 接口时携带，描述所有分片的 ETag。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "完成分片上传请求")
public class CompleteUploadDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上传ID
     */
    @Schema(description = "上传ID")
    private String uploadId;

    /**
     * 分片 ETag 列表
     */
    @Schema(description = "分片 ETag 列表")
    private List<PartETag> parts;

    /**
     * 单个分片 ETag 描述
     *
     * @author 红方团队
     */
    @Data
    @Schema(description = "分片 ETag")
    public static class PartETag implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 分片序号（从 1 开始）
         */
        @Schema(description = "分片序号")
        private Integer partNumber;

        /**
         * 分片 ETag
         */
        @Schema(description = "分片 ETag")
        private String eTag;

        /**
         * 默认构造方法
         */
        public PartETag() {
        }

        /**
         * 全参构造方法
         *
         * @param partNumber 分片序号
         * @param eTag       分片 ETag
         */
        public PartETag(Integer partNumber, String eTag) {
            this.partNumber = partNumber;
            this.eTag = eTag;
        }
    }
}
