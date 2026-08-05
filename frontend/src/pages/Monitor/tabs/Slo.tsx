/**
 * SLO 监控看板
 */
import React, { useMemo } from 'react';
import { Row, Col, Card, Tag, Table, Empty } from 'antd';
import ChartCard from '../components/ChartCard';
import ReactECharts from 'echarts-for-react';
import {
  sloBurnRateOption,
  sloBudgetGaugeOption,
} from '../charts';
import { getMockSloStatus } from '@/mock/monitor';
import type { MonitorFilter, SloStatus } from '@/types';

interface Props {
  filter: MonitorFilter;
}

const statusMeta: Record<number, { color: string; text: string }> = {
  0: { color: 'success', text: '达标' },
  1: { color: 'warning', text: '告警' },
  2: { color: 'error', text: '违约' },
};

const Slo: React.FC<Props> = ({ filter }) => {
  const sloList = useMemo(() => getMockSloStatus(filter.teamSpaceId), [filter]);

  return (
    <div>
      <Row gutter={[16, 16]}>
        {sloList.map(slo => (
          <Col xs={24} sm={12} lg={8} key={slo.sloCode}>
            <Card
              size="small"
              title={
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span>{slo.sloName}</span>
                  <Tag color={statusMeta[slo.status].color}>{statusMeta[slo.status].text}</Tag>
                </div>
              }
            >
              <Row>
                <Col span={12}>
                  <ReactECharts
                    option={sloBudgetGaugeOption(slo.errorBudgetRemaining)}
                    style={{ height: 160, width: '100%' }}
                    notMerge
                    lazyUpdate
                  />
                  <div style={{ textAlign: 'center', fontSize: 12, color: '#666' }}>剩余错误预算</div>
                </Col>
                <Col span={12}>
                  <div style={{ padding: '12px 0', fontSize: 12, lineHeight: 2 }}>
                    <div>目标: <b>{slo.targetValue} {slo.targetUnit}</b></div>
                    <div>实际: <b style={{ color: slo.actualValue >= slo.targetValue ? '#52c41a' : '#ff4d4f' }}>{slo.actualValue} {slo.targetUnit}</b></div>
                    <div>2h 燃烧率: <b>{slo.burnRate2h}</b></div>
                    <div>6h 燃烧率: <b>{slo.burnRate6h}</b></div>
                  </div>
                </Col>
              </Row>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <ChartCard title="SLO 燃烧率多窗口" option={sloBurnRateOption(sloList)} height={320} />
        </Col>
        <Col xs={24} lg={10}>
          <Card title="SLO 状态明细" size="small">
            <Table
              size="small"
              rowKey="sloCode"
              dataSource={sloList}
              pagination={false}
              scroll={{ y: 280 }}
              locale={{ emptyText: <Empty /> }}
              columns={[
                { title: 'SLO', dataIndex: 'sloName', width: 140 },
                { title: '目标', dataIndex: 'targetValue', width: 80, render: (v: number, r: SloStatus) => `${v} ${r.targetUnit}` },
                { title: '实际', dataIndex: 'actualValue', width: 80, render: (v: number, r: SloStatus) => `${v} ${r.targetUnit}` },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 80,
                  render: (v: number) => <Tag color={statusMeta[v].color}>{statusMeta[v].text}</Tag>,
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Slo;
