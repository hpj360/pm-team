package com.redteam.parse.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * YARA 匹配结果 VO
 *
 * <p>封装单条 YARA 规则对文件的扫描结果，供解析结果与扫描接口返回。</p>
 *
 * @author 红方团队
 */
@Data
public class YaraMatchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 是否匹配
     */
    private Boolean matched;

    /**
     * 匹配字符串列表（YARA 命中的 $a / $b 等）
     */
    private List<String> matchedStrings;

    /**
     * 严重级别：INFO/LOW/MEDIUM/HIGH/CRITICAL
     */
    private String severity;

    /**
     * 规则类别：MALWARE/EXPLOIT/LEAK/CREDENTIAL/OTHER
     */
    private String category;
}
