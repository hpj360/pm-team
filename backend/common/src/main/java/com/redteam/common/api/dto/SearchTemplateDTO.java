package com.redteam.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 搜索模板保存请求 DTO
 *
 * <p>用于接收前端提交的搜索模板数据，params_json 由前端负责序列化，后端原样存储。</p>
 *
 * @author 红方团队
 */
@Data
public class SearchTemplateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模板名称（必填）
     */
    @NotBlank(message = "模板名称不能为空")
    private String name;

    /**
     * 搜索条件JSON（关键词/模式/布尔条件/标签等，必填）
     */
    @NotBlank(message = "搜索条件不能为空")
    private String paramsJson;
}
