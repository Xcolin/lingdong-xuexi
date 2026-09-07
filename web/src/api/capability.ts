import { apiClient } from './http';

export interface ClientCapabilities {
  client: 'WEB' | 'MINIAPP';
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
  organizationManagementEnabled?: boolean;
  learningTaskManagementEnabled: boolean;
  classManagementEnabled?: boolean;
  dictionaryManagementEnabled?: boolean;
  cacheManagementEnabled?: boolean;
  interfaceServiceManagementEnabled?: boolean;
  attachmentServiceEnabled?: boolean;
  importExportTemplateManagementEnabled?: boolean;
  dataImportValidationEnabled?: boolean;
  dataExportEnabled?: boolean;
  studentBatchImportEnabled?: boolean;
  teacherManagementEnabled?: boolean;
  studentExceptionReportEnabled?: boolean;
  attendanceManagementEnabled?: boolean;
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
