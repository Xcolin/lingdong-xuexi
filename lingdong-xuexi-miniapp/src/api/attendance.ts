import { request } from './http';

export type AttendanceStatus = 'NORMAL' | 'LATE' | 'EARLY_LEAVE' | 'ABSENT' | 'LEAVE';
export type AttendanceIdentity = 'teacher' | 'organization' | 'parent' | 'student';

export interface AttendanceUser {
  userId: string;
  sessionId: string;
  roleCodes: string[];
  permissionCodes: string[];
}

export interface AttendanceRecord {
  id: string;
  studentId: string;
  studentName: string;
  classOrganizationId: string;
  className: string;
  attendanceDate: string;
  status: AttendanceStatus;
  checkinTime: string | null;
  checkoutTime: string | null;
  source: 'MANUAL';
  recordedBy: string;
  recorderName: string;
  versionNo: number;
  createdAt: string;
  updatedAt: string;
}

export interface AttendanceAction {
  id: string;
  actionType: 'CREATE' | 'CORRECT';
  operatorUserId: string;
  operatorName: string;
  beforeStatus: AttendanceStatus | null;
  afterStatus: AttendanceStatus;
  beforeCheckinTime: string | null;
  afterCheckinTime: string | null;
  beforeCheckoutTime: string | null;
  afterCheckoutTime: string | null;
  createdAt: string;
}

export interface AttendanceDetail { record: AttendanceRecord; actions: AttendanceAction[] }
export interface AttendanceClass { classOrganizationId: string; className: string }
export interface AttendanceRosterStudent { studentId: string; studentName: string; record: AttendanceRecord | null }
export interface AttendanceFilters {
  classOrganizationId?: string;
  studentId?: string;
  keyword?: string;
  status?: AttendanceStatus;
  dateFrom?: string;
  dateTo?: string;
  page: number;
  pageSize: number;
}
export interface AttendancePage { items: AttendanceRecord[]; page: number; pageSize: number; total: number }
export interface AttendanceBatchItem {
  studentId: string;
  status: AttendanceStatus;
  checkinTime: string | null;
  checkoutTime: string | null;
  versionNo: number | null;
}
export interface AttendanceBatch { classOrganizationId: string; attendanceDate: string; items: AttendanceBatchItem[] }

/** 显式传入当前身份令牌，避免多身份会话之间串用数据。 */
export function getAttendanceUser(accessToken: string): Promise<AttendanceUser> {
  return request('/auth/me', { header: { Authorization: `Bearer ${accessToken}` } });
}

export function listAttendanceRecords(accessToken: string, filters: AttendanceFilters): Promise<AttendancePage> {
  const query = Object.entries(filters)
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`).join('&');
  return request(`/attendance-records?${query}`, { header: { Authorization: `Bearer ${accessToken}` } });
}

/** 查询保留历史班级；点名仅使用有效且有登记权限的班级。 */
export function listAttendanceClasses(accessToken: string, operational = false): Promise<AttendanceClass[]> {
  return request(`/attendance-records/class-options${operational ? '?operational=true' : ''}`, {
    header: { Authorization: `Bearer ${accessToken}` }
  });
}

export function getAttendanceRoster(accessToken: string, classOrganizationId: string, attendanceDate: string): Promise<AttendanceRosterStudent[]> {
  return request(`/attendance-records/roster?classOrganizationId=${encodeURIComponent(classOrganizationId)}&attendanceDate=${encodeURIComponent(attendanceDate)}`, {
    header: { Authorization: `Bearer ${accessToken}` }
  });
}

export function submitAttendanceBatch(accessToken: string, payload: AttendanceBatch): Promise<AttendanceRecord[]> {
  return request('/attendance-records/batch', {
    method: 'POST', data: payload, header: { Authorization: `Bearer ${accessToken}` }
  });
}

export function getAttendanceDetail(accessToken: string, id: string): Promise<AttendanceDetail> {
  return request(`/attendance-records/${encodeURIComponent(id)}`, { header: { Authorization: `Bearer ${accessToken}` } });
}
