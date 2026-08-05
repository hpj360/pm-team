# 红方文件汇聚管理平台 — 项目全面审查报告

> **审查日期**: 2026-07-29
> **审查范围**: 需求文档(PRD/架构/数据库/UI设计/原型)与项目实现(前端/后端/监控/标签体系)
> **审查维度**: 功能匹配度 / 架构技术规格 / 数据模型 / UI交互设计 / 业务流程与监控标签
> **报告版本**: v1.0

---

## 一、审查概述

### 1.1 审查范围

| 类别 | 文档/代码 |
|------|-----------|
| 需求文档 | prd.md / red-team-file-platform-architecture.md / database-design.md / design-spec.md / prototype/* / api-contracts/* |
| 前端代码 | frontend/src/(20+页面、27个mock文件、完整路由) |
| 后端代码 | backend/(11个微服务模块) |
| 监控体系 | monitor-design.md + frontend/src/pages/Monitor/ |
| 标签体系 | tag-system-design.md(v1.5) + tag-system-review.md(v2) |
| 部署运维 | docker/ / k8s/ / .github/workflows/ / .gitlab-ci.yml |

### 1.2 审查方法

采用**5维度并行审查**方法,每个维度独立对比设计文档与实现代码:

1. **功能点匹配度** — PRD功能需求 vs 前端页面实现
2. **架构与技术规格** — 架构设计文档 vs 后端/前端代码结构
3. **数据模型与数据库** — 数据库设计文档 vs Entity/迁移脚本/标签/监控数据模型
4. **UI交互设计** — 设计规范/原型 vs 前端样式/交互实现
5. **业务流程与监控标签** — 业务流程定义 vs 监控指标/标签体系覆盖

---

## 二、总体结论

### 2.1 问题统计

| 严重程度 | 数量 | 占比 | 说明 |
|----------|------|------|------|
| **P0 阻断** | 25 | 22% | 阻断性,平台无法正常部署运行或核心能力缺失 |
| **P1 严重** | 41 | 37% | 严重,影响功能完整性或生产可用性 |
| **P2 一般** | 40 | 36% | 一般,需修复但不阻断主流程 |
| **P3 建议** | 6 | 5% | 优化建议 |
| **合计** | **112** | 100% | |

### 2.2 各维度问题分布

| 审查维度 | P0 | P1 | P2 | P3 | 合计 |
|----------|----|----|----|----|------|
| 1.功能点匹配度 | 4 | 7 | 11 | 0 | 22 |
| 2.架构技术规格 | 1 | 9 | 7 | 1 | 18 |
| 3.数据模型数据库 | 9 | 9 | 6 | 2 | 26 |
| 4.UI交互设计 | 5 | 10 | 12 | 1 | 28 |
| 5.业务流程监控标签 | 6 | 6 | 4 | 2 | 18 |
| **合计** | **25** | **41** | **40** | **6** | **112** |

### 2.3 核心风险判断

平台存在**三大系统性风险**:

1. **设计文档与实现"两套体系"并行** — 数据库设计描述的是 Citus+UUID+分布式企业架构,实现是 BIGINT+单机+字符串简化版;架构设计要求 gRPC+Vault+APISIX+TDengine,实现均未落地。需决策以哪个为准。

2. **红方核心业务场景覆盖不足** — 8大红方业务场景(智能分析/目标画像/网络地形/凭证获取/漏洞战机/横向移动)在监控指标中仅覆盖2个,标签体系前端完全未落地,IOC中心为空壳页面。

3. **安全合规能力严重缺失** — 团队空间隔离未落地、审计日志表缺失、下载水印/审批/授权码未实现、敏感数据无结构化存储,红方平台的安全合规底线未达到。

---

## 三、P0级问题清单(25项,阻断性)

### 3.1 功能点匹配度(P0×4)

| 编号 | 问题 | 位置 | 建议方案 |
|------|------|------|----------|
| F-P0-1 | **IOC中心完全空实现** — 顶部菜单入口存在但页面仅占位"功能开发中",与PRD安全分析核心能力严重不符 | `pages/IocCenter/index.tsx` | 参照mock/ioc.ts实现IOC列表/详情/关联文件/导出 |
| F-P0-2 | **批量下载缺失** — PRD F3.8.2标P0,红方需批量导出样本,FileList仅批量删除无批量下载 | `pages/FileList/index.tsx` | 批量操作区新增"批量下载"按钮,调用打包ZIP接口 |
| F-P0-3 | **下载水印缺失** — PRD F3.8.6标P0,L3+敏感数据强制水印,单文件下载直接window.open无水印 | `pages/FileList/index.tsx:280` | 下载前判断sensitivity≥L3调用水印接口 |
| F-P0-4 | **目标画像报告生成缺失** — PRD F3.5.8标P0,红方重要交付物,TargetProfile仅查看无生成报告按钮 | `pages/redteam/TargetProfile/index.tsx` | 详情页新增"生成画像报告"按钮,跳转报告预览 |

### 3.2 架构技术规格(P0×1)

| 编号 | 问题 | 位置 | 建议方案 |
|------|------|------|----------|
| A-P0-1 | **gRPC服务间通信完全未实现** — 架构§2.4定义4条gRPC同步依赖,proto/有5个IDL文件但backend无任何gRPC server/client代码,各服务pom无grpc依赖 | `backend/*/pom.xml`;`proto/*.proto` | 引入grpc-spring-boot-starter,按proto生成stub并实现FileService等gRPC服务端 |

### 3.3 数据模型数据库(P0×9)

| 编号 | 问题 | 位置 | 建议方案 |
|------|------|------|----------|
| D-P0-1 | **主键类型冲突** — 设计用UUID,实现用BIGINT;API契约file_id为string(UUID),Entity为Long,前端按契约调用会类型错误 | `BaseEntity.java`;所有Entity;`api-contracts/*.yaml` | 统一为UUID或反向更新设计文档与API契约 |
| D-P0-2 | **文件指纹算法不一致** — 设计用sha256_hash,实现用file_sm3(国密);无独立file_hash_index秒传表 | `V2_3_0__upload_enhance.sql`;`FileEntity.java` | 明确国密优先,补建file_hash_index或补file_sha256列 |
| D-P0-3 | **秒传引用计数缺失** — 无ref_count/version字段,秒传删除时物理存储会误删 | `V2_3_0__upload_enhance.sql:42-45` | 补ref_count/version字段或落地file_hash_index表 |
| D-P0-4 | **团队空间隔离未落地** — BaseTenantEntity类存在但全代码库无任何Entity继承它,t_file无team_space_id列 | `BaseTenantEntity.java`;所有Entity | 核心业务表改为继承BaseTenantEntity并补迁移 |
| D-P0-5 | **监控事实表未实现** — t_file_event设计为按月分区事实表,实际仅存在Kafka DTO未持久化 | `monitor-design.md:289`;`upload-service/dto/FileEvent.java` | 落地t_file_event表+Mapper |
| D-P0-6 | **监控聚合表全部未实现** — t_metric_hourly/daily等13张表无任何迁移脚本/Entity/Mapper | `monitor-design.md:244-258` | 新建monitor-service补齐表与聚合Job |
| D-P0-7 | **审计日志表缺失** — audit_logs分区表(保留7年)完全未实现,红方平台合规硬性要求 | `database-design.md:1313-1385` | 落地分区表+pg_partman自动维护 |
| D-P0-8 | **API契约与实现主键类型冲突** — FileInfo.file_id为string(UUID),FileEntity.id为Long | `api-contracts/upload-service.yaml` vs `FileEntity.java` | 对齐主键类型 |
| D-P0-9 | **初始Schema缺失** — 无V1初始迁移脚本,t_user/t_file等基础表无创建脚本,Flyway因基础表不存在而失败 | `backend/**/db/migration/`(仅V2.3.0+) | 补V1__init_schema.sql |

### 3.4 UI交互设计(P0×5)

| 编号 | 问题 | 位置 | 建议方案 |
|------|------|------|----------|
| U-P0-1 | **品牌主色完全错误** — 规范要求蓝色(#0052CC),实现用红色(#f5222d),且缺少赛博青强调色 | `styles/tokens/colors.ts:8-19`;`styles/theme.ts:11` | 重写colors.primary为蓝色系,新增accent赛博青 |
| U-P0-2 | **三层Token体系缺失** — 规范要求全局→语义→组件三层,tokens.less仅一层扁平变量 | `styles/tokens.less` | 新增语义令牌层(bg-primary/text-primary/border-base等) |
| U-P0-3 | **深色主题背景色错误** — 规范#0D1117系,实现#141414 | `colors.ts:60-65`;`tokens.less:82-87` | 改为#0D1117/#161B22/#21262D/#30363D色阶 |
| U-P0-4 | **主题切换机制不完整** — tokens.less仅覆盖4个变量,未实现data-theme完整切换,未持久化localStorage | `MainLayout.tsx:56`;`tokens.less:83` | html挂data-theme属性,持久化rt-theme键,补全语义令牌覆盖 |
| U-P0-5 | **全局键盘快捷键完全缺失** — 规范要求30+快捷键(Ctrl+K搜索/Ctrl+,主题/Esc关闭),一个未实现 | `MainLayout.tsx` | 新增useGlobalHotkeys hook |

### 3.5 业务流程监控标签(P0×6)

| 编号 | 问题 | 位置 | 建议方案 |
|------|------|------|----------|
| M-P0-1 | **监控仅覆盖2/8业务场景** — 监控定义4阶段(上传/索引/解析/搜索),标签L4定义8大场景,6个核心作战场景无监控指标 | `monitor-design.md §3.1`;`tag-system-design.md 第5章` | 新增ANALYZE/PROFILE/TOPOLOGY/CREDENTIAL/VULN/LATERAL六阶段指标 |
| M-P0-2 | **监控看板缺失4个Tab** — 设计9个看板,前端仅5个Tab,缺安全合规/告警事件/数据质量/容量成本 | `pages/Monitor/index.tsx:93-139` | 新增Security/Alert/Quality/Capacity四个Tab |
| M-P0-3 | **监控stage与标签L4场景不对齐** — 监控stage=UPLOAD/INDEX/PARSE/SEARCH,标签L4=8大场景,两者定义不同源 | `types/monitor.ts:6-11`;`tag-system-design.md 第5章` | 统一业务流程定义,以标签L4的8大场景为基准 |
| M-P0-4 | **标签体系前端完全未落地** — 274标签/81规则/6层设计完善,但前端无任何标签相关代码 | `frontend/src/`(全局无tag代码) | 新增types/tag.ts/services/tag.ts/pages/TagManage/ |
| M-P0-5 | **监控/标签API契约缺失** — api-contracts无monitor-service.yaml和tag-service.yaml | `docs/api-contracts/` | 新增monitor-service.yaml和tag-service.yaml |
| M-P0-6 | **team_space_id隔离链路断裂** — 监控/标签设计均依赖team_space_id,但Kafka事件payload无此字段 | `docs/api-contracts/events.md` | 所有文件域事件payload新增team_space_id字段 |

---

## 四、P1级问题清单(41项,严重)

### 4.1 功能点匹配度(P1×7)

| 编号 | 问题 | 位置 |
|------|------|------|
| F-P1-1 | 系统设置入口与admin/SystemConfig功能重叠,边界不清 | `Settings/index.tsx` vs `admin/SystemConfig` |
| F-P1-2 | 下载审计日志查看入口缺失,AuditLog未过滤下载类型 | `admin/AuditLog/index.tsx` |
| F-P1-3 | 断点续传下载+限流下载未实现,均为window.open直下载 | `FileList/index.tsx` |
| F-P1-4 | 下载审批+授权码(L4/L5)完全未实现 | `FileList/index.tsx` |
| F-P1-5 | 目标画像导出(PDF/Word)未实现 | `redteam/TargetProfile/Detail` |
| F-P1-6 | 文件分析报告硬编码mockTask/setTimeout,未对接真实API | `FileAnalyze/Report/index.tsx:50-142` |
| F-P1-7 | 文件对比分析引用mockFileList,未对接真实接口 | `FileAnalyze/Compare/index.tsx:37` |

### 4.2 架构技术规格(P1×9)

| 编号 | 问题 | 位置 |
|------|------|------|
| A-P1-1 | TDengine时序数据库缺失 — 架构要求analyze-service写时序数据 | `docker/dev/docker-compose.yml` |
| A-P1-2 | Vault KMS密钥管理缺失 — 架构要求DEK+KEK二级密钥+MinIO SSE-KMS | `docker/dev/docker-compose.yml` |
| A-P1-3 | APISIX API网关缺失 — 架构要求3节点集群+ClamAV扫描插件 | `docker/dev/docker-compose.yml`;`k8s/` |
| A-P1-4 | DolphinScheduler缺失 — 架构要求DAG编排/CRON,实际自研Spring Boot | `backend/task-service/` |
| A-P1-5 | Citus分布式PostgreSQL缺失 — docker-compose仅postgres:15单节点 | `docker/dev/docker-compose.yml` |
| A-P1-6 | OpenFeign基本未用 — 仅feishu-service有@EnableFeignClients,其余9服务无 | `backend/*/pom.xml` |
| A-P1-7 | K8s部署仅2/11服务 — 仅auth-service和frontend有部署清单 | `k8s/` |
| A-P1-8 | CI/CD仅覆盖2/11服务 — GitHub Actions/GitLab CI仅构建auth+frontend | `.github/workflows/ci.yml` |
| A-P1-9 | gRPC proto定义不完整 — 缺search/parse/profile/analyze服务proto | `proto/` |

### 4.3 数据模型数据库(P1×9)

| 编号 | 问题 | 位置 |
|------|------|------|
| D-P1-1 | 表命名规范不一致 — 设计用users/file_metadata,实现用t_user/t_file | 所有Entity |
| D-P1-2 | parse_results多版本未实现 — 仍保留UNIQUE(file_id) 1:1约束,无parse_version | `V2_3_0__parse_yara_bert.sql:19` |
| D-P1-3 | 标签体系v2表未落地 — tag_dict_v2/file_tags_v2标注"设计稿" | `tag-system-design.md:788` |
| D-P1-4 | Outbox Pattern未实现 — 直接Kafka投递,业务提交与消息投递非原子 | `database-design.md:3915` |
| D-P1-5 | 目标关联双写表未实现 — 无target_files_by_target/by_file双写表 | `database-design.md:1152` |
| D-P1-6 | 漏洞/敏感信息表未实现 — 混入t_analyze_result.result_json TEXT,无结构化查询 | `database-design.md:949` |
| D-P1-7 | 时间字段无时区 — 全表TIMESTAMP无时区,设计要求TIMESTAMPTZ | 所有迁移脚本 |
| D-P1-8 | ES索引设计三方不一致 — 设计snake_case+keyword UUID,实现camelCase+long,API契约string | `search-service/.../es/index-mapping.json` |
| D-P1-9 | 数据库划分未实现 — init-db.sql创建11个独立库,实际全连单库redteam_file | `init-db.sql`;`application.yml` |

### 4.4 UI交互设计(P1×10)

| 编号 | 问题 | 位置 |
|------|------|------|
| U-P1-1 | 功能色偏差 — success应用#39D353非#52c41a,error应用#FF4D4F非#f5222d | `colors.ts:36-39` |
| U-P1-2 | 风险等级色缺unknown,low颜色错误 | `colors.ts:42-48` |
| U-P1-3 | 侧边栏宽度220px非规范200px,未配置collapsedWidth=64 | `MainLayout.tsx:202` |
| U-P1-4 | 响应式断点策略未实现 — 无媒体查询自动收起 | `MainLayout.tsx:52-53` |
| U-P1-5 | 登录页背景暗红渐变非规范深色#0D1117 | `Login/Login.module.less:13-29` |
| U-P1-6 | 登录方式缺LDAP/SSO Tab,仅用户名密码+MFA | `Login/index.tsx:88-129` |
| U-P1-7 | 登录页缺记住我/忘记密码/注册申请链接 | `Login/index.tsx:117-128` |
| U-P1-8 | 主内容区无max-width:1440px居中约束 | `MainLayout.tsx:280-291` |
| U-P1-9 | 浅色主题菜单色错误 — 使用dark*暗色配置 | `theme.ts:30-34` |
| U-P1-10 | 深色主题侧边栏背景#000000非规范#161B22 | `theme.ts:70` |

### 4.5 业务流程监控标签(P1×6)

| 编号 | 问题 | 位置 |
|------|------|------|
| M-P1-1 | 错误码双轨未对齐 — 监控用字符串码,API契约用5位数字码,无映射 | `monitor-design.md §4`;`api-contracts/README.md §3.2` |
| M-P1-2 | 监控entity_type(3值)与标签L3实体(12类)不对齐 | `monitor-design.md §2`;`tag-system-design.md 第4章` |
| M-P1-3 | 监控ioc_type(4值)与标签L3.ENTITY.IOC(5子类)不对齐 | `monitor-design.md §2`;`tag-system-design.md L3` |
| M-P1-4 | Funnel看板"索引可搜时延P95"图表数据语义错误 — 用积压数非freshness P95 | `pages/Monitor/tabs/Funnel.tsx:69-75` |
| M-P1-5 | SearchExperience看板缺"搜索结果点击率"图表 | `pages/Monitor/tabs/SearchExperience.tsx` |
| M-P1-6 | SLO看板缺"SLO违约事件"表格,且SLO仅覆盖4阶段缺6大场景 | `pages/Monitor/tabs/Slo.tsx` |

---

## 五、P2/P3级问题摘要(46项)

### 5.1 P2级问题(40项)

**功能匹配度(11项)**: 文件解析无独立页面、向量语义检索缺Top-K参数、上传缺病毒扫描展示、哈希算法SHA256 vs SM3偏差、网络地形还原无独立可视化、访问凭证获取无凭证管理页、文件状态机缺skipped状态、监控/健康检查/通知中心/个人中心为超纲功能需补PRD。

**架构技术(7项)**: metadata-service缺失、服务命名偏差(user→auth/scheduler→task)、架构图未含report/feishu、错误码4位vs5位不符契约、前端技术栈架构规范缺失、analyze-service时序写入未实现、Nacos角色定位偏差。

**数据模型(6项)**: 字典/配置表未实现、IP字段VARCHAR非INET、软删除0/1非deleted_at、实体表字段类型偏差、文件版本/目录/压缩包/流量表未实现、Citus分布式能力未启用。

**UI交互(12项)**: 表格规范偏差、卡片规范缺失、字体家族偏差、高亮关键字样式仅浅色、表格a11y缺aria-label、NER实体标签无类型变体、按钮规范偏差、危险操作确认不完整、撤销机制缺失、顶部导航样式偏差、上传错误重试缺失、关系图谱待验证。

**业务流程监控(4项)**: mock错误码字典不完整、监控L4安全指标与标签L6无映射、标签规则效果监控未纳入、漏斗仅3阶段缺6大场景。

### 5.2 P3级问题(6项)

文件状态机skipped状态(P3)、CI/CD服务覆盖(P3→已升P1)、severity枚举约束(P3)、操作反馈时长错误提示5秒(P3)、Nacos角色偏差(P3)、监控超纲功能PRD补充(P3)。

---

## 六、修复优先级建议

### 6.1 第一优先级:阻断性修复(P0,立即处理)

| 序号 | 修复项 | 涉及问题 | 影响 |
|------|--------|----------|------|
| 1 | 补V1初始Schema迁移脚本 | D-P0-9 | 全新环境无法启动 |
| 2 | 对齐主键类型(UUID or BIGINT) | D-P0-1/D-P0-8 | API契约与实现冲突 |
| 3 | 落地team_space_id隔离 | D-P0-4/M-P0-6 | 多租户数据隔离 |
| 4 | 实现gRPC服务间通信 | A-P0-1 | 架构核心通信方式 |
| 5 | 修正品牌主色+Token体系 | U-P0-1/U-P0-2 | UI视觉基础 |
| 6 | 实现IOC中心页面 | F-P0-1 | 安全分析核心能力 |
| 7 | 实现批量下载+水印 | F-P0-2/F-P0-3 | 红方交付+合规 |
| 8 | 统一监控与标签业务流程(8大场景) | M-P0-1/M-P0-3 | 业务流程对齐 |
| 9 | 落地监控数据模型 | D-P0-5/D-P0-6 | 监控看板数据源 |
| 10 | 落地审计日志表 | D-P0-7 | 合规硬性要求 |

### 6.2 第二优先级:严重问题修复(P1,短期处理)

- 后端基础设施补齐(TDengine/Vault/APISIX/Citus)
- K8s部署清单补齐9个服务
- CI/CD流水线覆盖全部服务
- 标签体系前端落地
- 监控看板补齐4个缺失Tab
- 文件下载安全能力(审批/授权码/审计/断点续传)
- 分析报告对接真实API
- UI主题切换机制完善
- 登录页交互补全
- 错误码体系对齐

### 6.3 第三优先级:一般问题修复(P2/P3,中期处理)

- 字典表/业务表补齐
- ES索引对齐
- UI组件规范统一
- a11y无障碍增强
- 撤销机制/错误重试
- 监控-标签交叉引用建立

---

## 七、风险评估

### 7.1 部署风险(极高)

- **全新环境无法启动**: 缺V1初始迁移脚本,Flyway因基础表不存在而失败
- **API类型冲突**: 契约string(UUID) vs Entity Long,前端按契约调用必然类型错误
- **数据库未隔离**: init-db.sql创建11库但实际全连单库,微服务数据混杂

### 7.2 安全合规风险(极高)

- **团队空间隔离未落地**: 任何用户可访问全部文件数据
- **审计日志缺失**: 无法追溯操作行为,不满足红方平台合规要求
- **下载安全缺失**: 敏感文件无水印/审批/授权码,存在泄露风险

### 7.3 业务完整性风险(高)

- **红方核心场景覆盖不足**: 8大场景监控仅覆盖2个,标签体系前端零落地
- **IOC中心空壳**: 安全分析核心能力无法使用
- **报告生成缺失**: 画像报告/分析报告未对接真实数据

### 7.4 可维护性风险(中)

- **设计文档与实现脱节**: 两套体系并行,后续开发无基准
- **UI Token体系缺失**: 主题切换无法工作,样式难以统一管理

---

## 八、审查结论

平台整体架构与PRD模块划分基本对齐(前端路由覆盖8大模块,后端11个微服务均有真实代码),但存在 **25项P0阻断级问题** 和 **41项P1严重级问题**,集中在三大红线区域:

1. **部署可运行性**(初始Schema缺失/主键冲突/数据库未隔离)
2. **安全合规底线**(团队空间隔离/审计日志/下载安全)
3. **红方业务完整性**(IOC中心/监控8场景覆盖/标签体系落地)

**建议**: 在进入联调阶段前,优先处理第一优先级的10项P0修复项,确保平台可部署运行、数据可隔离、核心能力可用。

---

> **审查人**: AI项目审查Agent
> **审查耗时**: 5维度并行审查
> **问题总数**: 112项(P0×25 / P1×41 / P2×40 / P3×6)
> **下一步**: 按修复优先级建议依次处理,建议分3个迭代周期完成全部修复
