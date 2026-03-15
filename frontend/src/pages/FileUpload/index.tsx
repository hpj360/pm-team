/**
 * 文件上传页面
 */

import React, { useState } from 'react';
import {
  Card,
  Upload,
  Button,
  message,
  Input,
  Select,
  Typography,
  Space,
  Tag,
  Progress,
} from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  InboxOutlined,
  CloudUploadOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { useFile } from '@/hooks';

const { Title, Text } = Typography;
const { Dragger } = Upload;

const FileUpload: React.FC = () => {
  const { uploadFile } = useFile();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [tags, setTags] = useState<string[]>([]);
  const [description, setDescription] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<Record<string, number>>({});

  // 上传配置
  const uploadProps = {
    name: 'file',
    multiple: true,
    fileList,
    beforeUpload: (file: File) => {
      // 检查文件大小 (最大100MB)
      const isLt100M = file.size / 1024 / 1024 < 100;
      if (!isLt100M) {
        message.error('文件大小不能超过100MB');
        return false;
      }
      
      setFileList((prev) => [
        ...prev,
        {
          uid: file.name + Date.now(),
          name: file.name,
          status: 'done',
          originFileObj: file,
        } as UploadFile,
      ]);
      
      return false; // 阻止自动上传
    },
    onRemove: (file: UploadFile) => {
      const index = fileList.indexOf(file);
      const newFileList = fileList.slice();
      newFileList.splice(index, 1);
      setFileList(newFileList);
    },
  };

  // 手动上传
  const handleUpload = async () => {
    if (fileList.length === 0) {
      message.warning('请选择要上传的文件');
      return;
    }

    setUploading(true);
    let successCount = 0;
    let failCount = 0;

    for (const file of fileList) {
      if (file.originFileObj) {
        try {
          await uploadFile({
            file: file.originFileObj,
            tags,
            description,
            onProgress: (percent) => {
              setUploadProgress((prev) => ({
                ...prev,
                [file.uid]: percent,
              }));
            },
          });
          successCount++;
        } catch {
          failCount++;
        }
      }
    }

    setUploading(false);
    
    if (successCount > 0) {
      message.success(`成功上传 ${successCount} 个文件`);
      setFileList([]);
      setUploadProgress({});
    }
    if (failCount > 0) {
      message.error(`${failCount} 个文件上传失败`);
    }
  };

  // 标签输入
  const [tagInput, setTagInput] = useState('');
  const handleAddTag = () => {
    if (tagInput && !tags.includes(tagInput)) {
      setTags([...tags, tagInput]);
      setTagInput('');
    }
  };

  return (
    <div>
      <Title level={4}>文件上传</Title>

      <Card>
        {/* 拖拽上传区域 */}
        <Dragger {...uploadProps}>
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p className="ant-upload-hint">
            支持单个或批量上传，文件大小不超过100MB
          </p>
        </Dragger>

        {/* 文件信息设置 */}
        <Space direction="vertical" style={{ width: '100%', marginTop: 24 }}>
          <div>
            <Text strong>标签设置</Text>
            <div style={{ marginTop: 8 }}>
              <Space wrap>
                {tags.map((tag) => (
                  <Tag
                    key={tag}
                    closable
                    onClose={() => setTags(tags.filter((t) => t !== tag))}
                  >
                    {tag}
                  </Tag>
                ))}
                <Input
                  placeholder="输入标签"
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  onPressEnter={handleAddTag}
                  style={{ width: 120 }}
                />
                <Button size="small" icon={<PlusOutlined />} onClick={handleAddTag}>
                  添加
                </Button>
              </Space>
            </div>
          </div>

          <div>
            <Text strong>描述信息</Text>
            <Input.TextArea
              placeholder="请输入文件描述"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              style={{ marginTop: 8 }}
            />
          </div>
        </Space>

        {/* 上传进度 */}
        {uploading && (
          <div style={{ marginTop: 24 }}>
            <Text strong>上传进度</Text>
            {fileList.map((file) => (
              <div key={file.uid} style={{ marginTop: 8 }}>
                <Text>{file.name}</Text>
                <Progress percent={uploadProgress[file.uid] || 0} />
              </div>
            ))}
          </div>
        )}

        {/* 操作按钮 */}
        <div style={{ marginTop: 24, textAlign: 'center' }}>
          <Button
            type="primary"
            size="large"
            icon={<CloudUploadOutlined />}
            loading={uploading}
            onClick={handleUpload}
            disabled={fileList.length === 0}
          >
            开始上传 ({fileList.length} 个文件)
          </Button>
        </div>
      </Card>
    </div>
  );
};

export default FileUpload;
