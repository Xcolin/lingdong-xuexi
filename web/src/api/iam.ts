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
  grantRolePermission(roleId: string, permissionId: string): Promise<void> {
    return apiClient.post<void>(`/roles/${roleId}/permissions`, { permissionId });
  }
};
