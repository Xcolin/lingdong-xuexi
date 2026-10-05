package com.lingdong.learning.permission.application;

import com.lingdong.learning.audit.application.*;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.iam.domain.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.menu.application.MenuApplicationService;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.organization.domain.OrganizationChangeType;
import com.lingdong.learning.permission.domain.*;
import com.lingdong.learning.permission.infrastructure.persistence.*;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SystemPermissionDrivenAccessTest {
    @Autowired RoleMapper roles;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired PermissionMapper catalog;
    @Autowired RolePermissionMapper rolePermissions;
    @Autowired UserPermissionMapper explicitPermissions;
    @Autowired UserRoleMapper userRoles;
    @Autowired IdGenerator ids;
    @Autowired UserAccessApplicationService users;
    @Autowired AuthenticationApplicationService auth;
    @Autowired MenuApplicationService menus;
    @Autowired SystemTaskApplicationService tasks;
    @Autowired SystemTaskQueryService taskQueries;
    @Autowired com.lingdong.learning.exportjob.infrastructure.persistence.SystemTaskLedgerExportMapper taskExports;
    @Autowired OrganizationApplicationService organizations;
    @Autowired OrganizationManagementApplicationService management;
    @Autowired OrganizationChangeApplicationService changes;
    @Autowired StudentQrTicketApplicationService qrTickets;
    @Autowired com.lingdong.learning.student.application.StudentIdentityProvisioningService studentIdentities;
    @Autowired com.lingdong.learning.student.infrastructure.persistence.StudentMapper studentRecords;
    @Autowired com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper enrollments;

    @Test void customRolesCanReadResetSubmitReviewButCannotSelfReviewOrBypassDeny() {
        User operator = custom("permission_sys_operator", RoleDataScope.ALL,
                "MENU_READ", "IAM_USER_PASSWORD_SET", "SYSTEM_TASK_READ", "FEATURE_TOGGLE_READ", "FEATURE_TOGGLE_MANAGE", "FEATURE_TOGGLE_REVIEW");
        User reviewer = custom("permission_sys_reviewer", RoleDataScope.ALL, "FEATURE_TOGGLE_REVIEW");
        User target = users.createUser(new CreateUserCommand("permission_password_target", "密码目标", null, UserType.PLATFORM));
        assertThat(userRoles.findEnabledRoleCodesByUserId(operator.id())).containsExactly("PERMISSION_SYS_OPERATOR");
        menus.list(web(operator));
        auth.setPlatformUserPassword(new SetPlatformUserPasswordCommand(operator.id(), target.id(), "Password123"));
        SystemTask task = tasks.createDraft(new CreateSystemTaskCommand(operator.id(), SystemTaskType.GLOBAL_FEATURE_TOGGLE,
                "自定义角色申请", "验证不依赖内置身份", ImpactScope.GLOBAL));
        assertThat(taskQueries.findDetails(web(operator), task.id()).status()).isEqualTo(SystemTaskStatus.DRAFT);
        assertThat(taskQueries.findPage(web(operator), null, 1, 20).items()).extracting(SystemTask::id).contains(task.id());
        tasks.submit(task.id(), operator.id());
        assertThatThrownBy(() -> tasks.approve(task.id(), operator.id(), "自行审核"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("自己");
        assertThat(tasks.approve(task.id(), reviewer.id(), "同意").status()).isEqualTo(SystemTaskStatus.APPROVED);
        explicitPermissions.insert(ids.nextId(), operator.id(), catalog.findByCode("MENU_READ").id(), PermissionEffect.DENY);
        assertThatThrownBy(() -> menus.list(web(operator))).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> menus.list(new AuthenticatedUser(operator.id(), 1L, operator.username(), operator.displayName(),
                AuthClientType.MINIAPP, List.of("PERMISSION_SYS_OPERATOR"))))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> tasks.createDraft(new CreateSystemTaskCommand(reviewer.id(), SystemTaskType.CACHE_CLEAR,
                "无缓存权限", "不能借用开关审核权限", ImpactScope.GLOBAL)))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test void reviewInOneDomainNeitherHidesOwnReadOnlyDomainNorRevealsOtherDomainTasks() {
        jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='CACHE_MANAGEMENT'");
        User operator = custom("permission_domain_operator", RoleDataScope.ALL,
                "SYSTEM_TASK_READ", "FEATURE_TOGGLE_READ", "FEATURE_TOGGLE_REVIEW", "CACHE_READ", "CACHE_MANAGE");
        User other = custom("permission_domain_other", RoleDataScope.ALL, "CACHE_MANAGE");
        var own = tasks.createDraft(new CreateSystemTaskCommand(operator.id(), SystemTaskType.CACHE_CLEAR, "本人缓存草稿", "自己的只读领域", ImpactScope.GLOBAL));
        var hidden = tasks.createDraft(new CreateSystemTaskCommand(other.id(), SystemTaskType.CACHE_CLEAR, "他人缓存申请", "不能借用其他领域审核权限", ImpactScope.GLOBAL));
        tasks.submit(hidden.id(), other.id());
        assertThat(taskQueries.findPage(web(operator), null, 1, 100).items()).extracting(SystemTask::id).contains(own.id()).doesNotContain(hidden.id());
        assertThat(taskQueries.findDetails(web(operator), own.id()).id()).isEqualTo(own.id());
        assertThatThrownBy(() -> taskQueries.findDetails(web(operator), hidden.id()))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        var scope = taskQueries.resolveScope(operator.id());
        assertThat(scope.types()).contains(SystemTaskType.CACHE_CLEAR, SystemTaskType.GLOBAL_FEATURE_TOGGLE);
        assertThat(scope.reviewableTypes()).containsExactly(SystemTaskType.GLOBAL_FEATURE_TOGGLE);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var frozen = json.convertValue(java.util.Map.of("upperBound", Long.MAX_VALUE,
                "systemTaskAuditor", scope.auditor(), "systemTaskTypes", scope.types(),
                "systemTaskReviewableTypes", scope.reviewableTypes()),
                com.lingdong.learning.exportjob.application.ExportScopeSnapshot.class);
        var request = json.convertValue(java.util.Map.of("requesterId", operator.id(),
                "systemTaskAuditor", frozen.systemTaskAuditor(), "systemTaskTypes", frozen.systemTaskTypes(),
                "systemTaskReviewableTypes", frozen.systemTaskReviewableTypes()),
                com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition.class);
        assertThat(taskExports.findAfter(request, frozen.upperBound(), 0L, 100))
                .extracting(row -> row.id()).contains(own.id()).doesNotContain(hidden.id());
        var ownOnly = json.convertValue(java.util.Map.of("requesterId", operator.id(),
                "systemTaskAuditor", true, "systemTaskTypes", scope.types(),
                "systemTaskReviewableTypes", List.of()),
                com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition.class);
        assertThat(taskExports.findAfter(ownOnly, Long.MAX_VALUE, 0L, 100))
                .extracting(row -> row.id()).contains(own.id()).doesNotContain(hidden.id());
    }

    @Test void allCustomScopeManagesOrganizationsWhileSelfScopeCannotEscapeItsBoundary() {
        User all = custom("permission_org_all", RoleDataScope.ALL, "ORG_NODE_READ", "ORG_NODE_CREATE", "ORG_NODE_UPDATE",
                "ORG_NODE_CHANGE_SUBMIT", "ORG_NODE_CHANGE_REVIEW");
        User self = custom("permission_org_self", RoleDataScope.SELF, "ORG_NODE_READ", "ORG_NODE_CREATE", "ORG_NODE_UPDATE",
                "ORG_NODE_CHANGE_SUBMIT", "ORG_NODE_CHANGE_REVIEW");
        var node = organizations.createOrganization(new CreateOrganizationCommand("PERMISSION_ORG_ROOT", "权限区域", "REGION", null, 10));
        assertThat(management.listOrganizations(all.id())).extracting(o -> o.id()).contains(node.id());
        assertThat(management.listOrganizations(self.id())).isEmpty();
        assertThatThrownBy(() -> management.updateOrganization(self.id(), new UpdateOrganizationCommand(node.id(), "越界编辑", 10, node.versionNo())))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> management.createOrganization(self.id(), new CreateOrganizationCommand("PERMISSION_ESCAPE", "越界根", "REGION", null, 10)))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        var change = changes.createAndSubmit(new CreateOrganizationChangeCommand(all.id(), node.id(), OrganizationChangeType.DISABLE,
                null, node.versionNo(), "验证组织数据范围"));
        assertThat(changes.listChanges(self.id())).isEmpty();
        assertThatThrownBy(() -> changes.reject(change.taskId(), self.id(), "越界审核"))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> changes.reject(change.taskId(), all.id(), "自行审核"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("自己");
    }

    @Test void customWebQrIssuerNeedsExactPermissionAndActiveEnrollmentWithinScope() {
        jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='STUDENT_QR_LOGIN'");
        User all = custom("permission_qr_all", RoleDataScope.ALL, "STUDENT_LOGIN_QR_CREATE");
        User self = custom("permission_qr_self", RoleDataScope.SELF, "STUDENT_LOGIN_QR_CREATE");
        User missingPermission = custom("permission_qr_missing", RoleDataScope.ALL, "MENU_READ");
        var credential = studentIdentities.issue("二维码权限学生");
        var student = com.lingdong.learning.student.domain.Student.create(ids.nextId(), "二维码权限学生", "G3", credential.studentUserId());
        studentRecords.insert(student);
        assertThatThrownBy(() -> qrTickets.issue(web(all), student.id()))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        var region = organizations.createOrganization(new CreateOrganizationCommand("PERMISSION_QR_REGION", "二维码区域", "REGION", null, 10));
        var school = organizations.createOrganization(new CreateOrganizationCommand("PERMISSION_QR_SCHOOL", "二维码学校", "SCHOOL", region.id(), 10));
        enrollments.insertEnrollment(ids.nextId(), student.id(), school.id());
        assertThat(qrTickets.issue(web(all), student.id()).qrContent()).startsWith("lingdong-learning://student-login?ticket=");
        jdbc.update("UPDATE sys_organization SET effective_status='DISABLED' WHERE id=?", school.id());
        assertThatThrownBy(() -> qrTickets.issue(web(all), student.id()))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        jdbc.update("UPDATE sys_organization SET effective_status='ENABLED' WHERE id=?", school.id());
        assertThatThrownBy(() -> qrTickets.issue(web(self), student.id()))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        assertThatThrownBy(() -> qrTickets.issue(web(missingPermission), student.id()))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> qrTickets.issue(new AuthenticatedUser(all.id(), 1L, all.username(), all.displayName(),
                AuthClientType.MINIAPP, List.of("PERMISSION_QR_ALL")), student.id()))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
    }

    private User custom(String username, RoleDataScope scope, String... permissions) {
        Role role = Role.custom(ids.nextId(), username.toUpperCase(), "自定义权限角色", null, scope);
        roles.insert(role);
        for (String code : permissions) rolePermissions.insert(ids.nextId(), role.id(), catalog.findByCode(code).id(), PermissionEffect.ALLOW);
        User user = users.createUser(new CreateUserCommand(username, "自定义权限用户", null, UserType.PLATFORM));
        users.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
    private AuthenticatedUser web(User user) {
        return new AuthenticatedUser(user.id(), 1L, user.username(), user.displayName(), AuthClientType.WEB, List.of(user.username().toUpperCase()));
    }
}

