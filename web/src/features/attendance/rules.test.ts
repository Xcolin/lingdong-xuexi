import { describe, expect, it } from 'vitest';
import { buildBatch, canAccessAttendance, canRecordAttendance, normalizeFilters } from './rules';
import type { CurrentUser } from '../../api/auth';

const user: CurrentUser = { userId: '1874244142494647001', sessionId: '2', username: 'test', displayName: '测试', clientType: 'WEB', roleCodes: ['CUSTOM'], permissionCodes: ['ATTENDANCE_READ', 'ATTENDANCE_RECORD'] };
const row = { studentId: '1874244142494647002', studentName: '小明', record: null };

describe('考勤权限、筛选和原子载荷', () => {
  it('自定义角色按动态权限进入，缺失能力和系统审核员失败关闭', () => {
    expect(canAccessAttendance(user, true)).toBe(true);
    expect(canAccessAttendance(user, undefined)).toBe(false);
    expect(canAccessAttendance({ ...user, permissionCodes: [] }, true)).toBe(false);
    expect(canAccessAttendance({ ...user, roleCodes: ['SYS_AUDITOR'] }, true)).toBe(false);
  });
  it.each(['PARENT', 'STUDENT'])('%s 即使误授写权限也只读', (role) => {
    expect(canAccessAttendance({ ...user, roleCodes: [role] }, true)).toBe(true);
    expect(canRecordAttendance({ ...user, roleCodes: [role] })).toBe(false);
  });
  it('写入依据权限而非固定业务角色', () => {
    expect(canRecordAttendance(user)).toBe(true);
    expect(canRecordAttendance({ ...user, permissionCodes: ['ATTENDANCE_READ'] })).toBe(false);
  });
  it('组合筛选保留字符串雪花标识并校验成对日期', () => {
    expect(normalizeFilters({ classOrganizationId: row.studentId, studentId: user.userId, keyword: ' 小明 ', status: 'LATE', dateFrom: '2026-09-01', dateTo: '2026-09-07' })).toEqual({ classOrganizationId: row.studentId, studentId: user.userId, keyword: '小明', status: 'LATE', dateFrom: '2026-09-01', dateTo: '2026-09-07' });
    expect(() => normalizeFilters({ dateFrom: '2026-09-01' })).toThrow('开始日期和结束日期');
    expect(() => normalizeFilters({ dateFrom: '2026-09-07', dateTo: '2026-09-01' })).toThrow('开始日期不得晚于结束日期');
  });
  it('未选择状态的学生不默认正常且不进入提交', () => {
    expect(() => buildBatch('班级', '2026-09-01', [row], {}, '2026-09-07')).toThrow('1 至 100');
    const result = buildBatch('班级', '2026-09-01', [row, { ...row, studentId: '其他' }], { [row.studentId]: { status: 'NORMAL', checkinTime: '08:01', checkoutTime: '' } }, '2026-09-07');
    expect(result.items).toEqual([{ studentId: row.studentId, status: 'NORMAL', checkinTime: '08:01:00', checkoutTime: null, versionNo: null }]);
  });
  it('保留更正版本且接受 100 人，拒绝 101 人和重复学生', () => {
    const rows = Array.from({ length: 100 }, (_, i) => ({ ...row, studentId: String(i) }));
    const drafts = Object.fromEntries(rows.map((item) => [item.studentId, { status: 'NORMAL' as const, checkinTime: '', checkoutTime: '' }]));
    expect(buildBatch('班级', '2026-09-01', rows, drafts, '2026-09-07').items).toHaveLength(100);
    expect(() => buildBatch('班级', '2026-09-01', [...rows, { ...row, studentId: '100' }], { ...drafts, '100': drafts['0'] }, '2026-09-07')).toThrow('1 至 100');
    expect(() => buildBatch('班级', '2026-09-01', [rows[0], rows[0]], drafts, '2026-09-07')).toThrow('重复');
    expect(buildBatch('班级', '2026-09-01', [{ ...rows[0], record: { versionNo: 7 } as never }], drafts, '2026-09-07').items[0].versionNo).toBe(7);
  });
  it('拒绝未来日期、缺勤带时间和倒序时间，不拆分请求', () => {
    const drafts = { [row.studentId]: { status: 'ABSENT' as const, checkinTime: '08:00:00', checkoutTime: '' } };
    expect(() => buildBatch('班级', '2026-09-08', [row], drafts, '2026-09-07')).toThrow('未来');
    expect(() => buildBatch('班级', '2026-09-01', [row], drafts, '2026-09-07')).toThrow('时间必须为空');
    expect(() => buildBatch('班级', '2026-09-01', [row], { [row.studentId]: { status: 'NORMAL', checkinTime: '09:00', checkoutTime: '08:00' } }, '2026-09-07')).toThrow('签到时间不得晚于签退时间');
  });
});
