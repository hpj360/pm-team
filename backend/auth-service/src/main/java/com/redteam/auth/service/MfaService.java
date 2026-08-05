package com.redteam.auth.service;

import com.redteam.auth.dto.MfaSetupVO;

/**
 * MFA 多因素认证服务接口
 *
 * <p>基于 TOTP（RFC 6238）实现，密钥与备用码通过国密算法保护后存储：
 * <ul>
 *   <li>TOTP 密钥：SM4 加密后存 Redis（待启用）或 UserEntity（已启用）</li>
 *   <li>备用码：SM3 哈希后存 Redis Set，一次性使用</li>
 * </ul>
 * </p>
 *
 * @author 红方团队
 */
public interface MfaService {

    /**
     * 为用户初始化 MFA
     *
     * <p>生成 TOTP 密钥与 10 个备用码，密钥经 SM4 加密后存入 Redis（待启用状态，30 分钟有效）。
     * 返回明文密钥、二维码 URL 与明文备用码（仅返回一次）。</p>
     *
     * @param userId 用户ID，不能为 null
     * @return MFA 初始化响应
     */
    MfaSetupVO setupMfa(Long userId);

    /**
     * 验证 MFA 码
     *
     * <p>同时支持两类场景：
     * <ul>
     *   <li>启用确认：检测到 Redis 中存在待启用密钥时，验证通过后持久化到 UserEntity 并启用 MFA</li>
     *   <li>登录验证：无待启用密钥时，验证 UserEntity 中已存储的密钥</li>
     * </ul>
     * </p>
     *
     * @param userId 用户ID，不能为 null
     * @param code   验证码（TOTP 6 位动态码），不能为空
     * @return 验证通过返回 true，否则返回 false
     */
    boolean verifyMfa(Long userId, String code);

    /**
     * 禁用 MFA
     *
     * <p>需先通过 MFA 验证码校验，验证通过后清除 UserEntity 中的密钥与启用标记，
     * 并删除 Redis 中的备用码。</p>
     *
     * @param userId 用户ID，不能为 null
     * @param code   验证码，不能为空
     * @return 禁用成功返回 true，验证失败返回 false
     */
    boolean disableMfa(Long userId, String code);

    /**
     * 验证备用码
     *
     * <p>备用码为一次性使用，验证通过后从 Redis 中移除。</p>
     *
     * @param userId 用户ID，不能为 null
     * @param code   备用码，不能为空
     * @return 验证通过返回 true，否则返回 false
     */
    boolean verifyBackupCode(Long userId, String code);

    /**
     * 查询用户 MFA 启用状态
     *
     * @param userId 用户ID，不能为 null
     * @return 已启用返回 true，否则返回 false
     */
    boolean isMfaEnabled(Long userId);
}
