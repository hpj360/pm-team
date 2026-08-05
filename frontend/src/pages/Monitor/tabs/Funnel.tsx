/**
 * 业务链路漏斗看板
 */
import React, { useMemo } from 'react';
import { Row, Col, Card, Table, Empty } from 'antd';
import ChartCard from '../components/ChartCard';
import {
  funnelOption,
  latencyWaterfallOption,
  stageFailRateOption,
  indexLagOption,
  fileEventColumns,
} from '../charts';
import {
  getMockFunnel,
  getMockStageSeries,
  getMockQueueLag,
  getMockFileEvents,
} from '@/mock/monitor';
import { Stage, type MonitorFilter } from '@/types';

interface Props {
  filter: MonitorFilter;
}

const Funnel: React.FC<Props> = ({ filter }) => {
  const funnel = useMemo(() => getMockFunnel(filter.teamSpaceId), [filter]);
  const stageSeries = useMemo(
    () => getMockStageSeries(filter.timeRange, filter.teamSpaceId),
    [filter]
  );
  const queueLag = useMemo(() => getMockQueueLag(), []);
  const events = useMemo(() => getMockFileEvents(filter.teamSpaceId, 50), [filter]);

  const indexSeries = stageSeries.find(s => s.stage === Stage.INDEX)!;

  // 各阶段失败率
  const failStages = [Stage.UPLOAD, Stage.INDEX, Stage.PARSE];
  const failRates = failStages.map(st => {
    const s = stageSeries.find(x => x.stage === st);
    if (!s) return 0;
    const avg = s.points.reduce((sum, p) => sum + (100 - p.successRate), 0) / s.points.length;
    return +avg.toFixed(2);
  });

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <ChartCard title="上传 → 索引 → 解析 漏斗" option={funnelOption(funnel)} height={320} />
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard
            title="各阶段失败率对比"
            option={stageFailRateOption(failStages, failRates)}
            height={320}
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard
            title="端到端时延瀑布(P95 均值)"
            option={latencyWaterfallOption(stageSeries)}
            height={300}
          />
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard
            title="索引可搜时延 P95 趋势"
            option={indexLagOption(indexSeries)}
            height={300}
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title="解析队列积压 Top 空间" size="small">
            <Table
              size="small"
              rowKey="teamSpaceId"
              dataSource={queueLag}
              pagination={false}
              scroll={{ y: 280 }}
              locale={{ emptyText: <Empty /> }}
              columns={[
                { title: '排名', render: (_, __, i) => i + 1, width: 60 },
                { title: '团队空间', dataIndex: 'teamSpaceName' },
                {
                  title: '积压数',
                  dataIndex: 'lag',
                  sorter: (a: any, b: any) => b.lag - a.lag,
                  render: (v: number) => (
                    <span style={{ color: v > 100 ? '#ff4d4f' : v > 50 ? '#faad14' : '#52c41a' }}>
                      {v}
                    </span>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="链路追踪详情(最近事件)" size="small">
            <Table
              size="small"
              rowKey="id"
              columns={fileEventColumns.slice(0, 7)}
              dataSource={events}
              pagination={{ pageSize: 6, size: 'small' }}
              scroll={{ x: 800 }}
              locale={{ emptyText: <Empty /> }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Funnel;
