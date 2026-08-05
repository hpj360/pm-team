/**
 * 仪表盘 API 服务
 */
import { get } from '@/utils/request';
import type { DashboardData, ApiResponse } from '@/types';
import { mockDashboardData } from '@/mock/dashboard';

/**
 * 获取仪表盘完整数据
 */
export async function getDashboardData(): Promise<ApiResponse<DashboardData>> {
  try {
    return await get<DashboardData>('/dashboard');
  } catch {
    return { code: 200, message: 'success', data: mockDashboardData };
  }
}
