import { apiClient, authSessionStore, type AuthSession } from './http';

export interface CurrentUser {
  userId: string;
  sessionId: string;
  username: string;
  displayName: string;
  clientType: 'WEB';
  roleCodes: string[];
  permissionCodes: string[];
}

export interface DeviceSession {
  id: string;
  clientType: 'WEB' | 'MINIAPP';
  deviceName: string;
  current: boolean;
  accessExpiresAt: string;
  refreshExpiresAt: string;
  lastActiveAt: string;
}

export interface AccountSecurityEvent {
  id: string;
  eventType: 'NEW_DEVICE_LOGIN' | 'DEVICE_REVOKED' | 'ALL_SESSIONS_REVOKED';
  riskLevel: 'INFO' | 'WARNING';
  clientType: 'WEB' | 'MINIAPP';
  deviceName: string;
  status: 'UNREAD' | 'READ';
  occurredAt: string;
  readAt: string | null;
}

export interface ParentAuthContext {
  enabled: boolean;
  agreementVersion: string;
  codeExpiresInSeconds: number;
  retryAfterSeconds: number;
}

export interface ParentSmsCodeResult {
  expiresAt: string;
  retryAfterSeconds: number;
}

export interface ParentSmsLoginResult {
  session: AuthSession;
  onboardingRequired: boolean;
  agreementAcceptanceRequired: boolean;
  currentAgreementVersion: string;
}

export interface ParentAuthState {
  onboardingRequired: boolean;
  agreementAcceptanceRequired: boolean;
  currentAgreementVersion: string;
}

export interface ParentAccountLifecycleState {
  maskedMobile: string;
  activeStudentRelationshipCount: number;
  cancellationStatus: 'NONE' | 'COOLING_OFF' | 'READY_FOR_FINALIZATION';
  cancellationId: string | null;
  requestedAt: string | null;
  coolingEndsAt: string | null;
}

export interface ParentAccountCancellationResult {
  cancellationId: string;
  status: 'COOLING_OFF';
  requestedAt: string;
  coolingEndsAt: string;
}

export const authApi = {
  parentAuthContext(): Promise<ParentAuthContext> {
    return apiClient.publicGet<ParentAuthContext>('/public/parent-auth-context');
  },
  issueParentSmsCode(input: {
    mobile: string;
    purpose: 'REGISTER_OR_LOGIN' | 'RESET_PASSWORD';
    clientType: 'WEB';
  }): Promise<ParentSmsCodeResult> {
    return apiClient.publicPost<ParentSmsCodeResult>('/auth/parent-sms-codes', input);
  },
  completeParentOnboarding(): Promise<void> {
    return apiClient.post<void>('/parent-onboarding/completion', {});
  },
  parentState(): Promise<ParentAuthState> {
    return apiClient.get<ParentAuthState>('/auth/parent-state');
  },
  getParentAccountLifecycle(): Promise<ParentAccountLifecycleState> {
    return apiClient.get<ParentAccountLifecycleState>('/auth/parent-account-lifecycle');
  },
  issueCurrentMobileCode(): Promise<ParentSmsCodeResult> {
    return apiClient.post<ParentSmsCodeResult>('/auth/parent-mobile-change/current-codes', {});
  },
  verifyCurrentMobile(code: string): Promise<{ ticket: string }> {
    return apiClient.post<{ ticket: string }>('/auth/parent-mobile-change-tickets', { code });
  },
  issueNewMobileCode(ticket: string, newMobile: string): Promise<ParentSmsCodeResult> {
    return apiClient.post<ParentSmsCodeResult>('/auth/parent-mobile-change/new-codes', { ticket, newMobile });
  },
  changeParentMobile(ticket: string, newMobile: string, code: string): Promise<void> {
    return apiClient.post<void>('/auth/parent-mobile-changes', { ticket, newMobile, code });
  },
  issueParentCancellationCode(): Promise<ParentSmsCodeResult> {
    return apiClient.post<ParentSmsCodeResult>('/auth/parent-account-cancellation-codes', {});
  },
  requestParentCancellation(code: string, confirmation: string): Promise<ParentAccountCancellationResult> {
    return apiClient.post<ParentAccountCancellationResult>(
      '/auth/parent-account-cancellations', { code, confirmation }
    );
  },
  revokeParentCancellation(): Promise<void> {
    return apiClient.delete('/auth/parent-account-cancellations/current');
  },
  acceptParentAgreement(agreementVersion: string): Promise<void> {
    return apiClient.post<void>('/auth/parent-agreement-acceptances', { agreementVersion });
  },
  async loginParentBySms(input: {
    mobile: string;
    code: string;
    clientType: 'WEB';
    deviceId: string;
    deviceName: string;
    agreementAccepted: boolean;
    agreementVersion: string;
  }): Promise<ParentSmsLoginResult> {
    const result = await apiClient.publicPost<ParentSmsLoginResult>('/auth/parent-sessions/sms', input);
    authSessionStore.save(result.session);
    return result;
  },
  currentUser(): Promise<CurrentUser> {
    return apiClient.get<CurrentUser>('/auth/me');
  },
  listDevices(): Promise<DeviceSession[]> {
    return apiClient.get<DeviceSession[]>('/auth/devices');
  },
  listSecurityEvents(unreadOnly = false): Promise<AccountSecurityEvent[]> {
    return apiClient.get<AccountSecurityEvent[]>(`/auth/security-events?unreadOnly=${unreadOnly}`);
  },
  markSecurityEventRead(eventId: string): Promise<void> {
    return apiClient.post<void>(`/auth/security-events/${eventId}/read`, {});
  },
  markAllSecurityEventsRead(): Promise<void> {
    return apiClient.post<void>('/auth/security-events/read-all', {});
  },
  signOutCurrent(): Promise<void> {
    return apiClient.delete('/auth/sessions/current');
  },
  signOutDevice(sessionId: string): Promise<void> {
    return apiClient.delete(`/auth/devices/${sessionId}`);
  },
  signOutAllDevices(): Promise<void> {
    return apiClient.post<void>('/auth/devices/sign-out-all', {});
  },
  clearLocalSession(): void {
    authSessionStore.clear();
  },
  hasLocalSession(): boolean {
    return authSessionStore.get() !== null;
  },
  login(input: Parameters<typeof apiClient.loginByPassword>[0]): Promise<AuthSession> {
    return apiClient.loginByPassword(input);
  }
};
