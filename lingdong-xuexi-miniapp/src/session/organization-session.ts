import type { OrganizationSession } from '@/api/auth';

const SESSION_STORAGE_KEY = 'lingdong.organization.session';
const DEVICE_STORAGE_KEY = 'lingdong.organization.device-id';

/** 保存机构管理员小程序会话，不覆盖家长或学生身份。 */
export function saveOrganizationSession(session: OrganizationSession): void {
  uni.setStorageSync(SESSION_STORAGE_KEY, session);
}

export function getOrganizationSession(): OrganizationSession | null {
  const value = uni.getStorageSync(SESSION_STORAGE_KEY) as OrganizationSession | undefined;
  return value?.accessToken ? value : null;
}

export function clearOrganizationSession(): void {
  uni.removeStorageSync(SESSION_STORAGE_KEY);
}

export function getOrCreateOrganizationDeviceId(): string {
  const existing = uni.getStorageSync(DEVICE_STORAGE_KEY) as string;
  if (existing) return existing;
  const randomPart = Math.random().toString(36).slice(2, 12);
  const deviceId = `organization-miniapp-${Date.now().toString(36)}-${randomPart}`;
  uni.setStorageSync(DEVICE_STORAGE_KEY, deviceId);
  return deviceId;
}

export function getOrganizationDeviceName(): string {
  try {
    const system = uni.getSystemInfoSync();
    return `${system.brand || '微信'} ${system.model || '机构小程序'}`.trim().slice(0, 100);
  } catch {
    return '机构小程序设备';
  }
}
