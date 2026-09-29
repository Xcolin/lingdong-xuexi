import { request, ApiError } from './http';
import { getMiniappCapabilities } from './capability';
import { getTeacherWorkbenchContext } from './teacher-workbench';
import { getOrganizationWorkbenchContext } from './organization-workbench';
import { managedAccessAllowed, type ManagedIdentity, type ManagedIdentityUser } from '@/models/managed-review-access';
/** 复核实际会话身份、实时权限、功能以及当前角色范围，不能仅相信首页缓存。 */
export async function requireManagedAccess(token: string, identity: ManagedIdentity, permission: string) {
  const [user, capabilities] = await Promise.all([
    request<ManagedIdentityUser>('/auth/me', { header: { Authorization: `Bearer ${token}` } }),
    getMiniappCapabilities()
  ]);
  if (!managedAccessAllowed(user, identity, capabilities.organizationMiniappAuthEnabled,
      capabilities.learningTaskManagementEnabled, permission)) throw new ApiError(403, { message: '任务功能未开启或无操作权限' });
  const context = identity === 'teacher' ? await getTeacherWorkbenchContext(token) : await getOrganizationWorkbenchContext(token);
  return { user, context };
}

