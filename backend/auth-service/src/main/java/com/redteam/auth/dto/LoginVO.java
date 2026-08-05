package com.redteam.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "登录响应")
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 访问Token
     */
    @Schema(description = "访问Token")
    private String accessToken;

    /**
     * 刷新Token（v2.3 新增，SM2 签名）
     */
    @Schema(description = "刷新Token")
    private String refreshToken;

    /**
     * Token类型
     */
    @Schema(description = "Token类型")
    private String tokenType = "Bearer";

    /**
     * 过期时间（秒）
     */
    @Schema(description = "过期时间（秒）")
    private Long expiresIn;

    /**
     * 是否需要 MFA 验证（v2.3 新增，为 true 时表示登录第一阶段通过，需进行第二阶段 MFA 验证）
     */
    @Schema(description = "是否需要MFA验证")
    private Boolean mfaRequired;

    /**
     * MFA 临时 Token（v2.3 新增，仅在 mfaRequired=true 时返回，5 分钟有效）
     */
    @Schema(description = "MFA临时Token")
    private String mfaToken;

    /**
     * 用户信息
     */
    @Schema(description = "用户信息")
    private UserDTO userInfo;
}
