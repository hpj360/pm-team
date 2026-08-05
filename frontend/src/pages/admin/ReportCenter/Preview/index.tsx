/**
 * 报告预览页
 * - 顶部：报告基本信息 + 状态 + 操作
 * - HTML 预览（iframe 渲染）
 * - 报告元数据 / 关联资源
 */
import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Empty,
  Spin,
  Row,
  Col,
  Statistic,
  Descriptions,
  Tabs,
  List,
  message,
  Input,
  Alert,
  Skeleton,
} from 'antd';
import {
  ArrowLeftOutlined,
  FileTextOutlined,
  FileDoneOutlined,
  DownloadOutlined,
  ShareAltOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  EyeOutlined,
  FilePdfOutlined,
  FileMarkdownOutlined,
  GlobalOutlined,
  ClockCircleOutlined,
  TeamOutlined,
  BugOutlined,
  AimOutlined,
  RobotOutlined,
  CheckOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { getReportById, getReportTemplateById, mockReports } from '@/mock/adminReport';
import { generateReportDraft } from '@/services/ai';
import type { ReportItem, ReportDraft } from '@/types';
import { ReportTypeLabel, ReportStatusLabel, ReportFormatLabel } from '@/types';
import { formatDateTime, formatFileSize } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

/** 报告状态颜色 */
const statusColor: Record<ReportItem['status'], string> = {
  draft: 'default',
  generating: 'processing',
  completed: 'success',
  failed: 'error',
  archived: 'default',
};

/** 报告格式图标 */
const formatIcon: Record<ReportItem['format'], React.ReactNode> = {
  pdf: <FilePdfOutlined style={{ color: colors.error }} />,
  html: <GlobalOutlined style={{ color: colors.info }} />,
  markdown: <FileMarkdownOutlined style={{ color: colors.warning }} />,
};

const ReportPreviewPage: React.FC = () => {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [item, setItem] = useState<ReportItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('preview');

  // AI 草稿相关状态
  // ReportItem 无 conclusion 字段，这里用本地 state 维护可编辑的结论，
  // 「一键采纳」将草稿内容填入 reportConclusion；draftText 为编辑区内容
  const [draftText, setDraftText] = useState<string>('');
  const [draftLoading, setDraftLoading] = useState(false);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [draftRecommendations, setDraftRecommendations] = useState<string[]>([]);
  const [draftMeta, setDraftMeta] = useState<Pick<ReportDraft, 'reportId' | 'createdAt'> | null>(null);
  const [reportConclusion, setReportConclusion] = useState<string>('');
  const [adopted, setAdopted] = useState(false);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      const data = getReportById(id) ?? null;
      setItem(data);
      // 初始化报告结论为已有摘要（ReportItem 无独立 conclusion 字段）
      const initialConclusion = data?.summary ?? '';
      setReportConclusion(initialConclusion);
      setDraftText(initialConclusion);
      setLoading(false);
    }, 200);
  }, [id]);

  /** 关联模板 */
  const template = useMemo(
    () => (item ? getReportTemplateById(item.templateId) : null),
    [item],
  );

  /**
   * 生成 AI 报告草稿
   * 调用 /api/ai/report-draft/generate，失败时由服务层降级返回 Mock 数据
   */
  const handleGenerateDraft = async () => {
    if (!id) return;
    setDraftLoading(true);
    setDraftError(null);
    try {
      // 携带当前报告的统计信息与文件列表，便于 AI 生成更精准的草稿
      const statsJson = JSON.stringify({
        type: item?.type,
        status: item?.status,
        fileCount: item?.fileIds?.length ?? 0,
        tagCount: item?.tags?.length ?? 0,
      });
      const fileListJson = JSON.stringify(item?.fileNames ?? []);
      const tagDistributionJson = JSON.stringify(item?.tags ?? []);
      const res = await generateReportDraft({
        reportId: id,
        statsJson,
        fileListJson,
        tagDistributionJson,
      });
      if (res.code === 200 || res.code === 0) {
        const draft: ReportDraft = res.data;
        setDraftText(draft.conclusion);
        setDraftRecommendations(draft.recommendations ?? []);
        setDraftMeta({ reportId: draft.reportId, createdAt: draft.createdAt });
        setAdopted(false);
        message.success('AI 草稿已生成');
      } else {
        setDraftError(res.message || 'AI 草稿生成失败');
        message.error(res.message || 'AI 草稿生成失败');
      }
    } catch {
      setDraftError('AI 草稿生成异常，请稍后重试');
      message.error('AI 草稿生成异常，请稍后重试');
    } finally {
      setDraftLoading(false);
    }
  };

  /** 一键采纳：将草稿内容填入报告结论文本 */
  const handleAdoptDraft = () => {
    if (!draftText.trim()) {
      message.warning('草稿内容为空，无法采纳');
      return;
    }
    setReportConclusion(draftText);
    setAdopted(true);
    message.success('已采纳为报告结论');
  };

  /** 同类型其他报告 */
  const relatedReports = useMemo(
    () => mockReports.filter((r) => r.type === item?.type && r.id !== item?.id).slice(0, 5),
    [item],
  );

  /** 报告类型分布图（按类型统计） */
  const typeChartOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { top: 0, left: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        data: (() => {
          const map = new Map<string, number>();
          mockReports.forEach((r) => {
            map.set(ReportTypeLabel[r.type], (map.get(ReportTypeLabel[r.type]) ?? 0) + 1);
          });
          return Array.from(map.entries()).map(([name, value]) => ({ name, value }));
        })(),
      },
    ],
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载报告..." /></div>;
  }

  if (!item) {
    return (
      <div style={{ padding: 40 }}>
        <Empty description="未找到报告">
          <Button type="primary" onClick={() => navigate('/admin/reports')}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  /** 构造预览 HTML */
  const previewHtml = item.htmlContent ?? `
    <section style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; padding: 24px;">
      <h1 style="color: #f5222d; border-bottom: 2px solid #f5222d; padding-bottom: 8px;">${item.title}</h1>
      <p style="color: #595959;">报告类型：${ReportTypeLabel[item.type]} | 状态：${ReportStatusLabel[item.status]} | 创建者：${item.creator}</p>
      <p style="color: #595959;">摘要：${item.summary ?? '暂无摘要'}</p>
      <h2>报告内容</h2>
      <p>该报告暂未生成 HTML 预览，请下载原文件查看完整内容。</p>
    </section>
  `;

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/reports')}>返回</Button>
          <FileTextOutlined style={{ fontSize: 24, color: colors.info }} />
          <Title level={4} style={{ margin: 0 }}>报告预览</Title>
          <Tag color={statusColor[item.status]}>{ReportStatusLabel[item.status]}</Tag>
          <Tag color="blue">{ReportTypeLabel[item.type]}</Tag>
          <Tag icon={formatIcon[item.format]}>{ReportFormatLabel[item.format]}</Tag>
        </Space>
        <Space>
          <Button icon={<ShareAltOutlined />} onClick={() => message.success('分享链接已复制')}>分享</Button>
          <Button icon={<EditOutlined />} onClick={() => message.success('编辑报告...')}>编辑</Button>
          <Button
            icon={<DownloadOutlined />}
            disabled={item.status !== 'completed'}
            onClick={() => message.success('下载中...')}
          >
            下载
          </Button>
          <Button
            type="primary"
            icon={item.status === 'generating' ? <ReloadOutlined /> : <FileDoneOutlined />}
            disabled={item.status === 'generating'}
            onClick={() => message.success('生成中...')}
          >
            {item.status === 'generating' ? '生成中' : item.status === 'completed' ? '重新生成' : '生成报告'}
          </Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="创建者" value={item.creator} valueStyle={{ fontSize: 16 }} prefix={<TeamOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="文件大小" value={item.fileSize ? formatFileSize(item.fileSize) : '-'} prefix={<FileTextOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="关联目标" value={item.targetName ?? '-'} valueStyle={{ fontSize: 14 }} prefix={<AimOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="生成时间" value={item.generatedAt ? formatDateTime(item.generatedAt) : '-'} valueStyle={{ fontSize: 14 }} prefix={<ClockCircleOutlined />} /></Card>
        </Col>
      </Row>

      {/* AI 草稿（ai-service 生成，失败降级） */}
      <Card
        size="small"
        style={{ marginBottom: spacing[4] }}
        title={
          <Space>
            <RobotOutlined style={{ color: '#722ed1' }} />
            <span>AI 草稿</span>
            <Tag color="purple">ai-service</Tag>
          </Space>
        }
        extra={
          <Text type="secondary" style={{ fontSize: 12 }}>
            Markdown 编辑器 · 由 AI 生成结论与建议
          </Text>
        }
      >
        <Space style={{ marginBottom: 12 }} wrap>
          <Button
            type="primary"
            icon={<RobotOutlined />}
            loading={draftLoading}
            onClick={handleGenerateDraft}
            data-testid="ai-generate-draft-btn"
          >
            生成草稿
          </Button>
          <Button
            icon={<CheckOutlined />}
            onClick={handleAdoptDraft}
            disabled={!draftText.trim()}
            data-testid="ai-adopt-draft-btn"
          >
            一键采纳
          </Button>
          {draftMeta && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              生成时间：{formatDateTime(draftMeta.createdAt)}
            </Text>
          )}
        </Space>

        {draftLoading ? (
          <Skeleton active paragraph={{ rows: 6 }} data-testid="ai-draft-loading" />
        ) : draftError ? (
          <Alert
            type="warning"
            message="AI 草稿生成失败"
            description={draftError}
            showIcon
            action={
              <Button size="small" onClick={handleGenerateDraft}>
                重试
              </Button>
            }
          />
        ) : (
          <>
            <TextArea
              value={draftText}
              onChange={(e) => {
                setDraftText(e.target.value);
                setAdopted(false);
              }}
              rows={8}
              placeholder="点击「生成草稿」由 AI 生成报告结论（Markdown 格式），可手动编辑后一键采纳"
              data-testid="ai-draft-textarea"
            />
            {draftRecommendations.length > 0 && (
              <div style={{ marginTop: 12 }}>
                <Text strong>建议措施：</Text>
                <List
                  size="small"
                  dataSource={draftRecommendations}
                  renderItem={(rec, idx) => (
                    <List.Item>
                      <Space align="start">
                        <Tag color="blue">{idx + 1}</Tag>
                        <Text>{rec}</Text>
                      </Space>
                    </List.Item>
                  )}
                />
              </div>
            )}
            {adopted && (
              <Alert
                style={{ marginTop: 12 }}
                type="success"
                message="已采纳为报告结论"
                description="以下为当前报告结论内容（仅本会话生效）："
                showIcon
              />
            )}
            {adopted && reportConclusion && (
              <Card
                size="small"
                type="inner"
                style={{ marginTop: 8 }}
                title="报告结论（已采纳）"
              >
                <Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
                  {reportConclusion}
                </Paragraph>
              </Card>
            )}
          </>
        )}
      </Card>

      {/* Tabs：预览 / 详情 / 关联资源 / 统计 */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'preview',
            label: <span><EyeOutlined /> HTML 预览</span>,
            children: (
              <Card size="small" title={<Space><EyeOutlined /> 报告预览（HTML 渲染）</Space>}>
                {item.status === 'completed' ? (
                  <div
                    style={{
                      border: `1px solid ${colors.neutral[200]}`,
                      borderRadius: 6,
                      padding: 0,
                      overflow: 'auto',
                      maxHeight: 720,
                    }}
                    dangerouslySetInnerHTML={{ __html: previewHtml }}
                  />
                ) : (
                  <Empty
                    description={
                      item.status === 'generating'
                        ? '报告生成中，请稍候...'
                        : item.status === 'failed'
                          ? '报告生成失败，请重试'
                          : '报告尚未生成，请先生成'
                    }
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  >
                    {item.status !== 'generating' && (
                      <Button type="primary" icon={<FileDoneOutlined />} onClick={() => message.success('开始生成...')}>生成报告</Button>
                    )}
                  </Empty>
                )}
              </Card>
            ),
          },
          {
            key: 'detail',
            label: <span><FileTextOutlined /> 报告详情</span>,
            children: (
              <Card size="small" title={<Space><FileTextOutlined /> 报告基本信息</Space>}>
                <ProDescriptions
                  column={2}
                  bordered
                  size="small"
                  dataSource={{
                    id: item.id,
                    title: item.title,
                    type: ReportTypeLabel[item.type],
                    status: ReportStatusLabel[item.status],
                    template: item.templateName,
                    target: item.targetName ?? '-',
                    creator: item.creator,
                    generatedAt: item.generatedAt ? formatDateTime(item.generatedAt) : '-',
                    fileSize: item.fileSize ? formatFileSize(item.fileSize) : '-',
                    format: ReportFormatLabel[item.format],
                    downloadUrl: item.downloadUrl ?? '-',
                    createTime: formatDateTime(item.createTime),
                    updateTime: formatDateTime(item.updateTime),
                  }}
                  columns={[
                    { title: '报告 ID', dataIndex: 'id', key: 'id' },
                    { title: '标题', dataIndex: 'title', key: 'title' },
                    { title: '类型', dataIndex: 'type', key: 'type', render: (v: React.ReactNode) => <Tag color="blue">{v}</Tag> },
                    { title: '状态', dataIndex: 'status', key: 'status', render: (v: React.ReactNode) => <Tag color={statusColor[item.status]}>{v}</Tag> },
                    { title: '模板', dataIndex: 'template', key: 'template' },
                    { title: '目标', dataIndex: 'target', key: 'target', render: (v: React.ReactNode) => String(v) !== '-' ? <Space><AimOutlined />{v}</Space> : '-' },
                    { title: '创建者', dataIndex: 'creator', key: 'creator', render: (v: React.ReactNode) => <Space><TeamOutlined />{v}</Space> },
                    { title: '生成时间', dataIndex: 'generatedAt', key: 'generatedAt', render: (v: React.ReactNode) => <Space><ClockCircleOutlined />{v}</Space> },
                    { title: '文件大小', dataIndex: 'fileSize', key: 'fileSize' },
                    { title: '导出格式', dataIndex: 'format', key: 'format', render: (v: React.ReactNode) => <Tag>{v}</Tag> },
                    { title: '下载链接', dataIndex: 'downloadUrl', key: 'downloadUrl', render: (v: React.ReactNode) => String(v) !== '-' ? <code>{v}</code> : '-' },
                    { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
                    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
                  ]}
                />
                {item.summary && (
                  <div style={{ marginTop: 16 }}>
                    <Title level={5}>报告摘要</Title>
                    <Paragraph>{item.summary}</Paragraph>
                  </div>
                )}
                {item.tags && item.tags.length > 0 && (
                  <div style={{ marginTop: 16 }}>
                    <Text strong>标签：</Text>
                    <Space wrap style={{ marginLeft: 8 }}>
                      {item.tags.map((t) => <Tag key={t} color="blue">{t}</Tag>)}
                    </Space>
                  </div>
                )}
              </Card>
            ),
          },
          {
            key: 'template',
            label: <span><FileDoneOutlined /> 模板信息</span>,
            children: (
              <Card size="small" title={<Space><FileDoneOutlined /> 报告模板</Space>}>
                {template ? (
                  <div>
                    <ProDescriptions
                      column={2}
                      bordered
                      size="small"
                      dataSource={{
                        id: template.id,
                        name: template.name,
                        type: ReportTypeLabel[template.type],
                        description: template.description,
                        defaultFormat: ReportFormatLabel[template.defaultFormat],
                        builtin: template.builtin ? '内置' : '自定义',
                        updateTime: formatDateTime(template.updateTime),
                        fields: template.fields.map((f) => f.label).join(' / '),
                      }}
                      columns={[
                        { title: '模板 ID', dataIndex: 'id', key: 'id' },
                        { title: '名称', dataIndex: 'name', key: 'name' },
                        { title: '类型', dataIndex: 'type', key: 'type', render: (v: React.ReactNode) => <Tag color="blue">{v}</Tag> },
                        { title: '默认格式', dataIndex: 'defaultFormat', key: 'defaultFormat', render: (v: React.ReactNode) => <Tag>{v}</Tag> },
                        { title: '是否内置', dataIndex: 'builtin', key: 'builtin' },
                        { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
                        { title: '描述', dataIndex: 'description', key: 'description', span: 2 },
                        { title: '字段', dataIndex: 'fields', key: 'fields', span: 2 },
                      ]}
                    />
                    <div style={{ marginTop: 16 }}>
                      <Title level={5}>模板字段</Title>
                      <List
                        size="small"
                        dataSource={template.fields}
                        renderItem={(field, idx) => (
                          <List.Item>
                            <Space>
                              <Tag color={field.required ? 'red' : 'default'}>{field.required ? '必填' : '可选'}</Tag>
                              <Text strong>{field.label}</Text>
                              <Text type="secondary">字段 {idx + 1}：{field.key}</Text>
                            </Space>
                          </List.Item>
                        )}
                      />
                    </div>
                  </div>
                ) : (
                  <Empty description="未找到模板信息" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
              </Card>
            ),
          },
          {
            key: 'related',
            label: <span><BugOutlined /> 关联资源</span>,
            children: (
              <Row gutter={16}>
                <Col xs={24} lg={12}>
                  <Card size="small" title={<Space><FileTextOutlined /> 关联文件 ({item.fileNames?.length ?? 0})</Space>} style={{ marginBottom: spacing[4] }}>
                    {item.fileIds && item.fileIds.length > 0 ? (
                      <List
                        size="small"
                        dataSource={item.fileIds.map((fid, idx) => ({ id: fid, name: item.fileNames?.[idx] ?? fid }))}
                        renderItem={(f) => (
                          <List.Item>
                            <Space>
                              <FileTextOutlined />
                              <a onClick={() => navigate(`/files/${f.id}`)}><Text strong>{f.name}</Text></a>
                            </Space>
                            <Tag>{f.id}</Tag>
                          </List.Item>
                        )}
                      />
                    ) : (
                      <Empty description="无关联文件" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    )}
                  </Card>
                </Col>
                <Col xs={24} lg={12}>
                  <Card size="small" title={<Space><FileDoneOutlined /> 同类型报告 ({relatedReports.length})</Space>}>
                    {relatedReports.length === 0 ? (
                      <Empty description="无同类型报告" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                      <List
                        size="small"
                        dataSource={relatedReports}
                        renderItem={(r) => (
                          <List.Item>
                            <Space>
                              {formatIcon[r.format]}
                              <a onClick={() => navigate(`/admin/reports/${r.id}`)}><Text strong>{r.title}</Text></a>
                            </Space>
                            <Tag color={statusColor[r.status]}>{ReportStatusLabel[r.status]}</Tag>
                          </List.Item>
                        )}
                      />
                    )}
                  </Card>
                </Col>
              </Row>
            ),
          },
          {
            key: 'stats',
            label: <span><FileTextOutlined /> 统计分析</span>,
            children: (
              <Card size="small" title={<Space><FileTextOutlined /> 报告类型分布</Space>}>
                <ReactECharts option={typeChartOption} style={{ height: 320, width: '100%' }} notMerge lazyUpdate />
                <div style={{ marginTop: spacing[4] }}>
                  <Descriptions column={2} size="small" bordered>
                    <Descriptions.Item label="模板字段数">{template?.fields.length ?? 0}</Descriptions.Item>
                    <Descriptions.Item label="关联文件数">{item.fileIds?.length ?? 0}</Descriptions.Item>
                    <Descriptions.Item label="标签数">{item.tags?.length ?? 0}</Descriptions.Item>
                    <Descriptions.Item label="同类型报告数">{relatedReports.length}</Descriptions.Item>
                  </Descriptions>
                </div>
              </Card>
            ),
          },
        ]}
      />

      {/* 危险操作 */}
      <Card size="small" style={{ marginTop: spacing[4], borderColor: colors.neutral[200] }}>
        <Space>
          <Text type="secondary">危险操作：</Text>
          <Button danger icon={<DeleteOutlined />} onClick={() => message.success('删除报告...')}>删除报告</Button>
        </Space>
      </Card>
    </div>
  );
};

export default ReportPreviewPage;
