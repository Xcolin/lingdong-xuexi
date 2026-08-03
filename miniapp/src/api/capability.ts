import { request } from './http';

export interface MiniappCapabilities {
  client: 'MINIAPP';
  studentCodeLoginEnabled: boolean;
  learningTaskManagementEnabled: boolean;
}

export function getMiniappCapabilities(): Promise<MiniappCapabilities> {
  return request<MiniappCapabilities>('/public/capabilities?client=MINIAPP');
}
