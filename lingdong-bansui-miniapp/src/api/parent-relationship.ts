import type { ParentSessionResult } from './auth';
import { request } from './http';

export type ParentRelationshipRole = 'PRIMARY_GUARDIAN' | 'SECONDARY_GUARDIAN';

export interface ParentRelationshipStudent {
  studentId: string;
  studentName: string;
  relationshipRole: ParentRelationshipRole;
  /** 关系建立日期（YYYY-MM-DD，数据可得时返回）。 */
  boundAt?: string | null;
}

export interface ParentReceivedInvitation {
  invitationId: string;
  studentName: string;
  inviterName: string | null;
  invitationType: 'SECONDARY_BIND' | 'PRIMARY_TRANSFER';
  createdAt: string | null;
  expiresAt: string | null;
}

export interface ParentInactiveRelationship {
  studentId: string;
  studentName: string;
  relationshipRole: ParentRelationshipRole;
  boundAt: string | null;
  unboundAt: string | null;
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

export interface RespondParentRelationshipInvitationPayload {
  mobile: string;
  smsCode: string;
  clientType: 'MINIAPP';
  deviceId: string;
  deviceName: string;
  agreementAccepted: boolean;
  agreementVersion: string;
}

export function listParentRelationshipStudents(
  accessToken: string
): Promise<ParentRelationshipStudent[]> {
  return parentRequest('/parent-relationships/students', accessToken);
}

/** 我收到的待处理关系邀请。 */
export function listReceivedInvitations(accessToken: string): Promise<ParentReceivedInvitation[]> {
  return parentRequest('/parent-relationships/received-invitations', accessToken);
}

/** 已失效的历史关系。 */
export function listInactiveRelationships(accessToken: string): Promise<ParentInactiveRelationship[]> {
  return parentRequest('/parent-relationships/inactive', accessToken);
}

export function getParentRelationships(
  studentId: string,
  accessToken: string
): Promise<ParentRelationship> {
  return parentRequest(`/students/${studentId}/parent-relationships`, accessToken);
}

export function createSecondaryParentInvitation(
  studentId: string,
  mobile: string,
  accessToken: string
): Promise<ParentRelationshipInvitation> {
  return parentRequest(`/students/${studentId}/secondary-parent-invitations`, accessToken, {
    method: 'POST', data: { mobile, clientType: 'MINIAPP' }
  });
}

export function createPrimaryParentTransferInvitation(
  studentId: string,
  mobile: string,
  accessToken: string
): Promise<ParentRelationshipInvitation> {
  return parentRequest(`/students/${studentId}/primary-transfer-invitations`, accessToken, {
    method: 'POST', data: { mobile, clientType: 'MINIAPP' }
  });
}

export function unbindSecondaryParent(
  studentId: string,
  parentUserId: string,
  accessToken: string
): Promise<void> {
  return parentRequest(
    `/students/${studentId}/secondary-parent-relationships/${parentUserId}`,
    accessToken,
    { method: 'DELETE' }
  );
}

export function unbindPrimaryParent(
  studentId: string,
  accessToken: string
): Promise<ParentRelationship> {
  return parentRequest(`/students/${studentId}/primary-parent-relationship`, accessToken, {
    method: 'DELETE'
  });
}

export function acceptParentRelationshipInvitation(
  invitationId: string,
  payload: RespondParentRelationshipInvitationPayload
): Promise<ParentSessionResult> {
  return request(`/parent-relationship-invitations/${invitationId}/acceptance`, {
    method: 'POST', data: payload
  });
}

export function rejectParentRelationshipInvitation(
  invitationId: string,
  payload: RespondParentRelationshipInvitationPayload
): Promise<void> {
  return request(`/parent-relationship-invitations/${invitationId}/rejection`, {
    method: 'POST', data: payload
  });
}

function parentRequest<T>(
  path: string,
  accessToken: string,
  options: { method?: UniApp.RequestOptions['method']; data?: UniApp.RequestOptions['data'] } = {}
): Promise<T> {
  return request<T>(path, {
    ...options,
    header: { Authorization: `Bearer ${accessToken}` }
  });
}
