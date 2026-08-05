package com.redteam.common.util.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SM2 工具类单元测试
 *
 * @author 红方团队
 */
class Sm2UtilTest {

    /**
     * 测试用密钥对（Base64）
     */
    private static String publicKeyBase64;
    private static String privateKeyBase64;
    private static PublicKey publicKey;
    private static PrivateKey privateKey;

    @BeforeAll
    static void initKeys() {
        KeyPair keyPair = Sm2Util.generateKeyPair();
        publicKey = keyPair.getPublic();
        privateKey = keyPair.getPrivate();
        publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    @Nested
    @DisplayName("generateKeyPair: 密钥对生成")
    class GenerateKeyPairTests {

        @Test
        @DisplayName("生成非空密钥对")
        void generateKeyPair_notNull() {
            KeyPair kp = Sm2Util.generateKeyPair();
            assertNotNull(kp);
            assertNotNull(kp.getPublic());
            assertNotNull(kp.getPrivate());
        }

        @Test
        @DisplayName("生成 Base64 密钥对（长度为 2）")
        void generateKeyPairBase64_returnsTwoElements() {
            String[] keys = Sm2Util.generateKeyPairBase64();
            assertEquals(2, keys.length);
            assertNotNull(keys[0]);
            assertNotNull(keys[1]);
            assertNotEquals(keys[0], keys[1]);
        }

        @Test
        @DisplayName("每次生成不同密钥对")
        void generateKeyPair_differentEachTime() {
            String[] k1 = Sm2Util.generateKeyPairBase64();
            String[] k2 = Sm2Util.generateKeyPairBase64();
            assertNotEquals(k1[0], k2[0]);
        }

        @Test
        @DisplayName("Base64 密钥可还原为 Key 对象")
        void publicKeyFromBase64_restoresKey() {
            PublicKey restored = Sm2Util.publicKeyFromBase64(publicKeyBase64);
            assertNotNull(restored);
            assertEquals(publicKey, restored);
        }

        @Test
        @DisplayName("私钥 Base64 可还原")
        void privateKeyFromBase64_restoresKey() {
            PrivateKey restored = Sm2Util.privateKeyFromBase64(privateKeyBase64);
            assertNotNull(restored);
            assertEquals(privateKey, restored);
        }
    }

    @Nested
    @DisplayName("encrypt/decrypt: 加解密")
    class EncryptDecryptTests {

        @Test
        @DisplayName("字符串加解密一致")
        void encryptDecrypt_string_consistent() {
            String plaintext = "SM2-敏感数据";
            String cipher = Sm2Util.encrypt(publicKeyBase64, plaintext);
            assertNotEquals(plaintext, cipher);
            assertEquals(plaintext, Sm2Util.decrypt(privateKeyBase64, cipher));
        }

        @Test
        @DisplayName("字节数组加解密一致")
        void encryptDecrypt_bytes_consistent() {
            byte[] data = "hello sm2".getBytes(StandardCharsets.UTF_8);
            byte[] cipher = Sm2Util.encrypt(publicKey, data);
            assertArrayEquals(data, Sm2Util.decrypt(privateKey, cipher));
        }

        @Test
        @DisplayName("相同明文每次密文不同")
        void encrypt_samePlaintext_differentCiphertext() {
            String c1 = Sm2Util.encrypt(publicKeyBase64, "same");
            String c2 = Sm2Util.encrypt(publicKeyBase64, "same");
            assertNotEquals(c1, c2);
        }

        @Test
        @DisplayName("大数据加解密一致（256 字节）")
        void encryptDecrypt_largeData_consistent() {
            StringBuilder sb = new StringBuilder(256);
            for (int i = 0; i < 256; i++) {
                sb.append((char) ('a' + (i % 26)));
            }
            String plaintext = sb.toString();
            String cipher = Sm2Util.encrypt(publicKeyBase64, plaintext);
            assertEquals(plaintext, Sm2Util.decrypt(privateKeyBase64, cipher));
        }

        @Test
        @DisplayName("错误私钥解密失败")
        void decrypt_wrongKey_throws() {
            String cipher = Sm2Util.encrypt(publicKeyBase64, "data");
            String[] otherKey = Sm2Util.generateKeyPairBase64();
            assertThrows(IllegalStateException.class, () -> Sm2Util.decrypt(otherKey[1], cipher));
        }
    }

    @Nested
    @DisplayName("sign/verify: 签名验签")
    class SignVerifyTests {

        @Test
        @DisplayName("字符串签名验签通过")
        void signVerify_string_valid() {
            String data = "待签名内容-红方";
            String signature = Sm2Util.sign(privateKeyBase64, data);
            assertNotNull(signature);
            assertTrue(Sm2Util.verify(publicKeyBase64, data, signature));
        }

        @Test
        @DisplayName("字节数组签名验签通过")
        void signVerify_bytes_valid() {
            byte[] data = "binary-data".getBytes(StandardCharsets.UTF_8);
            byte[] signature = Sm2Util.sign(privateKey, data);
            assertTrue(Sm2Util.verify(publicKey, data, signature));
        }

        @Test
        @DisplayName("相同数据每次签名不同（随机 k）")
        void sign_sameData_differentSignature() {
            String s1 = Sm2Util.sign(privateKeyBase64, "data");
            String s2 = Sm2Util.sign(privateKeyBase64, "data");
            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("篡改数据验签失败")
        void verify_tamperedData_returnsFalse() {
            String signature = Sm2Util.sign(privateKeyBase64, "original");
            assertFalse(Sm2Util.verify(publicKeyBase64, "tampered", signature));
        }

        @Test
        @DisplayName("篡改签名验签失败")
        void verify_tamperedSignature_returnsFalse() {
            String signature = Sm2Util.sign(privateKeyBase64, "data");
            String tampered = signature.substring(0, signature.length() - 4) + "AAAA";
            assertFalse(Sm2Util.verify(publicKeyBase64, "data", tampered));
        }

        @Test
        @DisplayName("错误公钥验签返回 false")
        void verify_wrongPublicKey_returnsFalse() {
            String signature = Sm2Util.sign(privateKeyBase64, "data");
            String[] otherKey = Sm2Util.generateKeyPairBase64();
            assertFalse(Sm2Util.verify(otherKey[0], "data", signature));
        }
    }

    @Nested
    @DisplayName("空安全检查")
    class NullSafetyTests {

        @Test
        @DisplayName("encrypt(null, ...) 抛出 NPE")
        void encrypt_nullKey_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm2Util.encrypt((String) null, "x"));
        }

        @Test
        @DisplayName("encrypt(..., null) 抛出 NPE")
        void encrypt_nullData_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm2Util.encrypt(publicKeyBase64, (String) null));
        }

        @Test
        @DisplayName("decrypt(null, ...) 抛出 NPE")
        void decrypt_nullKey_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm2Util.decrypt((String) null, "x"));
        }

        @Test
        @DisplayName("sign(null, ...) 抛出 NPE")
        void sign_nullKey_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm2Util.sign((String) null, "x"));
        }

        @Test
        @DisplayName("verify 任一参数为 null 抛出 NPE")
        void verify_nullArgs_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm2Util.verify(null, "x", "y"));
            assertThrows(NullPointerException.class, () -> Sm2Util.verify(publicKeyBase64, null, "y"));
            assertThrows(NullPointerException.class, () -> Sm2Util.verify(publicKeyBase64, "x", null));
        }

        @Test
        @DisplayName("publicKeyFromBase64(null) 抛出 NPE")
        void publicKeyFromBase64_null_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm2Util.publicKeyFromBase64(null));
        }

        @Test
        @DisplayName("privateKeyFromBase64(null) 抛出 NPE")
        void privateKeyFromBase64_null_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm2Util.privateKeyFromBase64(null));
        }

        @Test
        @DisplayName("无效公钥 Base64 抛出 IllegalArgumentException")
        void publicKeyFromBase64_invalid_throws() {
            assertThrows(IllegalArgumentException.class, () -> Sm2Util.publicKeyFromBase64("invalid"));
        }
    }
}
