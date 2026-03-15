/**
 * 工作台页面
 */

import React from 'react';
import { Card, Row, Col, Statistic, Typography } from 'antd';
import {
  FileTextOutlined,
  SearchOutlined,
  BarChartOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';

const { Title } = Typography;

const Dashboard: React.FC = () => {
  return (
    <div>
      <Title level={4}>工作台</Title>
      
      {/* 统计卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="文件总数"
              value={1234}
              prefix={<FileTextOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="检索次数"
              value={5678}
              prefix={<SearchOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="分析任务"
              value={89}
              prefix={<BarChartOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="威胁情报"
              value={456}
              prefix={<SafetyCertificateOutlined />}
              valueStyle={{ color: '#f5222d' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 快捷入口 */}
      <Card title="快捷入口" style={{ marginTop: 16 }}>
        <p>工作台内容区域，可添加快捷操作入口、最近文件、待办事项等</p>
      </Card>
    </div>
  );
};

export default Dashboard;
