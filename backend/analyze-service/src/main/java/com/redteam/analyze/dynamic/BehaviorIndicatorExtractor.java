package com.redteam.analyze.dynamic;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 行为指标提取器
 *
 * <p>从 Cuckoo 沙箱 JSON 报告中提取四类行为指标，并转换为 STIX 2.1 对象与 ATT&CK 技术映射：</p>
 * <ul>
 *   <li>进程树 → STIX Process 对象 + 进程树层级结构</li>
 *   <li>网络连接 → STIX NetworkTraffic 对象 + IOC 提取（IP/域名/URL）</li>
 *   <li>文件操作 → 文件系统变更清单（创建/修改/删除）</li>
 *   <li>API 调用 / 行为签名 → ATT&CK 技术映射（T1059 命令执行等）</li>
 * </ul>
 *
 * <p>纯逻辑组件，无外部依赖，便于单元测试。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class BehaviorIndicatorExtractor {

    /**
     * ATT&CK 技术映射：行为关键词 → 技术 ID
     */
    private static final Map<String, String> ATTACK_KEYWORD_MAP = new LinkedHashMap<>();

    /**
     * ATT&CK 技术 ID → 元数据（tactic / name）
     */
    private static final Map<String, String[]> ATTACK_TECHNIQUE_META = new LinkedHashMap<>();

    static {
        // 关键词 → 技术 ID
        ATTACK_KEYWORD_MAP.put("cmd.exe", "T1059");
        ATTACK_KEYWORD_MAP.put("powershell.exe", "T1059.001");
        ATTACK_KEYWORD_MAP.put("powershell", "T1059.001");
        ATTACK_KEYWORD_MAP.put("wscript", "T1059.005");
        ATTACK_KEYWORD_MAP.put("cscript", "T1059.005");
        ATTACK_KEYWORD_MAP.put("bash", "T1059.004");
        ATTACK_KEYWORD_MAP.put("sh", "T1059.004");
        ATTACK_KEYWORD_MAP.put("reg add", "T1112");
        ATTACK_KEYWORD_MAP.put("regedit", "T1112");
        ATTACK_KEYWORD_MAP.put("schtasks", "T1053.005");
        ATTACK_KEYWORD_MAP.put("createservice", "T1543.003");
        ATTACK_KEYWORD_MAP.put("rundll32", "T1218.011");
        ATTACK_KEYWORD_MAP.put("regsvr32", "T1218.010");
        ATTACK_KEYWORD_MAP.put("mimikatz", "T1003.001");
        ATTACK_KEYWORD_MAP.put("sekurlsa", "T1003.001");
        ATTACK_KEYWORD_MAP.put("lsadump", "T1003.001");
        ATTACK_KEYWORD_MAP.put("net user", "T1087.001");
        ATTACK_KEYWORD_MAP.put("net view", "T1018");
        ATTACK_KEYWORD_MAP.put("tasklist", "T1057");
        ATTACK_KEYWORD_MAP.put("systeminfo", "T1082");
        ATTACK_KEYWORD_MAP.put("ipconfig", "T1016");
        ATTACK_KEYWORD_MAP.put("whoami", "T1033");
        ATTACK_KEYWORD_MAP.put("download", "T1105");
        ATTACK_KEYWORD_MAP.put("http", "T1071.001");
        ATTACK_KEYWORD_MAP.put("dns", "T1071.004");
        ATTACK_KEYWORD_MAP.put("encryption", "T1486");
        ATTACK_KEYWORD_MAP.put("encrypt", "T1486");
        ATTACK_KEYWORD_MAP.put("delete", "T1485");
        ATTACK_KEYWORD_MAP.put("shadowcopy", "T1490");
        ATTACK_KEYWORD_MAP.put("vssadmin", "T1490");
        ATTACK_KEYWORD_MAP.put("wbadmin", "T1490");
        ATTACK_KEYWORD_MAP.put("bcdedit", "T1490");
        ATTACK_KEYWORD_MAP.put("wmic", "T1047");
        ATTACK_KEYWORD_MAP.put("winrm", "T1021.006");

        // 技术 ID → [tactic, name]
        ATTACK_TECHNIQUE_META.put("T1059", new String[]{"execution", "Command and Scripting Interpreter"});
        ATTACK_TECHNIQUE_META.put("T1059.001", new String[]{"execution", "PowerShell"});
        ATTACK_TECHNIQUE_META.put("T1059.004", new String[]{"execution", "Unix Shell"});
        ATTACK_TECHNIQUE_META.put("T1059.005", new String[]{"execution", "Visual Basic"});
        ATTACK_TECHNIQUE_META.put("T1112", new String[]{"defense-evasion", "Modify Registry"});
        ATTACK_TECHNIQUE_META.put("T1053.005", new String[]{"execution", "Scheduled Task"});
        ATTACK_TECHNIQUE_META.put("T1543.003", new String[]{"persistence", "Create or Modify System Process"});
        ATTACK_TECHNIQUE_META.put("T1218.011", new String[]{"defense-evasion", "Rundll32"});
        ATTACK_TECHNIQUE_META.put("T1218.010", new String[]{"defense-evasion", "Regsvr32"});
        ATTACK_TECHNIQUE_META.put("T1003.001", new String[]{"credential-access", "LSASS Memory"});
        ATTACK_TECHNIQUE_META.put("T1087.001", new String[]{"discovery", "Account Discovery"});
        ATTACK_TECHNIQUE_META.put("T1018", new String[]{"discovery", "Remote System Discovery"});
        ATTACK_TECHNIQUE_META.put("T1057", new String[]{"discovery", "Process Discovery"});
        ATTACK_TECHNIQUE_META.put("T1082", new String[]{"discovery", "System Information Discovery"});
        ATTACK_TECHNIQUE_META.put("T1016", new String[]{"discovery", "System Network Configuration Discovery"});
        ATTACK_TECHNIQUE_META.put("T1033", new String[]{"discovery", "System Owner/User Discovery"});
        ATTACK_TECHNIQUE_META.put("T1105", new String[]{"command-and-control", "Ingress Tool Transfer"});
        ATTACK_TECHNIQUE_META.put("T1071.001", new String[]{"command-and-control", "Web Protocols"});
        ATTACK_TECHNIQUE_META.put("T1071.004", new String[]{"command-and-control", "DNS"});
        ATTACK_TECHNIQUE_META.put("T1486", new String[]{"impact", "Data Encrypted for Impact"});
        ATTACK_TECHNIQUE_META.put("T1485", new String[]{"impact", "Data Destruction"});
        ATTACK_TECHNIQUE_META.put("T1490", new String[]{"impact", "Inhibit System Recovery"});
        ATTACK_TECHNIQUE_META.put("T1047", new String[]{"execution", "Windows Management Instrumentation"});
        ATTACK_TECHNIQUE_META.put("T1021.006", new String[]{"lateral-movement", "Windows Remote Management"});
    }

    /**
     * IPv4 正则
     */
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("\\b((?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b");

    /**
     * 域名正则
     */
    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("\\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}\\b");

    /**
     * 提取全部行为指标，填充到 {@link DynamicAnalysisTask}
     *
     * @param task 动态分析任务（含 rawReport）
     */
    public void extract(DynamicAnalysisTask task) {
        if (task == null) {
            return;
        }
        String raw = task.getRawReport();
        if (StrUtil.isBlank(raw)) {
            return;
        }
        JSONObject report;
        try {
            report = JSONUtil.parseObj(raw);
        } catch (Exception e) {
            log.warn("解析 Cuckoo 报告 JSON 失败: taskId={}", task.getTaskId(), e);
            return;
        }
        task.setProcessTree(extractProcessTree(report));
        task.setNetworkConnections(extractNetworkConnections(report));
        task.setFileOperations(extractFileOperations(report));
        task.setAttackTechniques(extractAttackTechniques(report, task.getProcessTree()));
        task.setIocs(extractIocs(task.getNetworkConnections(), task.getProcessTree()));
        task.setIndicators(buildIndicatorSummary(task));
    }

    /**
     * 提取进程树 → STIX Process 对象
     *
     * @param report Cuckoo 报告
     * @return 进程节点列表（含 stixId / pid / name / ppid / commandLine）
     */
    public List<Map<String, Object>> extractProcessTree(JSONObject report) {
        List<Map<String, Object>> result = new ArrayList<>();
        JSONArray processes = report.getByPath("behavior.processes", JSONArray.class);
        if (processes == null) {
            processes = report.getByPath("processes", JSONArray.class);
        }
        if (processes == null) {
            return result;
        }
        for (int i = 0; i < processes.size(); i++) {
            JSONObject proc = processes.getJSONObject(i);
            if (proc == null) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("stixId", "process--" + UUID.randomUUID());
            node.put("type", "process");
            node.put("pid", proc.getInt("pid"));
            node.put("ppid", proc.getInt("parent_id") != null ? proc.getInt("parent_id") : proc.getInt("ppid"));
            node.put("name", proc.getStr("process_name", proc.getStr("name")));
            node.put("commandLine", proc.getStr("command_line", proc.getStr("command")));
            // 进程行为摘要（calls 数量）
            JSONArray calls = proc.getJSONArray("calls");
            node.put("callCount", calls != null ? calls.size() : 0);
            result.add(node);
        }
        return result;
    }

    /**
     * 提取网络连接 → STIX NetworkTraffic + IOC 提取
     *
     * @param report Cuckoo 报告
     * @return 网络连接列表（含 stixId / srcIp / dstIp / dstPort / protocol / bytes）
     */
    public List<Map<String, Object>> extractNetworkConnections(JSONObject report) {
        List<Map<String, Object>> result = new ArrayList<>();
        // Cuckoo network 结构: { domain: [...], hosts: [...], http: [...], tcp: {...} }
        JSONObject network = report.getJSONObject("network");
        if (network == null) {
            return result;
        }
        // HTTP 请求
        JSONArray http = network.getJSONArray("http");
        if (http != null) {
            for (int i = 0; i < http.size(); i++) {
                JSONObject h = http.getJSONObject(i);
                if (h == null) {
                    continue;
                }
                Map<String, Object> conn = new LinkedHashMap<>();
                conn.put("stixId", "network-traffic--" + UUID.randomUUID());
                conn.put("type", "network-traffic");
                conn.put("protocol", "HTTP");
                conn.put("method", h.getStr("method"));
                conn.put("dstIp", h.getStr("ip"));
                conn.put("dstPort", h.getInt("port"));
                conn.put("uri", h.getStr("uri"));
                conn.put("host", h.getStr("host"));
                conn.put("userAgent", h.getStr("user-agent"));
                result.add(conn);
            }
        }
        // TCP/UDP 主机
        JSONArray hosts = network.getJSONArray("hosts");
        if (hosts != null) {
            for (int i = 0; i < hosts.size(); i++) {
                Object item = hosts.get(i);
                String hostStr = item == null ? null : item.toString();
                if (StrUtil.isBlank(hostStr)) {
                    continue;
                }
                String[] parts = hostStr.split(":");
                Map<String, Object> conn = new LinkedHashMap<>();
                conn.put("stixId", "network-traffic--" + UUID.randomUUID());
                conn.put("type", "network-traffic");
                conn.put("protocol", "TCP");
                conn.put("dstIp", parts.length > 0 ? parts[0] : hostStr);
                conn.put("dstPort", parts.length > 1 ? parsePort(parts[1]) : null);
                result.add(conn);
            }
        }
        // DNS 请求
        JSONArray dns = network.getJSONArray("dns");
        if (dns != null) {
            for (int i = 0; i < dns.size(); i++) {
                JSONObject d = dns.getJSONObject(i);
                if (d == null) {
                    continue;
                }
                Map<String, Object> conn = new LinkedHashMap<>();
                conn.put("stixId", "network-traffic--" + UUID.randomUUID());
                conn.put("type", "network-traffic");
                conn.put("protocol", "DNS");
                conn.put("request", d.getStr("request"));
                conn.put("type_", d.getStr("type"));
                result.add(conn);
            }
        }
        return result;
    }

    /**
     * 提取文件操作 → 文件系统变更清单
     *
     * @param report Cuckoo 报告
     * @return 文件操作列表（含 operation / path / category）
     */
    public List<Map<String, Object>> extractFileOperations(JSONObject report) {
        List<Map<String, Object>> result = new ArrayList<>();
        JSONArray behavior = report.getByPath("behavior.summary.files", JSONArray.class);
        if (behavior == null) {
            behavior = report.getByPath("behavior.files", JSONArray.class);
        }
        if (behavior == null) {
            // 兼容 dropped 数组
            JSONArray dropped = report.getJSONArray("dropped");
            if (dropped != null) {
                for (int i = 0; i < dropped.size(); i++) {
                    JSONObject f = dropped.getJSONObject(i);
                    if (f == null) {
                        continue;
                    }
                    Map<String, Object> op = new LinkedHashMap<>();
                    op.put("operation", "create");
                    op.put("path", f.getStr("filepath", f.getStr("name")));
                    op.put("category", "dropped");
                    op.put("sha256", f.getStr("sha256"));
                    result.add(op);
                }
            }
            return result;
        }
        for (int i = 0; i < behavior.size(); i++) {
            Object item = behavior.get(i);
            Map<String, Object> op = new LinkedHashMap<>();
            if (item instanceof String) {
                op.put("operation", "write");
                op.put("path", item.toString());
            } else if (item instanceof JSONObject) {
                JSONObject f = (JSONObject) item;
                op.put("operation", f.getStr("operation", "write"));
                op.put("path", f.getStr("path", f.getStr("file")));
            }
            op.put("category", "filesystem");
            result.add(op);
        }
        return result;
    }

    /**
     * 提取 ATT&CK 技术映射（基于 API 调用 / 行为签名 / 进程命令行）
     *
     * @param report      Cuckoo 报告
     * @param processTree 进程树（用于补充命令行匹配）
     * @return 技术列表（含 techniqueId / tactic / name / source）
     */
    public List<Map<String, Object>> extractAttackTechniques(JSONObject report, List<Map<String, Object>> processTree) {
        Map<String, Map<String, Object>> dedup = new LinkedHashMap<>();
        // 1. Cuckoo signatures（含 description / name）
        JSONArray signatures = report.getByPath("signatures", JSONArray.class);
        if (signatures != null) {
            for (int i = 0; i < signatures.size(); i++) {
                JSONObject sig = signatures.getJSONObject(i);
                if (sig == null) {
                    continue;
                }
                String desc = sig.getStr("description", sig.getStr("name"));
                matchAttackTechniques(desc, "signature", dedup);
            }
        }
        // 2. 进程命令行
        if (processTree != null) {
            for (Map<String, Object> proc : processTree) {
                Object cmd = proc.get("commandLine");
                if (cmd != null) {
                    matchAttackTechniques(cmd.toString(), "process", dedup);
                }
                Object name = proc.get("name");
                if (name != null) {
                    matchAttackTechniques(name.toString(), "process", dedup);
                }
            }
        }
        // 3. API 调用摘要
        JSONObject apiSummary = report.getByPath("behavior.summary", JSONObject.class);
        if (apiSummary != null) {
            for (String key : apiSummary.keySet()) {
                matchAttackTechniques(key, "api", dedup);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * 提取 IOC（IP / 域名 / URL），从网络连接与进程命令行中提取
     *
     * @param networkConnections 网络连接
     * @param processTree        进程树
     * @return IOC 列表（含 type / value / source）
     */
    public List<Map<String, Object>> extractIocs(List<Map<String, Object>> networkConnections,
                                                 List<Map<String, Object>> processTree) {
        List<Map<String, Object>> iocs = new ArrayList<>();
        if (networkConnections != null) {
            for (Map<String, Object> conn : networkConnections) {
                addIoc(iocs, "IP", (String) conn.get("dstIp"), "network");
                addIoc(iocs, "DOMAIN", (String) conn.get("host"), "network");
                Object uri = conn.get("uri");
                Object host = conn.get("host");
                if (uri != null && host != null) {
                    addIoc(iocs, "URL", "http://" + host + uri, "network");
                }
                Object dnsReq = conn.get("request");
                if ("DNS".equals(conn.get("protocol")) && dnsReq != null) {
                    addIoc(iocs, "DOMAIN", dnsReq.toString(), "dns");
                }
            }
        }
        if (processTree != null) {
            for (Map<String, Object> proc : processTree) {
                Object cmd = proc.get("commandLine");
                if (cmd == null) {
                    continue;
                }
                String cmdStr = cmd.toString();
                Matcher ipMatcher = IPV4_PATTERN.matcher(cmdStr);
                while (ipMatcher.find()) {
                    addIoc(iocs, "IP", ipMatcher.group(), "process-command");
                }
                Matcher domainMatcher = DOMAIN_PATTERN.matcher(cmdStr);
                while (domainMatcher.find()) {
                    String domain = domainMatcher.group();
                    // 排除 IP 误匹配
                    if (!domain.matches(".*\\d+$")) {
                        addIoc(iocs, "DOMAIN", domain, "process-command");
                    }
                }
            }
        }
        return iocs;
    }

    /**
     * 构建 STIX 2.1 Bundle 对象列表（Process + NetworkTraffic）
     *
     * @param task 已解析的任务
     * @return STIX 对象列表
     */
    public List<Map<String, Object>> buildStixObjects(DynamicAnalysisTask task) {
        List<Map<String, Object>> objects = new ArrayList<>();
        if (task.getProcessTree() != null) {
            objects.addAll(task.getProcessTree());
        }
        if (task.getNetworkConnections() != null) {
            objects.addAll(task.getNetworkConnections());
        }
        return objects;
    }

    // ==================== 内部方法 ====================

    /**
     * 根据文本匹配 ATT&CK 技术（关键词命中）
     *
     * @param text     待匹配文本
     * @param source   匹配来源（signature/process/api）
     * @param dedup    去重 Map
     */
    private void matchAttackTechniques(String text, String source, Map<String, Map<String, Object>> dedup) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : ATTACK_KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                String techId = entry.getValue();
                if (!dedup.containsKey(techId)) {
                    String[] meta = ATTACK_TECHNIQUE_META.getOrDefault(techId, new String[]{"unknown", techId});
                    Map<String, Object> tech = new LinkedHashMap<>();
                    tech.put("techniqueId", techId);
                    tech.put("tactic", meta[0]);
                    tech.put("name", meta[1]);
                    tech.put("matchedBy", entry.getKey());
                    tech.put("source", source);
                    dedup.put(techId, tech);
                }
            }
        }
    }

    /**
     * 添加 IOC（去重）
     */
    private void addIoc(List<Map<String, Object>> iocs, String type, String value, String source) {
        if (StrUtil.isBlank(value) || "null".equals(value)) {
            return;
        }
        // 简单去重
        for (Map<String, Object> ioc : iocs) {
            if (type.equals(ioc.get("type")) && value.equals(ioc.get("value"))) {
                return;
            }
        }
        Map<String, Object> ioc = new LinkedHashMap<>();
        ioc.put("type", type);
        ioc.put("value", value);
        ioc.put("source", source);
        iocs.add(ioc);
    }

    /**
     * 解析端口
     */
    private Integer parsePort(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构建指标摘要
     */
    private Map<String, Object> buildIndicatorSummary(DynamicAnalysisTask task) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("processCount", task.getProcessTree() != null ? task.getProcessTree().size() : 0);
        summary.put("networkCount", task.getNetworkConnections() != null ? task.getNetworkConnections().size() : 0);
        summary.put("fileOpCount", task.getFileOperations() != null ? task.getFileOperations().size() : 0);
        summary.put("techniqueCount", task.getAttackTechniques() != null ? task.getAttackTechniques().size() : 0);
        summary.put("iocCount", task.getIocs() != null ? task.getIocs().size() : 0);
        return summary;
    }
}
