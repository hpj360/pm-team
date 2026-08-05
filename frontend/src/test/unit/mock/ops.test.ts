/**
 * 单元测试：Mock 数据（src/mock/ops.ts）
 * - 数据完整性
 * - 分页过滤
 * - 按空间过滤
 */
import { describe, it, expect } from 'vitest';
import {
  mockSpaces,
  mockConsistencyChecks,
  mockConsistencyDiffs,
  mockHealJobs,
  mockLifecyclePolicies,
  mockConfigItems,
  mockStalePermissions,
  mockReports,
  mockTickets,
  getMockSpaces,
  getMockConsistencyChecks,
  getMockConsistencyDiffs,
  getMockHealJobs,
  getMockTickets,
  findMockSpace,
  findMockReport,
  findMockHealJob,
} from '@/mock/ops';

describe('应用运维 Mock 数据', () => {
  describe('数据完整性', () => {
    it('mockSpaces 至少 5 个', () => {
      expect(mockSpaces.length).toBeGreaterThanOrEqual(5);
    });

    it('mockSpaces 字段完整', () => {
      const s = mockSpaces[0];
      expect(s).toHaveProperty('id');
      expect(s).toHaveProperty('code');
      expect(s).toHaveProperty('name');
      expect(s).toHaveProperty('health_score');
      expect(s).toHaveProperty('lifecycle_status');
      expect(typeof s.storage_used).toBe('number');
      expect(s.storage_used).toBeGreaterThan(0);
    });

    it('mockConsistencyChecks 覆盖多种检查类型', () => {
      const types = new Set(mockConsistencyChecks.map((c) => c.check_type));
      expect(types.size).toBeGreaterThanOrEqual(5);
      expect(types.has('PG_MINIO')).toBe(true);
      expect(types.has('PG_ES')).toBe(true);
    });

    it('mockConsistencyDiffs 关联到 check_id', () => {
      mockConsistencyDiffs.forEach((d) => {
        expect(mockConsistencyChecks.some((c) => c.id === d.check_id)).toBe(true);
      });
    });

    it('mockHealJobs 包含多种状态', () => {
      const statuses = new Set(mockHealJobs.map((j) => j.status));
      expect(statuses.size).toBeGreaterThanOrEqual(2);
    });

    it('mockLifecyclePolicies 包含全局与空间级', () => {
      const hasGlobal = mockLifecyclePolicies.some((p) => p.team_space_id === null);
      const hasSpace = mockLifecyclePolicies.some((p) => p.team_space_id !== null);
      expect(hasGlobal).toBe(true);
      expect(hasSpace).toBe(true);
    });

    it('mockConfigItems 覆盖主要配置类型', () => {
      const types = new Set(mockConfigItems.map((c) => c.config_type));
      expect(types.size).toBeGreaterThanOrEqual(5);
    });

    it('mockStalePermissions 覆盖三种异常类型', () => {
      const types = new Set(mockStalePermissions.map((p) => p.stale_type));
      expect(types.has('RESIGNED_MEMBER')).toBe(true);
      expect(types.has('EXPIRED_LINK')).toBe(true);
      expect(types.has('OVER_PRIVILEGE')).toBe(true);
    });

    it('mockTickets 覆盖多种工单类型', () => {
      const types = new Set(mockTickets.map((t) => t.ticket_type));
      expect(types.size).toBeGreaterThanOrEqual(4);
    });

    it('mockReports 包含周报/月报/异常通报', () => {
      const types = new Set(mockReports.map((r) => r.report_type));
      expect(types.has('WEEKLY')).toBe(true);
      expect(types.has('MONTHLY')).toBe(true);
      expect(types.has('ALERT')).toBe(true);
    });
  });

  describe('分页查询', () => {
    it('getMockSpaces 默认第 1 页 10 条', () => {
      const r = getMockSpaces({ page: 1, pageSize: 10 });
      expect(r.page).toBe(1);
      expect(r.pageSize).toBe(10);
      expect(r.total).toBe(mockSpaces.length);
      expect(r.list.length).toBe(Math.min(10, mockSpaces.length));
    });

    it('getMockSpaces 第 2 页（不足则返回空列表）', () => {
      const r = getMockSpaces({ page: 2, pageSize: 10 });
      expect(r.page).toBe(2);
      expect(r.total).toBe(mockSpaces.length);
      // mockSpaces 不足 10 个，第 2 页应为空
      if (mockSpaces.length <= 10) {
        expect(r.list.length).toBe(0);
      }
    });

    it('getMockSpaces 按名称搜索', () => {
      const r = getMockSpaces({ page: 1, pageSize: 10, q: '红方' });
      r.list.forEach((s) => {
        expect(s.name.includes('红方') || s.code.toLowerCase().includes('红方')).toBe(true);
      });
    });

    it('getMockSpaces 按 team_space_id 过滤', () => {
      const r = getMockSpaces({ page: 1, pageSize: 10, team_space_id: 1 });
      expect(r.list.every((s) => s.id === 1)).toBe(true);
    });
  });

  describe('一致性对账查询', () => {
    it('getMockConsistencyChecks 返回全部', () => {
      const r = getMockConsistencyChecks({ page: 1, pageSize: 100 });
      expect(r.total).toBe(mockConsistencyChecks.length);
    });

    it('getMockConsistencyDiffs 按 check_id 过滤', () => {
      const checkId = mockConsistencyDiffs[0].check_id;
      const r = getMockConsistencyDiffs(checkId, { page: 1, pageSize: 100 });
      expect(r.list.every((d) => d.check_id === checkId)).toBe(true);
    });
  });

  describe('治愈任务查询', () => {
    it('getMockHealJobs 按 team_space_id 过滤', () => {
      const sid = mockHealJobs[0].team_space_id;
      const r = getMockHealJobs({ page: 1, pageSize: 100, team_space_id: sid });
      expect(r.list.every((j) => j.team_space_id === sid)).toBe(true);
    });
  });

  describe('工单查询', () => {
    it('getMockTickets 按标题搜索', () => {
      const r = getMockTickets({ page: 1, pageSize: 100, q: '配额' });
      r.list.forEach((t) => {
        expect(t.title.includes('配额') || t.ticket_no.toLowerCase().includes('配额')).toBe(true);
      });
    });
  });

  describe('查找函数', () => {
    it('findMockSpace 返回存在的空间', () => {
      const s = findMockSpace(1);
      expect(s).toBeDefined();
      expect(s?.id).toBe(1);
    });

    it('findMockSpace 不存在返回 undefined', () => {
      expect(findMockSpace(99999)).toBeUndefined();
    });

    it('findMockReport 返回存在的报告', () => {
      const r = findMockReport(1);
      expect(r).toBeDefined();
      expect(r?.id).toBe(1);
    });

    it('findMockHealJob 返回存在的治愈任务', () => {
      const j = findMockHealJob(1);
      expect(j).toBeDefined();
      expect(j?.id).toBe(1);
    });
  });
});
