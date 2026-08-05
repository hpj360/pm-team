/**
 * 单元测试：文件 Store（src/stores/file.ts）
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { useFileStore } from '@/stores';
import { FileType, FileStatus, SensitivityLevel } from '@/types';
import type { FileInfo, UploadTask } from '@/types';

const makeFile = (id: string, name: string = `file-${id}`): FileInfo => ({
  id,
  name,
  originalName: name,
  size: 1024,
  type: FileType.DOCUMENT,
  mimeType: 'application/pdf',
  status: FileStatus.COMPLETED,
  path: `/storage/${id}`,
  hash: 'abcdef',
  tags: [],
  uploaderId: 'u1',
  uploaderName: 'admin',
  sensitivity: SensitivityLevel.L2,
  createTime: '2026-01-01 00:00:00',
  updateTime: '2026-01-01 00:00:00',
});

const makeTask = (uid: string, status: UploadTask['status'] = 'pending'): UploadTask => ({
  uid,
  file: new File(['x'], 'test.txt'),
  fileName: 'test.txt',
  fileSize: 1,
  isMultipart: false,
  chunkSize: 5 * 1024 * 1024,
  chunkCount: 1,
  completedChunks: 0,
  percent: 0,
  status,
  instantHit: false,
  metadata: {},
  partPercents: [],
});

describe('useFileStore', () => {
  beforeEach(() => {
    useFileStore.getState().reset();
  });

  describe('基础 setter', () => {
    it('setFiles 设置文件列表', () => {
      const files = [makeFile('1'), makeFile('2')];
      useFileStore.getState().setFiles(files);
      expect(useFileStore.getState().files).toHaveLength(2);
    });

    it('setTotal 设置总数', () => {
      useFileStore.getState().setTotal(42);
      expect(useFileStore.getState().total).toBe(42);
    });

    it('setLoading 切换 loading 状态', () => {
      useFileStore.getState().setLoading(true);
      expect(useFileStore.getState().loading).toBe(true);
      useFileStore.getState().setLoading(false);
      expect(useFileStore.getState().loading).toBe(false);
    });

    it('setCurrentFile 设置当前文件', () => {
      const f = makeFile('1');
      useFileStore.getState().setCurrentFile(f);
      expect(useFileStore.getState().currentFile).toEqual(f);
      useFileStore.getState().setCurrentFile(null);
      expect(useFileStore.getState().currentFile).toBeNull();
    });

    it('setSelectedFileIds 设置选中 ID', () => {
      useFileStore.getState().setSelectedFileIds(['1', '2']);
      expect(useFileStore.getState().selectedFileIds).toEqual(['1', '2']);
    });
  });

  describe('setParams', () => {
    it('部分合并 params，保留未提供字段', () => {
      const initial = useFileStore.getState().params;
      expect(initial.page).toBe(1);
      useFileStore.getState().setParams({ page: 3 });
      expect(useFileStore.getState().params.page).toBe(3);
      expect(useFileStore.getState().params.pageSize).toBe(initial.pageSize);
    });

    it('多次 setParams 不会丢失其他字段', () => {
      useFileStore.getState().setParams({ keyword: 'test' });
      useFileStore.getState().setParams({ page: 2 });
      expect(useFileStore.getState().params.keyword).toBe('test');
      expect(useFileStore.getState().params.page).toBe(2);
    });
  });

  describe('addFile', () => {
    it('新增文件放到列表头部，total +1', () => {
      useFileStore.getState().setFiles([makeFile('1')]);
      useFileStore.getState().setTotal(1);
      useFileStore.getState().addFile(makeFile('2'));
      expect(useFileStore.getState().files).toHaveLength(2);
      expect(useFileStore.getState().files[0].id).toBe('2');
      expect(useFileStore.getState().total).toBe(2);
    });
  });

  describe('updateFile', () => {
    it('按 ID 更新文件字段', () => {
      useFileStore.getState().setFiles([makeFile('1'), makeFile('2')]);
      useFileStore.getState().updateFile('1', { size: 9999 });
      expect(useFileStore.getState().files[0].size).toBe(9999);
      expect(useFileStore.getState().files[1].size).toBe(1024);
    });

    it('currentFile 命中 ID 时同步更新', () => {
      useFileStore.getState().setFiles([makeFile('1')]);
      useFileStore.getState().setCurrentFile(makeFile('1'));
      useFileStore.getState().updateFile('1', { size: 9999 });
      expect(useFileStore.getState().currentFile?.size).toBe(9999);
    });
  });

  describe('removeFile', () => {
    it('按 ID 删除文件，total -1，并从 selectedFileIds 移除', () => {
      useFileStore.getState().setFiles([makeFile('1'), makeFile('2')]);
      useFileStore.getState().setTotal(2);
      useFileStore.getState().setSelectedFileIds(['1', '2']);
      useFileStore.getState().removeFile('1');
      expect(useFileStore.getState().files).toHaveLength(1);
      expect(useFileStore.getState().files[0].id).toBe('2');
      expect(useFileStore.getState().total).toBe(1);
      expect(useFileStore.getState().selectedFileIds).toEqual(['2']);
    });

    it('currentFile 命中删除 ID 时置空', () => {
      useFileStore.getState().setFiles([makeFile('1')]);
      useFileStore.getState().setCurrentFile(makeFile('1'));
      useFileStore.getState().removeFile('1');
      expect(useFileStore.getState().currentFile).toBeNull();
    });
  });

  describe('removeFiles', () => {
    it('批量删除多个文件', () => {
      useFileStore.getState().setFiles([makeFile('1'), makeFile('2'), makeFile('3')]);
      useFileStore.getState().setTotal(3);
      useFileStore.getState().removeFiles(['1', '3']);
      expect(useFileStore.getState().files).toHaveLength(1);
      expect(useFileStore.getState().files[0].id).toBe('2');
      expect(useFileStore.getState().total).toBe(1);
    });

    it('currentFile 在删除列表中时置空', () => {
      useFileStore.getState().setFiles([makeFile('1'), makeFile('2')]);
      useFileStore.getState().setCurrentFile(makeFile('1'));
      useFileStore.getState().removeFiles(['1']);
      expect(useFileStore.getState().currentFile).toBeNull();
    });
  });

  describe('上传任务管理', () => {
    it('addUploadTask 加入任务列表', () => {
      useFileStore.getState().addUploadTask(makeTask('t1'));
      expect(useFileStore.getState().uploadTasks).toHaveLength(1);
      expect(useFileStore.getState().uploadTasks[0].uid).toBe('t1');
    });

    it('updateUploadTask 按 uid 部分更新', () => {
      useFileStore.getState().addUploadTask(makeTask('t1'));
      useFileStore.getState().updateUploadTask('t1', { percent: 50, status: 'uploading' });
      expect(useFileStore.getState().uploadTasks[0].percent).toBe(50);
      expect(useFileStore.getState().uploadTasks[0].status).toBe('uploading');
    });

    it('removeUploadTask 按 uid 删除', () => {
      useFileStore.getState().addUploadTask(makeTask('t1'));
      useFileStore.getState().addUploadTask(makeTask('t2'));
      useFileStore.getState().removeUploadTask('t1');
      expect(useFileStore.getState().uploadTasks).toHaveLength(1);
      expect(useFileStore.getState().uploadTasks[0].uid).toBe('t2');
    });

    it('clearCompletedUploadTasks 清除 completed/instant/failed 任务', () => {
      useFileStore.getState().addUploadTask(makeTask('t1', 'completed'));
      useFileStore.getState().addUploadTask(makeTask('t2', 'instant'));
      useFileStore.getState().addUploadTask(makeTask('t3', 'failed'));
      useFileStore.getState().addUploadTask(makeTask('t4', 'uploading'));
      useFileStore.getState().clearCompletedUploadTasks();
      expect(useFileStore.getState().uploadTasks).toHaveLength(1);
      expect(useFileStore.getState().uploadTasks[0].uid).toBe('t4');
    });
  });

  describe('reset', () => {
    it('reset 还原所有状态到初始值', () => {
      useFileStore.getState().setFiles([makeFile('1')]);
      useFileStore.getState().setTotal(1);
      useFileStore.getState().setLoading(true);
      useFileStore.getState().addUploadTask(makeTask('t1'));
      useFileStore.getState().reset();
      expect(useFileStore.getState().files).toEqual([]);
      expect(useFileStore.getState().total).toBe(0);
      expect(useFileStore.getState().loading).toBe(false);
      expect(useFileStore.getState().uploadTasks).toEqual([]);
      expect(useFileStore.getState().params).toEqual({ page: 1, pageSize: 20 });
    });
  });
});
