import { describe, expect, it } from 'vitest';
import type { CurrentUser } from '../api/auth';
import type { ClientCapabilities } from '../api/capability';
import { canAccessGrowthPoints, canAccessGrowthReviews, canAccessLearningTasks, canAccessRewards, canAccessStudentQrLogin } from './App';

const parent: CurrentUser = {
  userId: '1', sessionId: '2', username: 'parent', displayName: '家长',
  clientType: 'WEB', roleCodes: ['PARENT']
};

describe('Web 业务入口', () => {
  it('同时要求可用开关和业务角色', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true
    };
    expect(canAccessLearningTasks(parent, enabled)).toBe(true);
    expect(canAccessLearningTasks(parent, { ...enabled, learningTaskManagementEnabled: false })).toBe(false);
    expect(canAccessLearningTasks({ ...parent, roleCodes: ['STUDENT'] }, enabled)).toBe(false);
  });

  it('积分台账同时要求可用开关和家长角色', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true
    };
    expect(canAccessGrowthPoints(parent, enabled)).toBe(true);
    expect(canAccessGrowthPoints(parent, { ...enabled, growthPointQueryEnabled: false })).toBe(false);
    expect(canAccessGrowthPoints({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
  });

  it('奖励管理同时要求兑换开关和家长角色', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true
    };
    expect(canAccessRewards(parent, enabled)).toBe(true);
    expect(canAccessRewards(parent, { ...enabled, rewardExchangeEnabled: false })).toBe(false);
    expect(canAccessRewards({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
  });

  it('成长复盘要求至少一个复盘开关和家长角色', () => {
    const enabled = {
      client: 'WEB' as const, studentCodeLoginEnabled: false, studentQrLoginEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: false
    };
    expect(canAccessGrowthReviews(parent, enabled)).toBe(true);
    expect(canAccessGrowthReviews(parent, {
      ...enabled, dailyGrowthReviewEnabled: false
    })).toBe(false);
    expect(canAccessGrowthReviews({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
  });

  it('学生扫码登录只向家长和机构管理员开放', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true,
      learningTaskManagementEnabled: true, previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true, growthPointQueryEnabled: true,
      growthPointCorrectionEnabled: true, rewardExchangeEnabled: true,
      dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true
    };
    expect(canAccessStudentQrLogin(parent, enabled)).toBe(true);
    expect(canAccessStudentQrLogin({ ...parent, roleCodes: ['ORG_ADMIN'] }, enabled)).toBe(true);
    expect(canAccessStudentQrLogin({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
    expect(canAccessStudentQrLogin(parent, { ...enabled, studentQrLoginEnabled: false })).toBe(false);
  });
});
