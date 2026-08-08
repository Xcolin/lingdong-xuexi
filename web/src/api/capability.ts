import { apiClient } from './http';

export interface ClientCapabilities {
  client: 'WEB' | 'MINIAPP';
  studentCodeLoginEnabled: boolean;
  studentQrLoginEnabled: boolean;
  learningTaskManagementEnabled: boolean;
  previousDayTaskCopyEnabled: boolean;
  learningTaskTemplateEnabled: boolean;
  growthPointQueryEnabled: boolean;
  growthPointCorrectionEnabled: boolean;
  rewardExchangeEnabled: boolean;
  dailyGrowthReviewEnabled: boolean;
  periodicGrowthReportEnabled: boolean;
}

export const capabilityApi = {
  web(): Promise<ClientCapabilities> {
    return apiClient.get<ClientCapabilities>('/public/capabilities?client=WEB');
  }
};
