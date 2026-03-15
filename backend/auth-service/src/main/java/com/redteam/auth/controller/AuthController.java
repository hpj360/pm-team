package com.redteam.auth.controller;

import com.redteam.auth.dto.LoginDTO;
import com.redteam.auth.dto.LoginVO;
import com.redteam.auth.dto.UserDTO;
import com.redteam.auth.service.UserService;
import com.redteam.common.result.Result;
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
}
