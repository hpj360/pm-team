# 红方视角标签体系审查报告

> **审查对象**: docs/tag-system-design.md (v1.6)
> **审查视角**: 红方(渗透测试/攻击方)实战作战需求
> **审查日期**: 2026-07-31

---

## 一、总体评估

### 综合评分

| 评估维度 | 评分(0-100) | 评价 |
|---------|------------|------|
| **总体支撑度** | **62** | 框架完整但红方主动攻击视角偏弱,存在"通用威胁情报视角偏重、红方攻击视角偏弱"的结构性失衡 |
| L1 文件属性层 | 58 | 缺失红方高频载体(PS1/BAT/SH/配置文件/内存镜像/磁盘镜像/RDP/PPK/KDBX) |
| L2 业务流程层 | 65 | 缺少按分析子能力(YARA/沙箱/NER)的执行状态追踪 |
| L3 实体识别层 | 55 | 凭证/服务/AD域/注册表持久化子类严重不足 |
| L4 业务场景层 | 60 | 横向移动技术/凭证可利用性/漏洞EXP/持久化/防御绕过细化缺失 |
| L5 情报关联层 | 68 | 偏防守方归因,缺红方自用工具OPSEC评估、子技术、C2框架特征 |
| L6 安全合规层 | 78 | 密级/保留期完备,缺红方产出物防溯源/销毁确认/任务关联 |

### 核心结论

标签体系以"蓝方威胁情报/防守方视角"为主,红方主动攻击视角(资产侦察、漏洞利用、凭证利用、横向移动、防御绕过、基础设施)覆盖不足。

---

## 二、问题清单(30项)

### P0级(16项,红方核心能力缺失)

| 编号 | 层级 | 问题 | 红方影响 |
|------|------|------|---------|
| P0-01 | L1 | 缺PS1/BAT/SH/CMD/VBS脚本类型 | 红方投递/横向核心载体无法识别 |
| P0-02 | L1 | 缺CONF/INI/YAML/JSON/XML配置文件 | 高价值配置无标签,凭证提取链路断裂 |
| P0-03 | L1 | 缺RAW/VMEM/DD/E01/DMP内存/磁盘镜像 | 红方取证核心载体无识别 |
| P0-04 | L1 | 缺RDP/PPK/KDBX/VPN凭证载体 | 凭证获取场景命中率大幅下降 |
| P0-06 | L3 | 凭证仅6子标签,缺Kerberos/NetNTLM/LSASS/ASREP/TGS等 | 红方核心凭证格式无法提取,作战链路断裂 |
| P0-07 | L3 | 缺AD域实体(域控/SID/SPN/GPO/委派) | 无法支撑Kerberoasting/DCSync/委派攻击 |
| P0-08 | L3 | 服务仅4类,缺WinRM/LDAP/Kerberos/DNS/AD/SNMP | 内网服务指纹粒度不足 |
| P0-09 | L3 | 漏洞缺CWE/CNVD,缺利用类型(RCE/LPE) | 无法按利用类型筛选战机 |
| P0-10 | L3 | 注册表持久化缺WMI/COM/AppInit/IFEO/Winlogon | 持久化核心注册表项无法识别 |
| P0-11 | L4 | 横向移动仅3标签,缺横向技术(PtH/Kerberoasting/PSExec) | 横向路径规划无技术维度 |
| P0-12 | L4 | 凭证缺Hash类型与可利用攻击评估 | 凭证利用决策无标签支撑 |
| P0-13 | L4 | 漏洞缺公开EXP/利用条件/在野利用 | 快速可利用战机筛选效率低 |
| P0-14 | L4 | 网络地形缺存活状态/服务版本/Web指纹/CMS/OS指纹 | 侦察产出无法打标 |
| P0-15 | L4 | 持久化场景与防御绕过场景完全空白 | 两大红方核心场景无承载 |
| P0-16 | L4 | 红队基础设施场景完全空白 | C2/域名前置/重定向器无标签 |
| P0-18 | L5 | 缺红方C2框架(Sliver/BruteRatel/Mythic等)+打包器 | 红队工具无L5标签产出 |

### P1级(10项,影响效率)

