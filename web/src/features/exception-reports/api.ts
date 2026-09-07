import { apiClient } from '../../api/http';

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
export interface ExceptionReportClassOption { classOrganizationId: string; className: string; }
export interface ExceptionReportStudentOption { studentId: string; studentName: string; studentAccountMasked: string; }
export interface ExceptionReportQuery {
  classOrganizationId?: string;
  studentId?: string;
  exceptionType?: ExceptionReportType;
  status?: ExceptionReportStatus;
  page?: number;
  pageSize?: number;
}

export const exceptionReportApi = {
  list(query: ExceptionReportQuery = {}): Promise<ExceptionReportPage> {
    const params = new URLSearchParams();
    if (query.classOrganizationId) params.set('classOrganizationId', query.classOrganizationId);
    if (query.studentId) params.set('studentId', query.studentId);
    if (query.exceptionType) params.set('exceptionType', query.exceptionType);
    if (query.status) params.set('status', query.status);
    params.set('page', String(query.page ?? 1));
    params.set('pageSize', String(query.pageSize ?? 20));
    return apiClient.get(`/exception-reports?${params.toString()}`);
  },
  details(id: string): Promise<ExceptionReportDetails> {
    return apiClient.get(`/exception-reports/${encodeURIComponent(id)}`);
  },
  classes(): Promise<ExceptionReportClassOption[]> {
    return apiClient.get('/exception-reports/class-options');
  },
  students(classId: string): Promise<ExceptionReportStudentOption[]> {
    return apiClient.get(`/exception-reports/student-options?classOrganizationId=${encodeURIComponent(classId)}`);
  },
  create(input: { classOrganizationId: string; studentId: string; exceptionType: ExceptionReportType; content: string; idempotencyKey: string }): Promise<ExceptionReport> {
    return apiClient.post('/exception-reports', input);
  },
  handle(id: string, versionNo: number, handlingNote: string): Promise<ExceptionReport> {
    return apiClient.post(`/exception-reports/${encodeURIComponent(id)}/handle`, { versionNo, handlingNote });
  }
};
