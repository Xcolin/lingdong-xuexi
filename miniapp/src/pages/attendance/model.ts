import type { AttendanceBatch, AttendanceIdentity, AttendanceRosterStudent, AttendanceStatus, AttendanceUser } from '@/api/attendance';

export const attendanceStatuses: Array<{ value: AttendanceStatus | ''; label: string }> = [
  { value: '', label: '未选择' }, { value: 'NORMAL', label: '正常' },
  { value: 'LATE', label: '迟到' }, { value: 'EARLY_LEAVE', label: '早退' },
  { value: 'ABSENT', label: '缺勤' }, { value: 'LEAVE', label: '请假' }
];

export interface AttendanceDraft {
  studentId: string;
  studentName: string;
  selected: boolean;
  status: AttendanceStatus | '';
  checkinTime: string;
  checkoutTime: string;
  versionNo: number | null;
}

export function attendancePermissions(enabled: boolean, user: Pick<AttendanceUser, 'roleCodes' | 'permissionCodes'> | null, identity: AttendanceIdentity) {
  const read = enabled && !!user && !user.roleCodes.includes('SYS_AUDITOR')
    && user.permissionCodes.includes('ATTENDANCE_READ');
  const organizationIdentity = user?.roleCodes.some((role) => role === 'ORG_ADMIN' || role === 'TEACHER');
  const readonly = identity === 'parent' || identity === 'student'
    || (!organizationIdentity && !!user?.roleCodes.some((role) => role === 'PARENT' || role === 'STUDENT'));
  return { read, record: read && !readonly && !!user?.permissionCodes.includes('ATTENDANCE_RECORD') };
}

/** 按上海自然日计算，设备切换时区不改变业务日期。 */
export function attendanceToday(now = new Date()): string {
  return new Date(now.getTime() + 8 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

export function attendanceStatusName(status: AttendanceStatus | '' | null): string {
  return attendanceStatuses.find((item) => item.value === status)?.label || '未登记';
}

export function createAttendanceDrafts(roster: AttendanceRosterStudent[]): AttendanceDraft[] {
  return roster.map((student) => ({
    studentId: student.studentId, studentName: student.studentName, selected: false,
    status: student.record?.status || '', checkinTime: student.record?.checkinTime?.slice(0, 5) || '',
    checkoutTime: student.record?.checkoutTime?.slice(0, 5) || '', versionNo: student.record?.versionNo ?? null
  }));
}

function normalizeTime(value: string): string | null {
  if (!value.trim()) return null;
  const time = value.trim().length === 5 ? `${value.trim()}:00` : value.trim();
  if (!/^([01]\d|2[0-3]):[0-5]\d:00$/.test(time)) throw new Error('请按小时和分钟填写时间');
  return time;
}

export function buildAttendanceBatch(classOrganizationId: string, attendanceDate: string, drafts: AttendanceDraft[], today = attendanceToday()): AttendanceBatch {
  if (!classOrganizationId || !/^\d{4}-\d{2}-\d{2}$/.test(attendanceDate)) throw new Error('请选择班级和日期');
  if (attendanceDate > today) throw new Error('不能登记未来日期的考勤');
  const selected = drafts.filter((row) => row.selected);
  if (selected.length < 1 || selected.length > 100) throw new Error('每次请选择 1 至 100 名学生');
  const seen = new Set<string>();
  const items = selected.map((row) => {
    if (seen.has(row.studentId)) throw new Error('同一批次不能重复选择学生');
    seen.add(row.studentId);
    if (!row.status || !attendanceStatuses.some((item) => item.value === row.status)) throw new Error(`请为${row.studentName}选择状态`);
    const checkinTime = normalizeTime(row.checkinTime);
    const checkoutTime = normalizeTime(row.checkoutTime);
    if ((row.status === 'ABSENT' || row.status === 'LEAVE') && (checkinTime || checkoutTime)) throw new Error('缺勤和请假不能填写签到签退时间');
    if (checkinTime && checkoutTime && checkinTime > checkoutTime) throw new Error('签到时间不得晚于签退时间');
    return { studentId: row.studentId, status: row.status, checkinTime, checkoutTime, versionNo: row.versionNo };
  });
  return { classOrganizationId, attendanceDate, items };
}
