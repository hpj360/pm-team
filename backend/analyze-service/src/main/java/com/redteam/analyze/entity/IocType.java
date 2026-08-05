package com.redteam.analyze.entity;

/**
 * IOC 类型常量
 *
 * <p>统一平台 IOC 类型命名，避免大小写与命名风格不一致导致的映射错配。</p>
 *
 * @author 红方团队
 */
public final class IocType {

    private IocType() {
    }

    /**
     * IP 地址
     */
    public static final String IP = "IP";

    /**
     * 域名
     */
    public static final String DOMAIN = "DOMAIN";

    /**
     * URL
     */
    public static final String URL = "URL";

    /**
     * MD5 哈希
     */
    public static final String MD5 = "MD5";

    /**
     * SHA256 哈希
     */
    public static final String SHA256 = "SHA256";

    /**
     * 邮箱
     */
    public static final String EMAIL = "EMAIL";
}
