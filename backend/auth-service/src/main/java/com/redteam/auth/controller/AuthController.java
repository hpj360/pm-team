package com.redteam.auth.controller;

import cn.hutool.core.util.StrUtil;
import com.redteam.auth.dto.LoginDTO;
import com.redteam.auth.dto.LoginVO;
import com.redteam.auth.dto.MfaSetupVO;
import com.redteam.auth.dto.MfaVerifyDTO;
import com.redteam.auth.dto.UserDTO;
import com.redteam.auth.service.MfaService;
import com.redteam.auth.service.UserService;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.Result;
import com.redteam.common.result.ResultCode;
import com.redteam.common.util.JwtUtil;
import com.redteam.common.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证接口", description = "用户登录、注册、权限管理等接口")
public class AuthController {

    private final UserService userService;
    private final MfaService mfaService;

    /**
     * 用户登录
     *
     * @param loginDTO 登录请求
     * @return 登录响应
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录获取Token")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        log.info("用户登录: username={}", loginDTO.getUsername());
        LoginVO loginVO = userService.login(loginDTO);
        return Result.success(loginVO);
    }

    /**
     * 用户登出
     *
     * @param request HTTP请求
     * @return 是否成功
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出清除Token")
    public Result<Void> logout(HttpServletRequest request) {
        log.info("用户登出");

        String token = request.getHeader(JwtUtil.HEADER_NAME);
        if (token != null && token.startsWith(JwtUtil.TOKEN_PREFIX)) {
            token = token.substring(JwtUtil.TOKEN_PREFIX.length());
            userService.logout(token);
        }

        return Result.success();
    }

    /**
     * 用户注册
     *
     * @param username 用户名
     * @param password 密码
     * @param email    邮箱
     * @return 用户信息
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户")
    public Result<UserDTO> register(
            @Parameter(description = "用户名") @RequestParam("username") String username,
            @Parameter(description = "密码") @RequestParam("password") String password,
            @Parameter(description = "邮箱") @RequestParam(value = "email", required = false) String email) {

        log.info("用户注册: username={}", username);
        UserDTO user = userService.register(username, password, email);
        return Result.success(user);
    }

    /**
     * 获取当前用户信息
     *
     * @return 用户信息
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前用户", description = "获取当前登录用户信息")
    public Result<UserDTO> getCurrentUser() {
        log.info("获取当前用户信息");
        UserDTO user = userService.getCurrentUser();
        return Result.success(user);
    }

    /**
     * 更新用户信息
     *
     * @param nickname 昵称
     * @param email    邮箱
     * @param phone    手机号
     * @return 用户信息
     */
    @PutMapping("/info")
    @Operation(summary = "更新用户信息", description = "更新当前用户的基本信息")
    public Result<UserDTO> updateUserInfo(
            @Parameter(description = "昵称") @RequestParam(value = "nickname", required = false) String nickname,
            @Parameter(description = "邮箱") @RequestParam(value = "email", required = false) String email,
            @Parameter(description = "手机号") @RequestParam(value = "phone", required = false) String phone) {

        log.info("更新用户信息");
        Long userId = UserContext.getUserId();
        UserDTO user = userService.updateUserInfo(userId, nickname, email, phone);
        return Result.success(user);
    }

    /**
     * 修改密码
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改当前用户密码")
    public Result<Void> updatePassword(
            @Parameter(description = "旧密码") @RequestParam("oldPassword") String oldPassword,
            @Parameter(description = "新密码") @RequestParam("newPassword") String newPassword) {

        log.info("修改密码");
        Long userId = UserContext.getUserId();
        userService.updatePassword(userId, oldPassword, newPassword);
        return Result.success();
    }

    /**
     * 刷新Token
     *
     * @param request HTTP请求
     * @return 新Token
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "刷新访问Token")
    public Result<String> refreshToken(HttpServletRequest request) {
        log.info("刷新Token");

        String token = request.getHeader(JwtUtil.HEADER_NAME);
        if (token != null && token.startsWith(JwtUtil.TOKEN_PREFIX)) {
            token = token.substring(JwtUtil.TOKEN_PREFIX.length());
        }

        String newToken = userService.refreshToken(token);
        return Result.success(newToken);
    }

    // ==================== MFA 多因素认证接口（v2.3 新增） ====================

    /**
     * 初始化 MFA（需登录）
     *
     * <p>生成 TOTP 密钥与备用码，返回二维码 URL。用户在认证器 App 添加后，
     * 需调用 /auth/mfa/verify（不传 mfaToken）完成启用确认。</p>
     *
     * @return MFA 初始化响应
     */
    @PostMapping("/mfa/setup")
    @Operation(summary = "初始化MFA", description = "为当前登录用户初始化 MFA，返回密钥、二维码与备用码")
    public Result<MfaSetupVO> setupMfa() {
        Long userId = requireLogin();
        log.info("初始化 MFA: userId={}", userId);
        MfaSetupVO vo = mfaService.setupMfa(userId);
        return Result.success(vo);
    }

    /**
     * 验证 MFA 码（登录第二阶段或 setup 启用确认）
     *
     * <p>当请求体携带 mfaToken 时为登录第二阶段验证，验证通过后返回正式 token；
     * 当未携带 mfaToken 时为 setup 启用确认，验证通过后启用 MFA。</p>
     *
     * @param dto MFA 验证请求
     * @return 登录第二阶段返回 LoginVO，启用确认返回 Boolean
     */
    @PostMapping("/mfa/verify")
    @Operation(summary = "验证MFA码", description = "登录第二阶段验证或 MFA 启用确认")
    public Result<Object> verifyMfa(@Valid @RequestBody MfaVerifyDTO dto) {
        if (StrUtil.isNotBlank(dto.getMfaToken())) {
            // 登录第二阶段验证
            log.info("MFA 登录第二阶段验证");
            LoginVO vo = userService.completeMfaLogin(dto.getMfaToken(), dto.getCode());
            return Result.success(vo);
        }
        // setup 启用确认
        Long userId = requireLogin();
        log.info("MFA 启用确认: userId={}", userId);
        boolean ok = mfaService.verifyMfa(userId, dto.getCode());
        if (!ok) {
            return Result.fail(ResultCode.CAPTCHA_ERROR, "MFA 验证码错误");
        }
        return Result.success(true);
    }

    /**
     * 禁用 MFA（需登录 + 验证码）
     *
     * @param dto MFA 验证请求
     * @return 是否禁用成功
     */
    @PostMapping("/mfa/disable")
    @Operation(summary = "禁用MFA", description = "禁用当前用户的 MFA，需提供验证码")
    public Result<Boolean> disableMfa(@Valid @RequestBody MfaVerifyDTO dto) {
        Long userId = requireLogin();
        log.info("禁用 MFA: userId={}", userId);
        boolean ok = mfaService.disableMfa(userId, dto.getCode());
        if (!ok) {
            return Result.fail(ResultCode.CAPTCHA_ERROR, "MFA 验证码错误，禁用失败");
        }
        return Result.success(true);
    }

    /**
     * 查询当前用户 MFA 状态
     *
     * @return 是否已启用 MFA
     */
    @GetMapping("/mfa/status")
    @Operation(summary = "查询MFA状态", description = "查询当前登录用户的 MFA 启用状态")
    public Result<Boolean> mfaStatus() {
        Long userId = requireLogin();
        boolean enabled = mfaService.isMfaEnabled(userId);
        return Result.success(enabled);
    }

    /**
     * 获取当前登录用户ID，未登录抛出业务异常
     *
     * @return 用户ID
     */
    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
