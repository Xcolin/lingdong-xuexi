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
