-- ============================================================
-- V3.1 标签字典体系（六层架构）迁移脚本
-- 1. tag_dict_v2   标签字典表（L1文件属性/L2业务流程/L3实体识别/L4业务场景/L5情报关联/L6安全合规）
-- 2. file_tags     文件标签关联表
-- 3. 种子数据：覆盖 L1-L6 六层核心标签（父标签 + 子标签）
-- 编码规范：层级.分类.名称.值，如 L1.FILE.TYPE.PDF、L3.ENTITY.IP.PUBLIC
-- ============================================================

-- 标签字典表（六层架构）
CREATE TABLE IF NOT EXISTS tag_dict_v2 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tag_code VARCHAR(128) NOT NULL COMMENT '标签编码，格式：层级.分类.名称.值，如 L1.FILE.TYPE.PDF',
    tag_name VARCHAR(128) NOT NULL COMMENT '标签中文名',
    layer VARCHAR(10) NOT NULL COMMENT '层级：L1-L6',
    category VARCHAR(64) NOT NULL COMMENT '分类：FILE/BUSINESS/ENTITY/SCENE/INTEL/COMPLIANCE',
    value_type VARCHAR(20) NOT NULL DEFAULT 'ENUM' COMMENT '值类型：ENUM/TEXT/NUMBER/BOOL/DATE',
    applicable_object VARCHAR(50) NOT NULL DEFAULT 'FILE' COMMENT '适用对象：FILE/ENTITY/TARGET/TASK/ALL',
    identify_rule TEXT COMMENT '识别规则描述（正则/字典/模型/关联）',
    is_multi TINYINT NOT NULL DEFAULT 0 COMMENT '是否多选：0单选 1多选',
    parent_code VARCHAR(128) DEFAULT NULL COMMENT '父标签编码',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用：0禁用 1启用',
    description TEXT COMMENT '口径定义',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag_code (tag_code),
    KEY idx_layer (layer),
    KEY idx_category (category),
    KEY idx_parent_code (parent_code),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签字典表（六层架构）';

-- 文件标签关联表
CREATE TABLE IF NOT EXISTS file_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    file_id BIGINT NOT NULL COMMENT '文件ID',
    tag_id BIGINT NOT NULL COMMENT '标签ID',
    tag_code VARCHAR(128) NOT NULL COMMENT '标签编码（冗余，便于查询）',
    source VARCHAR(10) NOT NULL DEFAULT 'AUTO' COMMENT '标签来源：AUTO自动/MANUAL手动',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_file_tag (file_id, tag_id),
    KEY idx_file_id (file_id),
    KEY idx_tag_id (tag_id),
    KEY idx_tag_code (tag_code),
    KEY idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件标签关联表';


-- ============================================================
-- 种子数据：L1-L6 六层核心标签
-- 字段顺序：tag_code, tag_name, layer, category, value_type, applicable_object,
--          identify_rule, is_multi, parent_code, enabled, description
-- ============================================================

