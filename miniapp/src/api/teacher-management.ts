import { request } from './http';

export type TeacherStatus = 'ENABLED' | 'DISABLED' | 'LOCKED';

export interface OrganizationTeacher {
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

export interface OrganizationTeacherPage {
  items: OrganizationTeacher[];
  page: number;
  pageSize: number;
  total: number;
}

function authorization(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

export function listOrganizationTeachers(
  accessToken: string,
  keyword = '',
  page = 1,
  pageSize = 20
): Promise<OrganizationTeacherPage> {
  const parameters = [`page=${page}`, `pageSize=${pageSize}`];
  if (keyword.trim()) parameters.push(`keyword=${encodeURIComponent(keyword.trim())}`);
  return request<OrganizationTeacherPage>(`/organization-teachers?${parameters.join('&')}`, {
    header: authorization(accessToken)
  });
}

export function createOrganizationTeacher(
  accessToken: string,
  input: {
    username: string;
    displayName: string;
    mobile?: string;
    initialPassword: string;
    schoolId: string;
    classOrganizationIds: string[];
  }
): Promise<OrganizationTeacher> {
  return request<OrganizationTeacher>('/organization-teachers', {
    method: 'POST', header: authorization(accessToken), data: input
  });
}

export function updateOrganizationTeacher(
  accessToken: string,
  teacherUserId: string,
  input: { displayName: string; mobile?: string; clearMobile: boolean }
): Promise<OrganizationTeacher> {
  return request<OrganizationTeacher>(`/organization-teachers/${teacherUserId}/profile`, {
    method: 'PUT', header: authorization(accessToken), data: input
  });
}

export function changeOrganizationTeacherStatus(
  accessToken: string,
  teacherUserId: string,
  status: TeacherStatus
): Promise<OrganizationTeacher> {
  return request<OrganizationTeacher>(`/organization-teachers/${teacherUserId}/status`, {
    method: 'PUT', header: authorization(accessToken), data: { status }
  });
}

export function resetOrganizationTeacherPassword(
  accessToken: string,
  teacherUserId: string,
  newPassword: string
): Promise<void> {
  return request<void>(`/organization-teachers/${teacherUserId}/password-resets`, {
    method: 'POST', header: authorization(accessToken), data: { newPassword }
  });
}

export function bindOrganizationTeacherClass(
  accessToken: string,
  teacherUserId: string,
  classOrganizationId: string
): Promise<void> {
  return request<void>(`/teachers/${teacherUserId}/classes/${classOrganizationId}`, {
    method: 'PUT', header: authorization(accessToken), data: {}
  });
}

export function unbindOrganizationTeacherClass(
  accessToken: string,
  teacherUserId: string,
  classOrganizationId: string
): Promise<void> {
  return request<void>(`/teachers/${teacherUserId}/classes/${classOrganizationId}`, {
    method: 'DELETE', header: authorization(accessToken)
  });
}
