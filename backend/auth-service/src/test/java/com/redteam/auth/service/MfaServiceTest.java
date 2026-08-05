package com.redteam.auth.service;

import com.redteam.auth.config.CryptoProperties;
import com.redteam.auth.dto.MfaSetupVO;
import com.redteam.auth.entity.UserEntity;
import com.redteam.auth.mapper.UserMapper;
import com.redteam.auth.service.impl.MfaServiceImpl;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.util.crypto.Sm3Util;
import com.redteam.common.util.crypto.Sm4Util;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MFA 服务单元测试
 *
 * @author 红方团队
 */
class MfaServiceTest {

    private UserMapper userMapper;
    private RedissonClient redissonClient;
    private CryptoProperties cryptoProperties;
    private MfaServiceImpl mfaService;

    private RBucket<String> secretBucket;
    private RSet<String> backupSet;

    private static final Long USER_ID = 1001L;
    private static final String SECRET_KEY_PREFIX = "mfa:secret:";
    private static final String BACKUP_KEY_PREFIX = "mfa:backup:";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        redissonClient = mock(RedissonClient.class);
        secretBucket = mock(RBucket.class);
        backupSet = mock(RSet.class);

        cryptoProperties = new CryptoProperties();
        cryptoProperties.setSm4Key(Sm4Util.generateKey());

        mfaService = new MfaServiceImpl(userMapper, redissonClient, cryptoProperties);

