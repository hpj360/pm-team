package com.redteam.common.util.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SM3 工具类单元测试
 *
 * @author 红方团队
 */
class Sm3UtilTest {

    /**
     * SM3 标准测试向量：SM3("abc")
     */
    private static final String SM3_ABC = "66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0";

    @Nested
    @DisplayName("digest: 哈希计算")
    class DigestTests {

        @Test
        @DisplayName("标准向量：SM3(abc) 与已知值比对")
        void digest_abc_matchesStandardVector() {
            byte[] result = Sm3Util.digest("abc".getBytes(StandardCharsets.UTF_8));
            assertEquals(Sm3Util.DIGEST_LENGTH, result.length);
            assertEquals(SM3_ABC, Sm3Util.digestHex("abc"));
        }

        @Test
        @DisplayName("字符串与字节数组结果一致")
        void digest_stringAndBytes_consistent() {
            byte[] bytes = Sm3Util.digest("hello".getBytes(StandardCharsets.UTF_8));
            byte[] fromStr = Sm3Util.digest("hello");
            assertArrayEquals(bytes, fromStr);
        }

        @Test
        @DisplayName("相同输入产生相同摘要")
        void digest_sameInput_sameOutput() {
            String h1 = Sm3Util.digestHex("test-data");
            String h2 = Sm3Util.digestHex("test-data");
            assertEquals(h1, h2);
        }

        @Test
        @DisplayName("不同输入产生不同摘要")
        void digest_differentInput_differentOutput() {
            assertNotEquals(Sm3Util.digestHex("a"), Sm3Util.digestHex("b"));
        }

        @Test
        @DisplayName("摘要长度为 32 字节")
        void digest_lengthIs32() {
            assertEquals(32, Sm3Util.digest("x").length);
        }
    }

    @Nested
    @DisplayName("digestHex: 十六进制输出")
    class DigestHexTests {

        @Test
        @DisplayName("返回 64 位十六进制字符串")
        void digestHex_lengthIs64() {
            String hex = Sm3Util.digestHex("any-input");
            assertEquals(64, hex.length());
        }

        @Test
        @DisplayName("字节数组版本与字符串版本一致")
        void digestHex_bytesAndString_consistent() {
            String fromBytes = Sm3Util.digestHex("data".getBytes(StandardCharsets.UTF_8));
            String fromString = Sm3Util.digestHex("data");
            assertEquals(fromString, fromBytes);
        }
    }

    @Nested
    @DisplayName("verifyDigest: 哈希验证")
    class VerifyDigestTests {

        @Test
        @DisplayName("正确摘要验证通过")
        void verifyDigest_correct_returnsTrue() {
            String hex = Sm3Util.digestHex("password");
            assertTrue(Sm3Util.verifyDigest("password", hex));
        }

        @Test
        @DisplayName("错误摘要验证失败")
        void verifyDigest_wrong_returnsFalse() {
            assertFalse(Sm3Util.verifyDigest("password", Sm3Util.digestHex("other")));
        }

        @Test
        @DisplayName("字节数组版本验证")
        void verifyDigest_bytes_correct() {
            byte[] data = "content".getBytes(StandardCharsets.UTF_8);
            byte[] digest = Sm3Util.digest(data);
            assertTrue(Sm3Util.verifyDigest(data, digest));
            assertFalse(Sm3Util.verifyDigest(data, new byte[32]));
        }
    }

    @Nested
    @DisplayName("空安全检查")
    class NullSafetyTests {

        @Test
        @DisplayName("digest(null) 抛出 NPE")
        void digest_null_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm3Util.digest((byte[]) null));
        }

        @Test
        @DisplayName("digest((String)null) 抛出 NPE")
        void digest_stringNull_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm3Util.digest((String) null));
        }

        @Test
        @DisplayName("digestHex(null) 抛出 NPE")
        void digestHex_null_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm3Util.digestHex((String) null));
        }

        @Test
        @DisplayName("verifyDigest(null, ...) 抛出 NPE")
        void verifyDigest_nullData_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm3Util.verifyDigest((byte[]) null, new byte[32]));
        }

        @Test
        @DisplayName("verifyDigest(..., null) 抛出 NPE")
        void verifyDigest_nullExpected_throwsNpe() {
            assertThrows(NullPointerException.class,
                    () -> Sm3Util.verifyDigest("x".getBytes(StandardCharsets.UTF_8), null));
        }

        @Test
        @DisplayName("verifyDigest((String)null, ...) 抛出 NPE")
        void verifyDigest_stringNull_throwsNpe() {
            assertThrows(NullPointerException.class, () -> Sm3Util.verifyDigest(null, "abc"));
        }
    }
}
