import { apiClient } from './http';

export type ImportJobStatus = 'QUEUED' | 'VALIDATING' | 'VALIDATED' | 'VALIDATION_FAILED' | 'SYSTEM_FAILED';
export interface ImportJobOption { id: string; label: string; }
export interface ImportJobOptions { templates: ImportJobOption[]; organizations: ImportJobOption[]; }
export interface ImportJobRecord {
  id: string; jobCode: string; templateId: string; templateVersion: string; templateName: string;
  sourceFileId: string; errorFileId: string | null; requesterId: string; organizationId: string | null;
  status: ImportJobStatus; versionNo: number; failureCode: string | null; failureMessage: string | null;
  totalRows: number; processedRows: number; validRows: number; invalidRows: number;
  queuedAt: string; startedAt: string | null; completedAt: string | null; createdAt: string; updatedAt: string;
}
export interface ImportJobPage { items: ImportJobRecord[]; page: number; pageSize: number; total: number; }
export interface ImportFieldSnapshot {
  fieldCode: string; columnName: string; dataType: string; required: boolean;
  maxLength: number | null; dictionaryValues: string[]; sortOrder: number;
}
export interface ImportJobDetail { job: ImportJobRecord; fields: ImportFieldSnapshot[]; }
export interface ImportJobErrorRecord {
  id: string; rowNumber: number; status: 'INVALID'; errorSummary: string; createdAt: string;
}
export interface ImportJobErrorPage {
  items: ImportJobErrorRecord[]; page: number; pageSize: number; total: number;
}
export interface ImportJobQuery {
  jobCode?: string; templateId?: string; organizationId?: string; status?: ImportJobStatus;
  page?: number; pageSize?: number;
}

function queryString(query: ImportJobQuery): string {
  const parameters = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') parameters.set(key, String(value));
  });
  const text = parameters.toString();
  return text ? `?${text}` : '';
}

export const importJobApi = {
  options(): Promise<ImportJobOptions> { return apiClient.get('/import-jobs/options'); },
  list(query: ImportJobQuery = {}): Promise<ImportJobPage> {
    return apiClient.get(`/import-jobs${queryString(query)}`);
  },
  create(input: { templateId: string; organizationId?: string; file: File }): Promise<ImportJobRecord> {
    const form = new FormData();
    form.set('templateId', input.templateId);
    if (input.organizationId) form.set('organizationId', input.organizationId);
    form.set('file', input.file);
    return apiClient.postForm('/import-jobs', form);
  },
  detail(id: string): Promise<ImportJobDetail> { return apiClient.get(`/import-jobs/${id}`); },
  errors(id: string, page = 1, pageSize = 20): Promise<ImportJobErrorPage> {
    return apiClient.get(`/import-jobs/${id}/errors?page=${page}&pageSize=${pageSize}`);
  },
  downloadError(id: string): Promise<Blob> { return apiClient.getBlob(`/import-jobs/${id}/error-file`); }
};
