package com.redteam.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redteam.auth.config.CryptoProperties;
import com.redteam.auth.dto.LoginDTO;
import com.redteam.auth.dto.LoginVO;
import com.redteam.auth.dto.UserDTO;
import com.redteam.auth.entity.UserEntity;
import com.redteam.auth.mapper.UserMapper;
import com.redteam.auth.service.MfaService;
import com.redteam.auth.service.UserService;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.common.util.JwtUtil;
import com.redteam.common.util.UserContext;
import com.redteam.common.util.crypto.Sm3Util;
import com.redteam.common.util.crypto.Sm4Util;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现
 *
 * <p>认证流程采用国密算法增强：
 * <ul>
 *   <li>密码哈希：SM3 + 随机盐值（替代 BCrypt），存储格式 {salt}${hash}</li>
 *   <li>敏感字段：邮箱、手机号使用 SM4 对称加密存储</li>
 *   <li>登录 Token：access token 使用 HMAC（兼容），refresh token 使用 SM2 签名</li>
 *   <li>MFA 两阶段登录：第一阶段验证密码后返回 mfaToken（5 分钟有效），
 *       第二阶段通过 /auth/mfa/verify 验证 MFA 码后发放正式 token</li>
 *   <li>登出：Token 加入 Redis 黑名单，validateToken 校验黑名单</li>
 * </ul>
 * </p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    /**
     * MFA 临时 Token 有效期（5 分钟，毫秒）
     */
    private static final long MFA_TOKEN_EXPIRATION_MS = 5 * 60 * 1000L;

    /**
     * Token 黑名单 Redis Key 前缀
     */
    private static final String REDIS_KEY_TOKEN_BLACKLIST_PREFIX = "jwt:blacklist:";

    /**
     * MFA 临时 Token 阶段标识
     */
    private static final String MFA_PHASE_LOGIN = "login";

    /**
     * 密码盐值长度（字节）
     */
    private static final int SALT_LENGTH = 8;

    /**
     * 密码存储分隔符
     */
    private static final String PASSWORD_SEPARATOR = "$";

    /**
     * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MfaService mfaService;
    private final CryptoProperties cryptoProperties;
    private final RedissonClient redissonClient;

    /**
     * 用户登录
     *
     * @param loginDTO 登录请求
     * @return 登录响应
     */
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        Objects.requireNonNull(loginDTO, "登录请求不能为空");
        log.info("用户登录第一阶段: username={}", loginDTO.getUsername());

        UserEntity user = getByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        if (!passwordMatches(loginDTO.getPassword(), user.getPassword())) {
            log.warn("用户密码错误: username={}", loginDTO.getUsername());
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }

        // 启用 MFA：返回 mfaToken，进入第二阶段
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            String mfaToken = generateMfaToken(user.getId(), user.getUsername());
            LoginVO vo = new LoginVO();
            vo.setMfaRequired(true);
            vo.setMfaToken(mfaToken);
            return vo;
        }

        // 未启用 MFA：直接发放 token
        updateLastLogin(user.getId());
        log.info("用户登录成功: username={}", user.getUsername());
        return issueTokens(user);
    }

    /**
     * MFA 二阶段登录验证
     *
     * @param mfaToken 第一阶段返回的 MFA 临时 Token
     * @param code     MFA 验证码
     * @return 登录响应
     */
    @Override
    public LoginVO completeMfaLogin(String mfaToken, String code) {
        if (mfaToken == null || mfaToken.isBlank()) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        log.info("用户登录第二阶段 MFA 验证");

        Long userId;
        String username;
        try {
            Claims claims = JwtUtil.parseToken(mfaToken);
            userId = claims.get("userId", Long.class);
            username = claims.get("username", String.class);
            String phase = claims.get("mfaPhase", String.class);
            if (!MFA_PHASE_LOGIN.equals(phase)) {
                throw new BusinessException(ResultCode.TOKEN_INVALID);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("MFA Token 解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        if (userId == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        // 先验证 TOTP，再验证备用码
        boolean verified = mfaService.verifyMfa(userId, code) || mfaService.verifyBackupCode(userId, code);
        if (!verified) {
            log.warn("MFA 验证码错误: userId={}", userId);
            throw new BusinessException("MFA 验证码错误");
        }

        UserEntity user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        updateLastLogin(user.getId());
        log.info("用户 MFA 登录成功: username={}", username);
        return issueTokens(user);
    }

    /**
     * 用户登出
     *
     * @param token Token
     */
    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        blacklistToken(token);
        UserContext.clear();
        log.info("用户登出成功");
    }

    /**
     * 用户注册
     *
     * @param username 用户名
     * @param password 密码
     * @param email    邮箱
     * @return 用户信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO register(String username, String password, String email) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码不能为空");
        }

        if (getByUsername(username) != null) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(hashPassword(password));
        user.setNickname(username);
        user.setEmail(encryptSensitive(email));
        user.setStatus(1);
        user.setMfaEnabled(false);
        user.setPasswordUpdatedAt(LocalDateTime.now());
        baseMapper.insert(user);
        log.info("用户注册成功: username={}", username);
        return toUserDTO(user);
    }

    /**
     * 获取当前用户信息
     *
     * @return 用户信息
     */
    @Override
    public UserDTO getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        UserEntity user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toUserDTO(user);
    }

    /**
     * 根据用户名获取用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    @Override
    public UserEntity getByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return baseMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
    }

    /**
     * 更新用户信息
     *
     * @param userId   用户ID
     * @param nickname 昵称
     * @param email    邮箱
     * @param phone    手机号
     * @return 用户信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO updateUserInfo(Long userId, String nickname, String email, String phone) {
        UserEntity user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (nickname != null && !nickname.isBlank()) {
            user.setNickname(nickname);
        }
        if (email != null) {
            user.setEmail(encryptSensitive(email));
        }
        if (phone != null) {
            user.setPhone(encryptSensitive(phone));
        }
        baseMapper.updateById(user);
        log.info("更新用户信息成功: userId={}", userId);
        return toUserDTO(user);
    }

    /**
     * 修改密码
     *
     * @param userId      用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        UserEntity user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordMatches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        baseMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .set(UserEntity::getPassword, hashPassword(newPassword))
                .set(UserEntity::getPasswordUpdatedAt, LocalDateTime.now())
                .eq(UserEntity::getId, userId));
        log.info("修改密码成功: userId={}", userId);
        return true;
    }

    /**
     * 重置密码
     *
     * @param userId      用户ID
     * @param newPassword 新密码
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能为空");
        }
        baseMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .set(UserEntity::getPassword, hashPassword(newPassword))
                .set(UserEntity::getPasswordUpdatedAt, LocalDateTime.now())
                .eq(UserEntity::getId, userId));
        log.info("重置密码成功: userId={}", userId);
        return true;
    }

    /**
     * 刷新Token
     *
     * @param token 旧Token
     * @return 新Token（SM2 签名）
     */
    @Override
    public String refreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        if (isBlacklisted(token)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        Long userId;
        String username;
        try {
            Claims claims = JwtUtil.parseToken(token);
            userId = claims.get("userId", Long.class);
            username = claims.get("username", String.class);
        } catch (Exception hmacEx) {
            try {
                Map<String, Object> claims = JwtUtil.parseAndVerifyWithSm2(token,
                        cryptoProperties.getSm2PublicKey());
                userId = JwtUtil.getUserIdFromClaims(claims);
                username = JwtUtil.getUsernameFromClaims(claims);
            } catch (Exception sm2Ex) {
                throw new BusinessException(ResultCode.TOKEN_INVALID);
            }
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }
        log.info("刷新 Token: userId={}", userId);
        return issueSm2RefreshToken(userId, username);
    }

    /**
     * 验证Token
     *
     * @param token Token
     * @return 是否有效
     */
    @Override
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (isBlacklisted(token)) {
            return false;
        }
        if (JwtUtil.validateToken(token)) {
            return true;
        }
        if (cryptoProperties.isSm2Ready()) {
            return JwtUtil.validateSm2Token(token, cryptoProperties.getSm2PublicKey());
        }
        return false;
    }

    /**
     * 获取用户权限列表
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public List<String> getUserPermissions(Long userId) {
        // TODO: 接入角色权限表后实现具体查询
        return Collections.emptyList();
    }

    /**
     * 获取用户角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<String> getUserRoles(Long userId) {
        // TODO: 接入角色表后实现具体查询
        return Collections.emptyList();
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 签发 access + refresh token
     *
     * @param user 用户实体
     * @return 登录响应
     */
    private LoginVO issueTokens(UserEntity user) {
        String accessToken = JwtUtil.generateToken(user.getId(), user.getUsername());
        String refreshToken = issueSm2RefreshToken(user.getId(), user.getUsername());

        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(JwtUtil.getDefaultExpiration() / 1000L);
        vo.setMfaRequired(false);
        vo.setUserInfo(toUserDTO(user));
        return vo;
    }

    /**
     * 生成 SM2 签名的 refresh token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return SM2 签名的 JWT
     */
    private String issueSm2RefreshToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("tokenType", "refresh");
        return JwtUtil.signWithSm2(cryptoProperties.getSm2PrivateKey(), claims);
    }

    /**
     * 生成 MFA 临时 Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return MFA 临时 Token（5 分钟有效）
     */
    private String generateMfaToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("mfaPhase", MFA_PHASE_LOGIN);
        return JwtUtil.generateToken(userId, username, claims, MFA_TOKEN_EXPIRATION_MS);
    }

    /**
     * 更新最后登录时间
     *
     * @param userId 用户ID
     */
    private void updateLastLogin(Long userId) {
        try {
            baseMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                    .set(UserEntity::getLastLoginTime, LocalDateTime.now())
                    .eq(UserEntity::getId, userId));
        } catch (Exception e) {
            log.warn("更新最后登录时间失败: userId={}", userId, e);
        }
    }

    /**
     * 密码哈希（SM3 + 随机盐值）
     *
     * @param password 明文密码
     * @return 存储格式 {salt}${hash}
     */
    private String hashPassword(String password) {
        String salt = generateSalt();
        String hash = Sm3Util.digestHex(salt + password);
        return salt + PASSWORD_SEPARATOR + hash;
    }

    /**
     * 校验密码
     *
     * @param rawPassword 明文密码
     * @param storedHash  存储的哈希值
     * @return 一致返回 true，否则返回 false
     */
    private boolean passwordMatches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        int idx = storedHash.indexOf(PASSWORD_SEPARATOR);
        if (idx <= 0) {
            return false;
        }
        String salt = storedHash.substring(0, idx);
        String hash = storedHash.substring(idx + 1);
        return Sm3Util.verifyDigest(salt + rawPassword, hash);
    }

    /**
     * 生成随机盐值（16 位十六进制字符串）
     *
     * @return 盐值
     */
    private String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * SM4 加密敏感字段
     *
     * @param plain 明文
     * @return 密文（Base64），明文为空则原样返回
     */
    private String encryptSensitive(String plain) {
        if (plain == null || plain.isBlank()) {
            return plain;
        }
        return Sm4Util.encrypt(cryptoProperties.getSm4Key(), plain);
    }

    /**
     * SM4 解密敏感字段
     *
     * @param cipher 密文
     * @return 明文，解密失败则原样返回（兼容历史明文数据）
     */
    private String decryptSensitive(String cipher) {
        if (cipher == null || cipher.isBlank()) {
            return cipher;
        }
        try {
            return Sm4Util.decrypt(cryptoProperties.getSm4Key(), cipher);
        } catch (Exception e) {
            log.debug("敏感字段解密失败，按明文处理");
            return cipher;
        }
    }

    /**
     * 加入 Token 黑名单
     *
     * @param token Token
     */
    private void blacklistToken(String token) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY_TOKEN_BLACKLIST_PREFIX + token);
            bucket.set("1", JwtUtil.getDefaultExpiration(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("加入 Token 黑名单失败", e);
        }
    }

    /**
     * 判断 Token 是否在黑名单中
     *
     * @param token Token
     * @return 在黑名单中返回 true
     */
    private boolean isBlacklisted(String token) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY_TOKEN_BLACKLIST_PREFIX + token);
            return bucket.isExists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 用户实体转 DTO（解密敏感字段）
     *
     * @param user 用户实体
     * @return 用户 DTO
     */
    private UserDTO toUserDTO(UserEntity user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(decryptSensitive(user.getEmail()));
        dto.setPhone(decryptSensitive(user.getPhone()));
        dto.setAvatar(user.getAvatar());
        dto.setStatus(user.getStatus());
        dto.setDeptId(user.getDeptId());
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setCreateTime(user.getCreateTime());
        return dto;
    }

    /**
     * 字节数组转十六进制小写字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        String hex = "0123456789abcdef";
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hex.charAt(v >>> 4);
            hexChars[i * 2 + 1] = hex.charAt(v & 0x0F);
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }
}
