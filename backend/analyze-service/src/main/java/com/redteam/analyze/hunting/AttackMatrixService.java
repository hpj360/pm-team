package com.redteam.analyze.hunting;

import com.redteam.analyze.hunting.entity.AttackTechniqueEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ATT&CK 矩阵服务
 *
 * <p>提供 MITRE ATT&CK Enterprise 矩阵的数据查询能力：</p>
 * <ul>
 *   <li>{@link #getAllTechniques()}：全部技术列表</li>
 *   <li>{@link #getAllTactics()}：全部战术列表</li>
 *   <li>{@link #getTechniquesByTactic(String)}：按战术筛选技术</li>
 *   <li>{@link #searchTechniques(String)}：关键词检索技术</li>
 *   <li>{@link #getTechnique(String)}：根据技术 ID 查询</li>
 * </ul>
 *
 * <p>数据集：14 战术 × N 技术（内置代表性子集，可扩展至完整 193 技术）。
 * 数据初始化由 {@link AttackMatrixInitializer} 在应用启动时调用 {@link #loadDefaultMatrix()} 完成。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class AttackMatrixService {

    /**
     * 14 战术（ATT&CK Enterprise v15）
     */
    public static final String[] TACTICS = {
            "reconnaissance", "resource-development", "initial-access",
            "execution", "persistence", "privilege-escalation",
            "defense-evasion", "credential-access", "discovery",
            "lateral-movement", "collection", "command-and-control",
            "exfiltration", "impact"
    };

    /**
     * 战术中文名映射
     */
    public static final Map<String, String> TACTIC_NAMES;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("reconnaissance", "侦察");
        names.put("resource-development", "资源开发");
        names.put("initial-access", "初始访问");
        names.put("execution", "执行");
        names.put("persistence", "持久化");
        names.put("privilege-escalation", "权限提升");
        names.put("defense-evasion", "防御规避");
        names.put("credential-access", "凭证访问");
        names.put("discovery", "发现");
        names.put("lateral-movement", "横向移动");
        names.put("collection", "收集");
        names.put("command-and-control", "命令与控制");
        names.put("exfiltration", "外泄");
        names.put("impact", "影响");
        TACTIC_NAMES = Collections.unmodifiableMap(names);
    }

    /**
     * 技术存储（techniqueId -> entity）
     */
    private final Map<String, AttackTechniqueEntity> techniqueStore = new ConcurrentHashMap<>();

    /**
     * 加载默认 ATT&CK 矩阵数据集
     *
     * <p>内置 14 战术的代表性技术（约 40+ 技术，可扩展至完整 193）。
     * 重复调用会清空旧数据重新加载。</p>
     */
    public void loadDefaultMatrix() {
        techniqueStore.clear();
        // ===== 侦察 reconnaissance =====
        add("T1595", "Active Scanning", "攻击者扫描目标网络以收集可利用信息", "reconnaissance", "Process execution logs", "network");
        add("T1592", "Gather Victim Host Information", "收集受害者主机信息（操作系统、硬件等）", "reconnaissance", "Host network interaction", "host");
        add("T1589", "Gather Victim Identity Information", "收集受害者身份信息（邮箱、账号等）", "reconnaissance", "User activity logs", "identity");

        // ===== 资源开发 resource-development =====
        add("T1583", "Acquire Infrastructure", "获取基础设施（域名、服务器等）用于作战", "resource-development", "Domain registration", "infrastructure");
        add("T1587", "Develop Capabilities", "开发恶意软件、漏洞利用代码等能力", "resource-development", "Build artifacts", "malware");

        // ===== 初始访问 initial-access =====
        add("T1566", "Phishing", "钓鱼攻击获取初始访问", "initial-access", "Application log", "email");
        add("T1190", "Exploit Public-Facing Application", "利用面向公众的应用漏洞", "initial-access", "Application logs", "network");
        add("T1078", "Valid Accounts", "使用合法账号获取访问", "initial-access", "Authentication logs", "account");

        // ===== 执行 execution =====
        add("T1059", "Command and Scripting Interpreter", "命令与脚本解释器执行", "execution", "Process execution", "process");
        add("T1059.001", "PowerShell", "PowerShell 执行恶意命令", "execution", "PowerShell logs", "process");
        add("T1059.004", "Unix Shell", "Unix Shell 执行", "execution", "Shell command logs", "process");
        add("T1059.005", "Visual Basic", "VBScript 执行", "execution", "Process execution", "process");
        add("T1106", "Native API", "通过原生 API 执行", "execution", "API call logs", "process");
        add("T1047", "Windows Management Instrumentation", "WMI 执行命令", "execution", "WMI activity", "process");
        add("T1204", "User Execution", "诱导用户执行恶意代码", "execution", "Process execution", "user");

        // ===== 持久化 persistence =====
        add("T1053", "Scheduled Task/Job", "计划任务持久化", "persistence", "Scheduled task logs", "process");
        add("T1053.005", "Scheduled Task", "Windows 计划任务", "persistence", "Scheduled task creation", "process");
        add("T1543", "Create or Modify System Process", "创建或修改系统进程", "persistence", "Service creation logs", "process");
        add("T1543.003", "Windows Service", "Windows 服务持久化", "persistence", "Service installation", "process");
        add("T1547", "Boot or Logon Autostart Execution", "开机或登录自启动", "persistence", "Registry logs", "registry");
        add("T1136", "Create Account", "创建账号持久化", "persistence", "Account creation logs", "account");

        // ===== 权限提升 privilege-escalation =====
        add("T1068", "Exploitation for Privilege Escalation", "利用漏洞提权", "privilege-escalation", "Application logs", "exploit");
        add("T1548", "Abuse Elevation Control Mechanism", "滥用提权控制机制", "privilege-escalation", "Process execution", "process");

        // ===== 防御规避 defense-evasion =====
        add("T1112", "Modify Registry", "修改注册表规避检测", "defense-evasion", "Windows Registry logs", "registry");
        add("T1218", "System Binary Proxy Execution", "系统二进制代理执行", "defense-evasion", "Process execution", "process");
        add("T1218.011", "Rundll32", "Rundll32 执行恶意 DLL", "defense-evasion", "Process execution", "process");
        add("T1218.010", "Regsvr32", "Regsvr32 执行", "defense-evasion", "Process execution", "process");
        add("T1027", "Obfuscated Files or Information", "混淆文件或信息", "defense-evasion", "File metadata", "file");
        add("T1036", "Masquerading", "伪装合法进程或文件", "defense-evasion", "Process metadata", "process");

        // ===== 凭证访问 credential-access =====
        add("T1003", "OS Credential Dumping", "操作系统凭证转储", "credential-access", "Process execution", "credential");
        add("T1003.001", "LSASS Memory", "LSASS 内存凭证转储（Mimikatz）", "credential-access", "Memory access", "credential");
        add("T1110", "Brute Force", "暴力破解凭证", "credential-access", "Authentication logs", "credential");
        add("T1555", "Credentials from Password Stores", "从密码存储获取凭证", "credential-access", "Password store access", "credential");

        // ===== 发现 discovery =====
        add("T1087", "Account Discovery", "账号发现", "discovery", "Command execution logs", "account");
        add("T1087.001", "Local Account", "本地账号发现", "discovery", "Command execution", "account");
        add("T1018", "Remote System Discovery", "远程系统发现", "discovery", "Network scan logs", "network");
        add("T1057", "Process Discovery", "进程发现", "discovery", "Process enumeration", "process");
        add("T1082", "System Information Discovery", "系统信息发现", "discovery", "System command logs", "system");
        add("T1016", "System Network Configuration Discovery", "系统网络配置发现", "discovery", "Network command logs", "network");
        add("T1033", "System Owner/User Discovery", "系统所有者/用户发现", "discovery", "Command execution", "user");

        // ===== 横向移动 lateral-movement =====
        add("T1021", "Remote Services", "远程服务横向移动", "lateral-movement", "Authentication logs", "network");
        add("T1021.006", "Windows Remote Management", "WinRM 横向移动", "lateral-movement", "WinRM logs", "network");
        add("T1072", "Software Deployment Tools", "软件部署工具横向移动", "lateral-movement", "Deployment logs", "tool");

        // ===== 收集 collection =====
        add("T1005", "Data from Local System", "本地系统数据收集", "collection", "File access logs", "file");
        add("T1056", "Input Capture", "输入捕获（键盘记录）", "collection", "API call logs", "input");

        // ===== 命令与控制 command-and-control =====
        add("T1071", "Application Layer Protocol", "应用层协议 C2", "command-and-control", "Network traffic", "network");
        add("T1071.001", "Web Protocols", "Web 协议 C2（HTTP/HTTPS）", "command-and-control", "Network traffic", "network");
        add("T1071.004", "DNS", "DNS 协议 C2", "command-and-control", "DNS traffic", "network");
        add("T1105", "Ingress Tool Transfer", "工具传入", "command-and-control", "Network traffic", "file");
        add("T1132", "Data Encoding", "数据编码规避检测", "command-and-control", "Network traffic", "network");

        // ===== 外泄 exfiltration =====
        add("T1041", "Exfiltration Over C2 Channel", "通过 C2 通道外泄", "exfiltration", "Network traffic", "network");
        add("T1567", "Exfiltration Over Web Service", "通过 Web 服务外泄", "exfiltration", "Network traffic", "cloud");

        // ===== 影响 impact =====
        add("T1486", "Data Encrypted for Impact", "数据加密勒索", "impact", "File modification logs", "file");
        add("T1485", "Data Destruction", "数据破坏", "impact", "File deletion logs", "file");
        add("T1490", "Inhibit System Recovery", "抑制系统恢复", "impact", "Backup deletion logs", "system");
        add("T1498", "Network Denial of Service", "网络拒绝服务", "impact", "Network traffic", "network");

        log.info("ATT&CK 矩阵加载完成: 战术={}, 技术={}", TACTICS.length, techniqueStore.size());
    }

    /**
     * 添加技术到矩阵
     */
    private void add(String techniqueId, String name, String description,
                     String tactic, String dataSource, String category) {
        AttackTechniqueEntity entity = new AttackTechniqueEntity();
        entity.setTechniqueId(techniqueId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setTactic(tactic);
        entity.setTacticName(TACTIC_NAMES.getOrDefault(tactic, tactic));
        entity.setDataSource(dataSource);
        entity.setSubTechnique(techniqueId.contains("."));
        if (entity.isSubTechnique()) {
            entity.setSubTechniqueId(techniqueId);
        }
        techniqueStore.put(techniqueId, entity);
    }

    /**
     * 获取全部技术
     *
     * @return 技术列表
     */
    public List<AttackTechniqueEntity> getAllTechniques() {
        return new ArrayList<>(techniqueStore.values());
    }

    /**
     * 获取全部战术
     *
     * @return 战术列表（含 id / name / 中文名）
     */
    public List<Map<String, String>> getAllTactics() {
        List<Map<String, String>> result = new ArrayList<>();
        for (String tactic : TACTICS) {
            Map<String, String> t = new LinkedHashMap<>();
            t.put("id", tactic);
            t.put("name", tactic);
            t.put("nameZh", TACTIC_NAMES.getOrDefault(tactic, tactic));
            result.add(t);
        }
        return result;
    }

    /**
     * 按战术获取技术
     *
     * @param tactic 战术 ID
     * @return 技术列表
     */
    public List<AttackTechniqueEntity> getTechniquesByTactic(String tactic) {
        if (tactic == null || tactic.isEmpty()) {
            return Collections.emptyList();
        }
        return techniqueStore.values().stream()
                .filter(t -> tactic.equalsIgnoreCase(t.getTactic()))
                .collect(Collectors.toList());
    }

    /**
     * 关键词搜索技术
     *
     * @param keyword 关键词（匹配 techniqueId / name / description）
     * @return 匹配的技术列表
     */
    public List<AttackTechniqueEntity> searchTechniques(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lower = keyword.trim().toLowerCase();
        return techniqueStore.values().stream()
                .filter(t -> (t.getTechniqueId() != null && t.getTechniqueId().toLowerCase().contains(lower))
                        || (t.getName() != null && t.getName().toLowerCase().contains(lower))
                        || (t.getDescription() != null && t.getDescription().toLowerCase().contains(lower))
                        || (t.getTacticName() != null && t.getTacticName().contains(keyword.trim())))
                .collect(Collectors.toList());
    }

    /**
     * 根据 techniqueId 查询技术
     *
     * @param techniqueId 技术 ID
     * @return 技术实体，不存在返回 null
     */
    public AttackTechniqueEntity getTechnique(String techniqueId) {
        if (techniqueId == null || techniqueId.isEmpty()) {
            return null;
        }
        return techniqueStore.get(techniqueId);
    }

    /**
     * 技术总数
     *
     * @return 技术数量
     */
    public int techniqueCount() {
        return techniqueStore.size();
    }

    /**
     * 战术总数
     *
     * @return 战术数量（固定 14）
     */
    public int tacticCount() {
        return TACTICS.length;
    }
}
