package com.redteam.analyze.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * MISP Event 数据传输对象
 *
 * <p>对应 MISP REST API 中的 Event 对象（不含外层 "Event" 包装）。</p>
 *
 * <p>字段命名遵循 MISP 官方 API 规范，使用 {@link JsonProperty} 映射 JSON 字段名。</p>
 *
 * @author 红方团队
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MispEvent {

    /**
     * 事件 ID（MISP 返回值通常为字符串形式的数字）
     */
    @JsonProperty("id")
    private String id;

    /**
     * 事件描述/标题
     */
    @JsonProperty("info")
    private String info;

    /**
     * 威胁等级 ID（1=高 / 2=中 / 3=低 / 4=未定义）
     */
    @JsonProperty("threat_level_id")
    private String threatLevelId;

    /**
     * 分析状态（0=初始 / 1=已分析 / 2=最终）
     */
    @JsonProperty("analysis")
    private String analysis;

    /**
     * 事件日期（YYYY-MM-DD）
     */
    @JsonProperty("date")
    private String date;

    /**
     * 发布状态（0=私有 / 1=可共享 / 2=已发布）
     */
    @JsonProperty("published")
    private String published;

    /**
     * 事件下的属性列表（MISP 使用大写 "Attribute" 作为键）
     */
    @JsonProperty("Attribute")
    private List<MispAttribute> attributes;

    /**
     * 事件所属组织 UUID
     */
    @JsonProperty("orgc_uuid")
    private String orgcUuid;

    /**
     * 事件 UUID
     */
    @JsonProperty("uuid")
    private String uuid;
}
