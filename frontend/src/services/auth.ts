/**
 * 认证相关API服务
 */

import { get, post } from '@/utils/request';
import type { LoginParams, LoginResult, UserInfo, ApiResponse } from '@/types';

/**
 * 用户登录
 */
export function login(params: LoginParams): Promise<ApiResponse<LoginResult>> {
  return post<LoginResult>('/auth/login', params);
}

/**
 * 用户登出
 */
export function logout(): Promise<ApiResponse<void>> {
  return post<void>('/auth/logout');
}

/**
 * 获取当前用户信息
 */
export function getCurrentUser(): Promise<ApiResponse<UserInfo>> {
  return get<UserInfo>('/auth/current');
}

/**
 * 刷新Token
 */
export function refreshToken(): Promise<ApiResponse<{ token: string }>> {
  return post<{ token: string }>('/auth/refresh');
}

/**
 * 修改密码
 */
export function changePassword(data: {
  oldPassword: string;
  newPassword: string;
}): Promise<ApiResponse<void>> {
  return post<void>('/auth/change-password', data);
}
