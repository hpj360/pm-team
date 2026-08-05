/**
 * 单元测试：幂等助手（src/components/ops/IdempotencyHelper.ts）
 * - 幂等头生成
 * - 时间戳新鲜度校验
 */
import { describe, it, expect } from 'vitest';
import { generateIdempotencyHeaders, isTimestampFresh } from '@/components/ops/IdempotencyHelper';

describe('IdempotencyHelper', () => {
  describe('generateIdempotencyHeaders', () => {
    it('返回包含三个必要字段的对象', () => {
      const headers = generateIdempotencyHeaders();
      expect(headers).toHaveProperty('Idempotency-Key');
      expect(headers).toHaveProperty('X-Request-Nonce');
      expect(headers).toHaveProperty('X-Request-Timestamp');
    });

    it('Idempotency-Key 由时间戳与 nonce 组合', () => {
      const headers = generateIdempotencyHeaders();
      const [ts, nonce] = headers['Idempotency-Key'].split('-');
      expect(ts).toBe(headers['X-Request-Timestamp']);
      expect(nonce).toBe(headers['X-Request-Nonce']);
    });

    it('X-Request-Timestamp 为数字字符串', () => {
      const headers = generateIdempotencyHeaders();
      const ts = parseInt(headers['X-Request-Timestamp'], 10);
      expect(Number.isNaN(ts)).toBe(false);
      expect(ts).toBeGreaterThan(0);
    });

    it('X-Request-Nonce 长度为 10', () => {
      const headers = generateIdempotencyHeaders();
      expect(headers['X-Request-Nonce'].length).toBe(10);
    });

    it('多次调用应产生不同结果', () => {
      const h1 = generateIdempotencyHeaders();
      const h2 = generateIdempotencyHeaders();
      expect(h1['Idempotency-Key']).not.toBe(h2['Idempotency-Key']);
    });
  });

  describe('isTimestampFresh', () => {
    it('当前时间戳应判为新鲜', () => {
      const ts = Date.now().toString();
      expect(isTimestampFresh(ts)).toBe(true);
    });

    it('5 分钟内的时间戳应判为新鲜', () => {
      const ts = (Date.now() - 4 * 60 * 1000).toString();
      expect(isTimestampFresh(ts)).toBe(true);
    });

    it('超过 5 分钟的时间戳应判为过期', () => {
      const ts = (Date.now() - 10 * 60 * 1000).toString();
      expect(isTimestampFresh(ts)).toBe(false);
    });

    it('非数字时间戳应判为过期', () => {
      expect(isTimestampFresh('not-a-number')).toBe(false);
    });

    it('空字符串应判为过期', () => {
      expect(isTimestampFresh('')).toBe(false);
    });

    it('自定义窗口：1 秒内新鲜', () => {
      const ts = (Date.now() - 500).toString();
      expect(isTimestampFresh(ts, 1000)).toBe(true);
    });

    it('自定义窗口：超过 1 秒过期', () => {
      const ts = (Date.now() - 2000).toString();
      expect(isTimestampFresh(ts, 1000)).toBe(false);
    });
  });
});
