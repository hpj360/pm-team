package com.redteam.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体类
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class UserEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 状态（0-禁用，1-正常）
     */
    private Integer status;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 最后登录时间
     */
    private java.time.LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;

    /**
     * 是否启用 MFA 多因素认证（v2.3 新增）
     */
    private Boolean mfaEnabled;

    /**
     * MFA 密钥（SM4 加密后存储，v2.3 新增）
     */
    private String mfaSecret;

    /**
     * 密码最后更新时间（v2.3 新增）
     */
    private java.time.LocalDateTime passwordUpdatedAt;

    /**
     * 许可等级 1-4（v4.2.3 新增）
     * 1-PUBLIC 2-INTERNAL 3-CONFIDENTIAL 4-SECRET 99-管理员
     */
    private Integer clearanceLevel;
}
