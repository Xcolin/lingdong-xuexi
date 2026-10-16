import { describe, expect, it } from 'vitest';
import type { CurrentUser } from '../api/auth';
import type { ClientCapabilities } from '../api/capability';
import { canAccessConfiguredPage, canExportStudentTasks, canExportOrgTaskStatistics, canExportAttendanceLedger, canExportAttachments, canExportExceptions, canExportRewards, canExportSystemTasks, canExportCache, canExportInterfaceLedger, canAccessAttachmentManagement, canAccessCacheManagement, canAccessDictionaryManagement, canAccessExceptionReports, canAccessExportJobs, canAccessGrowthPoints, canAccessGrowthReviews, canAccessImportExportTemplates, canAccessImportJobs, canAccessInterfaceServiceManagement, canAccessLearningTasks, canAccessOrganizationPage, canAccessParentRelationships, canAccessRewards, canAccessStudentQrLogin, canAccessTeacherManagement } from './App';

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
  [canExportRewards, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'rewardExchangeEnabled'], ['REWARD_EXCHANGE_READ', 'REWARD_EXCHANGE_EXPORT']],
  [canExportExceptions, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'studentExceptionReportEnabled'], ['EXCEPTION_REPORT_READ', 'EXCEPTION_REPORT_EXPORT']],
  [canExportAttachments, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled'], ['ATTACHMENT_FILE_LEDGER_READ', 'ATTACHMENT_FILE_LEDGER_EXPORT']],
  [canExportStudentTasks, ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'learningTaskManagementEnabled'], ['LEARNING_TASK_PROGRESS_READ', 'STUDENT_TASK_REPORT_EXPORT']],
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
  it('家长关系按有效读取权限开放，自定义角色无需角色名称匹配', () => {
    const capabilities = { parentRelationshipManagementEnabled: true, studentQrLoginEnabled: true } as ClientCapabilities;
    expect(canAccessParentRelationships(custom, capabilities)).toBe(false);
    expect(canAccessStudentQrLogin(custom, capabilities)).toBe(false);
    expect(canAccessParentRelationships({ ...custom, permissionCodes: ['PARENT_RELATIONSHIP_READ'] }, capabilities)).toBe(true);
  });
});

it('学生微信入口要求有效学生读取权限和开关，身份适用性由服务端判定', () => {
 const capabilities = { studentQrLoginEnabled: false, studentWechatAuthEnabled: true } as ClientCapabilities;
 expect(canAccessStudentQrLogin({ ...custom, roleCodes: ['PARENT'], permissionCodes: ['STUDENT_READ'] }, capabilities)).toBe(true);
 expect(canAccessStudentQrLogin({ ...custom, roleCodes: ['PARENT'] }, capabilities)).toBe(false);
 expect(canAccessStudentQrLogin({ ...custom, permissionCodes: ['STUDENT_READ'] }, capabilities)).toBe(true);
});

it('学生登录页面权限允许进入，但全局关闭仍阻止入口',()=>{
 const user={permissionCodes:['STUDENT_LOGIN_READ'],roleCodes:['PARENT']} as any;
 expect(canAccessStudentQrLogin(user,{studentQrLoginEnabled:true} as any)).toBe(true);
 expect(canAccessStudentQrLogin(user,{studentQrLoginEnabled:false,studentWechatAuthEnabled:false} as any)).toBe(false);
});

it('菜单和直达路由使用同一有效PAGE权限及功能可用性，缺配置安全拒绝', () => {
 const caps={client:'WEB',teacherManagementEnabled:true} as ClientCapabilities;
 const directory={id:'d',code:'directory',type:'DIRECTORY',status:'ENABLED',parentId:null} as any;
 const page={id:'p',code:'page-label',permissionCode:'TEACHER_READ',type:'PAGE',route:'/teachers',status:'ENABLED',parentId:'d'} as any;
 expect(canAccessConfiguredPage('/teachers',[directory,page],['TEACHER_READ'],caps)).toBe(true);
 expect(canAccessConfiguredPage('/teachers',[directory,page],[],caps)).toBe(false);
 expect(canAccessConfiguredPage('/teachers',[],['TEACHER_READ'],caps)).toBe(false);
 expect(canAccessConfiguredPage('/teachers',[{...directory,status:'DISABLED'},page],['TEACHER_READ'],caps)).toBe(false);
 expect(canAccessConfiguredPage('/teachers',[directory,page],['TEACHER_READ'],{...caps,teacherManagementEnabled:false})).toBe(false);
});

it('学生任务导出按真实家长身份选择读取能力，角色名称不能替代身份',()=>{
 const caps={client:'WEB',dataExportEnabled:true,attachmentServiceEnabled:true,importExportTemplateManagementEnabled:true,learningTaskManagementEnabled:true} as ClientCapabilities;
 const family={...custom,parentProfile:true,permissionCodes:['LEARNING_TASK_READ_MANAGED','STUDENT_TASK_REPORT_EXPORT']};
 expect(canExportStudentTasks(family,caps)).toBe(true);
 expect(canExportStudentTasks({...family,parentProfile:false,roleCodes:['PARENT']},caps)).toBe(false);
 expect(canExportStudentTasks({...family,parentProfile:false,permissionCodes:['LEARNING_TASK_PROGRESS_READ','STUDENT_TASK_REPORT_EXPORT']},caps)).toBe(true);
});
it('学生列表PAGE不因二维码和微信登录功能关闭被拒绝',()=>{
 const page={id:'students',code:'STUDENT_READ',permissionCode:'STUDENT_READ',type:'PAGE',route:'/student-login',parentId:null,status:'ENABLED'} as any;
 expect(canAccessConfiguredPage('/student-login',[page],['STUDENT_READ'],{client:'WEB',studentQrLoginEnabled:false,studentWechatAuthEnabled:false} as ClientCapabilities)).toBe(true);
});
