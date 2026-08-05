/**
 * 审计日志页面
 * ProTable + 时间筛选 + 操作类型筛选 + 用户筛选
 * 日志详情抽屉
 */
import React, { useMemo, useRef, useState } from 'react';
import {
  Card,
  Typography,
  Button,
  Space,
  Tag,
  Drawer,
  Descriptions,
  DatePicker,
  Select,
} from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import {
  ReloadOutlined,
  ExportOutlined,
  EyeOutlined,
  LoginOutlined,
  LogoutOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  DownloadOutlined,
  UploadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { getAuditLogs, getAuditLogDetail, exportAuditLogs } from '@/services';
import { AuditActionLabel } from '@/types';
import type { AuditLogItem, AuditAction } from '@/types';
import { formatDateTime } from '@/utils';

const { Title, Text, Paragraph } = Typography;

const { RangePicker } = DatePicker;

/** 操作类型图标 */
const actionIcon: Record<AuditAction, React.ReactNode> = {
  login: <LoginOutlined />,
  logout: <LogoutOutlined />,
  create: <PlusOutlined />,
  update: <EditOutlined />,
  delete: <DeleteOutlined />,
  export: <ExportOutlined />,
  import: <UploadOutlined />,
  execute: <ThunderboltOutlined />,
};

/** 操作类型颜色 */
const actionColor: Record<AuditAction, string> = {
  login: 'blue',
  logout: 'default',
  create: 'success',
  update: 'warning',
  delete: 'error',
  export: 'purple',
  import: 'cyan',
  execute: 'magenta',
};

const AuditLogPage: React.FC = () => {
  const actionRef = useRef<ActionType>(null);
  const [detail, setDetail] = useState<AuditLogItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [username, setUsername] = useState<string>('');
  const [action, setAction] = useState<AuditAction | undefined>();
  const [timeRange, setTimeRange] = useState<[Dayjs, Dayjs] | null>(null);

  /** 打开详情 */
  const openDetail = (record: AuditLogItem) => {
    getAuditLogDetail(record.id).then((res) => {
      setDetail(res.data);
      setDrawerOpen(true);
    });
  };

  /** 导出 */
  const handleExport = async () => {
    await exportAuditLogs({
      username: username || undefined,
      action,
      startTime: timeRange?.[0].toISOString(),
      endTime: timeRange?.[1].toISOString(),
    });
  };

  /** 列定义 */
  const columns: ProColumns<AuditLogItem>[] = useMemo(
    () => [
      {
        title: '时间',
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 160,
        sorter: (a, b) => (a.createdAt < b.createdAt ? 1 : -1),
        render: (v: unknown) => formatDateTime(v as string),
      },
      {
        title: '用户',
        dataIndex: 'username',
        key: 'username',
        width: 130,
        render: (v: unknown) => <Tag color="blue">{v as string}</Tag>,
      },
      {
        title: '操作类型',
        dataIndex: 'action',
        key: 'action',
        width: 110,
        render: (v: unknown) => {
          const a = v as AuditAction;
          return (
            <Tag color={actionColor[a]} icon={actionIcon[a]}>
              {AuditActionLabel[a]}
            </Tag>
          );
        },
      },
      {
        title: '资源',
        dataIndex: 'resource',
        key: 'resource',
        ellipsis: true,
        render: (v: unknown) => (
          <Tag style={{ fontFamily: 'monospace' }}>{v as string}</Tag>
        ),
      },
      {
        title: '详情',
        dataIndex: 'detail',
        key: 'detail',
        ellipsis: true,
      },
      {
        title: 'IP',
        dataIndex: 'ip',
        key: 'ip',
        width: 130,
        render: (v: unknown) => <Text code style={{ fontSize: 12 }}>{v as string}</Text>,
      },
      {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 90,
        render: (v: unknown) =>
          v === 'success' ? (
            <Tag color="success">成功</Tag>
          ) : (
            <Tag color="error">失败</Tag>
          ),
      },
      {
        title: '耗时',
        dataIndex: 'costMs',
        key: 'costMs',
        width: 90,
        sorter: (a, b) => a.costMs - b.costMs,
        render: (v: unknown) => (
          <Text type={v as number > 200 ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>
            {(v as number).toFixed(0)} ms
          </Text>
        ),
      },
      {
        title: '操作',
        key: 'action',
        width: 100,
        fixed: 'right',
        render: (_, record) => (
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openDetail(record)}>
            详情
          </Button>
        ),
      },
    ],
    [],
  );

  /** 操作类型选项 */
  const actionOptions = (Object.keys(AuditActionLabel) as AuditAction[]).map((a) => ({
    label: AuditActionLabel[a],
    value: a,
  }));

  return (
    <div>
      <Title level={4}>审计日志</Title>

      <Card style={{ marginBottom: 12 }}>
        <Space wrap>
          <Select
            allowClear
            placeholder="用户名"
            style={{ width: 200 }}
            value={username || undefined}
            onChange={setUsername}
            options={['admin', 'redteam_lead', 'redteam_web', 'redteam_ot', 'analyst_01', 'viewer'].map((u) => ({ label: u, value: u }))}
          />
          <Select
            allowClear
            placeholder="操作类型"
            style={{ width: 160 }}
            value={action}
            onChange={setAction}
            options={actionOptions}
          />
          <RangePicker
            showTime
            value={timeRange ?? undefined}
            onChange={(range) => setTimeRange(range as [Dayjs, Dayjs] | null)}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              setUsername('');
              setAction(undefined);
              setTimeRange(null);
              actionRef.current?.reload();
            }}
          >
            重置
          </Button>
          <Button type="primary" icon={<DownloadOutlined />} onClick={handleExport}>
            导出
          </Button>
        </Space>
      </Card>

      <Card>
        <ProTable<AuditLogItem>
          actionRef={actionRef}
          columns={columns}
          rowKey="id"
          search={false}
          request={async (params) => {
            const page = params.current ?? 1;
            const pageSize = params.pageSize ?? 20;
            const res = await getAuditLogs({
              username: username || undefined,
              action,
              startTime: timeRange?.[0].toISOString(),
              endTime: timeRange?.[1].toISOString(),
              page,
              pageSize,
            });
            return {
              data: res.data.list,
              total: res.data.total,
              success: true,
            };
          }}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          size="middle"
          scroll={{ x: 1100 }}
          toolBarRender={false}
        />
      </Card>

      {/* 详情抽屉 */}
      <Drawer
        title="日志详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
      >
        {detail && (
          <>
            <Tag color={actionColor[detail.action]} icon={actionIcon[detail.action]} style={{ marginBottom: 12 }}>
              {AuditActionLabel[detail.action]}
            </Tag>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="日志 ID">{detail.id}</Descriptions.Item>
              <Descriptions.Item label="时间">{formatDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="用户">{detail.username}</Descriptions.Item>
              <Descriptions.Item label="用户 ID">{detail.userId}</Descriptions.Item>
              <Descriptions.Item label="操作">{AuditActionLabel[detail.action]}</Descriptions.Item>
              <Descriptions.Item label="资源">{detail.resource}</Descriptions.Item>
              <Descriptions.Item label="资源 ID">{detail.resourceId}</Descriptions.Item>
              <Descriptions.Item label="详情">{detail.detail}</Descriptions.Item>
              <Descriptions.Item label="IP">{detail.ip}</Descriptions.Item>
              <Descriptions.Item label="User-Agent">{detail.userAgent}</Descriptions.Item>
              <Descriptions.Item label="状态">
                {detail.status === 'success' ? (
                  <Tag color="success">成功</Tag>
                ) : (
                  <Tag color="error">失败</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="耗时">{detail.costMs} ms</Descriptions.Item>
            </Descriptions>
            <Paragraph type="secondary" style={{ marginTop: 12, fontSize: 12 }}>
              该日志为不可篡改记录，保存时长由系统配置中的「审计日志保留」控制。
            </Paragraph>
          </>
        )}
      </Drawer>
    </div>
  );
};

export default AuditLogPage;
