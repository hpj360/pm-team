/**
 * 文件对比页
 * - 双栏布局：左侧 vs 右侧
 * - 支持选择两个文件进行对比
 * - 对比维度：基本信息 / 哈希 / YARA 命中 / NER 实体 / 风险评分
 */
import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Select,
  Empty,
  Spin,
  Row,
  Col,
  Descriptions,
  Statistic,
  Table,
  message,
  Divider,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  SwapOutlined,
  FileTextOutlined,
  BugOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import { mockFileList } from '@/mock/file';
import { mockYaraRules } from '@/mock/yara';
import { mockThreatIntelItems } from '@/mock/threatIntel';
import type { FileInfo } from '@/types';
import { FileStatus, SensitivityLevel, SensitivityLabel } from '@/types';
import { formatDateTime, formatFileSize } from '@/utils';
import { fileTypeLabel, fileTypeColor } from '@/utils/fileType';
import { colors, spacing } from '@/styles/tokens';
import FileIcon from '@/components/common/FileIcon';

const { Title, Text } = Typography;

/** Mock YARA 命中结果（基于文件 id） */
interface YaraMatchSummary {
  total: number;
  matched: number;
  critical: number;
  high: number;
  rules: Array<{ name: string; severity: 'info' | 'low' | 'medium' | 'high' | 'critical' }>;
}

/** Mock NER 实体摘要 */
interface NerSummary {
  total: number;
  types: number;
  ip: number;
  domain: number;
  email: number;
  cve: number;
}

/** Mock 风险评分 */
interface RiskScore {
  overall: number;
  malicious: number;
  suspicious: number;
}

/** 对比项差异 */
type DiffStatus = 'same' | 'diff' | 'na';

/** 根据文件生成 mock 数据 */
function buildMockData(fileId: string) {
  const seed = fileId.length + fileId.charCodeAt(0);
  const yaraMatched = mockYaraRules.slice(0, (seed % 4) + 1);
  const yaraSummary: YaraMatchSummary = {
    total: mockYaraRules.length,
    matched: yaraMatched.length,
    critical: yaraMatched.filter((r) => r.severity === 'critical').length,
    high: yaraMatched.filter((r) => r.severity === 'high').length,
    rules: yaraMatched.map((r) => ({ name: r.name, severity: r.severity })),
  };
  const nerSummary: NerSummary = {
    total: 12 + (seed % 10),
    types: 5 + (seed % 3),
    ip: 2 + (seed % 3),
    domain: 3 + (seed % 2),
    email: 1 + (seed % 2),
    cve: 1 + (seed % 3),
  };
  const risk: RiskScore = {
    overall: 40 + (seed % 50),
    malicious: yaraSummary.critical + yaraSummary.high,
    suspicious: yaraSummary.matched - yaraSummary.critical - yaraSummary.high,
  };
  return { yaraSummary, nerSummary, risk };
}

