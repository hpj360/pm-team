package com.redteam.parse.service;

import cn.hutool.core.util.StrUtil;
import com.redteam.common.entity.FileTagEntity;
import com.redteam.common.entity.TagDictEntity;
import com.redteam.common.mapper.FileTagMapper;
import com.redteam.common.mapper.TagDictMapper;
import com.redteam.parse.dto.NerEntityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动标签识别引擎
 *
 * <p>支持四类识别规则：</p>
 * <ul>
 *   <li><b>REGEX</b>：基于正则表达式识别 CVE/IP/域名/邮箱/文件类型</li>
 *   <li><b>DICT</b>：基于硬编码字典识别 APT 组织</li>
 *   <li><b>ML</b>：基于 NER 模型识别结果映射实体标签</li>
 *   <li><b>ASSOC</b>：基于关联场景关键词识别业务场景标签</li>
 * </ul>
 *
 * <p>识别规则硬编码，不依赖 tag_dict_v2.identify_rule 字段；
 * 产出的 tagCode 与种子数据保持一致，字典中不存在的 tagCode 在持久化时自动跳过。</p>
 *
 * @author 红方团队
 */
@Service
@Slf4j
public class TagRecognitionEngine {

    @Autowired
    private TagDictMapper tagDictMapper;

    @Autowired
    private FileTagMapper fileTagMapper;

    // ==================== 正则规则（硬编码） ====================

