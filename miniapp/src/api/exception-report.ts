import { request } from './http';

export type ExceptionReportType = 'ATTENDANCE' | 'LEARNING_STATUS' | 'MENTAL_STATE';
export type ExceptionReportStatus = 'SUBMITTED' | 'HANDLED';

export interface ExceptionReport {
  id: string; studentId: string; studentName: string; studentAccountMasked: string;
  classOrganizationId: string; className: string; reporterUserId: string; reporterName: string;
  exceptionType: ExceptionReportType; content: string; status: ExceptionReportStatus;
  handlerName?: string | null; reportedAt: string; handledAt?: string | null; versionNo: number;
}
export interface ExceptionReportPage { items: ExceptionReport[]; page: number; pageSize: number; total: number; }
export interface ExceptionReportAction { id: string; actionType: string; operatorName: string; actionNote?: string; createdAt: string; }
export interface ExceptionReportDetails { report: ExceptionReport; actions: ExceptionReportAction[]; }
export interface ExceptionReportStudentOption { studentId: string; studentName: string; studentAccountMasked: string; }

export function listExceptionReports(token: string, page=1, pageSize=20, status?:ExceptionReportStatus): Promise<ExceptionReportPage> {
  return authenticated(token, `/exception-reports?page=${page}&pageSize=${pageSize}${status?`&status=${status}`:''}`);
}
export function listExceptionReportStudents(token: string, classId: string): Promise<ExceptionReportStudentOption[]> {
  return authenticated(token, `/exception-reports/student-options?classOrganizationId=${encodeURIComponent(classId)}`);
}
export function createExceptionReport(token: string, input: {
  classOrganizationId: string; studentId: string; exceptionType: ExceptionReportType;
  content: string; idempotencyKey: string;
}): Promise<ExceptionReport> {
  return authenticated(token, '/exception-reports', { method: 'POST', data: input });
}
export function getExceptionReport(token: string, id: string): Promise<ExceptionReportDetails> {
  return authenticated(token, `/exception-reports/${encodeURIComponent(id)}`);
}
export function handleExceptionReport(token: string, id: string, versionNo: number, handlingNote: string): Promise<ExceptionReport> {
  return authenticated(token, `/exception-reports/${encodeURIComponent(id)}/handle`, {
    method: 'POST', data: { versionNo, handlingNote }
  });
}
function authenticated<T>(token: string, path: string,
  options: { method?: UniApp.RequestOptions['method']; data?: UniApp.RequestOptions['data'] } = {}): Promise<T> {
  return request<T>(path, { ...options, header: { Authorization: `Bearer ${token}` } });
}
