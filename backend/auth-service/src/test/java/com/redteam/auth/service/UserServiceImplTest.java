package com.redteam.auth.service;

import com.redteam.auth.config.CryptoProperties;
import com.redteam.auth.dto.LoginDTO;
import com.redteam.auth.dto.LoginVO;
import com.redteam.auth.dto.UserDTO;
import com.redteam.auth.entity.UserEntity;
import com.redteam.auth.mapper.UserMapper;
import com.redteam.auth.service.impl.UserServiceImpl;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.util.JwtUtil;
import com.redteam.common.util.UserContext;
import com.redteam.common.util.crypto.Sm2Util;
import com.redteam.common.util.crypto.Sm3Util;
import com.redteam.common.util.crypto.Sm4Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 用户服务实现单元测试
 *
 * @author 红方团队
 */
class UserServiceImplTest {

    private UserMapper userMapper;
    private MfaService mfaService;
    private RedissonClient redissonClient;
    private CryptoProperties cryptoProperties;
    private UserServiceImpl userService;
    private RBucket<String> blacklistBucket;

    private static final Long USER_ID = 2001L;
    private static final String USERNAME = "alice";
    private static final String PASSWORD = "Pass@1234";
    private static final String EMAIL = "alice@redteam.com";
    private static final String PHONE = "13800001111";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        mfaService = mock(MfaService.class);
        redissonClient = mock(RedissonClient.class);
        blacklistBucket = mock(RBucket.class);

        cryptoProperties = new CryptoProperties();
        cryptoProperties.setSm4Key(Sm4Util.generateKey());
        String[] kp = Sm2Util.generateKeyPairBase64();
        cryptoProperties.setSm2PublicKey(kp[0]);
        cryptoProperties.setSm2PrivateKey(kp[1]);