        // 默认返回 bucket/set mock
        when(redissonClient.getBucket(eq(SECRET_KEY_PREFIX + USER_ID))).thenReturn(secretBucket);
        when(redissonClient.getSet(eq(BACKUP_KEY_PREFIX + USER_ID))).thenReturn(backupSet);
    }

    /**
     * 构造用户实体
     */
    private UserEntity buildUser(boolean mfaEnabled, String mfaSecret) {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername("tester");
        user.setMfaEnabled(mfaEnabled);
        user.setMfaSecret(mfaSecret);
        user.setStatus(1);
        return user;
    }

    /**
     * 生成当前时间窗口的合法 TOTP 验证码
     */
    private String generateValidTotp(String secret) throws Exception {
        long counter = new SystemTimeProvider().getTime() / 30;
        return new DefaultCodeGenerator(HashingAlgorithm.SHA1).generate(secret, counter);
    }

    // ==================== setupMfa ====================

    @Nested
    @DisplayName("setupMfa: 初始化 MFA")
    class SetupMfaTests {

        @Test
        @DisplayName("初始化成功：返回密钥、二维码 URL、10 个备用码")
        void setupMfa_success() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(false, null));

            MfaSetupVO vo = mfaService.setupMfa(USER_ID);

            assertNotNull(vo.getSecret());
            assertNotNull(vo.getQrCodeUrl());
            assertTrue(vo.getQrCodeUrl().startsWith("otpauth://totp/RedTeam:tester"));
            assertTrue(vo.getQrCodeUrl().contains("issuer=RedTeam"));
            assertNotNull(vo.getBackupCodes());
            assertEquals(10, vo.getBackupCodes().size());

            verify(secretBucket).set(anyString(), eq(30L), eq(TimeUnit.MINUTES));
            verify(backupSet).clear();
            verify(backupSet, times(10)).add(anyString());
        }

        @Test
        @DisplayName("备用码格式为 XXXX-XXXX")
        void setupMfa_backupCodeFormat() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(false, null));

            MfaSetupVO vo = mfaService.setupMfa(USER_ID);

            for (String code : vo.getBackupCodes()) {
                assertEquals(9, code.length());
                assertEquals('-', code.charAt(4));
            }
        }

        @Test
        @DisplayName("用户不存在：抛出业务异常")
        void setupMfa_userNotFound_throws() {
            when(userMapper.selectById(USER_ID)).thenReturn(null);
            assertThrows(BusinessException.class, () -> mfaService.setupMfa(USER_ID));
        }

        @Test
        @DisplayName("MFA 已启用：抛出业务异常")
        void setupMfa_alreadyEnabled_throws() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, "enc"));
            assertThrows(BusinessException.class, () -> mfaService.setupMfa(USER_ID));
        }

        @Test
        @DisplayName("userId 为 null：抛出 NPE")
        void setupMfa_nullUserId_throwsNpe() {
            assertThrows(NullPointerException.class, () -> mfaService.setupMfa(null));
        }
    }

    // ==================== verifyMfa ====================

    @Nested
    @DisplayName("verifyMfa: 验证 MFA 码")
    class VerifyMfaTests {

        @Test
        @DisplayName("启用确认（待启用密钥）：验证码正确，持久化并启用")
        void verifyMfa_setupConfirmation_validCode() throws Exception {
            String secret = new DefaultSecretGenerator().generate();
            String encrypted = Sm4Util.encrypt(cryptoProperties.getSm4Key(), secret);
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(false, null));
            when(secretBucket.get()).thenReturn(encrypted);
            String code = generateValidTotp(secret);

            boolean result = mfaService.verifyMfa(USER_ID, code);

            assertTrue(result);
            verify(userMapper).update(eq(null), any());
            verify(secretBucket).delete();
        }

        @Test
        @DisplayName("启用确认：验证码错误，返回 false")
        void verifyMfa_setupConfirmation_invalidCode() {
            String secret = new DefaultSecretGenerator().generate();
            String encrypted = Sm4Util.encrypt(cryptoProperties.getSm4Key(), secret);
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(false, null));
            when(secretBucket.get()).thenReturn(encrypted);

            boolean result = mfaService.verifyMfa(USER_ID, "000000");

            assertFalse(result);
            verify(userMapper, never()).update(eq(null), any());
        }

        @Test
        @DisplayName("登录验证（已启用）：验证码正确，返回 true")
        void verifyMfa_login_validCode() throws Exception {
            String secret = new DefaultSecretGenerator().generate();
            String encrypted = Sm4Util.encrypt(cryptoProperties.getSm4Key(), secret);
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, encrypted));
            when(secretBucket.get()).thenReturn(null);
            String code = generateValidTotp(secret);

            boolean result = mfaService.verifyMfa(USER_ID, code);

            assertTrue(result);
        }

        @Test
        @DisplayName("登录验证：验证码错误，返回 false")
        void verifyMfa_login_invalidCode() {
            String secret = new DefaultSecretGenerator().generate();
            String encrypted = Sm4Util.encrypt(cryptoProperties.getSm4Key(), secret);
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, encrypted));
            when(secretBucket.get()).thenReturn(null);

            boolean result = mfaService.verifyMfa(USER_ID, "999999");

            assertFalse(result);
        }

        @Test
        @DisplayName("用户未启用 MFA：返回 false")
        void verifyMfa_notEnabled_returnsFalse() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(false, null));
            when(secretBucket.get()).thenReturn(null);

            assertFalse(mfaService.verifyMfa(USER_ID, "123456"));
        }

        @Test
        @DisplayName("已启用但密钥缺失：返回 false")
        void verifyMfa_enabledButNoSecret_returnsFalse() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, null));
            when(secretBucket.get()).thenReturn(null);

            assertFalse(mfaService.verifyMfa(USER_ID, "123456"));
        }

        @Test
        @DisplayName("验证码为空：返回 false")
        void verifyMfa_blankCode_returnsFalse() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, "enc"));
            assertFalse(mfaService.verifyMfa(USER_ID, ""));
            assertFalse(mfaService.verifyMfa(USER_ID, null));
        }

        @Test
        @DisplayName("用户不存在：抛出业务异常")
        void verifyMfa_userNotFound_throws() {
            when(userMapper.selectById(USER_ID)).thenReturn(null);
            assertThrows(BusinessException.class, () -> mfaService.verifyMfa(USER_ID, "123456"));
        }
    }

    // ==================== verifyBackupCode ====================

    @Nested
    @DisplayName("verifyBackupCode: 备用码验证")
    class VerifyBackupCodeTests {

        @Test
        @DisplayName("备用码正确：返回 true 并移除")
        void verifyBackupCode_valid() {
            String plain = "ABCD-1234";
            String normalized = "ABCD1234";
            String hash = Sm3Util.digestHex(normalized);
            when(backupSet.contains(hash)).thenReturn(true);

            boolean result = mfaService.verifyBackupCode(USER_ID, plain);

            assertTrue(result);
            verify(backupSet).remove(hash);
        }

        @Test
        @DisplayName("备用码错误：返回 false")
        void verifyBackupCode_invalid() {
            when(backupSet.contains(anyString())).thenReturn(false);
            assertFalse(mfaService.verifyBackupCode(USER_ID, "ZZZZ-9999"));
            verify(backupSet, never()).remove(anyString());
        }

        @Test
        @DisplayName("备用码大小写与分隔符不敏感")
        void verifyBackupCode_caseAndSeparatorInsensitive() {
            String plain = "abcd-1234";
            String hash = Sm3Util.digestHex("ABCD1234");
            when(backupSet.contains(hash)).thenReturn(true);

            assertTrue(mfaService.verifyBackupCode(USER_ID, "a b c d-1 2 3 4".replace(" ", "")));
        }

        @Test
        @DisplayName("备用码为空：返回 false")
        void verifyBackupCode_blank_returnsFalse() {
            assertFalse(mfaService.verifyBackupCode(USER_ID, ""));
            assertFalse(mfaService.verifyBackupCode(USER_ID, null));
        }

        @Test
        @DisplayName("userId 为 null：抛出 NPE")
        void verifyBackupCode_nullUserId_throwsNpe() {
            assertThrows(NullPointerException.class, () -> mfaService.verifyBackupCode(null, "x"));
        }
    }

    // ==================== disableMfa ====================

    @Nested
    @DisplayName("disableMfa: 禁用 MFA")
    class DisableMfaTests {

        @Test
        @DisplayName("验证码正确：禁用成功，清除密钥与备用码")
        void disableMfa_validCode() throws Exception {
            String secret = new DefaultSecretGenerator().generate();
            String encrypted = Sm4Util.encrypt(cryptoProperties.getSm4Key(), secret);
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, encrypted));
            when(secretBucket.get()).thenReturn(null);
            String code = generateValidTotp(secret);

            boolean result = mfaService.disableMfa(USER_ID, code);

            assertTrue(result);
            verify(userMapper).update(eq(null), any());
            verify(secretBucket).delete();
            verify(backupSet).delete();
        }

        @Test
        @DisplayName("验证码错误：返回 false")
        void disableMfa_invalidCode() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, "enc"));
            when(secretBucket.get()).thenReturn(null);
            when(backupSet.contains(anyString())).thenReturn(false);

            boolean result = mfaService.disableMfa(USER_ID, "000000");

            assertFalse(result);
            verify(userMapper, never()).update(eq(null), any());
        }

        @Test
        @DisplayName("未启用 MFA：返回 false")
        void disableMfa_notEnabled_returnsFalse() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(false, null));

            assertFalse(mfaService.disableMfa(USER_ID, "123456"));
        }

        @Test
        @DisplayName("验证码为空：返回 false")
        void disableMfa_blankCode_returnsFalse() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, "enc"));
            assertFalse(mfaService.disableMfa(USER_ID, ""));
            assertFalse(mfaService.disableMfa(USER_ID, null));
        }
    }

    // ==================== isMfaEnabled ====================

    @Nested
    @DisplayName("isMfaEnabled: 查询启用状态")
    class IsMfaEnabledTests {

        @Test
        @DisplayName("已启用：返回 true")
        void isMfaEnabled_true() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(true, "enc"));
            assertTrue(mfaService.isMfaEnabled(USER_ID));
        }

        @Test
        @DisplayName("未启用：返回 false")
        void isMfaEnabled_false() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildUser(false, null));
            assertFalse(mfaService.isMfaEnabled(USER_ID));
        }

        @Test
        @DisplayName("用户不存在：返回 false")
        void isMfaEnabled_userNotFound_returnsFalse() {
            when(userMapper.selectById(USER_ID)).thenReturn(null);
            assertFalse(mfaService.isMfaEnabled(USER_ID));
        }

        @Test
        @DisplayName("userId 为 null：抛出 NPE")
        void isMfaEnabled_nullUserId_throwsNpe() {
            assertThrows(NullPointerException.class, () -> mfaService.isMfaEnabled(null));
        }
    }
}
