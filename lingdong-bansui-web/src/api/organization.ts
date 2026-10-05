import { apiClient } from './http';
import type { ManagedUser, UserDirectoryPage } from './users';

export interface OrganizationMember extends ManagedUser { administrator:boolean; }
export interface OrganizationMemberPage extends Omit<UserDirectoryPage,'items'> { items:OrganizationMember[]; }

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
  adminDivisionCode: string | null;
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
  adminDivisionCode?: string | null;
}

export interface UpdateOrganizationInput {
  name: string;
  sortOrder: number;
  versionNo: number;
  /** 传 null 表示保持既有行政区划不变；显式空串表示清除。 */
  adminDivisionCode?: string | null;
}

export interface ReorderOrganizationsInput {
  parentId: string | null;
  items: Array<{ organizationId: string; expectedVersion: number }>;
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
  memberRelations(id:string,userIds:string[]):Promise<{userId:string;administrator:boolean}[]> {
    return apiClient.get(`/organizations/${id}/member-relations?${new URLSearchParams({userIds:userIds.join(',')})}`);
  },
  members(id:string,query:{page:number;pageSize:number;keyword?:string}):Promise<OrganizationMemberPage> {
    const parameters=new URLSearchParams({page:String(query.page),pageSize:String(query.pageSize)});
    if(query.keyword)parameters.set('keyword',query.keyword);
    return apiClient.get(`/organizations/${id}/members?${parameters}`);
  },
  associateMembers(id:string,userIds:string[],administrator:boolean):Promise<void> {
    return apiClient.post(`/organizations/${id}/members:batch`,{userIds,administrator});
  },
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
  /** 拖拽同级排序直接生效：items 为排序后的完整同级节点集合。 */
  reorderOrganizations(input: ReorderOrganizationsInput): Promise<OrganizationNode[]> {
    return apiClient.put<OrganizationNode[]>('/organizations/order', input);
  },
  /** 拖拽改父级直接生效：目标父级不得为自身或自身后代。 */
  moveOrganization(organizationId: string, targetParentId: string, versionNo: number): Promise<OrganizationNode> {
    return apiClient.put<OrganizationNode>(`/organizations/${organizationId}/position`, { targetParentId, expectedVersion: versionNo });
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
