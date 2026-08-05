/**
 * 监控看板主入口
 * 含全局筛选器(时间范围/团队空间)与多 Tab 看板
 */
import React, { useMemo, useState } from 'react';
import { Typography, Segmented, Select, Tabs, Space, Tag } from 'antd';
import {
  LineChartOutlined,
  TeamOutlined,
  FilterOutlined,
  SearchOutlined,
  DashboardOutlined,
} from '@ant-design/icons';
import Overview from './tabs/Overview';
import SpaceDetail from './tabs/SpaceDetail';
import Funnel from './tabs/Funnel';
import SearchExperience from './tabs/SearchExperience';
import Slo from './tabs/Slo';
import { mockTeamSpaces } from '@/mock/monitor';
import { type MonitorFilter, type TimeRange } from '@/types';

const { Title } = Typography;

const TIME_OPTIONS: Array<{ label: string; value: TimeRange }> = [
  { label: '近1小时',  value: '1h'  },
  { label: '近6小时',  value: '6h'  },
  { label: '近24小时', value: '24h' },
  { label: '近7天',    value: '7d'  },
  { label: '近30天',   value: '30d' },
];

const Monitor: React.FC = () => {
  const [timeRange, setTimeRange] = useState<TimeRange>('24h');
  const [teamSpaceId, setTeamSpaceId] = useState<number | undefined>(undefined);
  const [activeTab, setActiveTab] = useState('overview');

  const filter: MonitorFilter = useMemo(
    () => ({ timeRange, teamSpaceId }),
    [timeRange, teamSpaceId]
  );

  const handleTabChange = (key: string) => {
    // 切换到团队空间详情时,如果未选空间,自动选第一个
    if (key === 'space' && !teamSpaceId) {
      setTeamSpaceId(mockTeamSpaces[0]?.id);
    }
    setActiveTab(key);
  };

  return (
    <div>
      {/* 顶部标题与全局筛选器 */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <Title level={4} style={{ margin: 0 }}>
          数据空间监控看板
        </Title>
        <Space size={12} wrap>
          <Tag icon={<FilterOutlined />} color="blue">
            全局筛选
          </Tag>
          <Segmented
            options={TIME_OPTIONS}
            value={timeRange}
            onChange={(v) => setTimeRange(v as TimeRange)}
          />
          <Select
            style={{ width: 220 }}
            placeholder="选择团队空间"
            allowClear
            showSearch
            value={teamSpaceId}
            onChange={(v) => setTeamSpaceId(v)}
            options={mockTeamSpaces.map(s => ({
              label: `${s.name} (${s.code})`,
              value: s.id,
            }))}
          />
        </Space>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        items={[
          {
            key: 'overview',
            label: (
              <span>
                <DashboardOutlined /> 业务总览
              </span>
            ),
            children: <Overview filter={filter} />,
          },
          {
            key: 'space',
            label: (
              <span>
                <TeamOutlined /> 团队空间详情
              </span>
            ),
            children: <SpaceDetail filter={filter} />,
          },
          {
            key: 'funnel',
            label: (
              <span>
                <LineChartOutlined /> 业务链路漏斗
              </span>
            ),
            children: <Funnel filter={filter} />,
          },
          {
            key: 'search',
            label: (
              <span>
                <SearchOutlined /> 搜索体验
              </span>
            ),
            children: <SearchExperience filter={filter} />,
          },
          {
            key: 'slo',
            label: (
              <span>
                <DashboardOutlined /> SLO 监控
              </span>
            ),
            children: <Slo filter={filter} />,
          },
        ]}
      />
    </div>
  );
};

export default Monitor;