-- ---------- L1 文件属性层 ----------
INSERT INTO tag_dict_v2
(tag_code, tag_name, layer, category, value_type, applicable_object, identify_rule, is_multi, parent_code, enabled, description) VALUES
('L1.FILE.TYPE',        '文件类型',   'L1', 'FILE', 'ENUM', 'FILE', '文件扩展名+Magic Number 字典匹配', 0, NULL, 1, '文件类型的统称，父标签'),
('L1.FILE.TYPE.PDF',     'PDF文档',    'L1', 'FILE', 'ENUM', 'FILE', '扩展名 .pdf 或 Magic Number 25 50 44 46', 0, 'L1.FILE.TYPE', 1, 'Adobe PDF 格式文档'),
('L1.FILE.TYPE.DOCX',    'Word文档',   'L1', 'FILE', 'ENUM', 'FILE', '扩展名 .docx 或 PK ZIP 头 50 4B 03 04 含 word 目录', 0, 'L1.FILE.TYPE', 1, 'Microsoft Word 2007+ 文档'),
('L1.FILE.TYPE.EXE',     '可执行文件', 'L1', 'FILE', 'ENUM', 'FILE', '扩展名 .exe 或 PE 头 4D 5A', 0, 'L1.FILE.TYPE', 1, 'Windows PE 可执行文件'),
('L1.FILE.TYPE.PCAP',    '网络抓包',   'L1', 'FILE', 'ENUM', 'FILE', '扩展名 .pcap/.pcapng 或 Magic D4 C3 B2 A1 / 0A 0D 0D 0A', 0, 'L1.FILE.TYPE', 1, 'libpcap/pcapng 网络抓包文件'),
('L1.FILE.TYPE.ZIP',     'ZIP压缩包',  'L1', 'FILE', 'ENUM', 'FILE', '扩展名 .zip 或 Magic 50 4B 03 04', 0, 'L1.FILE.TYPE', 1, 'ZIP 压缩包'),
('L1.FILE.TYPE.PNG',     'PNG图片',    'L1', 'FILE', 'ENUM', 'FILE', '扩展名 .png 或 Magic 89 50 4E 47', 0, 'L1.FILE.TYPE', 1, 'PNG 图片'),
('L1.FILE.TYPE.LOG',     '日志文件',   'L1', 'FILE', 'ENUM', 'FILE', '扩展名 .log 或内容以时间戳开头多行', 0, 'L1.FILE.TYPE', 1, '系统或应用日志'),
('L1.FILE.TYPE.PY',      'Python脚本', 'L1', 'FILE', 'ENUM', 'FILE', '扩展名 .py 或首行含 #!/usr/bin/python', 0, 'L1.FILE.TYPE', 1, 'Python 脚本'),
('L1.FILE.SOURCE',       '来源类型',   'L1', 'FILE', 'ENUM', 'FILE', '上传接口字段 source 判断', 1, NULL, 1, '文件获取渠道，父标签'),
('L1.FILE.SOURCE.UPLOAD','用户上传',   'L1', 'FILE', 'ENUM', 'FILE', 'source=upload', 0, 'L1.FILE.SOURCE', 1, '用户主动通过平台上传'),
('L1.FILE.SOURCE.CRAWL', '网络爬取',   'L1', 'FILE', 'ENUM', 'FILE', 'source=crawl', 0, 'L1.FILE.SOURCE', 1, '通过爬虫自动采集'),
('L1.FILE.LANG',         '语言',       'L1', 'FILE', 'ENUM', 'FILE', '语言识别模型（基于 Unicode 字符分布）', 0, NULL, 1, '文件内容主语言，父标签'),
('L1.FILE.LANG.ZH',      '中文',       'L1', 'FILE', 'ENUM', 'FILE', 'CJK 字符占比 ≥ 60%', 0, 'L1.FILE.LANG', 1, '内容以中文为主'),
('L1.FILE.SIZE',         '大小分级',   'L1', 'FILE', 'ENUM', 'FILE', '文件大小阈值判断', 0, NULL, 1, '按文件字节数分级，父标签'),
('L1.FILE.SIZE.MEDIUM',  '中',         'L1', 'FILE', 'ENUM', 'FILE', '1048576 ≤ size < 104857600', 0, 'L1.FILE.SIZE', 1, '1MB ~ 100MB');

-- ---------- L2 业务流程层 ----------
INSERT INTO tag_dict_v2
(tag_code, tag_name, layer, category, value_type, applicable_object, identify_rule, is_multi, parent_code, enabled, description) VALUES
('L2.UPLOAD.SOURCE',          '上传来源',   'L2', 'BUSINESS', 'ENUM', 'FILE', '上传请求入口 channel 字段', 0, NULL, 1, '上传渠道，父标签'),
('L2.UPLOAD.SOURCE.WEB',      'Web端',      'L2', 'BUSINESS', 'ENUM', 'FILE', 'channel=web', 0, 'L2.UPLOAD.SOURCE', 1, '通过 Web 浏览器上传'),
('L2.UPLOAD.SOURCE.API',      'API接口',    'L2', 'BUSINESS', 'ENUM', 'FILE', 'channel=api', 0, 'L2.UPLOAD.SOURCE', 1, '通过 OpenAPI 上传'),
('L2.PARSE.STATUS',           '解析状态',   'L2', 'BUSINESS', 'ENUM', 'FILE', 'parse_results 表 parse_status 字段', 0, NULL, 1, '解析任务当前状态，父标签'),
('L2.PARSE.STATUS.SUCCESS',   '解析成功',   'L2', 'BUSINESS', 'ENUM', 'FILE', 'status=success', 0, 'L2.PARSE.STATUS', 1, '解析完成无错误'),
('L2.PARSE.STATUS.FAILED',    '解析失败',   'L2', 'BUSINESS', 'ENUM', 'FILE', 'status=failed', 0, 'L2.PARSE.STATUS', 1, '解析异常退出'),
('L2.ANALYZE.RESULT',         '分析结论',   'L2', 'BUSINESS', 'ENUM', 'FILE', '分析任务结论字段', 0, NULL, 1, '文件综合判定结论，父标签'),
('L2.ANALYZE.RESULT.MALICIOUS','恶意',      'L2', 'BUSINESS', 'ENUM', 'FILE', 'score ≥ 0.7', 0, 'L2.ANALYZE.RESULT', 1, '判定为恶意文件'),
('L2.ANALYZE.RESULT.BENIGN',  '良性',       'L2', 'BUSINESS', 'ENUM', 'FILE', 'score < 0.3', 0, 'L2.ANALYZE.RESULT', 1, '判定为良性文件'),
('L2.PROFILE.COVERAGE',       '画像覆盖',   'L2', 'BUSINESS', 'ENUM', 'FILE', '文件→目标画像字段映射率计算', 0, NULL, 1, '文件对目标画像的贡献覆盖，父标签'),
('L2.PROFILE.COVERAGE.FULL',  '已覆盖',     'L2', 'BUSINESS', 'ENUM', 'FILE', '覆盖率 ≥ 80%', 0, 'L2.PROFILE.COVERAGE', 1, '高度覆盖目标画像字段');

