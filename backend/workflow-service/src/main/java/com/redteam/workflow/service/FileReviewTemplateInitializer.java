package com.redteam.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.entity.WorkflowDefinitionEntity;
import com.redteam.common.mapper.WorkflowDefinitionMapper;
import com.redteam.workflow.dto.WorkflowEdgeDTO;
import com.redteam.workflow.dto.WorkflowNodeDTO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 文件评审工作流模板初始化器
 *
 * <p>服务启动时通过 {@code @PostConstruct} 检查数据库是否已有 FILE_REVIEW 类型的工作流定义，
 * 若无则创建默认模板：START → APPROVAL1 → APPROVAL2 → END（两个评审节点均为 SEQUENTIAL 模式）。</p>
 *
 * <p>默认审批人 ID 为占位值（1001/1002），生产环境可通过更新工作流定义替换为实际评审人。</p>
 *
 * @author 红方团队
 */
@Component
@Slf4j
public class FileReviewTemplateInitializer {

    /** 文件评审业务类型 */
    private static final String BUSINESS_TYPE = "FILE_REVIEW";

    /** 默认模板名称 */
    private static final String DEFAULT_TEMPLATE_NAME = "文件评审工作流";

    /** 默认模板描述 */
    private static final String DEFAULT_TEMPLATE_DESC = "默认文件评审流程：提交→评审人1→评审人2→归档";

    /** 节点类型常量 */
    private static final String NODE_TYPE_START = "START";
    private static final String NODE_TYPE_APPROVAL = "APPROVAL";
    private static final String NODE_TYPE_END = "END";

    /** 审批模式常量 */
    private static final String MODE_SEQUENTIAL = "SEQUENTIAL";

    /** 默认评审人1 ID */
    private static final Long DEFAULT_REVIEWER_1 = 1001L;

    /** 默认评审人2 ID */
    private static final Long DEFAULT_REVIEWER_2 = 1002L;

    @Autowired
    private WorkflowDefinitionMapper definitionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 初始化文件评审工作流模板
     *
     * <p>检查 FILE_REVIEW 类型工作流定义是否已存在，不存在则插入默认模板。
     * 初始化失败仅记录日志，不阻断服务启动。</p>
     */
    @PostConstruct
    public void initTemplate() {
        try {
            List<WorkflowDefinitionEntity> existing = definitionMapper.selectByBusinessType(BUSINESS_TYPE);
            if (existing != null && !existing.isEmpty()) {
                log.info("文件评审工作流模板已存在，跳过初始化: count={}", existing.size());
                return;
            }

            WorkflowDefinitionEntity template = buildDefaultTemplate();
            definitionMapper.insert(template);
            log.info("文件评审工作流模板初始化完成: id={}, name={}", template.getId(), template.getName());
        } catch (Exception e) {
            // 初始化失败不应阻断服务启动
            log.error("文件评审工作流模板初始化失败", e);
        }
    }

    /**
     * 构造默认文件评审工作流模板
     *
     * <p>节点结构：</p>
     * <ul>
     *   <li>node-start: START 发起人</li>
     *   <li>node-approval-1: APPROVAL 评审人1（SEQUENTIAL）</li>
     *   <li>node-approval-2: APPROVAL 评审人2（SEQUENTIAL）</li>
     *   <li>node-end: END 归档</li>
     * </ul>
     * <p>边：start → approval-1 → approval-2 → end</p>
     *
     * @return 工作流定义实体
     */
    private WorkflowDefinitionEntity buildDefaultTemplate() {
        // 节点1: START（发起人）
        WorkflowNodeDTO start = new WorkflowNodeDTO();
        start.setNodeId("node-start");
        start.setNodeName("发起人");
        start.setNodeType(NODE_TYPE_START);

        // 节点2: APPROVAL（评审人1，SEQUENTIAL）
        WorkflowNodeDTO approval1 = new WorkflowNodeDTO();
        approval1.setNodeId("node-approval-1");
        approval1.setNodeName("评审人1");
        approval1.setNodeType(NODE_TYPE_APPROVAL);
        approval1.setApprovalMode(MODE_SEQUENTIAL);
        approval1.setApproverIds(Collections.singletonList(DEFAULT_REVIEWER_1));

        // 节点3: APPROVAL（评审人2，SEQUENTIAL）
        WorkflowNodeDTO approval2 = new WorkflowNodeDTO();
        approval2.setNodeId("node-approval-2");
        approval2.setNodeName("评审人2");
        approval2.setNodeType(NODE_TYPE_APPROVAL);
        approval2.setApprovalMode(MODE_SEQUENTIAL);
        approval2.setApproverIds(Collections.singletonList(DEFAULT_REVIEWER_2));

        // 节点4: END（归档）
        WorkflowNodeDTO end = new WorkflowNodeDTO();
        end.setNodeId("node-end");
        end.setNodeName("归档");
        end.setNodeType(NODE_TYPE_END);

        List<WorkflowNodeDTO> nodes = Arrays.asList(start, approval1, approval2, end);

        // 边: START → APPROVAL1 → APPROVAL2 → END
        WorkflowEdgeDTO e1 = new WorkflowEdgeDTO();
        e1.setSourceNodeId("node-start");
        e1.setTargetNodeId("node-approval-1");

        WorkflowEdgeDTO e2 = new WorkflowEdgeDTO();
        e2.setSourceNodeId("node-approval-1");
        e2.setTargetNodeId("node-approval-2");

        WorkflowEdgeDTO e3 = new WorkflowEdgeDTO();
        e3.setSourceNodeId("node-approval-2");
        e3.setTargetNodeId("node-end");

        List<WorkflowEdgeDTO> edges = Arrays.asList(e1, e2, e3);

        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setName(DEFAULT_TEMPLATE_NAME);
        entity.setDescription(DEFAULT_TEMPLATE_DESC);
        entity.setBusinessType(BUSINESS_TYPE);
        entity.setEnabled(1);
        entity.setVersion(1);
        try {
            entity.setNodesJson(objectMapper.writeValueAsString(nodes));
            entity.setEdgesJson(objectMapper.writeValueAsString(edges));
        } catch (Exception e) {
            throw new RuntimeException("序列化节点/边 JSON 失败", e);
        }
        return entity;
    }
}
