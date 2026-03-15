package com.redteam.common.exception;

import com.redteam.common.result.ResultCode;

/**
 * 认证异常
 *
 * @author 红方团队
 */
public class AuthException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(ResultCode.UNAUTHORIZED, message);
    }

    public AuthException(ResultCode resultCode) {
        super(resultCode);
    }

    /**
     * 快速创建认证异常
     *
     * @param message 错误消息
     * @return 认证异常
     */
    public static AuthException of(String message) {
        return new AuthException(message);
    }

    /**
     * 快速创建认证异常
     *
     * @param resultCode 错误码
     * @return 认证异常
     */
    public static AuthException of(ResultCode resultCode) {
        return new AuthException(resultCode);
    }
}
