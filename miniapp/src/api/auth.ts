import { request } from './http';

export interface CaptchaChallenge {
  challengeId: string;
  imageBase64: string;
  expiresAt: string;
}

export interface StudentSession {
  sessionId: string;
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
}

export interface OrganizationSession extends StudentSession { }

export type MiniappAuthIdentity = 'parent' | 'student' | 'organization';

export interface AccountDeviceSession {
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
  wechatEnabled: boolean;
  agreementVersion: string;
  codeExpiresInSeconds: number;
  retryAfterSeconds: number;
}

export interface ParentAuthState {
  onboardingRequired: boolean;
  agreementAcceptanceRequired: boolean;
  currentAgreementVersion: string;
}

export interface ParentSessionResult extends ParentAuthState {
  session: StudentSession;
}

export interface ParentWechatSessionExchange {
  bindingRequired: boolean;
  bindingTicket?: string;
  session?: ParentSessionResult;
}

export interface StudentCodeLoginPayload {
  studentAccount: string;
  loginCode: string;
  deviceId: string;
  deviceName: string;
  captchaChallengeId?: string;
  captchaAnswer?: string;
}

export interface StudentQrLoginPayload {
  qrContent: string;
  loginCode: string;
  deviceId: string;
  deviceName: string;
  captchaChallengeId?: string;
  captchaAnswer?: string;
}

export interface StudentQrSession extends StudentSession {
  studentAccount: string;
}

export interface StudentWechatSession extends StudentSession {
  studentAccount: string;
}

export interface StudentWechatSessionExchange {
  bindingRequired: boolean;
  bindingTicket?: string;
  session?: StudentWechatSession;
}

export interface StudentWechatBindingChallenge {
  verificationTicket: string;
  maskedMobile: string;
  expiresAt: string;
  retryAfterSeconds: number;
}

export function issueStudentCaptcha(studentAccount: string, deviceId: string): Promise<CaptchaChallenge> {
  return request<CaptchaChallenge>('/auth/student-captchas', {
    method: 'POST',
    data: { studentAccount, deviceId }
  });
}

export function loginStudentByCode(payload: StudentCodeLoginPayload): Promise<StudentSession> {
  return request<StudentSession>('/auth/student-sessions/code', {
    method: 'POST',
    data: payload
  });
}

export function loginStudentByQr(payload: StudentQrLoginPayload): Promise<StudentQrSession> {
  return request<StudentQrSession>('/auth/student-sessions/qr', {
    method: 'POST',
    data: payload
  });
}

export function issueStudentQrCaptcha(qrContent: string, deviceId: string): Promise<CaptchaChallenge> {
  return request<CaptchaChallenge>('/auth/student-qr-captchas', {
    method: 'POST',
    data: { qrContent, deviceId }
  });
}

/** 使用微信临时凭证交换学生会话或一次性绑定票据。 */
export function exchangeStudentWechatSession(payload: {
  temporaryCode: string;
  deviceId: string;
  deviceName: string;
}): Promise<StudentWechatSessionExchange> {
  return request<StudentWechatSessionExchange>('/auth/student-wechat-sessions', {
    method: 'POST',
    data: payload
  });
}

/** 校验学生账号与登录码后，由服务端向当前主监护人手机号发送验证码。 */
export function issueStudentWechatBindingCode(payload: {
  bindingTicket: string;
  studentAccount: string;
  loginCode: string;
  deviceId: string;
  deviceName: string;
  captchaChallengeId?: string;
  captchaAnswer?: string;
}): Promise<StudentWechatBindingChallenge> {
  return request<StudentWechatBindingChallenge>('/auth/student-wechat-binding-codes', {
    method: 'POST',
    data: payload
  });
}

/** 使用服务端固定的绑定上下文和主监护人短信完成学生微信绑定。 */
export function bindStudentWechat(payload: {
  verificationTicket: string;
  smsCode: string;
  deviceId: string;
  deviceName: string;
}): Promise<StudentWechatSession> {
  return request<StudentWechatSession>('/auth/student-wechat-bindings', {
    method: 'POST',
    data: payload
  });
}

export function logoutStudent(accessToken: string): Promise<void> {
  return request<void>('/auth/sessions/current', {
    method: 'DELETE',
    header: { Authorization: `Bearer ${accessToken}` }
  });
}

export function loginOrganizationByPassword(payload: {
  username: string;
  password: string;
  deviceId: string;
  deviceName: string;
}): Promise<OrganizationSession> {
  return request<OrganizationSession>('/auth/organization-sessions/password', {
    method: 'POST',
    data: payload
  });
}

export function logoutOrganization(accessToken: string): Promise<void> {
  return request<void>('/auth/sessions/current', {
    method: 'DELETE',
    header: { Authorization: `Bearer ${accessToken}` }
  });
}

export function getParentAuthContext(): Promise<ParentAuthContext> {
  return request<ParentAuthContext>('/public/parent-auth-context');
}

export function issueParentSmsCode(mobile: string): Promise<{ expiresAt: string; retryAfterSeconds: number }> {
  return request('/auth/parent-sms-codes', {
    method: 'POST',
    data: { mobile, purpose: 'REGISTER_OR_LOGIN', clientType: 'MINIAPP' }
  });
}

