package com.redteam.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redteam.auth.dto.LoginDTO;
import com.redteam.auth.dto.LoginVO;
import com.redteam.auth.dto.UserDTO;
import com.redteam.auth.entity.UserEntity;
import com.redteam.auth.mapper.UserMapper;
import com.redteam.auth.service.UserService;
import com.redteam.common.exception.AuthException;
import com.redteam.common.result.ResultCode;
import com.redteam.common.util.JwtUtil;
import com.redteam.common.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_CACHE_PREFIX = "auth:token:";
    private static final String USER_CACHE_PREFIX = "auth:user:";
    private static final long TOKEN_EXPIRE_SECONDS = 86400; // 24小时

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        log.info("用户登录: username={}", loginDTO.getUsername());

        // 查询用户
        UserEntity user = getByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new AuthException(ResultCode.LOGIN_ERROR);
        }

        // 验证密码
        if (!BCrypt.checkpw(loginDTO.getPassword(), user.getPassword())) {
            throw new AuthException(ResultCode.PASSWORD_ERROR);
        }

        // 检查用户状态
        if (user.getStatus() == 0) {
            throw new AuthException(ResultCode.ACCOUNT_DISABLED);
        }

        // 生成Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

        // 缓存Token
        String tokenKey = TOKEN_CACHE_PREFIX + token;
        redisTemplate.opsForValue().set(tokenKey, String.valueOf(user.getId()), TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        this.updateById(user);

        // 构建响应
        LoginVO loginVO = new LoginVO();
        loginVO.setAccessToken(token);
        loginVO.setExpiresIn(TOKEN_EXPIRE_SECONDS);
        loginVO.setUserInfo(convertToDTO(user));

        return loginVO;
    }

    @Override
    public void logout(String token) {
        log.info("用户登出");

        // 删除Token缓存
        if (StrUtil.isNotBlank(token)) {
            String tokenKey = TOKEN_CACHE_PREFIX + token;
            redisTemplate.delete(tokenKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO register(String username, String password, String email) {
        log.info("用户注册: username={}", username);

        // 检查用户名是否已存在
        if (getByUsername(username) != null) {
            throw new AuthException(ResultCode.USER_EXISTS);
        }

        // 创建用户
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password));
        user.setEmail(email);
        user.setNickname(username);
        user.setStatus(1); // 正常状态

        this.save(user);

        return convertToDTO(user);
    }

    @Override
    public UserDTO getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED);
        }

        UserEntity user = this.getById(userId);
        if (user == null) {
            throw new AuthException(ResultCode.USER_NOT_FOUND);
        }

        return convertToDTO(user);
    }

    @Override
    public UserEntity getByUsername(String username) {
        LambdaQueryWrapper<UserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserEntity::getUsername, username);
        return this.getOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO updateUserInfo(Long userId, String nickname, String email, String phone) {
        log.info("更新用户信息: userId={}", userId);

        UserEntity user = this.getById(userId);
        if (user == null) {
            throw new AuthException(ResultCode.USER_NOT_FOUND);
        }

        if (StrUtil.isNotBlank(nickname)) {
            user.setNickname(nickname);
        }
        if (StrUtil.isNotBlank(email)) {
            user.setEmail(email);
        }
        if (StrUtil.isNotBlank(phone)) {
            user.setPhone(phone);
        }

        this.updateById(user);

        // 清除用户缓存
        redisTemplate.delete(USER_CACHE_PREFIX + userId);

        return convertToDTO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        log.info("修改密码: userId={}", userId);

        UserEntity user = this.getById(userId);
        if (user == null) {
            throw new AuthException(ResultCode.USER_NOT_FOUND);
        }

        // 验证旧密码
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new AuthException(ResultCode.PASSWORD_ERROR);
        }

        // 更新密码
        user.setPassword(BCrypt.hashpw(newPassword));
        return this.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(Long userId, String newPassword) {
        log.info("重置密码: userId={}", userId);

        UserEntity user = this.getById(userId);
        if (user == null) {
            throw new AuthException(ResultCode.USER_NOT_FOUND);
        }

        user.setPassword(BCrypt.hashpw(newPassword));
        return this.updateById(user);
    }

    @Override
    public String refreshToken(String token) {
        log.info("刷新Token");

        // 验证旧Token
        if (!validateToken(token)) {
            throw new AuthException(ResultCode.TOKEN_INVALID);
        }

        // 获取用户信息
        Long userId = JwtUtil.getUserId(token);
        String username = JwtUtil.getUsername(token);

        // 生成新Token
        String newToken = JwtUtil.generateToken(userId, username);

        // 缓存新Token
        String tokenKey = TOKEN_CACHE_PREFIX + newToken;
        redisTemplate.opsForValue().set(tokenKey, String.valueOf(userId), TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 删除旧Token
        redisTemplate.delete(TOKEN_CACHE_PREFIX + token);

        return newToken;
    }

    @Override
    public boolean validateToken(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }

        // 验证Token格式
        if (!JwtUtil.validateToken(token)) {
            return false;
        }

        // 检查Token是否在缓存中
        String tokenKey = TOKEN_CACHE_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(tokenKey);

        return StrUtil.isNotBlank(userId);
    }

    @Override
    public List<String> getUserPermissions(Long userId) {
        // TODO: 从数据库查询用户权限
        List<String> permissions = new ArrayList<>();
        permissions.add("file:read");
        permissions.add("file:write");
        permissions.add("file:delete");
        return permissions;
    }

    @Override
    public List<String> getUserRoles(Long userId) {
        // TODO: 从数据库查询用户角色
        List<String> roles = new ArrayList<>();
        roles.add("user");
        return roles;
    }

    /**
     * 实体转DTO
     *
     * @param entity 实体
     * @return DTO
     */
    private UserDTO convertToDTO(UserEntity entity) {
        UserDTO dto = new UserDTO();
        dto.setId(entity.getId());
        dto.setUsername(entity.getUsername());
        dto.setNickname(entity.getNickname());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setAvatar(entity.getAvatar());
        dto.setStatus(entity.getStatus());
        dto.setDeptId(entity.getDeptId());
        dto.setLastLoginTime(entity.getLastLoginTime());
        dto.setCreateTime(entity.getCreateTime());
        dto.setRoles(getUserRoles(entity.getId()));
        dto.setPermissions(getUserPermissions(entity.getId()));
        return dto;
    }
}
