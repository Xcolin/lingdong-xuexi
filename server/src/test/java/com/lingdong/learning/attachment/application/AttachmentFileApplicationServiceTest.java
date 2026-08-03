package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileRelationStatus;
import com.lingdong.learning.attachment.domain.FileStatus;
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
class AttachmentFileApplicationServiceTest {
    @Autowired private AttachmentRuleApplicationService attachmentRuleApplicationService;
    @Autowired private AttachmentFileApplicationService attachmentFileApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;

    @Test
    void registersCompletesAndReleasesAFileWithoutPhysicallyDeletingItsMetadata() {
        User administrator = createUserWithRole("attachment_file_admin", "附件文件管理员", "SYS_ADMIN");
        User uploader = createUser("attachment_file_uploader", "附件上传人");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "TASK_FILE", "EVIDENCE", "任务凭证", List.of("jpg"), 10_240L, 1, true
        ));

        ManagedFile file = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), "TASK_FILE", "EVIDENCE", "proof.jpg", "image/jpeg", 1_024L
        ));

        assertThat(Long.toString(file.id())).hasSize(19);
        assertThat(file.status()).isEqualTo(FileStatus.UPLOADING);
        assertThat(file.storageKey()).contains("attachment/");
        assertThatThrownBy(() -> attachmentFileApplicationService.attachToBusiness(new AttachFileToBusinessCommand(
                file.id(), "TASK", 1001L, "CHECK_IN_PROOF", "BUSINESS_AUTHORIZED"
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未完成");

        ManagedFile completedFile = attachmentFileApplicationService.completeUpload(
                new CompleteAttachmentUploadCommand(file.id(), 1_024L, "image/jpeg")
        );
        FileRelation relation = attachmentFileApplicationService.attachToBusiness(new AttachFileToBusinessCommand(
                completedFile.id(), "TASK", 1001L, "CHECK_IN_PROOF", "BUSINESS_AUTHORIZED"
        ));
        assertThat(relation.status()).isEqualTo(FileRelationStatus.ACTIVE);
        assertThat(Long.toString(relation.id())).hasSize(19);

        attachmentFileApplicationService.releaseBusinessRelation(relation.id());

        assertThat(attachmentFileApplicationService.findFile(file.id()).status()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(attachmentFileApplicationService.findRelation(relation.id()).status()).isEqualTo(FileRelationStatus.RELEASED);
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
