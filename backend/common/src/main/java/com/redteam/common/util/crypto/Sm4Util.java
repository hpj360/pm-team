package com.redteam.common.util.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * SM4 对称加密算法工具类
 *
 * <p>SM4 是国家密码管理局发布的分组对称密码算法，密钥长度与分组长度均为 128 位（16 字节）。
 * 本工具类采用 CBC 工作模式 + PKCS7Padding 填充，用于敏感数据（邮箱、手机号等）的加密存储。</p>
 *
 * <p>密钥采用 Base64 编码存储/传输；每次加密生成随机 IV 并与密文拼接后整体 Base64 编码，
 * 解密时自动拆分 IV 与密文，保证相同明文每次密文不同，提升安全性。</p>
 *
 * @author 红方团队
 */
public final class Sm4Util {

    /**
     * SM4 算法名称
     */
    public static final String ALGORITHM = "SM4";

    /**
     * SM4 完整变换算法（CBC 模式 + PKCS7Padding）
     */
    public static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";

    /**
     * SM4 密钥长度（字节）
     */
    public static final int KEY_LENGTH = 16;

    /**
     * SM4 IV 长度（字节，等于分组长度）
     */
    public static final int IV_LENGTH = 16;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Util() {
        // 工具类禁止实例化
    }

    /**
     * 生成随机 SM4 密钥
     *
     * @return Base64 编码的密钥字符串
     */
    public static String generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            kg.init(KEY_LENGTH * 8, new SecureRandom());
            SecretKey key = kg.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("SM4 密钥生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成随机 SM4 密钥（字节数组形式）
     *
     * @return 16 字节密钥
     */
    public static byte[] generateKeyBytes() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            kg.init(KEY_LENGTH * 8, new SecureRandom());
            return kg.generateKey().getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("SM4 密钥生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 Base64 密钥加密字符串（UTF-8 编码），返回 Base64 编码密文（内含 IV）
     *
     * @param keyBase64 Base64 编码的密钥，不能为 null
     * @param plaintext 明文字符串，不能为 null
     * @return Base64 编码的密文（前 16 字节为 IV，其后为密文）
     * @throws NullPointerException 当 keyBase64 或 plaintext 为 null 时
     */
    public static String encrypt(String keyBase64, String plaintext) {
        Objects.requireNonNull(keyBase64, "SM4 密钥不能为空");
        Objects.requireNonNull(plaintext, "待加密明文不能为空");
        byte[] encrypted = encrypt(Base64.getDecoder().decode(keyBase64),
                plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 使用密钥加密数据，返回 IV + 密文拼接的字节数组
     *
     * @param keyBytes  16 字节密钥，不能为 null
     * @param plaintext 明文字节数组，不能为 null
     * @return IV（16 字节）+ 密文 拼接的字节数组
     * @throws NullPointerException 当 keyBytes 或 plaintext 为 null 时
     */
    public static byte[] encrypt(byte[] keyBytes, byte[] plaintext) {
        Objects.requireNonNull(keyBytes, "SM4 密钥不能为空");
        Objects.requireNonNull(plaintext, "待加密明文不能为空");
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("SM4 密钥长度必须为 " + KEY_LENGTH + " 字节");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, ALGORITHM),
                    new IvParameterSpec(iv));
            byte[] cipherText = cipher.doFinal(plaintext);
            byte[] output = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(cipherText, 0, output, iv.length, cipherText.length);
            return output;
        } catch (Exception e) {
            throw new IllegalStateException("SM4 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 Base64 密钥解密 Base64 密文（内含 IV），返回明文字符串（UTF-8 解码）
     *
     * @param keyBase64     Base64 编码的密钥，不能为 null
     * @param ciphertextB64 Base64 编码的密文（内含 IV），不能为 null
     * @return 明文字符串
     * @throws NullPointerException 当 keyBase64 或 ciphertextB64 为 null 时
     */
    public static String decrypt(String keyBase64, String ciphertextB64) {
        Objects.requireNonNull(keyBase64, "SM4 密钥不能为空");
        Objects.requireNonNull(ciphertextB64, "待解密密文不能为空");
        byte[] decrypted = decrypt(Base64.getDecoder().decode(keyBase64),
                Base64.getDecoder().decode(ciphertextB64));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 使用密钥解密 IV + 密文拼接的字节数组，返回明文字节数组
     *
     * @param keyBytes       16 字节密钥，不能为 null
     * @param ivAndCiphertext IV（前 16 字节）+ 密文 拼接的字节数组，不能为 null
     * @return 明文字节数组
     * @throws NullPointerException 当 keyBytes 或 ivAndCiphertext 为 null 时
     */
    public static byte[] decrypt(byte[] keyBytes, byte[] ivAndCiphertext) {
        Objects.requireNonNull(keyBytes, "SM4 密钥不能为空");
        Objects.requireNonNull(ivAndCiphertext, "待解密密文不能为空");
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("SM4 密钥长度必须为 " + KEY_LENGTH + " 字节");
        }
        if (ivAndCiphertext.length < IV_LENGTH) {
            throw new IllegalArgumentException("密文长度不足，无法提取 IV");
        }
        try {
            byte[] iv = Arrays.copyOfRange(ivAndCiphertext, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(ivAndCiphertext, IV_LENGTH, ivAndCiphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, ALGORITHM),
                    new IvParameterSpec(iv));
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 解密失败: " + e.getMessage(), e);
        }
    }
}
