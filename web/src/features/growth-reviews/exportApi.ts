import { apiClient } from '../../api/http';
import type { ExportJobRecord, ExportJobStatus } from '../../api/export-jobs';
import type { GrowthReviewPeriodType } from './types';

export type GrowthReviewExportRecord = Omit<ExportJobRecord, 'exportType'> & { exportType: 'GROWTH_REVIEW_PDF' };
export interface GrowthReviewExportPage {
  items: GrowthReviewExportRecord[];
  page: number;
  pageSize: number;
  total: number;
}

/** 两种选取互斥，雪花标识始终为字符串，避免浏览器整数精度丢失。 */
export type CreateGrowthReviewExportInput = {
  studentId: string;
  templateId: string;
  mode: 'SIMPLE' | 'DETAILED';
  reason: string;
} & (
  { reviewId: string; periodType?: never; dateFrom?: never; dateTo?: never }
  | { reviewId?: never; periodType: GrowthReviewPeriodType; dateFrom: string; dateTo: string }
);

export const growthReviewExportApi = {
  options(studentId: string): Promise<Array<{ id: string; templateName: string; version: string; modes: Array<'SIMPLE' | 'DETAILED'> }>> {
    return apiClient.get(`/growth-review-export-jobs/options?${new URLSearchParams({ studentId })}`);
  },
  downloadFile(id: string) {
    return apiClient.getDownload(`/growth-review-export-jobs/${encodeURIComponent(id)}/download`);
  },
  create(input: CreateGrowthReviewExportInput): Promise<GrowthReviewExportRecord> {
    return apiClient.post('/growth-review-export-jobs', input);
  },
  list(studentId: string, query: { status?: ExportJobStatus; page?: number; pageSize?: number } = {}): Promise<GrowthReviewExportPage> {
    const params = new URLSearchParams({ studentId, page: String(query.page ?? 1), pageSize: String(query.pageSize ?? 20) });
    if (query.status) params.set('status', query.status);
    return apiClient.get(`/growth-review-export-jobs?${params}`);
  },
  detail(id: string): Promise<GrowthReviewExportRecord> {
    return apiClient.get(`/growth-review-export-jobs/${encodeURIComponent(id)}`);
  },
  download(id: string): Promise<Blob> {
    return apiClient.getBlob(`/growth-review-export-jobs/${encodeURIComponent(id)}/download`);
  }
};
