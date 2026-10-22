package com.lingdong.learning.user.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UserAccessApplicationServiceTest {
    @Autowired
    private UserAccessApplicationService userAccessApplicationService;

    @Autowired
    private OrganizationApplicationService organizationApplicationService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    void cleanFixedDataResidue() {
        // 非 @Transactional：上轮运行留下的固定用户/组织会污染其他测试类的关键字与唯一性断言。
        String[] users = {"teacher_zhang", "teacher_wang", "teacher_ineffective_role", "teacher_ineffective_relation", "parent_liu", "parent_cancel_guard"};
        String userIn = String.join(",", java.util.Arrays.stream(users).map(u -> "'" + u + "'").toArray(String[]::new));
        jdbcTemplate.update("delete from auth_initial_password_state where user_id in (select id from sys_user where username in (" + userIn + "))");
        jdbcTemplate.update("delete from auth_security_event where session_id in (select id from auth_device_session where user_id in (select id from sys_user where username in (" + userIn + ")))");
        jdbcTemplate.update("delete from auth_device_session where user_id in (select id from sys_user where username in (" + userIn + "))");
        jdbcTemplate.update("delete from sys_user_role where user_id in (select id from sys_user where username in (" + userIn + "))");
        jdbcTemplate.update("delete from sys_user_permission where user_id in (select id from sys_user where username in (" + userIn + "))");
        jdbcTemplate.update("delete from sys_user_organization where user_id in (select id from sys_user where username in (" + userIn + "))");
        jdbcTemplate.update("delete from sys_organization_admin where user_id in (select id from sys_user where username in (" + userIn + "))");
        jdbcTemplate.update("delete from sys_user where username in (" + userIn + ")");
        // 自引用外键 fk_sys_organization_parent：先删子（SCHOOL）再删父（REGION）。
        String[] schoolCodes = {"SCHOOL_USER_A", "SCHOOL_USER_B", "SCHOOL_USER_INEFFECTIVE"};
        String[] regionCodes = {"REGION_USER_A", "REGION_USER_B", "REGION_USER_INEFFECTIVE"};
        for (String[] batch : new String[][]{schoolCodes, regionCodes}) {
            String orgIn = String.join(",", java.util.Arrays.stream(batch).map(o -> "'" + o + "'").toArray(String[]::new));
            jdbcTemplate.update("delete from sys_role_data_scope where organization_id in (select id from sys_organization where organization_code in (" + orgIn + "))");
            jdbcTemplate.update("delete from sys_user_role where organization_id in (select id from sys_organization where organization_code in (" + orgIn + "))");
            jdbcTemplate.update("delete from sys_user_organization where organization_id in (select id from sys_organization where organization_code in (" + orgIn + "))");
            jdbcTemplate.update("delete from sys_organization_admin where organization_id in (select id from sys_organization where organization_code in (" + orgIn + "))");
            jdbcTemplate.update("delete from sys_organization_owner where organization_id in (select id from sys_organization where organization_code in (" + orgIn + "))");
            jdbcTemplate.update("delete from sys_organization where organization_code in (" + orgIn + ")");
        }
    }

    @Test
    void associatesUserBeforeGrantingOrganizationScopedRole() {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand("teacher_zhang", "张老师", "13800000001", UserType.ORGANIZATION)
        );
        assertThat(Long.toString(user.id())).hasSize(19);
        Organization organization = createSchool("REGION_USER_A", "SCHOOL_USER_A", "用户授权学校A");
        Role organizationAdmin = roleMapper.findByCode("ORG_ADMIN");

        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(user.id(), organization.id())
        );
        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(user.id(), organizationAdmin.id(), organization.id())
        );

        assertThat(userRoleMapper.exists(user.id(), organizationAdmin.id(), "ORG:" + organization.id())).isTrue();
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_user_organization where user_id = ? and organization_id = ?",
                Long.class, user.id(), organization.id()
        ))).hasSize(19);
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_user_role where user_id = ? and role_id = ? and organization_scope_key = ?",
                Long.class, user.id(), organizationAdmin.id(), "ORG:" + organization.id()
        ))).hasSize(19);
    }

    @Test
    void rejectsOrganizationScopedRoleBeforeUserIsAssociated() {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand("teacher_wang", "王老师", "13800000002", UserType.ORGANIZATION)
        );
        Organization organization = createSchool("REGION_USER_B", "SCHOOL_USER_B", "用户授权学校B");
        Role teacher = roleMapper.findByCode("TEACHER");

        assertThatThrownBy(() -> userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(user.id(), teacher.id(), organization.id())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("组织关联");
    }

    @Test
    void rejectsOrganizationAssociationAndScopedRoleWhenAncestorMakesOrganizationIneffective() {
        User associatedUser = userAccessApplicationService.createUser(
                new CreateUserCommand("teacher_ineffective_role", "有效停用授权教师", null, UserType.ORGANIZATION)
        );
        User newUser = userAccessApplicationService.createUser(
                new CreateUserCommand("teacher_ineffective_relation", "有效停用关联教师", null, UserType.ORGANIZATION)
        );
        Organization organization = createSchool(
                "REGION_USER_INEFFECTIVE", "SCHOOL_USER_INEFFECTIVE", "上级停用学校");
        Role teacher = roleMapper.findByCode("TEACHER");
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(associatedUser.id(), organization.id())
        );

        jdbcTemplate.update(
                "update sys_organization set effective_status = 'DISABLED' where id = ?",
                organization.id());

        assertThat(jdbcTemplate.queryForObject(
                "select status from sys_organization where id = ?", String.class, organization.id()))
                .isEqualTo("ENABLED");
        assertThatThrownBy(() -> userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(newUser.id(), organization.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("组织已停用");
        assertThatThrownBy(() -> userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(associatedUser.id(), teacher.id(), organization.id())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("组织已停用");
    }

    @Test
    void grantsGlobalRoleToIndependentParent() {
        User parent = userAccessApplicationService.createUser(
                new CreateUserCommand("parent_liu", "刘家长", "13800000003", UserType.FAMILY)
        );
        Role parentRole = roleMapper.findByCode("PARENT");

        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(parent.id(), parentRole.id(), null)
        );

        assertThat(userRoleMapper.exists(parent.id(), parentRole.id(), "GLOBAL")).isTrue();
    }

    @Test
    void rejectsSettingCancelledStatusThroughGeneralAccountManagement() {
        User parent = userAccessApplicationService.createUser(
                new CreateUserCommand("parent_cancel_guard", "注销保护家长", "13800000004", UserType.FAMILY)
        );

        assertThatThrownBy(() -> userAccessApplicationService.updateStatus(
                new UpdateUserStatusCommand(parent.id(), UserStatus.CANCELLED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("注销流程");

        assertThat(userAccessApplicationService.updateStatus(
                new UpdateUserStatusCommand(parent.id(), UserStatus.ENABLED)).status())
                .isEqualTo(UserStatus.ENABLED);
    }

    private Organization createSchool(String regionCode, String schoolCode, String schoolName) {
        Organization region = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(regionCode, regionCode, "REGION", null, 10)
        );
        return organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(schoolCode, schoolName, "SCHOOL", region.id(), 10)
        );
    }
}
