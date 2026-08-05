package com.redteam.analyze.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.analyze.config.TaxiiProperties;
import com.redteam.analyze.service.StixExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TAXII 2.1 Server 控制器
 *
 * <p>提供威胁情报订阅端点，遵循 TAXII 2.1 规范：</p>
 * <ul>
 *   <li>{@code GET /taxii/} — Discovery 服务发现</li>
 *   <li>{@code GET /taxii/api/} — API Root 信息</li>
 *   <li>{@code GET /taxii/api/collections/} — 集合列表</li>
 *   <li>{@code GET /taxii/api/collections/{id}/} — 集合详情</li>
 *   <li>{@code GET /taxii/api/collections/{id}/objects/} — STIX 对象订阅</li>
 * </ul>
 *
 * <p>认证方式由 {@link TaxiiProperties} 配置，支持 basic / apikey / none。
 * 所有响应 Content-Type 为 {@code application/taxii+json;version=2.1}。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/taxii")
@Tag(name = "TAXII 2.1 Server", description = "威胁情报订阅服务")
public class TaxiiController {

    /**
     * TAXII 2.1 规范的响应 Content-Type
     */
    private static final MediaType TAXII_MEDIA_TYPE =
            MediaType.parseMediaType("application/taxii+json;version=2.1");

    /**
     * STIX 2.1 媒体类型（集合支持的 media_types）
     */
    private static final String STIX_MEDIA_TYPE = "application/stix+json;version=2.1";

    /**
     * IOC 集合 ID
     */
    private static final String IOC_COLLECTION_ID = "ioc-collection";

    /**
     * APT 集合 ID
     */
    private static final String APT_COLLECTION_ID = "apt-collection";

    @Autowired
    private StixExportService stixExportService;

    @Autowired
    private TaxiiProperties taxiiProperties;

    // ==================== TAXII 2.1 端点 ====================

    /**
     * Discovery 端点 - 返回 API 根信息
     *
     * @param request HTTP 请求（用于认证校验）
     * @return Discovery 响应
     */
    @GetMapping("/")
    @Operation(summary = "TAXII Discovery", description = "服务发现，返回 API 根列表")
    public ResponseEntity<Map<String, Object>> discovery(HttpServletRequest request) {
        if (!checkAuth(request)) {
            return unauthorized();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "红方文件分析平台 TAXII Server");
        body.put("description", "威胁情报订阅服务");
        body.put("default", "/taxii/api/");
        body.put("api_roots", Collections.singletonList("/taxii/api/"));
        return ok(body);
    }

    /**
     * API Root 端点 - 返回 API 根详细信息
     *
     * @param request HTTP 请求
     * @return API Root 响应
     */
    @GetMapping("/api/")
    @Operation(summary = "API Root", description = "返回 API 根详细信息")
    public ResponseEntity<Map<String, Object>> apiRoot(HttpServletRequest request) {
        if (!checkAuth(request)) {
            return unauthorized();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "红方文件分析平台 API Root");
        body.put("description", "威胁情报 API 根");
        body.put("versions", Collections.singletonList("taxii-2.1"));
        body.put("max_content_length", 10485760);
        return ok(body);
    }

