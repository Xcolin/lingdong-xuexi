import { apiClient } from '../../api/http';

export type AttendanceStatus = 'NORMAL' | 'LATE' | 'EARLY_LEAVE' | 'ABSENT' | 'LEAVE';
export interface AttendanceRecord {
  id: string; studentId: string; studentName: string; classOrganizationId: string; className: string;
  attendanceDate: string; status: AttendanceStatus; checkinTime: string | null; checkoutTime: string | null;
  source: string; recordedBy: string; recorderName: string; versionNo: number; createdAt: string; updatedAt: string;
}
export interface AttendanceAction {
  id: string; actionType: 'CREATE' | 'CORRECT'; operatorUserId: string; operatorName: string;
  beforeStatus: AttendanceStatus | null; afterStatus: AttendanceStatus;
  beforeCheckinTime: string | null; afterCheckinTime: string | null;
  beforeCheckoutTime: string | null; afterCheckoutTime: string | null; createdAt: string;
}
export interface AttendanceDetails { record: AttendanceRecord; actions: AttendanceAction[]; }
export interface AttendanceClass { classOrganizationId: string; className: string; }
export interface AttendanceRosterRow { studentId: string; studentName: string; record: AttendanceRecord | null; }
export interface AttendanceQuery {
  classOrganizationId?: string; studentId?: string; keyword?: string; status?: AttendanceStatus;
  dateFrom?: string; dateTo?: string; page?: number; pageSize?: number;
}
export interface AttendanceResult { items: AttendanceRecord[]; page: number; pageSize: number; total: number; }
export interface AttendanceBatch {
  classOrganizationId: string; attendanceDate: string;
  items: { studentId: string; status: AttendanceStatus; checkinTime: string | null; checkoutTime: string | null; versionNo: number | null }[];
}
const base = '/attendance-records';
export const attendanceApi = {
  list(query: AttendanceQuery = {}): Promise<AttendanceResult> {
    const params = new URLSearchParams();
    for (const key of ['classOrganizationId', 'studentId', 'keyword', 'status', 'dateFrom', 'dateTo'] as const) {
      if (query[key]) params.set(key, query[key]);
    }
    params.set('page', String(query.page ?? 1));
    params.set('pageSize', String(query.pageSize ?? 20));
    return apiClient.get(`${base}?${params}`);
  },
  classes(operational = false): Promise<AttendanceClass[]> {
    return apiClient.get(`${base}/class-options${operational ? '?operational=true' : ''}`);
  },
  roster(classOrganizationId: string, attendanceDate: string): Promise<AttendanceRosterRow[]> {
    return apiClient.get(`${base}/roster?${new URLSearchParams({ classOrganizationId, attendanceDate })}`);
  },
  batch(input: AttendanceBatch): Promise<AttendanceRecord[]> { return apiClient.post(`${base}/batch`, input); },
  details(id: string): Promise<AttendanceDetails> { return apiClient.get(`${base}/${encodeURIComponent(id)}`); }
};