| 编号 | 层级 | 问题 | 红方影响 |
|------|------|------|---------|
| P0-05 | L1 | 缺SQL/DUMP/7Z | 数据外带与压缩投递无识别 |
| P0-17 | L5 | TTP仅到战术层,缺子技术(T1059.001) | 无法按具体子技术筛选 |
| P0-19 | 规则 | 正则缺Kerberos/NetNTLM/Shadow/PPK/RDP/VPN格式 | 高频凭证无法自动提取 |
| P0-20 | 规则 | 关联规则缺红方作战链路推导 | 作战链路需人工串联 |
| P1-21 | 规则 | 字典缺国产Webshell/红队工具特征 | 工具识别不全 |
| P1-22 | 规则 | 模型缺Shellcode/混淆/打包器/Webshell检测 | 载荷分析能力不足 |
| P1-23 | L3 | IP缺VPN/PROXY/CDN分类 | 网络侦察精度不足 |
| P1-24 | L3 | 主机缺域控/工作组/域加入状态 | AD攻击优先级判断缺失 |
| P1-25 | L4 | 画像缺存活状态与资产关键性 | 高价值目标筛选缺失 |
| P1-26 | L6 | 缺防溯源/销毁确认/任务关联/加密存储 | OPSEC与销毁审计缺失 |

### P2级(4项,优化体验)

| 编号 | 层级 | 问题 |
|------|------|------|
| P2-27 | L4 | 缺社会工程场景(钓鱼目标/组织架构/人员关系) |
| P2-28 | L3 | URL缺注入点/Webshell分类 |
| P2-29 | L5 | 缺红方自用工具归因风险评估 |
| P2-30 | 规则 | 弱口令字典仅16条,覆盖不足 |

---

## 三、缺失标签建议(共80+项新增)

### L1 文件属性层(新增23项)

| 标签编码 | 中文名 | 红方用途 |
|---------|--------|---------|
| L1.FILE.TYPE.PS1/BAT/SH/VBS | 脚本文件 | 红方投递/横向载体 |
| L1.FILE.TYPE.CONF/INI/YAML/JSON/XML | 配置文件 | 高价值配置识别 |
| L1.FILE.TYPE.RAW/DMP/DD/E01 | 取证镜像 | 红方volatility分析对象 |
| L1.FILE.TYPE.RDP/PPK/KDBX/VPN | 凭证载体 | 凭证harvesting产出 |
| L1.FILE.TYPE.SQL/DUMP/7Z | 数据外带 | 红方数据外带载体 |
| L1.FILE.TYPE.JSP/ASPX | Web脚本 | Webshell载体识别 |
| L1.FILE.FORMAT.FORENSIC/CREDENTIAL/CONFIG | 格式族 | 归类管理 |

### L3 实体识别层(新增30+项)

| 标签组 | 新增标签 | 红方用途 |
|--------|---------|---------|
| 凭证(9) | KERBEROS/NETNTLM/LSASS/ASREP/TGS/RDPCRED/PPK/BROWSER/WIFI/SHADOW | 红方核心凭证格式覆盖 |
| AD域(5,新组) | DC/SID/SPN/GPO/DELEGATION | AD域攻击目标识别 |
| 服务(6) | WINRM/LDAP/KERBEROS/DNS/AD/SNMP | 内网服务指纹细化 |
| 漏洞(3) | CWE/CNVD/CNNVD | 漏洞类型分析 |
| 注册表(5) | WMI/COM/APPINIT/IFEO/WINLOGON | 持久化机制识别 |
| IP(3) | VPN/PROXY/CDN_NODE | 网络侦察精度提升 |
| 主机(3) | DC/WORKGROUP/DOMAIN_JOINED | 域环境判定 |
| URL(2) | INJECTION/WEBSHELL | 注入点与Webshell定位 |

### L4 业务场景层(新增25+项)

| 标签组 | 新增标签 | 红方用途 |
|--------|---------|---------|
| 横向移动(2) | TECHNIQUE(PtH/Kerberoasting/PSExec/WMI等)/DEPTH | 横向技术分类与链路深度 |
| 凭证获取(3) | HASH_TYPE/USABLE_ATTACK/SOURCE | 凭证利用决策 |
| 漏洞战机(4) | EXPLOIT_TYPE/PUBLIC_EXP/IN_WILD/EXPLOIT_COND | 可利用性评估 |
| 网络地形(5) | ALIVE_STATUS/SERVICE_VERSION/WEBAPP_FINGER/CMS/OS_FINGER | 资产可达性与漏洞关联 |
| 持久化(2,新组) | MECHANISM/BACKDOOR_TYPE | 权限维持场景 |
| 防御绕过(3,新组) | AV_EDR/AVOID_STATUS/OBFUSCATION | 免杀与绕过场景 |
| 红队基础设施(4,新组) | C2/DOMAIN_FRONT/REDIRECTOR/CDN_USE | 基础设施管理 |
| 社会工程(3,新组) | TARGET_INFO/ORG_CHART/RELATION | 钓鱼前侦察 |
| 目标画像(2) | ALIVE_STATUS/CRITICALITY | 高价值目标筛选 |