const FileComparePage: React.FC = () => {
  const navigate = useNavigate();
  const [fileAId, setFileAId] = useState<string | undefined>(mockFileList[0]?.id);
  const [fileBId, setFileBId] = useState<string | undefined>(mockFileList[1]?.id);
  const [fileA, setFileA] = useState<FileInfo | null>(null);
  const [fileB, setFileB] = useState<FileInfo | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      setFileA(mockFileList.find((f) => f.id === fileAId) ?? null);
      setFileB(mockFileList.find((f) => f.id === fileBId) ?? null);
      setLoading(false);
    }, 200);
  }, [fileAId, fileBId]);

  /** Mock 数据 */
  const dataA = useMemo(() => (fileA ? buildMockData(fileA.id) : null), [fileA]);
  const dataB = useMemo(() => (fileB ? buildMockData(fileB.id) : null), [fileB]);

  /** 对比差异 */
  const getDiffStatus = (a?: unknown, b?: unknown): DiffStatus => {
    if (a === undefined || b === undefined) return 'na';
    return a === b ? 'same' : 'diff';
  };

  /** 差异标记 */
  const diffTag = (status: DiffStatus) => {
    if (status === 'same') return <Tag color="success" icon={<CheckCircleOutlined />}>一致</Tag>;
    if (status === 'diff') return <Tag color="error" icon={<CloseCircleOutlined />}>差异</Tag>;
    return <Tag icon={<MinusCircleOutlined />}>N/A</Tag>;
  };

  /** 文件选项 */
  const fileOptions = mockFileList.map((f) => ({
    value: f.id,
    label: `${f.id} · ${f.originalName}`,
  }));

  /** YARA 命中列 */
  const yaraColumns: ColumnsType<YaraMatchSummary['rules'][number]> = [
    {
      title: '规则名',
      dataIndex: 'name',
      render: (v: string) => <Text strong>{v}</Text>,
    },
    {
      title: '严重程度',
      dataIndex: 'severity',
      width: 100,
      render: (v: YaraMatchSummary['rules'][number]['severity']) => {
        const color = v === 'critical' ? 'red' : v === 'high' ? 'volcano' : v === 'medium' ? 'orange' : 'blue';
        return <Tag color={color}>{v}</Tag>;
      },
    },
  ];

  /** 单侧渲染 */
  const renderSide = (file: FileInfo | null, data: ReturnType<typeof buildMockData> | null, side: 'A' | 'B') => {
    if (!file || !data) {
      return <Empty description={`请选择文件 ${side}`} />;
    }
    return (
      <div>
        <Space style={{ marginBottom: 12 }}>
          <FileIcon type={file.type} size={20} />
          <Text strong>{file.originalName}</Text>
          <Tag color={fileTypeColor[file.type]}>{fileTypeLabel[file.type]}</Tag>
        </Space>
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="文件 ID">{file.id}</Descriptions.Item>
          <Descriptions.Item label="大小">{formatFileSize(file.size)}</Descriptions.Item>
          <Descriptions.Item label="敏感等级">
            <Tag color="volcano">{file.sensitivity} - {SensitivityLabel[file.sensitivity ?? SensitivityLevel.L1]}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={file.status === FileStatus.COMPLETED ? 'success' : 'default'}>
              {file.status === FileStatus.COMPLETED ? '已完成' : file.status === FileStatus.PROCESSING ? '处理中' : file.status === FileStatus.FAILED ? '失败' : '待处理'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="上传者">{file.uploaderName}</Descriptions.Item>
          <Descriptions.Item label="上传时间">{formatDateTime(file.createTime)}</Descriptions.Item>
          <Descriptions.Item label="MD5"><code>{file.hash}</code></Descriptions.Item>
        </Descriptions>

        <Row gutter={8} style={{ marginTop: 12 }}>
          <Col span={8}>
            <Card size="small">
              <Statistic
                title="风险评分"
                value={data.risk.overall}
                suffix="/100"
                valueStyle={{ color: data.risk.overall >= 70 ? colors.error : data.risk.overall >= 50 ? colors.warning : colors.success, fontSize: 18 }}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small"><Statistic title="YARA 命中" value={data.yaraSummary.matched} valueStyle={{ fontSize: 18 }} /></Card>
          </Col>
          <Col span={8}>
            <Card size="small"><Statistic title="NER 实体" value={data.nerSummary.total} valueStyle={{ fontSize: 18 }} /></Card>
          </Col>
        </Row>

        <Divider orientation="left" style={{ fontSize: 13 }}>YARA 命中规则</Divider>
        {data.yaraSummary.rules.length === 0 ? (
          <Empty description="未命中" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Table
            size="small"
            rowKey="name"
            pagination={false}
            columns={yaraColumns}
            dataSource={data.yaraSummary.rules}
          />
        )}

        <Divider orientation="left" style={{ fontSize: 13 }}>NER 实体分布</Divider>
        <Space wrap>
          <Tag color="blue">IP: {data.nerSummary.ip}</Tag>
          <Tag color="green">域名: {data.nerSummary.domain}</Tag>
          <Tag color="cyan">邮箱: {data.nerSummary.email}</Tag>
          <Tag color="red">CVE: {data.nerSummary.cve}</Tag>
        </Space>
      </div>
    );
  };

  /** 对比表 */
  const diffRows = useMemo(() => {
    if (!fileA || !fileB || !dataA || !dataB) return [];
    return [
      { key: 'type', label: '文件类型', a: fileTypeLabel[fileA.type], b: fileTypeLabel[fileB.type] },
      { key: 'size', label: '大小', a: formatFileSize(fileA.size), b: formatFileSize(fileB.size) },
      { key: 'sensitivity', label: '敏感等级', a: fileA.sensitivity ?? '-', b: fileB.sensitivity ?? '-' },
      { key: 'status', label: '状态', a: fileA.status, b: fileB.status },
      { key: 'hash', label: 'MD5', a: fileA.hash.slice(0, 16) + '...', b: fileB.hash.slice(0, 16) + '...' },
      { key: 'yaraMatched', label: 'YARA 命中数', a: dataA.yaraSummary.matched, b: dataB.yaraSummary.matched },
      { key: 'nerTotal', label: 'NER 实体数', a: dataA.nerSummary.total, b: dataB.nerSummary.total },
      { key: 'riskScore', label: '风险评分', a: dataA.risk.overall, b: dataB.risk.overall },
    ];
  }, [fileA, fileB, dataA, dataB]);

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/files')}>返回</Button>
          <Title level={4} style={{ margin: 0 }}>文件对比</Title>
        </Space>
        <Space>
          <Text type="secondary">选择两个文件进行多维度对比</Text>
          <Button
            type="primary"
            icon={<SwapOutlined />}
            onClick={() => {
              const tmp = fileAId;
              setFileAId(fileBId);
              setFileBId(tmp);
              message.success('已交换');
            }}
          >
            交换
          </Button>
        </Space>
      </div>

      {/* 文件选择 */}
      <Card size="small" style={{ marginBottom: spacing[4] }}>
        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Space style={{ width: '100%' }}>
              <Tag color="blue">A</Tag>
              <Select
                placeholder="选择文件 A"
                style={{ flex: 1, minWidth: 300 }}
                showSearch
                options={fileOptions}
                value={fileAId}
                onChange={setFileAId}
              />
            </Space>
          </Col>
          <Col xs={24} sm={12}>
            <Space style={{ width: '100%' }}>
              <Tag color="purple">B</Tag>
              <Select
                placeholder="选择文件 B"
                style={{ flex: 1, minWidth: 300 }}
                showSearch
                options={fileOptions}
                value={fileBId}
                onChange={setFileBId}
              />
            </Space>
          </Col>
        </Row>
      </Card>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>
      ) : (
        <>
          {/* 双栏对比 */}
          <Row gutter={16}>
            <Col xs={24} lg={12}>
              <Card
                size="small"
                title={<Space><FileTextOutlined /> 文件 A</Space>}
                style={{ marginBottom: spacing[4] }}
              >
                {renderSide(fileA, dataA, 'A')}
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card
                size="small"
                title={<Space><FileTextOutlined /> 文件 B</Space>}
                style={{ marginBottom: spacing[4] }}
              >
                {renderSide(fileB, dataB, 'B')}
              </Card>
            </Col>
          </Row>

          {/* 差异对比表 */}
          <Card size="small" title={<Space><SwapOutlined /> 差异对比</Space>}>
            {diffRows.length === 0 ? (
              <Empty description="请选择两个文件" />
            ) : (
              <Table
                size="small"
                rowKey="key"
                pagination={false}
                dataSource={diffRows}
                columns={[
                  { title: '维度', dataIndex: 'label', width: 150 },
                  {
                    title: '文件 A',
                    dataIndex: 'a',
                    render: (v: unknown) => <code>{String(v)}</code>,
                  },
                  {
                    title: '差异',
                    key: 'diff',
                    width: 100,
                    render: (_, record) => diffTag(getDiffStatus(record.a, record.b)),
                  },
                  {
                    title: '文件 B',
                    dataIndex: 'b',
                    render: (v: unknown) => <code>{String(v)}</code>,
                  },
                ]}
              />
            )}
          </Card>

          {/* 关联威胁情报对比 */}
          <Row gutter={16} style={{ marginTop: spacing[4] }}>
            <Col xs={24} lg={12}>
              <Card size="small" title={<Space><BugOutlined /> A 关联 IOC</Space>}>
                <Table
                  size="small"
                  rowKey="id"
                  pagination={false}
                  dataSource={mockThreatIntelItems.slice(0, 3)}
                  columns={[
                    { title: '类型', dataIndex: 'type', width: 80, render: (v: string) => <Tag color="red">{v}</Tag> },
                    { title: '值', dataIndex: 'value', ellipsis: true, render: (v: string) => <code>{v}</code> },
                    { title: '置信度', dataIndex: 'confidence', width: 100, render: (v: number) => `${(v * 100).toFixed(0)}%` },
                  ]}
                />
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card size="small" title={<Space><BugOutlined /> B 关联 IOC</Space>}>
                <Table
                  size="small"
                  rowKey="id"
                  pagination={false}
                  dataSource={mockThreatIntelItems.slice(3, 6)}
                  columns={[
                    { title: '类型', dataIndex: 'type', width: 80, render: (v: string) => <Tag color="red">{v}</Tag> },
                    { title: '值', dataIndex: 'value', ellipsis: true, render: (v: string) => <code>{v}</code> },
                    { title: '置信度', dataIndex: 'confidence', width: 100, render: (v: number) => `${(v * 100).toFixed(0)}%` },
                  ]}
                />
              </Card>
            </Col>
          </Row>

          {/* 风险对比摘要 */}
          {dataA && dataB && (
            <Card size="small" title={<Space><WarningOutlined /> 风险对比摘要</Space>} style={{ marginTop: spacing[4] }}>
              <Row gutter={16}>
                <Col span={6}>
                  <Statistic
                    title="A 风险评分"
                    value={dataA.risk.overall}
                    valueStyle={{ color: dataA.risk.overall >= 70 ? colors.error : dataA.risk.overall >= 50 ? colors.warning : colors.success }}
                  />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="B 风险评分"
                    value={dataB.risk.overall}
                    valueStyle={{ color: dataB.risk.overall >= 70 ? colors.error : dataB.risk.overall >= 50 ? colors.warning : colors.success }}
                  />
                </Col>
                <Col span={6}>
                  <Statistic title="评分差" value={Math.abs(dataA.risk.overall - dataB.risk.overall)} />
                </Col>
                <Col span={6}>
                  <Statistic
                    title="综合判定"
                    value={dataA.risk.overall > dataB.risk.overall ? 'A 风险更高' : dataA.risk.overall < dataB.risk.overall ? 'B 风险更高' : '风险相当'}
                    valueStyle={{ fontSize: 16 }}
                  />
                </Col>
              </Row>
            </Card>
          )}
        </>
      )}
    </div>
  );
};

export default FileComparePage;
