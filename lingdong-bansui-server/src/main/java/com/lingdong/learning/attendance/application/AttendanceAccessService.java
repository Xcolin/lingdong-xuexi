package com.lingdong.learning.attendance.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.attendance.infrastructure.persistence.AttendanceScope;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.springframework.stereotype.Service;
import java.util.List;

/** 功能权限与对象范围分别校验，动态角色仍必须具备真实组织授权。 */
@Service
public class AttendanceAccessService {
    private final FeatureAccessService features;
    private final PermissionDecisionService permissions;
    private final OrganizationDataScopeService scopes;

    public AttendanceAccessService(FeatureAccessService features, PermissionDecisionService permissions,
            OrganizationDataScopeService scopes) {
        this.features = features;
        this.permissions = permissions;
        this.scopes = scopes;
    }

    public AttendanceScope require(AuthenticatedUser user, boolean write) {
        features.requireEnabled("ATTENDANCE_MANAGEMENT", null);
        if (user == null || user.clientType() == null
                || !permissions.isAllowed(user.userId(), PermissionClient.valueOf(user.clientType().name()),
                        write ? "ATTENDANCE_RECORD" : "ATTENDANCE_READ")) throw denied();
        var roles = user.roleCodes();
        String mode;
        if (roles.contains("ORG_ADMIN")) mode = "ORGANIZATION";
        else if (roles.contains("TEACHER")) mode = "TEACHER";
        else if (roles.contains("PARENT")) mode = "PARENT";
        else if (roles.contains("STUDENT")) mode = "STUDENT";
        else mode = "ORGANIZATION";
        if (write && (mode.equals("PARENT") || mode.equals("STUDENT"))) throw denied();
        if (!mode.equals("ORGANIZATION")) return new AttendanceScope(user.userId(), mode, false, List.of());
        var scope = scopes.resolve(user.userId());
        if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) throw denied();
        return new AttendanceScope(user.userId(), mode, scope.allOrganizations(), scope.rootPaths());
    }

    private SystemOperationAccessDeniedException denied() {
        return new SystemOperationAccessDeniedException("无权访问考勤或没有有效数据范围");
    }
}
