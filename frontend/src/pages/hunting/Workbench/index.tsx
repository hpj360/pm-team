/**
 * 威胁狩猎工作台（V5.3）
 * - ATT&CK 矩阵浏览（按战术筛选技术）
 * - 狩猎假设管理（创建 / 列表 / 验证 / 详情）
 * - 假设验证结果展示（命中清单、置信度、推荐 IOC）
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Table,
  Tag,
  Space,
  Button,
  Input,
  Select,
  Modal,
  Form,
  Descriptions,
  Statistic,
  Empty,
  Spin,
  message,
  Typography,
  Progress,
  List,
  Drawer,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  PlusOutlined,
  ThunderboltOutlined,
  AimOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExperimentOutlined,
  RadarChartOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import {
  getAttackMatrix,
  getTechniquesByTactic,
  searchAttackTechniques,
  createHypothesis,
  listHypotheses,
  validateHypothesis,
  findRulesByTechnique,
} from '@/services/hunting';
import type {
  AttackMatrix,
  AttackTechnique,
  HypothesisDetail,
  HuntingRule,
  CreateHypothesisPayload,
} from '@/types';
import {
  HypothesisStatus,
  HypothesisStatusLabel,
  HypothesisStatusColor,
} from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text } = Typography;

const HuntingWorkbench: React.FC = () => {
  // ATT&CK 矩阵
  const [matrix, setMatrix] = useState<AttackMatrix | null>(null);
  const [matrixLoading, setMatrixLoading] = useState(false);
  const [selectedTactic, setSelectedTactic] = useState<string | undefined>(undefined);
  const [techniques, setTechniques] = useState<AttackTechnique[]>([]);
  const [keyword, setKeyword] = useState('');
  const [searching, setSearching] = useState(false);

  // 狩猎假设
  const [hypotheses, setHypotheses] = useState<HypothesisDetail[]>([]);
  const [hypoLoading, setHypoLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [validatingId, setValidatingId] = useState<string | null>(null);
  const [detail, setDetail] = useState<HypothesisDetail | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [relatedRules, setRelatedRules] = useState<HuntingRule[]>([]);
  const [rulesLoading, setRulesLoading] = useState(false);
  const [form] = Form.useForm<CreateHypothesisPayload>();

  /** 加载 ATT&CK 矩阵 */
  useEffect(() => {
    setMatrixLoading(true);
    getAttackMatrix()
      .then((res) => {
        if (res.code === 200 || res.code === 0) {
          setMatrix(res.data);
          setTechniques(res.data.techniques);
        }
      })
      .finally(() => setMatrixLoading(false));
  }, []);

  /** 加载狩猎假设列表 */
  const loadHypotheses = () => {
    setHypoLoading(true);
    listHypotheses()
      .then((res) => {
        if (res.code === 200 || res.code === 0) {
          setHypotheses(res.data);
        }
      })
      .finally(() => setHypoLoading(false));
  };

  useEffect(() => {
    loadHypotheses();
  }, []);

  /** 按战术筛选技术 */
  const handleTacticChange = (tactic: string | undefined) => {
    setSelectedTactic(tactic);
    setKeyword('');
    if (!tactic) {
      setTechniques(matrix?.techniques ?? []);
      return;
    }
    getTechniquesByTactic(tactic).then((res) => {
      if (res.code === 200 || res.code === 0) {
        setTechniques(res.data);
      }
    });
  };

  /** 关键词搜索技术 */
  const handleSearch = (value: string) => {
    setKeyword(value);
    if (!value.trim()) {
      handleTacticChange(selectedTactic);
      return;
    }
    setSearching(true);
    searchAttackTechniques(value)
      .then((res) => {
        if (res.code === 200 || res.code === 0) {
          setTechniques(res.data);
        }
      })
      .finally(() => setSearching(false));
  };

  /** 创建假设 */
  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      const res = await createHypothesis(values);
      if (res.code === 200 || res.code === 0) {
        message.success(`狩猎假设已创建：${res.data}`);
        setCreateOpen(false);
        form.resetFields();
        loadHypotheses();
      } else {
        message.error(res.message || '创建假设失败');
      }
    } catch {
      // 校验失败或创建失败
    }
  };

  /** 触发假设验证 */
  const handleValidate = async (id: string) => {
    setValidatingId(id);
    try {
      const res = await validateHypothesis(id);
      if (res.code === 200 || res.code === 0) {
        message.success(
          res.data.status === HypothesisStatus.CONFIRMED
            ? `假设已确认：置信度 ${((res.data.confidence ?? 0) * 100).toFixed(0)}%`
            : '假设已否定：未发现命中',
        );
        loadHypotheses();
      } else {
        message.error(res.message || '验证失败');
      }
    } catch {
      message.error('验证失败');
    } finally {
      setValidatingId(null);
    }
  };

  /** 查看假设详情 + 加载关联规则 */
  const openDetail = (h: HypothesisDetail) => {
    setDetail(h);
    setDrawerOpen(true);
    setRulesLoading(true);
    setRelatedRules([]);
    findRulesByTechnique(h.techniqueId)
      .then((res) => {
        if (res.code === 200 || res.code === 0) {
          setRelatedRules(res.data);
        }
      })
      .finally(() => setRulesLoading(false));
  };

  /** 假设统计 */
  const stats = useMemo(() => {
    const confirmed = hypotheses.filter((h) => h.status === HypothesisStatus.CONFIRMED).length;
    const refuted = hypotheses.filter((h) => h.status === HypothesisStatus.REFUTED).length;
    const draft = hypotheses.filter((h) => h.status === HypothesisStatus.DRAFT).length;
    return { total: hypotheses.length, confirmed, refuted, draft };
  }, [hypotheses]);

  /** 技术列表列 */
  const techniqueColumns: ColumnsType<AttackTechnique> = [
    {
      title: '技术 ID',
      dataIndex: 'techniqueId',
      width: 120,
      render: (v: string, r) => (
        <Tag color={r.subTechnique ? 'volcano' : 'red'}>{v}</Tag>
      ),
    },
    { title: '技术名称', dataIndex: 'name', ellipsis: true },
    {
      title: '战术',
      dataIndex: 'tacticName',
      width: 110,
      render: (v: string, r) => <Tag color="geekblue">{v ?? r.tactic}</Tag>,
    },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '数据源',
      dataIndex: 'dataSource',
      width: 180,
      ellipsis: true,
      render: (v: string) => (v ? <Text type="secondary" style={{ fontSize: 12 }}>{v}</Text> : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, r) => (
        <Button
          type="link"
          size="small"
          icon={<PlusOutlined />}
          onClick={() => {
            form.setFieldsValue({ techniqueId: r.techniqueId });
            setCreateOpen(true);
          }}
        >
          建假设
        </Button>
      ),
    },
  ];

  /** 假设列表列 */
  const hypothesisColumns: ColumnsType<HypothesisDetail> = [
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => (
        <Tag color={HypothesisStatusColor[v as HypothesisStatus] ?? 'default'}>
          {HypothesisStatusLabel[v as HypothesisStatus] ?? v}
        </Tag>
      ),
    },
    {
      title: '假设描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (v: string, r) => (
        <a onClick={() => openDetail(r)}>{v}</a>
      ),
    },
    {
      title: 'ATT&CK',
      dataIndex: 'techniqueId',
      width: 120,
      render: (v: string, r) => (
        <Space direction="vertical" size={0}>
          <Tag color="red">{v}</Tag>
          {r.techniqueName && (
            <Text type="secondary" style={{ fontSize: 11 }}>{r.techniqueName}</Text>
          )}
        </Space>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 140,
      sorter: (a, b) => (a.confidence ?? 0) - (b.confidence ?? 0),
      render: (v: number) =>
        v !== undefined && v > 0 ? (
          <Progress
            percent={Math.round(v * 100)}
            size="small"
            status={v >= 0.7 ? 'exception' : v >= 0.4 ? 'active' : 'normal'}
          />
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '命中数',
      dataIndex: 'hits',
      width: 80,
      render: (hits: HypothesisDetail['hits']) => hits?.length ?? 0,
    },
    {
      title: '创建人',
      dataIndex: 'userName',
      width: 100,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      render: (t: string) => formatDateTime(t),
    },
    {
      title: '操作',
      key: 'action',
      width: 160,
      render: (_, r) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<ThunderboltOutlined />}
            loading={validatingId === r.id}
            disabled={r.status === HypothesisStatus.VALIDATING}
            onClick={() => handleValidate(r.id)}
          >
            验证
          </Button>
          <Button type="link" size="small" onClick={() => openDetail(r)}>
            详情
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: spacing[4] }} data-testid="hunting-workbench">
      <Title level={4}>
        <RadarChartOutlined style={{ marginRight: 8, color: colors.primary[500] }} />
        威胁狩猎工作台
      </Title>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="假设总数" value={stats.total} prefix={<BulbOutlined />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="已确认"
              value={stats.confirmed}
              valueStyle={{ color: colors.success }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="已否定"
              value={stats.refuted}
              valueStyle={{ color: colors.error }}
              prefix={<CloseCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="待验证"
              value={stats.draft}
              valueStyle={{ color: colors.warning }}
              prefix={<AimOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* ATT&CK 矩阵 */}
      <Card
        title={
          <Space>
            <ExperimentOutlined />
            <span>ATT&CK 矩阵</span>
            {matrix && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {matrix.tacticCount} 战术 / {matrix.techniqueCount} 技术
              </Text>
            )}
          </Space>
        }
        extra={
          <Space>
            <Select
              allowClear
              placeholder="按战术筛选"
              style={{ width: 180 }}
              value={selectedTactic}
              onChange={handleTacticChange}
              options={matrix?.tactics.map((t) => ({ label: `${t.nameCn} (${t.id})`, value: t.id })) ?? []}
              data-testid="tactic-select"
            />
            <Input.Search
              allowClear
              placeholder="搜索技术 ID / 名称"
              style={{ width: 240 }}
              value={keyword}
              onChange={(e) => handleSearch(e.target.value)}
              onSearch={handleSearch}
              loading={searching}
              prefix={<SearchOutlined />}
              data-testid="technique-search"
            />
          </Space>
        }
        style={{ marginBottom: spacing[4] }}
      >
        {matrixLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}><Spin tip="加载 ATT&CK 矩阵..." /></div>
        ) : (
          <Table
            size="small"
            rowKey="techniqueId"
            columns={techniqueColumns}
            dataSource={techniques}
            pagination={{ pageSize: 10, showSizeChanger: true }}
            data-testid="technique-table"
          />
        )}
      </Card>

      {/* 狩猎假设列表 */}
      <Card
        title={
          <Space>
            <BulbOutlined />
            <span>狩猎假设</span>
          </Space>
        }
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields();
              setCreateOpen(true);
            }}
            data-testid="create-hypothesis-btn"
          >
            新建假设
          </Button>
        }
      >
        <Table
          size="small"
          rowKey="id"
          columns={hypothesisColumns}
          dataSource={hypotheses}
          loading={hypoLoading}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          data-testid="hypothesis-table"
        />
      </Card>

      {/* 创建假设弹窗 */}
      <Modal
        title="新建狩猎假设"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        okText="创建"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="description"
            label="假设描述"
            rules={[{ required: true, message: '请输入假设描述' }]}
          >
            <Input.TextArea
              rows={3}
              placeholder="描述预期的攻击行为，例如：检测 PowerShell 编码命令执行"
            />
          </Form.Item>
          <Form.Item
            name="techniqueId"
            label="关联 ATT&CK 技术"
            rules={[{ required: true, message: '请选择或输入技术 ID' }]}
          >
            <Select
              showSearch
              placeholder="搜索并选择技术"
              optionFilterProp="label"
              options={matrix?.techniques.map((t) => ({
                label: `${t.techniqueId} - ${t.name} (${t.tacticName})`,
                value: t.techniqueId,
              })) ?? []}
            />
          </Form.Item>
          <Form.Item name="userId" label="创建人ID" initialValue={1}>
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 假设详情抽屉 */}
      <Drawer
        title="狩猎假设详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={640}
      >
        {detail && (
          <div>
            <Descriptions column={1} bordered size="small" style={{ marginBottom: spacing[3] }}>
              <Descriptions.Item label="状态">
                <Tag color={HypothesisStatusColor[detail.status as HypothesisStatus] ?? 'default'}>
                  {HypothesisStatusLabel[detail.status as HypothesisStatus] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="假设描述">{detail.description}</Descriptions.Item>
              <Descriptions.Item label="ATT&CK 技术">
                <Space>
                  <Tag color="red">{detail.techniqueId}</Tag>
                  <Text>{detail.techniqueName ?? '-'}</Text>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="战术">
                <Tag color="geekblue">{detail.tacticName ?? detail.tactic ?? '-'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="置信度">
                {detail.confidence !== undefined && detail.confidence > 0 ? (
                  <Progress
                    percent={Math.round(detail.confidence * 100)}
                    style={{ width: 200 }}
                    status={detail.confidence >= 0.7 ? 'exception' : 'active'}
                  />
                ) : (
                  <Text type="secondary">未验证</Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.userName ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(detail.createTime)}</Descriptions.Item>
              <Descriptions.Item label="验证时间">
                {detail.validatedTime ? formatDateTime(detail.validatedTime) : '-'}
              </Descriptions.Item>
            </Descriptions>

            {/* 命中清单 */}
            <Card size="small" title={<><CheckCircleOutlined /> 命中清单 ({detail.hits.length})</>} style={{ marginBottom: spacing[3] }}>
              {detail.hits.length > 0 ? (
                <List
                  size="small"
                  dataSource={detail.hits}
                  renderItem={(hit) => (
                    <List.Item>
                      <Space direction="vertical" size={2} style={{ width: '100%' }}>
                        <Space>
                          <Tag color="blue">{hit.entityType}</Tag>
                          <Text strong>{hit.entityName ?? hit.entityId}</Text>
                          <Tag color={hit.score >= 0.8 ? 'red' : 'orange'}>
                            评分 {(hit.score * 100).toFixed(0)}%
                          </Tag>
                        </Space>
                        <Text>{hit.description}</Text>
                        {hit.evidence && (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            证据：{hit.evidence}
                          </Text>
                        )}
                      </Space>
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="无命中项" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Card>

            {/* 推荐 IOC */}
            <Card size="small" title="推荐 IOC" style={{ marginBottom: spacing[3] }}>
              {detail.recommendedIocs.length > 0 ? (
                <Space wrap>
                  {detail.recommendedIocs.map((ioc) => (
                    <Tag key={ioc} color="magenta"><code>{ioc}</code></Tag>
                  ))}
                </Space>
              ) : (
                <Empty description="无推荐 IOC" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Card>

            {/* 关联规则 */}
            <Card
              size="small"
              title={`关联狩猎规则 (${relatedRules.length})`}
              loading={rulesLoading}
            >
              {relatedRules.length > 0 ? (
                <List
                  size="small"
                  dataSource={relatedRules}
                  renderItem={(rule) => (
                    <List.Item>
                      <Space>
                        <Tag color={rule.type === 'SIGMA' ? 'blue' : 'purple'}>{rule.type}</Tag>
                        <Text strong>{rule.name}</Text>
                        {rule.severity && <Tag color="orange">{rule.severity}</Tag>}
                      </Space>
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="无关联规则" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              )}
            </Card>

            {/* 操作 */}
            <Space style={{ marginTop: spacing[3] }}>
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                loading={validatingId === detail.id}
                onClick={() => handleValidate(detail.id)}
              >
                重新验证
              </Button>
            </Space>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default HuntingWorkbench;
