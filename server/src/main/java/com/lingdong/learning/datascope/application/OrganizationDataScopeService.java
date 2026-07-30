package com.lingdong.learning.datascope.application;

import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.RoleAssignmentScopeMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.RoleDataScopeMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import org.springframework.stereotype.Service;
import java.util.List;

/** Computes the intersection of role, user-organization, and optional organization-admin boundaries. */
@Service
public class OrganizationDataScopeService {
    private final RoleAssignmentScopeMapper roleScopeMapper; private final RoleDataScopeMapper customScopeMapper;
    private final UserOrganizationMapper userOrganizationMapper; private final OrganizationAdminMapper organizationAdminMapper;
    private final OrganizationMapper organizationMapper;
    public OrganizationDataScopeService(RoleAssignmentScopeMapper r, RoleDataScopeMapper c, UserOrganizationMapper u, OrganizationAdminMapper a, OrganizationMapper o) { roleScopeMapper=r; customScopeMapper=c; userOrganizationMapper=u; organizationAdminMapper=a; organizationMapper=o; }

    public boolean canAccess(Long userId, Long targetOrganizationId) {
        if (userId == null || targetOrganizationId == null) return false;
        Organization target = organizationMapper.findById(targetOrganizationId); if (target == null) return false;
        List<RoleAssignmentScope> assignments = roleScopeMapper.findByUserId(userId);
        if (assignments.stream().anyMatch(scope -> scope.dataScope() == RoleDataScope.ALL)) return true;
        List<Long> userRoots = userOrganizationMapper.findOrganizationIds(userId);
        if (!containsTarget(userRoots, target)) return false;
        List<Long> adminRoots = organizationAdminMapper.findOrganizationIds(userId);
        if (!adminRoots.isEmpty() && !containsTarget(adminRoots, target)) return false;
        return assignments.stream().anyMatch(scope -> allowsTarget(scope, target));
    }

    private boolean allowsTarget(RoleAssignmentScope scope, Organization target) {
        if (scope.dataScope() == RoleDataScope.SELF) return false;
        if (scope.dataScope() != RoleDataScope.CUSTOM && scope.organizationId() == null) return false;
        List<Long> roots = scope.dataScope() == RoleDataScope.CUSTOM
                ? customScopeMapper.findOrganizationIds(scope.roleId())
                : List.of(scope.organizationId());
        return containsTarget(roots, target);
    }

    private boolean containsTarget(List<Long> rootIds, Organization target) {
        return rootIds.stream().filter(java.util.Objects::nonNull).map(organizationMapper::findById).filter(java.util.Objects::nonNull).anyMatch(root -> target.path().startsWith(root.path()));
    }
}
