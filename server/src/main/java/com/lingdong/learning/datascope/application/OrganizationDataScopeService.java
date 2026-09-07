package com.lingdong.learning.datascope.application;

import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.RoleAssignmentScopeMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.RoleDataScopeMapper;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 解析并应用角色、用户组织关系和组织管理员边界的通用组织数据范围。 */
@Service
public class OrganizationDataScopeService {
    private final RoleAssignmentScopeMapper roleScopeMapper;
    private final RoleDataScopeMapper customScopeMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final OrganizationAdminMapper organizationAdminMapper;
    private final OrganizationMapper organizationMapper;
    private final UserMapper userMapper;

    public OrganizationDataScopeService(
            RoleAssignmentScopeMapper roleScopeMapper,
            RoleDataScopeMapper customScopeMapper,
            UserOrganizationMapper userOrganizationMapper,
            OrganizationAdminMapper organizationAdminMapper,
            OrganizationMapper organizationMapper,
            UserMapper userMapper
    ) {
        this.roleScopeMapper = roleScopeMapper;
        this.customScopeMapper = customScopeMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.organizationAdminMapper = organizationAdminMapper;
        this.organizationMapper = organizationMapper;
        this.userMapper = userMapper;
    }

    public OrganizationDataScope resolve(Long userId) {
        User user = userId == null ? null : userMapper.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED) {
            return OrganizationDataScope.empty();
        }

        List<RoleAssignmentScope> assignments = roleScopeMapper.findByUserId(userId);
        boolean selfAllowed = assignments.stream()
                .anyMatch(assignment -> assignment.dataScope() == RoleDataScope.SELF);
        if (assignments.stream().anyMatch(assignment -> assignment.dataScope() == RoleDataScope.ALL)) {
            return OrganizationDataScope.all(selfAllowed);
        }

        List<String> roleRootPaths = resolveRoleRootPaths(assignments);
        List<String> userRootPaths = findPaths(userOrganizationMapper.findOrganizationIds(userId));
        List<String> effectivePaths = intersect(roleRootPaths, userRootPaths);

        List<String> administratorRootPaths = findPaths(organizationAdminMapper.findOrganizationIds(userId));
        if (!administratorRootPaths.isEmpty()) {
            effectivePaths = intersect(effectivePaths, administratorRootPaths);
        }
        return new OrganizationDataScope(false, selfAllowed, effectivePaths);
    }

    public boolean canAccess(Long userId, Long targetOrganizationId) {
        if (targetOrganizationId == null) {
            return false;
        }
        return resolve(userId).allows(organizationMapper.findById(targetOrganizationId));
    }

    public List<Organization> findAccessibleOrganizations(Long userId) {
        OrganizationDataScope scope = resolve(userId);
        if (scope.allOrganizations()) {
            return organizationMapper.findAll();
        }
        if (scope.rootPaths().isEmpty()) {
            return List.of();
        }
        return organizationMapper.findByRootPaths(scope.rootPaths());
    }

    private List<String> resolveRoleRootPaths(List<RoleAssignmentScope> assignments) {
        List<String> paths = new ArrayList<>();
        for (RoleAssignmentScope assignment : assignments) {
            if (assignment.dataScope() == RoleDataScope.CUSTOM) {
                paths.addAll(findPaths(customScopeMapper.findOrganizationIds(assignment.roleId())));
                continue;
            }
            if (assignment.dataScope() == RoleDataScope.REGION
                    || assignment.dataScope() == RoleDataScope.SCHOOL
                    || assignment.dataScope() == RoleDataScope.CLASS) {
                Organization root = organizationMapper.findById(assignment.organizationId());
                if (root != null && assignment.dataScope().name().equals(root.typeCode())) {
                    paths.add(root.path());
                }
            }
        }
        return OrganizationDataScope.normalize(paths);
    }

    private List<String> findPaths(List<Long> organizationIds) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            return List.of();
        }
        return OrganizationDataScope.normalize(organizationMapper.findByIds(organizationIds).stream()
                .map(Organization::path)
                .toList());
    }

    private List<String> intersect(List<String> leftPaths, List<String> rightPaths) {
        if (leftPaths.isEmpty() || rightPaths.isEmpty()) {
            return List.of();
        }
        List<String> intersections = new ArrayList<>();
        for (String leftPath : leftPaths) {
            for (String rightPath : rightPaths) {
                if (leftPath.startsWith(rightPath)) {
                    intersections.add(leftPath);
                } else if (rightPath.startsWith(leftPath)) {
                    intersections.add(rightPath);
                }
            }
        }
        return OrganizationDataScope.normalize(intersections);
    }
}
