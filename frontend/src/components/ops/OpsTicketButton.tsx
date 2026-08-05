/**
 * 运维工单申请按钮
 * - 用于触发高风险操作的工单申请流程
 * - 弹出表单收集工单信息，调用 createTicket
 * - 自动携带当前空间上下文、操作类型、影响预览
 */
import React, { useState } from 'react';
import { Button, Modal, Form, Input, Select, message, Typography, Space } from 'antd';
import { ScheduleOutlined } from '@ant-design/icons';
import { useCreateTicket } from '@/hooks/useOps';
import { useOpsStore } from '@/stores/ops';
import {
  TicketType,
  TicketTypeLabel,
} from '@/types/ops';

const { TextArea } = Input;
const { Text } = Typography;

export interface OpsTicketButtonProps {
  /** 工单类型 */
  ticketType: TicketType;
  /** 工单标题前缀（默认使用类型标签） */
  titlePrefix?: string;
  /** 目标空间 ID（不传则使用 store 中的当前空间） */
  teamSpaceId?: number;
  /** 目标空间名称 */
  teamSpaceName?: string;
  /** 目标引用（如 space:1 / config:9001） */
  targetRef?: string;
  /** 工单参数（自动序列化到 params 字段） */
  params?: Record<string, unknown>;
  /** 影响预览（自动序列化到 impact_preview 字段） */
  impactPreview?: Record<string, unknown>;
  /** 按钮文本，默认 "申请工单" */
  buttonText?: string;
  /** 按钮类型 */
  buttonType?: 'primary' | 'default' | 'dashed' | 'link' | 'text';
  /** 按钮尺寸 */
  buttonSize?: 'large' | 'middle' | 'small';
  /** 是否禁用 */
  disabled?: boolean;
  /** 子节点（覆盖默认按钮） */
  children?: React.ReactNode;
  /** 创建成功回调 */
  onSuccess?: (ticketId: number) => void;
}

const OpsTicketButton: React.FC<OpsTicketButtonProps> = ({
  ticketType,
  titlePrefix,
  teamSpaceId,
  teamSpaceName,
  targetRef = '',
  params = {},
  impactPreview = {},
  buttonText = '申请工单',
  buttonType = 'default',
  buttonSize = 'middle',
  disabled = false,
  children,
  onSuccess,
}) => {
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const createTicket = useCreateTicket();
  const storeSpaceId = useOpsStore((s) => s.currentSpaceId);

  const finalSpaceId = teamSpaceId ?? storeSpaceId ?? 0;

  const handleOpen = () => {
    form.resetFields();
    form.setFieldsValue({
      title: titlePrefix ? `${titlePrefix}` : `${TicketTypeLabel[ticketType]}申请`,
      description: '',
    });
    setOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const res = await createTicket.mutateAsync({
        ticket_type: ticketType,
        title: values.title,
        description: values.description,
        team_space_id: finalSpaceId,
        team_space_name: teamSpaceName ?? '',
        target_ref: targetRef,
        params,
        impact_preview: impactPreview,
        status: 1, // 待审批
      } as never);
      const ticketId = (res as { data?: { id?: number } })?.data?.id ?? 0;
      message.success(`工单已提交，编号 ${ticketId}`);
      setOpen(false);
      onSuccess?.(ticketId);
    } catch (err) {
      if (err instanceof Error) {
        message.error(err.message);
      }
    }
  };

  return (
    <>
      {children ? (
        <span onClick={handleOpen}>{children}</span>
      ) : (
        <Button
          type={buttonType}
          size={buttonSize}
          icon={<ScheduleOutlined />}
          onClick={handleOpen}
          disabled={disabled}
          aria-label={buttonText}
        >
          {buttonText}
        </Button>
      )}

      <Modal
        open={open}
        title={
          <Space>
            <ScheduleOutlined />
            <span>申请运维工单 · {TicketTypeLabel[ticketType]}</span>
          </Space>
        }
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={createTicket.isPending}
        okText="提交申请"
        cancelText="取消"
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="title"
            label="工单标题"
            rules={[{ required: true, message: '请输入工单标题' }]}
          >
            <Input maxLength={100} placeholder="请输入工单标题" />
          </Form.Item>
          <Form.Item
            name="description"
            label="申请说明"
            rules={[{ required: true, message: '请输入申请说明' }]}
          >
            <TextArea rows={4} maxLength={500} placeholder="请描述操作目的、影响范围、回滚方案" />
          </Form.Item>

          {finalSpaceId > 0 && (
            <Form.Item label="目标空间">
              <Text>{teamSpaceName ?? `空间 #${finalSpaceId}`}</Text>
            </Form.Item>
          )}

          {Object.keys(impactPreview).length > 0 && (
            <Form.Item label="影响预览">
              <pre
                style={{
                  background: '#fafafa',
                  border: '1px solid #f0f0f0',
                  padding: 8,
                  borderRadius: 4,
                  fontSize: 12,
                  margin: 0,
                  maxHeight: 160,
                  overflow: 'auto',
                }}
              >
                {JSON.stringify(impactPreview, null, 2)}
              </pre>
            </Form.Item>
          )}

          <Select style={{ display: 'none' } as React.CSSProperties} />
        </Form>
      </Modal>
    </>
  );
};

export default OpsTicketButton;
