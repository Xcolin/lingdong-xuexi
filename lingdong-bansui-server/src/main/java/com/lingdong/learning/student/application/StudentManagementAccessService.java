package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import org.springframework.stereotype.Service;

/** Management capabilities and organization scope are independent of built-in role names. */
@Service
public class StudentManagementAccessService {
    private final PermissionDecisionService permissions;
    private final OrganizationDataScopeService scopes;
    private final StudentOrganizationMapper relationships;

    public StudentManagementAccessService(PermissionDecisionService permissions,
            OrganizationDataScopeService scopes, StudentOrganizationMapper relationships) {
        this.permissions = permissions;
        this.scopes = scopes;
        this.relationships = relationships;
    }

    public void require(AuthenticatedUser user, String permission) {
        if (user == null || user.clientType() == null
                || !permissions.isAllowed(user.userId(), PermissionClient.valueOf(user.clientType().name()), permission)) {
            throw new SystemOperationAccessDeniedException("无权执行学生管理操作");
        }
    }

    public boolean webAllowed(AuthenticatedUser user, String permission) {
        return user != null && user.clientType() == AuthClientType.WEB
                && permissions.isAllowed(user.userId(), PermissionClient.WEB, permission);
    }

    public OrganizationDataScope scope(AuthenticatedUser user) {
        return scopes.resolve(user.userId());
    }

    public boolean organizationAllowed(AuthenticatedUser user, Long organizationId) {
        return user != null && scopes.canAccess(user.userId(), organizationId);
    }

    public boolean studentAllowed(AuthenticatedUser user, Long studentId) {
        if (user == null) return false;
        return relationships.findActiveEnrollmentOrganizationIds(studentId).stream()
                .anyMatch(id -> scopes.canAccess(user.userId(), id)
                        && relationships.existsActiveByStudentAndOrganization(studentId, id));
    }
}
