import { request } from './http';

export interface MiniappCapabilities {
  client: 'MINIAPP';
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

export function getMiniappCapabilities(): Promise<MiniappCapabilities> {
  return request<MiniappCapabilities>('/public/capabilities?client=MINIAPP');
}
