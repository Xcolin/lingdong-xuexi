package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportExecutionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentImportCredentialCleanupServiceTest {
    @Test
    void removesEncryptedContentBeforeMarkingCredentialExpired() {
        StudentImportExecutionMapper executionMapper = mock(StudentImportExecutionMapper.class);
        AttachmentFileApplicationService fileService = mock(AttachmentFileApplicationService.class);
        ManagedAttachmentContentService contentService = mock(ManagedAttachmentContentService.class);
        StudentImportCredentialCleanupService service = new StudentImportCredentialCleanupService(
                executionMapper, fileService, contentService, 10);
        StudentImportExecutionRecord execution = expiredExecution();
        ManagedFile file = mock(ManagedFile.class);
        when(executionMapper.findExpiredCredentials(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(10))).thenReturn(List.of(execution));
        when(fileService.findFile(execution.credentialFileId())).thenReturn(file);
        when(file.storageKey()).thenReturn("student-import/credential.enc");
        when(executionMapper.expireCredential(org.mockito.ArgumentMatchers.eq(execution.id()),
                org.mockito.ArgumentMatchers.eq(execution.versionNo()),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);

        assertThat(service.cleanupExpired()).isEqualTo(1);

        InOrder order = inOrder(contentService, executionMapper);
        order.verify(contentService).discardContent("student-import/credential.enc");
        order.verify(executionMapper).expireCredential(
                org.mockito.ArgumentMatchers.eq(execution.id()),
                org.mockito.ArgumentMatchers.eq(execution.versionNo()),
                org.mockito.ArgumentMatchers.any());
    }

    private StudentImportExecutionRecord expiredExecution() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 18, 0);
        return new StudentImportExecutionRecord(
                1874244142494647050L, "SIM-1874244142494647050", 2L, 3L, 4L, null,
                StudentImportExecutionStatus.SUCCEEDED, 2L, 1, 1, 1, 0,
                null, null, 1874244142494647051L, StudentImportCredentialStatus.AVAILABLE,
                now.minusMinutes(1), null, now.minusHours(24), now.minusHours(23), now,
                now.minusHours(24), now);
    }
}
