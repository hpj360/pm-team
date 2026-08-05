package com.redteam.analyze.controller;

import com.redteam.analyze.service.StixExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 威胁情报导出控制器
 *
 * <p>提供 STIX 2.1 Bundle 导出接口，支持 IOC / APT / TTP 单独或混合导出。</p>
 *
 * <p>当前平台尚无独立的 IOC 数据表，使用内置 Mock 数据替代真实数据源。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/intel/export")
@RequiredArgsConstructor
@Tag(name = "威胁情报导出", description = "STIX 2.1 Bundle 导出接口")
public class StixExportController {

    private final StixExportService stixExportService;

    /**
     * 导出 STIX 2.1 Bundle
     *
     * @param iocIds IOC ID 列表（可选，为空时使用全部 Mock 数据）
     * @param aptIds APT ID 列表（可选）
     * @param ttpIds TTP ID 列表（可选）
     * @param format 导出格式：all / ioc / apt / ttp
     * @return STIX Bundle JSON
     */
    @GetMapping("/stix")
    @Operation(summary = "导出 STIX 2.1 Bundle", description = "根据参数导出 IOC/APT/TTP 的 STIX 2.1 Bundle JSON")
    public ResponseEntity<String> exportStix(
            @Parameter(description = "IOC ID 列表") @RequestParam(required = false) List<Long> iocIds,
            @Parameter(description = "APT ID 列表") @RequestParam(required = false) List<Long> aptIds,
            @Parameter(description = "TTP ID 列表") @RequestParam(required = false) List<Long> ttpIds,
            @Parameter(description = "导出格式：all / ioc / apt / ttp")
            @RequestParam(defaultValue = "all") String format) {

        log.info("导出 STIX Bundle: format={}, iocIds={}, aptIds={}, ttpIds={}",
                format, iocIds, aptIds, ttpIds);

        String formatLower = format == null ? "all" : format.toLowerCase();
        String json;
        switch (formatLower) {
            case "ioc":
                json = stixExportService.exportIocsToStix(filterIocs(iocIds));
                break;
            case "apt":
                json = stixExportService.exportAptsToStix(filterApts(aptIds));
                break;
            case "ttp":
                json = stixExportService.exportTtpsToStix(filterTtps(ttpIds));
                break;
            case "all":
            default:
                json = stixExportService.exportAllToStix(
                        filterIocs(iocIds), filterApts(aptIds), filterTtps(ttpIds));
                break;
        }
        return wrapAttachment(json, "stix-bundle.json");
    }

