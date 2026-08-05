/**
 * 上传编排 Hook
 * - 文件哈希计算（MD5 + SM3）
 * - 秒传判断（checkFile）
 * - 直传（<5MB）/ 分片上传（>=5MB）
 * - 暂停 / 恢复 / 取消
 * - 实时进度跟踪（分片级 + 文件级）
 */

import { useCallback, useRef } from 'react';
import { message } from 'antd';
import { useFileStore } from '@/stores';
import {
  checkFile,
  uploadFile as uploadFileApi,
  listMultipart,
  uploadPart,
  completeMultipart,
} from '@/services';
import { calculateFileHashes, sliceFile } from '@/utils';
import {
  DEFAULT_CHUNK_SIZE,
  MULTIPART_THRESHOLD,
  type FileUploadMetadata,
  type UploadTask,
  type UploadTaskStatus,
} from '@/types';
import { generateId } from '@/utils';

/** 单文件上传控制句柄 */
interface UploadController {
  abortController: AbortController;
  paused: boolean;
  /** 恢复上传的 Promise resolve */
  resumeResolve?: () => void;
}

export function useUpload() {
  const {
    uploadTasks,
    addUploadTask,
    updateUploadTask,
    removeUploadTask,
    clearCompletedUploadTasks,
    addFile,
  } = useFileStore();

  // 每个任务的控制句柄（uid -> controller）
  const controllersRef = useRef<Map<string, UploadController>>(new Map());

  /** 创建控制句柄 */
  const ensureController = useCallback((uid: string): UploadController => {
    let ctrl = controllersRef.current.get(uid);
    if (!ctrl) {
      ctrl = { abortController: new AbortController(), paused: false };
      controllersRef.current.set(uid, ctrl);
    }
    return ctrl;
  }, []);

  /** 等待恢复（暂停时阻塞） */
  const waitIfPaused = useCallback(async (uid: string): Promise<void> => {
    const ctrl = controllersRef.current.get(uid);
    if (!ctrl || !ctrl.paused) return;
    await new Promise<void>((resolve) => {
      ctrl.resumeResolve = resolve;
    });
    ctrl.resumeResolve = undefined;
  }, []);

  /**
   * 处理单个文件上传（核心流程）
   */
  const processFile = useCallback(
    async (file: File, metadata: FileUploadMetadata) => {
      const uid = generateId();
      const isMultipart = file.size >= MULTIPART_THRESHOLD;
      const chunkSize = DEFAULT_CHUNK_SIZE;
      const chunkCount = isMultipart ? Math.ceil(file.size / chunkSize) : 1;

      const task: UploadTask = {
        uid,
        file,
        fileName: file.name,
        fileSize: file.size,
        isMultipart,
        chunkSize,
        chunkCount,
        completedChunks: 0,
        percent: 0,
        status: 'pending',
        instantHit: false,
        metadata,
        partPercents: isMultipart ? new Array(chunkCount).fill(0) : [],
      };

      addUploadTask(task);
      ensureController(uid);

      try {
        // ============ 1. 计算文件哈希 ============
        updateUploadTask(uid, { status: 'uploading', percent: 5 });
        const { md5, sm3 } = await calculateFileHashes(file, (p) => {
          // 哈希阶段占用 5%~20%
          updateUploadTask(uid, { percent: 5 + Math.floor(p * 0.15) });
        });
        updateUploadTask(uid, { md5 });

        // ============ 2. 秒传检查 ============
        const checkRes = await checkFile({
          md5,
          sm3,
          fileName: file.name,
          fileSize: file.size,
        });
        if (checkRes.code === 200 && checkRes.data.hit && checkRes.data.file) {
          updateUploadTask(uid, {
            status: 'instant',
            percent: 100,
            instantHit: true,
            fileId: checkRes.data.file.id,
            result: checkRes.data.file,
          });
          addFile(checkRes.data.file);
          message.success(`「${file.name}」秒传成功`);
          return;
        }

        // ============ 3. 直传 / 分片上传 ============
        if (!isMultipart) {
          // 小文件直传
          const res = await uploadFileApi({
            file,
            ...metadata,
            onProgress: (percent) => {
              // 上传阶段占用 20%~100%
              updateUploadTask(uid, {
                percent: 20 + Math.floor(percent * 0.8),
              });
            },
          });
          if (res.code === 200 || res.code === 0) {
            updateUploadTask(uid, {
              status: 'completed',
              percent: 100,
              fileId: res.data.id,
              result: res.data,
            });
            addFile(res.data);
            message.success(`「${file.name}」上传成功`);
          } else {
            throw new Error(res.message || '上传失败');
          }
        } else {
          // 分片上传
          const initRes = await listMultipart({
            fileName: file.name,
            fileSize: file.size,
            md5,
            sm3,
            mimeType: file.type || 'application/octet-stream',
            chunkSize,
            chunkCount,
            metadata,
          });
          if (initRes.code !== 200 && initRes.code !== 0) {
            throw new Error(initRes.message || '初始化分片上传失败');
          }
          const { uploadId, fileId } = initRes.data;
          // parts 为各分片预签名上传地址，直传模式下由 uploadPart 内部处理
          updateUploadTask(uid, { uploadId, fileId, percent: 22 });

          const uploadedParts: Array<{ partNumber: number; etag: string }> = [];
          const chunks = sliceFile(file, chunkSize);

          for (let i = 0; i < chunks.length; i++) {
            const ctrl = controllersRef.current.get(uid);
            if (ctrl?.abortController.signal.aborted) {
              throw new DOMException('上传已取消', 'AbortError');
            }
            // 暂停检测
            await waitIfPaused(uid);

            const partNumber = i + 1;
            const partRes = await uploadPart(
              uploadId,
              partNumber,
              chunks[i],
              (percent) => {
                const partPercents = [
                  ...(useFileStore.getState().uploadTasks.find(
                    (t) => t.uid === uid,
                  )?.partPercents ?? []),
                ];
                partPercents[i] = percent;
                const completed = partPercents.filter((p) => p >= 100).length;
                const partAvg =
                  partPercents.reduce((s, p) => s + p, 0) / chunkCount;
                updateUploadTask(uid, {
                  partPercents,
                  completedChunks: completed,
                  // 分片上传阶段 22%~100%
                  percent: 22 + Math.floor(partAvg * 0.78),
                });
              },
            );
            if (partRes.code !== 200 && partRes.code !== 0) {
              throw new Error(partRes.message || `分片 ${partNumber} 上传失败`);
            }
            uploadedParts.push({
              partNumber,
              etag: partRes.data.etag,
            });
          }

          // 完成分片上传
          const completeRes = await completeMultipart({
            uploadId,
            fileId,
            parts: uploadedParts,
          });
          if (completeRes.code === 200 || completeRes.code === 0) {
            updateUploadTask(uid, {
              status: 'completed',
              percent: 100,
              completedChunks: chunkCount,
              result: completeRes.data,
            });
            addFile(completeRes.data);
            message.success(`「${file.name}」上传成功（${chunkCount} 分片）`);
          } else {
            throw new Error(completeRes.message || '完成分片上传失败');
          }
        }
      } catch (error) {
        const isAbort =
          error instanceof DOMException && error.name === 'AbortError';
        const newStatus: UploadTaskStatus = isAbort ? 'failed' : 'failed';
        updateUploadTask(uid, {
          status: newStatus,
          error: isAbort
            ? '上传已取消'
            : error instanceof Error
              ? error.message
              : '上传失败',
        });
        if (!isAbort) {
          message.error(`「${file.name}」上传失败`);
        }
      } finally {
        controllersRef.current.delete(uid);
      }
    },
    [
      addUploadTask,
      updateUploadTask,
      ensureController,
      addFile,
      waitIfPaused,
    ],
  );

  /**
   * 批量提交上传
   * @param files 文件列表
   * @param metadata 元数据
   */
  const startUpload = useCallback(
    async (files: File[], metadata: FileUploadMetadata) => {
      if (files.length === 0) {
        message.warning('请选择要上传的文件');
        return;
      }
      // 串行上传，避免并发触发过多请求
      for (const file of files) {
        await processFile(file, metadata);
      }
    },
    [processFile],
  );

  /** 暂停任务（仅分片上传有效） */
  const pauseTask = useCallback(
    (uid: string) => {
      const ctrl = controllersRef.current.get(uid);
      if (ctrl) {
        ctrl.paused = true;
        const task = uploadTasks.find((t) => t.uid === uid);
        if (task && task.isMultipart && task.status === 'uploading') {
          updateUploadTask(uid, { status: 'paused' });
          message.info(`「${task.fileName}」已暂停`);
        }
      }
    },
    [uploadTasks, updateUploadTask],
  );

  /** 恢复任务 */
  const resumeTask = useCallback(
    (uid: string) => {
      const ctrl = controllersRef.current.get(uid);
      if (ctrl) {
        ctrl.paused = false;
        if (ctrl.resumeResolve) {
          ctrl.resumeResolve();
        }
        const task = uploadTasks.find((t) => t.uid === uid);
        if (task && task.status === 'paused') {
          updateUploadTask(uid, { status: 'uploading' });
          message.info(`「${task.fileName}」已恢复`);
        }
      }
    },
    [uploadTasks, updateUploadTask],
  );

  /** 取消任务 */
  const cancelTask = useCallback(
    (uid: string) => {
      const ctrl = controllersRef.current.get(uid);
      if (ctrl) {
        ctrl.abortController.abort();
        if (ctrl.resumeResolve) {
          ctrl.resumeResolve();
        }
      }
      const task = uploadTasks.find((t) => t.uid === uid);
      updateUploadTask(uid, {
        status: 'failed',
        error: '上传已取消',
      });
      if (task) {
        message.info(`「${task.fileName}」已取消`);
      }
    },
    [uploadTasks, updateUploadTask],
  );

  /** 移除任务 */
  const removeTask = useCallback(
    (uid: string) => {
      controllersRef.current.delete(uid);
      removeUploadTask(uid);
    },
    [removeUploadTask],
  );

  return {
    uploadTasks,
    startUpload,
    pauseTask,
    resumeTask,
    cancelTask,
    removeTask,
    clearCompleted: clearCompletedUploadTasks,
  };
}
