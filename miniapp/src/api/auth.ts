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

export function logoutStudent(accessToken: string): Promise<void> {
  return request<void>('/auth/sessions/current', {
    method: 'DELETE',
    header: { Authorization: `Bearer ${accessToken}` }
  });
}
