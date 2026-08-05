/**
 * 高风险操作确认弹窗
 * - 用于批量删除、空间销毁、配置回滚等高风险操作
 * - 强制二次确认 + 文字校验 + MFA 验证码（可选）
 * 对齐上游 §10.3 高风险操作二次确认要求
 */
import React, { useState, useEffect } from 'react';
import { Modal, Input, Typography, Space, Alert, Form, message } from 'antd';
import { ExclamationCircleOutlined, SafetyCertificateOutlined } from '@ant-design/icons';

const { Text, Paragraph } = Typography;

export interface HighRiskConfirmModalProps {
  /** 是否显示 */
  open: boolean;
  /** 标题 */
  title: string;
  /** 操作描述（说明影响） */
  description: React.ReactNode;
  /** 需要输入的确认文本（如空间名） */
  confirmText: string;
  /** 确认提示文案，默认 "请输入 $confirmText 以确认" */
  promptLabel?: string;
  /** 是否要求 MFA 验证码 */
  requireMfa?: boolean;
  /** 风险等级提示（如 "高"） */
  riskLevel?: 'low' | 'mid' | 'high';
  /** 影响预览数据，会以 JSON 形式展示 */
  impactPreview?: Record<string, unknown>;
  /** 取消回调 */
  onCancel: () => void;
  /** 确认回调，返回 Promise 表示异步操作 */
  onConfirm: () => Promise<void> | void;
}

const riskColor: Record<string, string> = {
  low: '#52c41a',
  mid: '#faad14',
  high: '#ff4d4f',
};

const riskLabel: Record<string, string> = {
  low: '低风险',
  mid: '中风险',
  high: '高风险',
};

const HighRiskConfirmModal: React.FC<HighRiskConfirmModalProps> = ({
  open,
  title,
  description,
  confirmText,
  promptLabel,
  requireMfa = false,
  riskLevel = 'high',
  impactPreview,
  onCancel,
  onConfirm,
}) => {
  const [inputValue, setInputValue] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setInputValue('');
      setMfaCode('');
      setSubmitting(false);
    }
  }, [open]);

  const textMatch = inputValue === confirmText;
  const mfaValid = !requireMfa || /^\d{6}$/.test(mfaCode);
  const canSubmit = textMatch && mfaValid && !submitting;

  const handleOk = async () => {
    if (!canSubmit) {
      if (!textMatch) message.warning(`请准确输入 "${confirmText}"`);
      if (!mfaValid) message.warning('请输入 6 位 MFA 验证码');
      return;
    }
    setSubmitting(true);
    try {
      await onConfirm();
      onCancel();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={
        <Space>
          <ExclamationCircleOutlined style={{ color: riskColor[riskLevel] }} />
          <span>{title}</span>
        </Space>
      }
      okText="确认执行"
      cancelText="取消"
      okButtonProps={{ danger: riskLevel === 'high', disabled: !canSubmit, loading: submitting }}
      onCancel={onCancel}
      onOk={handleOk}
      maskClosable={false}
      destroyOnClose
      width={520}
    >
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Alert
          type={riskLevel === 'high' ? 'error' : riskLevel === 'mid' ? 'warning' : 'info'}
          showIcon
          message={`${riskLabel[riskLevel]}操作`}
          description={description}
        />

        {impactPreview && (
          <div>
            <Text type="secondary">影响预览：</Text>
            <pre
              style={{
                background: '#fafafa',
                border: '1px solid #f0f0f0',
                padding: 8,
                borderRadius: 4,
                fontSize: 12,
                margin: '4px 0 0',
                maxHeight: 180,
                overflow: 'auto',
              }}
            >
              {JSON.stringify(impactPreview, null, 2)}
            </pre>
          </div>
        )}

        <div>
          <Paragraph style={{ marginBottom: 4 }}>
            {promptLabel ?? `请输入 "${confirmText}" 以确认执行`}
          </Paragraph>
          <Input
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder={confirmText}
            autoComplete="off"
            aria-label="确认文本输入"
          />
        </div>

        {requireMfa && (
          <Form.Item label={<><SafetyCertificateOutlined /> MFA 验证码</>} style={{ marginBottom: 0 }}>
            <Input
              value={mfaCode}
              onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              placeholder="6 位动态验证码"
              maxLength={6}
              autoComplete="one-time-code"
              aria-label="MFA 验证码"
            />
          </Form.Item>
        )}
      </Space>
    </Modal>
  );
};

export default HighRiskConfirmModal;
