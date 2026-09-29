import { apiClient } from './http';

export type CacheDomain = 'PERMISSION' | 'DICTIONARY' | 'ORGANIZATION' | 'FEATURE_TOGGLE' | 'USER_SESSION' | 'BUSINESS_STATISTICS' | 'ALL';
export type CacheOperationType = 'CLEAR' | 'REFRESH';
export type CacheOperationStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED';

export interface CacheOperation {
  id: string;
  code: string;
  taskId?: string;
  cacheDomain: CacheDomain;
  operationType: CacheOperationType;
  status: CacheOperationStatus;
  impactDescription: string;
  requestedBy: string;
  executedBy?: string;
  failureMessage?: string;
  executedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CacheReviewQueueItem {
  operationId: string;
  taskId: string;
  cacheDomain: CacheDomain;
  operationType: CacheOperationType;
  operationStatus: CacheOperationStatus;
  impactDescription: string;
  taskStatus: 'PENDING_REVIEW';
  taskTitle: string;
  submittedBy: string;
  submittedAt: string;
}

export interface ExecuteCacheOperationInput {
  cacheDomain: CacheDomain;
  operationType: CacheOperationType;
  impactDescription: string;
}

export interface SubmitHighRiskCacheOperationInput {
  cacheDomain: 'ALL' | 'USER_SESSION';
  operationType: 'CLEAR';
  title: string;
  description: string;
  confirmed: boolean;
}

export const cacheManagementApi = {
  listOperations: (): Promise<CacheOperation[]> => apiClient.get('/cache-management/operations'),
  executeOperation: (input: ExecuteCacheOperationInput): Promise<CacheOperation> =>
    apiClient.post('/cache-management/operations', input),
  submitHighRisk: (input: SubmitHighRiskCacheOperationInput): Promise<CacheOperation> =>
    apiClient.post('/cache-management/review-submissions', input),
  listReviewQueue: (): Promise<CacheReviewQueueItem[]> =>
    apiClient.get('/cache-management/review-queue'),
  approve: (taskId: string, comment: string): Promise<CacheOperation> =>
    apiClient.post(`/cache-management/review-queue/${taskId}/approve`, { comment }),
  reject: (taskId: string, comment: string): Promise<CacheOperation> =>
    apiClient.post(`/cache-management/review-queue/${taskId}/reject`, { comment })
};
