package com.lingdong.learning.datascope.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrganizationDataScopeServiceTest {
    @Autowired private OrganizationDataScopeService organizationDataScopeService;
    @Autowired private DataScopeAdministrationService dataScopeAdministrationService;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private RoleApplicationService roleApplicationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void letsAllScopeSystemAdministratorAccessAnyOrganization() {
        User administrator = createUserWithRole("scope_sys_admin", "系统管理员", "SYS_ADMIN");
        Organization target = createClass("ALL_SCOPE");

        assertThat(organizationDataScopeService.resolve(administrator.id()).allOrganizations()).isTrue();
        assertThat(organizationDataScopeService.canAccess(administrator.id(), target.id())).isTrue();
        assertThat(organizationDataScopeService.findAccessibleOrganizations(administrator.id()))
                .extracting(Organization::id)
                .contains(target.id());
    }

    @Test
    void requiresTeacherRoleScopeAndUserOrganizationAssociation() {
        User administrator = createUserWithRole("scope_config_admin", "配置管理员", "SYS_ADMIN");
        User teacher = userAccessApplicationService.createUser(new CreateUserCommand("scope_teacher", "教师", null, UserType.ORGANIZATION));
        Organization target = createClass("TEACHER_SCOPE");
        Role teacherRole = roleMapper.findByCode("TEACHER");

        userAccessApplicationService.associateWithOrganization(new AssociateUserWithOrganizationCommand(teacher.id(), target.id()));
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(teacher.id(), teacherRole.id(), target.id()));

        assertThat(organizationDataScopeService.canAccess(teacher.id(), target.id())).isTrue();

        Organization otherTarget = createClass("TEACHER_OTHER");
        assertThat(organizationDataScopeService.canAccess(teacher.id(), otherTarget.id())).isFalse();

        userAccessApplicationService.associateWithOrganization(new AssociateUserWithOrganizationCommand(teacher.id(), otherTarget.id()));
        dataScopeAdministrationService.configureOrganizationAdministrator(administrator.id(), teacher.id(), otherTarget.id());
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_organization_admin where user_id = ? and organization_id = ?",
                Long.class, teacher.id(), otherTarget.id()
        ))).hasSize(19);
        assertThat(organizationDataScopeService.canAccess(teacher.id(), target.id())).isFalse();
    }

    @Test
    void usesConfiguredOrganizationsForCustomRoleDataScope() {
        User administrator = createUserWithRole("scope_custom_admin", "系统管理员", "SYS_ADMIN");
        User operator = userAccessApplicationService.createUser(new CreateUserCommand("scope_custom_user", "自定义范围用户", null, UserType.ORGANIZATION));
        Organization target = createClass("CUSTOM_SCOPE");
        Role role = roleApplicationService.createCustomRole(new CreateCustomRoleCommand("CUSTOM_SCOPE_VIEW", "自定义范围查看", null, RoleDataScope.CUSTOM));

        userAccessApplicationService.associateWithOrganization(new AssociateUserWithOrganizationCommand(operator.id(), target.id()));
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(operator.id(), role.id(), target.id()));
        dataScopeAdministrationService.configureRoleCustomScope(administrator.id(), role.id(), target.id());
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_role_data_scope where role_id = ? and organization_id = ?",
                Long.class, role.id(), target.id()
        ))).hasSize(19);

        assertThat(organizationDataScopeService.resolve(operator.id()).rootPaths())
                .containsExactly(target.path());
        assertThat(organizationDataScopeService.canAccess(operator.id(), target.id())).isTrue();
        assertThat(organizationDataScopeService.findAccessibleOrganizations(operator.id()))
                .extracting(Organization::id)
                .containsExactly(target.id());
    }

    @Test
    void resolvesRegionSchoolAndClassScopesIntoReusableOrganizationQueries() {
        OrganizationTree first = createTree("QUERY_FIRST");
        OrganizationTree second = createTree("QUERY_SECOND");

        User regionUser = createScopedUser("scope_region_user", "区域用户", RoleDataScope.REGION, first.region());
        User schoolUser = createScopedUser("scope_school_user", "学校用户", RoleDataScope.SCHOOL, first.school());
        User classUser = createScopedUser("scope_class_user", "班级用户", RoleDataScope.CLASS, first.schoolClass());

        assertThat(organizationDataScopeService.resolve(regionUser.id()).rootPaths())
                .containsExactly(first.region().path());
        assertThat(organizationDataScopeService.findAccessibleOrganizations(regionUser.id()))
                .extracting(Organization::id)
                .containsExactly(first.region().id(), first.school().id(), first.schoolClass().id());
        assertThat(organizationDataScopeService.canAccess(regionUser.id(), second.schoolClass().id())).isFalse();

        assertThat(organizationDataScopeService.resolve(schoolUser.id()).rootPaths())
                .containsExactly(first.school().path());
        assertThat(organizationDataScopeService.findAccessibleOrganizations(schoolUser.id()))
                .extracting(Organization::id)
                .containsExactly(first.school().id(), first.schoolClass().id());

        assertThat(organizationDataScopeService.resolve(classUser.id()).rootPaths())
                .containsExactly(first.schoolClass().path());
        assertThat(organizationDataScopeService.findAccessibleOrganizations(classUser.id()))
                .extracting(Organization::id)
                .containsExactly(first.schoolClass().id());
    }

    @Test
    void keepsSelfScopeSeparateFromOrganizationQueries() {
        Organization target = createClass("SELF_QUERY");
        User selfUser = createUserWithRole("scope_self_user", "本人用户", "PARENT");

        OrganizationDataScope scope = organizationDataScopeService.resolve(selfUser.id());

        assertThat(scope.selfAllowed()).isTrue();
        assertThat(scope.allOrganizations()).isFalse();
        assertThat(scope.rootPaths()).isEmpty();
        assertThat(organizationDataScopeService.canAccess(selfUser.id(), target.id())).isFalse();
        assertThat(organizationDataScopeService.findAccessibleOrganizations(selfUser.id())).isEmpty();
    }

    @Test
    void rejectsDisabledUsersAndMismatchedOrganizationTypes() {
        OrganizationTree tree = createTree("INVALID_SCOPE");
        User mismatched = createScopedUser(
                "scope_mismatched_user", "范围错配用户", RoleDataScope.SCHOOL, tree.region());
        assertThat(organizationDataScopeService.resolve(mismatched.id()).rootPaths()).isEmpty();

        User disabled = createScopedUser(
                "scope_disabled_user", "停用范围用户", RoleDataScope.CLASS, tree.schoolClass());
        jdbcTemplate.update("update sys_user set status = ? where id = ?", UserStatus.DISABLED.name(), disabled.id());

        assertThat(organizationDataScopeService.resolve(disabled.id()).isEmpty()).isTrue();
        assertThat(organizationDataScopeService.canAccess(disabled.id(), tree.schoolClass().id())).isFalse();
    }

    private Organization createClass(String prefix) {
        return createTree(prefix).schoolClass();
    }

    private OrganizationTree createTree(String prefix) {
        Organization region = organizationApplicationService.createOrganization(new CreateOrganizationCommand(prefix + "_REGION", prefix + "区域", "REGION", null, 10));
        Organization school = organizationApplicationService.createOrganization(new CreateOrganizationCommand(prefix + "_SCHOOL", prefix + "学校", "SCHOOL", region.id(), 10));
        Organization schoolClass = organizationApplicationService.createOrganization(new CreateOrganizationCommand(prefix + "_CLASS", prefix + "班级", "CLASS", school.id(), 10));
        return new OrganizationTree(region, school, schoolClass);
    }

    private User createScopedUser(
            String username,
            String name,
            RoleDataScope dataScope,
            Organization organization
    ) {
        Role role = roleApplicationService.createCustomRole(new CreateCustomRoleCommand(
                username.toUpperCase(), name + "角色", null, dataScope));
        User user = userAccessApplicationService.createUser(new CreateUserCommand(
                username, name, null, UserType.ORGANIZATION));
        userAccessApplicationService.associateWithOrganization(new AssociateUserWithOrganizationCommand(
                user.id(), organization.id()));
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(
                user.id(), role.id(), organization.id()));
        return user;
    }

    private User createUserWithRole(String username, String name, String roleCode) {
        User user = userAccessApplicationService.createUser(new CreateUserCommand(username, name, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private record OrganizationTree(Organization region, Organization school, Organization schoolClass) {
    }
}
