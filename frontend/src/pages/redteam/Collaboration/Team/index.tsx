/**
 * 团队成员页
 * - 团队成员列表（在线状态、当前任务）
 * - 成员详情侧边栏
 * - 任务分配统计
 * - 在线/离线分布
 */
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Typography,
  Tag,
  Space,
  Button,
  Row,
  Col,
  Statistic,
  Drawer,
  Empty,
  Spin,
  Avatar,
  message,
  Input,
  Segmented,
  List,
  Badge,
} from 'antd';
import {
  ArrowLeftOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  PlusOutlined,
  UserOutlined,
  FireOutlined,
  ThunderboltOutlined,
  SearchOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import { ProDescriptions } from '@ant-design/pro-components';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { mockTeamMembers, mockCollaborationTasks } from '@/mock/collaboration';
import type { TeamMember } from '@/types';
import { formatDateTime } from '@/utils';
import { colors, spacing } from '@/styles/tokens';

const { Title, Text } = Typography;

const TeamPage: React.FC = () => {
  const navigate = useNavigate();
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [filter, setFilter] = useState<'all' | 'online' | 'offline'>('all');
  const [selected, setSelected] = useState<TeamMember | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    setLoading(true);
    setTimeout(() => {
      setMembers([...mockTeamMembers]);
      setLoading(false);
    }, 200);
  }, []);

  /** 过滤后的成员 */
  const filtered = useMemo(() => {
    let arr = [...members];
    if (filter === 'online') arr = arr.filter((m) => m.online);
    if (filter === 'offline') arr = arr.filter((m) => !m.online);
    if (keyword.trim()) {
      const kw = keyword.toLowerCase();
      arr = arr.filter(
        (m) => m.name.toLowerCase().includes(kw) || m.role.toLowerCase().includes(kw),
      );
    }
    return arr;
  }, [members, filter, keyword]);

  /** 统计 */
  const stats = useMemo(() => {
    const total = members.length;
    const online = members.filter((m) => m.online).length;
    const offline = total - online;
    const busy = members.filter((m) => !!m.currentTask).length;
    return { total, online, offline, busy };
  }, [members]);

  /** 角色分布图 */
  const roleChartOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { top: 0, left: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        data: (() => {
          const map = new Map<string, number>();
          members.forEach((m) => {
            map.set(m.role, (map.get(m.role) ?? 0) + 1);
          });
          return Array.from(map.entries()).map(([name, value]) => ({ name, value }));
        })(),
      },
    ],
  };

  /** 在线状态图 */
  const onlineChartOption: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { top: 0, left: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        data: [
          { value: stats.online, name: '在线', itemStyle: { color: colors.success } },
          { value: stats.offline, name: '离线', itemStyle: { color: colors.neutral[400] } },
        ],
      },
    ],
  };

  /** 任务分配图 */
  const taskAssignChartOption: EChartsOption = {
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: members.map((m) => m.name) },
    yAxis: { type: 'value', name: '任务数' },
    series: [
      {
        type: 'bar',
        name: '任务数',
        data: members.map((m) =>
          mockCollaborationTasks.filter((t) => t.assignee === m.name).length,
        ),
        itemStyle: { color: colors.info, borderRadius: [4, 4, 0, 0] },
      },
    ],
  };

  /** 渲染成员卡片 */
  const renderMemberCard = (member: TeamMember) => {
    const taskCount = mockCollaborationTasks.filter((t) => t.assignee === member.name).length;
    return (
      <Card
        key={member.id}
        size="small"
        hoverable
        onClick={() => {
          setSelected(member);
          setDrawerOpen(true);
        }}
        style={{ cursor: 'pointer', marginBottom: spacing[3] }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Badge dot status={member.online ? 'success' : 'default'} offset={[-4, 36]}>
            <Avatar size={48} icon={<UserOutlined />} src={member.avatar} style={{ backgroundColor: member.online ? colors.info : colors.neutral[400] }} />
          </Badge>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Space>
                <Text strong>{member.name}</Text>
                <Tag color={member.online ? 'success' : 'default'}>{member.online ? '在线' : '离线'}</Tag>
              </Space>
              <Tag color="blue">{member.role}</Tag>
            </div>
            <div style={{ marginTop: 4 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {member.currentTask ? `正在处理：${member.currentTask}` : '暂无任务'}
              </Text>
            </div>
            <div style={{ marginTop: 4, fontSize: 12, color: '#8c8c8c' }}>
              最后活跃：{formatDateTime(member.lastActive)}
              <span style={{ marginLeft: 12 }}>任务数：{taskCount}</span>
            </div>
          </div>
        </div>
      </Card>
    );
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" tip="加载团队成员..." /></div>;
  }

  return (
    <div style={{ padding: spacing[4] }}>
      {/* 顶部 */}
      <div style={{ marginBottom: spacing[4], display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/redteam/collaboration')}>返回</Button>
          <TeamOutlined style={{ fontSize: 24, color: colors.info }} />
          <Title level={4} style={{ margin: 0 }}>团队成员</Title>
          <Tag color="blue">共 {stats.total} 人</Tag>
        </Space>
        <Space>
          <Input
            placeholder="搜索成员姓名/角色"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            allowClear
            prefix={<SearchOutlined />}
            style={{ width: 240 }}
          />
          <Segmented
            options={[
              { label: '全部', value: 'all' },
              { label: '在线', value: 'online' },
              { label: '离线', value: 'offline' },
            ]}
            value={filter}
            onChange={(v) => setFilter(v as 'all' | 'online' | 'offline')}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => message.success('邀请成员...')}>邀请成员</Button>
        </Space>
      </div>

      {/* 概要统计 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="成员总数" value={stats.total} prefix={<TeamOutlined />} valueStyle={{ color: colors.info }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="在线" value={stats.online} prefix={<CheckCircleOutlined />} valueStyle={{ color: colors.success }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="离线" value={stats.offline} prefix={<ClockCircleOutlined />} valueStyle={{ color: colors.neutral[500] }} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="执行任务中" value={stats.busy} prefix={<FireOutlined />} valueStyle={{ color: colors.warning }} /></Card>
        </Col>
      </Row>

      {/* 图表 */}
      <Row gutter={16} style={{ marginBottom: spacing[4] }}>
        <Col xs={24} lg={8}>
          <Card size="small" title={<Space><TeamOutlined /> 角色分布</Space>}>
            <ReactECharts option={roleChartOption} style={{ height: 260, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card size="small" title={<Space><CheckCircleOutlined /> 在线状态分布</Space>}>
            <ReactECharts option={onlineChartOption} style={{ height: 260, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card size="small" title={<Space><ThunderboltOutlined /> 任务分配</Space>}>
            <ReactECharts option={taskAssignChartOption} style={{ height: 260, width: '100%' }} notMerge lazyUpdate />
          </Card>
        </Col>
      </Row>

      {/* 成员列表 */}
      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card size="small" title={<Space><TeamOutlined /> 成员列表 ({filtered.length})</Space>}>
            {filtered.length === 0 ? (
              <Empty description="无匹配成员" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              filtered.map(renderMemberCard)
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card size="small" title={<Space><FireOutlined /> 成员当前任务</Space>}>
            <List
              dataSource={members.filter((m) => m.currentTask)}
              renderItem={(m) => (
                <List.Item>
                  <List.Item.Meta
                    avatar={<Avatar icon={<UserOutlined />} src={m.avatar} />}
                    title={<Space><Text strong>{m.name}</Text><Tag color="blue">{m.role}</Tag></Space>}
                    description={<Space><FireOutlined style={{ color: colors.warning }} />{m.currentTask}</Space>}
                  />
                  <Button type="link" size="small" icon={<MessageOutlined />} onClick={() => message.success(`向 ${m.name} 发消息...`)}>私信</Button>
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      {/* 成员详情抽屉 */}
      <Drawer
        title="成员详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
      >
        {selected && (
          <div>
            <div style={{ textAlign: 'center', marginBottom: 16 }}>
              <Badge dot status={selected.online ? 'success' : 'default'} offset={[8, 64]}>
                <Avatar size={72} icon={<UserOutlined />} src={selected.avatar} style={{ backgroundColor: selected.online ? colors.info : colors.neutral[400] }} />
              </Badge>
              <Title level={4} style={{ marginTop: 12, marginBottom: 4 }}>{selected.name}</Title>
              <Tag color="blue">{selected.role}</Tag>
              <Tag color={selected.online ? 'success' : 'default'}>{selected.online ? '在线' : '离线'}</Tag>
            </div>
            <ProDescriptions
              column={1}
              bordered
              size="small"
              dataSource={{
                id: selected.id,
                name: selected.name,
                role: selected.role,
                online: selected.online ? '在线' : '离线',
                lastActive: formatDateTime(selected.lastActive),
                currentTask: selected.currentTask ?? '暂无任务',
              }}
              columns={[
                { title: '成员 ID', dataIndex: 'id', key: 'id' },
                { title: '姓名', dataIndex: 'name', key: 'name' },
                { title: '角色', dataIndex: 'role', key: 'role' },
                { title: '状态', dataIndex: 'online', key: 'online' },
                { title: '最后活跃', dataIndex: 'lastActive', key: 'lastActive' },
                { title: '当前任务', dataIndex: 'currentTask', key: 'currentTask' },
              ]}
            />
            <div style={{ marginTop: 16 }}>
              <Title level={5}>分配的任务</Title>
              <List
                size="small"
                dataSource={mockCollaborationTasks.filter((t) => t.assignee === selected.name)}
                renderItem={(task) => (
                  <List.Item>
                    <Space>
                      <Tag color="blue">{task.id}</Tag>
                      <Text strong>{task.title}</Text>
                    </Space>
                    <Tag>{task.status}</Tag>
                  </List.Item>
                )}
              />
            </div>
            <div style={{ marginTop: 16 }}>
              <Space>
                <Button type="primary" icon={<MessageOutlined />} onClick={() => message.success(`向 ${selected.name} 发消息...`)}>发消息</Button>
                <Button onClick={() => message.success('分配任务...')}>分配任务</Button>
              </Space>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default TeamPage;
