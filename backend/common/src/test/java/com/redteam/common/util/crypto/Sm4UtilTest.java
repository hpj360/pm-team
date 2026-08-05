package com.redteam.common.util.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SM4 工具类单元测试
 *
 * @author 红方团队
 */
class Sm4UtilTest {

    @Nested
    @DisplayName("generateKey: 密钥生成")
    class GenerateKeyTests {

        @Test
        @DisplayName("生成 Base64 字符串密钥")
        void generateKey_returnsBase64String() {
            String key = Sm4Util.generateKey();
            assertNotNull(key);
            byte[] decoded = Base64.getDecoder().decode(key);
            assertEquals(Sm4Util.KEY_LENGTH, decoded.length);
        }

        @Test
        @DisplayName("生成 16 字节密钥")
        void generateKeyBytes_returns16Bytes() {
            byte[] key = Sm4Util.generateKeyBytes();
            assertEquals(Sm4Util.KEY_LENGTH, key.length);
        }

        @Test
        @DisplayName("每次生成不同密钥")
        void generateKey_differentEachTime() {
            assertNotEquals(Sm4Util.generateKey(), Sm4Util.generateKey());
        }
    }

    @Nested
    @DisplayName("encrypt/decrypt: 加解密一致性")
    class EncryptDecryptTests {

        @Test
        @DisplayName("字符串加解密一致")
        void encryptDecrypt_string_consistent() {
            String key = Sm4Util.generateKey();
            String plaintext = "敏感数据-邮箱：test@redteam.com";
            String cipher = Sm4Util.encrypt(key, plaintext);
            assertNotEquals(plaintext, cipher);
            assertEquals(plaintext, Sm4Util.decrypt(key, cipher));
        }

        @Test
        @DisplayName("字节数组加解密一致")
        void encryptDecrypt_bytes_consistent() {
            byte[] key = Sm4Util.generateKeyBytes();
            byte[] data = "hello sm4".getBytes(StandardCharsets.UTF_8);
            byte[] cipher = Sm4Util.encrypt(key, data);
            assertArrayEquals(data, Sm4Util.decrypt(key, cipher));
        }

        @Test
        @DisplayName("中文加解密一致")
        void encryptDecrypt_chinese_consistent() {
            String key = Sm4Util.generateKey();
            String plaintext = "红方团队国密测试数据123！@#";
            String cipher = Sm4Util.encrypt(key, plaintext);
            assertEquals(plaintext, Sm4Util.decrypt(key, cipher));
        }

        @Test
        @DisplayName("空字符串加解密一致")
        void encryptDecrypt_emptyString_consistent() {
            String key = Sm4Util.generateKey();
            String cipher = Sm4Util.encrypt(key, "");
            assertEquals("", Sm4Util.decrypt(key, cipher));
        }

        @Test
        @DisplayName("长文本加解密一致（跨分组）")
        void encryptDecrypt_longText_consistent() {
            String key = Sm4Util.generateKey();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                sb.append("段落").append(i).append(";");
            }
            String plaintext = sb.toString();
            String cipher = Sm4Util.encrypt(key, plaintext);
            assertEquals(plaintext, Sm4Util.decrypt(key, cipher));
        }
    }

    @Nested
    @DisplayName("CBC 模式 IV 行为")
    class CbcIvTests {

        @Test
        @DisplayName("相同明文每次密文不同（随机 IV）")
        void encrypt_samePlaintext_differentCiphertext() {
            String key = Sm4Util.generateKey();
            String plaintext = "same-input";
            String c1 = Sm4Util.encrypt(key, plaintext);
            String c2 = Sm4Util.encrypt(key, plaintext);
            assertNotEquals(c1, c2);
        }

        @Test
        @DisplayName("密文前 16 字节为 IV")
        void ciphertext_containsIvPrefix() {
            byte[] key = Sm4Util.generateKeyBytes();
            byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
            byte[] cipher = Sm4Util.encrypt(key, data);
            assertTrue(cipher.length > Sm4Util.IV_LENGTH);
        }
    }

    @Nested
    @DisplayName("参数校验")
    class ValidationTests {

        @Test
        @DisplayName("密钥长度错误抛出 IllegalArgumentException")
        void encrypt_wrongKeyLength_throws() {
            byte[] badKey = new byte[10];
            assertThrows(IllegalArgumentException.class,
                    () -> Sm4Util.encrypt(badKey, "data".getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        @DisplayName("解密密钥长度错误抛出 IllegalArgumentException")
        void decrypt_wrongKeyLength_throws() {
            byte[] badKey = new byte[20];
            byte[] cipher = new byte[32];
            assertThrows(IllegalArgumentException.class, () -> Sm4Util.decrypt(badKey, cipher));
        }

        @Test
        @DisplayName("解密密文过短抛出 IllegalArgumentException")
        void decrypt_shortCiphertext_throws() {
            byte[] key = Sm4Util.generateKeyBytes();
            byte[] shortCipher = new byte[8];
            assertThrows(IllegalArgumentException.class, () -> Sm4Util.decrypt(key, shortCipher));
        }

        @Test
        @DisplayName("错误密钥解密抛出异常")
        void decrypt_wrongKey_throws() {
            String key1 = Sm4Util.generateKey();
            String key2 = Sm4Util.generateKey();
            String cipher = Sm4Util.encrypt(key1, "data");
            assertThrows(IllegalStateException.class, () -> Sm4Util.decrypt(key2, cipher));
        }
    }

    @Nested
    @DisplayName("空安全检查")
    class NullSafetyTests {

        @Test
        @DisplayName("encrypt(null, ...) 抛出 NPE")
        void encrypt_nullKey_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm4Util.encrypt((String) null, "x"));
        }

        @Test
        @DisplayName("encrypt(..., null) 抛出 NPE")
        void encrypt_nullPlaintext_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm4Util.encrypt("key", null));
        }

        @Test
        @DisplayName("decrypt(null, ...) 抛出 NPE")
        void decrypt_nullKey_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm4Util.decrypt((String) null, "x"));
        }

        @Test
        @DisplayName("decrypt(..., null) 抛出 NPE")
        void decrypt_nullCipher_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm4Util.decrypt("key", null));
        }
    }
}