    /**
     * Collections 列表端点
     *
     * @param request HTTP 请求
     * @return Collections 响应
     */
    @GetMapping("/api/collections/")
    @Operation(summary = "Collections 列表", description = "返回可订阅的情报集合列表")
    public ResponseEntity<Map<String, Object>> collections(HttpServletRequest request) {
        if (!checkAuth(request)) {
            return unauthorized();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collections", buildAllCollections());
        return ok(body);
    }

    /**
     * 集合详情端点
     *
     * @param id      集合 ID
     * @param request HTTP 请求
     * @return 集合详情
     */
    @GetMapping("/api/collections/{id}/")
    @Operation(summary = "集合详情", description = "返回单个集合的详细信息")
    public ResponseEntity<Map<String, Object>> collectionDetail(
            @Parameter(description = "集合 ID") @PathVariable String id,
            HttpServletRequest request) {
        if (!checkAuth(request)) {
            return unauthorized();
        }
        Map<String, Object> collection = findCollection(id);
        if (collection == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(TAXII_MEDIA_TYPE)
                    .body(errorBody("集合不存在: " + id));
        }
        return ok(collection);
    }

    /**
     * STIX Objects 端点 - 返回集合中的 STIX 对象
     *
     * @param id         集合 ID
     * @param type       STIX 对象类型过滤（逗号分隔，如 indicator,threat-actor）
     * @param limit      返回数量上限
     * @param addedAfter 仅返回该时间之后新增的对象（ISO 8601）
     * @param request    HTTP 请求
     * @return STIX Objects 响应
     */
    @GetMapping("/api/collections/{id}/objects/")
    @Operation(summary = "STIX Objects", description = "返回集合中的 STIX 2.1 对象列表")
    public ResponseEntity<Map<String, Object>> objects(
            @Parameter(description = "集合 ID") @PathVariable String id,
            @Parameter(description = "STIX 对象类型过滤") @RequestParam(required = false) String type,
            @Parameter(description = "返回数量上限") @RequestParam(required = false, defaultValue = "50") Integer limit,
            @Parameter(description = "仅返回该时间之后新增的对象") @RequestParam(required = false) String addedAfter,
            HttpServletRequest request) {
        if (!checkAuth(request)) {
            return unauthorized();
        }
        // 集合存在性校验
        if (findCollection(id) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(TAXII_MEDIA_TYPE)
                    .body(errorBody("集合不存在: " + id));
        }

        // 加载 STIX 对象（StixExportService 不可用时返回空列表）
        List<Map<String, Object>> stixObjects = loadStixObjects(id);

        // 类型过滤
        if (type != null && !type.isEmpty()) {
            Set<String> typeSet = new HashSet<>(Arrays.asList(type.split(",")));
            stixObjects = filterByType(stixObjects, typeSet);
        }

        // 分页
        int pageSize = (limit == null || limit <= 0) ? 50 : limit;
        boolean more = false;
        if (stixObjects.size() > pageSize) {
            more = true;
            stixObjects = new ArrayList<>(stixObjects.subList(0, pageSize));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("more", more);
        body.put("objects", stixObjects);
        return ok(body);
    }

    // ==================== 认证 ====================

    /**
     * 校验请求认证
     *
     * <p>根据 {@link TaxiiProperties#getAuthType()} 选择认证方式：</p>
     * <ul>
     *   <li>none：不校验</li>
     *   <li>basic：Basic Auth（Authorization: Basic base64(user:pass)）</li>
     *   <li>apikey：API Key（X-API-Key 请求头）</li>
     * </ul>
     *
     * @param request HTTP 请求
     * @return 认证通过返回 true，否则 false
     */
    private boolean checkAuth(HttpServletRequest request) {
        if (!taxiiProperties.isEnabled()) {
            return true;
        }
        String authType = taxiiProperties.getAuthType();
        if (authType == null || "none".equalsIgnoreCase(authType)) {
            return true;
        }
        if ("basic".equalsIgnoreCase(authType)) {
            return checkBasicAuth(request);
        }
        if ("apikey".equalsIgnoreCase(authType)) {
            return checkApiKeyAuth(request);
        }
        log.warn("未知 TAXII 认证方式: {}", authType);
        return false;
    }

    /**
     * Basic Auth 校验
     *
     * @param request HTTP 请求
     * @return 校验通过返回 true
     */
    private boolean checkBasicAuth(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Basic ")) {
            return false;
        }
        String encoded = auth.substring(6).trim();
        String decoded;
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encoded);
            decoded = new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int idx = decoded.indexOf(':');
        if (idx < 0) {
            return false;
        }
        String user = decoded.substring(0, idx);
        String pass = decoded.substring(idx + 1);
        return taxiiProperties.getUsername().equals(user)
                && taxiiProperties.getPassword().equals(pass);
    }

    /**
     * API Key 校验
     *
     * @param request HTTP 请求
     * @return 校验通过返回 true
     */
    private boolean checkApiKeyAuth(HttpServletRequest request) {
        String key = request.getHeader("X-API-Key");
        String expected = taxiiProperties.getApiKey();
        return expected != null && !expected.isEmpty() && expected.equals(key);
    }

    // ==================== STIX 对象加载 ====================

