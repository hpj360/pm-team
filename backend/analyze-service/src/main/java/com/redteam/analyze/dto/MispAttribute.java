package com.redteam.analyze.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * MISP Attribute 数据传输对象
 *
 * <p>对应 MISP REST API 中的 Attribute 对象（IOC 指标项）。</p>
 *
 * <p>常见 attribute type 与平台 IOC 类型映射：</p>
 * <ul>
 *   <li>ip-src / ip-dst ↔ IOC IP</li>
 *   <li>domain ↔ IOC Domain</li>
 *   <li>url ↔ IOC URL</li>
 *   <li>md5 / sha256 ↔ IOC Hash</li>
 *   <li>email-src ↔ IOC Email</li>
 * </ul>
 *
 * @author 红方团队
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MispAttribute {

    /**
     * 属性 ID
     */
    @JsonProperty("id")
    private String id;

    /**
     * 属性类型（如 ip-src / domain / url / md5 / sha256 / email-src）
     */
    @JsonProperty("type")
    private String type;

    /**
     * 属性值
     */
    @JsonProperty("value")
    private String value;

    /**
     * 属性分类（如 Network activity / Payload delivery / Artifacts dropped）
     */
    @JsonProperty("category")
    private String category;

    /**
     * 是否触发 IDS 检测
     *
     * <p>MISP 不同版本可能返回 boolean 或 "0"/"1" 字符串，统一以字符串存储，
     * 业务层通过 {@link #isToIdsTrue()} 判断。</p>
     */
    @JsonProperty("to_ids")
    private String toIds;

    /**
     * 备注
     */
    @JsonProperty("comment")
    private String comment;

    /**
     * 事件 ID（属性所属事件）
     */
    @JsonProperty("event_id")
    private String eventId;

    /**
     * 判断 to_ids 是否为真
     *
     * @return to_ids 为 "1" 或 "true"（不区分大小写）时返回 true
     */
    public boolean isToIdsTrue() {
        if (toIds == null) {
            return false;
        }
        String v = toIds.trim();
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }
}
