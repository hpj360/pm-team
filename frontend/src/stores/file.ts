/**
 * 文件状态管理
 */

import { create } from 'zustand';
import type { FileInfo, FileListParams, FileStatus, FileType } from '@/types';

interface FileState {
  // 状态
  files: FileInfo[];
  currentFile: FileInfo | null;
  total: number;
  loading: boolean;
  params: FileListParams;
  selectedFileIds: string[];
  
  // 操作
  setFiles: (files: FileInfo[]) => void;
  setTotal: (total: number) => void;
  setLoading: (loading: boolean) => void;
  setParams: (params: Partial<FileListParams>) => void;
  setCurrentFile: (file: FileInfo | null) => void;
  setSelectedFileIds: (ids: string[]) => void;
  addFile: (file: FileInfo) => void;
  updateFile: (id: string, data: Partial<FileInfo>) => void;
  removeFile: (id: string) => void;
  removeFiles: (ids: string[]) => void;
  reset: () => void;
}

// 默认查询参数
const defaultParams: FileListParams = {
  page: 1,
  pageSize: 20,
};

export const useFileStore = create<FileState>((set) => ({
  // 初始状态
  files: [],
  currentFile: null,
  total: 0,
  loading: false,
  params: defaultParams,
  selectedFileIds: [],

  // 设置文件列表
  setFiles: (files) => set({ files }),

  // 设置总数
  setTotal: (total) => set({ total }),

  // 设置加载状态
  setLoading: (loading) => set({ loading }),

  // 设置查询参数
  setParams: (params) =>
    set((state) => ({
      params: { ...state.params, ...params },
    })),

  // 设置当前文件
  setCurrentFile: (file) => set({ currentFile: file }),

  // 设置选中的文件ID列表
  setSelectedFileIds: (ids) => set({ selectedFileIds: ids }),

  // 添加文件
  addFile: (file) =>
    set((state) => ({
      files: [file, ...state.files],
      total: state.total + 1,
    })),

  // 更新文件
  updateFile: (id, data) =>
    set((state) => ({
      files: state.files.map((file) =>
        file.id === id ? { ...file, ...data } : file
      ),
      currentFile:
        state.currentFile?.id === id
          ? { ...state.currentFile, ...data }
          : state.currentFile,
    })),

  // 删除文件
  removeFile: (id) =>
    set((state) => ({
      files: state.files.filter((file) => file.id !== id),
      total: state.total - 1,
      selectedFileIds: state.selectedFileIds.filter((fileId) => fileId !== id),
      currentFile: state.currentFile?.id === id ? null : state.currentFile,
    })),

  // 批量删除文件
  removeFiles: (ids) =>
    set((state) => ({
      files: state.files.filter((file) => !ids.includes(file.id)),
      total: state.total - ids.length,
      selectedFileIds: state.selectedFileIds.filter(
        (fileId) => !ids.includes(fileId)
      ),
      currentFile: ids.includes(state.currentFile?.id || '')
        ? null
        : state.currentFile,
    })),

  // 重置状态
  reset: () =>
    set({
      files: [],
      currentFile: null,
      total: 0,
      loading: false,
      params: defaultParams,
      selectedFileIds: [],
    }),
}));
