package com.redteam.analyze.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.analyze.config.MispProperties;
import com.redteam.analyze.dto.MispAttribute;
import com.redteam.analyze.dto.MispEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MISP REST API 客户端
 *
 * <p>基于 {@link RestTemplate} 封装 MISP 平台的 REST API 调用，提供事件与属性
 * 的增删查能力。所有请求统一携带以下头部：</p>
 * <ul>
 *   <li>{@code Authorization}: MISP API Key（{@link MispProperties#getApikey()}）</li>
 *   <li>{@code Accept}: application/json</li>
 *   <li>{@code Content-Type}: application/json</li>
 * </ul>
 *
 * <p>调用失败（网络错误、HTTP 非 2xx、JSON 解析失败）统一抛出 {@link MispException}，
 * 并记录错误日志。</p>
 *
 * <p>支持的方法：</p>
 * <ul>
 *   <li>{@link #addEvent(MispEvent)}：POST /events/add</li>
 *   <li>{@link #listEvents()}：GET /events/index</li>
 *   <li>{@link #listAttributes(String)}：GET /attributes/event/{eventId}</li>
 *   <li>{@link #addAttribute(String, MispAttribute)}：POST /attributes/add/{eventId}</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class MispClient {

    /**
     * Authorization 请求头名
     */
    private static final String HEADER_AUTH = "Authorization";

    /**
     * Accept 请求头名
     */
    private static final String HEADER_ACCEPT = "Accept";

    /**
     * Content-Type 请求头名
     */
    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    /**
     * MISP Event 包装键（MISP API 请求/响应中外层 "Event" 键）
     */
    private static final String KEY_EVENT = "Event";

    /**
     * MISP Attribute 包装键
     */
    private static final String KEY_ATTRIBUTE = "Attribute";

    /**
     * MISP 响应包装键
     */
    private static final String KEY_RESPONSE = "response";

    /**
     * MISP 配置属性
     */
    private final MispProperties mispProperties;

    /**
     * RestTemplate 实例
     */
    private final RestTemplate restTemplate;

    /**
     * JSON 序列化/反序列化器
     */
    private final ObjectMapper objectMapper;

    /**
     * 生产构造器：根据配置创建带超时的 RestTemplate
     *
     * @param mispProperties MISP 配置属性
     */
    @Autowired
    public MispClient(MispProperties mispProperties) {
        this.mispProperties = mispProperties;
        this.objectMapper = createObjectMapper();
        this.restTemplate = buildRestTemplate(mispProperties);
    }

    /**
     * 测试构造器：注入自定义 RestTemplate（便于 mock）
     *
     * @param mispProperties MISP 配置属性
     * @param restTemplate   自定义 RestTemplate
     */
    MispClient(MispProperties mispProperties, RestTemplate restTemplate) {
        this.mispProperties = mispProperties;
        this.objectMapper = createObjectMapper();
        this.restTemplate = restTemplate;
    }

    /**
     * 创建 ObjectMapper（忽略未知属性）
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }

    /**
     * 根据配置构建 RestTemplate（设置连接与读取超时）
     */
    private static RestTemplate buildRestTemplate(MispProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeout());
        factory.setReadTimeout(props.getTimeout());
        return new RestTemplate(factory);
    }

    /**
     * 构建通用请求头
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_AUTH, mispProperties.getApikey());
        headers.set(HEADER_ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HEADER_CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    /**
     * 拼接完整请求 URL（去除 endpoint 末尾斜杠）
     */
    private String endpointUrl(String path) {
        String base = mispProperties.getEndpoint();
        if (base == null || base.isEmpty()) {
            base = "http://localhost:8081";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    /**
     * 创建 MISP 事件
     *
     * <p>POST /events/add，请求体格式 {@code {"Event": {...}}}，
     * 响应格式 {@code {"response": {"Event": {...}}}}。</p>
     *
     * @param event MISP 事件（info / threat_level_id 等字段）
     * @return 创建后的 MISP 事件（含分配的 id）
     * @throws MispException 调用失败
     */
    public MispEvent addEvent(MispEvent event) {
        if (event == null) {
            throw new MispException("addEvent 失败：event 为空");
        }
        String url = endpointUrl("/events/add");
        try {
            String requestBody = objectMapper.writeValueAsString(Collections.singletonMap(KEY_EVENT, event));
            HttpEntity<String> entity = new HttpEntity<>(requestBody, buildHeaders());
            log.info("MISP addEvent 请求: url={}, info={}", url, event.getInfo());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode eventNode = root.path(KEY_RESPONSE).path(KEY_EVENT);
            if (eventNode.isMissingNode() || eventNode.isNull()) {
                eventNode = root.path(KEY_EVENT);
            }
            if (eventNode.isMissingNode() || eventNode.isNull()) {
                throw new MispException("addEvent 失败：响应缺少 Event 字段: " + resp.getBody());
            }
            MispEvent created = objectMapper.treeToValue(eventNode, MispEvent.class);
            log.info("MISP addEvent 成功: id={}", created.getId());
            return created;
        } catch (MispException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("MISP addEvent 网络错误: url={}", url, e);
            throw new MispException("MISP addEvent 网络错误: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("MISP addEvent 解析失败: url={}", url, e);
            throw new MispException("MISP addEvent 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出 MISP 全部事件
     *
     * <p>GET /events/index，响应格式 {@code [{"Event": {...}}, ...]}。</p>
     *
     * @return MISP 事件列表
     * @throws MispException 调用失败
     */
    public List<MispEvent> listEvents() {
        String url = endpointUrl("/events/index");
        try {
            HttpEntity<String> entity = new HttpEntity<>(buildHeaders());
            log.info("MISP listEvents 请求: url={}", url);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            List<MispEvent> result = new ArrayList<>();
            // 响应可能是数组 [{Event: {...}}, ...] 或 {response: [...]}
            JsonNode arrayNode;
            if (root.isArray()) {
                arrayNode = root;
            } else {
                JsonNode respNode = root.path(KEY_RESPONSE);
                arrayNode = respNode.isArray() ? respNode : root;
            }
            if (arrayNode.isArray()) {
                for (JsonNode item : arrayNode) {
                    JsonNode eventNode = item.path(KEY_EVENT);
                    if (eventNode.isMissingNode() || eventNode.isNull()) {
                        eventNode = item;
                    }
                    if (!eventNode.isMissingNode() && !eventNode.isNull()) {
                        result.add(objectMapper.treeToValue(eventNode, MispEvent.class));
                    }
                }
            }
            log.info("MISP listEvents 成功: count={}", result.size());
            return result;
        } catch (RestClientException e) {
            log.error("MISP listEvents 网络错误: url={}", url, e);
            throw new MispException("MISP listEvents 网络错误: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("MISP listEvents 解析失败: url={}", url, e);
            throw new MispException("MISP listEvents 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出指定事件下的全部属性
     *
     * <p>GET /attributes/event/{eventId}，响应格式 {@code {"response": {"Attribute": [...]}}}。</p>
     *
     * @param eventId MISP 事件 ID
     * @return 属性列表，eventId 为空时返回空列表
     * @throws MispException 调用失败
     */
    public List<MispAttribute> listAttributes(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return Collections.emptyList();
        }
        String url = endpointUrl("/attributes/event/" + eventId);
        try {
            HttpEntity<String> entity = new HttpEntity<>(buildHeaders());
            log.info("MISP listAttributes 请求: url={}", url);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            List<MispAttribute> result = new ArrayList<>();
            // 主格式: {"response": {"Attribute": [...]}}
            JsonNode attrArray = root.path(KEY_RESPONSE).path(KEY_ATTRIBUTE);
            if (attrArray.isArray()) {
                for (JsonNode a : attrArray) {
                    result.add(objectMapper.treeToValue(a, MispAttribute.class));
                }
            } else {
                // 兼容 [{"Attribute": {...}}, ...] 数组格式
                JsonNode respArray = root.path(KEY_RESPONSE);
                if (respArray.isArray()) {
                    for (JsonNode item : respArray) {
                        JsonNode a = item.path(KEY_ATTRIBUTE);
                        if (!a.isMissingNode() && !a.isNull()) {
                            result.add(objectMapper.treeToValue(a, MispAttribute.class));
                        }
                    }
                } else if (root.path(KEY_ATTRIBUTE).isArray()) {
                    // 兼容 {"Attribute": [...]}
                    for (JsonNode a : root.path(KEY_ATTRIBUTE)) {
                        result.add(objectMapper.treeToValue(a, MispAttribute.class));
                    }
                }
            }
            log.info("MISP listAttributes 成功: eventId={}, count={}", eventId, result.size());
            return result;
        } catch (RestClientException e) {
            log.error("MISP listAttributes 网络错误: url={}", url, e);
            throw new MispException("MISP listAttributes 网络错误: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("MISP listAttributes 解析失败: url={}", url, e);
            throw new MispException("MISP listAttributes 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 向指定事件添加属性
     *
     * <p>POST /attributes/add/{eventId}，请求体格式 {@code {"Attribute": {...}}}，
     * 响应格式 {@code {"response": {"Attribute": {...}}}} 或简单成功响应。</p>
     *
     * @param eventId   MISP 事件 ID
     * @param attribute 属性（type / value / category / to_ids / comment）
     * @return 创建后的属性（含分配的 id）
     * @throws MispException 调用失败
     */
    public MispAttribute addAttribute(String eventId, MispAttribute attribute) {
        if (eventId == null || eventId.isEmpty()) {
            throw new MispException("addAttribute 失败：eventId 为空");
        }
        if (attribute == null) {
            throw new MispException("addAttribute 失败：attribute 为空");
        }
        String url = endpointUrl("/attributes/add/" + eventId);
        try {
            String requestBody = objectMapper.writeValueAsString(Collections.singletonMap(KEY_ATTRIBUTE, attribute));
            HttpEntity<String> entity = new HttpEntity<>(requestBody, buildHeaders());
            log.info("MISP addAttribute 请求: url={}, type={}, value={}",
                    url, attribute.getType(), attribute.getValue());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode attrNode = root.path(KEY_RESPONSE).path(KEY_ATTRIBUTE);
            if (attrNode.isMissingNode() || attrNode.isNull()) {
                attrNode = root.path(KEY_ATTRIBUTE);
            }
            if (attrNode.isMissingNode() || attrNode.isNull()) {
                // 兼容简单成功响应 {"saved": true, "id": "..."}
                JsonNode idNode = root.path("id");
                if (!idNode.isMissingNode() && !idNode.isNull()) {
                    attribute.setId(idNode.asText());
                }
                log.info("MISP addAttribute 成功（简单响应）: eventId={}", eventId);
                return attribute;
            }
            MispAttribute created = objectMapper.treeToValue(attrNode, MispAttribute.class);
            log.info("MISP addAttribute 成功: id={}", created.getId());
            return created;
        } catch (MispException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("MISP addAttribute 网络错误: url={}", url, e);
            throw new MispException("MISP addAttribute 网络错误: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("MISP addAttribute 解析失败: url={}", url, e);
            throw new MispException("MISP addAttribute 解析失败: " + e.getMessage(), e);
        }
    }
}
