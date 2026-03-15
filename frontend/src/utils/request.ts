/**
 * Axios请求封装
 * 提供统一的请求/响应拦截器、错误处理、请求取消等功能
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { message, Modal } from 'antd';
import type { ApiResponse } from '@/types';

// 创建Axios实例
const service: AxiosInstance = axios.create({
  // 基础URL，根据环境配置
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  // 请求超时时间
  timeout: 60000,
  // 允许跨域携带凭证
  withCredentials: false,
});

// 请求队列，用于存储请求取消函数
let requestQueue: Map<string, AbortController> = new Map();

/**
 * 生成请求唯一标识
 */
function generateRequestKey(config: AxiosRequestConfig): string {
  const { url, method, params, data } = config;
  return `${method}-${url}-${JSON.stringify(params)}-${JSON.stringify(data)}`;
}

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 获取Token
    const token = localStorage.getItem('token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 添加时间戳，防止缓存
    if (config.method === 'get') {
      config.params = {
        ...config.params,
        _t: Date.now(),
      };
    }

    // 显示加载状态（可根据需要配置）
    // 显示全屏加载
    // if (config.headers?.showLoading) {
    //   // 可以在这里触发全局loading
    // }

    return config;
  },
  (error) => {
    // 请求错误处理
    console.error('请求错误:', error);
    return Promise.reject(error);
  }
);

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const config = response.config;
    const requestKey = generateRequestKey(config);
    requestQueue.delete(requestKey);

    const res = response.data as ApiResponse;

    // 根据后端返回的状态码处理
    if (res.code === 200 || res.code === 0) {
      return res;
    }

    // Token过期或无效
    if (res.code === 401) {
      Modal.warning({
        title: '提示',
        content: '登录已过期，请重新登录',
        onOk: () => {
          localStorage.removeItem('token');
          window.location.href = '/login';
        },
      });
      return Promise.reject(new Error(res.message || '未授权'));
    }

    // 其他错误
    message.error(res.message || '请求失败');
    return Promise.reject(new Error(res.message || '请求失败'));
  },
  (error) => {
    // 响应错误处理
    const config = error.config;
    if (config) {
      const requestKey = generateRequestKey(config);
      requestQueue.delete(requestKey);
    }

    let errorMessage = '网络错误，请稍后重试';

    if (error.response) {
      switch (error.response.status) {
        case 400:
          errorMessage = '请求参数错误';
          break;
        case 403:
          errorMessage = '没有权限访问该资源';
          break;
        case 404:
          errorMessage = '请求的资源不存在';
          break;
        case 408:
          errorMessage = '请求超时';
          break;
        case 500:
          errorMessage = '服务器内部错误';
          break;
        case 502:
          errorMessage = '网关错误';
          break;
        case 503:
          errorMessage = '服务不可用';
          break;
        case 504:
          errorMessage = '网关超时';
          break;
        default:
          errorMessage = `请求失败(${error.response.status})`;
      }
    } else if (error.request) {
      errorMessage = '网络连接失败，请检查网络';
    }

    message.error(errorMessage);
    return Promise.reject(error);
  }
);

/**
 * GET请求
 */
export function get<T = unknown>(
  url: string,
  params?: Record<string, unknown>,
  config?: AxiosRequestConfig
): Promise<ApiResponse<T>> {
  return service.get(url, { params, ...config });
}

/**
 * POST请求
 */
export function post<T = unknown>(
  url: string,
  data?: Record<string, unknown>,
  config?: AxiosRequestConfig
): Promise<ApiResponse<T>> {
  return service.post(url, data, config);
}

/**
 * PUT请求
 */
export function put<T = unknown>(
  url: string,
  data?: Record<string, unknown>,
  config?: AxiosRequestConfig
): Promise<ApiResponse<T>> {
  return service.put(url, data, config);
}

/**
 * DELETE请求
 */
export function del<T = unknown>(
  url: string,
  params?: Record<string, unknown>,
  config?: AxiosRequestConfig
): Promise<ApiResponse<T>> {
  return service.delete(url, { params, ...config });
}

/**
 * PATCH请求
 */
export function patch<T = unknown>(
  url: string,
  data?: Record<string, unknown>,
  config?: AxiosRequestConfig
): Promise<ApiResponse<T>> {
  return service.patch(url, data, config);
}

/**
 * 上传文件请求
 */
export function upload<T = unknown>(
  url: string,
  formData: FormData,
  onProgress?: (percent: number) => void
): Promise<ApiResponse<T>> {
  return service.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    onUploadProgress: (progressEvent) => {
      if (progressEvent.total) {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        onProgress?.(percent);
      }
    },
  });
}

/**
 * 下载文件请求
 */
export function download(
  url: string,
  params?: Record<string, unknown>,
  filename?: string
): Promise<void> {
  return service
    .get(url, {
      params,
      responseType: 'blob',
    })
    .then((response) => {
      const blob = new Blob([response as unknown as BlobPart]);
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = filename || 'download';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(link.href);
    });
}

/**
 * 取消所有请求
 */
export function cancelAllRequests(): void {
  requestQueue.forEach((controller) => {
    controller.abort();
  });
  requestQueue.clear();
}

/**
 * 取消指定请求
 */
export function cancelRequest(requestKey: string): void {
  const controller = requestQueue.get(requestKey);
  if (controller) {
    controller.abort();
    requestQueue.delete(requestKey);
  }
}

export default service;
