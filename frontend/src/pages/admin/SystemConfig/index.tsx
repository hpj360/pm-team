/**
 * 系统配置页面
 * 配置项表单（分 Tab：基础/安全/存储/检索）
 */
import React, { useEffect, useState } from 'react';
import {
  Card,
  Typography,
  Tabs,
  Form,
  Input,
  InputNumber,
  Switch,
  Select,
  Button,
  Row,
  Col,
  message,
  Spin,
  Space,
} from 'antd';
import {
  SettingOutlined,
  SafetyOutlined,
  DatabaseOutlined,
  SearchOutlined,
  SaveOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { getSystemConfigs, saveSystemConfigs } from '@/services';
import type { SystemConfigItem } from '@/types';
import { colors } from '@/styles/tokens';

const { Title, Text, Paragraph } = Typography;

/** 配置 Tab 分组 */
const configGroupLabel: Record<SystemConfigItem['group'], string> = {
  basic: '基础配置',
  security: '安全配置',
  storage: '存储配置',
  search: '检索配置',
};

const configGroupIcon: Record<SystemConfigItem['group'], React.ReactNode> = {
  basic: <SettingOutlined />,
  security: <SafetyOutlined />,
  storage: <DatabaseOutlined />,
  search: <SearchOutlined />,
};

/** 表单值类型 */
type FormValues = Record<string, string | number | boolean>;

const SystemConfigPage: React.FC = () => {
  const [items, setItems] = useState<SystemConfigItem[]>([]);
  const [form] = Form.useForm<FormValues>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = () => {
    setLoading(true);
    getSystemConfigs()
      .then((res) => {
        setItems(res.data);
        const values: FormValues = {};
        for (const item of res.data) {
          values[item.key] = item.value;
        }
        form.setFieldsValue(values);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  /** 保存 */
  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const updated = items.map((item) => ({ ...item, value: values[item.key] }));
      await saveSystemConfigs(updated);
      message.success('配置已保存');
      setItems(updated);
    } catch {
      // 校验失败
    } finally {
      setSaving(false);
    }
  };

  /** 渲染单项 */
  const renderItem = (item: SystemConfigItem) => {
    const label = (
      <Space direction="vertical" size={0}>
        <Text strong>{item.label}</Text>
        <Text type="secondary" style={{ fontSize: 11 }}>
          {item.description}
        </Text>
      </Space>
    );

    let control: React.ReactNode = null;
    switch (item.type) {
      case 'string':
        control = <Input placeholder={item.label} />;
        break;
      case 'number':
        control = <InputNumber style={{ width: '100%' }} />;
        break;
      case 'switch':
        control = <Switch />;
        break;
      case 'select':
        control = (
          <Select
            options={(item.options ?? []).map((o) => ({ label: o, value: o }))}
          />
        );
        break;
      default:
        control = <Input />;
    }

    return (
      <Form.Item key={item.key} name={item.key} label={label} valuePropName={item.type === 'switch' ? 'checked' : 'value'}>
        {control}
      </Form.Item>
    );
  };

  /** 按 group 分组 */
  const groups = (Object.keys(configGroupLabel) as Array<SystemConfigItem['group']>).map((g) => ({
    key: g,
    label: configGroupLabel[g],
    items: items.filter((i) => i.group === g),
  }));

  return (
    <div>
      <Title level={4}>系统配置</Title>
      <Card
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load}>
              重置
            </Button>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
              保存配置
            </Button>
          </Space>
        }
      >
        <Spin spinning={loading}>
          {items.length === 0 ? (
            <Paragraph type="secondary">加载中...</Paragraph>
          ) : (
            <Form form={form} layout="vertical" preserve>
              <Tabs
                defaultActiveKey="basic"
                items={groups.map((g) => ({
                  key: g.key,
                  label: (
                    <Space>
                      {configGroupIcon[g.key]}
                      <span>{g.label}</span>
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        ({g.items.length})
                      </Text>
                    </Space>
                  ),
                  children: (
                    <Row gutter={[24, 0]}>
                      {g.items.map((item) => (
                        <Col xs={24} md={12} lg={8} key={item.key}>
                          {renderItem(item)}
                        </Col>
                      ))}
                    </Row>
                  ),
                }))}
              />
              <div
                style={{
                  borderTop: `1px solid ${colors.neutral[200]}`,
                  paddingTop: 16,
                  marginTop: 16,
                  display: 'flex',
                  justifyContent: 'flex-end',
                }}
              >
                <Space>
                  <Button icon={<ReloadOutlined />} onClick={load}>
                    重置
                  </Button>
                  <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
                    保存配置
                  </Button>
                </Space>
              </div>
            </Form>
          )}
        </Spin>
      </Card>
    </div>
  );
};

export default SystemConfigPage;
