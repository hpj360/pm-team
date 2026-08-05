/**
 * 业务总览看板
 */
import React, { useMemo } from 'react';
import { Row, Col, Card, Statistic } from 'antd';
import {
  FileTextOutlined,
  DatabaseOutlined,
  TeamOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import ChartCard from '../components/ChartCard';
import {
  successRateLineOption,
  durationP95LineOption,
  storageRankingOption,
  fileTypePieOption,
  funnelOption,
} from '../charts';
import {
  getMockKpi,
  getMockStageSeries,
  getMockStorageRanking,
  getMockFileTypeDist,
  getMockFunnel,
} from '@/mock/monitor';
import { formatFileSize } from '@/utils';
import type { MonitorFilter } from '@/types';

interface Props {
  filter: MonitorFilter;
}

const Overview: React.FC<Props> = ({ filter }) => {
  const kpi = useMemo(() => getMockKpi(filter.timeRange, filter.teamSpaceId), [filter]);
  const stageSeries = useMemo(() => getMockStageSeries(filter.timeRange, filter.teamSpaceId), [filter]);
  const storageRanking = useMemo(() => getMockStorageRanking(10), []);
  const fileTypeDist = useMemo(() => getMockFileTypeDist(filter.teamSpaceId), [filter]);
  const funnel = useMemo(() => getMockFunnel(filter.teamSpaceId), [filter]);

  return (
    <div>
      {/* KPI 卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic
              title="上传文件数"
              value={kpi.uploadCount}
              prefix={<FileTextOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic
              title="总存储量"
              value={formatFileSize(kpi.totalStorage)}
              prefix={<DatabaseOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic
              title="在线团队空间"
              value={kpi.spaceCount}
              prefix={<TeamOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Statistic
              title="今日搜索数"
              value={kpi.searchCountToday}
              prefix={<SearchOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard
            title="四阶段成功率趋势"
            option={successRateLineOption(stageSeries)}
            height={300}
          />
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard
            title="四阶段耗时 P95 趋势"
            option={durationP95LineOption(stageSeries)}
            height={300}
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard
            title="团队空间存储用量排行"
            option={storageRankingOption(storageRanking)}
            height={300}
          />
        </Col>
        <Col xs={24} lg={6}>
          <ChartCard
            title="文件类型分布"
            option={fileTypePieOption(fileTypeDist)}
            height={300}
          />
        </Col>
        <Col xs={24} lg={6}>
          <ChartCard
            title="业务链路漏斗"
            option={funnelOption(funnel)}
            height={300}
          />
        </Col>
      </Row>
    </div>
  );
};

export default Overview;
