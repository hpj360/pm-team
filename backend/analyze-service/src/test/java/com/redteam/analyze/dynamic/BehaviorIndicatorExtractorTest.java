package com.redteam.analyze.dynamic;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BehaviorIndicatorExtractor 单元测试
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BehaviorIndicatorExtractorTest {

    private BehaviorIndicatorExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new BehaviorIndicatorExtractor();
    }

    /**
     * 构造一个完整的 Cuckoo 报告 JSON
     */
    private String buildFullReport() {
        JSONObject report = new JSONObject();
        // 进程
        JSONObject proc1 = new JSONObject();
        proc1.set("pid", 1234);
        proc1.set("parent_id", 0);
        proc1.set("process_name", "malware.exe");
        proc1.set("command_line", "cmd.exe /c powershell -enc SGVsbG8=");
        report.setByPath("behavior.processes", new cn.hutool.json.JSONArray().set(proc1));

        // 网络流量
        JSONObject http = new JSONObject();
        http.set("method", "GET");
        http.set("ip", "45.155.205.233");
        http.set("port", 443);
        http.set("uri", "/gate.php");
        http.set("host", "malicious-update.example-evil.com");
        http.set("user-agent", "Mozilla/5.0");
        report.setByPath("network.http", new cn.hutool.json.JSONArray().set(http));
        report.setByPath("network.hosts", new cn.hutool.json.JSONArray().set("8.8.8.8:53"));
        JSONObject dns = new JSONObject();
        dns.set("request", "evil-c2.example.com");
        dns.set("type", "A");
        report.setByPath("network.dns", new cn.hutool.json.JSONArray().set(dns));

        // 签名（触发 ATT&CK）
        JSONObject sig = new JSONObject();
        sig.set("name", "powershell_execution");
        sig.set("description", "Detects powershell command execution");
        report.set("signatures", new cn.hutool.json.JSONArray().set(sig));

        // 文件操作
        report.setByPath("behavior.summary.files", new cn.hutool.json.JSONArray()
                .set("C:\\Users\\Public\\dropper.exe"));

        return JSONUtil.toJsonStr(report);
    }

    // ==================== extract 完整流程 ====================

    @Test
    @DisplayName("extract: 完整报告提取进程树/网络/文件/ATT&CK/IOC")
    void extract_fullReport_extractsAllIndicators() {
        DynamicAnalysisTask task = new DynamicAnalysisTask();
        task.setTaskId("dyn-1");
        task.setRawReport(buildFullReport());

        extractor.extract(task);

        // 进程树
        assertEquals(1, task.getProcessTree().size());
        Map<String, Object> proc = task.getProcessTree().get(0);
        assertEquals(1234, proc.get("pid"));
        assertEquals("malware.exe", proc.get("name"));
        assertNotNull(proc.get("stixId"));
        assertEquals("process", proc.get("type"));

        // 网络连接（http + hosts + dns = 3）
        assertEquals(3, task.getNetworkConnections().size());

        // 文件操作
        assertEquals(1, task.getFileOperations().size());

        // ATT&CK 技术（powershell -> T1059.001, cmd.exe -> T1059）
        assertFalse(task.getAttackTechniques().isEmpty());
        assertTrue(task.getAttackTechniques().stream()
                .anyMatch(t -> "T1059.001".equals(t.get("techniqueId"))));

        // IOC
        assertFalse(task.getIocs().isEmpty());
        assertTrue(task.getIocs().stream()
                .anyMatch(i -> "45.155.205.233".equals(i.get("value"))));

        // 指标摘要
        assertNotNull(task.getIndicators());
        assertEquals(1, task.getIndicators().get("processCount"));
    }

    @Test
    @DisplayName("extract: 任务为空安全返回")
    void extract_nullTask_safeReturn() {
        extractor.extract(null); // 不抛异常
    }

    @Test
    @DisplayName("extract: 原始报告为空安全返回")
    void extract_blankReport_safeReturn() {
        DynamicAnalysisTask task = new DynamicAnalysisTask();
        task.setTaskId("dyn-2");
        task.setRawReport("");
        extractor.extract(task);
        assertTrue(task.getProcessTree().isEmpty());
        assertTrue(task.getNetworkConnections().isEmpty());
    }

    @Test
    @DisplayName("extract: 非法 JSON 报告安全返回")
    void extract_invalidJson_safeReturn() {
        DynamicAnalysisTask task = new DynamicAnalysisTask();
        task.setTaskId("dyn-3");
        task.setRawReport("not a json {{{");
        extractor.extract(task);
        assertTrue(task.getProcessTree().isEmpty());
    }

    // ==================== 进程树 ====================

    @Test
    @DisplayName("extractProcessTree: 无 behavior.processes 返回空")
    void extractProcessTree_emptyReport_returnsEmpty() {
        List<Map<String, Object>> tree = extractor.extractProcessTree(new JSONObject());
        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    @Test
    @DisplayName("extractProcessTree: 进程包含 STIX process ID")
    void extractProcessTree_containsStixId() {
        JSONObject report = JSONUtil.parseObj(buildFullReport());
        List<Map<String, Object>> tree = extractor.extractProcessTree(report);
        assertEquals(1, tree.size());
        assertTrue(((String) tree.get(0).get("stixId")).startsWith("process--"));
    }

    // ==================== 网络连接 ====================

    @Test
    @DisplayName("extractNetworkConnections: HTTP + hosts + DNS 三类")
    void extractNetworkConnections_threeTypes() {
        JSONObject report = JSONUtil.parseObj(buildFullReport());
        List<Map<String, Object>> conns = extractor.extractNetworkConnections(report);
        assertEquals(3, conns.size());
        // 验证 HTTP 连接字段
        Map<String, Object> httpConn = conns.stream()
                .filter(c -> "HTTP".equals(c.get("protocol")))
                .findFirst()
                .orElseThrow();
        assertEquals("45.155.205.233", httpConn.get("dstIp"));
        assertEquals(443, httpConn.get("dstPort"));
        assertEquals("malicious-update.example-evil.com", httpConn.get("host"));
    }

    @Test
    @DisplayName("extractNetworkConnections: 无 network 字段返回空")
    void extractNetworkConnections_noNetwork_returnsEmpty() {
        List<Map<String, Object>> conns = extractor.extractNetworkConnections(new JSONObject());
        assertNotNull(conns);
        assertTrue(conns.isEmpty());
    }

    // ==================== ATT&CK 映射 ====================

    @Test
    @DisplayName("extractAttackTechniques: powershell 命令行映射 T1059.001")
    void extractAttackTechniques_powershell_mapped() {
        JSONObject report = JSONUtil.parseObj(buildFullReport());
        JSONObject proc1 = new JSONObject();
        proc1.set("pid", 100);
        proc1.set("process_name", "powershell.exe");
        proc1.set("command_line", "powershell -EncodedCommand abc");
        report.setByPath("behavior.processes", new cn.hutool.json.JSONArray().set(proc1));

        List<Map<String, Object>> processTree = extractor.extractProcessTree(report);
        List<Map<String, Object>> techniques = extractor.extractAttackTechniques(report, processTree);
        assertTrue(techniques.stream().anyMatch(t -> "T1059.001".equals(t.get("techniqueId"))));
    }

    @Test
    @DisplayName("extractAttackTechniques: 多技术去重")
    void extractAttackTechniques_dedup() {
        JSONObject report = new JSONObject();
        JSONObject sig = new JSONObject();
        sig.set("description", "powershell execution via cmd.exe");
        report.set("signatures", new cn.hutool.json.JSONArray().set(sig));
        List<Map<String, Object>> techniques = extractor.extractAttackTechniques(report, Collections.emptyList());
        // 一次 description 同时命中 powershell 和 cmd.exe，去重后应包含 T1059 和 T1059.001
        assertTrue(techniques.size() >= 2);
    }

    // ==================== IOC 提取 ====================

    @Test
    @DisplayName("extractIocs: 从进程命令行提取 IP 与域名")
    void extractIocs_fromCommandLine() {
        Map<String, Object> proc = new HashMap<>();
        proc.put("commandLine", "cmd.exe /c net view 192.168.1.10 && ping evil-c2.example.com");
        List<Map<String, Object>> iocs = extractor.extractIocs(Collections.emptyList(), List.of(proc));
        assertTrue(iocs.stream().anyMatch(i -> "192.168.1.10".equals(i.get("value"))));
        assertTrue(iocs.stream().anyMatch(i -> "evil-c2.example.com".equals(i.get("value"))));
    }

    @Test
    @DisplayName("extractIocs: IOC 去重")
    void extractIocs_dedup() {
        Map<String, Object> conn = new HashMap<>();
        conn.put("protocol", "HTTP");
        conn.put("dstIp", "1.2.3.4");
        conn.put("host", "example.com");
        conn.put("uri", "/path");
        List<Map<String, Object>> iocs = extractor.extractIocs(List.of(conn), Collections.emptyList());
        // 再次添加相同连接，应去重
        List<Map<String, Object>> iocs2 = extractor.extractIocs(List.of(conn), Collections.emptyList());
        assertEquals(iocs.size(), iocs2.size());
    }

    // ==================== STIX 对象构建 ====================

    @Test
    @DisplayName("buildStixObjects: 聚合进程与网络对象")
    void buildStixObjects_aggregatesProcessAndNetwork() {
        DynamicAnalysisTask task = new DynamicAnalysisTask();
        task.setRawReport(buildFullReport());
        extractor.extract(task);
        List<Map<String, Object>> stix = extractor.buildStixObjects(task);
        assertEquals(task.getProcessTree().size() + task.getNetworkConnections().size(), stix.size());
    }

    @Test
    @DisplayName("extract: dropped 文件被识别为 create 操作")
    void extract_droppedFiles_createOp() {
        JSONObject report = new JSONObject();
        JSONObject dropped = new JSONObject();
        dropped.set("filepath", "C:\\Temp\\dropped.dll");
        dropped.set("sha256", "abc123");
        report.set("dropped", new cn.hutool.json.JSONArray().set(dropped));

        DynamicAnalysisTask task = new DynamicAnalysisTask();
        task.setTaskId("dyn-4");
        task.setRawReport(JSONUtil.toJsonStr(report));
        extractor.extract(task);

        assertFalse(task.getFileOperations().isEmpty());
        Map<String, Object> op = task.getFileOperations().get(0);
        assertEquals("create", op.get("operation"));
        assertEquals("dropped", op.get("category"));
    }
}
