package com.redteam.analyze.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.analyze.config.MispProperties;
import com.redteam.analyze.dto.MispAttribute;
import com.redteam.analyze.dto.MispEvent;
import com.redteam.analyze.dto.MispWebhookPayload;
import com.redteam.analyze.entity.IoCEntity;
import com.redteam.analyze.entity.IocType;
import com.redteam.analyze.service.IoCService;
import com.redteam.common.annotation.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MISP Webhook 接收控制器
 *
 * <p>接收 MISP 平台通过 Webhook 推送的事件/属性通知，解析后写入平台 IOC 库。</p>
 *
 * <p>鉴权方式：HMAC-SHA256 签名校验。MISP 推送时携带 {@code X-Signature}
 * 请求头（值为 {@code sha256=<hex>} 或纯 hex），服务端用配置的
 * {@code misp.webhook-secret} 对原始请求体计算 HMAC-SHA256 并比对。
 * 当 {@code misp.webhook-secret} 为空时跳过校验（仅用于内网测试环境）。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/intel/misp")
@RequiredArgsConstructor
@Tag(name = "MISP Webhook", description = "MISP 事件推送接收")
public class MispWebhookController {

    /**
     * 签名请求头名（主）
     */
    private static final String HEADER_SIGNATURE = "X-Signature";

    /**
     * 签名请求头名（兼容）
     */
    private static final String HEADER_SIGNATURE_ALT = "Signature";

    /**
     * HMAC 算法名
     */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 签名前缀
     */
    private static final String SIGNATURE_PREFIX = "sha256=";

    /**
     * 数据来源标记
     */
    private static final String SOURCE_MISP = "MISP";

    /**
     * 默认威胁等级
     */
    private static final String DEFAULT_THREAT_LEVEL = "3";

    private final MispProperties mispProperties;

    private final IoCService ioCService;

