import { request } from './http';

export interface OrganizationWorkbenchOrganization {
  id: string;
  name: string;
  typeCode: string;
}

export interface OrganizationWorkbenchContext {
  userId: string;
  username: string;
  displayName: string;
  permissionCodes: string[];
  organizations: OrganizationWorkbenchOrganization[];
}

/** 使用显式机构访问令牌查询当前管理员的直接管理组织。 */
export function getOrganizationWorkbenchContext(
  accessToken: string
): Promise<OrganizationWorkbenchContext> {
  return request<OrganizationWorkbenchContext>('/organization-workbench/context', {
    header: { Authorization: `Bearer ${accessToken}` }
  });
}

export interface OrganizationWorkbenchStatistics {
  campusCount: number | null;
  gradeCount: number | null;
  classCount: number | null;
  teacherCount: number | null;
  studentCount: number | null;
}

/** 使用显式机构访问令牌查询工作台统计汇总，无对应 read 权限的计数为 null。 */
export function getOrganizationWorkbenchStatistics(
  accessToken: string
): Promise<OrganizationWorkbenchStatistics> {
  return request<OrganizationWorkbenchStatistics>('/organization-workbench/statistics', {
    header: { Authorization: `Bearer ${accessToken}` }
  });
}
