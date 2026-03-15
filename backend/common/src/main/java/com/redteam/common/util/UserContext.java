package com.redteam.common.util;

import cn.hutool.core.util.StrUtil;

/**
 * 用户上下文工具类
 * 用于存储当前请求的用户信息
 *
 * @author 红方团队
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 设置用户名
     *
     * @param username 用户名
     */
    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 设置Token
     *
     * @param token Token
     */
    public static void setToken(String token) {
        TOKEN.set(token);
    }

    /**
     * 获取Token
     *
     * @return Token
     */
    public static String getToken() {
        return TOKEN.get();
    }

    /**
     * 判断用户是否已登录
     *
     * @return 是否已登录
     */
    public static boolean isLogin() {
        return getUserId() != null && StrUtil.isNotBlank(getToken());
    }

    /**
     * 清除上下文
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        TENANT_ID.remove();
        TOKEN.remove();
    }
}
