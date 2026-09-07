package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.application.ConfigureRolePermissionCommand;
import com.lingdong.learning.permission.application.PermissionAdministrationService;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AttachmentRuleApplicationServiceTest {
    @Autowired private AttachmentRuleApplicationService attachmentRuleApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private RoleApplicationService roleApplicationService;
    @Autowired private PermissionAdministrationService permissionAdministrationService;
    @Autowired private PermissionMapper permissionMapper;

    @Test
    void systemAdministratorCreatesRuleAndValidatesExtensionSizeAndBatchCount() {
        User administrator = createUserWithRole("attachment_rule_admin", "附件规则管理员", "SYS_ADMIN");

        AttachmentRule rule = attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TASK", "EVIDENCE", "任务凭证", List.of("JPG", "pdf"),
                10 * 1024 * 1024L, 3, true
        ));

        assertThat(Long.toString(rule.id())).hasSize(19);
        assertThat(rule.versionNo()).isZero();
        assertThat(rule.allowedExtensions()).containsExactly("jpg", "pdf");
        attachmentRuleApplicationService.validateNewFiles("TASK", "EVIDENCE", List.of(
                new AttachmentCandidate("homework.JPG", "image/jpeg", 1_024L),
                new AttachmentCandidate("report.pdf", "application/pdf", 2_048L)
        ));

        assertThatThrownBy(() -> attachmentRuleApplicationService.validateNewFiles("TASK", "EVIDENCE", List.of(
                new AttachmentCandidate("script.exe", "application/octet-stream", 1L)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式");
        assertThatThrownBy(() -> attachmentRuleApplicationService.validateNewFiles("TASK", "EVIDENCE", List.of(
                new AttachmentCandidate("oversize.jpg", "image/jpeg", 10 * 1024 * 1024L + 1)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大小");
        assertThatThrownBy(() -> attachmentRuleApplicationService.validateNewFiles("TASK", "EVIDENCE", List.of(
                new AttachmentCandidate("one.jpg", "image/jpeg", 1L),
                new AttachmentCandidate("two.jpg", "image/jpeg", 1L),
                new AttachmentCandidate("three.jpg", "image/jpeg", 1L),
                new AttachmentCandidate("four.jpg", "image/jpeg", 1L)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("批量");
    }

    @Test
    void rejectsNonAdministratorConfigurationAndDisabledRuleUploads() {
        User ordinaryUser = createUser("attachment_rule_user", "普通附件用户");
        User administrator = createUserWithRole("attachment_rule_disable_admin", "附件停用管理员", "SYS_ADMIN");

        assertThatThrownBy(() -> attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                ordinaryUser.id(), "REVIEW", "MATERIAL", "复盘材料", List.of("png"), 1024L, 1, true
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("附件规则管理权限");

        AttachmentRule rule = attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "REVIEW", "MATERIAL", "复盘材料", List.of("png"), 1024L, 1, true
        ));
        attachmentRuleApplicationService.disableRule(administrator.id(), rule.id(), rule.versionNo());

        assertThatThrownBy(() -> attachmentRuleApplicationService.validateNewFiles("REVIEW", "MATERIAL", List.of(
                new AttachmentCandidate("review.png", "image/png", 1L)
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void queriesUpdatesAndChangesRuleStatusWithOptimisticConcurrency() {
        User administrator = createUserWithRole("attachment_lifecycle_admin", "附件生命周期管理员", "SYS_ADMIN");
        AttachmentRule created = attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "COURSEWORK", "EVIDENCE", "课程凭证", List.of("jpg"), 1024L, 2, true
        ));

        assertThat(attachmentRuleApplicationService.listRules(new AttachmentRuleQuery(
                administrator.id(), "凭证", "coursework", "evidence", AttachmentRuleStatus.ENABLED
        ))).extracting(AttachmentRule::id).containsExactly(created.id());

        AttachmentRule updated = attachmentRuleApplicationService.updateRule(new UpdateAttachmentRuleCommand(
                administrator.id(), created.id(), "课程材料", List.of(".PNG", "pdf"),
                2048L, 5, false, created.versionNo()
        ));
        assertThat(updated.ruleName()).isEqualTo("课程材料");
        assertThat(updated.allowedExtensions()).containsExactlyInAnyOrder("png", "pdf");
        assertThat(updated.maxFileSizeBytes()).isEqualTo(2048L);
        assertThat(updated.maxBatchCount()).isEqualTo(5);
        assertThat(updated.previewEnabled()).isFalse();
        assertThat(updated.versionNo()).isEqualTo(1L);
        assertThat(updated.moduleCode()).isEqualTo("COURSEWORK");
        assertThat(updated.fileCategory()).isEqualTo("EVIDENCE");

        assertThatThrownBy(() -> attachmentRuleApplicationService.updateRule(new UpdateAttachmentRuleCommand(
                administrator.id(), created.id(), "过期编辑", List.of("jpg"),
                1024L, 1, true, created.versionNo()
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("版本");

        AttachmentRule disabled = attachmentRuleApplicationService.disableRule(
                administrator.id(), updated.id(), updated.versionNo());
        assertThat(disabled.status()).isEqualTo(AttachmentRuleStatus.DISABLED);
        assertThat(disabled.versionNo()).isEqualTo(2L);
        AttachmentRule repeatedDisable = attachmentRuleApplicationService.disableRule(
                administrator.id(), disabled.id(), disabled.versionNo());
        assertThat(repeatedDisable.versionNo()).isEqualTo(disabled.versionNo());

        assertThatThrownBy(() -> attachmentRuleApplicationService.validateNewFiles(
                "COURSEWORK", "EVIDENCE", List.of(new AttachmentCandidate("proof.png", "image/png", 1L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");

        AttachmentRule enabled = attachmentRuleApplicationService.enableRule(
                administrator.id(), disabled.id(), disabled.versionNo());
        assertThat(enabled.status()).isEqualTo(AttachmentRuleStatus.ENABLED);
        assertThat(enabled.versionNo()).isEqualTo(3L);
    }

    @Test
    void letsDynamicallyAuthorizedCustomOperationsRoleManageRules() {
        User administrator = createUserWithRole("attachment_permission_admin", "附件授权管理员", "SYS_ADMIN");
        Role operationsRole = roleApplicationService.createCustomRole(new CreateCustomRoleCommand(
                "ATTACHMENT_OPS", "附件运维", "维护附件规则", RoleDataScope.ALL, administrator.id()
        ));
        grantPermission(administrator.id(), operationsRole.id(), "ATTACHMENT_RULE_READ");
        grantPermission(administrator.id(), operationsRole.id(), "ATTACHMENT_RULE_MANAGE");
        User operator = createUser("attachment_custom_operator", "附件运维人员");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(operator.id(), operationsRole.id(), null));

        AttachmentRule created = attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                operator.id(), "OPERATIONS", "LOG", "运维日志", List.of("txt"), 4096L, 2, false
        ));

        assertThat(attachmentRuleApplicationService.listRules(new AttachmentRuleQuery(
                operator.id(), null, "OPERATIONS", null, null
        ))).extracting(AttachmentRule::id).containsExactly(created.id());
    }

    private void grantPermission(Long administratorId, Long roleId, String permissionCode) {
        Permission permission = permissionMapper.findByCode(permissionCode);
        permissionAdministrationService.configureRolePermission(new ConfigureRolePermissionCommand(
                administratorId, roleId, permission.id(), PermissionEffect.ALLOW
        ));
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = createUser(username, displayName);
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }
}