-- ---------- L3 实体识别层 ----------
INSERT INTO tag_dict_v2
(tag_code, tag_name, layer, category, value_type, applicable_object, identify_rule, is_multi, parent_code, enabled, description) VALUES
('L3.ENTITY.IP',              'IP地址',     'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', 'IPv4/IPv6 正则', 1, NULL, 1, 'IP 地址实体，父标签'),
('L3.ENTITY.IP.PUBLIC',       '公网IP',     'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', 'IPv4 且非私网/保留段', 0, 'L3.ENTITY.IP', 1, '公网可路由 IP'),
('L3.ENTITY.IP.PRIVATE',      '内网IP',     'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '命中 10.0.0.0/8、172.16-31.0.0/16、192.168.0.0/16', 0, 'L3.ENTITY.IP', 1, '私网保留地址段'),
('L3.ENTITY.IP.C2',           'C2 IP',      'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', 'IP 命中威胁情报 C2 库', 0, 'L3.ENTITY.IP', 1, '命令与控制服务器 IP'),
('L3.ENTITY.DOMAIN',          '域名',       'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '域名正则', 1, NULL, 1, '域名实体，父标签'),
('L3.ENTITY.DOMAIN.ROOT',     '根域名',     'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '提取 eTLD+1', 0, 'L3.ENTITY.DOMAIN', 1, '注册顶级根域名'),
('L3.ENTITY.HOST',            '主机',       'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '主机名/IP+设备类型识别', 1, NULL, 1, '主机/设备实体，父标签'),
('L3.ENTITY.HOST.SERVER',     '服务器',     'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '设备类型字典匹配（Linux/Windows Server）', 0, 'L3.ENTITY.HOST', 1, '业务/服务器设备'),
('L3.ENTITY.USER',            '用户',       'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '账户名正则+上下文', 1, NULL, 1, '用户/账户实体，父标签'),
('L3.ENTITY.USER.ADMIN',      '管理员',     'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '上下文含 administrator/admin/root 字样', 0, 'L3.ENTITY.USER', 1, '高权限管理员账户'),
('L3.ENTITY.CRED',            '凭证',       'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '凭证模式模型识别', 1, NULL, 1, '访问凭证实体，父标签'),
('L3.ENTITY.CRED.PASSWORD',   '密码',       'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '上下文含 password/pwd/passwd=', 0, 'L3.ENTITY.CRED', 1, '明文或加密密码'),
('L3.ENTITY.VULN',            '漏洞',       'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', 'CVE 编号正则+漏洞库匹配', 1, NULL, 1, '漏洞实体，父标签'),
('L3.ENTITY.VULN.CVE',        'CVE漏洞',    'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '正则 CVE-\\d{4}-\\d{4,7}', 0, 'L3.ENTITY.VULN', 1, '已公开 CVE 编号漏洞'),
('L3.ENTITY.IOC',             'IOC指标',    'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '命中威胁情报 IOC 库', 1, NULL, 1, '失陷指标实体，父标签'),
('L3.ENTITY.IOC.FILE_HASH',   '文件哈希',   'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', 'MD5/SHA1/SHA256 正则+情报匹配', 0, 'L3.ENTITY.IOC', 1, '恶意文件哈希'),
('L3.ENTITY.PORT',            '端口',       'L3', 'ENTITY', 'NUMBER','FILE/ENTITY', '端口号或端口字段', 1, NULL, 1, '网络端口实体，父标签'),
('L3.ENTITY.PORT.HIGHRISK',   '高危端口',   'L3', 'ENTITY', 'NUMBER','FILE/ENTITY', '命中高危端口字典(22/3389/445/1433 等)', 0, 'L3.ENTITY.PORT', 1, '高风险服务端口'),
('L3.ENTITY.SERVICE',         '服务',       'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '服务名字典+端口映射', 1, NULL, 1, '网络服务实体，父标签'),
('L3.ENTITY.SERVICE.WEB',     'Web服务',    'L3', 'ENTITY', 'TEXT', 'FILE/ENTITY', '端口 80/443/8080 或含 nginx/apache/iis', 0, 'L3.ENTITY.SERVICE', 1, 'Web 服务');

-- ---------- L4 业务场景层 ----------
INSERT INTO tag_dict_v2
(tag_code, tag_name, layer, category, value_type, applicable_object, identify_rule, is_multi, parent_code, enabled, description) VALUES
('L4.SCENE.UPLOAD',                  '文件上传场景',   'L4', 'SCENE', 'BOOL', 'FILE',         '文件入库时触发', 1, NULL, 1, '文件上传场景标记，父标签'),
('L4.SCENE.PARSE',                   '文件解析场景',   'L4', 'SCENE', 'BOOL', 'FILE',         '文件解析任务执行', 1, NULL, 1, '文件解析场景标记，父标签'),
('L4.SCENE.ANALYZE',                 '智能分析场景',   'L4', 'SCENE', 'BOOL', 'FILE',         '智能分析任务执行', 1, NULL, 1, '智能分析场景标记，父标签'),
('L4.SCENE.PROFILE',                 '目标画像场景',   'L4', 'SCENE', 'BOOL', 'FILE/TARGET',  '文件关联目标 ID 时触发', 1, NULL, 1, '目标画像刻画场景，父标签'),
('L4.SCENE.PROFILE.COMPLETENESS',    '画像完整度',     'L4', 'SCENE', 'ENUM', 'TARGET',       '画像字段覆盖率计算', 0, 'L4.SCENE.PROFILE', 1, '画像完整度分级，ENUM 值:HIGH(≥80%)/MID(50%-80%)/LOW(<50%)'),
('L4.SCENE.TOPOLOGY',                '网络地形场景',   'L4', 'SCENE', 'BOOL', 'FILE/TARGET',  '文件含主机/网络设备实体', 1, NULL, 1, '网络地形还原场景，父标签'),
('L4.SCENE.TOPOLOGY.ZONE',           '网络区域',       'L4', 'SCENE', 'ENUM', 'TARGET',       'IP 网段归属判定', 0, 'L4.SCENE.TOPOLOGY', 1, '网络区域分类，ENUM 值:DMZ/INTRANET/CORE'),
('L4.SCENE.CREDENTIAL',              '凭证获取场景',   'L4', 'SCENE', 'BOOL', 'FILE/TARGET',  '文件含 L3.ENTITY.CRED 实体', 1, NULL, 1, '凭证获取场景，父标签'),
('L4.SCENE.VULN',                    '漏洞战机场景',   'L4', 'SCENE', 'BOOL', 'FILE/TARGET',  '文件含 L3.ENTITY.VULN 实体', 1, NULL, 1, '漏洞战机识别场景，父标签'),
('L4.SCENE.VULN.IMPACT',             '影响等级',       'L4', 'SCENE', 'ENUM', 'TARGET',       'CVSS 评分+资产关键性', 0, 'L4.SCENE.VULN', 1, '影响等级分级，ENUM 值:HIGH(CVSS≥7.0)/MID(4.0-7.0)/LOW(<4.0)'),
('L4.SCENE.LATERAL',                 '横向移动场景',   'L4', 'SCENE', 'BOOL', 'FILE/TARGET',  '文件含横向移动痕迹(日志/工具)', 1, NULL, 1, '横向移动场景，父标签');

-- ---------- L5 情报关联层 ----------
INSERT INTO tag_dict_v2
(tag_code, tag_name, layer, category, value_type, applicable_object, identify_rule, is_multi, parent_code, enabled, description) VALUES
('L5.INTEL.APT',                'APT组织',         'L5', 'INTEL', 'ENUM', 'FILE/ENTITY/TARGET', '命中 APT 组织 TTP/IOCs 字典', 1, NULL, 1, 'APT 攻击组织，父标签'),
('L5.INTEL.APT.APT29',          'APT29(舒适熊)',   'L5', 'INTEL', 'ENUM', 'FILE/ENTITY/TARGET', '命中 APT29 TTP/IOCs', 0, 'L5.INTEL.APT', 1, 'APT29 又名舒适熊，俄罗斯背景组织'),
('L5.INTEL.TTP',                '攻击技术TTP',      'L5', 'INTEL', 'ENUM', 'FILE/ENTITY/TARGET', 'ATT&CK 技术 ID 字典匹配', 1, NULL, 1, 'ATT&CK 攻击技术，父标签'),
('L5.INTEL.TTP.TA0006',         '凭据访问',         'L5', 'INTEL', 'ENUM', 'FILE/ENTITY/TARGET', '命中 TA0006 战术下技术', 0, 'L5.INTEL.TTP', 1, 'ATT&CK 战术:凭据访问'),
('L5.INTEL.THREAT',             '威胁等级',         'L5', 'INTEL', 'ENUM', 'FILE/ENTITY/TARGET', '由 MalwareML/情报库综合判定', 0, NULL, 1, '综合威胁等级，父标签'),
('L5.INTEL.THREAT.HIGH',        '高危',             'L5', 'INTEL', 'ENUM', 'FILE/ENTITY/TARGET', '命中恶意 IOC 或 ML score ≥ 0.7', 0, 'L5.INTEL.THREAT', 1, '高威胁等级'),
('L5.INTEL.SOURCE',             '情报来源',         'L5', 'INTEL', 'ENUM', 'FILE/ENTITY/TARGET', '情报命中记录的来源字段', 1, NULL, 1, '威胁情报来源，父标签'),
('L5.INTEL.SOURCE.OPEN_SOURCE', '开源情报',         'L5', 'INTEL', 'ENUM', 'FILE/ENTITY/TARGET', '来源=osint', 0, 'L5.INTEL.SOURCE', 1, '开源威胁情报');

-- ---------- L6 安全合规层 ----------
INSERT INTO tag_dict_v2
(tag_code, tag_name, layer, category, value_type, applicable_object, identify_rule, is_multi, parent_code, enabled, description) VALUES
('L6.COMP.CLASSIFICATION',            '密级',       'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '敏感关键词字典+人工标注', 0, NULL, 1, '数据涉密等级，父标签'),
('L6.COMP.CLASSIFICATION.PUBLIC',     '公开',       'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '无敏感特征', 0, 'L6.COMP.CLASSIFICATION', 1, '可公开数据'),
('L6.COMP.CLASSIFICATION.SECRET',     '秘密',       'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '命中敏感关键词或来源为秘密级', 0, 'L6.COMP.CLASSIFICATION', 1, '秘密级数据'),
('L6.COMP.CLASSIFICATION.TOPSECRET',  '绝密',       'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '含核心指挥/决策信息', 0, 'L6.COMP.CLASSIFICATION', 1, '绝密级数据'),
('L6.COMP.RETENTION',                 '保留期',     'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '密级→保留期映射规则', 0, NULL, 1, '数据保留时长，父标签'),
('L6.COMP.RETENTION.Y5',              '5年',        'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '密级=秘密', 0, 'L6.COMP.RETENTION', 1, '保留 5 年'),
('L6.COMP.REGULATION',                '合规要求',   'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '数据类型→法规映射', 1, NULL, 1, '适用合规要求，父标签'),
('L6.COMP.REGULATION.MLPS3',          '等保三级',   'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '系统等保三级定级', 0, 'L6.COMP.REGULATION', 1, '网络安全等级保护三级'),
('L6.COMP.ACCESS',                    '访问限制',   'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '密级→访问限制映射', 0, NULL, 1, '数据访问范围，父标签'),
('L6.COMP.ACCESS.APPROVAL',           '审批访问',   'L6', 'COMPLIANCE', 'ENUM', 'FILE/TARGET', '密级=绝密', 0, 'L6.COMP.ACCESS', 1, '需审批方可访问');
