import { apiClient } from './http';

export type StudentImportStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL_SUCCEEDED' | 'FAILED';
export type StudentImportCredentialStatus = 'NONE' | 'AVAILABLE' | 'CONSUMED' | 'EXPIRED';
export type StudentImportRowStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED';

export interface StudentImportRecord {
  id: string;
  executionCode: string;
  validationJobId: string;
  organizationId: string;
  classOrganizationId: string | null;
  status: StudentImportStatus;
  versionNo: number;
  totalRows: number;
  processedRows: number;
  succeededRows: number;
  failedRows: number;
  failureCode: string | null;
  failureMessage: string | null;
  credentialStatus: StudentImportCredentialStatus;
  credentialExpiresAt: string | null;
  credentialDownloadedAt: string | null;
  queuedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export interface StudentImportRowRecord {
  id: string;
  rowNumber: number;
  status: StudentImportRowStatus;
  studentId: string | null;
  studentAccount: string | null;
  failureCode: string | null;
  failureMessage: string | null;
  attemptCount: number;
  updatedAt: string;
}

export interface StudentImportPage { items: StudentImportRecord[]; page: number; pageSize: number; total: number; }
export interface StudentImportRowPage { items: StudentImportRowRecord[]; page: number; pageSize: number; total: number; }

function queryString(values: Record<string, unknown>): string {
  const query = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
  });
  return query.size ? `?${query.toString()}` : '';
}

export const studentImportApi = {
  create(input: { validationJobId: string; classOrganizationId?: string }): Promise<StudentImportRecord> {
    return apiClient.post('/student-import-executions', input);
  },
  list(query: { status?: StudentImportStatus; page?: number; pageSize?: number } = {}): Promise<StudentImportPage> {
    return apiClient.get(`/student-import-executions${queryString(query)}`);
  },
  detail(id: string): Promise<StudentImportRecord> {
    return apiClient.get(`/student-import-executions/${id}`);
  },
  rows(id: string, status?: StudentImportRowStatus, page = 1, pageSize = 20): Promise<StudentImportRowPage> {
    return apiClient.get(`/student-import-executions/${id}/rows${queryString({ status, page, pageSize })}`);
  },
  retryFailures(id: string): Promise<StudentImportRecord> {
    return apiClient.post(`/student-import-executions/${id}/retry-failures`, {});
  },
  downloadCredentials(id: string): Promise<Blob> {
    return apiClient.getBlob(`/student-import-executions/${id}/credentials`);
  }
};
