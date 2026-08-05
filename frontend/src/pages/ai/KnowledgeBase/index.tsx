/**
 * 知识库管理页（V5.1）
 * - 文档列表 + 上传文档
 * - 检索测试（输入查询 → 显示匹配知识片段）
 */
import React, { useEffect, useState, useCallback } from 'react';
import {
  Card,
  Row,
  Col,
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  Tag,
  Space,
  Typography,
  Empty,
  List,
  message,
  Tooltip,
  Divider,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  BookOutlined,
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  DatabaseOutlined,
} from '@ant-design/icons';
import {
  getKnowledgeList,
  indexKnowledge,
  searchKnowledge,
} from '@/services/agent';
import type { Knowledge, KnowledgeSearchResult } from '@/types';

const { Title, Paragraph, Text } = Typography;
const { TextArea } = Input;

/** 知识来源颜色映射 */
const sourceColor: Record<string, string> = {
  'ATT&CK': 'blue',
  CVE: 'red',
  APT: 'purple',
  REPORT: 'orange',
};

const KnowledgeBase: React.FC = () => {
  const [knowledgeList, setKnowledgeList] = useState<Knowledge[]>([]);
  const [loading, setLoading] = useState(false);
  const [indexModalVisible, setIndexModalVisible] = useState(false);
  const [indexing, setIndexing] = useState(false);
  const [form] = Form.useForm();

  // 检索测试状态
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<KnowledgeSearchResult[]>([]);
  const [searching, setSearching] = useState(false);

  /** 加载知识库列表 */
  const loadKnowledge = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getKnowledgeList();
      if (res.code === 200 && res.data) {
        setKnowledgeList(res.data);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  /** 提交索引文档 */
  const handleIndex = async () => {
    try {
      const values = await form.validateFields();
      setIndexing(true);
      const res = await indexKnowledge({
        title: values.title,
        content: values.content,
        source: values.source,
        metadata: values.metadata ? { note: values.metadata } : undefined,
      });
      if (res.code === 200) {
        message.success('文档索引成功');
        setIndexModalVisible(false);
        form.resetFields();
        await loadKnowledge();
      } else {
        message.error(res.message || '索引失败');
      }
    } catch (err) {
      // 表单校验失败
    } finally {
      setIndexing(false);
    }
  };

  /** 检索测试 */
  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      message.warning('请输入检索查询');
      return;
    }
    setSearching(true);
    try {
      const res = await searchKnowledge(searchQuery.trim(), 5);
      if (res.code === 200 && res.data) {
        setSearchResults(res.data);
      }
    } finally {
      setSearching(false);
    }
  };

  useEffect(() => {
    loadKnowledge();
  }, [loadKnowledge]);

  /** 知识库表格列 */
  const columns: ColumnsType<Knowledge> = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true,
      render: (text: string | null) => text || '未命名',
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 120,
      render: (source: string | null) =>
        source ? (
          <Tag color={sourceColor[source] ?? 'default'}>{source}</Tag>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: '内容预览',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
      render: (text: string | null) =>
        text ? text.substring(0, 80) + (text.length > 80 ? '...' : '') : '-',
    },
    {
      title: '知识ID',
      dataIndex: 'knowledgeId',
      key: 'knowledgeId',
      width: 160,
      ellipsis: true,
      render: (text: string) => <Text code>{text.substring(0, 20)}</Text>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
    },
  ];

  return (
    <div>
      <Title level={4}>
        <BookOutlined /> 知识库管理
      </Title>

      <Row gutter={16}>
        {/* 左侧：知识库文档列表 */}
        <Col span={14}>
          <Card
            title={
              <Space>
                <DatabaseOutlined />
                <span>知识库文档</span>
                <Tag>{knowledgeList.length}</Tag>
              </Space>
            }
            extra={
              <Space>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={loadKnowledge}
                  loading={loading}
                  size="small"
                >
                  刷新
                </Button>
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setIndexModalVisible(true)}
                  size="small"
                >
                  索引文档
                </Button>
              </Space>
            }
          >
            <Table
              columns={columns}
              dataSource={knowledgeList}
              rowKey="knowledgeId"
              loading={loading}
              size="small"
              pagination={{ pageSize: 8, showSizeChanger: false }}
            />
          </Card>
        </Col>

        {/* 右侧：检索测试 */}
        <Col span={10}>
          <Card title={<Space><SearchOutlined /><span>检索测试</span></Space>}>
            <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
              <Input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="输入查询语句测试知识库语义检索"
                onPressEnter={handleSearch}
                prefix={<SearchOutlined />}
              />
              <Button
                type="primary"
                onClick={handleSearch}
                loading={searching}
              >
                检索
              </Button>
            </Space.Compact>

            {searchResults.length > 0 ? (
              <>
                <Divider style={{ margin: '8px 0' }} />
                <List
                  dataSource={searchResults}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space>
                            <Text strong>{item.title ?? '未命名'}</Text>
                            {item.source && (
                              <Tag color={sourceColor[item.source] ?? 'default'}>
                                {item.source}
                              </Tag>
                            )}
                            <Tooltip title="匹配分数">
                              <Tag color="green">
                                {(item.score * 100).toFixed(0)}%
                              </Tag>
                            </Tooltip>
                          </Space>
                        }
                        description={
                          <div>
                            <Paragraph
                              style={{ marginBottom: 4, color: '#595959' }}
                              ellipsis={{ rows: 3 }}
                            >
                              {item.content}
                            </Paragraph>
                            <Text code style={{ fontSize: 12 }}>
                              {item.knowledgeId}
                            </Text>
                          </div>
                        }
                      />
                    </List.Item>
                  )}
                />
              </>
            ) : (
              <Empty
                description={
                  searching ? '检索中...' : '输入查询语句后点击检索'
                }
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* 索引文档 Modal */}
      <Modal
        title="索引知识库文档"
        open={indexModalVisible}
        onOk={handleIndex}
        onCancel={() => setIndexModalVisible(false)}
        confirmLoading={indexing}
        width={640}
        okText="索引"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="文档标题"
            rules={[{ required: true, message: '请输入文档标题' }]}
          >
            <Input placeholder="如：ATT&CK T1059 - 命令与脚本解释器" />
          </Form.Item>
          <Form.Item
            name="source"
            label="来源"
            rules={[{ required: true, message: '请选择来源' }]}
          >
            <Select placeholder="选择知识来源">
              <Select.Option value="ATT&CK">ATT&CK 矩阵</Select.Option>
              <Select.Option value="CVE">CVE 漏洞库</Select.Option>
              <Select.Option value="APT">APT 组织档案</Select.Option>
              <Select.Option value="REPORT">历史分析报告</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="content"
            label="文档内容"
            rules={[{ required: true, message: '请输入文档内容' }]}
          >
            <TextArea
              rows={6}
              placeholder="输入知识库文档内容..."
              maxLength={10000}
              showCount
            />
          </Form.Item>
          <Form.Item name="metadata" label="元数据备注（可选）">
            <Input placeholder="如：T1059 / CVSS 9.8 / APT28" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default KnowledgeBase;