    /**
     * 从 StixExportService 加载指定集合的 STIX 对象
     *
     * <p>StixExportService 不可用或异常时返回空列表。</p>
     *
     * @param collectionId 集合 ID
     * @return STIX 对象列表
     */
    private List<Map<String, Object>> loadStixObjects(String collectionId) {
        try {
            String bundleJson;
            switch (collectionId) {
                case IOC_COLLECTION_ID:
                    bundleJson = stixExportService.exportIocsToStix(mockIocs());
                    break;
                case APT_COLLECTION_ID:
                    bundleJson = stixExportService.exportAptsToStix(mockApts());
                    break;
                default:
                    return new ArrayList<>();
            }
            return extractObjects(bundleJson);
        } catch (Exception e) {
            log.warn("加载 STIX 对象失败，返回空列表: collection={}, error={}", collectionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从 STIX Bundle JSON 中提取 objects 数组
     *
     * @param bundleJson Bundle JSON 字符串
     * @return STIX 对象列表
     */
    private List<Map<String, Object>> extractObjects(String bundleJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (bundleJson == null || bundleJson.isEmpty()) {
            return result;
        }
        JSONObject bundle = JSONUtil.parseObj(bundleJson);
        JSONArray objects = bundle.getJSONArray("objects");
        if (objects == null) {
            return result;
        }
        for (int i = 0; i < objects.size(); i++) {
            JSONObject obj = objects.getJSONObject(i);
            result.add(new LinkedHashMap<>(obj));
        }
        return result;
    }

    /**
     * 按类型过滤 STIX 对象
     *
     * @param objects STIX 对象列表
     * @param types   允许的类型集合
     * @return 过滤后的列表
     */
    private List<Map<String, Object>> filterByType(List<Map<String, Object>> objects, Set<String> types) {
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> obj : objects) {
            Object t = obj.get("type");
            if (t != null && types.contains(t.toString())) {
                filtered.add(obj);
            }
        }
        return filtered;
    }

    // ==================== 集合定义 ====================

    /**
     * 构建全部集合定义
     *
     * @return 集合列表
     */
    private List<Map<String, Object>> buildAllCollections() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildCollection(IOC_COLLECTION_ID, "IOC 情报集合",
                "恶意IP/域名/文件哈希", true, false));
        list.add(buildCollection(APT_COLLECTION_ID, "APT 组织集合",
                "APT 组织信息", true, false));
        return list;
    }

    /**
     * 查找指定 ID 的集合
     *
     * @param id 集合 ID
     * @return 集合 Map，不存在返回 null
     */
    private Map<String, Object> findCollection(String id) {
        if (id == null) {
            return null;
        }
        for (Map<String, Object> c : buildAllCollections()) {
            if (id.equals(c.get("id"))) {
                return c;
            }
        }
        return null;
    }

    /**
     * 构建单个集合 Map
     */
    private Map<String, Object> buildCollection(String id, String title, String description,
                                                boolean canRead, boolean canWrite) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("title", title);
        map.put("description", description);
        map.put("can_read", canRead);
        map.put("can_write", canWrite);
        map.put("media_types", Collections.singletonList(STIX_MEDIA_TYPE));
        return map;
    }

    // ==================== Mock 数据（替代 DB 数据源） ====================

    /**
     * Mock IOC 数据
     *
     * @return IOC 列表
     */
    private List<Map<String, Object>> mockIocs() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildIoc("IP", "1.2.3.4", "恶意 C2 服务器 IP"));
        list.add(buildIoc("Domain", "evil.com", "恶意域名"));
        list.add(buildIoc("URL", "http://evil.com/payload", "恶意载荷下载地址"));
        list.add(buildIoc("MD5", "d41d8cd98f00b204e9800998ecf8427e", "恶意样本 MD5"));
        return list;
    }

    /**
     * Mock APT 组织数据
     *
     * @return APT 列表
     */
    private List<Map<String, Object>> mockApts() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> apt1 = new HashMap<>();
        apt1.put("name", "APT28");
        apt1.put("description", "国家级 APT 组织，疑似俄罗斯背景");
        apt1.put("threatActorTypes", Collections.singletonList("nation-state"));
        apt1.put("aliases", Arrays.asList("Fancy Bear", "Sofacy"));
        list.add(apt1);
        Map<String, Object> apt2 = new HashMap<>();
        apt2.put("name", "APT41");
        apt2.put("description", "国家级 APT 组织");
        apt2.put("threatActorTypes", Arrays.asList("nation-state", "crime-syndicate"));
        apt2.put("aliases", Arrays.asList("Winnti", "Barium"));
        list.add(apt2);
        return list;
    }

    /**
     * 构建 IOC Map
     */
    private Map<String, Object> buildIoc(String type, String value, String description) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("value", value);
        map.put("description", description);
        return map;
    }

    // ==================== 响应辅助 ====================

    /**
     * 构建成功响应（TAXII Content-Type）
     *
     * @param body 响应体
     * @return ResponseEntity
     */
    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TAXII_MEDIA_TYPE);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * 构建未认证响应（401）
     *
     * @return ResponseEntity
     */
    private ResponseEntity<Map<String, Object>> unauthorized() {
        Map<String, Object> body = errorBody("未授权：TAXII 认证失败");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TAXII_MEDIA_TYPE);
        return new ResponseEntity<>(body, headers, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 构建错误响应体
     *
     * @param message 错误描述
     * @return 错误体 Map
     */
    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "TAXII Error");
        body.put("description", message);
        body.put("error_code", "INVALID");
        return body;
    }
}
