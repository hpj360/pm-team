package com.redteam.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.redteam.auth.dto.MfaSetupVO;
import com.redteam.auth.entity.UserEntity;
import com.redteam.auth.mapper.UserMapper;
import com.redteam.auth.service.MfaService;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.common.util.crypto.Sm3Util;
import com.redteam.common.util.crypto.Sm4Util;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * MFA 多因素认证服务实现
 *
 * <p>基于 TOTP（RFC 6238）实现双因素认证，结合国密算法保护敏感数据：
 * <ul>
 *   <li>TOTP 密钥：使用 dev.samstevens.totp 生成 Base32 密钥，SM4 加密后存 Redis（待启用）或 UserEntity（已启用）</li>
 *   <li>备用码：10 个随机码，SM3 哈希后存 Redis Set，一次性使用</li>
 * </ul>
 * </p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    /**
     * Redis 待启用 MFA 密钥 Key 前缀
     */
    private static final String REDIS_KEY_SECRET_PREFIX = "mfa:secret:";

    /**
     * Redis 备用码集合 Key 前缀
     */
    private static final String REDIS_KEY_BACKUP_PREFIX = "mfa:backup:";

    /**
     * 待启用密钥有效期（分钟）
     */
    private static final long PENDING_SECRET_TTL_MINUTES = 30L;

    /**
     * 备用码数量
     */
    private static final int BACKUP_CODE_COUNT = 10;

    /**
     * 备用码字符集
     */
    private static final String BACKUP_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * 二维码签发者
     */
    private static final String ISSUER = "RedTeam";

    /**
     * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * TOTP 密钥生成器
     */
    private static final SecretGenerator SECRET_GENERATOR = new DefaultSecretGenerator();

    /**
     * TOTP 验证器（允许 ±1 个时间窗口的偏差）
     */
    private static final DefaultCodeVerifier CODE_VERIFIER;

    static {
        CODE_VERIFIER = new DefaultCodeVerifier(new DefaultCodeGenerator(HashingAlgorithm.SHA1),
                new SystemTimeProvider());
        CODE_VERIFIER.setAllowedTimePeriodDiscrepancy(1);
    }

    private final UserMapper userMapper;
    private final RedissonClient redissonClient;
    private final com.redteam.auth.config.CryptoProperties cryptoProperties;

    /**
     * 为用户初始化 MFA
     *
     * @param userId 用户ID
     * @return MFA 初始化响应
     */
    @Override
    public MfaSetupVO setupMfa(Long userId) {
        Objects.requireNonNull(userId, "用户ID不能为空");
        log.info("初始化 MFA: userId={}", userId);

        UserEntity user = loadUser(userId);
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new BusinessException(ResultCode.CONFLICT, "MFA 已启用，请先禁用后再重新初始化");
        }

        // 生成 TOTP 密钥
        String secret = SECRET_GENERATOR.generate();

        // SM4 加密后存入 Redis（待启用状态）
        String encryptedSecret = Sm4Util.encrypt(cryptoProperties.getSm4Key(), secret);
        try {
            RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY_SECRET_PREFIX + userId);
            bucket.set(encryptedSecret, PENDING_SECRET_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("写入待启用 MFA 密钥到 Redis 失败: userId={}", userId, e);
            throw new BusinessException("MFA 初始化失败，请稍后重试");
        }

        // 生成备用码并 SM3 哈希后存入 Redis
        List<String> backupCodes = generateBackupCodes();
        try {
            RSet<String> backupSet = redissonClient.getSet(REDIS_KEY_BACKUP_PREFIX + userId);
            backupSet.clear();
            for (String code : backupCodes) {
                backupSet.add(Sm3Util.digestHex(normalizeBackupCode(code)));
            }
        } catch (Exception e) {
            log.error("写入备用码到 Redis 失败: userId={}", userId, e);
            throw new BusinessException("MFA 初始化失败，请稍后重试");
        }

        // 构建二维码 URL
        String qrCodeUrl = buildQrCodeUrl(user.getUsername(), secret);

        MfaSetupVO vo = new MfaSetupVO();
        vo.setSecret(secret);
        vo.setQrCodeUrl(qrCodeUrl);
        vo.setBackupCodes(backupCodes);
        return vo;
    }

    /**
     * 验证 MFA 码
     *
     * @param userId 用户ID
     * @param code   验证码
     * @return 验证通过返回 true，否则返回 false
     */
    @Override
    public boolean verifyMfa(Long userId, String code) {
        Objects.requireNonNull(userId, "用户ID不能为空");
        if (code == null || code.isBlank()) {
            return false;
        }
        log.info("验证 MFA: userId={}", userId);

        UserEntity user = loadUser(userId);

        // 检查是否存在待启用密钥（setup 确认场景）
        String pendingEncrypted = getPendingSecret(userId);
        if (pendingEncrypted != null) {
            // setup 确认场景
            String rawSecret = Sm4Util.decrypt(cryptoProperties.getSm4Key(), pendingEncrypted);
            if (!verifyTotp(rawSecret, code)) {
                log.warn("MFA 启用确认失败（验证码错误）: userId={}", userId);
                return false;
            }
            // 验证通过：持久化密钥到 UserEntity 并启用 MFA
            enableMfaForUser(userId, pendingEncrypted);
            // 删除待启用密钥
            deletePendingSecret(userId);
            log.info("MFA 启用成功: userId={}", userId);
            return true;
        }

        // 登录验证场景：验证 UserEntity 中已存储的密钥
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            log.warn("用户未启用 MFA: userId={}", userId);
            return false;
        }
        String storedEncrypted = user.getMfaSecret();
        if (storedEncrypted == null) {
            log.warn("用户 MFA 密钥缺失: userId={}", userId);
            return false;
        }
        String rawSecret = Sm4Util.decrypt(cryptoProperties.getSm4Key(), storedEncrypted);
        boolean valid = verifyTotp(rawSecret, code);
        if (!valid) {
            log.warn("MFA 验证码错误: userId={}", userId);
        }
        return valid;
    }

    /**
     * 禁用 MFA
     *
     * @param userId 用户ID
     * @param code   验证码
     * @return 禁用成功返回 true，验证失败返回 false
     */
    @Override
    public boolean disableMfa(Long userId, String code) {
        Objects.requireNonNull(userId, "用户ID不能为空");
        if (code == null || code.isBlank()) {
            return false;
        }
        log.info("禁用 MFA: userId={}", userId);

        UserEntity user = loadUser(userId);
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            log.warn("用户未启用 MFA，无法禁用: userId={}", userId);
            return false;
        }

        // 先验证 TOTP，再验证备用码
        boolean verified = verifyMfa(userId, code) || verifyBackupCode(userId, code);
        if (!verified) {
            log.warn("禁用 MFA 验证失败: userId={}", userId);
            return false;
        }

        // 清除 UserEntity 中的 MFA 字段
        try {
            userMapper.update(null, Wrappers.<UserEntity>lambdaUpdate()
                    .set(UserEntity::getMfaEnabled, false)
                    .set(UserEntity::getMfaSecret, null)
                    .eq(UserEntity::getId, userId));
        } catch (Exception e) {
            log.error("更新用户 MFA 状态失败: userId={}", userId, e);
            throw new BusinessException("禁用 MFA 失败，请稍后重试");
        }

        // 清除 Redis 中的密钥与备用码
        deletePendingSecret(userId);
        deleteBackupCodes(userId);
        log.info("MFA 禁用成功: userId={}", userId);
        return true;
    }

    /**
     * 验证备用码
     *
     * @param userId 用户ID
     * @param code   备用码
     * @return 验证通过返回 true，否则返回 false
     */
    @Override
    public boolean verifyBackupCode(Long userId, String code) {
        Objects.requireNonNull(userId, "用户ID不能为空");
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = normalizeBackupCode(code);
        String hash = Sm3Util.digestHex(normalized);
        try {
            RSet<String> backupSet = redissonClient.getSet(REDIS_KEY_BACKUP_PREFIX + userId);
            if (backupSet.contains(hash)) {
                backupSet.remove(hash);
                log.info("备用码验证成功: userId={}", userId);
                return true;
            }
        } catch (Exception e) {
            log.error("读取备用码集合失败: userId={}", userId, e);
            throw new BusinessException("备用码验证失败，请稍后重试");
        }
        log.warn("备用码无效: userId={}", userId);
        return false;
    }

    /**
     * 查询用户 MFA 启用状态
     *
     * @param userId 用户ID
     * @return 已启用返回 true，否则返回 false
     */
    @Override
    public boolean isMfaEnabled(Long userId) {
        Objects.requireNonNull(userId, "用户ID不能为空");
        UserEntity user = userMapper.selectById(userId);
        return user != null && Boolean.TRUE.equals(user.getMfaEnabled());
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 加载用户实体，不存在则抛业务异常
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    private UserEntity loadUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 验证 TOTP 验证码
     *
     * @param rawSecret Base32 原始密钥
     * @param code      验证码
     * @return 验证通过返回 true，否则返回 false
     */
    private boolean verifyTotp(String rawSecret, String code) {
        if (rawSecret == null || code == null) {
            return false;
        }
        try {
            return CODE_VERIFIER.isValid(rawSecret, code);
        } catch (Exception e) {
            log.warn("TOTP 验证异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 持久化 MFA 密钥并启用 MFA
     *
     * @param userId           用户ID
     * @param encryptedSecret SM4 加密后的密钥
     */
    private void enableMfaForUser(Long userId, String encryptedSecret) {
        try {
            userMapper.update(null, Wrappers.<UserEntity>lambdaUpdate()
                    .set(UserEntity::getMfaEnabled, true)
                    .set(UserEntity::getMfaSecret, encryptedSecret)
                    .eq(UserEntity::getId, userId));
        } catch (Exception e) {
            log.error("启用 MFA 持久化失败: userId={}", userId, e);
            throw new BusinessException("MFA 启用失败，请稍后重试");
        }
    }

    /**
     * 获取待启用密钥
     *
     * @param userId 用户ID
     * @return 加密的待启用密钥，不存在返回 null
     */
    private String getPendingSecret(Long userId) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY_SECRET_PREFIX + userId);
            return bucket.get();
        } catch (Exception e) {
            log.error("读取待启用 MFA 密钥失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 删除待启用密钥
     *
     * @param userId 用户ID
     */
    private void deletePendingSecret(Long userId) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY_SECRET_PREFIX + userId);
            bucket.delete();
        } catch (Exception e) {
            log.warn("删除待启用 MFA 密钥失败: userId={}", userId, e);
        }
    }

    /**
     * 删除备用码集合
     *
     * @param userId 用户ID
     */
    private void deleteBackupCodes(Long userId) {
        try {
            RSet<String> backupSet = redissonClient.getSet(REDIS_KEY_BACKUP_PREFIX + userId);
            backupSet.delete();
        } catch (Exception e) {
            log.warn("删除备用码集合失败: userId={}", userId, e);
        }
    }

    /**
     * 构建二维码 URL
     *
     * @param username 用户名
     * @param secret   Base32 密钥
     * @return otpauth 协议 URL
     */
    private String buildQrCodeUrl(String username, String secret) {
        QrData data = new QrData.Builder()
                .label(ISSUER + ":" + username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    /**
     * 生成备用码列表
     *
     * @return 10 个明文备用码
     */
    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes.add(generateOneBackupCode());
        }
        return codes;
    }

    /**
     * 生成单个备用码（格式 XXXX-XXXX）
     *
     * @return 备用码
     */
    private String generateOneBackupCode() {
        char[] buf = new char[8];
        for (int i = 0; i < 8; i++) {
            buf[i] = BACKUP_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(BACKUP_CODE_CHARS.length()));
        }
        return new String(buf, 0, 4) + "-" + new String(buf, 4, 4);
    }

    /**
     * 规范化备用码（去除分隔符并大写）
     *
     * @param code 原始备用码
     * @return 规范化后的备用码
     */
    private String normalizeBackupCode(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }
}
