package com.redteam.parse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * YARA 规则 DTO
 *
 * <p>用于创建/更新 YARA 规则的请求体。</p>
 *
 * @author 红方团队
 */
@Data
public class YaraRuleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128")
    private String ruleName;

    /**
     * 规则内容（YARA 规则源码）
     */
    @NotBlank(message = "规则内容不能为空")
    private String ruleContent;

    /**
     * 规则描述
     */
    @Size(max = 512, message = "描述长度不能超过 512")
    private String description;

    /**
     * 严重级别：INFO/LOW/MEDIUM/HIGH/CRITICAL
     */
    private String severity;

    /**
     * 规则类别：MALWARE/EXPLOIT/LEAK/CREDENTIAL/OTHER
     */
    private String category;

    /**
     * 是否启用
     */
    private Boolean enabled;
}
