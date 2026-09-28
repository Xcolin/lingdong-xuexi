import { describe, expect, it } from 'vitest';
import type { CurrentUser } from '../api/auth';
import type { ClientCapabilities } from '../api/capability';
import { canExportAttachments, canExportExceptions, canExportRewards, canExportSystemTasks, canExportCache, canExportInterfaceLedger, canAccessAttachmentManagement, canAccessCacheManagement, canAccessDictionaryManagement, canAccessExceptionReports, canAccessExportJobs, canAccessGrowthPoints, canAccessGrowthReviews, canAccessImportExportTemplates, canAccessImportJobs, canAccessInterfaceServiceManagement, canAccessLearningTasks, canAccessOrganizationPage, canAccessParentRelationships, canAccessRewards, canAccessStudentQrLogin, canAccessTeacherManagement } from './App';

const parent: CurrentUser = {
  userId: '1', sessionId: '2', username: 'parent', displayName: '家长',
  clientType: 'WEB', roleCodes: ['PARENT'], permissionCodes: []
};

describe('Web 业务入口', () => {
  it('附件导出遵从自定义动态授权及三项功能开关，不排除审核员', () => {
    const capabilities = { dataExportEnabled: true, attachmentServiceEnabled: true, importExportTemplateManagementEnabled: true } as ClientCapabilities;
    const user = { ...parent, roleCodes: ['CUSTOM_OPERATIONS'], permissionCodes: ['ATTACHMENT_FILE_LEDGER_READ', 'ATTACHMENT_FILE_LEDGER_EXPORT'] };
    for (const roleCodes of [['CUSTOM_OPERATIONS'], ['SYS_AUDITOR'], ['CUSTOM_OPERATIONS', 'SYS_AUDITOR']]) expect(canExportAttachments({ ...user, roleCodes }, capabilities)).toBe(true);
    for (const key of ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled']) expect(canExportAttachments(user, { ...capabilities, [key]: false })).toBe(false);
    for (const permissionCodes of [['ATTACHMENT_FILE_LEDGER_READ'], ['ATTACHMENT_FILE_LEDGER_EXPORT'], []]) expect(canExportAttachments({ ...user, permissionCodes }, capabilities)).toBe(false);
    expect(canAccessExportJobs(user, capabilities)).toBe(false);
    expect(canAccessExportJobs({ ...user, permissionCodes: [...user.permissionCodes, 'EXPORT_JOB_READ'] }, capabilities)).toBe(true);
  });

  it('异常台账仅教师或机构管理员可导出，混合审核员及任一权限开关缺失均拒绝', () => {
    const capabilities = { dataExportEnabled: true, attachmentServiceEnabled: true, importExportTemplateManagementEnabled: true, studentExceptionReportEnabled: true } as ClientCapabilities;
    const user = { ...parent, roleCodes: ['TEACHER'], permissionCodes: ['EXCEPTION_REPORT_READ','EXCEPTION_REPORT_EXPORT'] };
    for (const roleCodes of [['TEACHER'], ['ORG_ADMIN'], ['TEACHER','ORG_ADMIN']]) expect(canExportExceptions({ ...user, roleCodes }, capabilities)).toBe(true);
    for (const roleCodes of [['PARENT'], ['SYS_ADMIN'], ['TEACHER','SYS_AUDITOR'], ['ORG_ADMIN','SYS_AUDITOR']]) expect(canExportExceptions({ ...user, roleCodes }, capabilities)).toBe(false);
    for (const key of ['dataExportEnabled','attachmentServiceEnabled','importExportTemplateManagementEnabled','studentExceptionReportEnabled']) expect(canExportExceptions(user, { ...capabilities, [key]: false })).toBe(false);
    for (const permissionCodes of [['EXCEPTION_REPORT_READ'], ['EXCEPTION_REPORT_EXPORT']]) expect(canExportExceptions({ ...user, permissionCodes }, capabilities)).toBe(false);
  });
  it('奖励报表入口要求家长身份、动态权限和四项功能开关', () => {
    const user = { ...parent, permissionCodes: ['REWARD_EXCHANGE_REVIEW_CHILD', 'REWARD_EXCHANGE_EXPORT'] };
    const capabilities = { dataExportEnabled: true, attachmentServiceEnabled: true, importExportTemplateManagementEnabled: true, rewardExchangeEnabled: true } as ClientCapabilities;
    expect(canExportRewards(user, capabilities)).toBe(true);
    for (const key of ['dataExportEnabled', 'attachmentServiceEnabled', 'importExportTemplateManagementEnabled', 'rewardExchangeEnabled']) {
      expect(canExportRewards(user, { ...capabilities, [key]: false })).toBe(false);
    }
    for (const roleCodes of [['TEACHER'], ['ORG_ADMIN'], ['PARENT', 'SYS_AUDITOR']]) {
      expect(canExportRewards({ ...user, roleCodes }, capabilities)).toBe(false);
    }
    for (const permissionCodes of [['REWARD_EXCHANGE_EXPORT'], ['REWARD_EXCHANGE_REVIEW_CHILD']]) {
      expect(canExportRewards({ ...user, permissionCodes }, capabilities)).toBe(false);
    }
  });
  it('系统任务导出允许管理员及审核员，仍受读取导出权限和功能约束', () => {
    const capabilities = { dataExportEnabled: true, attachmentServiceEnabled: true, importExportTemplateManagementEnabled: true } as ClientCapabilities;
    for (const roleCodes of [['SYS_ADMIN'], ['SYS_AUDITOR'], ['SYS_ADMIN','SYS_AUDITOR']]) {
      const user = { ...parent, roleCodes, permissionCodes: ['SYSTEM_TASK_READ','SYSTEM_TASK_EXPORT'] };
      expect(canExportSystemTasks(user, capabilities)).toBe(true);
      expect(canExportSystemTasks({ ...user, permissionCodes: ['SYSTEM_TASK_READ'] }, capabilities)).toBe(false);
      expect(canExportSystemTasks(user, { ...capabilities, attachmentServiceEnabled: false })).toBe(false);
    }
    expect(canExportSystemTasks({ ...parent, permissionCodes: ['SYSTEM_TASK_READ','SYSTEM_TASK_EXPORT'] }, capabilities)).toBe(false);
  });

  it('缓存日志导出同时要求全部功能开关和管理员动态权限', () => {
    const user = { ...parent, roleCodes: ['SYS_ADMIN'], permissionCodes: ['CACHE_READ', 'CACHE_EXPORT'] };
    const capabilities = { dataExportEnabled: true, attachmentServiceEnabled: true, cacheManagementEnabled: true, importExportTemplateManagementEnabled: true } as ClientCapabilities;
    expect(canExportCache(user, capabilities)).toBe(true);
    for (const key of ['dataExportEnabled', 'attachmentServiceEnabled', 'cacheManagementEnabled', 'importExportTemplateManagementEnabled']) {
      expect(canExportCache(user, { ...capabilities, [key]: false })).toBe(false);
    }
    expect(canExportCache({ ...user, roleCodes: ['SYS_ADMIN', 'SYS_AUDITOR'] }, capabilities)).toBe(false);
    expect(canExportCache({ ...user, permissionCodes: ['CACHE_READ'] }, capabilities)).toBe(false);
  });

  it('接口导出同时要求全部功能开关和管理员动态权限', () => {
    const user = { ...parent, roleCodes: ['SYS_ADMIN'], permissionCodes: ['INTERFACE_SERVICE_READ', 'INTERFACE_SERVICE_EXPORT'] };
    const capabilities = { dataExportEnabled: true, attachmentServiceEnabled: true, interfaceServiceManagementEnabled: true, importExportTemplateManagementEnabled: true } as ClientCapabilities;
    expect(canExportInterfaceLedger(user, capabilities)).toBe(true);
    for (const key of ['dataExportEnabled', 'attachmentServiceEnabled', 'interfaceServiceManagementEnabled', 'importExportTemplateManagementEnabled']) {
      expect(canExportInterfaceLedger(user, { ...capabilities, [key]: false })).toBe(false);
    }
    expect(canExportInterfaceLedger({ ...user, roleCodes: ['SYS_ADMIN', 'SYS_AUDITOR'] }, capabilities)).toBe(false);
    expect(canExportInterfaceLedger({ ...user, permissionCodes: ['INTERFACE_SERVICE_READ'] }, capabilities)).toBe(false);
  });

  it('异常报备同时要求开关、读取权限和教师或机构管理员角色', () => {
    const capabilities = { studentExceptionReportEnabled: true } as ClientCapabilities;
    const teacher = { ...parent, roleCodes: ['TEACHER'], permissionCodes: ['EXCEPTION_REPORT_READ'] };
    expect(canAccessExceptionReports(teacher, capabilities)).toBe(true);
    expect(canAccessExceptionReports({ ...teacher, permissionCodes: [] }, capabilities)).toBe(false);
    expect(canAccessExceptionReports({ ...teacher, roleCodes: ['PARENT'] }, capabilities)).toBe(false);
    expect(canAccessExceptionReports(teacher, { ...capabilities, studentExceptionReportEnabled: false })).toBe(false);
  });

  it('教师管理同时要求开关、机构管理员角色和查询权限', () => {
    const capabilities = { teacherManagementEnabled: true } as ClientCapabilities;
    const administrator = {
      ...parent,
      roleCodes: ['ORG_ADMIN'],
      permissionCodes: ['TEACHER_READ']
    };
    expect(canAccessTeacherManagement(administrator, capabilities)).toBe(true);
    expect(canAccessTeacherManagement(administrator, {
      ...capabilities,
      teacherManagementEnabled: false
    })).toBe(false);
    expect(canAccessTeacherManagement({ ...administrator, permissionCodes: [] }, capabilities)).toBe(false);
    expect(canAccessTeacherManagement({ ...administrator, roleCodes: ['TEACHER'] }, capabilities)).toBe(false);
  });

  it('同时要求可用开关和业务角色', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true, parentRelationshipManagementEnabled: true, organizationMiniappAuthEnabled: false, studentOrganizationRelationshipEnabled: true, accountSecurityManagementEnabled: true, parentAccountLifecycleEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true
    };
    expect(canAccessLearningTasks(parent, enabled)).toBe(true);
    expect(canAccessLearningTasks(parent, { ...enabled, learningTaskManagementEnabled: false })).toBe(false);
    expect(canAccessLearningTasks({ ...parent, roleCodes: ['STUDENT'] }, enabled)).toBe(false);
  });

  it('积分台账同时要求可用开关和家长角色', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true, parentRelationshipManagementEnabled: true, organizationMiniappAuthEnabled: false, studentOrganizationRelationshipEnabled: true, accountSecurityManagementEnabled: true, parentAccountLifecycleEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true
    };
    expect(canAccessGrowthPoints(parent, enabled)).toBe(true);
    expect(canAccessGrowthPoints(parent, { ...enabled, growthPointQueryEnabled: false })).toBe(false);
    expect(canAccessGrowthPoints({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
  });

  it('奖励管理同时要求兑换开关和家长角色', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true, parentRelationshipManagementEnabled: true, organizationMiniappAuthEnabled: false, studentOrganizationRelationshipEnabled: true, accountSecurityManagementEnabled: true, parentAccountLifecycleEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true
    };
    expect(canAccessRewards(parent, enabled)).toBe(true);
    expect(canAccessRewards(parent, { ...enabled, rewardExchangeEnabled: false })).toBe(false);
    expect(canAccessRewards({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
  });

  it('成长复盘要求至少一个复盘开关和家长角色', () => {
    const enabled = {
      client: 'WEB' as const, studentCodeLoginEnabled: false, studentQrLoginEnabled: true, parentRelationshipManagementEnabled: true, organizationMiniappAuthEnabled: false, studentOrganizationRelationshipEnabled: true, accountSecurityManagementEnabled: true, parentAccountLifecycleEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: false
    };
    expect(canAccessGrowthReviews(parent, enabled)).toBe(true);
    expect(canAccessGrowthReviews(parent, {
      ...enabled, dailyGrowthReviewEnabled: false
    })).toBe(false);
    expect(canAccessGrowthReviews({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
  });

  it('学生扫码登录只向家长和机构管理员开放', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true,
      parentRelationshipManagementEnabled: true,
      organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: true,
      accountSecurityManagementEnabled: true,
      parentAccountLifecycleEnabled: true,
      learningTaskManagementEnabled: true, previousDayTaskCopyEnabled: true,
      learningTaskTemplateEnabled: true, growthPointQueryEnabled: true,
      growthPointCorrectionEnabled: true, rewardExchangeEnabled: true,
      dailyGrowthReviewEnabled: true, periodicGrowthReportEnabled: true
    };
    expect(canAccessStudentQrLogin(parent, enabled)).toBe(true);
    expect(canAccessStudentQrLogin({ ...parent, roleCodes: ['ORG_ADMIN'] }, enabled)).toBe(true);
    expect(canAccessStudentQrLogin({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
    expect(canAccessStudentQrLogin(parent, { ...enabled, studentQrLoginEnabled: false })).toBe(false);
  });

  it('家长关系入口同时要求功能开关和家长角色', () => {
    const enabled: ClientCapabilities = {
      client: 'WEB', studentCodeLoginEnabled: false, studentQrLoginEnabled: true,
      parentRelationshipManagementEnabled: true, organizationMiniappAuthEnabled: false, studentOrganizationRelationshipEnabled: true, accountSecurityManagementEnabled: true, parentAccountLifecycleEnabled: true, learningTaskManagementEnabled: true,
      previousDayTaskCopyEnabled: true, learningTaskTemplateEnabled: true,
      growthPointQueryEnabled: true, growthPointCorrectionEnabled: true,
      rewardExchangeEnabled: true, dailyGrowthReviewEnabled: true,
      periodicGrowthReportEnabled: true
    };
    expect(canAccessParentRelationships(parent, enabled)).toBe(true);
    expect(canAccessParentRelationships(parent, {
      ...enabled, parentRelationshipManagementEnabled: false
    })).toBe(false);
    expect(canAccessParentRelationships({ ...parent, roleCodes: ['TEACHER'] }, enabled)).toBe(false);
  });

  it('全局组织管理要求平台角色与功能开关，机构管理员仅保留机构业务入口', () => {
    const capabilities = {
      client: 'WEB' as const, organizationManagementEnabled: true,
      studentCodeLoginEnabled: false, studentQrLoginEnabled: false,
      parentRelationshipManagementEnabled: false, organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: false, accountSecurityManagementEnabled: false,
      parentAccountLifecycleEnabled: false, learningTaskManagementEnabled: false,
      previousDayTaskCopyEnabled: false, learningTaskTemplateEnabled: false,
      growthPointQueryEnabled: false, growthPointCorrectionEnabled: false,
      rewardExchangeEnabled: false, dailyGrowthReviewEnabled: false,
      periodicGrowthReportEnabled: false
    };
    expect(canAccessOrganizationPage({ ...parent, roleCodes: ['SYS_ADMIN'] }, capabilities)).toBe(true);
    expect(canAccessOrganizationPage({ ...parent, roleCodes: ['SYS_AUDITOR'] }, capabilities)).toBe(true);
    expect(canAccessOrganizationPage({ ...parent, roleCodes: ['SYS_ADMIN'] }, { ...capabilities, organizationManagementEnabled: false })).toBe(false);
    expect(canAccessOrganizationPage({ ...parent, roleCodes: ['ORG_ADMIN'] }, { ...capabilities, organizationManagementEnabled: false })).toBe(true);
    expect(canAccessOrganizationPage({ ...parent, roleCodes: ['TEACHER'] }, capabilities)).toBe(false);
  });

  it('数据字典入口同时要求功能开关和动态查询权限', () => {
    const capabilities = {
      client: 'WEB' as const, dictionaryManagementEnabled: true,
      studentCodeLoginEnabled: false, studentQrLoginEnabled: false,
      parentRelationshipManagementEnabled: false, organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: false, accountSecurityManagementEnabled: false,
      parentAccountLifecycleEnabled: false, learningTaskManagementEnabled: false,
      previousDayTaskCopyEnabled: false, learningTaskTemplateEnabled: false,
      growthPointQueryEnabled: false, growthPointCorrectionEnabled: false,
      rewardExchangeEnabled: false, dailyGrowthReviewEnabled: false,
      periodicGrowthReportEnabled: false
    };
    expect(canAccessDictionaryManagement({
      ...parent, roleCodes: ['OPS'], permissionCodes: ['DICTIONARY_READ']
    }, capabilities)).toBe(true);
    expect(canAccessDictionaryManagement({
      ...parent, roleCodes: ['SYS_ADMIN'], permissionCodes: ['DICTIONARY_READ']
    }, {
      ...capabilities, dictionaryManagementEnabled: false
    })).toBe(false);
    expect(canAccessDictionaryManagement({
      ...parent, roleCodes: ['SYS_ADMIN'], permissionCodes: []
    }, capabilities)).toBe(false);
  });

  it('缓存管理入口同时要求功能开关和动态查询权限', () => {
    const capabilities = {
      client: 'WEB' as const, cacheManagementEnabled: true,
      studentCodeLoginEnabled: false, studentQrLoginEnabled: false,
      parentRelationshipManagementEnabled: false, organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: false, accountSecurityManagementEnabled: false,
      parentAccountLifecycleEnabled: false, learningTaskManagementEnabled: false,
      previousDayTaskCopyEnabled: false, learningTaskTemplateEnabled: false,
      growthPointQueryEnabled: false, growthPointCorrectionEnabled: false,
      rewardExchangeEnabled: false, dailyGrowthReviewEnabled: false,
      periodicGrowthReportEnabled: false
    };
    expect(canAccessCacheManagement({
      ...parent, roleCodes: ['OPS'], permissionCodes: ['CACHE_READ']
    }, capabilities)).toBe(true);
    expect(canAccessCacheManagement({
      ...parent, roleCodes: ['SYS_ADMIN'], permissionCodes: ['CACHE_READ']
    }, { ...capabilities, cacheManagementEnabled: false })).toBe(false);
    expect(canAccessCacheManagement({
      ...parent, roleCodes: ['SYS_AUDITOR'], permissionCodes: ['CACHE_REVIEW']
    }, capabilities)).toBe(false);
  });

  it('接口服务管理入口同时要求功能开关和动态查询权限', () => {
    const capabilities = {
      client: 'WEB' as const, interfaceServiceManagementEnabled: true,
      studentCodeLoginEnabled: false, studentQrLoginEnabled: false,
      parentRelationshipManagementEnabled: false, organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: false, accountSecurityManagementEnabled: false,
      parentAccountLifecycleEnabled: false, learningTaskManagementEnabled: false,
      previousDayTaskCopyEnabled: false, learningTaskTemplateEnabled: false,
      growthPointQueryEnabled: false, growthPointCorrectionEnabled: false,
      rewardExchangeEnabled: false, dailyGrowthReviewEnabled: false,
      periodicGrowthReportEnabled: false
    };
    expect(canAccessInterfaceServiceManagement({
      ...parent, roleCodes: ['SYS_ADMIN'], permissionCodes: ['INTERFACE_SERVICE_READ']
    }, capabilities)).toBe(true);
    expect(canAccessInterfaceServiceManagement({
      ...parent, roleCodes: ['SYS_ADMIN'], permissionCodes: ['INTERFACE_SERVICE_READ']
    }, { ...capabilities, interfaceServiceManagementEnabled: false })).toBe(false);
    expect(canAccessInterfaceServiceManagement({
      ...parent, roleCodes: ['SYS_AUDITOR'], permissionCodes: ['INTERFACE_SERVICE_REVIEW']
    }, capabilities)).toBe(false);
  });

  it('附件管理入口同时要求总开关和至少一项台账读取权限', () => {
    const capabilities: ClientCapabilities = {
      client: 'WEB', attachmentServiceEnabled: true,
      studentCodeLoginEnabled: false, studentQrLoginEnabled: false,
      parentRelationshipManagementEnabled: false, organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: false, accountSecurityManagementEnabled: false,
      parentAccountLifecycleEnabled: false, learningTaskManagementEnabled: false,
      previousDayTaskCopyEnabled: false, learningTaskTemplateEnabled: false,
      growthPointQueryEnabled: false, growthPointCorrectionEnabled: false,
      rewardExchangeEnabled: false, dailyGrowthReviewEnabled: false,
      periodicGrowthReportEnabled: false
    };
    expect(canAccessAttachmentManagement({
      ...parent, permissionCodes: ['ATTACHMENT_RULE_READ']
    }, capabilities)).toBe(true);
    expect(canAccessAttachmentManagement({
      ...parent, permissionCodes: ['ATTACHMENT_FILE_LEDGER_READ']
    }, capabilities)).toBe(true);
    expect(canAccessAttachmentManagement({
      ...parent, permissionCodes: ['ATTACHMENT_RULE_MANAGE']
    }, capabilities)).toBe(false);
    expect(canAccessAttachmentManagement({
      ...parent, permissionCodes: ['ATTACHMENT_RULE_READ']
    }, { ...capabilities, attachmentServiceEnabled: false })).toBe(false);
  });

  it('导入导出模板入口同时要求 Web 能力和模板读取权限', () => {
    const capabilities: ClientCapabilities = {
      client: 'WEB', importExportTemplateManagementEnabled: true,
      studentCodeLoginEnabled: false, studentQrLoginEnabled: false,
      parentRelationshipManagementEnabled: false, organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: false, accountSecurityManagementEnabled: false,
      parentAccountLifecycleEnabled: false, learningTaskManagementEnabled: false,
      previousDayTaskCopyEnabled: false, learningTaskTemplateEnabled: false,
      growthPointQueryEnabled: false, growthPointCorrectionEnabled: false,
      rewardExchangeEnabled: false, dailyGrowthReviewEnabled: false,
      periodicGrowthReportEnabled: false
    };
    const reader = { ...parent, permissionCodes: ['IMPORT_EXPORT_TEMPLATE_READ'] };

    expect(canAccessImportExportTemplates(reader, capabilities)).toBe(true);
    expect(canAccessImportExportTemplates(reader, {
      ...capabilities, importExportTemplateManagementEnabled: false
    })).toBe(false);
    expect(canAccessImportExportTemplates(parent, capabilities)).toBe(false);
  });

  it('导入校验作业入口同时要求三功能聚合能力和读取权限', () => {
    const capabilities = {
      client: 'WEB' as const, dataImportValidationEnabled: true,
      studentCodeLoginEnabled: false, studentQrLoginEnabled: false,
      parentRelationshipManagementEnabled: false, organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: false, accountSecurityManagementEnabled: false,
      parentAccountLifecycleEnabled: false, learningTaskManagementEnabled: false,
      previousDayTaskCopyEnabled: false, learningTaskTemplateEnabled: false,
      growthPointQueryEnabled: false, growthPointCorrectionEnabled: false,
      rewardExchangeEnabled: false, dailyGrowthReviewEnabled: false,
      periodicGrowthReportEnabled: false
    };
    const reader = { ...parent, permissionCodes: ['IMPORT_JOB_READ'] };

    expect(canAccessImportJobs(reader, capabilities)).toBe(true);
    expect(canAccessImportJobs(reader, {
      ...capabilities, dataImportValidationEnabled: false
    })).toBe(false);
    expect(canAccessImportJobs(parent, capabilities)).toBe(false);
  });

  it('数据导出入口同时要求聚合能力和本人查询或敏感审核权限', () => {
    const capabilities = {
      client: 'WEB' as const, dataExportEnabled: true,
      studentCodeLoginEnabled: false, studentQrLoginEnabled: false,
      parentRelationshipManagementEnabled: false, organizationMiniappAuthEnabled: false,
      studentOrganizationRelationshipEnabled: false, accountSecurityManagementEnabled: false,
      parentAccountLifecycleEnabled: false, learningTaskManagementEnabled: false,
      previousDayTaskCopyEnabled: false, learningTaskTemplateEnabled: false,
      growthPointQueryEnabled: false, growthPointCorrectionEnabled: false,
      rewardExchangeEnabled: false, dailyGrowthReviewEnabled: false,
      periodicGrowthReportEnabled: false
    };
    const reader = { ...parent, permissionCodes: ['EXPORT_JOB_READ'] };
    const reviewer = { ...parent, roleCodes: ['SYS_AUDITOR'], permissionCodes: ['EXPORT_SENSITIVE_REVIEW'] };

    expect(canAccessExportJobs(reader, capabilities)).toBe(true);
    expect(canAccessExportJobs(reviewer, capabilities)).toBe(true);
    expect(canAccessExportJobs(reader, { ...capabilities, dataExportEnabled: false })).toBe(false);
    expect(canAccessExportJobs(parent, capabilities)).toBe(false);
  });
});
