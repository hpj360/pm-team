package com.redteam.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * MFA 初始化响应 VO
 *
 * <p>用户调用 /auth/mfa/setup 后返回，包含 TOTP 密钥、二维码 URL 和一次性备用码列表。
 * 备用码以明文形式仅返回一次，服务端仅存储其 SM3 哈希值。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "MFA初始化响应")
public class MfaSetupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * TOTP 密钥（Base32 编码，用于手动输入到认证器）
     */
    @Schema(description = "TOTP密钥（Base32）")
    private String secret;

    /**
     * 二维码 URL（otpauth://totp/RedTeam:{username}?secret={secret}&issuer=RedTeam）
     */
    @Schema(description = "二维码URL")
    private String qrCodeUrl;

    /**
     * 备用码列表（10 个，明文仅返回一次）
     */
    @Schema(description = "备用码列表")
    private List<String> backupCodes;
}
