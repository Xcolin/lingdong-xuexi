import { request } from './http';

export interface ParentMobileManualRecoveryCandidate {
  studentId: string;
  studentName: string;
  organizationId: string;
  organizationName: string;
  parentUserId: string;
  parentDisplayName: string;
  maskedMobile: string;
  relationshipRole: 'PRIMARY_GUARDIAN' | 'SECONDARY_GUARDIAN';
}

export interface ParentMobileManualRecoveryInput {
  studentId: string;
  parentUserId: string;
  newMobile: string;
  smsCode: string;
  reason: string;
  confirmation: string;
}

function authorization(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

/** 读取当前机构管理员组织范围内可人工换绑的活动家长关系。 */
export function listParentMobileManualRecoveryCandidates(
  accessToken: string
): Promise<ParentMobileManualRecoveryCandidate[]> {
  return request<ParentMobileManualRecoveryCandidate[]>(
    '/organization-parent-mobile-recoveries/candidates',
    { header: authorization(accessToken) }
  );
}

/** 向已核验的新手机号发送人工换绑专用验证码。 */
export function issueParentMobileManualRecoveryCode(
  accessToken: string,
  studentId: string,
  parentUserId: string,
  newMobile: string
): Promise<void> {
  return request<void>('/organization-parent-mobile-recoveries/codes', {
    method: 'POST',
    header: authorization(accessToken),
    data: { studentId, parentUserId, newMobile }
  });
}

/** 提交线下核验事实并完成手机号换绑。 */
export function recoverParentMobile(
  accessToken: string,
  input: ParentMobileManualRecoveryInput
): Promise<void> {
  return request<void>('/organization-parent-mobile-recoveries', {
    method: 'POST',
    header: authorization(accessToken),
    data: input
  });
}
