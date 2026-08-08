package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAttachmentApplicationServiceTest {
    private AttachmentFileApplicationService fileService;
    private ManagedFileMapper fileMapper;
    private FileRelationMapper relationMapper;
    private AttachmentContentStorage contentStorage;
    private TaskAttachmentApplicationService service;
    private AuthenticatedUser student;

    @BeforeEach
    void setUp() {
        fileService = mock(AttachmentFileApplicationService.class);
        fileMapper = mock(ManagedFileMapper.class);
        relationMapper = mock(FileRelationMapper.class);
        contentStorage = mock(AttachmentContentStorage.class);
        service = new TaskAttachmentApplicationService(
                fileService, fileMapper, relationMapper, contentStorage,
                mock(FeatureAccessService.class));
        student = new AuthenticatedUser(
                1874244142494647001L, 1874244142494647002L,
                "student", "学生", AuthClientType.MINIAPP, List.of("STUDENT"));
    }

    @Test
    void uploadsRealJpegAndPersistsSha256WithoutExposingStorageKey() {
        byte[] content = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        ManagedFile registered = file(FileStatus.UPLOADING, null);
        ManagedFile completed = file(FileStatus.AVAILABLE,
                "829b21a0693c4e12098f545e2a1e4dd0078a834f1df32f850503edb8b055f37a");
        when(fileService.registerUpload(any())).thenReturn(registered);
        when(fileService.completeUpload(any())).thenReturn(completed);

        TaskAttachmentView result = service.upload(student, new UploadTaskAttachmentCommand(
                "LEARNING_TASK_CHECKIN", "IMAGE", "reading.jpg", "image/jpeg", content));

        assertThat(result.id()).isEqualTo(1874244142494647003L);
        assertThat(result.contentUrl()).isEqualTo(
                "/api/v1/attachments/1874244142494647003/content");
        verify(contentStorage).store("attachment/test/reading", content);
        ArgumentCaptor<CompleteAttachmentUploadCommand> captor =
                ArgumentCaptor.forClass(CompleteAttachmentUploadCommand.class);
        verify(fileService).completeUpload(captor.capture());
        assertThat(captor.getValue().contentSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsDisguisedImageBeforeMetadataRegistration() {
        assertThatThrownBy(() -> service.upload(student, new UploadTaskAttachmentCommand(
                "LEARNING_TASK_CHECKIN", "IMAGE", "fake.jpg", "image/jpeg",
                "not-image".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("真实格式");

        verify(fileService, never()).registerUpload(any());
    }

    @Test
    void rejectsDuplicateFileIdsWhenAssociatingCheckIn() {
        assertThatThrownBy(() -> service.attachToCheckIn(
                student.userId(), List.of(1L, 1L), 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }

    private ManagedFile file(FileStatus status, String contentSha256) {
        return new ManagedFile(
                1874244142494647003L, "attachment/test/reading", "reading.jpg", "jpg",
                "image/jpeg", 4L, student.userId(), "LEARNING_TASK_CHECKIN", "IMAGE",
                contentSha256, status);
    }
}