export function issueParentWechatBindingSmsCode(
  mobile: string
): Promise<{ expiresAt: string; retryAfterSeconds: number }> {
  return request('/auth/parent-sms-codes', {
    method: 'POST',
    data: { mobile, purpose: 'WECHAT_BIND', clientType: 'MINIAPP' }
  });
}

export function exchangeParentWechatSession(payload: {
  temporaryCode: string;
  deviceId: string;
  deviceName: string;
}): Promise<ParentWechatSessionExchange> {
  return request<ParentWechatSessionExchange>('/auth/parent-wechat-sessions', {
    method: 'POST',
    data: payload
  });
}

export function bindParentWechat(payload: {
  bindingTicket: string;
  mobile: string;
  smsCode: string;
  deviceId: string;
  deviceName: string;
  agreementAccepted: boolean;
  agreementVersion: string;
}): Promise<ParentSessionResult> {
  return request<ParentSessionResult>('/auth/parent-wechat-bindings', {
    method: 'POST',
    data: payload
  });
}

export function loginParentBySms(payload: {
  mobile: string;
  code: string;
  deviceId: string;
  deviceName: string;
  agreementAccepted: boolean;
  agreementVersion: string;
}): Promise<ParentSessionResult> {
  return request<ParentSessionResult>('/auth/parent-sessions/sms', {
    method: 'POST',
    data: { ...payload, clientType: 'MINIAPP' }
  });
}

export function loginParentByPassword(payload: {
  mobile: string;
  password: string;
  deviceId: string;
  deviceName: string;
}): Promise<ParentSessionResult> {
  return request<ParentSessionResult>('/auth/parent-sessions/password', {
    method: 'POST',
    data: { ...payload, clientType: 'MINIAPP' }
  });
}

export function getParentState(accessToken: string): Promise<ParentAuthState> {
  return parentAuthenticatedRequest<ParentAuthState>('/auth/parent-state', accessToken);
}

export function acceptParentAgreement(accessToken: string, agreementVersion: string): Promise<void> {
  return parentAuthenticatedRequest<void>('/auth/parent-agreement-acceptances', accessToken, {
    method: 'POST',
    data: { agreementVersion }
  });
}

export function completeParentOnboarding(accessToken: string): Promise<void> {
  return parentAuthenticatedRequest<void>('/parent-onboarding/completion', accessToken, { method: 'POST' });
}

export function logoutParent(accessToken: string): Promise<void> {
  return parentAuthenticatedRequest<void>('/auth/sessions/current', accessToken, { method: 'DELETE' });
}

/** 账号安全请求必须显式声明身份并传入该身份的访问令牌。 */
export function listAccountDevices(
  identity: MiniappAuthIdentity,
  accessToken: string
): Promise<AccountDeviceSession[]> {
  return accountSecurityRequest('/auth/devices', identity, accessToken);
}

export function listAccountSecurityEvents(
  identity: MiniappAuthIdentity,
  accessToken: string
): Promise<AccountSecurityEvent[]> {
  return accountSecurityRequest('/auth/security-events', identity, accessToken);
}

export function signOutAccountDevice(
  identity: MiniappAuthIdentity,
  accessToken: string,
  sessionId: string
): Promise<void> {
  return accountSecurityRequest(`/auth/devices/${sessionId}`, identity, accessToken, { method: 'DELETE' });
}

export function signOutAllAccountDevices(
  identity: MiniappAuthIdentity,
  accessToken: string
): Promise<void> {
  return accountSecurityRequest('/auth/devices/sign-out-all', identity, accessToken, { method: 'POST' });
}

export function markAccountSecurityEventRead(
  identity: MiniappAuthIdentity,
  accessToken: string,
  eventId: string
): Promise<void> {
  return accountSecurityRequest(`/auth/security-events/${eventId}/read`, identity, accessToken, { method: 'POST' });
}

export function markAllAccountSecurityEventsRead(
  identity: MiniappAuthIdentity,
  accessToken: string
): Promise<void> {
  return accountSecurityRequest('/auth/security-events/read-all', identity, accessToken, { method: 'POST' });
}

function accountSecurityRequest<T>(
  path: string,
  identity: MiniappAuthIdentity,
  accessToken: string,
  options: { method?: UniApp.RequestOptions['method'] } = {}
): Promise<T> {
  if (identity !== 'parent' && identity !== 'student' && identity !== 'organization') {
    return Promise.reject(new Error('不支持的账号身份'));
  }
  return request<T>(path, {
    ...options,
    header: { Authorization: `Bearer ${accessToken}` }
  });
}

/** 家长请求显式接收家长令牌，避免隐式读取学生会话。 */
function parentAuthenticatedRequest<T>(
  path: string,
  accessToken: string,
  options: { method?: UniApp.RequestOptions['method']; data?: UniApp.RequestOptions['data'] } = {}
): Promise<T> {
  return request<T>(path, {
    ...options,
    header: { Authorization: `Bearer ${accessToken}` }
  });
}
