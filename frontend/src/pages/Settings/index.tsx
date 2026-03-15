/**
 * 系统设置页面
 */

import React from 'react';
import { Card, Typography, Empty } from 'antd';

const { Title } = Typography;

const Settings: React.FC = () => {
  return (
    <div>
      <Title level={4}>系统设置</Title>
      
      <Card>
        <Empty description="系统设置功能开发中..." />
      </Card>
    </div>
  );
};

export default Settings;
