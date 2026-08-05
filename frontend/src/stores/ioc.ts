/**
 * IOC（威胁情报）状态管理
 */
import { create } from 'zustand';
import { IocType } from '@/types';
import type { IocItem, IocListParams, IocAggregation } from '@/types';

interface IocState {
  iocs: IocItem[];
  total: number;
  loading: boolean;
  params: IocListParams;
  currentIoc: IocItem | null;
  aggregation: IocAggregation | null;

  setIocs: (iocs: IocItem[]) => void;
  setTotal: (total: number) => void;
  setLoading: (loading: boolean) => void;
  setParams: (params: Partial<IocListParams>) => void;
  setCurrentIoc: (ioc: IocItem | null) => void;
  setAggregation: (agg: IocAggregation | null) => void;
  reset: () => void;
}

const defaultParams: IocListParams = {
  page: 1,
  pageSize: 20,
  sortBy: 'occurrences',
  order: 'desc',
};

export const useIocStore = create<IocState>((set) => ({
  iocs: [],
  total: 0,
  loading: false,
  params: defaultParams,
  currentIoc: null,
  aggregation: null,

  setIocs: (iocs) => set({ iocs }),
  setTotal: (total) => set({ total }),
  setLoading: (loading) => set({ loading }),
  setParams: (params) =>
    set((state) => ({ params: { ...state.params, ...params } })),
  setCurrentIoc: (currentIoc) => set({ currentIoc }),
  setAggregation: (aggregation) => set({ aggregation }),

  reset: () =>
    set({
      iocs: [],
      total: 0,
      loading: false,
      params: defaultParams,
      currentIoc: null,
      aggregation: null,
    }),
}));

/** IOC 类型中文标签 */
export const iocTypeLabel: Record<IocType, string> = {
  [IocType.IP]: 'IP',
  [IocType.DOMAIN]: '域名',
  [IocType.URL]: 'URL',
  [IocType.MD5]: 'MD5',
  [IocType.SHA1]: 'SHA1',
  [IocType.SHA256]: 'SHA256',
  [IocType.EMAIL]: '邮箱',
  [IocType.CVE]: 'CVE',
  [IocType.BTC]: '比特币',
};
