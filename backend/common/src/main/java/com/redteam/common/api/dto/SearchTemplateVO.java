package com.redteam.common.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 搜索模板展示 VO
 *
 * <p>返回给前端的搜索模板视图对象，不包含 user_id 等敏感字段。</p>
 *
 * @author 红方团队
 */
@Data
public class SearchTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模板ID
     */
    private Long id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 搜索条件JSON
     */
    private String paramsJson;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
