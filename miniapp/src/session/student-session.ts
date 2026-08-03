import type { StudentSession } from '@/api/auth';

const SESSION_STORAGE_KEY = 'lingdong.student.session';
const DEVICE_STORAGE_KEY = 'lingdong.student.device-id';

export interface StoredStudentSession extends StudentSession {
  studentAccount: string;
}

export function saveStudentSession(session: StudentSession, studentAccount: string): void {
  const stored: StoredStudentSession = { ...session, studentAccount };
  uni.setStorageSync(SESSION_STORAGE_KEY, stored);
}

export function getStudentSession(): StoredStudentSession | null {
  const value = uni.getStorageSync(SESSION_STORAGE_KEY) as StoredStudentSession | undefined;
  return value?.accessToken ? value : null;
}

export function clearStudentSession(): void {
  uni.removeStorageSync(SESSION_STORAGE_KEY);
}

export function getOrCreateDeviceId(): string {
  const existing = uni.getStorageSync(DEVICE_STORAGE_KEY) as string;
  if (existing) return existing;
  const randomPart = Math.random().toString(36).slice(2, 12);
  const deviceId = `miniapp-${Date.now().toString(36)}-${randomPart}`;
  uni.setStorageSync(DEVICE_STORAGE_KEY, deviceId);
  return deviceId;
}

export function getDeviceName(): string {
  try {
    const system = uni.getSystemInfoSync();
    return `${system.brand || '微信'} ${system.model || '小程序设备'}`.trim().slice(0, 100);
  } catch {
    return '微信小程序设备';
  }
}
