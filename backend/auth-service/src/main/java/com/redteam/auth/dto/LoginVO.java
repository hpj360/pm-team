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
     * 用户信息
     */
    @Schema(description = "用户信息")
    private UserDTO userInfo;
}
