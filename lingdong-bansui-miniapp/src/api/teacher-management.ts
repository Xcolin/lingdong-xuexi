import { request } from './http';

export type TeacherStatus = 'ENABLED' | 'DISABLED' | 'LOCKED';

export interface TeacherSubject {
  teacherUserId: string;
  institutionOrganizationId: string;
  subjectCode: string | null;
  versionNo: number;
}

export interface TeacherSubjectOption { code: string; name: string }
export interface TeacherSubjectSummary {
  teacherUserId: string;
  institutionOrganizationId: string;
  subjectCode: string | null;
  subjectName: string | null;
  classCount: number;
}

export function listTeacherSubjectSummaries(accessToken: string,
  items: {teacherUserId:string;institutionOrganizationId:string}[]): Promise<TeacherSubjectSummary[]> {
  if (!items.length) return Promise.resolve([]);
  return request<TeacherSubjectSummary[]>('/organization-teachers/subject-summaries', {
    method: 'POST', header: authorization(accessToken), data: {items}
  });
}

export function listTeacherSubjectOptions(accessToken: string): Promise<TeacherSubjectOption[]> {
  return request<TeacherSubjectOption[]>('/organization-teachers/subject-options', {
    header: authorization(accessToken)
  });
}

function teacherSubjectPath(teacherUserId: string, institutionOrganizationId: string): string {
  return `/organization-teachers/${encodeURIComponent(teacherUserId)}/institutions/${encodeURIComponent(institutionOrganizationId)}/subject`;
}

export function readTeacherSubject(accessToken: string, teacherUserId: string,
  institutionOrganizationId: string): Promise<TeacherSubject> {
  return request<TeacherSubject>(teacherSubjectPath(teacherUserId, institutionOrganizationId), {
    header: authorization(accessToken)
  });
}

export function updateTeacherSubject(accessToken: string, teacherUserId: string,
  institutionOrganizationId: string, input: { subjectCode: string | null; versionNo: number }): Promise<TeacherSubject> {
  return request<TeacherSubject>(teacherSubjectPath(teacherUserId, institutionOrganizationId), {
    method: 'PUT', header: authorization(accessToken), data: input
  });
}

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
  pageSize = 20,
  filters: {status?:TeacherStatus;schoolId?:string;classOrganizationId?:string;campusOrganizationId?:string;subjectCode?:string} = {}
): Promise<OrganizationTeacherPage> {
  const parameters = [`page=${page}`, `pageSize=${pageSize}`];
  if(filters.subjectCode)parameters.push(`subjectCode=${encodeURIComponent(filters.subjectCode)}`);
  if(filters.campusOrganizationId)parameters.push(`campusOrganizationId=${encodeURIComponent(filters.campusOrganizationId)}`);
  if(filters.status)parameters.push(`status=${encodeURIComponent(filters.status)}`);
  if(filters.schoolId)parameters.push(`schoolId=${encodeURIComponent(filters.schoolId)}`);
  if(filters.classOrganizationId)parameters.push(`classOrganizationId=${encodeURIComponent(filters.classOrganizationId)}`);
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

export function changeOrganizationTeacherClasses(accessToken: string, teacherUserId: string,
  additions: string[], removals: string[]): Promise<void> {
  return request<void>(`/teachers/${encodeURIComponent(teacherUserId)}/classes/changes`, {
    method: 'PUT', header: authorization(accessToken), data: { additions, removals }
  });
}
export interface TeacherCampusOption {id:string;name:string;schoolOrganizationId:string|null;typeCode:string;sortOrder:number;status:string;effectiveStatus:string}
export function listTeacherCampuses(accessToken:string,schoolId?:string):Promise<TeacherCampusOption[]>{return request<TeacherCampusOption[]>(`/classes/campuses${schoolId?'?schoolOrganizationId='+encodeURIComponent(schoolId):''}`,{header:authorization(accessToken)});}
