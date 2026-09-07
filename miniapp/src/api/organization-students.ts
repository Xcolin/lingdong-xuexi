import { request } from './http';

export interface OrganizationStudentRelationship {
  studentId: string;
  studentName: string;
  gradeCode: string | null;
  enrollmentOrganizationId: string;
  enrollmentOrganizationName: string;
  currentClassOrganizationId: string | null;
  currentClassOrganizationName: string | null;
}

export interface OrganizationClassOption {
  id: string;
  name: string;
}

export interface OrganizationRelationshipResult {
  studentId: string;
  enrollmentOrganizationId: string;
  currentClassOrganizationId: string | null;
  status: 'ACTIVE' | 'INACTIVE';
}

function authorization(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

/** 使用机构小程序会话读取当前管理员范围内的活动学员。 */
export function listOrganizationStudents(
  accessToken: string
): Promise<OrganizationStudentRelationship[]> {
  return request<OrganizationStudentRelationship[]>('/students/organization-relationships', {
    header: authorization(accessToken)
  });
}

/** 使用机构小程序会话读取可用班级候选项。 */
export function listOrganizationClasses(
  accessToken: string
): Promise<OrganizationClassOption[]> {
  return request<OrganizationClassOption[]>('/students/organization-relationship-classes', {
    header: authorization(accessToken)
  });
}

export function transferOrganizationStudent(
  accessToken: string,
  studentId: string,
  classOrganizationId: string,
  reason: string
): Promise<OrganizationRelationshipResult> {
  return request<OrganizationRelationshipResult>(`/students/${studentId}/class-transfers`, {
    method: 'POST',
    header: authorization(accessToken),
    data: { classOrganizationId, reason }
  });
}

export function deactivateOrganizationStudent(
  accessToken: string,
  studentId: string,
  organizationId: string,
  reason: string
): Promise<OrganizationRelationshipResult> {
  return request<OrganizationRelationshipResult>(`/students/${studentId}/organization-deactivations`, {
    method: 'POST',
    header: authorization(accessToken),
    data: { organizationId, reason }
  });
}
