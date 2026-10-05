package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 在每次操作时复核开关、账号、角色、动态权限、所有权和组织范围。 */
@Service
public class StudentImportAccessService {
    private static final String STUDENT_IMPORT_FEATURE = "STUDENT_BATCH_IMPORT";
    private static final String IMPORT_VALIDATION_FEATURE = "DATA_IMPORT_VALIDATION";
    private static final String ATTACHMENT_FEATURE = "ATTACHMENT_SERVICE";
    private static final String TEMPLATE_FEATURE = "IMPORT_EXPORT_TEMPLATE_MANAGEMENT";

    private final FeatureAccessService featureAccessService;
    private final PermissionDecisionService permissionDecisionService;
    private final OrganizationDataScopeService dataScopeService;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;

    public StudentImportAccessService(
            FeatureAccessService featureAccessService,
            PermissionDecisionService permissionDecisionService,
            OrganizationDataScopeService dataScopeService,
            UserRoleMapper userRoleMapper,
            UserMapper userMapper,
            OrganizationMapper organizationMapper
    ) {
        this.featureAccessService = featureAccessService;
        this.permissionDecisionService = permissionDecisionService;
        this.dataScopeService = dataScopeService;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
        this.organizationMapper = organizationMapper;
    }

    public void requireExecute(long userId, long organizationId, Long classOrganizationId) {
        requireCurrentIdentity(userId, "STUDENT_IMPORT_EXECUTE");
        Organization school = requireOrganization(userId, organizationId, "SCHOOL", "目标组织不是可用学校");
        if (classOrganizationId == null) {
            return;
        }
        Organization classOrganization = requireOrganization(
                userId, classOrganizationId, "CLASS", "目标组织不是可用班级");
        if (school.path() == null || classOrganization.path() == null
                || !classOrganization.path().startsWith(school.path())) {
            throw new SystemOperationAccessDeniedException("目标班级不属于所选学校");
        }
    }

    public void requireOwnerRead(long userId, StudentImportExecutionRecord execution) {
        requireOwner(userId, execution, "STUDENT_IMPORT_RESULT_READ");
    }

    public void requireCredentialDownload(long userId, StudentImportExecutionRecord execution) {
        requireOwner(userId, execution, "STUDENT_IMPORT_CREDENTIAL_DOWNLOAD");
    }

    public void requireListRead(long userId) {
        requireCurrentIdentity(userId, "STUDENT_IMPORT_RESULT_READ");
    }

    public com.lingdong.learning.datascope.application.OrganizationDataScope currentScope(long userId) {
        return dataScopeService.resolve(userId);
    }

    public void requireFeatures() {
        featureAccessService.requireEnabled(STUDENT_IMPORT_FEATURE, null);
        featureAccessService.requireEnabled(IMPORT_VALIDATION_FEATURE, null);
        featureAccessService.requireEnabled(ATTACHMENT_FEATURE, null);
        featureAccessService.requireEnabled(TEMPLATE_FEATURE, null);
    }

    private void requireOwner(
            long userId,
            StudentImportExecutionRecord execution,
            String permissionCode
    ) {
        requireCurrentIdentity(userId, permissionCode);
        if (execution == null || !Objects.equals(execution.requesterId(), userId)
                || !dataScopeService.canAccess(userId, execution.organizationId())) {
            throw denied("当前账号无权访问该学员导入执行");
        }
    }

    private void requireCurrentIdentity(long userId, String permissionCode) {
        requireFeatures();
        User user = userMapper.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED) {
            throw denied("当前账号不可用");
        }
        if (!permissionDecisionService.isAllowed(userId, PermissionClient.WEB, permissionCode)) {
            throw denied("当前账号缺少学员导入权限：" + permissionCode);
        }
    }

    private Organization requireOrganization(
            long userId,
            long organizationId,
            String expectedType,
            String typeMessage
    ) {
        Organization organization = organizationMapper.findById(organizationId);
        if (organization == null || !dataScopeService.canAccess(userId, organizationId)) {
            throw denied("目标组织不存在或不在当前数据范围内");
        }
        if (!expectedType.equals(organization.typeCode())) {
            throw new IllegalArgumentException(typeMessage);
        }
        if (!OrganizationOperationalStatusService.isOperational(organization)) {
            throw new IllegalStateException("目标组织已停用");
        }
        return organization;
    }

    private SystemOperationAccessDeniedException denied(String message) {
        return new SystemOperationAccessDeniedException(message);
    }
}