        userService = new UserServiceImpl(mfaService, cryptoProperties, redissonClient);
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);

        when(redissonClient.getBucket(anyString())).thenReturn(blacklistBucket);
        when(blacklistBucket.isExists()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 构造已哈希的密码（与服务 hashPassword 格式一致：salt$hash）
     */
    private String buildStoredPassword(String plain) {
        String salt = "abcd1234abcd1234";
        return salt + "$" + Sm3Util.digestHex(salt + plain);
    }

    /**
     * 构造用户实体（模拟数据库存储状态：邮箱/手机号已 SM4 加密）
     */
    private UserEntity buildStoredUser(boolean mfaEnabled) {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setPassword(buildStoredPassword(PASSWORD));
        user.setNickname(USERNAME);
        user.setEmail(Sm4Util.encrypt(cryptoProperties.getSm4Key(), EMAIL));
        user.setPhone(Sm4Util.encrypt(cryptoProperties.getSm4Key(), PHONE));
        user.setStatus(1);
        user.setMfaEnabled(mfaEnabled);
        return user;
    }

    private LoginDTO buildLoginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    // ==================== register ====================

    @Nested
    @DisplayName("register: 注册")
    class RegisterTests {

        @Test
        @DisplayName("注册成功：密码 SM3 哈希、邮箱 SM4 加密、返回解密后的 DTO")
        void register_success() {
            when(userMapper.selectOne(any())).thenReturn(null);
            when(userMapper.insert(any())).thenReturn(1);

            UserDTO dto = userService.register(USERNAME, PASSWORD, EMAIL);

            assertNotNull(dto);
            assertEquals(USERNAME, dto.getUsername());

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userMapper).insert(captor.capture());
            UserEntity saved = captor.getValue();

            // 密码已哈希（含分隔符，非明文）
            assertTrue(saved.getPassword().contains("$"));
            assertNotEquals(PASSWORD, saved.getPassword());
            // 邮箱已加密（非明文）
            assertNotEquals(EMAIL, saved.getEmail());
            // 返回的 DTO 邮箱已解密
            assertEquals(EMAIL, dto.getEmail());
            assertEquals(Boolean.FALSE, saved.getMfaEnabled());
            assertNotNull(saved.getPasswordUpdatedAt());
        }

        @Test
        @DisplayName("用户名已存在：抛出 USER_EXISTS")
        void register_userExists_throws() {
            when(userMapper.selectOne(any())).thenReturn(buildStoredUser(false));
            assertThrows(BusinessException.class, () -> userService.register(USERNAME, PASSWORD, EMAIL));
        }

        @Test
        @DisplayName("用户名为空：抛出 PARAM_ERROR")
        void register_blankUsername_throws() {
            assertThrows(BusinessException.class, () -> userService.register("", PASSWORD, EMAIL));
            assertThrows(BusinessException.class, () -> userService.register(null, PASSWORD, EMAIL));
        }

        @Test
        @DisplayName("密码为空：抛出 PARAM_ERROR")
        void register_blankPassword_throws() {
            assertThrows(BusinessException.class, () -> userService.register(USERNAME, "", EMAIL));
        }
    }

    // ==================== login ====================

    @Nested
    @DisplayName("login: 登录第一阶段")
    class LoginTests {

        @Test
        @DisplayName("无 MFA：返回 access/refresh token 与用户信息")
        void login_noMfa_success() {
            when(userMapper.selectOne(any())).thenReturn(buildStoredUser(false));
            when(userMapper.update(eq(null), any())).thenReturn(1);

            LoginVO vo = userService.login(buildLoginDTO(USERNAME, PASSWORD));

            assertNotNull(vo.getAccessToken());
            assertNotNull(vo.getRefreshToken());
            assertFalse(vo.getMfaRequired());
            assertNotNull(vo.getUserInfo());
            assertEquals(USERNAME, vo.getUserInfo().getUsername());
            assertEquals(EMAIL, vo.getUserInfo().getEmail());
            assertEquals("Bearer", vo.getTokenType());
        }

        @Test
        @DisplayName("启用 MFA：返回 mfaToken，不返回 access token")
        void login_withMfa_returnsMfaToken() {
            when(userMapper.selectOne(any())).thenReturn(buildStoredUser(true));

            LoginVO vo = userService.login(buildLoginDTO(USERNAME, PASSWORD));

            assertTrue(vo.getMfaRequired());
            assertNotNull(vo.getMfaToken());
            assertNull(vo.getAccessToken());
            assertNull(vo.getUserInfo());
        }

        @Test
        @DisplayName("密码错误：抛出 LOGIN_ERROR")
        void login_wrongPassword_throws() {
            when(userMapper.selectOne(any())).thenReturn(buildStoredUser(false));
            assertThrows(BusinessException.class,
                    () -> userService.login(buildLoginDTO(USERNAME, "WrongPass")));
        }

        @Test
        @DisplayName("用户不存在：抛出 LOGIN_ERROR")
        void login_userNotFound_throws() {
            when(userMapper.selectOne(any())).thenReturn(null);
            assertThrows(BusinessException.class,
                    () -> userService.login(buildLoginDTO("ghost", PASSWORD)));
        }

        @Test
        @DisplayName("账号禁用：抛出 ACCOUNT_DISABLED")
        void login_disabled_throws() {
            UserEntity user = buildStoredUser(false);
            user.setStatus(0);
            when(userMapper.selectOne(any())).thenReturn(user);
            assertThrows(BusinessException.class,
                    () -> userService.login(buildLoginDTO(USERNAME, PASSWORD)));
        }
    }

    // ==================== completeMfaLogin ====================

    @Nested
    @DisplayName("completeMfaLogin: MFA 二阶段验证")
    class CompleteMfaLoginTests {

        @Test
        @DisplayName("验证码正确：返回正式 token")
        void completeMfaLogin_validCode() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("mfaPhase", "login");
            String mfaToken = JwtUtil.generateToken(USER_ID, USERNAME, claims, 5 * 60 * 1000L);

            when(mfaService.verifyMfa(USER_ID, "123456")).thenReturn(true);
            when(userMapper.selectById(USER_ID)).thenReturn(buildStoredUser(true));
            when(userMapper.update(eq(null), any())).thenReturn(1);

            LoginVO vo = userService.completeMfaLogin(mfaToken, "123456");

            assertNotNull(vo.getAccessToken());
            assertNotNull(vo.getRefreshToken());
            assertFalse(vo.getMfaRequired());
            assertNotNull(vo.getUserInfo());
        }

        @Test
        @DisplayName("TOTP 失败但备用码通过：返回正式 token")
        void completeMfaLogin_backupCode_success() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("mfaPhase", "login");
            String mfaToken = JwtUtil.generateToken(USER_ID, USERNAME, claims, 5 * 60 * 1000L);

            when(mfaService.verifyMfa(USER_ID, "backup-code")).thenReturn(false);
            when(mfaService.verifyBackupCode(USER_ID, "backup-code")).thenReturn(true);
            when(userMapper.selectById(USER_ID)).thenReturn(buildStoredUser(true));
            when(userMapper.update(eq(null), any())).thenReturn(1);

            LoginVO vo = userService.completeMfaLogin(mfaToken, "backup-code");
            assertNotNull(vo.getAccessToken());
        }

        @Test
        @DisplayName("验证码错误：抛出业务异常")
        void completeMfaLogin_invalidCode_throws() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("mfaPhase", "login");
            String mfaToken = JwtUtil.generateToken(USER_ID, USERNAME, claims, 5 * 60 * 1000L);

            when(mfaService.verifyMfa(anyLong(), anyString())).thenReturn(false);
            when(mfaService.verifyBackupCode(anyLong(), anyString())).thenReturn(false);

            assertThrows(BusinessException.class, () -> userService.completeMfaLogin(mfaToken, "000000"));
        }

        @Test
        @DisplayName("mfaToken 无效：抛出 TOKEN_INVALID")
        void completeMfaLogin_invalidToken_throws() {
            assertThrows(BusinessException.class,
                    () -> userService.completeMfaLogin("invalid.token.here", "123456"));
        }

        @Test
        @DisplayName("mfaToken 为空：抛出 TOKEN_INVALID")
        void completeMfaLogin_blankToken_throws() {
            assertThrows(BusinessException.class,
                    () -> userService.completeMfaLogin("", "123456"));
            assertThrows(BusinessException.class,
                    () -> userService.completeMfaLogin(null, "123456"));
        }

        @Test
        @DisplayName("mfaToken 阶段标识不符：抛出 TOKEN_INVALID")
        void completeMfaLogin_wrongPhase_throws() {
            // 普通 token，无 mfaPhase 声明
            String regularToken = JwtUtil.generateToken(USER_ID, USERNAME);
            assertThrows(BusinessException.class,
                    () -> userService.completeMfaLogin(regularToken, "123456"));
        }

        @Test
        @DisplayName("用户不存在：抛出 USER_NOT_FOUND")
        void completeMfaLogin_userNotFound_throws() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("mfaPhase", "login");
            String mfaToken = JwtUtil.generateToken(USER_ID, USERNAME, claims, 5 * 60 * 1000L);

            when(mfaService.verifyMfa(anyLong(), anyString())).thenReturn(true);
            when(userMapper.selectById(USER_ID)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> userService.completeMfaLogin(mfaToken, "123456"));
        }
    }

    // ==================== updatePassword / resetPassword ====================

    @Nested
    @DisplayName("密码管理")
    class PasswordTests {

        @Test
        @DisplayName("修改密码成功：旧密码正确")
        void updatePassword_success() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildStoredUser(false));
            when(userMapper.update(eq(null), any())).thenReturn(1);

            assertTrue(userService.updatePassword(USER_ID, PASSWORD, "NewPass@2024"));
            verify(userMapper).update(eq(null), any());
        }

        @Test
        @DisplayName("修改密码失败：旧密码错误")
        void updatePassword_wrongOld_throws() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildStoredUser(false));
            assertThrows(BusinessException.class,
                    () -> userService.updatePassword(USER_ID, "WrongOld", "NewPass"));
        }

        @Test
        @DisplayName("修改密码失败：用户不存在")
        void updatePassword_userNotFound_throws() {
            when(userMapper.selectById(USER_ID)).thenReturn(null);
            assertThrows(BusinessException.class,
                    () -> userService.updatePassword(USER_ID, PASSWORD, "NewPass"));
        }

        @Test
        @DisplayName("重置密码成功")
        void resetPassword_success() {
            when(userMapper.update(eq(null), any())).thenReturn(1);
            assertTrue(userService.resetPassword(USER_ID, "ResetPass@2024"));
            verify(userMapper).update(eq(null), any());
        }

        @Test
        @DisplayName("重置密码失败：新密码为空")
        void resetPassword_blank_throws() {
            assertThrows(BusinessException.class,
                    () -> userService.resetPassword(USER_ID, ""));
            assertThrows(BusinessException.class,
                    () -> userService.resetPassword(USER_ID, null));
        }
    }

    // ==================== refreshToken ====================

    @Nested
    @DisplayName("refreshToken: 刷新 Token")
    class RefreshTokenTests {

        @Test
        @DisplayName("HMAC token 刷新为 SM2 token")
        void refreshToken_hmacToken() {
            String hmacToken = JwtUtil.generateToken(USER_ID, USERNAME);

            String newToken = userService.refreshToken(hmacToken);

            assertNotNull(newToken);
            assertNotEquals(hmacToken, newToken);
            // 验证新 token 为 SM2 签名
            Map<String, Object> claims = JwtUtil.parseAndVerifyWithSm2(newToken,
                    cryptoProperties.getSm2PublicKey());
            assertEquals(USERNAME, JwtUtil.getUsernameFromClaims(claims));
        }

        @Test
        @DisplayName("SM2 token 刷新为新 SM2 token")
        void refreshToken_sm2Token() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", USER_ID);
            claims.put("username", USERNAME);
            String sm2Token = JwtUtil.signWithSm2(cryptoProperties.getSm2PrivateKey(), claims);

            String newToken = userService.refreshToken(sm2Token);

            assertNotNull(newToken);
            assertNotEquals(sm2Token, newToken);
        }

        @Test
        @DisplayName("无效 token：抛出 TOKEN_INVALID")
        void refreshToken_invalid_throws() {
            assertThrows(BusinessException.class, () -> userService.refreshToken("invalid.token"));
        }

        @Test
        @DisplayName("空 token：抛出 TOKEN_INVALID")
        void refreshToken_blank_throws() {
            assertThrows(BusinessException.class, () -> userService.refreshToken(""));
            assertThrows(BusinessException.class, () -> userService.refreshToken(null));
        }

        @Test
        @DisplayName("黑名单 token：抛出 TOKEN_INVALID")
        void refreshToken_blacklisted_throws() {
            when(blacklistBucket.isExists()).thenReturn(true);
            String token = JwtUtil.generateToken(USER_ID, USERNAME);
            assertThrows(BusinessException.class, () -> userService.refreshToken(token));
        }
    }

    // ==================== validateToken ====================

    @Nested
    @DisplayName("validateToken: 校验 Token")
    class ValidateTokenTests {

        @Test
        @DisplayName("有效 HMAC token：返回 true")
        void validateToken_hmac_true() {
            String token = JwtUtil.generateToken(USER_ID, USERNAME);
            assertTrue(userService.validateToken(token));
        }

        @Test
        @DisplayName("有效 SM2 token：返回 true")
        void validateToken_sm2_true() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", USER_ID);
            claims.put("username", USERNAME);
            String token = JwtUtil.signWithSm2(cryptoProperties.getSm2PrivateKey(), claims);
            assertTrue(userService.validateToken(token));
        }

        @Test
        @DisplayName("无效 token：返回 false")
        void validateToken_invalid_false() {
            assertFalse(userService.validateToken("invalid.token.here"));
        }

        @Test
        @DisplayName("空 token：返回 false")
        void validateToken_blank_false() {
            assertFalse(userService.validateToken(""));
            assertFalse(userService.validateToken(null));
        }

        @Test
        @DisplayName("黑名单 token：返回 false")
        void validateToken_blacklisted_false() {
            when(blacklistBucket.isExists()).thenReturn(true);
            String token = JwtUtil.generateToken(USER_ID, USERNAME);
            assertFalse(userService.validateToken(token));
        }
    }

    // ==================== logout ====================

    @Nested
    @DisplayName("logout: 登出")
    class LogoutTests {

        @Test
        @DisplayName("登出成功：加入黑名单")
        void logout_success() {
            userService.logout("some-token");
            verify(blacklistBucket).set(eq("1"), anyLong(), any());
        }

        @Test
        @DisplayName("空 token：不加入黑名单")
        void logout_blankToken_noBlacklist() {
            userService.logout("");
            userService.logout(null);
            verify(blacklistBucket, never()).set(anyString(), anyLong(), any());
        }
    }

    // ==================== getByUsername / getCurrentUser / updateUserInfo ====================

    @Nested
    @DisplayName("用户信息查询与更新")
    class UserInfoTests {

        @Test
        @DisplayName("getByUsername：返回用户实体")
        void getByUsername_success() {
            when(userMapper.selectOne(any())).thenReturn(buildStoredUser(false));
            UserEntity user = userService.getByUsername(USERNAME);
            assertNotNull(user);
            assertEquals(USERNAME, user.getUsername());
        }

        @Test
        @DisplayName("getByUsername 空字符串：返回 null")
        void getByUsername_blank_returnsNull() {
            assertNull(userService.getByUsername(""));
            assertNull(userService.getByUsername(null));
        }

        @Test
        @DisplayName("getCurrentUser：返回解密后的 DTO")
        void getCurrentUser_success() {
            UserContext.setUserId(USER_ID);
            when(userMapper.selectById(USER_ID)).thenReturn(buildStoredUser(false));

            UserDTO dto = userService.getCurrentUser();
            assertEquals(USERNAME, dto.getUsername());
            assertEquals(EMAIL, dto.getEmail());
            assertEquals(PHONE, dto.getPhone());
        }

        @Test
        @DisplayName("getCurrentUser 未登录：抛出 UNAUTHORIZED")
        void getCurrentUser_notLogin_throws() {
            UserContext.clear();
            assertThrows(BusinessException.class, () -> userService.getCurrentUser());
        }

        @Test
        @DisplayName("getCurrentUser 用户不存在：抛出 USER_NOT_FOUND")
        void getCurrentUser_userNotFound_throws() {
            UserContext.setUserId(USER_ID);
            when(userMapper.selectById(USER_ID)).thenReturn(null);
            assertThrows(BusinessException.class, () -> userService.getCurrentUser());
        }

        @Test
        @DisplayName("updateUserInfo：更新并返回 DTO")
        void updateUserInfo_success() {
            when(userMapper.selectById(USER_ID)).thenReturn(buildStoredUser(false));
            when(userMapper.updateById(any())).thenReturn(1);

            UserDTO dto = userService.updateUserInfo(USER_ID, "新昵称", "new@redteam.com", "13900002222");

            assertEquals("新昵称", dto.getNickname());
            assertEquals("new@redteam.com", dto.getEmail());
            assertEquals("13900002222", dto.getPhone());
            verify(userMapper).updateById(any());
        }

        @Test
        @DisplayName("updateUserInfo 用户不存在：抛出 USER_NOT_FOUND")
        void updateUserInfo_userNotFound_throws() {
            when(userMapper.selectById(USER_ID)).thenReturn(null);
            assertThrows(BusinessException.class,
                    () -> userService.updateUserInfo(USER_ID, "nick", "e@x.com", "138"));
        }
    }

    // ==================== 角色权限（占位实现） ====================

    @Nested
    @DisplayName("角色权限查询（占位）")
    class RolePermissionTests {

        @Test
        @DisplayName("getUserPermissions 返回空列表")
        void getUserPermissions_returnsEmpty() {
            assertTrue(userService.getUserPermissions(USER_ID).isEmpty());
        }

        @Test
        @DisplayName("getUserRoles 返回空列表")
        void getUserRoles_returnsEmpty() {
            assertTrue(userService.getUserRoles(USER_ID).isEmpty());
        }
    }
}
