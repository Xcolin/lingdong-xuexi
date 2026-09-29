import { apiClient } from '../../api/http';

export type ParentRelationshipRole = 'PRIMARY_GUARDIAN' | 'SECONDARY_GUARDIAN';

export interface ParentMobileManualRecoveryCandidate {
  studentId: string;
  studentName: string;
  organizationId: string;
  organizationName: string;
  parentUserId: string;
  parentDisplayName: string;
  maskedMobile: string;
  relationshipRole: ParentRelationshipRole;
}

export interface ParentMobileManualRecoveryTarget {
  studentId: string;
  parentUserId: string;
  newMobile: string;
}

export interface ParentMobileManualRecoveryInput extends ParentMobileManualRecoveryTarget {
  smsCode: string;
  reason: string;
  confirmation: string;
}

interface SmsCodeIssueResponse {
  expiresAt: string;
  retryAfterSeconds: number;
}

export const parentMobileManualRecoveryApi = {
  listCandidates(): Promise<ParentMobileManualRecoveryCandidate[]> {
    return apiClient.get<ParentMobileManualRecoveryCandidate[]>(
      '/organization-parent-mobile-recoveries/candidates'
    );
  },
  issueCode(input: ParentMobileManualRecoveryTarget): Promise<SmsCodeIssueResponse> {
    return apiClient.post<SmsCodeIssueResponse>(
      '/organization-parent-mobile-recoveries/codes', input
    );
  },
  recover(input: ParentMobileManualRecoveryInput): Promise<void> {
    return apiClient.post<void>('/organization-parent-mobile-recoveries', input);
  }
};
