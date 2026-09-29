import { apiClient } from './http';

export type TeacherStatus = 'ENABLED' | 'DISABLED' | 'LOCKED';
export type TeacherBatchOperation = 'ENABLE' | 'DISABLE' | 'LOCK' | 'BIND_CLASS' | 'UNBIND_CLASS';

export interface Teacher {
  id: string;
  username: string;
  displayName: string;
  mobile: string | null;
  status: TeacherStatus;
  schoolId: string;
  schoolName: string;
  classOrganizationIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface TeacherPage {
  items: Teacher[];
  page: number;
  pageSize: number;
  total: number;
}

export interface TeacherQuery {
  keyword?: string;
  schoolId?: string;
  classOrganizationId?: string;
  status?: TeacherStatus;
  page: number;
  pageSize: number;
}

export interface CreateTeacherInput {
  username: string;
  displayName: string;
  mobile?: string;
  initialPassword: string;
  schoolId: string;
  classOrganizationIds: string[];
}

export interface UpdateTeacherProfileInput {
  displayName: string;
  mobile?: string;
  clearMobile: boolean;
}

export interface TeacherBatchInput {
  operation: TeacherBatchOperation;
  teacherUserIds: string[];
  classOrganizationId?: string;
}

export interface TeacherBatchResult {
  successCount: number;
  failureCount: number;
  items: Array<{
    teacherUserId: string;
    success: boolean;
    errorCode: string | null;
    message: string | null;
  }>;
}

export const teachersApi = {
  list(query: TeacherQuery): Promise<TeacherPage> {
    const parameters = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) });
    if (query.keyword) parameters.set('keyword', query.keyword);
    if (query.schoolId) parameters.set('schoolId', query.schoolId);
    if (query.classOrganizationId) parameters.set('classOrganizationId', query.classOrganizationId);
    if (query.status) parameters.set('status', query.status);
    return apiClient.get<TeacherPage>(`/organization-teachers?${parameters.toString()}`);
  },
  create(input: CreateTeacherInput): Promise<Teacher> {
    return apiClient.post<Teacher>('/organization-teachers', input);
  },
  updateProfile(teacherUserId: string, input: UpdateTeacherProfileInput): Promise<Teacher> {
    return apiClient.put<Teacher>(`/organization-teachers/${teacherUserId}/profile`, input);
  },
  changeStatus(teacherUserId: string, status: TeacherStatus): Promise<Teacher> {
    return apiClient.put<Teacher>(`/organization-teachers/${teacherUserId}/status`, { status });
  },
  resetPassword(teacherUserId: string, newPassword: string): Promise<void> {
    return apiClient.post<void>(`/organization-teachers/${teacherUserId}/password-resets`, { newPassword });
  },
  bindClass(teacherUserId: string, classOrganizationId: string): Promise<void> {
    return apiClient.put<void>(`/teachers/${teacherUserId}/classes/${classOrganizationId}`, {});
  },
  unbindClass(teacherUserId: string, classOrganizationId: string): Promise<void> {
    return apiClient.delete(`/teachers/${teacherUserId}/classes/${classOrganizationId}`);
  },
  batch(input: TeacherBatchInput): Promise<TeacherBatchResult> {
    return apiClient.post<TeacherBatchResult>('/organization-teachers/batch', input);
  }
};
