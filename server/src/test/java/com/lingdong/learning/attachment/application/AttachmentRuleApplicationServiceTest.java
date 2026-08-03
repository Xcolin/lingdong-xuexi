package com.lingdong.learning.attachment.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
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

    @Test
    void systemAdministratorCreatesRuleAndValidatesExtensionSizeAndBatchCount() {
        User administrator = createUserWithRole("attachment_rule_admin", "附件规则管理员", "SYS_ADMIN");

        AttachmentRule rule = attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TASK", "EVIDENCE", "任务凭证", List.of("JPG", "pdf"),
                10 * 1024 * 1024L, 3, true
        ));

        assertThat(Long.toString(rule.id())).hasSize(19);
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
                .hasMessageContaining("系统管理员");

        AttachmentRule rule = attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "REVIEW", "MATERIAL", "复盘材料", List.of("png"), 1024L, 1, true
        ));
        attachmentRuleApplicationService.disableRule(administrator.id(), rule.id());

        assertThatThrownBy(() -> attachmentRuleApplicationService.validateNewFiles("REVIEW", "MATERIAL", List.of(
                new AttachmentCandidate("review.png", "image/png", 1L)
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");
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
