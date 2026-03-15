/**
 * 威胁情报中心页面
 */

import React from 'react';
import { Card, Typography, Empty } from 'antd';

const { Title } = Typography;

const IocCenter: React.FC = () => {
  return (
    <div>
      <Title level={4}>威胁情报中心</Title>
      
      <Card>
        <Empty description="威胁情报中心功能开发中..." />
      </Card>
    </div>
  );
};

export default IocCenter;
