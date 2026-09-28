import { apiClient } from './http';

export type ExportJobType = 'GROWTH_POINT_LEDGER' | 'IAM_CHANGE_AUDIT' | 'DICTIONARY_LEDGER' | 'TEMPLATE_LEDGER' | 'INTERFACE_SERVICE_LEDGER' | 'CACHE_OPERATION_LOG' | 'SYSTEM_TASK_LEDGER' | 'REWARD_EXCHANGE_LEDGER' | 'EXCEPTION_REPORT_LEDGER' | 'ATTACHMENT_LEDGER';
export type ExportJobStatus = 'PENDING_REVIEW' | 'QUEUED' | 'EXPORTING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED';

export interface ExportColumn { code: string; header: string; defaultSelected?: boolean; }
export interface ExportStudentOption { id: string; name: string; }
export interface ExportJobOptions {
  exceptionClasses?: { id: string; name: string }[];
  systemTaskTypes?: string[];
  exportType: ExportJobType;
  templateName: string;
  templateVersion: string;
  columns: ExportColumn[];
  students: ExportStudentOption[];
  sensitive: boolean;
}
export interface ExportJobRecord {
  id: string;
  jobCode: string;
  exportType: ExportJobType;
  templateName: string;
  templateVersion: string;
  status: ExportJobStatus;
  totalRows: number;
  processedRows: number;
  failureCode: string | null;
  failureMessage: string | null;
  requestReason: string;
  requestedAt: string;
  completedAt: string | null;
}
export interface ExportJobPage { items: ExportJobRecord[]; page: number; pageSize: number; total: number; }
export interface ExportJobEvent { id: string; eventType: string; summary: string; occurredAt: string; }
export interface ExportJobDetail {
  job: ExportJobRecord;
  columns: ExportColumn[];
  scopeSummary: string;
  events: ExportJobEvent[];
}
export interface ExportJobReview {
  jobId: string;
  systemTaskId: string;
  requesterName: string;
  exportType: ExportJobType;
  scopeSummary: string;
  requestReason: string;
  requestedAt: string;
}
export interface ExportJobReviewPage { items: ExportJobReview[]; page: number; pageSize: number; total: number; }
export interface CreateExportJobInput {
  attachmentModuleCode?: string;
  attachmentUploaderId?: string;
  attachmentFileCategory?: string;
  exceptionClassId?: string;
  exceptionType?: 'ATTENDANCE' | 'LEARNING_STATUS' | 'MENTAL_STATE';
  exceptionStatus?: 'SUBMITTED' | 'HANDLED';
  rewardExchangeStatus?: 'PENDING_APPROVAL' | 'PENDING_VERIFICATION' | 'REJECTED' | 'AUTO_REJECTED' | 'EXPIRED' | 'VERIFIED';
  systemTaskType?: string;
  systemTaskStatus?: string;
  cacheDomain?: string;
  cacheStatus?: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED';
  interfaceCallerName?: string;
  interfaceStatus?: 'ENABLED' | 'DISABLED';
  interfaceOwnerId?: string;
  templateType?: 'IMPORT' | 'EXPORT';
  templateModuleCode?: string;
  templateStatus?: 'ENABLED' | 'DISABLED';
  dictionaryTypeCode?: string;
  dictionaryStatus?: 'ENABLED' | 'DISABLED';
  exportType: ExportJobType;
  studentId?: string;
  startedAt?: string;
  endedAt?: string;
  eventType?: string;
  columns: string[];
  reason: string;
}
export interface ExportJobQuery {
  exportType?: ExportJobType;
  status?: ExportJobStatus;
  page?: number;
  pageSize?: number;
}

function queryString(query: ExportJobQuery): string {
  const parameters = new URLSearchParams();
  Object.entries(query as Record<string, string | number | undefined>).forEach(([key, value]) => {
    if (value !== undefined && value !== '') parameters.set(key, String(value));
  });
  const text = parameters.toString();
  return text ? `?${text}` : '';
}

export const exportJobApi = {
  options(exportType: ExportJobType): Promise<ExportJobOptions> {
    return apiClient.get(`/export-jobs/options?exportType=${exportType}`);
  },
  list(query: ExportJobQuery = {}): Promise<ExportJobPage> {
    return apiClient.get(`/export-jobs${queryString(query)}`);
  },
  create(input: CreateExportJobInput): Promise<ExportJobRecord> {
    return apiClient.post('/export-jobs', input);
  },
  detail(id: string): Promise<ExportJobDetail> {
    return apiClient.get(`/export-jobs/${id}`);
  },
  download(id: string): Promise<Blob> {
    return apiClient.getBlob(`/export-jobs/${id}/download`);
  },
  listReviews(page = 1, pageSize = 20): Promise<ExportJobReviewPage> {
    return apiClient.get(`/export-job-reviews?page=${page}&pageSize=${pageSize}`);
  },
  approve(taskId: string, comment: string): Promise<ExportJobRecord> {
    return apiClient.post(`/export-job-reviews/${taskId}/approve`, { comment });
  },
  reject(taskId: string, comment: string): Promise<ExportJobRecord> {
    return apiClient.post(`/export-job-reviews/${taskId}/reject`, { comment });
  }
};
