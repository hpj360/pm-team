/**
 * 单元测试：类型定义（src/types/ops.ts）
 * - 状态标签映射完整性
 * - 工单类型/状态映射
 * - 配置变更状态映射
 */
import { describe, it, expect } from 'vitest';
import {
  SpaceLifecycleLabel,
  SpaceLifecycleTag,
  SpaceMemberRoleLabel,
  CheckTypeLabel,
  CheckStatusTag,
  DiffActionLabel,
  DiffStatusTag,
  HealJobTypeLabel,
  BATCH_HEAL_TYPES,
  HealJobStatusTag,
  StorageTierLabel,
  ConfigTypeLabel,
  ALL_CONFIG_TYPES,
  ConfigChangeStatusTag,
  StaleTypeLabel,
  ExportStatusTag,
  ReportTypeLabel,
  ALL_REPORT_TYPES,
  TicketTypeLabel,
  TicketStatusTag,
  ALL_CHECK_TYPES,
} from '@/types/ops';
import type {
  SpaceLifecycleStatus,
  SpaceMemberRole,
  CheckStatus,
  DiffSuggestedAction,
  DiffStatus,
  HealJobType,
  HealJobStatus,
  StorageTier,
  ConfigType,
  ConfigChangeStatus,
  StaleType,
  ExportStatus,
  ReportType,
  TicketType,
  TicketStatus,
} from '@/types/ops';

describe('应用运维类型定义', () => {
  describe('D1 空间台账', () => {
    it('SpaceLifecycleLabel 覆盖所有状态', () => {
      const statuses: SpaceLifecycleStatus[] = ['active', 'frozen', 'archived', 'destroyed', 'partial_destroyed'];
      statuses.forEach((s) => {
        expect(SpaceLifecycleLabel[s]).toBeDefined();
        expect(typeof SpaceLifecycleLabel[s]).toBe('string');
      });
    });

    it('SpaceLifecycleTag 包含 color 和 text', () => {
      Object.entries(SpaceLifecycleTag).forEach(([, v]) => {
        expect(v).toHaveProperty('color');
        expect(v).toHaveProperty('text');
        expect(typeof v.color).toBe('string');
        expect(typeof v.text).toBe('string');
      });
    });

    it('SpaceMemberRoleLabel 覆盖三种角色', () => {
      const roles: SpaceMemberRole[] = ['OWNER', 'MAINTAINER', 'VIEWER'];
      roles.forEach((r) => {
        expect(SpaceMemberRoleLabel[r]).toBeDefined();
      });
    });
  });

  describe('D2 一致性对账', () => {
    it('ALL_CHECK_TYPES 与 CheckTypeLabel 一致', () => {
      ALL_CHECK_TYPES.forEach((t) => {
        expect(CheckTypeLabel[t]).toBeDefined();
      });
    });

    it('CheckStatusTag 覆盖 4 种状态', () => {
      const statuses: CheckStatus[] = [0, 1, 2, 3];
      statuses.forEach((s) => {
        expect(CheckStatusTag[s]).toBeDefined();
        expect(CheckStatusTag[s]).toHaveProperty('color');
        expect(CheckStatusTag[s]).toHaveProperty('text');
      });
    });

    it('DiffActionLabel 覆盖 4 种建议动作', () => {
      const actions: DiffSuggestedAction[] = ['REINDEX', 'REPARSE', 'PURGE_ORPHAN', 'MANUAL'];
      actions.forEach((a) => {
        expect(DiffActionLabel[a]).toBeDefined();
      });
    });

    it('DiffStatusTag 覆盖 3 种状态', () => {
      const statuses: DiffStatus[] = [0, 1, 2];
      statuses.forEach((s) => {
        expect(DiffStatusTag[s]).toBeDefined();
      });
    });
  });

  describe('D3 链路治愈', () => {
    it('HealJobTypeLabel 覆盖所有治愈类型', () => {
      const types: HealJobType[] = ['RETRY_INDEX', 'RETRY_PARSE', 'REBUILD_GRAPH', 'REBUILD_VECTOR', 'PURGE_ORPHAN', 'DELETE_FILE', 'FIX_TRACE'];
      types.forEach((t) => {
        expect(HealJobTypeLabel[t]).toBeDefined();
      });
    });

    it('BATCH_HEAL_TYPES 不包含免审批类型', () => {
      expect(BATCH_HEAL_TYPES).not.toContain('RETRY_INDEX');
      expect(BATCH_HEAL_TYPES).not.toContain('FIX_TRACE');
      expect(BATCH_HEAL_TYPES.length).toBeGreaterThan(0);
    });

    it('HealJobStatusTag 覆盖 6 种状态', () => {
      const statuses: HealJobStatus[] = [0, 1, 2, 3, 4, 5];
      statuses.forEach((s) => {
        expect(HealJobStatusTag[s]).toBeDefined();
      });
    });
  });

  describe('D4 生命周期', () => {
    it('StorageTierLabel 覆盖三种存储层级', () => {
      const tiers: StorageTier[] = ['hot', 'cold', 'archived'];
      tiers.forEach((t) => {
        expect(StorageTierLabel[t]).toBeDefined();
      });
    });
  });

  describe('D5 应用配置', () => {
    it('ALL_CONFIG_TYPES 与 ConfigTypeLabel 一致', () => {
      ALL_CONFIG_TYPES.forEach((t) => {
        expect(ConfigTypeLabel[t as ConfigType]).toBeDefined();
      });
    });

    it('ConfigChangeStatusTag 覆盖 6 种状态', () => {
      const statuses: ConfigChangeStatus[] = [0, 1, 2, 3, 4, 5];
      statuses.forEach((s) => {
        expect(ConfigChangeStatusTag[s]).toBeDefined();
      });
    });
  });

  describe('D6 数据安全', () => {
    it('StaleTypeLabel 覆盖三种异常类型', () => {
      const types: StaleType[] = ['RESIGNED_MEMBER', 'EXPIRED_LINK', 'OVER_PRIVILEGE'];
      types.forEach((t) => {
        expect(StaleTypeLabel[t]).toBeDefined();
      });
    });

    it('ExportStatusTag 覆盖 6 种状态', () => {
      const statuses: ExportStatus[] = [0, 1, 2, 3, 4, 5];
      statuses.forEach((s) => {
        expect(ExportStatusTag[s]).toBeDefined();
      });
    });
  });

  describe('D7 空间报告', () => {
    it('ALL_REPORT_TYPES 与 ReportTypeLabel 一致', () => {
      ALL_REPORT_TYPES.forEach((t) => {
        expect(ReportTypeLabel[t as ReportType]).toBeDefined();
      });
    });
  });

  describe('运维工单', () => {
    it('TicketTypeLabel 覆盖 9 种工单类型', () => {
      const types: TicketType[] = ['QUOTA', 'DESTROY', 'DELETE', 'REPARSE', 'REINDEX', 'REBUILD_GRAPH', 'EXPORT', 'CONFIG', 'ARCHIVE'];
      types.forEach((t) => {
        expect(TicketTypeLabel[t]).toBeDefined();
      });
    });

    it('TicketStatusTag 覆盖 8 种状态', () => {
      const statuses: TicketStatus[] = [0, 1, 2, 3, 4, 5, 6, 7];
      statuses.forEach((s) => {
        expect(TicketStatusTag[s]).toBeDefined();
        expect(TicketStatusTag[s]).toHaveProperty('color');
        expect(TicketStatusTag[s]).toHaveProperty('text');
      });
    });
  });
});
