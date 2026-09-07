import { apiClient } from './http';

export interface Role {
  id: string;
  code: string;
  name: string;
  type: 'BUILT_IN' | 'CUSTOM';
  dataScope: 'ALL' | 'REGION' | 'SCHOOL' | 'CLASS' | 'SELF' | 'CUSTOM';
  builtIn: boolean;
  status: 'ENABLED' | 'DISABLED';
  description: string | null;
}

export interface Permission {
  id: string;
  code: string;
  name: string;
  resourceType: 'MENU' | 'PAGE' | 'BUTTON' | 'OPERATION';
  client: 'WEB' | 'MINIAPP' | 'BOTH';
  parentId: string | null;
  status: 'ENABLED' | 'DISABLED';
  description: string | null;
}

export interface CreateRoleInput {
  code: string;
  name: string;
  description?: string;
  dataScope: Role['dataScope'];
}

export interface CreatePermissionInput {
  code: string;
  name: string;
  resourceType: Permission['resourceType'];
  client: Permission['client'];
  parentId?: string;
}

export type PermissionEffect = 'ALLOW' | 'DENY';

export interface PermissionAssignment {
  permissionId: string;
  effect: PermissionEffect;
}

export type IamAuditEventType =
  | 'USER_CREATE' | 'USER_STATUS_CHANGE' | 'USER_ORGANIZATION_ASSOCIATE' | 'USER_ROLE_ASSIGN'
  | 'ROLE_CREATE' | 'PERMISSION_CREATE' | 'ROLE_PERMISSION_CONFIGURE' | 'ROLE_PERMISSION_REMOVE'
  | 'USER_PERMISSION_CONFIGURE' | 'USER_PERMISSION_REMOVE' | 'ROLE_DATA_SCOPE_ADD'
  | 'ORGANIZATION_ADMIN_ASSIGN';

export type IamAuditTargetType =
  | 'USER' | 'ROLE' | 'PERMISSION' | 'ROLE_PERMISSION' | 'USER_PERMISSION'
  | 'ROLE_DATA_SCOPE' | 'ORGANIZATION_ADMIN' | 'USER_ORGANIZATION' | 'USER_ROLE';

export interface IamChangeAudit {
  id: string;
  eventType: IamAuditEventType;
  operatorId: string | null;
  targetType: IamAuditTargetType;
  targetId: string;
  relatedId: string | null;
  organizationId: string | null;
  beforeValue: string | null;
  afterValue: string | null;
  occurredAt: string;
}

export interface IamAuditQuery {
  eventType?: IamAuditEventType;
  targetType?: IamAuditTargetType;
  operatorId?: string;
  targetId?: string;
  startedAt?: string;
  endedAt?: string;
  page: number;
  pageSize: number;
}

export interface IamAuditPage {
  items: IamChangeAudit[];
  page: number;
  pageSize: number;
  total: number;
}

export const iamApi = {
  listRoles(): Promise<Role[]> {
    return apiClient.get<Role[]>('/roles');
  },
  createRole(input: CreateRoleInput): Promise<Role> {
    return apiClient.post<Role>('/roles', input);
  },
  listPermissions(): Promise<Permission[]> {
    return apiClient.get<Permission[]>('/permissions');
  },
  createPermission(input: CreatePermissionInput): Promise<Permission> {
    return apiClient.post<Permission>('/permissions', input);
  },
  listRolePermissions(roleId: string): Promise<PermissionAssignment[]> {
    return apiClient.get<PermissionAssignment[]>(`/roles/${roleId}/permissions`);
  },
  configureRolePermission(roleId: string, permissionId: string, effect: PermissionEffect): Promise<void> {
    return apiClient.put<void>(`/roles/${roleId}/permissions/${permissionId}`, { effect });
  },
  removeRolePermission(roleId: string, permissionId: string): Promise<void> {
    return apiClient.delete(`/roles/${roleId}/permissions/${permissionId}`);
  },
  listUserPermissions(userId: string): Promise<PermissionAssignment[]> {
    return apiClient.get<PermissionAssignment[]>(`/users/${userId}/permissions`);
  },
  configureUserPermission(userId: string, permissionId: string, effect: PermissionEffect): Promise<void> {
    return apiClient.put<void>(`/users/${userId}/permissions/${permissionId}`, { effect });
  },
  removeUserPermission(userId: string, permissionId: string): Promise<void> {
    return apiClient.delete(`/users/${userId}/permissions/${permissionId}`);
  },
  listAudits(query: IamAuditQuery): Promise<IamAuditPage> {
    const parameters = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) });
    if (query.eventType) parameters.set('eventType', query.eventType);
    if (query.targetType) parameters.set('targetType', query.targetType);
    if (query.operatorId) parameters.set('operatorId', query.operatorId);
    if (query.targetId) parameters.set('targetId', query.targetId);
    if (query.startedAt) parameters.set('startedAt', query.startedAt);
    if (query.endedAt) parameters.set('endedAt', query.endedAt);
    return apiClient.get<IamAuditPage>(`/iam/audits?${parameters.toString()}`);
  }
};
