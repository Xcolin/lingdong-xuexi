import { apiClient } from './http';

export interface OrganizationType {
  id: string;
  code: string;
  name: string;
  builtIn: boolean;
  status: 'ENABLED' | 'DISABLED';
  sortOrder: number;
}

export interface OrganizationNode {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  typeCode: string;
  path: string;
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
  effectiveStatus: 'ENABLED' | 'DISABLED';
  versionNo: number;
  createdAt?: string;
  updatedAt?: string;
  children: OrganizationNode[];
}

export type OrganizationChangeType = 'DISABLE' | 'MOVE' | 'DELETE';

export interface OrganizationChange {
  changeId: string;
  taskId: string;
  organizationId: string;
  changeType: OrganizationChangeType;
  targetParentId: string | null;
  expectedVersion: number;
  organizationCodeSnapshot: string;
  organizationNameSnapshot: string;
  fromParentIdSnapshot: string | null;
  reason: string;
  executionStatus: 'PENDING' | 'APPLIED' | 'FAILED';
  failureReason: string | null;
  taskStatus: 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'EFFECTIVE';
  submittedBy: string;
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
  createdAt: string;
}

export interface CreateOrganizationTypeInput {
  code: string;
  name: string;
  sortOrder: number;
}

export interface CreateOrganizationInput {
  code: string;
  name: string;
  typeCode: string;
  parentId?: string;
  sortOrder: number;
}

export interface UpdateOrganizationInput {
  name: string;
  sortOrder: number;
  versionNo: number;
}

export interface CreateOrganizationChangeInput {
  organizationId: string;
  changeType: OrganizationChangeType;
  targetParentId?: string;
  expectedVersion: number;
  reason: string;
}

export interface OrganizationChangeReviewInput {
  comment?: string;
}

export const organizationApi = {
  listTypes(): Promise<OrganizationType[]> {
    return apiClient.get<OrganizationType[]>('/organization-types');
  },
  createType(input: CreateOrganizationTypeInput): Promise<OrganizationType> {
    return apiClient.post<OrganizationType>('/organization-types', input);
  },
  listTree(): Promise<OrganizationNode[]> {
    return apiClient.get<OrganizationNode[]>('/organizations');
  },
  createOrganization(input: CreateOrganizationInput): Promise<OrganizationNode> {
    return apiClient.post<OrganizationNode>('/organizations', input);
  },
  updateOrganization(organizationId: string, input: UpdateOrganizationInput): Promise<OrganizationNode> {
    return apiClient.put<OrganizationNode>(`/organizations/${organizationId}`, input);
  },
  enableOrganization(organizationId: string, versionNo: number): Promise<OrganizationNode> {
    return apiClient.post<OrganizationNode>(`/organizations/${organizationId}/enable`, { versionNo });
  },
  createChange(input: CreateOrganizationChangeInput): Promise<OrganizationChange> {
    return apiClient.post<OrganizationChange>('/organization-changes', input);
  },
  listChanges(): Promise<OrganizationChange[]> {
    return apiClient.get<OrganizationChange[]>('/organization-changes');
  },
  approveChange(taskId: string, input: OrganizationChangeReviewInput): Promise<OrganizationChange> {
    return apiClient.post<OrganizationChange>(`/organization-changes/${taskId}/approve`, input);
  },
  rejectChange(taskId: string, input: OrganizationChangeReviewInput): Promise<OrganizationChange> {
    return apiClient.post<OrganizationChange>(`/organization-changes/${taskId}/reject`, input);
  }
};
