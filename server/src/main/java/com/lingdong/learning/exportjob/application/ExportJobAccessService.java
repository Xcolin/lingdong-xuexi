package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointQueryMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 在创建、执行、查询和下载节点统一执行当前权限与对象关系校验。 */
@Service
public class ExportJobAccessService {
    private static final String EXPORT_FEATURE = "DATA_EXPORT";
    private static final String TEMPLATE_FEATURE = "IMPORT_EXPORT_TEMPLATE_MANAGEMENT";
    private static final String ATTACHMENT_FEATURE = "ATTACHMENT_SERVICE";

    private final FeatureAccessService featureAccessService;
    private final PermissionDecisionService permissionDecisionService;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final GrowthPointQueryMapper growthPointQueryMapper;
    private final com.lingdong.learning.audit.application.SystemTaskQueryService taskQuery;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.lingdong.learning.exceptionreport.application.ExceptionReportApplicationService exceptionReports;

    public ExportJobAccessService(FeatureAccessService features, PermissionDecisionService permissions,
            UserRoleMapper roles, UserMapper users, GrowthPointQueryMapper growth) {
        this(features, permissions, roles, users, growth, null, null);
    }


    public ExportJobAccessService(FeatureAccessService features, PermissionDecisionService permissions,
            UserRoleMapper roles, UserMapper users, GrowthPointQueryMapper growth,
            com.lingdong.learning.audit.application.SystemTaskQueryService taskQuery,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(features, permissions, roles, users, growth, taskQuery, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ExportJobAccessService(
            FeatureAccessService featureAccessService,
            PermissionDecisionService permissionDecisionService,
            UserRoleMapper userRoleMapper,
            UserMapper userMapper,
            GrowthPointQueryMapper growthPointQueryMapper,
            com.lingdong.learning.audit.application.SystemTaskQueryService taskQuery,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            com.lingdong.learning.exceptionreport.application.ExceptionReportApplicationService exceptionReports
    ) {
        this.featureAccessService = featureAccessService;
        this.permissionDecisionService = permissionDecisionService;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.growthPointQueryMapper = growthPointQueryMapper;
        this.taskQuery = taskQuery;
        this.objectMapper = objectMapper;
        this.exceptionReports = exceptionReports;
    }

    public record ExceptionExportScope(boolean teacherOnly,
            java.util.List<com.lingdong.learning.exceptionreport.application.ExceptionReportClassOption> classes) {
        public ExceptionExportScope { classes = java.util.List.copyOf(classes); }
        public java.util.List<Long> classIds() { return classes.stream().map(cl -> cl.classOrganizationId()).distinct().toList(); }
    }

    public ExceptionExportScope requireExceptionReportExport(long userId) {
        requireFeatures();
        featureAccessService.requireEnabled("STUDENT_EXCEPTION_REPORT", null);
        requireEnabledUser(userId);
        var roles = userRoleMapper.findEnabledRoleCodesByUserId(userId);
        if (roles.contains("SYS_AUDITOR") || (!roles.contains("TEACHER") && !roles.contains("ORG_ADMIN"))) {
            throw denied("仅教师或机构管理员可导出异常报备台账");
        }
        requirePermission(userId, "EXCEPTION_REPORT_EXPORT");
        requirePermission(userId, "EXCEPTION_REPORT_READ");
        var user = new com.lingdong.learning.auth.application.AuthenticatedUser(userId, null, null, null,
                com.lingdong.learning.auth.domain.AuthClientType.WEB, roles);
        return new ExceptionExportScope(!roles.contains("ORG_ADMIN"), exceptionReports.findClassOptions(user));
    }

    private void requireExceptionSnapshot(ExportJobRecord job) {
        var current = requireExceptionReportExport(job.requesterId());
        try {
            var frozen = objectMapper.readValue(job.scopeSnapshot(), ExportScopeSnapshot.class);
            if (frozen == null || frozen.studentId() != null || frozen.upperBound() < 0
                    || frozen.exceptionTeacherOnly() == null || frozen.exceptionClassIds() == null
                    || frozen.exceptionTeacherOnly() != current.teacherOnly()
                    || !current.classIds().containsAll(frozen.exceptionClassIds())) {
                throw denied("异常报备导出角色或班级范围已失效");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException exception) {
            throw denied("异常报备导出范围快照无效");
        }
    }

    public com.lingdong.learning.audit.application.SystemTaskQueryService.VisibilityScope requireSystemTaskExport(long userId) {
        requireFeatures();
        requireEnabledUser(userId);
        requirePermission(userId, "SYSTEM_TASK_EXPORT");
        return taskQuery.resolveScope(userId);
    }

    private void requireSystemTaskSnapshot(ExportJobRecord job) {
        var current = requireSystemTaskExport(job.requesterId());
        try {
            ExportScopeSnapshot frozen = objectMapper.readValue(job.scopeSnapshot(), ExportScopeSnapshot.class);
            if (frozen == null || frozen.studentId() != null || frozen.upperBound() < 0 || frozen.systemTaskAuditor() == null
                    || frozen.systemTaskTypes() == null || frozen.systemTaskAuditor() != current.auditor()
                    || !current.types().containsAll(frozen.systemTaskTypes())) {
                throw denied("系统任务导出角色或领域范围已失效");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException exception) {
            throw denied("系统任务导出范围快照无效");
        }
    }

    public void requireRewardExchangeExport(long userId) {
        requireFeatures();
        featureAccessService.requireEnabled("REWARD_EXCHANGE", null);
        requireEnabledUser(userId);
        requireRole(userId, "PARENT", "仅家长可导出奖励兑换报表");
        if (userRoleMapper.hasRoleCode(userId, "SYS_AUDITOR")) throw denied("系统审核员不能导出奖励兑换报表");
        requirePermission(userId, "REWARD_EXCHANGE_EXPORT");
        requirePermission(userId, "REWARD_EXCHANGE_REVIEW_CHILD");
    }

    public void requireRewardExchangeExport(long userId, long studentId) {
        requireRewardExchangeExport(userId);
        requirePrimaryStudent(userId, studentId);
    }

    public void requireOrdinaryCreate(long userId, long studentId) {
        requireFeatures();
        requireEnabledUser(userId);
        requireRole(userId, "PARENT", "仅家长可创建积分明细导出");
        requirePermission(userId, "EXPORT_JOB_CREATE");
        requirePermission(userId, "GROWTH_POINT_READ_CHILD");
        requirePrimaryStudent(userId, studentId);
    }

    public void requireDictionaryExport(long userId) {
        requireFeatures();
        featureAccessService.requireEnabled("DICTIONARY_MANAGEMENT", null);
        requireEnabledUser(userId);
        requireRole(userId, "SYS_ADMIN", "仅系统管理员可导出字典台账");
        if (userRoleMapper.hasRoleCode(userId, "SYS_AUDITOR")) {
            throw denied("系统审核员不能导出字典台账");
        }
        requirePermission(userId, "DICTIONARY_READ");
        requirePermission(userId, "DICTIONARY_EXPORT");
    }

    public void requireTemplateExport(long userId) {
        requireFeatures();
        requireEnabledUser(userId);
        requireRole(userId, "SYS_ADMIN", "仅系统管理员可导出模板台账");
        if (userRoleMapper.hasRoleCode(userId, "SYS_AUDITOR")) {
            throw denied("系统审核员不能导出模板台账");
        }
        requirePermission(userId, "IMPORT_EXPORT_TEMPLATE_READ");
        requirePermission(userId, "IMPORT_EXPORT_TEMPLATE_EXPORT");
    }

    public void requireAttachmentLedgerExport(long userId) {
        requireFeatures();
        requireEnabledUser(userId);
        requirePermission(userId, "ATTACHMENT_FILE_LEDGER_READ");
        requirePermission(userId, "ATTACHMENT_FILE_LEDGER_EXPORT");
    }

    public void requireCacheExport(long userId) {
        requireFeatures();
        featureAccessService.requireEnabled("CACHE_MANAGEMENT", null);
        requireEnabledUser(userId);
        requireRole(userId, "SYS_ADMIN", "仅系统管理员可导出缓存操作日志");
        if (userRoleMapper.hasRoleCode(userId, "SYS_AUDITOR")) {
            throw denied("系统审核员不能导出缓存操作日志");
        }
        requirePermission(userId, "CACHE_READ");
        requirePermission(userId, "CACHE_EXPORT");
    }

    public void requireInterfaceExport(long userId) {
        requireFeatures();
        featureAccessService.requireEnabled("INTERFACE_SERVICE_MANAGEMENT", null);
        requireEnabledUser(userId);
        requireRole(userId, "SYS_ADMIN", "仅系统管理员可导出接口服务台账");
        if (userRoleMapper.hasRoleCode(userId, "SYS_AUDITOR")) {
            throw denied("系统审核员不能导出接口服务台账");
        }
        requirePermission(userId, "INTERFACE_SERVICE_READ");
        requirePermission(userId, "INTERFACE_SERVICE_EXPORT");
    }

    public void requireSensitiveSubmit(long userId) {
        requireFeatures();
        requireEnabledUser(userId);
        requireRole(userId, "SYS_ADMIN", "仅系统管理员可提交敏感导出");
        requirePermission(userId, "EXPORT_SENSITIVE_SUBMIT");
        requirePermission(userId, "IAM_AUDIT_READ");
    }

    public void requireSensitiveReview(long userId) {
        requireFeatures();
        requireEnabledUser(userId);
        requireRole(userId, "SYS_AUDITOR", "仅系统审核员可审核敏感导出");
        requirePermission(userId, "EXPORT_SENSITIVE_REVIEW");
    }

    public void requireListRead(long userId) {
        requireFeatures();
        requireEnabledUser(userId);
        requirePermission(userId, "EXPORT_JOB_READ");
        if (!userRoleMapper.hasRoleCode(userId, "PARENT")
                && !userRoleMapper.hasRoleCode(userId, "SYS_ADMIN")
                && !userRoleMapper.hasRoleCode(userId, "SYS_AUDITOR")
                && !userRoleMapper.hasRoleCode(userId, "TEACHER")
                && !userRoleMapper.hasRoleCode(userId, "ORG_ADMIN")
                && !(permissionDecisionService.isAllowed(userId, PermissionClient.WEB, "ATTACHMENT_FILE_LEDGER_READ")
                    && permissionDecisionService.isAllowed(userId, PermissionClient.WEB, "ATTACHMENT_FILE_LEDGER_EXPORT"))) {
            throw denied("当前账号不能查询导出作业");
        }
    }

    public void requireExecution(ExportJobRecord job) {
        if (job == null || job.requesterId() == null) {
            throw denied("导出作业执行身份无效");
        }
        if (job.exportType() == ExportJobType.EXCEPTION_REPORT_LEDGER && job.studentId() == null) {
            requireExceptionSnapshot(job);
            return;
        }
        if (job.exportType() == ExportJobType.SYSTEM_TASK_LEDGER && job.studentId() == null) {
            requireSystemTaskSnapshot(job);
            return;
        }
        if (job.exportType() == ExportJobType.ATTACHMENT_LEDGER && job.studentId() == null) {
            requireAttachmentLedgerExport(job.requesterId());
            return;
        }
        if (job.exportType() == ExportJobType.CACHE_OPERATION_LOG && job.studentId() == null) {
            requireCacheExport(job.requesterId());
            return;
        }
        if (job.exportType() == ExportJobType.INTERFACE_SERVICE_LEDGER && job.studentId() == null) {
            requireInterfaceExport(job.requesterId());
            return;
        }
        if (job.exportType() == ExportJobType.TEMPLATE_LEDGER && job.studentId() == null) {
            requireTemplateExport(job.requesterId());
            return;
        }
        if (job.exportType() == ExportJobType.DICTIONARY_LEDGER && job.studentId() == null) {
            requireDictionaryExport(job.requesterId());
            return;
        }
        if (job.exportType() == ExportJobType.REWARD_EXCHANGE_LEDGER && job.studentId() != null) {
            requireRewardExchangeExport(job.requesterId(), job.studentId());
            return;
        }
        if (job.exportType() == ExportJobType.GROWTH_POINT_LEDGER && job.studentId() != null) {
            requireOrdinaryCreate(job.requesterId(), job.studentId());
            return;
        }
        if (job.exportType() == ExportJobType.IAM_CHANGE_AUDIT) {
            requireSensitiveSubmit(job.requesterId());
            return;
        }
        throw denied("导出作业类型或对象范围无效");
    }

    public void requireOwnerRead(long userId, ExportJobRecord job) {
        requireOwner(userId, job);
        requirePermission(userId, "EXPORT_JOB_READ");
        requireDatasetRead(userId, job);
    }

    public void requireOwnerDownload(long userId, ExportJobRecord job) {
        requireOwnerRead(userId, job);
    }

    public void requireFeatures() {
        featureAccessService.requireEnabled(EXPORT_FEATURE, null);
        featureAccessService.requireEnabled(TEMPLATE_FEATURE, null);
        featureAccessService.requireEnabled(ATTACHMENT_FEATURE, null);
    }

    private void requireOwner(long userId, ExportJobRecord job) {
        requireFeatures();
        requireEnabledUser(userId);
        if (job == null || !Objects.equals(job.requesterId(), userId)) {
            throw denied("当前账号无权读取该导出作业");
        }
    }

    private void requireDatasetRead(long userId, ExportJobRecord job) {
        if (job.exportType() == ExportJobType.EXCEPTION_REPORT_LEDGER && job.studentId() == null) {
            requireExceptionSnapshot(job);
            return;
        }
        if (job.exportType() == ExportJobType.SYSTEM_TASK_LEDGER && job.studentId() == null) {
            requireSystemTaskSnapshot(job);
            return;
        }
        if (job.exportType() == ExportJobType.ATTACHMENT_LEDGER && job.studentId() == null) {
            requireAttachmentLedgerExport(job.requesterId());
            return;
        }
        if (job.exportType() == ExportJobType.CACHE_OPERATION_LOG && job.studentId() == null) {
            requireCacheExport(job.requesterId());
            return;
        }
        if (job.exportType() == ExportJobType.INTERFACE_SERVICE_LEDGER && job.studentId() == null) {
            requireInterfaceExport(job.requesterId());
            return;
        }
        if (job.exportType() == ExportJobType.TEMPLATE_LEDGER && job.studentId() == null) {
            requireTemplateExport(userId);
            return;
        }
        if (job.exportType() == ExportJobType.DICTIONARY_LEDGER && job.studentId() == null) {
            requireDictionaryExport(userId);
            return;
        }
        if (job.exportType() == ExportJobType.REWARD_EXCHANGE_LEDGER && job.studentId() != null) {
            requireRewardExchangeExport(job.requesterId(), job.studentId());
            return;
        }
        if (job.exportType() == ExportJobType.GROWTH_POINT_LEDGER && job.studentId() != null) {
            requireRole(userId, "PARENT", "当前账号不再具备家长导出身份");
            requirePermission(userId, "GROWTH_POINT_READ_CHILD");
            requirePrimaryStudent(userId, job.studentId());
            return;
        }
        if (job.exportType() == ExportJobType.IAM_CHANGE_AUDIT) {
            requireRole(userId, "SYS_ADMIN", "当前账号不再具备系统管理员身份");
            requirePermission(userId, "IAM_AUDIT_READ");
            return;
        }
        throw denied("导出作业类型或对象范围无效");
    }

    private void requireEnabledUser(long userId) {
        User user = userMapper.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED) {
            throw denied("当前账号不可用");
        }
    }

    private void requireRole(long userId, String roleCode, String message) {
        if (!userRoleMapper.hasRoleCode(userId, roleCode)) {
            throw denied(message);
        }
    }

    private void requirePermission(long userId, String permissionCode) {
        if (!permissionDecisionService.isAllowed(userId, PermissionClient.WEB, permissionCode)) {
            throw denied("当前账号缺少导出权限：" + permissionCode);
        }
    }

    private void requirePrimaryStudent(long userId, long studentId) {
        boolean allowed = growthPointQueryMapper.findPrimaryStudentsByParentUserId(userId).stream()
                .anyMatch(student -> Objects.equals(student.studentId(), studentId));
        if (!allowed) {
            throw denied("当前账号不是目标学生的活动主家长");
        }
    }

    private SystemOperationAccessDeniedException denied(String message) {
        return new SystemOperationAccessDeniedException(message);
    }
}
