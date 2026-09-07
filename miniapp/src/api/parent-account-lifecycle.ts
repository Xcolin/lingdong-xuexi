import { request } from './http';

export interface ParentAccountLifecycleState {
  maskedMobile: string;
  activeStudentRelationshipCount: number;
  cancellationStatus: 'NONE' | 'COOLING_OFF' | 'READY_FOR_FINALIZATION';
  cancellationId: string | null;
  requestedAt: string | null;
  coolingEndsAt: string | null;
}

export interface ParentSmsCodeResult {
  expiresAt: string;
  retryAfterSeconds: number;
}

function authenticatedRequest<T>(
  path: string,
  accessToken: string,
  options: { method?: UniApp.RequestOptions['method']; data?: UniApp.RequestOptions['data'] } = {}
): Promise<T> {
  return request<T>(path, {
    ...options,
    header: { Authorization: `Bearer ${accessToken}` }
  });
}

export function getParentAccountLifecycle(accessToken: string): Promise<ParentAccountLifecycleState> {
  return authenticatedRequest('/auth/parent-account-lifecycle', accessToken);
}

export function issueCurrentMobileCode(accessToken: string): Promise<ParentSmsCodeResult> {
  return authenticatedRequest('/auth/parent-mobile-change/current-codes', accessToken, { method: 'POST' });
}

export function verifyCurrentMobile(accessToken: string, code: string): Promise<{ ticket: string }> {
  return authenticatedRequest('/auth/parent-mobile-change-tickets', accessToken, {
    method: 'POST', data: { code }
  });
}

export function issueNewMobileCode(
  accessToken: string,
  ticket: string,
  newMobile: string
): Promise<ParentSmsCodeResult> {
  return authenticatedRequest('/auth/parent-mobile-change/new-codes', accessToken, {
    method: 'POST', data: { ticket, newMobile }
  });
}

export function changeParentMobile(
  accessToken: string,
  ticket: string,
  newMobile: string,
  code: string
): Promise<void> {
  return authenticatedRequest('/auth/parent-mobile-changes', accessToken, {
    method: 'POST', data: { ticket, newMobile, code }
  });
}

export function issueParentCancellationCode(accessToken: string): Promise<ParentSmsCodeResult> {
  return authenticatedRequest('/auth/parent-account-cancellation-codes', accessToken, { method: 'POST' });
}

export function requestParentCancellation(
  accessToken: string,
  code: string,
  confirmation: string
): Promise<void> {
  return authenticatedRequest('/auth/parent-account-cancellations', accessToken, {
    method: 'POST', data: { code, confirmation }
  });
}

export function revokeParentCancellation(accessToken: string): Promise<void> {
  return authenticatedRequest('/auth/parent-account-cancellations/current', accessToken, { method: 'DELETE' });
}
