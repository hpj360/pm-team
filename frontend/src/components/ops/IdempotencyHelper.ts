/**
 * 幂等助手
 * - 生成幂等头（Idempotency-Key / X-Request-Nonce / X-Request-Timestamp）
 * - 防止高风险操作重复提交
 * - 服务层（services/ops.ts）已在所有 mutation API 中默认使用
 *   此处仅提供工具函数供页面层在自定义请求场景下使用
 * 对齐上游 §9.5 幂等性设计
 */

/** 生成幂等头 */
export function generateIdempotencyHeaders(): Record<string, string> {
  const ts = Date.now().toString();
  const nonce = Math.random().toString(36).slice(2, 12);
  const key = `${ts}-${nonce}`;
  return {
    'Idempotency-Key': key,
    'X-Request-Nonce': nonce,
    'X-Request-Timestamp': ts,
  };
}

/** 校验时间戳新鲜度（5 分钟内有效） */
export function isTimestampFresh(timestamp: string, windowMs: number = 5 * 60 * 1000): boolean {
  const ts = parseInt(timestamp, 10);
  if (Number.isNaN(ts)) return false;
  return Math.abs(Date.now() - ts) <= windowMs;
}

/** 默认导出对象（便于按需使用） */
export default {
  generateIdempotencyHeaders,
  isTimestampFresh,
};
