package com.redteam.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.util.crypto.Sm2Util;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JWT工具类
 *
 * <p>支持两种签名算法：
 * <ul>
 *   <li>HMAC-SHA256（兼容模式，原默认算法）</li>
 *   <li>SM3withSM2（国密模式，符合 GM/T 0018 规范，用于 refreshToken 等场景）</li>
 * </ul>
 * SM2 模式下 JWT 结构仍为 header.payload.signature，header.alg="SM2"，
 * 签名内容为 "{header}.{payload}" 的 UTF-8 字节，签名值采用 Base64URL 无填充编码。</p>
 *
 * @author 红方团队
 */
public class JwtUtil {

    /**
     * 默认密钥（生产环境应从配置文件读取）
     */
    private static final String DEFAULT_SECRET = "redteam-file-platform-secret-key-for-jwt-token-generation-2024";

    /**
     * 默认过期时间（24小时，毫秒）
     */
    private static final long DEFAULT_EXPIRATION = 24 * 60 * 60 * 1000L;

    /**
     * SM2 模式下 header.alg 取值
     */
    public static final String SM2_ALGORITHM = "SM2";

    /**
     * SM2 模式 JWT header（固定值）
     */
    private static final String SM2_HEADER = "{\"alg\":\"SM2\",\"typ\":\"JWT\"}";

    /**
     * Jackson 序列化器（线程安全）
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Base64URL 编码器（无填充）
     */
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * Base64URL 解码器
     */
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    /**
     * Token前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 请求头名称
     */
    public static final String HEADER_NAME = "Authorization";

    /**
     * 默认过期时间（毫秒）
     *
     * @return 过期时间
     */
    public static long getDefaultExpiration() {
        return DEFAULT_EXPIRATION;
    }

    /**
     * 生成Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return Token
     */
    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, null, DEFAULT_EXPIRATION);
    }

    /**
     * 生成Token
     *
     * @param userId     用户ID
     * @param username   用户名
     * @param claims     自定义声明
     * @param expiration 过期时间（毫秒）
     * @return Token
     */
    public static String generateToken(Long userId, String username, Map<String, Object> claims, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .claim("userId", userId)
                .claim("username", username);

        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }

        return builder.signWith(getSecretKey()).compact();
    }

    /**
     * 解析Token
     *
     * @param token Token
     * @return Claims
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从Token中获取用户ID
     *
     * @param token Token
     * @return 用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取用户名
     *
     * @param token Token
     * @return 用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 验证Token是否有效
     *
     * @param token Token
     * @return 是否有效
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * 判断Token是否过期
     *
     * @param token Token
     * @return 是否过期
     */
    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }

    /**
     * 获取密钥
     *
     * @return 密钥
     */
    private static SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== SM2 国密签名扩展 ====================

    /**
     * 使用 SM2 私钥生成 JWT Token
     *
     * <p>claims 中可传入 userId、username 以及自定义声明；若未传入 exp/iat，
     * 则使用默认 24 小时过期。</p>
     *
     * @param privateKeyBase64 Base64 编码的 SM2 私钥，不能为 null
     * @param claims           自定义声明，可为 null
     * @return SM2 签名的 JWT Token
     * @throws NullPointerException 当 privateKeyBase64 为 null 时
     */
    public static String signWithSm2(String privateKeyBase64, Map<String, Object> claims) {
        Objects.requireNonNull(privateKeyBase64, "SM2 私钥不能为空");
        try {
            long nowSec = System.currentTimeMillis() / 1000L;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iat", nowSec);
            payload.put("exp", nowSec + DEFAULT_EXPIRATION / 1000L);
            if (claims != null && !claims.isEmpty()) {
                payload.putAll(claims);
            }

            String header = urlEncode(SM2_HEADER.getBytes(StandardCharsets.UTF_8));
            String payloadJson = OBJECT_MAPPER.writeValueAsString(payload);
            String payloadEncoded = urlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = header + "." + payloadEncoded;

            byte[] signature = Sm2Util.sign(Sm2Util.privateKeyFromBase64(privateKeyBase64),
                    signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + urlEncode(signature);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 JWT 签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 SM2 公钥解析并验签 JWT Token
     *
     * @param token           JWT Token，不能为 null
     * @param publicKeyBase64 Base64 编码的 SM2 公钥，不能为 null
     * @return 声明内容（包含 userId、username、exp、iat 等）
     * @throws NullPointerException       当 token 或 publicKeyBase64 为 null 时
     * @throws io.jsonwebtoken.JwtException 验签失败或 token 格式错误时
     */
    public static Map<String, Object> parseAndVerifyWithSm2(String token, String publicKeyBase64) {
        Objects.requireNonNull(token, "Token 不能为空");
        Objects.requireNonNull(publicKeyBase64, "SM2 公钥不能为空");
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("SM2 JWT 格式错误，应为 header.payload.signature");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] signature = URL_DECODER.decode(parts[2]);
        boolean valid = Sm2Util.verify(Sm2Util.publicKeyFromBase64(publicKeyBase64),
                signingInput.getBytes(StandardCharsets.UTF_8), signature);
        if (!valid) {
            throw new JwtException("SM2 JWT 验签失败");
        }
        try {
            byte[] payloadBytes = URL_DECODER.decode(parts[1]);
            return OBJECT_MAPPER.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new JwtException("SM2 JWT payload 解析失败: " + e.getMessage());
        }
    }

    /**
     * 验证 SM2 Token 是否有效（验签通过且未过期）
     *
     * @param token           JWT Token
     * @param publicKeyBase64 Base64 编码的 SM2 公钥
     * @return 是否有效
     */
    public static boolean validateSm2Token(String token, String publicKeyBase64) {
        try {
            Map<String, Object> claims = parseAndVerifyWithSm2(token, publicKeyBase64);
            Object exp = claims.get("exp");
            if (exp instanceof Number) {
                long expSec = ((Number) exp).longValue();
                return System.currentTimeMillis() / 1000L < expSec;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 SM2 Token 声明中获取用户ID
     *
     * @param claims 声明内容
     * @return 用户ID，不存在返回 null
     */
    public static Long getUserIdFromClaims(Map<String, Object> claims) {
        Object val = claims.get("userId");
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return Long.valueOf(val.toString());
    }

    /**
     * 从 SM2 Token 声明中获取用户名
     *
     * @param claims 声明内容
     * @return 用户名，不存在返回 null
     */
    public static String getUsernameFromClaims(Map<String, Object> claims) {
        Object val = claims.get("username");
        return val == null ? null : val.toString();
    }

    /**
     * Base64URL 无填充编码
     *
     * @param bytes 字节数组
     * @return Base64URL 字符串
     */
    private static String urlEncode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }
}
