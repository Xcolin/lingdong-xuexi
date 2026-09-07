package com.lingdong.learning.permission.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.domain.PermissionResourceType;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PermissionDecisionServiceTest {
    @Autowired private PermissionAdministrationService permissionAdministrationService;
    @Autowired private PermissionDecisionService permissionDecisionService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void letsRoleGrantThenLetsUserDenyOverrideIt() {
        User administrator = createUserWithRole("permission_admin", "权限管理员", "SYS_ADMIN");
        User parent = createUserWithRole("permission_parent", "家长", "PARENT");
        Role parentRole = roleMapper.findByCode("PARENT");
        Permission permission = permissionAdministrationService.createPermission(new CreatePermissionCommand(
                administrator.id(), "TASK_CREATE", "创建任务", PermissionResourceType.OPERATION, PermissionClient.BOTH, null
        ));
        assertThat(Long.toString(permission.id())).hasSize(19);
        permissionAdministrationService.grantRolePermission(new GrantRolePermissionCommand(administrator.id(), parentRole.id(), permission.id()));
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_role_permission where role_id = ? and permission_id = ?",
                Long.class, parentRole.id(), permission.id()
        ))).hasSize(19);
        assertThat(permissionDecisionService.isAllowed(
                parent.id(), PermissionClient.WEB, "TASK_CREATE")).isTrue();
        assertThat(permissionDecisionService.findAllowedCodes(parent.id(), PermissionClient.WEB))
                .contains("TASK_CREATE");

        permissionAdministrationService.configureUserPermission(new ConfigureUserPermissionCommand(
                administrator.id(), parent.id(), permission.id(), PermissionEffect.DENY
        ));
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_user_permission where user_id = ? and permission_id = ?",
                Long.class, parent.id(), permission.id()
        ))).hasSize(19);
        assertThat(permissionDecisionService.isAllowed(
                parent.id(), PermissionClient.WEB, "TASK_CREATE")).isFalse();
        assertThat(permissionDecisionService.findAllowedCodes(parent.id(), PermissionClient.WEB))
                .doesNotContain("TASK_CREATE");
    }

    @Test
    void rejectsUserAllowWhenTheUserHasNoEnabledRole() {
        User administrator = createUserWithRole("permission_admin_allow", "权限管理员", "SYS_ADMIN");
        User user = userAccessApplicationService.createUser(new CreateUserCommand("permission_viewer", "查看用户", null, UserType.FAMILY));
        Permission permission = permissionAdministrationService.createPermission(new CreatePermissionCommand(
                administrator.id(), "REPORT_VIEW", "查看报表", PermissionResourceType.PAGE, PermissionClient.WEB, null
        ));

        assertThatThrownBy(() -> permissionAdministrationService.configureUserPermission(
                new ConfigureUserPermissionCommand(
                        administrator.id(), user.id(), permission.id(), PermissionEffect.ALLOW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("活动角色");
    }

    @Test
    void letsRoleDenyOverrideAnotherRoleAndUserAllow() {
        User administrator = createUserWithRole("permission_admin_role_deny", "权限管理员", "SYS_ADMIN");
        User user = createUserWithRole("permission_multi_role", "多角色用户", "PARENT");
        Role studentRole = roleMapper.findByCode("STUDENT");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), studentRole.id(), null));
        Role parentRole = roleMapper.findByCode("PARENT");
        Permission permission = permissionAdministrationService.createPermission(new CreatePermissionCommand(
                administrator.id(), "MULTI_ROLE_REPORT_VIEW", "多角色报表查看",
                PermissionResourceType.PAGE, PermissionClient.BOTH, null));
        permissionAdministrationService.configureRolePermission(new ConfigureRolePermissionCommand(
                administrator.id(), parentRole.id(), permission.id(), PermissionEffect.ALLOW));
        permissionAdministrationService.configureRolePermission(new ConfigureRolePermissionCommand(
                administrator.id(), studentRole.id(), permission.id(), PermissionEffect.DENY));
        permissionAdministrationService.configureUserPermission(new ConfigureUserPermissionCommand(
                administrator.id(), user.id(), permission.id(), PermissionEffect.ALLOW));

        assertThat(permissionDecisionService.isAllowed(
                user.id(), PermissionClient.WEB, permission.code())).isFalse();
    }

    @Test
    void rejectsConfiguringDisabledPermission() {
        User administrator = createUserWithRole("permission_admin_disabled", "权限管理员", "SYS_ADMIN");
        Role parentRole = roleMapper.findByCode("PARENT");
        Permission permission = permissionAdministrationService.createPermission(new CreatePermissionCommand(
                administrator.id(), "DISABLED_PERMISSION", "停用权限",
                PermissionResourceType.OPERATION, PermissionClient.WEB, null));
        jdbcTemplate.update("update sys_permission set status = 'DISABLED' where id = ?", permission.id());

        assertThatThrownBy(() -> permissionAdministrationService.configureRolePermission(
                new ConfigureRolePermissionCommand(
                        administrator.id(), parentRole.id(), permission.id(), PermissionEffect.ALLOW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("权限已停用");
    }

    @Test
    void rejectsPermissionChangesFromDisabledSystemAdministrator() {
        User administrator = createUserWithRole("permission_admin_locked", "权限管理员", "SYS_ADMIN");
        Role parentRole = roleMapper.findByCode("PARENT");
        Permission permission = permissionAdministrationService.createPermission(new CreatePermissionCommand(
                administrator.id(), "LOCKED_ADMIN_PERMISSION", "停用管理员权限",
                PermissionResourceType.OPERATION, PermissionClient.WEB, null));
        jdbcTemplate.update("update sys_user set status = 'DISABLED' where id = ?", administrator.id());

        assertThatThrownBy(() -> permissionAdministrationService.configureRolePermission(
                new ConfigureRolePermissionCommand(
                        administrator.id(), parentRole.id(), permission.id(), PermissionEffect.ALLOW)))
                .isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class)
                .hasMessageContaining("启用中的系统管理员");
    }

    private User createUserWithRole(String username, String name, String roleCode) {
        User user = userAccessApplicationService.createUser(new CreateUserCommand(username, name, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
}
