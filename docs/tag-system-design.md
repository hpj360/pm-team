# 红方文件汇聚平台标签体系设计文档

> 版本: v2.0(红方实战完善版)
> 编写日期: 2026-07-29
> 适用范围: 红方文件汇聚管理平台全业务流程
> 关联文档: [database-design.md](./database-design.md)、[design-spec.md](./design-spec.md)、[prd.md](./prd.md)
> 修订记录: v1.1 修复 L2 编码规范(P0)、L5 APT 去重与中文名(P1)、L6 密级去重(P1)、L2 评分区间互斥(P1)、L4 BOOL→ENUM(P1)等评审问题;v1.2 第8章自动识别规则集大幅深化,规则数 42→81(正则×39/字典×15/模型×12/关联×15),规则字段 5→11,新增 8.6 规则配置与热更新节;v1.3 对抗性审查修复:补入 L1.FILE.TYPE 10 个缺失类型(PHP/ELF/MACHO/JAR/APK/GZIP/BZIP2/XZ/DOC/XLS)、修复命令注入/注册表键/Base64/IPv6/XSS/文件哈希等正则规则错误、修复 Webshell 关联规则类型不匹配、修正表名引用(entities/parse_results/analysis_results)、补充 ML 模型降级策略、补充系统派生规则说明、补充外部数据依赖、补充大文件性能保护、修正冲突处理策略;v1.3.1 二轮复核修复:同步更新 A.1 各层标签数量统计(L1 子标签 47→57、总数 264→274,与 A.2/A.5 对齐)、修正 L2.PARSE.STATUS 识别规则表名与字段(parse_task.status→parse_results.parse_status)、修正 L2.ANALYZE.TYPE 识别规则表名与字段(analyze_task.type→analysis_results.analysis_type);v1.4 表格整合:将第2-7章原 44 个标签字典小表格(L1×6/L2×8/L3×12/L4×8/L5×5/L6×5)按层合并为 6 个大表格(L1-L6 每层1个),表头新增"标签组"列作为第一列以标识每行归属,移除 ### 子节标题与重复表头,标签总数保持 274 不变(父44+子230),跨表格引用内联化(如"第 2.5 节映射表"改为指向"第2章 标签组=格式族"的行),第8章规则集4类表格保持不变;v1.5 去编号引用:将第8章4类规则表(正则/字典/模型/关联)的"规则编码"列改为"序号+规则描述"列(序号仅用于表内排序,规则描述一句话概括规则作用),规则"前置依赖""冲突处理"列及正文/执行流程图/性能表/配置管理/附录中所有规则编号引用统一替换为规则描述,规则总数 81 条与标签总数 274 保持不变;v1.6 对抗性审查优化:补充术语表(IOC/TTP/ATT&CK/Magic Number/DGA/YARA/CVSS/eTLD+1/JWT/PE/ELF/CIDR 等 12 项首次出现定义)、修复正则5(URL提取)反引号破坏 Markdown 表格解析问题、修复正则28(命令注入特征识别)`\|\|` 转义歧义(改用字符类 `[|]{2}` 消除 Markdown 转义与正则转义的不可区分性)、修正 A.4 网络地形还原可选层级数与第9章映射矩阵不一致(3→2,L2 由可选修正为不适用)、补充目录子章节导航(8.0-8.6/10.1-10.4)、补充 DDL 字段数差异说明(tag_dict_v2 14 字段 vs 标签字典 11 字段)、优化修订记录可读性,标签总数 274 与规则总数 81 保持不变;v2.0 红方实战完善版:基于红方视角审查,原标签体系以蓝方威胁情报视角为主、红方主动攻击视角覆盖不足,本次新增 103 项标签覆盖红方核心作战场景(凭证利用/AD域/横向移动/持久化/防御绕过/红队基础设施/打包器/防溯源/销毁管理),其中 L1 新增 25 项(22 文件类型+3 格式族)、L3 新增 36 项(凭证 10+AD域新组 6+服务 6+漏洞 3+注册表键 5+IP 3+主机 3,新增 AD域标签组)、L4 新增 25 项(横向移动 2+凭证获取 3+漏洞战机 4+网络地形 5+持久化新组 3+防御绕过新组 4+红队基础设施新组 4)、L5 新增 12 项(TTP子技术 1+恶意软件家族 6+打包器新组 5)、L6 新增 5 项(防溯源新组 3+销毁管理新组 2),新增 7 个父标签(AD域/持久化/防御绕过/红队基础设施/打包器/防溯源/销毁管理),标签总数 274→377(父 44→51、子 230→326),第9章映射矩阵与 A.4 业务场景新增 3 个红方场景行(持久化/防御绕过/红队基础设施,8 大场景→11 大场景),术语表补充 Kerberoasting/AS-REP Roasting/Pass-the-Hash/Pass-the-Ticket/DCSync/IFEO/AppInit_DLLs/CLSID/SPN/GPO/UPX/Themida/VMProtect/Donut/Sliver/Brute Ratel/Mythic/Havoc/Empire/Covenant 等 20 项红方专业术语,第8章新增 50 条红方实战识别规则(正则+19/字典+8/模型+8/关联+15)覆盖红方高频凭证格式/AD域实体/持久化机制/C2框架/作战链路推导,规则总数 81→131(正则39→58/字典15→23/模型12→20/关联15→30),8.5 节执行流程补充红方四条专项流水线(凭证提取/AD域侦察/持久化检测/横向移动推导);v2.0 DDL 更新与对抗性审查修复:tag_dict_v2 新增 redteam_scenario/tech_category 字段(14→16)及 3 项索引、file_tags_v2 新增 tag_rule_type 字段及索引、补充 7 个新标签组(AD域/持久化/防御绕过/红队基础设施/打包器/防溯源/销毁管理)DDL COMMENT、修复 L4.SCENE.LATERAL.TECHNIQUE 枚举缺失 SSH/DCSYNC、修复 8.5.1 解析阶段正则计数(50→53)与上传阶段流程图(补入双扩展名识别/KeePass数据库识别),标签总数 377 与规则总数 131 经复核无丢失
---

## 目录

