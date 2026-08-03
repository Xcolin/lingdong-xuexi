import { describe, expect, it } from 'vitest';
import type { CurrentUser } from '../api/auth';
import type { ClientCapabilities } from '../api/capability';
import { canAccessLearningTasks } from './App';

const parent: CurrentUser = {
  userId: '1', sessionId: '2', username: 'parent', displayName: '家长',
  clientType: 'WEB', roleCodes: ['PARENT']
};

describe('Web 学习任务入口', () => {
  it('同时要求可用开关和业务角色', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, learningTaskManagementEnabled: true
    };
    expect(canAccessLearningTasks(parent, enabled)).toBe(true);
    expect(canAccessLearningTasks(parent, { ...enabled, learningTaskManagementEnabled: false })).toBe(false);
    expect(canAccessLearningTasks({ ...parent, roleCodes: ['STUDENT'] }, enabled)).toBe(false);
  });
});
