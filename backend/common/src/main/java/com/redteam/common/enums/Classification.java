package com.redteam.common.enums;

import lombok.Getter;

/**
 * 文件密级枚举
 *
 * <p>密级与许可等级（clearance_level）对应关系：</p>
 * <ul>
 *   <li>PUBLIC(1) - 公开：所有用户可访问</li>
 *   <li>INTERNAL(2) - 内部：许可等级 ≥ 2 的用户可访问</li>
 *   <li>CONFIDENTIAL(3) - 秘密：许可等级 ≥ 3 的用户可访问</li>
 *   <li>SECRET(4) - 机密：许可等级 ≥ 4 的用户可访问</li>
 * </ul>
 * <p>许可等级 99 为管理员，绕过所有密级校验。</p>
 *
 * @author 红方团队
 */
@Getter
public enum Classification {

    /**
     * 公开
     */
    PUBLIC("PUBLIC", 1, "公开"),

    /**
     * 内部
     */
    INTERNAL("INTERNAL", 2, "内部"),

    /**
     * 秘密
     */
    CONFIDENTIAL("CONFIDENTIAL", 3, "秘密"),

    /**
     * 机密
     */
    SECRET("SECRET", 4, "机密");

    /**
     * 密级编码
     */
    private final String code;

    /**
     * 密级等级（与用户许可等级对比）
     */
    private final int level;

    /**
     * 中文标签
     */
    private final String label;

    Classification(String code, int level, String label) {
        this.code = code;
        this.level = level;
        this.label = label;
    }

    /**
     * 根据编码解析密级
     *
     * @param code 密级编码
     * @return 密级枚举，未知编码返回 PUBLIC
     */
    public static Classification fromCode(String code) {
        if (code == null) {
            return PUBLIC;
        }
        for (Classification c : values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }
        return PUBLIC;
    }

    /**
     * 判断指定许可等级是否可以访问当前密级
     *
     * <p>管理员（许可等级 99）绕过所有密级校验。</p>
     *
     * @param clearanceLevel 用户许可等级
     * @return true 表示允许访问
     */
    public boolean isAccessibleBy(int clearanceLevel) {
        // 管理员绕过校验
        if (clearanceLevel >= 99) {
            return true;
        }
        return clearanceLevel >= this.level;
    }
}
