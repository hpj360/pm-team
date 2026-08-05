/**
 * 登录页面
 * - 两阶段登录：用户名密码 → MFA 验证码
 * - admin / admin123（MFA 已启用，任意 6 位数字均可通过）
 * - analyst / analyst123（MFA 未启用，直接登录）
 */
import React, { useState } from 'react';
import { Form, Input, Button, Card, Typography, Steps, App } from 'antd';
import {
  UserOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  SafetyOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/hooks';
import type { LoginParams } from '@/types';
import styles from './Login.module.less';

const { Title, Text, Paragraph } = Typography;

type Stage = 'credentials' | 'mfa';

const Login: React.FC = () => {
  const { login, verifyMfa, cancelMfa, mfaToken } = useAuth();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [stage, setStage] = useState<Stage>('credentials');

  // 阶段 1：用户名密码登录
  const handleLogin = async (values: LoginParams) => {
    setLoading(true);
    try {
      const ok = await login(values);
      if (ok) {
        // 直接登录成功（无 MFA）
        return;
      }
      // 若服务端返回需要 MFA，useAuth 已设置 mfaPending
      // 同步本地阶段
      setStage('mfa');
    } finally {
      setLoading(false);
    }
  };

  // 阶段 2：MFA 验证
  const handleMfa = async (values: { code: string }) => {
    if (!mfaToken) {
      message.error('MFA 会话已失效，请重新登录');
      handleBack();
      return;
    }
    setLoading(true);
    try {
      await verifyMfa({ mfaToken, code: values.code });
    } finally {
      setLoading(false);
    }
  };

  // 返回到用户名密码阶段
  const handleBack = () => {
    cancelMfa();
    setStage('credentials');
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

        <Steps
          size="small"
          current={stage === 'credentials' ? 0 : 1}
          items={[{ title: '账号密码' }, { title: 'MFA 验证' }]}
          style={{ marginBottom: 24 }}
        />

        {stage === 'credentials' ? (
          <Form<LoginParams>
            name="login"
            onFinish={handleLogin}
            autoComplete="off"
            size="large"
            className={styles.form}
            initialValues={{ username: 'admin', password: 'admin123' }}
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
        ) : (
          <Form<{ code: string }>
            name="mfa"
            onFinish={handleMfa}
            autoComplete="off"
            size="large"
            className={styles.form}
          >
            <Form.Item
              name="code"
              rules={[
                { required: true, message: '请输入 MFA 验证码' },
                { pattern: /^\d{6}$/, message: '验证码为 6 位数字' },
              ]}
            >
              <Input
                prefix={<SafetyOutlined />}
                placeholder="请输入 6 位 MFA 验证码"
                maxLength={6}
                autoComplete="one-time-code"
                inputMode="numeric"
              />
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
              >
                验证
              </Button>
            </Form.Item>

            <Button
              type="link"
              icon={<ArrowLeftOutlined />}
              onClick={handleBack}
              block
            >
              返回重新登录
            </Button>
          </Form>
        )}

        <div className={styles.footer}>
          <Paragraph type="secondary" style={{ marginBottom: 4 }}>
            <Text strong>测试账号：</Text>
          </Paragraph>
          <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 12 }}>
            admin / admin123（启用 MFA，任意 6 位数字通过）
            <br />
            analyst / analyst123（未启用 MFA，直接登录）
          </Paragraph>
        </div>
      </Card>
    </div>
  );
};

export default Login;
