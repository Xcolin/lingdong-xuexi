import { request } from './http';

export interface OrganizationClass {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  typeCode: 'SCHOOL' | 'CLASS';
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
  effectiveStatus: 'ENABLED' | 'DISABLED';
  versionNo: number;
}

export interface OrganizationTeacherOption {
  userId: string;
  displayName: string;
  classOrganizationIds: string[];
}

export interface TeacherClassRelation {
  id: string;
  teacherUserId: string;
  classOrganizationId: string;
  status: 'ACTIVE' | 'INACTIVE';
}

function authorization(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

export function listManageableSchools(accessToken: string): Promise<OrganizationClass[]> {
  return request<OrganizationClass[]>('/classes/schools', {
    header: authorization(accessToken)
  });
}

export function listManagedClasses(accessToken: string): Promise<OrganizationClass[]> {
  return request<OrganizationClass[]>('/classes', {
    header: authorization(accessToken)
  });
}

export function createManagedClass(
  accessToken: string,
  schoolOrganizationId: string,
  name: string,
  sortOrder: number
): Promise<OrganizationClass> {
  return request<OrganizationClass>('/classes', {
    method: 'POST', header: authorization(accessToken),
    data: { schoolOrganizationId, name, sortOrder }
  });
}

export function updateManagedClass(
  accessToken: string,
  classId: string,
  name: string,
  sortOrder: number,
  versionNo: number
): Promise<OrganizationClass> {
  return request<OrganizationClass>(`/classes/${classId}`, {
    method: 'PUT', header: authorization(accessToken),
    data: { name, sortOrder, versionNo }
  });
}

export function changeManagedClassStatus(
  accessToken: string,
  item: OrganizationClass
): Promise<OrganizationClass> {
  const action = item.status === 'ENABLED' ? 'disable' : 'enable';
  return request<OrganizationClass>(`/classes/${item.id}/${action}`, {
    method: 'POST', header: authorization(accessToken), data: { versionNo: item.versionNo }
  });
}

export function listOrganizationTeachers(
  accessToken: string,
  classId: string
): Promise<OrganizationTeacherOption[]> {
  return request<OrganizationTeacherOption[]>(
    `/learning-task-options/teachers?classId=${encodeURIComponent(classId)}`,
    { header: authorization(accessToken) }
  );
}

export function bindOrganizationTeacher(
  accessToken: string,
  teacherUserId: string,
  classId: string
): Promise<TeacherClassRelation> {
  return request<TeacherClassRelation>(`/teachers/${teacherUserId}/classes/${classId}`, {
    method: 'PUT', header: authorization(accessToken), data: {}
  });
}

export function unbindOrganizationTeacher(
  accessToken: string,
  teacherUserId: string,
  classId: string
): Promise<void> {
  return request<void>(`/teachers/${teacherUserId}/classes/${classId}`, {
    method: 'DELETE', header: authorization(accessToken)
  });
}
