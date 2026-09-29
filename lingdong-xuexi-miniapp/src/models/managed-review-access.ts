export type ManagedIdentity = 'teacher' | 'organization';
export interface ManagedIdentityUser { clientType: string; roleCodes: string[]; permissionCodes: string[] }
export function managedAccessAllowed(user: ManagedIdentityUser, identity: ManagedIdentity,
  organizationEnabled: boolean, taskEnabled: boolean, permission: string): boolean {
  return organizationEnabled && taskEnabled && user.clientType === 'MINIAPP'
    && !user.roleCodes.includes('SYS_AUDITOR')
    && user.roleCodes.includes(identity === 'teacher' ? 'TEACHER' : 'ORG_ADMIN')
    && user.permissionCodes.includes(permission);
}
