package com.redteam.common.util.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * SM2 非对称密码算法工具类
 *
 * <p>SM2 是基于椭圆曲线密码（ECC）的国产非对称密码算法，曲线参数为 sm2p256v1，
 * 典型场景包括：JWT 签名验签、敏感字段加密、密钥协商等。</p>
 *
 * <p>本工具类通过 JCE 接口调用 BouncyCastle Provider 实现：
 * <ul>
 *   <li>密钥对生成：EC KeyPairGenerator + sm2p256v1 曲线</li>
 *   <li>加解密：Cipher "SM2"（C1C3C2 模式）</li>
 *   <li>签名验签：Signature "SM3withSM2"</li>
 * </ul>
 * 密钥采用 Base64 编码存储/传输：公钥为 X.509 SubjectPublicKeyInfo，
 * 私钥为 PKCS#8 PrivateKeyInfo。</p>
 *
 * @author 红方团队
 */
public final class Sm2Util {

    /**
     * SM2 椭圆曲线名称
     */
    public static final String CURVE_NAME = "sm2p256v1";

    /**
     * SM2 加密算法名称
     */
    public static final String CIPHER_ALGORITHM = "SM2";

    /**
     * SM2 签名算法名称（SM3 作为前置摘要）
     */
    public static final String SIGNATURE_ALGORITHM = "SM3withSM2";

    /**
     * 密钥工厂算法
     */
    public static final String KEY_FACTORY_ALGORITHM = "EC";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm2Util() {
        // 工具类禁止实例化
    }

    /**
     * 生成 SM2 密钥对
     *
     * @return SM2 密钥对
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_FACTORY_ALGORITHM,
                    BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(new ECGenParameterSpec(CURVE_NAME), new SecureRandom());
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("SM2 密钥对生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成 SM2 密钥对，返回 Base64 编码的字符串数组
     *
     * @return 长度为 2 的数组，[0]=Base64 公钥，[1]=Base64 私钥
     */
    public static String[] generateKeyPairBase64() {
        KeyPair keyPair = generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        return new String[]{publicKey, privateKey};
    }

