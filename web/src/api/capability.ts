import { apiClient } from './http';

export interface ClientCapabilities {
  client: 'WEB' | 'MINIAPP';
  studentCodeLoginEnabled: boolean;
  learningTaskManagementEnabled: boolean;
}

export const capabilityApi = {
  web(): Promise<ClientCapabilities> {
    return apiClient.get<ClientCapabilities>('/public/capabilities?client=WEB');
  }
};
