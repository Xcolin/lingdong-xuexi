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
        assertThat(permissionDecisionService.isAllowed(parent.id(), "TASK_CREATE")).isTrue();

        permissionAdministrationService.configureUserPermission(new ConfigureUserPermissionCommand(
                administrator.id(), parent.id(), permission.id(), PermissionEffect.DENY
        ));
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_user_permission where user_id = ? and permission_id = ?",
                Long.class, parent.id(), permission.id()
        ))).hasSize(19);
        assertThat(permissionDecisionService.isAllowed(parent.id(), "TASK_CREATE")).isFalse();
    }

    @Test
    void letsUserAllowSupplementAUserWithoutRole() {
        User administrator = createUserWithRole("permission_admin_allow", "权限管理员", "SYS_ADMIN");
        User user = userAccessApplicationService.createUser(new CreateUserCommand("permission_viewer", "查看用户", null, UserType.FAMILY));
        Permission permission = permissionAdministrationService.createPermission(new CreatePermissionCommand(
                administrator.id(), "REPORT_VIEW", "查看报表", PermissionResourceType.PAGE, PermissionClient.WEB, null
        ));

        permissionAdministrationService.configureUserPermission(new ConfigureUserPermissionCommand(
                administrator.id(), user.id(), permission.id(), PermissionEffect.ALLOW
        ));
        assertThat(permissionDecisionService.isAllowed(user.id(), "REPORT_VIEW")).isTrue();
    }

    private User createUserWithRole(String username, String name, String roleCode) {
        User user = userAccessApplicationService.createUser(new CreateUserCommand(username, name, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
}
