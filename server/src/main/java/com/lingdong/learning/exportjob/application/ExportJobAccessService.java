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

    public ExportJobAccessService(
            FeatureAccessService featureAccessService,
            PermissionDecisionService permissionDecisionService,
            UserRoleMapper userRoleMapper,
            UserMapper userMapper,
            GrowthPointQueryMapper growthPointQueryMapper
    ) {
        this.featureAccessService = featureAccessService;
        this.permissionDecisionService = permissionDecisionService;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.growthPointQueryMapper = growthPointQueryMapper;
    }

    public void requireOrdinaryCreate(long userId, long studentId) {
        requireFeatures();
        requireEnabledUser(userId);
        requireRole(userId, "PARENT", "仅家长可创建积分明细导出");
        requirePermission(userId, "EXPORT_JOB_CREATE");
        requirePermission(userId, "GROWTH_POINT_READ_CHILD");
        requirePrimaryStudent(userId, studentId);
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
                && !userRoleMapper.hasRoleCode(userId, "SYS_ADMIN")) {
            throw denied("当前账号不能查询导出作业");
        }
    }

    public void requireExecution(ExportJobRecord job) {
        if (job == null || job.requesterId() == null) {
            throw denied("导出作业执行身份无效");
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
