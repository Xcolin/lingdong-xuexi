import { request, type MiniappRequestOptions } from './http';
export interface OnboardingContext { userId: string; mobile: string | null; verifiedMobile: boolean; email: string | null; verifiedEmail: boolean }
export interface MyOrganization { id: string; name: string; typeCode: string; roleCodes: string[]; owner: boolean; canEnterFamily: boolean; canEnterInstitution: boolean }
export interface Region { id: string; name: string; typeCode: string }
export interface ApplicationInput { typeCode: 'SCHOOL' | 'TRAINING_INSTITUTION'; name: string; regionId: string; contactName: string }
export interface OrganizationApplication extends ApplicationInput { id: string; mobile: string | null; email?: string | null; status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'; taskId: string | null; organizationId: string | null; rejectionReason: string | null; submissionBatch: number; versionNo: number; createdAt: string; updatedAt: string }
function call<T>(token: string, path: string, options: MiniappRequestOptions = {}) {
  return request<T>(`/organization-onboarding${path}`, { ...options, header: { Authorization: `Bearer ${token}` } });
}
export const onboardingApi = {
  mobileCode: (token: string) => call<{ retryAfterSeconds: number; expiresAt: string }>(token, '/mobile-code', { method: 'POST', data: {} }),
  verifyMobile: (token: string, code: string) => call<void>(token, '/mobile-verify', { method: 'POST', data: { code } }),
  context: (token: string) => call<OnboardingContext>(token, '/context'),
  regions: (token: string) => call<Region[]>(token, '/regions'),
  organizations: (token: string) => call<MyOrganization[]>(token, '/my-organizations'),
  applications: (token: string) => call<OrganizationApplication[]>(token, '/applications'),
  application: (token: string, id: string) => call<OrganizationApplication>(token, `/applications/${encodeURIComponent(id)}`),
  family: (token: string, name: string, idempotencyKey: string, regionId: string) => call<MyOrganization>(token, '/families', { method: 'POST', data: { name, idempotencyKey, regionId } }),
  submit: (token: string, data: ApplicationInput) => call<OrganizationApplication>(token, '/applications', { method: 'POST', data }),
  resubmit: (token: string, item: OrganizationApplication, data: ApplicationInput) => call<OrganizationApplication>(token, `/applications/${encodeURIComponent(item.id)}/resubmit`, { method: 'POST', data: { ...data, versionNo: item.versionNo, submissionBatch: item.submissionBatch } }),
  withdraw: (token: string, item: OrganizationApplication) => call<OrganizationApplication>(token, `/applications/${encodeURIComponent(item.id)}/withdraw`, { method: 'POST', data: { versionNo: item.versionNo, submissionBatch: item.submissionBatch } })
};
export const applicationStatus = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' };
