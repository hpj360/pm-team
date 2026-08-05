/**
 * Mock 数据 - 系统配置
 */
import type { SystemConfigItem } from '@/types';

export const mockSystemConfigs: SystemConfigItem[] = [
  // 基础配置
  { key: 'site_name', label: '站点名称', value: '红方文件汇聚平台', type: 'string', group: 'basic', description: '展示在登录页与浏览器标题' },
  { key: 'site_url', label: '站点 URL', value: 'https://redteam.local', type: 'string', group: 'basic', description: '系统对外访问地址' },
  { key: 'session_timeout', label: '会话超时（分钟）', value: 30, type: 'number', group: 'basic', description: '用户空闲多久后自动登出' },
  { key: 'language', label: '默认语言', value: 'zh-CN', type: 'select', options: ['zh-CN', 'en-US'], group: 'basic', description: '默认 UI 语言' },

  // 安全配置
  { key: 'mfa_required', label: '强制 MFA', value: true, type: 'switch', group: 'security', description: '所有用户必须启用多因子认证' },
  { key: 'password_min_length', label: '密码最小长度', value: 12, type: 'number', group: 'security', description: '用户密码最小长度' },
  { key: 'password_complexity', label: '密码复杂度策略', value: 'high', type: 'select', options: ['low', 'medium', 'high'], group: 'security', description: '密码强度要求' },
  { key: 'ip_whitelist', label: '登录 IP 白名单', value: '10.0.0.0/8', type: 'string', group: 'security', description: '允许登录的 IP 段，逗号分隔' },
  { key: 'audit_log_retention', label: '审计日志保留（天）', value: 180, type: 'number', group: 'security', description: '审计日志保留时长' },

  // 存储配置
  { key: 'storage_backend', label: '存储后端', value: 'minio', type: 'select', options: ['minio', 'oss', 's3'], group: 'storage', description: '文件存储后端类型' },
  { key: 'storage_bucket', label: '默认 Bucket', value: 'redteam-files', type: 'string', group: 'storage', description: '默认存储桶名称' },
  { key: 'max_upload_size_mb', label: '最大上传大小（MB）', value: 2048, type: 'number', group: 'storage', description: '单文件最大上传大小' },
  { key: 'auto_parse', label: '上传后自动解析', value: true, type: 'switch', group: 'storage', description: '文件上传后是否自动触发解析' },

  // 检索配置
  { key: 'search_engine', label: '搜索引擎', value: 'elasticsearch', type: 'select', options: ['elasticsearch', 'milvus', 'hybrid'], group: 'search', description: '检索引擎类型' },
  { key: 'vector_dim', label: '向量维度', value: 768, type: 'number', group: 'search', description: '向量索引维度' },
  { key: 'enable_ner', label: '启用 NER 实体识别', value: true, type: 'switch', group: 'search', description: '检索时是否启用实体识别' },
  { key: 'snippet_length', label: '片段长度', value: 200, type: 'number', group: 'search', description: '检索结果高亮片段字符数' },
];

export default { mockSystemConfigs };
