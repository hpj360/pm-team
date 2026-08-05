/**
 * 团队空间详情看板
 */
import React, { useMemo } from 'react';
import { Row, Col, Card, Statistic, Table, Empty, Alert } from 'antd';
import {
  FileTextOutlined,
  DatabaseOutlined,
  PieChartOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import ChartCard from '../components/ChartCard';
import {
  uploadTrendOption,
  indexLagOption,
  parseStackBarOption,
  failReasonPieOption,
  searchP95Option,
  quotaGaugeOption,
  fileEventColumns,
} from '../charts';
import {
  mockTeamSpaces,
  getMockStageSeries,
  getMockFailReasons,
  getMockFileEvents,
} from '@/mock/monitor';
import { formatFileSize } from '@/utils';
import { Stage, type MonitorFilter } from '@/types';

interface Props {
  filter: MonitorFilter;
}

const SpaceDetail: React.FC<Props> = ({ filter }) => {
  const space = useMemo(
    () => mockTeamSpaces.find(s => s.id === filter.teamSpaceId),
    [filter.teamSpaceId]
  );

  const stageSeries = useMemo(
    () => getMockStageSeries(filter.timeRange, filter.teamSpaceId),
    [filter]
  );
  const parseFailReasons = useMemo(
    () => getMockFailReasons(Stage.PARSE, filter.teamSpaceId),
    [filter]
  );
  const events = useMemo(
    () => getMockFileEvents(filter.teamSpaceId, 20),
    [filter]
  );

  if (!space) {
    return (
      <Alert
        type="info"
        showIcon
        message="请先在顶部筛选器中选择一个具体的团队空间"
        description="团队空间详情看板需要锁定单一空间进行下钻分析。"
      />
    );
  }

  const uploadSeries = stageSeries.find(s => s.stage === Stage.UPLOAD)!;
  const indexSeries = stageSeries.find(s => s.stage === Stage.INDEX)!;
  const searchSeries = stageSeries.find(s => s.stage === Stage.SEARCH)!;
  const usageRate = +((space.storageUsed / space.storageQuota) * 100).toFixed(2);

  // 解析成功率按文件类型(用文件类型清单生成模拟数据)
  const fileTypes = ['pdf', 'docx', 'eml', 'exe', 'pcap', 'zip'];
  const success = fileTypes.map(() => Math.floor(Math.random() * 800 + 200));
  const fail = fileTypes.map(() => Math.floor(Math.random() * 100 + 10));

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic title="文件数" value={space.fileCount} prefix={<FileTextOutlined />} valueStyle={{ color: '#1890ff' }} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic title="存储用量" value={formatFileSize(space.storageUsed)} prefix={<DatabaseOutlined />} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic title="配额使用率" value={usageRate} suffix="%" prefix={<PieChartOutlined />} valueStyle={{ color: usageRate > 90 ? '#ff4d4f' : '#faad14' }} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic title="今日上传" value={uploadSeries.points[uploadSeries.points.length - 1]?.count || 0} prefix={<UploadOutlined />} valueStyle={{ color: '#722ed1' }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={8}>
          <ChartCard title="配额使用率仪表盘" option={quotaGaugeOption(usageRate)} height={280} />
        </Col>
        <Col xs={24} lg={16}>
          <ChartCard title="上传趋势(文件数 + 字节MB)" option={uploadTrendOption(stageSeries)} height={280} />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard title="索引积压趋势" option={indexLagOption(indexSeries)} height={280} />
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard title="解析成功率按文件类型" option={parseStackBarOption(fileTypes, success, fail)} height={280} />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard title="失败原因 Top5" option={failReasonPieOption(parseFailReasons)} height={280} />
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard
            title="搜索 P95 与零命中率"
            option={searchP95Option(searchSeries, searchSeries.points.map(() => +(Math.random() * 20 + 5).toFixed(2)))}
            height={280}
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card title="最近 20 条事件" size="small">
            <Table
              size="small"
              rowKey="id"
              columns={fileEventColumns}
              dataSource={events}
              pagination={{ pageSize: 8, size: 'small' }}
              scroll={{ x: 1100 }}
              locale={{ emptyText: <Empty description="暂无事件" /> }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default SpaceDetail;
