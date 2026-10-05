package com.lingdong.learning.importjob.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 按当前开关、权限、角色和组织范围判断导入作业访问。 */
@Service
public class ImportJobAccessService {
    private static final String IMPORT_FEATURE = "DATA_IMPORT_VALIDATION";
    private static final String ATTACHMENT_FEATURE = "ATTACHMENT_SERVICE";
    private static final String TEMPLATE_FEATURE = "IMPORT_EXPORT_TEMPLATE_MANAGEMENT";

    private final FeatureAccessService featureAccessService;
    private final PermissionDecisionService permissionDecisionService;
    private final OrganizationDataScopeService dataScopeService;
    private final UserRoleMapper userRoleMapper;

    public ImportJobAccessService(
            FeatureAccessService featureAccessService,
            PermissionDecisionService permissionDecisionService,
            OrganizationDataScopeService dataScopeService,
            UserRoleMapper userRoleMapper
    ) {
        this.featureAccessService = featureAccessService;
        this.permissionDecisionService = permissionDecisionService;
        this.dataScopeService = dataScopeService;
        this.userRoleMapper = userRoleMapper;
    }

    public void requireCreate(Long userId, Long organizationId) {
        requireFeatures(organizationId);
        requirePermission(userId, "IMPORT_JOB_CREATE");
        if (organizationId != null && !dataScopeService.canAccess(userId, organizationId)) {
            throw new SystemOperationAccessDeniedException("当前账号无权在所选组织创建导入校验作业");
        }
    }

    public void requireRead(Long userId, ImportJobRecord job) {
        requireFeatures(job.organizationId());
        requirePermission(userId, "IMPORT_JOB_READ");
        
        boolean owner = Objects.equals(job.requesterId(), userId);
        boolean currentScope = job.organizationId() == null
                || dataScopeService.canAccess(userId, job.organizationId());
        if (!owner || !currentScope) {
            throw new SystemOperationAccessDeniedException("当前账号无权读取该导入校验作业");
        }
    }

    public boolean isSystemAdministrator(Long userId) {
        return false;
    }

    public void requireReadPermission(Long userId) {
        requireFeatures(null);
        requirePermission(userId, "IMPORT_JOB_READ");
    }

    private void requireFeatures(Long organizationId) {
        featureAccessService.requireEnabled(IMPORT_FEATURE, organizationId);
        featureAccessService.requireEnabled(ATTACHMENT_FEATURE, organizationId);
        featureAccessService.requireEnabled(TEMPLATE_FEATURE, organizationId);
    }

    private void requirePermission(Long userId, String code) {
        if (!permissionDecisionService.isAllowed(userId, PermissionClient.WEB, code)) {
            throw new SystemOperationAccessDeniedException("当前账号缺少导入校验作业权限：" + code);
        }
    }
}