    /**
     * ObjectMapper（忽略未知属性）
     */
    private final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }

    /**
     * 接收 MISP Webhook 推送
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>校验 HMAC-SHA256 签名（secret 为空时跳过）</li>
     *   <li>解析原始请求体为 {@link MispWebhookPayload}</li>
     *   <li>提取 Event.attributes 或 Attribute，映射为 IoCEntity</li>
     *   <li>调用 {@link IoCService#saveOrUpdateIoc} 写入平台 IOC 库</li>
     * </ol>
     *
     * @param rawBody 原始请求体（用于签名校验）
     * @param request HTTP 请求（读取签名头）
     * @return 处理结果（saved 计数）
     */
    @PostMapping("/webhook")
    @Operation(summary = "接收 MISP Webhook", description = "接收 MISP 事件推送并写入平台 IOC 库")
    @AuditLog(action = "SYNC", resourceType = "INTEL", description = "MISP Webhook 接收")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody String rawBody,
                                                       HttpServletRequest request) {
        // 1. 签名校验
        if (!verifySignature(rawBody, request)) {
            log.warn("MISP Webhook 签名校验失败");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "签名校验失败");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        // 2. 解析 payload
        MispWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, MispWebhookPayload.class);
        } catch (Exception e) {
            log.error("MISP Webhook payload 解析失败", e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "payload 解析失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        // 3. 处理 payload，写入 IOC 库
        int saved = processPayload(payload);
        log.info("MISP Webhook 处理完成: saved={}", saved);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("saved", saved);
        body.put("action", payload.getAction());
        body.put("type", payload.getType());
        return ResponseEntity.ok(body);
    }

    /**
     * 处理 Webhook payload，将 MISP 数据写入平台 IOC 库
     *
     * @param payload Webhook 载荷
     * @return 保存的 IOC 数量
     */
    private int processPayload(MispWebhookPayload payload) {
        int saved = 0;
        if (payload == null) {
            return 0;
        }
        // 事件级别推送：提取 Event.attributes
        MispEvent event = payload.getEvent();
        if (event != null && event.getAttributes() != null) {
            for (MispAttribute attr : event.getAttributes()) {
                IoCEntity ioc = mapAttributeToIoc(attr, event);
                if (ioc != null) {
                    try {
                        ioCService.saveOrUpdateIoc(ioc);
                        saved++;
                    } catch (Exception e) {
                        log.warn("Webhook 保存 IOC 失败: type={}, value={}",
                                ioc.getIocType(), ioc.getIocValue(), e);
                    }
                }
            }
        }
        // 属性级别推送：直接处理单个 Attribute
        MispAttribute attr = payload.getAttribute();
        if (attr != null) {
            IoCEntity ioc = mapAttributeToIoc(attr, event);
            if (ioc != null) {
                try {
                    ioCService.saveOrUpdateIoc(ioc);
                    saved++;
                } catch (Exception e) {
                    log.warn("Webhook 保存 IOC 失败: type={}, value={}",
                            ioc.getIocType(), ioc.getIocValue(), e);
                }
            }
        }
        return saved;
    }

    /**
     * 将 MISP Attribute 映射为 IoCEntity
     *
     * @param attr  MISP 属性
     * @param event 所属事件（可为 null）
     * @return IOC 实体，不支持的属性类型返回 null
     */
    private IoCEntity mapAttributeToIoc(MispAttribute attr, MispEvent event) {
        if (attr == null || attr.getValue() == null) {
            return null;
        }
        String iocType = mapAttrTypeToIocType(attr.getType());
        if (iocType == null) {
            return null;
        }
        IoCEntity ioc = new IoCEntity();
        ioc.setIocType(iocType);
        ioc.setIocValue(attr.getValue());
        ioc.setDescription(attr.getComment() != null ? attr.getComment()
                : (event != null ? event.getInfo() : null));
        ioc.setSource(SOURCE_MISP);
        ioc.setThreatLevel(event != null && event.getThreatLevelId() != null
                ? event.getThreatLevelId() : DEFAULT_THREAT_LEVEL);
        if (event != null && event.getId() != null) {
            ioc.setMispEventId(event.getId());
        }
        return ioc;
    }

    /**
     * MISP attribute type → 平台 IOC 类型 映射
     *
     * @param attrType MISP attribute type
     * @return 平台 IOC 类型，不支持的类型返回 null
     */
    private String mapAttrTypeToIocType(String attrType) {
        if (attrType == null) {
            return null;
        }
        switch (attrType.trim().toLowerCase()) {
            case "ip-src":
            case "ip-dst":
            case "ip-src|port":
            case "ip-dst|port":
                return IocType.IP;
            case "domain":
            case "hostname":
                return IocType.DOMAIN;
            case "url":
                return IocType.URL;
            case "md5":
                return IocType.MD5;
            case "sha256":
                return IocType.SHA256;
            case "email-src":
            case "email-dst":
            case "email":
                return IocType.EMAIL;
            default:
                return null;
        }
    }

    /**
     * 校验 HMAC-SHA256 签名
     *
     * <p>当 {@code misp.webhook-secret} 为空时跳过校验（返回 true）。
     * 签名头支持 {@code X-Signature} 与 {@code Signature}，值支持
     * {@code sha256=<hex>} 与纯 {@code <hex>} 两种格式。</p>
     *
     * @param rawBody 原始请求体
     * @param request HTTP 请求
     * @return 校验通过返回 true，secret 为空时返回 true，校验失败返回 false
     */
    private boolean verifySignature(String rawBody, HttpServletRequest request) {
        String secret = mispProperties.getWebhookSecret();
        if (secret == null || secret.isEmpty()) {
            // 未配置 secret，跳过校验（仅用于内网测试环境）
            log.debug("MISP webhook-secret 未配置，跳过签名校验");
            return true;
        }
        String signature = request.getHeader(HEADER_SIGNATURE);
        if (signature == null || signature.isEmpty()) {
            signature = request.getHeader(HEADER_SIGNATURE_ALT);
        }
        if (signature == null || signature.isEmpty()) {
            log.warn("MISP Webhook 缺少签名头");
            return false;
        }
        // 去除 "sha256=" 前缀
        String received = signature.trim();
        if (received.toLowerCase().startsWith(SIGNATURE_PREFIX)) {
            received = received.substring(SIGNATURE_PREFIX.length());
        }
        String expected = hmacSha256Hex(secret, rawBody == null ? "" : rawBody);
        boolean ok = expected.equalsIgnoreCase(received);
        if (!ok) {
            log.warn("MISP Webhook 签名不匹配: expected={}, received={}", expected, received);
        }
        return ok;
    }

    /**
     * 计算 HMAC-SHA256 并返回 hex 编码字符串
     *
     * @param secret  密钥
     * @param payload 原始数据
     * @return hex 编码的 HMAC-SHA256
     */
    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("计算 HMAC-SHA256 失败", e);
            return "";
        }
    }

    /**
     * 字节数组转 hex 字符串
     *
     * @param bytes 字节数组
     * @return hex 字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * 暴露 ObjectMapper 供测试使用
     *
     * @return ObjectMapper
     */
    ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
