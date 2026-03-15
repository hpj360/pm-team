/**
 * 文件检索页面
 */

import React, { useState } from 'react';
import {
  Card,
  Input,
  Button,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Empty,
  Spin,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  SearchOutlined,
  HistoryOutlined,
  ClearOutlined,
} from '@ant-design/icons';
import { useSearch } from '@/hooks';
import type { SearchResultItem } from '@/types';
import { SearchType } from '@/types';

const { Title, Text } = Typography;

const FileSearch: React.FC = () => {
  const {
    keyword,
    searchType,
    results,
    total,
    loading,
    history,
    search,
    changeSearchType,
    setKeyword,
    clearHistory,
    removeHistory,
  } = useSearch();

  const [inputKeyword, setInputKeyword] = useState(keyword);

  // 搜索类型选项
  const searchTypeOptions = [
    { value: SearchType.KEYWORD, label: '关键词搜索' },
    { value: SearchType.SEMANTIC, label: '语义搜索' },
    { value: SearchType.FUZZY, label: '模糊搜索' },
    { value: SearchType.REGEX, label: '正则搜索' },
  ];

  // 执行搜索
  const handleSearch = () => {
    setKeyword(inputKeyword);
    search({ keyword: inputKeyword, page: 1 });
  };

  // 表格列定义
  const columns: ColumnsType<SearchResultItem> = [
    {
      title: '文件名',
      dataIndex: 'fileName',
      key: 'fileName',
      ellipsis: true,
      width: 250,
      render: (text: string) => (
        <a href={`/files/${text}`}>{text}</a>
      ),
    },
    {
      title: '相关度',
      dataIndex: 'score',
      key: 'score',
      width: 100,
      render: (score: number) => (
        <Tag color={score > 0.8 ? 'green' : score > 0.5 ? 'blue' : 'default'}>
          {(score * 100).toFixed(0)}%
        </Tag>
      ),
    },
    {
      title: '内容片段',
      dataIndex: 'snippet',
      key: 'snippet',
      ellipsis: true,
      render: (text: string) => (
        <Text type="secondary" ellipsis={{ tooltip: text }}>
          {text}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small">
            查看
          </Button>
          <Button type="link" size="small">
            分析
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>文件检索</Title>

      <Card>
        {/* 搜索栏 */}
        <Space.Compact style={{ width: '100%', marginBottom: 16 }}>
          <Select
            value={searchType}
            onChange={changeSearchType}
            options={searchTypeOptions}
            style={{ width: 140 }}
          />
          <Input
            placeholder="输入关键词搜索文件..."
            value={inputKeyword}
            onChange={(e) => setInputKeyword(e.target.value)}
            onPressEnter={handleSearch}
            size="large"
          />
          <Button
            type="primary"
            size="large"
            icon={<SearchOutlined />}
            onClick={handleSearch}
            loading={loading}
          >
            搜索
          </Button>
        </Space.Compact>

        {/* 搜索历史 */}
        {history.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <Space>
              <HistoryOutlined />
              <Text type="secondary">搜索历史:</Text>
              {history.slice(0, 5).map((item) => (
                <Tag
                  key={item.id}
                  closable
                  onClose={() => removeHistory(item.id)}
                  style={{ cursor: 'pointer' }}
                  onClick={() => {
                    setInputKeyword(item.keyword);
                    changeSearchType(item.type);
                    search({ keyword: item.keyword, type: item.type, page: 1 });
                  }}
                >
                  {item.keyword}
                </Tag>
              ))}
              <Button
                type="link"
                size="small"
                icon={<ClearOutlined />}
                onClick={clearHistory}
              >
                清空
              </Button>
            </Space>
          </div>
        )}

        {/* 搜索结果 */}
        {loading ? (
          <div style={{ textAlign: 'center', padding: 50 }}>
            <Spin size="large" tip="搜索中..." />
          </div>
        ) : results.length > 0 ? (
          <>
            <Text type="secondary" style={{ marginBottom: 16, display: 'block' }}>
              找到 {total} 个相关结果
            </Text>
            <Table
              columns={columns}
              dataSource={results}
              rowKey="id"
              pagination={{
                pageSize: 20,
                total,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条`,
              }}
            />
          </>
        ) : (
          <Empty description="请输入关键词进行搜索" />
        )}
      </Card>
    </div>
  );
};

export default FileSearch;
