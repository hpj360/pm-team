package com.redteam.analyze.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * MISP Webhook 推送载荷
 *
 * <p>接收 MISP 通过 Webhook 推送的事件通知。MISP 的 Webhook payload 格式
 * 因 MISP 版本与插件配置而异，本类兼容事件级别与属性级别两种推送：</p>
 * <ul>
 *   <li>事件推送：payload 含 {@code Event} 字段</li>
 *   <li>属性推送：payload 含 {@code Attribute} 字段</li>
 * </ul>
 *
 * <p>同时保留 {@code action}（add/edit/delete）与 {@code type}（event/attribute）
 * 字段用于区分推送类型。</p>
 *
 * @author 红方团队
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MispWebhookPayload {

    /**
     * 推送动作：add / edit / delete / publish 等
     */
    @JsonProperty("action")
    private String action;

    /**
     * 推送对象类型：event / attribute
     */
    @JsonProperty("type")
    private String type;

    /**
     * 推送的 MISP 事件（事件级别推送时填充）
     */
    @JsonProperty("Event")
    private MispEvent event;

    /**
     * 推送的 MISP 属性（属性级别推送时填充）
     */
    @JsonProperty("Attribute")
    private MispAttribute attribute;

    /**
     * 原始事件 ID（属性推送时可能携带）
     */
    @JsonProperty("event_id")
    private String eventId;
}
