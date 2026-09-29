import { apiClient } from './http';

export type InterfaceDirection = 'INBOUND' | 'OUTBOUND';
export type InterfacePurpose = 'WECHAT' | 'MAP' | 'SMS' | 'SCHOOL' | 'DATA_SYNC' | 'OTHER';
export type InterfaceAuthorizationScope = 'GLOBAL' | 'REGION' | 'SCHOOL' | 'INSTITUTION' | 'SPECIFIED_CALLER';
export type InterfaceServiceStatus = 'ENABLED' | 'DISABLED';
export type InterfaceServiceChangeType = 'CREATE' | 'ENABLE' | 'DISABLE' | 'CHANGE_AUTHORIZATION';
export type InterfaceServiceChangeExecutionStatus = 'PENDING' | 'APPLIED' | 'FAILED';
export type InterfaceCallResult = 'SUCCEEDED' | 'FAILED';
export type SystemTaskStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'EFFECTIVE' | 'VOIDED';

export interface InterfaceServiceRecord {
  id: string;
  serviceName: string;
  direction: InterfaceDirection;
  purpose: InterfacePurpose;
  callerName: string;
  authorizationScope: InterfaceAuthorizationScope;
  authorizationScopeValue: string | null;
  ownerId: string;
  status: InterfaceServiceStatus;
  createdAt: string;
  updatedAt: string;
}

export interface InterfaceServiceChangeRecord {
  changeId: string;
  taskId: string;
  serviceId: string | null;
  changeType: InterfaceServiceChangeType;
  serviceName: string | null;
  direction: InterfaceDirection | null;
  purpose: InterfacePurpose | null;
  callerName: string | null;
  authorizationScope: InterfaceAuthorizationScope | null;
  authorizationScopeValue: string | null;
  ownerId: string | null;
  targetStatus: InterfaceServiceStatus | null;
  executionStatus: InterfaceServiceChangeExecutionStatus;
  failureReason: string | null;
  taskTitle: string;
  taskDescription: string;
  taskStatus: SystemTaskStatus;
  submittedBy: string;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
  createdAt: string;
}

export interface InterfaceServiceCallLog {
  id: string;
  serviceId: string;
  serviceName: string;
  callerName: string;
  result: InterfaceCallResult;
  errorSummary: string | null;
  traceId: string | null;
  occurredAt: string;
}

export interface InterfaceServiceSubmission {
  changeId: string;
  taskId: string;
  serviceId: string | null;
  changeType: InterfaceServiceChangeType;
}

export interface InterfaceServiceTaskResult {
  taskId: string;
  status: SystemTaskStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
}

export interface RegisterInterfaceServiceInput {
  serviceName: string;
  direction: InterfaceDirection;
  purpose: InterfacePurpose;
  callerName: string;
  authorizationScope: InterfaceAuthorizationScope;
  authorizationScopeValue?: string;
  ownerId: string;
  title: string;
  description: string;
}

export interface SubmitInterfaceServiceAuthorizationInput {
  authorizationScope: InterfaceAuthorizationScope;
  authorizationScopeValue?: string;
  title: string;
  description: string;
}

export interface InterfaceServiceQuery {
  serviceName?: string;
  callerName?: string;
  status?: InterfaceServiceStatus;
  purpose?: InterfacePurpose;
  ownerId?: string;
}

function queryString(query: Record<string, string | undefined>): string {
  const parameters = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => { if (value) parameters.set(key, value); });
  const text = parameters.toString();
  return text ? `?${text}` : '';
}

export const interfaceServiceManagementApi = {
  listServices(query: InterfaceServiceQuery = {}): Promise<InterfaceServiceRecord[]> {
    return apiClient.get<InterfaceServiceRecord[]>(`/interface-services${queryString({
      serviceName: query.serviceName,
      callerName: query.callerName,
      status: query.status,
      purpose: query.purpose,
      ownerId: query.ownerId
    })}`);
  },
  listChanges(): Promise<InterfaceServiceChangeRecord[]> {
    return apiClient.get<InterfaceServiceChangeRecord[]>('/interface-services/changes');
  },
  listReviewQueue(): Promise<InterfaceServiceChangeRecord[]> {
    return apiClient.get<InterfaceServiceChangeRecord[]>('/interface-services/review-queue');
  },
  listCallLogs(serviceId?: string, result?: InterfaceCallResult): Promise<InterfaceServiceCallLog[]> {
    return apiClient.get<InterfaceServiceCallLog[]>(
      `/interface-services/call-logs${queryString({ serviceId, result })}`
    );
  },
  submitRegistration(input: RegisterInterfaceServiceInput): Promise<InterfaceServiceSubmission> {
    return apiClient.post<InterfaceServiceSubmission>('/interface-services/registration-submissions', input);
  },
  submitEnable(serviceId: string, title: string, description: string): Promise<InterfaceServiceSubmission> {
    return apiClient.post<InterfaceServiceSubmission>(
      `/interface-services/${serviceId}/enable-submissions`, { title, description }
    );
  },
  submitDisable(serviceId: string, title: string, description: string): Promise<InterfaceServiceSubmission> {
    return apiClient.post<InterfaceServiceSubmission>(
      `/interface-services/${serviceId}/disable-submissions`, { title, description }
    );
  },
  submitAuthorization(
    serviceId: string,
    input: SubmitInterfaceServiceAuthorizationInput
  ): Promise<InterfaceServiceSubmission> {
    return apiClient.post<InterfaceServiceSubmission>(
      `/interface-services/${serviceId}/authorization-submissions`, input
    );
  },
  approve(taskId: string, comment: string): Promise<InterfaceServiceTaskResult> {
    return apiClient.post<InterfaceServiceTaskResult>(
      `/interface-services/review-tasks/${taskId}/approve`, { comment }
    );
  },
  reject(taskId: string, comment: string): Promise<InterfaceServiceTaskResult> {
    return apiClient.post<InterfaceServiceTaskResult>(
      `/interface-services/review-tasks/${taskId}/reject`, { comment }
    );
  }
};
