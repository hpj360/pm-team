/**
 * 权限管理页面
 * 权限列表 + 资源树
 */
import React, { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Typography,
  Tree,
  Tag,
  Space,
  Row,
  Col,
  Empty,
  Spin,
  Input,
  Button,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import {
  AppstoreOutlined,
  MenuOutlined,
  ApiOutlined,
  ThunderboltOutlined,
  DatabaseOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { getAdminPermissions } from '@/services';
import { PermissionTypeLabel } from '@/types';
import type { AdminPermission, PermissionType } from '@/types';
import { formatDateTime } from '@/utils';
import { colors } from '@/styles/tokens';

const { Title, Text } = Typography;

/** 权限类型图标 */
const typeIcon: Record<PermissionType, React.ReactNode> = {
  menu: <MenuOutlined style={{ color: colors.severity.info }} />,
  api: <ApiOutlined style={{ color: colors.severity.high }} />,
  action: <ThunderboltOutlined style={{ color: colors.severity.medium }} />,
  data: <DatabaseOutlined style={{ color: colors.success }} />,
};

/** 权限类型颜色 */
const typeColor: Record<PermissionType, string> = {
  menu: 'blue',
  api: 'red',
  action: 'orange',
  data: 'green',
};

const PermissionManagePage: React.FC = () => {
  const [list, setList] = useState<AdminPermission[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    setLoading(true);
    getAdminPermissions()
      .then((res) => setList(res.data))
      .finally(() => setLoading(false));
  }, []);

  /** 过滤 */
  const filtered = useMemo(() => {
    if (!keyword) return list;
    const kw = keyword.toLowerCase();
    return list.filter(
      (p) =>
        p.name.toLowerCase().includes(kw) ||
        p.code.toLowerCase().includes(kw) ||
        p.resource.toLowerCase().includes(kw),
    );
  }, [list, keyword]);

  /** 资源树构建 */
  const resourceTree = useMemo<DataNode[]>(() => {
    const map = new Map<string, DataNode & { children: DataNode[] }>();
    for (const p of list) {
      map.set(p.id, {
        key: p.id,
        title: (
          <Space size={6}>
            {typeIcon[p.type]}
            <Text strong>{p.name}</Text>
            <Tag color={typeColor[p.type]} style={{ fontSize: 11 }}>
              {PermissionTypeLabel[p.type]}
            </Tag>
            <Tag style={{ fontSize: 11 }}>{p.code}</Tag>
          </Space>
        ),
        children: [],
      });
    }
    const roots: DataNode[] = [];
    for (const p of list) {
      const node = map.get(p.id)!;
      if (p.parentId && map.has(p.parentId)) {
        map.get(p.parentId)!.children.push(node);
      } else {
        roots.push(node);
      }
    }
    return roots;
  }, [list]);

  /** 列定义 */
  const columns: ProColumns<AdminPermission>[] = [
    {
      title: '权限名称',
      dataIndex: 'name',
      key: 'name',
      width: 180,
      render: (_, record) => (
        <Space>
          {typeIcon[record.type]}
          <Text strong>{record.name}</Text>
        </Space>
      ),
    },
    {
      title: '权限编码',
      dataIndex: 'code',
      key: 'code',
      width: 200,
      copyable: true,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      filters: [
        { text: '菜单', value: 'menu' },
        { text: '接口', value: 'api' },
        { text: '操作', value: 'action' },
        { text: '数据', value: 'data' },
      ],
      onFilter: (val, record) => record.type === val,
      render: (type: unknown) => (
        <Tag color={typeColor[type as PermissionType]}>
          {PermissionTypeLabel[type as PermissionType]}
        </Tag>
      ),
    },
    {
      title: '资源路径',
      dataIndex: 'resource',
      key: 'resource',
      ellipsis: true,
      render: (v: unknown) => (
        <Tag style={{ fontFamily: 'monospace' }}>{v as string}</Tag>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      render: (v: unknown) => formatDateTime(v as string),
    },
  ];

  return (
    <div>
      <Title level={4}>权限管理</Title>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8} xl={7}>
          <Card
            title={
              <Space>
                <AppstoreOutlined />
                <span>资源权限树</span>
              </Space>
            }
            size="small"
            bodyStyle={{ maxHeight: 640, overflow: 'auto' }}
            extra={
              <Button size="small" icon={<ReloadOutlined />} onClick={() => setKeyword('')}>
                展开
              </Button>
            }
          >
            {loading ? (
              <Spin>
                <Empty description="加载中" />
              </Spin>
            ) : (
              <Tree
                treeData={resourceTree}
                defaultExpandAll
                showLine
                selectable
              />
            )}
          </Card>
        </Col>

        <Col xs={24} lg={16} xl={17}>
          <Card
            title="权限列表"
            extra={
              <Input
                placeholder="搜索名称 / 编码 / 资源"
                prefix={<SearchOutlined />}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                allowClear
                style={{ width: 280 }}
              />
            }
          >
            <ProTable<AdminPermission>
              columns={columns}
              dataSource={filtered}
              rowKey="id"
              loading={loading}
              search={false}
              pagination={{ pageSize: 12, showSizeChanger: true }}
              size="middle"
              scroll={{ x: 1000 }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default PermissionManagePage;
