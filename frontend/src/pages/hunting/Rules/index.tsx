/**
 * 狩猎规则管理（V5.3）
 * - Sigma / YARA 规则列表
 * - 规则导入（Sigma YAML / YARA 源码）
 * - 规则测试（对指定文件测试命中）
 * - 规则统计（命中次数、测试次数、版本）
 */
import React, { useEffect, useState } from 'react';
import {
  Card,
  Table,
  Tag,
  Space,
  Button,
  Modal,
  Input,
  Tabs,
  Descriptions,
  Statistic,
  Row,
  Col,
  Spin,
  message,
  Typography,
  Drawer,
  Badge,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ImportOutlined,
  PlayCircleOutlined,
  CodeOutlined,
  BugOutlined,
  BarChartOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  listHuntingRules,
  getHuntingRule,
  importSigmaRule,
  importYaraRule,
  testHuntingRule,
  getHuntingRuleStats,
} from '@/services/hunting';
import type {
  HuntingRule,
  RuleTestResult,
  RuleStats,
  ImportRulePayload,
} from '@/types';
import { HuntingRuleType, HuntingRuleTypeLabel } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

/** 严重等级颜色 */
const severityColor: Record<string, string> = {
  critical: 'red',
  high: 'volcano',
  medium: 'orange',
  low: 'blue',
  info: 'default',
};

