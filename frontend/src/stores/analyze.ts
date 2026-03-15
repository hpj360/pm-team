/**
 * 分析状态管理
 */

import { create } from 'zustand';
import type { AnalyzeTask, AnalyzeResult, AnalyzeType, AnalyzeStatus } from '@/types';

interface AnalyzeState {
  // 状态
  tasks: AnalyzeTask[];
  currentTask: AnalyzeTask | null;
  currentResult: AnalyzeResult | null;
  total: number;
  loading: boolean;
  
  // 操作
  setTasks: (tasks: AnalyzeTask[]) => void;
  setTotal: (total: number) => void;
  setLoading: (loading: boolean) => void;
  setCurrentTask: (task: AnalyzeTask | null) => void;
  setCurrentResult: (result: AnalyzeResult | null) => void;
  addTask: (task: AnalyzeTask) => void;
  updateTask: (id: string, data: Partial<AnalyzeTask>) => void;
  removeTask: (id: string) => void;
  reset: () => void;
}

export const useAnalyzeStore = create<AnalyzeState>((set) => ({
  // 初始状态
  tasks: [],
  currentTask: null,
  currentResult: null,
  total: 0,
  loading: false,

  // 设置任务列表
  setTasks: (tasks) => set({ tasks }),

  // 设置总数
  setTotal: (total) => set({ total }),

  // 设置加载状态
  setLoading: (loading) => set({ loading }),

  // 设置当前任务
  setCurrentTask: (task) => set({ currentTask: task }),

  // 设置当前结果
  setCurrentResult: (result) => set({ currentResult: result }),

  // 添加任务
  addTask: (task) =>
    set((state) => ({
      tasks: [task, ...state.tasks],
      total: state.total + 1,
    })),

  // 更新任务
  updateTask: (id, data) =>
    set((state) => ({
      tasks: state.tasks.map((task) =>
        task.id === id ? { ...task, ...data } : task
      ),
      currentTask:
        state.currentTask?.id === id
          ? { ...state.currentTask, ...data }
          : state.currentTask,
    })),

  // 删除任务
  removeTask: (id) =>
    set((state) => ({
      tasks: state.tasks.filter((task) => task.id !== id),
      total: state.total - 1,
      currentTask: state.currentTask?.id === id ? null : state.currentTask,
      currentResult: state.currentTask?.id === id ? null : state.currentResult,
    })),

  // 重置状态
  reset: () =>
    set({
      tasks: [],
      currentTask: null,
      currentResult: null,
      total: 0,
      loading: false,
    }),
}));
