package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileRelationStatus;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
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
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AttachmentFileApplicationServiceTest {
    @Autowired private AttachmentRuleApplicationService attachmentRuleApplicationService;
    @Autowired private AttachmentFileApplicationService attachmentFileApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private AttachmentLedgerApplicationService attachmentLedgerApplicationService;
    @Autowired private ManagedFileMapper managedFileMapper;

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

    @Test
    void queriesSafeFileLedgerAndReturnsActiveAndReleasedRelations() {
        User administrator = createUserWithRole("attachment_ledger_admin", "附件台账管理员", "SYS_ADMIN");
        User uploader = createUser("attachment_ledger_uploader", "台账上传人");
        attachmentRuleApplicationService.createRule(new CreateAttachmentRuleCommand(
                administrator.id(), "LEDGER_SOURCE", "DOCUMENT", "台账文档", List.of("pdf"), 20_480L, 3, true
        ));
        LocalDateTime queryStartedAt = LocalDateTime.now().minusMinutes(1);
        ManagedFile availableFile = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), "LEDGER_SOURCE", "DOCUMENT", "ledger-proof.pdf", "application/pdf", 2_048L
        ));
        availableFile = attachmentFileApplicationService.completeUpload(new CompleteAttachmentUploadCommand(
                availableFile.id(), 2_048L, "application/pdf", "a".repeat(64)
        ));
        FileRelation released = attachmentFileApplicationService.attachToBusiness(new AttachFileToBusinessCommand(
                availableFile.id(), "LEDGER_TASK", 2001L, "EVIDENCE", "BUSINESS_AUTHORIZED"
        ));
        attachmentFileApplicationService.attachToBusiness(new AttachFileToBusinessCommand(
                availableFile.id(), "LEDGER_TASK", 2002L, "EVIDENCE", "BUSINESS_AUTHORIZED"
        ));
        attachmentFileApplicationService.releaseBusinessRelation(released.id());

        ManagedFile uploadingFile = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), "LEDGER_SOURCE", "DOCUMENT", "pending-proof.pdf", "application/pdf", 512L
        ));
        ManagedFile retiredFile = attachmentFileApplicationService.registerUpload(new RegisterAttachmentFileCommand(
                uploader.id(), "LEDGER_SOURCE", "DOCUMENT", "retired-proof.pdf", "application/pdf", 256L
        ));
        retiredFile = attachmentFileApplicationService.completeUpload(new CompleteAttachmentUploadCommand(
                retiredFile.id(), 256L, "application/pdf"
        ));
        assertThat(managedFileMapper.markRetired(retiredFile.id())).isEqualTo(1);

        List<AttachmentFileLedgerView> result = attachmentLedgerApplicationService.listFiles(new AttachmentFileQuery(
                administrator.id(), "ledger", "ledger_source", "document", FileStatus.AVAILABLE,
                uploader.id(), queryStartedAt, LocalDateTime.now().plusMinutes(1)
        ));
        assertThat(result).hasSize(1);
        AttachmentFileLedgerView ledger = result.get(0);
        assertThat(ledger.id()).isEqualTo(availableFile.id());
        assertThat(ledger.uploaderName()).isEqualTo("台账上传人");
        assertThat(ledger.contentSha256Present()).isTrue();
        assertThat(Arrays.stream(AttachmentFileLedgerView.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("storageKey", "contentSha256");

        assertThat(attachmentLedgerApplicationService.listFiles(new AttachmentFileQuery(
                administrator.id(), "pending", null, null, FileStatus.UPLOADING,
                null, null, null
        ))).extracting(AttachmentFileLedgerView::id).containsExactly(uploadingFile.id());
        assertThat(attachmentLedgerApplicationService.listFiles(new AttachmentFileQuery(
                administrator.id(), "retired", null, null, FileStatus.RETIRED,
                null, null, null
        ))).extracting(AttachmentFileLedgerView::id).containsExactly(retiredFile.id());

        assertThat(attachmentLedgerApplicationService.listRelations(administrator.id(), availableFile.id()))
                .extracting(AttachmentRelationLedgerView::status)
                .containsExactlyInAnyOrder(FileRelationStatus.ACTIVE, FileRelationStatus.RELEASED);
    }

    @Test
    void rejectsFileLedgerQueryWithoutDynamicPermission() {
        User unauthorized = createUserWithRole("attachment_ledger_unauthorized", "无台账权限用户", "PARENT");

        assertThatThrownBy(() -> attachmentLedgerApplicationService.listFiles(new AttachmentFileQuery(
                unauthorized.id(), null, null, null, null, null, null, null
        ))).isInstanceOf(SystemOperationAccessDeniedException.class)
                .hasMessageContaining("文件台账权限");
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
