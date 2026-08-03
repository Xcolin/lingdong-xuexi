import { apiClient, authSessionStore, type AuthSession } from './http';

export interface CurrentUser {
  userId: string;
  sessionId: string;
  username: string;
  displayName: string;
  clientType: 'WEB';
  roleCodes: string[];
}

export interface DeviceSession {
  id: string;
  clientType: 'WEB' | 'MINIAPP';
  deviceId: string;
  deviceName: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
  lastActiveAt: string;
}

export const authApi = {
  currentUser(): Promise<CurrentUser> {
    return apiClient.get<CurrentUser>('/auth/me');
  },
  listDevices(): Promise<DeviceSession[]> {
    return apiClient.get<DeviceSession[]>('/auth/devices');
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
