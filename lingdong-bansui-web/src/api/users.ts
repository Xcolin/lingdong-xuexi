import { apiClient } from './http';

export type UserType = 'PLATFORM' | 'ORGANIZATION' | 'FAMILY' | 'STUDENT';
export type UserStatus = 'ENABLED' | 'DISABLED' | 'LOCKED' | 'CANCELLED';
export type MutableUserStatus = Exclude<UserStatus, 'CANCELLED'>;

export interface ManagedUser {
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

export interface CreateUserInput {
  password?: string;
  organizationId: string;
  username: string;
  displayName: string;
  mobile?: string;
  type: UserType;
}

export const usersApi = {
  list(query: UserDirectoryQuery): Promise<UserDirectoryPage> {
    const parameters = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) });
    if (query.keyword) parameters.set('keyword', query.keyword);
    if (query.type) parameters.set('type', query.type);
    if (query.status) parameters.set('status', query.status);
    if (query.organizationId) parameters.set('organizationId', query.organizationId);
    return apiClient.get<UserDirectoryPage>(`/users?${parameters.toString()}`);
  },
  create(input: CreateUserInput): Promise<ManagedUser> {
    return apiClient.post<ManagedUser>('/users', input);
  },
  updateStatus(userId: string, status: MutableUserStatus): Promise<ManagedUser> {
    return apiClient.patch<ManagedUser>(`/users/${userId}/status`, { status });
  },
  setPassword(userId: string, password: string): Promise<void> {
    return apiClient.post<void>(`/users/${userId}/password`, { password });
  }
};
