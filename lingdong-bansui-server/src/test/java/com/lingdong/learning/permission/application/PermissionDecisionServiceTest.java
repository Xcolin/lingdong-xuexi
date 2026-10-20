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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
// MySQL 共享库：历史版本无事务注解，createPermission 等真实提交造成跨类残留（IAM_AUDIT_TEST_PERMISSION 污染 FlywayMigrationTest）
@Transactional
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
        // bf34c6a 后 WEB 权限必须注册在 authorization-model.json（规范动作），自定义权限在配置入口即被拒；
        // 本测试验证判定引擎的角色授予/DENY 优先级语义，改直插授权行 + MINIAPP 判定（MINIAPP 不做规范动作校验）
        jdbcTemplate.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) VALUES(1900000000000099101,?,?, 'ALLOW')",
                parentRole.id(), permission.id());
        assertThat(Long.toString(jdbcTemplate.queryForObject(
                "select id from sys_role_permission where role_id = ? and permission_id = ?",
                Long.class, parentRole.id(), permission.id()
        ))).hasSize(19);
        assertThat(permissionDecisionService.isAllowed(
                parent.id(), PermissionClient.MINIAPP, "TASK_CREATE")).isTrue();
        assertThat(permissionDecisionService.findAllowedCodes(parent.id(), PermissionClient.MINIAPP))
                .contains("TASK_CREATE");

        // 用户级 DENY 走真实配置入口（effect=DENY 不做规范动作/活动角色校验）；
        // 不能用 jdbcTemplate 直插：MyBatis 一级缓存会在同事务内保留首次 findByUserId 的空结果，
        // 而服务内部的 MyBatis insert 会清缓存使新行可见
        permissionAdministrationService.configureUserPermission(new ConfigureUserPermissionCommand(
                administrator.id(), parent.id(), permission.id(), PermissionEffect.DENY));
        // ServletTestExecutionListener 会为 WebApplicationContext 绑定请求上下文，
        // AuthorizationRequestCache 据此在同一测试方法内固化权限快照；变更授权后需重置才能重新判定
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        assertThat(permissionDecisionService.isAllowed(
                parent.id(), PermissionClient.MINIAPP, "TASK_CREATE")).isFalse();
        assertThat(permissionDecisionService.findAllowedCodes(parent.id(), PermissionClient.MINIAPP))
                .doesNotContain("TASK_CREATE");
    }

    @Test
    void rejectsUserAllowWhenTheUserHasNoEnabledRole() {
        User administrator = createUserWithRole("permission_admin_allow", "权限管理员", "SYS_ADMIN");
        User user = userAccessApplicationService.createUser(new CreateUserCommand("permission_viewer", "查看用户", null, UserType.FAMILY));
        // bf34c6a 后自定义权限在 requireUserAvailable（WEB 规范动作校验）即被拒，
        // 改用目录规范权限让异常来自“用户补充允许必须建立在活动角色基础上”的活动角色校验
        Long permissionId = jdbcTemplate.queryForObject(
                "select id from sys_permission where permission_code = 'IAM_PERMISSION_READ'", Long.class);

        assertThatThrownBy(() -> permissionAdministrationService.configureUserPermission(
                new ConfigureUserPermissionCommand(
                        administrator.id(), user.id(), permissionId, PermissionEffect.ALLOW)))
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
        // 角色级 ALLOW/DENY 与用户级 ALLOW 均直插（自定义权限不进入规范动作配置入口）
        jdbcTemplate.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) VALUES(1900000000000099111,?,?, 'ALLOW')",
                parentRole.id(), permission.id());
        jdbcTemplate.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) VALUES(1900000000000099112,?,?, 'DENY')",
                studentRole.id(), permission.id());
        jdbcTemplate.update("INSERT INTO sys_user_permission(id,user_id,permission_id,effect) VALUES(1900000000000099113,?,?, 'ALLOW')",
                user.id(), permission.id());

        assertThat(permissionDecisionService.isAllowed(
                user.id(), PermissionClient.MINIAPP, permission.code())).isFalse();
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
        // 改用已注册规范动作的权限，让异常来自停用管理员校验而非规范动作拦截
        Long permissionId = jdbcTemplate.queryForObject(
                "select id from sys_permission where permission_code = 'IAM_ROLE_READ'", Long.class);
        jdbcTemplate.update("update sys_user set status = 'DISABLED' where id = ?", administrator.id());

        assertThatThrownBy(() -> permissionAdministrationService.configureRolePermission(
                new ConfigureRolePermissionCommand(
                        administrator.id(), parentRole.id(), permissionId, PermissionEffect.ALLOW)))
                .isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class)
                // 停用管理员在 requirePermission 的操作资格校验即被拦（早于“启用中的系统管理员”校验），语义不变
                .hasMessageContaining("当前账号无管理权限授权资格");
    }

    private User createUserWithRole(String username, String name, String roleCode) {
        User user = userAccessApplicationService.createUser(new CreateUserCommand(username, name, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
}
