/**
 * 文件详情页（完整版）
 * - 顶部：文件基本信息卡片 + 操作按钮（下载/重新解析/YARA扫描）
 * - Tab 切换：YARA 匹配 / NER 实体 / 元数据 / 解析结果 / 关联 IOC
 * - 路由参数：/files/:id
 */
import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Tabs,
  Typography,
  Tag,
  Space,
  Button,
  Descriptions,
  Table,
  Empty,
  Spin,
  message,
  Row,
  Col,
  Statistic,
  Badge,
  List,
  Skeleton,
  Result,
  Collapse,
  Alert,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  DownloadOutlined,
  ReloadOutlined,
  ScanOutlined,
  CopyOutlined,
  WarningOutlined,
  FileTextOutlined,
  BugOutlined,
  TagsOutlined,
  FileSearchOutlined,
  RobotOutlined,
  ExperimentOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import { getMockFileById } from '@/mock/file';
import { mockYaraRules } from '@/mock/yara';
import { mockThreatIntelItems } from '@/mock/threatIntel';
import { scanFile, getNerResult } from '@/services/analyze';
import { generateThreatSummary, inferAttackChain } from '@/services/ai';
import type { FileInfo, YaraScanResult, YaraMatchResult, YaraMatchString, NerResult, NerEntity, ThreatSummary, AiAttackChain } from '@/types';
import { FileStatus, SensitivityLabel, NerEntityType, NerEntityTypeLabel, FileClassification, FileClassificationLabel } from '@/types';
import { formatDateTime, formatFileSize, copyToClipboard } from '@/utils';
import { fileTypeLabel, fileTypeColor } from '@/utils/fileType';
import { colors, spacing } from '@/styles/tokens';
import FileIcon from '@/components/common/FileIcon';
// V4.7-P1-3 协同编辑：WebSocket hook + 在线用户 Badge + 评审区域
import { useCollaboration } from '@/hooks/useCollaboration';
import { MOCK_CURRENT_USER } from '@/mock/fileReview';
import OnlineUsersBadge from './components/OnlineUsersBadge';
import FileReviewSection from './components/FileReviewSection';
import DynamicAnalysisTab from './components/DynamicAnalysisTab';
import ClassificationTag from '@/components/common/ClassificationTag';

const { Title, Text, Paragraph } = Typography;

/** 状态映射 */
const statusMap: Record<FileStatus, { color: string; text: string }> = {
  [FileStatus.PENDING]: { color: 'default', text: '待处理' },
  [FileStatus.PROCESSING]: { color: 'processing', text: '处理中' },
  [FileStatus.COMPLETED]: { color: 'success', text: '已完成' },
  [FileStatus.FAILED]: { color: 'error', text: '失败' },
};

/** Mock 沙箱报告 */
const sandboxReport = {
  environment: 'Windows 10 1909 x64',
  duration: 120,
  score: 78,
  processes: [
    { pid: 1234, name: 'malware_sample.exe', parent: 0, action: '创建进程' },
    { pid: 1456, name: 'cmd.exe', parent: 1234, action: '执行命令' },
    { pid: 1789, name: 'powershell.exe', parent: 1456, action: '下载脚本' },
  ],
  network: [
    { dst: '45.155.205.233:443', proto: 'TCP', bytes: 8420 },
    { dst: 'malicious-update.example-evil.com:443', proto: 'HTTPS', bytes: 15200 },
  ],
};

const FileDetailPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [file, setFile] = useState<FileInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('yara');

  const [yaraLoading, setYaraLoading] = useState(false);
  const [yaraResult, setYaraResult] = useState<YaraScanResult | null>(null);
  const [nerLoading, setNerLoading] = useState(false);
  const [nerResult, setNerResult] = useState<NerResult | null>(null);

  // AI 分析状态（懒加载：切换到 AI Tab 时触发）
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);
  const [threatSummary, setThreatSummary] = useState<ThreatSummary | null>(null);
  const [attackChain, setAttackChain] = useState<AiAttackChain | null>(null);
  const [aiLoaded, setAiLoaded] = useState(false);

  // V4.7-P1-3 协同编辑：WebSocket hook（fileId 取路由 id 或 file.id，file 加载前用路由 id）
  const collabFileId = file?.id ?? id;
  const {
    onlineUsers,
    lastTagUpdate,
    isConnected,
    joinFile,
    leaveFile,
    notifyTagUpdate,
  } = useCollaboration(collabFileId);

  /** 标签刷新版本号：lastTagUpdate 变化时自增，触发标签数据重新加载 */
  const [tagVersion, setTagVersion] = useState(0);

  /** 加载文件详情 */
  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = getMockFileById(id) ?? null;
      setFile(data);
      setLoading(false);
    }, 200);
  }, [id]);

  /**
   * 监听标签更新事件：当其他用户通过 WebSocket 通知标签变更时
   * - 弹出提示
   * - 自动刷新文件标签数据（自增 tagVersion 触发相关 useEffect）
   */
  useEffect(() => {
    if (!lastTagUpdate) return;
    // 排除自己触发的回环（后端通常会广播给所有用户，包括发送者；这里简化处理）
    message.info(`用户 ${lastTagUpdate.userName} 更新了标签`);
    setTagVersion((v) => v + 1);
  }, [lastTagUpdate]);

  /** 文件加载完成后自动加入协作 */
  useEffect(() => {
    if (file && isConnected) {
      joinFile();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [file, isConnected]);

  /**
   * 通知其他端标签已变更
   * 当前页面没有独立的标签编辑入口，此处封装为 useCallback 供后续标签编辑组件接入；
   * 同时通过 void 使用 notifyTagUpdate 避免 noUnusedLocals 报错。
   */
  const handleNotifyTagUpdate = useCallback(
    (tags: string[]) => {
      notifyTagUpdate(tags);
      setTagVersion((v) => v + 1);
    },
    [notifyTagUpdate],
  );

  /**
   * 标签刷新：tagVersion 变化时（其他用户标签变更触发）重新加载文件
   * 仅在 tagVersion > 0 时触发，避免初始加载重复请求
   */
  useEffect(() => {
    if (tagVersion === 0 || !id) return;
    const data = getMockFileById(id);
    if (data) {
      setFile(data);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tagVersion]);

  // 离开页面时通知后端（组件卸载时由 useCollaboration 内部 cleanup 处理 leave）
  // 这里显式调用一次 leaveFile 以应对路由切换场景
  useEffect(() => {
    return () => {
      leaveFile();
    };
  }, [leaveFile]);

  /** 触发 YARA 扫描 */
  const handleYaraScan = async () => {
    if (!file) return;
    setYaraLoading(true);
    try {
      const res = await scanFile(file.id, file.originalName);
      if (res.code === 200 || res.code === 0) {
        setYaraResult(res.data);
        message.success(`扫描完成：命中 ${res.data.matchedRules} 条规则`);
      } else {
        message.error(res.message || 'YARA 扫描失败');
      }
    } catch {
      message.error('YARA 扫描失败');
    } finally {
      setYaraLoading(false);
    }
  };

  /** 获取 NER 实体 */
  const handleFetchNer = async () => {
    if (!file) return;
    setNerLoading(true);
    try {
      const res = await getNerResult(file.id, file.originalName);
      if (res.code === 200 || res.code === 0) {
        setNerResult(res.data);
      } else {
        message.error(res.message || 'NER 识别失败');
      }
    } catch {
      message.error('NER 识别失败');
    } finally {
      setNerLoading(false);
    }
  };

  /**
   * 拉取 AI 分析结果（威胁摘要 + 攻击链推理）
   * 服务层已内置失败降级，此处再兜底一次以应对极端异常
   */
  const handleFetchAi = async (fileId: string) => {
    setAiLoading(true);
    setAiError(null);
    try {
      const [summaryRes, chainRes] = await Promise.allSettled([
        generateThreatSummary(fileId),
        inferAttackChain(fileId),
      ]);
      // 威胁摘要
      const summaryOk =
        summaryRes.status === 'fulfilled' &&
        (summaryRes.value.code === 200 || summaryRes.value.code === 0);
      if (summaryOk) {
        setThreatSummary(summaryRes.value.data);
      } else {
        setThreatSummary(null);
      }
      // 攻击链
      const chainOk =
        chainRes.status === 'fulfilled' &&
        (chainRes.value.code === 200 || chainRes.value.code === 0);
      if (chainOk) {
        setAttackChain(chainRes.value.data);
      } else {
        setAttackChain(null);
      }
      // 两者均无结果时标记错误（理论上降级保证不会到这里）
      if (!summaryOk && !chainOk) {
        setAiError('AI 分析服务暂不可用，请稍后重试');
      }
      setAiLoaded(true);
    } catch {
      setAiError('AI 分析服务调用异常，请稍后重试');
      setAiLoaded(true);
    } finally {
      setAiLoading(false);
    }
  };

  /** 切换到 AI Tab 时懒加载分析结果（仅加载一次） */
  useEffect(() => {
    if (activeTab === 'ai' && file && !aiLoaded && !aiLoading) {
      handleFetchAi(file.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, file, aiLoaded]);

  /** 复制 */
  const handleCopy = async (text: string, label: string) => {
    const ok = await copyToClipboard(text);
    if (ok) message.success(`已复制${label}`);
    else message.error('复制失败');
  };

  /** YARA 匹配字符串列 */
  const matchStringColumns: ColumnsType<YaraMatchString> = [
    { title: '标识', dataIndex: 'identifier', width: 80, render: (v: string) => <code>{v || '-'}</code> },
    { title: '匹配字符串', dataIndex: 'value', ellipsis: true, render: (v: string) => <code>{v}</code> },
    { title: '偏移', dataIndex: 'offset', width: 100, render: (v: number) => `0x${v.toString(16).toUpperCase()}` },
    { title: '长度', dataIndex: 'length', width: 80 },
  ];

  /** YARA 匹配规则列 */
  const yaraMatchColumns: ColumnsType<YaraMatchResult> = [
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      width: 220,
      render: (v: string, record) => (
        <Space>
          <Tag color={record.severity === 'critical' ? 'error' : record.severity === 'high' ? 'volcano' : 'warning'}>
            {record.severity}
          </Tag>
          <Text strong>{v}</Text>
        </Space>
      ),
    },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '标签',
      dataIndex: 'tags',
      width: 200,
      render: (tags: string[]) => tags?.map((t) => <Tag key={t}>{t}</Tag>),
    },
    {
      title: '匹配数',
      dataIndex: 'matchedStrings',
      width: 80,
      render: (arr: YaraMatchString[]) => arr?.length ?? 0,
    },
  ];

  /** NER 实体列 */
  const nerEntityColumns: ColumnsType<NerEntity> = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 120,
      render: (type: NerEntityType) => <Tag color="blue">{NerEntityTypeLabel[type]}</Tag>,
    },
    {
      title: '值',
      dataIndex: 'value',
      ellipsis: true,
      render: (v: string) => (
        <Space>
          <code>{v}</code>
          <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => handleCopy(v, '实体值')} />
        </Space>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 120,
      render: (c: number) => (
        <Badge status={c >= 0.9 ? 'success' : c >= 0.7 ? 'processing' : 'warning'} text={`${(c * 100).toFixed(0)}%`} />
      ),
    },
    { title: '位置', key: 'position', width: 120, render: (_, r) => `${r.start} - ${r.end}` },
  ];

  /** 渲染 YARA Tab */
  const renderYaraTab = () => (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<ScanOutlined />} loading={yaraLoading} onClick={handleYaraScan}>
          触发 YARA 扫描
        </Button>
        {yaraResult && (
          <Button icon={<ReloadOutlined />} onClick={handleYaraScan}>
            重新扫描
          </Button>
        )}
      </Space>
      {yaraLoading ? (
        <div style={{ textAlign: 'center', padding: 40 }}><Spin tip="YARA 扫描中..." /></div>
      ) : yaraResult ? (
        <div>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={6}><Statistic title="规则总数" value={yaraResult.totalRules} /></Col>
            <Col span={6}>
              <Statistic title="命中规则" value={yaraResult.matchedRules} valueStyle={{ color: colors.error }} prefix={<WarningOutlined />} />
            </Col>
            <Col span={6}><Statistic title="扫描耗时" value={yaraResult.costMs} suffix="ms" /></Col>
            <Col span={6}><Statistic title="扫描时间" value={formatDateTime(yaraResult.scannedAt)} valueStyle={{ fontSize: 13 }} /></Col>
          </Row>
          {yaraResult.matches.length > 0 ? (
            <Table
              columns={yaraMatchColumns}
              dataSource={yaraResult.matches}
              rowKey="ruleId"
              size="small"
              pagination={false}
              expandable={{
                expandedRowRender: (record) => (
                  <Table
                    columns={matchStringColumns}
                    dataSource={record.matchedStrings}
                    rowKey={(r) => `${r.identifier}-${r.offset}`}
                    size="small"
                    pagination={false}
                  />
                ),
              }}
            />
          ) : (
            <Empty description="未命中任何 YARA 规则" />
          )}
        </div>
      ) : (
        <Empty description="点击「触发 YARA 扫描」开始检测恶意特征" />
      )}
      {/* 推荐规则参考 */}
      <Card size="small" title="参考规则集" style={{ marginTop: 16 }}>
        <List
          size="small"
          dataSource={mockYaraRules.slice(0, 5)}
          renderItem={(item) => (
            <List.Item>
              <Space>
                <Tag color="blue">{item.severity}</Tag>
                <Text strong>{item.name}</Text>
                <Text type="secondary">{item.tags?.join(' / ')}</Text>
              </Space>
            </List.Item>
          )}
        />
      </Card>
    </div>
  );

  /** 渲染 NER Tab */
  const renderNerTab = () => (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<ScanOutlined />} loading={nerLoading} onClick={handleFetchNer}>
          获取 NER 实体
        </Button>
        {nerResult && (
          <Button icon={<ReloadOutlined />} onClick={handleFetchNer}>重新识别</Button>
        )}
      </Space>
      {nerLoading ? (
        <div style={{ textAlign: 'center', padding: 40 }}><Spin tip="NER 识别中..." /></div>
      ) : nerResult ? (
        <div>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={6}><Statistic title="实体总数" value={nerResult.totalEntities} valueStyle={{ color: colors.info }} /></Col>
            <Col span={6}><Statistic title="类型数" value={nerResult.typeDistribution.length} /></Col>
            <Col span={6}><Statistic title="文本长度" value={nerResult.textLength} suffix="字符" /></Col>
            <Col span={6}><Statistic title="处理耗时" value={nerResult.costMs} suffix="ms" /></Col>
          </Row>
          <Space wrap style={{ marginBottom: 16 }}>
            {nerResult.typeDistribution.map((d) => (
              <Tag key={d.type} color="purple">{NerEntityTypeLabel[d.type]}: {d.count}</Tag>
            ))}
          </Space>
          <Table
            columns={nerEntityColumns}
            dataSource={nerResult.entities}
            rowKey="id"
            size="small"
            pagination={{ pageSize: 10, showSizeChanger: false }}
          />
        </div>
      ) : (
        <Empty description="点击「获取 NER 实体」提取文件中的命名实体" />
      )}
    </div>
  );

  /** 渲染元数据 Tab */
  const renderMetaTab = () => {
    if (!file) return null;
    return (
      <ProDescriptions
        column={2}
        bordered
        title="文件元数据"
        size="small"
        dataSource={{
          id: file.id,
          originalName: file.originalName,
          storageName: file.name,
          size: formatFileSize(file.size),
          type: fileTypeLabel[file.type],
          mimeType: file.mimeType,
          status: statusMap[file.status].text,
          sensitivity: file.sensitivity ? `${file.sensitivity} - ${SensitivityLabel[file.sensitivity]}` : '-',
          classification: file.classification
            ? `${file.classification} - ${FileClassificationLabel[file.classification as FileClassification] ?? file.classification}`
            : '未分级',
          uploader: file.uploaderName,
          target: file.targetName ?? '-',
          isPublic: file.isPublic ? '是' : '否',
          createTime: formatDateTime(file.createTime),
          updateTime: formatDateTime(file.updateTime),
          parsedAt: file.parsedAt ? formatDateTime(file.parsedAt) : '-',
        }}
        columns={[
          { title: '文件 ID', dataIndex: 'id', key: 'id' },
          { title: '原始文件名', dataIndex: 'originalName', key: 'originalName' },
          { title: '存储名称', dataIndex: 'storageName', key: 'storageName' },
          { title: '文件大小', dataIndex: 'size', key: 'size' },
          { title: '文件类型', dataIndex: 'type', key: 'type' },
          { title: 'MIME 类型', dataIndex: 'mimeType', key: 'mimeType' },
          { title: '状态', dataIndex: 'status', key: 'status' },
          { title: '敏感等级', dataIndex: 'sensitivity', key: 'sensitivity' },
          { title: '密级', dataIndex: 'classification', key: 'classification' },
          { title: '上传者', dataIndex: 'uploader', key: 'uploader' },
          { title: '关联目标', dataIndex: 'target', key: 'target' },
          { title: '是否公开', dataIndex: 'isPublic', key: 'isPublic' },
          { title: '上传时间', dataIndex: 'createTime', key: 'createTime' },
          { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
          { title: '解析完成', dataIndex: 'parsedAt', key: 'parsedAt' },
        ]}
      />
    );
  };

  /** 渲染解析结果 Tab */
  const renderParseTab = () => (
    <div>
      <Card size="small" title="解析摘要" style={{ marginBottom: 16 }}>
        <Paragraph>
          文件 <Text strong>{file?.originalName}</Text> 已完成解析，共识别
          <Text strong style={{ color: colors.error }}> 12 </Text> 个可疑特征，
          <Text strong style={{ color: colors.warning }}> 5 </Text> 个 IOC，
          <Text strong style={{ color: colors.info }}> 28 </Text> 个实体。
        </Paragraph>
        <Space wrap>
          <Tag color="red">含 webshell 特征</Tag>
          <Tag color="orange">混淆代码</Tag>
          <Tag color="blue">C2 通信</Tag>
          <Tag color="purple">加密载荷</Tag>
        </Space>
      </Card>

      <Card size="small" title="沙箱报告" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}><Statistic title="运行环境" value={sandboxReport.environment} valueStyle={{ fontSize: 13 }} /></Col>
          <Col span={6}><Statistic title="运行时长" value={sandboxReport.duration} suffix="s" /></Col>
          <Col span={6}>
            <Statistic
              title="威胁评分"
              value={sandboxReport.score}
              suffix="/100"
              valueStyle={{ color: sandboxReport.score >= 70 ? colors.error : colors.warning }}
            />
          </Col>
          <Col span={6}><Statistic title="进程数" value={sandboxReport.processes.length} /></Col>
        </Row>
      </Card>

      <Card size="small" title="进程行为链" style={{ marginBottom: 16 }}>
        <Table
          size="small"
          rowKey="pid"
          pagination={false}
          dataSource={sandboxReport.processes}
          columns={[
            { title: 'PID', dataIndex: 'pid', width: 80 },
            { title: '进程名', dataIndex: 'name' },
            { title: '父 PID', dataIndex: 'parent', width: 100 },
            { title: '行为', dataIndex: 'action', render: (v: string) => <Tag color="orange">{v}</Tag> },
          ]}
        />
      </Card>

      <Card size="small" title="网络行为">
        <Table
          size="small"
          rowKey="dst"
          pagination={false}
          dataSource={sandboxReport.network}
          columns={[
            { title: '目标地址', dataIndex: 'dst' },
            { title: '协议', dataIndex: 'proto', render: (v: string) => <Tag color="blue">{v}</Tag> },
            { title: '流量(Byte)', dataIndex: 'bytes' },
          ]}
        />
      </Card>
    </div>
  );

  /** 渲染关联 IOC Tab */
  const renderIocTab = () => {
    const related = mockThreatIntelItems.slice(0, 4);
    return (
      <Card size="small" title={<><BugOutlined /> 关联威胁情报 ({related.length})</>}>
        <Table
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={related}
          columns={[
            { title: '类型', dataIndex: 'type', width: 80, render: (v: string) => <Tag color="red">{v}</Tag> },
            { title: '值', dataIndex: 'value', ellipsis: true, render: (v: string) => <code>{v}</code> },
            { title: '置信度', dataIndex: 'confidence', width: 100, render: (v: number) => `${(v * 100).toFixed(0)}%` },
            { title: 'APT', dataIndex: 'threatActors', render: (arr: string[]) => arr?.map((t) => <Tag key={t}>{t}</Tag>) },
            { title: '出现次数', dataIndex: 'occurrences', width: 100 },
          ]}
        />
      </Card>
    );
  };

  /** 渲染 AI 分析 Tab */
  const renderAiTab = () => {
    // 加载中：Skeleton
    if (aiLoading && !threatSummary && !attackChain) {
      return (
        <div data-testid="ai-tab-loading">
          <Skeleton active paragraph={{ rows: 4 }} style={{ marginBottom: 16 }} />
          <Skeleton active paragraph={{ rows: 6 }} />
        </div>
      );
    }

    // 错误降级：Result status="warning" + 重试按钮
    if (aiError && !threatSummary && !attackChain) {
      return (
        <Result
          status="warning"
          title="AI 分析结果加载失败"
          subTitle={aiError}
          extra={
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              onClick={() => {
                setAiLoaded(false);
                if (file) handleFetchAi(file.id);
              }}
            >
              重新分析
            </Button>
          }
        />
      );
    }

    // 置信度颜色映射
    const confidenceColor = (c: number) =>
      c >= 0.8 ? 'green' : c >= 0.6 ? 'blue' : c >= 0.4 ? 'orange' : 'red';

    return (
      <div data-testid="ai-tab-content">
        <Space style={{ marginBottom: 16 }}>
          <Button
            type="primary"
            icon={<RobotOutlined />}
            loading={aiLoading}
            onClick={() => {
              if (!file) return;
              setAiLoaded(false);
              handleFetchAi(file.id);
            }}
          >
            重新生成 AI 分析
          </Button>
          <Text type="secondary" style={{ fontSize: 12 }}>
            威胁摘要与攻击链由 ai-service（端口 8093）生成，失败时自动降级到 Mock 数据
          </Text>
        </Space>

        {/* 威胁摘要卡片 */}
        <Card
          size="small"
          style={{ marginBottom: 16 }}
          title={
            <Space>
              <RobotOutlined style={{ color: colors.info }} />
              <span>威胁摘要</span>
              {threatSummary && (
                <Tag color="blue">{threatSummary.model}</Tag>
              )}
            </Space>
          }
        >
          {threatSummary ? (
            <div>
              <Paragraph>{threatSummary.summary}</Paragraph>
              <div style={{ marginBottom: 12 }}>
                <Text strong>关键发现</Text>
              </div>
              <List
                size="small"
                bordered
                dataSource={threatSummary.keyFindings}
                style={{ marginBottom: 12 }}
                renderItem={(finding, idx) => (
                  <List.Item>
                    <Space align="start">
                      <Tag color="volcano">{idx + 1}</Tag>
                      <Text>{finding}</Text>
                    </Space>
                  </List.Item>
                )}
              />
              <Descriptions size="small" column={3} bordered>
                <Descriptions.Item label="使用模型">{threatSummary.model}</Descriptions.Item>
                <Descriptions.Item label="Token 消耗">{threatSummary.tokens}</Descriptions.Item>
                <Descriptions.Item label="生成时间">{formatDateTime(threatSummary.createdAt)}</Descriptions.Item>
              </Descriptions>
            </div>
          ) : (
            <Empty description="暂无威胁摘要数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>

        {/* 攻击链推理卡片 */}
        <Card
          size="small"
          title={
            <Space>
              <BugOutlined style={{ color: colors.error }} />
              <span>攻击链推理</span>
              {attackChain && (
                <Tag color={confidenceColor(attackChain.confidence)}>
                  置信度 {(attackChain.confidence * 100).toFixed(0)}%
                </Tag>
              )}
            </Space>
          }
        >
          {attackChain ? (
            <div>
              <Paragraph type="secondary">{attackChain.reasoning}</Paragraph>
              {attackChain.attackPaths.length > 0 ? (
                <Collapse
                  defaultActiveKey={attackChain.attackPaths.map((_, i) => String(i))}
                  items={attackChain.attackPaths.map((path, idx) => ({
                    key: String(idx),
                    label: (
                      <Space>
                        <Tag color="processing">路径 {idx + 1}</Tag>
                        <Text strong>{path.name}</Text>
                      </Space>
                    ),
                    children: (
                      <div>
                        <Paragraph>{path.description}</Paragraph>
                        <Text strong>攻击步骤：</Text>
                        <List
                          size="small"
                          dataSource={path.steps}
                          renderItem={(step) => (
                            <List.Item style={{ padding: '6px 0' }}>
                              <Text style={{ fontFamily: 'monospace' }}>{step}</Text>
                            </List.Item>
                          )}
                        />
                      </div>
                    ),
                  }))}
                />
              ) : (
                <Empty description="未识别到攻击路径" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </div>
          ) : (
            <Empty description="暂无攻击链推理数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      </div>
    );
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载文件详情..." /></div>;
  }

  if (!file) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到文件">
          <Button type="primary" onClick={() => navigate('/files')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  /**
   * 顶部密级水印 Alert
   * - SECRET: error "该文件为机密文件，仅授权用户可访问"
   * - CONFIDENTIAL: warning "该文件为秘密文件"
   * - INTERNAL: info "该文件为内部文件"
   * - PUBLIC: 不显示
   */
  const renderClassificationAlert = () => {
    const cls = file.classification as FileClassification | undefined;
    if (!cls) {
      return null;
    }
    switch (cls) {
      case FileClassification.SECRET:
        return (
          <Alert
            type="error"
            showIcon
            banner
            message="该文件为机密文件，仅授权用户可访问"
            description="未经授权的访问、复制、传播均被严格禁止，所有操作将记录审计日志。"
            data-testid="classification-alert-secret"
          />
        );
      case FileClassification.CONFIDENTIAL:
        return (
          <Alert
            type="warning"
            showIcon
            banner
            message="该文件为秘密文件"
            description="仅限授权范围内使用，请遵守相关保密规定。"
            data-testid="classification-alert-confidential"
          />
        );
      case FileClassification.INTERNAL:
        return (
          <Alert
            type="info"
            showIcon
            banner
            message="该文件为内部文件"
            description="仅供组织内部使用，请勿外传。"
            data-testid="classification-alert-internal"
          />
        );
      case FileClassification.PUBLIC:
      default:
        return null;
    }
  };

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部密级水印 */}
      {renderClassificationAlert() && (
        <div style={{ marginBottom: spacing[4] }} data-testid="classification-alert-wrapper">
          {renderClassificationAlert()}
        </div>
      )}
      {/* 顶部导航 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/files')}>返回</Button>
          <FileIcon type={file.type} size={24} />
          <Title level={4} style={{ margin: 0 }}>{file.originalName}</Title>
          <Tag color={fileTypeColor[file.type]}>{fileTypeLabel[file.type]}</Tag>
          <Tag color={statusMap[file.status].color}>{statusMap[file.status].text}</Tag>
          {file.sensitivity && (
            <Tag color="volcano">{file.sensitivity} - {SensitivityLabel[file.sensitivity]}</Tag>
          )}
          <ClassificationTag
            classification={file.classification}
            showCode
            data-testid="classification-tag-detail"
          />
        </Space>
        <Space>
          {/* V4.7-P1-3 在线用户 Badge（WebSocket 协同） */}
          <OnlineUsersBadge onlineUsers={onlineUsers} isConnected={isConnected} />
          <Button
            icon={<TagsOutlined />}
            onClick={() => handleNotifyTagUpdate(file.tags)}
            disabled={!isConnected}
            aria-label="同步标签到协作端"
          >
            同步标签
          </Button>
          <Button icon={<DownloadOutlined />} onClick={() => message.success('开始下载...')}>下载</Button>
          <Button icon={<ReloadOutlined />} onClick={() => message.success('已请求重新解析')}>重新解析</Button>
          <Button type="primary" icon={<ScanOutlined />} onClick={handleYaraScan} loading={yaraLoading}>YARA 扫描</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col span={6}><Card size="small"><Statistic title="文件大小" value={formatFileSize(file.size)} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="标签数" value={file.tags.length} prefix={<TagsOutlined />} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="关联目标" value={file.targetName ?? '-'} valueStyle={{ fontSize: 16 }} /></Card></Col>
        <Col span={6}><Card size="small"><Statistic title="上传者" value={file.uploaderName} valueStyle={{ fontSize: 16 }} /></Card></Col>
      </Row>

      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: 'yara', label: <span><FileSearchOutlined /> YARA 匹配</span>, children: renderYaraTab() },
            { key: 'ner', label: <span><FileTextOutlined /> NER 实体</span>, children: renderNerTab() },
            { key: 'meta', label: <span><FileTextOutlined /> 元数据</span>, children: renderMetaTab() },
            { key: 'parse', label: <span><FileTextOutlined /> 解析结果</span>, children: renderParseTab() },
            { key: 'ioc', label: <span><BugOutlined /> 关联 IOC</span>, children: renderIocTab() },
            { key: 'ai', label: <span><RobotOutlined /> AI 分析</span>, children: renderAiTab() },
            { key: 'dynamic', label: <span><ExperimentOutlined /> 动态分析</span>, children: <DynamicAnalysisTab fileId={file.id} /> },
          ]}
        />
      </Card>

      {/* 哈希信息 */}
      <Card size="small" title="哈希与存储信息" style={{ marginTop: spacing[4] }}>
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="MD5">
            <Space>
              <code>{file.hash}</code>
              <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => handleCopy(file.hash, 'MD5')} />
            </Space>
          </Descriptions.Item>
          {file.sm3 && (
            <Descriptions.Item label="SM3（国密）">
              <Space>
                <code>{file.sm3}</code>
                <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => handleCopy(file.sm3 as string, 'SM3')} />
              </Space>
            </Descriptions.Item>
          )}
          <Descriptions.Item label="存储路径"><code>{file.path}</code></Descriptions.Item>
        </Descriptions>
      </Card>

      {/* V4.7-P1-2 文件评审区域：状态 Tag + 意见列表 + 提交/审批操作 */}
      <div style={{ marginTop: spacing[4] }} data-testid="file-review-section-wrapper">
        <FileReviewSection
          fileId={file.id}
          currentUser={MOCK_CURRENT_USER}
          onChange={() => {
            // 评审状态变更后刷新文件数据（评审状态可能影响文件可见性等）
            const data = getMockFileById(id);
            if (data) setFile(data);
          }}
        />
      </div>
    </div>
  );
};

export default FileDetailPage;
