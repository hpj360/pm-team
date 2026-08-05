package com.redteam.upload.service;

import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.exception.FileException;
import com.redteam.upload.dto.MultipartUploadInfoVO;
import com.redteam.upload.entity.FileChunkEntity;
import com.redteam.upload.entity.FileEntity;
import com.redteam.upload.entity.UploadTaskEntity;
import com.redteam.upload.mapper.FileChunkMapper;
import com.redteam.upload.mapper.FileMapper;
import com.redteam.upload.mapper.UploadTaskMapper;
import com.redteam.upload.producer.FileEventProducer;
import com.redteam.upload.service.impl.FileServiceImpl;
import io.minio.ComposeObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 文件服务单元测试
 *
 * <p>覆盖单文件上传、秒传、分片上传、完成分片、取消分片、下载、预览、删除、更新等核心场景。
 * 依赖（MinioClient / RedissonClient / Mapper / FileEventProducer）均通过 Mockito 模拟。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceImplTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private FileMapper fileMapper;

    @Mock
    private FileChunkMapper fileChunkMapper;

    @Mock
    private UploadTaskMapper uploadTaskMapper;

    @Mock
    private FileEventProducer fileEventProducer;

    @Mock
    private RLock rLock;

    @InjectMocks
    private FileServiceImpl fileService;

    /**
     * 测试用文件名
     */
    private static final String FILE_NAME = "test.txt";

    /**
     * 测试用文件内容
     */
    private static final byte[] FILE_BYTES = "hello-redteam".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(fileService, "bucketName", "redteam-files");
        ReflectionTestUtils.setField(fileService, "chunkSize", 5L * 1024 * 1024);
        ReflectionTestUtils.setField(fileService, "previewExpireSeconds", 3600);

        // MinIO 存储桶默认存在
        when(minioClient.bucketExists(any())).thenReturn(true);

        // Redisson 锁默认能拿到
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // Kafka 发送不抛异常
        doNothing().when(fileEventProducer).sendFileUploadedEvent(any(), any());
        doNothing().when(fileEventProducer).sendFileDeletedEvent(any(FileEntity.class), any());
        doNothing().when(fileEventProducer).sendFileDeletedEvent(anyLong(), any());
    }

    /**
     * 构造测试用 MultipartFile
     */
    private MultipartFile buildMultipartFile(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "text/plain", bytes);
    }

    /**
     * 构造测试用 MultipartFile（默认内容）
     */
    private MultipartFile buildMultipartFile() {
        return buildMultipartFile(FILE_NAME, FILE_BYTES);
    }

    /**
     * 构造一个已完成的 FileEntity
     */
    private FileEntity buildCompletedFile() {
        FileEntity entity = new FileEntity();
        entity.setId(1001L);
        entity.setFilename("uuid.txt");
        entity.setOriginalFilename(FILE_NAME);
        entity.setStoragePath("files/2026/07/27/uuid.txt");
        entity.setFileSize((long) FILE_BYTES.length);
        entity.setFileType("txt");
        entity.setMimeType("text/plain");
        entity.setFileMd5("md5-fake");
        entity.setFileSm3("sm3-fake");
        entity.setUploadStatus("COMPLETED");
        return entity;
    }

    // ==================== upload ====================

    @Nested
    @DisplayName("upload: 单文件上传")
    class UploadTests {

        @Test
        @DisplayName("新文件上传成功：写入 MinIO + DB，并发送 Kafka 事件")
        void upload_newFile_success() throws Exception {
            MultipartFile file = buildMultipartFile();
            when(fileMapper.selectOne(any())).thenReturn(null); // SM3 未命中
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
            when(fileMapper.insert(any(FileEntity.class))).thenReturn(1);

            FileInfoDTO result = fileService.upload(file, 100L, "tag1", "desc", 2, 0);

            assertNotNull(result);
            assertEquals(FILE_NAME, result.getOriginalFilename());
            assertEquals("txt", result.getFileType());
            verify(fileMapper).insert(any(FileEntity.class));
            verify(fileEventProducer).sendFileUploadedEvent(any(FileEntity.class), any());
        }

        @Test
        @DisplayName("秒传命中：返回已存在文件信息，不写 MinIO 与 DB")
        void upload_instantUpload_hit() throws Exception {
            MultipartFile file = buildMultipartFile();
            FileEntity exist = buildCompletedFile();
            when(fileMapper.selectOne(any())).thenReturn(exist);

            FileInfoDTO result = fileService.upload(file, 100L, "tag1", "desc", 2, 0);

            assertNotNull(result);
            assertEquals(exist.getId(), result.getId());
            verify(fileMapper, never()).insert(any(FileEntity.class));
            verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("空文件：抛 FileException")
        void upload_emptyFile_throwsException() {
            MultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
            assertThrows(FileException.class, () -> fileService.upload(file, null, null, null, null, null));
        }

        @Test
        @DisplayName("文件名为空：抛 FileException")
        void upload_blankFilename_throwsException() {
            MultipartFile file = new MockMultipartFile("file", "", "text/plain", FILE_BYTES);
            assertThrows(FileException.class, () -> fileService.upload(file, null, null, null, null, null));
        }

        @Test
        @DisplayName("不支持的文件类型：抛 FileException")
        void upload_unsupportedType_throwsException() {
            MultipartFile file = new MockMultipartFile("file", "malware.virus", "application/octet-stream", FILE_BYTES);
            assertThrows(FileException.class, () -> fileService.upload(file, null, null, null, null, null));
        }
    }

    // ==================== checkFileByMd5 / checkFileBySm3 ====================

    @Nested
    @DisplayName("checkFileByMd5 / checkFileBySm3: 秒传检查")
    class CheckTests {

        @Test
        @DisplayName("MD5 命中：返回 DTO")
        void checkFileByMd5_hit() {
            when(fileMapper.selectOne(any())).thenReturn(buildCompletedFile());
            FileInfoDTO dto = fileService.checkFileByMd5("md5-fake");
            assertNotNull(dto);
            assertEquals(1001L, dto.getId());
        }

        @Test
        @DisplayName("MD5 未命中：返回 null")
        void checkFileByMd5_miss() {
            when(fileMapper.selectOne(any())).thenReturn(null);
            assertNull(fileService.checkFileByMd5("missing"));
        }

        @Test
        @DisplayName("MD5 为空：返回 null，不查库")
        void checkFileByMd5_blank_returnsNull() {
            assertNull(fileService.checkFileByMd5(""));
            assertNull(fileService.checkFileByMd5(null));
            verifyNoInteractions(fileMapper);
        }

        @Test
        @DisplayName("SM3 命中：返回 DTO")
        void checkFileBySm3_hit() {
            when(fileMapper.selectOne(any())).thenReturn(buildCompletedFile());
            FileInfoDTO dto = fileService.checkFileBySm3("sm3-fake");
            assertNotNull(dto);
            assertEquals(1001L, dto.getId());
        }

        @Test
        @DisplayName("SM3 未命中：返回 null")
        void checkFileBySm3_miss() {
            when(fileMapper.selectOne(any())).thenReturn(null);
            assertNull(fileService.checkFileBySm3("missing"));
        }

        @Test
        @DisplayName("SM3 为空：返回 null，不查库")
        void checkFileBySm3_blank_returnsNull() {
            assertNull(fileService.checkFileBySm3(""));
            assertNull(fileService.checkFileBySm3(null));
            verifyNoInteractions(fileMapper);
        }
    }

    // ==================== initMultipartUpload ====================

    @Nested
    @DisplayName("initMultipartUpload: 初始化分片上传")
    class InitMultipartTests {

        @Test
        @DisplayName("成功：返回 uploadId，落库任务")
        void initMultipartUpload_success() {
            when(uploadTaskMapper.insert(any(UploadTaskEntity.class))).thenReturn(1);

            String uploadId = fileService.initMultipartUpload("big.zip", 10L * 1024 * 1024, "md5-xxx", 200L);

            assertNotNull(uploadId);
            assertEquals(32, uploadId.length());

            ArgumentCaptor<UploadTaskEntity> captor = ArgumentCaptor.forClass(UploadTaskEntity.class);
            verify(uploadTaskMapper).insert(captor.capture());
            UploadTaskEntity task = captor.getValue();
            assertEquals(uploadId, task.getUploadId());
            assertEquals("big.zip", task.getFileName());
            assertEquals(2, task.getChunkCount()); // 10MB / 5MB = 2
            assertEquals("UPLOADING", task.getStatus());
        }

        @Test
        @DisplayName("文件名为空：抛 FileException")
        void initMultipartUpload_blankFilename_throwsException() {
            assertThrows(FileException.class,
                    () -> fileService.initMultipartUpload("", 1024L, "md5", null));
        }

        @Test
        @DisplayName("文件大小非法：抛 FileException")
        void initMultipartUpload_illegalSize_throwsException() {
            assertThrows(FileException.class,
                    () -> fileService.initMultipartUpload("a.txt", 0L, "md5", null));
            assertThrows(FileException.class,
                    () -> fileService.initMultipartUpload("a.txt", null, "md5", null));
        }
    }

    // ==================== getMultipartUploadInfo ====================

    @Nested
    @DisplayName("getMultipartUploadInfo: 查询分片进度")
    class GetMultipartUploadInfoTests {

        @Test
        @DisplayName("成功：返回已上传分片序号列表")
        void getMultipartUploadInfo_success() {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setFileName("big.zip");
            task.setFileSize(10L * 1024 * 1024);
            task.setChunkSize(5L * 1024 * 1024);
            task.setChunkCount(2);
            task.setCompletedChunks(1);
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            FileChunkEntity c1 = new FileChunkEntity();
            c1.setChunkNumber(1);
            c1.setUploaded(true);
            FileChunkEntity c2 = new FileChunkEntity();
            c2.setChunkNumber(2);
            c2.setUploaded(false);
            when(fileChunkMapper.selectList(any())).thenReturn(Arrays.asList(c1, c2));

            MultipartUploadInfoVO vo = fileService.getMultipartUploadInfo("uid-1");

            assertNotNull(vo);
            assertEquals("uid-1", vo.getUploadId());
            assertEquals(2, vo.getChunkCount());
            assertEquals(1, vo.getUploadedChunks().size());
            assertEquals(1, vo.getUploadedChunks().get(0));
        }

        @Test
        @DisplayName("上传ID为空：抛 FileException")
        void getMultipartUploadInfo_blankId_throwsException() {
            assertThrows(FileException.class, () -> fileService.getMultipartUploadInfo(""));
            assertThrows(FileException.class, () -> fileService.getMultipartUploadInfo(null));
        }

        @Test
        @DisplayName("任务不存在：抛 FileException")
        void getMultipartUploadInfo_notFound_throwsException() {
            when(uploadTaskMapper.selectOne(any())).thenReturn(null);
            assertThrows(FileException.class, () -> fileService.getMultipartUploadInfo("missing"));
        }
    }

    // ==================== uploadPart ====================

    @Nested
    @DisplayName("uploadPart: 上传分片")
    class UploadPartTests {

        @Test
        @DisplayName("成功：使用分布式锁，上传 MinIO，写入分片记录")
        void uploadPart_success() throws Exception {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setChunkCount(2);
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);
            when(fileChunkMapper.selectOne(any())).thenReturn(null);
            when(fileChunkMapper.selectCount(any())).thenReturn(1L);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

            String eTag = fileService.uploadPart("uid-1", 1, buildMultipartFile("p1", FILE_BYTES));

            assertNotNull(eTag);
            verify(redissonClient).getLock(eq("upload:part:uid-1:1"));
            verify(rLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
            verify(rLock).unlock();
            verify(fileChunkMapper).insert(any(FileChunkEntity.class));
            verify(uploadTaskMapper).updateById(any(UploadTaskEntity.class));
        }

        @Test
        @DisplayName("已存在分片：更新而非新增")
        void uploadPart_existingChunk_updates() throws Exception {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setChunkCount(2);
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            FileChunkEntity exist = new FileChunkEntity();
            exist.setId(1L);
            exist.setChunkNumber(1);
            exist.setUploaded(false);
            when(fileChunkMapper.selectOne(any())).thenReturn(exist);
            when(fileChunkMapper.selectCount(any())).thenReturn(1L);

            String eTag = fileService.uploadPart("uid-1", 1, buildMultipartFile("p1", FILE_BYTES));

            assertNotNull(eTag);
            verify(fileChunkMapper).updateById(any(FileChunkEntity.class));
            verify(fileChunkMapper, never()).insert(any(FileChunkEntity.class));
        }

        @Test
        @DisplayName("任务已完成：抛 FileException，不调用锁")
        void uploadPart_taskCompleted_throwsException() {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setStatus("COMPLETED");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            assertThrows(FileException.class,
                    () -> fileService.uploadPart("uid-1", 1, buildMultipartFile()));
            verifyNoInteractions(redissonClient);
        }

        @Test
        @DisplayName("任务已取消：抛 FileException")
        void uploadPart_taskCancelled_throwsException() {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setStatus("CANCELLED");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            assertThrows(FileException.class,
                    () -> fileService.uploadPart("uid-1", 1, buildMultipartFile()));
        }

        @Test
        @DisplayName("获取锁失败：抛 FileException")
        void uploadPart_lockFailed_throwsException() throws Exception {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

            assertThrows(FileException.class,
                    () -> fileService.uploadPart("uid-1", 1, buildMultipartFile()));
        }

        @Test
        @DisplayName("上传ID为空：抛 FileException")
        void uploadPart_blankUploadId_throwsException() {
            assertThrows(FileException.class,
                    () -> fileService.uploadPart("", 1, buildMultipartFile()));
        }

        @Test
        @DisplayName("分片序号非法：抛 FileException")
        void uploadPart_illegalPartNumber_throwsException() {
            assertThrows(FileException.class,
                    () -> fileService.uploadPart("uid-1", 0, buildMultipartFile()));
            assertThrows(FileException.class,
                    () -> fileService.uploadPart("uid-1", null, buildMultipartFile()));
        }

        @Test
        @DisplayName("分片内容为空：抛 FileException")
        void uploadPart_emptyContent_throwsException() {
            assertThrows(FileException.class,
                    () -> fileService.uploadPart("uid-1", 1,
                            new MockMultipartFile("file", "p1", "text/plain", new byte[0])));
        }
    }

    // ==================== completeMultipartUpload ====================

    @Nested
    @DisplayName("completeMultipartUpload: 完成分片上传")
    class CompleteMultipartTests {

        @Test
        @DisplayName("composeObject 成功路径：服务端合并，不调用 putObject")
        void completeMultipartUpload_composeSuccess() throws Exception {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setFileName("big.txt");
            task.setFileSize(13L);
            task.setChunkSize(5L * 1024 * 1024);
            task.setChunkCount(1);
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            // composeObject 成功返回 null
            when(minioClient.composeObject(any(ComposeObjectArgs.class))).thenReturn(null);
            // statObject 返回合并后大小
            StatObjectResponse stat = org.mockito.Mockito.mock(StatObjectResponse.class);
            when(stat.size()).thenReturn((long) FILE_BYTES.length);
            when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);
            // getObject 返回合并后对象流（用于计算 SM3）
            when(minioClient.getObject(any(GetObjectArgs.class)))
                    .thenReturn(new GetObjectResponse(
                            Headers.of(), "redteam-files", "local", "obj",
                            new ByteArrayInputStream(FILE_BYTES)));
            when(fileMapper.selectOne(any())).thenReturn(null); // 合并后 SM3 未命中
            when(fileMapper.insert(any(FileEntity.class))).thenReturn(1);
            when(fileChunkMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

            String parts = "[{\"partNumber\":1,\"eTag\":\"etag-1\"}]";
            FileInfoDTO result = fileService.completeMultipartUpload("uid-1", parts);

            assertNotNull(result);
            assertEquals("big.txt", result.getOriginalFilename());
            // composeObject 被调用，putObject 未被调用（服务端合并）
            verify(minioClient).composeObject(any(ComposeObjectArgs.class));
            verify(minioClient, never()).putObject(any(PutObjectArgs.class));
            verify(fileMapper).insert(any(FileEntity.class));
            verify(fileEventProducer).sendFileUploadedEvent(any(FileEntity.class), any());
            verify(uploadTaskMapper).updateById(any(UploadTaskEntity.class));
        }

        @Test
        @DisplayName("composeObject 失败降级路径：回退到应用层合并")
        void completeMultipartUpload_composeFailure_fallback() throws Exception {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-2");
            task.setFileName("big.txt");
            task.setFileSize(13L);
            task.setChunkSize(5L * 1024 * 1024);
            task.setChunkCount(1);
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            // composeObject 抛异常，触发降级
            when(minioClient.composeObject(any(ComposeObjectArgs.class)))
                    .thenThrow(new RuntimeException("composeObject not supported"));
            // 降级路径：getObject 返回分片流用于应用层合并
            when(minioClient.getObject(any(GetObjectArgs.class)))
                    .thenReturn(new GetObjectResponse(
                            Headers.of(), "redteam-files", "local", "obj",
                            new ByteArrayInputStream(FILE_BYTES)));
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
            when(fileMapper.selectOne(any())).thenReturn(null); // SM3 未命中
            when(fileMapper.insert(any(FileEntity.class))).thenReturn(1);
            when(fileChunkMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

            String parts = "[{\"partNumber\":1,\"eTag\":\"etag-1\"}]";
            FileInfoDTO result = fileService.completeMultipartUpload("uid-2", parts);

            assertNotNull(result);
            // composeObject 和 putObject 均被调用（先尝试 compose，失败后降级到 putObject）
            verify(minioClient).composeObject(any(ComposeObjectArgs.class));
            verify(minioClient).putObject(any(PutObjectArgs.class));
            verify(fileMapper).insert(any(FileEntity.class));
        }

        @Test
        @DisplayName("秒传命中：删除新合并对象，返回旧文件")
        void completeMultipartUpload_instantUpload_hit() throws Exception {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setFileName("big.txt");
            task.setFileSize(13L);
            task.setChunkSize(5L * 1024 * 1024);
            task.setChunkCount(1);
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            // composeObject 成功
            when(minioClient.composeObject(any(ComposeObjectArgs.class))).thenReturn(null);
            StatObjectResponse stat = org.mockito.Mockito.mock(StatObjectResponse.class);
            when(stat.size()).thenReturn((long) FILE_BYTES.length);
            when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);
            when(minioClient.getObject(any(GetObjectArgs.class)))
                    .thenReturn(new GetObjectResponse(
                            Headers.of(), "redteam-files", "local", "obj",
                            new ByteArrayInputStream(FILE_BYTES)));

            // 合并后 SM3 命中已有文件
            FileEntity exist = buildCompletedFile();
            when(fileMapper.selectOne(any())).thenReturn(exist);

            String parts = "[{\"partNumber\":1,\"eTag\":\"etag-1\"}]";
            FileInfoDTO result = fileService.completeMultipartUpload("uid-1", parts);

            assertNotNull(result);
            assertEquals(exist.getId(), result.getId());
            // 删除新合并对象
            verify(minioClient).removeObject(any(RemoveObjectArgs.class));
            // 不写 t_file
            verify(fileMapper, never()).insert(any(FileEntity.class));
        }

        @Test
        @DisplayName("分片不完整：抛 FileException")
        void completeMultipartUpload_chunksIncomplete_throwsException() {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setChunkCount(2);
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            // 仅 1 个分片，但期望 2 个
            String parts = "[{\"partNumber\":1,\"eTag\":\"etag-1\"}]";
            assertThrows(FileException.class, () -> fileService.completeMultipartUpload("uid-1", parts));
        }

        @Test
        @DisplayName("任务已完成：抛 FileException")
        void completeMultipartUpload_alreadyCompleted_throwsException() {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setChunkCount(1);
            task.setStatus("COMPLETED");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            assertThrows(FileException.class,
                    () -> fileService.completeMultipartUpload("uid-1", "[]"));
        }

        @Test
        @DisplayName("任务已取消：抛 FileException")
        void completeMultipartUpload_cancelled_throwsException() {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setChunkCount(1);
            task.setStatus("CANCELLED");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            assertThrows(FileException.class,
                    () -> fileService.completeMultipartUpload("uid-1", "[]"));
        }
    }

    // ==================== cancelMultipartUpload ====================

    @Nested
    @DisplayName("cancelMultipartUpload: 取消分片上传")
    class CancelMultipartTests {

        @Test
        @DisplayName("成功：清理 MinIO 分片、删除分片记录、更新任务为 CANCELLED")
        void cancelMultipartUpload_success() throws Exception {
            UploadTaskEntity task = new UploadTaskEntity();
            task.setUploadId("uid-1");
            task.setChunkCount(2);
            task.setStatus("UPLOADING");
            when(uploadTaskMapper.selectOne(any())).thenReturn(task);

            fileService.cancelMultipartUpload("uid-1");

            // 2 个分片对象被清理
            verify(minioClient, times(2)).removeObject(any(RemoveObjectArgs.class));
            verify(fileChunkMapper).delete(any());
            verify(uploadTaskMapper).updateById(argThat(t -> "CANCELLED".equals(((UploadTaskEntity) t).getStatus())));
        }

        @Test
        @DisplayName("上传ID为空：抛 FileException")
        void cancelMultipartUpload_blankId_throwsException() {
            assertThrows(FileException.class, () -> fileService.cancelMultipartUpload(""));
        }

        @Test
        @DisplayName("任务不存在：抛 FileException")
        void cancelMultipartUpload_taskNotFound_throwsException() {
            when(uploadTaskMapper.selectOne(any())).thenReturn(null);
            assertThrows(FileException.class, () -> fileService.cancelMultipartUpload("missing"));
        }
    }

    // ==================== download ====================

    @Nested
    @DisplayName("download: 文件下载")
    class DownloadTests {

        @Test
        @DisplayName("成功：流式写入 HttpServletResponse")
        void download_success() throws Exception {
            FileEntity entity = buildCompletedFile();
            when(fileMapper.selectById(1001L)).thenReturn(entity);
            when(minioClient.getObject(any(GetObjectArgs.class)))
                    .thenReturn(new GetObjectResponse(
                            Headers.of(), "redteam-files", "local", "obj",
                            new ByteArrayInputStream(FILE_BYTES)));

            HttpServletResponse response = mock(HttpServletResponse.class);
            ServletOutputStream out = mock(ServletOutputStream.class);
            when(response.getOutputStream()).thenReturn(out);

            fileService.download(1001L, response);

            verify(response).setContentType("text/plain");
            verify(response).setHeader(eq("Content-Disposition"), anyString());
            verify(out).write(any(byte[].class), anyInt(), anyInt());
            verify(fileMapper).updateById(any(FileEntity.class)); // 更新下载次数
        }

        @Test
        @DisplayName("文件不存在：抛 FileException")
        void download_notFound_throwsException() {
            when(fileMapper.selectById(any())).thenReturn(null);
            assertThrows(FileException.class,
                    () -> fileService.download(9999L, mock(HttpServletResponse.class)));
        }

        @Test
        @DisplayName("id 为 null：抛 FileException")
        void download_nullId_throwsException() {
            assertThrows(FileException.class,
                    () -> fileService.download(null, mock(HttpServletResponse.class)));
        }
    }

    // ==================== getPreviewUrl ====================

    @Nested
    @DisplayName("getPreviewUrl: 获取预签名 URL")
    class GetPreviewUrlTests {

        @Test
        @DisplayName("成功：返回预签名 URL，更新预览次数")
        void getPreviewUrl_success() throws Exception {
            FileEntity entity = buildCompletedFile();
            when(fileMapper.selectById(1001L)).thenReturn(entity);
            when(minioClient.getPresignedObjectUrl(any())).thenReturn("https://minio.example.com/preview");

            String url = fileService.getPreviewUrl(1001L);

            assertEquals("https://minio.example.com/preview", url);
            verify(fileMapper).updateById(any(FileEntity.class));
        }

        @Test
        @DisplayName("文件不存在：抛 FileException")
        void getPreviewUrl_notFound_throwsException() {
            when(fileMapper.selectById(any())).thenReturn(null);
            assertThrows(FileException.class, () -> fileService.getPreviewUrl(9999L));
        }
    }

    // ==================== getFileInfo ====================

    @Nested
    @DisplayName("getFileInfo: 获取文件详情")
    class GetFileInfoTests {

        @Test
        @DisplayName("成功：返回 DTO")
        void getFileInfo_success() {
            when(fileMapper.selectById(1001L)).thenReturn(buildCompletedFile());
            FileInfoDTO dto = fileService.getFileInfo(1001L);
            assertNotNull(dto);
            assertEquals(1001L, dto.getId());
            assertEquals("test.txt", dto.getOriginalFilename());
        }

        @Test
        @DisplayName("文件不存在：抛 FileException")
        void getFileInfo_notFound_throwsException() {
            when(fileMapper.selectById(any())).thenReturn(null);
            assertThrows(FileException.class, () -> fileService.getFileInfo(9999L));
        }
    }

    // ==================== deleteFile ====================

    @Nested
    @DisplayName("deleteFile: 软删除")
    class DeleteFileTests {

        @Test
        @DisplayName("成功：软删除 + 发送 Kafka 事件")
        void deleteFile_success() {
            FileEntity entity = buildCompletedFile();
            when(fileMapper.selectById(1001L)).thenReturn(entity);
            when(fileMapper.deleteById(1001L)).thenReturn(1);

            fileService.deleteFile(1001L);

            verify(fileMapper).deleteById(1001L);
            verify(fileEventProducer).sendFileDeletedEvent(any(FileEntity.class), any());
        }

        @Test
        @DisplayName("文件不存在：抛 FileException")
        void deleteFile_notFound_throwsException() {
            when(fileMapper.selectById(any())).thenReturn(null);
            assertThrows(FileException.class, () -> fileService.deleteFile(9999L));
        }

        @Test
        @DisplayName("Kafka 事件发送失败：不影响主流程")
        void deleteFile_kafkaFailure_doesNotPropagate() {
            FileEntity entity = buildCompletedFile();
            when(fileMapper.selectById(1001L)).thenReturn(entity);
            when(fileMapper.deleteById(1001L)).thenReturn(1);
            doThrow(new RuntimeException("Kafka 不可用"))
                    .when(fileEventProducer).sendFileDeletedEvent(any(FileEntity.class), any());

            assertDoesNotThrow(() -> fileService.deleteFile(1001L));
            verify(fileMapper).deleteById(1001L);
        }
    }

    // ==================== updateFileInfo ====================

    @Nested
    @DisplayName("updateFileInfo: 更新文件信息")
    class UpdateFileInfoTests {

        @Test
        @DisplayName("成功：更新 tags/description/sensitiveLevel/isPublic")
        void updateFileInfo_success() {
            FileEntity entity = buildCompletedFile();
            when(fileMapper.selectById(1001L)).thenReturn(entity);
            when(fileMapper.updateById(any(FileEntity.class))).thenReturn(1);

            FileInfoDTO dto = fileService.updateFileInfo(1001L, "new-tag", "new-desc", 3, 1);

            assertNotNull(dto);
            assertEquals("new-tag", dto.getTags());
            assertEquals("new-desc", dto.getDescription());
            assertEquals(3, dto.getSensitiveLevel());
            assertEquals(1, dto.getIsPublic());
            verify(fileMapper).updateById(any(FileEntity.class));
        }

        @Test
        @DisplayName("部分字段为 null：保留原值")
        void updateFileInfo_partialUpdate() {
            FileEntity entity = buildCompletedFile();
            entity.setTags("old-tag");
            entity.setDescription("old-desc");
            entity.setSensitiveLevel(1);
            entity.setIsPublic(0);
            when(fileMapper.selectById(1001L)).thenReturn(entity);

            fileService.updateFileInfo(1001L, null, null, null, null);

            assertEquals("old-tag", entity.getTags());
            assertEquals("old-desc", entity.getDescription());
            assertEquals(1, entity.getSensitiveLevel());
            assertEquals(0, entity.getIsPublic());
        }

        @Test
        @DisplayName("文件不存在：抛 FileException")
        void updateFileInfo_notFound_throwsException() {
            when(fileMapper.selectById(any())).thenReturn(null);
            assertThrows(FileException.class,
                    () -> fileService.updateFileInfo(9999L, "t", "d", 1, 0));
        }
    }
}
