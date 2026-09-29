import type { StudentSession } from '@/api/auth';

const SESSION_STORAGE_KEY = 'lingdong.parent.session';
const DEVICE_STORAGE_KEY = 'lingdong.parent.device-id';

export interface StoredParentSession extends StudentSession {
  mobile: string;
}

/** 家长会话使用独立存储键，禁止覆盖学生身份。 */
export function saveParentSession(session: StudentSession, mobile: string): void {
  uni.setStorageSync(SESSION_STORAGE_KEY, { ...session, mobile } satisfies StoredParentSession);
}

export function getParentSession(): StoredParentSession | null {
  const value = uni.getStorageSync(SESSION_STORAGE_KEY) as StoredParentSession | undefined;
  return value?.accessToken ? value : null;
}

export function clearParentSession(): void {
  uni.removeStorageSync(SESSION_STORAGE_KEY);
}

export function getOrCreateParentDeviceId(): string {
  const existing = uni.getStorageSync(DEVICE_STORAGE_KEY) as string;
  if (existing) return existing;
  const randomPart = Math.random().toString(36).slice(2, 12);
  const deviceId = `parent-miniapp-${Date.now().toString(36)}-${randomPart}`;
  uni.setStorageSync(DEVICE_STORAGE_KEY, deviceId);
  return deviceId;
}

export function getParentDeviceName(): string {
  try {
    const system = uni.getSystemInfoSync();
    return `${system.brand || '微信'} ${system.model || '家长小程序'}`.trim().slice(0, 100);
  } catch {
    return '家长小程序设备';
  }
}
