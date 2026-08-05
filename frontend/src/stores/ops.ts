/**
 * 应用运维全局状态
 * - 当前选中空间（应用运维全局筛选）
 * - 治愈操作台选择的操作类型
 */
import { create } from 'zustand';

interface OpsState {
  /** 当前选中的空间（应用运维全局筛选，null=全部） */
  currentSpaceId: number | null;
  setCurrentSpaceId: (id: number | null) => void;
  /** 治愈操作台选择的操作类型 */
  healJobType: string | null;
  setHealJobType: (t: string | null) => void;
}

export const useOpsStore = create<OpsState>((set) => ({
  currentSpaceId: null,
  setCurrentSpaceId: (currentSpaceId) => set({ currentSpaceId }),
  healJobType: null,
  setHealJobType: (healJobType) => set({ healJobType }),
}));
