package com.redteam.search.service;

import com.redteam.common.api.dto.TagDictDTO;
import com.redteam.common.api.dto.TagTreeVO;
import com.redteam.common.api.dto.FileTagVO;
import com.redteam.common.entity.FileTagEntity;
import com.redteam.common.entity.TagDictEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.FileTagMapper;
import com.redteam.common.mapper.TagDictMapper;
import com.redteam.common.service.impl.TagServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 标签服务单元测试
 *
 * <p>使用 Mockito mock {@link TagDictMapper} 与 {@link FileTagMapper}，
 * 验证 {@link TagServiceImpl} 的字典 CRUD、文件打标去重及按标签检索逻辑。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagServiceTest {

    @Mock
    private TagDictMapper tagDictMapper;

    @Mock
    private FileTagMapper fileTagMapper;

    @InjectMocks
    private TagServiceImpl tagService;

    // ==================== listTags ====================

    /**
     * 查询全部标签（无筛选条件）
     */
    @Test
    @DisplayName("listTags：无筛选条件应返回全部标签")
    void testListTags_All() {
        List<TagDictEntity> all = Arrays.asList(
                buildTagEntity(1L, "L1.FILE.TYPE", "文件类型", "L1", "FILE", null, 1),
                buildTagEntity(2L, "L2.UPLOAD.SOURCE.WEB", "Web端", "L2", "BUSINESS", "L2.UPLOAD.SOURCE", 1),
                buildTagEntity(3L, "L6.COMP.CLASSIFICATION.PUBLIC", "公开", "L6", "COMPLIANCE", "L6.COMP.CLASSIFICATION", 0)
        );
        when(tagDictMapper.selectList(any())).thenReturn(all);

        List<TagDictEntity> result = tagService.listTags(null, null, null);

        assertEquals(3, result.size(), "应返回全部 3 条标签");
        verify(tagDictMapper, times(1)).selectList(any());
    }

    /**
     * 按层级筛选
     */
    @Test
    @DisplayName("listTags：按 layer 筛选应只返回该层级标签")
    void testListTags_ByLayer() {
        List<TagDictEntity> l1Tags = Arrays.asList(
                buildTagEntity(1L, "L1.FILE.TYPE", "文件类型", "L1", "FILE", null, 1),
                buildTagEntity(2L, "L1.FILE.TYPE.PDF", "PDF文档", "L1", "FILE", "L1.FILE.TYPE", 1)
        );
        when(tagDictMapper.selectList(any())).thenReturn(l1Tags);

        List<TagDictEntity> result = tagService.listTags("L1", null, null);

        assertEquals(2, result.size(), "L1 层级应返回 2 条");
        result.forEach(t -> assertEquals("L1", t.getLayer(), "所有返回标签层级应为 L1"));
        verify(tagDictMapper, times(1)).selectList(any());
    }

    /**
     * 按启用状态筛选
     */
    @Test
    @DisplayName("listTags：按 enabled 筛选应只返回对应状态标签")
    void testListTags_ByEnabled() {
        List<TagDictEntity> disabledTags = Collections.singletonList(
                buildTagEntity(3L, "L6.COMP.CLASSIFICATION.PUBLIC", "公开", "L6", "COMPLIANCE", "L6.COMP.CLASSIFICATION", 0)
        );
        when(tagDictMapper.selectList(any())).thenReturn(disabledTags);

        List<TagDictEntity> result = tagService.listTags(null, null, 0);

        assertEquals(1, result.size(), "禁用标签应返回 1 条");
        assertEquals(0, result.get(0).getEnabled(), "返回标签应为禁用状态");
        verify(tagDictMapper, times(1)).selectList(any());
    }

    // ==================== getTagTree ====================

    /**
     * 树形结构正确性：父节点 + 子节点递归组装
     */
    @Test
    @DisplayName("getTagTree：应按 parent_code 正确构建层级树")
    void testGetTagTree() {
        TagDictEntity parent = buildTagEntity(1L, "L1.FILE.TYPE", "文件类型", "L1", "FILE", null, 1);
        TagDictEntity child = buildTagEntity(2L, "L1.FILE.TYPE.PDF", "PDF文档", "L1", "FILE", "L1.FILE.TYPE", 1);
        // 注意：父节点须排在子节点前
        when(tagDictMapper.selectList(any())).thenReturn(Arrays.asList(parent, child));

        TagTreeVO tree = tagService.getTagTree();

        assertNotNull(tree, "树根不应为空");
        assertEquals("全部标签", tree.getTagName(), "虚拟根节点名称应为'全部标签'");
        assertNotNull(tree.getChildren(), "虚拟根的 children 不应为空");
        assertEquals(1, tree.getChildren().size(), "应只有 1 个根标签");

        TagTreeVO parentNode = tree.getChildren().get(0);
        assertEquals(1L, parentNode.getId(), "根节点 id 应为 1");
        assertEquals("L1.FILE.TYPE", parentNode.getTagCode(), "根节点 tagCode 应为 L1.FILE.TYPE");
        assertNotNull(parentNode.getChildren(), "根节点的 children 不应为空");
        assertEquals(1, parentNode.getChildren().size(), "根节点应有 1 个子标签");

        TagTreeVO childNode = parentNode.getChildren().get(0);
        assertEquals(2L, childNode.getId(), "子节点 id 应为 2");
        assertEquals("L1.FILE.TYPE.PDF", childNode.getTagCode(), "子节点 tagCode 应为 L1.FILE.TYPE.PDF");
        assertTrue(childNode.getChildren() == null || childNode.getChildren().isEmpty(),
                "叶子节点 children 应为空");
    }

    // ==================== createTag ====================

    /**
     * 创建标签成功
     */
    @Test
    @DisplayName("createTag：编码不重复时应创建成功")
    void testCreateTag_Success() {
        TagDictDTO dto = new TagDictDTO();
        dto.setTagCode("L1.FILE.TYPE.NEW");
        dto.setTagName("新标签");
        dto.setLayer("L1");
        dto.setCategory("FILE");

        when(tagDictMapper.selectByCode(eq("L1.FILE.TYPE.NEW"))).thenReturn(null);
        when(tagDictMapper.insert(any(TagDictEntity.class))).thenAnswer(invocation -> {
            TagDictEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            entity.setCreatedAt(LocalDateTime.now());
            return 1;
        });

        TagDictEntity result = tagService.createTag(dto);

        assertNotNull(result, "返回实体不应为空");
        assertEquals(100L, result.getId(), "自增ID应为 100");
        assertEquals("L1.FILE.TYPE.NEW", result.getTagCode(), "tagCode 应与入参一致");
        assertEquals("新标签", result.getTagName(), "tagName 应与入参一致");
        assertEquals(1, result.getEnabled(), "未指定 enabled 时应默认为 1");
        assertEquals(0, result.getIsMulti(), "未指定 isMulti 时应默认为 0");

        ArgumentCaptor<TagDictEntity> captor = ArgumentCaptor.forClass(TagDictEntity.class);
        verify(tagDictMapper, times(1)).insert(captor.capture());
        assertEquals("L1.FILE.TYPE.NEW", captor.getValue().getTagCode());
    }

    /**
     * 编码重复应抛异常
     */
    @Test
    @DisplayName("createTag：编码已存在应抛出 BusinessException")
    void testCreateTag_DuplicateCode() {
        TagDictDTO dto = new TagDictDTO();
        dto.setTagCode("L1.FILE.TYPE.PDF");
        dto.setTagName("PDF文档");
        dto.setLayer("L1");

        when(tagDictMapper.selectByCode(eq("L1.FILE.TYPE.PDF")))
                .thenReturn(buildTagEntity(2L, "L1.FILE.TYPE.PDF", "PDF文档", "L1", "FILE", "L1.FILE.TYPE", 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagService.createTag(dto),
                "编码重复应抛出 BusinessException");
        assertTrue(ex.getMessage().contains("已存在"), "异常消息应包含'已存在'");
        verify(tagDictMapper, never()).insert(any(TagDictEntity.class));
    }

    // ==================== updateTag ====================

    /**
     * 更新标签成功（tagCode 不可修改）
     */
    @Test
    @DisplayName("updateTag：应更新字段且不修改 tagCode")
    void testUpdateTag_Success() {
        TagDictEntity existing = buildTagEntity(1L, "L1.FILE.TYPE", "文件类型", "L1", "FILE", null, 1);
        when(tagDictMapper.selectById(eq(1L))).thenReturn(existing);
        when(tagDictMapper.updateById(any(TagDictEntity.class))).thenReturn(1);

        TagDictDTO dto = new TagDictDTO();
        dto.setTagCode("SHOULD.NOT.CHANGE"); // 尝试修改编码，应被忽略
        dto.setTagName("文件类型-已更新");
        dto.setLayer("L1");
        dto.setCategory("FILE");
        dto.setEnabled(0);

        TagDictEntity result = tagService.updateTag(1L, dto);

        assertEquals("L1.FILE.TYPE", result.getTagCode(), "tagCode 不应被修改");
        assertEquals("文件类型-已更新", result.getTagName(), "tagName 应已更新");
        assertEquals(0, result.getEnabled(), "enabled 应已更新为 0");

        ArgumentCaptor<TagDictEntity> captor = ArgumentCaptor.forClass(TagDictEntity.class);
        verify(tagDictMapper, times(1)).updateById(captor.capture());
        assertEquals("L1.FILE.TYPE", captor.getValue().getTagCode(), "传入 updateById 的 tagCode 应保持原值");
    }

    // ==================== toggleTag ====================

    /**
     * 启用/禁用切换：1 → 0
     */
    @Test
    @DisplayName("toggleTag：启用状态 1 应切换为 0")
    void testToggleTag() {
        TagDictEntity enabled = buildTagEntity(1L, "L1.FILE.TYPE", "文件类型", "L1", "FILE", null, 1);
        when(tagDictMapper.selectById(eq(1L))).thenReturn(enabled);
        when(tagDictMapper.updateById(any(TagDictEntity.class))).thenReturn(1);

        tagService.toggleTag(1L);

        ArgumentCaptor<TagDictEntity> captor = ArgumentCaptor.forClass(TagDictEntity.class);
        verify(tagDictMapper, times(1)).updateById(captor.capture());
        assertEquals(0, captor.getValue().getEnabled(), "enabled=1 切换后应为 0");
    }

    /**
     * 启用/禁用切换：0 → 1
     */
    @Test
    @DisplayName("toggleTag：禁用状态 0 应切换为 1")
    void testToggleTag_DisabledToEnabled() {
        TagDictEntity disabled = buildTagEntity(1L, "L1.FILE.TYPE", "文件类型", "L1", "FILE", null, 0);
        when(tagDictMapper.selectById(eq(1L))).thenReturn(disabled);
        when(tagDictMapper.updateById(any(TagDictEntity.class))).thenReturn(1);

        tagService.toggleTag(1L);

        ArgumentCaptor<TagDictEntity> captor = ArgumentCaptor.forClass(TagDictEntity.class);
        verify(tagDictMapper, times(1)).updateById(captor.capture());
        assertEquals(1, captor.getValue().getEnabled(), "enabled=0 切换后应为 1");
    }

    // ==================== deleteTag ====================

    /**
     * 删除标签 + 同步清理文件标签关联
     */
    @Test
    @DisplayName("deleteTag：应删除标签并清理 file_tags 关联")
    void testDeleteTag() {
        TagDictEntity existing = buildTagEntity(1L, "L1.FILE.TYPE", "文件类型", "L1", "FILE", null, 1);
        when(tagDictMapper.selectById(eq(1L))).thenReturn(existing);
        when(tagDictMapper.deleteById(eq(1L))).thenReturn(1);
        when(fileTagMapper.delete(any())).thenReturn(3);

        tagService.deleteTag(1L);

        verify(tagDictMapper, times(1)).deleteById(eq(1L));
        verify(fileTagMapper, times(1)).delete(any());
    }

    /**
     * 删除不存在的标签应抛异常
     */
    @Test
    @DisplayName("deleteTag：标签不存在应抛出 BusinessException")
    void testDeleteTag_NotFound() {
        when(tagDictMapper.selectById(eq(99L))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagService.deleteTag(99L),
                "标签不存在应抛出 BusinessException");
        assertTrue(ex.getMessage().contains("不存在"), "异常消息应包含'不存在'");
        verify(tagDictMapper, never()).deleteById(anyLong());
    }

    // ==================== addFileTags ====================

    /**
     * 批量打标成功（无已存在标签）
     */
    @Test
    @DisplayName("addFileTags：无已存在标签时应全部新增")
    void testAddFileTags_Success() {
        Long fileId = 10L;
        List<Long> tagIds = Arrays.asList(1L, 2L);

        when(fileTagMapper.selectByFileId(eq(fileId))).thenReturn(Collections.emptyList());
        when(tagDictMapper.selectBatchIds(eq(tagIds))).thenReturn(Arrays.asList(
                buildTagEntity(1L, "L1.FILE.TYPE.PDF", "PDF文档", "L1", "FILE", "L1.FILE.TYPE", 1),
                buildTagEntity(2L, "L1.FILE.TYPE.DOCX", "Word文档", "L1", "FILE", "L1.FILE.TYPE", 1)
        ));
        when(fileTagMapper.batchInsert(anyList())).thenReturn(2);

        tagService.addFileTags(fileId, tagIds, null);

        ArgumentCaptor<List<FileTagEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(fileTagMapper, times(1)).batchInsert(captor.capture());
        List<FileTagEntity> inserted = captor.getValue();
        assertEquals(2, inserted.size(), "应批量插入 2 条记录");
        // source 默认 MANUAL
        inserted.forEach(ft -> assertEquals("MANUAL", ft.getSource(), "source 默认应为 MANUAL"));
        // tagCode 应被冗余写入
        assertEquals("L1.FILE.TYPE.PDF", inserted.get(0).getTagCode(), "第一条 tagCode 应为 L1.FILE.TYPE.PDF");
    }

    /**
     * 已有标签应跳过
     */
    @Test
    @DisplayName("addFileTags：已存在的标签应被跳过")
    void testAddFileTags_Dedup() {
        Long fileId = 10L;
        List<Long> tagIds = Arrays.asList(1L, 2L);

        // 文件已存在标签 1
        FileTagEntity existing = new FileTagEntity();
        existing.setId(1L);
        existing.setFileId(fileId);
        existing.setTagId(1L);
        existing.setTagCode("L1.FILE.TYPE.PDF");
        existing.setSource("MANUAL");
        when(fileTagMapper.selectByFileId(eq(fileId))).thenReturn(Collections.singletonList(existing));
        // 仅查询新增的标签 2
        when(tagDictMapper.selectBatchIds(eq(Collections.singletonList(2L))))
                .thenReturn(Collections.singletonList(
                        buildTagEntity(2L, "L1.FILE.TYPE.DOCX", "Word文档", "L1", "FILE", "L1.FILE.TYPE", 1)));
        when(fileTagMapper.batchInsert(anyList())).thenReturn(1);

        tagService.addFileTags(fileId, tagIds, "AUTO");

        ArgumentCaptor<List<FileTagEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(fileTagMapper, times(1)).batchInsert(captor.capture());
        List<FileTagEntity> inserted = captor.getValue();
        assertEquals(1, inserted.size(), "应仅插入 1 条（跳过已存在的标签 1）");
        assertEquals(2L, inserted.get(0).getTagId(), "新增的应为标签 2");
        assertEquals("AUTO", inserted.get(0).getSource(), "source 应为传入的 AUTO");
    }

    /**
     * 全部标签均已存在时不应调用 batchInsert
     */
    @Test
    @DisplayName("addFileTags：全部标签已存在时应跳过插入")
    void testAddFileTags_AllDuplicated() {
        Long fileId = 10L;
        List<Long> tagIds = Collections.singletonList(1L);

        FileTagEntity existing = new FileTagEntity();
        existing.setFileId(fileId);
        existing.setTagId(1L);
        when(fileTagMapper.selectByFileId(eq(fileId))).thenReturn(Collections.singletonList(existing));

        tagService.addFileTags(fileId, tagIds, null);

        verify(fileTagMapper, never()).batchInsert(anyList());
    }

    // ==================== removeFileTag ====================

    /**
     * 取消文件标签
     */
    @Test
    @DisplayName("removeFileTag：应调用 deleteFileTag 删除关联")
    void testRemoveFileTag() {
        when(fileTagMapper.deleteFileTag(eq(10L), eq(1L))).thenReturn(1);

        tagService.removeFileTag(10L, 1L);

        verify(fileTagMapper, times(1)).deleteFileTag(eq(10L), eq(1L));
    }

    // ==================== getFileTags ====================

    /**
     * 查询文件标签（关联字典返回完整信息）
     */
    @Test
    @DisplayName("getFileTags：应关联字典返回完整的标签信息")
    void testGetFileTags() {
        Long fileId = 10L;

        FileTagEntity ft = new FileTagEntity();
        ft.setId(1L);
        ft.setFileId(fileId);
        ft.setTagId(2L);
        ft.setTagCode("L1.FILE.TYPE.PDF");
        ft.setSource("MANUAL");
        ft.setCreatedAt(LocalDateTime.now());
        when(fileTagMapper.selectByFileId(eq(fileId))).thenReturn(Collections.singletonList(ft));

        when(tagDictMapper.selectBatchIds(eq(Collections.singletonList(2L))))
                .thenReturn(Collections.singletonList(
                        buildTagEntity(2L, "L1.FILE.TYPE.PDF", "PDF文档", "L1", "FILE", "L1.FILE.TYPE", 1)));

        List<FileTagVO> result = tagService.getFileTags(fileId);

        assertEquals(1, result.size(), "应返回 1 条标签");
        FileTagVO vo = result.get(0);
        assertEquals(10L, vo.getFileId(), "fileId 应为 10");
        assertEquals(2L, vo.getTagId(), "tagId 应为 2");
        assertEquals("L1.FILE.TYPE.PDF", vo.getTagCode(), "tagCode 应为 L1.FILE.TYPE.PDF");
        assertEquals("PDF文档", vo.getTagName(), "tagName 应为关联字典的'PDF文档'");
        assertEquals("L1", vo.getLayer(), "layer 应为 L1");
        assertEquals("MANUAL", vo.getSource(), "source 应为 MANUAL");
    }

    /**
     * 文件无标签时应返回空列表
     */
    @Test
    @DisplayName("getFileTags：无标签时应返回空列表")
    void testGetFileTags_Empty() {
        when(fileTagMapper.selectByFileId(eq(10L))).thenReturn(Collections.emptyList());

        List<FileTagVO> result = tagService.getFileTags(10L);

        assertNotNull(result, "返回列表不应为 null");
        assertTrue(result.isEmpty(), "返回列表应为空");
    }

    // ==================== getFileIdsByTagId ====================

    /**
     * 按标签检索文件
     */
    @Test
    @DisplayName("getFileIdsByTagId：应返回打有该标签的文件ID列表")
    void testGetFilesByTagId() {
        Long tagId = 1L;
        FileTagEntity ft1 = new FileTagEntity();
        ft1.setFileId(10L);
        ft1.setTagId(tagId);
        FileTagEntity ft2 = new FileTagEntity();
        ft2.setFileId(20L);
        ft2.setTagId(tagId);
        when(fileTagMapper.selectByTagId(eq(tagId))).thenReturn(Arrays.asList(ft1, ft2));

        List<Long> fileIds = tagService.getFileIdsByTagId(tagId);

        assertEquals(2, fileIds.size(), "应返回 2 个文件ID");
        assertTrue(fileIds.contains(10L), "应包含 fileId=10");
        assertTrue(fileIds.contains(20L), "应包含 fileId=20");
    }

    /**
     * 按标签编码检索文件
     */
    @Test
    @DisplayName("getFileIdsByTagCode：应先查字典再查文件标签关联")
    void testGetFilesByTagCode() {
        String tagCode = "L1.FILE.TYPE.PDF";
        TagDictEntity tag = buildTagEntity(2L, tagCode, "PDF文档", "L1", "FILE", "L1.FILE.TYPE", 1);
        when(tagDictMapper.selectByCode(eq(tagCode))).thenReturn(tag);

        FileTagEntity ft = new FileTagEntity();
        ft.setFileId(10L);
        ft.setTagId(2L);
        when(fileTagMapper.selectByTagId(eq(2L))).thenReturn(Collections.singletonList(ft));

        List<Long> fileIds = tagService.getFileIdsByTagCode(tagCode);

        assertEquals(1, fileIds.size(), "应返回 1 个文件ID");
        assertEquals(10L, fileIds.get(0), "应返回 fileId=10");
        verify(tagDictMapper, times(1)).selectByCode(eq(tagCode));
        verify(fileTagMapper, times(1)).selectByTagId(eq(2L));
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用标签实体
     */
    private TagDictEntity buildTagEntity(Long id, String tagCode, String tagName,
                                         String layer, String category, String parentCode, Integer enabled) {
        TagDictEntity entity = new TagDictEntity();
        entity.setId(id);
        entity.setTagCode(tagCode);
        entity.setTagName(tagName);
        entity.setLayer(layer);
        entity.setCategory(category);
        entity.setParentCode(parentCode);
        entity.setEnabled(enabled);
        entity.setIsMulti(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
