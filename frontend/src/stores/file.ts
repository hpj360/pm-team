/**
 * 文件状态管理
 * - 列表 + 详情
 * - 上传任务（含分片进度）
 */
import { create } from 'zustand';
import type { FileInfo, FileListParams, UploadTask } from '@/types';

interface FileState {
  files: FileInfo[];
  currentFile: FileInfo | null;
  total: number;
  loading: boolean;
  params: FileListParams;
  selectedFileIds: string[];
  /** 上传任务列表 */
  uploadTasks: UploadTask[];

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

  addUploadTask: (task: UploadTask) => void;
  updateUploadTask: (uid: string, data: Partial<UploadTask>) => void;
  removeUploadTask: (uid: string) => void;
  clearCompletedUploadTasks: () => void;

  reset: () => void;
}

const defaultParams: FileListParams = {
  page: 1,
  pageSize: 20,
};

export const useFileStore = create<FileState>((set) => ({
  files: [],
  currentFile: null,
  total: 0,
  loading: false,
  params: defaultParams,
  selectedFileIds: [],
  uploadTasks: [],

  setFiles: (files) => set({ files }),
  setTotal: (total) => set({ total }),
  setLoading: (loading) => set({ loading }),
  setParams: (params) =>
    set((state) => ({ params: { ...state.params, ...params } })),
  setCurrentFile: (file) => set({ currentFile: file }),
  setSelectedFileIds: (ids) => set({ selectedFileIds: ids }),

  addFile: (file) =>
    set((state) => ({ files: [file, ...state.files], total: state.total + 1 })),

  updateFile: (id, data) =>
    set((state) => ({
      files: state.files.map((file) => (file.id === id ? { ...file, ...data } : file)),
      currentFile:
        state.currentFile?.id === id ? { ...state.currentFile, ...data } : state.currentFile,
    })),

  removeFile: (id) =>
    set((state) => ({
      files: state.files.filter((file) => file.id !== id),
      total: state.total - 1,
      selectedFileIds: state.selectedFileIds.filter((fileId) => fileId !== id),
      currentFile: state.currentFile?.id === id ? null : state.currentFile,
    })),

  removeFiles: (ids) =>
    set((state) => ({
      files: state.files.filter((file) => !ids.includes(file.id)),
      total: state.total - ids.length,
      selectedFileIds: state.selectedFileIds.filter((fileId) => !ids.includes(fileId)),
      currentFile: ids.includes(state.currentFile?.id || '') ? null : state.currentFile,
    })),

  addUploadTask: (task) =>
    set((state) => ({ uploadTasks: [task, ...state.uploadTasks] })),

  updateUploadTask: (uid, data) =>
    set((state) => ({
      uploadTasks: state.uploadTasks.map((t) => (t.uid === uid ? { ...t, ...data } : t)),
    })),

  removeUploadTask: (uid) =>
    set((state) => ({
      uploadTasks: state.uploadTasks.filter((t) => t.uid !== uid),
    })),

  clearCompletedUploadTasks: () =>
    set((state) => ({
      uploadTasks: state.uploadTasks.filter(
        (t) => t.status !== 'completed' && t.status !== 'instant' && t.status !== 'failed',
      ),
    })),

  reset: () =>
    set({
      files: [],
      currentFile: null,
      total: 0,
      loading: false,
      params: defaultParams,
      selectedFileIds: [],
      uploadTasks: [],
    }),
}));
