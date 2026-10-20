package com.lingdong.learning.organization.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationWorkbenchStatisticsStore;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryCriteria;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherManagementMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 查询当前机构管理员小程序工作台，不接受外部用户或组织范围参数。 */
@Service
public class OrganizationWorkbenchQueryService {
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";
    private static final String ORGANIZATION_MINIAPP_AUTH_FEATURE = "ORGANIZATION_MINIAPP_AUTH";
    private static final String CLASS_READ_PERMISSION = "CLASS_READ";
    private static final String TEACHER_READ_PERMISSION = "TEACHER_READ";
    private static final String STUDENT_ORGANIZATION_MANAGE_PERMISSION = "STUDENT_ORGANIZATION_MANAGE";

    private final UserMapper userMapper;
    private final OrganizationAdminMapper organizationAdminMapper;
    private final FeatureAccessService featureAccessService;
    private final PermissionDecisionService permissionDecisionService;
    private final OrganizationDataScopeService dataScopeService;
    private final OrganizationWorkbenchStatisticsStore statisticsStore;
    private final TeacherManagementMapper teacherManagementMapper;
    private final StudentOrganizationMapper studentOrganizationMapper;

    public OrganizationWorkbenchQueryService(
            UserMapper userMapper,
            OrganizationAdminMapper organizationAdminMapper,
            FeatureAccessService featureAccessService,
            PermissionDecisionService permissionDecisionService,
            OrganizationDataScopeService dataScopeService,
            OrganizationWorkbenchStatisticsStore statisticsStore,
            TeacherManagementMapper teacherManagementMapper,
            StudentOrganizationMapper studentOrganizationMapper
    ) {
        this.userMapper = userMapper;
        this.organizationAdminMapper = organizationAdminMapper;
        this.featureAccessService = featureAccessService;
        this.permissionDecisionService = permissionDecisionService;
        this.dataScopeService = dataScopeService;
        this.statisticsStore = statisticsStore;
        this.teacherManagementMapper = teacherManagementMapper;
        this.studentOrganizationMapper = studentOrganizationMapper;
    }

    /** 返回当前机构管理员直接管理的启用组织摘要。 */
    public OrganizationWorkbenchContext getContext(AuthenticatedUser currentUser) {
        User user = requireWorkbenchUser(currentUser);
        List<OrganizationWorkbenchOrganization> organizations = organizationAdminMapper
                .findEnabledManagedOrganizationSummaries(user.id()).stream()
                .map(row -> new OrganizationWorkbenchOrganization(row.id(), row.name(), row.typeCode()))
                .toList();
        if (organizations.isEmpty()) {
            throw accessDenied();
        }
        return new OrganizationWorkbenchContext(
                user.id(), user.username(), user.displayName(),
                permissionDecisionService.findAllowedCodes(user.id(), PermissionClient.MINIAPP),
                organizations);
    }

    /** 返回工作台统计：校区/年级/班级/教师/学生计数，无对应 read 权限的项为 null。 */
    public OrganizationWorkbenchStatistics getStatistics(AuthenticatedUser currentUser) {
        User user = requireWorkbenchUser(currentUser);
        OrganizationDataScope scope = dataScopeService.resolve(user.id());
        List<String> allowed = permissionDecisionService.findAllowedCodes(user.id(), PermissionClient.MINIAPP);
        Long campusCount = null;
        Long gradeCount = null;
        Long classCount = null;
        if (allowed.contains(CLASS_READ_PERMISSION)) {
            Map<String, Long> counts = statisticsStore
                    .countEducationalOrganizations(scope.allOrganizations(), scope.rootPaths());
            campusCount = counts.get("CAMPUS");
            gradeCount = counts.get("GRADE");
            classCount = counts.get("CLASS");
        }
        return new OrganizationWorkbenchStatistics(
                campusCount, gradeCount, classCount,
                teacherCount(scope, allowed), studentCount(scope, allowed));
    }

    /** 教师计数复用教师目录口径（启用中的成长伙伴账号，限定数据范围）。 */
    private Long teacherCount(OrganizationDataScope scope, List<String> allowed) {
        if (!allowed.contains(TEACHER_READ_PERMISSION)) {
            return null;
        }
        if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) {
            return 0L;
        }
        return teacherManagementMapper.count(new TeacherDirectoryCriteria(
                null, null, null, UserStatus.ENABLED, scope.allOrganizations(), scope.rootPaths(), 1, 0));
    }

    /** 学生计数复用组织学员关系页口径（范围内有效入学的去重学生数）。 */
    private Long studentCount(OrganizationDataScope scope, List<String> allowed) {
        if (!allowed.contains(STUDENT_ORGANIZATION_MANAGE_PERMISSION)) {
            return null;
        }
        return studentOrganizationMapper.countActiveEnrollmentGroups(scope);
    }

    private User requireWorkbenchUser(AuthenticatedUser currentUser) {
        featureAccessService.requireEnabled(ORGANIZATION_MINIAPP_AUTH_FEATURE, null);
        if (currentUser == null
                || currentUser.clientType() != AuthClientType.MINIAPP
                || !currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE)) {
            throw accessDenied();
        }
        User user = userMapper.findById(currentUser.userId());
        if (user == null || user.status() != UserStatus.ENABLED || user.type() == UserType.STUDENT) {
            throw accessDenied();
        }
        return user;
    }

    private SystemOperationAccessDeniedException accessDenied() {
        return new SystemOperationAccessDeniedException("当前身份不能访问机构工作台");
    }
}