    /** CVE 编号正则 */
    private static final Pattern CVE_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,7}");

    /** IPv4 地址正则 */
    private static final Pattern IP_PATTERN =
            Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");

    /** 域名正则（常见顶级域） */
    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("[a-zA-Z0-9.-]+\\.(com|net|org|io|cn|ru|tk)");

    /** 邮箱正则 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** 已知文件扩展名集合 */
    private static final Set<String> KNOWN_EXTENSIONS =
            Set.of("pdf", "docx", "exe", "pcap", "zip", "png", "log", "py");

    /** APT 组织字典（硬编码） */
    private static final List<String> APT_ORGS = List.of(
            "APT28", "APT29", "APT32", "APT41", "Lazarus",
            "FancyBear", "CozyBear", "EquationGroup", "Turla", "Sandworm");

    /** 标签来源：自动 */
    private static final String SOURCE_AUTO = "AUTO";

    /**
     * 对文件执行自动标签识别
     *
     * @param fileId      文件ID
     * @param textContent 文件文本内容
     * @param fileName    文件名
     * @param fileType    文件类型
     * @param nerEntities NER 识别实体列表（可能为空）
     * @return 识别到的标签列表
     */
    public List<FileTagEntity> recognizeTags(Long fileId, String textContent,
                                              String fileName, String fileType,
                                              List<NerEntityVO> nerEntities) {
        // 1. 调用四类规则引擎，收集所有 tagCode（LinkedHashSet 保序去重）
        Set<String> codeSet = new LinkedHashSet<>();
        codeSet.addAll(recognizeByRegex(textContent, fileName, fileType));
        codeSet.addAll(recognizeByDict(textContent));
        codeSet.addAll(recognizeByML(nerEntities));
        codeSet.addAll(recognizeByAssoc(fileId, textContent, collectEntityIPs(nerEntities)));

        if (codeSet.isEmpty()) {
            log.info("自动标签识别无结果: fileId={}", fileId);
            return new ArrayList<>();
        }

        // 2. 查询 tag_dict_v2 获取 tagId（字典中不存在的 tagCode 跳过）
        List<FileTagEntity> recognized = new ArrayList<>();
        for (String tagCode : codeSet) {
            TagDictEntity dict = tagDictMapper.selectByCode(tagCode);
            if (dict == null) {
                log.debug("标签编码不在字典中，跳过持久化: tagCode={}", tagCode);
                continue;
            }
            FileTagEntity fileTag = new FileTagEntity();
            fileTag.setFileId(fileId);
            fileTag.setTagId(dict.getId());
            fileTag.setTagCode(tagCode);
            fileTag.setSource(SOURCE_AUTO);
            recognized.add(fileTag);
        }

        // 3. 持久化（去重：跳过文件已有的标签，避免唯一键冲突）
        int inserted = 0;
        if (fileId != null && !recognized.isEmpty()) {
            List<FileTagEntity> existing = fileTagMapper.selectByFileId(fileId);
            Set<String> existingCodes = new HashSet<>();
            if (existing != null) {
                for (FileTagEntity ft : existing) {
                    if (ft.getTagCode() != null) {
                        existingCodes.add(ft.getTagCode());
                    }
                }
            }
            List<FileTagEntity> toInsert = new ArrayList<>();
            for (FileTagEntity ft : recognized) {
                if (!existingCodes.contains(ft.getTagCode())) {
                    toInsert.add(ft);
                }
            }
            if (!toInsert.isEmpty()) {
                fileTagMapper.batchInsert(toInsert);
                inserted = toInsert.size();
            }
        }
        log.info("自动标签识别完成: fileId={}, 识别标签数={}, 新增={}",
                fileId, recognized.size(), inserted);
        return recognized;
    }

    // ==================== REGEX 规则引擎 ====================

    /**
     * 基于正则表达式识别标签
     *
     * @param textContent 文本内容
     * @param fileName    文件名
     * @param fileType    文件类型
     * @return 匹配到的 tagCode 列表
     */
    public List<String> recognizeByRegex(String textContent, String fileName, String fileType) {
        List<String> codes = new ArrayList<>();
        String text = textContent == null ? "" : textContent;

        // CVE 漏洞编号
        if (CVE_PATTERN.matcher(text).find()) {
            codes.add("L3.ENTITY.VULN.CVE");
        }

        // IP 地址（区分公网/私网）
        Matcher ipMatcher = IP_PATTERN.matcher(text);
        boolean hasPublic = false;
        boolean hasPrivate = false;
        while (ipMatcher.find()) {
            if (isPrivateIp(ipMatcher.group())) {
                hasPrivate = true;
            } else {
                hasPublic = true;
            }
        }
        if (hasPublic) {
            codes.add("L3.ENTITY.IP.PUBLIC");
        }
        if (hasPrivate) {
            codes.add("L3.ENTITY.IP.PRIVATE");
        }

        // 域名
        if (DOMAIN_PATTERN.matcher(text).find()) {
            codes.add("L3.ENTITY.DOMAIN");
        }

        // 邮箱
        if (EMAIL_PATTERN.matcher(text).find()) {
            codes.add("L3.ENTITY.USER.EMAIL");
        }

        // 文件扩展名匹配
        String ext = extractFileExtension(fileName, fileType);
        if (ext != null) {
            codes.add("L1.FILE.TYPE." + ext);
        }

        return codes;
    }

    // ==================== DICT 规则引擎 ====================

    /**
     * 基于字典识别 APT 组织标签
     *
     * @param textContent 文本内容
     * @return 匹配到的 tagCode 列表
     */
    public List<String> recognizeByDict(String textContent) {
        List<String> codes = new ArrayList<>();
        if (StrUtil.isBlank(textContent)) {
            return codes;
        }
        String lower = textContent.toLowerCase();
        for (String apt : APT_ORGS) {
            if (lower.contains(apt.toLowerCase())) {
                codes.add("L5.INTEL.APT." + apt);
            }
        }
        return codes;
    }

    // ==================== ML 规则引擎 ====================

    /**
     * 基于 NER 实体识别结果映射标签
     *
     * @param nerEntities NER 实体列表
     * @return 映射后的 tagCode 列表
     */
    public List<String> recognizeByML(List<NerEntityVO> nerEntities) {
        List<String> codes = new ArrayList<>();
        if (nerEntities == null || nerEntities.isEmpty()) {
            return codes;
        }
        for (NerEntityVO entity : nerEntities) {
            if (entity == null || StrUtil.isBlank(entity.getEntityType())) {
                continue;
            }
            String type = entity.getEntityType().toUpperCase();
            String value = entity.getEntityText();
            switch (type) {
                case "IP":
                    if (isPrivateIp(value)) {
                        codes.add("L3.ENTITY.IP.PRIVATE");
                    } else {
                        codes.add("L3.ENTITY.IP.PUBLIC");
                    }
                    break;
                case "DOMAIN":
                    codes.add("L3.ENTITY.DOMAIN");
                    break;
                case "HOSTNAME":
                    codes.add("L3.ENTITY.HOST");
                    break;
                case "USERNAME":
                case "EMAIL":
                    codes.add("L3.ENTITY.USER");
                    break;
                case "CREDENTIAL":
                    codes.add("L3.ENTITY.CRED");
                    break;
                case "VULNERABILITY":
                    codes.add("L3.ENTITY.VULN");
                    break;
                case "CVE":
                    codes.add("L3.ENTITY.VULN.CVE");
                    break;
                case "IOC":
                    codes.add("L3.ENTITY.IOC");
                    break;
                case "HASH_MD5":
                case "HASH_SHA256":
                case "HASH":
                    codes.add("L3.ENTITY.IOC.FILE_HASH");
                    break;
                default:
                    break;
            }
        }
        return codes;
    }

    // ==================== ASSOC 规则引擎 ====================

    /**
     * 基于关联场景关键词识别业务场景标签
     *
     * <p>简化实现：基于关键词匹配，不实际查询 profile-service（避免循环依赖）。</p>
     *
     * @param fileId      文件ID
     * @param textContent 文本内容
     * @param entityIPs   NER 识别的 IP 实体列表
     * @return 匹配到的 tagCode 列表
     */
    public List<String> recognizeByAssoc(Long fileId, String textContent, List<String> entityIPs) {
        List<String> codes = new ArrayList<>();
        String text = textContent == null ? "" : textContent;
        String lower = text.toLowerCase();

        // 1. 包含 IP → 目标画像场景
        boolean hasIP = (entityIPs != null && !entityIPs.isEmpty()) || IP_PATTERN.matcher(text).find();
        if (hasIP) {
            codes.add("L4.SCENE.TARGET_PROFILE");
        }

        // 2. pcap 抓包文件 → 网络地形场景
        if (lower.contains("pcap") || lower.contains("pcapng") || lower.contains("抓包")) {
            codes.add("L4.SCENE.NETWORK_TOPOLOGY");
        }

        // 3. 凭证关键词 → 凭证获取场景
        if (lower.contains("credential") || lower.contains("password")
                || lower.contains("passwd") || lower.contains("hash")) {
            codes.add("L4.SCENE.CREDENTIAL_ACCESS");
        }

        // 4. 漏洞关键词 → 漏洞侦察场景
        if (lower.contains("cve") || lower.contains("漏洞")
                || lower.contains("vulnerability") || lower.contains("exploit")) {
            codes.add("L4.SCENE.VULNERABILITY_RECON");
        }

        return codes;
    }

    // ==================== 工具方法 ====================

    /**
     * 判断是否为私网 IP
     *
     * <p>私网段：10.0.0.0/8、172.16.0.0/12（172.16-31）、192.168.0.0/16</p>
     *
     * @param ip IP 地址
     * @return true 为私网
     */
    private boolean isPrivateIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int a;
        int b;
        try {
            a = Integer.parseInt(parts[0]);
            b = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (a == 10) {
            return true;
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return true;
        }
        return a == 192 && b == 168;
    }

    /**
     * 提取文件扩展名（仅返回已知类型，大写形式）
     *
     * @param fileName 文件名
     * @param fileType 文件类型
     * @return 大写扩展名（如 PDF），未知返回 null
     */
    private String extractFileExtension(String fileName, String fileType) {
        String ext = null;
        if (StrUtil.isNotBlank(fileName)) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                ext = fileName.substring(dot + 1).toLowerCase();
            }
        }
        if (ext == null && StrUtil.isNotBlank(fileType)) {
            ext = fileType.toLowerCase().replace(".", "");
        }
        if (ext == null || ext.isEmpty() || !KNOWN_EXTENSIONS.contains(ext)) {
            return null;
        }
        return ext.toUpperCase();
    }

    /**
     * 从 NER 实体中收集 IP 实体值（供 ASSOC 引擎使用）
     *
     * @param nerEntities NER 实体列表
     * @return IP 值列表
     */
    private List<String> collectEntityIPs(List<NerEntityVO> nerEntities) {
        List<String> ips = new ArrayList<>();
        if (nerEntities == null) {
            return ips;
        }
        for (NerEntityVO entity : nerEntities) {
            if (entity != null && "IP".equalsIgnoreCase(entity.getEntityType())
                    && StrUtil.isNotBlank(entity.getEntityText())) {
                ips.add(entity.getEntityText());
            }
        }
        return ips;
    }
}
