/**
 * IOC（威胁情报）相关 API 服务
 */
import { get, post } from '@/utils/request';
import { IocType } from '@/types';
import type { IocItem, IocListParams, IocAggregation, ApiResponse, PageResult } from '@/types';
import { getMockIocList, mockIocList, mockIocAggregation } from '@/mock/ioc';

/**
 * 获取 IOC 列表
 */
export async function getIocList(
  params: IocListParams,
): Promise<ApiResponse<PageResult<IocItem>>> {
  try {
    return await get<PageResult<IocItem>>('/iocs', params as unknown as Record<string, unknown>);
  } catch {
    const { list, total } = getMockIocList({
      keyword: params.keyword,
      type: params.type,
      malicious: params.malicious,
      threatCategory: params.threatCategory,
      page: params.page,
      pageSize: params.pageSize,
      sortBy: params.sortBy,
      order: params.order,
    });
    return {
      code: 200,
      message: 'success',
      data: { list, total, page: params.page, pageSize: params.pageSize },
    };
  }
}

/**
 * 获取 IOC 详情
 */
export async function getIocDetail(id: string): Promise<ApiResponse<IocItem>> {
  try {
    return await get<IocItem>(`/iocs/${id}`);
  } catch {
    const ioc = mockIocList.find((i) => i.id === id) ?? mockIocList[0];
    return { code: 200, message: 'success', data: ioc };
  }
}

/**
 * 获取 IOC 聚合统计
 */
export async function getIocAggregations(): Promise<ApiResponse<IocAggregation>> {
  try {
    return await get<IocAggregation>('/iocs/aggregations');
  } catch {
    return { code: 200, message: 'success', data: mockIocAggregation };
  }
}

/**
 * 导出 IOC（返回 CSV/JSON 字符串内容，由前端触发下载）
 */
export async function exportIocs(
  format: 'csv' | 'json',
  params?: { type?: IocType; malicious?: boolean },
): Promise<string> {
  // Mock：直接基于内存数据生成
  let list = [...mockIocList];
  if (params?.type) list = list.filter((i) => i.type === params.type);
  if (params?.malicious !== undefined) list = list.filter((i) => i.malicious === params.malicious);

  if (format === 'json') {
    return JSON.stringify(list, null, 2);
  }

  // CSV
  const headers = ['ID', '类型', '值', '置信度', '恶意', '威胁分类', '出现次数', '首次出现', '最近出现', '来源文件'];
  const rows = list.map((i) => [
    i.id,
    i.type,
    i.value,
    String(i.confidence),
    i.malicious ? '是' : '否',
    i.threatCategory ?? '',
    String(i.occurrences),
    i.firstSeen,
    i.lastSeen,
    i.sourceFileName,
  ]);
  return [headers, ...rows].map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n');
}

/**
 * 标记 IOC 恶意状态
 */
export function markIocMalicious(id: string, malicious: boolean): Promise<ApiResponse<void>> {
  return post<void>(`/iocs/${id}/mark`, { malicious });
}
