/**
 * 越权访问友好提示页
 * - antd Result status="403"
 * - 副标题："您没有权限访问该文件，请联系管理员申请权限"
 * - 提供"返回列表"按钮，点击后跳转到 /files
 *
 * 使用场景：文件详情页检测到用户 clearanceLevel 不足以访问该文件密级时展示
 */
import React from 'react';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

export interface ForbiddenTipProps {
  /** 自定义副标题，默认使用标准越权提示文案 */
  subTitle?: React.ReactNode;
  /** 返回按钮的目标路径，默认 /files */
  backPath?: string;
  /** 返回按钮文案，默认"返回列表" */
  backButtonText?: string;
}

const ForbiddenTip: React.FC<ForbiddenTipProps> = ({
  subTitle = '您没有权限访问该文件，请联系管理员申请权限',
  backPath = '/files',
  backButtonText = '返回列表',
}) => {
  const navigate = useNavigate();
  return (
    <div style={{ padding: 40 }} data-testid="forbidden-tip">
      <Result
        status="403"
        title="403"
        subTitle={subTitle}
        extra={
          <Button
            type="primary"
            onClick={() => navigate(backPath)}
            data-testid="forbidden-back-btn"
          >
            {backButtonText}
          </Button>
        }
      />
    </div>
  );
};

export default ForbiddenTip;