### L5 情报关联层(新增12项)

| 标签组 | 新增标签 | 红方用途 |
|--------|---------|---------|
| TTP子技术 | SUBTECH(T1059.001等) | 细粒度TTP筛选 |
| C2框架(6) | SLIVER/BRUTERATEL/MYTHIC/HAVOC/EMPIRE/COVENANT | 红方C2识别 |
| 打包器(5,新组) | UPX/THEMIDA/VMPROT/DONUT/ENIGMA | 载荷脱壳识别 |
| 归因风险(1) | SELF_ATTRIBUTION_RISK | 红方OPSEC评估 |

### L6 安全合规层(新增5项)

| 标签组 | 新增标签 | 红方用途 |
|--------|---------|---------|
| 防溯源(2,新组) | SCRUBBED/WATERMARK | 产出物OPSEC管理 |
| 销毁管理(2,新组) | CONFIRMED/PENDING | 销毁审计 |
| 任务关联(1) | TASK_BIND | 产出物与任务绑定 |
| 加密存储(1) | ENCRYPTED_AT_REST | 静态加密 |

---

## 四、缺失规则建议(共50+项新增)

### 正则规则(新增19条)

| 规则 | 目标 | 红方场景 |
|------|------|---------|
| Kerberos票据(.kirbi) | R2\x00/base64 doIF | Kerberoasting/PtT |
| NetNTLMv1/v2 Hash | user::domain:LM:NT:challenge | 中继攻击/破解 |
| AS-REP Hash | $krb5asrep$23$ | AS-REP Roasting |
| TGS Hash | $krb5tgs$23$ | Kerberoasting |
| Linux Shadow Hash | $[156y]$ | Linux密码破解 |
| PuTTY会话(.ppk) | PuTTY-User-Key-File-2 | SSH会话劫持 |
| RDP文件 | full address:s: | RDP凭证载体 |
| VPN配置(OpenVPN) | client/dev/remote/<ca> | 内网接入凭证 |
| AD域SID | S-1-5-21-\d+-\d+-\d+ | AD域权限判定 |
| SPN识别 | service/host格式 | Kerberoasting目标 |
| CWE/CNVD/CNNVD编号 | 编号正则 | 漏洞类型分析 |
| WMI订阅持久化 | __EventFilter/Consumer | WMI持久化检测 |
| IFEO镜像劫持 | Image File Execution Options | IFEO持久化 |
| AppInit_DLLs | AppInit_DLLs | DLL注入持久化 |
| PowerShell Empire cradle | IEX(New-Object...DownloadString | Empire框架识别 |
| SharpHound/BloodHound输出 | BloodHound/SharpHound/session_json | AD侦察工具识别 |
| MSSQL服务路径 | xp_cmdshell/sp_addextendedproc | MSSQL提权侦察 |

### 字典规则(新增8条)

| 规则 | 内容 | 红方场景 |
|------|------|---------|
| 红队C2框架特征 | Sliver/BruteRatel/Mythic/Havoc/Empire/Covenant | C2框架识别 |
| 国产Webshell框架 | 哥斯拉/冰蝎/蚁剑/菜刀 | Webshell工具识别 |
| 打包器/混淆器 | UPX/Themida/VMProtect/Enigma/Donut | 载荷脱壳 |
| AV/EDR产品 | Defender/CrowdStrike/SentinelOne/CarbonBlack | 防御产品识别 |
| 弱口令扩充(200+) | 通用/中文/企业默认 | 弱口令爆破 |
| AD域控特征 | NTDS.DIT/SYSVOL/NETLOGON/krbtgt | 域控识别 |
| CMS指纹 | WordPress/Joomla/Drupal/Shiro/Struts/ThinkPHP | Web应用漏洞关联 |
| 横向移动工具 | PsExec/WMIC/CrackMapExec/Impacket/Rubeus | 横向技术识别 |

### 模型规则(新增8条)

| 规则 | 模型 | 红方场景 |
|------|------|---------|
| Shellcode识别 | ShellcodeML(熵值+指令模式) | 载荷shellcode识别 |
| 代码混淆检测 | ObfuscationML | 混淆载荷分析 |
| 打包器识别 | PackerML(PE节区特征) | 脱壳前置识别 |
| Webshell内容检测 | WebshellML(PHP/JSP/ASP内容) | Webshell深度检测 |
| Office宏代码检测 | MacroML(VBA恶意调用) | 宏载荷分析 |
| 内存取证特征 | MemoryForensicML | volatility替代 |
| DGA域名检测 | DGAML(LSTM熵值) | C2域名识别 |
| 流量入侵特征 | NIDSML(PCAP攻击流量) | 流量取证 |

### 关联规则(新增15条)

| 规则 | 推导逻辑 | 红方场景 |
|------|---------|---------|
| RDP配置+凭证→可横向 | TYPE=RDP + CRED.RDPCRED → LATERAL=RDPHIJACK | RDP横向链路 |
| SSH私钥+主机→可横向 | CRED.KEY + HOST → LATERAL=SSH | SSH横向链路 |
| AD域信息+域账户→可Kerberoasting | AD.SPN + USER.DOMAIN → LATERAL=KERBEROAST | Kerberoasting链路 |
| CVE+公开EXP→可利用 | VULN.CVE + EXP库命中 → VULN.PUBLIC_EXP=AVAILABLE | 快速可利用筛选 |
| 服务路径可写→可提权 | SERVICE + 路径可写 → VULN.EXPLOIT_TYPE=LPE | 服务提权 |
| AV/EDR+载荷→需免杀 | EVASION.AV_EDR + 载荷 → EVASION.AVOID_STATUS=DETECTED | 免杀需求评估 |
| NTLM Hash+主机→可PtH | CRED.HASH(NTLM) + HOST → LATERAL=PTH | Pass-the-Hash |
| Kerberos票据+域→可PtT | CRED.KERBEROS + AD → LATERAL=PTT | Pass-the-Ticket |
| 域控+域管→可DCSync | AD.DC + USER.ADMIN → LATERAL=DCSYNC | DCSync攻击 |
| CMS指纹+CVE→可Web利用 | TOPOLOGY.CMS + CMS关联CVE → VULN=RCE | Web漏洞利用 |

---

## 五、修复优先级建议

### 第一优先级:P0(立即补全,红方核心能力)

1. **L1文件类型补全**(PS1/BAT/CONF/RAW/RDP/PPK/KDBX/VPN等23项)— 基础设施级缺失
2. **L3凭证实体扩充**(Kerberos/NetNTLM/LSASS等9项)— 红方作战核心资产
3. **L3新增AD域实体组**(DC/SID/SPN/GPO/委派5项)— AD域攻击基础
4. **L4新增持久化/防御绕过/红队基础设施3大场景**(9项)— 核心作战场景空白
5. **正则规则补Kerberos/NetNTLM/Shadow/PPK等19条**— 高频凭证自动提取
6. **关联规则补红方作战链路15条**— 作战链路自动串联

### 第二优先级:P1(近期补全,影响效率)

7. L5 TTP增加子技术层 + C2框架标签 + 打包器标签
8. 字典补国产Webshell/AV-EDR/CMS/横向工具8条
9. 模型补Shellcode/混淆/打包器/Webshell检测8条
10. L3 IP补VPN/PROXY/CDN,主机补域控/工作组
11. L4画像补存活状态与资产关键性
12. L6补防溯源/销毁确认/任务关联

### 第三优先级:P2(中期优化)

13. L4新增社会工程场景
14. L5新增自我归因风险评估
15. 弱口令字典扩充至200+

---

## 六、关键发现

1. **结构性视角失衡**:标签以蓝方威胁情报视角为主,红方主动攻击视角覆盖不足
2. **凭证维度是最大短板**:L3凭证仅6子标签,缺Kerberos/NetNTLM/LSASS等红方高频格式
3. **AD域纵深完全缺失**:无AD域专属实体,无法支撑Kerberoasting/DCSync/委派攻击
4. **横向移动链路断裂**:无横向技术维度,缺作战链路自动串联
5. **持久化/防御绕过/红队基础设施三大场景空白**:红方核心场景无承载
6. **正则规则缺口集中在凭证与配置格式**:高频凭证无法自动提取
7. **关联规则缺红方作战链路串联**:偏威胁情报推导,缺攻击路径规划

**建议**:新增标签约80项,新增规则约50项,标签总数从274增至约354,规则总数从81增至约131。
