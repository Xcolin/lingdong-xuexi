import { apiClient } from './http';

export type UserType = 'PERSONAL' | 'PLATFORM' | 'ORGANIZATION' | 'FAMILY' | 'STUDENT';
export type UserStatus = 'ENABLED' | 'DISABLED' | 'LOCKED' | 'CANCELLED';
export type MutableUserStatus = Exclude<UserStatus, 'CANCELLED'>;

export interface ManagedUser {
  email?:string|null;
  emailSource?:string|null;
  roleIdentities?: Array<{roleCode:string;roleName:string;organizationId:string|null;organizationName:string|null}>;
  organizationNames?: string[];
  id: string;
  username: string;
  displayName: string;
  mobile: string | null;
  type: UserType;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface UserDirectoryQuery {
  identityRoleCode?:string;
  organizationId?: string;
  keyword?: string;
  type?: UserType;
  status?: UserStatus;
  page: number;
  pageSize: number;
}

export interface UserDirectoryPage {
  items: ManagedUser[];
  page: number;
  pageSize: number;
  total: number;
}

export interface UserGrant { userId: string; organizationId: string | null; }
export interface PickerUser extends ManagedUser { administrator?: boolean; assignments?: UserGrant[]; }
export interface UserPickerPage extends Omit<UserDirectoryPage,'items'> { items: PickerUser[]; allowedUnbound?: boolean; }
export interface UserPickerQuery { side: 'CANDIDATES'|'LINKED'; page:number; pageSize:number; keyword?:string; filterOrganizationId?:string; grantOrganizationId?:string; group?:'SCHOOL'|'UNBOUND'; }

export interface CreateUserInput {
  defaultRoleCode?: 'TEACHER';
  studentSchoolOrganizationId?: string;
  gradeCode?: string;
  password?: string;
  organizationId: string;
  username: string;
  displayName: string;
  mobile?: string;
  email?: string;
  type: Exclude<UserType, 'PERSONAL'>;
}

export interface UserCreateResult extends ManagedUser {
  studentId?: string | null;
  initialLoginCode?: string | null;
}

export interface UserOrganizationEditContext {organizationIds:string[];lockedOrganizationIds:string[];options:{id:string;parentId:string|null;name:string;typeCode:string;disabled:boolean}[];}
export interface UserRoleAssociationContext {roles:{id:string;code:string;name:string;dataScope:string;status:string}[];assignments:{roleId:string;organizationId:string|null;organizationName:string;removable:boolean}[];organizations:{id:string;parentId:string|null;name:string;typeCode:string;disabled:boolean}[];globalAssignable:boolean;}
export interface UserRoleAssociationItem {roleId:string;organizationId:string|null;}
export const usersApi = {
  scopeOrganizations(permission: string): Promise<UserOrganizationEditContext['options']> {
    return apiClient.get(`/users/scope-organizations?${new URLSearchParams({permission})}`);
  },
  creationOrganizations(type?: UserType): Promise<UserOrganizationEditContext['options']> {
    return apiClient.get('/users/creation-organizations'+(type?`?type=${encodeURIComponent(type)}`:''));
  },
  roleAssociations(userId:string):Promise<UserRoleAssociationContext>{return apiClient.get(`/users/${userId}/role-associations`);},
  addRoles(userId:string,assignments:UserRoleAssociationItem[]):Promise<void>{return apiClient.post(`/users/${userId}/roles:batch`,{assignments});},
  removeRoles(userId:string,assignments:UserRoleAssociationItem[]):Promise<void>{return apiClient.post(`/users/${userId}/roles:revoke`,{assignments});},
  organizationEdit(userId:string):Promise<UserOrganizationEditContext>{return apiClient.get(`/users/${userId}/organization-edit`);},
  list(query: UserDirectoryQuery): Promise<UserDirectoryPage> {
    const parameters = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) });
    if (query.keyword) parameters.set('keyword', query.keyword);
    if (query.type) parameters.set('type', query.type);
    if (query.identityRoleCode) parameters.set('identityRoleCode', query.identityRoleCode);
    if (query.status) parameters.set('status', query.status);
    if (query.organizationId) parameters.set('organizationId', query.organizationId);
    return apiClient.get<UserDirectoryPage>(`/users?${parameters.toString()}`);
  },
  create(input: CreateUserInput): Promise<UserCreateResult> {
    return apiClient.post<UserCreateResult>('/users', input);
  },
  updateProfile(userId:string,input:{displayName:string;mobile:string|null;email?:string|null;organizationIds:string[];expectedOrganizationIds:string[]}):Promise<ManagedUser>{return apiClient.patch<ManagedUser>(`/users/${userId}`,input);},
  updateStatus(userId: string, status: MutableUserStatus): Promise<ManagedUser> {
    return apiClient.patch<ManagedUser>(`/users/${userId}/status`, { status });
  },
  setPassword(userId: string, password: string): Promise<void> {
    return apiClient.post<void>(`/users/${userId}/password`, { password });
  }
};
