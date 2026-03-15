/**
 * 登录页面
 */

import React, { useState } from 'react';
import { Form, Input, Button, Card, Typography, message } from 'antd';
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useAuth } from '@/hooks';
import type { LoginParams } from '@/types';
import styles from './Login.module.less';

const { Title, Text } = Typography;

const Login: React.FC = () => {
  const { login } = useAuth();
  const [loading, setLoading] = useState(false);

  // 表单提交
  const handleSubmit = async (values: LoginParams) => {
    setLoading(true);
    try {
      await login(values);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.background} />
      
      <Card className={styles.loginCard}>
        <div className={styles.header}>
          <SafetyCertificateOutlined className={styles.logo} />
          <Title level={3} style={{ margin: 0 }}>
            网络安全红方文件汇聚平台
          </Title>
          <Text type="secondary">Red Team File Aggregation Platform</Text>
        </div>

        <Form
          name="login"
          onFinish={handleSubmit}
          autoComplete="off"
          size="large"
          className={styles.form}
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名"
              autoComplete="username"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
            >
              登录
            </Button>
          </Form.Item>
        </Form>

        <div className={styles.footer}>
          <Text type="secondary">
            默认账号: admin / admin123
          </Text>
        </div>
      </Card>
    </div>
  );
};

export default Login;
