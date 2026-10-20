package com.lingdong.learning.iam.audit.application;

import com.lingdong.learning.datascope.application.DataScopeAdministrationService;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.permission.application.ConfigureRolePermissionCommand;
import com.lingdong.learning.permission.application.ConfigureUserPermissionCommand;
import com.lingdong.learning.permission.application.CreatePermissionCommand;
import com.lingdong.learning.permission.application.PermissionAdministrationService;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.domain.PermissionResourceType;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UpdateUserStatusCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.iam.audit.infrastructure.persistence.IamChangeAuditMapper;
import com.lingdong.learning.menu.application.MenuApplicationService;
import com.lingdong.learning.menu.application.MenuOrderCommand;
import com.lingdong.learning.menu.application.MenuWriteCommand;
import com.lingdong.learning.menu.domain.MenuStatus;
import com.lingdong.learning.menu.domain.MenuType;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 通过真实 Flyway、MySQL 和 MyBatis 验证身份权限审计的完整追加链路。 */
@SpringBootTest
@ActiveProfiles("test")
// MySQL 共享库：无事务注解会真实提交造成跨类残留（IAM_AUDIT_TEST_PERMISSION 曾污染 FlywayMigrationTest 权限计数）
@org.springframework.transaction.annotation.Transactional
class IamChangeAuditPersistenceTest {
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleApplicationService roleService;
    @Autowired private PermissionAdministrationService permissionService;
    @Autowired private com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper permissionMapper;
    @Autowired private DataScopeAdministrationService dataScopeService;
    @Autowired private OrganizationApplicationService organizationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private IamChangeAuditService auditService;
    @Autowired private MenuApplicationService menuService;
    @Autowired private com.lingdong.learning.organization.application.OrganizationTypeLifecycleApplicationService organizationTypeLifecycle;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    void appendsEverySupportedEventWithoutCreatingLogsForNoopOperations() {
        User operator = userService.createUser(new CreateUserCommand(
                "iam_audit_persistence_admin", "持久化审计管理员", null, UserType.PLATFORM));
        userService.assignRole(new AssignRoleToUserCommand(
                operator.id(), roleMapper.findByCode("SYS_ADMIN").id(), null));

        User target = userService.createUser(new CreateUserCommand(
                "iam_audit_persistence_target", "持久化审计目标", null, UserType.PLATFORM, operator.id()));
        Organization school = organizationService.createOrganization(new CreateOrganizationCommand(
                "IAM_AUDIT_PERSIST_SCHOOL", "审计持久化学校", "SCHOOL", null, 1));
        userService.associateWithOrganization(new AssociateUserWithOrganizationCommand(
                target.id(), school.id(), operator.id()));
        Role parentRole = roleMapper.findByCode("PARENT");
        userService.assignRole(new AssignRoleToUserCommand(target.id(), parentRole.id(), null, operator.id()));

        Role customRole = roleService.createCustomRole(new CreateCustomRoleCommand(
                "IAM_AUDIT_CUSTOM", "审计自定义角色", null, RoleDataScope.CUSTOM, operator.id()));
        Permission permission = permissionService.createPermission(new CreatePermissionCommand(
                operator.id(), "IAM_AUDIT_TEST_PERMISSION", "审计测试权限",
                PermissionResourceType.OPERATION, PermissionClient.WEB, null));

        // bf34c6a 后 WEB 权限必须注册在 authorization-model.json，自定义权限在配置入口即被
        // “尚未接入规范接口动作”拒绝；授权/审计链路改用目录规范权限验证（createPermission 保留以覆盖其审计事件）
        Long grantablePermissionId = permissionMapper.findByCode("IAM_PERMISSION_READ").id();
        permissionService.configureRolePermission(new ConfigureRolePermissionCommand(
                operator.id(), customRole.id(), grantablePermissionId, PermissionEffect.ALLOW));
        permissionService.configureRolePermission(new ConfigureRolePermissionCommand(
                operator.id(), customRole.id(), grantablePermissionId, PermissionEffect.DENY));
        permissionService.configureRolePermission(new ConfigureRolePermissionCommand(
                operator.id(), customRole.id(), grantablePermissionId, PermissionEffect.DENY));
        permissionService.removeRolePermission(operator.id(), customRole.id(), grantablePermissionId);
        permissionService.removeRolePermission(operator.id(), customRole.id(), grantablePermissionId);

        permissionService.configureUserPermission(new ConfigureUserPermissionCommand(
                operator.id(), target.id(), grantablePermissionId, PermissionEffect.DENY));
        permissionService.removeUserPermission(operator.id(), target.id(), grantablePermissionId);
        dataScopeService.configureRoleCustomScope(operator.id(), customRole.id(), school.id());
        // 学校需两名有效管理员：OrganizationOwnerGuard 的 activeAdministrators 要求
        // sys_organization_admin + sys_user_role(organization_id) + ORG_ADMIN/PARENT_ADMIN 角色三重匹配，
        // 单一管理员停用会被“不能移除最后一名有效管理员”防护拒绝
        Role orgAdminRole = roleMapper.findByCode("ORG_ADMIN");
        userService.assignRole(new AssignRoleToUserCommand(
                target.id(), orgAdminRole.id(), school.id(), operator.id()));
        User coAdmin = userService.createUser(new CreateUserCommand(
                "iam_audit_persistence_coadmin", "持久化审计共同管理员", null, UserType.PLATFORM, operator.id()));
        userService.associateWithOrganization(new AssociateUserWithOrganizationCommand(
                coAdmin.id(), school.id(), operator.id()));
        userService.assignRole(new AssignRoleToUserCommand(
                coAdmin.id(), orgAdminRole.id(), school.id(), operator.id()));
        dataScopeService.configureOrganizationAdministrator(operator.id(), coAdmin.id(), school.id());
        dataScopeService.configureOrganizationAdministrator(operator.id(), target.id(), school.id());
        // ORGANIZATION_ADMIN_REVOKE：需在 target 停用前撤销（双管理员时 count=2），
        // 停用后 coAdmin 成为最后一名有效管理员，任何撤销/解除关联都会被防护拒绝
        dataScopeService.revokeOrganizationAdministrator(operator.id(), target.id(), school.id());
        userService.updateStatus(new UpdateUserStatusCommand(target.id(), UserStatus.DISABLED, operator.id()));
        auditService.record(IamChangeAuditEventType.USER_PROFILE_CHANGE, operator.id(),
                IamChangeTargetType.USER, target.id(), null, school.id(), "资料变更前", "资料变更后");
        auditService.record(IamChangeAuditEventType.USER_PASSWORD_RESET, operator.id(),
                IamChangeTargetType.USER, target.id(), null, school.id(), null, "会话已撤销");

        // 补齐剩余审计事件类型：角色更名/启停/删除、角色撤销、组织关联撤销
        roleService.updateRole(operator.id(), customRole.id(), "审计自定义角色改名", "审计事件覆盖");
        roleService.changeRoleStatus(operator.id(), customRole.id(), com.lingdong.learning.iam.domain.RoleStatus.DISABLED);
        roleService.deleteRole(operator.id(), customRole.id());
        userService.removeRole(new AssignRoleToUserCommand(target.id(), parentRole.id(), null, operator.id()));
        userService.dissociateFromOrganization(new AssociateUserWithOrganizationCommand(
                target.id(), school.id(), operator.id()));

        // 补齐组织类型编辑/启停/删除事件（需自定义类型，内置类型不允许）
        var auditType = organizationService.createOrganizationType(new com.lingdong.learning.organization.application.CreateOrganizationTypeCommand(
                "AUDIT_ORG_TYPE", "审计组织类型", 90));
        organizationTypeLifecycle.update(operator.id(), auditType.id(), "审计组织类型改名", 91);
        organizationTypeLifecycle.changeStatus(operator.id(), auditType.id(),
                com.lingdong.learning.organization.domain.OrganizationStatus.DISABLED);
        organizationTypeLifecycle.delete(operator.id(), auditType.id());

        // 菜单管理操作：产生 MENU_CREATE / MENU_UPDATE / MENU_REORDER 审计，覆盖全部审计事件类型。
        // 页面路由必须在 PAGE_ROUTES 白名单内且未被占用：事务内清退 /users 真实页面后复用该路由
        jdbcTemplate.update("delete from sys_menu where parent_id in (select id from (select id from sys_menu where route='/users') routes)");
        jdbcTemplate.update("delete from sys_menu where route='/users'");
        AuthenticatedUser menuOperator = new AuthenticatedUser(
                operator.id(), 1L, operator.username(), operator.displayName(), AuthClientType.WEB, List.of("SYS_ADMIN"));
        var menuPage = menuService.create(menuOperator, new MenuWriteCommand(
                "AUDIT_PERSIST_PAGE", "审计页面", MenuType.PAGE, null, "/users", null, false, 0, MenuStatus.ENABLED, null));
        var buttonA = menuService.create(menuOperator, new MenuWriteCommand(
                "AUDIT_PERSIST_BTN_A", "审计按钮甲", MenuType.BUTTON, menuPage.id(), null, null, true, 0, MenuStatus.ENABLED, null));
        var buttonB = menuService.create(menuOperator, new MenuWriteCommand(
                "AUDIT_PERSIST_BTN_B", "审计按钮乙", MenuType.BUTTON, menuPage.id(), null, null, true, 10, MenuStatus.ENABLED, null));
        menuService.update(menuOperator, menuPage.id(), new MenuWriteCommand(
                "AUDIT_PERSIST_PAGE", "审计页面改名", MenuType.PAGE, null, "/users", null, false, 0, MenuStatus.ENABLED, menuPage.version()));
        menuService.order(menuOperator, new MenuOrderCommand(menuPage.id(),
                List.of(buttonB.id(), buttonA.id()),
                java.util.Map.of(buttonA.id(), buttonA.version(), buttonB.id(), buttonB.version())));
        menuService.delete(menuOperator, buttonB.id());

        IamChangeAuditPage page = auditService.query(null, null, operator.id(), null, null, null, 1, 100);
        Set<IamChangeAuditEventType> eventTypes = page.items().stream()
                .map(IamChangeAudit::eventType).collect(Collectors.toSet());

        // 15 条既有事件 + 共同管理员链路 5 条 + 角色更名/启停/删除 3 条 + 角色撤销/管理员撤销/关联撤销 3 条
        // + 组织类型编辑/启停/删除 3 条 + 3 条 MENU_CREATE + MENU_UPDATE + MENU_REORDER + MENU_DELETE 4 条 = 36
        assertThat(page.total()).isEqualTo(36);
        assertThat(eventTypes).containsExactlyInAnyOrder(IamChangeAuditEventType.values());
    }

    @Test
    void exposesOnlyAppendAndQueryPersistenceOperations() {
        Set<String> methods = Arrays.stream(IamChangeAuditMapper.class.getDeclaredMethods())
                .map(method -> method.getName().toLowerCase()).collect(Collectors.toSet());

        assertThat(methods).contains("insert", "findpage", "count");
        assertThat(methods).noneMatch(name -> name.startsWith("update") || name.startsWith("delete"));
    }
}
