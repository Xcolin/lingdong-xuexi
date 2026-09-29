import { apiClient } from './http';

export type SystemTaskStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'EFFECTIVE' | 'VOIDED';
export interface SystemTask {
  payload?: {
    fields: { label: string; value: string | null }[];
    differences: { label: string; before: string | null; after: string | null }[];
    executionStatus: string | null;
    failureReason: string | null;
    notice: string | null;
  };
  id: string;
  code: string;
  type: string;
  title: string;
  description: string | null;
  impactScope: string | null;
  status: SystemTaskStatus;
  submittedBy: string | null;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}
export interface SystemTaskPage { items: SystemTask[]; page: number; pageSize: number; total: number }
export const systemTasksApi = {
  list(input: { page: number; pageSize: number; status?: SystemTaskStatus }): Promise<SystemTaskPage> {
    const query = new URLSearchParams({ page: String(input.page), pageSize: String(input.pageSize) });
    if (input.status) query.set('status', input.status);
    return apiClient.get(`/system-tasks?${query}`);
  },
  detail(id: string): Promise<SystemTask> { return apiClient.get(`/system-tasks/${encodeURIComponent(id)}`); }
};
