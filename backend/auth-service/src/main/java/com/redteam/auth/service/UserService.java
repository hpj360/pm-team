package com.redteam.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.redteam.auth.dto.LoginDTO;
import com.redteam.auth.dto.LoginVO;
import com.redteam.auth.dto.UserDTO;
import com.redteam.auth.entity.UserEntity;

/**
 * 用户服务接口
 *
 * @author 红方团队
 */
public interface UserService extends IService<UserEntity> {

    /**
     * 用户登录
     *
     * @param loginDTO 登录请求
     * @return 登录响应
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 用户登出
     *
     * @param token Token
     */
    void logout(String token);

    /**
     * 用户注册
     *
     * @param username 用户名
     * @param password 密码
     * @param email    邮箱
     * @return 用户信息
     */
    UserDTO register(String username, String password, String email);

    /**
     * 获取当前用户信息
     *
     * @return 用户信息
     */
    UserDTO getCurrentUser();

    /**
     * 根据用户名获取用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    UserEntity getByUsername(String username);

    /**
     * 更新用户信息
     *
     * @param userId   用户ID
     * @param nickname 昵称
     * @param email    邮箱
     * @param phone    手机号
     * @return 用户信息
     */
    UserDTO updateUserInfo(Long userId, String nickname, String email, String phone);

    /**
     * 修改密码
     *
     * @param userId      用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean updatePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 重置密码
     *
     * @param userId      用户ID
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean resetPassword(Long userId, String newPassword);

    /**
     * 刷新Token
     *
     * @param token 旧Token
     * @return 新Token
     */
    String refreshToken(String token);

    /**
     * 验证Token
     *
     * @param token Token
     * @return 是否有效
     */
    boolean validateToken(String token);

    /**
     * 获取用户权限列表
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    java.util.List<String> getUserPermissions(Long userId);

    /**
     * 获取用户角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    java.util.List<String> getUserRoles(Long userId);
}
