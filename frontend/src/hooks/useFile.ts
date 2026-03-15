/**
 * 文件相关Hook
 */

import { useCallback } from 'react';
import { message } from 'antd';
import { useFileStore } from '@/stores';
import {
  getFileList,
  getFileDetail,
  uploadFile as uploadFileApi,
  deleteFile as deleteFileApi,
  deleteFiles as deleteFilesApi,
  updateFile as updateFileApi,
} from '@/services';
import type { FileListParams, FileUploadParams } from '@/types';

export function useFile() {
  const {
    files,
    currentFile,
    total,
    loading,
    params,
    selectedFileIds,
    setFiles,
    setTotal,
    setLoading,
    setParams,
    setCurrentFile,
    setSelectedFileIds,
    addFile,
    updateFile,
    removeFile,
    removeFiles,
  } = useFileStore();

  // 获取文件列表
  const fetchFiles = useCallback(async (searchParams?: Partial<FileListParams>) => {
    setLoading(true);
    try {
      const mergedParams = { ...params, ...searchParams };
      const res = await getFileList(mergedParams);
      if (res.code === 200 || res.code === 0) {
        setFiles(res.data.list);
        setTotal(res.data.total);
        setParams(mergedParams);
      }
    } catch (error) {
      message.error('获取文件列表失败');
    } finally {
      setLoading(false);
    }
  }, [params, setFiles, setTotal, setLoading, setParams]);

  // 获取文件详情
  const fetchFileDetail = useCallback(async (id: string) => {
    setLoading(true);
    try {
      const res = await getFileDetail(id);
      if (res.code === 200 || res.code === 0) {
        setCurrentFile(res.data);
        return res.data;
      }
      return null;
    } catch (error) {
      message.error('获取文件详情失败');
      return null;
    } finally {
      setLoading(false);
    }
  }, [setCurrentFile, setLoading]);

  // 上传文件
  const uploadFile = useCallback(async (uploadParams: FileUploadParams) => {
    try {
      const res = await uploadFileApi(uploadParams);
      if (res.code === 200 || res.code === 0) {
        addFile(res.data);
        message.success('上传成功');
        return res.data;
      }
      message.error(res.message || '上传失败');
      return null;
    } catch (error) {
      message.error('上传失败，请稍后重试');
      return null;
    }
  }, [addFile]);

  // 删除文件
  const deleteFile = useCallback(async (id: string) => {
    try {
      const res = await deleteFileApi(id);
      if (res.code === 200 || res.code === 0) {
        removeFile(id);
        message.success('删除成功');
        return true;
      }
      message.error(res.message || '删除失败');
      return false;
    } catch (error) {
      message.error('删除失败，请稍后重试');
      return false;
    }
  }, [removeFile]);

  // 批量删除文件
  const deleteFiles = useCallback(async (ids: string[]) => {
    try {
      const res = await deleteFilesApi(ids);
      if (res.code === 200 || res.code === 0) {
        removeFiles(ids);
        message.success(`成功删除 ${ids.length} 个文件`);
        return true;
      }
      message.error(res.message || '删除失败');
      return false;
    } catch (error) {
      message.error('删除失败，请稍后重试');
      return false;
    }
  }, [removeFiles]);

  // 更新文件信息
  const updateFileInfo = useCallback(async (id: string, data: Parameters<typeof updateFileApi>[1]) => {
    try {
      const res = await updateFileApi(id, data);
      if (res.code === 200 || res.code === 0) {
        updateFile(id, res.data);
        message.success('更新成功');
        return res.data;
      }
      message.error(res.message || '更新失败');
      return null;
    } catch (error) {
      message.error('更新失败，请稍后重试');
      return null;
    }
  }, [updateFile]);

  // 选择/取消选择文件
  const toggleFileSelection = useCallback((id: string) => {
    setSelectedFileIds(
      selectedFileIds.includes(id)
        ? selectedFileIds.filter((fileId) => fileId !== id)
        : [...selectedFileIds, id]
    );
  }, [selectedFileIds, setSelectedFileIds]);

  // 全选/取消全选
  const toggleSelectAll = useCallback(() => {
    if (selectedFileIds.length === files.length) {
      setSelectedFileIds([]);
    } else {
      setSelectedFileIds(files.map((file) => file.id));
    }
  }, [files, selectedFileIds, setSelectedFileIds]);

  return {
    files,
    currentFile,
    total,
    loading,
    params,
    selectedFileIds,
    fetchFiles,
    fetchFileDetail,
    uploadFile,
    deleteFile,
    deleteFiles,
    updateFileInfo,
    toggleFileSelection,
    toggleSelectAll,
    setSelectedFileIds,
    setCurrentFile,
    setParams,
  };
}
