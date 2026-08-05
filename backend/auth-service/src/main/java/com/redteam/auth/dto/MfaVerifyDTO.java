package com.redteam.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * MFA 验证请求 DTO
 *
 * <p>用于 /auth/mfa/verify 接口，可同时承载两类场景：
 * <ul>
 *   <li>登录第二阶段验证：携带 mfaToken（第一阶段返回的临时 Token）</li>
 *   <li>MFA 启用确认：不携带 mfaToken（已登录用户在 setup 后验证）</li>
 * </ul>
 * 验证码既可为 TOTP 6 位动态码，也可为备用码。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "MFA验证请求")
public class MfaVerifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * MFA 验证码（TOTP 动态码或备用码）
     */
    @Schema(description = "MFA验证码")
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * MFA 临时 Token（登录第二阶段验证时必填，启用确认时为空）
     */
    @Schema(description = "MFA临时Token（登录第二阶段必填）")
    private String mfaToken;
}
