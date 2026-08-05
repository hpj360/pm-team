package com.redteam.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 检索结果 VO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "检索结果")
public class SearchResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 总命中数
     */
    @Schema(description = "总命中数")
    private Long total;

    /**
     * 页码
     */
    @Schema(description = "页码")
    private Integer pageNum;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小")
    private Integer pageSize;

    /**
     * 命中列表
     */
    @Schema(description = "命中列表")
    private List<SearchHitVO> hits;

    /**
     * 聚合结果（key=聚合名称，value=聚合内容）
     */
    @Schema(description = "聚合结果")
    private Map<String, Object> aggregations;

    /**
     * 检索耗时（毫秒）
     */
    @Schema(description = "检索耗时（毫秒）")
    private Long responseTimeMs;

    /**
     * 构造空结果
     *
     * @param request 检索请求
     * @return 空结果
     */
    public static SearchResultVO empty(SearchRequestDTO request) {
        SearchResultVO vo = new SearchResultVO();
        vo.setTotal(0L);
        vo.setPageNum(request == null ? 1 : request.getPageNum());
        vo.setPageSize(request == null ? 10 : request.getPageSize());
        vo.setHits(List.of());
        vo.setResponseTimeMs(0L);
        return vo;
    }
}
