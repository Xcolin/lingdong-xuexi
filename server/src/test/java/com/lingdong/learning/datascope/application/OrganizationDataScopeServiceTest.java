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
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void letsAllScopeSystemAdministratorAccessAnyOrganization() {
        User administrator = createUserWithRole("scope_sys_admin", "系统管理员", "SYS_ADMIN");
        Organization target = createClass("ALL_SCOPE");

        assertThat(organizationDataScopeService.canAccess(administrator.id(), target.id())).isTrue();
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

        assertThat(organizationDataScopeService.canAccess(operator.id(), target.id())).isTrue();
    }

    private Organization createClass(String prefix) {
        Organization region = organizationApplicationService.createOrganization(new CreateOrganizationCommand(prefix + "_REGION", prefix + "区域", "REGION", null, 10));
        Organization school = organizationApplicationService.createOrganization(new CreateOrganizationCommand(prefix + "_SCHOOL", prefix + "学校", "SCHOOL", region.id(), 10));
        return organizationApplicationService.createOrganization(new CreateOrganizationCommand(prefix + "_CLASS", prefix + "班级", "CLASS", school.id(), 10));
    }

    private User createUserWithRole(String username, String name, String roleCode) {
        User user = userAccessApplicationService.createUser(new CreateUserCommand(username, name, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
}
