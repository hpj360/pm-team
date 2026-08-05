package com.redteam.common.util.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Objects;

/**
 * SM3 密码哈希算法工具类
 *
 * <p>SM3 是国家密码管理局发布的国产密码杂凑算法，输出 256 位（32 字节）摘要，
 * 用于替代 SHA-256，典型场景包括：密码哈希、文件指纹、数字签名前置摘要等。</p>
 *
 * <p>本工具类基于 BouncyCastle Provider 实现，所有方法均做空安全检查，
 * 密码、密钥等敏感信息不会出现在异常消息之外的日志中。</p>
 *
 * @author 红方团队
 */
public final class Sm3Util {

    /**
     * SM3 算法名称
     */
    public static final String ALGORITHM = "SM3";

    /**
     * SM3 摘要长度（字节）
     */
    public static final int DIGEST_LENGTH = 32;

    static {
        // 注册 BouncyCastle Provider（幂等，重复注册不会报错）
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm3Util() {
        // 工具类禁止实例化
    }

    /**
     * 计算数据的 SM3 摘要
     *
     * @param data 原始数据，不能为 null
     * @return 摘要字节数组（32 字节）
     * @throws NullPointerException 当 data 为 null 时
     */
    public static byte[] digest(byte[] data) {
        Objects.requireNonNull(data, "待摘要数据不能为空");
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            return md.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SM3 摘要计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算字符串的 SM3 摘要（UTF-8 编码）
     *
     * @param text 原始字符串，不能为 null
     * @return 摘要字节数组（32 字节）
     * @throws NullPointerException 当 text 为 null 时
     */
    public static byte[] digest(String text) {
        Objects.requireNonNull(text, "待摘要字符串不能为空");
        return digest(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算数据的 SM3 摘要并返回十六进制小写字符串
     *
     * @param data 原始数据，不能为 null
     * @return 摘要十六进制字符串（64 个字符）
     * @throws NullPointerException 当 data 为 null 时
     */
    public static String digestHex(byte[] data) {
        return toHex(digest(data));
    }

    /**
     * 计算字符串的 SM3 摘要并返回十六进制小写字符串（UTF-8 编码）
     *
     * @param text 原始字符串，不能为 null
     * @return 摘要十六进制字符串（64 个字符）
     * @throws NullPointerException 当 text 为 null 时
     */
    public static String digestHex(String text) {
        Objects.requireNonNull(text, "待摘要字符串不能为空");
        return toHex(digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 验证数据的 SM3 摘要是否与期望值一致
     *
     * @param data           原始数据，不能为 null
     * @param expectedDigest 期望的摘要字节数组，不能为 null
     * @return 一致返回 true，否则返回 false
     * @throws NullPointerException 当 data 或 expectedDigest 为 null 时
     */
    public static boolean verifyDigest(byte[] data, byte[] expectedDigest) {
        Objects.requireNonNull(data, "待验证数据不能为空");
        Objects.requireNonNull(expectedDigest, "期望摘要不能为空");
        return MessageDigest.isEqual(digest(data), expectedDigest);
    }

    /**
     * 验证字符串的 SM3 摘要是否与期望的十六进制字符串一致
     *
     * @param text               原始字符串，不能为 null
     * @param expectedDigestHex  期望的摘要十六进制字符串，不能为 null
     * @return 一致返回 true，否则返回 false
     * @throws NullPointerException 当 text 或 expectedDigestHex 为 null 时
     */
    public static boolean verifyDigest(String text, String expectedDigestHex) {
        Objects.requireNonNull(text, "待验证字符串不能为空");
        Objects.requireNonNull(expectedDigestHex, "期望摘要不能为空");
        return digestHex(text).equalsIgnoreCase(expectedDigestHex);
    }

    /**
     * 字节数组转十六进制小写字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        String hex = "0123456789abcdef";
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hex.charAt(v >>> 4);
            hexChars[i * 2 + 1] = hex.charAt(v & 0x0F);
        }
        return new String(hexChars);
    }
}
