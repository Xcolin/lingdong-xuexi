import { describe, expect, it } from 'vitest';
import type { CurrentUser } from '../api/auth';
import type { ClientCapabilities } from '../api/capability';
import { canExportStudentTasks, canExportOrgTaskStatistics, canExportAttendanceLedger, canExportAttachments, canExportExceptions, canExportRewards, canExportSystemTasks, canExportCache, canExportInterfaceLedger, canAccessAttachmentManagement, canAccessCacheManagement, canAccessDictionaryManagement, canAccessExceptionReports, canAccessExportJobs, canAccessGrowthPoints, canAccessGrowthReviews, canAccessImportExportTemplates, canAccessImportJobs, canAccessInterfaceServiceManagement, canAccessLearningTasks, canAccessOrganizationPage, canAccessParentRelationships, canAccessRewards, canAccessStudentQrLogin, canAccessTeacherManagement } from './App';

const custom: CurrentUser = { userId: '1', sessionId: 's', username: 'custom', displayName: '自定义管理员', clientType: 'WEB', roleCodes: ['ALL_ROLE_TEST'], permissionCodes: [] };
const cases = [
  [canAccessStudentQrLogin, ['studentQrLoginEnabled'], ['STUDENT_READ']],
  [canAccessLearningTasks, ['learningTaskManagementEnabled'], ['LEARNING_TASK_READ_MANAGED']],
  [canAccessGrowthPoints, ['growthPointQueryEnabled'], ['GROWTH_POINT_READ_CHILD']],
  [canAccessRewards, ['rewardExchangeEnabled'], ['REWARD_MANAGE_CHILD']],
  [canAccessGrowthReviews, ['dailyGrowthReviewEnabled'], ['GROWTH_REVIEW_READ_CHILD']],
  [canAccessOrganizationPage, ['organizationManagementEnabled'], ['ORG_NODE_READ']],
  [canAccessTeacherManagement, ['teacherManagementEnabled'], ['TEACHER_READ']],
  [canAccessExceptionReports, ['studentExceptionReportEnabled'], ['EXCEPTION_REPORT_READ']],
  [canAccessDictionaryManagement, ['dictionaryManagementEnabled'], ['DICTIONARY_READ']],
  [canAccessCacheManagement, ['cacheManagementEnabled'], ['CACHE_READ']],
  [canAccessInterfaceServiceManagement, ['interfaceServiceManagementEnabled'], ['INTERFACE_SERVICE_READ']],
  [canAccessAttachmentManagement, ['attachmentServiceEnabled'], ['ATTACHMENT_RULE_READ']],
  [canAccessImportExportTemplates, ['importExportTemplateManagementEnabled'], ['IMPORT_EXPORT_TEMPLATE_READ']],
  [canAccessImportJobs, ['dataImportValidationEnabled'], ['IMPORT_JOB_READ']],
  [canAccessExportJobs, ['dataExportEnabled'], ['EXPORT_JOB_READ']],
  [canExportInterfaceLedger, ['dataExportEnabled', 'attachmentServiceEnabled', 'interfaceServiceManagementEnabled', 'importExportTemplateManagementEnabled'], ['INTERFACE_SERVICE_READ', 'INTERFACE_SERVICE_EXPORT']],
  [canExportCache, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'cacheManagementEnabled'], ['CACHE_READ', 'CACHE_EXPORT']],
  [canExportSystemTasks, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled'], ['SYSTEM_TASK_READ', 'SYSTEM_TASK_EXPORT']],
  [canExportRewards, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'rewardExchangeEnabled'], ['REWARD_EXCHANGE_REVIEW_CHILD', 'REWARD_EXCHANGE_EXPORT']],
  [canExportExceptions, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'studentExceptionReportEnabled'], ['EXCEPTION_REPORT_READ', 'EXCEPTION_REPORT_EXPORT']],
  [canExportAttachments, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled'], ['ATTACHMENT_FILE_LEDGER_READ', 'ATTACHMENT_FILE_LEDGER_EXPORT']],
  [canExportStudentTasks, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'learningTaskManagementEnabled'], ['LEARNING_TASK_READ_MANAGED', 'STUDENT_TASK_REPORT_EXPORT']],
  [canExportOrgTaskStatistics, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'learningTaskManagementEnabled'], ['LEARNING_TASK_PROGRESS_READ', 'ORGANIZATION_TASK_STATISTICS_EXPORT']],
  [canExportAttendanceLedger, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'attendanceManagementEnabled'], ['ATTENDANCE_READ', 'ATTENDANCE_LEDGER_EXPORT']]
] as const;
describe('权限驱动 Web 页面和导出入口', () => {
  for (const [check, flags, permissions] of cases) {
    it(check.name + '接受自定义角色并拒绝缺失权限或开关', () => {
      const capabilities = Object.fromEntries([['client', 'WEB'], ...flags.map(flag => [flag, true])]) as unknown as ClientCapabilities;
      const user = { ...custom, permissionCodes: [...permissions] };
      for (const roleCodes of [['ALL_ROLE_TEST'], ['SYS_ADMIN'], ['SYS_AUDITOR'], []]) expect(check({ ...user, roleCodes }, capabilities)).toBe(true);
      for (const permission of permissions) expect(check({ ...user, permissionCodes: permissions.filter(code => code !== permission) }, capabilities)).toBe(false);
      for (const flag of flags) expect(check(user, { ...capabilities, [flag]: false })).toBe(false);
      expect(check(custom, capabilities)).toBe(false);
    });
  }
  it('个人家长关系和学生扫码保留业务身份', () => {
    const capabilities = { parentRelationshipManagementEnabled: true, studentQrLoginEnabled: true } as ClientCapabilities;
    expect(canAccessParentRelationships(custom, capabilities)).toBe(false);
    expect(canAccessStudentQrLogin(custom, capabilities)).toBe(false);
    expect(canAccessParentRelationships({ ...custom, roleCodes: ['PARENT'] }, capabilities)).toBe(true);
  });
});

it('学生微信个人管理要求家长真实身份、学生读取权限和开关', () => {
 const capabilities = { studentQrLoginEnabled: false, studentWechatAuthEnabled: true } as ClientCapabilities;
 expect(canAccessStudentQrLogin({ ...custom, roleCodes: ['PARENT'], permissionCodes: ['STUDENT_READ'] }, capabilities)).toBe(true);
 expect(canAccessStudentQrLogin({ ...custom, roleCodes: ['PARENT'] }, capabilities)).toBe(false);
 expect(canAccessStudentQrLogin({ ...custom, permissionCodes: ['STUDENT_READ'] }, capabilities)).toBe(false);
});
