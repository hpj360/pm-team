package com.redteam.ai.agent;

import com.redteam.ai.agent.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 工具注册中心
 *
 * <p>统一管理 Agent 可调用的工具，支持动态注册、查询与权限校验。
 * 启动时自动注册 6 个内置工具（标注 @Component 的 AgentTool 实现类）。</p>
 *
 * @author 红方团队
 */
@Component
@Slf4j
public class ToolRegistry {

    /**
     * 工具表（name -> tool），使用 LinkedHashMap 保持注册顺序
     */
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /**
     * 工具所需权限表（name -> permission）
     */
    private final Map<String, String> permissions = new LinkedHashMap<>();

    /**
     * 注入所有 AgentTool 实现（Spring 自动收集）
     *
     * @param registeredTools 已注册的工具列表
     */
    @Autowired
    public ToolRegistry(List<AgentTool> registeredTools) {
        if (registeredTools != null) {
            for (AgentTool tool : registeredTools) {
                registerTool(tool);
            }
            log.info("ToolRegistry 初始化完成，已注册 {} 个工具: {}",
                    tools.size(), tools.keySet());
        }
    }

    /**
     * 动态注册工具
     *
     * @param tool 工具实例
     */
    public void registerTool(AgentTool tool) {
        if (tool == null || tool.getName() == null || tool.getName().isBlank()) {
            log.warn("工具注册失败：工具或名称为空");
            return;
        }
        tools.put(tool.getName(), tool);
        permissions.put(tool.getName(), tool.getRequiredPermission());
        log.info("注册工具: {} ({})", tool.getName(), tool.getDescription());
    }

    /**
     * 注销工具
     *
     * @param name 工具名称
     */
    public void unregisterTool(String name) {
        tools.remove(name);
        permissions.remove(name);
        log.info("注销工具: {}", name);
    }

    /**
     * 按名称获取工具
     *
     * @param name 工具名称
     * @return 工具实例，不存在返回 null
     */
    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 列出全部已注册工具
     *
     * @return 工具列表
     */
    public List<AgentTool> listTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 列出全部工具名称
     *
     * @return 工具名称列表
     */
    public Set<String> listToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /**
     * 校验用户是否拥有调用指定工具的权限
     *
     * @param toolName   工具名称
     * @param userPerms  用户拥有的权限集合
     * @return true 表示有权限（或工具无需权限），false 表示无权限
     */
    public boolean checkPermission(String toolName, Set<String> userPerms) {
        String required = permissions.get(toolName);
        // 无需权限
        if (required == null || required.isBlank()) {
            return true;
        }
        if (userPerms == null || userPerms.isEmpty()) {
            return false;
        }
        return userPerms.contains(required) || userPerms.contains("*");
    }

    /**
     * 执行工具（带权限校验）
     *
     * @param toolName  工具名称
     * @param params    工具参数
     * @param userPerms 用户权限集合
     * @return 执行结果字符串；权限不足或工具不存在时返回提示信息
     */
    public String executeTool(String toolName, Map<String, Object> params, Set<String> userPerms) {
        AgentTool tool = getTool(toolName);
        if (tool == null) {
            return "错误：工具 " + toolName + " 不存在";
        }
        if (!checkPermission(toolName, userPerms)) {
            log.warn("权限不足，拒绝调用工具 {}, required={}, userPerms={}", toolName,
                    permissions.get(toolName), userPerms);
            return "错误：权限不足，无法调用工具 " + toolName;
        }
        try {
            return tool.execute(params);
        } catch (Exception e) {
            log.error("工具执行异常, tool={}: {}", toolName, e.getMessage(), e);
            return "工具 " + toolName + " 执行异常: " + e.getMessage();
        }
    }
}