const HuntingRules: React.FC = () => {
  const [rules, setRules] = useState<HuntingRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importType, setImportType] = useState<HuntingRuleType>(HuntingRuleType.SIGMA);
  const [importContent, setImportContent] = useState('');
  const [importing, setImporting] = useState(false);
  const [detail, setDetail] = useState<HuntingRule | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [testOpen, setTestOpen] = useState(false);
  const [testingRule, setTestingRule] = useState<HuntingRule | null>(null);
  const [testFileId, setTestFileId] = useState('');
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<RuleTestResult | null>(null);
  const [stats, setStats] = useState<RuleStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);

  /** 加载规则列表 */
  const loadRules = () => {
    setLoading(true);
    listHuntingRules()
      .then((res) => {
        if (res.code === 200 || res.code === 0) {
          setRules(res.data);
        }
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadRules();
  }, []);

  /** 打开导入弹窗 */
  const handleOpenImport = (type: HuntingRuleType) => {
    setImportType(type);
    setImportContent('');
    setImportOpen(true);
  };

  /** 执行导入 */
  const handleImport = async () => {
    if (!importContent.trim()) {
      message.warning('请输入规则内容');
      return;
    }
    setImporting(true);
    try {
      const payload: ImportRulePayload = { content: importContent };
      const res =
        importType === HuntingRuleType.SIGMA
          ? await importSigmaRule(payload)
          : await importYaraRule(payload);
      if (res.code === 200 || res.code === 0) {
        message.success(`${HuntingRuleTypeLabel[importType]} 规则已导入：${res.data}`);
        setImportOpen(false);
        loadRules();
      } else {
        message.error(res.message || '导入失败');
      }
    } catch {
      message.error('导入失败');
    } finally {
      setImporting(false);
    }
  };

  /** 查看规则详情 */
  const openDetail = async (rule: HuntingRule) => {
    setDrawerOpen(true);
    setStatsLoading(true);
    setStats(null);
    // 先用列表数据展示
    setDetail(rule);
    // 拉取最新详情与统计
    try {
      const [detailRes, statsRes] = await Promise.all([
        getHuntingRule(rule.id),
        getHuntingRuleStats(rule.id),
      ]);
      if (detailRes.code === 200 || detailRes.code === 0) {
        setDetail(detailRes.data);
      }
      if (statsRes.code === 200 || statsRes.code === 0) {
        setStats(statsRes.data);
      }
    } finally {
      setStatsLoading(false);
    }
  };

  /** 打开测试弹窗 */
  const openTest = (rule: HuntingRule) => {
    setTestingRule(rule);
    setTestFileId('');
    setTestResult(null);
    setTestOpen(true);
  };

  /** 执行规则测试 */
  const handleTest = async () => {
    if (!testingRule || !testFileId.trim()) {
      message.warning('请输入文件ID');
      return;
    }
    setTesting(true);
    setTestResult(null);
    try {
      const res = await testHuntingRule(testingRule.id, testFileId.trim());
      if (res.code === 200 || res.code === 0) {
        setTestResult(res.data);
        if (res.data.matched) {
          message.success('规则命中！');
        } else {
          message.info('规则未命中');
        }
      } else {
        message.error(res.message || '测试失败');
      }
    } catch {
      message.error('测试失败');
    } finally {
      setTesting(false);
    }
  };

  /** 规则列表列 */
  const columns: ColumnsType<HuntingRule> = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 80,
      render: (v: string) => (
        <Tag color={v === 'SIGMA' ? 'blue' : 'purple'}>{HuntingRuleTypeLabel[v as HuntingRuleType] ?? v}</Tag>
      ),
    },
    {
      title: '规则名称',
      dataIndex: 'name',
      render: (v: string, r) => (
        <Space>
          <a onClick={() => openDetail(r)}>{v}</a>
          {!r.enabled && <Badge status="default" text="已禁用" />}
        </Space>
      ),
    },
    {
      title: '严重等级',
      dataIndex: 'severity',
      width: 90,
      render: (v: string) => (v ? <Tag color={severityColor[v] ?? 'default'}>{v}</Tag> : '-'),
    },
    {
      title: 'ATT&CK',
      dataIndex: 'attackTechniqueIds',
      width: 160,
      render: (ids: string[]) =>
        ids.length > 0 ? (
          <Space wrap size={4}>
            {ids.map((id) => (
              <Tag key={id} color="red">{id}</Tag>
            ))}
          </Space>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '命中/测试',
      key: 'stats',
      width: 110,
      render: (_, r) => (
        <Text>
          <Text strong style={{ color: r.matchCount > 0 ? colors.error : undefined }}>
            {r.matchCount}
          </Text>
          {' / '}
          {r.testCount}
        </Text>
      ),
    },
    { title: '版本', dataIndex: 'version', width: 70 },
    {
      title: '最近命中',
      dataIndex: 'lastMatchTime',
      width: 160,
      render: (t: string) => (t ? formatDateTime(t) : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, r) => (
        <Space>
          <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => openTest(r)}>
            测试
          </Button>
          <Button type="link" size="small" onClick={() => openDetail(r)}>
            详情
          </Button>
        </Space>
      ),
    },
  ];

  /** 统计 */
  const totalStats = {
    total: rules.length,
    sigma: rules.filter((r) => r.type === 'SIGMA').length,
    yara: rules.filter((r) => r.type === 'YARA').length,
    matches: rules.reduce((sum, r) => sum + r.matchCount, 0),
  };

  return (
    <div style={{ padding: spacing[4] }} data-testid="hunting-rules-page">
      <Title level={4}>
        <CodeOutlined style={{ marginRight: 8, color: colors.primary[500] }} />
        狩猎规则管理
      </Title>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col span={6}>
          <Card size="small"><Statistic title="规则总数" value={totalStats.total} prefix={<CodeOutlined />} /></Card>
        </Col>
        <Col span={6}>
          <Card size="small"><Statistic title="Sigma 规则" value={totalStats.sigma} prefix={<BugOutlined />} /></Card>
        </Col>
        <Col span={6}>
          <Card size="small"><Statistic title="YARA 规则" value={totalStats.yara} prefix={<CodeOutlined />} /></Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="累计命中"
              value={totalStats.matches}
              valueStyle={{ color: colors.error }}
              prefix={<BarChartOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* 规则列表 */}
      <Card
        title={
          <Space>
            <BugOutlined />
            <span>规则列表</span>
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ImportOutlined />} onClick={() => handleOpenImport(HuntingRuleType.SIGMA)} data-testid="import-sigma-btn">
              导入 Sigma
            </Button>
            <Button icon={<ImportOutlined />} onClick={() => handleOpenImport(HuntingRuleType.YARA)} data-testid="import-yara-btn">
              导入 YARA
            </Button>
            <Button icon={<ReloadOutlined />} onClick={loadRules}>刷新</Button>
          </Space>
        }
      >
        <Table
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={rules}
          loading={loading}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          data-testid="hunting-rules-table"
        />
      </Card>

      {/* 导入规则弹窗 */}
      <Modal
        title={`导入 ${HuntingRuleTypeLabel[importType]} 规则`}
        open={importOpen}
        onOk={handleImport}
        onCancel={() => setImportOpen(false)}
        okText="导入"
        cancelText="取消"
        confirmLoading={importing}
        width={720}
        destroyOnClose
      >
        <Tabs
          activeKey={importType}
          onChange={(k) => setImportType(k as HuntingRuleType)}
          items={[
            { key: HuntingRuleType.SIGMA, label: 'Sigma (YAML)' },
            { key: HuntingRuleType.YARA, label: 'YARA' },
          ]}
        />
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          {importType === HuntingRuleType.SIGMA
            ? '粘贴 Sigma YAML 规则，系统将自动解析 title / description / tags / attack 技术。'
            : '粘贴 YARA 规则源码，支持版本管理。'}
        </Paragraph>
        <TextArea
          rows={14}
          value={importContent}
          onChange={(e) => setImportContent(e.target.value)}
          placeholder={
            importType === HuntingRuleType.SIGMA
              ? 'title: Suspicious PowerShell\nstatus: experimental\ndescription: ...\nlogsource:\n  product: windows\ndetection:\n  selection:\n    Image|endswith: powershell.exe\n  condition: selection'
              : 'rule Example_Rule {\n  meta:\n    description = "Example"\n  strings:\n    $s1 = "malware"\n  condition:\n    $s1\n}'
          }
          style={{ fontFamily: 'monospace', fontSize: 12 }}
          data-testid="import-rule-content"
        />
      </Modal>

      {/* 规则详情抽屉 */}
      <Drawer
        title="规则详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={720}
      >
        {detail && (
          <div>
            <Descriptions column={1} bordered size="small" style={{ marginBottom: spacing[3] }}>
              <Descriptions.Item label="类型">
                <Tag color={detail.type === 'SIGMA' ? 'blue' : 'purple'}>
                  {HuntingRuleTypeLabel[detail.type as HuntingRuleType] ?? detail.type}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="名称">{detail.name}</Descriptions.Item>
              <Descriptions.Item label="描述">{detail.description ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="作者">{detail.author ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="严重等级">
                {detail.severity ? <Tag color={severityColor[detail.severity] ?? 'default'}>{detail.severity}</Tag> : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="标签">
                {detail.tags.length > 0 ? (
                  <Space wrap>{detail.tags.map((t) => <Tag key={t}>{t}</Tag>)}</Space>
                ) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="ATT&CK 技术">
                {detail.attackTechniqueIds.length > 0 ? (
                  <Space wrap>
                    {detail.attackTechniqueIds.map((id) => <Tag key={id} color="red">{id}</Tag>)}
                  </Space>
                ) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="版本">{detail.version}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {detail.enabled ? <Badge status="success" text="启用" /> : <Badge status="default" text="禁用" />}
              </Descriptions.Item>
            </Descriptions>

            {/* 统计信息 */}
            <Card size="small" title={<><BarChartOutlined /> 命中统计</>} style={{ marginBottom: spacing[3] }} loading={statsLoading}>
              <Row gutter={16}>
                <Col span={8}><Statistic title="命中次数" value={stats?.matchCount ?? detail.matchCount} valueStyle={{ color: colors.error }} /></Col>
                <Col span={8}><Statistic title="测试次数" value={stats?.testCount ?? detail.testCount} /></Col>
                <Col span={8}><Statistic title="最近命中" value={stats?.lastMatchTime ? formatDateTime(stats.lastMatchTime) : (detail.lastMatchTime ? formatDateTime(detail.lastMatchTime) : '-')} valueStyle={{ fontSize: 13 }} /></Col>
              </Row>
            </Card>

            {/* 规则内容 */}
            <Card size="small" title={<><CodeOutlined /> 规则源码</>}>
              <pre style={{ background: '#f5f5f5', padding: 12, borderRadius: 4, fontSize: 12, maxHeight: 360, overflow: 'auto' }}>
                {detail.content}
              </pre>
            </Card>

            <Space style={{ marginTop: spacing[3] }}>
              <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => openTest(detail)}>
                测试规则
              </Button>
            </Space>
          </div>
        )}
      </Drawer>

      {/* 测试规则弹窗 */}
      <Modal
        title={`测试规则：${testingRule?.name ?? ''}`}
        open={testOpen}
        onCancel={() => setTestOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setTestOpen(false)}>关闭</Button>,
          <Button key="test" type="primary" icon={<PlayCircleOutlined />} loading={testing} onClick={handleTest}>
            执行测试
          </Button>,
        ]}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={16}>
          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>文件 ID</Text>
            <Input
              placeholder="请输入文件ID，如 1 或 f0001"
              value={testFileId}
              onChange={(e) => setTestFileId(e.target.value)}
              data-testid="test-file-id-input"
            />
          </div>
          {testing && <Spin tip="测试中..." />}
          {testResult && (
            <Card size="small" data-testid="test-result">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="结果">
                  {testResult.matched ? (
                    <Tag color="red" icon={<BugOutlined />}>命中</Tag>
                  ) : (
                    <Tag color="default">未命中</Tag>
                  )}
                </Descriptions.Item>
                {testResult.matchCount !== undefined && (
                  <Descriptions.Item label="命中数">{testResult.matchCount}</Descriptions.Item>
                )}
                {testResult.costMs !== undefined && (
                  <Descriptions.Item label="耗时">{testResult.costMs} ms</Descriptions.Item>
                )}
                {testResult.details && (
                  <Descriptions.Item label="详情">{testResult.details}</Descriptions.Item>
                )}
              </Descriptions>
            </Card>
          )}
        </Space>
      </Modal>
    </div>
  );
};

export default HuntingRules;
