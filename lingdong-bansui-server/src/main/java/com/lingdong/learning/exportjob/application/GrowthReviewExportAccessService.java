package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.application.GrowthReviewExportPreparationService.Prepared;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** 复盘 PDF 专用授权，区分新生成与历史读取，不改变既有 XLSX 数据集规则。 */
@Service
public class GrowthReviewExportAccessService {
    public static final String PDF_FEATURE = "GROWTH_REVIEW_PDF_EXPORT";
    private final FeatureAccessService features;
    private final PermissionDecisionService permissions;
    private final UserMapper users;
    private final UserRoleMapper roles;
    private final ParentStudentMapper relations;

    public GrowthReviewExportAccessService(FeatureAccessService features, PermissionDecisionService permissions,
                                          UserMapper users, UserRoleMapper roles, ParentStudentMapper relations) {
        this.features = features;
        this.permissions = permissions;
        this.users = users;
        this.roles = roles;
        this.relations = relations;
    }

    public void requireCreate(AuthenticatedUser user, Long studentId) {
        requireWeb(user);
        requireGeneration(user.userId(), studentId);
    }

    /** 后台无登录会话，必须重新检查持久化申请人的当前资格，而非信任创建时角色快照。 */
    public void requireExecution(Prepared storedScope) {
        requireStoredScope(storedScope);
        requireGeneration(storedScope.requesterId(), storedScope.studentId());
    }

    /** 范围必须来自受控持久化内容；文件成功状态和附件关系由统一下载流程另行校验。 */
    public void requireHistoricalRead(AuthenticatedUser user, Prepared storedScope) {
        requireWeb(user);
        requireStoredScope(storedScope);
        if (!Objects.equals(user.userId(), storedScope.requesterId())) {
            throw denied("当前账号无权访问他人的复盘导出");
        }
        // 新导出和模板管理关闭不撤销历史文件资格，但统一附件服务停用仍须拦截。
        features.requireEnabled("ATTACHMENT_SERVICE", null);
        requireReader(user.userId(), storedScope.studentId());
        requirePermission(user.userId(), "EXPORT_JOB_READ");
    }

    private void requireGeneration(Long userId, Long studentId) {
        features.requireEnabled(PDF_FEATURE, null);
        features.requireEnabled("DATA_EXPORT", null);
        features.requireEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null);
        features.requireEnabled("ATTACHMENT_SERVICE", null);
        requireReader(userId, studentId);
        requirePermission(userId, "EXPORT_JOB_CREATE");
    }

    private void requireReader(Long userId, Long studentId) {
        if (userId == null || studentId == null || userId <= 0 || studentId <= 0) {
            throw denied("复盘导出身份或对象无效");
        }
        var user = users.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED
                || !roles.hasRoleCode(userId, "PARENT") || roles.hasRoleCode(userId, "SYS_AUDITOR")) {
            throw denied("当前账号不具备家长复盘导出身份");
        }
        requirePermission(userId, "GROWTH_REVIEW_READ_CHILD");
        if (!relations.existsActiveByParentAndStudent(userId, studentId)) {
            throw denied("当前账号与目标学生不存在有效亲子关系");
        }
    }

    private void requirePermission(Long userId, String permission) {
        if (!permissions.isAllowed(userId, PermissionClient.WEB, permission)) {
            throw denied("当前账号缺少复盘导出权限：" + permission);
        }
    }

    private static void requireWeb(AuthenticatedUser user) {
        if (user == null || user.userId() == null || user.clientType() != AuthClientType.WEB) {
            throw denied("复盘报告导出仅允许已登录的Web家长访问");
        }
    }

    private static void requireStoredScope(Prepared scope) {
        if (scope == null || scope.requesterId() == null || scope.studentId() == null) {
            throw denied("复盘导出持久化范围无效");
        }
    }

    private static SystemOperationAccessDeniedException denied(String message) {
        return new SystemOperationAccessDeniedException(message);
    }
}
