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

/** 通过真实 Flyway、H2 和 MyBatis 验证身份权限审计的完整追加链路。 */
@SpringBootTest
@ActiveProfiles("test")
class IamChangeAuditPersistenceTest {
    @Autowired private UserAccessApplicationService userService;
    @Autowired private RoleApplicationService roleService;
    @Autowired private PermissionAdministrationService permissionService;
    @Autowired private DataScopeAdministrationService dataScopeService;
    @Autowired private OrganizationApplicationService organizationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private IamChangeAuditService auditService;
    @Autowired private MenuApplicationService menuService;

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

        permissionService.configureRolePermission(new ConfigureRolePermissionCommand(
                operator.id(), customRole.id(), permission.id(), PermissionEffect.ALLOW));
        permissionService.configureRolePermission(new ConfigureRolePermissionCommand(
                operator.id(), customRole.id(), permission.id(), PermissionEffect.DENY));
        permissionService.configureRolePermission(new ConfigureRolePermissionCommand(
                operator.id(), customRole.id(), permission.id(), PermissionEffect.DENY));
        permissionService.removeRolePermission(operator.id(), customRole.id(), permission.id());
        permissionService.removeRolePermission(operator.id(), customRole.id(), permission.id());

        permissionService.configureUserPermission(new ConfigureUserPermissionCommand(
                operator.id(), target.id(), permission.id(), PermissionEffect.DENY));
        permissionService.removeUserPermission(operator.id(), target.id(), permission.id());
        dataScopeService.configureRoleCustomScope(operator.id(), customRole.id(), school.id());
        dataScopeService.configureOrganizationAdministrator(operator.id(), target.id(), school.id());
        userService.updateStatus(new UpdateUserStatusCommand(target.id(), UserStatus.DISABLED, operator.id()));
        auditService.record(IamChangeAuditEventType.USER_PROFILE_CHANGE, operator.id(),
                IamChangeTargetType.USER, target.id(), null, school.id(), "资料变更前", "资料变更后");
        auditService.record(IamChangeAuditEventType.USER_PASSWORD_RESET, operator.id(),
                IamChangeTargetType.USER, target.id(), null, school.id(), null, "会话已撤销");

        // 菜单管理操作：产生 MENU_CREATE / MENU_UPDATE / MENU_REORDER 审计，覆盖全部审计事件类型
        AuthenticatedUser menuOperator = new AuthenticatedUser(
                operator.id(), 1L, operator.username(), operator.displayName(), AuthClientType.WEB, List.of("SYS_ADMIN"));
        var menuPage = menuService.create(menuOperator, new MenuWriteCommand(
                "AUDIT_PERSIST_PAGE", "审计页面", MenuType.PAGE, null, "/audit-persist", null, false, 0, MenuStatus.ENABLED, null));
        var buttonA = menuService.create(menuOperator, new MenuWriteCommand(
                "AUDIT_PERSIST_BTN_A", "审计按钮甲", MenuType.BUTTON, menuPage.id(), null, null, true, 0, MenuStatus.ENABLED, null));
        var buttonB = menuService.create(menuOperator, new MenuWriteCommand(
                "AUDIT_PERSIST_BTN_B", "审计按钮乙", MenuType.BUTTON, menuPage.id(), null, null, true, 10, MenuStatus.ENABLED, null));
        menuService.update(menuOperator, menuPage.id(), new MenuWriteCommand(
                "AUDIT_PERSIST_PAGE", "审计页面改名", MenuType.PAGE, null, "/audit-persist", null, false, 0, MenuStatus.ENABLED, menuPage.version()));
        menuService.order(menuOperator, new MenuOrderCommand(menuPage.id(),
                List.of(buttonB.id(), buttonA.id()),
                java.util.Map.of(buttonA.id(), buttonA.version(), buttonB.id(), buttonB.version())));

        IamChangeAuditPage page = auditService.query(null, null, operator.id(), null, null, null, 1, 100);
        Set<IamChangeAuditEventType> eventTypes = page.items().stream()
                .map(IamChangeAudit::eventType).collect(Collectors.toSet());

        // 15 条既有事件 + 3 条 MENU_CREATE + 1 条 MENU_UPDATE + 2 条 MENU_REORDER = 21
        assertThat(page.total()).isEqualTo(21);
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