    /**
     * 仅导出 IOC
     *
     * @param iocIds IOC ID 列表（可选，为空时使用全部 Mock 数据）
     * @return STIX Bundle JSON
     */
    @GetMapping("/stix/iocs")
    @Operation(summary = "仅导出 IOC", description = "导出 IOC 为 STIX 2.1 Indicator Bundle")
    public ResponseEntity<String> exportIocs(
            @Parameter(description = "IOC ID 列表") @RequestParam(required = false) List<Long> iocIds) {

        log.info("仅导出 IOC: iocIds={}", iocIds);
        String json = stixExportService.exportIocsToStix(filterIocs(iocIds));
        return wrapAttachment(json, "stix-iocs.json");
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 包装响应为附件下载（Content-Type: application/json + Content-Disposition）
     *
     * @param json     JSON 字符串
     * @param filename 下载文件名
     * @return ResponseEntity
     */
    private ResponseEntity<String> wrapAttachment(String json, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return new ResponseEntity<>(json, headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * 按 IOC ID 过滤 Mock IOC 数据（ID 为空时返回全部）
     *
     * @param iocIds IOC ID 列表
     * @return 过滤后的 IOC 列表
     */
    private List<Map<String, Object>> filterIocs(List<Long> iocIds) {
        List<Map<String, Object>> all = mockIocs();
        if (iocIds == null || iocIds.isEmpty()) {
            return all;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> ioc : all) {
            Object idObj = ioc.get("id");
            if (idObj instanceof Number) {
                Long id = ((Number) idObj).longValue();
                if (iocIds.contains(id)) {
                    filtered.add(ioc);
                }
            }
        }
        return filtered;
    }

    /**
     * 按 APT ID 过滤 Mock APT 数据
     *
     * @param aptIds APT ID 列表
     * @return 过滤后的 APT 列表
     */
    private List<Map<String, Object>> filterApts(List<Long> aptIds) {
        List<Map<String, Object>> all = mockApts();
        if (aptIds == null || aptIds.isEmpty()) {
            return all;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> apt : all) {
            Object idObj = apt.get("id");
            if (idObj instanceof Number) {
                Long id = ((Number) idObj).longValue();
                if (aptIds.contains(id)) {
                    filtered.add(apt);
                }
            }
        }
        return filtered;
    }

    /**
     * 按 TTP ID 过滤 Mock TTP 数据
     *
     * @param ttpIds TTP ID 列表
     * @return 过滤后的 TTP 列表
     */
    private List<Map<String, Object>> filterTtps(List<Long> ttpIds) {
        List<Map<String, Object>> all = mockTtps();
        if (ttpIds == null || ttpIds.isEmpty()) {
            return all;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> ttp : all) {
            Object idObj = ttp.get("id");
            if (idObj instanceof Number) {
                Long id = ((Number) idObj).longValue();
                if (ttpIds.contains(id)) {
                    filtered.add(ttp);
                }
            }
        }
        return filtered;
    }

    /**
     * Mock IOC 数据（替代 DB 数据源）
     *
     * @return IOC 列表
     */
    private List<Map<String, Object>> mockIocs() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildIoc(1L, "IP", "1.2.3.4", "恶意 C2 服务器 IP", "C2-IP-1.2.3.4"));
        list.add(buildIoc(2L, "Domain", "evil.com", "恶意域名", "C2-Domain-evil"));
        list.add(buildIoc(3L, "URL", "http://evil.com/payload", "恶意载荷下载地址", "Payload-URL"));
        list.add(buildIoc(4L, "MD5", "d41d8cd98f00b204e9800998ecf8427e", "恶意样本 MD5", "Sample-MD5"));
        list.add(buildIoc(5L, "SHA256",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "恶意样本 SHA256", "Sample-SHA256"));
        list.add(buildIoc(6L, "Email", "attacker@evil.com", "攻击者邮箱", "Attacker-Email"));
        return list;
    }

    /**
     * Mock APT 组织数据
     *
     * @return APT 列表
     */
    private List<Map<String, Object>> mockApts() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildApt(1L, "APT28", "国家级 APT 组织，疑似俄罗斯背景",
                Arrays.asList("nation-state"), Arrays.asList("Fancy Bear", "Sofacy")));
        list.add(buildApt(2L, "APT41", "国家级 APT 组织，疑似中国背景",
                Arrays.asList("nation-state", "crime-syndicate"), Arrays.asList("Winnti", "Barium")));
        return list;
    }

    /**
     * Mock TTP 数据
     *
     * @return TTP 列表
     */
    private List<Map<String, Object>> mockTtps() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildTtp(1L, "Spearphishing Attachment",
                "鱼叉式钓鱼附件攻击", Collections.singletonList("MITRE ATT&CK T1566.001")));
        list.add(buildTtp(2L, "PowerShell",
                "利用 PowerShell 执行恶意命令", Collections.singletonList("MITRE ATT&CK T1059.001")));
        return list;
    }

    /**
     * 构建 IOC Map
     */
    private Map<String, Object> buildIoc(Long id, String type, String value, String description, String name) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("type", type);
        map.put("value", value);
        map.put("description", description);
        map.put("name", name);
        return map;
    }

    /**
     * 构建 APT Map
     */
    private Map<String, Object> buildApt(Long id, String name, String description,
                                         List<String> threatActorTypes, List<String> aliases) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("description", description);
        map.put("threatActorTypes", threatActorTypes);
        map.put("aliases", aliases);
        return map;
    }

    /**
     * 构建 TTP Map
     */
    private Map<String, Object> buildTtp(Long id, String name, String description,
                                         List<String> externalReferences) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("description", description);
        map.put("externalReferences", externalReferences);
        return map;
    }
}
