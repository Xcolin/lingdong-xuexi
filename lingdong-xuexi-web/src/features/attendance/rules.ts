import type { CurrentUser } from '../../api/auth';
import type { AttendanceBatch, AttendanceQuery, AttendanceRosterRow, AttendanceStatus } from './api';

export const statusLabels: Record<AttendanceStatus, string> = {
  NORMAL: '正常', LATE: '迟到', EARLY_LEAVE: '早退', ABSENT: '缺勤', LEAVE: '请假'
};
export interface AttendanceDraft { status?: AttendanceStatus; checkinTime: string; checkoutTime: string; }
export function canAccessAttendance(user: CurrentUser, enabled?: boolean): boolean {
  return enabled === true && !user.roleCodes.includes('SYS_AUDITOR') && user.permissionCodes.includes('ATTENDANCE_READ');
}
export function canRecordAttendance(user: CurrentUser): boolean {
  const organizationIdentity = user.roleCodes.some((role) => ['ORG_ADMIN', 'TEACHER'].includes(role));
  return !user.roleCodes.includes('SYS_AUDITOR')
    && (organizationIdentity || !user.roleCodes.some((role) => ['PARENT', 'STUDENT'].includes(role)))
    && user.permissionCodes.includes('ATTENDANCE_RECORD');
}
export function shanghaiToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  return ['year', 'month', 'day'].map((type) => parts.find((part) => part.type === type)?.value).join('-');
}
function validDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
}
export function normalizeFilters(query: AttendanceQuery): AttendanceQuery {
  if (Boolean(query.dateFrom) !== Boolean(query.dateTo)) throw new Error('请同时填写开始日期和结束日期');
  if (query.dateFrom && query.dateTo) {
    if (!validDate(query.dateFrom) || !validDate(query.dateTo)) throw new Error('日期格式不正确');
    if (query.dateFrom > query.dateTo) throw new Error('开始日期不得晚于结束日期');
  }
  return { ...query, keyword: query.keyword?.trim() || undefined, studentId: query.studentId?.trim() || undefined };
}
function normalizeTime(value: string): string | null {
  if (!value) return null;
  if (!/^([01]\d|2[0-3]):[0-5]\d(:00)?$/.test(value)) throw new Error('请按小时和分钟填写时间');
  return value.length === 5 ? `${value}:00` : value;
}
export function buildBatch(classOrganizationId: string, attendanceDate: string, roster: AttendanceRosterRow[], drafts: Record<string, AttendanceDraft>, today = shanghaiToday()): AttendanceBatch {
  if (!classOrganizationId || !validDate(attendanceDate)) throw new Error('请选择有效班级和考勤日期');
  if (attendanceDate > today) throw new Error('不能登记未来日期的考勤');
  // 只有明确选择状态的学生才进入这一笔原子请求，未登记不等于正常或缺勤。
  const selected = roster.filter((row) => drafts[row.studentId]?.status);
  if (selected.length < 1 || selected.length > 100) throw new Error('每次请选择 1 至 100 名学生');
  if (new Set(selected.map((row) => row.studentId)).size !== selected.length) throw new Error('不能重复提交同一学生');
  return { classOrganizationId, attendanceDate, items: selected.map((row) => {
    const draft = drafts[row.studentId];
    const status = draft.status!;
    if (!(status in statusLabels)) throw new Error('请选择有效考勤状态');
    const checkinTime = normalizeTime(draft.checkinTime);
    const checkoutTime = normalizeTime(draft.checkoutTime);
    if (['ABSENT', 'LEAVE'].includes(status) && (checkinTime || checkoutTime)) throw new Error('缺勤或请假的签到签退时间必须为空');
    if (checkinTime && checkoutTime && checkinTime > checkoutTime) throw new Error('签到时间不得晚于签退时间');
    return { studentId: row.studentId, status, checkinTime, checkoutTime, versionNo: row.record?.versionNo ?? null };
  }) };
}
