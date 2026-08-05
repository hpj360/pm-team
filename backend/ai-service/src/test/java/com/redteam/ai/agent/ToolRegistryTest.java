package com.redteam.ai.agent;

import com.redteam.ai.agent.tool.AgentTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ToolRegistry} 单元测试
 *
 * <p>覆盖注册、查询、权限校验、执行四类场景。</p>
 *
 * @author 红方团队
 */
class ToolRegistryTest {

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        // 用空列表构造，避免依赖 Spring 注入
        toolRegistry = new ToolRegistry(Collections.emptyList());
    }

    /**
     * 用例 1: registerTool 应成功注册工具
     */
    @Test
    @DisplayName("registerTool_Success - 应成功注册工具")
    void testRegisterTool_Success() {
        AgentTool tool = createMockTool("search_files", "文件检索", "ai:agent:tool:search");
        toolRegistry.registerTool(tool);

        assertEquals(1, toolRegistry.listTools().size());
        assertTrue(toolRegistry.listToolNames().contains("search_files"));
    }

    /**
     * 用例 2: registerTool 空名称不应注册
     */
    @Test
    @DisplayName("registerTool_EmptyName - 空名称不应注册")
    void testRegisterTool_EmptyName() {
        AgentTool tool = createMockTool("", "空名称", "");
        toolRegistry.registerTool(tool);

        assertEquals(0, toolRegistry.listTools().size());
    }

    /**
     * 用例 3: getTool 应返回已注册工具
     */
    @Test
    @DisplayName("getTool_Success - 应返回已注册工具")
    void testGetTool_Success() {
        AgentTool tool = createMockTool("get_threat_intel", "威胁情报查询", "ai:agent:tool:threat-intel");
        toolRegistry.registerTool(tool);

        AgentTool result = toolRegistry.getTool("get_threat_intel");
        assertNotNull(result);
        assertEquals("get_threat_intel", result.getName());
    }

    /**
     * 用例 4: getTool 不存在的工具应返回 null
     */
    @Test
    @DisplayName("getTool_NotFound - 不存在的工具应返回 null")
    void testGetTool_NotFound() {
        assertNull(toolRegistry.getTool("nonexistent_tool"));
    }

    /**
     * 用例 5: checkPermission 无需权限的工具应允许调用
     */
    @Test
    @DisplayName("checkPermission_NoPermissionRequired - 无需权限应允许")
    void testCheckPermission_NoPermissionRequired() {
        AgentTool tool = createMockTool("free_tool", "免费工具", "");
        toolRegistry.registerTool(tool);

        assertTrue(toolRegistry.checkPermission("free_tool", Collections.emptySet()));
        assertTrue(toolRegistry.checkPermission("free_tool", null));
    }

    /**
     * 用例 6: checkPermission 需权限且用户有权限应允许
     */
    @Test
    @DisplayName("checkPermission_HasPermission - 有权限应允许")
    void testCheckPermission_HasPermission() {
        AgentTool tool = createMockTool("search_files", "文件检索", "ai:agent:tool:search");
        toolRegistry.registerTool(tool);

        Set<String> userPerms = new HashSet<>(Arrays.asList("ai:agent:tool:search", "ai:agent:tool:ner"));
        assertTrue(toolRegistry.checkPermission("search_files", userPerms));
    }

    /**
     * 用例 7: checkPermission 需权限但用户无权限应拒绝
     */
    @Test
    @DisplayName("checkPermission_NoPermission - 无权限应拒绝")
    void testCheckPermission_NoPermission() {
        AgentTool tool = createMockTool("search_files", "文件检索", "ai:agent:tool:search");
        toolRegistry.registerTool(tool);

        Set<String> userPerms = new HashSet<>(Collections.singletonList("ai:agent:tool:ner"));
        assertFalse(toolRegistry.checkPermission("search_files", userPerms));
    }

    /**
     * 用例 8: checkPermission 通配权限应允许全部工具
     */
    @Test
    @DisplayName("checkPermission_Wildcard - 通配权限应允许")
    void testCheckPermission_Wildcard() {
        AgentTool tool = createMockTool("search_files", "文件检索", "ai:agent:tool:search");
        toolRegistry.registerTool(tool);

        assertTrue(toolRegistry.checkPermission("search_files", Set.of("*")));
    }

    /**
     * 用例 9: executeTool 不存在的工具应返回错误提示
     */
    @Test
    @DisplayName("executeTool_NotFound - 不存在的工具应返回错误")
    void testExecuteTool_NotFound() {
        String result = toolRegistry.executeTool("nonexistent", Collections.emptyMap(), Set.of("*"));
        assertTrue(result.contains("不存在"));
    }

    /**
     * 用例 10: executeTool 权限不足应返回错误提示
     */
    @Test
    @DisplayName("executeTool_NoPermission - 权限不足应返回错误")
    void testExecuteTool_NoPermission() {
        AgentTool tool = createMockTool("search_files", "文件检索", "ai:agent:tool:search");
        toolRegistry.registerTool(tool);

        String result = toolRegistry.executeTool("search_files", Map.of("query", "test"),
                Collections.emptySet());
        assertTrue(result.contains("权限不足"));
    }

    /**
     * 用例 11: executeTool 正常执行应返回工具结果
     */
    @Test
    @DisplayName("executeTool_Success - 应返回工具执行结果")
    void testExecuteTool_Success() {
        AgentTool tool = createMockTool("search_files", "文件检索", "ai:agent:tool:search");
        when(tool.execute(anyMap())).thenReturn("检索结果: 文件A, 文件B");
        toolRegistry.registerTool(tool);

        String result = toolRegistry.executeTool("search_files", Map.of("query", "test"),
                Set.of("ai:agent:tool:search"));
        assertEquals("检索结果: 文件A, 文件B", result);
    }

    /**
     * 用例 12: unregisterTool 应注销工具
     */
    @Test
    @DisplayName("unregisterTool_Success - 应注销工具")
    void testUnregisterTool_Success() {
        AgentTool tool = createMockTool("search_files", "文件检索", "ai:agent:tool:search");
        toolRegistry.registerTool(tool);
        assertEquals(1, toolRegistry.listTools().size());

        toolRegistry.unregisterTool("search_files");
        assertEquals(0, toolRegistry.listTools().size());
        assertNull(toolRegistry.getTool("search_files"));
    }

    /**
     * 创建 Mock 工具
     *
     * @param name           工具名称
     * @param description    工具描述
     * @param permission     所需权限
     * @return Mock 工具实例
     */
    private AgentTool createMockTool(String name, String description, String permission) {
        AgentTool tool = org.mockito.Mockito.mock(AgentTool.class);
        when(tool.getName()).thenReturn(name);
        when(tool.getDescription()).thenReturn(description);
        when(tool.getParametersSchema()).thenReturn("{}");
        when(tool.getRequiredPermission()).thenReturn(permission);
        when(tool.execute(anyMap())).thenReturn("ok");
        return tool;
    }
}
