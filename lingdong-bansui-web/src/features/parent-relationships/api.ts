import { apiClient, authSessionStore, type AuthSession } from '../../api/http';

export type ParentRelationshipRole = 'PRIMARY_GUARDIAN' | 'SECONDARY_GUARDIAN';

export interface ParentStudentOption {
  studentId: string;
  studentName: string;
  relationshipRole: ParentRelationshipRole;
}

export interface ParentRelationshipMember {
  userId: string;
  displayName: string | null;
  mobileMasked: string | null;
  relationshipRole: ParentRelationshipRole;
}

export interface ParentRelationship {
  studentId: string;
  primaryParentUserId: string | null;
  secondaryParentUserId: string | null;
  primaryParent: ParentRelationshipMember | null;
  secondaryParent: ParentRelationshipMember | null;
}

export interface ParentRelationshipInvitation {
  invitationId: string;
  maskedMobile: string;
  expiresAt: string;
}

export interface ParentInvitationSessionResult {
  session: AuthSession;
  onboardingRequired: boolean;
  agreementAcceptanceRequired: boolean;
  currentAgreementVersion: string;
}

export interface RespondParentRelationshipInvitationInput {
  mobile: string;
  smsCode: string;
  clientType: 'WEB';
  deviceId: string;
  deviceName: string;
  agreementAccepted: boolean;
  agreementVersion: string;
}

export const parentRelationshipApi = {
  listStudents(): Promise<ParentStudentOption[]> {
    return apiClient.get<ParentStudentOption[]>('/parent-relationships/students');
  },
  get(studentId: string): Promise<ParentRelationship> {
    return apiClient.get<ParentRelationship>(`/students/${studentId}/parent-relationships`);
  },
  createSecondaryInvitation(studentId: string, mobile: string): Promise<ParentRelationshipInvitation> {
    return apiClient.post<ParentRelationshipInvitation>(
      `/students/${studentId}/secondary-parent-invitations`, { mobile, clientType: 'WEB' }
    );
  },
  createPrimaryTransferInvitation(studentId: string, mobile: string): Promise<ParentRelationshipInvitation> {
    return apiClient.post<ParentRelationshipInvitation>(
      `/students/${studentId}/primary-transfer-invitations`, { mobile, clientType: 'WEB' }
    );
  },
  unbindSecondary(studentId: string, parentUserId: string): Promise<void> {
    return apiClient.delete(`/students/${studentId}/secondary-parent-relationships/${parentUserId}`);
  },
  unbindPrimary(studentId: string): Promise<ParentRelationship> {
    return apiClient.deleteWithResponse<ParentRelationship>(
      `/students/${studentId}/primary-parent-relationship`
    );
  },
  async acceptInvitation(
    invitationId: string,
    input: RespondParentRelationshipInvitationInput
  ): Promise<ParentInvitationSessionResult> {
    const result = await apiClient.publicPost<ParentInvitationSessionResult>(
      `/parent-relationship-invitations/${invitationId}/acceptance`, input
    );
    authSessionStore.save(result.session);
    return result;
  },
  rejectInvitation(invitationId: string, input: RespondParentRelationshipInvitationInput): Promise<void> {
    return apiClient.publicPost<void>(
      `/parent-relationship-invitations/${invitationId}/rejection`, input
    );
  }
};
