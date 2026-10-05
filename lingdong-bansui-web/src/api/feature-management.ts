import { apiClient } from './http';
import type { SystemTaskStatus } from './system-tasks';
export type FeatureStatus = 'ENABLED' | 'DISABLED';
export interface GlobalFeatureToggle { id: string; featureCode: string; featureName: string; status: FeatureStatus; versionNo: string; description: string | null; enableAllowed: boolean }
export interface FeatureToggleChange { id: string; taskId: string; featureCode: string; featureName: string; beforeStatus: FeatureStatus | null; targetStatus: FeatureStatus; currentStatus: FeatureStatus; baseVersion: string | null; currentVersion: string; taskStatus: SystemTaskStatus; title: string; description: string | null; submittedBy: string | null; submittedByUserId?: string | null; submittedAt: string | null; reviewedBy: string | null; reviewedAt: string | null; reviewComment: string | null; createdAt: string | null; enableAllowed: boolean }
export interface FeatureChangePage { items: FeatureToggleChange[]; page: number; pageSize: number; total: number }
export interface FeatureSubmission { featureCode: string; targetStatus: FeatureStatus; expectedVersion: string; title: string; description: string; confirmed: boolean }
const prefix = '/feature-management';
function query(input: { page: number; pageSize: number; taskStatus?: SystemTaskStatus }) {
  const params = new URLSearchParams({ page: String(input.page), pageSize: String(input.pageSize) });
  if (input.taskStatus) params.set('taskStatus', input.taskStatus);
  return params.toString();
}
export const featureManagementApi = {
  toggles: (): Promise<GlobalFeatureToggle[]> => apiClient.get(`${prefix}/toggles`),
  changes: (input: { page: number; pageSize: number; taskStatus?: SystemTaskStatus }): Promise<FeatureChangePage> => apiClient.get(`${prefix}/changes?${query(input)}`),
  reviewQueue: (input: { page: number; pageSize: number }): Promise<FeatureChangePage> => apiClient.get(`${prefix}/review-queue?${query(input)}`),
  submit: (input: FeatureSubmission): Promise<FeatureToggleChange> => apiClient.post(`${prefix}/review-submissions`, input),
  approve: (taskId: string, input: { comment: string }): Promise<FeatureToggleChange> => apiClient.post(`${prefix}/review-queue/${encodeURIComponent(taskId)}/approve`, input),
  reject: (taskId: string, input: { comment: string }): Promise<FeatureToggleChange> => apiClient.post(`${prefix}/review-queue/${encodeURIComponent(taskId)}/reject`, input)
};
