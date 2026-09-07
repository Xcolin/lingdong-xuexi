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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
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

        IamChangeAuditPage page = auditService.query(null, null, operator.id(), null, null, null, 1, 100);
        Set<IamChangeAuditEventType> eventTypes = page.items().stream()
                .map(IamChangeAudit::eventType).collect(Collectors.toSet());

        assertThat(page.total()).isEqualTo(15);
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