    /**
     * 使用 Base64 公钥加密字符串（UTF-8 编码），返回 Base64 编码密文
     *
     * @param publicKeyBase64 Base64 编码的公钥，不能为 null
     * @param plaintext       明文字符串，不能为 null
     * @return Base64 编码的密文
     * @throws NullPointerException 当 publicKeyBase64 或 plaintext 为 null 时
     */
    public static String encrypt(String publicKeyBase64, String plaintext) {
        Objects.requireNonNull(publicKeyBase64, "SM2 公钥不能为空");
        Objects.requireNonNull(plaintext, "待加密明文不能为空");
        byte[] encrypted = encrypt(publicKeyFromBase64(publicKeyBase64),
                plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 使用公钥加密数据
     *
     * @param publicKey 公钥，不能为 null
     * @param data      明文字节数组，不能为 null
     * @return 密文字节数组
     * @throws NullPointerException 当 publicKey 或 data 为 null 时
     */
    public static byte[] encrypt(PublicKey publicKey, byte[] data) {
        Objects.requireNonNull(publicKey, "SM2 公钥不能为空");
        Objects.requireNonNull(data, "待加密数据不能为空");
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 Base64 私钥解密 Base64 密文，返回明文字符串（UTF-8 解码）
     *
     * @param privateKeyBase64 Base64 编码的私钥，不能为 null
     * @param ciphertextBase64 Base64 编码的密文，不能为 null
     * @return 明文字符串
     * @throws NullPointerException 当 privateKeyBase64 或 ciphertextBase64 为 null 时
     */
    public static String decrypt(String privateKeyBase64, String ciphertextBase64) {
        Objects.requireNonNull(privateKeyBase64, "SM2 私钥不能为空");
        Objects.requireNonNull(ciphertextBase64, "待解密密文不能为空");
        byte[] decrypted = decrypt(privateKeyFromBase64(privateKeyBase64),
                Base64.getDecoder().decode(ciphertextBase64));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 使用私钥解密数据
     *
     * @param privateKey 私钥，不能为 null
     * @param data       密文字节数组，不能为 null
     * @return 明文字节数组
     * @throws NullPointerException 当 privateKey 或 data 为 null 时
     */
    public static byte[] decrypt(PrivateKey privateKey, byte[] data) {
        Objects.requireNonNull(privateKey, "SM2 私钥不能为空");
        Objects.requireNonNull(data, "待解密数据不能为空");
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 Base64 私钥对字符串签名（UTF-8 编码），返回 Base64 编码签名
     *
     * @param privateKeyBase64 Base64 编码的私钥，不能为 null
     * @param data             待签名字符串，不能为 null
     * @return Base64 编码的签名
     * @throws NullPointerException 当 privateKeyBase64 或 data 为 null 时
     */
    public static String sign(String privateKeyBase64, String data) {
        Objects.requireNonNull(privateKeyBase64, "SM2 私钥不能为空");
        Objects.requireNonNull(data, "待签名数据不能为空");
        byte[] signature = sign(privateKeyFromBase64(privateKeyBase64),
                data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * 使用私钥对数据签名
     *
     * @param privateKey 私钥，不能为 null
     * @param data       待签名字节数组，不能为 null
     * @return 签名字节数组
     * @throws NullPointerException 当 privateKey 或 data 为 null 时
     */
    public static byte[] sign(PrivateKey privateKey, byte[] data) {
        Objects.requireNonNull(privateKey, "SM2 私钥不能为空");
        Objects.requireNonNull(data, "待签名数据不能为空");
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM,
                    BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(privateKey, new SecureRandom());
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 Base64 公钥验证字符串签名（UTF-8 编码）
     *
     * @param publicKeyBase64  Base64 编码的公钥，不能为 null
     * @param data             原始字符串，不能为 null
     * @param signatureBase64  Base64 编码的签名，不能为 null
     * @return 验签通过返回 true，否则返回 false
     * @throws NullPointerException 当任一参数为 null 时
     */
    public static boolean verify(String publicKeyBase64, String data, String signatureBase64) {
        Objects.requireNonNull(publicKeyBase64, "SM2 公钥不能为空");
        Objects.requireNonNull(data, "待验签数据不能为空");
        Objects.requireNonNull(signatureBase64, "签名不能为空");
        return verify(publicKeyFromBase64(publicKeyBase64),
                data.getBytes(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(signatureBase64));
    }

    /**
     * 使用公钥验证签名
     *
     * <p>验签失败（密钥不匹配、签名篡改、签名格式错误等）统一返回 false，不抛异常；
     * 仅当参数为 null 时抛出 NullPointerException。</p>
     *
     * @param publicKey 公钥，不能为 null
     * @param data      原始字节数组，不能为 null
     * @param signature 签名字节数组，不能为 null
     * @return 验签通过返回 true，否则返回 false
     * @throws NullPointerException 当任一参数为 null 时
     */
    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
        Objects.requireNonNull(publicKey, "SM2 公钥不能为空");
        Objects.requireNonNull(data, "待验签数据不能为空");
        Objects.requireNonNull(signature, "签名不能为空");
        try {
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM,
                    BouncyCastleProvider.PROVIDER_NAME);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            // 验签失败（密钥不匹配、签名篡改、格式错误等）统一返回 false，不抛异常
            return false;
        }
    }

    /**
     * 从 Base64 字符串还原公钥
     *
     * @param publicKeyBase64 Base64 编码的公钥，不能为 null
     * @return 公钥对象
     * @throws NullPointerException 当 publicKeyBase64 为 null 时
     */
    public static PublicKey publicKeyFromBase64(String publicKeyBase64) {
        Objects.requireNonNull(publicKeyBase64, "SM2 公钥不能为空");
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64));
            return KeyFactory.getInstance(KEY_FACTORY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
                    .generatePublic(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("SM2 公钥解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 Base64 字符串还原私钥
     *
     * @param privateKeyBase64 Base64 编码的私钥，不能为 null
     * @return 私钥对象
     * @throws NullPointerException 当 privateKeyBase64 为 null 时
     */
    public static PrivateKey privateKeyFromBase64(String privateKeyBase64) {
        Objects.requireNonNull(privateKeyBase64, "SM2 私钥不能为空");
        try {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64));
            return KeyFactory.getInstance(KEY_FACTORY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
                    .generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("SM2 私钥解析失败: " + e.getMessage(), e);
        }
    }
}
