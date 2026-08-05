/**
 * D6 数据安全
 * 1. 权限扫描：离职成员、过期链接、越权成员
 * 2. 异常检测：下载量异常、敏感文件访问
 * 3. 数据导出审批：导出申请列表与审批
 */
import React, { useState } from 'react';
import {
  Card, Tabs, Typography, Space, Button, Tag, Modal, Input, message, Row, Col, Statistic, Descriptions, Alert,
} from 'antd';
import type { TabsProps } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import {
  ReloadOutlined, SafetyCertificateOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, DownloadOutlined,
} from '@ant-design/icons';
import {
  useStalePermissions, useDownloadAnomalies, useSensitiveAccess, useExportRequests,
  useCleanPermissions, useApproveExport,
} from '@/hooks/useOps';
import { useOpsPermission } from '@/hooks/useOpsPermission';
import {
  StalePermission, DownloadAnomaly, SensitiveAccess, ExportRequest,
  StaleTypeLabel, ExportStatusTag,
} from '@/types/ops';
import { formatDateTime, formatFileSize } from '@/utils';
import StatusTag from '@/components/ops/StatusTag';
import OpsTicketButton from '@/components/ops/OpsTicketButton';

const { Title, Text } = Typography;
const { TextArea } = Input;

const SecurityPage: React.FC = () => {
  const { can } = useOpsPermission();
  const [activeTab, setActiveTab] = useState('permissions');

  // 权限扫描
  const [permParams, setPermParams] = useState<{ page: number; pageSize: number; team_space_id?: number }>({ page: 1, pageSize: 10 });
  const permQ = useStalePermissions(permParams);
  const perms = permQ.data?.data?.list ?? [];
  const permTotal = permQ.data?.data?.total ?? 0;
  const cleanM = useCleanPermissions();
  const [selectedPermIds, setSelectedPermIds] = useState<number[]>([]);

  // 异常检测
  const [anoParams, setAnoParams] = useState<{ page: number; pageSize: number }>({ page: 1, pageSize: 10 });
  const anoQ = useDownloadAnomalies(anoParams);
  const anomalies = anoQ.data?.data?.list ?? [];
  const anoTotal = anoQ.data?.data?.total ?? 0;

  const [sensitiveParams, setSensitiveParams] = useState<{ page: number; pageSize: number }>({ page: 1, pageSize: 10 });
  const sensQ = useSensitiveAccess(sensitiveParams);
  const sensitive = sensQ.data?.data?.list ?? [];
  const sensTotal = sensQ.data?.data?.total ?? 0;

  // 导出审批
  const [exportParams, setExportParams] = useState<{ page: number; pageSize: number }>({ page: 1, pageSize: 10 });
  const exportQ = useExportRequests(exportParams);
  const exports = exportQ.data?.data?.list ?? [];
  const exportTotal = exportQ.data?.data?.total ?? 0;
  const approveM = useApproveExport();
  const [approveOpen, setApproveOpen] = useState(false);
  const [approveTarget, setApproveTarget] = useState<ExportRequest | null>(null);
  const [approveComment, setApproveComment] = useState('');

  /** 清理权限 */
  const handleClean = async () => {
    if (selectedPermIds.length === 0) {
      message.warning('请选择要清理的权限');
      return;
    }
    try {
      await cleanM.mutateAsync({ member_ids: selectedPermIds });
      message.success(`已清理 ${selectedPermIds.length} 项权限`);
      setSelectedPermIds([]);
      permQ.refetch();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '清理失败');
    }
  };

  /** 审批导出 */
  const handleApprove = async (approved: boolean) => {
    if (!approveTarget) return;
    try {
      await approveM.mutateAsync({ id: approveTarget.id, approved, comment: approveComment });
      message.success(approved ? '已通过审批' : '已拒绝');
      setApproveOpen(false);
      setApproveTarget(null);
      setApproveComment('');
      exportQ.refetch();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '审批失败');
    }
  };

  /** 权限扫描 Tab */
  const permColumns: ProColumns<StalePermission>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '用户', dataIndex: 'nickname', width: 120 },
    { title: '用户名', dataIndex: 'username', width: 140, render: (v) => v ? <Text code>{v as string}</Text> : '-' },
    { title: '空间', dataIndex: 'team_space_name', width: 140 },
    { title: '角色', dataIndex: 'role', width: 110 },
    {
      title: '异常类型', dataIndex: 'stale_type', width: 120,
      render: (_, r) => <Tag color="orange">{StaleTypeLabel[r.stale_type]}</Tag>,
    },
    { title: '发现时间', dataIndex: 'found_at', width: 160, render: (v) => formatDateTime(v as string) },
  ];
  const permTab: NonNullable<TabsProps['items']>[number] = {
    key: 'permissions',
    label: <Space><SafetyCertificateOutlined /><span>权限扫描</span></Space>,
    children: (
      <ProTable<StalePermission>
        rowKey="id"
        columns={permColumns}
        dataSource={perms}
        loading={permQ.isLoading}
        search={false}
        scroll={{ x: 1000 }}
        rowSelection={{
          selectedRowKeys: selectedPermIds,
          onChange: (keys) => setSelectedPermIds(keys as number[]),
        }}
        pagination={{
          current: permParams.page, pageSize: permParams.pageSize, total: permTotal,
          onChange: (page, pageSize) => setPermParams({ ...permParams, page, pageSize }),
        }}
        headerTitle={<Title level={5} style={{ margin: 0 }}>权限扫描结果</Title>}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => permQ.refetch()}>刷新</Button>,
          can('security') && (
            <Button key="clean" type="primary" danger icon={<DeleteOutlined />} onClick={handleClean} loading={cleanM.isPending} disabled={selectedPermIds.length === 0}>
              清理权限（{selectedPermIds.length}）
            </Button>
          ),
        ]}
      />
    ),
  };

  /** 异常检测 Tab */
  const anoColumns: ProColumns<DownloadAnomaly>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '用户', dataIndex: 'username', width: 140 },
    { title: '空间', dataIndex: 'team_space_name', width: 140 },
    { title: '下载数', dataIndex: 'count', width: 100, render: (v) => <Text strong style={{ color: '#ff4d4f' }}>{Number(v).toLocaleString()}</Text> },
    { title: '时间', dataIndex: 'time', width: 160, render: (v) => formatDateTime(v as string) },
    { title: '规则', dataIndex: 'rule', width: 220 },
    {
      title: '风险分', dataIndex: 'risk_score', width: 110,
      render: (v) => {
        const score = Number(v);
        const color = score >= 90 ? '#ff4d4f' : score >= 80 ? '#faad14' : '#52c41a';
        return <Tag color={color}>{score}</Tag>;
      },
    },
  ];
  const sensitiveColumns: ProColumns<SensitiveAccess>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '文件', dataIndex: 'file_name', width: 240, ellipsis: true },
    {
      title: '敏感等级', dataIndex: 'sensitivity_level', width: 100,
      render: (v) => {
        const lv = Number(v);
        return <Tag color={lv >= 5 ? 'red' : lv >= 4 ? 'orange' : lv >= 3 ? 'gold' : 'blue'}>L{lv}</Tag>;
      },
    },
    { title: '访问用户', dataIndex: 'username', width: 140 },
    { title: '访问次数', dataIndex: 'access_count', width: 100, render: (v) => <Text strong>{Number(v).toLocaleString()}</Text> },
    { title: '最后访问', dataIndex: 'last_access_at', width: 160, render: (v) => formatDateTime(v as string) },
  ];
  const anomalyTab: NonNullable<TabsProps['items']>[number] = {
    key: 'anomaly',
    label: <Space><SafetyCertificateOutlined /><span>异常检测</span></Space>,
    children: (
      <div>
        <Row gutter={12} style={{ marginBottom: 12 }}>
          <Col span={6}><Card size="small"><Statistic title="下载异常数" value={anoTotal} valueStyle={{ color: anoTotal > 0 ? '#ff4d4f' : undefined }} /></Card></Col>
          <Col span={6}><Card size="small"><Statistic title="敏感访问数" value={sensTotal} valueStyle={{ color: sensTotal > 0 ? '#faad14' : undefined }} /></Card></Col>
        </Row>
        <Card title="下载量异常" size="small" style={{ marginBottom: 12 }}>
          <ProTable<DownloadAnomaly>
            rowKey="id"
            columns={anoColumns}
            dataSource={anomalies}
            loading={anoQ.isLoading}
            search={false}
            scroll={{ x: 1100 }}
            pagination={{
              current: anoParams.page, pageSize: anoParams.pageSize, total: anoTotal,
              onChange: (page, pageSize) => setAnoParams({ page, pageSize }),
            }}
            toolBarRender={() => [
              <Button key="r" icon={<ReloadOutlined />} onClick={() => anoQ.refetch()}>刷新</Button>,
            ]}
          />
        </Card>
        <Card title="敏感文件访问" size="small">
          <ProTable<SensitiveAccess>
            rowKey="id"
            columns={sensitiveColumns}
            dataSource={sensitive}
            loading={sensQ.isLoading}
            search={false}
            scroll={{ x: 900 }}
            pagination={{
              current: sensitiveParams.page, pageSize: sensitiveParams.pageSize, total: sensTotal,
              onChange: (page, pageSize) => setSensitiveParams({ page, pageSize }),
            }}
            toolBarRender={() => [
              <Button key="r" icon={<ReloadOutlined />} onClick={() => sensQ.refetch()}>刷新</Button>,
            ]}
          />
        </Card>
      </div>
    ),
  };

  /** 导出审批 Tab */
  const exportColumns: ProColumns<ExportRequest>[] = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '申请人', dataIndex: 'requester_name', width: 100 },
    { title: '空间', dataIndex: 'team_space_name', width: 140 },
    {
      title: '导出范围', dataIndex: 'export_scope', width: 200, ellipsis: true,
      render: (v) => <Text code style={{ fontSize: 12 }}>{JSON.stringify(v)}</Text>,
    },
    { title: '数据量', dataIndex: 'data_size', width: 100, render: (v) => formatFileSize(Number(v)) },
    {
      title: '敏感等级', dataIndex: 'sensitive_level_max', width: 100,
      render: (v) => {
        const lv = Number(v);
        return <Tag color={lv >= 5 ? 'red' : lv >= 4 ? 'orange' : 'gold'}>L{lv}</Tag>;
      },
    },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (_, r) => {
        const tag = ExportStatusTag[r.status];
        return <StatusTag color={tag.color} text={tag.text} />;
      },
    },
    { title: '工单号', dataIndex: 'ticket_id', width: 100 },
    { title: '申请时间', dataIndex: 'created_at', width: 160, render: (v) => formatDateTime(v as string) },
    {
      title: '操作', width: 200, fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          {r.status === 0 && can('export:approve') && (
            <>
              <Button
                type="link"
                size="small"
                icon={<CheckOutlined />}
                style={{ color: '#52c41a' }}
                onClick={() => { setApproveTarget(r); setApproveOpen(true); setApproveComment(''); }}
              >
                通过
              </Button>
              <Button
                type="link"
                size="small"
                danger
                icon={<CloseOutlined />}
                onClick={() => { setApproveTarget(r); setApproveOpen(true); setApproveComment(''); }}
              >
                拒绝
              </Button>
            </>
          )}
          {r.status === 4 && r.package_url && (
            <Button type="link" size="small" icon={<DownloadOutlined />} href={r.package_url}>
              下载
            </Button>
          )}
        </Space>
      ),
    },
  ];
  const exportTab: NonNullable<TabsProps['items']>[number] = {
    key: 'exports',
    label: <Space><DownloadOutlined /><span>导出审批</span></Space>,
    children: (
      <ProTable<ExportRequest>
        rowKey="id"
        columns={exportColumns}
        dataSource={exports}
        loading={exportQ.isLoading}
        search={false}
        scroll={{ x: 1400 }}
        pagination={{
          current: exportParams.page, pageSize: exportParams.pageSize, total: exportTotal,
          onChange: (page, pageSize) => setExportParams({ page, pageSize }),
        }}
        headerTitle={<Title level={5} style={{ margin: 0 }}>数据导出审批</Title>}
        toolBarRender={() => [
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => exportQ.refetch()}>刷新</Button>,
          can('ticket:apply') && (
            <OpsTicketButton
              ticketType="EXPORT"
              targetRef="export:new"
              impactPreview={{}}
              buttonText="申请导出"
            />
          ),
        ]}
      />
    ),
  };

  return (
    <div>
      <Card bordered={false} style={{ marginBottom: 12 }}>
        <Space>
          <SafetyCertificateOutlined style={{ fontSize: 20, color: '#1677ff' }} />
          <Title level={5} style={{ margin: 0 }}>数据安全</Title>
          <Text type="secondary">D6 · 权限扫描 / 异常检测 / 导出审批</Text>
        </Space>
      </Card>

      <Card bordered={false}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[permTab, anomalyTab, exportTab]}
        />
      </Card>

      <Modal
        open={approveOpen}
        title={approveTarget ? `审批导出申请 #${approveTarget.id}` : '审批'}
        onCancel={() => setApproveOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setApproveOpen(false)}>取消</Button>,
          <Button key="reject" danger icon={<CloseOutlined />} loading={approveM.isPending} onClick={() => handleApprove(false)}>
            拒绝
          </Button>,
          <Button key="approve" type="primary" icon={<CheckOutlined />} loading={approveM.isPending} onClick={() => handleApprove(true)}>
            通过
          </Button>,
        ]}
        destroyOnClose
      >
        {approveTarget && (
          <div>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="申请人">{approveTarget.requester_name}</Descriptions.Item>
              <Descriptions.Item label="空间">{approveTarget.team_space_name}</Descriptions.Item>
              <Descriptions.Item label="数据量">{formatFileSize(approveTarget.data_size)}</Descriptions.Item>
              <Descriptions.Item label="敏感等级">L{approveTarget.sensitive_level_max}</Descriptions.Item>
              <Descriptions.Item label="导出范围">
                <Text code style={{ fontSize: 12 }}>{JSON.stringify(approveTarget.export_scope)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="水印">{approveTarget.watermark}</Descriptions.Item>
            </Descriptions>
            <Alert
              type="warning"
              showIcon
              message="请确认申请人身份与导出范围合规后再审批"
              style={{ marginTop: 12 }}
            />
            <TextArea
              rows={3}
              placeholder="审批意见"
              value={approveComment}
              onChange={(e) => setApproveComment(e.target.value)}
              style={{ marginTop: 12 }}
            />
          </div>
        )}
      </Modal>
    </div>
  );
};

export default SecurityPage;