- [第1章 六层标签体系架构](#第1章-六层标签体系架构)
- [第2章 L1 文件属性层标签字典](#第2章-l1-文件属性层标签字典)
- [第3章 L2 业务流程层标签字典](#第3章-l2-业务流程层标签字典)
- [第4章 L3 实体识别层标签字典](#第4章-l3-实体识别层标签字典)
- [第5章 L4 业务场景层标签字典](#第5章-l4-业务场景层标签字典)
- [第6章 L5 情报关联层标签字典](#第6章-l5-情报关联层标签字典)
- [第7章 L6 安全合规层标签字典](#第7章-l6-安全合规层标签字典)
- [第8章 自动识别规则集](#第8章-自动识别规则集)
  - [8.0 规则字段定义](#80-规则字段定义) / [8.1 正则规则](#81-正则规则regex-共-58-条) / [8.2 字典匹配规则](#82-字典匹配规则dict-共-23-条) / [8.3 模型识别规则](#83-模型识别规则ml-共-20-条) / [8.4 关联推导规则](#84-关联推导规则assoc-共-30-条)
  - [8.5 规则执行流程](#85-规则执行流程) / [8.6 规则配置与热更新](#86-规则配置与热更新)
- [第9章 标签×业务场景映射矩阵](#第9章-标签业务场景映射矩阵)
- [第10章 标签体系数据模型增强设计稿](#第10章-标签体系数据模型增强设计稿)
  - [10.1 tag_dict_v2](#101-tag_dict_v2-增强标签字典表) / [10.2 file_tags_v2](#102-file_tags_v2-扩展文件标签表) / [10.3 索引设计](#103-索引设计) / [10.4 兼容策略](#104-与现有表的兼容策略)
- [附录 标签统计](#附录-标签统计)

---

## 文档说明

### 设计目标

打通"文件→实体→画像→场景→情报→合规"链路,为红方文件汇聚平台提供体系化、可扩展、可自动识别的标签体系,支撑:

1. **自动打标**:基于正则/字典/模型/关联规则,在文件上传、解析、分析各阶段自动生成标签
2. **多维检索**:按层级、分类、场景等多维度筛选文件,支撑红方人员快速定位目标资产
3. **画像刻画**:以标签为载体,串联目标资产、网络地形、凭证、漏洞、横向移动等业务要素
4. **情报关联**:对接 ATT&CK、APT 组织、恶意软件家族等威胁情报知识库
5. **合规管控**:对涉密、敏感数据进行密级、保留期、访问限制等全生命周期管控

### 现状与差距

| 现有表 | 现有字段 | 差距 |
|--------|----------|------|
| `file_tags` | tag_name / tag_type(user\|system) / tag_color | 无层级、无编码、无来源、无置信度、无规则关联 |
| `tag_dict` | tag_name / tag_category / description | 无编码规范、无值类型、无识别规则、无父子层级、无适用对象 |

### 编码规范

**全局唯一编码格式**: `层级.分类.名称.值`

- **层级**: L1 ~ L6
- **分类**: 层内大组,L1=FILE,L2=UPLOAD/PARSE/ANALYZE/PROFILE,L3=ENTITY,L4=SCENE,L5=INTEL,L6=COMP
- **名称**: 标签组名,如 TYPE / SOURCE / IP / VULN
- **值**: 具体枚举值,如 PDF / PUBLIC / HIGH

示例:
- `L1.FILE.TYPE.PDF` — L1 文件属性层,文件类型组,PDF 类型
- `L3.ENTITY.IP.PUBLIC` — L3 实体识别层,IP 实体组,公网 IP
- `L4.SCENE.VULN.IMPACT` — L4 业务场景层,漏洞战机场景,影响等级(ENUM 值:HIGH/MID/LOW)
- `L6.COMP.CLASSIFICATION.SECRET` — L6 安全合规层,密级组,秘密

父标签编码示例:
- 父:`L3.ENTITY.IP` → 子:`L3.ENTITY.IP.PUBLIC`、`L3.ENTITY.IP.PRIVATE`

### 术语表

本文档涉及的专业术语首次出现时定义如下,后续引用不再重复解释:

| 术语 | 全称/释义 |
|------|----------|
| IOC | Indicator of Compromise,失陷指标,指主机或网络已被攻击者控制的可观测证据(如恶意 IP/域名/文件哈希/注册表键等) |
| TTP | Tactics, Techniques, and Procedures,战术/技术/程序,描述攻击者行为模式的高级抽象 |
| ATT&CK | MITRE ATT&CK,MITRE 公司维护的攻击行为知识库,以战术(TA)/技术(T)矩阵建模对手行为 |
| Magic Number | 魔数,文件头部固定字节序列,用于在无扩展名或扩展名伪造时识别文件真实类型 |
| DGA | Domain Generation Algorithm,域名生成算法,恶意软件用以动态生成 C2 域名规避静态封堵 |
| YARA | 针对恶意软件的模式匹配工具与规则语言,支持基于字节/字符串特征扫描二进制文件 |
| CVSS | Common Vulnerability Scoring System,通用漏洞评分系统,0-10 分量化漏洞严重程度 |
| eTLD+1 | effective Top-Level Domain plus one,有效顶级域名加一级,如 `a.b.example.com` 的 eTLD+1 为 `example.com` |
| JWT | JSON Web Token,基于 JSON 的无状态令牌格式,常用于 API 鉴权,形如 `eyJxxx.eyJxxx.signature` |
| PE | Portable Executable,Windows 可移植可执行文件格式,EXE/DLL 均基于 PE,头部魔数为 `4D 5A`(MZ) |
| ELF | Executable and Linkable Format,Linux/Unix 可执行与可链接格式,头部魔数为 `7F 45 4C 46` |
| CIDR | Classless Inter-Domain Routing,无类别域间路由,如 `10.0.0.0/8` 表示前 8 位为网络号 |
| C2 | Command and Control,命令与控制,攻击者用于向被控端下发指令的服务器/信道 |
| APT | Advanced Persistent Threat,高级持续性威胁,通常指有国家或组织背景的长期隐蔽攻击组织 |
| Kerberoasting | Kerberoasting 攻击,红方通过请求域内服务账户的 TGS 票据并离线破解其哈希以获取服务账户明文密码的攻击技术 |
| AS-REP Roasting | AS-REP 烘焙攻击,针对未启用预认证的域账户,红方无需凭据即可获取其 AS-REP 哈希并离线破解密码 |
| Pass-the-Hash | PtH 哈希传递,红方利用抓取到的 NTLM 哈希直接通过身份认证,无需还原明文密码即可横向移动 |
| Pass-the-Ticket | PtT 票据传递,红方利用窃取的 Kerberos 票据(TGT/TGS)冒充合法用户身份访问域内资源 |
| DCSync | DCSync 攻击,红方模拟域控制器的复制行为(DRSUAPI),从域控远程导出域内账户哈希,需具备 DC 复制权限 |
| IFEO | Image File Execution Options,镜像文件执行选项,Windows 注册表项,红方利用其 Debugger 键值实现镜像劫持持久化 |
| AppInit_DLLs | AppInit_DLLs,Windows 注册表键,红方通过设置该键值使任意进程加载指定 DLL,实现 DLL 注入持久化 |
| CLSID | Class Identifier,COM 类标识符,Windows 注册表 HKCR\CLSID 下的唯一标识,红方可通过篡改 CLSID 实现 COM 劫持持久化 |
| SPN | Service Principal Name,服务主体名称,域内服务实例的唯一标识,是 Kerberoasting 攻击的目标定位依据 |
| GPO | Group Policy Object,组策略对象,域内集中分发配置的策略容器,红方利用 GPO 实现权限维持与载荷分发 |
| UPX | Ultimate Packer for eXecutables,开源可执行文件压缩器,红方常用以减小载荷体积并做基础免杀 |
| Themida | Themida,商业软件保护与强加壳工具,采用代码虚拟化与反调试技术,红方载荷分析中的强壳识别对象 |
| VMProtect | VMProtect,商业代码虚拟化保护工具,将关键代码转为虚拟机字节码,红方载荷分析中的虚拟化壳识别对象 |
| Donut | Donut,.NET 程序集注入器,将 .NET Payload 转换为位置无关 shellcode,红方跨平台载荷投递工具 |
| Sliver | Sliver,开源跨平台 C2 框架,支持 mTLS/WireGuard 通信,红方常用 C2 基础设施 |
| Brute Ratel | Brute Ratel,商业红队 C2 框架,主打绕过 EDR 的仿真测试能力,红方高级 C2 基础设施 |
| Mythic | Mythic,模块化开源 C2 框架,支持多 Agent 与插件扩展,红方可定制 C2 基础设施 |
| Havoc | Havoc,开源 C2 框架,支持 Python 扩展与 sleep 混淆,红方轻量级 C2 基础设施 |
| Empire | Empire,基于 PowerShell 的开源 C2 框架,红方历史常用 C2 基础设施 |
| Covenant | Covenant,.NET 开源 C2 框架,支持 Covenant GUI 与多 Listener,红方 C2 基础设施 |

### 标签字段定义(全文档统一 11 字段)

| 序号 | 字段 | 中文名 | 说明 |
|------|------|--------|------|
| 1 | 标签编码 | tag_code | 全局唯一,遵循 `层级.分类.名称.值` 规范 |
| 2 | 标签中文名 | tag_name | 必须有中文名,无英文-only |
| 3 | 层级 | layer | L1-L6 |
| 4 | 分类 | category | 层内分组 |
| 5 | 值类型 | value_type | ENUM/TEXT/NUMBER/BOOL/DATE |
| 6 | 适用对象 | applicable_object | FILE/ENTITY/TARGET/TASK/ALL |
| 7 | 识别规则 | identify_rule | 自动打标规则描述 |
| 8 | 是否多选 | is_multi | 是/否 |
| 9 | 父标签 | parent_code | 父标签编码,无父留 `-` |
| 10 | 启用 | enabled | 是/否 |
| 11 | 口径定义 | description | 标签含义与边界 |

---

## 第1章 六层标签体系架构

### 1.1 六层架构总览

| 层级 | 名称 | 职责 | 标签对象 | 示例 |
|------|------|------|----------|------|
| L1 | 文件属性 | 描述文件自身客观属性及获取来源元数据,上传时即可识别 | 文件 | 文件类型(PDF/EXE)、来源类型、语言、大小分级、格式族、编码方式 |
| L2 | 业务流程 | 标记文件在业务链路中的阶段与处理结果,反映系统对文件的处理状态 | 文件 | 上传来源(Web/API)、解析状态、分析结论、画像覆盖 |
| L3 | 实体识别 | 从文件内容中提取的结构化实体类型,可独立于文件存在 | 文件/实体 | IP(公网/内网/C2)、域名、主机、用户、凭证、漏洞、IOC、端口、服务 |
| L4 | 业务场景 | 关联红方 11 大业务场景,反映文件在红方作战流程中的角色 | 文件/目标/任务 | 目标画像、网络地形、凭证获取、漏洞战机、横向移动、持久化、防御绕过、红队基础设施 |
| L5 | 情报关联 | 关联威胁情报与攻击知识库,刻画文件的威胁属性 | 文件/实体/目标 | APT 组织、攻击技术(TTP)、威胁等级、情报来源、恶意软件家族 |
| L6 | 安全合规 | 数据管控与合规属性,贯穿文件全生命周期 | 文件/目标 | 密级、保留期、合规要求、访问限制、脱敏状态 |

### 1.2 层级设计原则

1. **正交性**:同一文件在不同层级可有不同标签,层与层之间不重叠
   - L1 描述"文件是什么",L2 描述"系统对文件做了什么",L3 描述"文件里有什么",L4 描述"文件在红方场景中扮演什么角色",L5 描述"文件的威胁属性",L6 描述"如何管控这个文件"
2. **依赖性**:高层级标签依赖低层级标签推导
   - L4 业务场景标签常由 L3 实体标签关联推导;L6 合规标签可由 L3/L5 标签触发
3. **可扩展性**:新增业务场景或实体类型时,仅需在对应层级新增标签组,不影响其他层
4. **可识别性**:L1/L3 标签以自动识别为主,L2/L4/L5/L6 标签结合规则推导与人工标注

### 1.3 层级互斥规则

- **WHEN** 为文件打 `L1.FILE.TYPE.PDF` 标签
- **THEN** 该标签归属 L1 层,不与 L3 实体标签混淆
- **AND** 标签编码前缀反映层级(以 `L1.` 开头)
- **AND** 同一标签编码不可同时归属多个层级

---

## 第2章 L1 文件属性层标签字典

> L1 文件属性层共包含 6 个标签组:**文件类型**(L1.FILE.TYPE,49 个子标签)、**来源类型**(L1.FILE.SOURCE,5 个子标签)、**语言**(L1.FILE.LANG,6 个子标签)、**大小分级**(L1.FILE.SIZE,5 个子标签)、**格式族**(L1.FILE.FORMAT,11 个子标签)、**编码方式**(L1.FILE.ENCODING,6 个子标签)。该层职责是描述文件自身客观属性及获取来源元数据,上传阶段即可识别。下表为该层完整标签字典(共 6 个父标签 + 82 个子标签 = 88 行)。

| 标签组 | 标签编码 | 标签中文名 | 层级 | 分类 | 值类型 | 适用对象 | 识别规则 | 是否多选 | 父标签 | 启用 | 口径定义 |
|--------|----------|-----------|------|------|--------|----------|----------|----------|--------|------|----------|
| 文件类型 | L1.FILE.TYPE | 文件类型 | L1 | FILE | ENUM | FILE | 文件扩展名+Magic Number 字典匹配 | 否 | - | 是 | 文件类型的统称,父标签 |
| 文件类型 | L1.FILE.TYPE.PDF | PDF文档 | L1 | FILE | ENUM | FILE | 扩展名 `\.pdf$` 或 Magic Number `25 50 44 46` | 否 | L1.FILE.TYPE | 是 | Adobe PDF 格式文档 |
| 文件类型 | L1.FILE.TYPE.DOCX | Word文档 | L1 | FILE | ENUM | FILE | 扩展名 `\.docx$` 或 PK ZIP 头 `50 4B 03 04`+word 目录 | 否 | L1.FILE.TYPE | 是 | Microsoft Word 2007+ 文档 |
| 文件类型 | L1.FILE.TYPE.XLSX | Excel表格 | L1 | FILE | ENUM | FILE | 扩展名 `\.xlsx$` 或 PK 头+xl 目录 | 否 | L1.FILE.TYPE | 是 | Microsoft Excel 2007+ 表格 |
| 文件类型 | L1.FILE.TYPE.PPTX | PPT演示 | L1 | FILE | ENUM | FILE | 扩展名 `\.pptx$` 或 PK 头+ppt 目录 | 否 | L1.FILE.TYPE | 是 | Microsoft PowerPoint 2007+ 演示文稿 |
| 文件类型 | L1.FILE.TYPE.EML | 邮件文件 | L1 | FILE | ENUM | FILE | 扩展名 `\.eml$` 或内容含 `From:`/`Received:` 头 | 否 | L1.FILE.TYPE | 是 | RFC 822/2822 邮件格式 |
| 文件类型 | L1.FILE.TYPE.EXE | 可执行文件 | L1 | FILE | ENUM | FILE | 扩展名 `\.exe$` 或 PE 头 `4D 5A` | 否 | L1.FILE.TYPE | 是 | Windows PE 可执行文件 |
| 文件类型 | L1.FILE.TYPE.DLL | 动态链接库 | L1 | FILE | ENUM | FILE | 扩展名 `\.dll$` 或 PE 头+特征字段 | 否 | L1.FILE.TYPE | 是 | Windows 动态链接库 |
| 文件类型 | L1.FILE.TYPE.PCAP | 网络抓包 | L1 | FILE | ENUM | FILE | 扩展名 `\.pcap$`/`\.pcapng$` 或 Magic `D4 C3 B2 A1`/`0A 0D 0D 0A` | 否 | L1.FILE.TYPE | 是 | libpcap/pcapng 网络抓包文件 |
| 文件类型 | L1.FILE.TYPE.ZIP | ZIP压缩包 | L1 | FILE | ENUM | FILE | 扩展名 `\.zip$` 或 Magic `50 4B 03 04` | 否 | L1.FILE.TYPE | 是 | ZIP 压缩包 |
| 文件类型 | L1.FILE.TYPE.RAR | RAR压缩包 | L1 | FILE | ENUM | FILE | 扩展名 `\.rar$` 或 Magic `52 61 72 21` | 否 | L1.FILE.TYPE | 是 | RAR 压缩包 |
| 文件类型 | L1.FILE.TYPE.PNG | PNG图片 | L1 | FILE | ENUM | FILE | 扩展名 `\.png$` 或 Magic `89 50 4E 47` | 否 | L1.FILE.TYPE | 是 | PNG 图片 |
| 文件类型 | L1.FILE.TYPE.JPG | JPG图片 | L1 | FILE | ENUM | FILE | 扩展名 `\.jpe?g$` 或 Magic `FF D8 FF` | 否 | L1.FILE.TYPE | 是 | JPEG 图片 |
| 文件类型 | L1.FILE.TYPE.LOG | 日志文件 | L1 | FILE | ENUM | FILE | 扩展名 `\.log$` 或内容以时间戳开头多行 | 否 | L1.FILE.TYPE | 是 | 系统或应用日志 |
| 文件类型 | L1.FILE.TYPE.PY | Python脚本 | L1 | FILE | ENUM | FILE | 扩展名 `\.py$` 或首行 `#!/usr/bin/python` | 否 | L1.FILE.TYPE | 是 | Python 脚本 |
| 文件类型 | L1.FILE.TYPE.BIN | 二进制文件 | L1 | FILE | ENUM | FILE | 无可识别文本编码或扩展名 `\.bin$` | 否 | L1.FILE.TYPE | 是 | 未知二进制文件 |
| 文件类型 | L1.FILE.TYPE.TXT | 文本文件 | L1 | FILE | ENUM | FILE | 扩展名 `\.txt$` 且全文本可读 | 否 | L1.FILE.TYPE | 是 | 纯文本文件 |
| 文件类型 | L1.FILE.TYPE.HTML | HTML页面 | L1 | FILE | ENUM | FILE | 扩展名 `\.html?$` 或含 `<html`/`<!DOCTYPE` | 否 | L1.FILE.TYPE | 是 | HTML 网页文件 |
| 文件类型 | L1.FILE.TYPE.PHP | PHP脚本 | L1 | FILE | ENUM | FILE | 扩展名 `\.php$` 或含 `<?php` 标记 | 否 | L1.FILE.TYPE | 是 | PHP 脚本文件(v1.2 新增:支撑 Webshell 识别) |
| 文件类型 | L1.FILE.TYPE.ELF | ELF可执行 | L1 | FILE | ENUM | FILE | Magic `7F 45 4C 46` | 否 | L1.FILE.TYPE | 是 | Linux ELF 可执行文件(v1.2 新增) |
| 文件类型 | L1.FILE.TYPE.MACHO | Mach-O | L1 | FILE | ENUM | FILE | Magic `CF FA ED FE`/`FE ED FA CE` | 否 | L1.FILE.TYPE | 是 | macOS Mach-O 可执行文件(v1.2 新增) |
| 文件类型 | L1.FILE.TYPE.JAR | JAR包 | L1 | FILE | ENUM | FILE | PK 头+`META-INF/MANIFEST.MF` | 否 | L1.FILE.TYPE | 是 | Java 归档包(v1.2 新增) |
| 文件类型 | L1.FILE.TYPE.APK | APK应用 | L1 | FILE | ENUM | FILE | PK 头+`AndroidManifest.xml` | 否 | L1.FILE.TYPE | 是 | Android 应用包(v1.2 新增) |
| 文件类型 | L1.FILE.TYPE.GZIP | GZIP压缩 | L1 | FILE | ENUM | FILE | Magic `1F 8B` | 否 | L1.FILE.TYPE | 是 | GZIP 压缩包(v1.2 新增) |
| 文件类型 | L1.FILE.TYPE.BZIP2 | BZIP2压缩 | L1 | FILE | ENUM | FILE | Magic `42 5A 68` | 否 | L1.FILE.TYPE | 是 | BZIP2 压缩包(v1.2 新增) |
| 文件类型 | L1.FILE.TYPE.XZ | XZ压缩 | L1 | FILE | ENUM | FILE | Magic `FD 37 7A 5A 58` | 否 | L1.FILE.TYPE | 是 | XZ 压缩包(v1.2 新增) |
| 文件类型 | L1.FILE.TYPE.DOC | Word旧版 | L1 | FILE | ENUM | FILE | Magic `D0 CF 11 E0`(OLE2 复合文档) | 否 | L1.FILE.TYPE | 是 | Word 97-2003 文档(v1.2 新增) |
| 文件类型 | L1.FILE.TYPE.XLS | Excel旧版 | L1 | FILE | ENUM | FILE | Magic `D0 CF 11 E0`+Excel 流标识 | 否 | L1.FILE.TYPE | 是 | Excel 97-2003 表格(v1.2 新增) |
| 来源类型 | L1.FILE.SOURCE | 来源类型 | L1 | FILE | ENUM | FILE | 上传接口字段 source 判断 | 是 | - | 是 | 文件获取渠道,父标签 |
| 来源类型 | L1.FILE.SOURCE.UPLOAD | 用户上传 | L1 | FILE | ENUM | FILE | source=upload | 否 | L1.FILE.SOURCE | 是 | 用户主动通过平台上传 |
| 来源类型 | L1.FILE.SOURCE.CRAWL | 网络爬取 | L1 | FILE | ENUM | FILE | source=crawl | 否 | L1.FILE.SOURCE | 是 | 通过爬虫自动采集 |
| 来源类型 | L1.FILE.SOURCE.IMPORT | 批量导入 | L1 | FILE | ENUM | FILE | source=import | 否 | L1.FILE.SOURCE | 是 | 从外部系统批量导入 |
| 来源类型 | L1.FILE.SOURCE.SHARE | 共享转入 | L1 | FILE | ENUM | FILE | source=share | 否 | L1.FILE.SOURCE | 是 | 跨团队/跨空间共享转入 |
| 来源类型 | L1.FILE.SOURCE.COLLECT | 任务采集 | L1 | FILE | ENUM | FILE | source=collect | 否 | L1.FILE.SOURCE | 是 | 红方任务执行过程中自动采集 |
| 语言 | L1.FILE.LANG | 语言 | L1 | FILE | ENUM | FILE | 语言识别模型(基于 Unicode 字符分布) | 否 | - | 是 | 文件内容主语言,父标签 |
| 语言 | L1.FILE.LANG.ZH | 中文 | L1 | FILE | ENUM | FILE | CJK 字符占比 ≥ 60% | 否 | L1.FILE.LANG | 是 | 内容以中文为主 |
| 语言 | L1.FILE.LANG.EN | 英文 | L1 | FILE | ENUM | FILE | ASCII 字母占比 ≥ 70% | 否 | L1.FILE.LANG | 是 | 内容以英文为主 |
| 语言 | L1.FILE.LANG.JA | 日文 | L1 | FILE | ENUM | FILE | 平假名/片假名占比 ≥ 20% | 否 | L1.FILE.LANG | 是 | 内容以日文为主 |
| 语言 | L1.FILE.LANG.RU | 俄文 | L1 | FILE | ENUM | FILE | 西里尔字符占比 ≥ 50% | 否 | L1.FILE.LANG | 是 | 内容以俄文为主 |
| 语言 | L1.FILE.LANG.MULTI | 多语言 | L1 | FILE | ENUM | FILE | 多种语言占比均 > 20% | 否 | L1.FILE.LANG | 是 | 多语言混合,无明显主导 |
| 语言 | L1.FILE.LANG.UNKNOWN | 未知 | L1 | FILE | ENUM | FILE | 二进制文件或无法识别 | 否 | L1.FILE.LANG | 是 | 无法识别语言 |
| 大小分级 | L1.FILE.SIZE | 大小分级 | L1 | FILE | ENUM | FILE | 文件大小阈值判断 | 否 | - | 是 | 按文件字节数分级,父标签 |
| 大小分级 | L1.FILE.SIZE.TINY | 极小 | L1 | FILE | ENUM | FILE | size < 1024 字节 | 否 | L1.FILE.SIZE | 是 | 小于 1KB |
| 大小分级 | L1.FILE.SIZE.SMALL | 小 | L1 | FILE | ENUM | FILE | 1024 ≤ size < 1048576 | 否 | L1.FILE.SIZE | 是 | 1KB ~ 1MB |
| 大小分级 | L1.FILE.SIZE.MEDIUM | 中 | L1 | FILE | ENUM | FILE | 1048576 ≤ size < 104857600 | 否 | L1.FILE.SIZE | 是 | 1MB ~ 100MB |
| 大小分级 | L1.FILE.SIZE.LARGE | 大 | L1 | FILE | ENUM | FILE | 104857600 ≤ size < 1073741824 | 否 | L1.FILE.SIZE | 是 | 100MB ~ 1GB |
| 大小分级 | L1.FILE.SIZE.HUGE | 极大 | L1 | FILE | ENUM | FILE | size ≥ 1073741824 | 否 | L1.FILE.SIZE | 是 | 大于 1GB |
| 格式族 | L1.FILE.FORMAT | 格式族 | L1 | FILE | ENUM | FILE | 由文件类型映射 | 否 | - | 是 | 文件格式大类,父标签 |
| 格式族 | L1.FILE.FORMAT.DOCUMENT | 文档 | L1 | FILE | ENUM | FILE | TYPE ∈ {PDF,DOCX,XLSX,PPTX,EML,TXT,HTML,DOC,XLS} | 否 | L1.FILE.FORMAT | 是 | 文档类格式 |
| 格式族 | L1.FILE.FORMAT.EXECUTABLE | 可执行 | L1 | FILE | ENUM | FILE | TYPE ∈ {EXE,DLL,ELF,MACHO,APK} | 否 | L1.FILE.FORMAT | 是 | 可执行代码 |
| 格式族 | L1.FILE.FORMAT.ARCHIVE | 压缩包 | L1 | FILE | ENUM | FILE | TYPE ∈ {ZIP,RAR,GZIP,BZIP2,XZ,JAR} | 否 | L1.FILE.FORMAT | 是 | 压缩归档 |
| 格式族 | L1.FILE.FORMAT.IMAGE | 图片 | L1 | FILE | ENUM | FILE | TYPE ∈ {PNG,JPG} | 否 | L1.FILE.FORMAT | 是 | 图像文件 |
| 格式族 | L1.FILE.FORMAT.CAPTURE | 抓包 | L1 | FILE | ENUM | FILE | TYPE ∈ {PCAP} | 否 | L1.FILE.FORMAT | 是 | 网络抓包 |
| 格式族 | L1.FILE.FORMAT.LOG | 日志 | L1 | FILE | ENUM | FILE | TYPE ∈ {LOG} | 否 | L1.FILE.FORMAT | 是 | 日志文件 |
| 格式族 | L1.FILE.FORMAT.CODE | 代码 | L1 | FILE | ENUM | FILE | TYPE ∈ {PY,PHP} | 否 | L1.FILE.FORMAT | 是 | 脚本代码 |
| 格式族 | L1.FILE.FORMAT.BINARY | 二进制 | L1 | FILE | ENUM | FILE | TYPE ∈ {BIN} | 否 | L1.FILE.FORMAT | 是 | 未知二进制 |
| 编码方式 | L1.FILE.ENCODING | 编码方式 | L1 | FILE | ENUM | FILE | 文件头字节序检测(chardet/uchardet) | 否 | - | 是 | 文件内容字节编码,父标签 |
| 编码方式 | L1.FILE.ENCODING.UTF8 | UTF-8 | L1 | FILE | ENUM | FILE | BOM `EF BB BF` 或无 BOM 且 UTF-8 合法 | 否 | L1.FILE.ENCODING | 是 | UTF-8 编码 |
| 编码方式 | L1.FILE.ENCODING.GBK | GBK | L1 | FILE | ENUM | FILE | chardet 检测为 GBK/GB2312 | 否 | L1.FILE.ENCODING | 是 | GBK 中文编码 |
| 编码方式 | L1.FILE.ENCODING.UTF16 | UTF-16 | L1 | FILE | ENUM | FILE | BOM `FF FE` 或 `FE FF` | 否 | L1.FILE.ENCODING | 是 | UTF-16 编码 |
| 编码方式 | L1.FILE.ENCODING.ASCII | ASCII | L1 | FILE | ENUM | FILE | 全部字节 < 0x80 | 否 | L1.FILE.ENCODING | 是 | 纯 ASCII 编码 |
| 编码方式 | L1.FILE.ENCODING.BASE64 | Base64 | L1 | FILE | ENUM | FILE | 正则 `^[A-Za-z0-9+/=\s]{64,}$` 命中率 ≥ 80% | 否 | L1.FILE.ENCODING | 是 | Base64 编码内容 |
| 编码方式 | L1.FILE.ENCODING.BINARY | 二进制 | L1 | FILE | ENUM | FILE | 含大量非文本字节(占比 ≥ 30%) | 否 | L1.FILE.ENCODING | 是 | 非文本二进制 |
| 文件类型 | L1.FILE.TYPE.PS1 | PowerShell脚本 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.ps1$` | 否 | L1.FILE.TYPE | 是 | PowerShell脚本文件,红方投递与横向核心载体 |
| 文件类型 | L1.FILE.TYPE.BAT | 批处理脚本 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.bat$`/`\.cmd$` | 否 | L1.FILE.TYPE | 是 | Windows批处理脚本,红方投递与持久化载体 |
| 文件类型 | L1.FILE.TYPE.SH | Shell脚本 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.sh$` 或首行 `#!/bin/(ba)?sh` | 否 | L1.FILE.TYPE | 是 | Linux Shell脚本,红方Linux横向载体 |
| 文件类型 | L1.FILE.TYPE.VBS | VBScript | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.vbs$` | 否 | L1.FILE.TYPE | 是 | VBScript脚本,红方旧版Windows投递载体 |
| 文件类型 | L1.FILE.TYPE.CONF | 配置文件 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.conf$`/`\.cfg$` 或内容含 `server {`/`Port`/`Password` | 否 | L1.FILE.TYPE | 是 | 服务配置文件(nginx/sshd/redis等),高价值信息载体 |
| 文件类型 | L1.FILE.TYPE.INI | INI配置 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.ini$` 或内容含 `[section]`+`key=value` | 否 | L1.FILE.TYPE | 是 | INI格式配置文件,Windows/应用配置 |
| 文件类型 | L1.FILE.TYPE.YAML | YAML配置 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.ya?ml$` 或内容含 `---`/`key:` | 否 | L1.FILE.TYPE | 是 | YAML格式配置文件,K8s/Docker/Ansible配置 |
| 文件类型 | L1.FILE.TYPE.JSON | JSON配置 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.json$` 且可JSON解析 | 否 | L1.FILE.TYPE | 是 | JSON格式配置文件,API配置/凭据文件 |
| 文件类型 | L1.FILE.TYPE.XML | XML配置 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.xml$` 或内容含 `<?xml` | 否 | L1.FILE.TYPE | 是 | XML格式配置文件,web.config/pom.xml等 |
| 文件类型 | L1.FILE.TYPE.RAW | 内存镜像 | L1 | FILE | ENUM | FILE | Magic识别或扩展名 `\.raw$`/`\.mem$`/`\.vmem$` | 否 | L1.FILE.TYPE | 是 | 内存镜像文件,红方volatility分析对象 |
| 文件类型 | L1.FILE.TYPE.DMP | 内存转储 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.dmp$` 或 Magic `MDMP` | 否 | L1.FILE.TYPE | 是 | 内存转储文件,LSASS dump等关键证据 |
| 文件类型 | L1.FILE.TYPE.DD | 磁盘镜像 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.dd$`/`\.img$` | 否 | L1.FILE.TYPE | 是 | 磁盘镜像文件,红方磁盘取证 |
| 文件类型 | L1.FILE.TYPE.E01 | E01取证镜像 | L1 | FILE | ENUM | FILE | Magic `45 56 46 0D` 识别 | 否 | L1.FILE.TYPE | 是 | E01格式取证镜像,红方取证标准格式 |
| 文件类型 | L1.FILE.TYPE.SQL | SQL脚本 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.sql$` 或内容含 `CREATE TABLE`/`INSERT INTO` | 否 | L1.FILE.TYPE | 是 | SQL脚本文件,红方数据库dump外带 |
| 文件类型 | L1.FILE.TYPE.RDP | RDP配置文件 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.rdp$` 或内容含 `full address:s:` | 否 | L1.FILE.TYPE | 是 | RDP连接配置文件,红方RDP凭证载体 |
| 文件类型 | L1.FILE.TYPE.PPK | PuTTY会话 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.ppk$` 或内容含 `PuTTY-User-Key-File` | 否 | L1.FILE.TYPE | 是 | PuTTY密钥文件,红方SSH凭证载体 |
| 文件类型 | L1.FILE.TYPE.KDBX | KeePass数据库 | L1 | FILE | ENUM | FILE | Magic `03 D9 A2 9A` 或扩展名 `\.kdbx$` | 否 | L1.FILE.TYPE | 是 | KeePass密码库文件,红方密码管理载体 |
| 文件类型 | L1.FILE.TYPE.VPN | VPN配置 | L1 | FILE | ENUM | FILE | 内容含 `-----BEGIN OPENVPN`/`[Interface]`/`conn` | 否 | L1.FILE.TYPE | 是 | VPN配置文件,红方内网接入凭证 |
| 文件类型 | L1.FILE.TYPE.JSP | JSP脚本 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.jsp$`/`\.jspx$` | 否 | L1.FILE.TYPE | 是 | JSP脚本文件,红方Webshell载体 |
| 文件类型 | L1.FILE.TYPE.ASPX | ASPX脚本 | L1 | FILE | ENUM | FILE | 正则匹配文件扩展名 `\.aspx$`/`\.asp$` | 否 | L1.FILE.TYPE | 是 | ASPX脚本文件,红方Webshell载体 |
| 文件类型 | L1.FILE.TYPE.7Z | 7z压缩包 | L1 | FILE | ENUM | FILE | Magic `37 7A BC AF 27 1C` 识别 | 否 | L1.FILE.TYPE | 是 | 7z压缩包,红方投递压缩载体 |
| 文件类型 | L1.FILE.TYPE.DUMP | 数据库dump | L1 | FILE | ENUM | FILE | 扩展名 `\.dump$`/`\.bak$` 或内容含数据库导出特征 | 否 | L1.FILE.TYPE | 是 | 数据库导出文件,红方数据外带 |
| 格式族 | L1.FILE.FORMAT.FORENSIC | 取证镜像 | L1 | FILE | ENUM | FILE | 文件类型属于RAW/DMP/DD/E01 | 否 | L1.FILE.FORMAT | 是 | 取证镜像格式族,内存/磁盘镜像归类 |
| 格式族 | L1.FILE.FORMAT.CREDENTIAL | 凭证载体 | L1 | FILE | ENUM | FILE | 文件类型属于RDP/PPK/KDBX/VPN | 否 | L1.FILE.FORMAT | 是 | 凭证载体格式族,凭证文件归类 |
| 格式族 | L1.FILE.FORMAT.CONFIG | 配置文件 | L1 | FILE | ENUM | FILE | 文件类型属于CONF/INI/YAML/JSON/XML | 否 | L1.FILE.FORMAT | 是 | 配置文件格式族,配置文件归类 |

---

## 第3章 L2 业务流程层标签字典

> L2 业务流程层共包含 8 个标签组:**上传来源**(L2.UPLOAD.SOURCE,5 个子标签)、**上传方式**(L2.UPLOAD.MODE,5 个子标签)、**去重状态**(L2.UPLOAD.DEDUP,3 个子标签)、**解析能力**(L2.PARSE.ABILITY,4 个子标签)、**解析状态**(L2.PARSE.STATUS,5 个子标签)、**分析类型**(L2.ANALYZE.TYPE,6 个子标签)、**分析结论**(L2.ANALYZE.RESULT,5 个子标签)、**画像覆盖**(L2.PROFILE.COVERAGE,4 个子标签)。该层职责是标记文件在业务链路中的阶段与处理结果,反映系统对文件的处理状态。下表为该层完整标签字典(共 8 个父标签 + 37 个子标签 = 45 行)。

| 标签组 | 标签编码 | 标签中文名 | 层级 | 分类 | 值类型 | 适用对象 | 识别规则 | 是否多选 | 父标签 | 启用 | 口径定义 |
|--------|----------|-----------|------|------|--------|----------|----------|----------|--------|------|----------|
| 上传来源 | L2.UPLOAD.SOURCE | 上传来源 | L2 | UPLOAD | ENUM | FILE | 上传请求入口 channel 字段 | 否 | - | 是 | 上传渠道,父标签 |
| 上传来源 | L2.UPLOAD.SOURCE.WEB | Web端 | L2 | UPLOAD | ENUM | FILE | channel=web | 否 | L2.UPLOAD.SOURCE | 是 | 通过 Web 浏览器上传 |
| 上传来源 | L2.UPLOAD.SOURCE.CLIENT | 客户端 | L2 | UPLOAD | ENUM | FILE | channel=client | 否 | L2.UPLOAD.SOURCE | 是 | 通过桌面客户端上传 |
| 上传来源 | L2.UPLOAD.SOURCE.API | API接口 | L2 | UPLOAD | ENUM | FILE | channel=api | 否 | L2.UPLOAD.SOURCE | 是 | 通过 OpenAPI 上传 |
| 上传来源 | L2.UPLOAD.SOURCE.MAIL | 邮件入库 | L2 | UPLOAD | ENUM | FILE | channel=mail | 否 | L2.UPLOAD.SOURCE | 是 | 邮件附件自动入库 |
| 上传来源 | L2.UPLOAD.SOURCE.BATCH | 批量导入 | L2 | UPLOAD | ENUM | FILE | channel=batch | 否 | L2.UPLOAD.SOURCE | 是 | 批量任务导入 |
| 上传方式 | L2.UPLOAD.MODE | 上传方式 | L2 | UPLOAD | ENUM | FILE | 上传接口 mode 字段 | 否 | - | 是 | 上传技术方式,父标签 |
| 上传方式 | L2.UPLOAD.MODE.SINGLE | 单文件 | L2 | UPLOAD | ENUM | FILE | mode=single | 否 | L2.UPLOAD.MODE | 是 | 单个文件上传 |
| 上传方式 | L2.UPLOAD.MODE.BATCH | 批量 | L2 | UPLOAD | ENUM | FILE | mode=batch | 否 | L2.UPLOAD.MODE | 是 | 多文件批量上传 |
| 上传方式 | L2.UPLOAD.MODE.RESUMABLE | 断点续传 | L2 | UPLOAD | ENUM | FILE | mode=resumable | 否 | L2.UPLOAD.MODE | 是 | 分片断点续传 |
| 上传方式 | L2.UPLOAD.MODE.INSTANT | 秒传 | L2 | UPLOAD | ENUM | FILE | mode=instant 且命中 file_hash_index | 否 | L2.UPLOAD.MODE | 是 | 哈希命中秒传 |
| 上传方式 | L2.UPLOAD.MODE.DRAG | 拖拽 | L2 | UPLOAD | ENUM | FILE | mode=drag | 否 | L2.UPLOAD.MODE | 是 | 拖拽上传 |
| 去重状态 | L2.UPLOAD.DEDUP | 去重状态 | L2 | UPLOAD | ENUM | FILE | 上传时查 file_hash_index 结果 | 否 | - | 是 | 上传去重判定结果,父标签 |
| 去重状态 | L2.UPLOAD.DEDUP.UNIQUE | 唯一 | L2 | UPLOAD | ENUM | FILE | sha256 未命中既有哈希 | 否 | L2.UPLOAD.DEDUP | 是 | 新文件,平台首次入库 |
| 去重状态 | L2.UPLOAD.DEDUP.DUPLICATE | 重复 | L2 | UPLOAD | ENUM | FILE | sha256 命中且非秒传通道 | 否 | L2.UPLOAD.DEDUP | 是 | 重复文件,引用计数+1 |
| 去重状态 | L2.UPLOAD.DEDUP.INSTANT_HIT | 秒传命中 | L2 | UPLOAD | ENUM | FILE | sha256 命中且 mode=instant | 否 | L2.UPLOAD.DEDUP | 是 | 秒传成功,不重复落盘 |
| 解析能力 | L2.PARSE.ABILITY | 解析能力 | L2 | PARSE | ENUM | FILE | 文件类型→解析器支持矩阵查询 | 否 | - | 是 | 文件是否可被解析器处理,父标签 |
| 解析能力 | L2.PARSE.ABILITY.PARSEABLE | 可解析 | L2 | PARSE | ENUM | FILE | 文件类型在解析器支持列表 | 否 | L2.PARSE.ABILITY | 是 | 平台支持完整解析 |
| 解析能力 | L2.PARSE.ABILITY.UNPARSEABLE | 不可解析 | L2 | PARSE | ENUM | FILE | 文件类型无对应解析器 | 否 | L2.PARSE.ABILITY | 是 | 平台无对应解析器 |
| 解析能力 | L2.PARSE.ABILITY.PARTIAL | 部分解析 | L2 | PARSE | ENUM | FILE | 仅可解析部分内容(如 ZIP 内部分文件) | 否 | L2.PARSE.ABILITY | 是 | 仅能解析部分内容 |
| 解析能力 | L2.PARSE.ABILITY.NEED_PASSWORD | 需密码 | L2 | PARSE | ENUM | FILE | 文件被加密(如加密 ZIP/PDF) | 否 | L2.PARSE.ABILITY | 是 | 需提供密码方可解析 |
| 解析状态 | L2.PARSE.STATUS | 解析状态 | L2 | PARSE | ENUM | FILE | parse_results 表 parse_status 字段 | 否 | - | 是 | 解析任务当前状态,父标签 |
| 解析状态 | L2.PARSE.STATUS.PENDING | 待解析 | L2 | PARSE | ENUM | FILE | status=pending | 否 | L2.PARSE.STATUS | 是 | 已入队未开始 |
| 解析状态 | L2.PARSE.STATUS.PARSING | 解析中 | L2 | PARSE | ENUM | FILE | status=running | 否 | L2.PARSE.STATUS | 是 | 正在解析 |
| 解析状态 | L2.PARSE.STATUS.SUCCESS | 解析成功 | L2 | PARSE | ENUM | FILE | status=success | 否 | L2.PARSE.STATUS | 是 | 解析完成无错误 |
| 解析状态 | L2.PARSE.STATUS.FAILED | 解析失败 | L2 | PARSE | ENUM | FILE | status=failed | 否 | L2.PARSE.STATUS | 是 | 解析异常退出 |
| 解析状态 | L2.PARSE.STATUS.SKIPPED | 已跳过 | L2 | PARSE | ENUM | FILE | status=skipped | 否 | L2.PARSE.STATUS | 是 | 主动跳过(如不可解析) |
| 分析类型 | L2.ANALYZE.TYPE | 分析类型 | L2 | ANALYZE | ENUM | FILE | analysis_results 表 analysis_type 字段 | 是 | - | 是 | 智能分析任务类型,父标签 |
| 分析类型 | L2.ANALYZE.TYPE.MALWARE | 恶意代码分析 | L2 | ANALYZE | ENUM | FILE | type=malware | 否 | L2.ANALYZE.TYPE | 是 | 恶意代码检测与分析 |
| 分析类型 | L2.ANALYZE.TYPE.BEHAVIOR | 行为分析 | L2 | ANALYZE | ENUM | FILE | type=behavior | 否 | L2.ANALYZE.TYPE | 是 | 动态行为特征分析 |
| 分析类型 | L2.ANALYZE.TYPE.VULN | 漏洞提取 | L2 | ANALYZE | ENUM | FILE | type=vuln_extract | 否 | L2.ANALYZE.TYPE | 是 | 漏洞信息提取 |
| 分析类型 | L2.ANALYZE.TYPE.IOC | IOC提取 | L2 | ANALYZE | ENUM | FILE | type=ioc_extract | 否 | L2.ANALYZE.TYPE | 是 | 失陷指标提取 |
| 分析类型 | L2.ANALYZE.TYPE.STATIC | 静态分析 | L2 | ANALYZE | ENUM | FILE | type=static | 否 | L2.ANALYZE.TYPE | 是 | 静态特征分析(反汇编/反编译) |
| 分析类型 | L2.ANALYZE.TYPE.DYNAMIC | 动态分析 | L2 | ANALYZE | ENUM | FILE | type=dynamic | 否 | L2.ANALYZE.TYPE | 是 | 沙箱动态执行分析 |
| 分析结论 | L2.ANALYZE.RESULT | 分析结论 | L2 | ANALYZE | ENUM | FILE | 分析任务结论字段 | 否 | - | 是 | 文件综合判定结论,父标签 |
| 分析结论 | L2.ANALYZE.RESULT.BENIGN | 良性 | L2 | ANALYZE | ENUM | FILE | score < 0.3 | 否 | L2.ANALYZE.RESULT | 是 | 判定为良性文件 |
| 分析结论 | L2.ANALYZE.RESULT.SUSPICIOUS | 可疑 | L2 | ANALYZE | ENUM | FILE | 0.3 ≤ score < 0.5 | 否 | L2.ANALYZE.RESULT | 是 | 存在可疑特征,评分区间与 REVIEW 互斥 |
| 分析结论 | L2.ANALYZE.RESULT.REVIEW | 需人工复核 | L2 | ANALYZE | ENUM | FILE | 0.5 ≤ score < 0.7 或规则标记 | 否 | L2.ANALYZE.RESULT | 是 | 需人工二次确认,评分区间与 SUSPICIOUS 互斥 |
| 分析结论 | L2.ANALYZE.RESULT.MALICIOUS | 恶意 | L2 | ANALYZE | ENUM | FILE | score ≥ 0.7 | 否 | L2.ANALYZE.RESULT | 是 | 判定为恶意文件 |
| 分析结论 | L2.ANALYZE.RESULT.UNKNOWN | 未知 | L2 | ANALYZE | ENUM | FILE | 分析未产出结论 | 否 | L2.ANALYZE.RESULT | 是 | 暂无结论 |
| 画像覆盖 | L2.PROFILE.COVERAGE | 画像覆盖 | L2 | PROFILE | ENUM | FILE | 文件→目标画像字段映射率计算 | 否 | - | 是 | 文件对目标画像的贡献覆盖,父标签 |
| 画像覆盖 | L2.PROFILE.COVERAGE.FULL | 已覆盖 | L2 | PROFILE | ENUM | FILE | 覆盖率 ≥ 80% | 否 | L2.PROFILE.COVERAGE | 是 | 高度覆盖目标画像字段 |
| 画像覆盖 | L2.PROFILE.COVERAGE.NONE | 未覆盖 | L2 | PROFILE | ENUM | FILE | 覆盖率 = 0% | 否 | L2.PROFILE.COVERAGE | 是 | 未对画像产生贡献 |
| 画像覆盖 | L2.PROFILE.COVERAGE.PARTIAL | 部分覆盖 | L2 | PROFILE | ENUM | FILE | 0% < 覆盖率 < 80% | 否 | L2.PROFILE.COVERAGE | 是 | 部分字段贡献 |
| 画像覆盖 | L2.PROFILE.COVERAGE.EXPIRED | 已过期 | L2 | PROFILE | ENUM | FILE | 上次贡献时间 > 90 天 | 否 | L2.PROFILE.COVERAGE | 是 | 画像数据已过期需刷新 |

---

## 第4章 L3 实体识别层标签字典

> **ENTITY 与 IOC 关系说明**:ENTITY.* 标签组按实体类型(IP/域名/URL 等)分类,描述"实体是什么";IOC(失陷指标)是横切分类,描述"实体是否命中威胁情报"。同一实体可同时携带 ENTITY.IP.C2(实体属性)和 ENTITY.IOC.MAL_IP(情报属性)标签,二者视角不同不构成冗余:ENTITY.* 回答实体类型,IOC 回答威胁状态。

> L3 实体识别层共包含 13 个标签组:**IP**(L3.ENTITY.IP,8 个子标签)、**域名**(L3.ENTITY.DOMAIN,5 个子标签)、**主机**(L3.ENTITY.HOST,8 个子标签)、**用户**(L3.ENTITY.USER,5 个子标签)、**凭证**(L3.ENTITY.CRED,16 个子标签)、**漏洞**(L3.ENTITY.VULN,8 个子标签)、**IOC**(L3.ENTITY.IOC,5 个子标签)、**端口**(L3.ENTITY.PORT,3 个子标签)、**服务**(L3.ENTITY.SERVICE,10 个子标签)、**URL**(L3.ENTITY.URL,4 个子标签)、**邮箱**(L3.ENTITY.EMAIL,3 个子标签)、**注册表键**(L3.ENTITY.REGKEY,8 个子标签)、**AD域**(L3.ENTITY.AD,5 个子标签)。该层职责是从文件内容中提取结构化实体类型,可独立于文件存在。下表为该层完整标签字典(共 13 个父标签 + 88 个子标签 = 101 行)。

| 标签组 | 标签编码 | 标签中文名 | 层级 | 分类 | 值类型 | 适用对象 | 识别规则 | 是否多选 | 父标签 | 启用 | 口径定义 |
|--------|----------|-----------|------|------|--------|----------|----------|----------|--------|------|----------|
| IP | L3.ENTITY.IP | IP地址 | L3 | ENTITY | TEXT | FILE/ENTITY | IPv4/IPv6 正则 | 是 | - | 是 | IP 地址实体,父标签 |
| IP | L3.ENTITY.IP.PUBLIC | 公网IP | L3 | ENTITY | TEXT | FILE/ENTITY | IPv4 且非私网/保留段 | 否 | L3.ENTITY.IP | 是 | 公网可路由 IP |
| IP | L3.ENTITY.IP.PRIVATE | 内网IP | L3 | ENTITY | TEXT | FILE/ENTITY | 命中 10.0.0.0/8、172.16-31.0.0/16、192.168.0.0/16 | 否 | L3.ENTITY.IP | 是 | 私网保留地址段 |
| IP | L3.ENTITY.IP.GATEWAY | 网关IP | L3 | ENTITY | TEXT | FILE/ENTITY | IP 关联设备类型为网关或上下文含 gateway 字样 | 否 | L3.ENTITY.IP | 是 | 网络网关地址 |
| IP | L3.ENTITY.IP.C2 | C2 IP | L3 | ENTITY | TEXT | FILE/ENTITY | IP 命中威胁情报 C2 库 | 否 | L3.ENTITY.IP | 是 | 命令与控制服务器 IP |
| IP | L3.ENTITY.IP.HONEYPOT | 蜜罐IP | L3 | ENTITY | TEXT | FILE/ENTITY | IP 在蜜罐资产列表中 | 否 | L3.ENTITY.IP | 是 | 蜜罐节点 IP |
| 域名 | L3.ENTITY.DOMAIN | 域名 | L3 | ENTITY | TEXT | FILE/ENTITY | 域名正则 | 是 | - | 是 | 域名实体,父标签 |
| 域名 | L3.ENTITY.DOMAIN.ROOT | 根域名 | L3 | ENTITY | TEXT | FILE/ENTITY | 提取 eTLD+1 | 否 | L3.ENTITY.DOMAIN | 是 | 注册顶级根域名 |
| 域名 | L3.ENTITY.DOMAIN.SUB | 子域名 | L3 | ENTITY | TEXT | FILE/ENTITY | 非根域名,有上级标签 | 否 | L3.ENTITY.DOMAIN | 是 | 根域名下的子域名 |
| 域名 | L3.ENTITY.DOMAIN.DGA | DGA域名 | L3 | ENTITY | TEXT | FILE/ENTITY | DGA 检测模型判定 | 否 | L3.ENTITY.DOMAIN | 是 | 算法生成的恶意域名 |
| 域名 | L3.ENTITY.DOMAIN.CDN | CDN域名 | L3 | ENTITY | TEXT | FILE/ENTITY | 命中 CDN 厂商域名库 | 否 | L3.ENTITY.DOMAIN | 是 | CDN 加速域名 |
| 域名 | L3.ENTITY.DOMAIN.MALICIOUS | 恶意域名 | L3 | ENTITY | TEXT | FILE/ENTITY | 命中威胁情报恶意域名库 | 否 | L3.ENTITY.DOMAIN | 是 | 已知恶意域名 |
| 主机 | L3.ENTITY.HOST | 主机 | L3 | ENTITY | TEXT | FILE/ENTITY | 主机名/IP+设备类型识别 | 是 | - | 是 | 主机/设备实体,父标签 |
| 主机 | L3.ENTITY.HOST.SERVER | 服务器 | L3 | ENTITY | TEXT | FILE/ENTITY | 设备类型字典匹配(Linux/Windows Server) | 否 | L3.ENTITY.HOST | 是 | 业务/服务器设备 |
| 主机 | L3.ENTITY.HOST.NETWORK | 网络设备 | L3 | ENTITY | TEXT | FILE/ENTITY | 型号字典(思科/华为/华三等) | 否 | L3.ENTITY.HOST | 是 | 路由器/交换机/防火墙 |
| 主机 | L3.ENTITY.HOST.ENDPOINT | 终端 | L3 | ENTITY | TEXT | FILE/ENTITY | OS 含 Win7/10/11、macOS | 否 | L3.ENTITY.HOST | 是 | 用户终端设备 |
| 主机 | L3.ENTITY.HOST.ICS | 工控设备 | L3 | ENTITY | TEXT | FILE/ENTITY | 型号含 PLC/SCADA/RTU 字样 | 否 | L3.ENTITY.HOST | 是 | 工业控制系统设备 |
| 主机 | L3.ENTITY.HOST.CLOUD | 云主机 | L3 | ENTITY | TEXT | FILE/ENTITY | 命中云厂商 IP 段或 hostname 含 cloud 字样 | 否 | L3.ENTITY.HOST | 是 | 云端虚拟主机 |
| 用户 | L3.ENTITY.USER | 用户 | L3 | ENTITY | TEXT | FILE/ENTITY | 账户名正则+上下文 | 是 | - | 是 | 用户/账户实体,父标签 |
| 用户 | L3.ENTITY.USER.SYSTEM | 系统账户 | L3 | ENTITY | TEXT | FILE/ENTITY | 命中 SYSTEM/root/LocalSystem | 否 | L3.ENTITY.USER | 是 | 操作系统内置系统账户 |
| 用户 | L3.ENTITY.USER.SERVICE | 服务账户 | L3 | ENTITY | TEXT | FILE/ENTITY | 命中 IIS_/MSSQL$/svc 等 | 否 | L3.ENTITY.USER | 是 | 服务运行账户 |
| 用户 | L3.ENTITY.USER.DOMAIN | 域账户 | L3 | ENTITY | TEXT | FILE/ENTITY | 形如 DOMAIN\username 或 user@domain | 否 | L3.ENTITY.USER | 是 | AD 域账户 |
| 用户 | L3.ENTITY.USER.NORMAL | 普通用户 | L3 | ENTITY | TEXT | FILE/ENTITY | 非系统/服务/域账户 | 否 | L3.ENTITY.USER | 是 | 普通自然人账户 |
| 用户 | L3.ENTITY.USER.ADMIN | 管理员 | L3 | ENTITY | TEXT | FILE/ENTITY | 上下文含 administrator/admin/root 字样 | 否 | L3.ENTITY.USER | 是 | 高权限管理员账户 |
| 凭证 | L3.ENTITY.CRED | 凭证 | L3 | ENTITY | TEXT | FILE/ENTITY | 凭证模式模型识别 | 是 | - | 是 | 访问凭证实体,父标签 |
| 凭证 | L3.ENTITY.CRED.PASSWORD | 密码 | L3 | ENTITY | TEXT | FILE/ENTITY | 上下文含 password/pwd/passwd= | 否 | L3.ENTITY.CRED | 是 | 明文或加密密码 |
| 凭证 | L3.ENTITY.CRED.HASH | 哈希 | L3 | ENTITY | TEXT | FILE/ENTITY | NTLM/MD5/SHA 等哈希格式正则 | 否 | L3.ENTITY.CRED | 是 | 凭证哈希(NTLM/SHA 等) |
| 凭证 | L3.ENTITY.CRED.KEY | 密钥 | L3 | ENTITY | TEXT | FILE/ENTITY | 含 `-----BEGIN ... PRIVATE KEY-----` | 否 | L3.ENTITY.CRED | 是 | SSH/RSA 私钥 |
| 凭证 | L3.ENTITY.CRED.CERT | 证书 | L3 | ENTITY | TEXT | FILE/ENTITY | 含 `-----BEGIN CERTIFICATE-----` | 否 | L3.ENTITY.CRED | 是 | X.509 数字证书 |
| 凭证 | L3.ENTITY.CRED.TOKEN | Token | L3 | ENTITY | TEXT | FILE/ENTITY | JWT 格式或 API Token 模式 | 否 | L3.ENTITY.CRED | 是 | JWT/API Token |
| 凭证 | L3.ENTITY.CRED.SESSION | 会话凭证 | L3 | ENTITY | TEXT | FILE/ENTITY | Cookie/Session ID 模式 | 否 | L3.ENTITY.CRED | 是 | 会话标识 |
| 漏洞 | L3.ENTITY.VULN | 漏洞 | L3 | ENTITY | TEXT | FILE/ENTITY | CVE 编号正则+漏洞库匹配 | 是 | - | 是 | 漏洞实体,父标签 |
| 漏洞 | L3.ENTITY.VULN.CVE | CVE漏洞 | L3 | ENTITY | TEXT | FILE/ENTITY | 正则 `CVE-\d{4}-\d{4,7}` | 否 | L3.ENTITY.VULN | 是 | 已公开 CVE 编号漏洞 |
| 漏洞 | L3.ENTITY.VULN.ZERODAY | 0day漏洞 | L3 | ENTITY | TEXT | FILE/ENTITY | 漏洞库未收录但具备漏洞特征 | 否 | L3.ENTITY.VULN | 是 | 未公开 0day |
| 漏洞 | L3.ENTITY.VULN.NDAY | Nday漏洞 | L3 | ENTITY | TEXT | FILE/ENTITY | 已公开但未修复的旧漏洞 | 否 | L3.ENTITY.VULN | 是 | 历史 Nday |
| 漏洞 | L3.ENTITY.VULN.MISCONFIG | 配置缺陷 | L3 | ENTITY | TEXT | FILE/ENTITY | 配置项基线比对 | 否 | L3.ENTITY.VULN | 是 | 安全配置缺陷 |
| 漏洞 | L3.ENTITY.VULN.LOGIC | 逻辑漏洞 | L3 | ENTITY | TEXT | FILE/ENTITY | 业务逻辑分析识别 | 否 | L3.ENTITY.VULN | 是 | 业务逻辑类漏洞 |
| IOC | L3.ENTITY.IOC | IOC指标 | L3 | ENTITY | TEXT | FILE/ENTITY | 命中威胁情报 IOC 库 | 是 | - | 是 | 失陷指标实体,父标签 |
| IOC | L3.ENTITY.IOC.FILE_HASH | 文件哈希 | L3 | ENTITY | TEXT | FILE/ENTITY | MD5/SHA1/SHA256 正则+情报匹配 | 否 | L3.ENTITY.IOC | 是 | 恶意文件哈希 |
| IOC | L3.ENTITY.IOC.MAL_URL | 恶意URL | L3 | ENTITY | TEXT | FILE/ENTITY | URL 命中恶意 URL 库 | 否 | L3.ENTITY.IOC | 是 | 恶意下载/钓鱼 URL |
| IOC | L3.ENTITY.IOC.C2_DOMAIN | C2域名 | L3 | ENTITY | TEXT | FILE/ENTITY | 域名命中 C2 情报 | 否 | L3.ENTITY.IOC | 是 | C2 控制域名 |
| IOC | L3.ENTITY.IOC.MAL_IP | 恶意IP | L3 | ENTITY | TEXT | FILE/ENTITY | IP 命中恶意 IP 库 | 否 | L3.ENTITY.IOC | 是 | 恶意 IP 地址 |
| IOC | L3.ENTITY.IOC.REG_KEY | 注册表键 | L3 | ENTITY | TEXT | FILE/ENTITY | 命中恶意注册表持久化键 | 否 | L3.ENTITY.IOC | 是 | 恶意注册表键 |
| 端口 | L3.ENTITY.PORT | 端口 | L3 | ENTITY | NUMBER | FILE/ENTITY | `:\d{1,5}` 或端口字段 | 是 | - | 是 | 网络端口实体,父标签 |
| 端口 | L3.ENTITY.PORT.WELLKNOWN | 知名端口 | L3 | ENTITY | NUMBER | FILE/ENTITY | 1 ≤ port ≤ 1023 | 否 | L3.ENTITY.PORT | 是 | 0-1023 知名端口 |
| 端口 | L3.ENTITY.PORT.HIGHRISK | 高危端口 | L3 | ENTITY | NUMBER | FILE/ENTITY | 命中高危端口字典(22/3389/445/1433 等) | 否 | L3.ENTITY.PORT | 是 | 高风险服务端口 |
| 端口 | L3.ENTITY.PORT.SERVICE | 服务端口 | L3 | ENTITY | NUMBER | FILE/ENTITY | 1024 ≤ port ≤ 65535 非高危 | 否 | L3.ENTITY.PORT | 是 | 注册/动态端口 |
| 服务 | L3.ENTITY.SERVICE | 服务 | L3 | ENTITY | TEXT | FILE/ENTITY | 服务名字典+端口映射 | 是 | - | 是 | 网络服务实体,父标签 |
| 服务 | L3.ENTITY.SERVICE.WEB | Web服务 | L3 | ENTITY | TEXT | FILE/ENTITY | 端口 80/443/8080 或含 nginx/apache/iis | 否 | L3.ENTITY.SERVICE | 是 | Web 服务 |
| 服务 | L3.ENTITY.SERVICE.DB | 数据库服务 | L3 | ENTITY | TEXT | FILE/ENTITY | 端口 1433/3306/5432/1521/27017 | 否 | L3.ENTITY.SERVICE | 是 | 数据库服务 |
| 服务 | L3.ENTITY.SERVICE.REMOTE | 远程服务 | L3 | ENTITY | TEXT | FILE/ENTITY | 端口 22/3389/5900/23 | 否 | L3.ENTITY.SERVICE | 是 | 远程管理服务 |
| 服务 | L3.ENTITY.SERVICE.FILE | 文件服务 | L3 | ENTITY | TEXT | FILE/ENTITY | 端口 21/445/2049 | 否 | L3.ENTITY.SERVICE | 是 | 文件共享服务 |
| URL | L3.ENTITY.URL | URL | L3 | ENTITY | TEXT | FILE/ENTITY | URL 正则 | 是 | - | 是 | URL 实体,父标签 |
| URL | L3.ENTITY.URL.NORMAL | 普通URL | L3 | ENTITY | TEXT | FILE/ENTITY | URL 且未命中恶意库 | 否 | L3.ENTITY.URL | 是 | 普通访问 URL |
| URL | L3.ENTITY.URL.DOWNLOAD | 下载URL | L3 | ENTITY | TEXT | FILE/ENTITY | URL 路径含 .exe/.zip 等可执行/压缩后缀 | 否 | L3.ENTITY.URL | 是 | 文件下载 URL |
| URL | L3.ENTITY.URL.C2 | C2 URL | L3 | ENTITY | TEXT | FILE/ENTITY | URL 命中 C2 情报 | 否 | L3.ENTITY.URL | 是 | C2 回连 URL |
| URL | L3.ENTITY.URL.PHISHING | 钓鱼URL | L3 | ENTITY | TEXT | FILE/ENTITY | 命中钓鱼 URL 库或仿冒特征 | 否 | L3.ENTITY.URL | 是 | 钓鱼欺诈 URL |
| 邮箱 | L3.ENTITY.EMAIL | 邮箱 | L3 | ENTITY | TEXT | FILE/ENTITY | 邮箱正则 | 是 | - | 是 | 邮箱实体,父标签 |
| 邮箱 | L3.ENTITY.EMAIL.SENDER | 发件邮箱 | L3 | ENTITY | TEXT | FILE/ENTITY | 邮件头 From 字段 | 否 | L3.ENTITY.EMAIL | 是 | 邮件发件人 |
| 邮箱 | L3.ENTITY.EMAIL.RECIPIENT | 收件邮箱 | L3 | ENTITY | TEXT | FILE/ENTITY | 邮件头 To/Cc 字段 | 否 | L3.ENTITY.EMAIL | 是 | 邮件收件人 |
| 邮箱 | L3.ENTITY.EMAIL.PHISHING | 钓鱼邮箱 | L3 | ENTITY | TEXT | FILE/ENTITY | 命中钓鱼发件人库 | 否 | L3.ENTITY.EMAIL | 是 | 钓鱼邮件发件人 |
| 注册表键 | L3.ENTITY.REGKEY | 注册表键 | L3 | ENTITY | TEXT | FILE/ENTITY | 注册表路径正则 | 是 | - | 是 | Windows 注册表键,父标签 |
| 注册表键 | L3.ENTITY.REGKEY.STARTUP | 启动项 | L3 | ENTITY | TEXT | FILE/ENTITY | 路径含 `Run`/`RunOnce`/`StartupApproved` | 否 | L3.ENTITY.REGKEY | 是 | 自启动注册表项 |
| 注册表键 | L3.ENTITY.REGKEY.SERVICE | 服务项 | L3 | ENTITY | TEXT | FILE/ENTITY | 路径含 `Services\` | 否 | L3.ENTITY.REGKEY | 是 | 系统服务注册表项 |
| 注册表键 | L3.ENTITY.REGKEY.SCHEDULE | 计划任务 | L3 | ENTITY | TEXT | FILE/ENTITY | 路径含 `Schedule\TaskCache` | 否 | L3.ENTITY.REGKEY | 是 | 计划任务注册表项 |
| 凭证 | L3.ENTITY.CRED.KERBEROS | Kerberos票据 | L3 | ENTITY | ENUM | FILE/TEXT | 正则匹配Kerberos票据格式(.kirbi或base64 doIF) | 否 | L3.ENTITY.CRED | 是 | Kerberos票据(TGT/TGS),Kerberoasting/PtT作战核心 |
| 凭证 | L3.ENTITY.CRED.NETNTLM | NetNTLM Hash | L3 | ENTITY | STRING | TEXT | 正则匹配 `user::domain:LM:NT:challenge` 格式 | 否 | L3.ENTITY.CRED | 是 | NetNTLMv1/v2哈希,红方中继/破解对象 |
| 凭证 | L3.ENTITY.CRED.LSASS | LSASS转储 | L3 | ENTITY | FILE | FILE | 文件含LSASS特征或为DMP且含LSASS进程 | 否 | L3.ENTITY.CRED | 是 | LSASS内存转储,红方凭据提取核心证据 |
| 凭证 | L3.ENTITY.CRED.ASREP | AS-REP Hash | L3 | ENTITY | STRING | TEXT | 正则匹配 `$krb5asrep$23$` 格式 | 否 | L3.ENTITY.CRED | 是 | AS-REP哈希,AS-REP Roasting产出 |
| 凭证 | L3.ENTITY.CRED.TGS | TGS票据 | L3 | ENTITY | STRING | TEXT | 正则匹配 `$krb5tgs$23$` 格式 | 否 | L3.ENTITY.CRED | 是 | TGS票据哈希,Kerberoasting产出 |
| 凭证 | L3.ENTITY.CRED.RDPCRED | RDP凭据 | L3 | ENTITY | STRING | FILE/TEXT | RDP文件含 `password 51:b:` 或cmdkey凭据 | 否 | L3.ENTITY.CRED | 是 | RDP保存凭证,红方RDP横向凭证 |
| 凭证 | L3.ENTITY.CRED.PPK | PuTTY会话 | L3 | ENTITY | FILE | FILE | 文件含 `PuTTY-User-Key-File-2` 头 | 否 | L3.ENTITY.CRED | 是 | PuTTY密钥会话,红方SSH会话劫持 |
| 凭证 | L3.ENTITY.CRED.BROWSER | 浏览器密码 | L3 | ENTITY | FILE | FILE | Chrome Login Data或Firefox logins.json | 否 | L3.ENTITY.CRED | 是 | 浏览器保存密码,红方凭据harvesting |
| 凭证 | L3.ENTITY.CRED.WIFI | WiFi密码 | L3 | ENTITY | STRING | FILE/TEXT | XML含 `<authentication>`+`<keyMaterial>` | 否 | L3.ENTITY.CRED | 是 | WiFi密码,红方内网接入 |
| 凭证 | L3.ENTITY.CRED.SHADOW | Linux Shadow Hash | L3 | ENTITY | STRING | TEXT | 正则匹配 `$[156y]$` 开头的哈希格式 | 否 | L3.ENTITY.CRED | 是 | Linux shadow文件哈希,红方Linux密码破解 |
| AD域 | L3.ENTITY.AD | AD域信息 | L3 | ENTITY | ENUM | FILE/TEXT | AD域相关信息汇总 | 否 | - | 是 | AD域实体父标签,红方AD域攻击目标识别 |
| AD域 | L3.ENTITY.AD.DC | 域控制器 | L3 | ENTITY | STRING | TEXT | 主机名含DC/$或SPN含ldap或主机角色为DC | 否 | L3.ENTITY.AD | 是 | 域控制器,红方高价值目标 |
| AD域 | L3.ENTITY.AD.SID | 域SID | L3 | ENTITY | STRING | TEXT | 正则匹配 `S-1-5-21-\d+-\d+-\d+` | 否 | L3.ENTITY.AD | 是 | 域安全标识符,红方权限判定 |
| AD域 | L3.ENTITY.AD.SPN | 服务主体名 | L3 | ENTITY | STRING | TEXT | 正则匹配 `service/host` 格式且上下文含SPN | 否 | L3.ENTITY.AD | 是 | SPN,Kerberoasting目标 |
| AD域 | L3.ENTITY.AD.GPO | 组策略对象 | L3 | ENTITY | STRING | TEXT/FILE | 路径含 `\\Policies\` 或GUID格式 | 否 | L3.ENTITY.AD | 是 | GPO,红方权限维持/分发 |
| AD域 | L3.ENTITY.AD.DELEGATION | 委派配置 | L3 | ENTITY | STRING | TEXT | 属性含 `msDS-AllowedToDelegateTo`/`TrustedForDelegation` | 否 | L3.ENTITY.AD | 是 | 委派配置,红方委派攻击 |
| 服务 | L3.ENTITY.SERVICE.WINRM | WinRM服务 | L3 | ENTITY | ENUM | TEXT | 端口5985/5986或含 `wsman`/`winrm` | 否 | L3.ENTITY.SERVICE | 是 | WinRM服务,红方WinRM横向 |
| 服务 | L3.ENTITY.SERVICE.LDAP | LDAP服务 | L3 | ENTITY | ENUM | TEXT | 端口389/636或含 `ldap` | 否 | L3.ENTITY.SERVICE | 是 | LDAP服务,红方AD侦察 |
| 服务 | L3.ENTITY.SERVICE.KERBEROS | Kerberos服务 | L3 | ENTITY | ENUM | TEXT | 端口88或含 `kerberos`/`kdc` | 否 | L3.ENTITY.SERVICE | 是 | Kerberos服务,红方票据攻击 |
| 服务 | L3.ENTITY.SERVICE.DNS | DNS服务 | L3 | ENTITY | ENUM | TEXT | 端口53或含 `dns`/`bind` | 否 | L3.ENTITY.SERVICE | 是 | DNS服务,红方DNS侦察/隧道 |
| 服务 | L3.ENTITY.SERVICE.AD | AD域服务 | L3 | ENTITY | ENUM | TEXT | 含 `ntds.dit`/`SYSVOL`/`NETLOGON` | 否 | L3.ENTITY.SERVICE | 是 | AD域服务,红方AD攻击 |
| 服务 | L3.ENTITY.SERVICE.SNMP | SNMP服务 | L3 | ENTITY | ENUM | TEXT | 端口161/162 | 否 | L3.ENTITY.SERVICE | 是 | SNMP服务,红方设备侦察 |
| 漏洞 | L3.ENTITY.VULN.CWE | CWE编号 | L3 | ENTITY | STRING | TEXT | 正则匹配 `CWE-\d+` | 否 | L3.ENTITY.VULN | 是 | CWE漏洞类型编号 |
| 漏洞 | L3.ENTITY.VULN.CNVD | CNVD编号 | L3 | ENTITY | STRING | TEXT | 正则匹配 `CNVD-\d{4}-\d{4,}` | 否 | L3.ENTITY.VULN | 是 | CNVD国产漏洞编号 |
| 漏洞 | L3.ENTITY.VULN.CNNVD | CNNVD编号 | L3 | ENTITY | STRING | TEXT | 正则匹配 `CNNVD-\d{6}-\d{3,}` | 否 | L3.ENTITY.VULN | 是 | CNNVD国产漏洞编号 |
| 注册表键 | L3.ENTITY.REGKEY.WMI | WMI订阅 | L3 | ENTITY | STRING | TEXT | 路径含 `ROOT\Subscription` 或 `__EventFilter` | 否 | L3.ENTITY.REGKEY | 是 | WMI事件订阅,红方WMI持久化 |
| 注册表键 | L3.ENTITY.REGKEY.COM | COM劫持 | L3 | ENTITY | STRING | TEXT | 路径含 `CLSID`/`InprocServer32` | 否 | L3.ENTITY.REGKEY | 是 | COM劫持,红方COM持久化 |
| 注册表键 | L3.ENTITY.REGKEY.APPINIT | AppInit DLLs | L3 | ENTITY | STRING | TEXT | 路径含 `AppInit_DLLs` | 否 | L3.ENTITY.REGKEY | 是 | AppInit_DLLs,红方DLL注入持久化 |
| 注册表键 | L3.ENTITY.REGKEY.IFEO | 镜像劫持 | L3 | ENTITY | STRING | TEXT | 路径含 `Image File Execution Options` | 否 | L3.ENTITY.REGKEY | 是 | IFEO镜像劫持,红方持久化 |
| 注册表键 | L3.ENTITY.REGKEY.WINLOGON | Winlogon Shell | L3 | ENTITY | STRING | TEXT | 路径含 `Winlogon\Shell`/`Userinit` | 否 | L3.ENTITY.REGKEY | 是 | Winlogon Shell,红方Shell持久化 |
| IP | L3.ENTITY.IP.VPN | VPN节点IP | L3 | ENTITY | IP | TEXT | 命中VPN厂商IP段或上下文含 `vpn`/`openvpn` | 否 | L3.ENTITY.IP | 是 | VPN节点IP,红方VPN接入识别 |
| IP | L3.ENTITY.IP.PROXY | 代理IP | L3 | ENTITY | IP | TEXT | 上下文含 `proxy`/`squid`/`socks` | 否 | L3.ENTITY.IP | 是 | 代理IP,红方代理链路识别 |
| IP | L3.ENTITY.IP.CDN_NODE | CDN节点IP | L3 | ENTITY | IP | TEXT | 命中CDN厂商IP段 | 否 | L3.ENTITY.IP | 是 | CDN节点IP,红方真实IP溯源 |
| 主机 | L3.ENTITY.HOST.DC | 域控制器主机 | L3 | ENTITY | STRING | TEXT | 主机角色识别为域控 | 否 | L3.ENTITY.HOST | 是 | 域控主机,红方高价值目标 |
| 主机 | L3.ENTITY.HOST.WORKGROUP | 工作组主机 | L3 | ENTITY | STRING | TEXT | 上下文含 `workgroup` 且无域 | 否 | L3.ENTITY.HOST | 是 | 工作组主机,红方非域环境判定 |
| 主机 | L3.ENTITY.HOST.DOMAIN_JOINED | 域成员主机 | L3 | ENTITY | STRING | TEXT | 上下文含 `domain`+域名 | 否 | L3.ENTITY.HOST | 是 | 域成员主机,红方域攻击目标 |

---

## 第5章 L4 业务场景层标签字典

> L4 业务场景层共包含 11 个标签组:**文件上传**(L4.SCENE.UPLOAD,3 个子标签)、**文件解析**(L4.SCENE.PARSE,3 个子标签)、**文件智能分析**(L4.SCENE.ANALYZE,3 个子标签)、**目标画像刻画**(L4.SCENE.PROFILE,3 个子标签)、**网络地形还原**(L4.SCENE.TOPOLOGY,8 个子标签)、**访问凭证获取**(L4.SCENE.CREDENTIAL,6 个子标签)、**漏洞战机识别**(L4.SCENE.VULN,8 个子标签)、**横向移动**(L4.SCENE.LATERAL,5 个子标签)、**持久化**(L4.SCENE.PERSIST,2 个子标签)、**防御绕过**(L4.SCENE.EVASION,3 个子标签)、**红队基础设施**(L4.SCENE.INFRA,3 个子标签)。该层职责是关联红方 11 大业务场景,反映文件在红方作战流程中的角色。下表为该层完整标签字典(共 11 个父标签 + 47 个子标签 = 58 行)。

| 标签组 | 标签编码 | 标签中文名 | 层级 | 分类 | 值类型 | 适用对象 | 识别规则 | 是否多选 | 父标签 | 启用 | 口径定义 |
|--------|----------|-----------|------|------|--------|----------|----------|----------|--------|------|----------|
| 文件上传 | L4.SCENE.UPLOAD | 文件上传场景 | L4 | SCENE | BOOL | FILE | 文件入库时触发 | 是 | - | 是 | 文件上传场景标记,父标签 |
| 文件上传 | L4.SCENE.UPLOAD.SOURCE | 上传来源标记 | L4 | SCENE | BOOL | FILE | 由 L2.UPLOAD.SOURCE 推导 | 否 | L4.SCENE.UPLOAD | 是 | 标记文件上传渠道来源 |
| 文件上传 | L4.SCENE.UPLOAD.MODE | 上传方式标记 | L4 | SCENE | BOOL | FILE | 由 L2.UPLOAD.MODE 推导 | 否 | L4.SCENE.UPLOAD | 是 | 标记文件上传技术方式 |
| 文件上传 | L4.SCENE.UPLOAD.DEDUP | 去重状态标记 | L4 | SCENE | BOOL | FILE | 由 L2.UPLOAD.DEDUP 推导 | 否 | L4.SCENE.UPLOAD | 是 | 标记文件去重命中情况 |
| 文件解析 | L4.SCENE.PARSE | 文件解析场景 | L4 | SCENE | BOOL | FILE | 文件解析任务执行 | 是 | - | 是 | 文件解析场景标记,父标签 |
| 文件解析 | L4.SCENE.PARSE.ABILITY | 解析能力 | L4 | SCENE | BOOL | FILE | 由 L2.PARSE.ABILITY 推导 | 否 | L4.SCENE.PARSE | 是 | 标记文件是否可被解析 |
| 文件解析 | L4.SCENE.PARSE.RESULT | 解析结果 | L4 | SCENE | BOOL | FILE | 由 L2.PARSE.STATUS 推导 | 否 | L4.SCENE.PARSE | 是 | 标记解析任务执行结果 |
| 文件解析 | L4.SCENE.PARSE.ENTITY | 提取实体 | L4 | SCENE | BOOL | FILE | 解析产出实体数 > 0 | 否 | L4.SCENE.PARSE | 是 | 标记解析阶段是否提取实体 |
| 文件智能分析 | L4.SCENE.ANALYZE | 智能分析场景 | L4 | SCENE | BOOL | FILE | 智能分析任务执行 | 是 | - | 是 | 智能分析场景标记,父标签 |
| 文件智能分析 | L4.SCENE.ANALYZE.TYPE | 分析类型 | L4 | SCENE | BOOL | FILE | 由 L2.ANALYZE.TYPE 推导 | 否 | L4.SCENE.ANALYZE | 是 | 标记分析任务类型 |
| 文件智能分析 | L4.SCENE.ANALYZE.RESULT | 分析结论 | L4 | SCENE | BOOL | FILE | 由 L2.ANALYZE.RESULT 推导 | 否 | L4.SCENE.ANALYZE | 是 | 标记分析结论 |
| 文件智能分析 | L4.SCENE.ANALYZE.THREAT | 威胁等级 | L4 | SCENE | BOOL | FILE | 由 L5.INTEL.THREAT 推导 | 否 | L4.SCENE.ANALYZE | 是 | 标记分析产出的威胁等级 |
| 目标画像刻画 | L4.SCENE.PROFILE | 目标画像场景 | L4 | SCENE | BOOL | FILE/TARGET | 文件关联目标 ID 时触发 | 是 | - | 是 | 目标画像刻画场景,父标签 |
| 目标画像刻画 | L4.SCENE.PROFILE.TARGET_TYPE | 目标类型 | L4 | SCENE | ENUM | TARGET | 由实体类型推导(主机/域/人) | 否 | L4.SCENE.PROFILE | 是 | 目标资产类型分类,ENUM 值:HOST/DOMAIN/PERSON |
| 目标画像刻画 | L4.SCENE.PROFILE.ASSET | 资产分类 | L4 | SCENE | ENUM | TARGET | 资产分类字典匹配 | 否 | L4.SCENE.PROFILE | 是 | 资产业务分类,ENUM 值由字典定义 |
| 目标画像刻画 | L4.SCENE.PROFILE.COMPLETENESS | 画像完整度 | L4 | SCENE | ENUM | TARGET | 画像字段覆盖率计算 | 否 | L4.SCENE.PROFILE | 是 | 画像完整度分级,ENUM 值:HIGH(≥80%)/MID(50%-80%)/LOW(<50%) |
| 网络地形还原 | L4.SCENE.TOPOLOGY | 网络地形场景 | L4 | SCENE | BOOL | FILE/TARGET | 文件含主机/网络设备实体 | 是 | - | 是 | 网络地形还原场景,父标签 |
| 网络地形还原 | L4.SCENE.TOPOLOGY.NODE | 拓扑节点类型 | L4 | SCENE | ENUM | TARGET | 由 L3.ENTITY.HOST 推导 | 否 | L4.SCENE.TOPOLOGY | 是 | 拓扑节点设备类型,ENUM 值:SERVER/NETWORK/ENDPOINT/ICS/CLOUD |
| 网络地形还原 | L4.SCENE.TOPOLOGY.ZONE | 网络区域 | L4 | SCENE | ENUM | TARGET | IP 网段归属判定 | 否 | L4.SCENE.TOPOLOGY | 是 | 网络区域分类,ENUM 值:DMZ/INTRANET/CORE |
| 网络地形还原 | L4.SCENE.TOPOLOGY.LINK | 连接关系 | L4 | SCENE | BOOL | TARGET | 文件含连接日志/会话记录 | 否 | L4.SCENE.TOPOLOGY | 是 | 节点间连接关系 |
| 访问凭证获取 | L4.SCENE.CREDENTIAL | 凭证获取场景 | L4 | SCENE | BOOL | FILE/TARGET | 文件含 L3.ENTITY.CRED 实体 | 是 | - | 是 | 凭证获取场景,父标签 |
| 访问凭证获取 | L4.SCENE.CREDENTIAL.TYPE | 凭证类型 | L4 | SCENE | ENUM | TARGET | 由 L3.ENTITY.CRED 子标签推导 | 否 | L4.SCENE.CREDENTIAL | 是 | 凭证类型分类,ENUM 值:PASSWORD/HASH/KEY/CERT/TOKEN/SESSION |
| 访问凭证获取 | L4.SCENE.CREDENTIAL.STATUS | 凭证状态 | L4 | SCENE | ENUM | TARGET | 凭证验证结果 | 否 | L4.SCENE.CREDENTIAL | 是 | 凭证有效性状态,ENUM 值:VALID(有效)/INVALID(失效)/EXPIRED(过期) |
| 访问凭证获取 | L4.SCENE.CREDENTIAL.USABILITY | 可用性 | L4 | SCENE | ENUM | TARGET | 凭证可用性验证结果 | 否 | L4.SCENE.CREDENTIAL | 是 | 凭证可用性判定,ENUM 值:AVAILABLE(可用)/VERIFY(需验证)/UNAVAILABLE(不可用) |
| 漏洞战机识别 | L4.SCENE.VULN | 漏洞战机场景 | L4 | SCENE | BOOL | FILE/TARGET | 文件含 L3.ENTITY.VULN 实体 | 是 | - | 是 | 漏洞战机识别场景,父标签 |
| 漏洞战机识别 | L4.SCENE.VULN.TYPE | 漏洞类型 | L4 | SCENE | ENUM | TARGET | 由 L3.ENTITY.VULN 子标签推导 | 否 | L4.SCENE.VULN | 是 | 漏洞类型分类,ENUM 值:CVE/ZERODAY/NDAY/MISCONFIG/LOGIC |
| 漏洞战机识别 | L4.SCENE.VULN.EXPLOITABILITY | 可利用性 | L4 | SCENE | ENUM | TARGET | 漏洞利用性验证结果 | 否 | L4.SCENE.VULN | 是 | 漏洞可利用性判定,ENUM 值:EXPLOITABLE(可利用)/VERIFY(待验证)/NOT_EXPLOITABLE(不可利用) |
| 漏洞战机识别 | L4.SCENE.VULN.DIFFICULTY | 利用难度 | L4 | SCENE | ENUM | TARGET | CVSS 攻击复杂度+PoC 可得性 | 否 | L4.SCENE.VULN | 是 | 利用难度分级,ENUM 值:EASY(易)/MID(中)/HARD(难) |
| 漏洞战机识别 | L4.SCENE.VULN.IMPACT | 影响等级 | L4 | SCENE | ENUM | TARGET | CVSS 评分+资产关键性 | 否 | L4.SCENE.VULN | 是 | 影响等级分级,ENUM 值:HIGH(CVSS≥7.0)/MID(4.0-7.0)/LOW(<4.0) |
| 横向移动 | L4.SCENE.LATERAL | 横向移动场景 | L4 | SCENE | BOOL | FILE/TARGET | 文件含横向移动痕迹(日志/工具) | 是 | - | 是 | 横向移动场景,父标签 |
| 横向移动 | L4.SCENE.LATERAL.PATH | 移动路径 | L4 | SCENE | BOOL | TARGET | 文件含登录日志/会话链路 | 否 | L4.SCENE.LATERAL | 是 | 标识横向移动路径 |
| 横向移动 | L4.SCENE.LATERAL.PIVOT | 跳板节点 | L4 | SCENE | BOOL | TARGET | 主机作为中转跳板被利用 | 否 | L4.SCENE.LATERAL | 是 | 横向移动跳板节点 |
| 横向移动 | L4.SCENE.LATERAL.PRIV_CHANGE | 权限变化 | L4 | SCENE | ENUM | TARGET | 文件含权限变更痕迹 | 否 | L4.SCENE.LATERAL | 是 | 权限变化类型,ENUM 值:ESCALATION(提权)/PERSIST(维持)/DOWNGRADE(降级) |
| 横向移动 | L4.SCENE.LATERAL.TECHNIQUE | 横向技术 | L4 | SCENE | ENUM | FILE/TEXT | 关联推导:凭证类型+目标→横向技术 | 是 | L4.SCENE.LATERAL | 是 | 横向移动技术分类(PTH/PTT/KERBEROAST/PSEXEC/WMI/RDPHIJACK/SSH/DCSYNC/GPP),红方攻击技术筛选 |
| 横向移动 | L4.SCENE.LATERAL.DEPTH | 跳板深度 | L4 | SCENE | NUMBER | FILE/TEXT | 统计凭证链路跳数 | 否 | L4.SCENE.LATERAL | 是 | 横向移动跳板深度,红方攻击链路评估 |
| 凭证获取 | L4.SCENE.CREDENTIAL.HASH_TYPE | Hash类型 | L4 | SCENE | ENUM | FILE/TEXT | 正则匹配Hash格式判定类型 | 否 | L4.SCENE.CREDENTIAL | 是 | Hash类型(NTLMV1/NTLMV2/KERBEROS/SHA1/MD5/LM/NETNTLMV2),红方Hash利用方式判定 |
| 凭证获取 | L4.SCENE.CREDENTIAL.USABLE_ATTACK | 可利用攻击 | L4 | SCENE | ENUM | FILE/TEXT | 关联推导:Hash类型→可利用攻击 | 是 | L4.SCENE.CREDENTIAL | 是 | 凭证可利用攻击(PTH/PTT/OVERPASS_HASH/KERBEROAST/ASREPROAST),红方凭证利用决策 |
| 凭证获取 | L4.SCENE.CREDENTIAL.SOURCE | 凭证来源 | L4 | SCENE | ENUM | FILE/TEXT | 分析凭证文件来源 | 否 | L4.SCENE.CREDENTIAL | 是 | 凭证来源(FILE/MEMORY/NETWORK/REGISTRY/LSASS),红方凭证来源追踪 |
| 漏洞战机 | L4.SCENE.VULN.EXPLOIT_TYPE | 漏洞利用类型 | L4 | SCENE | ENUM | FILE/TEXT | 关联CVE/CWE利用类型 | 是 | L4.SCENE.VULN | 是 | 漏洞利用类型(RCE/LPE/INFO_LEAK/DoS/AUTH_BYPASS/SQLI),红方按利用类型筛选 |
| 漏洞战机 | L4.SCENE.VULN.PUBLIC_EXP | 公开EXP | L4 | SCENE | ENUM | FILE/TEXT | 查询公开EXP库 | 否 | L4.SCENE.VULN | 是 | 公开EXP状态(AVAILABLE/PRIVATE/UNKNOWN),红方快速可利用判定 |
| 漏洞战机 | L4.SCENE.VULN.IN_WILD | 在野利用 | L4 | SCENE | BOOL | FILE/TEXT | 命中在野利用情报 | 否 | L4.SCENE.VULN | 是 | 是否在野利用,红方威胁优先级评估 |
| 漏洞战机 | L4.SCENE.VULN.EXPLOIT_COND | 利用条件 | L4 | SCENE | TEXT | FILE/TEXT | 分析漏洞利用前提 | 否 | L4.SCENE.VULN | 是 | 利用条件描述(需认证/需物理接触等),红方利用可行性评估 |
| 网络地形 | L4.SCENE.TOPOLOGY.ALIVE_STATUS | 存活状态 | L4 | SCENE | ENUM | TEXT | 网络扫描/探测结果 | 否 | L4.SCENE.TOPOLOGY | 是 | 资产存活状态(ALIVE/DOWN/UNKNOWN),红方资产可达性筛选 |
| 网络地形 | L4.SCENE.TOPOLOGY.SERVICE_VERSION | 服务版本指纹 | L4 | SCENE | TEXT | TEXT | 服务指纹识别 | 否 | L4.SCENE.TOPOLOGY | 是 | 服务版本(如nginx/1.18.0),红方版本漏洞关联 |
| 网络地形 | L4.SCENE.TOPOLOGY.WEBAPP_FINGER | Web应用指纹 | L4 | SCENE | TEXT | TEXT | Web应用指纹识别 | 否 | L4.SCENE.TOPOLOGY | 是 | Web应用指纹(如WordPress/Joomla),红方Web漏洞关联 |
| 网络地形 | L4.SCENE.TOPOLOGY.CMS | CMS识别 | L4 | SCENE | ENUM | TEXT | CMS指纹字典匹配 | 否 | L4.SCENE.TOPOLOGY | 是 | CMS类型(WORDPRESS/JOOMLA/DRUPAL/SHIRO/STRUTS/OTHER),红方CMS漏洞关联 |
| 网络地形 | L4.SCENE.TOPOLOGY.OS_FINGER | OS指纹 | L4 | SCENE | ENUM | TEXT | OS指纹识别 | 否 | L4.SCENE.TOPOLOGY | 是 | 操作系统类型(WINDOWS/LINUX/MACOS/UNKNOWN),红方OS漏洞关联 |
| 持久化 | L4.SCENE.PERSIST | 持久化场景 | L4 | SCENE | ENUM | FILE/TEXT | 持久化场景汇总 | 否 | - | 是 | 持久化场景父标签,红方权限维持场景 |
| 持久化 | L4.SCENE.PERSIST.MECHANISM | 持久化机制 | L4 | SCENE | ENUM | FILE/TEXT | 注册表/服务/计划任务等特征匹配 | 是 | L4.SCENE.PERSIST | 是 | 持久化机制(REGISTRY/SERVICE/SCHEDULE/WMI/STARTUP/COM/APPINIT/IFEO),红方持久化方式分类 |
| 持久化 | L4.SCENE.PERSIST.BACKDOOR_TYPE | 后门类型 | L4 | SCENE | ENUM | FILE/TEXT | 后门特征识别 | 否 | L4.SCENE.PERSIST | 是 | 后门类型(WEBSHELL/TROJAN/IMPLANT/C2_AGENT/ROOTKIT),红方后门识别 |
| 防御绕过 | L4.SCENE.EVASION | 防御绕过场景 | L4 | SCENE | ENUM | FILE/TEXT | 防御绕过场景汇总 | 否 | - | 是 | 防御绕过场景父标签,红方免杀/绕过场景 |
| 防御绕过 | L4.SCENE.EVASION.AV_EDR | AV/EDR识别 | L4 | SCENE | ENUM | FILE/TEXT | AV/EDR产品字典匹配 | 是 | L4.SCENE.EVASION | 是 | AV/EDR产品(DEFENDER/CROWDSTRIKE/SENTINELONE/CARBONBLACK/MCAFEE),红方防御产品识别 |
| 防御绕过 | L4.SCENE.EVASION.AVOID_STATUS | 免杀状态 | L4 | SCENE | ENUM | FILE/TEXT | 沙箱/AV扫描结果 | 否 | L4.SCENE.EVASION | 是 | 免杀状态(FUD/UD/DETECTED),红方载荷免杀评估 |
| 防御绕过 | L4.SCENE.EVASION.OBFUSCATION | 混淆方式 | L4 | SCENE | ENUM | FILE/TEXT | 代码混淆特征识别 | 是 | L4.SCENE.EVASION | 是 | 混淆方式(PACKING/ENCODING/ENCRYPTION/POLYMORPHIC/STEGO),红方混淆技术分类 |
| 红队基础设施 | L4.SCENE.INFRA | 红队基础设施场景 | L4 | SCENE | ENUM | FILE/TEXT | 红队基础设施汇总 | 否 | - | 是 | 红队基础设施父标签,红方基础设施管理 |
| 红队基础设施 | L4.SCENE.INFRA.C2 | C2服务器 | L4 | SCENE | STRING | FILE/TEXT | C2 profile/Beacon配置识别 | 否 | L4.SCENE.INFRA | 是 | C2服务器配置,红方C2基础设施识别 |
| 红队基础设施 | L4.SCENE.INFRA.DOMAIN_FRONT | 域名前置 | L4 | SCENE | BOOL | FILE/TEXT | Host头与实际域名不一致配置 | 否 | L4.SCENE.INFRA | 是 | 域名前置配置,红方流量伪装识别 |
| 红队基础设施 | L4.SCENE.INFRA.REDIRECTOR | 重定向器 | L4 | SCENE | BOOL | FILE/TEXT | nginx/socat端口转发配置 | 否 | L4.SCENE.INFRA | 是 | 重定向器配置,红方流量中转识别 |

---

## 第6章 L5 情报关联层标签字典

> L5 情报关联层共包含 6 个标签组:**APT组织**(L5.INTEL.APT,7 个子标签)、**攻击技术TTP**(L5.INTEL.TTP,12 个子标签)、**威胁等级**(L5.INTEL.THREAT,4 个子标签)、**情报来源**(L5.INTEL.SOURCE,4 个子标签)、**恶意软件家族**(L5.INTEL.MALWARE,14 个子标签)、**打包器**(L5.INTEL.PACKER,4 个子标签)。该层职责是关联威胁情报与攻击知识库,刻画文件的威胁属性。下表为该层完整标签字典(共 6 个父标签 + 45 个子标签 = 51 行)。

| 标签组 | 标签编码 | 标签中文名 | 层级 | 分类 | 值类型 | 适用对象 | 识别规则 | 是否多选 | 父标签 | 启用 | 口径定义 |
|--------|----------|-----------|------|------|--------|----------|----------|----------|--------|------|----------|
| APT组织 | L5.INTEL.APT | APT组织 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 APT 组织 TTP/IOCs 字典 | 是 | - | 是 | APT 攻击组织,父标签 |
| APT组织 | L5.INTEL.APT.APT29 | APT29(舒适熊) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 APT29 TTP/IOCs | 否 | L5.INTEL.APT | 是 | APT29 又名舒适熊,俄罗斯背景组织 |
| APT组织 | L5.INTEL.APT.APT28 | APT28(花式熊) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 APT28 TTP/IOCs | 否 | L5.INTEL.APT | 是 | APT28 又名花式熊,俄罗斯背景组织 |
| APT组织 | L5.INTEL.APT.LAZARUS | Lazarus(拉扎勒斯) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 Lazarus TTP/IOCs | 否 | L5.INTEL.APT | 是 | Lazarus 拉扎勒斯,朝鲜半岛背景组织 |
| APT组织 | L5.INTEL.APT.CONTI | Conti(康蒂勒索) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 Conti TTP/IOCs | 否 | L5.INTEL.APT | 是 | Conti 康蒂勒索软件组织 |
| APT组织 | L5.INTEL.APT.FIN7 | FIN7(金融犯罪组织) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 FIN7 TTP/IOCs | 否 | L5.INTEL.APT | 是 | FIN7 金融犯罪组织 |
| APT组织 | L5.INTEL.APT.APT41 | APT41(双尾组织) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 APT41 TTP/IOCs | 否 | L5.INTEL.APT | 是 | APT41 双尾,双重用途组织 |
| APT组织 | L5.INTEL.APT.TURLA | Turla(蛇形组织) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 Turla TTP/IOCs | 否 | L5.INTEL.APT | 是 | Turla 蛇形组织 |
| 攻击技术TTP | L5.INTEL.TTP | 攻击技术TTP | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | ATT&CK 技术 ID 字典匹配 | 是 | - | 是 | ATT&CK 攻击技术,父标签 |
| 攻击技术TTP | L5.INTEL.TTP.TA0001 | 初始访问 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0001 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:初始访问 |
| 攻击技术TTP | L5.INTEL.TTP.TA0002 | 执行 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0002 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:执行 |
| 攻击技术TTP | L5.INTEL.TTP.TA0003 | 持久化 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0003 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:持久化 |
| 攻击技术TTP | L5.INTEL.TTP.TA0004 | 提权 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0004 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:权限提升 |
| 攻击技术TTP | L5.INTEL.TTP.TA0005 | 防御逃逸 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0005 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:防御逃逸 |
| 攻击技术TTP | L5.INTEL.TTP.TA0006 | 凭据访问 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0006 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:凭据访问 |
| 攻击技术TTP | L5.INTEL.TTP.TA0007 | 发现 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0007 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:发现 |
| 攻击技术TTP | L5.INTEL.TTP.TA0008 | 横向移动 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0008 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:横向移动 |
| 攻击技术TTP | L5.INTEL.TTP.TA0009 | 收集 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0009 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:收集 |
| 攻击技术TTP | L5.INTEL.TTP.TA0010 | 外传 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0010 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:数据外传 |
| 攻击技术TTP | L5.INTEL.TTP.TA0011 | 影响 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中 TA0011 战术下技术 | 否 | L5.INTEL.TTP | 是 | ATT&CK 战术:影响 |
| 威胁等级 | L5.INTEL.THREAT | 威胁等级 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 由 MalwareML/情报库综合判定 | 否 | - | 是 | 综合威胁等级,父标签 |
| 威胁等级 | L5.INTEL.THREAT.HIGH | 高危 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 命中恶意 IOC 或 ML score ≥ 0.7 | 否 | L5.INTEL.THREAT | 是 | 高威胁等级 |
| 威胁等级 | L5.INTEL.THREAT.MEDIUM | 中危 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 部分可疑特征,0.4 ≤ score < 0.7 | 否 | L5.INTEL.THREAT | 是 | 中威胁等级 |
| 威胁等级 | L5.INTEL.THREAT.LOW | 低危 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 仅少量低风险特征,score < 0.4 | 否 | L5.INTEL.THREAT | 是 | 低威胁等级 |
| 威胁等级 | L5.INTEL.THREAT.BENIGN | 良性 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 情报库标记为白名单 | 否 | L5.INTEL.THREAT | 是 | 已知良性 |
| 情报来源 | L5.INTEL.SOURCE | 情报来源 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 情报命中记录的来源字段 | 是 | - | 是 | 威胁情报来源,父标签 |
| 情报来源 | L5.INTEL.SOURCE.INTERNAL | 内部情报 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 来源=internal | 否 | L5.INTEL.SOURCE | 是 | 平台积累的内部情报 |
| 情报来源 | L5.INTEL.SOURCE.OPEN_SOURCE | 开源情报 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 来源=osint | 否 | L5.INTEL.SOURCE | 是 | 开源威胁情报 |
| 情报来源 | L5.INTEL.SOURCE.COMMERCIAL | 商业情报 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 来源=commercial | 否 | L5.INTEL.SOURCE | 是 | 商业付费情报 |
| 情报来源 | L5.INTEL.SOURCE.SUBSCRIPTION | 威胁订阅 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | 来源=feed | 否 | L5.INTEL.SOURCE | 是 | 威胁情报订阅源 |
| 恶意软件家族 | L5.INTEL.MALWARE | 恶意软件家族 | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 规则+恶意软件家族字典 | 是 | - | 是 | 恶意软件家族,父标签 |
| 恶意软件家族 | L5.INTEL.MALWARE.COBALT_STRIKE | Cobalt Strike(渗透框架) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 命中 CS Beacon 特征 | 否 | L5.INTEL.MALWARE | 是 | Cobalt Strike 商业渗透框架 |
| 恶意软件家族 | L5.INTEL.MALWARE.METASPLOIT | Metasploit(渗透框架) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 命中 MSF Payload 特征 | 否 | L5.INTEL.MALWARE | 是 | Metasploit 渗透框架 |
| 恶意软件家族 | L5.INTEL.MALWARE.MIMIKATZ | Mimikatz(凭据提取) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 命中 Mimikatz 特征 | 否 | L5.INTEL.MALWARE | 是 | Mimikatz 凭据提取工具 |
| 恶意软件家族 | L5.INTEL.MALWARE.EMOTET | Emotet(银行木马) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 命中 Emotet 特征 | 否 | L5.INTEL.MALWARE | 是 | Emotet 银行木马 |
| 恶意软件家族 | L5.INTEL.MALWARE.TRICKBOT | TrickBot(木马) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 命中 TrickBot 特征 | 否 | L5.INTEL.MALWARE | 是 | TrickBot 木马 |
| 恶意软件家族 | L5.INTEL.MALWARE.RYUK | Ryuk(勒索软件) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 命中 Ryuk 特征 | 否 | L5.INTEL.MALWARE | 是 | Ryuk 勒索软件 |
| 恶意软件家族 | L5.INTEL.MALWARE.WANNACRY | WannaCry(勒索软件) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 命中 WannaCry 特征 | 否 | L5.INTEL.MALWARE | 是 | WannaCry 勒索软件 |
| 恶意软件家族 | L5.INTEL.MALWARE.METERPRETER | Meterpreter(后渗透载荷) | L5 | INTEL | ENUM | FILE/ENTITY/TARGET | YARA 命中 Meterpreter 特征 | 否 | L5.INTEL.MALWARE | 是 | Meterpreter 后渗透载荷 |
| 攻击技术TTP | L5.INTEL.TTP.SUBTECH | ATT&CK子技术 | L5 | INTEL | STRING | FILE/TEXT | ATT&CK子技术字典匹配(如T1059.001) | 否 | L5.INTEL.TTP | 是 | ATT&CK子技术编号,细粒度TTP筛选 |
| 恶意软件家族 | L5.INTEL.MALWARE.SLIVER | Sliver框架 | L5 | INTEL | ENUM | FILE | YARA命中Sliver特征 | 否 | L5.INTEL.MALWARE | 是 | Sliver C2框架,红方C2识别 |
| 恶意软件家族 | L5.INTEL.MALWARE.BRUTERATEL | Brute Ratel | L5 | INTEL | ENUM | FILE | YARA命中Brute Ratel特征 | 否 | L5.INTEL.MALWARE | 是 | Brute Ratel C2框架,红方C2识别 |
| 恶意软件家族 | L5.INTEL.MALWARE.MYTHIC | Mythic框架 | L5 | INTEL | ENUM | FILE | YARA命中Mythic特征 | 否 | L5.INTEL.MALWARE | 是 | Mythic C2框架,红方C2识别 |
| 恶意软件家族 | L5.INTEL.MALWARE.HAVOC | Havoc框架 | L5 | INTEL | ENUM | FILE | YARA命中Havoc特征 | 否 | L5.INTEL.MALWARE | 是 | Havoc C2框架,红方C2识别 |
| 恶意软件家族 | L5.INTEL.MALWARE.EMPIRE | Empire框架 | L5 | INTEL | ENUM | FILE | YARA命中PowerShell Empire特征 | 否 | L5.INTEL.MALWARE | 是 | Empire C2框架,红方C2识别 |
| 恶意软件家族 | L5.INTEL.MALWARE.COVENANT | Covenant框架 | L5 | INTEL | ENUM | FILE | YARA命中Covenant特征 | 否 | L5.INTEL.MALWARE | 是 | Covenant C2框架,红方C2识别 |
| 打包器 | L5.INTEL.PACKER | 打包器/混淆器 | L5 | INTEL | ENUM | FILE | 打包器特征汇总 | 否 | - | 是 | 打包器父标签,红方载荷分析 |
| 打包器 | L5.INTEL.PACKER.UPX | UPX | L5 | INTEL | ENUM | FILE | PE节名含UPX0/UPX1 | 否 | L5.INTEL.PACKER | 是 | UPX打包器,红方载荷脱壳识别 |
| 打包器 | L5.INTEL.PACKER.THEMIDA | Themida | L5 | INTEL | ENUM | FILE | Themida特征字节/节名 | 否 | L5.INTEL.PACKER | 是 | Themida强壳,红方强壳识别 |
| 打包器 | L5.INTEL.PACKER.VMPROT | VMProtect | L5 | INTEL | ENUM | FILE | VMProtect特征 | 否 | L5.INTEL.PACKER | 是 | VMProtect虚拟化壳,红方虚拟化壳识别 |
| 打包器 | L5.INTEL.PACKER.DONUT | Donut | L5 | INTEL | ENUM | FILE | Donut特征字节 | 否 | L5.INTEL.PACKER | 是 | Donut .NET注入器,红方注入器识别 |

---

## 第7章 L6 安全合规层标签字典

> L6 安全合规层共包含 7 个标签组:**密级**(L6.COMP.CLASSIFICATION,5 个子标签)、**保留期**(L6.COMP.RETENTION,6 个子标签)、**合规要求**(L6.COMP.REGULATION,4 个子标签)、**访问限制**(L6.COMP.ACCESS,5 个子标签)、**脱敏状态**(L6.COMP.DESSENSITIZE,4 个子标签)、**防溯源**(L6.COMP.ANTI_FORENSIC,2 个子标签)、**销毁管理**(L6.COMP.DESTRUCTION,1 个子标签)。该层职责是数据管控与合规属性,贯穿文件全生命周期。下表为该层完整标签字典(共 7 个父标签 + 27 个子标签 = 34 行)。

| 标签组 | 标签编码 | 标签中文名 | 层级 | 分类 | 值类型 | 适用对象 | 识别规则 | 是否多选 | 父标签 | 启用 | 口径定义 |
|--------|----------|-----------|------|------|--------|----------|----------|----------|--------|------|----------|
| 密级 | L6.COMP.CLASSIFICATION | 密级 | L6 | COMP | ENUM | FILE/TARGET | 敏感关键词字典+人工标注 | 否 | - | 是 | 数据涉密等级,父标签 |
| 密级 | L6.COMP.CLASSIFICATION.PUBLIC | 公开 | L6 | COMP | ENUM | FILE/TARGET | 无敏感特征 | 否 | L6.COMP.CLASSIFICATION | 是 | 可公开数据 |
| 密级 | L6.COMP.CLASSIFICATION.INTERNAL | 内部 | L6 | COMP | ENUM | FILE/TARGET | 内部使用,非公开 | 否 | L6.COMP.CLASSIFICATION | 是 | 内部使用数据 |
| 密级 | L6.COMP.CLASSIFICATION.SECRET | 秘密 | L6 | COMP | ENUM | FILE/TARGET | 命中敏感关键词或来源为秘密级 | 否 | L6.COMP.CLASSIFICATION | 是 | 秘密级数据 |
| 密级 | L6.COMP.CLASSIFICATION.CONFIDENTIAL | 机密 | L6 | COMP | ENUM | FILE/TARGET | 含有效凭证或核心资产信息 | 否 | L6.COMP.CLASSIFICATION | 是 | 机密级数据 |
| 密级 | L6.COMP.CLASSIFICATION.TOPSECRET | 绝密 | L6 | COMP | ENUM | FILE/TARGET | 含核心指挥/决策信息 | 否 | L6.COMP.CLASSIFICATION | 是 | 绝密级数据 |
| 保留期 | L6.COMP.RETENTION | 保留期 | L6 | COMP | ENUM | FILE/TARGET | 密级→保留期映射规则 | 否 | - | 是 | 数据保留时长,父标签 |
| 保留期 | L6.COMP.RETENTION.PERMANENT | 永久 | L6 | COMP | ENUM | FILE/TARGET | 密级=绝密 | 否 | L6.COMP.RETENTION | 是 | 永久保留 |
| 保留期 | L6.COMP.RETENTION.Y10 | 10年 | L6 | COMP | ENUM | FILE/TARGET | 密级=机密 | 否 | L6.COMP.RETENTION | 是 | 保留 10 年 |
| 保留期 | L6.COMP.RETENTION.Y5 | 5年 | L6 | COMP | ENUM | FILE/TARGET | 密级=秘密 | 否 | L6.COMP.RETENTION | 是 | 保留 5 年 |
| 保留期 | L6.COMP.RETENTION.Y3 | 3年 | L6 | COMP | ENUM | FILE/TARGET | 含 IOC/情报 | 否 | L6.COMP.RETENTION | 是 | 保留 3 年 |
| 保留期 | L6.COMP.RETENTION.Y1 | 1年 | L6 | COMP | ENUM | FILE/TARGET | 密级=内部 | 否 | L6.COMP.RETENTION | 是 | 保留 1 年 |
| 保留期 | L6.COMP.RETENTION.TASK_END | 任务结束即删 | L6 | COMP | ENUM | FILE/TARGET | 临时任务数据 | 否 | L6.COMP.RETENTION | 是 | 任务结束即删除 |
| 合规要求 | L6.COMP.REGULATION | 合规要求 | L6 | COMP | ENUM | FILE/TARGET | 数据类型→法规映射 | 是 | - | 是 | 适用合规要求,父标签 |
| 合规要求 | L6.COMP.REGULATION.MLPS3 | 等保三级 | L6 | COMP | ENUM | FILE/TARGET | 系统等保三级定级 | 否 | L6.COMP.REGULATION | 是 | 网络安全等级保护三级 |
| 合规要求 | L6.COMP.REGULATION.NATIONAL_CRYPTO | 国密合规 | L6 | COMP | ENUM | FILE/TARGET | 加密算法须使用国密 | 否 | L6.COMP.REGULATION | 是 | 国密算法合规要求 |
| 合规要求 | L6.COMP.REGULATION.DATA_SECURITY | 数据安全法 | L6 | COMP | ENUM | FILE/TARGET | 数据安全法适用 | 否 | L6.COMP.REGULATION | 是 | 数据安全法 |
| 合规要求 | L6.COMP.REGULATION.PIPL | 个人信息保护 | L6 | COMP | ENUM | FILE/TARGET | 含个人信息字段 | 否 | L6.COMP.REGULATION | 是 | 个人信息保护法 |
| 访问限制 | L6.COMP.ACCESS | 访问限制 | L6 | COMP | ENUM | FILE/TARGET | 密级→访问限制映射 | 否 | - | 是 | 数据访问范围,父标签 |
| 访问限制 | L6.COMP.ACCESS.PUBLIC | 公开 | L6 | COMP | ENUM | FILE/TARGET | 密级=公开 | 否 | L6.COMP.ACCESS | 是 | 所有人可见 |
| 访问限制 | L6.COMP.ACCESS.TEAM | 团队内 | L6 | COMP | ENUM | FILE/TARGET | 密级=内部 | 否 | L6.COMP.ACCESS | 是 | 团队成员可见 |
| 访问限制 | L6.COMP.ACCESS.PROJECT_OWNER | 项目负责人 | L6 | COMP | ENUM | FILE/TARGET | 密级=秘密 | 否 | L6.COMP.ACCESS | 是 | 仅项目负责人可见 |
| 访问限制 | L6.COMP.ACCESS.SELF_ONLY | 仅本人 | L6 | COMP | ENUM | FILE/TARGET | 密级=机密 | 否 | L6.COMP.ACCESS | 是 | 仅上传者可见 |
| 访问限制 | L6.COMP.ACCESS.APPROVAL | 审批访问 | L6 | COMP | ENUM | FILE/TARGET | 密级=绝密 | 否 | L6.COMP.ACCESS | 是 | 需审批方可访问 |
| 脱敏状态 | L6.COMP.DESSENSITIZE | 脱敏状态 | L6 | COMP | ENUM | FILE/TARGET | 脱敏任务执行结果 | 否 | - | 是 | 数据脱敏处理状态,父标签 |
| 脱敏状态 | L6.COMP.DESSENSITIZE.NONE | 未脱敏 | L6 | COMP | ENUM | FILE/TARGET | 原始数据,未做脱敏 | 否 | L6.COMP.DESSENSITIZE | 是 | 未脱敏 |
| 脱敏状态 | L6.COMP.DESSENSITIZE.DONE | 已脱敏 | L6 | COMP | ENUM | FILE/TARGET | 全部敏感字段已脱敏 | 否 | L6.COMP.DESSENSITIZE | 是 | 已脱敏 |
| 脱敏状态 | L6.COMP.DESSENSITIZE.PARTIAL | 部分脱敏 | L6 | COMP | ENUM | FILE/TARGET | 仅部分敏感字段已脱敏 | 否 | L6.COMP.DESSENSITIZE | 是 | 部分脱敏 |
| 脱敏状态 | L6.COMP.DESSENSITIZE.NOT_REQUIRED | 无需脱敏 | L6 | COMP | ENUM | FILE/TARGET | 公开数据无需脱敏 | 否 | L6.COMP.DESSENSITIZE | 是 | 无需脱敏 |
| 防溯源 | L6.COMP.ANTI_FORENSIC | 防溯源属性 | L6 | COMP | ENUM | FILE | 防溯源属性汇总 | 否 | - | 是 | 防溯源父标签,红方产出物OPSEC管理 |
| 防溯源 | L6.COMP.ANTI_FORENSIC.SCRUBBED | 已清除痕迹 | L6 | COMP | BOOL | FILE | 元数据已擦除(作者/时间戳) | 否 | L6.COMP.ANTI_FORENSIC | 是 | 已清除溯源痕迹,红方产出物OPSEC |
| 防溯源 | L6.COMP.ANTI_FORENSIC.WATERMARK | 含水印 | L6 | COMP | BOOL | FILE | 文件含水印标识 | 否 | L6.COMP.ANTI_FORENSIC | 是 | 含水印标识,红方产出物水印追踪 |
| 销毁管理 | L6.COMP.DESTRUCTION | 销毁管理 | L6 | COMP | ENUM | FILE | 销毁管理汇总 | 否 | - | 是 | 销毁管理父标签,红方产出物销毁审计 |
| 销毁管理 | L6.COMP.DESTRUCTION.CONFIRMED | 已确认销毁 | L6 | COMP | BOOL | FILE | 销毁任务完成确认 | 否 | L6.COMP.DESTRUCTION | 是 | 已确认销毁,红方任务结束销毁审计 |

---

## 第8章 自动识别规则集

> 本章规则集共 **131 条**(正则×58 / 字典×23 / 模型×20 / 关联×30),覆盖 L1-L6 全层级。每条规则采用 12 字段结构化定义,规则执行遵循「上传→解析→分析→关联」四阶段流水线,并由规则冲突仲裁器统一仲裁。每类规则表内含"序号"列(从1起连续编号,仅用于表内排序)与"规则描述"列(一句话概括规则作用,作为规则的可读标识,文中所有引用均以规则描述呈现)。

### 8.0 规则字段定义

每条规则统一采用以下 12 字段表格描述:

| 序号 | 字段 | 说明 |
|------|------|------|
| 1 | 序号 | 表内连续编号(从1起),仅用于排序定位 |
| 2 | 规则描述 | 一句话概括规则作用,作为规则的可读标识,文中引用均以规则描述呈现 |
| 3 | 规则类型 | 正则/字典/模型/关联 |
| 4 | 触发时机 | 上传后/解析后/分析后/实体入库后 |
| 5 | 输入数据 | 文件名/文件内容文本/文件二进制/文件元数据/提取实体列表 |
| 6 | 规则表达式/模型 | 正则表达式/字典内容/模型名称/关联条件 |
| 7 | 产出标签 | 命中后产出的标签编码 |
| 8 | 输出置信度 | 0.0-1.0,正则≈0.9、字典≈0.85、模型=0.7-0.95、关联≈0.85 |
| 9 | 优先级 | P0(必须执行)/P1(默认执行)/P2(按需执行) |
| 10 | 前置依赖 | 该规则执行前需先执行的规则(以规则描述列出),无则填「无」 |
| 11 | 冲突处理 | 高优先级覆盖低优先级/置信度高者胜出/合并多选/人工复核 |
| 12 | 示例 | 一个具体输入→输出的示例 |

> **正则表达式转义约定**:本章正则规则在 Markdown 表格中展示时,管道符 `|` 以 `\|` 转义以避免与表格列分隔符冲突。实际入库与执行时,正则引擎应将 `\|` 还原为 `|`(交替运算符)。例如表格中的 `(a\|b)` 实际正则为 `(a|b)`。特此说明,避免与 PCRE/RE2 中 `\|`(匹配字面管道符)混淆。

### 8.1 正则规则(REGEX)— 共 58 条

| 序号 | 规则描述 | 规则类型 | 触发时机 | 输入数据 | 规则表达式/模型 | 产出标签 | 输出置信度 | 优先级 | 前置依赖 | 冲突处理 | 示例 |
|------|----------|----------|----------|----------|----------------|----------|-----------|--------|----------|----------|------|
| 1 | 文件扩展名识别 | 正则 | 文件上传后 | 文件名 | `\.(pdf\|docx\|xlsx\|pptx\|eml\|exe\|dll\|pcap\|pcapng\|zip\|rar\|png\|jpg\|jpeg\|log\|py\|bin\|txt\|html?)$` | L1.FILE.TYPE.* | 0.9 | P0 | 无 | 与 Magic Number 字典匹配冲突时以 Magic Number 为准 | `report.pdf` → L1.FILE.TYPE.PDF, 置信度0.9 |
| 2 | IPv4地址提取 | 正则 | 文件解析后 | 文件内容文本 | `\b((25[0-5]\|2[0-4]\d\|[01]?\d?\d)\.){3}(25[0-5]\|2[0-4]\d\|[01]?\d?\d)\b` | L3.ENTITY.IP | 0.9 | P0 | 无 | 与私网IPv4段识别冲突时优先私网IPv4段识别 | `192.168.1.1` → L3.ENTITY.IP, 置信度0.9 |
| 3 | 域名提取 | 正则 | 文件解析后 | 文件内容文本 | `\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\b` | L3.ENTITY.DOMAIN | 0.85 | P0 | 无 | 合并多选 | `example.com` → L3.ENTITY.DOMAIN, 置信度0.85 |
| 4 | 邮箱地址提取 | 正则 | 文件解析后 | 文件内容文本 | `\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b` | L3.ENTITY.EMAIL | 0.9 | P0 | 无 | 合并多选 | `user@example.com` → L3.ENTITY.EMAIL, 置信度0.9 |
| 5 | URL提取 | 正则 | 文件解析后 | 文件内容文本 | ``https?://[^\s<>"'{}\|\\^` ]+`` | L3.ENTITY.URL | 0.9 | P0 | 无 | 合并多选 | `https://example.com/path` → L3.ENTITY.URL, 置信度0.9 |
| 6 | CVE漏洞编号提取 | 正则 | 文件解析后 | 文件内容文本 | `\bCVE-\d{4}-\d{4,7}\b` | L3.ENTITY.VULN.CVE | 0.95 | P0 | 无 | 合并多选 | `CVE-2024-12345` → L3.ENTITY.VULN.CVE, 置信度0.95 |
| 7 | 文件哈希提取(MD5/SHA1/SHA256) | 正则 | 文件解析后 | 文件内容文本 | `\b(?:[a-fA-F0-9]{32}\|[a-fA-F0-9]{40}\|[a-fA-F0-9]{64})\b`(MD5/SHA1/SHA256 三选一,按长度区分) | L3.ENTITY.IOC.FILE_HASH | 0.85 | P0 | 无 | 与 AWS Secret Key 识别(40字符密钥)冲突时优先 AWS Secret Key 识别 | `d41d8cd98f00b204e9800998ecf8427e` → L3.ENTITY.IOC.FILE_HASH, 置信度0.85 |
| 8 | 端口提取 | 正则 | 文件解析后 | 文件内容文本 | `(?::\|端口\|port\s*=\s*)(\d{1,5})\b` 且 1 ≤ 值 ≤ 65535 | L3.ENTITY.PORT | 0.8 | P1 | 无 | 与高危端口字典匹配合并多选 | `:3389` → L3.ENTITY.PORT, 置信度0.8 |
| 9 | 注册表键提取 | 正则 | 文件解析后 | 文件内容文本 | `(?:HKLM\|HKEY_LOCAL_MACHINE\|HKCU\|HKEY_CURRENT_USER\|HKCR\|HKEY_CLASSES_ROOT)\\[\w\\.$]+` | L3.ENTITY.REGKEY | 0.85 | P1 | 无 | 合并多选 | `HKLM\SOFTWARE\Microsoft` → L3.ENTITY.REGKEY, 置信度0.85 |
| 10 | Base64编码识别 | 正则 | 文件解析后 | 文件内容文本 | `[A-Za-z0-9+/=\s]{64,}`(去除空白后)占全文 ≥ 80%,且 Base64 解码后含可读字符 | L1.FILE.ENCODING.BASE64 | 0.85 | P2 | 无 | 与编码检测结果为准 | `dGhpcyBpcyBhIHRlc3Q=` → L1.FILE.ENCODING.BASE64, 置信度0.85 |
| 11 | 私网IPv4段识别 | 正则 | 文件解析后 | 文件内容文本 | `\b(10\.\d{1,3}\.\d{1,3}\.\d{1,3}\|172\.(1[6-9]\|2\d\|3[01])\.\d{1,3}\.\d{1,3}\|192\.168\.\d{1,3}\.\d{1,3})\b` | L3.ENTITY.IP.PRIVATE | 0.95 | P0 | IPv4地址提取 | 高优先级覆盖 IPv4地址提取的 PUBLIC 判定 | `192.168.1.1` → L3.ENTITY.IP.PRIVATE, 置信度0.95 |
| 12 | 公网IPv4识别 | 正则 | 文件解析后 | 文件内容文本 | 在 IPv4地址提取命中基础上排除私网IPv4段识别的私网段及保留段(0.0.0.0/8、127.0.0.0/8、169.254.0.0/16、224.0.0.0/4) | L3.ENTITY.IP.PUBLIC | 0.9 | P1 | IPv4地址提取、私网IPv4段识别 | 与私网IPv4段识别冲突时以私网IPv4段识别为准 | `8.8.8.8` → L3.ENTITY.IP.PUBLIC, 置信度0.9 |
| 13 | MAC地址提取 | 正则 | 文件解析后 | 文件内容文本 | `\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b` | L3.ENTITY.HOST | 0.85 | P2 | 无 | 合并多选;MAC 地址作为主机网卡标识符映射至 HOST 实体 | `00:11:22:33:44:55` → L3.ENTITY.HOST, 置信度0.85 |
| 14 | JWT Token识别 | 正则 | 文件解析后 | 文件内容文本 | `\beyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b` | L3.ENTITY.CRED.TOKEN | 0.95 | P1 | 无 | 合并多选 | `eyJhbGciOiJIUzI1...` → L3.ENTITY.CRED.TOKEN, 置信度0.95 |
| 15 | IPv6地址提取 | 正则 | 文件解析后 | 文件内容文本 | `\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\b\|(?:[0-9a-fA-F]{1,4}:)*:(?::[0-9a-fA-F]{1,4})*\b` | L3.ENTITY.IP | 0.8 | P2 | 无 | 合并多选 | `2001:db8::1` → L3.ENTITY.IP, 置信度0.8 |
| 16 | Windows路径提取 | 正则 | 文件解析后 | 文件内容文本 | `[A-Za-z]:\\[^\s<>:"\|?*]+` | L3.ENTITY.HOST | 0.75 | P2 | 无 | 合并多选;Windows 路径作为主机 OS 识别上下文线索 | `C:\Windows\System32` → L3.ENTITY.HOST, 置信度0.75 |
| 17 | PEM私钥头识别 | 正则 | 文件解析后 | 文件内容文本 | `-----BEGIN ([A-Z]+ )?PRIVATE KEY-----` | L3.ENTITY.CRED.KEY | 0.95 | P1 | 无 | 合并多选 | `-----BEGIN RSA PRIVATE KEY-----` → L3.ENTITY.CRED.KEY, 置信度0.95 |
| 18 | Webshell文件名识别 | 正则 | 文件上传后 | 文件名 | `(?i)(webshell\|shell\|c99\|r57\|b374k\|wso)[a-z0-9_-]*\.(php\|jsp\|asp\|aspx)` | L1.FILE.TYPE.PHP + 触发 Webshell→持久化关联 | 0.9 | P0 | 无 | 高优先级覆盖低优先级 | `c99.php` → L1.FILE.TYPE.PHP + 触发 Webshell 关联, 置信度0.9 |
| 19 | PowerShell加密命令识别 | 正则 | 文件解析后 | 文件内容文本 | `(?i)powershell.*(-enc\|-encodedcommand\|-e)\s+[A-Za-z0-9+/=]{20,}` | L5.INTEL.TTP.TA0002 + 触发 PowerShell→执行TTP关联 | 0.9 | P0 | 无 | 合并多选 | `powershell -enc SQBFAFgA...` → L5.INTEL.TTP.TA0002, 置信度0.9 |
| 20 | Linux敏感路径识别 | 正则 | 文件解析后 | 文件内容文本 | `(/etc/passwd\|/etc/shadow\|/home/\w+/\.ssh/\|/root/\.ssh/\|/var/log/auth\.log)` | L5.INTEL.TTP.TA0007 | 0.85 | P1 | 无 | 合并多选 | `/etc/shadow` → L5.INTEL.TTP.TA0007, 置信度0.85 |
| 21 | 数据库连接串识别 | 正则 | 文件解析后 | 文件内容文本 | `(?:(?:mysql\|postgresql\|mongodb\|redis)://[^\s]*:[^\s]*@\|jdbc:[a-z]+://[^\s]+)` | L3.ENTITY.CRED.PASSWORD + 触发数据库连接串→凭证密级关联 | 0.9 | P0 | 无 | 合并多选 | `mysql://root:pass@host:3306/db` → L3.ENTITY.CRED.PASSWORD, 置信度0.9 |
| 22 | AWS Access Key ID识别 | 正则 | 文件解析后 | 文件内容文本 | `\bAKIA[0-9A-Z]{16}\b` | L3.ENTITY.CRED.KEY + 触发 AWS密钥→凭证密级关联 | 0.95 | P0 | 无 | 合并多选 | `AKIAIOSFODNN7EXAMPLE` → L3.ENTITY.CRED.KEY, 置信度0.95 |
| 23 | AWS Secret Key识别 | 正则 | 文件解析后 | 文件内容文本 | `(?i)(aws_secret_access_key\|secret)\s*[=:]\s*['"]?[A-Za-z0-9/+=]{40}['"]?` | L3.ENTITY.CRED.KEY + 触发 AWS密钥→凭证密级关联 | 0.9 | P0 | 无 | 与文件哈希提取(SHA1 40字符)冲突时优先 AWS Secret Key 识别 | `aws_secret=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` → L3.ENTITY.CRED.KEY, 置信度0.9 |
| 24 | PEM私钥完整格式识别 | 正则 | 文件解析后 | 文件内容文本 | `-----BEGIN ([A-Z]+ )?PRIVATE KEY-----[\s\S]*?-----END \1?PRIVATE KEY-----` | L3.ENTITY.CRED.KEY | 0.95 | P0 | PEM私钥头识别 | 与 PEM私钥头识别冲突时合并多选,以 PEM私钥完整格式识别(完整格式)为准 | `-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----` → L3.ENTITY.CRED.KEY, 置信度0.95 |
| 25 | NTLM Hash识别 | 正则 | 文件解析后 | 文件内容文本 | `\b[a-fA-F0-9]{32}:[a-fA-F0-9]{32}\b` | L3.ENTITY.CRED.HASH | 0.9 | P0 | 无 | 合并多选 | `31d6cfe0d16ae931b73c59d7e0c089c0:8846f7eaee8fb117ad06bdd830b7586c` → L3.ENTITY.CRED.HASH, 置信度0.9 |
| 26 | SQL注入特征识别 | 正则 | 文件解析后 | 文件内容文本 | `(?i)(union\s+select\|'\s*or\s*'1'='1\|information_schema\|select\s+\*\s+from\s+information_schema)` | L5.INTEL.TTP.TA0001 | 0.85 | P1 | 无 | 合并多选 | `' OR '1'='1` → L5.INTEL.TTP.TA0001, 置信度0.85 |
| 27 | XSS特征识别 | 正则 | 文件解析后 | 文件内容文本 | `(?i)(<script[^>]*>\|javascript:\|onerror\s*=\|onload\s*=\|onclick\s*=)` | L5.INTEL.TTP.TA0002 | 0.85 | P1 | 无 | 合并多选 | `<script>alert(1)</script>` → L5.INTEL.TTP.TA0002, 置信度0.85 |
| 28 | 命令注入特征识别 | 正则 | 文件解析后 | 文件内容文本 | `(?:;\|&&\|[|]{2})\s*(?:cat\|ls\|id\|whoami\|uname\|ifconfig\|ipconfig\|net\s+user\|tasklist)\b` | L5.INTEL.TTP.TA0002 | 0.85 | P1 | 无 | 合并多选 | `; cat /etc/passwd` → L5.INTEL.TTP.TA0002, 置信度0.85 |
| 29 | Cobalt Strike特征识别 | 正则 | 文件解析后 | 文件内容文本/二进制 | `(?i)(http-post\s*\{[^}]*beacon\|submit\.php\|\.beacon\.\|cobaltstrike\|CS-Shell)` | L5.INTEL.MALWARE.COBALT_STRIKE | 0.9 | P0 | 无 | 与红队工具字典匹配合并多选 | `http-post { uri "/api/beacon"; }` → L5.INTEL.MALWARE.COBALT_STRIKE, 置信度0.9 |
| 30 | Mimikatz特征识别 | 正则 | 文件解析后 | 文件内容文本 | `(?i)(mimikatz\|sekurlsa::logonpasswords\|lsadump::sam\|lsadump::secrets\|kerberos::ptt\|crypto::capi)` | L5.INTEL.MALWARE.MIMIKATZ + L5.INTEL.TTP.TA0006 | 0.95 | P0 | 无 | 合并多选 | `sekurlsa::logonpasswords` → L5.INTEL.MALWARE.MIMIKATZ, 置信度0.95 |
| 31 | 文件大小分级 | 正则 | 文件上传后 | 文件元数据 | size<1024→TINY; 1024≤size<1048576→SMALL; 1048576≤size<104857600→MEDIUM; 104857600≤size<1073741824→LARGE; size≥1073741824→HUGE | L1.FILE.SIZE.* | 1.0 | P0 | 无 | 高优先级覆盖低优先级 | `size=5242880` → L1.FILE.SIZE.MEDIUM, 置信度1.0 |
| 32 | GBK编码识别 | 正则 | 文件解析后 | 文件二进制 | GBK BOM 检测(无 UTF-8 BOM 且 GBK 双字节字符频率≥30%)或 chardet 判定 confidence>0.8 | L1.FILE.ENCODING.GBK | 0.85 | P1 | 无 | 与 Base64编码识别冲突时以编码检测结果为准 | 含 GB2312 BOM 的中文文本 → L1.FILE.ENCODING.GBK, 置信度0.85 |
| 33 | IPv6特殊地址识别 | 正则 | 文件解析后 | 文件内容文本 | `\b(fe80::[0-9a-fA-F]{0,4}(:[0-9a-fA-F]{0,4})*\|2001:db8::[0-9a-fA-F:]*\|::1)\b` | L3.ENTITY.IP | 0.85 | P2 | IPv6地址提取 | 与 IPv6地址提取合并多选,以 IPv6特殊地址识别(更精确)为准 | `fe80::1` → L3.ENTITY.IP, 置信度0.85 |
| 34 | 高危端口识别 | 正则 | 文件解析后 | 文件内容文本 | 端口值 ∈ {22,23,135,137,139,445,1433,1521,3306,3389,5432,5900,6379,27017} | L3.ENTITY.PORT.HIGHRISK | 0.9 | P0 | 端口提取 | 与高危端口字典匹配冲突时取置信度高者;HIGHRISK 为单选标签,不合并 | `:3389` → L3.ENTITY.PORT.HIGHRISK, 置信度0.9 |
| 35 | 双扩展名识别 | 正则 | 文件上传后 | 文件名 | `\.[a-z0-9]+\.(exe\|dll\|scr\|com\|bat\|ps1\|vbs\|js\|jar)$` | L5.INTEL.THREAT.MEDIUM | 0.85 | P1 | 无 | 合并多选 | `report.pdf.exe` → L5.INTEL.THREAT.MEDIUM, 置信度0.85 |
| 36 | 比特币地址识别 | 正则 | 文件解析后 | 文件内容文本 | `\b[13][a-km-zA-HJ-NP-Z1-9]{25,34}\b\|bc1[a-z0-9]{6,87}` | L5.INTEL.TTP.TA0011 | 0.85 | P1 | 无 | 合并多选;比特币地址常关联勒索赎金(TA0011 影响) | `1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa` → L5.INTEL.TTP.TA0011, 置信度0.85 |
| 37 | 可疑User-Agent识别 | 正则 | 文件解析后 | 文件内容文本 | `(?i)user-agent:\s*(curl\|wget\|python-requests\|nikto\|sqlmap\|nmap\|metasploit)` | L5.INTEL.TTP.TA0001 | 0.85 | P1 | 无 | 合并多选 | `User-Agent: sqlmap/1.5` → L5.INTEL.TTP.TA0001, 置信度0.85 |
| 38 | 编码命令行识别 | 正则 | 文件解析后 | 文件内容文本 | `(?i)(cmd\s+/c\|cmd\.exe).*[A-Za-z0-9+/=]{50,}` | L5.INTEL.TTP.TA0002 | 0.85 | P1 | 无 | 合并多选 | `cmd /c powershell -enc...` → L5.INTEL.TTP.TA0002, 置信度0.85 |
| 39 | 计划任务命令识别 | 正则 | 文件解析后 | 文件内容文本 | `(?i)(schtasks\s+/create\|crontab\s+-\|at\s+\d{1,2}:\d{2})` | L5.INTEL.TTP.TA0003 | 0.85 | P1 | 无 | 合并多选 | `schtasks /create /tn update /tr cmd` → L5.INTEL.TTP.TA0003, 置信度0.85 |
| 40 | Kerberos票据(.kirbi)识别 | 正则 | 文件解析后 | 文件二进制/文件内容文本 | `(?s)R2\x00.{10,500}` 或 base64编码 `doIF[A-Za-z0-9+/=]{40,}` | L3.ENTITY.CRED.KERBEROS | 0.90 | P1 | 无 | 合并多选 | .kirbi文件或base64编码的Kerberos票据 → L3.ENTITY.CRED.KERBEROS, 置信度0.90 |
| 41 | NetNTLMv1/v2 Hash识别 | 正则 | 文件解析后 | 文件内容文本/日志 | `[^\s:]+::[^\s:]+:[a-fA-F0-9]{32}:[a-fA-F0-9]{32}:[a-fA-F0-9]{16}` | L3.ENTITY.CRED.NETNTLM | 0.95 | P0 | 无 | 合并多选 | `user::DOMAIN:LMhash:NThash:challenge` → L3.ENTITY.CRED.NETNTLM, 置信度0.95 |
| 42 | AS-REP Hash识别 | 正则 | 文件解析后 | 文件内容文本 | `\$krb5asrep\$23\$[^\s:]+@[^\s:]+:[a-fA-F0-9]{32,}` | L3.ENTITY.CRED.ASREP | 0.95 | P0 | 无 | 合并多选 | `$krb5asrep$23$user@DOMAIN:hash` → L3.ENTITY.CRED.ASREP, 置信度0.95 |
| 43 | TGS Hash(Kerberoasting)识别 | 正则 | 文件解析后 | 文件内容文本 | `\$krb5tgs\$23\$\*?[^\$]*\$[a-fA-F0-9]{32,}\$[a-fA-F0-9]{32,}` | L3.ENTITY.CRED.TGS | 0.95 | P0 | 无 | 合并多选 | `$krb5tgs$23$*spn*$hash$hash` → L3.ENTITY.CRED.TGS, 置信度0.95 |
| 44 | Linux Shadow Hash识别 | 正则 | 文件解析后 | 文件内容文本 | `\$[156y]\$[A-Za-z0-9./]{0,16}\$[A-Za-z0-9./]{22,}` | L3.ENTITY.CRED.SHADOW | 0.95 | P0 | 无 | 合并多选 | `$6$rounds=5000$salt$hash` → L3.ENTITY.CRED.SHADOW, 置信度0.95 |
| 45 | PuTTY会话(.ppk)识别 | 正则 | 文件解析后 | 文件内容文本 | `PuTTY-User-Key-File-2:\s*[^\r\n]+` | L3.ENTITY.CRED.PPK + L1.FILE.TYPE.PPK | 0.95 | P1 | 无 | 合并多选 | `PuTTY-User-Key-File-2: ssh-rsa` → L3.ENTITY.CRED.PPK + L1.FILE.TYPE.PPK, 置信度0.95 |
| 46 | RDP文件识别 | 正则 | 文件上传后/解析后 | 文件内容文本 | `full\s+address:s:[^\r\n]+` | L1.FILE.TYPE.RDP + L3.ENTITY.CRED.RDPCRED | 0.90 | P1 | 无 | 合并多选 | `full address:s:192.168.1.1:3389` → L1.FILE.TYPE.RDP + L3.ENTITY.CRED.RDPCRED, 置信度0.90 |
| 47 | VPN配置(OpenVPN)识别 | 正则 | 文件解析后 | 文件内容文本 | `(?m)^(client\|dev\s+\w+\|remote\s+[^\s]+\s+\d+\|<ca>\|<cert>\|<key>)` | L1.FILE.TYPE.VPN | 0.90 | P1 | 无 | 合并多选 | `client\nremote vpn.example.com 1194` → L1.FILE.TYPE.VPN, 置信度0.90 |
| 48 | KeePass数据库识别 | 正则 | 文件上传后 | 文件二进制(头部字节) | Magic `03 D9 A2 9A`(hex) | L1.FILE.TYPE.KDBX | 0.99 | P1 | 无 | 与扩展Magic Number字典匹配冲突时合并多选 | 头部 `03 D9 A2 9A 67 FB 4B 24` → L1.FILE.TYPE.KDBX, 置信度0.99 |
| 49 | AD域SID识别 | 正则 | 文件解析后 | 文件内容文本/注册表 | `S-1-5-21-\d+-\d+-\d+-\d+` | L3.ENTITY.AD.SID | 0.95 | P1 | 无 | 合并多选 | `S-1-5-21-3693859775-2112003782-2659238173-1000` → L3.ENTITY.AD.SID, 置信度0.95 |
| 50 | SPN服务主体名识别 | 正则 | 文件解析后 | 文件内容文本 | `[A-Za-z][A-Za-z0-9\-]*/[A-Za-z0-9\-._]+(:\d+)?` 且上下文含 `setspn`/`SPN`/`kerberoast` | L3.ENTITY.AD.SPN | 0.85 | P1 | 无 | 与 HOST/SERVICE 服务实体去重,合并多选 | `HTTP/web.corp.com:80` → L3.ENTITY.AD.SPN, 置信度0.85 |
| 51 | CWE编号识别 | 正则 | 文件解析后 | 文件内容文本 | `\bCWE-\d{1,4}\b` | L3.ENTITY.VULN.CWE | 0.99 | P2 | 无 | 合并多选 | `CWE-79: XSS` → L3.ENTITY.VULN.CWE, 置信度0.99 |
| 52 | CNVD编号识别 | 正则 | 文件解析后 | 文件内容文本 | `\bCNVD-\d{4}-\d{4,}\b` | L3.ENTITY.VULN.CNVD | 0.99 | P2 | 无 | 合并多选 | `CNVD-2024-12345` → L3.ENTITY.VULN.CNVD, 置信度0.99 |
| 53 | CNNVD编号识别 | 正则 | 文件解析后 | 文件内容文本 | `\bCNNVD-\d{6}-\d{3,}\b` | L3.ENTITY.VULN.CNNVD | 0.99 | P2 | 无 | 合并多选 | `CNNVD-202401-0123` → L3.ENTITY.VULN.CNNVD, 置信度0.99 |
| 54 | WMI订阅持久化识别 | 正则 | 文件解析后 | 文件内容文本/脚本 | `(__EventFilter\|__EventConsumer\|CommandLineEventConsumer\|ActiveScriptEventConsumer\|CommandLineTemplate)` | L3.ENTITY.REGKEY.WMI + L4.SCENE.PERSIST.MECHANISM=WMI | 0.85 | P0 | 无 | 合并多选 | `__EventFilter + CommandLineEventConsumer` → L3.ENTITY.REGKEY.WMI + L4.SCENE.PERSIST.MECHANISM=WMI, 置信度0.85 |
| 55 | IFEO镜像劫持识别 | 正则 | 文件解析后 | 注册表/文件内容文本 | `Image File Execution Options\\[^\s\"']+` | L3.ENTITY.REGKEY.IFEO + L4.SCENE.PERSIST.MECHANISM=IFEO | 0.90 | P0 | 无 | 合并多选 | `Image File Execution Options\sethc.exe` → L3.ENTITY.REGKEY.IFEO + L4.SCENE.PERSIST.MECHANISM=IFEO, 置信度0.90 |
| 56 | AppInit_DLLs持久化识别 | 正则 | 文件解析后 | 注册表/文件内容文本 | `AppInit_DLLs` | L3.ENTITY.REGKEY.APPINIT + L4.SCENE.PERSIST.MECHANISM=APPINIT | 0.90 | P1 | 无 | 合并多选 | `HKLM\...\AppInit_DLLs` → L3.ENTITY.REGKEY.APPINIT + L4.SCENE.PERSIST.MECHANISM=APPINIT, 置信度0.90 |
| 57 | Winlogon Shell持久化识别 | 正则 | 文件解析后 | 注册表/文件内容文本 | `(Winlogon\\Shell\|Userinit)` | L3.ENTITY.REGKEY.WINLOGON + L4.SCENE.PERSIST.MECHANISM=REGISTRY | 0.85 | P1 | 无 | 合并多选 | `HKLM\...\Winlogon\Shell` → L3.ENTITY.REGKEY.WINLOGON + L4.SCENE.PERSIST.MECHANISM=REGISTRY, 置信度0.85 |
| 58 | PowerShell Empire cradle识别 | 正则 | 文件解析后 | 脚本/文件内容文本 | `(?i)(IEX\s*\(\s*\(\s*New-Object\s+Net\.WebClient\s*\)\.DownloadString\|Invoke-Expression\s*\(\s*\(\s*New-Object\s+Net\.WebClient)` | L5.INTEL.MALWARE.EMPIRE | 0.80 | P1 | 无 | 合并多选 | `IEX (New-Object Net.WebClient).DownloadString('http://...')` → L5.INTEL.MALWARE.EMPIRE, 置信度0.80 |

### 8.2 字典匹配规则(DICT)— 共 23 条

| 序号 | 规则描述 | 规则类型 | 触发时机 | 输入数据 | 规则表达式/模型 | 产出标签 | 输出置信度 | 优先级 | 前置依赖 | 冲突处理 | 示例 |
|------|----------|----------|----------|----------|----------------|----------|-----------|--------|----------|----------|------|
| 1 | 文件扩展名字典匹配 | 字典 | 文件上传后 | 文件扩展名 | `{pdf:PDF, docx:DOCX, xlsx:XLSX, pptx:PPTX, eml:EML, exe:EXE, dll:DLL, pcap:PCAP, zip:ZIP, rar:RAR, png:PNG, jpg:JPG, log:LOG, py:PY, bin:BIN, txt:TXT, html:HTML}` | L1.FILE.TYPE.* | 0.85 | P0 | 无 | 与 Magic Number 字典匹配冲突时以 Magic Number 为准 | `report.pdf` → L1.FILE.TYPE.PDF, 置信度0.85 |
| 2 | APT组织字典匹配 | 字典 | 智能分析后 | 文件内容文本/提取实体列表 | `{APT29:舒适熊, APT28:花式熊, Lazarus:拉扎勒斯, Conti:康蒂勒索, FIN7:金融犯罪组织, APT41:双尾, Turla:蛇形}` | L5.INTEL.APT.* | 0.8 | P1 | 无 | 合并多选 | `APT29` 命中 → L5.INTEL.APT.APT29, 置信度0.8 |
| 3 | 恶意软件家族字典匹配 | 字典 | 智能分析后 | 文件内容文本/二进制 | `{Cobalt Strike, Metasploit, Mimikatz, Emotet, TrickBot, Ryuk, WannaCry, Meterpreter}` | L5.INTEL.MALWARE.* | 0.8 | P1 | 无 | 合并多选 | `Cobalt Strike` 命中 → L5.INTEL.MALWARE.COBALT_STRIKE, 置信度0.8 |
| 4 | 高危端口字典匹配 | 字典 | 文件解析后 | 提取端口实体 | `{22:SSH, 23:Telnet, 135:MSRPC, 137:NetBIOS, 139:NetBIOS, 445:SMB, 1433:MSSQL, 1521:Oracle, 3306:MySQL, 3389:RDP, 5432:PostgreSQL, 5900:VNC, 6379:Redis, 27017:MongoDB}` | L3.ENTITY.PORT.HIGHRISK | 0.9 | P0 | 端口提取 | 合并多选 | `port=3389` → L3.ENTITY.PORT.HIGHRISK, 置信度0.9 |
| 5 | 网络服务字典匹配 | 字典 | 文件解析后 | 文件内容文本 | `{www, http, https, nginx, apache, iis → WEB; mysql, mssql, postgres, oracle, redis, mongodb → DB; ssh, rdp, vnc, telnet → REMOTE; ftp, smb, nfs → FILE}` | L3.ENTITY.SERVICE.* | 0.85 | P1 | 无 | 合并多选 | `nginx` → L3.ENTITY.SERVICE.WEB, 置信度0.85 |
| 6 | ATT&CK技术字典匹配 | 字典 | 智能分析后 | 文件内容文本/提取实体列表 | `{T1078:TA0001, T1059:TA0002, T1547:TA0003, T1068:TA0004, T1027:TA0005, T1003:TA0006, T1087:TA0007, T1021:TA0008, T1560:TA0009, T1041:TA0010, T1486:TA0011}` | L5.INTEL.TTP.* | 0.85 | P1 | 无 | 合并多选 | `T1059` → L5.INTEL.TTP.TA0002, 置信度0.85 |
| 7 | 敏感关键词字典匹配 | 字典 | 文件解析后 | 文件内容文本 | `{绝密, 机密, 秘密, 内部, 涉密, 武器装备, 作战方案, 部队番号, 战备, 核心密码}` | L6.COMP.CLASSIFICATION.* | 0.85 | P0 | 无 | 高优先级覆盖低优先级,按关键词最高密级判定 | `绝密` → L6.COMP.CLASSIFICATION.TOPSECRET, 置信度0.85 |
| 8 | Magic Number字典匹配 | 字典 | 文件上传后 | 文件二进制(头部字节) | `{25:50:44:46→PDF, 50:4B:03:04→ZIP/DOCX/XLSX, 4D:5A→EXE/DLL, D4:C3:B2:A1→PCAP, 89:50:4E:47→PNG, FF:D8:FF→JPG, 52:61:72:21→RAR}` | L1.FILE.TYPE.* | 0.95 | P0 | 无 | 高优先级覆盖文件扩展名字典匹配 | 头部 `25 50 44 46` → L1.FILE.TYPE.PDF, 置信度0.95 |
| 9 | 网络设备厂商字典匹配 | 字典 | 文件解析后 | 文件内容文本 | `{Cisco, 华为, 华三, H3C, Juniper, 锐捷, 迈普, 山石, 深信服}` + 型号正则 | L3.ENTITY.HOST.NETWORK | 0.8 | P2 | 无 | 合并多选 | `Cisco` → L3.ENTITY.HOST.NETWORK, 置信度0.8 |
| 10 | Webshell文件名字典匹配 | 字典 | 文件上传后/解析后 | 文件名 | `{c99.php, r57.php, wso.php, shell.php, cmd.php, eval.php, x.php, 1.php, mm.php, b374k.php, indoxploit.php, wsoshell.php}` | L1.FILE.TYPE.PHP + 触发 Webshell→持久化关联 | 0.9 | P0 | 无 | 合并多选 | `c99.php` → L1.FILE.TYPE.PHP + Webshell 标记, 置信度0.9 |
| 11 | 弱口令字典匹配 | 字典 | 文件解析后 | 文件内容文本(凭证上下文) | `{123456, admin, password, root, 12345678, qwerty, abc123, 111111, 1234567890, password123, admin123, 000000, 12345, iloveyou, 1234567}` | L3.ENTITY.CRED.PASSWORD + 触发 L4.SCENE.CREDENTIAL.USABILITY=AVAILABLE | 0.9 | P0 | 数据库连接串识别、凭证模式识别模型 | 合并多选;弱口令可被攻击者轻易利用,可用性=AVAILABLE | `password=admin` → L3.ENTITY.CRED.PASSWORD, 置信度0.9 |
| 12 | 私网IP CIDR字典匹配 | 字典 | 文件解析后 | 提取 IP 实体列表 | `{10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 100.64.0.0/10, 169.254.0.0/16}` | L3.ENTITY.IP.PRIVATE | 0.95 | P0 | IPv4地址提取 | 高优先级覆盖低优先级;与私网IPv4段识别同优先级同置信度,CIDR 精确匹配优先于正则模式匹配 | `10.0.0.5` → L3.ENTITY.IP.PRIVATE, 置信度0.95 |
| 13 | 扩展Magic Number字典匹配 | 字典 | 文件上传后 | 文件二进制(头部字节) | 扩展 Magic Number 字典:`{PK\x03\x04+[Content_Types].xml→DOCX/XLSX/PPTX, 7F:45:4C:46→ELF, CF:FA:ED:FE→Mach-O64, FE:ED:FA:CE→Mach-O32, PK\x03\x04+META-INF/MANIFEST.MF→JAR, PK\x03\x04+AndroidManifest.xml→APK, 25:50:44:46→PDF, FF:D8:FF→JPG, 89:50:4E:47→PNG, D0:CF:11:E0→DOC/XLS(旧Office), 1F:8B→GZIP, 42:5A:68→BZIP2, FD:37:7A:5A:58→XZ}` | L1.FILE.TYPE.* | 0.95 | P0 | 无 | 高优先级覆盖文件扩展名字典匹配/Magic Number 字典匹配 | 头部 `7F 45 4C 46` → L1.FILE.TYPE.ELF, 置信度0.95 |
| 14 | ATT&CK全量战术字典匹配 | 字典 | 智能分析后 | 文件内容文本/提取实体列表 | ATT&CK 11 战术全量映射:`{TA0001:[T1078,T1190,T1193,T1195], TA0002:[T1059,T1053,T1106,T1129], TA0003:[T1547,T1136,T1543,T1098], TA0004:[T1068,T1548,T1078,T1134], TA0005:[T1027,T1140,T1036,T1112], TA0006:[T1003,T1110,T1552,T1056], TA0007:[T1087,T1046,T1018,T1082], TA0008:[T1021,T1570,T1072,T1550], TA0009:[T1560,T1119,T1005,T1213], TA0010:[T1041,T1048,T1047,T1090], TA0011:[T1486,T1490,T1485,T1561]}` | L5.INTEL.TTP.* | 0.85 | P1 | 无 | 合并多选 | `T1059` → L5.INTEL.TTP.TA0002, 置信度0.85 |
| 15 | 红队工具字典匹配 | 字典 | 智能分析后 | 文件内容文本/二进制 | `{Cobalt Strike, Metasploit, Empire, Covenant, Sliver, Brute Ratel, Havoc, Mythic, PoshC2, Merlin, Starkiller, Manuka}` | L5.INTEL.MALWARE.* + L4.SCENE.LATERAL | 0.85 | P1 | 无 | 合并多选 | `Cobalt Strike` → L5.INTEL.MALWARE.COBALT_STRIKE, 置信度0.85 |
| 16 | 红队C2框架特征字典 | 字典 | 智能分析后 | 可执行文件/配置文件 | `{Sliver:"Sliver", BruteRatel:"BruteRatel", Mythic:"Mythic", Havoc:"Havoc", Empire:"Empire", Covenant:"Covenant", PoshC2:"PoshC2", Merlin:"Merlin", Starkiller:"Starkiller"}` + YARA规则集匹配PE特征 | L5.INTEL.MALWARE.SLIVER + L5.INTEL.MALWARE.BRUTERATEL + L5.INTEL.MALWARE.MYTHIC + L5.INTEL.MALWARE.HAVOC + L5.INTEL.MALWARE.EMPIRE + L5.INTEL.MALWARE.COVENANT | 0.85 | P0 | 无 | 与红队工具字典匹配/恶意软件家族字典匹配去重,合并多选 | Sliver C2 Beacon配置/Brute Ratel Badger特征 → L5.INTEL.MALWARE.SLIVER/BRUTERATEL, 置信度0.85 |
| 17 | 国产Webshell框架字典 | 字典 | 文件解析后 | 脚本文件/流量 | `{哥斯拉:"Godzilla", 冰蝎:"Behinder", 蚁剑:"AntSword", 菜刀:"Chopper", Altman:"Altman", C刀:"Cknife"}` + Webshell内容特征(eval/base64_decode/assert/system) | L4.SCENE.PERSIST.BACKDOOR_TYPE=WEBSHELL | 0.85 | P0 | 无 | 与Webshell文件名识别/Webshell文件名字典匹配合并多选 | 哥斯拉PHP脚本/冰蝎AES加密流量 → L4.SCENE.PERSIST.BACKDOOR_TYPE=WEBSHELL, 置信度0.85 |
| 18 | 打包器/混淆器字典 | 字典 | 智能分析后 | 可执行文件 | `{UPX:"UPX0/UPX1节名", Themida:"Themida特征字节", VMProtect:"VMP特征", Enigma:"Enigma特征", Donut:"Donut特征", Confuser:"ConfuserEx特征", MPRESS:"MPRESS特征", ASPack:"ASPack特征"}` + PE节区熵值分析 | L5.INTEL.PACKER.UPX + L5.INTEL.PACKER.THEMIDA + L5.INTEL.PACKER.VMPROT + L5.INTEL.PACKER.DONUT | 0.80 | P1 | 无 | 与MalwareML恶意文件分类去重,合并多选 | UPX0/UPX1节名/Themida节区 → L5.INTEL.PACKER.UPX/THEMIDA, 置信度0.80 |
| 19 | AV/EDR产品字典 | 字典 | 文件解析后 | 进程列表/文件 | `{MsMpEng:"Windows Defender", CSFalconService:"CrowdStrike", SentinelAgent:"SentinelOne", CbDefense:"CarbonBlack", mcshield:"McAfee", rtvscan:"Norton", Tmccsf:"TrendMicro", bdservicehost:"百度卫士", QQPCTray:"QQ电脑管家", ZhuDongFangYu:"360主动防御"}` | L4.SCENE.EVASION.AV_EDR | 0.90 | P0 | 无 | 合并多选 | `MsMpEng.exe` → L4.SCENE.EVASION.AV_EDR(DEFENDER), 置信度0.90 |
| 20 | 弱口令字典扩充(200+) | 字典 | 文件解析后 | 文件内容文本(凭证上下文) | 通用:`{admin/admin/admin123/root/toor/guest/guest/test/test}`;中文常见:`{admin/admin@123/admin/P@ssw0rd/root/root@123/sa/sa@123/administrator/Admin123}`;企业默认:`{admin/Cisco123/admin/h3c123/admin/huawei@123/oracle/oracle/postgres/postgres/redis/redis/mongo/mongo/admin/Admin@123}`;设备默认:`{admin/admin/admin/password/admin/123456/admin/admin@huawei/admin/admin@h3c}` | L3.ENTITY.CRED.PASSWORD | 0.70 | P1 | 弱口令字典匹配 | 合并多选;与弱口令字典匹配同标签合并,以置信度高者(0.9)胜出 | `admin/admin@123`(P0级企业弱口令) → L3.ENTITY.CRED.PASSWORD, 置信度0.70 |
| 21 | AD域控特征字典 | 字典 | 文件解析后 | 文件内容文本/命令记录 | `{NTDS.DIT:"NTDS.DIT", SYSVOL:"SYSVOL", NETLOGON:"NETLOGON", krbtgt:"krbtgt账户", PDC:"PDC Emulator", ldap_dc:"_ldap._tcp.dc", CurrentDomain:"CurrentDomain", GetADDomain:"GetADDomain", dsquery:"dsquery server", nltest:"nltest /dclist"}` | L3.ENTITY.AD.DC | 0.85 | P1 | 无 | 合并多选 | `ntds.dit / krbtgt / _ldap._tcp.dc._msdcs` → L3.ENTITY.AD.DC, 置信度0.85 |
| 22 | CMS指纹字典 | 字典 | 文件解析后 | HTTP响应/HTML | `{WordPress:"wp-content/wp-includes", Joomla:"components/com_", Drupal:"sites/all/modules", Shiro:"rememberMe=deleteMe", Struts:".action/.do", ThinkPHP:"index.php?s=", Spring:"actuator/env", weblogic:"wlw_manifest", Tomcat:"manager/html", phpMyAdmin:"phpmyadmin", Nacos:"nacos/v1", XXL-Job:"xxl-job-admin"}` | L4.SCENE.TOPOLOGY.CMS | 0.85 | P1 | 无 | 合并多选 | `wp-content/` → L4.SCENE.TOPOLOGY.CMS(WORDPRESS), 置信度0.85 |
| 23 | 横向移动工具字典 | 字典 | 智能分析后 | 文件/命令记录 | `{PsExec:"PsExec.exe", WMIC:"wmic", WinRM:"Enter-PSSession/New-PSSession", CrackMapExec:"cme/crackmapexec", NetExec:"nxc/netexec", Impacket:"impacket/secretsdump/wmiexec/psexec", Atexec:"atexec.py", Smbexec:"smbexec.py", Dcomexec:"dcomexec.py", Rubeus:"Rubeus", SharpRoast:"SharpRoast", Mimikatz:"mimikatz/sekurlsa", PowerCat:"powercat", SharpHound:"SharpHound.exe"}` | L4.SCENE.LATERAL.TECHNIQUE | 0.85 | P1 | 无 | 与横向移动工具关联路径合并多选 | `PsExec.exe / CrackMapExec / secretsdump` → L4.SCENE.LATERAL.TECHNIQUE, 置信度0.85 |

### 8.3 模型识别规则(ML)— 共 20 条

| 序号 | 规则描述 | 规则类型 | 触发时机 | 输入数据 | 规则表达式/模型 | 产出标签 | 输出置信度 | 优先级 | 前置依赖 | 冲突处理 | 示例 |
|------|----------|----------|----------|----------|----------------|----------|-----------|--------|----------|----------|------|
| 1 | MalwareML恶意文件分类 | 模型 | 智能分析阶段 | 文件二进制 | MalwareML 恶意文件分类模型(score ≥ 0.7→HIGH, 0.4-0.7→MEDIUM, <0.4→LOW, 白名单→BENIGN) | L5.INTEL.THREAT.* | 0.7-0.95(按 score) | P0 | Magic Number 字典匹配 | 置信度高者胜出 | `score=0.85` → L5.INTEL.THREAT.HIGH, 置信度0.85 |
| 2 | NER命名实体识别 | 模型 | 文件解析后 | 文件内容文本 | NER 命名实体识别模型(BERT-base-chinese-NER),识别 IP/域名/邮箱/URL/凭证/漏洞等实体 | L3.ENTITY.* | 0.7-0.95 | P0 | 无 | 合并多选 | `192.168.1.1` → L3.ENTITY.IP, 置信度0.85 |
| 3 | MalwareML行为特征分析 | 模型 | 智能分析阶段 | API 调用序列 | MalwareML 行为特征模型(基于 API 调用序列映射 ATT&CK 技术) | L5.INTEL.TTP.* | 0.7-0.9 | P1 | MalwareML恶意文件分类 | 合并多选 | API 序列含 CreateProcess → L5.INTEL.TTP.TA0002, 置信度0.8 |
| 4 | 文本分类模型 | 模型 | 智能分析阶段 | 文件内容文本 | 文本分类模型(基于文件内容判断 BENIGN/SUSPICIOUS/MALICIOUS/UNKNOWN/REVIEW) | L2.ANALYZE.RESULT.* | 0.7-0.9 | P1 | 无 | 合并多选 | `MALICIOUS` → L2.ANALYZE.RESULT.MALICIOUS, 置信度0.8 |
| 5 | 图像OCR识别 | 模型 | 文件解析后 | 图片(PNG/JPG) | 图像 OCR 识别模型(PaddleOCR)+ NER | L3.ENTITY.* | 0.7-0.85 | P2 | Magic Number 字典匹配 | 合并多选 | PNG 截图含 IP → L3.ENTITY.IP, 置信度0.75 |
| 6 | 凭证模式识别模型 | 模型 | 文件解析后 | 文件内容文本 | 凭证模式识别模型(基于上下文判断 password/hash/key/cert/token/session) | L3.ENTITY.CRED.* | 0.75-0.9 | P1 | NER命名实体识别 | 合并多选 | `password=admin123` → L3.ENTITY.CRED.PASSWORD, 置信度0.85 |
| 7 | 语言识别模型 | 模型 | 文件解析后 | 文件内容文本 | 语言识别模型(基于 Unicode 字符分布判定 ZH/EN/JA/RU/MULTI/UNKNOWN) | L1.FILE.LANG.* | 0.85-0.95 | P2 | 无 | 置信度高者胜出 | 中文占比 80% → L1.FILE.LANG.ZH, 置信度0.95 |
| 8 | PE特征模型 | 模型 | 智能分析阶段 | 文件二进制(PE 文件) | PE 特征模型(基于导入表/导出表/节区特征判断恶意) | L5.INTEL.THREAT.* + L2.ANALYZE.RESULT.* | 0.75-0.9 | P1 | Magic Number 字典匹配、扩展Magic Number 字典匹配 | 置信度高者胜出 | PE 含恶意导入表 → L5.INTEL.THREAT.HIGH, 置信度0.85 |
| 9 | YARA规则匹配引擎 | 模型 | 智能分析阶段 | 文件二进制 | YARA 规则匹配引擎(基于 YARA 规则集扫描文件) | L5.INTEL.MALWARE.* + L3.ENTITY.IOC.FILE_HASH | 0.9-0.95 | P0 | 无 | 合并多选 | YARA 命中 Mimikatz 规则 → L5.INTEL.MALWARE.MIMIKATZ, 置信度0.95 |
| 10 | 文档元数据提取模型 | 模型 | 文件解析后 | 文件二进制(PDF/Office) | 文档元数据提取模型(从 PDF/Office 文档提取作者/创建时间/软件信息) | L3.ENTITY.USER | 0.85-0.95 | P1 | Magic Number 字典匹配、扩展Magic Number 字典匹配 | 合并多选 | PDF 作者 `admin` → L3.ENTITY.USER, 置信度0.9 |
| 11 | 网络流量分析模型 | 模型 | 智能分析阶段 | PCAP 文件 | 网络流量分析模型(对 PCAP 文件提取会话/协议/数据流特征) | L3.ENTITY.IP + L3.ENTITY.PORT + L4.SCENE.TOPOLOGY.* | 0.8-0.9 | P1 | Magic Number 字典匹配 | 合并多选 | PCAP 含 HTTP 会话 → L3.ENTITY.SERVICE.WEB, 置信度0.85 |
| 12 | 凭证强度评估模型 | 模型 | 智能分析阶段 | 提取凭证实体列表 | 凭证强度评估模型(评估密码强度/哈希类型/可破解性) | L4.SCENE.CREDENTIAL.STATUS + L4.SCENE.CREDENTIAL.USABILITY | 0.75-0.9 | P1 | 凭证模式识别模型 | 合并多选 | 弱密码 `123456` → STATUS=VALID, USABILITY=AVAILABLE, 置信度0.9 |
| 13 | Shellcode识别模型 | 模型 | 智能分析阶段 | 可执行文件/脚本/文档 | ShellcodeML:基于字节序列熵值(>0.9 高熵)+指令模式识别(GetPC/decode stub/FLDPI/0xEB0E)+常见 shellcode 模式(Metasploit/Cobalt Strike/MSFvenom)特征匹配 | L5.INTEL.TTP.TA0005 + L5.INTEL.THREAT | 0.80 | P1 | Magic Number 字典匹配、扩展Magic Number字典匹配 | 合并多选 | 高熵字节+GetPC模式 → L5.INTEL.TTP.TA0005 + L5.INTEL.THREAT, 置信度0.80 |
| 14 | 代码混淆检测模型 | 模型 | 智能分析阶段 | 脚本/可执行文件 | ObfuscationML:检测变量名随机化(熵值>4.0)+控制流扁平化(基本块扇出>10)+字符串加密(XOR/Base64 比例>30%)+死代码插入+指令模式异常 | L4.SCENE.EVASION.OBFUSCATION | 0.75 | P1 | 无 | 合并多选 | PowerShell 变量名随机化+字符串加密 → L4.SCENE.EVASION.OBFUSCATION(ENCODING/ENCRYPTION), 置信度0.75 |
| 15 | 打包器识别模型 | 模型 | 智能分析阶段 | 可执行文件 | PackerML:基于 PE 节区特征(节名/熵值/虚拟大小/原始大小比)+入口点 OEP 特征+导入表特征+TLS 回调+资源节特征,识别 UPX/Themida/VMProtect/Enigma/Donut/Confuser 等 | L5.INTEL.PACKER.UPX + L5.INTEL.PACKER.THEMIDA + L5.INTEL.PACKER.VMPROT + L5.INTEL.PACKER.DONUT | 0.85 | P1 | Magic Number 字典匹配、扩展Magic Number字典匹配 | 与打包器/混淆器字典交叉验证,合并多选 | UPX0/UPX1节+熵值0.9 → L5.INTEL.PACKER.UPX, 置信度0.85 |
| 16 | Webshell内容检测模型 | 模型 | 智能分析阶段 | 脚本文件 | WebshellML:基于 PHP/JSP/ASP 内容特征(危险函数组合:eval+system/exec+base64_decode/assert+preg_replace)+文件路径特征+参数传递模式+编码混淆检测,超越文件名检测 | L4.SCENE.PERSIST.BACKDOOR_TYPE=WEBSHELL | 0.85 | P0 | Webshell文件名识别、Webshell文件名字典匹配 | 与国产Webshell框架字典交叉验证,合并多选 | `eval($_POST['cmd'])` → L4.SCENE.PERSIST.BACKDOOR_TYPE=WEBSHELL, 置信度0.85 |
| 17 | Office宏代码检测模型 | 模型 | 智能分析阶段 | Office文档 | MacroML:检测 VBA 宏中的恶意调用模式(CreateProcess/Shell/WinExec/URLDownloadToFile/CreateObject+WScript.Shell)+自动执行宏(AutoOpen/DocumentOpen)+字符串拼接混淆+DDE 调用 | L5.INTEL.TTP.TA0002 + L2.ANALYZE.RESULT.MALICIOUS | 0.80 | P1 | Magic Number 字典匹配、扩展Magic Number字典匹配 | 合并多选 | AutoOpen+CreateProcess → L5.INTEL.TTP.TA0002 + L2.ANALYZE.RESULT.MALICIOUS, 置信度0.80 |
| 18 | 内存取证特征模型 | 模型 | 智能分析阶段 | 内存镜像文件 | MemoryForensicML:对内存镜像提取进程列表(签名匹配)+凭证(Mimikatz特征/LSASS加密blob)+注入代码(可写可执行页)+网络连接+注册表 hive+计划任务,输出结构化取证结果 | L3.ENTITY.CRED.LSASS + L4.SCENE.CREDENTIAL.SOURCE=MEMORY | 0.85 | P0 | 文件类型为 RAW/DMP(Magic Number 字典匹配、扩展Magic Number字典匹配) | 合并多选 | LSASS进程+凭证blob → L3.ENTITY.CRED.LSASS + L4.SCENE.CREDENTIAL.SOURCE=MEMORY, 置信度0.85 |
| 19 | DGA域名检测模型 | 模型 | 智能分析阶段 | 域名/DNS日志 | DGAML:基于域名 LSTM 熵值(>3.5)+词频异常(不含常见英文单词)+N-gram 概率(异常低)+域名长度(>12)+TLD 异常+字符分布均匀度,检测 DGA 域名 | L3.ENTITY.DOMAIN.DGA | 0.80 | P1 | 域名提取 | 合并多选 | `xkqjfwtbpl.com` → L3.ENTITY.DOMAIN.DGA, 置信度0.80 |
| 20 | 流量入侵特征模型 | 模型 | 智能分析阶段 | 流量包(PCAP) | NIDSML:对 PCAP 检测攻击流量(SYN 扫描/端口爆破/SQL 注入特征/横向移动 SMB/Kerberos 异常/C2 beacon 周期性/数据外带流量峰值),输出攻击流量标记 | L5.INTEL.TTP.TA0006 + L5.INTEL.TTP.TA0008 | 0.80 | P1 | 文件类型为 PCAP(Magic Number 字典匹配) | 合并多选 | 周期性 beacon → L5.INTEL.TTP.TA0006 + L5.INTEL.TTP.TA0008(C2 通信), 置信度0.80 |

> **模型可用性与降级策略**:PRD 模型清单含 6 个已部署模型(BGE-large-zh/BGE-M3/PaddleOCR/BERT-NER/MalwareML/YARA)。MalwareML恶意文件分类/NER命名实体识别/图像OCR识别/YARA规则匹配引擎 直接引用已部署模型;MalwareML行为特征分析 复用 MalwareML 行为特征分支;文本分类模型/凭证模式识别模型/语言识别模型/PE特征模型/文档元数据提取模型/网络流量分析模型/凭证强度评估模型 引用待建模型,上线前需在 AI 模型管理模块(F3.4.10)注册并部署。待建模型不可用时降级策略:文本分类模型 降级为 敏感关键词字典匹配;凭证模式识别模型 降级为 JWT Token识别/PEM私钥头识别/数据库连接串识别/AWS Access Key ID识别/AWS Secret Key识别/NTLM Hash识别 正则模式;语言识别模型 降级为 L1.FILE.LANG 字典的 Unicode 字符占比规则;PE特征模型 降级为 Magic Number 字典匹配 + YARA(YARA规则匹配引擎)兜底;文档元数据提取模型 降级为跳过(无替代);网络流量分析模型 降级为 IPv4地址提取/端口提取 IP+端口正则;凭证强度评估模型 降级为 弱口令字典匹配。新增待建模型(Shellcode识别模型/代码混淆检测模型/打包器识别模型/Webshell内容检测模型/Office宏代码检测模型/内存取证特征模型/DGA域名检测模型/流量入侵特征模型)上线前需在 AI 模型管理模块(F3.4.10)注册并部署;降级策略:Shellcode识别模型 降级为 YARA(YARA规则匹配引擎)shellcode 规则集兜底;代码混淆检测模型 降级为 Base64编码识别+PowerShell加密命令识别 正则模式;打包器识别模型 降级为 打包器/混淆器字典;Webshell内容检测模型 降级为 Webshell文件名识别+Webshell文件名字典匹配+国产Webshell框架字典;Office宏代码检测模型 降级为跳过(无替代);内存取证特征模型 降级为跳过(无替代,需 volatility 插件);DGA域名检测模型 降级为 域名提取+词频统计规则;流量入侵特征模型 降级为 网络流量分析模型+IPv4地址提取/端口提取 正则。

### 8.4 关联推导规则(ASSOC)— 共 30 条

| 序号 | 规则描述 | 规则类型 | 触发时机 | 输入数据 | 规则表达式/模型 | 产出标签 | 输出置信度 | 优先级 | 前置依赖 | 冲突处理 | 示例 |
|------|----------|----------|----------|----------|----------------|----------|-----------|--------|----------|----------|------|
| 1 | 文件IP命中目标资产段关联 | 关联 | 实体入库后 | 提取 IP 实体列表 | 文件提取 IP 命中目标 T001 关联 IP 段 | L4.SCENE.PROFILE.ASSET | 0.85 | P0 | IPv4地址提取、私网IPv4段识别 | 合并多选 | 文件 IP ∈ T001 资产段 → L4.SCENE.PROFILE.ASSET, 置信度0.85 |
| 2 | IP/域名命中C2情报关联 | 关联 | 实体入库后 | 提取 IP/域名实体列表 | 文件提取 IP/域名命中威胁情报 C2 库 | L5.INTEL.THREAT.HIGH + L3.ENTITY.IP.C2 + L3.ENTITY.IOC.C2_DOMAIN | 0.95 | P0 | IPv4地址提取、域名提取 | 高优先级覆盖低优先级 | IP 命中 C2 库 → L5.INTEL.THREAT.HIGH, 置信度0.95 |
| 3 | 有效凭证关联密级 | 关联 | 实体入库后 | 提取凭证实体列表 | 文件提取凭证且凭证状态=有效 | L4.SCENE.CREDENTIAL.STATUS=VALID + L6.COMP.CLASSIFICATION.CONFIDENTIAL | 0.9 | P0 | 凭证模式识别模型、凭证强度评估模型 | 合并多选 | 凭证有效 → L4.SCENE.CREDENTIAL.STATUS=VALID, 置信度0.9 |
| 4 | 高危漏洞关联可利用性 | 关联 | 实体入库后 | 提取 CVE 实体列表 | 文件提取 CVE 漏洞且 CVSS ≥ 7.0 | L4.SCENE.VULN.EXPLOITABILITY=EXPLOITABLE + L4.SCENE.VULN.IMPACT=HIGH | 0.9 | P0 | CVE漏洞编号提取 | 合并多选 | CVE-2024-12345 CVSS=9.8 → L4.SCENE.VULN.IMPACT=HIGH, 置信度0.9 |
| 5 | 横向移动工具关联路径 | 关联 | 实体入库后 | 文件内容文本 | 文件含横向移动工具特征(PsExec/WMIC/Cobalt Strike) | L4.SCENE.LATERAL.PATH + L4.SCENE.LATERAL.PIVOT | 0.9 | P0 | 红队工具字典匹配、Cobalt Strike特征识别 | 合并多选 | 含 PsExec → L4.SCENE.LATERAL.PATH, 置信度0.9 |
| 6 | 敏感关键词关联密级 | 关联 | 文件解析后 | 文件内容文本 | 文件命中敏感关键词字典(按关键词最高密级判定:绝密>机密>秘密>内部) | L6.COMP.CLASSIFICATION.{SECRET/CONFIDENTIAL/TOPSECRET} | 0.85 | P0 | 敏感关键词字典匹配 | 高优先级覆盖低优先级 | `绝密` → L6.COMP.CLASSIFICATION.TOPSECRET, 置信度0.85 |
| 7 | 多主机设备关联拓扑 | 关联 | 实体入库后 | 提取主机/网络设备实体列表 | 文件提取主机/网络设备实体(数量 ≥ 2) | L4.SCENE.TOPOLOGY.NODE + L4.SCENE.TOPOLOGY.LINK | 0.85 | P1 | 网络设备厂商字典匹配、NER命名实体识别 | 合并多选 | 含 3 台主机 → L4.SCENE.TOPOLOGY.NODE, 置信度0.85 |
| 8 | 多目标覆盖关联画像完整度 | 关联 | 实体入库后 | 文件关联目标数 | 文件关联目标数 ≥ 3 且画像覆盖率 ≥ 80% | L4.SCENE.PROFILE.COMPLETENESS=HIGH | 0.85 | P1 | 文件IP命中目标资产段关联、多主机设备关联拓扑 | 高优先级覆盖低优先级 | 覆盖 5 个目标 85% → L4.SCENE.PROFILE.COMPLETENESS=HIGH, 置信度0.85 |
| 9 | 恶意哈希命中关联威胁等级 | 关联 | 实体入库后 | 文件 SHA256 | 文件 SHA256 命中恶意文件哈希库 | L5.INTEL.THREAT.HIGH + L3.ENTITY.IOC.FILE_HASH | 0.95 | P0 | 无 | 高优先级覆盖低优先级 | SHA256 命中恶意库 → L5.INTEL.THREAT.HIGH, 置信度0.95 |
| 10 | Webshell关联持久化TTP | 关联 | 实体入库后 | 文件名/文件内容 | 文件含 Webshell 特征(Webshell文件名识别 或 Webshell文件名字典匹配 命中) | L2.ANALYZE.RESULT.MALICIOUS + L5.INTEL.TTP.TA0003 | 0.9 | P0 | Webshell文件名识别、Webshell文件名字典匹配 | 高优先级覆盖低优先级 | `c99.php` → L2.ANALYZE.RESULT.MALICIOUS, 置信度0.9 |
| 11 | PowerShell加密命令关联执行TTP | 关联 | 实体入库后 | 文件内容 | 文件含 PowerShell 加密命令(PowerShell加密命令识别 命中) | L5.INTEL.TTP.TA0002 + L4.SCENE.LATERAL | 0.9 | P0 | PowerShell加密命令识别 | 合并多选 | `powershell -enc ...` → L5.INTEL.TTP.TA0002, 置信度0.9 |
| 12 | 数据库连接串关联凭证密级 | 关联 | 实体入库后 | 文件内容 | 文件含数据库连接串(数据库连接串识别 命中) | L3.ENTITY.CRED + L6.COMP.CLASSIFICATION.CONFIDENTIAL | 0.9 | P0 | 数据库连接串识别 | 合并多选 | `mysql://root:pass@...` → L6.COMP.CLASSIFICATION.CONFIDENTIAL, 置信度0.9 |
| 13 | AWS密钥关联凭证密级 | 关联 | 实体入库后 | 文件内容 | 文件含 AWS 密钥(AWS Access Key ID识别 或 AWS Secret Key识别 命中) | L3.ENTITY.CRED.KEY + L6.COMP.CLASSIFICATION.CONFIDENTIAL | 0.95 | P0 | AWS Access Key ID识别、AWS Secret Key识别 | 高优先级覆盖低优先级 | `AKIAIOSFODNN7EXAMPLE` → L6.COMP.CLASSIFICATION.CONFIDENTIAL, 置信度0.95 |
| 14 | YARA恶意规则关联威胁等级 | 关联 | 实体入库后 | YARA 扫描结果 | 文件命中 YARA 恶意规则(YARA规则匹配引擎 命中) | L5.INTEL.THREAT.HIGH + L5.INTEL.MALWARE.* | 0.95 | P0 | YARA规则匹配引擎 | 高优先级覆盖低优先级 | YARA 命中 Emotet → L5.INTEL.THREAT.HIGH + L5.INTEL.MALWARE.EMOTET, 置信度0.95 |
| 15 | 多内网IP关联拓扑节点 | 关联 | 实体入库后 | 提取 IP 实体列表 | 文件含多个内网 IP(数量 ≥ 3) | L4.SCENE.TOPOLOGY.NODE + L4.SCENE.TOPOLOGY.LINK | 0.85 | P1 | 私网IPv4段识别、私网IP CIDR字典匹配 | 合并多选;与多主机设备关联拓扑同时触发时合并不覆盖 | 含 5 个内网 IP → L4.SCENE.TOPOLOGY.NODE, 置信度0.85 |
| 16 | RDP配置+凭证→可横向移动 | 关联 | 实体入库后 | RDP文件+凭证 | 文件含 L1.FILE.TYPE.RDP 且 含 L3.ENTITY.CRED.RDPCRED 且 L4.SCENE.CREDENTIAL.STATUS=VALID | L4.SCENE.LATERAL.TECHNIQUE=RDPHIJACK + L4.SCENE.LATERAL.PATH | 0.85 | P0 | RDP文件识别、凭证强度评估模型 | 合并多选 | RDP配置 `192.168.1.10` +有效凭证 → L4.SCENE.LATERAL.TECHNIQUE=RDPHIJACK, 置信度0.85 |
| 17 | SSH私钥+主机→可横向移动 | 关联 | 实体入库后 | SSH密钥+主机列表 | 文件含 L3.ENTITY.CRED.KEY(SSH私钥) 且 含 ≥1 个 L3.ENTITY.HOST | L4.SCENE.LATERAL.TECHNIQUE=SSH + L4.SCENE.LATERAL.PATH | 0.85 | P0 | PEM私钥头识别、PEM私钥完整格式识别 | 合并多选 | `id_rsa` + 主机列表 → L4.SCENE.LATERAL.TECHNIQUE=SSH, 置信度0.85 |
| 18 | AD域信息+域账户→可Kerberoasting | 关联 | 实体入库后 | AD信息文件 | 文件含 L3.ENTITY.AD.SPN 且 含 L3.ENTITY.USER(域账户) | L4.SCENE.LATERAL.TECHNIQUE=KERBEROAST + L4.SCENE.CREDENTIAL.USABLE_ATTACK=KERBEROAST | 0.85 | P0 | SPN服务主体名识别 | 合并多选 | SPN列表+域账户 → L4.SCENE.LATERAL.TECHNIQUE=KERBEROAST, 置信度0.85 |
| 19 | CVE+公开EXP→可立即利用 | 关联 | 实体入库后 | 漏洞报告/文件 | 文件含 L3.ENTITY.VULN.CVE 且 公开EXP库(exploit-db/GitHub)命中 | L4.SCENE.VULN.PUBLIC_EXP=AVAILABLE + L4.SCENE.VULN.EXPLOITABILITY=EXPLOITABLE | 0.90 | P0 | CVE漏洞编号提取 | 合并多选 | CVE-2021-44228+公开EXP → L4.SCENE.VULN.PUBLIC_EXP=AVAILABLE, 置信度0.90 |
| 20 | 服务路径可写→可提权 | 关联 | 实体入库后 | 服务配置文件 | 文件含 L3.ENTITY.SERVICE 且 服务二进制路径可写(文件权限检查) | L4.SCENE.VULN.EXPLOIT_TYPE=LPE + L4.SCENE.VULN.EXPLOITABILITY=EXPLOITABLE | 0.80 | P1 | 网络服务字典匹配 | 合并多选 | 服务路径 `C:\temp\` +可写 → L4.SCENE.VULN.EXPLOIT_TYPE=LPE, 置信度0.80 |
| 21 | AV/EDR+载荷→需免杀 | 关联 | 实体入库后 | 环境信息+载荷 | 文件含 L4.SCENE.EVASION.AV_EDR(非空) 且 含可执行载荷(PE/脚本) | L4.SCENE.EVASION.AVOID_STATUS=DETECTED | 0.75 | P1 | AV/EDR产品字典 | 合并多选 | Defender+payload.exe → L4.SCENE.EVASION.AVOID_STATUS=DETECTED, 置信度0.75 |
| 22 | NTLM Hash+主机→可PtH | 关联 | 实体入库后 | 凭证文件 | 文件含 L3.ENTITY.CRED.HASH(NTLM格式) 且 含 L3.ENTITY.HOST | L4.SCENE.LATERAL.TECHNIQUE=PTH + L4.SCENE.CREDENTIAL.USABLE_ATTACK=PTH | 0.85 | P0 | NTLM Hash识别 | 合并多选 | NTLM hash+主机列表 → L4.SCENE.LATERAL.TECHNIQUE=PTH, 置信度0.85 |
| 23 | Kerberos票据+域→可PtT | 关联 | 实体入库后 | 票据文件 | 文件含 L3.ENTITY.CRED.KERBEROS 且 含 L3.ENTITY.AD | L4.SCENE.LATERAL.TECHNIQUE=PTT + L4.SCENE.CREDENTIAL.USABLE_ATTACK=PTT | 0.85 | P0 | Kerberos票据(.kirbi)识别 | 合并多选 | `.kirbi`+域信息 → L4.SCENE.LATERAL.TECHNIQUE=PTT, 置信度0.85 |
| 24 | 域控+域管→可DCSync | 关联 | 实体入库后 | AD侦察文件 | 文件含 L3.ENTITY.AD.DC 且 含 L3.ENTITY.USER(域管账户) | L4.SCENE.LATERAL.TECHNIQUE=DCSYNC + L5.INTEL.TTP.TA0006 | 0.85 | P0 | AD域控特征字典 | 合并多选 | DC主机+域管账户 → L4.SCENE.LATERAL.TECHNIQUE=DCSYNC, 置信度0.85 |
| 25 | CMS指纹+CVE→可Web利用 | 关联 | 实体入库后 | Web侦察文件 | 文件含 L4.SCENE.TOPOLOGY.CMS 且 CMS 关联 CVE 库命中 | L4.SCENE.VULN.EXPLOIT_TYPE=RCE + L4.SCENE.VULN.EXPLOITABILITY=EXPLOITABLE | 0.80 | P1 | CMS指纹字典 | 合并多选 | WordPress+CVE-2023-3460 → L4.SCENE.VULN.EXPLOIT_TYPE=RCE, 置信度0.80 |
| 26 | 多有效凭证→凭证库构建 | 关联 | 实体入库后 | 凭证集合 | 文件含 ≥3 个 L4.SCENE.CREDENTIAL.STATUS=VALID 凭证 | L4.SCENE.CREDENTIAL + L4.SCENE.LATERAL.PATH | 0.80 | P1 | 凭证强度评估模型 | 合并多选 | 3+有效凭证 → L4.SCENE.CREDENTIAL + L4.SCENE.LATERAL.PATH, 置信度0.80 |
| 27 | VPN配置+凭证→可内网接入 | 关联 | 实体入库后 | VPN配置+凭证 | 文件含 L1.FILE.TYPE.VPN 且 L4.SCENE.CREDENTIAL.STATUS=VALID | L4.SCENE.LATERAL + L4.SCENE.TOPOLOGY.ZONE=INTRANET | 0.85 | P0 | VPN配置(OpenVPN)识别、凭证强度评估模型 | 合并多选 | OpenVPN配置+有效凭证 → L4.SCENE.LATERAL + L4.SCENE.TOPOLOGY.ZONE=INTRANET, 置信度0.85 |
| 28 | 0day+在野利用→高价值战机 | 关联 | 实体入库后 | 漏洞情报 | 文件含 L3.ENTITY.VULN.ZERODAY 且 命中在野利用情报(威胁情报源) | L4.SCENE.VULN.IN_WILD + L4.SCENE.VULN.IMPACT=HIGH | 0.90 | P0 | 无 | 高优先级覆盖低优先级 | 0day+在野利用 → L4.SCENE.VULN.IN_WILD, 置信度0.90 |
| 29 | 多主机+多端口→攻击面画像 | 关联 | 实体入库后 | 侦察文件 | 文件含 ≥3 个 L3.ENTITY.HOST 且 ≥5 个 L3.ENTITY.PORT | L4.SCENE.TOPOLOGY + L4.SCENE.PROFILE.COMPLETENESS=HIGH | 0.75 | P1 | NER命名实体识别 | 合并多选;与多主机设备关联拓扑/多内网IP关联拓扑节点合并不覆盖 | 5主机+10端口 → L4.SCENE.TOPOLOGY + L4.SCENE.PROFILE.COMPLETENESS=HIGH, 置信度0.75 |
| 30 | 计划任务+SYSTEM→持久化 | 关联 | 实体入库后 | 持久化脚本/配置 | 文件含 L3.ENTITY.REGKEY.SCHEDULE(计划任务) 且 上下文含 SYSTEM 权限 | L4.SCENE.PERSIST.MECHANISM=SCHEDULE + L5.INTEL.TTP.TA0003 | 0.80 | P1 | 计划任务命令识别 | 合并多选 | `schtasks /ru SYSTEM` → L4.SCENE.PERSIST.MECHANISM=SCHEDULE, 置信度0.80 |

> **系统派生规则(不计入 131 条显式规则)**:以下标签由系统在打标阶段自动派生,无需独立关联规则:
> - **L4 场景镜像**:`L4.SCENE.UPLOAD.SOURCE/MODE/DEDUP`、`L4.SCENE.PARSE.ABILITY/RESULT/ENTITY`、`L4.SCENE.ANALYZE.TYPE/RESULT/THREAT` 由对应 L2 标签直接镜像(系统在 L2 标签落库时同步写入 L4 镜像标签,置信度继承)。
> - **L1 格式族映射**:`L1.FILE.FORMAT.*` 由 `L1.FILE.TYPE.*` 按第2章 L1 文件属性层标签字典中标签组=格式族的映射规则派生(TYPE∈{PDF,DOCX,...}→DOCUMENT 等,上传阶段同步执行)。
> - **L6 保留期映射**:`L6.COMP.RETENTION.*` 由 `L6.COMP.CLASSIFICATION.*` 按密级→保留期映射表派生(绝密→永久/机密→10年/秘密→5年/内部→1年/含IOC→3年/临时→任务结束)。
> - **L6 访问限制映射**:`L6.COMP.ACCESS.*` 由 `L6.COMP.CLASSIFICATION.*` 按密级→访问限制映射表派生(公开→公开/内部→团队/秘密→项目负责人/机密→仅本人/绝密→审批)。
> - **L6 合规要求**:`L6.COMP.REGULATION.*` 由数据类型与密级综合判定(等保三级为系统默认;含个人信息→PIPL;含凭证→数据安全法)。
> - **L6 脱敏状态**:`L6.COMP.DESSENSITIZE.*` 由脱敏任务执行结果写入(系统在脱敏流水线回调时更新)。
> - **L5 情报来源**:`L5.INTEL.SOURCE.*` 由情报命中记录的 source 字段自动填充。
> - **L2 画像覆盖**:`L2.PROFILE.COVERAGE.*` 由文件→目标画像字段映射率计算引擎产出(定时/事件触发)。
> - **L4 拓扑区域**:`L4.SCENE.TOPOLOGY.ZONE` 由 IP 网段归属判定引擎产出(DMZ/INTRANET/CORE 三区,基于网段配置表)。
> - **L4 漏洞利用难度**:`L4.SCENE.VULN.DIFFICULTY` 由 CVSS 攻击复杂度+PoC 可得性综合计算。
> - **L4 权限变化**:`L4.SCENE.LATERAL.PRIV_CHANGE` 由权限变更检测引擎产出(审计日志分析)。
> - **L4 目标类型**:`L4.SCENE.PROFILE.TARGET_TYPE` 由 L3 实体类型聚合推导(主机→HOST/域→DOMAIN/人→PERSON)。

> **外部数据依赖说明**:
> - **威胁情报 C2 库/恶意哈希库**(IP/域名命中C2情报关联、恶意哈希命中关联威胁等级):数据源为内部情报库+开源情报(MISP/OTX)+商业情报,更新频率:T+1 增量同步,全量刷新周频。
> - **ATT&CK 字典**(ATT&CK技术字典匹配、ATT&CK全量战术字典匹配):基于 MITRE ATT&CK v15(2025 年发布),技术 ID→战术映射表随 ATT&CK 版本升级更新。
> - **APT 组织字典**(APT组织字典匹配):基于 MITRE ATT&CK Groups + 内部积累,季度评审更新,关注组织改名/合并/解散。
> - **YARA 规则集**(YARA规则匹配引擎):数据源为 GitHub 开源规则集+内部自有规则,月度评审,支持紧急热加载(如突发威胁响应)。
> - **Magic Number 字典**(Magic Number 字典匹配、扩展Magic Number 字典匹配):基于 file(1) 源码 magic 数据库,随系统包升级更新。

### 8.5 规则执行流程

规则执行遵循「上传→解析→分析→关联」四阶段流水线,前阶段产出作为后阶段输入,各阶段之间由消息队列解耦,支持失败重试与断点续跑。

```
┌──────────────────────────────────────────────────────────────────────────┐
│ 阶段1·上传阶段(同步,阻塞返回)                                            │
│  触发:文件元数据落库后                                                    │
│  规则:文件扩展名识别 / Webshell文件名识别 / 文件大小分级 / 双扩展名识别     │
│       / KeePass数据库识别(RDP文件识别跨上传+解析两阶段)                    │
│       + 文件扩展名字典匹配 / Magic Number字典匹配 / 扩展Magic Number字典匹配│
│  产出:L1.FILE.TYPE / L1.FILE.SIZE / L1.FILE.FORMAT(派生)                 │
│  耗时上限:≤100ms(同步阻塞用户上传响应)                                  │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 阶段2·解析阶段(异步,后台消费)                                            │
│  触发:文件解析任务出队                                                    │
│  规则:实体提取正则(IP/域名/邮箱/URL/CVE/哈希/端口/注册表键等)            │
│       + 攻击特征正则(Webshell/PowerShell/SQL注入/XSS/命令注入等)         │
│       + 编码/IPv6/高危端口等正则                                          │
│       + 红方凭证正则(Kerberos票据/NetNTLM/AS-REP/TGS/Shadow Hash/        │
│         PuTTY会话/RDP文件/VPN配置/KeePass数据库)                         │
│       + 红方AD域正则(AD域SID/SPN服务主体名)                              │
│       + 红方漏洞编号正则(CWE/CNVD/CNNVD)                                 │
│       + 红方持久化正则(WMI订阅/IFEO镜像劫持/AppInit_DLLs/Winlogon Shell/  │
│         PowerShell Empire cradle)                                        │
│       + 高危端口/网络服务/敏感关键词/网络设备厂商/Webshell文件名/         │
│         弱口令/私网IP CIDR 字典匹配                                       │
│       + 国产Webshell框架/AV-EDR产品/弱口令扩充/AD域控特征/CMS指纹 字典    │
│  产出:L1.FILE.ENCODING / L1.FILE.LANG / L3.ENTITY.* / L4.SCENE.PERSIST.*│
│       / L4.SCENE.EVASION.AV_EDR / L4.SCENE.TOPOLOGY.CMS / L6 密级初判    │
│  耗时上限:≤5s(单文件)                                                   │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 阶段3·分析阶段(异步,资源消耗大)                                          │
│  触发:智能分析任务出队                                                    │
│  规则:APT组织/恶意软件家族/ATT&CK技术/ATT&CK全量战术/红队工具 字典匹配   │
│       + 红队C2框架特征/打包器混淆器/横向移动工具 字典匹配                 │
│       + 全部20个模型规则(MalwareML/NER/行为特征/文本分类/OCR/凭证模式/   │
│         语言识别/PE特征/YARA/文档元数据/网络流量/凭证强度 +               │
│         Shellcode识别/代码混淆检测/打包器识别/Webshell内容检测/          │
│         Office宏代码检测/内存取证特征/DGA域名检测/流量入侵特征)           │
│  产出:L2.ANALYZE.RESULT / L5.INTEL.APT / L5.INTEL.TTP / L5.INTEL.MALWARE│
│       / L5.INTEL.PACKER.* / L4.SCENE.EVASION.OBFUSCATION                 │
│  耗时上限:≤60s(含 YARA 扫描 + ML 推理)                                  │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 阶段4·关联阶段(异步,依赖前序产出)                                        │
│  触发:实体入库后/分析完成后                                               │
│  规则:全部30条关联规则(资产段/C2情报/凭证密级/漏洞可利用性/横向移动/   │
│       敏感关键词/拓扑/画像完整度/恶意哈希/Webshell/PowerShell/数据库连接 │
│       串/AWS密钥/YARA/内网IP拓扑 +                                       │
│       红方作战链路推导:RDP/SSH/Kerberoasting/PtH/PtT/DCSync/CMS-EXP/    │
│       VPN接入/0day战机/攻击面画像/计划任务持久化等)                      │
│  产出:L4.SCENE.* / L5.INTEL.THREAT / L6.COMP.CLASSIFICATION              │
│  耗时上限:≤10s                                                          │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 阶段5·规则冲突仲裁器                                                      │
│  触发:同标签编码被多条规则同时产出                                        │
│  策略:优先级 P0>P1>P2;同优先级按置信度高者胜出;                          │
│       同优先级同置信度→合并多选;冲突不可调和→标记人工复核                 │
│  耗时上限:≤1s                                                           │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
                          人工复核(可选)→ 标签修正
```

> **红方场景专项处理流水线**(在四阶段流水线基础上,针对红方高频场景叠加以下四条专项推导链路,由关联阶段统一调度):

- **凭证提取流水线**:文件 → Magic/扩展名识别(L1.FILE.TYPE.*) → 正则提取 Kerberos票据/NetNTLM/AS-REP/TGS/Shadow Hash/PPK/RDP/VPN 等凭证(L3.ENTITY.CRED.*) → 凭证类型标注(L4.SCENE.CREDENTIAL.TYPE/HASH_TYPE) → 凭证强度评估模型评估有效性(L4.SCENE.CREDENTIAL.STATUS) → 可利用攻击推导(L4.SCENE.CREDENTIAL.USABLE_ATTACK:PTH/PTT/KERBEROAST/ASREPROAST) → 横向移动技术匹配(L4.SCENE.LATERAL.TECHNIQUE)
- **AD域侦察流水线**:文件 → SPN/SID/域控特征提取(L3.ENTITY.AD.SPN/SID/DC,AD域控特征字典) → 域信息标注(L3.ENTITY.AD.*) → Kerberoasting 可行性推导(AD域信息+域账户→可Kerberoasting 关联) → DCSync 可行性推导(域控+域管→可DCSync 关联) → 域攻击路径规划
- **持久化检测流水线**:文件 → 注册表路径/服务配置/计划任务/WMI订阅提取(L3.ENTITY.REGKEY.*) → 持久化机制标注(L4.SCENE.PERSIST.MECHANISM:REGISTRY/SERVICE/SCHEDULE/WMI/IFEO/APPINIT/COM) → 后门类型识别(L4.SCENE.PERSIST.BACKDOOR_TYPE:WEBSHELL/TROJAN/IMPLANT/C2_AGENT/ROOTKIT) → ATT&CK 战术映射(L5.INTEL.TTP.TA0003)
- **横向移动推导流水线**:凭证(L3.ENTITY.CRED.*) + 目标主机(L3.ENTITY.HOST) → 横向技术匹配(L4.SCENE.LATERAL.TECHNIQUE:RDPHIJACK/SSH/PTH/PTT/KERBEROAST/DCSYNC/PSEXEC/WMI) → 攻击路径规划(L4.SCENE.LATERAL.PATH) → 跳板深度计算(L4.SCENE.LATERAL.DEPTH) → VPN接入推导(L4.SCENE.TOPOLOGY.ZONE=INTRANET)

#### 8.5.1 各阶段性能要求

| 阶段 | 规则集 | 同步/异步 | 单文件耗时上限 | 阶段总耗时上限 | 并发数 | 失败重试 |
|------|-------|-----------|----------------|----------------|--------|----------|
| 1 上传阶段 | 正则:文件扩展名识别/Webshell文件名识别/文件大小分级/双扩展名识别/KeePass数据库识别(RDP文件识别跨上传+解析两阶段)(5条上传专属+1条跨阶段) + 字典:文件扩展名/Magic Number/扩展Magic Number | 同步 | ≤100ms | ≤100ms | 1(阻塞上传) | 不重试,失败降级 |
| 2 解析阶段 | 正则:实体提取+攻击特征+编码+红方凭证/AD域/漏洞编号/持久化等(53条,含跨阶段的RDP文件识别) + 字典:高危端口/网络服务/敏感关键词/网络设备厂商/Webshell文件名/弱口令/私网IP CIDR/国产Webshell框架/AV-EDR产品/弱口令扩充/AD域控特征/CMS指纹(12条) | 异步 | ≤2s | ≤5s | 4 | 最多 3 次,指数退避 |
| 3 分析阶段 | 字典:APT组织/恶意软件家族/ATT&CK技术/ATT&CK全量战术/红队工具/红队C2框架特征/打包器混淆器/横向移动工具(8条) + 模型:全部20个模型规则 | 异步 | ≤30s | ≤60s | 2 | 最多 2 次,YARA 失败跳过 |
| 4 关联阶段 | 关联:全部30条关联规则 | 异步 | ≤5s | ≤10s | 2 | 最多 3 次,依赖缺失跳过 |
| 5 仲裁器 | 规则冲突仲裁 | 同步 | ≤500ms | ≤1s | 1 | 不重试 |
| **总计** | 全部 131 条规则 | — | — | **≤76.1s** | — | — |

> **大文件性能保护**:对超过 50MB 的文件,解析阶段正则规则采用分段采样策略(首尾各 5MB + 中间均匀采样 10MB),避免全量扫描导致超时。对超过 100MB 的文件,跳过 SQL注入特征识别/XSS特征识别/命令注入特征识别/可疑User-Agent识别/编码命令行识别 等高耗时文本规则,仅执行 Magic Number 字典匹配 + MalwareML恶意文件分类 + YARA规则匹配引擎 核心检测。采样策略与跳过规则可在 `tag_rule_config` 表按文件大小阈值动态配置。

#### 8.5.2 阶段间数据流转

- **阶段1→阶段2**:上传阶段产出的 L1 标签写入 `file_tags_v2` 表,通过消息队列 `tag.parse.queue` 触发解析任务
- **阶段2→阶段3**:解析阶段提取的 L3 实体写入 `entities` 表,通过消息队列 `tag.analyze.queue` 触发分析任务
- **阶段3→阶段4**:分析阶段产出的 L2/L5 标签写入 `file_tags_v2` 表,通过消息队列 `tag.assoc.queue` 触发关联推导
- **阶段4→阶段5**:关联阶段产出的 L4/L5/L6 标签与同文件已有标签汇聚,由仲裁器统一裁决

### 8.6 规则配置与热更新

#### 8.6.1 规则启停管理

支持运行时启用/禁用单条规则,无需重启服务:

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| rule_id | 规则描述(规则的可读标识,如"Webshell文件名识别") | — |
| enabled | 启用状态:0-禁用 / 1-启用 | 1 |
| effective_time | 生效时间戳(支持延迟生效) | 立即 |
| expire_time | 失效时间戳(支持定时禁用) | 永久 |
| operator | 操作人 UUID | — |

**热更新机制**:
- 规则配置存储于 `tag_rule_config` 表(参考表,全集群同步)
- 配置变更通过广播消息 `rule.config.update` 通知所有计算节点
- 计算节点本地缓存规则配置,TTL=60s,过期自动拉取最新配置
- 禁用规则后,正在执行中的该规则实例不中断,但新任务不再触发该规则

#### 8.6.2 规则优先级调整

支持运行时调整规则优先级,用于动态调优:

| 配置项 | 说明 |
|--------|------|
| rule_id | 规则描述(规则的可读标识) |
| priority | 优先级:P0/P1/P2 |
| reason | 调整原因(如误报率过高/漏报率过高) |
| review_required | 是否需人工复核后生效:是/否 |

**仲裁策略**:
- 同一文件同一标签编码被多条规则产出时,按 `priority DESC, confidence DESC` 排序
- P0 规则产出覆盖 P1/P2 规则产出
- 同优先级规则按置信度高者胜出
- 同优先级同置信度且标签为多选类型→合并多选
- 同优先级同置信度且标签为单选类型→标记 `L2.ANALYZE.RESULT.REVIEW` 触发人工复核

#### 8.6.3 规则版本管理

规则变更需版本号管理,支持回滚:

| 字段 | 说明 |
|------|------|
| rule_id | 规则描述(规则的可读标识) |
| version | 版本号,格式 `v{YYYYMMDD}.{seq}`,如 v20260729.001 |
| change_type | 变更类型:ADD/MODIFY/DELETE |
| change_content | 变更内容(JSON Diff) |
| rollback_to | 回滚目标版本号(可选) |
| approved_by | 审批人 UUID |
| effective_time | 生效时间 |

**版本控制策略**:
- 每次规则变更生成新版本,旧版本保留 90 天
- 支持按版本号回滚单条规则或批量回滚
- 回滚操作需审批,审批通过后立即生效
- 版本变更记录写入 `tag_rule_version` 表,供审计追溯

#### 8.6.4 规则测试沙箱

新规则上线前可在沙箱环境对样本文件试运行:

| 功能 | 说明 |
|------|------|
| 样本库 | 维护 100+ 样本文件(含正样本/负样本/边界样本),覆盖各规则类型 |
| 试运行 | 新规则对样本库全量执行,产出命中结果与置信度 |
| 评估指标 | 准确率(Precision)、召回率(Recall)、F1、平均耗时、误报率、漏报率 |
| 准入阈值 | Precision ≥ 0.85 且 Recall ≥ 0.80 且 F1 ≥ 0.82 方可申请上线 |
| 审批流程 | 试运行报告自动生成→规则负责人审核→安全负责人审批→灰度发布 |
| 灰度发布 | 按 1%→10%→50%→100% 流量比例逐步放量,每阶段观察 24h |

#### 8.6.5 规则效果监控

运行时监控规则命中情况,辅助持续优化:

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| hit_count | 规则命中次数(按天) | 突增/突降 >50% 告警 |
| avg_confidence | 平均置信度 | <0.6 告警(规则退化) |
| avg_latency | 平均执行耗时 | 超过阶段耗时上限 80% 告警 |
| error_count | 执行错误次数 | >0 告警 |
| override_count | 被仲裁器覆盖次数 | 覆盖率 >30% 告警(规则优先级需调整) |
| manual_correct_count | 人工修正次数 | 修正率 >10% 告警(规则准确性需复核) |

---

## 第9章 标签×业务场景映射矩阵

> 单元格值含义:**必选**(该场景必须打该层标签) / **可选**(该场景按需打该层标签) / **不适用**(该场景不涉及该层标签)

| 业务场景 | L1 文件属性 | L2 业务流程 | L3 实体识别 | L4 业务场景 | L5 情报关联 | L6 安全合规 |
|----------|-------------|-------------|-------------|-------------|-------------|-------------|
| 文件上传 | **必选**:基础文件属性(类型/大小/格式/编码/语言/来源)是入库必备信息 | **必选**:上传来源/方式/去重状态反映上传链路 | 不适用:解析未执行,无实体提取 | **必选**:对应 L4.SCENE.UPLOAD 子标签 | 不适用:暂未触发情报关联 | **可选**:命中敏感关键词可立即打密级 |
| 文件解析 | **可选**:可补充修正编码方式等属性 | **必选**:解析能力/解析状态反映处理结果 | **必选**:解析阶段提取实体(IP/域名/凭证等),核心产出 | **必选**:对应 L4.SCENE.PARSE 子标签 | 不适用:解析阶段不直接关联情报 | **可选**:解析出敏感数据可触发密级标记 |
| 文件智能分析 | **可选**:补充分析阶段产生的属性 | **必选**:分析类型/分析结论反映分析结果 | **必选**:分析阶段可深挖 IOC/漏洞/凭证等实体 | **必选**:对应 L4.SCENE.ANALYZE 子标签 | **必选**:APT/TTP/威胁等级是分析核心产出 | **可选**:恶意文件可触发密级提升 |
| 目标画像刻画 | 不适用:画像不依赖文件本身属性 | **可选**:画像覆盖率反映文件对画像贡献 | **必选**:画像由实体(主机/用户/凭证等)汇聚 | **必选**:对应 L4.SCENE.PROFILE 子标签 | **可选**:画像可关联情报 | **必选**:目标涉密等级是画像必备属性 |
| 网络地形还原 | 不适用:地形不依赖文件属性 | 不适用:地形不属于业务流程阶段 | **必选**:主机/网络设备/服务实体构成拓扑节点 | **必选**:对应 L4.SCENE.TOPOLOGY 子标签 | **可选**:可关联节点威胁情报 | **可选**:核心区资产可触发密级 |
| 访问凭证获取 | 不适用:凭证与文件属性无关 | **可选**:可记录凭证提取阶段 | **必选**:凭证实体(IP/账户/凭证)是核心 | **必选**:对应 L4.SCENE.CREDENTIAL 子标签 | **可选**:凭证可关联泄露情报 | **必选**:凭证必然涉及机密级 |
| 漏洞战机识别 | 不适用:漏洞与文件属性无关 | **可选**:可记录漏洞提取阶段 | **必选**:漏洞实体(CVE/0day/Nday)是核心 | **必选**:对应 L4.SCENE.VULN 子标签 | **必选**:漏洞利用情报是战机识别依据 | **可选**:0day 漏洞可触发密级提升 |
| 横向移动 | 不适用:横向移动与文件属性无关 | **可选**:可记录横向移动分析阶段 | **必选**:主机/账户/凭证/会话实体是路径核心 | **必选**:对应 L4.SCENE.LATERAL 子标签 | **必选**:横向移动 TTP/工具是关键情报 | **必选**:横向移动痕迹涉密级高 |
| 持久化 | 不适用:持久化与文件属性无关 | **可选**:可记录持久化分析阶段 | **必选**:注册表键/服务/计划任务实体是核心 | **必选**:对应 L4.SCENE.PERSIST 子标签 | **可选**:持久化 TTP 可关联情报 | **必选**:持久化后门涉密级高 |
| 防御绕过 | **可选**:载荷文件属性可辅助判定免杀 | **可选**:可记录免杀分析阶段 | **必选**:AV/EDR/混淆特征实体 | **必选**:对应 L4.SCENE.EVASION 子标签 | **必选**:免杀/绕过 TTP 是关键情报 | **可选**:免杀载荷可触发密级 |
| 红队基础设施 | **可选**:基础设施配置文件属性 | 不适用:不属于业务流程阶段 | **必选**:C2/域名/重定向器实体 | **必选**:对应 L4.SCENE.INFRA 子标签 | **必选**:C2 框架/基础设施情报 | **必选**:基础设施配置涉密级高 |

### 9.1 矩阵使用说明

1. **必选层**:系统在该场景下自动打标,缺失视为数据质量缺陷
2. **可选层**:由规则推导或人工标注,不影响主流程
3. **不适用层**:该场景下不应产生该层标签,出现即视为异常
4. 检索时,可按场景+层级组合筛选,如"漏洞战机场景 + L5 情报关联"快速定位可利用漏洞

---

## 第10章 标签体系数据模型增强设计稿

### 10.1 tag_dict_v2 增强标签字典表

> 本设计稿不落地 DDL,作为后续迭代基线。原 `tag_dict` 表保留兼容,新增 `tag_dict_v2` 表承载完整字段。
>
> **字段数差异说明**:前文"标签字段定义"统一为 11 字段(标签编码/中文名/层级/分类/值类型/适用对象/识别规则/是否多选/父标签/启用/口径定义),对应第2-7章标签字典表格的 11 列。本 DDL 在此基础上扩展为 16 字段,新增 `rule_type`(规则类型,细化识别规则)、`rule_expr`(规则表达式,与 `identify_rule` 文本描述互补,存可执行表达式)、`severity`(严重级别,情报/合规层使用)、`redteam_scenario`(红方场景标记,v2.0 新增,支撑按红方作战场景筛选标签)、`tech_category`(技术分类,v2.0 新增,支撑跨层技术聚类检索),以支撑自动化引擎调度、风险评估与红方场景化检索。
>
> **v2.0 新增标签组 COMMENT 索引**(7 个新父标签,跨 L3/L4/L5/L6 层):
> - `L3.ENTITY.AD`(AD域)— 域控制器/域SID/SPN/GPO/委派配置,红方 AD 域攻击目标识别
> - `L4.SCENE.PERSIST`(持久化)— 持久化机制/后门类型,红方权限维持场景
> - `L4.SCENE.EVASION`(防御绕过)— AV/EDR 识别/免杀状态/混淆方式,红方免杀绕过场景
> - `L4.SCENE.INFRA`(红队基础设施)— C2 服务器/域名前置/重定向器,红方基础设施管理
> - `L5.INTEL.PACKER`(打包器)— UPX/Themida/VMProtect/Donut,红方载荷脱壳识别
> - `L6.COMP.ANTI_FORENSIC`(防溯源)— 已清除痕迹/含水印,红方产出物 OPSEC 管理
> - `L6.COMP.DESTRUCTION`(销毁管理)— 已确认销毁,红方任务结束销毁审计

```sql
-- ============================================================
-- 标签字典表 v2(设计稿,后续迭代落地)
-- 增强:编码规范、值类型、识别规则、父子层级、适用对象、严重级别、红方场景、技术分类
-- v2.0 新增字段: redteam_scenario / tech_category, 支撑 11 大业务场景与红方技术聚类检索
-- v2.0 新增标签组: L3.ENTITY.AD / L4.SCENE.PERSIST / L4.SCENE.EVASION
--                 L4.SCENE.INFRA / L5.INTEL.PACKER / L6.COMP.ANTI_FORENSIC / L6.COMP.DESTRUCTION
-- ============================================================
CREATE TABLE tag_dict_v2 (
    tag_code           VARCHAR(128) PRIMARY KEY,                 -- 标签编码,全局唯一,格式 层级.分类.名称.值
    tag_name           VARCHAR(128) NOT NULL,                    -- 标签中文名(无英文-only)
    layer              CHAR(2)      NOT NULL,                    -- 层级 L1-L6
    category           VARCHAR(32)  NOT NULL,                    -- 分类: FILE/UPLOAD/PARSE/ANALYZE/PROFILE/ENTITY/SCENE/INTEL/COMP
    value_type         VARCHAR(16)  DEFAULT 'ENUM',              -- 值类型: ENUM/TEXT/NUMBER/BOOL/DATE
    applicable_object  VARCHAR(16)  DEFAULT 'FILE',              -- 适用对象: FILE/ENTITY/TARGET/TASK/ALL
    identify_rule      TEXT,                                     -- 识别规则描述(规则类型+表达式说明)
    rule_type          VARCHAR(16),                              -- 规则类型: REGEX/DICT/ML/ASSOC/MANUAL
    rule_expr          TEXT,                                     -- 规则表达式(正则/字典 key/模型 ID/关联条件)
    is_multi           SMALLINT     DEFAULT 0,                   -- 是否多选: 0-单选 1-多选
    parent_code        VARCHAR(128),                             -- 父标签编码(支持层级树,根标签为 NULL)
    severity           SMALLINT,                                 -- 严重级别(情报/合规层使用,1-5)
    redteam_scenario   VARCHAR(32),                              -- 红方场景标记(v2.0新增): PERSIST/EVASION/INFRA/CREDENTIAL/LATERAL/AD/PACKER/ANTI_FORENSIC/DESTRUCTION,非红方标签为 NULL
    tech_category      VARCHAR(32),                              -- 技术分类(v2.0新增): CREDENTIAL/AD/PERSISTENCE/EVASION/INFRA/PACKER/ANTI_FORENSIC/DESTRUCTION/GENERAL,跨层技术聚类
    enabled            SMALLINT     DEFAULT 1,                   -- 启用状态: 0-禁用 1-启用
    description        TEXT                                       -- 口径定义:标签含义与边界说明
);

-- 参考表(全集群同步,小表)
SELECT create_reference_table('tag_dict_v2');

COMMENT ON TABLE  tag_dict_v2 IS '标签字典表v2: 六层标签体系元数据, 支持编码规范/识别规则/父子层级/红方场景标记(v2.0新增7标签组: AD域/持久化/防御绕过/红队基础设施/打包器/防溯源/销毁管理)';
COMMENT ON COLUMN tag_dict_v2.tag_code          IS '标签编码, 格式 层级.分类.名称.值, 如 L1.FILE.TYPE.PDF / L4.SCENE.PERSIST.MECHANISM(v2.0新增)';
COMMENT ON COLUMN tag_dict_v2.tag_name          IS '标签中文名, 必须有中文名';
COMMENT ON COLUMN tag_dict_v2.layer             IS '层级 L1-L6: L1文件属性/L2业务流程/L3实体识别/L4业务场景/L5情报关联/L6安全合规';
COMMENT ON COLUMN tag_dict_v2.category          IS '层内分类: L1=FILE/L2=UPLOAD,PARSE,ANALYZE,PROFILE/L3=ENTITY/L4=SCENE/L5=INTEL/L6=COMP';
COMMENT ON COLUMN tag_dict_v2.value_type        IS '值类型: ENUM-枚举/TEXT-文本/NUMBER-数值/BOOL-布尔/DATE-日期';
COMMENT ON COLUMN tag_dict_v2.applicable_object IS '适用对象: FILE-文件/ENTITY-实体/TARGET-目标/TASK-任务/ALL-全部';
COMMENT ON COLUMN tag_dict_v2.identify_rule     IS '识别规则描述, 说明如何自动识别该标签';
COMMENT ON COLUMN tag_dict_v2.rule_type         IS '规则类型: REGEX-正则/DICT-字典/ML-模型/ASSOC-关联/MANUAL-人工';
COMMENT ON COLUMN tag_dict_v2.rule_expr         IS '规则表达式, 正则表达式/字典 key/模型 ID/关联条件表达式';
COMMENT ON COLUMN tag_dict_v2.is_multi          IS '是否多选: 0-单选(如文件类型), 1-多选(如 IOC 类型)';
COMMENT ON COLUMN tag_dict_v2.parent_code       IS '父标签编码, 支持层级树, 根标签为 NULL';
COMMENT ON COLUMN tag_dict_v2.severity          IS '严重级别(情报/合规层使用), 1-最低 5-最高';
COMMENT ON COLUMN tag_dict_v2.redteam_scenario  IS '红方场景标记(v2.0新增): PERSIST-持久化/EVASION-防御绕过/INFRA-红队基础设施/CREDENTIAL-凭证获取/LATERAL-横向移动/AD-AD域/PACKER-打包器/ANTI_FORENSIC-防溯源/DESTRUCTION-销毁管理, 非红方标签为 NULL';
COMMENT ON COLUMN tag_dict_v2.tech_category     IS '技术分类(v2.0新增): CREDENTIAL/AD/PERSISTENCE/EVASION/INFRA/PACKER/ANTI_FORENSIC/DESTRUCTION/GENERAL, 跨层技术聚类检索(如汇集 L3+L4+L5+L6 全部凭证相关标签)';
COMMENT ON COLUMN tag_dict_v2.enabled           IS '启用状态: 0-禁用 1-启用';
COMMENT ON COLUMN tag_dict_v2.description       IS '口径定义: 标签含义与边界说明';
```

### 10.2 file_tags_v2 扩展文件标签表

> 在现有 `file_tags` 基础上扩展,保留原表兼容;新增字段记录标签来源、置信度、规则关联等信息。
>
> **字段说明**:v2.0 经核查,`tag_confidence`(置信度)与 `tag_rule_id`(来源规则 ID)字段已在 v1.6 DDL 中存在,无需重复新增。本版新增 `tag_rule_type`(规则类型,v2.0 新增),用于按规则类型(REGEX/DICT/ML/ASSOC/MANUAL)筛选标签,支撑规则效果分类型评估与红方专项流水线(凭证提取/AD域侦察/持久化检测/横向移动推导)按规则类型回溯。

```sql
-- ============================================================
-- 文件标签表 v2(设计稿,后续迭代落地)
-- 扩展: 关联 tag_dict_v2 / 标签来源 / 置信度 / 规则 ID / 规则类型 / 多层支持
-- v2.0 新增字段: tag_rule_type, 支撑按规则类型筛选与红方专项流水线回溯
-- ============================================================
CREATE TABLE file_tags_v2 (
    file_id           UUID         NOT NULL,                     -- 文件 ID(分片键,关联 file_metadata)
    tag_code          VARCHAR(128) NOT NULL,                     -- 标签编码(关联 tag_dict_v2.tag_code)
    tag_layer         CHAR(2)      NOT NULL,                     -- 标签层级 L1-L6(冗余,加速按层检索)
    tag_value         TEXT,                                      -- 标签值(TEXT/NUMBER/DATE 类型时存具体值)
    tag_source        VARCHAR(16)  DEFAULT 'AUTO',               -- 标签来源: AUTO-自动/MANUAL-手动/IMPORT-导入
    tag_confidence    DECIMAL(4,3) DEFAULT 1.000,                -- 置信度 0.000-1.000(AUTO 标签由规则/模型给出)
    tag_rule_id       VARCHAR(64),                               -- 来源规则 ID(关联识别规则,如"文件扩展名识别")
    tag_rule_type     VARCHAR(16),                               -- 规则类型(v2.0新增): REGEX/DICT/ML/ASSOC/MANUAL,按规则类型筛选标签
    team_space_id     UUID,                                      -- 团队空间 ID(支持多团队隔离)
    created_by        UUID,                                      -- 创建人(MANUAL 标签为操作人,IMPORT 为导入任务 ID)
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),       -- 创建时间
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),       -- 更新时间

    CONSTRAINT pk_file_tags_v2 PRIMARY KEY (file_id, tag_code, team_space_id),
    CONSTRAINT fk_file_tags_v2_tag_code FOREIGN KEY (tag_code)
        REFERENCES tag_dict_v2(tag_code),
    CONSTRAINT chk_file_tags_v2_source CHECK (tag_source IN ('AUTO','MANUAL','IMPORT')),
    CONSTRAINT chk_file_tags_v2_confidence CHECK (tag_confidence >= 0 AND tag_confidence <= 1),
    CONSTRAINT chk_file_tags_v2_rule_type CHECK (tag_rule_type IS NULL OR tag_rule_type IN ('REGEX','DICT','ML','ASSOC','MANUAL'))
);

-- 创建分布式表(与 file_metadata 同一 colocate 组)
SELECT create_distributed_table('file_tags_v2', 'file_id', colocate_with => 'file_metadata');

COMMENT ON TABLE  file_tags_v2 IS '文件标签表v2: 关联标签字典, 记录来源/置信度/规则ID/规则类型, 支持自动+人工打标与红方专项流水线回溯';
COMMENT ON COLUMN file_tags_v2.file_id        IS '文件 ID, 分片键, 关联 file_metadata';
COMMENT ON COLUMN file_tags_v2.tag_code       IS '标签编码, 关联 tag_dict_v2.tag_code';
COMMENT ON COLUMN file_tags_v2.tag_layer      IS '标签层级 L1-L6(冗余字段, 加速按层检索)';
COMMENT ON COLUMN file_tags_v2.tag_value      IS '标签值: TEXT/NUMBER/DATE 类型时存具体值, ENUM 类型可为空';
COMMENT ON COLUMN file_tags_v2.tag_source     IS '标签来源: AUTO-自动识别/MANUAL-人工标注/IMPORT-外部导入';
COMMENT ON COLUMN file_tags_v2.tag_confidence IS '置信度 0-1: AUTO 标签由规则/模型给出, MANUAL/IMPORT 默认 1.0';
COMMENT ON COLUMN file_tags_v2.tag_rule_id    IS '来源规则 ID, 关联识别规则(如"文件扩展名识别"), 用于追溯打标依据';
COMMENT ON COLUMN file_tags_v2.tag_rule_type  IS '规则类型(v2.0新增): REGEX-正则/DICT-字典/ML-模型/ASSOC-关联/MANUAL-人工, 按规则类型筛选标签与规则效果分类型评估';
COMMENT ON COLUMN file_tags_v2.team_space_id  IS '团队空间 ID, 支持多团队标签隔离';
COMMENT ON COLUMN file_tags_v2.created_by     IS '创建人: MANUAL 为操作人 UUID, IMPORT 为导入任务 ID, AUTO 为系统';
```

### 10.3 索引设计

```sql
-- ============================================================
-- 索引设计(建议)
-- ============================================================

-- ---- tag_dict_v2 索引(参考表,索引在全节点生效)----
CREATE INDEX idx_tag_dict_v2_layer             ON tag_dict_v2(layer);
CREATE INDEX idx_tag_dict_v2_category          ON tag_dict_v2(category);
CREATE INDEX idx_tag_dict_v2_parent_code       ON tag_dict_v2(parent_code);
CREATE INDEX idx_tag_dict_v2_layer_cat         ON tag_dict_v2(layer, category);
CREATE INDEX idx_tag_dict_v2_rule_type         ON tag_dict_v2(rule_type);
CREATE INDEX idx_tag_dict_v2_enabled           ON tag_dict_v2(enabled);
-- v2.0 新增索引:支撑红方场景筛选与跨层技术聚类
CREATE INDEX idx_tag_dict_v2_redteam_scenario  ON tag_dict_v2(redteam_scenario);
CREATE INDEX idx_tag_dict_v2_tech_category     ON tag_dict_v2(tech_category);
-- 复合索引:按场景+层级(常用:筛选某红方场景下某层标签,如"持久化场景 L4 标签")
CREATE INDEX idx_tag_dict_v2_scenario_layer   ON tag_dict_v2(redteam_scenario, layer);

-- ---- file_tags_v2 索引(分布式表,索引包含分片键)----
-- 按层级检索(常用:展示某文件某层标签)
CREATE INDEX idx_file_tags_v2_layer       ON file_tags_v2(file_id, tag_layer);
-- 按标签编码检索(常用:统计某标签命中文件数)
CREATE INDEX idx_file_tags_v2_tag_code    ON file_tags_v2(tag_code, team_space_id);
-- 按 team_space_id 检索(团队空间隔离查询)
CREATE INDEX idx_file_tags_v2_team_space  ON file_tags_v2(team_space_id, tag_code);
-- 按来源检索(常用:筛选自动/人工标签)
CREATE INDEX idx_file_tags_v2_source      ON file_tags_v2(file_id, tag_source);
-- 按置信度检索(常用:筛选低置信度待复核标签)
CREATE INDEX idx_file_tags_v2_confidence  ON file_tags_v2(file_id, tag_confidence);
-- 按规则 ID 检索(常用:统计某规则命中情况,用于规则效果评估)
CREATE INDEX idx_file_tags_v2_rule_id     ON file_tags_v2(tag_rule_id, team_space_id);
-- 按规则类型检索(v2.0新增,常用:按规则类型分类型评估规则效果,支撑红方专项流水线回溯)
CREATE INDEX idx_file_tags_v2_rule_type   ON file_tags_v2(tag_rule_type, team_space_id);
-- 按创建时间检索(常用:增量同步、TTL 清理)
CREATE INDEX idx_file_tags_v2_created_at  ON file_tags_v2(file_id, created_at);
-- 复合索引:按 team_space + layer + tag_code(常用:团队空间内按层+标签筛选)
CREATE INDEX idx_file_tags_v2_space_layer ON file_tags_v2(team_space_id, tag_layer, tag_code);
```

### 10.4 与现有表的兼容策略

| 现有表 | v2 表 | 兼容策略 |
|--------|-------|----------|
| `tag_dict` | `tag_dict_v2` | 保留 `tag_dict` 只读,新逻辑读写 v2;后续迭代通过数据迁移脚本同步历史字典到 v2 |
| `file_tags` | `file_tags_v2` | 保留 `file_tags` 只读,新逻辑写入 v2;通过迁移脚本将历史 `tag_name` 映射为 `tag_code` 后导入 v2 |

---

## 附录 标签统计

### A.1 各层标签数量统计

| 层级 | 名称 | 父标签数 | 子标签数 | 合计 |
|------|------|----------|----------|------|
| L1 | 文件属性 | 6 | 82 | 88 |
| L2 | 业务流程 | 8 | 37 | 45 |
| L3 | 实体识别 | 13 | 88 | 101 |
| L4 | 业务场景 | 11 | 47 | 58 |
| L5 | 情报关联 | 6 | 45 | 51 |
| L6 | 安全合规 | 7 | 27 | 34 |
| **合计** | — | **51** | **326** | **377** |

### A.2 各层标签组明细

| 层级 | 标签组 | 父标签编码 | 子标签数 | 小计 |
|------|--------|------------|----------|------|
| L1 | 文件类型 | L1.FILE.TYPE | 49 | 50 |
| L1 | 来源类型 | L1.FILE.SOURCE | 5 | 6 |
| L1 | 语言 | L1.FILE.LANG | 6 | 7 |
| L1 | 大小分级 | L1.FILE.SIZE | 5 | 6 |
| L1 | 格式族 | L1.FILE.FORMAT | 11 | 12 |
| L1 | 编码方式 | L1.FILE.ENCODING | 6 | 7 |
| L2 | 上传来源 | L2.UPLOAD.SOURCE | 5 | 6 |
| L2 | 上传方式 | L2.UPLOAD.MODE | 5 | 6 |
| L2 | 去重状态 | L2.UPLOAD.DEDUP | 3 | 4 |
| L2 | 解析能力 | L2.PARSE.ABILITY | 4 | 5 |
| L2 | 解析状态 | L2.PARSE.STATUS | 5 | 6 |
| L2 | 分析类型 | L2.ANALYZE.TYPE | 6 | 7 |
| L2 | 分析结论 | L2.ANALYZE.RESULT | 5 | 6 |
| L2 | 画像覆盖 | L2.PROFILE.COVERAGE | 4 | 5 |
| L3 | IP | L3.ENTITY.IP | 8 | 9 |
| L3 | 域名 | L3.ENTITY.DOMAIN | 5 | 6 |
| L3 | 主机 | L3.ENTITY.HOST | 8 | 9 |
| L3 | 用户 | L3.ENTITY.USER | 5 | 6 |
| L3 | 凭证 | L3.ENTITY.CRED | 16 | 17 |
| L3 | 漏洞 | L3.ENTITY.VULN | 8 | 9 |
| L3 | IOC | L3.ENTITY.IOC | 5 | 6 |
| L3 | 端口 | L3.ENTITY.PORT | 3 | 4 |
| L3 | 服务 | L3.ENTITY.SERVICE | 10 | 11 |
| L3 | URL | L3.ENTITY.URL | 4 | 5 |
| L3 | 邮箱 | L3.ENTITY.EMAIL | 3 | 4 |
| L3 | 注册表键 | L3.ENTITY.REGKEY | 8 | 9 |
| L3 | AD域 | L3.ENTITY.AD | 5 | 6 |
| L4 | 文件上传场景 | L4.SCENE.UPLOAD | 3 | 4 |
| L4 | 文件解析场景 | L4.SCENE.PARSE | 3 | 4 |
| L4 | 智能分析场景 | L4.SCENE.ANALYZE | 3 | 4 |
| L4 | 目标画像场景 | L4.SCENE.PROFILE | 3 | 4 |
| L4 | 网络地形场景 | L4.SCENE.TOPOLOGY | 8 | 9 |
| L4 | 凭证获取场景 | L4.SCENE.CREDENTIAL | 6 | 7 |
| L4 | 漏洞战机场景 | L4.SCENE.VULN | 8 | 9 |
| L4 | 横向移动场景 | L4.SCENE.LATERAL | 5 | 6 |
| L4 | 持久化场景 | L4.SCENE.PERSIST | 2 | 3 |
| L4 | 防御绕过场景 | L4.SCENE.EVASION | 3 | 4 |
| L4 | 红队基础设施场景 | L4.SCENE.INFRA | 3 | 4 |
| L5 | APT组织 | L5.INTEL.APT | 7 | 8 |
| L5 | 攻击技术TTP | L5.INTEL.TTP | 12 | 13 |
| L5 | 威胁等级 | L5.INTEL.THREAT | 4 | 5 |
| L5 | 情报来源 | L5.INTEL.SOURCE | 4 | 5 |
| L5 | 恶意软件家族 | L5.INTEL.MALWARE | 14 | 15 |
| L5 | 打包器 | L5.INTEL.PACKER | 4 | 5 |
| L6 | 密级 | L6.COMP.CLASSIFICATION | 5 | 6 |
| L6 | 保留期 | L6.COMP.RETENTION | 6 | 7 |
| L6 | 合规要求 | L6.COMP.REGULATION | 4 | 5 |
| L6 | 访问限制 | L6.COMP.ACCESS | 5 | 6 |
| L6 | 脱敏状态 | L6.COMP.DESSENSITIZE | 4 | 5 |
| L6 | 防溯源 | L6.COMP.ANTI_FORENSIC | 2 | 3 |
| L6 | 销毁管理 | L6.COMP.DESTRUCTION | 1 | 2 |

### A.3 自动识别规则数量统计

| 规则类型 | 规则数 | 规则描述概要 | 触发阶段 | 覆盖层级 |
|----------|--------|--------------|----------|----------|
| 正则规则 | 58 | 文件扩展名/IPv4/域名/邮箱/URL/CVE/哈希/端口等实体提取,Webshell/PowerShell/SQL注入/XSS/命令注入等攻击特征识别,编码/IPv6/高危端口/双扩展名等可疑特征识别,红方凭证(Kerberos/NetNTLM/AS-REP/TGS/Shadow/PPK/RDP/VPN/KeePass)/AD域(SID/SPN)/漏洞编号(CWE/CNVD/CNNVD)/持久化(WMI/IFEO/AppInit/Winlogon/Empire)识别 | 上传后/解析后 | L1/L3/L4/L5 |
| 字典匹配 | 23 | 文件扩展名/APT组织/恶意软件家族/高危端口/网络服务/ATT&CK技术/敏感关键词/Magic Number/网络设备厂商/Webshell文件名/弱口令/私网IP CIDR/扩展Magic Number/ATT&CK全量战术/红队工具,红队C2框架/国产Webshell框架/打包器混淆器/AV-EDR产品/弱口令扩充/AD域控特征/CMS指纹/横向移动工具 | 上传后/解析后/分析后 | L1/L3/L4/L5/L6 |
| 模型识别 | 20 | MalwareML恶意文件分类/NER命名实体识别/行为特征分析/文本分类/图像OCR/凭证模式识别/语言识别/PE特征/YARA/文档元数据/网络流量分析/凭证强度评估,Shellcode识别/代码混淆检测/打包器识别/Webshell内容检测/Office宏代码检测/内存取证特征/DGA域名检测/流量入侵特征 | 解析后/分析后 | L1/L2/L3/L4/L5 |
| 关联推导 | 30 | 文件IP命中目标资产段/IP域名命中C2情报/有效凭证关联密级/漏洞可利用性/横向移动/敏感关键词关联密级/网络拓扑/画像完整度/恶意哈希/Webshell关联/PowerShell关联/数据库连接串关联/AWS密钥关联/YARA关联/内网IP拓扑,RDP/SSH/Kerberoasting/CVE-EXP/服务提权/AV免杀/PtH/PtT/DCSync/CMS-EXP/凭证库/VPN接入/0day战机/攻击面画像/计划任务持久化 | 实体入库后 | L4/L5/L6 |
| **合计** | **131** | — | 上传/解析/分析/关联四阶段 | L1-L6 全覆盖 |

#### A.3.1 新增规则清单(v1.2 扩充)

| 规则描述清单 | 新增数量 | 新增覆盖场景 |
|--------------|----------|--------------|
| Webshell文件名识别/PowerShell加密命令识别/Linux敏感路径识别/数据库连接串识别/AWS Access Key ID识别/AWS Secret Key识别/PEM私钥完整格式识别/NTLM Hash识别/SQL注入特征识别/XSS特征识别/命令注入特征识别/Cobalt Strike特征识别/Mimikatz特征识别/文件大小分级/GBK编码识别/IPv6特殊地址识别/高危端口识别/双扩展名识别/比特币地址识别/可疑User-Agent识别/编码命令行识别/计划任务命令识别 | 22 条 | Webshell 文件名/PowerShell 加密命令/Linux 敏感路径/数据库连接串/AWS 密钥/PEM 私钥/NTLM Hash/SQL 注入/XSS/命令注入/Cobalt Strike/Mimikatz/文件大小/GBK 编码/IPv6 压缩/高危端口/双扩展名/比特币地址/可疑 UA/编码命令行/计划任务 |
| Webshell文件名字典匹配/弱口令字典匹配/私网IP CIDR字典匹配/扩展Magic Number字典匹配/ATT&CK全量战术字典匹配/红队工具字典匹配 | 6 条 | Webshell 文件名/弱口令/内网 IP 段/Magic Number 扩展/ATT&CK 全量战术/红队工具 |
| PE特征模型/YARA规则匹配引擎/文档元数据提取模型/网络流量分析模型/凭证强度评估模型 | 5 条 | PE 特征模型/YARA 引擎/文档元数据/PCAP 流量分析/凭证强度评估 |
| Webshell关联持久化TTP/PowerShell加密命令关联执行TTP/数据库连接串关联凭证密级/AWS密钥关联凭证密级/YARA恶意规则关联威胁等级/多内网IP关联拓扑节点 | 6 条 | Webshell 关联/PowerShell 关联/数据库连接串关联/AWS 密钥关联/YARA 关联/内网 IP 拓扑关联 |
| **新增合计** | **39 条** | 原有 42 条 → 81 条(扩充 92.9%) |

#### A.3.2 新增规则清单(v2.0 红方实战扩充)

| 规则描述清单 | 新增数量 | 新增覆盖场景 |
|--------------|----------|--------------|
| Kerberos票据(.kirbi)识别/NetNTLMv1/v2 Hash识别/AS-REP Hash识别/TGS Hash(Kerberoasting)识别/Linux Shadow Hash识别/PuTTY会话(.ppk)识别/RDP文件识别/VPN配置(OpenVPN)识别/KeePass数据库识别/AD域SID识别/SPN服务主体名识别/CWE编号识别/CNVD编号识别/CNNVD编号识别/WMI订阅持久化识别/IFEO镜像劫持识别/AppInit_DLLs持久化识别/Winlogon Shell持久化识别/PowerShell Empire cradle识别 | 19 条 | 红方凭证(Kerberos/NetNTLM/AS-REP/TGS/Shadow/PPK/RDP/VPN/KeePass)/AD域(SID/SPN)/漏洞编号(CWE/CNVD/CNNVD)/持久化(WMI/IFEO/AppInit/Winlogon/Empire) |
| 红队C2框架特征字典/国产Webshell框架字典/打包器混淆器字典/AV-EDR产品字典/弱口令字典扩充(200+)/AD域控特征字典/CMS指纹字典/横向移动工具字典 | 8 条 | 红队 C2 框架(Sliver/BruteRatel/Mythic/Havoc/Empire/Covenant)/国产 Webshell(哥斯拉/冰蝎/蚁剑/菜刀)/打包器(UPX/Themida/VMP/Donut)/AV-EDR 产品/弱口令扩充/AD 域控/CMS 指纹/横向移动工具 |
| Shellcode识别模型/代码混淆检测模型/打包器识别模型/Webshell内容检测模型/Office宏代码检测模型/内存取证特征模型/DGA域名检测模型/流量入侵特征模型 | 8 条 | Shellcode 识别/代码混淆检测/打包器识别/Webshell 内容检测/Office 宏检测/内存取证/DGA 域名/流量入侵特征 |
| RDP配置+凭证→可横向移动/SSH私钥+主机→可横向移动/AD域信息+域账户→可Kerberoasting/CVE+公开EXP→可立即利用/服务路径可写→可提权/AV/EDR+载荷→需免杀/NTLM Hash+主机→可PtH/Kerberos票据+域→可PtT/域控+域管→可DCSync/CMS指纹+CVE→可Web利用/多有效凭证→凭证库构建/VPN配置+凭证→可内网接入/0day+在野利用→高价值战机/多主机+多端口→攻击面画像/计划任务+SYSTEM→持久化 | 15 条 | 红方作战链路推导:RDP/SSH/Kerberoasting/CVE-EXP/服务提权/AV 免杀/PtH/PtT/DCSync/CMS-EXP/凭证库/VPN 接入/0day 战机/攻击面画像/计划任务持久化 |
| **新增合计** | **50 条** | 原有 81 条 → 131 条(扩充 61.7%) |

### A.4 业务场景标签覆盖统计

| 业务场景 | L4 场景标签数 | 必选层级数 | 可选层级数 |
|----------|---------------|------------|------------|
| 文件上传 | 4 | 3(L1/L2/L4) | 1(L6) |
| 文件解析 | 4 | 3(L2/L3/L4) | 2(L1/L6) |
| 文件智能分析 | 4 | 4(L2/L3/L4/L5) | 2(L1/L6) |
| 目标画像刻画 | 4 | 3(L3/L4/L6) | 2(L2/L5) |
| 网络地形还原 | 4 | 2(L3/L4) | 2(L5/L6) |
| 访问凭证获取 | 4 | 3(L3/L4/L6) | 2(L2/L5) |
| 漏洞战机识别 | 5 | 3(L3/L4/L5) | 2(L2/L6) |
| 横向移动 | 4 | 4(L3/L4/L5/L6) | 1(L2) |
| 持久化 | 3 | 3(L3/L4/L6) | 2(L2/L5) |
| 防御绕过 | 4 | 3(L3/L4/L5) | 3(L1/L2/L6) |
| 红队基础设施 | 4 | 4(L3/L4/L5/L6) | 1(L1) |

### A.5 编码规范校验

- ✅ 所有 377 个标签均遵循 `层级.分类.名称.值` 规范(L2 层分类为 UPLOAD/PARSE/ANALYZE/PROFILE,叶子标签保持 4 段)
- ✅ 所有标签均有中文名,无英文-only 名称(L5 APT/恶意软件标签均含中文标注)
- ✅ 父子层级关系清晰,父标签与子标签通过 `parent_code` 关联
- ✅ 11 个字段全部填写完整,无空缺
- ✅ 规则表达式具体可执行,正则给出完整表达式,字典给出示例 key
- ✅ 11 大业务场景在 L4 层全部覆盖,且与映射矩阵对应
- ✅ L4 互斥分类统一使用 ENUM 类型(可利用性/难度/影响/完整度/区域/凭证状态/可用性/权限变化)
- ✅ L6 密级与数据分级不重复(已移除冗余的 DATALEVEL 组)
- ✅ 第8章规则集共 131 条,各类型规则数量明确(正则58/字典23/模型20/关联30),每类规则表内序号连续递增无重复
- ✅ 每条规则 12 个字段全部填写完整(序号/规则描述/规则类型/触发时机/输入数据/规则表达式/产出标签/输出置信度/优先级/前置依赖/冲突处理/示例)
- ✅ 正则表达式在 Markdown 表格中管道符以 `\|` 转义展示,实际执行时还原为 `|`(交替运算符),转义约定已在 8.0 节明确说明
- ✅ 字典规则均给出 ≥5 个具体条目,无占位符
- ✅ 模型规则均给出具体模型名称与输入输出,待建模型降级策略已在 8.3 节说明
- ✅ 关联规则均给出明确条件表达式与前置依赖,无循环依赖
- ✅ 规则执行四阶段流水线(上传/解析/分析/关联)总耗时 ≤76.1s,符合各阶段性能上限
- ✅ 规则冲突仲裁器策略明确(优先级→置信度→合并多选→人工复核四级回退)
- ✅ v1.3 对抗性审查修复:Webshell文件名字典匹配/Webshell文件名识别 产出的 L1.FILE.TYPE.PHP 已补入字典;扩展Magic Number字典匹配 产出的 ELF/JAR/APK/GZIP/BZIP2/XZ/DOC/XLS/MACHO 已补入字典;命令注入特征识别 正则已修复;注册表键提取 正则已修复(统一前缀+路径捕获);Base64编码识别 正则已移除锚点;IPv6地址提取 正则已支持压缩格式;XSS特征识别 TTP 映射已修正为 TA0002;Webshell关联持久化TTP BOOL/ENUM 类型不匹配已修正为 L2.ANALYZE.RESULT.MALICIOUS;表名引用已与 database-design.md 对齐(entities/parse_results/analysis_results);L4/L6 系统派生规则已补充说明;外部数据依赖已补充数据源与更新频率;大文件性能保护策略已补充
- ✅ v1.3.1 二轮复核修复:A.1 各层标签数量统计已与 A.2 明细/A.5 声明对齐(L1 子标签 47→57、总数 264→274);L2.PARSE.STATUS 识别规则表名与字段已修正(parse_task.status→parse_results.parse_status);L2.ANALYZE.TYPE 识别规则表名与字段已修正(analyze_task.type→analysis_results.analysis_type);全文已无 parse_task/analyze_task/entity_pool 遗留引用
- ✅ v1.4 表格整合:第2-7章原 44 个标签字典小表格已按层合并为 6 个大表格(L1×1/L2×1/L3×1/L4×1/L5×1/L6×1),表头统一为 12 列(标签组|标签编码|标签中文名|层级|分类|值类型|适用对象|识别规则|是否多选|父标签|启用|口径定义);每行"标签组"列正确标识归属,同一标签组行连续排列,父标签排在该组最前面、子标签紧随其后;合并后标签总数保持 274 不变(父44+子230),无标签丢失;跨表格引用已内联化(原"第 2.5 节映射表"已改为指向第2章标签组=格式族的行);第8章规则集 4 类表格(REGEX/DICT/ML/ASSOC)保持不变;第1章/第9章/第10章/附录保持不变
- ✅ v1.6 对抗性审查优化:补充术语表(IOC/TTP/ATT&CK/Magic Number/DGA/YARA/CVSS/eTLD+1/JWT/PE/ELF/CIDR/C2/APT 共 14 项定义);修复正则5(URL提取)反引号破坏 Markdown 表格解析(改用双反引号包裹);修复正则28(命令注入特征识别)`\|\|` 转义歧义(改用字符类 `[|]{2}` 消除 Markdown 转义与正则转义的不可区分性);修正 A.4 网络地形还原可选层级数与第9章映射矩阵不一致(3→2,L2 由可选修正为不适用);补充目录子章节导航(8.0-8.6/10.1-10.4);补充 DDL tag_dict_v2 字段数差异说明(11 字段→14 字段,新增 rule_type/rule_expr/severity);标签总数 274 与规则总数 81 经复核无丢失
- ✅ v2.0 红方实战完善版:基于红方视角审查补全标签体系,新增 103 项标签(L1+25/L3+36/L4+25/L5+12/L6+5),新增 7 个父标签(L3.AD域/L4.持久化/L4.防御绕过/L4.红队基础设施/L5.打包器/L6.防溯源/L6.销毁管理),标签总数 274→377(父 44→51、子 230→326)经 A.1/A.2 复核一致;新增标签均保持 12 列格式一致、是否多选/启用列统一为"是/否"中文取值(原任务清单 0/1 已转换)、父标签排在子标签之前、同组新增标签连续排列;术语表补充 Kerberoasting/AS-REP Roasting/Pass-the-Hash/Pass-the-Ticket/DCSync/IFEO/AppInit_DLLs/CLSID/SPN/GPO/UPX/Themida/VMProtect/Donut/Sliver/Brute Ratel/Mythic/Havoc/Empire/Covenant 共 20 项红方术语定义;第9章映射矩阵与 A.4 业务场景覆盖统计同步新增持久化/防御绕过/红队基础设施 3 个场景行(8 大场景→11 大场景);第1章架构总览 L4 行场景数同步更新(8→11);第8章新增 50 条红方实战识别规则(正则+19/字典+8/模型+8/关联+15),覆盖红方高频凭证格式(Kerberos/NetNTLM/AS-REP/TGS/Shadow/PPK/RDP/VPN/KeePass)、AD域实体(SID/SPN/域控)、持久化机制(WMI/IFEO/AppInit/Winlogon/Empire)、C2框架(Sliver/BruteRatel/Mythic/Havoc/Empire/Covenant)、作战链路推导(RDP/SSH/Kerberoasting/PtH/PtT/DCSync/CMS-EXP/VPN接入/0day战机)等,规则总数 81→131(正则39→58/字典15→23/模型12→20/关联15→30),8.5 节执行流程补充红方四条专项流水线(凭证提取/AD域侦察/持久化检测/横向移动推导),A.3/A.5 统计与校验同步更新
- ✅ v2.0 DDL 更新与对抗性审查修复(本次):tag_dict_v2 表新增 `redteam_scenario`(红方场景标记:PERSIST/EVASION/INFRA/CREDENTIAL/LATERAL/AD/PACKER/ANTI_FORENSIC/DESTRUCTION)与 `tech_category`(技术分类:CREDENTIAL/AD/PERSISTENCE/EVASION/INFRA/PACKER/ANTI_FORENSIC/DESTRUCTION/GENERAL)两字段及对应索引(idx_tag_dict_v2_redteam_scenario / idx_tag_dict_v2_tech_category / idx_tag_dict_v2_scenario_layer),字段数 14→16;file_tags_v2 表新增 `tag_rule_type`(规则类型:REGEX/DICT/ML/ASSOC/MANUAL)字段及索引(idx_file_tags_v2_rule_type),支撑按规则类型分类型评估与红方专项流水线回溯;`tag_confidence`/`tag_rule_id` 经核查已存在无需重复新增;为 7 个新增标签组(AD域/持久化/防御绕过/红队基础设施/打包器/防溯源/销毁管理)补充 DDL COMMENT 与字段 COMMENT;修复 L4.SCENE.LATERAL.TECHNIQUE 枚举缺失 SSH/DCSYNC(原枚举 PTH/PTT/KERBEROAST/PSEXEC/WMI/RDPHIJACK/GPP → 新增 SSH/DCSYNC,与 8.4 关联规则 17/24 及 8.5 横向移动推导流水线一致);修复 8.5.1 解析阶段正则计数(50→53,含跨阶段的 RDP文件识别)与上传阶段流程图(补入双扩展名识别/KeePass数据库识别两条件上传专属正则);标签总数 377 与规则总数 131 经复核无丢失

---

> **文档结束**
> 本文档为设计稿,不修改现有代码与 DDL。后续迭代落地时,按 10.1/10.2 DDL 创建 v2 表,并按第 8 章规则集实现自动打标引擎。
