import { request } from './http';

export interface MiniappCapabilities {
  client: 'MINIAPP';
  studentCodeLoginEnabled: boolean;
  studentQrLoginEnabled: boolean;
  studentWechatAuthEnabled?: boolean;
  parentRelationshipManagementEnabled: boolean;
  organizationMiniappAuthEnabled: boolean;
  studentOrganizationRelationshipEnabled: boolean;
  accountSecurityManagementEnabled: boolean;
  parentAccountLifecycleEnabled: boolean;
  parentMobileManualRecoveryEnabled?: boolean;
  studentAccountCancellationEnabled?: boolean;
  learningTaskManagementEnabled: boolean;
  classManagementEnabled?: boolean;
  teacherManagementEnabled?: boolean;
  studentExceptionReportEnabled?: boolean;
  attendanceManagementEnabled?: boolean;
  anonymousClassRankEnabled?: boolean;
  attachmentServiceEnabled?: boolean;
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
