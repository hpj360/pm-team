package com.redteam.analyze.client;

/**
 * MISP 客户端调用异常
 *
 * <p>封装 MISP REST API 调用过程中的网络错误、HTTP 非 2xx 响应、
 * JSON 解析失败等异常，便于上层统一捕获与日志记录。</p>
 *
 * @author 红方团队
 */
public class MispException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * HTTP 状态码（网络错误等无状态码场景为 -1）
     */
    private final int statusCode;

    /**
     * 构造 MISP 异常
     *
     * @param message 错误描述
     */
    public MispException(String message) {
        super(message);
        this.statusCode = -1;
    }

    /**
     * 构造 MISP 异常
     *
     * @param message    错误描述
     * @param statusCode HTTP 状态码
     */
    public MispException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * 构造 MISP 异常
     *
     * @param message 错误描述
     * @param cause   原始异常
     */
    public MispException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /**
     * 构造 MISP 异常
     *
     * @param message    错误描述
     * @param statusCode HTTP 状态码
     * @param cause      原始异常
     */
    public MispException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * 获取 HTTP 状态码
     *
     * @return HTTP 状态码，无状态码时返回 -1
     */
    public int getStatusCode() {
        return statusCode;
    }
}
